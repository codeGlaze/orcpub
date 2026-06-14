# Content Extensibility — Decisions & Audit

**Purpose:** Record *how* the content-extensibility direction was reached — the
pivots, the dead-ends, and why we changed our minds — plus the crisp decisions it
produced. For both humans and agents picking this up cold. Design record, not
verified source behavior. See [content-extensibility.md](content-extensibility.md).

**Date opened:** 2026-06-13.

> ⚠️ **D12–D16 (added late) DEFLATE D2/D3 and the catalog-grant framing.** The grand
> registry / DSL was scaled back to "descriptor + a HOF over existing factories, for
> mechanical boilerplate only." `content-extensibility-direction.md` is the authoritative
> plan; D1–D11 are kept as the record of how we got there.

---

## Part 1 — How the thinking evolved (audit)

Read this to understand what we were thinking at each step, not just where we landed.

1. **Start: "8 files to add a minor option — is there a better way?"**
   First instinct was a single content-type *registry* to kill the scattered
   registration boilerplate. Plausible, but it was reasoning from the symptom.

2. **Pushback: "we might be talking past each other."**
   Prompted to look at a real change — the Pact Boon builder (commit `6029fd0`) — and
   at a hypothetical dragonborn lineage builder, instead of theorizing.

3. **Evidence changed the framing.** The boon diff touched 10 files, but split into
   two unrelated costs: *registration* (mechanical, scattered) and *injection*
   (wiring boons into the warlock via positional function args). The registry idea
   only addressed the mechanical half — and the cheaper half. → **D1.**

4. **First Layer-2 idea: parent-keyed "slots."** Attach a boon to
   `[:class :warlock :pact-boon]`. Looked tidy.

5. **Caveat that broke it: 5e is expansion-driven.** Feats grant spells, ASIs, even
   pact boons; backgrounds grant feats; homebrew adds more later. Asked directly:
   does the proposal make cross-tapping easier or harder? Honest answer: rigid slots
   make it *harder*, because one option granted by several parents needs several
   attachment declarations. → pivoted to **type-addressed catalogs + grants** (D3),
   and split "grant a fixed thing" (keep modifiers, D4) from "grant a choice from a
   set" (new).

6. **Pseudocode requested** to make the intent concrete — confirmed the catalog/grant
   shape reads cleanly and that subraces already do exactly this by parent key.

