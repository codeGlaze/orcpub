# Testing Guide

> Test suite overview, patterns, E2E testing, and known gotchas.

---

## Running Tests

```bash
lein test                      # All server-side tests (Clojure JVM)
lein test :only ns/test-name   # Single test
lein cljsbuild once dev        # ClojureScript compilation check
lein lint                      # clj-kondo linter
```

### What Each Command Validates

| Command | Scope | Catches |
|---------|-------|---------|
| `lein test` | Server-side only | Backend logic, routes, DB, PDF, entity/build |
| `lein lint` | CLJ + CLJS syntax | Typos, unused vars, style |
| `lein cljsbuild once dev` | ClojureScript | Reagent/re-frame API changes, CLJS errors |
| `lein fig:dev` | Full frontend runtime | Runtime errors, React rendering |

## Test Suite Overview

Test files live in `test/clj/` and `test/cljc/`:

| File | What it tests |
|------|---------------|
| `character_test.clj` | Strict format round-trips (Datomic serialization) |
| `warlock_test.clj` | Entity/build integration — 8 tests, 21 assertions |
| `template_test.clj` | Template smoke test — constructs all 12 PHB class options |
| `registration_test.clj` | User registration |
| `entity_test.clj` | Entity spec validation |
| `ac_test.clj` | AC stacking bugs — 11 tests, 47 assertions (10 fail: Bug 1 x8, Bug 2 x2) |
| `magic_items_test.clj` | Magic item modifier serialization — 3 tests, 22 assertions |
| `magic_items_integration_test.clj` | Magic item modifiers in character builds — 23 tests, 38 assertions |

### Entity/Build Integration Tests

`warlock_test.clj` is the pattern for entity/build integration tests. It:
- Uses inline test configs for data that lives in `.cljs` files
- Exercises the full `entity/build` pipeline
- Tests class features, ability scores, proficiencies, spell slots

`magic_items_integration_test.clj` tests magic item modifier functions via
custom race configs injected into the entity build. Covers:
- Ability score overrides (`ability-override`) and additive bonuses (`ability`)
- Saving throw bonuses (`saving-throw-bonuses`)
- Damage resistances and immunities
- Speed overrides and swimming speed
- Darkvision (base + bonus stacking)
- Skill bonuses (single and all-skills)
- Spell save DC and attack modifier bonuses
- AC bonus items (Bracers of Defense, Staff of Power)
- Multi-item stacking interactions

Use `warlock_test.clj` as a template for class tests, and
`magic_items_integration_test.clj` as a template for item/modifier tests.

## E2E Testing

### Running E2E Tests
```bash
# Quick start (assumes Datomic is running)
PORT=8890 lein run &   # Start backend
cd e2e && npm test     # Run all tests
```

### Important Route Notes

OrcPub routes use **`/dnd/5e/`** (not `/dnd/e5/`):
- Character builder: `/pages/dnd/5e/character-builder`
- Spells: `/pages/dnd/5e/spells`
- My Content: `/dnd/5e/my-content` (no /pages/ prefix)

### DOM Structure
- **Home page (`/`)**: Splash page with `.splash-button` elements, no traditional header
- **Interior pages**: Rendered into `#app` container

### Expected Errors to Ignore

In production mode (`lein run`), you'll see:
```
WebSocket connection to 'ws://localhost:3449/figwheel-ws/dev' failed
```
This is expected — the Figwheel client code is in the compiled JS but Figwheel isn't running.

### E2E Testing Checklist

Before committing UI changes:
1. Run E2E tests: `cd e2e && npm test`
2. Check for JS console errors (tests capture these automatically)
3. Verify routes if UI changes involved

## Known Gotchas

### clj vs cljs test boundary

- `.clj` tests run on JVM only (`lein test`)
- `.cljs` files cannot be required from `.clj` tests
- Data stuck in `.cljs` files must be duplicated inline in tests or extracted to `.cljc`
- See `warlock_test.clj` for the inline config pattern

### PersistentArrayMap threshold

Maps with ≤8 keys use `PersistentArrayMap` (preserves insertion order).
Adding a 9th+ NEW key promotes to `PersistentHashMap` (arbitrary order).
Use `(apply array-map ...)` to force ordered maps at any size.
This caused the `strict-round-trip-2` test failure — see `entity.cljc:from-strict-selections`.
