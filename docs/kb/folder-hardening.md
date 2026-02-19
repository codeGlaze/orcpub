# Folder Feature Hardening Patterns

## Context

The character folders feature (from `develop`) went through a hardening pass
after merging into `breaking/2026-stack-modernization`. These patterns apply
to any CRUD feature with optimistic UI and server-backed state.

## Pattern: On-Failure Rollback via Server Re-Fetch

**Problem**: Optimistic UI updates fire immediately (`:db` in `reg-event-fx`),
but if the HTTP request fails, the UI stays out of sync with the server.

**Solution**: Add an `on-failure` handler that re-fetches the canonical state
from the server and replaces the local state.

```clojure
;; Shared failure handler — shows error toast + reconciles state
(reg-event-fx
 ::on-folder-failure
 (fn [{:keys [db]} [_ _response]]
   {:http {:method :get
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-folders-route)
           :on-success [::set-folders-from-response]}
    :dispatch (show-generic-error)}))

;; Every mutation references it
(reg-event-fx
 ::rename-folder
 (fn [{:keys [db]} [_ id new-name]]
   {:db (optimistic-update db ...)
    :http {:method :put ...
           :on-failure [::on-folder-failure]}}))
```

**Why not rollback with saved state?** Capturing pre-mutation state adds
complexity and can go stale if multiple mutations overlap. A server re-fetch
is simple, correct, and only costs one request on the rare failure path.

**Key**: The `:http` fx handler already shows a generic error when `on-failure`
is nil, but it does NOT rollback the `:db` update. Setting `on-failure` means
you take over error display responsibility.

## Pattern: Client + Server Name Validation

**Problem**: An empty folder name can slip through if neither side validates.

**Solution**: Validate on both sides. Client prevents the request; server
rejects it with 400 as a safety net.

```clojure
;; Client — trim and reject blank before sending
(let [trimmed (clojure.string/trim (str new-name))]
  (when-not (clojure.string/blank? trimmed)
    {:db ... :http ...}))

;; Server — reject blank with 400
(let [trimmed (clojure.string/trim (str folder-name))]
  (if (clojure.string/blank? trimmed)
    {:status 400 :body {:message "Folder name cannot be blank"}}
    (do @(d/transact conn [...]) {:status 200 ...})))
```

**Key**: Client returns `nil` from the event handler (no-op) when the name is
blank. re-frame treats `nil` returns from `reg-event-fx` as no effects.

## Pattern: Named Tempids

**Problem**: `(-> result :tempids first val)` assumes exactly one tempid in the
transaction. If schema evolution adds another, it silently returns the wrong ID.

**Solution**: Use a named tempid string and resolve it explicitly.

```clojure
;; Before — fragile
(let [result @(d/transact conn [{::folder/name name ::folder/owner user}])
      new-id (-> result :tempids first val)] ...)

;; After — explicit
(let [tempid "new-folder"
      result @(d/transact conn [{:db/id tempid
                                  ::folder/name name
                                  ::folder/owner user}])
      new-id (d/resolve-tempid (d/db conn) (:tempids result) tempid)] ...)
```

## Pattern: Interceptor Wrapping

**Problem**: A plain map `{:name :check-foo :enter (fn ...)}` works in most
Pedestal versions because route processing coerces it, but it's inconsistent
with other interceptors that use `interceptor/interceptor`.

**Solution**: Always wrap with `interceptor/interceptor` for consistency. Also
a good opportunity to add a 404 path for missing entities.

```clojure
(def check-folder-owner
  (interceptor/interceptor
   {:name :check-folder-owner
    :enter (fn [context]
             (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                   owner (folder-owner db id)]
               (cond
                 (nil? owner) (terminate-request context 404 "Folder not found")
                 (= (:user identity) owner) context
                 :else (terminate-request context 401 "..."))))}))
```

## Pattern: Case Statement Default Clause

**Problem**: `(case status 200 ... 401 ... 500 ...)` throws
`IllegalArgumentException: No matching clause` for any status not listed
(e.g., 502, 503).

**Solution**: Add a default clause. In most HTTP error contexts, show a
generic error for anything that isn't 200 or 401.

```clojure
;; Before — crashes on 502, 503, etc
(case (:status response)
  200 (dispatch [::set-data (:body response)])
  401 (dispatch [:route-to-login])
  500 (dispatch (show-generic-error)))

;; After — catches all error statuses
(case (:status response)
  200 (dispatch [::set-data (:body response)])
  401 (dispatch [:route-to-login])
  (dispatch (show-generic-error)))
```

## Bug Found During Review

`events/show-generic-error` in `subs.cljs` — the `events` namespace alias
didn't exist in that file. `show-generic-error` was already `:refer`'d from
`event-utils`. Only triggered on HTTP 500 when loading folders (dead code
path until it isn't).

**Lesson**: When the `:http` fx handler's default error path is "good enough,"
it's easy to miss that a custom error handler references the wrong namespace.
Compile doesn't catch undefined namespace references in CLJS if the call site
is inside a `go` block (async, not eagerly evaluated).
