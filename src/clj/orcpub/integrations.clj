(ns orcpub.integrations
  "Optional third-party <head> integrations (analytics, SDKs, etc.).
   Configure via environment variables; disabled when unset.
   See commented examples below for the pattern."
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
;;
;;
;; ─── Example: Analytics ──────────────────────────────────────────────
;;
;; (def analytics-url     (env :analytics-url))
;; (def analytics-site-id (env :analytics-site-id))
;;
;; (defn- analytics-tags
;;   "Self-hosted analytics. Returns a list of hiccup elements."
;;   [nonce]
;;   (when (and (seq analytics-url) (seq analytics-site-id))
;;     (list
;;       [:link {:rel "preconnect" :href analytics-url :crossorigin ""}]
;;       [:script {:nonce nonce}
;;        (str "(function(){var u='" analytics-url "';"
;;             "/* tracker init */})();")])))
;;
;;
;; ─── Example: External SDK ────────────────────────────────────────────
;;
;; (def sdk-client (env :sdk-client))
;;
;; (defn- sdk-tag
;;   "External SDK loader."
;;   [nonce]
;;   (when (seq sdk-client)
;;     [:script {:nonce nonce :async ""
;;               :src (str "https://cdn.example.com/sdk.js?client=" sdk-client)
;;               :crossorigin "anonymous"}]))

(def csp-domains
  "Extra CSP domains required by enabled integrations.
   Returns {:connect-src [\"https://...\"] :frame-src [\"https://...\"]}.
   csp.clj merges these into the Content-Security-Policy header."
  ;; (merge-with into
  ;;   (when (seq analytics-url)
  ;;     {:connect-src [analytics-url]})
  ;;   (when (seq sdk-client)
  ;;     {:connect-src ["https://cdn.example.com"]
  ;;      :frame-src   ["https://cdn.example.com"]}))
  {})

(defn head-tags
  "All third-party integration tags for <head>.
   Returns a flat seq of hiccup elements, empty when nothing is configured.
   Uncomment integration calls as you enable them."
  [_nonce]
  ;; (remove nil?
  ;;   (concat
  ;;     [(sdk-tag nonce)]
  ;;     (analytics-tags nonce)))
  ())
