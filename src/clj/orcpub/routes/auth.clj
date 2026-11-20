(ns orcpub.routes.auth
  "HTTP handlers for accounts: sign-up, verification, login, password reset, email change and the
   user lookups, queries and messages they share. Moved out of orcpub.routes, which requires this
   namespace; this one never requires it."
  (:require [buddy.hashers :as hashers]
            [buddy.sign.jwt :as jwt]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [datomic.api :as d]
            [orcpub.artist-accounts :as artist-accounts]
            [orcpub.artist-credit :as artist-credit]
            [orcpub.config :as config]
            [orcpub.crypto :as crypto]
            [orcpub.datomic :as odb]
            [orcpub.email :as email]
            [orcpub.errors :as errors]
            [orcpub.fork.auth :as fork-auth]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.user-data :as user-data]
            [orcpub.index :refer [index]]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.outbox :as outbox]
            [orcpub.pwned :as pwned]
            [orcpub.registration :as registration]
            [orcpub.reset-key :as reset-key]
            [orcpub.route-map :as route-map]
            [orcpub.security :as security]
            [orcpub.time :as time :refer [hours ago from-now instant before?]]
            [orcpub.user-email :as user-email]
            [ring.util.response :as ring-resp]))

(def jwt-secret
  "JWT signing secret: /run/secrets/signature, else SIGNATURE (config/signature), nil when unset
   or blank. Never read the environment directly here, or a Docker-secrets deployment gets nil.
   Blank must be nil: buddy signs and verifies with \"\", so an empty SIGNATURE= accepted forged
   tokens. docs/kb/blank-env-values.md."
  (config/signature))

(when-not jwt-secret
  (println (str "WARNING: " config/signature-missing-message)))

(defn signature-or-throw
  "The JWT secret, or an error that says how to fix it.

   The token-minting paths used to read the env var straight into jwt/sign, which fails deep
   inside the library with nothing an operator can act on. check-auth already answered a
   clear 500; these did not."
  []
  (or jwt-secret
      (throw (ex-info config/signature-missing-message {:error :signature-not-set}))))

(defn first-user-by [db query value]
  (let [result (d/q query
                    db
                    value)
        user-id (ffirst result)]
    (d/pull db '[*] user-id)))

(def username-query
  '[:find ?e
    :in $ ?username
    :where [?e :orcpub.user/username ?username]])

;; Email lookup by the indexed key, or the stored address when it equals the key: registration has
;; long stored addresses lowercased, so those match while the start-up backfill has yet to reach
;; them (orcpub.user-email). Callers pass (user-email/email-key address).
(def email-query
  '[:find ?e
    :in $ ?email
    :where (or-join [?e ?email]
                    [?e :orcpub.user/email-key ?email]
                    [?e :orcpub.user/email ?email])])

(defn find-user-by-username-or-email
  "The account, pulled whole, whose username is `username-or-email` or whose email matches it in
   any casing; nil when none does."
  [db username-or-email]
  (d/q
   '[:find (pull ?e [*]) .
     :in $ ?name ?key
     :where (or-join [?e ?name ?key]
                     [?e :orcpub.user/username ?name]
                     [?e :orcpub.user/email-key ?key]
                     [?e :orcpub.user/email ?key])]
   db
   username-or-email
   (or (user-email/email-key username-or-email) "")))

(defn find-user-by-username [db username]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?username
         :where [?e :orcpub.user/username ?username]]
       db
       username))

(defn lookup-user-by-username [db username password]
  (let [user (d/q '[:find (pull ?e [*]) .
                    :in $ [?username ?password]
                    :where
                    [?e :orcpub.user/username ?username]
                    [?e :orcpub.user/password ?enc]
                    [(buddy.hashers/check ?password ?enc)]]
                  db
                  [username password])]
    user))

(defn lookup-user-by-email
  "The account, pulled whole, whose email matches `email` in any casing and whose password is
   `password`; without a :db/id when none does."
  [db email password]
  (first-user-by db
                 '{:find [?e]
                   :in [$ [?email ?password]]
                   :where [(or-join [?e ?email]
                                    [?e :orcpub.user/email-key ?email]
                                    [?e :orcpub.user/email ?email])
                           [?e :orcpub.user/password ?enc]
                           [(buddy.hashers/check ?password ?enc)]]}
                 [(user-email/email-key email) password]))

(defn lookup-user [db username password]
  (if (re-matches registration/email-format username)
    (lookup-user-by-email db username password)
    (lookup-user-by-username db username password)))

(defn redirect [route-key]
  (ring-resp/redirect (route-map/path-for route-key)))

(defn verification-expired? [verification-sent]
  (before? (instant verification-sent) (-> 24 hours ago)))

(defn login-error [error-key & [data]]
  {:status 401 :body (merge
                      data
                      {:error error-key})})

(defn create-token [username exp]
  ;; :minted, not :iat. buddy validates the registered claims it recognises, and
  ;; a standard iat is seconds where this is millis -- naming it apart keeps the
  ;; library out of it entirely.
  (jwt/sign {:user username
             :minted (System/currentTimeMillis)
             :exp exp}
            (signature-or-throw)))

