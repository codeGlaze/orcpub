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

### re-frame

Updated from `0.x` to `1.4.4`. The event/subscription API is unchanged — existing handlers, subscriptions, and effects work without modification.

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
