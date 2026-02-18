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

**Known issue**: The original codebase has ~12 instances of `@(subscribe [...])` called outside reactive context (inside event handlers and top-level `let` bindings). These produce console warnings but don't break functionality. The proper fix is to read from the `db` parameter directly in event handlers instead of subscribing. See `src/cljs/orcpub/dnd/e5/events.cljs` and `web/cljs/orcpub/core.cljs`.

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