(defn following-usernames [db ids]
  (map :orcpub.user/username
       (d/pull-many db '[:orcpub.user/username] ids)))

(defn artist-credit-body
  "What the account page needs to show an artist their credit."
  [db user-id]
  (when-let [{:keys [artist-id default current own locked]} (artist-credit/credit-for-account db user-id)]
    {:artist-id (name artist-id)
     :default default
     :current (select-keys current (into [:artist/id :artist/name :artist/link :artist/links]
                                         (vals artist-credit/page-switches)))
     :own own
     :locked locked
     ;; so the page can show which icon a link will get as it's typed
     :services (for [[icon {:keys [label color hosts]}] artist-credit/services]
                 {:icon icon :label label :color color :hosts (vec hosts)})
     :max-links artist-credit/max-links}))


(defn stored-pdf-file-name
  "The PDF file name choices saved on `user` (an entity or map), read and cleaned
   (pdf-spec/clean-file-name-format); {} when unset or unreadable."
  [user]
  (pdf-spec/clean-file-name-format
   (try (edn/read-string (str (:orcpub.user/pdf-file-name user))) (catch Exception _ nil))))

(defn user-body
  "Build the user API response. Core fields are inline; fork-specific
   fields (e.g. tier data) are added by user-data/enrich-response."
  [db user]
  (cond-> (user-data/enrich-response
           {:username (:orcpub.user/username user)
            :email (:orcpub.user/email user)
            :send-updates? (boolean (:orcpub.user/send-updates? user))
            :following (following-usernames db (map :db/id (:orcpub.user/following user)))}
           user)
    (:orcpub.user/pending-email user)
    (assoc :pending-email (:orcpub.user/pending-email user))

    ;; whether this deployment can store one at all (it needs a key)
    true
    (assoc :preferred-name-enabled? (crypto/enabled?))

    (:orcpub.user/preferred-name user)
    (assoc :preferred-name (crypto/decrypt crypto/preferred-name-purpose
                                           (:orcpub.user/preferred-name user)))

    (:orcpub.user/artist user)
    (assoc :artist-credit (artist-credit-body db (:db/id user)))

    (some? (:orcpub.user/dark-button-text? user))
    (assoc :dark-button-text? (:orcpub.user/dark-button-text? user))

    (:orcpub.user/pdf-file-name user)
    (assoc :pdf-file-name (stored-pdf-file-name user))))

(defn base-url [{:keys [scheme headers]}]
  (str (or (headers "x-forwarded-proto") (name scheme)) "://" (headers "host")))

(defn- site-url
  "The configured public site address (APP_URL), without a trailing slash, or nil. What links in
   mail to a stranger are built on, since a request's own headers are the sender's to choose."
  []
  (some-> branding/app-url not-empty (s/replace #"/+$" "")))

(defn mail-base-url
  "The address links in account mail are built on: the configured site URL (APP_URL) when set, else
   the request's own scheme and Host (`base-url`). Every mail link goes through here. GOTCHA: the
   request fallback exists so an install without APP_URL still works; those headers are the
   sender's to choose, so a production site sets APP_URL (the boot report says so when it is
   not). The taken-address notice does NOT fall back: it uses `site-url` and sends nothing without it."
  [request]
  (or (site-url) (base-url request)))

(def ^:private turnstile-failed-message
  "We couldn't confirm you're not a bot. Please try the check again.")

(defn- turnstile-message
  "Why the challenge refused: a blank token means the widget never ran (a blocked script), which the
   player can fix; a token that failed verification just means try the check again."
  [blank?]
  (if blank? errors/turnstile-blocked-message turnstile-failed-message))

(defn- refusal-response
  "The response to a refusal from security/guard or record!, in the style the route's policy names
   (:answer in security/auth-guards). The same for every address and name, and nothing here looks
   anything up, so a refusal cannot say whether an account exists. A failed challenge is retried
   with a fresh token."
  [route {:keys [reason blank?]}]
  (let [{:keys [answer message]} (security/policy route)]
    (cond
      (and (= reason :turnstile) (= answer :error)) (login-error errors/challenge-required {:challenge? true})
      (= reason :turnstile) {:status 400 :body {:general [(turnstile-message blank?)]}}
      (= answer :uniform) {:status 200}
      (= answer :error) (login-error errors/too-many-attempts (security/challenge-flag ::login))
      :else {:status 400 :body {:general [message]}})))

(defn bad-credentials-response [db username ip request]
  (let [{:keys [refusal notify]} (security/record! ::login :failure {:ip ip :typed-name username})]
    (if refusal
      (refusal-response ::login refusal)
      (let [user-for-username (find-user-by-username-or-email db username)
            notice (when (and notify (:db/id user-for-username))
                     (security/claim-notice! notify (:db/id user-for-username)))]
        ;; A name with no account is not logged: it is sometimes a password typed in the wrong box.
        (security/audit! :sign-in-failed request (:orcpub.user/username user-for-username))
        ;; Several addresses failing against ONE account inside a minute is also what
        ;; a person with a phone, a laptop and a tablet looks like -- so it notifies
        ;; the owner rather than locking them out. Runs on another thread, and its
        ;; outcome never reaches the response: failing logins must not learn a thing.
        (when notice
          (future
            (try
              (outbox/enqueue! (:conn request) :sign-in-attempts
                               (email/sign-in-attempts-message
                                (mail-base-url request)
                                (merge notice
                                       {:email (:orcpub.user/email user-for-username)
                                        :first-and-last-name (:orcpub.user/first-and-last-name user-for-username)
                                        :user-agent (get (:headers request) "user-agent")})))
              (catch Exception e
                (security/release-notice! notify (:db/id user-for-username) notice)
                (println "ERROR: could not queue a sign-in attempts notice:" (.getMessage e))))))
        ;; One answer for both. Telling somebody the username does not exist made
        ;; the login form the same membership test the reset endpoint was, and a
        ;; cheaper one, since it needs no mail to be sent.
        (login-error errors/bad-credentials (security/challenge-flag ::login))))))

(defn create-login-response [db conn user id & [headers]]
  (let [token (create-token (:orcpub.user/username user)
                            (-> fork-auth/token-lifetime-hours hours from-now))
        now (java.util.Date.)]
    (when fork-auth/track-last-login?
      (d/transact conn [{:db/id id
                         :orcpub.user/last-login now}]))
    {:status 200
     :headers headers
     :body {:user-data (user-body db user)
            :token token}}))

(defn login-response
  [{:keys [json-params db conn remote-addr] :as request}]
  (let [{raw-username :username raw-password :password} json-params
        refusal (when-not (or (s/blank? raw-username) (s/blank? raw-password))
                  ;; Before the credentials are looked at, so a bot without a token learns nothing
                  ;; from the answer, and a spray is stopped before it can land on the account it
                  ;; guesses right (see security/auth-guards).
                  (security/guard ::login {:ip remote-addr :typed-name raw-username
                                           :token (:turnstile-token json-params)}))]
    (cond
      (s/blank? raw-username) (login-error errors/username-required)
      (s/blank? raw-password) (login-error errors/password-required)
      refusal (refusal-response ::login refusal)

      :else (let [username (s/trim raw-username)
                  password (s/trim raw-password)
                  {:keys [:orcpub.user/verified?
                          :orcpub.user/verification-sent
                          :orcpub.user/email
                          :db/id] :as user} (lookup-user db username password)
                  unverified? (not verified?)
                  expired? (and verification-sent (verification-expired? verification-sent))]
              (cond
                (nil? id) (bad-credentials-response db username remote-addr request)
                (and unverified? expired?) (login-error errors/unverified-expired)
                unverified? (login-error errors/unverified {:email email})
                :else
                (do (security/record! ::login :success {:ip remote-addr :typed-name username})
                    (security/audit! :sign-in request (:orcpub.user/username user))
                    (create-login-response db conn user id)))))))

(defn login [request]
  (login-response request))

(defn user-for-email
  "The account, pulled whole, whose email matches `email` in any casing; without a :db/id when none
   does."
  [db email]
  (first-user-by db
                 '{:find [?e]
                   :in [$ ?email]
                   :where [(or-join [?e ?email]
                                    [?e :orcpub.user/email-key ?email]
                                    [?e :orcpub.user/email ?email])]}
                 (user-email/email-key email)))

(defn preferred-name-of
  "The name an email greets the account by: its preferred name, decrypted, else the fork's
   fallback, else its username."
  [user]
  (or (crypto/decrypt crypto/preferred-name-purpose (:orcpub.user/preferred-name user))
      fork-auth/verification-display-name
      (:orcpub.user/username user)))

(defn do-verification
  "Creates or refreshes a pending verification and queues its email in the same transaction
   (orcpub.outbox), so the account and the email are saved together or not at all; the outbox
   sends it and retries while the mail server is down. `params` is the user map, `tx-data` the
   account attributes (an existing {:db/id} for a resend). Returns the response map.
   Tests: registration_email_test.clj. docs/email-system.md mirrors this flow; change both."
  [request params conn & [tx-data]]
  (cond
    ;; No SMTP and no ALLOW_UNVERIFIED_REGISTRATION: fail closed. A typo'd or empty mount must
    ;; not quietly turn the site into open registration.
    (and (not (email/configured?))
         (not (email/unverified-registration-allowed?)))
    (do
      (println "ERROR: registration unavailable — EMAIL_SERVER_URL is unset, so no"
               "verification email can be sent. Set it, or set"
               "ALLOW_UNVERIFIED_REGISTRATION=true to accept accounts without"
               "verifying the address.")
      ;; A response, not a throw. A throw reaches the client as a bare 500 it
      ;; cannot tell apart from any other failure, so the form showed nothing.
      {:status 503 :body {:error :email-not-configured}})

    ;; Deliberately running without email: verify on the spot rather than
    ;; promising a mail that cannot be sent. The operator of a mail-less
    ;; instance is handing out the accounts themselves, so address ownership is
    ;; not being proved by anyone anyway.
    (not (email/configured?))
    (do
      (println "INFO: EMAIL_SERVER_URL is unset — verifying" (:username params)
               "on creation instead of sending a verification email.")
      (try
        @(d/transact conn [(merge tx-data {:orcpub.user/verified? true})])
        ;; The client branches on this to show "registration complete, you can
        ;; log in" rather than "check your email".
        {:status 200 :body {:verified? true}}
        (catch Exception e
          (println "ERROR: Failed to create account:" (.getMessage e))
          (throw (ex-info "Unable to complete registration. Please try again or contact support."
                          {:error :verification-failed}
                          e)))))
    :else
    (let [verification-key (str (java.util.UUID/randomUUID))
          existing-id (:db/id tx-data)
          ;; A resend may not undo a verification that landed after the caller read the account:
          ;; the write is a compare-and-swap on the value read just now, and a verified account is
          ;; left alone.
          verified-now (when existing-id
                         (:orcpub.user/verified? (d/entity (d/db conn) existing-id)))]
      (when-not (true? verified-now)
        (let [queued? (try
                        @(d/transact
                          conn
                          (cond-> [(merge
                                    tx-data
                                    {:db/id (or existing-id "verification-subject")
                                     :orcpub.user/verification-key verification-key
                                     :orcpub.user/verification-sent (java.util.Date.)}
                                    (when-not existing-id {:orcpub.user/verified? false}))
                                   (outbox/email-tx :verification
                                                    (email/verification-message (mail-base-url request) params verification-key))]
                            existing-id (conj [:db/cas existing-id :orcpub.user/verified? verified-now false])))
                        true
                        (catch Exception e
                          (when-not (odb/cas-failed? e)
                            (println "ERROR: Failed to create verification record:" (.getMessage e))
                            (throw (ex-info "Unable to complete registration. Please try again or contact support."
                                            {:error :verification-failed}
                                            e)))
                          false))]
          (when queued? (outbox/sent!))))
      {:status 200})))

(defn- breach-message
  "Why this reads as a strength verdict, not a security warning: the corpus
   measures commonness, not a breach or an attacker targeting this person.
   GOTCHA: speaks like the strength meter under the \"Too common\" badge, so it
   must not repeat what the badge already said. See account-flows.md."
  [_n]
  "A few words strung together are harder to guess and easier to remember.")

(def ^:private breach-refusal-threshold
  "How many corpus appearances make a password common enough to refuse.
   GOTCHA: below this line it's advisory only, surfaced by the strength meter
   while typing; at or above, refusal at submit is reserved for the egregious
   case -- one appearance is an old leak, four figures ships in every cracking
   wordlist. See account-flows.md."
  1000)

(defn- breach-errors
  "A validation map for an egregiously common password, or nil.

   Only a positive answer counts, and only one at or above the threshold: an
   unreachable service must read as no objection, and nor must a handful of
   appearances."
  [password]
  (let [result (pwned/check password)]
    (when (and (number? result) (>= result breach-refusal-threshold))
      ;; Its OWN key, not :password: a strength verdict, not a rule fault, so it
      ;; belongs to the meter -- the only thing on the page judging password quality.
      ;; Keyed with the rule faults instead, it would render as a red field error
      ;; sitting right above a meter reporting UNCOMMON in green: two disagreeing verdicts.
      {:password-common [(breach-message result)]})))

(def ^:private taken-email-refusal-message
  "This email can't be used. Check it and try again, or sign in.")

(defn- notify-taken-owner!
  "Mails the owner of `email` that somebody tried to sign up with it, at most once a day. Never
   throws and never changes the answer: a failure here must not tell a taken address from a new one."
  [{:keys [db conn]} email]
  (let [claimed (atom nil)]
    (try
      ;; GOTCHA: links come from the configured site address, never the request's Host or
      ;; X-Forwarded-Proto, which the sender controls: a forged Host would put a phishing link in
      ;; mail from us to the account's owner. No configured address, no notice.
      (when-let [site (site-url)]
        (let [owner (user-for-email db email)]
          (when-let [notice (and (:db/id owner)
                                 (security/claim-notice! :taken-signup email))]
            (reset! claimed notice)
            (outbox/enqueue! conn :signup-attempt
                             (email/signup-attempt-message
                              site
                              (assoc notice :email (or (:orcpub.user/email owner) email)))))))
      (catch Exception e
        ;; The claim is given back, or the owner is never told about this attempt.
        (when-let [notice @claimed] (security/release-notice! :taken-signup email notice))
        (println "ERROR: could not queue a sign-up attempt notice:" (.getMessage e))))))

(defn- answer-taken-email
  "The answer for a well-formed sign-up whose address already has an account. With a mail server:
   exactly what a new sign-up answers, nothing created, the owner mailed instead. Without one there
   is nobody to mail, so a plain refusal (owner decision 2026-11-04), not a field error naming the
   address.
   The password is hashed and thrown away so this path costs about what a new sign-up does, since a
   much faster answer would tell a taken address apart by timing alone."
  [request email password]
  (hashers/encrypt password)
  (if (email/configured?)
    (do (notify-taken-owner! request email)
        {:status 200})
    {:status 400 :body {:general [taken-email-refusal-message]}}))

(defn register [{:keys [json-params db conn remote-addr] :as request}]
  (let [{:keys [username email password send-updates?]} json-params
        username (when username (s/trim username))
        email (user-email/email-key email)
        password (when password (s/trim password))
        ;; Whether the address is taken is judged AFTER every check a new sign-up goes through,
        ;; and never shown: see answer-taken-email.
        taken? (boolean (seq (d/q email-query db email)))
        validation (registration/validate-registration
                    json-params
                    (seq (d/q username-query db username)))
        now (java.util.Date.)
        ;; Checked only once the form is otherwise valid: no reason to ask a third
        ;; party about a password attached to a malformed signup.
        validation (if (seq validation)
                     validation
                     (or (breach-errors password) validation))
        ;; Only for a form that was going to go through: no reason to ask a third party about a
        ;; malformed sign-up, or to count it against the host. The challenge comes first, so a bot
        ;; that fails it does not use up the host's sign-ups.
        refusal (when (empty? validation)
                  (security/guard ::register {:ip remote-addr :token (:turnstile-token json-params)}))]
    (cond
      (seq validation)
      {:status 400
       :body validation}

      refusal (refusal-response ::register refusal)

      taken?
      (answer-taken-email request email password)

      :else
      (let [response (do-verification
                      request
                      json-params
                      conn
                      (merge
                       {:orcpub.user/username username
                        :orcpub.user/password (hashers/encrypt password)
                        :orcpub.user/send-updates? send-updates?
                        :orcpub.user/created now}
                       (user-email/email-attrs email)
                       (when fork-auth/record-last-login-at-registration?
                         {:orcpub.user/last-login now})
                       (user-data/registration-defaults)))]
        (when (= 200 (:status response)) (security/audit! :registered request username))
        response))))

(def user-for-verification-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/verification-key ?key]])

(def user-for-email-query
  '[:find ?e
    :in $ ?email
    :where [?e :orcpub.user/email ?email]])

(defn user-for-verification-key [db key]
  (first-user-by db user-for-verification-key-query key))

(defn- link-artist-on-confirm!
  "A confirmed account just confirmed a new address. If it is an artist's configured
   account, link it. Never lets a failure here fail the confirmation itself."
  [conn id]
  (try (artist-accounts/on-email-confirmed! conn id (artist-accounts/configured) artist-accounts/notify)
       (catch Exception e
         (println "artist-accounts: link on confirmation failed -" (.getMessage e)))))

(defn verify [{:keys [query-params db conn] :as request}]
  (if-let [key (:key query-params)]
    (let [{:keys [:orcpub.user/verification-sent
                  :orcpub.user/verified?
                  :orcpub.user/username
                  :orcpub.user/pending-email
                  :db/id] :as user} (user-for-verification-key (d/db conn) key)]
      (if username
        (cond
          (and verified? (nil? pending-email))
          (redirect route-map/verify-success-route)

          (or (nil? verification-sent)
              (verification-expired? verification-sent))
          ;; Clean up stale pending state so user can request a fresh change
          (do (let [retractions (cond-> [[:db/retract id :orcpub.user/verification-key key]
                                         [:db/retract id :orcpub.user/verification-sent verification-sent]]
                                  pending-email
                                  (conj [:db/retract id :orcpub.user/pending-email pending-email]))]
                @(d/transact conn retractions))
              (redirect route-map/verify-failed-route))

          pending-email
          ;; Guard: re-check that the target email hasn't been claimed since request.
          ;; All paths retract verification-key and verification-sent to prevent
          ;; link reuse and avoid stale rate-limit data.
          (if (seq (d/q email-query (d/db conn) (user-email/email-key pending-email)))
            (do @(d/transact conn [[:db/retract id :orcpub.user/pending-email pending-email]
                                   [:db/retract id :orcpub.user/verification-key key]
                                   [:db/retract id :orcpub.user/verification-sent verification-sent]])
                (redirect route-map/verify-failed-route))
            (do @(d/transact conn [(assoc (user-email/email-attrs pending-email) :db/id id)
                                   [:db/retract id :orcpub.user/pending-email pending-email]
                                   [:db/retract id :orcpub.user/verification-key key]
                                   [:db/retract id :orcpub.user/verification-sent verification-sent]])
                (link-artist-on-confirm! conn id)
                (security/audit! :email-change-confirmed request username)
                (redirect route-map/verify-success-route)))

          ;; No artist link here: whoever registered chose the password, and confirming
          ;; proves only the address (artist-accounts claims such accounts instead).
          :else
          (do @(d/transact conn [{:db/id id
                                  :orcpub.user/verified? true}])
              (redirect route-map/verify-success-route)))
        {:status 400}))
    {:status 400}))

(defn re-verify [{:keys [query-params db conn remote-addr] :as request}]
  (let [email (:email query-params)
        user (delay (user-for-email db email))
        ;; The challenge first, then (only for an unverified account that exists) the allowances.
        ;; A limit answers like an unknown address, so it is not a membership oracle.
        refusal (security/guard ::re-verify
                                {:ip remote-addr :address email
                                 :token (:turnstile-token query-params)
                                 :actionable (delay (and (:db/id @user)
                                                         (not (:orcpub.user/verified? @user))))})
        query-params (dissoc query-params :turnstile-token)
        {:keys [:orcpub.user/verified?
                :db/id] :as user} (when-not refusal @user)]
    (cond
      refusal (refusal-response ::re-verify refusal)
      ;; No account, or one already verified: the same bare 200 as a resend, sending nothing, so
      ;; the answer does not say which addresses have verified accounts. (Passing {:db/id nil} on
      ;; would register a key-only record and email a link to any address typed.)
      (or verified? (nil? id)) {:status 200}
      :else
      (do-verification request
                       (merge query-params
                              {:first-and-last-name (preferred-name-of user)})
                       conn
                       {:db/id id}))))

(defn hash-reset-key
  "What goes in the database for a reset key: see orcpub.reset-key, shared with
   the artist welcome link."
  [key]
  (reset-key/digest key))

;; Two hours. One is the usual choice and is a one-line change here; two leaves
;; room for mail that takes a while to arrive and for somebody who reads it on
;; the way home, on a site whose worst case is a character sheet.
(def password-reset-valid-hours 2)

(defn- notice-tx
  "Transaction data queuing the email `build` returns (a function of no arguments) as `kind`, or
   nil when building it fails, which is logged. For notices that must never cost the change they
   report on."
  [kind build]
  (try (outbox/email-tx kind (build))
       (catch Exception e
         (println "ERROR: could not build a" (name kind) "email; the change goes ahead without it:"
                  (.getMessage e))
         nil)))

(defn do-send-password-reset [user-id email conn request]
  (let [key (str (java.util.UUID/randomUUID))]
    (try
      @(d/transact
        conn
        (cond-> [{:db/id user-id
                  :orcpub.user/password-reset-key (hash-reset-key key)
                  :orcpub.user/password-reset-sent (java.util.Date.)}
                 (outbox/email-tx :password-reset
                                  (email/reset-message
                                   (mail-base-url request)
                                   {:first-and-last-name (preferred-name-of (d/entity (d/db conn) user-id))
                                    :email email}
                                   key))]
          ;; a fresh ordinary reset replaces a week-long welcome link, so it
          ;; must not inherit that link's expiry
          (:orcpub.user/password-reset-expires (d/entity (d/db conn) user-id))
          (conj [:db/retract user-id :orcpub.user/password-reset-expires
                 (:orcpub.user/password-reset-expires (d/entity (d/db conn) user-id))])))
      (outbox/sent!)
      {:status 200}
      (catch Exception e
        (println "ERROR: Failed to initiate password reset for user" user-id ":" (.getMessage e))
        (throw (ex-info "Unable to initiate password reset. Please try again or contact support."
                        {:error :password-reset-failed
                         :user-id user-id}
                        e))))))

(defn password-reset-expired? [password-reset-sent]
  (and password-reset-sent
       (before? (instant password-reset-sent) (-> password-reset-valid-hours hours ago))))

(defn reset-link-expired?
  "Whether a reset link is past its expiry: its own expiry when it has one
   (the artist welcome link lasts a week), otherwise the usual two hours."
  [{:keys [:orcpub.user/password-reset-sent :orcpub.user/password-reset-expires]}]
  (if password-reset-expires
    (.after (java.util.Date.) password-reset-expires)
    (password-reset-expired? password-reset-sent)))

(defn password-already-reset? [password-reset password-reset-sent]
  (and password-reset (before? (instant password-reset-sent) (instant password-reset))))

(defn send-password-reset [{:keys [query-params db conn remote-addr] :as request}]
  (let [email (:email query-params)
        user (delay (user-for-email db email))
        ;; The answer is identical whether or not that address has an account: confirmed-valid
        ;; addresses are precisely what a stuffing run needs. The allowances are spent only for an
        ;; address that has an account, after the lookup, and a limit never changes the response --
        ;; one that visibly altered it would itself be a membership oracle.
        refusal (security/guard ::send-password-reset
                                {:ip remote-addr :address email
                                 :token (:turnstile-token query-params)
                                 :actionable (delay (:db/id @user))})]
    (or (when refusal (refusal-response ::send-password-reset refusal))
        (do (when-let [id (:db/id @user)]
              (try
                (do-send-password-reset id email conn request)
                (security/audit! :password-reset-requested request
                                 (:orcpub.user/username (d/entity db id)))
                (catch Exception e
                  ;; Swallowed on purpose. Letting this escape would restore the oracle
                  ;; in a subtler form -- only a real account can fail to be emailed,
                  ;; so an error response would mark the address as registered.
                  (println "ERROR: password reset for a known address could not be sent:"
                           (.getMessage e)))))
            {:status 200}))))

(def reset-link-spent-message
  "This reset link has expired or has already been used. Request a new one from the login page.")

(defn do-password-reset [conn user-id password & [request user]]
  (try
    @(d/transact
      conn
      (cond-> [{:db/id user-id
                :orcpub.user/password (hashers/encrypt (s/trim password))
                :orcpub.user/verified? true}
               ;; Conditional on the reset this request read (nil: none yet), so of two submits of
               ;; one link racing, the second fails instead of also setting a password.
               [:db/cas user-id :orcpub.user/password-reset
                (:orcpub.user/password-reset user) (java.util.Date.)]]
        ;; The link is spent with the password it set, in the same transaction.
        (:orcpub.user/password-reset-key user)
        (conj [:db/retract user-id :orcpub.user/password-reset-key
               (:orcpub.user/password-reset-key user)])
        ;; The notice goes with the change: it is the only sign an unauthorised reset leaves.
        (and request (:orcpub.user/email user))
        (conj (notice-tx :password-changed
                         #(email/password-changed-message
                           (mail-base-url request)
                           {:email (:orcpub.user/email user)
                            :first-and-last-name (:orcpub.user/first-and-last-name user)
                            :user-agent (get (:headers request) "user-agent")})))
        true (->> (remove nil?))))
    (outbox/sent!)
    ;; Before anything else that can fail: every token minted before now is no
    ;; longer good for this account, including the one this very request is
    ;; using. That is the point -- a reset is how somebody takes their account
    ;; back, and leaving the intruder's session alive would defeat it.
    (when-let [username (:orcpub.user/username user)]
      (security/note-password-changed! username)
      (when request (security/audit! :password-reset request username)))
    {:status 200}
    (catch Exception e
      (if (odb/cas-failed? e)
        {:status 400 :body {:general [reset-link-spent-message]}}
        (do (println "ERROR: Failed to reset password for user" user-id ":" (.getMessage e))
            (throw (ex-info "Unable to reset password. Please try again or contact support."
                            {:error :password-update-failed
                             :user-id user-id}
                            e)))))))

