(ns orcpub.config
  (:require [orcpub.env :as env]
            [orcpub.fork.branding :as branding]
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

(defn profile-encryption-keys
  "Return PROFILE_ENCRYPTION_KEYS from Docker secret, env var, or nil.
  Resolution order: /run/secrets/profile_encryption_keys > PROFILE_ENCRYPTION_KEYS.
  Format and use: orcpub.crypto." []
  (or (read-secret "profile_encryption_keys")
      (env/value :profile-encryption-keys)))

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
  ;; Blank means unset, so an empty CSP_POLICY is "strict", not the permissive fallback.
  (let [policy (env/value :csp-policy "strict")]
    ;; Locale/ROOT, not str/lower-case: this is an ASCII config token, not
    ;; prose. str/lower-case folds using the default locale, so on a Turkish
    ;; machine "STRICT" becomes "strıct" (dotless i), misses every comparison
    ;; below, and silently falls through to the permissive policy.
    (.toLowerCase ^String policy Locale/ROOT)))

(defn- env-raw
  "The string for an env var name, from environ or the process environment; nil when unset or
   blank (orcpub.env/value). One implementation so \"is it set?\" and \"what is it?\" can never
   disagree."
  [n]
  (or (env/value (keyword (.toLowerCase (str/replace n "_" "-") Locale/ROOT)))
      (some-> (System/getenv n) str/trim not-empty)))

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

(defn get-portrait-concurrency
  "How many shared-link portrait images may be drawn at once, from ORCPUB_PORTRAIT_CONCURRENCY.
   Defaults to half the cores, minimum two: each draw is about half a second of one core, and
   the rest are left for pages and exports. docs/PORTRAIT-SERVING.md."
  []
  (positive-int-env ["ORCPUB_PORTRAIT_CONCURRENCY"] (max 2 (quot @available-processors 2))))

(defn get-portrait-queue-timeout-ms
  "How long a portrait image waits to be drawn before the server says it is busy, from
   ORCPUB_PORTRAIT_QUEUE_TIMEOUT_MS. Short, because link-preview fetchers give up quickly."
  []
  (positive-int-env ["ORCPUB_PORTRAIT_QUEUE_TIMEOUT_MS"] 5000))

(defn get-portrait-store-dir
  "Where the server keeps its drawings of shared-link portraits, from PORTRAIT_STORE_DIR. Defaults
   to orcpub-portraits under the system temp directory: the Docker image runs from / as a user
   that can write only /tmp. Point several containers at one shared volume and each portrait is
   drawn once for all of them. docs/PORTRAIT-SERVING.md."
  []
  (or (not-empty (env/value :portrait-store-dir))
      (str (io/file (System/getProperty "java.io.tmpdir") "orcpub-portraits"))))

(defn get-alert-spool-dir
  "Where admin alerts that could not be sent wait, from ALERT_SPOOL_DIR. Defaults to orcpub-alerts
   under the system temp directory, which a replaced container loses: point it at a volume."
  []
  (or (not-empty (env/value :alert-spool-dir))
      (str (io/file (System/getProperty "java.io.tmpdir") "orcpub-alerts"))))

(defn get-alert-max-per-hour
  "Most admin alert emails sent in an hour, from ALERT_MAX_PER_HOUR; past it alerts are logged only."
  []
  (positive-int-env ["ALERT_MAX_PER_HOUR"] 20))

(defn get-trusted-proxies
  "Extra proxies to trust for the player's address, from TRUSTED_PROXIES: comma-separated
   addresses or CIDR ranges, or nil. Private networks are always trusted."
  []
  (env/value :trusted-proxies))

(defn trust-cloudflare?
  "Whether Cloudflare's edge ranges are trusted as proxies, from TRUST_CLOUDFLARE: only when the site
   is served through Cloudflare."
  []
  (env/flag? :trust-cloudflare))

(defn copy-name
  "This server's name among several copies: HOSTNAME (Docker and Swarm set it per container), else
   the machine's host name, else \"server\". Names its alert spool file and its alerts."
  []
  (or (env/value :hostname)
      (try (.getHostName (java.net.InetAddress/getLocalHost)) (catch Exception _ nil))
      "server"))

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
  "Returns true when CSP_POLICY=strict, regardless of dev mode.
   `orcpub.pedestal/make-nonce-interceptor` acts on it only outside dev mode, where it adds
   per-request nonces and an enforcing Content-Security-Policy header. In dev mode this
   application sends no CSP at all, which is what lets Figwheel's scripts and websocket work.
   DEV_MODE defaults to false, so a checkout with no .env enforces CSP and Figwheel's hot reload
   (ws://localhost:3449) is blocked with no obvious cause; .env.example sets DEV_MODE=true."
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

(def current-release
  "The release this build ships as. Settings whose `:since` is this one end the boot report while
   unset, so an operator upgrading sees what is new."
  "Summer Patch")

(def branding-vars
  "The APP_* fork-branding variables. Reported as a count rather than twenty rows -- a
   banner nobody reads because it scrolls is no better than no banner."
  ["APP_NAME" "APP_TAGLINE" "APP_PAGE_TITLE" "APP_URL" "APP_LOGO_PATH" "APP_OG_IMAGE"
   "APP_HELP_URL" "APP_SUPPORT_EMAIL" "APP_EMAIL_SENDER_NAME" "APP_EMAIL_SIGNOFF"
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
   ORCPUB_PWNED_CHECK; on by default.
   GOTCHA: fails open on a timeout, so a slow or down HIBP service never blocks
   signup. Set the env var to off/false/0/no to disable the check entirely.
   See account-flows.md."
  []
  (let [raw (some-> (env-raw "ORCPUB_PWNED_CHECK") str/trim (.toLowerCase Locale/ROOT))]
    (not (contains? #{"off" "false" "0" "no"} raw))))

(defn- positive-int-text?
  "Whether text `raw` reads as a positive whole number."
  [raw]
  (let [n (try (Integer/parseInt (str/trim raw)) (catch NumberFormatException _ nil))]
    (boolean (and n (pos? n)))))

(def settings
  "Everything the boot banner reports, in print order.
   `:secret?`: the VALUE IS NEVER PRINTED, only whether it is present.
   `:critical?`: its absence breaks the site; called out below the table, with its `:fix`.
   `:get`: resolves the value where an accessor exists; otherwise the raw env value is shown,
   which is right for settings that have no default of ours.
   `:since`: the release that added it; `current-release` rows end the report while unset."
  (concat
   [{:group "runtime"  :var "PORT" :since "2.5"}
    {:group "runtime"  :var "DEV_MODE" :since "2.5"      :note "development only: relaxed CORS"}
    {:group "database" :var "DATOMIC_URL" :since "2.5"   :get #(get-datomic-uri) :redact? true}
    {:group "database" :var "DATOMIC_PASSWORD" :since "2.5" :secret? true}
    {:group "security" :var "SIGNATURE" :since "2.5"     :secret? true :critical? true
     :note "signs logins; every login and API call fails without it"
     :fix signature-missing-message}
    {:group "security" :var "TRUST_CLOUDFLARE" :since current-release
     :get #(if (trust-cloudflare?) "true" "false")
     :note "true when the site is served through Cloudflare"}
    {:group "security" :var "TRUSTED_PROXIES" :since current-release
     :note "proxies to trust besides private networks; comma-separated"}
    {:group "security" :var "CSP_POLICY" :since "2.5"    :note "strict, permissive or none"}
    {:group "email"    :var "EMAIL_SERVER_URL" :since "2.5" :critical? true :fix email-missing-message
     :note "SMTP host; sign-up and password reset need it"}
    {:group "email"    :var "EMAIL_SERVER_PORT" :since "2.5" :note "default 587"}
    {:group "email"    :var "EMAIL_SSL" :since "2.5" :note "TRUE: connect over SSL"}
    {:group "email"    :var "EMAIL_TLS" :since "2.5" :note "TRUE: use STARTTLS"}
    {:group "email"    :var "ALLOW_UNVERIFIED_REGISTRATION" :since current-release
     :note "true: sign-up without an email check; only with no SMTP"}
    {:group "email"    :var "EMAIL_ACCESS_KEY" :since "2.5" :secret? true}
    {:group "email"    :var "EMAIL_SECRET_KEY" :since "2.5" :secret? true}
    {:group "email"    :var "EMAIL_FROM_ADDRESS" :since "2.5"}
    {:group "email"    :var "EMAIL_ERRORS_TO" :since "2.5"
     :note "admin alerts; default EMAIL_ADMIN_TO, APP_SUPPORT_EMAIL"}
    {:group "email"    :var "ALERT_SPOOL_DIR" :since current-release :get #(get-alert-spool-dir)
     :accepts (complement str/blank?) :expects "a directory path"
     :note "unsent admin alerts; keep it on a volume"}
    {:group "email"    :var "ALERT_MAX_PER_HOUR" :since current-release :get #(get-alert-max-per-hour)
     :accepts positive-int-text? :expects "a positive whole number"
     :note "alert emails per hour; more are only logged"}
    {:group "email"    :var "EMAIL_ADMIN_TO" :since current-release
     :note "artist notices; default EMAIL_ERRORS_TO, APP_SUPPORT_EMAIL"}
    {:group "security" :var "PROFILE_ENCRYPTION_KEYS" :since current-release :secret? true
     :note "encrypts preferred names; or its Docker secret"}
    {:group "content"  :var "LOAD_HOMEBREW_URL" :since "2.5" :note "homebrew loaded with every page"}
    {:group "content"  :var "PORTRAIT_ARTISTS" :since current-release
     :note "artist credits and accounts; see ARTISTS.md"}
    {:group "content"  :var "APP_URL" :since "2.5"
     :note "public site address, for links in emails"}]
   [{:group "capacity" :var "ORCPUB_HTTP_MAX_THREADS" :since current-release :get #(get-http-max-threads)
     :note "requests handled at once"}
    {:group "capacity" :var "ORCPUB_PDF_CONCURRENCY" :since current-release :get #(get-pdf-concurrency)
     :note "PDF sheets made at once"}
    {:group "capacity" :var "ORCPUB_PDF_QUEUE_TIMEOUT_MS" :since current-release :get #(get-pdf-queue-timeout-ms)
     :note "how long an export waits for a slot"}
    {:group "capacity" :var "ORCPUB_PORTRAIT_CONCURRENCY" :since current-release :get #(get-portrait-concurrency)
     :note "share-link portraits drawn at once"}
    {:group "capacity" :var "ORCPUB_PORTRAIT_QUEUE_TIMEOUT_MS" :since current-release :get #(get-portrait-queue-timeout-ms)
     :note "how long a portrait waits to be drawn"}
    {:group "capacity" :var "PORTRAIT_STORE_DIR" :since current-release :get #(get-portrait-store-dir)
     :accepts (complement str/blank?) :expects "a directory path"
     :note "where share-link portraits are kept"}
    {:group "capacity" :var "ORCPUB_PDF_MAX_RETRIES" :since current-release :get #(get-pdf-max-retries)
     :note "busy retries before asking for a click"}
    {:group "capacity" :var "ORCPUB_PDF_MAX_CASTER_SECTIONS" :since current-release :get #(get-pdf-max-caster-sections)
     :note "spellcasting sections per sheet, at most"}
    {:group "capacity" :var "ORCPUB_PDF_MAX_CARDS" :since current-release :get #(get-pdf-max-cards)
     :note "cards of each kind per export, 9 a sheet"}
    {:group "capacity" :var "ORCPUB_SHARE_MAX_UPLOAD_KB" :since current-release :get #(get-share-max-upload-kb)
     :note "one share link's data, compressed"}
    {:group "capacity" :var "ORCPUB_SHARE_MAX_TEXT_KB" :since current-release :get #(get-share-max-text-kb)
     :note "one share link's data, unpacked"}
    {:group "capacity" :var "ORCPUB_SHARE_MAX_ACCOUNT_KB" :since current-release :get #(get-share-max-account-kb)
     :note "share data per account, in all"}
    {:group "runtime" :var "ORCPUB_PWNED_CHECK" :since current-release :get #(if (pwned-check-enabled?) "on" "off")
     :accepts #(contains? #{"on" "off" "true" "false" "1" "0" "yes" "no"} (.toLowerCase (str/trim %) Locale/ROOT))
     :expects "on or off"
     :note "checks new passwords against known breaches"}
    {:group "retention" :var "ORCPUB_SHARE_PRUNE_DAYS" :since current-release :get #(get-share-prune-days)
     :accepts #(some-> % str/trim parse-long (>= 0)) :expects "a whole number of days, 0 or more"
     :note "days before an unused share link is deleted; 0 never"}]))

(def ^:private tunables
  "Kept for the capacity rows' typo detection: only these parse as integers."
  (filter #(= "capacity" (:group %)) settings))

(defn report
  "What each setting resolved to, and whether that came from the environment.

   `:set?` is the part an operator actually needs: a bare value cannot tell you whether your
   change was picked up, ignored as a typo, or never set. Secret values are dropped here, not
   at print time, so nothing downstream can leak one by accident."
  []
  (for [{env-var :var getter :get :keys [secret? redact? critical? note group fix accepts expects since]} settings]
    (let [raw      (env-raw env-var)
          tunable? (some? (some #(= env-var (:var %)) tunables))
          ;; A row with its own :accepts (the prune window takes 0) is checked by that instead.
          accepts  (or accepts (when tunable? positive-int-text?))
          ignored? (boolean (and raw accepts (not (accepts raw))))
          value    (cond secret? nil
                         getter  (getter)
                         :else   raw)]
      {:var env-var :group group :note note :critical? critical? :fix fix :expects expects :since since
       :value (if (and value redact?) (redact-secrets value) value)
       :secret? secret?
       :raw (when-not secret? raw)
       :set? (boolean (and raw (not ignored?)))
       :ignored? ignored?})))

(defn- wrap-names
  "`names` joined with commas into lines of at most `width` characters, each indented two spaces."
  [names width]
  (reduce (fn [lines nm]
            (let [last-line (peek lines)
                  joined    (str last-line ", " nm)]
              (if (and last-line (<= (count joined) width))
                (conj (pop lines) joined)
                (conj lines (str "  " nm)))))
          [] names))

(defn report-lines
  "The boot banner, as lines: `rows` from `report`, `actual` values read off the running server
   (var name to value), and `branding-ignored`, the branding values that fell back to a default
   ({:var :value :why}), and `deployment`, lines from `deployment-lines`. Ends with this release's
   new settings that are still unset. ASCII only,
   no colour: it is read in log files and aggregators as often as in a terminal."
  ([] (report-lines (report) nil nil))
  ([rows] (report-lines rows nil nil))
  ([rows actual] (report-lines rows actual nil nil))
  ([rows actual branding-ignored] (report-lines rows actual branding-ignored nil))
  ([rows actual branding-ignored deployment]
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
         ;; Each group once, in the order groups first appear; rows keep their order within it.
         order  (into {} (map-indexed (fn [i g] [g i]) (distinct (map :group rows))))
         rows   (vec (sort-by (comp order :group) rows))
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
                (cons (str "  [" (.toUpperCase ^String (or (:group (first gr)) "other") Locale/ROOT) "]")
                      (map #(trim (format fmt (:var %) (val %) (source %) (or (:note %) ""))) gr)))
              (partition-by :group rows))
      ["  [BRANDING]"
       (format "  %s   %s of %s set" (apply str (repeat w " ")) brand (count branding-vars))]
      (when (or (seq (filter :ignored? rows)) (seq branding-ignored))
        (cons rule
              (concat
               (for [{env-var :var raw :raw expects :expects} (filter :ignored? rows)]
                 (format "  (!)  %s=%s was ignored: not %s. The default above is in use."
                         env-var raw (or expects "a positive integer")))
               (for [{env-var :var value :value why :why} branding-ignored]
                 (format "  (!)  %s=%s was ignored: %s. Its default is in use."
                         env-var value why)))))
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
      (when (seq deployment)
        (concat [rule "  DEPLOYMENT"] (map #(str "  " %) deployment)))
      ;; Last, where an operator upgrading looks: what this release added and is still unset.
      (when-let [fresh (seq (filter #(and (= current-release (:since %)) (not (:set? %))) rows))]
        (concat
         [rule (str "  NEW IN " (.toUpperCase ^String current-release Locale/ROOT)
                    ": " (count fresh) " setting" (when (next fresh) "s") " not set yet, using defaults")]
         (wrap-names (map :var fresh) 100)))
      [rule]))))

(defn- first-address
  "The first of `vars` (env var names) with a value, then APP_SUPPORT_EMAIL (which fork branding may
   default), as {:address :from}; nil when none is set."
  [vars]
  (or (some (fn [v] (when-let [a (env/value (keyword (str/lower-case (str/replace v "_" "-"))))]
                      {:address a :from v}))
            vars)
      (when (seq branding/support-email)
        {:address branding/support-email :from "APP_SUPPORT_EMAIL"})))

(defn alert-address
  "Where admin alerts go: EMAIL_ERRORS_TO, else EMAIL_ADMIN_TO, else APP_SUPPORT_EMAIL, as
   {:address :from} (:from names the setting it came from); nil when none is set."
  []
  (first-address ["EMAIL_ERRORS_TO" "EMAIL_ADMIN_TO"]))

(defn admin-notice-address
  "Where artist-account notices go: EMAIL_ADMIN_TO, else EMAIL_ERRORS_TO, else APP_SUPPORT_EMAIL,
   as {:address :from}; nil when none is set."
  []
  (first-address ["EMAIL_ADMIN_TO" "EMAIL_ERRORS_TO"]))

(def deployment-notes
  "This release's deployment changes, one line each, printed in the boot report's DEPLOYMENT
   block so an admin who deploys without ./run still sees them. docs/DOCKER.md has the detail."
  ["Docker Swarm: Datomic needs TRANSACTOR_HOST=0.0.0.0 and ALT_HOST=datomic. Unset, it detects"
   "  Swarm itself; its log line 'transactor: host=... alt-host=...' shows what it uses."
   "Docker: the app container starts as root, prepares its folders, then runs Java as 'app'."
   "  Remove any user: line from the orcpub service."
   "Docker: container logs are capped by LOG_MAX_SIZE and LOG_MAX_FILES (10m x 5)."])

(defn- shown-dir
  "How `dir` reads to an admin: a /host-log path as the host's /var/log path, else as is."
  [dir]
  (if (str/starts-with? dir "/host-log/")
    (str "/var/log/" (subs dir (count "/host-log/")) " on the host")
    dir))

(defn deployment-lines
  "The boot report's DEPLOYMENT block, as lines. Takes `:crash-dir` and `:alert-dir`, the resolved
   folders for crash files and unsent alerts (either nil when unknown), `:max-heap-bytes`, the
   most memory Java may use (nil leaves the line out), and `:admin`, `alert-address`'s answer
   (the line appears when the key is given). Says where those files land, flags a folder
   inside the container with the mount that moves it, gives the memory limit, and ends with
   `deployment-notes`."
  [{:keys [crash-dir alert-dir max-heap-bytes admin] :as dirs}]
  (let [inside? #(and % (str/starts-with? % "/var/lib/orcpub/"))]
    (concat
     (when (contains? dirs :admin)
       [(if admin
          (str "Admin email: " (:address admin) " (from " (:from admin) ")")
          "Admin email: nobody; crash and failure alerts are only logged. Set APP_SUPPORT_EMAIL or EMAIL_ERRORS_TO.")])
     [(str "Crash files: " (if crash-dir (shown-dir crash-dir) "not configured (no -XX:HeapDumpPath)"))
      (str "Unsent alerts: " (if alert-dir (shown-dir alert-dir) "-"))]
     (when (or (inside? crash-dir) (inside? alert-dir))
       ["  Those are inside the container and go when it is replaced. Mount - /var/log:/host-log"
        "  on this service to keep them in /var/log on the host."])
     (when max-heap-bytes
       [(str "Java memory limit: " (quot max-heap-bytes (* 1024 1024)) " MB"
             " (raise it with JAVA_OPTS, for example -Xmx2g)")])
     deployment-notes)))

(defn- canonical
  "`f`'s path with links followed, or as given when it cannot be resolved."
  [f]
  (try (.getCanonicalPath (io/file f)) (catch Exception _ (str f))))

(defn crash-and-alert-dirs
  "{:crash-dir :alert-dir}: the folder the JVM's -XX:HeapDumpPath points into (nil without the
   flag) and the alert spool folder, both with links followed, so a container's
   /var/lib/orcpub/state reads as the /host-log folder it links to."
  []
  (let [args (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean))
        heap (some #(when (str/starts-with? % "-XX:HeapDumpPath=") (subs % (count "-XX:HeapDumpPath="))) args)]
    {:crash-dir (some-> heap io/file .getParentFile canonical)
     :alert-dir (canonical (get-alert-spool-dir))}))

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
     (println (str/join (System/lineSeparator) (report-lines (report) actual @branding/ignored
                                                             (deployment-lines (assoc (crash-and-alert-dirs)
                                                                                      :max-heap-bytes (.maxMemory (Runtime/getRuntime))
                                                                                      :admin (alert-address))))))
     (flush))))

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
