# Testing

## Running Tests

| Command | What | Runtime |
|---------|------|---------|
| `lein test` | JVM tests (`test/clj/` + `test/cljc/`) | JVM |
| `lein fig:test` | CLJS tests (`test/cljs/` + transitive `.cljc`) | Browser |
| `lein fig:build` | Compilation check (no tests) | CLJS |

`fig:test` compiles to `target/test/js/test.js` — tests auto-run in browser via `auto-testing`. No headless runner (doo) yet.

## Directory Layout

```
test/
  clj/     JVM-only (.clj)
  cljc/    Cross-platform (.clj and .cljc — both run on JVM, .cljc also on CLJS)
  cljs/    CLJS-only (.cljs)
    orcpub/
      test_runner.cljs         fig:test entry point
      dnd/e5/events_test.cljs  re-frame handler tests
```

## Adding a Pure Function Test (.cljc)

1. Create `test/cljc/your/namespace_test.cljc`
2. Use `clojure.test` (aliased automatically in `.cljc`)
3. Add to `test/cljs/orcpub/test_runner.cljs` requires + `run-tests`
4. Verify: `lein test` + `lein fig:test`

## Adding a re-frame Handler Test (.cljs)

1. Create `test/cljs/your/namespace_test.cljs`
2. Use `cljs.test` with `refer-macros`
3. Require the events namespace (registers handlers at load time)
4. `reset! app-db` before each test
5. Assert on `@app-db` after `rf/dispatch-sync`
6. Add to test runner
7. Verify: `lein fig:test` only

```clojure
;; Example: testing a reg-event-db handler
(reset! app-db {:plugins {}})
(rf/dispatch-sync [::char5e/filter-spells "fire"])
(let [result (::char5e/filtered-spells @app-db)]
  (is (contains? (set (map :name result)) "Fireball")))
```

Effect interception (`:http`, `:dispatch`) requires `day8.re-frame/re-frame-test` — not currently a dependency.

## What's NOT Tested (Manual Only)

- `track!` template cache — needs live Reagent reactive context
- Component dispatch-site changes — needs full rendering
- HTTP calls (verify-user-session, save) — needs HTTP mock
- Browser-specific behavior (CSP, favicon, CSS)

See the manual checklist in [frontend-stack.md](frontend-stack.md#test-coverage).
