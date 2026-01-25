# CSP Enforcing Mode Experiment (January 2026)

## TL;DR
**Don't bother trying to get enforcing CSP working in dev mode with figwheel-main.**
Use Report-Only CSP in dev, enforcing in prod. This is the pragmatic solution.

## The Problem
CSP `'strict-dynamic'` blocks `document.write()` scripts. Figwheel's dev loader uses `document.write()`.

## What We Tried
1. **Custom static loader** (`csp-loader.js`) - Uses `createElement('script')` instead of `document.write()`
2. **ClojureScript `:target-fn`** - Compiler option for custom loader generation

## What We Learned

| Component | CSP Support | Status |
|-----------|-------------|--------|
| Google Closure Library | `goog.ENABLE_CHROME_APP_SAFE_SCRIPT_LOADING` | Built-in |
| ClojureScript Compiler | `:target-fn` option | Works |
| figwheel-main | Doesn't pass through `:target-fn` | **Gap** |
| shadow-cljs | Same issue (GitHub #566) | **Gap** |

**The `:target-fn` option works** when calling ClojureScript compiler directly, but figwheel-main doesn't pass it through. We verified this by testing directly with `cljs.build.api`.

## Why We Abandoned This

1. Report-Only CSP in dev catches violations without blocking
2. Prod builds use `:optimizations :advanced` = single bundled file (no loader issues)
3. The workaround adds complexity for minimal benefit
4. figwheel-main is in maintenance mode (last release Jan 2025)

## If You Want to Try Again

The files below show the approach. To use:

1. Add `:target-fn` to `dev.cljs.edn`:
   ```clojure
   {:target-fn orcpub.cljs-loader/csp-loader}
   ```

2. figwheel-main will throw NullPointerException in `cljs.closure/output-bootstrap`

3. To verify `:target-fn` works without figwheel:
   ```clojure
   (require '[cljs.build.api :as bapi])
   (bapi/build "src/cljs" {:main 'orcpub.dnd.e5.core
                           :output-to "test-output.js"
                           :output-dir "test-out"
                           :target-fn 'orcpub.cljs-loader/csp-loader})
   ```

## Current Solution (What We Use)

- **Dev mode**: `Content-Security-Policy-Report-Only` header (logs, doesn't block)
- **Prod mode**: `Content-Security-Policy` header with nonces (enforcing)

See `src/clj/orcpub/pedestal.clj` for implementation.

## References

- [Google CSP Guide](https://csp.withgoogle.com/docs/adopting-csp.html)
- [ClojureScript :target-fn](https://clojurescript.org/reference/compiler-options)
- [shadow-cljs Issue #566](https://github.com/thheller/shadow-cljs/issues/566)
