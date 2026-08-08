# Pedestal 0.5.1 → 0.7.0

## Version Pin

Pedestal is pinned to **0.7.0** in `project.clj`. This is intentional:

- **0.7.0** uses Jetty 11, which is compatible with figwheel-main's Ring adapter
- **0.7.1+** and **0.8.x** use Jetty 12, which removes `ScopedHandler` — causing `NoClassDefFoundError` at startup when figwheel-main is running

This pin stays until figwheel-main supports Jetty 12.

## Breaking Changes

### 1. Interceptor Maps Must Be Wrapped

Pedestal 0.7 requires explicit interceptor coercion. Plain maps cause `AssertionError`.

**Before** (0.5.1):
```clojure
(def db-interceptor
  {:name :db-interceptor
   :enter (fn [context] ...)})
```

**After** (0.7.0):
```clojure
(defn db-interceptor [conn]
  (interceptor/interceptor
   {:name :db-interceptor
    :enter (fn [context] ...)}))
```

Changed in: `src/clj/orcpub/pedestal.clj` — `db-interceptor` and `etag-interceptor`

### 2. Content Security Policy (CSP)

Pedestal 0.5.1 had no CSP support. Pedestal 0.7.0 adds a default CSP via `::http/secure-headers` with `strict-dynamic`.

In CSP Level 3 browsers (all modern browsers), `strict-dynamic` causes the browser to **ignore** `unsafe-inline` and `unsafe-eval`. Without nonces, this breaks:
- Figwheel's `document.write()` for hot-reload script injection
- Any inline `<script>` tags in HTML templates

**Solution**: Nonce-based CSP with per-request cryptographic nonces.

**New files**:
- `src/clj/orcpub/csp.clj` — nonce generation + CSP header builder
- CSP config section in `src/clj/orcpub/config.clj`

**How it works**:

1. `nonce-interceptor` (in `pedestal.clj`) generates a 128-bit nonce per request
2. The nonce is stored in `[:request :csp-nonce]` for templates to read
3. On `:leave`, the interceptor sets the `Content-Security-Policy` header with the nonce
4. **Dev mode**: The nonce interceptor is a no-op — no nonce is generated, no CSP header is added. Pedestal 0.7's built-in `secure-headers` still applies its own defaults, but the custom nonce header is skipped entirely. This avoids flooding the browser console with Report-Only violations from Figwheel's inline scripts.
5. **Prod mode**: Enforcing `Content-Security-Policy` with per-request nonces

**Configuration** via `CSP_POLICY` env var (see `.env.example`):
- `strict` (default) — nonce-based with `strict-dynamic`
- `permissive` — allows `unsafe-inline` + `unsafe-eval` (legacy fallback)
- `none` — disables CSP entirely

### 3. Date Parsing

`parse-date` in `pedestal.clj` was rewritten from clj-time to java-time.api. Same alias (`t`), different implementation. See [library-upgrades.md](library-upgrades.md).

## Files Changed

| File | What Changed |
|------|-------------|
| `src/clj/orcpub/pedestal.clj` | Interceptor wrapping, nonce-interceptor, java-time parse-date |
| `src/clj/orcpub/csp.clj` | New — nonce generation + CSP header building |
| `src/clj/orcpub/config.clj` | New — CSP policy config (`get-csp-policy`, `strict-csp?`, `get-secure-headers-config`) |
| `src/clj/orcpub/system.clj` | `::http/secure-headers` now uses `config/get-secure-headers-config` |
