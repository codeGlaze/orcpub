# Branch plan — the single source (START HERE)

One reconciled plan for branch `claude/zen-wright-04xhdz`. It replaces the previous split between
this file and `content-extensibility-direction.md` as competing "start here" docs. The branch ran in
loops because decisions lived in two parallel trackers; this is the one that supersedes both for
*navigation and status*. The detail/decision docs below remain authoritative for their own track.

## The arc (two phases — both real, one branch)
- **Phase 1 — Content extensibility (pool + grant).** The founding purpose: replace bespoke positional
  cross-type wiring with one open **pool + grant** authoring layer (stability and flexibility are the
  same abstraction). **Substantially built.** Canonical detail: `content-extensibility-direction.md`
  (v2, "read this for the content track"); decisions: `content-extensibility-decisions.md` (the
  `D`-numbers); how-to: `content-extensibility-framework.md`; branch history/handoff: `BRANCH.md`.
- **Phase 2 — Mechanization, class features, spell slots, AC.** The later expansion (this session):
  make content cross-silo *and* turn "just text" effects into real sheet/roll mechanics, including the
  class-feature registry and the spellcasting-progression rework. Mostly design + a foundation net.

## Status ledger (anchored to commits; detail in the linked docs)

### BUILT — Phase 1 (verified by git + BRANCH.md)
- `content_types` registry + passthrough-subs loop; `register-homebrew-content!` wiring layer (`3980ea1b`).
- **First pool+grant slice on real mechanics**: open draconic-ancestry pool, dragonborn grants from it,
  homebrew ancestry inherits full mechanics (`acaa131d`); richer ancestries via `:props` (`026f8707`).
- `simple-content-builder` — builder forms are data (`109b5dd0`); draconic-ancestry builder end-to-end,
  author→pool→export→import→round-trip (`0aca6113`).
- Registry **drives** events (`d2e002b4`), db (`af68061d`), routes (`506c32b3`/`c5e9aea6`/`58c4de47`).
- Declarative builder **field-schema** → save-spec + `:required?` form + import/export sync + strict mode
  (`f32790b1`…`e4614519`).

### BUILT — Phase 2 (this session)
- Cross-bucket grant **bridge prototype** `grant-selection` (`c1f54967`, `options.cljc:3447`) + feat→
  fighting-style e2e (`8c8c0b10`/`a4c34f3c`). **⚠️ see Flagged conflict #1 — this may diverge from D17.**
