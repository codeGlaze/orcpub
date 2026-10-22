(ns orcpub.email
  "Email sending functionality with error handling.

  Provides functions for sending verification emails, password reset emails,
  and error notification emails. All operations include comprehensive error
  handling to prevent silent failures when the SMTP server is unavailable."
  (:require [hiccup2.core :as hiccup]
            [postal.core :as postal]
            [orcpub.env :as env]
            [clojure.pprint :as pprint]
            [clojure.string :as s]
            [orcpub.route-map :as routes]
            [orcpub.fork.branding :as branding]
            [orcpub.alerts :as alerts]
            [orcpub.config :as config]
            [cuerdas.core :as str]))

(defn- social-links-footer
  "Render email footer social links from branding config. Empty links hidden."
  []
  (let [{:keys [patreon twitter facebook]} branding/social-links]
    (remove nil?
      [(when (seq patreon) [:br])
       (when (seq patreon) (str patreon "  <-- Like what we are doing? Support us here."))
       (when (seq twitter) [:br])
       (when (seq twitter) twitter)
       (when (seq facebook) [:br])
       (when (seq facebook) facebook)
       [:br]])))

(defn verification-email-html [first-and-last-name username verification-url]
  (into
   [:div
    (str "Welcome to " branding/app-name
         (when (seq first-and-last-name) (str ", " first-and-last-name)) "!")
    [:br]
    [:br]
    (str "Your " branding/app-name " account is almost ready, we just need you to verify your email address by visiting the following URL to confirm that you are authorized to use this email address:")
    [:br]
    [:br]
    [:a {:href verification-url} verification-url]
    [:br]
    [:br]
    "Sincerely,"
    [:br]
    [:br]
    (str "The " branding/email-sender-name)]
   (social-links-footer)))

(defn verification-email [first-and-last-name username verification-url]
  [{:type "text/html"
    :content (str (hiccup/html (verification-email-html first-and-last-name username verification-url)))}])

(defn greeting
  "The opening line: 'Hi Kaylee,' with a preferred name, 'Hi there,' without."
  [preferred-name]
  (str "Hi " (if (seq preferred-name) preferred-name "there") ","))

(defn email-change-verification-html
  "Email body for existing users changing their email (distinct from registration)."
  ([username verification-url] (email-change-verification-html nil username verification-url))
  ([preferred-name username verification-url]
  [:div
   (greeting preferred-name)
   [:br]
   [:br]
   "You requested to change the email address on your account (" username "). "
   "Please visit the following URL to confirm this change:"
   [:br]
   [:br]
   [:a {:href verification-url} verification-url]
   [:br]
   [:br]
   "If you did not request this change, you can safely ignore this email."
   [:br]
   [:br]
   "Sincerely,"
   [:br]
   [:br]
   (str "The " branding/email-sender-name)]))

(defn email-change-verification-email
  ([username verification-url] (email-change-verification-email nil username verification-url))
  ([preferred-name username verification-url]
   [{:type "text/html"
     :content (str (hiccup/html (email-change-verification-html preferred-name username verification-url)))}]))

(defn configured?
  "True when an SMTP host is set, i.e. when this deployment can send mail. Leaving
   EMAIL_SERVER_URL empty disables email, as .env.example promises. docs/kb/blank-env-values.md."
  []
  (some? (env/value :email-server-url)))

(defn unverified-registration-allowed?
  "True when this deployment has DELIBERATELY opted out of email verification
   (ALLOW_UNVERIFIED_REGISTRATION=true). Missing SMTP alone never allows it: a typo or an empty
   mount must break registration loudly, not open it. docs/kb/blank-env-values.md."
  []
  (env/flag? :allow-unverified-registration))

