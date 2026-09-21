(ns orcpub.config
  (:require [orcpub.env :as env]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.util Locale]))

(def default-datomic-uri "datomic:dev://localhost:4334/orcpub")

(defn read-secret
  "Read a Docker secret from /run/secrets/<name>, or nil if not mounted.
  Trims trailing whitespace (secret files often end with a newline)."
  [name]
  (let [f (io/file "/run/secrets" name)]
    (when (.exists f)
      (not-empty (str/trim (slurp f))))))

(defn datomic-env
  "Return the raw DATOMIC_URL environment value or nil if unset or blank." []
  (env/value :datomic-url))

(defn datomic-password
  "Return DATOMIC_PASSWORD from Docker secret, env var, or nil.
  Resolution order: /run/secrets/datomic_password > DATOMIC_PASSWORD env var." []
  (or (read-secret "datomic_password")
      (env/value :datomic-password)))

(defn signature
  "Return SIGNATURE from Docker secret, env var, or nil.
  Resolution order: /run/secrets/signature > SIGNATURE env var." []
  (or (read-secret "signature")
      (env/value :signature)))

(defn get-datomic-uri
  "Return the Datomic URI from the environment or the default.

  Prefers the raw env value (from `datomic-env`), otherwise returns a safe
  local development default (datomic:dev://localhost:4334/orcpub).

  If the URL does not contain a ?password= parameter and DATOMIC_PASSWORD
  is set, appends it automatically. This allows admins to keep the password
  out of DATOMIC_URL (e.g. for Docker secrets) while remaining backward
  compatible with URLs that embed the password."
  []
  (let [url (or (datomic-env) default-datomic-uri)
        pw  (datomic-password)]
    (if (and pw (not (str/includes? url "password=")))
      (str url "?password=" pw)
      url)))

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
  ;; env-value, so an EMPTY CSP_POLICY means unset and therefore "strict".
  ;; It used to mean "": not strict, not none, so get-secure-headers-config
  ;; fell through to the static permissive policy. An empty setting silently
  ;; selecting a DIFFERENT and less strict policy than the documented default
  ;; is the opposite of what the blank was meant to express.
  (let [policy (env/value :csp-policy "strict")]
    ;; Locale/ROOT, not str/lower-case: this is an ASCII config token, not
    ;; prose. str/lower-case folds using the default locale, so on a Turkish
    ;; machine "STRICT" becomes "strıct" (dotless i), misses every comparison
    ;; below, and silently falls through to the permissive policy.
    (.toLowerCase ^String policy Locale/ROOT)))

(defn dev-mode?
  "Returns true when running in dev mode (DEV_MODE env var is 'true').
   Env vars are strings — (boolean \"false\") is true in Clojure, so we
   must compare against the string \"true\" explicitly."
  []
  ;; equalsIgnoreCase compares per character rather than by locale casing
  ;; rules, so it is immune to the Turkish-I problem described above. Note the
  ;; receiver order: the literal is first so a nil env var returns false
  ;; instead of throwing.
  (env/flag? :dev-mode))

(defn strict-csp?
  "Returns true when CSP_POLICY=strict (regardless of dev mode).

   When true AND dev-mode? is false, nonce-interceptor generates a per-request
   nonce and sets an ENFORCING Content-Security-Policy header.

   In dev mode it generates no nonce and sets no header at all, so there is no
   CSP from this application -- which is what lets Figwheel's scripts and its
   websocket work. Note the consequence: DEV_MODE defaults to FALSE, so a
   checkout with no .env runs enforcing CSP, and ws://localhost:3449 is absent
   from connect-src. Figwheel's hot reload is then blocked with no obvious
   cause. .env.example sets DEV_MODE=true for exactly this reason.

   There is no Report-Only mode. Earlier revisions of this docstring described
   one; no code has ever emitted Content-Security-Policy-Report-Only."
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
    ;; (enforcing when DEV_MODE is not true; no header at all in dev mode)
    (= "strict" (get-csp-policy))
    {:content-security-policy-settings nil}

    ;; CSP disabled
    (= "none" (get-csp-policy))
    {:content-security-policy-settings nil}

    ;; Default to permissive
    :else
    {:content-security-policy-settings permissive-csp-settings}))
