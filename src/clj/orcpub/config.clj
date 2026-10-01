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
  "Blank out credentials in a connection string so it can be logged; nil for nil.
   Redacts a `password=`/`secret=`/`token=`-style query parameter and `scheme://user:pass@host`
   userinfo; anything else is returned unchanged.
   GOTCHA: redacted is not safe to print. Call it only on values meant to be seen, and never
   widen what is logged because it is redacted."
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
  "Return the Datomic URI: the raw env value (`datomic-env`), else the local development
  default datomic:dev://localhost:4334/orcpub.

  When DATOMIC_PASSWORD is set and the URL has no `password=`, appends `?password=<pw>`, so
  the password can be kept out of DATOMIC_URL (e.g. in a Docker secret)."
  []
  (let [url (or (datomic-env) default-datomic-uri)
        pw  (datomic-password)]
    (if (and pw (not (str/includes? url "password=")))
      (str url "?password=" pw)
      url)))

;; CSP_POLICY options (read by `get-csp-policy`):
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
   Defaults to twice the core count, minimum eight.

   Separate from the HTTP pool: exports past this limit wait for a slot while pages, logins
   and saves keep their own workers. Sizing against the heap: docs/PDF-EXPORT-CAPACITY.md."
  []
  (positive-int-env ["ORCPUB_PDF_CONCURRENCY"] (max 8 (* 2 @available-processors))))

(defn get-pdf-max-caster-sections
  "Most spellcasting sections one sheet may be grown to, from ORCPUB_PDF_MAX_CASTER_SECTIONS.
   Defaults to 13, every class in the game.
   GOTCHA: the count comes from the field NAMES in the request (the largest N in
   spellcasting-class-N), so without a ceiling a tiny body can ask for thousands of pages."
  []
  (positive-int-env ["ORCPUB_PDF_MAX_CASTER_SECTIONS"] 13))

(def cards-per-page
  "Cards on one printed sheet, at the size the exports use.

   NOT a constant in the renderer: pdf.clj derives the grid per call, as
   `(int (/ 8.5 box-width))` by `(int (/ 11.0 box-height))`, and the card call sites pass
   2.5 x 3.5in -- so 3 across by 3 down. Named here so the cap below reads as whole sheets
   rather than an arbitrary number, and so anyone changing the card size finds this."
  9)

(defn get-pdf-max-cards
  "Most cards of one kind a single export will print, from ORCPUB_PDF_MAX_CARDS.
   Defaults to 22 whole sheets (198). Counted per KIND, so spells, items and features are
   each bounded separately.
   GOTCHA: the caller says how many cards it wants, so without a cap an export is only as
   bounded as the request body. Sizing: docs/PDF-EXPORT-CAPACITY.md."
  []
  (positive-int-env ["ORCPUB_PDF_MAX_CARDS"] (* 22 cards-per-page)))

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
  "Returns true when CSP_POLICY=strict, regardless of dev mode.
   `orcpub.pedestal/make-nonce-interceptor` acts on it only outside dev mode, where it adds
   per-request nonces and an enforcing Content-Security-Policy header."
  []
  (= "strict" (get-csp-policy)))

(def signature-missing-message
  "Why authentication is broken, and how to fix it. One string, used by the boot banner, the
   startup warning and the 500 a caller gets, so an operator reading any of them is told the
   same thing and does not have to work out the remedy from the symptom."
  (str "SIGNATURE is not set, so every login, signup and authenticated API call fails.\n"
       "   It is the key JWT tokens are signed with; without it none can be issued or verified.\n"
       "   Fix: set a long random value and restart, either as a Docker secret at\n"
       "        /run/secrets/signature (preferred) or as the SIGNATURE environment variable.\n"
       "        e.g.  openssl rand -base64 48\n"
       "   Keep it stable: changing it signs out everyone and invalidates unsubscribe links."))

(def email-missing-message
  "Registration is inside the same try as the verification email, so an unconfigured mail
   server does not merely skip the email -- signup throws and the caller is told to \"try
   again\", which will never work. Password reset fails the same way."
  (str "EMAIL_SERVER_URL is not set, so no mail can be sent.\n"
       "   Registration FAILS for every new user -- the verification email is sent inside the\n"
       "   signup transaction, so the whole signup errors with \"please try again\". Password\n"
       "   reset fails too, so existing users cannot recover an account.\n"
       "   Fix: set EMAIL_SERVER_URL to your SMTP host, with EMAIL_ACCESS_KEY and\n"
       "        EMAIL_SECRET_KEY for authentication, then restart.\n"
       "        EMAIL_SERVER_PORT defaults to 587; set EMAIL_SSL or EMAIL_TLS if required."))