(declare user-by-password-reset-key-query)

(defn reset-password
  "Set a new password with the key from a reset link. The key is the only authority: opening
   the link grants no session, and the key is checked here, at submit, then retired."
  [{:keys [json-params db conn] :as request}]
  (try
    (let [{:keys [password verify-password]} json-params
          ;; Trimmed before anything judges it, because do-password-reset stores it
          ;; trimmed and the corpus lookup hashes it: an untrimmed check asks about
          ;; a different string from the one saved.
          password        (some-> password s/trim)
          verify-password (some-> verify-password s/trim)
          reset-key (:key json-params)
          {:keys [:db/id :orcpub.user/username :orcpub.user/email
                  :orcpub.user/password-reset-sent :orcpub.user/password-reset] :as user}
          (when-not (s/blank? reset-key)
            (first-user-by db user-by-password-reset-key-query (hash-reset-key reset-key)))
          usable? (and id
                       ;; a welcome link carries its own, longer expiry
                       (not (reset-link-expired? user))
                       (not (password-already-reset? password-reset password-reset-sent)))
          ;; The username is known here, even though this page never shows it,
          ;; so a password that IS the username can be refused the way
          ;; registration already refuses one. Without the context this was the
          ;; one rule the two paths did not share.
          context {:username username :email email}
          rule-errors (registration/validate-password password context)
          ;; Asked once. Only reached when the password is otherwise acceptable,
          ;; so a rejected reset never costs a call.
          breached (when (and usable? (= password verify-password) (empty? rule-errors))
                     (first (:password-common (breach-errors password))))]
      ;; Field-keyed, in :body, the same shape registration answers with.
      ;; :message is not a Pedestal response key: these came back as a 400 with
      ;; an EMPTY body, so the reasons never left the server and the page had
      ;; nothing to show but a generic apology.
      (cond
        ;; Missing, unknown, expired and used are one answer: this link no longer works.
        (not usable?)
        {:status 400 :body {:general [reset-link-spent-message]}}

        (not= password verify-password)
        {:status 400 :body {:verify-password ["Passwords do not match"]}}

        (seq rule-errors) {:status 400 :body rule-errors}
        breached {:status 400 :body {:password-common [breached]}}
        :else (do-password-reset conn id password request user)))
    (catch Throwable t (prn t) (throw t))))

