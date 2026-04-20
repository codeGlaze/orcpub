# Frontend Stack Upgrade

## React 15 → 18

React 18 introduces the `createRoot` API and deprecates the legacy `ReactDOM.render()`.

### Dependencies

```clojure
;; Before
[cljsjs/react "15.x"]
[cljsjs/react-dom "15.x"]
[reagent "0.6.x"]

;; After
[cljsjs/react "18.3.1-1"]
[cljsjs/react-dom "18.3.1-1"]
[reagent "2.0.1"]
```

### createRoot Migration

In `web/cljs/orcpub/core.cljs`, the app mounts using Reagent 2.0's `reagent.dom.client` namespace:

```clojure
(ns orcpub.core
  (:require [reagent.dom.client :as rdc] ...))

;; createRoot-based mounting (React 18)
```

This replaces the old `reagent.dom/render` call.

### Reagent 2.x: `:class` vs `:class-name`

Reagent 2.x changed how CSS classes merge. The `:class-name` prop **overwrites** classes set on the hiccup tag, while `:class` **merges** with them:

```clojure
;; BAD — .white is lost, only bg-red applies
[:div.white {:class-name "bg-red"}]

;; GOOD — both .white and .bg-red apply
[:div.white {:class "bg-red"}]
```

Always use `:class` (not `:class-name`) when the hiccup tag already has classes like `[:div.foo.bar ...]`.

### Production Build: Externs for React 18

The `cljsjs/react-dom 18.3.1-1` package has incomplete externs. Under Closure Compiler `:advanced` optimization, two React 18 APIs get renamed, causing a runtime crash (`c0 is not a function`):

- `ReactDOM.Root.render` — used by `reagent.dom.client/render`
- `ReactDOM.flushSync` — used by `reagent.impl.batching/react-flush`

**Fix**: A custom `externs.js` at the repo root declares these symbols:

```javascript
ReactDOM.Root.render = function(children) {};
ReactDOM.flushSync = function(callback) {};
```

The uberjar profile references it:

```clojure
:compiler {:optimizations :advanced
           :infer-externs true
           :externs       ["externs.js"]}
```

### re-frame

Updated from `0.x` to `1.4.4`. The event/subscription API is unchanged — existing handlers, subscriptions, and effects work without modification.

#### Subscribe-outside-reactive-context: Fixed

The original codebase had **56 instances** of `@(subscribe [...])` called outside Reagent reactive context (inside event handlers, modifier conditions, top-level code, and pure `.cljc` namespaces). In re-frame 0.x these worked silently; in 1.3+ they produce console warnings and leak Reaction objects. All 56 have been fixed across two phases.

**Fix patterns used** (stable re-frame APIs only — no `re-frame.alpha` or third-party libs):

| Pattern | When to use | Instances |
|---------|-------------|-----------|
| Direct db read | Subscription is `(get db :key)` | 3 |
| Pass from component | Component already has the value | 4 |
| Extract pure helpers | Subscription computes derived data | 2 |
| Replace with dispatch | `reg-sub-raw` used for side effects | 1 |
| Template cache via `track!` | Autosave handler (no component context) | 1 |
| Direct db read (character) | Character already in db map | 1 |
| SSOT pure fn + `@app-db` | Modifier conditions needing dynamic data | 4 |
| Thread parameter from caller | Data already in the calling chain | 1 |
| Plugin-data map | Pure `.cljc` namespace, multiple subscribes | 7 |
| `reg-sub-raw` | Conditional subscription routing | 1 |
| Move to render scope | `@(subscribe)` in event closure | 1 |
| Pure character fns | Prereq functions with subscribe | 22 |
| `def` + `partial` → `defn` | Subscribe at load time via `partial` | 1 |

**Phase 1** (events.cljs, options.cljc, classes.cljc, core.cljs): 42 fixes
**Phase 2** (options.cljc, pdf_spec.cljc, equipment_subs.cljs, views.cljs): 14 fixes
**Browser console**: zero subscribe warnings on fresh page load.