- **Grant-matrix proof (in progress) — the 4 modes built + the feat silo proven end-to-end.**
  `grant-selection` evolved to 4 modes: ALL (`{:from p}`), FILTERED (`:filter #{…}`), SPECIFIC (`:key :k`),
  CUSTOM (homebrew entry in the pool). `fighting-style-grant-matrix-test` proves all 4 at compile level
  AND end-to-end on the feat silo (a built character gets the right style's mechanic). **Verified finding:**
  a NESTED grant must carry NO top-level `:ref` (adding one zeroed a feat-granted style's mechanic — the
  test caught it); top-level grants (a class's own fighting style) carry a `:ref` via their own constructor.
  **Remaining for the 6×4 matrix (next increment):** the other 5 silos (background, race, subrace, built-in
  class, custom class, subclass) — each is the same one-line `:grant` hook + a build test; the compiler is
  silo-agnostic (already proven bucket-agnostic).
- AC characterization test + model doc (`7b7f3b3a`/`68f42e77`, `armor-class-computation.md`).
- **Class-feature regression net** — all **12** classes baselined + fighter/rogue detail
  (`cc7cd171`…`b258639e`, `class_feature_snapshot_test.clj`).
- **`compile-feature` proof** — data spec reproduces real fighter/rogue output; `:die`/`:uses` overrides
  (`0618ab8c`/`8c65f420`).
- **Per-class catalogue** (C1, all 12) + **spell-slot-progression** analysis (`b8920f4f`/`355f8cee`,
  `class-feature-catalogue.md`, `spell-slot-progression.md`).
- **Spell-slot computation** characterized (`spell_slot_characterization_test`): full/half/pact singles,
  two-caster pooling, warlock-not-pooling — confirmed the analysis against the real build.
- **Grant vocabularies** characterized across both layers: A (cljc) `grant_vocabulary_characterization_test`,
  B (cljs harness) `grant_vocabulary_cljs_test` — shared `mod5e/*` primitive + B is level-gated (D31).
- **AC stack** deepened (`ac_characterization_test`): armored dex-cap-by-type + shield, two-unarmored-defense
  tie-break, tortle/lizardfolk natural-AC duplication; surfaced the `:max-dex-mod`-ignored + cljs-nil-add findings.
- **.orcbrew round-trip** (`orcbrew_round_trip_test`): EDN export→import is lossless (real fixture + rich class).

> **Round-trip coverage map** (so it isn't re-discovered): EDN serialization fidelity →
> `orcbrew_round_trip_test` (JVM, new); validate-import parse + clean-pack-unchanged → `import-validation-test`
> (`test-import-all-or-nothing-valid` asserts `= plugin (:data result)`); auto-clean transforms →
> same; key-conflict reconciliation → `content-reconciliation-test`; build-after-import (homebrew → character)
> → `draconic-ancestry-test`/`dragonborn-ancestry-e2e-test`; character strict round-trip + key survival →
> `extensibility-golden-test`. **Foundation (F) is now essentially complete** — the load-bearing computations
> and the round-trip are test-backed across both layers.
>
> **Import internals (authoritative; live on `agents/develop`, not this fork line):**
> `docs/ORCBREW_IMPORT_DEEP_DIVE.md` (two-phase cleaning, nil semantics — semantic nils preserved, numeric
> removed) and `docs/kb/error-handling-import-validation.md` (progressive recovery: invalid items skip +
> log; reconciliation; export pre-validation). KEY REFINEMENT to the round-trip: it's lossless only for
> CLEAN EDN; the import *intentionally transforms* malformed/partially-invalid input (cleaning is a
> feature). Surface these when split-committing docs to `agents/develop`.

### BUILT — Content-library management (parallel branch `feature/content-library-management`, PR #30)
Split off from this branch because it applies to **both** `develop` and this refactor line (it needs no
feat-pool rewrites). The My Content homebrew manager: duplicate-key **conflict resolution** (opinionated
summary-first + advanced per-conflict panel), the four-level **disable hierarchy**
(global/source/section/item, the two local levels in an overlay store — never in `.orcbrew` data),
**move/copy** between sources (clobber-free key policy), and a passive **health-status** card (one line
per problem *type*, warning-yellow for attention / red for broken; always-on on the hub, dismissable
elsewhere). Also a Skip-conflict bugfix and a nil-route handler fix. 230 CLJS tests; e2e-verified.
Detail: `docs/kb/library-management-and-conflicts.md`. **In review against `develop` (PR #30);** to be
integrated into this refactor branch (timing under discussion — cleanest once PR #30 lands on `develop`,
then merge `develop` in, since the shared files here have diverged for the feat work).

### DECIDED (design settled; don't re-litigate)
> Canonical log: `content-extensibility-decisions.md` — D1–D22 (content track) and **D23–D29**
> (this expansion: prototype-then-converge governance, the class-feature registry, `compile-feature`,
> the catalogue, the spell-slot bucket, non-SRD/synthetic-validation, and the open grant question).
- **Pool + grant is the spine** (`direction.md`). Abstraction earns its keep only if thicker than what
  it hides + intent-revealing (no cryptic DSL). Maintainability **gate**: register a pool once → grantable
  in every builder (O(1) to expose; D21).
- **D17 — audit what a new piece REPLACES before building.** Specifically: **do not build a generic
  `grant` wrapper**; point the existing per-feature `selection-cfg` constructors at **open pools**,
  preserving their load-bearing `:ref`/`:tags`. `content_pools/pool` exists; **no grant compiler yet** —
  the draconic grant is hand-wired and is the thing to generalize carefully.
- Stable keys, never display names (D10); pools are memoized derived subs, never recomputed (D11);
  variants get one `resolved-content` seam now, built later (D-pins).
- **Class features**: one keyed/filterable registry, pools = filtered views; reference = key + `:overrides`;
  features are macro-captured **code**, so a `compile-feature` step is required; summary = fields + a fill
  template, not interpolation. (`class-features-and-mechanization.md`.)
- **Spell slots**: replace the overloaded `:level-factor` with a **bucket of named/explicit slot tables**
  (authored as an absolute per-level grid, presets as seeds) + a **separately declared multiclass rule**
  + its own prepared-count; `:separate` (pact) schedules own their pool + recharge.
  (`spell-slot-progression.md`.)

### OPEN — Phase 1 levers & pins (from `direction.md`)
- **Grant-authoring UI** — the biggest remaining lever (declare "grant a choice from pool X" in a builder).
- 🔴 **`:required-when` conditional field validation** (HIGH — flagged in `builder_fields.cljc`).
- ✅ **Fighting styles: registry drift** — FIXED 2026-09-04. The `content-types` descriptor now
  exists, so `lein test` is at **0 failures** (it had been at 1 for the whole refactor). One entry
  generated the builder-item sub, the events wiring, the db draft slot, the bidi route, the
  my-content nav entry and the SPA allowlist path; `route_map` gained
  `dnd-e5-fighting-style-builder-page-route` for the drift guard, and both the `content_types_test`
  counts and the separate hand-maintained keyword-audit table in `e5_test` were updated.
- 🔴 **Fighting-style BUILDER PAGE** (HIGH — the remaining half). Everything above is data-driven,
  but the route-to-view binding in `views_2.cljc` is hand-wired by design: a view fn can't be
  derived from data in cljs (D-note in the framework doc). Until that lands, the route resolves and
  the type saves, but there is no page to author one on.
- Variants (`_copy`/`_mod`), class-feature pool (`[:class-feature :X]`), declarative cross-type prereq
  vocabulary, mechanical-effects-for-text-only (boons/ki), level-gated grants in `:props`.

## Tracks — Phase 2 (the expansion, layered on Phase 1)
- **A. Cross-silo grants** = the Phase-1 pool/grant track. **Not new work** — defer to `direction.md`/
  `decisions.md`. The live decision is D17 (open pools behind existing selections, no generic wrapper).
  - **A2.** Uniform spell-granting (route `:spells`/`:spell-choice` to the primitives across silos) — FEASIBLE.
  - **A3.** Spell-slot progression as data (bucket of tables + declared multiclass rule) — DESIGN;
    unblocks Artificer + homebrew tables; pact → `:separate` + own pool (`spell-slot-progression.md`).
  - **A4. Parametric `select`-grant: USER-CHOICE floating ASI (any silo)** — ✅ DONE (terse spread).
    A race/subrace declares `:ability-increases` as a list of `[amount pool]` pairs; the whole list is
    one spread (all increments land on different abilities). Pool = `:any`/`:martial`/`:mental` | a set
    `#{:wis :con}` | a single stat `:con` (= fixed). Data shape:
    ```clojure
    :ability-increases [[2 :cha] [1 :martial]]   ; +2 CHA (fixed), +1 to any martial stat (player choice)
    ```
    Single-stat → fixed `race-ability` modifier; multi-stat → a player-chosen slot in one `:asi`
    selection. Full spec: `docs/kb/ability-increase-spreads.md`.
    Built bottom-up and test-backed at every layer: JVM (compile + apply), cljs harness (the
    `::races5e/races` sub + orcbrew round-trip), and three rendered-UI E2Es (`exact-spread-asi`,
    `race-builder-asi`, `export-import-use`) covering render + pool-restriction + distinctness, authoring,
    and the full export→import→use round-trip. Lessons live in their own docs: the `<select>` string
    footgun → `:typed?` dropdown (D32, `dropdown-value-coercion.md`); "data in a sub ≠ rendered in the
    builder" (the builder couples ability-increase widgets to `:asi`); terse export data (D33).
    Backward-compat: races never had `:ability-increases`, so released data is unaffected.
    Silos wired: race, subrace, **background**, **subclass**, **feat** (all rendered-UI-proven —
    `background-asi.js`, `subclass-asi-toggle.js`); the authoring widget (`ability-increase-choices`) is
    silo-generic. Subclass ASI (non-standard) is authored behind a reusable `optional-builder-section`
    toggle (collapsed by default → keeps builders uncluttered; the pattern for other opt-in fields).
    Classes keep their own `:ability-increase-levels` mechanism. Multi-silo containment (a race + a
    background each granting an ASI stay bound to their own source and stack) is proven in
    `multi-container-asi.js` + `multi-container-roundtrip.js` (export→clear→import→use).
    **Feat-path reconciliation — DONE:** `feat-option-from-cfg` reads `:ability-increases` by shape — a
    set is the legacy feat format (`#{:str :con}` + `:saves?`, untouched), a vector is the spread. The
    one feature the spread can't model is the feat `:saves?` coupling — handled by the save tools below.
    **Save proficiencies — DONE:** an opt-in `:save` rider on a spread increment (`[1 :martial :save]`,
    the save rides the chosen ability) + a standalone `:save-proficiencies [[count pool]]` tool (saves
    independent of any bump); both compile to one `modifiers/saving-throws` primitive, merged into every
    silo via `compile-ability-grants`. Rendered-proven (`save-grants-authoring.js`, `save-grants-use.js`).
    Same-stat overlap collapses to one proficiency (set semantics — no double bonus). **Remaining
    (optional):** "choose between spreads"; explicit-set authoring; migrating the legacy feat `:saves?`
    set onto the rider (backfill-ledger).
- **B. Mechanism layers** (lift text → mechanical): **B1** structured/parameterized effect & feature
  records (keystone — `compile-feature` is the proven start); **B2** conditions (build-state auto /
  play-state toggle, on the verified `equipped?` substrate); **B3** resource counters as data (incl. the
  ability-derived/pool counts the catalogue found — ki/sorcery/Lay-on-Hands); **B4** roll integration.
- **C. Class features** (needs B1): **C1** catalogue — DONE (all 12). **C2** registry + extraction (start
  on clean classes: fighter/rogue, then bard/cleric/wizard; defer monk/paladin/druid; warlock ≈ none).
  **C3** custom-class builder surfaces (template-from-base + filterable picker; `:overrides`).
- **D. Armor Class** (needs the AC net, started): **D1** contribution-model refactor.
- **E. Generated builder UI from declarations** — last; rides a now-uniform substrate.

## Flagged conflicts (need a call — do not silently resolve)
1. **D29 — DECIDED (no longer a conflict).** The real question was bespoke built-ins vs the systematic
   pool/grant approach. Resolution: **no duplicated functionality — one mechanism per job.** Pool/grant is
   the standard for new / homebrew / cross-silo capability; stable bespoke constructors stay where they
   aren't cross-silo and aren't hurting, and migrate only opportunistically (never churn proven code, never
   add a pool/grant that duplicates a working bespoke path without replacing it). Full text: D29 in
   `content-extensibility-decisions.md`.

## Doc map (so there's one place to look)
- **Plan/status (this file).** Branch history/handoff: `BRANCH.md`.
- **Content track** (canonical): `content-extensibility-direction.md` (v2) · `-decisions.md` (D-log) ·
  `-framework.md` (how-to) · `-compatibility.md` · `-e2e.md` · `registry-before-after.md`.
- **History (superseded — read as "what was tried"):** `content-extensibility.md`, `-plan.md`.
- **Topic detail:** decision-vocabulary · homebrew-content-merge · spell-granting-across-silos ·
  spell-slot-progression · declarative-grant-vocabulary · class-features-and-mechanization ·
  class-feature-catalogue · building-a-class-from-builders · armor-class-computation ·
  runtime-toggles-and-conditional-modifiers · built-character-representation · character-validation ·
  cljs-headless-harness · test-suite-state · verification-discipline · datomic-crash-analysis.

## Critical path
> Conflict #1 (grant approach) is **RESOLVED (D29)** — one mechanism per job, open pools behind
> existing selections, no generic wrapper. It no longer gates anything; grant work is unblocked.
1. **A2** + Phase-1's grant-authoring UI lever — one complete cross-silo feature, exercising the full
   data→builder→character→round-trip path. (The proven, smaller sub-step: finish the grant-matrix by
   wiring the next silo — background/race/subrace/class/subclass — each a one-line `:grant` hook + a
   build test, extending the feat silo already proven end-to-end.)
2. **B1** (structured records) — keystone for the rest of B and all of C.
3. **B2–B4** — app-wide wins (conditions, counters incl. resource pools, roll integration).
4. **C2 → C3** — feature registry then builder surfaces (on F + B1).
5. **A3** (spell-slot bucket) — unblocks Artificer-shaped classes; **D1** in parallel once the AC net is full.
6. **E** — last.

### 🟡 MEDIUM — a real "can't wear armor" restriction

`:armor-gives-no-ac` (AC refactor) covers one consequence of the rule: worn armor stops counting
toward AC. It is named for exactly that and claims nothing more. The rule itself — *"a tortle can't
wear light, medium, or heavy armor"* — is not modelled. Today a flagged character can equip plate,
shows it equipped, and takes its stealth disadvantage (`?armor-stealth-disadvantage?`,
template_base.cljc:49) while getting none of its AC.

Needed:

- an authorable restriction distinct from the AC suppression, selectable when building a species
- build it as an **equipment-selection constraint, not a computed AC rule**. The mug icon
  (`homebrew-override.md`) waives selection rules per selection but never touches computed values,
  so a selection constraint is player-overridable for free — which is exactly the DM-override case.
  `:armor-gives-no-ac` is a computation and therefore cannot be overridden from the builder at all.
- whichever is chosen, the other armor-derived effects must agree with it rather than splitting
  the way they do now

`:tortle-ac` becomes that restriction plus the flat calculation once it exists; until then it is
the flat calculation plus `:armor-gives-no-ac`, which reproduces the shipped AC exactly.

### ✅ DONE — extract `orcpub.dnd.e5.armor-class` and wire it

Revises the earlier decision to DELETE that namespace under D34. That call assumed AC stays in
`template_base`; breaking up monoliths says otherwise, and AC is the best-understood candidate now
that it is characterized with the parity sweep at 0.

`template_base` keeps thin `?`-attribute declarations delegating into the namespace — the
declarations must stay, since `?`-attributes are entity-spec macros valid only inside
`es/make-entity`. Precedent in the same file: `?dual-wield-weapon? weapon5e/light-melee-weapon?`.

Expected to also remove `?armor-ac-suppressed?`, by making worn armor a formula like any other:
"worn armor gives no AC" becomes *register no armor formula*. That needs one list, not the
`:armor-formula` / `:other-formulas` grouping, whose only purpose is `best-ac` bucketing and which
carries a documented footgun (a misgrouped formula returns a wrong number or throws).

Not a line-count win — `template_base.cljc` is 339 lines, AC ~60. The monolith is `options.cljc`
(3938 lines), which is a separate and larger job.

### 🔵 FUTURE (cross-branch) — a rules-override layer

DM-issued a-la-carte grants and permissions above the content silos: an extra feat at an arbitrary
point, a feat for the whole party at level 1, "a tortle can wear armor", "this character is size
Large". A ledger, not content — and not expressible as feats. **"Boon" is unavailable**: epic and DM
boons are real 5e constructs and the app already ships `homebrew-boon`. Full writeup, naming
candidates and design constraints in `rules-override-layer.md`. Survives branches — do not drop it
when this refactor merges.

### 🔴 HIGH — port the Bracers/natural-armor fix to `integration`

Shipped defect: a character with natural armor and Bracers of Defense loses the bracers' +2
entirely (AC 15 where RAW is 17). The tie-break in `?unarmored-armor-class` zeroes the whole
`?unarmored-ac-bonus` channel when natural armor wins, and the flat bonus is sitting in that
channel.

Fixed on the refactor branch and **verified to work standalone against `origin/integration`'s own
AC engine**. One function, `modifiers.cljc:561-562`, one caller, no engine change — the exact diff
is in `armor-class-refactor.md`. Worth cherry-picking rather than waiting for the refactor to land.
