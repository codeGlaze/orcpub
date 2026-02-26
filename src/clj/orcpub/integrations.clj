(ns orcpub.integrations
  "Optional third-party <head> integrations.
   Configure via environment variables; disabled when unset.
   Fork overrides: uncomment examples and add real service config."
  (:require [environ.core :refer [env]]))

;; ─── How to add an integration ───────────────────────────────────────
;;
;; 1. Define env-var-gated config:
;;      (def my-service-id (env :my-service-id))
;;
;; 2. Write a tag function that returns hiccup (or nil when disabled):
;;      (defn- my-service-tag [nonce]
;;        (when (seq my-service-id)
;;          [:script {:nonce nonce :async ""
;;                    :src (str "https://example.com/sdk.js?id=" my-service-id)}]))
;;
;; 3. Add it to head-tags's concat list below.

;; ─── Client-Side Config Bridge ──────────────────────────────────
;; Passes server-side integration config to CLJS components.
;; index.clj injects this as window.__INTEGRATIONS__ JSON in <head>.
;; integrations.cljs reads it at namespace load time.
;; See branding.clj/client-config for a working example.

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
