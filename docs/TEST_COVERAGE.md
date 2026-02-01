# Test Coverage Analysis

> **Date:** 2026-02-01
> **Branch base:** `agents/develop` @ `c736b1f`
> **Purpose:** Audit the existing test suite, identify gaps, assess quality, and propose improvements.

---

## Executive Summary

OrcPub has **17 test files** covering **~2,100 lines** of test code against a codebase of **99 source files / ~62,000 lines**. Coverage is concentrated on the **core entity system** (entity building, strict conversions, round-trips) and **backend route handlers**, while the **D&D 5e game logic**, **frontend code**, and several **backend services** have little to no test coverage.

Two test files are effectively empty (stubs with no test definitions), and two tests are commented out with `#_` reader macros. The test infrastructure uses `clojure.test`, `clojure.spec`, and `clojure.test.check` (property-based testing), which is a solid foundation.

### Key Numbers

| Metric | Value |
|--------|-------|
| Source files | 99 |
| Source lines (approx.) | 62,200 |
| Test files | 17 |
| Test lines (approx.) | 2,100 |
| **Effective** test files (with actual tests) | 13 |
| Empty/stub test files | 2 (`dice_test`, `modifiers_test`) |
| Commented-out tests | 2 (`warlock_test`, `security_test`) |
| Test-to-source line ratio | ~3.4% |
| Source namespaces with any tests | 13 of 99 (~13%) |

---

## Existing Test Inventory

### Backend Tests (`test/clj/`)

| File | Source Namespace | Tests | Quality | Notes |
|------|-----------------|-------|---------|-------|
| `dnd/e5_test.clj` | `orcpub.dnd.e5` | 2 | Good | Plugin spec validation and merge behavior |
| `entity_spec_test.clj` | `orcpub.entity-spec` | 2 | Good | Entity building with modifier application |
| `pdf_test.clj` | `orcpub.pdf` | 1 | Shallow | Only tests font loading; no PDF generation tests |
| `routes_test.clj` | `orcpub.routes` | 7 | **Strong** | Full save/update/ownership/orphan handling with Datomic mock |
| `security_test.clj` | `orcpub.security` | 4 (+1 commented) | Good | Brute-force detection, multi-account access, attempt cleanup |

### Shared Tests (`test/cljc/`)

| File | Source Namespace | Tests | Quality | Notes |
|------|-----------------|-------|---------|-------|
| `common_test.clj` | `orcpub.common` | 1 | Minimal | Only tests `add-namespaces-to-keys` |
| `dice_test.clj` | `orcpub.dice` | **0** | **Stub** | Requires namespace but defines no tests |
| `dnd/e5/character_test.clj` | `orcpub.dnd.e5.character` | 6 | Good | Equipment conversion, ability namespacing, strict round-trips |
| `dnd/e5/character_test.cljc` | `orcpub.dnd.e5.character` | 2 | Good | Property-based spec testing, ability roll instrumentation |
| `dnd/e5/event_handlers_test.clj` | `orcpub.dnd.e5.event-handlers` | 9 | **Strong** | Level set/add/remove, class switching, inventory round-trips |
| `dnd/e5/magic_items_test.clj` | `orcpub.dnd.e5.magic-items` | 3 | **Strong** | to/from internal item, armor expansion, edge cases |
| `dnd/e5/modifiers_test.clj` | `orcpub.dnd.e5.modifiers` | **0** | **Stub** | Requires namespace but defines no tests |
| `dnd/e5/options_test.clj` | `orcpub.dnd.e5.options` | 1 | Minimal | Only tests `total-slots` |
| `dnd/e5/warlock_test.clj` | `orcpub.dnd.e5.character` | **0 active** | **Commented** | Elaborate test data defined, but `book-of-ancient-secrets` test is `#_`-ed out |
| `entity/strict_test.clj` | `orcpub.entity.strict` | 2 | Good | Duplicate selection detection, spec validation |
| `entity_test.clj` | `orcpub.entity` | 12 | **Strong** | to-strict/from-strict, round-trips (5 variants), path mapping, homebrew, empty field removal |
| `template_test.clj` | `orcpub.template` | 1 | Good | Modifier map construction at 1 and 2 levels |

---

## Coverage Gap Analysis

### Tier 1: Critical Gaps (High-value, testable logic with no coverage)

These are core logic namespaces with significant computation that would benefit most from tests.