**Key insight**: Most subscriptions were Layer 3 (computed/derived). Naively replacing `@(subscribe [...])` with `(get db ...)` would have returned `nil` — the computed values only exist in the subscription cache, not in app-db. Custom/homebrew content is especially tricky: `mi/all-weapons-map` (static) does NOT include user-imported weapons.

#### Utility namespace: `orcpub.dnd.e5.event-utils`

A circular dependency prevented `events.cljs` from importing subscription computation code:

```
equipment_subs.cljs → events.cljs (url-for-route, show-generic-error)
subs.cljs           → events.cljs (url-for-route, show-generic-error, mod-cfg, default-mod-set)
spell_subs.cljs     → events.cljs (dead import)
```

**Fix**: Shared utility functions extracted from `events.cljs` into `event_utils.cljs`:

| Function | Purpose |
|----------|---------|
| `backend-url` | Rewrites path to localhost:8890 in dev |
| `url-for-route` | Builds backend URL from bidi route |
| `auth-headers` | Returns `Authorization` header map (was duplicated 3x) |
| `show-generic-error` | Returns generic error dispatch vector |
| `mod-cfg` | Builds modifier config map |
| `mod-key` | Multimethod: extracts comparison key from modifier |
| `compare-mod-keys` | Comparator for modifier configs |
| `default-mod-set` | Ensures modifier set is sorted |

Subscription files now import `event-utils` instead of `events`, breaking the circle. `events.cljs` aliases the moved functions for backward compatibility.

#### Template caching via `reagent.core/track!`

**THIS IS A NEW PATTERN IN THIS PROJECT.** `track!` has never been used elsewhere in the codebase. It is stable Reagent API (since ~0.6) but warrants extra testing attention.

**Problem**: The autosave handler (`::char5e/save-character`) needs `built-character`, which requires the full template. The template is computed by a 12-input subscription chain. The handler is dispatched from a timer (no component context), so it can't subscribe.

**Solution**: `autosave_fx.cljs` creates a `track!` watcher that observes the `::char5e/template` subscription in a proper reactive context and caches the result in app-db:

```clojure
;; autosave_fx.cljs
(defonce _init-template-cache
  (js/setTimeout
    (fn []
      (r/track!
        (fn []
          (when-let [template @(subscribe [::char5e/template])]
            (dispatch [::cache-template template])))))
    0))
```

The save handler then reads the cached template and computes `entity/build(character, template)` directly:

```clojure
;; events.cljs — ::char5e/save-character
(let [cached-template (get db ::autosave-fx/cached-template)]
  (if-not cached-template
    {} ;; template not cached yet — skip this cycle
    (let [built-character (entity/build character cached-template)]
      ...)))
```

**Why `js/setTimeout 0`**: The `track!` must run after all subscription registrations. Since `autosave_fx.cljs` loads before `equipment_subs.cljs` (where `::char5e/template` is registered), the timeout defers creation to end-of-event-loop when all modules are loaded.

**Why `built-template` is a no-op**: The `built-template` function in `subs.cljs:254-270` has its plugin-merging logic entirely commented out (`#_`). It returns the input template unchanged. This means the cached `::char5e/template` IS the effective `built-template`.

**Safety**: If the template hasn't cached yet (e.g., app just loaded), the handler returns `{}` (no-op). The next autosave cycle (7.5s later) retries.

**Risk factors**:
- `track!` creates a long-lived reactive watcher — ensure it doesn't leak if the app is torn down
- If `::char5e/template` subscription is ever renamed/removed, the `track!` will silently fail
- The `js/setTimeout 0` relies on module load order — all `reg-sub` calls must complete before the timeout fires
- If `built-template` plugin merging is ever re-enabled, the cached template would need to include plugin-option processing

#### Files changed (subscribe refactor)

