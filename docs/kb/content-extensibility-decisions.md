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
>
> ⚠️ **D17b–D22 (Part 4) RE-CENTER.** The deflation over-applied a *local* readability lesson
> (kill the unreadable `by-parent` wrapper) to the *capability* (cross-silo composition) and
> wrongly shelved D3. Part 4 restores the capability — an open **pool + grant** layer with
> filter/gate/prereq and a variant forward-compat seam — with readability kept as a
> *constraint*, not a ceiling. The direction doc (v2) is authoritative.

---

## Status at a glance

One line per decision so the depth below is navigable. **LIVE** = current standing rule; **DONE** =
decided and shipped; **OPEN** = unresolved; **REVERSED** = overturned (kept, stubbed, as a "decided
against" record). Full reasoning is in the body — this is only an index.

| # | Decision (short) | Status |
|---|---|---|
| D1 | Split the "8-file" cost into registration vs injection | LIVE |
| D2 | Layer 1 = data + existing factories, not a macro | DONE (`3980ea1b`) |
| D3 | Address options by **type**, not parent slot | LIVE (became pool+grant, D19) |
| D4 | Keep `mod5e/*` modifiers for fixed grants | LIVE |
| D5 | Migrate subraces first | DONE |
| D6 | Keep route-keyword `def`s; generate downstream | LIVE |
| D7 | Registry namespace stays a dependency leaf | LIVE |
| D8 | Document before coding the spike | DONE (historical) |
| D9 | Backward compat is non-negotiable; zero-migration | LIVE (constraint) |
| D10 | Identity from a stable key, never a display name | LIVE |
| D11 | Catalog reads = layered memoized subs | LIVE |
| D12 | Readability is the deciding constraint | LIVE |
| D13 | Deflate Layer 1 to descriptor+HOF; ~~reject catalogs/grants~~ | DONE (deflation) / **REVERSED** (anti-catalog half, by D17b–D19) |
| D14 | Don't force different kinds into one registrar | LIVE |
| D15 | HOFs/macros are fine when fed clear inputs | LIVE |
| D16 | Working agreements (falsifiable tests; fix-on-sight) | LIVE |
| D17 | Audit what each piece REPLACES; **no generic wrapper** | LIVE (heavily cited) |
| D17b | Stability and flexibility are the same abstraction | LIVE (was a duplicate "D17") |
| D18 | The capability gap is in AUTHORING, not the engine | LIVE |
| D19 | Pool + grant, with graceful optional filtering | LIVE (core direction) |
| D20 | Variants designed-in now, built later (one indirection) | LIVE (pin) |
| D21 | Maintainability is a GATE: O(1) to expose a new pool | LIVE (falsifiable) |
| D22 | Builder forms are data ("irreducible" was a retreat) | DONE (`109b5dd0`) |
| D23 | This branch is a PROTOTYPE: decide a standard, then converge | LIVE (governing) |
| D24 | Class features → one keyed, filterable registry | LIVE (design) |
| D25 | Features are macro-captured code → `compile-feature` (fields+template) | LIVE (proven) |
| D26 | Per-class catalogue done for all 12; reshapes B1/B3 | DONE (catalogue) |
| D27 | Spell slots: bucket of tables + declared multiclass rule | LIVE (design) |
| D28 | Non-SRD never pre-built; validate with synthetic stand-ins | LIVE (rule) |
| D29 | The grant approach (generic compiler vs open pools) | **OPEN** |
| D30 | "grant" earns its keep only as a thin compiler, not a 2nd engine | LIVE |
| D31 | Two vocabularies share effects but differ in application mode | LIVE (test-backed) |
| D32 | UI dropdowns must round-trip their value type (`:typed?`) | DONE |
| D33 | Export data = terse encodings + commented source, not self-documenting bytes | LIVE (rule) |

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

## Part 3 — Late decisions (deflation; these scaled back D2/D3 — but were themselves RE-CENTERED by Part 4)

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
> ⚠️ **Half REVERSED.** The boilerplate-deflation half (descriptor + HOF over factories) stands and
> shipped (`3980ea1b`). The "named subs + `selection-cfg` are *enough*, don't build catalogs/grants"
> half was overturned by **D17b/D18/D19** — D18 explicitly corrects it ("true of the mechanism, false
> as a stopping point"). The pool+grant layer IS being built; only a *cryptic DSL* remains rejected.

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

**D17 — Audit what each framework piece REPLACES before building it.** For every new
primitive, first find the existing app code it would replace and ask: does the app already do
this as well or better? If the "new" thing isn't *thicker* than what it replaces (D12), or it
risks dropping load-bearing details (`:ref` — where a character stores a choice; `:tags`;
modifiers), DON'T build it — extend or point at the existing thing instead. *Caught immediately:*
the proposed generic `grant` primitive would have re-wrapped the perfectly-good per-feature
`selection-cfg` constructors (`fighting-style-selection`, `feat-selection`) — adding nothing and
risking dropping their `:ref`. The real gap is narrower: **open pools** feeding those existing
selections + an **author-declarable grant-spec that compiles down to `selection-cfg`** (preserving
`:ref`/`:tags`), NOT a wrapper that replaces the constructors.

**Net for next steps:** ~~revert `by-parent`~~ ✅ done `9777ce88`; ~~build
`register-homebrew-content!`; swap **boon**~~ ✅ done `3980ea1b`. **Now re-centered (Part 4):**
the next core work is the **pool + grant** composition layer (see direction doc v2 §"The spine"
and the PINS). Authoritative plan:
[content-extensibility-direction.md](content-extensibility-direction.md).

---

## Part 4 — Re-centering (these restore the capability D12–D16 over-deflated)

**Context.** After D12–D16 the plan was scaled to "collapse boilerplate, do not build
catalogs." A vision review showed that under-served the branch's *two* equal goals —
**stability** and **flexibility** — because it shelved the one capability that delivers both.

**D17b — Stability and flexibility are the SAME abstraction.** (Numbered D17b, not D17: this is a
*distinct* decision that collided with the Part 3 D17 due to a historical glitch. The Part 3 D17 — the
"audit what each piece replaces / no generic wrapper" decision — keeps the bare number because it is
cited ~30× across the codebase; this one is cited nowhere by number, so it takes the suffix instead of
renumbering D18–D32.) Today every cross-type link is
bespoke positional wiring (boons→warlock by arg; custom-race menu a hardcoded vector; styles
baked per class). That bespoke-ness *is* the ~8-file cost and the fragility. One declarative
**pool + grant** primitive collapses N×M bespoke wirings to N+M declarations down one tested
path → simultaneously the stability win and the flexibility win. They do not trade off.
*Rejected:* treating "make it stable/readable" and "make it flexible" as opposed.

**D18 — The capability gap is in AUTHORING, not the engine.** Verified: `selection-cfg` has
`prereq-fn`/`tags`/`ref`; `option-prereq` exists; `option-cfg` has `prereqs`/nested
`selections`/`modifiers`; `ability-increase-selection-2` is the floating-ASI mechanism. The
runtime already composes with filter/gate/prereq. What's missing is letting *content declare*
open cross-silo grants as data. So this is exposing existing engine power, not rebuilding it.
*Corrects:* the deflation's "named subs + selection-cfg are enough" — true of the mechanism,
false as a stopping point (it left the menu closed and the wiring bespoke).

**D19 — Pool + grant, with graceful optional filtering.** Two words of vocabulary: a **pool**
(named, open, type-addressed, derived over plugin storage, pack-extensible) and a **grant**
(fixed | choice → compiled to `selection-cfg`). Filters are predicates over *present*
metadata: absent metadata → not offered → **never an error**; tags are a useful add, not a
required schema. Blank-slate parametric grants (+N ASI / +N speed) are built-in pools +
parametric modifiers, same primitive. This *passes* D12 (thicker than a hardcoded vector,
intent-revealing) where `by-parent` failed it. *Rejected:* a cryptic DSL; new vocabulary
beyond pool/grant.

**D20 — Variants are designed-in-now, built-later, via one indirection.** A variant
(`_copy` + `_mod`, the 5etools shape) resolves to an ordinary item in a pre-pass:
`raw :plugins → resolve-variants → resolved-content → pools → grants`. `resolve-variants` is
**identity today**. The binding rule: *every pool derives from one `resolved-content`
indirection, never raw `:plugins`*. Hold it and variants slot in later with no refactor of
the pool/grant work. Variants reference base by stable key, not name (D10). *Rejected:*
ignoring variants now (would force a later refactor) and building full resolution now (YAGNI).

**D21 — Maintainability is a GATE: easier to add tooling, not harder.** The whole point is that
exposing a new grant-type/pool must drop from O(builders) bespoke edits (today) to O(1)
registration (register a pool once → grantable in every builder; "boons → feats/classes" falls
out free because boons are already a pool). This is guarded by two non-negotiable disciplines:
(1) `grant` is a **thin compiler** to `selection-cfg` — pool-kind logic lives in each pool's
definition, NEVER as a `cond` inside `grant` (that's the D14 god-function trap); (2) **one
reused** grant-authoring UI component, not per-builder forks (pools carry light "which builders
may offer me" scoping). **Falsifiable proof, not a promise:** the first slice's acceptance test
is "exposing a *second* pool in a builder is a ~1-line registration, shown in a commit"; if it
isn't trivially cheap, the retooling failed and we STOP. *Rejected:* taking "it'll be easier"
on faith — it must be measured.

**D22 — Builder FORMS are data; "irreducible per-type work" was a retreat reflex.** Claimed (in
conversation) that each type needs a bespoke builder form. The code disproved it: `boon-builder`
and `invocation-builder` were identical forms differing only by a `set-*-prop` keyword.
Collapsed into `simple-content-builder` (`109b5dd0`). The genuinely irreducible core is small —
the **field schema** (data) + a reusable widget registry for complex fields + the field→mechanics
mapping (mostly the existing `:props` vocabulary) — NOT a per-type form. *Process lesson:* the
"irreducible" framing appeared right after the readability pushback and functioned as a way to
lower the bar instead of keep hunting efficiencies — the same retreat/toadyism failure mode logged
in `verification-discipline.md`, recurring in new clothes. Caught by the user; the corrective is to
treat "this part is irreducible" as a claim that must be proven against the code, not asserted.

**Pins (designed-in, built-later):** variants (D20); new-skill *creation* (adds to the skill
registry, not a grant — different shape); the class-feature pool (`[:class-feature :X]` —
richer than flat pools); a declarative cross-type prereq vocabulary (`has-class?`/`level>=`/
`has-feature?`/`ability>=` — homebrew prereqs must not be raw fns); **mechanical effects for
text-only content** (boons/ki/sorcery-points are prose today — authors should attach real
modifiers/resources; user flagged boons as an enhancement; same Axis-B "declare-as-data"
family).

## Part 5 — Mechanization, class features, spell slots (the expansion)

*(Numbering note: the Part 4 duplicate "D17" is now **D17b** (the Part 3 D17 owns the bare number — it
is the one cited across the codebase). This part continues at D23. D-numbers are stable IDs cited from
tests/code/other docs, so entries are never renumbered once cited — corrections are stubbed in place.)*

**D23 — This branch is a PROTOTYPE to decide a standard, then converge on it.** The purpose is to
understand the app fully (by reading code, not assumptions/vibes), **decide** the best shape for each
mechanism, then **run with it** — slowly upgrading the codebase to that standard and **jettisoning
anything that doesn't fit**. So open questions (e.g. the grant approach, D29) are expected mid-prototype;
the deliverable is a decision + convergence, not a permanent fork. Corollary: every decision below is
provisional until "fully understood," and the loser of a competing-approach decision is removed, not kept.
**Clarification (user, 2026-06-19) — don't over-rotate into purge-zealotry.** Parallel/duplicate models
DURING testing are fine and expected — that's what prototyping is — *as long as each is clearly marked*
(a comment/flag) so it can be cleaned, updated, or removed later. Cleanup follows a DECISION; it does not
precede one. Do not delete a model just because another looks like it's winning. "Jettison what doesn't
fit" is the *end* state after deciding, not an in-flight reflex.

**D24 — Class features → one keyed, filterable registry; pools are filtered views over it.** Not
per-feature pools (category mismatch) and not whole level tables. An editable reference is a **key +
`:overrides`**; a feature is a parameterized record with defaults; overrides deep-merge at compile.
Scaling/padding (ASI, num-attacks, `level-val`) stays as existing primitives. (`class-features-and-mechanization.md`.)

**D25 — Features are macro-captured CODE, so extraction needs a `compile-feature` step — and the
summary is fields + a fill template, NOT string interpolation.** `dependent-trait`/`action`/etc. splice
the cfg (with live `?class-level`/`level-val`) into code at compile time. `compile-feature` translates a
DATA spec → the same cfg: scaling via a `{level→n}` schedule + a runtime `level-lookup`; the overridable
numbers (heal die/bonus, sneak die) are **fields**, and `:text` is a template (`{name}` prints, `{+name}`
signs). **Proven** against the real build — data specs reproduce fighter Action Surge/Second Wind and
rogue Sneak Attack, with `:uses` and `:die` overrides changing only their field (`compile-feature` proof,
`class_feature_snapshot_test.clj`). Retracts the earlier "summary scaling is a separate blocked templating
sub-problem" framing.

**D26 — Per-class catalogue (C1) done for all 12; it re-shapes B1/B3.** Sizing: ~3–6 distinct
auto-features/class, but monk/paladin ~10, druid/sorcerer/wizard ~2–3, warlock ≈ 0 (all selections). The
registry/compiler must also handle: use-counts from **non-level sources** (ability-mod, formula, level
itself); **class-wide resource pools** (ki/sorcery/Lay-on-Hands — their own mechanism, B3, not per-feature
frequency); summaries that interpolate the **build context** (save DC, ability bonuses, user selections);
**multi-part features** (compile → a seq of modifiers); **attribute interdependence** (`?martial-arts-die`,
`?paladin-aura`, `?wild-shape-cr`). Extraction order: clean classes first (fighter/rogue, then
bard/cleric/wizard); defer monk/paladin/druid; warlock barely participates. (`class-feature-catalogue.md`.)

**D27 — Spell slots: replace the overloaded `:level-factor` with a bucket of tables + a declared
multiclass rule.** One integer currently drives the solo slot table AND the multiclass contribution
(`int(level/factor)`) AND the prepared count — which is why Artificer can't be expressed and why
undocumented factors 4/5/6 exist. Decouple into: (1) a **bucket of named/explicit slot tables**, authored
as an **absolute per-level grid** (the app converts; presets seed the grid); (2) a **separately-declared
multiclass rule** (`:full|:half|:third|:none|:separate`); (3) its own prepared/known count. `:separate`
(pact) schedules own their pool + recharge (→ B3), generalizing warlock's hardcoded branch. (`spell-slot-progression.md`.)

**D28 — Non-SRD content (e.g. Artificer) is never shipped pre-built; it must be user-assembled from
generic tools, and expressiveness is validated with a SYNTHETIC stand-in, never a copyrighted fixture.**
The dead `ua_artificer.cljc` is a *capability witness* (the primitives suffice), not a shippable start.
Same rule as Maneuvers/Mariner. Infusions ≈ a **scaling, swappable, item-granting pool** — the warlock
invocation pattern (`eldritch-invocation-selection`) generalized + magic-item reuse — so Artificer is a
forcing function for the pool/grant + B3 + spell-slot work, not a special case.

**D29 — OPEN (the one unresolved decision): the grant approach.** The Phase-2 `grant-selection`
bridge prototype (`c1f54967`, `options.cljc:3447`) is a **generic** grant compiler (generic `:tags`, no
`:ref`). The Phase-1 **D17 audit decided against** a generic wrapper — point existing per-feature
`selection-cfg` constructors at **open pools**, preserving their load-bearing `:ref`/`:tags`; the
hand-wired draconic grant is the thing to generalize. These are two approaches to one goal. Per D23, this
must be **decided and converged**, not left forked: pick one, migrate to it, jettison the other.
(Per the D23 clarification, the `grant-selection` prototype can REMAIN as a clearly-marked parallel while
we test — it already carries "BRIDGE PROTOTYPE" comments — rather than being deleted pre-decision.)
**Recommendation:** D17's open-pool approach (preserves the `:ref`/`:tags` the engine relies on); fold the
prototype's cross-bucket intent into it.

**D30 — There is no pre-existing "grant" in the project; "grant" earns its keep only as a thin compiler
to the existing primitives, not a parallel selection engine.** Verified (callers, not comments): the
original vocabulary is `mod5e/*` modifiers (fixed grants — D4), `:props`→`plugin-modifiers`→
`make-feat-modifiers` (declarative fixed mechanics, run for feats/races/subraces/classes/subclasses/
ancestries via `spell_subs.cljs:144/157/457/491/779`), and `selection-cfg` (choices, carrying load-bearing
`:ref`/`:tags`). "Grant" is branch-introduced. **Verdict (per D12):** the grant *idea* — a data declaration
that compiles to a `selection-cfg` over an OPEN pool, preserving `:ref`/`:tags` — is a **beneficial wrapper**
(thicker: adds openness + author-declarability + filtering that don't exist today). The `grant-selection`
*implementation as built* (generic `:tags #{:grant from}`, no `:ref`) is **parallel duplication** of
`selection-cfg`, worse (drops the metadata). Resolution: grant = thin compiler to the existing primitives;
the value is openness + O(1) authoring + deleting hardcoded vectors, NEVER a second selection engine.

**D31 — The two declarative vocabularies share an effect set (real duplication) but differ in
application mode (a REAL distinction) — factor out the shared effects, keep both modes.**
⚠️ **Corrected** (this entry first claimed "no useful distinction; B does no level-gating" — WRONG, see
the methodology note). Traced up+down:
- **Shared / duplicated:** the ~9 overlapping keys (weapon/skill/armor prof, resist/immunity, save-adv,
  fly/swim speed) compile to the *same* `mod5e/*` primitive in both `make-feat-modifiers` (A) and
  `level-modifier` (B) — verified down (both call e.g. `mod5e/damage-resistance`). The effect arms are
  reimplemented in two `case`s. That redundancy is real.
- **Distinct / load-bearing:** **application mode.** A (`:props`) is a **flat, unconditional** attribute
  map ("this content has X"). B (`:level-modifiers`) is a **level-gated list** — each entry carries a
  `:level`, and `make-levels` (`spell_subs.cljs:392`) `(group-by :level …)` places it at that class level
  ("gain X at level N"). B can express level progression; A cannot. The value-shape difference
  (A map-of-flags vs B single-value-with-`:level`) reflects this, it is not arbitrary.
- **Better target (revised):** ONE shared effect vocabulary (the type→`mod5e/*` arms, defined once) used
  by BOTH a flat path and a level-gated path — or generalize to "an effect + an optional level/condition."
  NOT "collapse to one compiler" (that was the wrong conclusion — it would lose the level-gating).
- *Methodology note (verification-discipline):* I read the leaf compile fn and asserted behavior without
  tracing the caller that supplies the gating. Reading the leaf is not reading the feature — trace up to
  the wrapper (here `make-levels`) and down to the primitive before concluding. The user caught this.

**Same smell, smaller scale: `:lizardfolk-ac`
and `:tortle-ac`** (`options.cljc:3332/3339`) are two bespoke natural-AC functions where one parameterized
`:natural-ac` prop arm (base / +dex with cap / +shield) should serve both and delete them. These are the
model of "better": a parameterized declarative handler that compiles to the existing engine and replaces
duplicates — not a parallel layer. (Correction logged: an earlier turn cited these AC fns as a *virtuous*
code-escape-hatch; they are duplication. The escape-hatch principle is real, but these aren't an instance of it.)

**D31 follow-up (verified, 2026-06-19): the two vocabularies are in DIFFERENT LAYERS, and the shared half
is now pinned by a test.** Vocab A (`make-feat-modifiers`/`plugin-modifiers`) is **cljc** (`options.cljc`);
vocab B (`level-modifier`/`make-levels`) is **cljs** (`spell_subs.cljs`). So unifying them isn't a
two-`case` merge — it's a cross-layer refactor (the shared effect vocabulary would need to live in cljc and
be consumed by both the cljc and the cljs assembly paths). Test coverage of the claim (per the standing
rule): `grant_vocabulary_characterization_test.clj` pins, under the JVM gate, that A's `:damage-resistance`
compiles to the *same* `mod5e/damage-resistance` modifier B uses (shared primitive) and that A's value
shape is a map-of-flags. B's arms call the same `mod5e/*` (source-verified, `spell_subs.cljs:177`), and the
level-gating *mechanism* is already pinned by `class-feature-snapshot-test` (fighter Indomitable @9, absent
@5). B's cljs assembly is now ALSO pinned — `grant_vocabulary_cljs_test.cljs` (run in the headless cljs
harness) shows `level-modifier` compiles `:damage-resistance` to the same `mod5e/*` modifier and
`make-levels` places a `:level 3` modifier at level 3 (level 1 at 1, nothing at 2). So both halves of D31
are test-backed across both layers; nothing here is prose-only anymore.

**D32 — UI dropdowns must round-trip their value TYPE; templated via `:typed?` (verified, 2026-06-23).**
An HTML `<select>` value is always a string, but `dropdown` (`views.cljs`) handed that raw string to
each caller's `:on-change`, so a caller must re-hydrate the type (`(keyword %)` / `(js/parseInt %)` /
`(js/parseFloat %)`). Forgetting is silent corruption invisible to source review and the JVM/harness
tests (those dispatch already-typed values). **This bug class has now bitten THIS branch twice**
(git-verified, not presumed): (1) the dragonborn breath weapon — fixed by the `:enum` field's
index-round-trip (`views.cljs:~6595`, commit `f32790b1`, 2026-06-15, **not** in the merge-base; its
comment records *"the bug that shipped a broken breath weapon"*); that fix lived **only in a code
comment**, so (2) the floating-ASI widget repeated it, persisting `:ability "cha"` / `:from "martial"`
(bare strings), which makes `compile-ability-increases` get a string and `(ability-groups "martial")`
return nil (empty choice list). Only the browser-driven `test/e2e/race-builder-asi.js` exercises this
layer; it is what surfaced #2. *Correction logged:* the existing ~70 coercing call sites are **not** a
bug list — most are upstream and correct; `views.cljs:5801` (spellcasting ability,
`(keyword "orcpub.dnd.e5.character" %)`, Larry 2017, in the merge-base) was wrongly called "fragile/the
next instance" from pattern-matching — it coerces correctly and has shipped for ~8 years; the only note
is the hardcoded namespace. **Decision:** lift the index-round-trip into the primitive — `dropdown`
gains `:typed?`; on-change then receives the selected item's original `:value` (any type, incl.
nil/qualified keyword), so callers do zero coercion. Default path is unchanged string passthrough
(backward compatible; existing sites untouched). The ASI widget is migrated and its manual lookup maps
deleted; the E2E still passes. (Numbers already have a typed input — `number-field`, an
`<input type=number>` that returns an int/nil — so this is a `<select>`-only problem.) Answers "can we
template these elements to prevent the mistake in general?" — yes — and clears the last gate before the
floating-ASI round-trip (layer 5). **Convergence follow-up (optional, post-decision, per D23):** migrate
coercing call sites to `:typed?` for readability and fold the bespoke `:enum` round-trip onto the
primitive — cleanup, not a fix. Full write-up: `docs/kb/dropdown-value-coercion.md`.

**D33 — Data export formats favor TERSE encodings + commented source over self-documenting data
(verified, 2026-06-24).** Self-documenting data (keyed maps, fully-namespaced keywords) is nice in
small doses but multiplies in an export format that ships in every content entry — dozens of races/
subraces in every orcbrew pack. Decision: prefer compact positional/short encodings in the data, and
put the "what does this mean" in source comments + a KB doc, not in the bytes. First application:
`:ability-increases` went from `[{:ability :orcpub.dnd.e5.character/cha :amount 2} {:select {:from
:martial :num 1 :amount 1 :different? true}}]` to `[[2 :cha] [1 :martial]]` — `[amount pool]` pairs
with short ability keywords namespaced at compile. Smaller than the prior maps and even than the
built-in `:abilities` map, with the format spec in `docs/kb/ability-increase-spreads.md` and concise
comments at `compile-ability-increases`/`ability-bag-assigner`. General rule for future content data:
terse bytes, documented meaning.