(defn email-cfg []
  (try
    ;; "" defaults, NOT nil: postal given nil fails with a bare NullPointerException instead of
    ;; "Couldn't connect to host", and treats empty and nil :user/:pass differently.
    {:user (env/value :email-access-key "")
     :pass (env/value :email-secret-key "")
     :host (env/value :email-server-url "")
     :port (Integer/parseInt (env/value :email-server-port "587"))
     :ssl (or (str/to-bool (env/value :email-ssl)) nil)
     :tls (or (str/to-bool (env/value :email-tls)) nil)}
    (catch NumberFormatException e
      (throw (ex-info "Invalid email server port configuration. Expected a number."
                      {:error :invalid-port
                       :port (env/value :email-server-port)}
                      e)))))

(defn emailfrom
  "Returns the configured from-address. Delegates to branding/email-from-address
   which already reads EMAIL_FROM_ADDRESS env var with a fallback default."
  []
  branding/email-from-address)

(defn verification-message
  "The account verification email for a new user: `base-url` for the link, a user map with
  :email :username :first-and-last-name, and `verification-key`. Returns the postal message."
  [base-url {:keys [email username first-and-last-name]} verification-key]
  {:from (str branding/email-sender-name " <" (emailfrom) ">")
   :to email
   :subject (str branding/app-name " Email Verification")
   :body (verification-email
          first-and-last-name
          username
          (str base-url (routes/path-for routes/verify-route) "?key=" verification-key))})

(defn email-change-verification-message
  "The verification email for an email-change request, sent to the new address: `base-url` for
  the link, a user map with :email :username :preferred-name, and `verification-key`. Returns the
  postal message."
  [base-url {:keys [email username preferred-name]} verification-key]
  {:from (str branding/email-sender-name " <" (emailfrom) ">")
   :to email
   :subject (str branding/app-name " Email Change Verification")
   :body (email-change-verification-email
          preferred-name
          username
          (str base-url (routes/path-for routes/verify-route) "?key=" verification-key))})

(defn reset-password-email-html [first-and-last-name reset-url]
  (into
   [:div
    (greeting first-and-last-name)
    [:br]
    [:br]
    "We received a request to reset your password, to do so please go to the following URL to complete the reset."
    [:br]
    [:br]
    [:a {:href reset-url} reset-url]
    [:br]
    [:br]
    "If you did NOT request a reset, please do NOT click on the link."
    [:br]
    [:br]
    "Sincerely,"
    [:br]
    [:br]
    (str "The " branding/email-sender-name)]
   (social-links-footer)))

(defn reset-password-email [first-and-last-name reset-url]
  [{:type "text/html"
    :content (str (hiccup/html (reset-password-email-html first-and-last-name reset-url)))}])

(defn reset-message
  "The password reset email: `base-url` for the link, a user map with :email
  :first-and-last-name, and `reset-key`. Returns the postal message."
  [base-url {:keys [email first-and-last-name]} reset-key]
  {:from (str branding/email-sender-name " <" (emailfrom) ">")
   :to email
   :subject (str branding/app-name " Password Reset")
   :body (reset-password-email
          first-and-last-name
          (str base-url (routes/path-for routes/reset-password-page-route) "?key=" reset-key))})