| File | Changes |
|------|---------|
| `src/cljs/orcpub/dnd/e5/event_utils.cljs` | **NEW**: shared utilities extracted from events.cljs |
| `src/cljs/orcpub/dnd/e5/events.cljs` | All handler fixes, compute helpers, verify-user-session, subscribe removed from requires |
| `src/cljs/orcpub/dnd/e5/autosave_fx.cljs` | Template cache via `track!` |
| `src/cljs/orcpub/dnd/e5/subs.cljs` | Import event-utils, remove local auth-headers |
| `src/cljs/orcpub/dnd/e5/equipment_subs.cljs` | Import event-utils, remove local auth-headers |
| `src/cljs/orcpub/dnd/e5/spell_subs.cljs` | Remove dead events import |
| `src/cljs/orcpub/character_builder.cljs` | Pass built-char via dispatch (save, random name) |
| `src/cljc/orcpub/dnd/e5/options.cljc` | Multi-arity custom-option-builder (inject-template? flag) |
| `web/cljs/orcpub/core.cljs` | Replace `@(subscribe [:user false])` with `(dispatch-sync [:verify-user-session])` |

#### Test coverage

Pure function tests now cover many refactored modules. CLJS-only tests exist for re-frame handler integration. The JVM test suite includes tests for `compute-all-weapons-map`, feat-prereqs, pdf_spec pure functions, folder routes (CRUD + validation), and event handler round-trips.

**Current**: 206 JVM tests, 945 assertions, 0 failures.

**CLJS-only tests** (browser via `lein fig:test`):
- `test/cljs/orcpub/dnd/e5/events_test.cljs` — re-frame handler tests

**Manual testing checklist**:
- [ ] Save character (manual) — verify character saves with correct summary
- [ ] Autosave — edit character, wait 7.5s, verify save fires
- [ ] Random name — verify race/subrace/sex-appropriate name generated
- [ ] Filter spells — type 3+ characters, verify spell list filters
- [ ] Filter items — type 3+ characters, verify item list filters
- [ ] Level up — verify character navigates to builder
- [ ] Export plugins — verify .orcbrew file downloads
- [ ] Custom subclass/feat — verify name input works in homebrew builder
- [ ] Login — verify auth check on app startup
- [ ] Homebrew import — import a 2-5MB .orcbrew file, verify content loads

## Figwheel

Migrated from **lein-figwheel** (deprecated) to **figwheel-main 0.2.20**.

### What Changed

| Aspect | Before | After |
|--------|--------|-------|
| Plugin | `lein-figwheel` | `com.bhauman/figwheel-main 0.2.20` |
| Config | `:figwheel {}` in project.clj | `dev.cljs.edn` + `:figwheel {}` in project.clj |
| REPL | `lein figwheel` | `lein fig:dev` |
| Port | 3449 | 3449 (unchanged) |

### Port

Figwheel runs on **port 3449**. This has not changed. The devcontainer forwards this port.

### Figwheel Modes

Three Leiningen aliases expose figwheel-main's different modes:

| Alias | Command | Use when |
|-------|---------|----------|
| `lein fig:dev` | `--build dev --repl` | Interactive development (needs a terminal) |
| `lein fig:watch` | `--build dev` | Background/scripted startup (headless, works with nohup) |
| `lein fig:build` | `--build-once dev` | CI or quick compilation check |

**Important**: `fig:dev` uses `--repl` which requires an interactive terminal. Running it under `nohup` causes the REPL to read EOF and the watcher dies. `start.sh figwheel` uses `fig:watch` (headless) for this reason.

### user.clj Integration

`dev/user.clj` lazy-loads figwheel-main to avoid pulling in CLJS tooling for server-only REPL sessions:

```clojure
(def ^:private fig-api
  (delay
    (require 'figwheel.main.api)
    (find-ns 'figwheel.main.api)))
```

REPL functions: `(fig-start)`, `(fig-stop)`, `(cljs-repl)`

## Other Frontend Dependencies

| Library | Before | After | Notes |
|---------|--------|-------|-------|
| `binaryage/devtools` | 0.x | 1.0.7 | Chrome devtools for CLJS |
| `cider/piggieback` | 0.3.x | 0.5.3 | nREPL middleware for CLJS REPL |
| `day8.re-frame/re-frame-10x` | old | 1.11.0 | re-frame debugging panel |
| `hiccup` | 1.x | 2.0.0 | HTML templating |
| `com.cognitect/transit-cljs` | 0.8.x | 0.8.280 | Transit serialization |