7. **Documentation requested; KB location clarified.** The canonical agent KB lives
   on `agents/develop`, which already has overlapping docs
   (`spa-routing-architecture.md` covers the registration side) and an issue cluster
   (`homebrew-builders.md`: #58, #57/#209, #172/#170, #210/#107, #280, #173, #128)
   that are real instances of this problem. Restructured the docs to that KB's
   conventions and separated verified-from-code facts from the proposal. → **D8.**

8. **Compatibility raised as a hard constraint, audited before planning.** Users with
   existing orcbrew libraries and built characters can't be broken. Decided to audit
   the *current* persisted formats first (orcbrew/plugins map, strict-entity
   characters, localStorage), derive invariants, and constrain the design to be
   additive. The audit showed the design can be zero-migration *if* catalogs derive
   over existing storage (as subraces already do) and selection/option keys are
   preserved. → **D9.** See
   [content-extensibility-compatibility.md](content-extensibility-compatibility.md).

9. **Readability review deflated the whole thing.** Pushed on `(catalog/by-parent :race x)`
   vs `(group-by :race x)`: the wrapper *added* thinking to a clear builtin — negative
   value. Generalizing: an abstraction must be *thicker* than what it hides and reveal
   intent. Re-graded everything: the catalog/grant DSL would be more `by-parent`-style
   indirection; looping readable data (`default-value`) trades readability for little;
   only *pure boilerplate* (identical passthrough subs) and *fragile* code earn collapsing.
   Landed on: descriptor + a clear HOF (`register-homebrew-content!`) composing the
   existing factories, scoped to boilerplate; keep readable code explicit. → **D12–D16.**

The throughline: each pivot came from concrete evidence (a real diff), a domain
constraint (5e's cross-pollination), a user-data constraint (existing orcbrew and
characters), or a readability constraint — not from preference. Note the honest failure
mode caught along the way: agentic *toadyism* — collapsing/backpedaling when pushed instead
of holding or refining a position. The corrective is in `verification-discipline.md`.

## Part 2 — Decision summary

**D1 — Treat the "8-file" cost as two problems.** Registration boilerplate and
parent-injection are different shapes with different fixes. *Rejected:* one
"scaffolding" fix — it misses the fragile injection half where bugs hide.

**D2 — Layer 1 is data + existing factories, not a macro.** A `content-types`
descriptor list feeding loops that call the existing `reg-*-homebrew` factories.
*Rejected:* a `defcontent` macro — opaque expansion, buys nothing when factories exist.

**D3 — Layer 2 addresses options by type, not by parent slot.** Children declare a
type; consumers pull from a type catalog with an optional filter. *Rejected:* fixed
parent-keyed slots — they break when one option is granted by several parents.

**D4 — Keep the modifier system for fixed grants.** `mod5e/*` handles "grant this
specific thing"; leave it. Catalogs/grants are only for "choose from a set."

**D5 — Migrate subraces first.** They already use the target bucket-by-key pattern, so
step one is a behavior-preserving refactor and an easy review. *Rejected:* starting
with boons (touches the fragile class sub) or lineages (new capability, not refactor).

**D6 — Keep route-keyword `def`s; generate downstream.** They're referenced by symbol
at compile time; generate the bidi tree, route sets, pages, events, and subs.

**D7 — Registry namespace stays a dependency leaf.** Requires only specs and
`route-map`; views referenced by keyword and resolved in `core.cljs`, to avoid the
circular dep the code already works around (`events.cljs` ~204).

**D8 — Document first; no code until the subrace spike is reviewed.**

**D9 — Backward compatibility is a non-negotiable constraint; target zero-migration.**
Existing orcbrew libraries and saved characters must keep working with no user-facing
migration. The design stays additive: catalogs derive over the existing plugin
storage (don't reformat it), existing plugin keys and the `orcpub.dnd.e5` namespace are
preserved, and selection/option keys that characters may have chosen are not renamed.
Each migration step is guarded by an orcbrew + saved-character fixture. *Rejected:*
designing the storage model first and auditing compatibility afterward — the audit
would too late to reshape it. Full analysis:
[content-extensibility-compatibility.md](content-extensibility-compatibility.md).

**D10 — Identity comes from a stable key, never from a display name.** Option/selection
keys must derive from a stable id (the stored `:key`, a `:class-key`, etc.), and display
text (`:name`, plugin-source suffix) is a separate slot. `name-to-kw` is a creation-time
default only; never re-run it on a name that display code may manipulate. *Why:* doing so
already orphaned saved characters when a source suffix was folded into class `:name`
(fixed on `feature/name-keyword-fix`: `option-cfg` `::plugin-source` slot, key-from-
`:class-key`, a load-time reconciler). The catalog/grant work must pass each item's stored
`:key` through to `option-cfg`. *Rejected:* relying on name→key re-derivation (the footgun).

**D11 — Catalog reads are layered, memoized subscriptions; never recomputed in hot subs.**
Each catalog is its own `reg-sub` (re-frame memoizes it); `grant-choice` references it
rather than rebuilding a whole option list inside a hot subscription. Guard the layering
with a brief comment. *Why:* avoids the recompute-everything cost and is also the fix for
the monolithic god-subscriptions (e.g. the 8-input `::classes5e/classes`). *Rejected:*
inline catalog construction in consumer subs.

---

## Part 3 — Late decisions (deflation; these supersede D2/D3 in scope)

**D12 — Readability is the deciding constraint.** An abstraction earns its keep only when
it is *thicker* than what it hides AND its interface reveals intent. `by-parent` fails
(wraps `group-by`) → **revert it**; `reg-save-homebrew` passes (thick, clear, already
trusted). *Rejected:* applying the registry/loops uniformly "for consistency."

**D13 — Deflate Layer 1 to a descriptor + one HOF, scoped to boilerplate.** A per-type
descriptor (data) + `register-homebrew-content!` that **composes the existing factories**
(`reg-save/new/edit-homebrew`, `reg-option-*`). Apply only to mechanical, low-readability
duplication (e.g. the identical passthrough subs). Keep readable data — notably
`default-value` — **explicit**. *Rejected:* descriptor-drives-everything loops, and the
catalog/grant **DSL** (more `by-parent`-style indirection; named subs + `selection-cfg`
already give the cross-aspect capability without new vocabulary).

**D14 — Don't force genuinely-different kinds into one registrar.** Verified 3 buckets:
6 "basic" types fit verbatim; 6 "richer" via a readable `:builder-features` flag set
(`reg-option-traits/modifiers/selections`); deviations stay separate — **magic-item**
(server-persisted via `::mi/save-item`/`::mi/custom-items`) gets its own
`register-server-content!`, **selection** gets a `:save-fn` hook (dup-option validation),
**combat** is excluded (not a builder). *Rejected:* a universal registrar branching on
every deviation — that's the unreadable god-function trap.

**D15 — HOFs/macros are fine when fed clear inputs.** The codebase already trusts
`reg-*-homebrew`/`reg-option-*`. The mid-session lean toward "reject HOF/macro" was an
overcorrection to a readability concern; the real enemy is *thin/obscuring* abstraction,
not HOFs.

**D16 — Working agreements.** Tests must be **falsifiable** (no theater — "if I break the
code, does this go red?"); **fix bugs on sight** unless deep enough for their own branch;
goal is **stabilize while adding features**, not build on shaky foundations.

**Net for next steps:** revert `by-parent`; build `register-homebrew-content!`; swap **boon**
through it + commit (harness-gated); then create a **new** builder end-to-end to measure the
real effort. Keep `default-value` explicit; don't build the catalog/grant DSL. Authoritative
plan: [content-extensibility-direction.md](content-extensibility-direction.md).
