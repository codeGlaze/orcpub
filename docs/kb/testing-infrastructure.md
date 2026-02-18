# Testing Infrastructure — Agent Reference

## Verified Facts

### Test Runners

| Command | Runtime | What runs | Test count (as of 2026-02-18) |
|---------|---------|-----------|-------------------------------|
| `lein test` | JVM (Clojure) | `test/clj/` + `test/cljc/` | 105 tests, 301 assertions |
| `lein fig:test` | CLJS → browser | `test/cljs/` + transitive `.cljc` | Compiles only; runs in browser |
| `lein fig:build` | CLJS compilation | Source only, no tests | N/A |

### What `lein fig:test` Actually Does

Compiles `test.cljs.edn` to `target/test/js/test.js`. Tests auto-run when opened in a browser (via `:auto-testing true`). There is **no headless runner** — no doo, no karma, no node. The CLJS tests exist to be run interactively or eventually via doo.

### Directory Layout

```
test/
  clj/     JVM-only (.clj) — routes, PDF, security, CSP, integration
  cljc/    Cross-platform (.clj and .cljc) — entity, character, template, modifiers
  cljs/    CLJS-only (.cljs) — re-frame handlers, test runner
```

**Important**: `test/cljc/` contains BOTH `.clj` and `.cljc` files. The `.clj` files only run on JVM. The `.cljc` files run on both.

## Library Truths (Verified, Not Assumed)

### re-frame.test Does NOT Exist in re-frame 1.4.4

**Wrong assumption**: "`re-frame.test` is built into re-frame 1.x"
**Truth**: It is a **separate library**: `day8.re-frame/re-frame-test`. Not currently a project dependency.

Verified by extracting `re-frame-1.4.4.jar` — 21 source files, no test namespace. The re-frame docs mention testing utilities but they live in a separate package.

### What You CAN Do Without re-frame-test

- `re-frame.core/dispatch-sync` — executes handler synchronously, returns nil
- `re-frame.db/app-db` — the actual atom, can be `reset!` and `deref`'d
- Register stub effect handlers: `(re-frame.core/reg-fx :http (fn [_] nil))`

### What You CANNOT Do Without re-frame-test

- Intercept effects returned by `reg-event-fx` handlers
- Run tests in isolated re-frame state (shared global registrations)
- Wait for async dispatches

### Testing reg-event-db vs reg-event-fx

`reg-event-db` handlers are easy to test — `dispatch-sync` → check `@app-db`.

`reg-event-fx` handlers return effects maps (`{:http ... :dispatch ...}`). Without `re-frame-test`, you can only test:
- Guard logic (does the handler short-circuit correctly?)
- DB-side effects (does `@app-db` change?)
- No-crash behavior (does dispatch-sync complete without exception?)

You CANNOT assert on the returned `:http` or `:dispatch` effects.

### character_test.cljc Is Not CLJS-Compatible (Fixed)

The file uses `clojure.spec.alpha.test` and `defspec` from `clojure.test.check.clojure-test` — both JVM-only. Reader conditionals `#?(:clj ...)` were added to make it compile in CLJS. Without this fix, `lein fig:test` fails.

### CLJS Compiler Compiles Everything on Classpath

When `test/cljc/` is in `:test-paths`, the CLJS compiler tries to compile ALL `.cljc` files there — not just ones transitively required by the `:main` namespace. This means a single broken `.cljc` file blocks the entire test build.

## Clojure/CLJS Gotchas Encountered

### `(seq nil)` Returns `nil`, Not `()`

```clojure
(= () (seq nil))  ;; => false (nil ≠ ())
(empty? nil)      ;; => true  (use this instead)
```

### `.cljc` File Location Matters

The project convention: `.cljc` files go in `src/cljc/`, `.cljs` in `src/cljs/`. Both directories are on the classpath. If a `.cljc` file is in `src/cljs/`, it works but violates convention and confuses other agents.

`event_utils` was originally `.cljs` in `src/cljs/`. Moved to `.cljc` in `src/cljc/` with `#?(:cljs ...)` reader conditionals for `js/window.location` usage.

### Reader Conditionals for Browser APIs

Any function using `js/window`, `js/document`, `js/Blob`, `js/setTimeout`, etc. must be wrapped in `#?(:cljs ...)` in `.cljc` files. The JVM will fail to compile these otherwise.

## Namespace Architecture (Post-Refactor)

```
event_utils.cljc (src/cljc/)     compute.cljc (src/cljc/)
  ├─ auth-headers                   ├─ compute-plugin-vals
  ├─ url-for-route  [CLJS-only]    ├─ compute-sorted-spells
  ├─ backend-url    [CLJS-only]    ├─ compute-sorted-items
  ├─ show-generic-error             ├─ filter-spells
  ├─ mod-cfg / mod-key              ├─ filter-items
  └─ default-mod-set                └─ filter-by-name-xform
         ↑                                    ↑
    subs.cljs imports               events.cljs imports
    equipment_subs.cljs imports     (def aliases for backward compat)
    spell_subs.cljs imports
```

**Why these exist**: `subs.cljs` → `events.cljs` was a circular dependency. Extracting shared utilities to `event_utils` broke the circle. `compute.cljc` was extracted from `events.cljs` so pure functions could be tested on JVM via `lein test`.

## Test Patterns

### Pattern: Testing a reg-event-db Handler

```clojure
(ns my-test
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [my.events]))  ;; side effect: registers handlers

(use-fixtures :each {:before #(reset! app-db {})})

(deftest my-handler-test
  (reset! app-db {:plugins {}})           ;; 1. Set up db
  (rf/dispatch-sync [::my-event "arg"])   ;; 2. Dispatch
  (is (= expected (::my-key @app-db))))   ;; 3. Assert on db
```

### Pattern: Testing a .cljc Pure Function

```clojure
(ns my-test
  (:require [clojure.test :refer [deftest testing is]]
            [my.compute :as compute]))

(deftest my-fn-test
  ;; Use known static data for assertions, not (is (seq result))
  (let [result (compute/my-fn {:key "value"})]
    (is (= "expected" (:name (first result))))
    (is (= 3 (count result)))))
```

### Anti-Pattern: Garbage Assertions

```clojure
;; BAD — proves nothing, passes even if function returns garbage
(is (seq result))
(is (some? result))

;; GOOD — asserts on specific values that would break if logic changes
(is (contains? (set (map :name result)) "Fireball"))
(is (= 2 (count result)))
(is (= ["Fire Bolt" "Fireball"] (mapv :name result)))
```
