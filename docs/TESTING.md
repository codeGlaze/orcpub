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
| `warlock_test.clj` | Entity/build integration — 7 tests, 20 assertions |
| `template_test.clj` | Template smoke test — constructs all 12 PHB class options |
| `registration_test.clj` | User registration |
| `entity_test.clj` | Entity spec validation |

### Entity/Build Integration Tests

`warlock_test.clj` is the pattern for entity/build integration tests. It:
- Uses inline test configs for data that lives in `.cljs` files
- Exercises the full `entity/build` pipeline
- Tests class features, ability scores, proficiencies, spell slots

Use it as a template for adding new class/build tests.

## E2E Testing

### Running E2E Tests
```bash
# Start Datomic Pro transactor
lib/com/datomic/datomic-pro/1.0.7482/bin/transactor \
  lib/com/datomic/datomic-pro/1.0.7482/config/working-transactor.properties &
sleep 5

# Start backend
PORT=8890 lein run &

# Run all tests (from testing worktree or testing/develop branch)
cd e2e && npm test
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
