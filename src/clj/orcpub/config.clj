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

(defn- positive-int-env
  "Reads `names` in order and returns the first that parses as a positive
   integer, else `default`. A value that is present but unparseable or
   non-positive is ignored and reported, so a typo falls back to the default
   rather than failing the boot or silently meaning zero."
  [names default]
  (or (some (fn [n]
              (when-let [raw (not-empty (or (env (keyword (str/lower-case (str/replace n "_" "-"))))
                                            (System/getenv n)))]
                (let [v (try (Integer/parseInt (str/trim raw)) (catch NumberFormatException _ nil))]
                  (if (and v (pos? v))
                    v
                    (do (println (format "config: %s=%s is not a positive integer; using %d"
                                         n raw default))
                        nil)))))
            names)
      default))

(def ^:private available-processors
  (delay (.availableProcessors (Runtime/getRuntime))))

(defn get-http-max-threads
  "Size of Jetty's worker pool, from ORCPUB_HTTP_MAX_THREADS.

   nil leaves Pedestal's own default, which is `(max 50 ...)` and stays at 50
   until roughly sixteen cores. This caps how many requests of any kind are in
   flight; the rest queue in the accept backlog."
  []
  (positive-int-env ["ORCPUB_HTTP_MAX_THREADS"] nil))

(defn get-pdf-concurrency
  "How many character sheets may be generated at once, from ORCPUB_PDF_CONCURRENCY.

   Bounded separately from the HTTP pool so a rush of exports cannot take the
   whole site down with it: requests past this limit wait for a slot, and the
   pages, logins and saves keep their own workers.

   Sizing: an export in flight holds roughly 11 MB of heap, so the ceiling is
   about (usable heap - 100 MB) / 11 MB. Throughput is bounded by cores, not by
   this number -- raising it past what the cores can chew through lengthens the
   queue without shortening the wait. Defaults to twice the core count, minimum
   eight."
  []
  (positive-int-env ["ORCPUB_PDF_CONCURRENCY"] (max 8 (* 2 @available-processors))))

(defn get-pdf-max-caster-sections
  "Most spellcasting sections one sheet may be grown to, from
   ORCPUB_PDF_MAX_CASTER_SECTIONS.

   The caster count comes from the field NAMES in the request -- the largest N in
   spellcasting-class-N -- so without a ceiling a body of a few dozen bytes can
   ask for thousands of cloned pages at about 14 MB each. Thirteen is every class
   in the game, which no character can exceed."
  []
  (positive-int-env ["ORCPUB_PDF_MAX_CASTER_SECTIONS"] 13))

(defn get-pdf-max-cards
  "Most cards of one kind a single export will print, from ORCPUB_PDF_MAX_CARDS.

   Nine to a page, and the caller says how many: a 2 MB body holds about 60,000
   spell entries, which is 13,000 pages and a quarter of an hour holding an export
   slot. Two hundred is far past a real character -- a level 20 wizard's spellbook
   is about 44 -- and bounds the work a request can buy."
  []
  (positive-int-env ["ORCPUB_PDF_MAX_CARDS"] 200))

(defn get-pdf-max-retries
  "How many times the busy page retries itself before it waits for the person,
   from ORCPUB_PDF_MAX_RETRIES.

   Three covers the queue draining in the realistic case. The elapsed ceiling
   matters more than the count, and the page enforces one as well: the point is
   to spare someone a wait they would abandon anyway, not to retry forever."
  []
  (positive-int-env ["ORCPUB_PDF_MAX_RETRIES"] 3))

(defn get-pdf-queue-timeout-ms
  "How long an export waits for a slot before the server says it is busy, from
   ORCPUB_PDF_QUEUE_TIMEOUT_MS. Past this the request is answered 503 with a
   Retry-After rather than held open until the browser gives up."
  []
  (positive-int-env ["ORCPUB_PDF_QUEUE_TIMEOUT_MS"] 30000))

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