(defn describe-browser
  "A user agent in the words a person would use. Deliberately coarse: this goes
   in front of somebody deciding whether an attempt was their own, and \"Chrome on
   Windows\" answers that where the raw header does not. Order matters -- Edge and
   Opera both claim to be Chrome, and Chrome claims to be Safari."
  [ua]
  (if (s/blank? ua)
    "an unrecognised browser"
    (let [browser (cond
                    (re-find #"(?i)edg/" ua) "Edge"
                    (re-find #"(?i)opr/|opera" ua) "Opera"
                    (re-find #"(?i)firefox/" ua) "Firefox"
                    (re-find #"(?i)chrome/|crios/" ua) "Chrome"
                    (re-find #"(?i)safari/" ua) "Safari"
                    :else nil)
          platform (cond
                     (re-find #"(?i)iphone|ipad|ipod" ua) "iOS"
                     (re-find #"(?i)android" ua) "Android"
                     (re-find #"(?i)windows" ua) "Windows"
                     (re-find #"(?i)mac os x|macintosh" ua) "a Mac"
                     (re-find #"(?i)linux" ua) "Linux"
                     :else nil)]
      (cond
        (and browser platform) (str browser " on " platform)
        browser browser
        platform (str "a browser on " platform)
        :else "an unrecognised browser"))))

(defn- attempt-time []
  (.format (java.time.format.DateTimeFormatter/ofPattern "d MMMM yyyy 'at' HH:mm 'UTC'")
           (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC)))

(defn sign-in-attempts-email-html [first-and-last-name reset-url browser when-str]
  (into
   [:div
    (str "Dear " (if (seq first-and-last-name) first-and-last-name (str branding/app-name " User")) ",")
    [:br]
    [:br]
    "Someone tried to sign in to your account several times just now, from more than one place at once."
    [:br]
    [:br]
    (str "The last of those attempts was on " when-str ", from " browser ".")
    [:br]
    [:br]
    "Usually this is nothing. A phone and a laptop both signed out at the same time will do it, as will a password you half remember. Nobody got in, nothing has changed, and your account is not locked."
    [:br]
    [:br]
    "If none of it was you, pick a new password when you get a moment. You can start that here:"
    [:br]
    [:br]
    [:a {:href reset-url} reset-url]
    [:br]
    [:br]
    "Sincerely,"
    [:br]
    [:br]
    (str "The " branding/email-sender-name)]
   (social-links-footer)))

(defn sign-in-attempts-email [first-and-last-name reset-url browser when-str]
  [{:type "text/html"
    :content (str (hiccup/html (sign-in-attempts-email-html
                                first-and-last-name reset-url browser when-str)))}])

(defn sign-in-attempts-message
  "The notice that failed sign-ins arrived from several places at once: `base-url`, and a map
  with :email :first-and-last-name :user-agent. Links to the reset PAGE, never a token, so people
  are not trained to click mailed credentials (account-flows.md). Returns the postal message."
  [base-url {:keys [email first-and-last-name user-agent]}]
  {:from (str branding/email-sender-name " <" (emailfrom) ">")
   :to email
   :subject (str branding/app-name " sign-in attempts")
   :body (sign-in-attempts-email
          first-and-last-name
          (str base-url (routes/path-for routes/send-password-reset-page-route))
          (describe-browser user-agent)
          (attempt-time))})

(defn password-changed-email-html [first-and-last-name reset-url browser when-str]
  (into
   [:div
    (str "Dear " (if (seq first-and-last-name) first-and-last-name (str branding/app-name " User")) ",")
    [:br] [:br]
    "The password on your account was just changed."
    [:br] [:br]
    (str "That was on " when-str ", from " browser ".")
    [:br] [:br]
    "If it was you, there is nothing to do."
    [:br] [:br]
    "If it was not, somebody else can sign in as you right now. Change the password back straight away -- you will need access to this mailbox, which is how the change was made in the first place:"
    [:br] [:br]
    [:a {:href reset-url} reset-url]
    [:br] [:br]
    "Sincerely,"
    [:br] [:br]
    (str "The " branding/email-sender-name)]
   (social-links-footer)))

(defn password-changed-email [first-and-last-name reset-url browser when-str]
  [{:type "text/html"
    :content (str (hiccup/html (password-changed-email-html
                                first-and-last-name reset-url browser when-str)))}])

(defn password-changed-message
  "The notice that an account's password was just changed: `base-url`, and a map with :email
  :first-and-last-name :user-agent. The only signal that makes an unauthorised reset visible
  (account-flows.md). Returns the postal message."
  [base-url {:keys [email first-and-last-name user-agent]}]
  {:from (str branding/email-sender-name " <" (emailfrom) ">")
   :to email
   :subject (str branding/app-name " password changed")
   :body (password-changed-email
          first-and-last-name
          (str base-url (routes/path-for routes/send-password-reset-page-route))
          (describe-browser user-agent)
          (attempt-time))})

(defn email-change-notice-html [first-and-last-name new-email when-str]
  (into
   [:div
    (str "Dear " (if (seq first-and-last-name) first-and-last-name (str branding/app-name " User")) ",")
    [:br] [:br]
    (str "Somebody asked us to move your account to " new-email ".")
    [:br] [:br]
    (str "That was on " when-str ". Nothing has moved yet -- the new address has to confirm first.")
    [:br] [:br]
    "If it was you, carry on: the confirmation is waiting in that other mailbox."
    [:br] [:br]
    "If it was not, somebody is signed in to your account. Change your password now, from this address, while it still reaches you:"
    [:br] [:br]
    "Sincerely,"
    [:br] [:br]
    (str "The " branding/email-sender-name)]
   (social-links-footer)))

(defn email-change-notice [first-and-last-name new-email when-str]
  [{:type "text/html"
    :content (str (hiccup/html (email-change-notice-html
                                first-and-last-name new-email when-str)))}])

(defn email-change-notice-message
  "The notice to the address that currently owns an account that somebody is moving it: a map with
  :email :first-and-last-name :new-email. The only notice the losing address gets
  (account-flows.md). Returns the postal message."
  [{:keys [email first-and-last-name new-email]}]
  {:from (str branding/email-sender-name " <" (emailfrom) ">")
   :to email
   :subject (str branding/app-name " email change requested")
   :body (email-change-notice
          first-and-last-name
          new-email
          (attempt-time))})

(defn unsubscribe-url
  "Build a full unsubscribe URL from a base-url and a pre-signed JWT token.
   Token is generated by routes/unsubscribe-token to avoid circular deps."
  [base-url token]
  (str base-url (routes/path-for routes/unsubscribe-route) "?token=" token))

;; ---------------------------------------------------------------------------
;; Error email — helpers
;; ---------------------------------------------------------------------------

(defn- root-cause [^Throwable ex]
  (loop [e ex]
    (if-let [c (.getCause e)] (recur c) e)))

(defn- orcpub-frame? [^StackTraceElement f]
  (s/starts-with? (.getClassName f) "orcpub."))

(def ^:private infra-prefixes
  ["org.eclipse.jetty." "io.pedestal." "clojure.lang."
   "java.lang.Thread" "sun.reflect." "java.util.concurrent."
   "clojure.core$"])

(defn- infra-frame? [^StackTraceElement f]
  (let [cls (.getClassName f)]
    (some #(s/starts-with? cls %) infra-prefixes)))

(defn- fmt-frame [^StackTraceElement f]
  (str "    " (.getClassName f) "." (.getMethodName f)
       " (" (.getFileName f) ":" (.getLineNumber f) ")"))

(defn- render-stack [^Throwable ex]
  (let [frames     (seq (.getStackTrace ex))
        app-frames (filter orcpub-frame? frames)
        suppressed (count (filter infra-frame? frames))]
    (str
     (if (seq app-frames)
       (s/join "\n" (map fmt-frame app-frames))
       (let [fallback (->> frames (remove infra-frame?) last)]
         (if fallback
           (str (fmt-frame fallback) "  <- deepest non-infrastructure frame")
           "  (no frames available)")))
     (when (pos? suppressed)
       (str "\n    ... " suppressed " infrastructure frames suppressed")))))

(defn- render-cause-chain [^Throwable ex]
  (let [sb (java.lang.StringBuilder.)]
    (loop [e ex, depth 0]
      (when e
        (.append sb (str (when (pos? depth) "\nCaused by: ")
                         (.getName (.getClass e))
                         ": " (or (.getMessage e) "(no message)") "\n"))
        (.append sb (render-stack e))
        (.append sb "\n")
        (recur (.getCause e) (inc depth))))
    (str sb)))

(defn- throttle-fingerprint [^Throwable ex]
  (let [root       (root-cause ex)
        root-class (.getName (.getClass root))
        frames     (seq (.getStackTrace ex))
        app-frame  (first (filter orcpub-frame? frames))]
    (if app-frame
      (str root-class "+" (.getClassName app-frame) "." (.getMethodName app-frame))
      (let [msg (or (.getMessage root) "")]
        (str root-class "+" (subs msg 0 (min 60 (count msg))))))))

(def ^:private safe-headers
  #{"user-agent" "referer" "content-type" "accept-language" "cf-ipcountry"
    "x-forwarded-for" "x-real-ip" "cf-ray" "sec-fetch-site" "sec-fetch-mode"
    "x-forwarded-host" "x-forwarded-proto"})

(def ^:private drop-req-keys
  #{:json-params :transit-params :form-params :body :db :conn
    :servlet-request :servlet-response :servlet :url-for
    :async-supported? :identity :character-encoding :protocol
    :path-params :content-length})

(defn- scrub-request [req]
  (-> (apply dissoc req drop-req-keys)
      (update :headers #(select-keys (or % {}) safe-headers))))

(defn- pedestal-wrapper?
  "True when ex-data looks like a Pedestal interceptor error map."
  [data]
  (and (map? data) (contains? data :exception) (contains? data :interceptor)))

(defn- email-subject [^Throwable real-ex request]
  (let [cls    (.getSimpleName (.getClass real-ex))
        msg    (let [m (or (.getMessage real-ex) "(no message)")]
                 (subs m 0 (min 80 (count m))))
        method (some-> (:request-method request) name s/upper-case)
        uri    (or (:uri request) "?")]
    (str "[" branding/app-name "] " cls ": " msg " @ " method " " uri)))

(defn- build-body [request real-ex pedestal-meta]
  (str
   "=== Request ===\n"
   (with-out-str (pprint/pprint (scrub-request request)))
   (when-let [u (:username request)] (str "User: " u "\n"))
   "\n=== Exception ===\n"
   (render-cause-chain real-ex)
   (when (instance? clojure.lang.ExceptionInfo real-ex)
     (when-let [d (ex-data real-ex)]
       (str "\n=== Exception Data ===\n"
            (with-out-str (pprint/pprint d)))))
   (when pedestal-meta
     (str "\n=== Interceptor Context ===\n"
          (with-out-str (pprint/pprint pedestal-meta))))))

;; ---------------------------------------------------------------------------

(defn send-error-email
  "Reports a server error to the admin alerts (orcpub.alerts), which email EMAIL_ERRORS_TO and
  log it. Strips credentials, cookies, body params and Datomic objects from the request; keeps
  orcpub.* stack frames (else the deepest non-infra frame); walks the whole cause chain; puts
  Pedestal interceptor metadata in its own section. The same error is one alert with a count.
  Returns at once; nothing is sent on the caller's thread. Returns nil."
  [context exception]
  (let [data-map      (ex-data exception)
        pedestal?     (pedestal-wrapper? data-map)
        real-ex       (if pedestal? (:exception data-map) exception)
        pedestal-meta (when pedestal? (dissoc data-map :exception :exception-type))
        request       (or (:request context) {})]
    (alerts/raise-current! (throttle-fingerprint real-ex)
                           (email-subject real-ex request)
                           (build-body request real-ex pedestal-meta))))

(defn send-alert!
  "Emails alert `message` ({:subject :body}) to EMAIL_ERRORS_TO, naming this server's copy.
  Returns true when the mail server accepted it; throws when it did not."
  [{:keys [subject body]}]
  (let [result (postal/send-message
                (email-cfg)
                {:from    (str branding/app-name " Errors <" (emailfrom) ">")
                 :to      (str (env/value :email-errors-to))
                 :subject subject
                 :body    [{:type "text/plain"
                            :content (str body "\n\n-- " (config/copy-name))}]})]
    (when (not= :SUCCESS (:error result))
      (throw (ex-info "alert email refused" {:postal-response result})))
    true))

(defn send-message!
  "Sends postal message `message` with the configured mail server; how the outbox sends. Returns
  true when the server took it; throws when it did not (orcpub.outbox decides whether to retry)."
  [message]
  (let [result (postal/send-message (email-cfg) message)]
    (when (not= :SUCCESS (:error result))
      (throw (ex-info "mail server did not accept the message" {:postal-response result})))
    true))

(defn outbox-channel
  "`send-message!` when a mail server is configured, else nil (player emails then wait queued)."
  []
  (when (configured?) send-message!))

(defn alert-channel
  "`send-alert!` when admin alerts can be emailed (a mail server and EMAIL_ERRORS_TO are set), else nil."
  []
  (when (and (configured?) (env/value :email-errors-to)) send-alert!))

;; ---------------------------------------------------------------------------
;; User-initiated "this character won't load" report
;; ---------------------------------------------------------------------------

(def ^:private report-throttle (atom {}))
(def ^:private report-throttle-window-ms (* 10 60 1000)) ; one report per user+char / 10 min

(def ^:private max-report-raw-chars 100000)

(defn- header-safe
  "Strip CR/LF and other control chars from a client-supplied value so it can't
   inject extra email headers (Bcc:, Subject:, ...) when placed in the Subject or
   an address header. Collapses runs of control chars to a single space."
  [s]
  (some-> s str (s/replace #"[\p{Cntrl}]+" " ") s/trim))

(defn character-report-message
  "PURE: the exact postal message map for a character-load report — no send, no
   guards. Extracted so the send path AND dev/test tooling compose the identical
   email (see orcpub.dev.report-preview). char-id (client-supplied) is sanitized
   before it reaches the subject, user-email before the cc, and the raw blob is
   capped so a hostile client can't inject headers or send an unbounded body."
  [{:keys [char-id user-email error raw]}]
  (let [char-id*    (header-safe char-id)
        user-email* (header-safe user-email)
        raw-str     (str raw)
        raw*        (if (> (count raw-str) max-report-raw-chars)
                      (str (subs raw-str 0 max-report-raw-chars)
                           "\n…[truncated " (- (count raw-str) max-report-raw-chars) " chars]")
                      raw-str)
        body (str "A user reported a character that could not be loaded.\n\n"
                  "Character ID: " char-id* "\n"
                  "Reported by:  " (or user-email* "unknown") "\n"
                  "Error:        " error "\n\n"
                  "--- raw character data ---\n" raw*)]
    (cond-> {:from    (str branding/email-sender-name " <" (emailfrom) ">")
             :to      branding/report-recipient
             :subject (str branding/app-name " — character load error (id " char-id* ")")
             :body    [{:type "text/plain" :content body}]}
      (not (s/blank? user-email*)) (assoc :cc user-email*))))

(defn character-report-block-reason
  "Why a report would NOT send, or nil if it would proceed. Reads the branding
   config and the throttle atom (does not mutate). Exposed so the dev preview can
   show the gate decision without sending."
  [{:keys [char-id user-email]}]
  (let [now (System/currentTimeMillis)]
    (cond
      (not branding/email-configured?)     :email-not-configured
      (s/blank? branding/report-recipient) :no-recipient
      (when-let [t (get @report-throttle [user-email char-id])]
        (< (- now t) report-throttle-window-ms)) :throttled
      :else nil)))

(defn send-character-report
  "Emails a user-submitted 'character could not be loaded' report (id, reader
   error, raw undecodable data) to the configured support address, cc'ing the
   reporting user. Returns {:sent? true} on success, else
   {:sent? false :reason <kw>} — when email isn't configured, no support address
   is set, the same user+character was reported within the throttle window, or
   the send failed — so the caller can tell the user to use the copyable report."
  [{:keys [char-id user-email] :as report}]
  (if-let [reason (character-report-block-reason report)]
    {:sent? false :reason reason}
    (do
      (swap! report-throttle assoc [user-email char-id] (System/currentTimeMillis))
      (try
        (let [result (postal/send-message (email-cfg) (character-report-message report))]
          (if (= :SUCCESS (:error result))
            {:sent? true}
            (do (println "WARNING: character report email not sent:" (:error result))
                {:sent? false :reason :send-failed})))
        (catch Exception e
          (println "ERROR: character report email failed:" (.getMessage e))
          {:sent? false :reason :exception})))))