(def user-by-password-reset-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/password-reset-key ?key]])

(defn check-field [query value db]
  {:status 200
   :body (-> (d/q query db value)
             seq
             boolean
             str)})

(defn check-username [{:keys [db query-params]}]
  (check-field username-query (:username query-params) db))

(defn rate-limit-remaining-secs
  "Seconds until the user can act again. In the 0–1 min zone (email in transit)
   returns time until the 1-min resend window opens. In the 1–5 min zone (for a
   different email) returns time until the 5-min cooldown expires."
  [verification-sent new-email pending-email]
  (when verification-sent
    (let [elapsed-ms (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))
          ;; If same email, they're waiting for the 1-min resend window to open.
          ;; If different email, they're waiting for the full 5-min cooldown.
          target-ms (if (= new-email pending-email)
                      (* 1 60 1000)
                      (* 5 60 1000))
          remaining-ms (- target-ms elapsed-ms)]
      (when (pos? remaining-ms)
        (int (Math/ceil (/ remaining-ms 1000.0)))))))

(defn email-change-rate-limited? [verification-sent pending-email new-email]
  ;; Only rate-limits a pending email change, not registration verification.
  ;; From verification-sent: 0–1 min always blocked (email in transit); 1–5 min only a
  ;; resend to the same email is allowed; 5+ min open.
  (and pending-email
       verification-sent
       (let [elapsed-ms (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))
             same-email? (= new-email pending-email)]
         (cond
           (>= elapsed-ms (* 5 60 1000)) false          ;; past cooldown
           (< elapsed-ms (* 1 60 1000))  true           ;; too soon
           :else                          (not same-email?)))) ;; 1-5 min: resend ok, new email blocked
  )

