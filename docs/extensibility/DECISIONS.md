# Dev Decisions: Content Extensibility

ADR-style log of decisions made about the "8-file problem" / content extensibility
initiative. Each entry: **Context → Decision → Rationale → Alternatives → Consequences
→ Status.** Newest decisions appended at the bottom. Do not delete superseded entries;
mark them **Superseded** and link forward.

**Scope:** how new content types and cross-aspect grants are wired into the 5e app.
**Date opened:** 2026-06-13. **Stage:** design; no production code changed.

---

## D1 — Treat the "8-file" pain as two problems, not one

- **Context:** Adding a builder/plugin type touches ~8 files (Pact Boon = 10, commit
  `6029fd0`). Splitting that diff by kind of change showed two unrelated costs.
- **Decision:** Model the problem as (1) **registration** boilerplate and
  (2) **injection** of options into a parent entity. Solve them with separate,
  composable layers.
- **Rationale:** They have different shapes and different fixes; conflating them led
  to a registry proposal that solved only half the pain (and the cheaper half).
- **Alternatives:** Treat it as one "scaffolding" problem (rejected — misses the
  fragile positional injection that caused the real risk).
- **Consequences:** Two work-streams (Layer 1, Layer 2) that ship independently.
- **Status:** Accepted.

## D2 — Layer 1 is a data-driven registry built on existing factories

- **Context:** Registration is scattered parallel call-sites, but the codebase
  already has `reg-save-homebrew`, `reg-new-homebrew`, `reg-edit-homebrew`,
  `reg-delete-homebrew`, `reg-local-store-cofx`, and `builder-page`.
- **Decision:** Introduce a single `content-types` descriptor list; convert the
  scattered registrations into loops that feed the **existing** factories.
- **Rationale:** Reuse over reinvention — the per-type descriptor already implicitly
  exists; we are centralizing it, not building new abstractions. Lowest-risk way to
  kill the registration boilerplate.
- **Alternatives:** A `defcontent` **macro** (rejected, see D6); finishing the
  factoring without centralizing (lower payoff, leaves "did I edit all N files?"
  intact).
