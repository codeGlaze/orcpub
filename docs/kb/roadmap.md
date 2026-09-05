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
  fighting-style e2e (`8c8c0b10`/`a4c34f3c`). Conflict #1 later RESOLVED by D29/D30: it is the thin compiler, not a generic wrapper.
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

### BUILT — AC engine + authored mechanics (this branch, 2026-09-04/05) — `armor-class-refactor.md`
- **AC engine refactored end to end**: `mod5e/ac-formula` gives the dead `?ac-fns` channel a constructor;
  shield + character magic moved into `?ac-bonus-fns`; Barbarian/Monk/Draconic onto `ac-formula`; 18 → 10
  attributes; engine extracted to `orcpub.dnd.e5.armor-class` (reversing the D34 delete call); `:lizardfolk-ac`
  / `:tortle-ac` compile to the universal shape; **parity sweep pinned at 0** across 4 pairs × 7 equipment
  states. The bucketed outer loop was measured and rejected (slower below ~8 armors; the sub is memoized).
  **Track D1 is essentially delivered.**
- **Universal authored AC**: `{:ac {:ac 13 :abilities [:dex] :armor? … :shield? …}}`, `{:ac-bonus …}`,
  `{:armor-gives-no-ac true}`; three-state tags; `{:armor-dex-cap {:medium 3}}` generalises Medium Armor Master.
- **Shared `:props` fragments** — `ac-bonus-fields` / `attack-bonus-fields` / `damage-bonus-fields`
  (`builder_fields.cljc`) + the weapon predicate `weapons/matches?` over every real weapon flag. First
  **cross-type** field data — before this every schema was per-type. 3 of 14 published fighting styles
  authorable (Defense, Archery, Thrown Weapon); the measured gap is `fighting-style-vocabulary-gap.md`.
- **Fighting-style builder (Phase B)** — registry entry (cleared the long-red audit test → **0 failures**),
  page, nav, and the page-map now **generated by a compile-time macro** (`page-map/builder-pages`) —
  reversing direction.md's "irreducible" call. Round-trip proven in the real app
  (`homebrew_roundtrip_e2e.js`, 14/14): author → save → export `.orcbrew` → re-import, props intact.
- **Two shipped bugs fixed**: Bracers of Defense lost on natural armor (15 vs RAW 17) — fixed on
  `integration` (`0b4d499a`) and pulled down; every `:number` builder field threw `1 is not ISeqable`
  (draconic ancestry affected) — found only by driving the real UI.
- **Self-hosted Open Sans**, CSP tightened to `'self'` for fonts/styles.

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
- ✅ **Homebrew fighting styles are selectable by the class that grants them** (2026-09-05). The
  threading decided in `fighting-style-authoring.md` is done: all four class call sites pass the pool,
  `eligible-homebrew-styles` applies the `:classes` rule, the `:ref` survives.
  `test/e2e/imported-style-usable.js` flipped from pinning the gap to proving the chain — import,
  Fighter, offered (and a `:classes #{:paladin}` style withheld), picked, **AC 12 → 13 on screen**.
  - ✅ and the authoring half: a `:multi-enum` field type (set-valued, checkboxes, elementwise
    validation) and the "Classes that may take this style" control. `test/e2e/fighting-style-builder.js`
    proves a Paladin-only style is authorable in the form and stores `:classes #{:paladin}`.
- **Grant-authoring UI** — the biggest remaining lever. Now has a concrete substrate: see **Track E**.
- 🔴 **`:required-when`** conditional field validation (HIGH — flagged in `builder_fields.cljc`).
- **Grant matrix** — feat silo proven; background / race / subrace / class / subclass remain (each a
  one-line `:grant` hook + a build test).
