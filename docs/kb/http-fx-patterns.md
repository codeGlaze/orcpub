# :http Effect Handler Patterns

## How the :http fx works

The custom `:http` effect handler is registered in `events.cljs` (~line 1698).
It wraps `cljs-http` and dispatches re-frame events on completion.

```clojure
(reg-fx
 :http
 (fn [{:keys [on-success on-failure on-unauthorized auth-token] :as cfg}]
   ;; ... makes HTTP request ...
   ;; On response:
   (dispatch [:set-loading false])
   (if (<= 200 (:status response) 299)
     (when on-success (dispatch (conj on-success response)))
     (if (= 401 (:status response))
       (if on-unauthorized
         (dispatch (conj on-unauthorized response))
         (dispatch [:route-to-login]))
       (if on-failure
         (dispatch (conj on-failure response))
         (dispatch (show-generic-error)))))))
```

## on-success / on-failure MUST be dispatch vectors

The handler does `(dispatch (conj on-success response))`. This means:

- `on-success` must be a vector like `[:event-id]` or `[:event-id extra-arg]`
- The response is conj'd as the last element: `[:event-id response]`

## Bug: Eager JS calls in map literals

ClojureScript evaluates ALL values in a map literal when the map is constructed.
A bare JS interop call like `.reload` runs immediately:

```clojure
;; BROKEN - .reload runs when the map is constructed (on button click),
;; NOT when the HTTP response arrives
{:http {:method :post
        :url "/api/parties"
        :on-success (.reload js/window.location true)}}

;; CORRECT - dispatch vector, evaluated by the fx handler after response
{:http {:method :post
        :url "/api/parties"
        :on-success [::make-party-success]}}
```

This bug caused the party creation page to reload immediately on clicking
"Create Party", before the POST even completed.

## Audit result (2026-02-22)

All 20+ `:http` fx usages in events.cljs use proper dispatch vectors.
The party creation bug was an isolated case, not a systemic pattern.

## Missing handlers

Several `:http` calls have no `on-success` handler (fire-and-forget with
optimistic UI updates): `rename-party`, `delete-party`, `delete-custom-item`,
`remove-character`, `unfollow-user`. These fall through to `show-generic-error`
on failure. Not a bug, but no success confirmation toast either.

## Auth headers

Two patterns for authentication:

```clojure
;; Pattern 1: headers map (most handlers)
:headers (authorization-headers db)

;; Pattern 2: auth-token string (load-characters, delete-character, reset-password)
:auth-token (get-auth-token db)
```

Both are supported by the `:http` fx handler. Pattern 2 injects the token
into the request config directly.

## Key files

| File | Role |
|------|------|
| `events.cljs:1698` | `:http` fx registration |
| `event_utils.cljc` | `auth-headers`, `url-for-route` helpers |