- **Consequences:** Adding a type → append one descriptor. Registration becomes
  all-or-nothing (can't half-wire).
- **Status:** Accepted (design).

## D3 — Layer 2 uses type-addressed catalogs + grants, NOT parent-keyed slots

- **Context:** First framing of Layer 2 attached child options to a fixed parent
  "slot" like `[:class :warlock :pact-boon]`. The cross-aspect caveat (5e lets
  feats/backgrounds grant almost anything, and homebrew expands this) broke it.
- **Decision:** Address options by their **type** (catalog), and let each consumer
  declare a **grant** that reads a catalog with an optional filter. Producers and
  consumers never reference each other.
- **Rationale:** Fixed slots make a boon grantable by both warlock and feat require
  multiple attachment declarations — O(producers × consumers). Type catalogs make it
  O(producers + consumers) and let homebrew flow into existing grants for free, which
  matches 5e's "expansion of possibilities" reality.
- **Alternatives:** Rigid parent-keyed slots (**rejected**); per-consumer bespoke
  subscriptions (the status quo that caused the boon pain).
- **Consequences:** Introduces one new concept — a filter/prerequisite predicate on
  grants. Cross-linking stops being positional threading.
- **Status:** Accepted (design). Supersedes the "slots" idea floated earlier in
  discussion.

## D4 — Preserve the modifier system for fixed grants ("Kind A")

- **Context:** Some grants are a *specific known* thing ("grants Fire Bolt",
  "grants fire resistance"), already handled by `mod5e/*` late-binding modifiers.
- **Decision:** Keep `mod5e/*` untouched for Kind A. Catalogs/grants ("Kind B") are
  only for "choose from a whole option-set."
- **Rationale:** The modifier indirection is a genuinely good decoupling mechanism
  (a feat emits a modifier; it does not import the spell module). No reason to churn
  it. Both feed the same entity build.
- **Alternatives:** Route everything through catalogs (rejected — over-reach, would
  destabilize a working core).
- **Consequences:** Two complementary mechanisms with a clear boundary: fixed →
  modifier; choice-from-set → grant.
- **Status:** Accepted.

## D5 — Migrate subraces first (behavior-preserving proving ground)

- **Context:** Subraces already use the exact target shape (bucket-by-parent-key,
  merged in a sub, parent definitions untouched).
- **Decision:** First concrete step is a spike that introduces the generic catalog
  injector and migrates **subraces** onto it, with no behavior change, before
  touching boons/invocations or adding lineages.
- **Rationale:** Proves the abstraction reads cleanly against code that already
  works, so any diff is a pure refactor and easy to review/verify. De-risks the
  riskier migrations that follow.
- **Alternatives:** Start with boons (rejected — touches the fragile class-options
  subscription first); start with lineages (rejected — that's new capability, not a
  refactor).
- **Consequences:** Ordered migration: subraces → subclasses → boons/invocations →
  `ctx` map → lineage capability. See [the cross-link map](../kb/content-extensibility-cross-links.md).
- **Status:** Accepted; spike not yet started.

## D6 — Prefer data over macros for Layer 1

- **Context:** A `defcontent` macro could also collapse the registrations.
- **Decision:** Use a plain data registry + loops, not a macro.
- **Rationale:** cljc macros need `.clj`-ns + reader-conditional plumbing; expansion
  is opaque at the REPL; errors point at generated code; and the factories already
  exist, so a macro buys nothing over data. Data > macros here.
- **Alternatives:** `defcontent` macro (rejected).
- **Status:** Accepted.

## D7 — Keep route-keyword `def`s; generate everything downstream

- **Context:** `route_map.cljc` has one `(def …-route :kw)` per builder, referenced
  by symbol at compile time elsewhere.
- **Decision:** Keep those one-line `def`s. Generate the bidi tree, route sets, pages
  map, events, subs, and db slots *from* the registry that references them.
- **Rationale:** Generating vars for compile-time symbol references is more trouble
  than the single line it would save, and would hurt grep-ability.
- **Status:** Accepted.

## D8 — The registry namespace must be a dependency leaf

- **Context:** `events.cljs:204` already documents a circular-dependency workaround
  (`event-utils` delegation) between events and subs.
- **Decision:** The `content-types` registry ns may require only spec namespaces and
  `route-map`. Views are referenced by **keyword** and resolved in `core.cljs` (which
  already depends on `views`), never stored as functions in the registry.
- **Rationale:** Storing view fns or requiring events/subs/views from the registry
  would reintroduce cycles.
- **Status:** Accepted.

## D9 — Fold per-aspect positional args into an ambient build `ctx`

- **Context:** `spell-lists`, `spells-map`, `language-map`, `weapons-map` are threaded
  positionally into every class/race option builder; this width is what made adding
  `boons`/`invocations` as more positional args so error-prone.
- **Decision:** Plan to pass a single `ctx` map to option builders instead of a
  growing positional arg list.
- **Rationale:** Stops signatures and subscription vectors from growing one argument
  per feature; the root cause of the silent mis-binding risk.
- **Alternatives:** Keep positional args (rejected — the very problem we're fixing).
- **Consequences:** A wide but mechanical refactor; sequenced after the boon/invocation
  migration in CROSS_LINK_MAP.md.
- **Status:** Accepted (design); not started.

## D10 — Document-first; no code until the subrace spike is reviewed

- **Context:** The original request was explicitly "a plan, not immediate action,"
  later "document this."
- **Decision:** Capture analysis, target architecture, cross-link map, and these
  decisions as committed docs. Write no production code until the subrace spike is
  approved.
- **Rationale:** Preserves the reasoning against context loss; keeps the user in
  control of when implementation starts.
- **Status:** Accepted; docs created 2026-06-13.