- **Vocabulary gaps measured against published content** (`fighting-style-vocabulary-gap.md`): 3 of 14
  styles authorable; engine hooks exist for 8. Next: `:reaction` / `:trait` (sheet entries — Protection,
  Interception, and every text-only homebrew feature; this IS the "mechanical effects for text-only
  content" pin), blindsight, cantrip grants. Dueling / Two-Weapon need the weapon-bonus fn signature
  widened (no wielding context today). Great Weapon Fighting needs engine work (no damage-die hook).
- 🟡 **A real "can't wear armor" restriction.** `:armor-gives-no-ac` is an AC *computation*; the rules
  restriction is unbuilt. Build it as an **equipment-selection constraint**, not a computation — the mug
  override (`homebrew-override.md`) waives selection rules per selection and never touches computed
  values, so a selection constraint is player-overridable for free and a computation is not.
- Variants (`_copy`/`_mod`), class-feature pool, declarative cross-type prereq vocabulary, level-gated
  `:props`.
- 🔵 **FUTURE (cross-branch) — a rules-override layer**: DM-issued a-la-carte grants and permissions
  (extra feat, party-wide feat, "a tortle can wear armor", size Large). A ledger, not content; should
  ride the shared `:props` vocabulary. "Boon" is unavailable (real rules construct + `homebrew-boon`
  already ships). `rules-override-layer.md`.

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
- **D. Armor Class** — ✅ **D1 essentially delivered** (`armor-class-refactor.md`, this branch). Remaining
  there is vocabulary, not engine: `:reaction`/`:trait`, the wielding-context signature, damage-die hook.
- **E. Generated builder UI from declarations.** Was sequenced last; **pulled forward 2026-09-05** because
  its one missing primitive is also the grant-authoring UI's substrate (a Phase-1 lever). Plan:
  `builder-form-schemas.md` §6. In brief —
  - **E0** — ✅ the KB audit (commit `492e2c32`).
  - **E1 — Tier 1 example** — ✅ **done 2026-09-05.** `language-builder` and its single-caller
    `language-input-field` deleted (−21 lines) for the one-line `simple-content-builder` call.
    **The recipe is now proven, and it is the recipe for the other 14:** write the pin against the
    *existing* form (`test/e2e/language-builder.js`, driving `lein e2e-server`), run it green, swap,
    run it green again — 12/12 both times, same `:plugins` map to the character. The pin asserts only
    what a user can observe, so it is indifferent to how the form is built.
  - **E2 — Tier 2 example done RIGHT = fighting styles**: (a) ✅ **done 2026-09-05** — the class path is
    threaded and the `:classes` control authorable, so a style can be written, restricted, exported,
    imported and *used* (`fighting-style-authoring.md` "Status"); (b) replace the 19-field flat form with a **`:rows` node** — "add an effect",
    each row a titled group (AC / attack / damage / reaction); (c) rendered-UI E2E in **`test/e2e/`**
    following `race-builder-asi.js`. Acceptance: Defense, Archery and Thrown Weapon authored in the UI,
    picked by a Fighter, correct number on the sheet.
  - **E3 — Tier 3 stab**: the same `:rows` node over encounter's creatures (replacing the
    `creature-selector` loop), then background traits (`option-traits`, a 6-event signature). If one node
    kind serves all three, it has earned its place (the one principle).
  - **E4 — Grant-authoring UI**: `:rows` where each row is a grant `{:from <pool> :choose n}` iterating the
    **registered pools** — D21's falsifiable gate: exposing a second pool must be a ~1-line registration,
    shown in a commit.
  - **E5 — Convert the remaining builders**, cheapest first, each behavior-pinned: background 46 · item 53
    · feat 62 · spell 85 · subclass 105 · subrace 129 · race 152 · monster 233 · **class 268 last** (the
    only one with real conditionals + bespoke selectors — it tells us what the escape hatch needs).
  Sequencing caveat honoured: "flat pools before rich pools" — E4 iterates flat pools only; the
  class-feature pool stays a pin.

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
- **Builders + authored mechanics (this branch):** `builder-form-schemas.md` (tiers, node vocabulary,
  the `:rows` design, Track E plan) · `fighting-style-authoring.md` (the class-path DECISION) ·
  `fighting-style-vocabulary-gap.md` (measured against 14 published styles) · `armor-class-refactor.md`
  (current AC model + ledger; `armor-class-computation.md` is its historical predecessor) ·
  `weapon-data-model.md` · `homebrew-override.md` (the mug) · `rules-override-layer.md` (proposal).
- **Topic detail:** decision-vocabulary · homebrew-content-merge · spell-granting-across-silos ·
  spell-slot-progression · declarative-grant-vocabulary · class-features-and-mechanization ·
  class-feature-catalogue · building-a-class-from-builders · runtime-toggles-and-conditional-modifiers ·
  built-character-representation · character-validation · ability-increase-spreads ·
  dropdown-value-coercion · starting-equipment · custom-content-lifecycle · content-tiers-and-key-resolution ·
  key-collision-behavior · library-management-and-conflicts · keyword-trap-name-repair ·
  orcbrew-format-versioning · demo-content-tier.
- **Process:** verification-discipline · documentation-discipline · data-safety-layers · backfill-ledger ·
  cljs-headless-harness · test-suite-state · fonts.
- *(A `datomic-crash-analysis.md` used to be listed here; the file does not exist on this branch.)*

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