(defn request-email-change [{:keys [transit-params db conn identity] :as request}]
  (try
    ;; Client sends {:new-email "..."} (confirm-email is validated client-side only)
    (let [new-email (or (user-email/email-key (:new-email transit-params)) "")
          current-password (:current-password transit-params)
          username (:user identity)]
      (if (nil? username)
        {:status 400 :body {:error :user-not-found}}
        (let [{:keys [:db/id
                      :orcpub.user/email
                      :orcpub.user/pending-email
                      :orcpub.user/verification-sent] :as user} (find-user-by-username db username)]
          (cond
            (nil? id)
            {:status 400 :body {:error :user-not-found}}

            ;; Re-authentication: moving an account to another address takes it away
            ;; from whoever holds this one, requiring more than just a session -- a
            ;; borrowed or forgotten session alone must not be enough to walk off with
            ;; the account. The password is compared, never read: lookup-user does the hash check.
            (nil? (:db/id (lookup-user db username (str current-password))))
            {:status 400 :body {:error :bad-credentials}}

            (registration/bad-email? new-email)
            {:status 400 :body {:error :invalid-email}}

            (= new-email (user-email/email-key email))
            {:status 400 :body {:error :same-as-current}}

            (email-change-rate-limited? verification-sent pending-email new-email)
            {:status 429 :body {:error :too-many-requests
                                :retry-after-secs (rate-limit-remaining-secs verification-sent new-email pending-email)}}

            ;; Check no other account already owns this email
            (seq (d/q email-query db new-email))
            {:status 400 :body {:error :email-taken}}

            ;; Free resend: same email, 1–5 min after original send. Re-send with
            ;; existing key and don't update verification-sent (no rolling window).
            (and (= new-email pending-email)
                 verification-sent
                 (let [elapsed (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))]
                   (and (>= elapsed (* 1 60 1000))
                        (< elapsed (* 5 60 1000)))))
            (try
              (outbox/enqueue! conn :email-change-verification
                               (email/email-change-verification-message
                                (mail-base-url request)
                                {:email new-email :username username
                                 :preferred-name (preferred-name-of user)}
                                (:orcpub.user/verification-key user)))
              {:status 200 :body {:pending-email new-email}}
              (catch Throwable e
                (prn "Email resend could not be queued:" (.getMessage e))
                {:status 500 :body {:error :email-send-failed}}))

            :else
            (let [verification-key (str (java.util.UUID/randomUUID))
                  now (java.util.Date.)]
              ;; The verification goes to the NEW address, unreachable by the owner if this
              ;; wasn't them, so the notice to the current address goes in the same transaction.
              @(d/transact conn (remove nil? [{:db/id id
                                  :orcpub.user/pending-email new-email
                                  :orcpub.user/verification-key verification-key
                                  :orcpub.user/verification-sent now}
                                 (outbox/email-tx :email-change-verification
                                                  (email/email-change-verification-message
                                                   (mail-base-url request)
                                                   {:email new-email :username username
                                                    :preferred-name (preferred-name-of user)}
                                                   verification-key))
                                 (notice-tx :email-change-notice
                                            #(email/email-change-notice-message
                                              {:email email
                                               :first-and-last-name (:orcpub.user/first-and-last-name user)
                                               :new-email new-email}))]))
              (outbox/sent!)
              (security/audit! :email-change-requested request username)
              {:status 200 :body {:pending-email new-email}})))))
    (catch Throwable e (prn e) (throw e))))

