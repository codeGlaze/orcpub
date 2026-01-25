# Upgrade Dependencies & Compatibility Notes

This document tracks all dependency changes, compatibility tweaks, and required workarounds as the project is upgraded (Java, Datomic, Pedestal, etc).

## Java 9+/21 & Servlet API
- **Issue:** `javax.servlet.http.HttpServletRequest` is not included in Java 9+.
- **Solution:** Add `[javax.servlet/javax.servlet-api "4.0.1"]` to `project.clj`.
- **Reference:** See Pedestal and Ring issues for details.

## Datomic Pro
- **Upgrade:** Migrated from Datomic Free to Datomic Pro for Java 21 support.
- **Install:** Peer JAR must be placed in `lib/com/datomic/datomic-pro/<version>/`.
- **Reference:** See `AGENTS.md` and `docker/datomic/README.md`.

## Pedestal 0.7.x - Content Security Policy (Nonce-Based Implementation)

**Background:** Pedestal 0.7.x adds a strict Content-Security-Policy (CSP) header by default with `'strict-dynamic'`. This is a security feature that protects against XSS attacks by requiring scripts to have a cryptographic nonce.

**Implementation:** We've implemented proper nonce-based CSP:

- Every request generates a unique 128-bit cryptographic nonce
- All `<script>` tags include the nonce attribute
- CSP header uses `'strict-dynamic'` with the nonce
- Scripts without a valid nonce are blocked (XSS protection)

**CSP Policy Options:**

| Value | Behavior |
|-------|----------|
| `strict` | Nonce-based CSP with `'strict-dynamic'` **(default, recommended)** |
| `permissive` | Allows same-origin scripts without nonces (legacy fallback) |
| `none` | Disables CSP entirely (debugging only) |

**Configuration:**
```bash
# Docker/production (default is strict, no config needed)
docker run ...

# Or explicitly set:
docker run -e CSP_POLICY=strict ...

# .env file (default is strict)
CSP_POLICY=strict
```

**Dev mode:** When `DEV_MODE=true`, CSP automatically falls back to `permissive` because Figwheel/ClojureScript dev builds generate inline scripts (`goog/base.js`, `cljs_deps.js`) that are incompatible with strict CSP. Production builds compile everything into a single file, so strict CSP works.

**Files changed:**
- `src/clj/orcpub/csp.clj` - Nonce generation and CSP header building
- `src/clj/orcpub/config.clj` - CSP policy configuration
- `src/clj/orcpub/pedestal.clj` - Nonce interceptor (generates nonce, sets header)
- `src/clj/orcpub/routes.clj` - Passes nonce to HTML renderer
- `src/clj/orcpub/index.clj` - All script tags include nonce attribute

**Why strict-dynamic + nonces:** It's an anti-XSS defense. If an attacker injects HTML (XSS), they could add `<script src="https://evil.com/malware.js">`. With `strict-dynamic` + nonces, only scripts with the correct per-request nonce will run. Injected scripts can't guess the nonce.

### Technical Details & Lessons Learned

**ClojureScript dev builds and CSP:**

ClojureScript dev builds (Figwheel) use `document.write()` to load scripts dynamically:
```javascript
// Dev build orcpub.js is just a loader:
document.write('<script src="goog/base.js"></script>');
document.write('<script src="cljs_deps.js"></script>');
// ... more document.write calls
```

The `'strict-dynamic'` CSP directive allows scripts loaded via `createElement('script')` but blocks `document.write()` scripts. This is a known gap in the CLJS tooling ecosystem - Google Closure Library has `goog.getScriptNonce()` for CSP support, but the ClojureScript dev build doesn't use it.

**Solution: Report-Only mode in development**

Instead of disabling CSP in dev, we use `Content-Security-Policy-Report-Only`:
- **Dev mode**: Violations are logged to browser console but scripts aren't blocked
- **Prod mode**: Violations block script execution (real XSS protection)

This catches CSP issues during development (missing nonces on new scripts) while Figwheel continues to work.

**Why production builds work:**

Production CLJS builds (`lein prod-build`) use `:optimizations :advanced` which compiles everything into a single `orcpub.js` file. No `document.write()`, no dynamic loading - just one file with a nonce.

**Implementation approach:**