(def branding-vars
  "The APP_* fork-branding variables. Reported as a count rather than twenty rows -- a
   banner nobody reads because it scrolls is no better than no banner."
  ["APP_NAME" "APP_TAGLINE" "APP_PAGE_TITLE" "APP_URL" "APP_LOGO_PATH" "APP_OG_IMAGE"
   "APP_HELP_URL" "APP_SUPPORT_EMAIL" "APP_EMAIL_SENDER_NAME"
   "APP_COPYRIGHT_HOLDER" "APP_COPYRIGHT_URL" "APP_COPYRIGHT_YEAR"
   "APP_FIELD_LIMIT_NOTES" "APP_FIELD_LIMIT_NUMBER" "APP_FIELD_LIMIT_TEXT"
   "APP_SOCIAL_BLUESKY" "APP_SOCIAL_DISCORD" "APP_SOCIAL_FACEBOOK"
   "APP_SOCIAL_PATREON" "APP_SOCIAL_REDDIT" "APP_SOCIAL_TWITTER"])

(defn get-share-max-upload-kb
  "Largest compressed homebrew one short share link keeps, in KB, from ORCPUB_SHARE_MAX_UPLOAD_KB.

   Measured 2026-09-13 on the MegaPak: a packed level 20 character's homebrew compresses to 12 to 25 KB,
   and a wizard holding every subclass and spell in it to 40 KB. Over the cap, the owner's link carries
   the homebrew itself instead."
  []
  (positive-int-env ["ORCPUB_SHARE_MAX_UPLOAD_KB"] 64))

(defn get-share-max-text-kb
  "Largest that homebrew may be unpacked, in KB, from ORCPUB_SHARE_MAX_TEXT_KB. The same measurement:
   34 to 112 KB for a packed level 20 character, 175 KB for the everything wizard. It also bounds what a
   viewer's browser will unpack."
  []
  (positive-int-env ["ORCPUB_SHARE_MAX_TEXT_KB"] 256))

(defn get-share-max-account-kb
  "Shared homebrew one account keeps across all its characters, in KB, from ORCPUB_SHARE_MAX_ACCOUNT_KB.
   1024 is about 40 wizard-sized shares; a share over it carries the homebrew in its link instead."
  []
  (positive-int-env ["ORCPUB_SHARE_MAX_ACCOUNT_KB"] 1024))

(defn get-share-prune-days
  "Days a character's share link may go unused while the server runs before the link and its share data
   are deleted, from ORCPUB_SHARE_PRUNE_DAYS; 0 keeps them. The character itself is never deleted. 180
   leaves room for a campaign that pauses for a season. 1 to 29 count as 30, so a typo cannot delete
   links a day old."
  []
  (let [n (some-> (env-raw "ORCPUB_SHARE_PRUNE_DAYS") str/trim parse-long)]
    (cond
      (or (nil? n) (neg? n)) 180
      (zero? n)              0
      :else                  (max n 30))))