| Source File | Lines | Why It Matters |
|-------------|-------|----------------|
| `src/cljc/orcpub/dnd/e5/classes.cljc` | 3,144 | All 12 PHB class definitions, subclass features, spellcasting tables. Bugs here silently produce wrong character sheets. |
| `src/cljc/orcpub/dnd/e5/options.cljc` | 3,428 | Core character option builder. Only `total-slots` is tested; race/class/feat option construction, multiclass spell slots, saving throws are all untested. |
| `src/cljc/orcpub/dnd/e5/modifiers.cljc` | 657 | The entire D&D modifier system (ability scores, AC, saves, skills, resistances, etc.). Test file exists but is **empty**. |
| `src/cljc/orcpub/dnd/e5/template.cljc` | 1,552 | Template construction for the character builder. No tests despite being central to how characters are built. |
| `src/cljc/orcpub/dnd/e5/character.cljc` | 866 | Partially covered (strict round-trips, equipment). Missing: ability calculations, AC computation, HP calculation, spell slot derivation, multiclass logic. |
| `src/cljc/orcpub/dice.cljc` | 82 | Dice parsing, rolling, mean calculation. Test file exists but is **empty**. Pure functions, trivially testable. |
| `src/cljc/orcpub/registration.cljc` | 76 | Email/username/password validation. Pure validation functions, easy to test, security-relevant. |
| `src/cljc/orcpub/modifiers.cljc` | 124 | Core modifier application engine. Partially tested via `entity_spec_test`, but the namespace itself (topological sort, dependency resolution) has no direct tests. |

### Tier 2: Important Gaps (Backend services, partial coverage)

| Source File | Lines | Why It Matters |
|-------------|-------|----------------|
| `src/clj/orcpub/email.clj` | 95 | Email sending for verification and password reset. No tests. Errors here silently break user onboarding. |
| `src/clj/orcpub/routes/party.clj` | 61 | Party CRUD handlers. No tests despite `routes_test.clj` covering character routes. |
| `src/clj/orcpub/pdf.clj` | 550 | PDF generation. Only font loading is tested. The actual character sheet rendering, field mapping, and image handling are untested. |
| `src/clj/orcpub/db/schema.clj` | 392 | Database schema definitions. Could be tested for schema validity and transactability. |
| `src/cljc/orcpub/errors.cljc` | 8 | Error code constants. Trivial but worth a completeness test. |
| `src/cljc/orcpub/pdf_spec.cljc` | 608 | PDF field mapping specs. Could be tested with spec/exercise. |

### Tier 3: Lower Priority (Data definitions, UI, templates)

| Source File | Lines | Notes |
|-------------|-------|-------|
| `src/cljc/orcpub/dnd/e5/spells.cljc` | 4,229 | Spell data definitions. Mostly static data, but structural validation could catch corruption. |
| `src/cljc/orcpub/dnd/e5/monsters.cljc` | 9,272 | Monster stat blocks. Static data; spec validation would be sufficient. |
| `src/cljc/orcpub/dnd/e5/weapons.cljc` | 432 | Weapon definitions and properties. |
| `src/cljc/orcpub/dnd/e5/armor.cljc` | 104 | Armor definitions and AC math. |
| `src/cljc/orcpub/dnd/e5/skills.cljc` | 103 | Skill/ability associations. |
| `src/cljc/orcpub/dnd/e5/character/random.cljc` | 2,462 | Random character generation. Complex but hard to assert against without snapshot testing. |
| `src/cljc/orcpub/dnd/e5/char_decision_tree.cljc` | 980 | Character creation wizard logic. |
| All 15 UA/SCAG template files | ~5,400 | Optional content templates. Low risk of breaking core functionality. |
| All 11 CLJS frontend files | ~17,600 | Re-frame events/subs/views. Would require ClojureScript test runner (`lein doo`) or E2E tests. |

---

## Quality Assessment of Existing Tests

### Strengths

1. **Round-trip testing pattern**: `entity_test.clj`, `event_handlers_test.clj`, and `character_test.clj` extensively use a to-strict -> from-strict -> to-strict round-trip pattern. This is an effective strategy for catching serialization bugs.

2. **Datomic mocking**: `routes_test.clj` uses `datomock` with in-memory databases for realistic integration tests without external dependencies.

3. **Property-based testing**: `character_test.cljc` uses `clojure.test.check` with `defspec` for property-based speed validation. This approach could be extended to other areas.

4. **Spec instrumentation**: Several tests use `stest/instrument` to enable runtime spec checking during tests, catching spec violations automatically.

5. **Real-world test data**: Tests use realistic D&D character structures (multi-class fighters, warlocks with complex invocations), not just trivial examples.

### Weaknesses

1. **Empty stub files**: `dice_test.clj` and `modifiers_test.clj` were created but never filled in. These give a false impression of coverage.

2. **Commented-out tests**: The `book-of-ancient-secrets` test in `warlock_test.clj` and `test-multiple-ip-attempts-to-same-account?` in `security_test.clj` are disabled with `#_`. Both have `TODO: Fix / remove test` comments, suggesting they broke at some point and were never repaired.

