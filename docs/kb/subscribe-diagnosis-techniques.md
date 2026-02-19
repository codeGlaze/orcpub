# Diagnosing subscribe-outside-reactive-context Warnings

## The Problem

re-frame's warning says "Subscribe was called outside of a reactive context"
but gives NO stack trace or subscription vector. The warning comes from
`re-frame.subs/warn-when-not-reactive` and goes through `re-frame.loggers/console`.

## What Doesn't Work

### Patching `re-frame.core/subscribe` with `set!`

```clojure
;; BROKEN — causes infinite recursion
(let [orig re-frame.core/subscribe]
  (set! re-frame.core/subscribe
        (fn [& args] ... (apply orig args))))
```

CLJS compiles multi-arity functions with a dispatch function that references
the namespace property (`re_frame.core.subscribe`). After `set!`, calling
`orig` still routes through the new wrapper because `orig` holds the
*dispatch function*, which reads the *namespace property* — which is now
our wrapper. Infinite recursion.

### Patching `rf-subs/warn-when-not-reactive` with `set!`

The CLJS compiler resolves intra-namespace function calls statically.
Within `subs.cljc`, the call `(warn-when-not-reactive)` inside `subscribe`
is compiled as a direct call to the original function, not through the
var/namespace property. `set!` changes the property but not the compiled
call site.

### `re-frame.loggers/set-loggers!` in the app entry namespace

Works in principle (updates an atom), but fires TOO LATE. The app's
`:require` declarations load all namespaces before the entry ns body runs.
If the warning fires during namespace loading, the custom logger isn't
installed yet.

## What Works: Figwheel Preload

Preload namespaces run before the app entry point and all its requires.
Patch `console.warn` (plain JS, no CLJS inlining issues) in a preload:

```clojure
(ns myapp.debug-preload
  "Figwheel preload: patches console.warn for subscribe stack traces.
   Add to :preloads in dev.cljs.edn. Remove when done.")

(let [orig-warn js/console.warn]
  (set! js/console.warn
        (fn [& args]
          (.apply orig-warn js/console (to-array args))
          (when (and (string? (first args))
                     (.includes (first args) "Subscribe was called outside"))
            (.call js/console.trace js/console
                   "^^^ SUBSCRIBE WARNING SOURCE ^^^")))))
```

In `dev.cljs.edn`:
```clojure
{:preloads [devtools.preload myapp.debug-preload]}
```

This catches ALL subscribe warnings, including those that fire during
namespace loading (the hardest to find).

## Reading the Stack Trace

The trace shows the full call chain. Key frames to look for:

1. **Your app code** — the `<anonymous> views.cljs:4336` line is the source
2. **`re_frame$subs$inp_fn`** — subscription input function (inner subscribes
   from `reg-sub` chains). Each level adds one `inp_fn` frame.
3. **Multiple warnings from one source** — a single subscribe with a deep
   `reg-sub` chain produces N warnings (one per chain level), all pointing
   to the same app-code origin.

## Common Culprits

| Pattern | Example | Fix |
|---------|---------|-----|
| `def` + `partial` with subscribe | `(def foo (partial bar @(subscribe [...])))` | Convert to `defn` |
| Form-1 component that should be Form-2 | Subscribe in outer fn, not inner render fn | Add inner `(fn [] ...)` |
| Top-level `defonce` with subscribe | `(defonce x @(subscribe [...]))` | Move to init event |
| Subscribe in event handler | `@(subscribe [...])` in `reg-event-fx` | Read from `db` or pass from component |
| Subscribe in onClick/callback | `{:on-click #(... @(subscribe [...]))}` | Move to render-time `let` |

## Cleanup

After diagnosis, remove the preload:
1. Delete the debug preload .cljs file
2. Remove it from `:preloads` in dev.cljs.edn
