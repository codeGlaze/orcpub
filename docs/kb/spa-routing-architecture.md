# SPA Routing Architecture

Verified findings from the email preferences implementation session.

## Route Registration (3 places)

Adding a new page route requires changes in **three** locations:

### 1. Route Map — `src/cljc/orcpub/route_map.cljc`
- Define the route constant: `(def unsubscribe-success-route :unsubscribe-success)`
- Add to bidi route table: `"unsubscribe-success" unsubscribe-success-route`
- This is a `.cljc` file — shared between server and client

### 2. Server Index Pages — `src/clj/orcpub/routes.clj` → `index-page-paths`
- Lines ~1289-1326: vector of route refs that should serve the SPA index HTML
- Without this, Pedestal returns 404 for the URL
- Pattern: `[route-map/unsubscribe-success-route]`

### 3. Client Pages Map — `web/cljs/orcpub/core.cljs` → `pages`
- Lines ~33-75: map from route keyword → Reagent component
- Pattern: `routes/unsubscribe-success-route views/unsubscribe-success`
- If the route matches but no component is in the map, the SPA renders nil

### Bonus: Login Routes Set — `src/cljs/orcpub/dnd/e5/events.cljs` → `login-routes`
- Lines ~1828-1838: set of routes that don't require authentication
- Pages like verify-success, password-reset-success, unsubscribe-success must be in this set
- Without this, unauthenticated users get redirected to login

## Route Flow

1. User visits `/unsubscribe-success`
2. Pedestal matches against `index-page-paths` → serves `index.html`
3. SPA loads, `db.cljs/parse-route` calls `bidi/match-route` → `{:handler :unsubscribe-success}`
4. `core.cljs/main-view` looks up `(pages :unsubscribe-success)` → component fn
5. Component renders

## Server-Side Route Handlers vs SPA Pages

- **API routes** (e.g., `/unsubscribe`, `/register`, `/login`): Wired in `routes.clj` route table with handler functions
- **SPA pages** (e.g., `/unsubscribe-success`, `/verification-successful`): Served via `index-page-paths` → SPA renders the component
- **Server-rendered pages** (e.g., `/privacy-policy`, `/terms-of-use`): Dedicated handler returns HTML (via `privacy.clj`)
- Redirects between them work: API handler can `(redirect route-map/unsubscribe-success-route)` → 302 → SPA loads

## `user-for-email` / `first-user-by` Returns Non-nil for Missing Users

**Verified bug/gotcha**: `first-user-by` calls `(d/pull db '[*] nil)` when no user is found. Datomic `d/pull` with a nil entity-id returns `{:db/id nil}` (a map, which is truthy). Therefore:

```clojure
;; BAD — always truthy even for unknown email:
(if-let [{:keys [:db/id]} (user-for-email db email)]
  ...)

;; GOOD — explicitly check the id:
(let [{:keys [:db/id]} (user-for-email db email)]
  (if id ...))
```

This affects any new code that calls `user-for-email` and uses `if-let` on the result.

## The `pages` Map in `core.cljs`

The entry point file is `web/cljs/orcpub/core.cljs` (NOT under `src/`). It's in the `web/` source path (configured in `dev.cljs.edn` watch-dirs). This is where:
- `pages` map lives
- `main-view` component does route dispatch
- `Html5History` listener fires `handle-url-change`
- React 18 `createRoot` mounts the app
