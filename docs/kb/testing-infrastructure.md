# Testing Infrastructure — Agent Reference

## Verified Facts

### Test Runners

| Command | Runtime | What runs | Test count (as of 2026-02-18) |
|---------|---------|-----------|-------------------------------|
| `lein test` | JVM (Clojure) | `test/clj/` + `test/cljc/` | 123 tests, 332 assertions |
| `lein fig:test` | CLJS → browser | `test/cljs/` + transitive `.cljc` | Compiles only; runs in browser |
| `lein fig:build` | CLJS compilation | Source only, no tests | N/A |
| `./scripts/e2e/run.sh [script.js]` | Chromium (playwright) | `scripts/e2e/` against a real server | See "Browser checks" below |

### What `lein fig:test` Actually Does

Compiles `test.cljs.edn` to `target/test/js/test.js`. Tests auto-run when opened in a browser (via `:auto-testing true`). There is **no headless runner for `test/cljs/`** — no doo, no karma. Those tests are run interactively.

Separately, `scripts/e2e/` drives the real app in Chromium through playwright. It does not run `test/cljs/`; it exercises the running product. See "Browser checks" below.

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

## Browser checks (`scripts/e2e/`)

```
./scripts/e2e/run.sh            # runs scripts/e2e/run.js
./scripts/e2e/run.sh mine.js    # runs scripts/e2e/mine.js
```

`run.sh` boots the app on `datomic:mem://` with a seeded verified user, waits for
it, runs the script, then tears the server down. The in-memory database only
exists inside the JVM that made it, which is why `dev/e2e_boot.clj` starts the
server *and* seeds the user in one process rather than shelling out.

Requires the compiled bundle. `run.sh` builds it with `lein fig:prod` on first
run; that takes several minutes, so build it before you start iterating.

Fixed sleeps dominate the runtime of a script like this — they came to 19.5
seconds per scenario before being replaced by the waits described below, which
took the full three-scenario run from 2m51s to 2m15s while adding checks. Reach
for a condition first, quiet second, a duration never.

### Traps that cost real time

**The server leaks and the next run silently tests it.** `lein` forks a JVM, so
killing the wrapper leaves the child holding the port. The next run then fails to
bind and drives the *stale* server — old code, no error, wrong answers. `run.sh`
now uses `setsid` and kills the process group, and aborts if it sees
`BindException` in the log. If results contradict a change you just made, check
that first: `curl -sf localhost:8890/` before starting should fail.

**The bundled Chromium may not match playwright's expected build.** Playwright
wants a version under `/opt/pw-browsers` that may not be there and refuses to
launch. Pass `executablePath` explicitly — the scripts look for
`/opt/pw-browsers/chromium-1194/chrome-linux/chrome` and fall back.

**Most controls have hidden duplicates.** The mobile layout renders its own copy,
so `locator(...).first()` frequently lands on something invisible and the click
times out. Walk the matches and take the first where `isVisible()`.

**The sticky header intercepts clicks.** Playwright scrolls the target into view,
straight under the fixed header. Centre it first
(`el.scrollIntoView({block:'center'})`) and fall back to `click({force:true})`.

**A quiet DOM does not mean the render happened.** Waiting on a MutationObserver
going quiet is a good default where there is nothing specific to wait for, but
re-frame leaves a gap between the event and the re-render, and the observer fires
in that gap. Replacing the fixed sleeps in `setClasses` with a quiet-DOM wait
broke it: class 3's dropdown had not been rendered yet and the run reported the
character had no third class. Where there IS something to wait for, poll for that
instead — `waitForSelects` in `run.js` re-reads the selects until the one it
needs exists. Quiet is a fallback, not the tool.

**A response event does not mean your handler has run.** `waitForEvent('response')`
resolves when the response arrives, not when the `context.route` handler that
captures the body has finished. The sleep that used to follow it was really
waiting for the bytes, so wait for the bytes:

```js
for (let i = 0; i < 200 && pdfBytes === null; i++) await page.waitForTimeout(50);
```

**A probe script with its own copy of the helpers will lie to you.** A throwaway
script written to answer "does the Wizard have a spell selector?" carried its own
slightly wrong `setClasses`, silently set only one of three classes, and reported
that two whole classes had no selection block. They did. `run.js` exports its
helpers (`module.exports` guarded by `require.main !== module`) so a probe drives
the builder through the same code: `./scripts/e2e/run.sh probe.js`.

**Walking the page top-down under-tests a form with repeated sections.** The
Spells step gives every caster its own block, and choosing spells by clicking the
first N visible rows only ever fills the blocks nearest the top. `pdf_spec` emits
a spellcasting section only for a class that HAS spells, so the later casters got
no page and the multiclass scenario silently exercised two classes while claiming
three. Address each block by its heading — indices shift under the re-render each
selection causes — and spread the picks across each list so more than the first
few spell levels get used.

**No outbound network in the sandbox.** `fonts.googleapis.com` fails and shows up
as a console error. That is the environment, not the app — filter it, and say so
in the filter, or a real failure gets filtered next to it.

### Getting a PDF back out of the browser

`response.body()` does **not** give you the PDF. Chromium hands a PDF response to
its internal viewer, and the body playwright can see is the viewer's wrapper:

```
345 bytes: <!doctype html><html><body ...><embed name='D5C38E62...'></body></html>
```

Intercept the route instead. This runs the page's own request and yields the real
bytes before the viewer takes them:

```js
let pdfBytes = null;
await context.route('**/character.pdf', async route => {
  const res = await route.fetch();
  pdfBytes = await res.body();
  await route.fulfill({ response: res });
});
```

That returned the full 284,675-byte document where `response.body()` returned 345.
Re-posting the captured payload through `context.request.post` also works, but it
is a second request and tests a replay rather than what the page actually did.

Field names inside a PDF sit in compressed object streams, so they cannot be
grepped from the bytes in node. In the browser, assert status, content type, size
and that the bytes start with `%PDF-`; assert structure where PDFBox is.

`run.sh` does that second half itself: after the browser run it passes every
captured PDF to `dev/inspect_export.clj`, which checks the invariants an export
has to hold to — no orphaned widgets, no shared names, no field carrying widgets
on two pages, every spellcasting section named, a class's later pages marked
`(continued)`, slots only on a class's first page. `run.js` writes the expected
page count to a `.min-pages` file beside each PDF so the inspector can assert it
too. Add a check there rather than in the browser script whenever the question is
about the document instead of the page.

### The export flow, as the UI actually works

Worth knowing before writing a script against it:

- The builder is at `/pages/dnd/5e/character-builder`, not `/character-builder`.
- A cookie banner ("Got it!") covers the lower controls until dismissed.
- **"Export"** opens the PDF options panel — it is not the `.orcbrew` export.
- **"Create PDF" is disabled until a sheet is chosen** in the "Select Character
  sheet" dropdown. `print-button-enabled` in `views.cljs` gates on it.
- The form targets `_blank` and the route answers `Content-Disposition: inline`,
  so the PDF opens in a new tab. There is **no download event** to wait for.

### Console output

Collect `console`, `pageerror` and `requestfailed` throughout and fail the run on
any error or warning. Signed out, the builder and export produce none.
