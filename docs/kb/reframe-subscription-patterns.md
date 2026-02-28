# re-frame Subscription Patterns

Subscription lifecycle, loading counter, auth guards, and pitfalls specific to
this codebase.

## `reg-sub-raw` HTTP Pattern

Several subscriptions use `reg-sub-raw` to fire HTTP requests on first access.
This is the primary data-fetching mechanism — there's no separate "load data"
event dispatched on navigation.

### Lifecycle

```clojure
(reg-sub-raw
 ::characters
 (fn [app-db _]
   ;; 1. This function runs when (subscribe [::characters]) is first deref'd
   ;; 2. The go block fires immediately
   (when (:token (:user-data @app-db))        ; ← AUTH GUARD
     (go
       (dispatch [:set-loading true])          ; ← increment counter
       (let [response (<! (http-get "/api/characters"))]
         (dispatch [:set-characters response])
         (dispatch [:set-loading false]))))    ; ← decrement counter
   ;; 3. Return a reaction (cursor into app-db)
   (make-reaction #(get @app-db :characters))))
```

### Critical: Auth Guard Placement

The guard MUST wrap the `go` block, not just the HTTP call inside it:

```clojure
;; WRONG — go block fires, loading counter increments, then HTTP 401s
(go
  (when (:token (:user-data @app-db))
    ...))

;; RIGHT — no token = no go block = no loading increment = no HTTP call
(when (:token (:user-data @app-db))
  (go ...))
```

Without the guard: every unauthenticated page load fires HTTP requests →
401 responses → triggers `:route-to-login` → infinite redirect loop on
pages that don't require auth.

### Auth Token Path

The canonical token location is:
```clojure
[:user-data :token]    ; ← CORRECT
[:user :token]         ; ← WRONG (was used in some places, now fixed)
```

Check with: `(:token (:user-data @app-db))`

The `:user-data` key is set by `create-login-response` in routes.clj and
stored in app-db by the login success handler.

## Loading Counter

`:loading` in app-db is an **integer counter**, not a boolean.

### Why a counter?

Multiple `reg-sub-raw` subscriptions fire in parallel on page load. Each one
independently increments on start and decrements on completion. A boolean
would cause race conditions:

```
Sub A starts  → loading = true
Sub B starts  → loading = true (redundant)
Sub A finishes → loading = false ← WRONG, B is still loading
Sub B finishes → loading = false
```

With a counter:
```
Sub A starts  → loading = 1
Sub B starts  → loading = 2
Sub A finishes → loading = 1  ← still loading (correct)
Sub B finishes → loading = 0  ← done
```

### CLJS Truthiness Gotcha

In ClojureScript, `0` is **truthy** (unlike JavaScript). So:

```clojure
;; WRONG — shows loading overlay forever once counter hits 0
(when @(subscribe [:loading]) ...)

;; RIGHT — explicitly check for positive count
(when (pos? (or @(subscribe [:loading]) 0)) ...)
```

### Reset on Login Redirect

`:route-to-login` resets the counter to `0` (not `false`):
```clojure
(assoc db :loading 0)
```

This prevents stale loading overlays when a 401 forces a redirect.

### Implementation Location

`events.cljs` ~line 1635:
```clojure
(defn set-loading [db loading?]
  (update db :loading (if loading? inc dec)))
```

Called via `(dispatch [:set-loading true])` to increment,
`(dispatch [:set-loading false])` to decrement.

## `subscribe` Context Rules

### Reactive context (components) — use `subscribe`
```clojure
(defn my-component []
  (let [chars @(subscribe [::characters])]  ; ← OK
    [:div (count chars)]))
```

### Non-reactive context — read `@app-db` directly
```clojure
;; componentDidMount, event handlers, go blocks
(let [token (:token (:user-data @re-frame.db/app-db))]  ; ← OK
  ...)
```

**Never call `subscribe` in**:
- `componentDidMount` / `componentWillUnmount`
- re-frame event handlers (`reg-event-db`, `reg-event-fx`)
- `reg-sub-raw` body (outside the returned reaction)
- Plain functions called from non-reactive contexts

Calling `subscribe` outside a reactive context produces console warnings
and may cause memory leaks (subscriptions never disposed).

## `reg-sub` vs `reg-sub-raw`

| | `reg-sub` | `reg-sub-raw` |
|---|-----------|---------------|
| Use for | Pure derivations from app-db | Side effects (HTTP, timers) |
| Returns | Value (auto-wrapped in reaction) | Must return a `Reaction` manually |
| Caching | Automatic signal graph | Manual (reaction on app-db cursor) |
| When it runs | On dependency change | On first deref |
| This codebase | Most subscriptions | `::characters`, `::parties`, `:user` |

## Debugging Subscription Issues

1. **Loading overlay stuck**: Check if a `reg-sub-raw` go block incremented
   the counter but never decremented (HTTP error path missing decrement).
2. **Data not loading**: Check auth guard — if token path is wrong, the guard
   silently skips the HTTP request.
3. **Console warnings**: "subscribe outside reactive context" — find the call
   site and replace with direct `@app-db` read.
4. **Stale data after login**: The `reg-sub-raw` subs only fire on first deref.
   If the user logs in after the sub was already created, it won't re-fire.
   Navigation away and back creates new component → new subscribe → new deref → fires.
