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
- Variants (`_copy`/`_mod`), class-feature pool (`[:class-feature :X]`), declarative cross-type prereq
  vocabulary, mechanical-effects-for-text-only (boons/ki), level-gated grants in `:props`.

## Tracks — Phase 2 (the expansion, layered on Phase 1)
- **A. Cross-silo grants** = the Phase-1 pool/grant track. **Not new work** — defer to `direction.md`/
  `decisions.md`. The live decision is D17 (open pools behind existing selections, no generic wrapper).
  - **A2.** Uniform spell-granting (route `:spells`/`:spell-choice` to the primitives across silos) — FEASIBLE.
  - **A3.** Spell-slot progression as data (bucket of tables + declared multiclass rule) — DESIGN;
    unblocks Artificer + homebrew tables; pact → `:separate` + own pool (`spell-slot-progression.md`).
  - **A4. Parametric `select`-grant: USER-CHOICE floating ASI (any silo)** — DESIGN. The grant layer's
    other half: a grant that offers the *player* a choice, parametric over ability keys rather than a
    content pool. The engine primitive **already exists and is proven** — `ability-increase-selection-2`
    (`options.cljc:225`), used by built-in Variant Human (`spell_subs.cljs:759`) and Half-Elf (`:868`),
    but hardcoded there; no data path for homebrew. **Must handle FIXED + FLOATING combinations** (the
    real requirement): a race/feat/background declares a LIST of allotments, each either creator-fixed or
    user-chosen-from-a-set, composed together. Data shape:
    ```clojure
    :ability-increases
    [{:ability :cha :amount 2}                                   ; FIXED  -> race-ability modifier
     {:select {:from #{:str :dex :con} :num 1 :amount 1 :different? true}}] ; FLOATING -> ability-increase-selection-2
    ```
    e.g. "+2 CHA, +1 to any martial stat" = one fixed + one floating-from-`#{:str :dex :con}`. Named
    subsets ("martial") are predefined ability groups. Compiles: fixed → a modifier; each `:select` →
    `ability-increase-selection-2` over its `:from` set. Cross-silo: the same grant lets a feat/background/
    class hand out floating ASI too (reconcile with the feat-only `:ability-increases` path). Cheaper than
    the pool-grant work — the hard primitive is built; the gap is exposing it as authorable data + the silo
    hook + a builder form. **Good candidate for the first full-stack (engine→UI) vertical** (see below).
    **Progress:** layer 1 — `opt5e/compile-ability-increases` (fixed+floating, named subsets) proven on a
    built character (JVM, `ability-increase-grant-test`); layer 2 — race + subrace silo wiring
    (`spell_subs.cljs` plugin-races/subraces) proven through the real `::races5e/races` sub (cljs harness,
    `ability-increase-grant-cljs-test`); layer 3 — the authoring form (`race-ability-increase-choices` in
    views.cljs, fixed/floating rows via the generic `set-race-path-prop`) + proof that driving the REAL
    builder events authors a working floating-ASI race end to end (cljs harness). **Layers 1–4 done**
    (layer 4 — the player choice — already rendered, same as Variant Human). Remaining: feat-path
    reconciliation (feats have their own `:ability-increases` param, not via `:props`); round-trip (layer 5).
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
1. **`grant-selection` (`c1f54967`) vs D17 — logged as D29.** My Phase-2 bridge prototype is a *generic* grant compiler
   with generic `:tags #{:grant from}` and no `:ref`. Phase-1's D17 audit decided **against** a generic
   wrapper, in favor of pointing existing per-feature selections at open pools while preserving their
   `:ref`/`:tags`. These are two different approaches to the same goal. **Recommendation:** treat D17 as
   the standing decision; fold the prototype's intent (cross-bucket reuse) into the open-pool-behind-
   existing-selections approach, or consciously overturn D17 — but pick one. Until then, don't build more
   on `grant-selection`.

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
1. **Resolve conflict #1** (grant approach) — it gates all further grant work.
2. **A2** + Phase-1's grant-authoring UI lever — one complete cross-silo feature, exercising the full
   data→builder→character→round-trip path.
3. **B1** (structured records) — keystone for the rest of B and all of C.
4. **B2–B4** — app-wide wins (conditions, counters incl. resource pools, roll integration).
5. **C2 → C3** — feature registry then builder surfaces (on F + B1).
6. **A3** (spell-slot bucket) — unblocks Artificer-shaped classes; **D1** in parallel once the AC net is full.
7. **E** — last.
