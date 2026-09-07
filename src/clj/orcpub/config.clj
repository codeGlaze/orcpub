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

(defn redact-secrets
  "Blank out credentials in a connection string so it can be logged.

   A Datomic SQL URI carries the database password in plain sight:

     datomic:sql://datomic?jdbc:postgresql://host:5432/datomic?user=datomic&password=hunter2

   Handles both shapes a credential arrives in -- a `password=`/`secret=`/`token=`
   query parameter, and `scheme://user:pass@host` userinfo. Anything else is returned
   unchanged, so a `datomic:mem://orcpub` or `datomic:dev://localhost:4334/orcpub`
   still reads normally in the log.

   Redacting is not the same as being safe to print: only call this on values that are
   meant to be seen, and never widen what is logged because it is redacted."
  [s]
  (when s
    (-> (str s)
        (str/replace #"(?i)([?&](?:password|passwd|pwd|secret|token|api[-_]?key)=)[^&\s]*" "$1****")
        (str/replace #"(?i)(://[^:/?#\s]+):[^@/?#\s]+@" "$1:****@"))))

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

(defn- env-raw
  "The raw string for an env var name, from environ or the process environment, or nil.
   One implementation so \"is it set?\" and \"what is it?\" can never disagree."
  [n]
  (not-empty (or (env (keyword (str/lower-case (str/replace n "_" "-"))))
                 (System/getenv n))))

(defn- positive-int-env
  "Reads `names` in order and returns the first that parses as a positive
   integer, else `default`. A value that is present but unparseable or
   non-positive is ignored and reported, so a typo falls back to the default
   rather than failing the boot or silently meaning zero."
  [names default]
  (or (some (fn [n]
              (when-let [raw (env-raw n)]
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

(def branding-vars
  "The APP_* fork-branding variables. Reported as a count rather than twenty rows -- a
   banner nobody reads because it scrolls is no better than no banner."
  ["APP_NAME" "APP_TAGLINE" "APP_PAGE_TITLE" "APP_URL" "APP_LOGO_PATH" "APP_OG_IMAGE"
   "APP_HELP_URL" "APP_SUPPORT_EMAIL" "APP_EMAIL_SENDER_NAME"
   "APP_COPYRIGHT_HOLDER" "APP_COPYRIGHT_URL" "APP_COPYRIGHT_YEAR"
   "APP_FIELD_LIMIT_NOTES" "APP_FIELD_LIMIT_NUMBER" "APP_FIELD_LIMIT_TEXT"
   "APP_SOCIAL_BLUESKY" "APP_SOCIAL_DISCORD" "APP_SOCIAL_FACEBOOK"
   "APP_SOCIAL_PATREON" "APP_SOCIAL_REDDIT" "APP_SOCIAL_TWITTER"])

(def settings
  "Everything the boot banner reports, in print order.

   `:secret?` means the VALUE IS NEVER PRINTED -- only whether it is present. This whole
   banner exists because a password reached a log; it must not become the next way one does.
   `:critical?` marks a setting whose absence breaks the site, so it is called out below the
   table rather than left to be spotted in a row.

   `:get` supplies the resolved value where an accessor exists; without one the raw
   environment value is shown, which is right for settings that have no default of ours."
  (concat
   [{:group "runtime"  :var "PORT"}
    {:group "runtime"  :var "DEV_MODE"      :note "dev-only behaviour and relaxed CORS"}
    {:group "database" :var "DATOMIC_URL"   :get #(get-datomic-uri) :redact? true}
    {:group "database" :var "DATOMIC_PASSWORD" :secret? true}
    {:group "security" :var "SIGNATURE"     :secret? true :critical? true
     :note "JWT signing key; every login and API call fails without it"}
    {:group "security" :var "CSP_POLICY"    :note "overrides the built-in policy"}
    {:group "email"    :var "EMAIL_FROM_ADDRESS"}
    {:group "email"    :var "EMAIL_ERRORS_TO"}
    {:group "email"    :var "EMAIL_SECRET_KEY" :secret? true}
    {:group "content"  :var "LOAD_HOMEBREW_URL" :note "homebrew loaded at page load"}]
   [{:group "capacity" :var "ORCPUB_HTTP_MAX_THREADS" :get #(get-http-max-threads)
     :note "worker pool: requests of any kind in flight"}
    {:group "capacity" :var "ORCPUB_PDF_CONCURRENCY" :get #(get-pdf-concurrency)
     :note "sheets generated at once"}
    {:group "capacity" :var "ORCPUB_PDF_QUEUE_TIMEOUT_MS" :get #(get-pdf-queue-timeout-ms)
     :note "how long an export waits for a slot"}
    {:group "capacity" :var "ORCPUB_PDF_MAX_RETRIES" :get #(get-pdf-max-retries)
     :note "busy-page retries before it waits for a click"}
    {:group "capacity" :var "ORCPUB_PDF_MAX_CASTER_SECTIONS" :get #(get-pdf-max-caster-sections)
     :note "most spellcasting sections on one sheet"}
    {:group "capacity" :var "ORCPUB_PDF_MAX_CARDS" :get #(get-pdf-max-cards)
     :note "most cards of one kind per export"}]))

(def ^:private tunables
  "Kept for the capacity rows' typo detection: only these parse as integers."
  (filter #(= "capacity" (:group %)) settings))

(defn report
  "What each setting resolved to, and whether that came from the environment.

   `:set?` is the part an operator actually needs: a bare value cannot tell you whether your
   change was picked up, ignored as a typo, or never set. Secret values are dropped here, not
   at print time, so nothing downstream can leak one by accident."
  []
  (for [{:keys [var get secret? redact? critical? note group]} settings]
    (let [raw      (env-raw var)
          integer? (some? (some #(= var (:var %)) tunables))
          parsed   (when (and raw integer?)
                     (try (Integer/parseInt (str/trim raw)) (catch NumberFormatException _ nil)))
          ignored? (boolean (and raw integer? (not (and parsed (pos? parsed)))))
          value    (cond secret? nil
                         get     (get)
                         :else   raw)]
      {:var var :group group :note note :critical? critical?
       :value (if (and value redact?) (redact-secrets value) value)
       :secret? secret?
       :raw (when-not secret? raw)
       :set? (boolean (and raw (not ignored?)))
       :ignored? ignored?})))

(defn report-lines
  "The boot banner, as lines.

   ASCII only, no colour. This is read in log files and aggregators at least as often as in
   a terminal: escape codes are noise in both, and a stray em dash came back as `?` through
   one of those pipes. Alignment does the work instead."
  ([] (report-lines (report) nil))
  ([rows] (report-lines rows nil))
  ([rows actual]
   (let [;; A secret is reported as present or absent and NEVER by value. `-` where we have
         ;; no number at all, so the VALUE column never argues with SOURCE beside it.
         val    (fn [{:keys [var value secret? set?]}]
                  (cond secret?          (if set? "set" "NOT SET")
                        value            (str value)
                        (get actual var) (str (get actual var))
                        :else            "-"))
         ;; A secret's VALUE column already says set / NOT SET; repeating DEFAULT beside it
         ;; says nothing, and "DEFAULT" next to "NOT SET" reads as though there is a
         ;; default password.
         source (fn [{:keys [set? secret?]}] (cond secret? "" set? "SET" :else "DEFAULT"))
         rows   (vec rows)
         w      (apply max (map (comp count :var) rows))
         vw     (min 46 (apply max (map (comp count val) rows)))
         sw     7
         fmt    (str "  %-" w "s   %-" vw "s   %-" sw "s   %s")
         trim   #(str/replace % #"\s+$" "")
         rule   (apply str (repeat (+ w vw sw 46) "-"))
         brand  (count (filter env-raw branding-vars))]
     (concat
      [rule "  orcpub started" rule
       (trim (format fmt "SETTING" "VALUE" "SOURCE" ""))]
      ;; Grouped, because thirty ungrouped rows is a wall rather than a report.
      ;; partition-by yields GROUPS OF ROWS, not key/value pairs -- destructuring it as
      ;; [g gr] bound the first row map as the heading and printed the whole record.
      (mapcat (fn [gr]
                (cons (str "  [" (str/upper-case (or (:group (first gr)) "other")) "]")
                      (map #(trim (format fmt (:var %) (val %) (source %) (or (:note %) ""))) gr)))
              (partition-by :group rows))
      [(str "  [BRANDING]")
       (format "  %s   %s of %s set" (apply str (repeat w " ")) brand (count branding-vars))]
      (when-let [bad (seq (filter :ignored? rows))]
        (cons rule
              (for [{:keys [var raw]} bad]
                (format "  (!)  %s=%s was ignored: not a positive integer. The default above is in use."
                        var raw))))
      ;; A missing SIGNATURE means every login fails. That is not a row to be spotted.
      (when-let [miss (seq (filter #(and (:critical? %) (not (:set? %))) rows))]
        (cons rule
              (for [{:keys [var note]} miss]
                (format "  (!!) %s is NOT SET -- %s" var (or note "required")))))
      [rule]))))

(defn jetty-max-threads
  "The worker-pool size the running server actually settled on, or nil.

   When ORCPUB_HTTP_MAX_THREADS is unset we pass Pedestal nothing and it chooses, so this is
   the only way to report the real number rather than a formula copied from Pedestal that
   would drift. Best effort by design: it reaches through a started Jetty, so any change of
   container returns nil and the banner falls back to `-` instead of failing a boot."
  [created]
  (try
    (some-> (if (map? created) (get created :io.pedestal.http/server) created)
            (.getThreadPool) (.getMaxThreads))
    (catch Exception _ nil)))

(defn print-report!
  "Say the server is up and how it is configured. Called after the system has started, so
   `started` means started rather than `we got as far as printing`, and so the running
   thread-pool size can be read back rather than guessed."
  ([] (print-report! nil))
  ([created]
   (let [actual (when-let [n (jetty-max-threads created)]
                  {"ORCPUB_HTTP_MAX_THREADS" n})]
     ;; One write, then flush. Printed line by line, Jetty's own logging interleaved with it
     ;; and cut the table in half -- buffered stdout against unbuffered stderr in the same
     ;; stream. A banner that arrives in pieces is worse than no banner.
     (println (str/join (System/lineSeparator) (report-lines (report) actual)))
     (flush))))

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
