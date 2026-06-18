# Roadmap: de-siloing content + mechanizing effects

The shape of the initiative, as dependency-ordered layers. Goal: make content extension
cross-silo and sustainable, and convert "just text" effects into real (sheet + roll) mechanics —
without parallel copies of logic (the original siloed code smell).

Status legend: **DONE** (committed this branch) · **FEASIBLE** (verified the substrate exists) ·
**DESIGN** (shape agreed, not built). Each layer is independently valuable and shippable.

## Foundation (gates every refactor)
- **F. Characterization / regression net** — DESIGN. Snapshot current behavior before touching
  source: the AC stack, every class's built features, spell-grant paths, and import/export
  round-trips (the 11-scenario test plan). Plus the **per-class feature catalogue** (each class's
  distinct features tagged by kind/scaling/already-shared). Pure reading + tests, zero regression
  risk. **Nothing structural starts until this exists.**

## Track A — Cross-silo grants (mostly started)
- **A1.** Generic `:grant {:from <pool> :choose N}` over the existing primitives — DONE for the
  feat→fighting-style slice; bucket-agnostic, proven on a built character.
- **A2.** Uniform spell-granting: route `:spells` (fixed) and `:spell-choice` to `spells-known` /
  `spell-selection` across every silo's assembly fn — FEASIBLE (primitives exist; feat lacks the
  key today). Wire the homebrew pools through `template-selections`; expose in builders.
- **A3.** Spell-slot progression as data — DESIGN. Decouple the overloaded `:level-factor` (it drives
  slot table + multiclass contribution + prepared count at once) into: a bucket of named/explicit slot
  tables (authored as an absolute per-level grid, presets as seeds), a separately-declared multiclass
  rule (`:full|:half|:third|:none|:separate`), and the prepared/known count. Unblocks Artificer and
  homebrew progressions; generalizes warlock pact magic (`:separate` + own pool/recharge, → B3). See
  spell-slot-progression.md.

## Track B — Mechanism layers (app-wide; each lifts text → mechanical)
- **B1. Structured/parameterized effect & feature records** — DESIGN. Effects/features as data
  records with named params + defaults (not prose). **Keystone** — editable references, the feature
  registry, and roll integration all depend on it.
- **B2. Conditions layer** — DESIGN. build-state (auto) + play-state (toggle), on the verified
  `equipped?`/deferred-modifier substrate. Powers Mariner-AC, rage-while-active, "+X while Y."
- **B3. Resource counters as data** — FEASIBLE. The counter exists (`actions-indicators` +
  `features-used` + `clear-period`); add a data path so a feature can declare uses/pool + period.
- **B4. Roll integration** — FEASIBLE. Wire structured effect dice/mods into the existing roller via
  `?attack-modifier-fns`/`?damage-bonus-fns`; fix the USER-REPORTED "stuck as text" rolls.

## Track C — Class features (the big structural nut; needs F + B1)
- **C1.** Per-class feature catalogue — DONE. All 12 base classes read and inventoried in
  `class-feature-catalogue.md`. Sizing confirmed (~3–6 each; monk/paladin ~10 outliers; sorcerer/wizard
  ~2–3). Surfaced the odd cases that re-shape B1/B3: multi-source use-counts, class-wide resource pools
  (ki/sorcery/Lay-on-Hands — their own mechanism), build-context summary interpolation, multi-part
  features (compile → seq of modifiers), and `?attr` interdependence. Start extraction on the clean
  classes; defer monk/paladin until the pool + build-context-fill layers exist.
- **C2. Feature registry + extraction** — DESIGN. Extract the ~3–6 distinct features per class into a
  keyed, filterable registry of structured records, parameterized by class-key; prove byte-identical
  output per step. Pools = filtered views over it. Scaling/padding stays as existing primitives.
- **C3. Custom-class builder surfaces** — DESIGN. Template-from-a-base-class (copy *references*, not
  definitions) + a filterable picker; editable references (`:overrides` merged onto defaults);
  alternate features (replace) and add-feature-to-class. Touches saved data only when a swap is
  chosen.

## Track D — Armor Class (parallel; needs an AC net)
- **D1. AC contribution-model refactor** — DESIGN. Replace the ad-hoc channels + hardcoded tie-break
  with one rule (max of base alternatives, each + its applicable bonuses), behind the AC snapshot.

## Top layer
- **E. Generated builder UI from declarations** — DESIGN. Let the registry generate form elements
  from grant/feature/condition declarations (the "one step further"). Needs the reusable controls
  built first; UI throttled while the data shape stays open.

## Recommended critical path
1. **F** — build the net (incl. the per-class catalogue). Gates everything; no risk.
2. **A2** + finish A1's wiring/builder exposure — one complete, shippable cross-silo feature for
   momentum and to exercise the full data→builder→character→round-trip path.
3. **B1** (structured records) — the keystone the rest of B and all of C ride on.
4. **B2–B4** (conditions, counters, roll integration) — app-wide wins, reusable beyond classes.
5. **C2 → C3** (feature registry, then builder surfaces) — on top of F + B1.
6. **D1** — in parallel once the AC snapshot exists.
7. **E** — last; convenience over a now-uniform substrate.

Dependencies to respect: nothing structural before **F**; **B1** before C and editable references;
roll integration (**B4**) needs structured records to have params to roll. Everything else can
parallelize.

## Supporting docs
decision-vocabulary · spell-granting-across-silos · declarative-grant-vocabulary ·
runtime-toggles-and-conditional-modifiers · armor-class-computation · class-features-and-mechanization
· content-extensibility-framework. Each carries its own VERIFIED/DESIGN/SPECULATION flags.