(defn reset-password-page [{:keys [query-params db] :as req}]
  (let [key (:key query-params)
        {:keys [:db/id
                :orcpub.user/username
                :orcpub.user/password-reset-sent
                :orcpub.user/password-reset] :as user}
        ;; The link carries the key; the table holds only its digest.
        (when-not (s/blank? key)
          (first-user-by db user-by-password-reset-key-query (hash-reset-key key)))
        expired? (reset-link-expired? user)
        already-reset? (password-already-reset? password-reset password-reset-sent)]
    (cond
      ;; Covers both no key at all and a key matching nobody, so neither bare-strings
      ;; a "Key is required" error nor falls through to :else and signs a token for
      ;; a nil username. A mangled link and an expired one are the same event to the
      ;; reader -- it doesn't work -- so both land on the page offering to send a new one.
      (nil? id) (redirect route-map/password-reset-expired-route)
      expired? (redirect route-map/password-reset-expired-route)
      already-reset? (redirect route-map/password-reset-used-route)
      ;; No session: the key in the URL is what the form submits. The username cookie grants
      ;; nothing; it lets the meter flag a password equal to it. no-referrer keeps the key out of
      ;; the Referer of anything this page loads.
      :else (index req {:headers {"Referrer-Policy" "no-referrer"}
                        :cookies {"reset-username" {:value username :path "/"
                                                    :max-age (* password-reset-valid-hours 3600)
                                                    :same-site :strict}}}))))
