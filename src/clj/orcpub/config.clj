(ns orcpub.config
  (:require [environ.core :refer [env]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def default-datomic-uri "datomic:dev://localhost:4334/orcpub")

(defn read-secret
  "Read a Docker secret from /run/secrets/<name>, or nil if not mounted.
  Trims trailing whitespace (secret files often end with a newline)."
  [name]
  (let [f (io/file "/run/secrets" name)]
    (when (.exists f)
      (not-empty (str/trim (slurp f))))))

(defn datomic-env
  "Return the raw DATOMIC_URL environment value or nil if unset." []
  (or (env :datomic-url)
      (some-> (System/getenv "DATOMIC_URL") not-empty)))

(defn datomic-password
  "Return DATOMIC_PASSWORD from Docker secret, env var, or nil.
  Resolution order: /run/secrets/datomic_password > DATOMIC_PASSWORD env var." []
  (or (read-secret "datomic_password")
      (env :datomic-password)
      (some-> (System/getenv "DATOMIC_PASSWORD") not-empty)))

(defn signature
  "Return SIGNATURE from Docker secret, env var, or nil.
  Resolution order: /run/secrets/signature > SIGNATURE env var." []
  (or (read-secret "signature")
      (env :signature)
      (some-> (System/getenv "SIGNATURE") not-empty)))

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
  (let [policy (or (env :csp-policy)
                   (System/getenv "CSP_POLICY")
                   "strict")]
    (str/lower-case policy)))

(def default-site-homebrew-dir
  "Directory the app reads host-provided homebrew (.orcbrew) files from.
   Overridable via SITE_HOMEBREW_DIR. The default suits local dev (run from
   the repo root); the Docker image points it at the mounted /srv/homebrew."
  "deploy/homebrew")

(defn site-homebrew-dir []
  (or (some-> (env :site-homebrew-dir) not-empty)
      default-site-homebrew-dir))

(defn- sha1-hex [^String s]
  (let [md (java.security.MessageDigest/getInstance "SHA-1")]
    (->> (.digest md (.getBytes s "UTF-8"))
         (map #(format "%02x" %))
         (apply str))))

(defn read-site-homebrew
  "Read every *.orcbrew file in the site homebrew dir (sorted by name so the
   version hash is stable) and return
     {:version <sha1-hex or nil> :sources [<raw .orcbrew text> ...]}.

   The raw text is returned unparsed — the client runs each source through the
   same validate-import pipeline the UI import uses, so any file the site
   accepts on import works here too. Returns {:version nil :sources []} when the
   directory is missing or empty (caller then injects nothing). Never throws: a
   single unreadable file is skipped rather than failing the whole page.

   Zero-arg reads from (site-homebrew-dir); the dir-path arity is for testing."
  ([] (read-site-homebrew (site-homebrew-dir)))
  ([dir-path]
  (let [dir (io/file dir-path)]
    (if (and (.exists dir) (.isDirectory dir))
      (let [files   (->> (.listFiles dir)
                         (filter #(and (.isFile ^java.io.File %)
                                       (str/ends-with? (.getName ^java.io.File %) ".orcbrew")))
                         (sort-by #(.getName ^java.io.File %)))
            sources (vec (keep (fn [f]
                                 (try
                                   (not-empty (slurp f))
                                   (catch Exception e
                                     (println "WARN: could not read site homebrew file"
                                              (.getName ^java.io.File f) "-" (.getMessage e))
                                     nil)))
                               files))]
        {:version (when (seq sources) (sha1-hex (str/join "\n" sources)))
         :sources sources})
      {:version nil :sources []}))))

(defn dev-mode?
  "Returns true when running in dev mode (DEV_MODE env var is 'true').
   Env vars are strings — (boolean \"false\") is true in Clojure, so we
   must compare against the string \"true\" explicitly."
  []
  (= "true" (str/lower-case (or (env :dev-mode) ""))))

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