We chose to work WITH Pedestal rather than around it:
1. Pedestal's `secure-headers` interceptor is static (configured at startup)
2. Nonces must be unique per-request
3. Our `nonce-interceptor` generates nonces and sets CSP headers dynamically
4. Response headers from our interceptor override Pedestal's static headers (by design)
5. This is the [recommended approach](https://pedestal-users.narkive.com/nOdeCHui/csp-headers-issue) per Pedestal community

**CSP header directives (strict mode):**
```
default-src 'self';
script-src 'strict-dynamic' 'nonce-{base64}';
style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;
font-src 'self' https://fonts.gstatic.com;
img-src 'self' data: https:;
connect-src 'self';
object-src 'none';
base-uri 'self';
frame-ancestors 'self';
form-action 'self'
```

Key decisions:
- **No `'unsafe-inline'` for scripts** - Would defeat nonce protection
- **No `https: http:` fallbacks** - Modern browsers support `'strict-dynamic'`
- **Full security directives** - `base-uri`, `frame-ancestors`, `form-action`, `connect-src` for comprehensive protection

### Deep Dive: CLJS Ecosystem and CSP Support

This section documents extensive research into why CSP is challenging with ClojureScript development and the solutions available.

#### The Core Problem

ClojureScript dev builds use Google Closure Library's module system, which loads scripts via `document.write()`:

```javascript
// Generated dev orcpub.js (14 lines):
window.CLOSURE_UNCOMPILED_DEFINES = {"goog.DEBUG":true,...};
window.CLOSURE_NO_DEPS = true;
document.write('<script src="/js/compiled/out/goog/base.js"></script>');
document.write('<script src="/js/compiled/out/goog/deps.js"></script>');
document.write('<script src="/js/compiled/out/cljs_deps.js"></script>');
// ... more document.write calls
```

The CSP `'strict-dynamic'` directive:
- ✅ Allows scripts loaded via `createElement('script')` by a nonced parent
- ❌ Blocks scripts loaded via `document.write()` (security reasons - synchronous, dangerous)

#### What the Ecosystem Provides (and Doesn't)

| Component | CSP Support | Notes |
|-----------|-------------|-------|
| **Google Closure Library** | ✅ Full | Has `goog.getScriptNonce()` and `goog.ENABLE_CHROME_APP_SAFE_SCRIPT_LOADING` |
| **ClojureScript Compiler** | ✅ Has `:target-fn` | Allows custom loader generation |
| **Figwheel-main** | ❌ None | Doesn't pass through `:target-fn`, no CSP options |
| **shadow-cljs** | ❌ None | Same issue ([GitHub #566](https://github.com/thheller/shadow-cljs/issues/566)) |

**The gap:** The low-level support exists in Closure Library, and ClojureScript compiler has `:target-fn` to customize the loader, but the build tools (figwheel-main, shadow-cljs) don't expose these capabilities.

#### Google Closure Library's CSP Support

Closure Library has built-in CSP support that the CLJS ecosystem doesn't use:

```javascript
// goog.getScriptNonce() - finds nonce from existing script tags
var nonce = goog.getScriptNonce();

// ENABLE_CHROME_APP_SAFE_SCRIPT_LOADING - makes goog.require() use createElement()
window.CLOSURE_DEFINES = {
  'goog.ENABLE_CHROME_APP_SAFE_SCRIPT_LOADING': true
};
```

If enabled, `goog.require()` uses `createElement('script')` instead of `document.write()`, which works with `'strict-dynamic'`.

#### ClojureScript `:target-fn` Option

The ClojureScript compiler has a `:target-fn` option (added in 1.10.741) that allows custom loader generation:

```clojure
;; In build config:
{:target-fn 'my.namespace/custom-loader
 :main 'my.app
 ...}

;; The function receives compiler opts and returns JavaScript string:
(defn custom-loader [opts]
  (str "// Custom loader JS here..."))
```

**We verified this works** with the ClojureScript API directly:
```clojure
(cljs/build "src/cljs"
  {:target-fn 'orcpub.cljs-loader/csp-loader
   :main 'orcpub.core
   ...})
;; Output: CSP-compatible loader using createElement() ✓
```

**However, figwheel-main doesn't pass through `:target-fn`** - it throws a NullPointerException in `cljs.closure/output-bootstrap` when you try to use it.

#### Our Solution: Static CSP Loader

Since figwheel-main doesn't support `:target-fn`, we use a static loader file:

**`resources/public/js/csp-loader.js`:**
```javascript
(function() {
  // Get nonce from this script tag
  var nonce = document.currentScript.nonce;

  // Enable CSP-safe loading in Closure Library
  window.CLOSURE_DEFINES = {
    'goog.ENABLE_CHROME_APP_SAFE_SCRIPT_LOADING': true
  };

  // Load scripts via createElement (CSP-compatible)
  function loadScript(src) {
    var script = document.createElement('script');
    script.src = src;
    script.nonce = nonce;  // Propagate nonce
    document.head.appendChild(script);
  }

  // Load deps, then goog.require modules
  // ...
})();
```

**In index.clj:**
```clojure
(if devmode?
  (script-tag {:src "/js/csp-loader.js" :nonce nonce})
  (script-tag {:src "/js/compiled/orcpub.js" :nonce nonce}))
```

#### Why This Matters

1. **Production builds work perfectly** - `:optimizations :advanced` bundles everything into one file
2. **Dev builds need the workaround** - Figwheel's loader uses `document.write()`
3. **Report-Only mode catches issues** - Violations logged but not blocked
4. **Custom loader enables enforcing CSP in dev** - Full CSP protection during development

#### Files Implementing This

| File | Purpose |
|------|---------|
| `resources/public/js/csp-loader.js` | Static CSP-compatible dev loader |
| `src/clj/orcpub/cljs_loader.clj` | Function for `:target-fn` (works but figwheel doesn't use it) |
| `src/clj/orcpub/index.clj` | Switches between loaders based on dev mode |
| `src/clj/orcpub/csp.clj` | Nonce generation, CSP header building |
| `src/clj/orcpub/pedestal.clj` | Nonce interceptor with Report-Only for dev |

#### Future: Upstream Fixes

The proper fix would be for figwheel-main to either:
1. Pass through `:target-fn` to the ClojureScript compiler
2. Add native CSP/nonce support to its generated loader
3. Use `goog.ENABLE_CHROME_APP_SAFE_SCRIPT_LOADING` by default

Until then, our static loader workaround provides full CSP protection.

---

## Other Notable Upgrades
- **Guava:** Upgraded to `32.1.2-jre` for Java 21 compatibility.
- **Jackson:** Upgraded to `2.15.2` for security and compatibility.
- **Pedestal:** Using `0.7.0` for modern Ring middleware support (see CSP note above).

## How to Use This Document
- Add a new section for each upgrade, dependency change, or workaround.
- Link to this file from README.md and reference in code comments as needed.
- Use for onboarding and troubleshooting during upgrades.

---
_Last updated: January 2026_