(defn pwned-check-enabled?
  "Whether a new password is screened against Have I Been Pwned, from
   ORCPUB_PWNED_CHECK. On by default, because the check costs one request and
   catches what no rule of ours can.

   Off is the kill switch: set it to off/false/0/no and registration stops
   calling out entirely, with no other behaviour change. The check already fails
   open on a timeout, so this exists for the case where the service is not down
   but bad -- slow enough to be felt on every signup."
  []
  (let [raw (some-> (env-raw "ORCPUB_PWNED_CHECK") str/trim str/lower-case)]
    (not (contains? #{"off" "false" "0" "no"} raw))))

(def settings
  "Everything the boot banner reports, in print order.
   `:secret?`: the VALUE IS NEVER PRINTED, only whether it is present.
   `:critical?`: its absence breaks the site; called out below the table, with its `:fix`.
   `:get`: resolves the value where an accessor exists; otherwise the raw env value is shown,
   which is right for settings that have no default of ours."
  (concat
   [{:group "runtime"  :var "PORT"}
    {:group "runtime"  :var "DEV_MODE"      :note "dev-only behaviour and relaxed CORS"}
    {:group "database" :var "DATOMIC_URL"   :get #(get-datomic-uri) :redact? true}
    {:group "database" :var "DATOMIC_PASSWORD" :secret? true}
    {:group "security" :var "SIGNATURE"     :secret? true :critical? true
     :note "JWT signing key; every login and API call fails without it"
     :fix signature-missing-message}
    {:group "security" :var "CSP_POLICY"    :note "overrides the built-in policy"}
    {:group "email"    :var "EMAIL_SERVER_URL" :critical? true :fix email-missing-message
     :note "SMTP host; signup and password reset fail without it"}
    {:group "email"    :var "EMAIL_SERVER_PORT" :note "defaults to 587"}
    {:group "email"    :var "EMAIL_ACCESS_KEY" :secret? true}
    {:group "email"    :var "EMAIL_SECRET_KEY" :secret? true}
    {:group "email"    :var "EMAIL_FROM_ADDRESS"}
    {:group "email"    :var "EMAIL_ERRORS_TO"}
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
     :note "spellcasting sections one sheet may grow to"}
    {:group "capacity" :var "ORCPUB_PDF_MAX_CARDS" :get #(get-pdf-max-cards)
     :note "cards of each kind per export; 9 to a sheet, so 22 sheets"}
    {:group "capacity" :var "ORCPUB_SHARE_MAX_UPLOAD_KB" :get #(get-share-max-upload-kb)
     :note "compressed share data one share link keeps"}
    {:group "capacity" :var "ORCPUB_SHARE_MAX_TEXT_KB" :get #(get-share-max-text-kb)
     :note "that share data unpacked"}
    {:group "capacity" :var "ORCPUB_SHARE_MAX_ACCOUNT_KB" :get #(get-share-max-account-kb)
     :note "share data one account keeps in all"}
    {:group "runtime" :var "ORCPUB_PWNED_CHECK" :get #(if (pwned-check-enabled?) "on" "off")
     :accepts #(contains? #{"on" "off" "true" "false" "1" "0" "yes" "no"} (str/lower-case (str/trim %)))
     :expects "on or off"
     :note "screens a new password against known breaches; fails open, off disables the call"}
    {:group "retention" :var "ORCPUB_SHARE_PRUNE_DAYS" :get #(get-share-prune-days)
     :accepts #(some-> % str/trim parse-long (>= 0)) :expects "a whole number of days, 0 or more"
     :note "days unused before a character's share link and share data are deleted; 0 keeps them, at least 30"}]))

(def ^:private tunables
  "Kept for the capacity rows' typo detection: only these parse as integers."
  (filter #(= "capacity" (:group %)) settings))

(defn- positive-int-text? [raw]
  (let [n (try (Integer/parseInt (str/trim raw)) (catch NumberFormatException _ nil))]
    (boolean (and n (pos? n)))))

(defn report
  "What each setting resolved to, and whether that came from the environment.

   `:set?` is the part an operator actually needs: a bare value cannot tell you whether your
   change was picked up, ignored as a typo, or never set. Secret values are dropped here, not
   at print time, so nothing downstream can leak one by accident."
  []
  (for [{env-var :var getter :get :keys [secret? redact? critical? note group fix accepts expects]} settings]
    (let [raw      (env-raw env-var)
          tunable? (some? (some #(= env-var (:var %)) tunables))
          ;; A row with its own :accepts (the prune window takes 0) is checked by that instead.
          accepts  (or accepts (when tunable? positive-int-text?))
          ignored? (boolean (and raw accepts (not (accepts raw))))
          value    (cond secret? nil
                         getter  (getter)
                         :else   raw)]
      {:var env-var :group group :note note :critical? critical? :fix fix :expects expects
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
         val    (fn [{env-var :var :keys [value secret? set?]}]
                  (cond secret?          (if set? "set" "NOT SET")
                        value            (str value)
                        (get actual env-var) (str (get actual env-var))
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
              (for [{env-var :var raw :raw expects :expects} bad]
                (format "  (!)  %s=%s was ignored: not %s. The default above is in use."
                        env-var raw (or expects "a positive integer")))))
      ;; A missing SIGNATURE means every login fails. That is not a row to be spotted.
      ;; Say what broke AND how to fix it. A boot line that names a symptom and leaves the
      ;; remedy to be guessed just moves the work.
      (when-let [miss (seq (filter #(and (:critical? %) (not (:set? %))) rows))]
        (mapcat (fn [{env-var :var :keys [note fix]}]
                  (let [body (str/split-lines (or fix (str env-var " is NOT SET -- " (or note "required"))))]
                    (cons rule
                          ;; Continuations line up under the first line's text, not under the
                          ;; marker, so the block reads as one paragraph.
                          (cons (str "  (!!) " (first body))
                                (map #(str "        " (str/triml %)) (rest body))))))
                miss))
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
    ;; Strict mode - nonce-interceptor sets an enforcing CSP outside dev mode; in dev mode
    ;; it is a no-op and Pedestal's default CSP stays active
    (= "strict" (get-csp-policy))
    {:content-security-policy-settings nil}

    ;; CSP disabled
    (= "none" (get-csp-policy))
    {:content-security-policy-settings nil}

    ;; Default to permissive
    :else
    {:content-security-policy-settings permissive-csp-settings}))
