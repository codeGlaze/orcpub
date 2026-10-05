(ns orcpub.fork.integrations
  "Optional third-party <head> integrations.
   Configure via environment variables; disabled when unset.
   Fork overrides: uncomment examples and add real service config."
  ;; `env` is unused here on purpose: every integration below ships commented out,
  ;; and a fork uncommenting one reads its config through it. Dropping the require
  ;; would make the first thing a forker does a compile error.
  #_{:clj-kondo/ignore [:unused-namespace]}
  (:require [orcpub.env :as env]))

;; ─── How to add an integration ───────────────────────────────────────
;;
;; 1. Gate its config on an env var:  (def my-service-id (env/value :my-service-id))
;; 2. Write a tag fn of `nonce` returning hiccup, or nil when disabled; give each <script> :nonce.
;; 3. Call it from head-tags below, returning a flat seq of the enabled tags.

;; ─── Client-Side Config Bridge ──────────────────────────────────
;; index.clj injects client-config into <head> as window.__INTEGRATIONS__ JSON, which
;; integrations.cljs reads at namespace load time. Working example: branding.clj/client-config.

(defn client-config
  "Map of integration config for CLJS injection. Empty by default.
   Fork overrides: return env-var-gated config values for CLJS components."
  []
  {})

(def csp-domains
  "Extra CSP domains required by enabled integrations.
   Returns {:connect-src [\"https://...\"] :frame-src [\"https://...\"]}.
   csp.clj merges these into the Content-Security-Policy header."
  {})

(defn head-tags
  "All third-party integration tags for <head>.
   Returns a flat seq of hiccup elements, empty when nothing is configured.
   Fork overrides: add integration tag calls here."
  [_nonce]
  ())