3. **Missing negative tests**: Most tests only verify happy-path behavior. There are few tests for invalid inputs, edge cases, or error conditions (except `magic_items_test.clj` which tests `thrown?`).

4. **No assertions in some tests**: `strict-round-trip-2` in `character_test.clj:106` uses bare `(= strict round-trip)` without wrapping it in `(is ...)`, so it doesn't actually assert anything.

5. **No frontend tests**: The `test/cljs/` directory exists in the test-paths config but appears empty. No ClojureScript tests exist.

6. **Documentation/code mismatch**: `docs/ERROR_HANDLING.md` documents error handling macros (`with-db-error-handling`, `with-email-error-handling`, `with-validation`) and references `test/clj/orcpub/errors_test.clj`, but neither the macros nor the test file exist on this branch. The actual `errors.cljc` is just 8 lines of keyword constants.

---

## Recommended Improvements

### Quick Wins (can be done without infrastructure changes)

1. **Fill in `dice_test.clj`** - Pure functions, no dependencies needed:
   ```clojure
   ;; Test dice-str parsing, dice-mean, xdx-mean, roll
   (deftest test-dice-mean
     (is (= 3.5 (dice/dice-mean 6)))
     (is (= 10.5 (dice/xdx-mean 3 6))))
   ```

2. **Fill in `modifiers_test.clj`** - Test modifier construction macros, ability modifiers, AC modifiers, saving throw modifiers.

3. **Fix `strict-round-trip-2` in `character_test.clj:106`** - Wrap `(= strict round-trip)` with `(is ...)`.

4. **Repair or remove commented-out tests** - Investigate why `book-of-ancient-secrets` and `multiple-ip-attempts-to-same-account?` were disabled, fix them or delete the dead code.

5. **Add `registration_test.clj`** - The validation functions in `registration.cljc` are pure and trivially testable (email format, username rules, password strength).

6. **Add `errors_test.clj`** - Even if just constants, a sanity test ensures the error codes exist and are keywords.

### Medium Effort (focused test suites for core logic)

7. **`options_test.clj` expansion** - Test multiclass spell slot calculation, ability score improvement logic, proficiency bonus computation. These are the most commonly-reported-broken features in character builders.

8. **`character_test.clj` expansion** - Add tests for:
   - `ability-values` / `ability-bonuses` computation
   - `armor-class` derivation from armor, DEX, shields, features
   - `max-hit-points` calculation across levels
   - Multiclass spell slot merging

9. **`template_test.clj` expansion** - Test that the full D&D 5e template (`t5e/template`) can be constructed without errors. This is a smoke test that catches broken option definitions.

10. **`pdf_test.clj` expansion** - Test the field-mapping logic in `pdf_spec.cljc`. Given a built character, verify the correct values appear in the correct PDF form fields.

11. **Party route tests** - Mirror the pattern in `routes_test.clj` for `routes/party.clj` (create, update, remove character, delete party).

### Larger Efforts (infrastructure needed)

12. **ClojureScript tests** - Set up `lein doo` or `shadow-cljs` test runner for frontend tests. Priority targets:
    - `events.cljs` - re-frame event handlers (can be tested without DOM)
    - `subs.cljs` - re-frame subscriptions (pure functions of app-db)
    - `equipment_subs.cljs` / `spell_subs.cljs` - derived subscription logic

13. **Spec validation of static data** - Write a single test that exercises `clojure.spec/valid?` against every spell, monster, magic item, and class definition. This catches structural corruption without testing game logic:
    ```clojure
    (deftest all-spells-conform-to-spec
      (doseq [[k spell] (all-spells)]
        (is (spec/valid? ::spell/spell spell) (str "Invalid spell: " k))))
    ```

14. **Integration/smoke test** - Build a complete character (race + class + background + equipment) through the entity system and verify the built entity has expected attributes. This is effectively what the commented-out `book-of-ancient-secrets` warlock test was attempting.

15. **E2E tests** - The `CLAUDE.md` references an `e2e/` directory with Playwright tests (on the `testing/develop` branch). These should be kept in sync with feature development.

---

## Test Infrastructure Notes

### Running Tests
```bash
lein test                    # All JVM tests (test/clj + test/cljc)
lein test orcpub.entity-test # Single namespace
```

### Test Configuration
- **Test paths**: `test/clj`, `test/cljc`, `test/cljs` (configured in `project.clj:83`)
- **Framework**: `clojure.test` (built-in)
- **Property testing**: `org.clojure/test.check 0.9.0`
- **DB mocking**: `vvvvalvalval/datomock 0.2.0`
- **CI**: GitHub Actions on PRs to `develop` (lint + test)
- **CLJS tests**: Path exists but no runner is configured. Would need `lein-doo` or equivalent.

