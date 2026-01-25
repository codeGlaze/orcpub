(ns orcpub.config
  (:require [environ.core :refer [env]]
            [clojure.string :as str]))

(def default-datomic-uri "datomic:dev://localhost:4334/orcpub")

(defn datomic-env
  "Return the raw DATOMIC_URL environment value or nil if unset." []
  (or (env :datomic-url)
      (some-> (System/getenv "DATOMIC_URL") not-empty)))

(defn get-datomic-uri
  "Return the Datomic URI from the environment or the default.

  Prefers the raw env value (from `datomic-env`), otherwise returns a safe
  local development default (datomic:dev://localhost:4334/orcpub)."
  []
  (or (datomic-env)
      default-datomic-uri))

;; Content Security Policy configuration
;; CSP_POLICY environment variable options:
;;   - "strict"     : Nonce-based CSP with 'strict-dynamic' (default, maximum security)
;;   - "permissive" : Allows same-origin scripts without strict-dynamic (legacy fallback)
;;   - "none"       : Disables CSP entirely (not recommended for production)

(def permissive-csp-settings
  "CSP that allows same-origin scripts without strict-dynamic.
   Compatible with traditional <script src> tags. Less secure than strict mode."
  {:default-src "'self'"
   :script-src "'self' 'unsafe-inline' 'unsafe-eval' https://fonts.googleapis.com"
   :style-src "'self' 'unsafe-inline' https://fonts.googleapis.com"
   :font-src "'self' https://fonts.gstatic.com"
   :img-src "'self' data: https:"
   :object-src "'none'"})

(defn get-csp-policy
  "Return the CSP policy from CSP_POLICY env var. Defaults to 'strict'."
  []
  (let [policy (or (env :csp-policy)
                   (System/getenv "CSP_POLICY")
                   "strict")]
    (str/lower-case policy)))

(defn dev-mode?
  "Returns true when running in dev mode (DEV_MODE env var is truthy).
   Used by CSP to determine if Figwheel/CLJS dev builds are in use.
   See also: index.clj which uses the same env var pattern."
  []
  (boolean (env :dev-mode)))

(defn strict-csp?
  "Returns true when CSP_POLICY=strict (regardless of dev mode).

   When true, nonce-interceptor generates per-request nonces and adds them
   to script tags. The header type depends on mode:
   - Dev mode: Content-Security-Policy-Report-Only (violations logged, not blocked)
   - Prod mode: Content-Security-Policy (violations blocked)

   This allows catching CSP issues during development while still allowing
   Figwheel's document.write() scripts to execute."
  []
  (= "strict" (get-csp-policy)))

(defn get-secure-headers-config
  "Configure Pedestal secure-headers based on CSP_POLICY env var.

   - strict: Disables Pedestal's static CSP (nonce-interceptor handles it dynamically)
   - permissive: Uses static permissive CSP settings
   - none: Disables CSP entirely"
  []
  (cond
    ;; Strict mode - nonce-interceptor handles CSP dynamically
    ;; (uses Report-Only in dev, enforcing in prod)
    (= "strict" (get-csp-policy))
    {:content-security-policy-settings nil}

    ;; CSP disabled
    (= "none" (get-csp-policy))
    {:content-security-policy-settings nil}

    ;; Default to permissive
    :else
    {:content-security-policy-settings permissive-csp-settings}))