### Test File Naming Convention
- Backend tests: `test/clj/orcpub/<name>_test.clj`
- Shared tests: `test/cljc/orcpub/<name>_test.clj` (note: `.clj` extension, not `.cljc`)
- One exception: `test/cljc/orcpub/dnd/e5/character_test.cljc` uses `.cljc` extension for cross-platform spec testing

---

## Coverage Map

Visual representation of test coverage by namespace area.

```
Legend:  [====] tested   [----] untested   [stub] empty test file   [####] commented out

CORE ENGINE
  orcpub.entity           [============]  12 tests, round-trips, paths
  orcpub.entity.strict    [========]      2 tests, spec validation
  orcpub.entity-spec      [======]        2 tests, entity building
  orcpub.modifiers        [==]            Indirect coverage via entity-spec
  orcpub.template         [====]          1 test, modifier maps
  orcpub.common           [=]             1 test, namespace keys
  orcpub.dice             [stub]          File exists, no tests
  orcpub.errors           [----]          No tests
  orcpub.registration     [----]          No tests
  orcpub.pdf-spec         [----]          No tests

D&D 5e GAME LOGIC
  dnd.e5                  [====]          2 tests, plugin merge
  dnd.e5.character        [========]      8 tests across 2 files
  dnd.e5.event-handlers   [==========]   9 tests, level/class/inventory
  dnd.e5.magic-items      [========]      3 tests, conversion + expansion
  dnd.e5.options          [=]             1 test, spell slots
  dnd.e5.modifiers        [stub]          File exists, no tests
  dnd.e5.warlock          [####]          Test data defined, test commented out
  dnd.e5.classes          [----]          3,144 lines, no tests
  dnd.e5.template         [----]          1,552 lines, no tests
  dnd.e5.weapons          [----]          No tests
  dnd.e5.armor            [----]          No tests
  dnd.e5.skills           [----]          No tests
  dnd.e5.spells           [----]          No tests (data)
  dnd.e5.monsters         [----]          No tests (data)
  dnd.e5.spell-lists      [----]          No tests (data)
  dnd.e5.equipment        [----]          No tests
  dnd.e5.combat           [----]          No tests
  dnd.e5.display          [----]          No tests
  dnd.e5.backgrounds      [----]          No tests
  dnd.e5.races            [----]          No tests
  dnd.e5.feats            [----]          No tests
  dnd.e5.char-decision-tree [----]        No tests
  dnd.e5.character.random [----]          No tests
  15x UA/SCAG templates   [----]          No tests

BACKEND
  routes                  [==========]   7 tests, character CRUD
  routes.party            [----]          No tests
  security                [========]      4 tests (+1 commented)
  pdf                     [==]            1 test, fonts only
  email                   [----]          No tests
  db.schema               [----]          No tests
  datomic                 [----]          No tests
  system                  [----]          No tests
  pedestal                [----]          No tests

FRONTEND (all untested)
  events.cljs             [----]          3,964 lines
  views.cljs              [----]          8,231 lines
  subs.cljs               [----]          1,262 lines
  character-builder.cljs  [----]          2,090 lines
  equipment-subs.cljs     [----]          330 lines
  spell-subs.cljs         [----]          1,293 lines
  db.cljs                 [----]          290 lines
  autosave-fx.cljs        [----]          56 lines
```

---

## Suggested Priority Order

If you're going to invest time in improving test coverage, here's the suggested order of maximum impact per effort:

1. **Fill stub files** (`dice_test`, `modifiers_test`) - 30 min, removes false-coverage impression
2. **Fix broken test** (`character_test.clj:106` missing `is`) - 5 min, silent test failure
3. **Add `registration_test`** - 30 min, security-relevant validation
4. **Expand `options_test`** (spell slots, proficiency bonus) - 1-2 hours, commonly broken area
5. **Expand `character_test`** (AC, HP, ability bonuses) - 2-3 hours, core correctness
6. **Repair commented-out tests** (`warlock_test`, `security_test`) - 1-2 hours, reclaim lost coverage
7. **Add data spec validation** (spells, monsters, classes) - 1-2 hours, catches data corruption
8. **Add `party` route tests** - 1 hour, mirrors existing `routes_test` pattern
9. **Add `template` smoke test** - 1 hour, catches broken option definitions
10. **Set up CLJS test runner** - 2-3 hours infrastructure, then ongoing test writing

---

## Related Documentation

- [CODEBASE.md](./CODEBASE.md) - Architecture and data flow
- [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Error handling patterns (note: code/doc mismatch flagged above)
- [CLAUDE.md](../CLAUDE.md) - Development workflow and testing checklist
