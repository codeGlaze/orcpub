(ns orcpub.routes
  (:require [io.pedestal.http :as http]
            [orcpub.artist-accounts :as artist-accounts]
            [orcpub.bounded-cache :as bounded-cache]
            [cheshire.core :as cheshire]
            [orcpub.work-pool :as work-pool]
            [orcpub.portrait-store :as portrait-store]
            [orcpub.art-mark :as art-mark]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [io.pedestal.http.ring-middlewares :as ring]
            [ring.middleware.resource :as ring-resource]
            [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.error :as error-int]
            [io.pedestal.interceptor.chain :refer [terminate]]
            #_[com.stuartsierra.component :as component]
            [buddy.auth.protocols :as proto]
            [buddy.auth.backends :as backends]
            [buddy.sign.jwt :as jwt]
            [buddy.hashers :as hashers]
            [buddy.auth.middleware :refer [authentication-request]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [orcpub.time :as time :refer [hours ago from-now instant before?]]
            [clojure.string :as s]
            [clojure.spec.alpha :as spec]
            [clojure.pprint]
            [orcpub.dnd.e5.skills :as skill5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.portrait-assets :as portrait-assets5e]
            [orcpub.dnd.e5.artist-profile :as artist-profile]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.spell-annotations :as spell-annotations]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.share-bundle :as sb]
            [orcpub.dnd.e5.template :as t5e]
            [datomic.api :as d]
            [bidi.bidi :as bidi]
            [orcpub.common :as common]
            [orcpub.route-map :as route-map]
            [orcpub.errors :as errors]
            [orcpub.privacy :as privacy]
            [orcpub.config :as config]
            [orcpub.email :as email]
            [orcpub.outbox :as outbox]
            [orcpub.loading-spinner :as spinner]
            [orcpub.index :refer [index-page]]
            [orcpub.pdf :as pdf]
            [orcpub.pdf.draw :as draw]
            [orcpub.pdf.cards :as cards]
            [orcpub.portrait-render :as portrait-render]
            [orcpub.reset-key :as reset-key]
            [orcpub.portrait-pack.strands :as strands]
            [orcpub.dnd.e5.portrait-effects :as portrait-fx]
            [orcpub.artist-credit :as artist-credit]
            [orcpub.artist-credit-notices :as credit-notices]
            [orcpub.artist-email :as artist-email]
            [orcpub.crypto :as crypto]
            [orcpub.spellbook :as spellbook]
            [orcpub.dnd.e5.emblems :as emblems]
            [orcpub.registration :as registration]
            [orcpub.pwned :as pwned]
            [orcpub.entity.strict :as se]
            [orcpub.entity :as entity]
            [orcpub.security :as security]
            [orcpub.user-email :as user-email]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.auth :as auth]
            [orcpub.fork.user-data :as user-data]
            [orcpub.routes.party :as party]
            [orcpub.routes.folder :as folder]
            [orcpub.routes.share :as share]
            [orcpub.datomic :as odb]
            [hiccup.page :as page]
            [hiccup2.core :as h]
            [orcpub.env :as env]
            [clojure.set :as sets]
            [ring.middleware.head :as head]
            [ring.util.codec :as codec]
            [ring.util.request :as req])
  ;; PDFBox 3.x loads documents through Loader/loadPDF, which takes a byte array, not an
  ;; InputStream.
  (:import (org.apache.pdfbox.pdmodel PDPage PDPageContentStream)
           org.apache.pdfbox.Loader
           (java.io ByteArrayOutputStream ByteArrayInputStream))
  (:gen-class))

(deftype FixedBuffer [^long len])

(def ^:private jwt-secret
  "JWT signing secret: /run/secrets/signature, else SIGNATURE (config/signature), nil when unset
   or blank. Never read the environment directly here, or a Docker-secrets deployment gets nil.
   Blank must be nil: buddy signs and verifies with \"\", so an empty SIGNATURE= accepted forged
   tokens. docs/kb/blank-env-values.md."
  (config/signature))

(when-not jwt-secret
  (println (str "WARNING: " config/signature-missing-message)))

(defn- signature-or-throw
  "The JWT secret, or an error that says how to fix it.

   The token-minting paths used to read the env var straight into jwt/sign, which fails deep
   inside the library with nothing an operator can act on. check-auth already answered a
   clear 500; these did not."
  []
  (or jwt-secret
      (throw (ex-info config/signature-missing-message {:error :signature-not-set}))))

(def backend (backends/jws {:secret jwt-secret}))

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

(defn terminate-request [context status message]
  (-> context
      terminate
      (assoc :response {:status status :body {:message message}})))

(def check-auth
  "Interceptor that verifies the JWT bearer token on authenticated routes.
   Returns 401 for missing/invalid tokens, 500 with diagnostic if the
   JWT secret itself is not configured."
  (interceptor/interceptor
   {:name :check-auth
    :enter (fn [context]
             (if-not jwt-secret
               ;; The full remedy, not just the symptom: whoever sees this 500 in a log is
               ;; the person who can fix it.
               (terminate-request context 500
                                  (str "Server misconfigured. " config/signature-missing-message))
               (try
                 (let [request (:request context)
                       updated-request (authentication-request request backend)
                       username (get-in updated-request [:identity :user])
                       minted (get-in updated-request [:identity :minted])]
                   (cond
                     (not (and (:identity updated-request) username))
                     (do (when (get-in request [:headers "authorization"])
                           (security/audit! :token-invalid request))
                         (terminate-request context 401 "Unauthorized"))

                     ;; Changing a password takes back the sessions that existed
                     ;; before it. The same 401 as any other refusal: which of
                     ;; the two reasons it was is not the caller's business.
                     (security/token-withdrawn? username minted)
                     (do (security/audit! :token-withdrawn request username)
                         (terminate-request context 401 "Unauthorized"))

                     :else
                     (assoc context :request (assoc updated-request :username username))))
                 (catch Exception e
                   (security/audit! :token-invalid (:request context))
                   (terminate-request context 401
                                      (str "Authentication failed: "
                                           (.getMessage e)))))))}))

(defn withdraw-sessions
  "Signs every session for this account out, including the one asking.

   Deliberately does NOT require the password: somebody who thinks their account
   is open somewhere they cannot reach should be able to close it from the
   session they already hold, and they have already proved they hold it. It
   grants an attacker with a session nothing -- they can sign themselves out."
  [{:keys [conn db identity] :as _request}]
  (let [username (:user identity)
        {:keys [:db/id]} (when username (first-user-by db username-query username))]
    (if-not id
      {:status 400 :body {:error :user-not-found}}
      (let [now (java.util.Date.)]
        @(d/transact conn [{:db/id id :orcpub.user/sessions-withdrawn now}])
        (security/note-password-changed! username (.getTime now))
        {:status 200 :body {:withdrawn true}}))))

(def ^:private recent-password-changes-query
  ;; Either reason a token is withdrawn. The caller keeps the LATER per account,
  ;; so signing out everywhere and then resetting -- or the reverse -- does not
  ;; let the earlier of the two reinstate anything.
  '[:find ?username ?changed
    :in $ ?since
    :where
    (or [?e :orcpub.user/password-reset ?changed]
        [?e :orcpub.user/sessions-withdrawn ?changed])
    [(> ?changed ?since)]
    [?e :orcpub.user/username ?username]])

(defn refresh-token-withdrawals!
  "Rebuilds the in-memory register of passwords that moved recently, from the
   database, where the durable record lives.
   GOTCHA: memory-only, so a restart silently reinstates every token it was
   holding, looking exactly like working; only changes inside the token
   lifetime matter, since older ones already expired. See account-flows.md."
  [db]
  (let [since (java.util.Date. (- (System/currentTimeMillis)
                                  (* auth/token-lifetime-hours 60 60 1000)))
        rows (d/q recent-password-changes-query db since)]
    (security/absorb-password-changes!
     (reduce (fn [m [username ^java.util.Date changed]]
               (update m username (fnil max 0) (.getTime changed)))
             {}
             rows)
     (.getTime since))
    (count rows)))

(defn withdrawal-refresh-job
  "Heartbeat job: re-read the register from the database. Also repairs it if a
   note was ever lost, since the database is the record and this is the cache."
  [conn]
  (refresh-token-withdrawals! (d/db conn))
  nil)

(defn party-owner [db id]
  (d/q '[:find ?owner .
         :in $ ?id
         :where [?id :orcpub.dnd.e5.party/owner ?owner]]
       db
       id))

(def id-path [:request :path-params :id])

(def parse-id
  (interceptor/interceptor
   {:name :parse-id
    :enter (fn [context]
             (let [id-str (get-in context id-path)]
               (if (and id-str (re-matches #"\d+" id-str))
                 (assoc-in context
                           id-path
                           (Long/parseLong id-str))
                 (terminate-request context 400 "Bad ID"))))}))


(def check-party-owner
  (interceptor/interceptor
   {:name :check-party-owner
    :enter (fn [context]
             (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                   party-owner (party-owner db id)]
               (if (= (:user identity) party-owner)
                 context
                 (terminate-request context 401 "You don't own this party"))))}))

(defn folder-owner [db id]
  (d/q '[:find ?owner .
         :in $ ?id
         :where [?id :orcpub.dnd.e5.folder/owner ?owner]]
       db
       id))

(def check-folder-owner
  (interceptor/interceptor
   {:name :check-folder-owner
    :enter (fn [context]
             (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                   owner (folder-owner db id)]
               (cond
                 (nil? owner) (terminate-request context 404 "Folder not found")
                 (= (:user identity) owner) context
                 :else (terminate-request context 401 "You don't own this folder"))))}))

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

(def max-preferred-name-length 40)

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
    (assoc :dark-button-text? (:orcpub.user/dark-button-text? user))))

(defn base-url [{:keys [scheme headers]}]
  (str (or (headers "x-forwarded-proto") (name scheme)) "://" (headers "host")))

(defn bad-credentials-response [db username ip request]
  (security/add-failed-login-attempt! username ip)
  (if (security/too-many-attempts-for-username? username)
    (do (security/note-refusal! :login-username)
        (login-error errors/too-many-attempts))
    (let [user-for-username (find-user-by-username-or-email db username)]
      ;; A name with no account is not logged: it is sometimes a password typed in the wrong box.
      (security/audit! :sign-in-failed request (:orcpub.user/username user-for-username))
      ;; Several addresses failing against ONE account inside a minute is also what
      ;; a person with a phone, a laptop and a tablet looks like -- so it notifies
      ;; the owner rather than locking them out. Runs on another thread, and its
      ;; outcome never reaches the response: failing logins must not learn a thing.
      (when (and (:db/id user-for-username)
                 (security/multiple-ip-attempts-to-same-account? username)
                 (security/claim-sign-in-notice! username))
        (future
          (try
            (outbox/enqueue! (:conn request) :sign-in-attempts
                             (email/sign-in-attempts-message
                              (base-url request)
                              {:email (:orcpub.user/email user-for-username)
                               :first-and-last-name (:orcpub.user/first-and-last-name user-for-username)
                               :user-agent (get (:headers request) "user-agent")}))
            (catch Exception e
              (println "ERROR: could not queue a sign-in attempts notice:" (.getMessage e))))))
      ;; One answer for both. Telling somebody the username does not exist made
      ;; the login form the same membership test the reset endpoint was, and a
      ;; cheaper one, since it needs no mail to be sent.
      (login-error errors/bad-credentials))))

(defn create-login-response [db conn user id & [headers]]
  (let [token (create-token (:orcpub.user/username user)
                            (-> auth/token-lifetime-hours hours from-now))
        now (java.util.Date.)]
    (when auth/track-last-login?
      (d/transact conn [{:db/id id
                         :orcpub.user/last-login now}]))
    {:status 200
     :headers headers
     :body {:user-data (user-body db user)
            :token token}}))

(defn login-response
  [{:keys [json-params db conn remote-addr] :as request}]
  (let [{raw-username :username raw-password :password} json-params]
    (cond
      (s/blank? raw-username) (login-error errors/username-required)
      (s/blank? raw-password) (login-error errors/password-required)

      ;; Checked before the credentials, not on the failure path: stuffing ends on
      ;; the one account it guesses right, so a check that only runs after a failed
      ;; lookup would never see that attempt. Trips on five DISTINCT usernames
      ;; failing from this address inside a minute -- one person on several devices can't, a spray across accounts can.
      (security/multiple-account-access? remote-addr)
      (do (security/note-refusal! :login-spray)
          (login-error errors/too-many-attempts))

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
                (do (security/audit! :sign-in request (:orcpub.user/username user))
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
      auth/verification-display-name
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
          existing-id (:db/id tx-data)]
      (try
        @(d/transact
          conn
          [(merge
            tx-data
            {:db/id (or existing-id "verification-subject")
             :orcpub.user/verified? false
             :orcpub.user/verification-key verification-key
             :orcpub.user/verification-sent (java.util.Date.)})
           (outbox/email-tx :verification
                            (email/verification-message (base-url request) params verification-key))])
        (catch Exception e
          (println "ERROR: Failed to create verification record:" (.getMessage e))
          (throw (ex-info "Unable to complete registration. Please try again or contact support."
                          {:error :verification-failed}
                          e))))
      (outbox/sent!)
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

(def ^:private registration-throttled-message
  (str "Too many accounts have been created from this connection in the last hour. "
       "Try again a little later, or email us if you are stuck."))

(defn register [{:keys [json-params db conn remote-addr] :as request}]
  (let [{:keys [username email password send-updates?]} json-params
        username (when username (s/trim username))
        email (user-email/email-key email)
        password (when password (s/trim password))
        validation (registration/validate-registration
                    json-params
                    (seq (d/q email-query db email))
                    (seq (d/q username-query db username)))
        now (java.util.Date.)
        ;; Checked only once the form is otherwise valid: no reason to ask a third
        ;; party about a password attached to a malformed signup.
        validation (if (seq validation)
                     validation
                     (or (breach-errors password) validation))
        ;; Checked last, so a form that was going to be rejected anyway is not
        ;; counted against the host. :general rather than a field key: nobody
        ;; can edit their way past a rate limit, so it must not sit in the map
        ;; that disables the button.
        validation (if (or (seq validation) (security/registration-allowed? remote-addr))
                     validation
                     (assoc validation :general [registration-throttled-message]))]
    (if (seq validation)
      {:status 400
       :body validation}
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
                       (when auth/record-last-login-at-registration?
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

(defn user-id-for-username [db username]
  (d/q
   '[:find ?e .
     :in $ ?username
     :where [?e :orcpub.user/username ?username]]
   db
   username))

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

(defn re-verify [{:keys [query-params db conn] :as request}]
  (let [email (:email query-params)
        {:keys [:orcpub.user/verification-sent
                :orcpub.user/verified?
                :db/id] :as user} (user-for-email db email)]
    (cond
      verified? (redirect route-map/verify-success-route)
      ;; No account for this address: answer like a resend and create nothing. Passing
      ;; {:db/id nil} on would register a key-only record and email a link to any address typed.
      (nil? id) {:status 200}
      :else
      (do-verification request
                       (merge query-params
                              {:first-and-last-name (preferred-name-of user)})
                       conn
                       {:db/id id}))))

;; ─── Email Preferences ─────────────────────────────────────────────

(defn unsubscribe-token
  "Create a JWT-signed unsubscribe token for embedding in email links.
   Stateless — no DB storage needed. Verified by checking JWT signature."
  [email]
  (jwt/sign {:email (common/ascii-lower-case email) :action "unsubscribe"}
            (signature-or-throw)))

(defn unsubscribe
  "GET handler for /unsubscribe?token=<jwt>.
   Verifies JWT signature, sets send-updates? to false, redirects to success page.
   Idempotent — unsubscribing twice is harmless."
  [{:keys [query-params db conn]}]
  (let [token (:token query-params)]
    (if (s/blank? token)
      {:status 400 :body "Missing token"}
      (try
        (let [{:keys [email action]} (jwt/unsign token (signature-or-throw))]
          (if (not= "unsubscribe" action)
            {:status 400 :body "Invalid token"}
            (let [{:keys [:db/id]} (user-for-email (d/db conn) email)]
              (if id
                (do @(d/transact conn [{:db/id id :orcpub.user/send-updates? false}])
                    (redirect route-map/unsubscribe-success-route))
                {:status 400 :body "Unknown email"}))))
        (catch Exception _
          {:status 400 :body "Invalid or tampered token"})))))

(defn update-user-preferences
  "PUT handler for /user — update user preferences (send-updates?, dark-button-text?).
   Requires authentication. Only updates fields present in transit-params.
   Re-reads from DB after transact to return authoritative state."
  [{:keys [transit-params db conn identity]}]
  (let [username (:user identity)
        {:keys [:db/id]} (find-user-by-username db username)]
    (if id
      (let [pn (when (contains? transit-params :preferred-name)
                 (some-> (:preferred-name transit-params) str s/trim (s/replace #"\s+" " ")))]
        (cond
          (and pn (> (count pn) max-preferred-name-length))
          {:status 400 :body {:error (str "Names can be up to " max-preferred-name-length " characters")}}

          ;; refuse rather than store it readable: the point was that it isn't
          (and (seq pn) (not (crypto/enabled?)))
          {:status 400 :body {:error "Preferred names aren't available on this site yet"}}

          :else
          (do (when (contains? transit-params :send-updates?)
                @(d/transact conn [{:db/id id
                                    :orcpub.user/send-updates? (boolean (:send-updates? transit-params))}]))
              (when (contains? transit-params :dark-button-text?)
                @(d/transact conn [{:db/id id
                                    :orcpub.user/dark-button-text? (boolean (:dark-button-text? transit-params))}]))
              (when (contains? transit-params :preferred-name)
                (let [old (:orcpub.user/preferred-name (d/entity (d/db conn) id))]
                  (cond
                    (seq pn) @(d/transact conn [{:db/id id :orcpub.user/preferred-name
                                                 (crypto/encrypt crypto/preferred-name-purpose pn)}])
                    old @(d/transact conn [[:db/retract id :orcpub.user/preferred-name old]]))))
              ;; Re-read from DB after transact for authoritative response
              (let [updated-user (d/entity (d/db conn) id)]
                {:status 200
                 :body (cond-> {:send-updates? (boolean (:orcpub.user/send-updates? updated-user))
                                :preferred-name (crypto/decrypt crypto/preferred-name-purpose
                                                                (:orcpub.user/preferred-name updated-user))}
                         (some? (:orcpub.user/dark-button-text? updated-user))
                         (assoc :dark-button-text? (:orcpub.user/dark-button-text? updated-user)))}))))
      {:status 400 :body {:error "User not found"}})))

(defn update-artist-credit
  "PUT /user/artist-credit -- an artist edits how they're credited. The artist and the admin hear
   about every change, batched by orcpub.artist-credit-notices; 429 with a Retry-After once the
   account has saved artist-credit/save-cap changes in the hour."
  [{:keys [transit-params db conn identity]}]
  (let [user (find-user-by-username db (:user identity))
        id (:db/id user)]
    (if-not id
      {:status 400 :body {:error "User not found"}}
      (let [{:keys [errors unchanged wait saved before artist-id]}
            (artist-credit/save! conn id (select-keys transit-params
                                                      (into [:name :link :links] (keys artist-credit/page-switches))))]
        (cond
          errors {:status 400 :body {:errors errors}}
          wait {:status 429 :headers {"Retry-After" (str wait)}
                :body {:error (str "That's a lot of changes for one hour. Try again in "
                                   (max 1 (quot (+ wait 59) 60)) " minutes.")}}
          unchanged {:status 200 :body (artist-credit-body (d/db conn) id)}
          :else
          (do (credit-notices/saved! conn {:artist-id artist-id :user user :before before :after saved})
              {:status 200 :body (artist-credit-body (d/db conn) id)}))))))

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
                                   (base-url request)
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
        {:keys [:db/id]} (user-for-email db email)]
    ;; The answer is identical whether or not that address has an account:
    ;; confirmed-valid addresses are precisely what a stuffing run needs.
    ;; The per-address limit is checked AFTER the lookup but never changes the
    ;; response -- a limit that visibly altered it would itself be a membership oracle, which the uniform 200 closes.
    (when (and id (security/reset-email-allowed? email remote-addr))
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
    {:status 200}))

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
                           (base-url request)
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

(def font-sizes
  (merge
   (zipmap (map :key skill5e/skills) (repeat 8))
   (zipmap (map (fn [k] (keyword (str (name k) "-save"))) char5e/ability-keys) (repeat 8))
   {:personality-traits 8
    :ideals 8
    :bonds 8
    :flaws 8
    :features-and-traits 8
    :features-and-traits-2 8
    :attacks-and-spellcasting 8
    :backstory 8
    :other-profs 8
    :equipment 8
    :weapon-name-1 8
    :weapon-name-2 8
    :weapon-name-3 8}))

(defn- bound-collection
  "At most `n` entries, whether `v` is a list or a map of lists.

   A map is capped across ALL its values, not per key: spells-known is keyed by
   class, so a per-key cap would let thirteen classes carry the limit each."
  [n v]
  (cond
    (map? v) (first (reduce (fn [[m left] [k xs]]
                              (if (sequential? xs)
                                [(assoc m k (take left xs)) (max 0 (- left (count xs)))]
                                [(assoc m k xs) left]))
                            [{} n] v))
    (sequential? v) (take n v)
    :else v))

(defn- collection-size
  "Entries in `v`, counted the way bound-collection caps them; nil if it caps nothing."
  [v]
  (cond
    (map? v) (reduce + 0 (map #(if (sequential? %) (count %) 0) (vals v)))
    (sequential? v) (count v)
    :else nil))

(defn bound-request
  "Returns `fields` with everything caller-supplied capped, before any part of the export
   sees it. Acts on the REQUEST, so a feature added later that reads the body is bounded
   without being wired up: a `spellcasting-class-N` field past the section ceiling is
   dropped, and every collection is truncated to the card ceiling (nine cards to a page, so
   an uncapped list is an uncapped page count). Generators keep their own clamps as well."
  [fields]
  (let [max-sections (config/get-pdf-max-caster-sections)
        max-cards (config/get-pdf-max-cards)
        past-ceiling? (fn [k]
                        (when-let [[_ n] (re-matches #"spellcasting-class-(\d+)" (name k))]
                          (> (or (parse-long n) 0) max-sections)))
        dropped (count (filter past-ceiling? (keys fields)))]
    (when (pos? dropped)
      (println (format "pdf: dropped %d spellcasting-class field(s) past section %d"
                       dropped max-sections)))
    (reduce-kv (fn [m k v]
                 (if (past-ceiling? k)
                   m
                   (let [bounded (bound-collection max-cards v)
                         before (collection-size v)
                         after (collection-size bounded)]
                     ;; Reported HERE, not in bound-cards: this runs first, so by the
                     ;; time bound-cards counts, the list is already the cap and its
                     ;; own check can never be true. The entries went in silence.
                     (when (and before after (> before after))
                       (errors/note! "Export size"
                                     (format "%s: %d requested, %d printed -- the export is capped at %d"
                                             (name k) before after max-cards)))
                     (assoc m k bounded))))
               {} fields)))

(defn- bound-cards
  "At most ORCPUB_PDF_MAX_CARDS of `cards`, reporting when it truncates.

   Nine cards to a page and the caller decides how many, so an export is otherwise
   only as bounded as the request body: 2 MB of spell keys is some 13,000 pages
   and a quarter of an hour with an export slot held the whole time. The queue
   timeout bounds how long a request WAITS for a slot, not how long it keeps one."
  [kind cards]
  (let [limit (config/get-pdf-max-cards)
        n (count cards)]
    (when (> n limit)
      (errors/note! (str (common/ascii-capitalize kind) " cards")
                    (format "%d requested, %d printed -- the export is capped at %d"
                            n limit limit))
      (println (format "pdf: %d %s cards requested, printing the first %d"
                       n kind limit)))
    (take limit cards)))

(defn pdf-safe-text
  "PDFBox's standard-14 fonts are WinAnsi: a name with a character outside it
   throws on showText and would take the whole sheet down. Drop what cannot be
   encoded rather than lose the export, and keep the line short enough to fit
   under the portrait box."
  [s]
  (some-> s
          (s/replace #"[^\u0020-\u007e\u00a0-\u00ff]" "")
          ;; dropping a character must not leave a gap where it was
          (s/replace #"\s+" " ")
          s/trim
          (as-> t (when (seq t) (if (> (count t) 78) (str (subs t 0 75) "...") t)))))

(defn stamp-document-info!
  "Set the exported sheet's own metadata.

   The templates are third-party InDesign files, so a fresh export inherits
   their info dictionary and claims to have been made by 'Adobe InDesign CS6
   (Macintosh)' with an empty Author -- which is simply untrue, and is what a
   digital-asset tool or a search index reads. Overwriting it costs nothing
   and puts the art credit somewhere besides the picture.

   This is provenance, not protection: metadata strips in seconds."
  [doc {:keys [character-name credit art-mark]}]
  (try
    (let [info (.getDocumentInformation doc)]
      ;; drawn from the licensed art: the sheet says so where the repository's guard looks
      (when art-mark (.setCustomMetadataValue info art-mark/mark-keyword art-mark))
      (.setTitle info (or (some-> character-name s/trim not-empty)
                          branding/default-page-title))
      (.setCreator info branding/app-name)
      (.setProducer info branding/app-name)
      (when-let [c (pdf-safe-text credit)]
        (.setSubject info c)
        (.setKeywords info c)))
    (catch Exception e
      (println "pdf: could not stamp document info -" (.getMessage e)))))

(defn add-spell-cards!
  "Appends spell card pages, nine to a sheet, each with its back.

   `fonts` and `img` belong to the caller because they are per-DOCUMENT: each
   load-fonts embeds its own subset of every face used, so building a set here and
   another in add-magic-item-cards! puts two complete copies of Vollkorn in a
   character sheet that prints both kinds of card."
  [doc fonts img spells-known spell-save-dcs spell-attack-mods custom-spells print-spell-card-dc-mod? logo-img bw? bw-faded? & [spell-order]]
  (try
    (let [custom-spells-map (common/map-by-key custom-spells)
          spells-map (merge spells/spell-map custom-spells-map)
          ;; Bound the CARDS, not the classes: spells-known is keyed by class, so
          ;; capping it would keep the first few classes whole and drop the rest.
          flat-spells (bound-cards "spell" (-> spells-known vals flatten))
          ;; By class, then spellbook/spell-sort-key: level and name, or name alone.
          sorted-spells (sort-by
                         (fn [{:keys [class key]}]
                           (into [(if (keyword? class)
                                    (common/kw-to-name class)
                                    (str class))]
                                 (spellbook/spell-sort-key spell-order (spells-map key))))
                         flat-spells)
          ;; Capacity, not 9: print-spells draws only as many as the page has cells,
          ;; so a literal 9 drops the rest on any paper that holds fewer.
          parts (vec (partition-all (draw/cards-per-page 2.5 3.5) sorted-spells))]
      (doseq [i (range (count parts))
              :let [part (parts i)]]
        (let [page (PDPage. (draw/page-rect))]
          (.addPage doc page)
          (with-open [cs (PDPageContentStream. doc page)]
            (let [spells (sequence
                          (comp
                           (filter (fn [spell] (spells-map (:key spell))))
                           (map
                            ;; The DC and attack maps come from the request and may
                            ;; be absent. Calling nil threw an NPE that the catch
                            ;; below swallowed, so every card vanished and the
                            ;; export looked like it had simply ignored the option.
                            (fn [{:keys [key class]}]
                              {:spell (spells-map key)
                               :class-nm class
                               :dc (get spell-save-dcs class)
                               :attack-bonus (get spell-attack-mods class)})))
                          part)
                  remaining-desc-lines (vec
                                        (cards/print-spells
                                         cs
                                         doc
                                         fonts
                                         img
                                         2.5
                                         3.5
                                         spells
                                         i
                                         print-spell-card-dc-mod?
                                         bw?
                                         bw-faded?))
                  back-page (PDPage. (draw/page-rect))]
              (with-open [back-page-cs (PDPageContentStream. doc back-page)]
                (.addPage doc back-page)
                (cards/print-backs back-page-cs fonts img 2.5 3.5 remaining-desc-lines i
                                   logo-img)))))))
    (catch Exception e
      (errors/note! "Spell cards"
                    (str "could not be drawn and were left out (" (.getMessage e) ")")))))

(defn add-magic-item-cards!
  "Appends card pages for `magic-items`, nine to a sheet, each with its back; the same layout
   as the spell cards. A card page that throws is logged, not propagated, so it cannot cost
   the character their sheet. `fonts` and `img` are the caller's, for the reason given on
   add-spell-cards!."
  [doc fonts img magic-items logo-img bw? bw-faded?]
  (try
    (let [parts (vec (partition-all (draw/cards-per-page 2.5 3.5)
                                    (bound-cards "magic item" magic-items)))]
      (doseq [i (range (count parts))
              :let [part (parts i)]]
        (let [page (PDPage. (draw/page-rect))]
          (.addPage doc page)
          (with-open [cs (PDPageContentStream. doc page)]
            (let [remaining-desc-lines (vec (cards/print-items cs doc fonts img 2.5 3.5
                                                               part i bw? bw-faded?))
                  back-page (PDPage. (draw/page-rect))]
              (with-open [back-page-cs (PDPageContentStream. doc back-page)]
                (.addPage doc back-page)
                (cards/print-backs back-page-cs fonts img 2.5 3.5 remaining-desc-lines i
                                   logo-img)))))))
    (catch Exception e
      (errors/note! "Magic item cards"
                    (str "could not be drawn and were left out (" (.getMessage e) ")")))))

(defn- one-of
  "`v` as a keyword when it names one of `allowed` (keyword or string), else `default`."
  [allowed v default]
  (let [k (cond (keyword? v) v (string? v) (keyword v))]
    (if (contains? allowed k) k default)))

(defn- small-int
  "`v` when it is an integer from `lo` to `hi`, else nil."
  [v lo hi]
  (when (and (integer? v) (<= lo v hi)) v))

(defn- short-str
  "`v` cut to at most `n` characters when it is a string, else nil."
  [v n]
  (when (string? v) (subs v 0 (min n (count v)))))

(defn- slot-table
  "A slot table {level count} from request map `m`, keeping levels 1-9 with counts 0-20; level
   keys may arrive as keywords."
  [m]
  (into {}
        (for [[l n] (when (map? m) m)
              :let [l (small-int (if (keyword? l) (parse-long (name l)) l) 1 9)
                    n (small-int n 0 20)]
              :when (and l n)]
          [l n])))

(defn spellbook-options
  "The :spellbook request map made safe to lay out from, or nil. Caller-supplied, so every
   value is checked: unknown choices fall back to the defaults, numbers are bounded, strings
   cut short, and an emblem must be one of the vendored icons, since its name becomes part
   of a resource path."
  [sb character-name]
  (when (map? sb)
    (let [classes (->> (:classes sb)
                       (filter map?)
                       (take (config/get-pdf-max-caster-sections))
                       (keep (fn [{:keys [class level ability dc attack pact? icon]}]
                               (when-let [class (short-str class 60)]
                                 (let [class-kw (common/name-to-kw class)]
                                   {:class class
                                    :class-kw class-kw
                                    :level (small-int level 1 30)
                                    :ability (short-str ability 20)
                                    :dc (small-int dc 0 99)
                                    :attack (short-str attack 6)
                                    :pact? (true? pact?)
                                    :icon (emblems/icon-for class-kw icon)}))))
                       vec)]
      (when (seq classes)
        {:layout (one-of #{:book :ledger :prep} (:layout sb) :book)
         :order (one-of #{:level :alpha} (:order sb) :level)
         :class-break (one-of #{:runon :page} (:class-break sb) :runon)
         :tabs (one-of #{:head :inset :off} (:tabs sb) :head)
         :classes classes
         :slots (slot-table (:slots sb))
         :pact-slots (slot-table (:pact-slots sb))
         :character-name (short-str character-name 80)}))))

(defn add-spellbook!
  "Appends the spellbook pages. Like the cards, a failure is logged rather than thrown, so
   it cannot cost the character their sheet."
  [doc fonts img opts spells-known custom-spells]
  (try
    (let [spells-map (merge spells/spell-map (common/map-by-key custom-spells))
          ;; Bounded by the card ceiling; the pages read only the flat list, under one key.
          spells-known {:all (bound-cards "spellbook" (-> spells-known vals flatten))}]
      (spellbook/add-spellbook! doc fonts img opts spells-known spells-map))
    (catch Exception e
      (errors/note! "Spellbook"
                    (str "could not be drawn and was left out (" (.getMessage e) ")")))))

(def valid-sheet-styles
  "Style ids with a template on disk: resources/fillable-char-sheetstyle-N-*.pdf"
  #{1 2 3 4})

(def valid-page-sizes
  "Paper the export draws on: Letter, the default, and A4. Anything else falls back to
   Letter. The character-sheet TEMPLATES are 612x792 whatever this says -- their
   artwork is fixed, and fit-sheet-to-page! re-papers them onto A4."
  #{:letter :a4})

(def default-sheet-style 1)

(def ^:private pdf-option-keys
  "Keys the client sends alongside the field values to steer the export. They name
   no field, so they are removed before write-fields!, which reports whatever it
   cannot place and would otherwise flag every one of these on every request."
  #{:image-url :image-url-failed :image-data
    :faction-image-url :faction-image-url-failed :faction-image-data
    :spells-known :custom-spells :spell-save-dcs :spell-attack-mods
    :print-character-sheet? :print-spell-cards? :print-character-sheet-style?
    :print-spell-card-dc-mod? :print-card-back-logo? :card-back-logo-faded?
    :print-bw? :bw-faded? :print-prepared-spells? :print-large-abilities?
    :print-spell-annotations? :spell-relabels :spell-headings :spell-layout
    :magic-items-known :print-magic-item-cards?
    :spellbook :spell-order
    :page-size
    ;; The composed portrait's bytes and its credit line. They are drawn, not written
    ;; into a field, so leaving them here made write-fields! report two unplaceable
    ;; values -- a notice page on every composed-portrait export.
    :portrait-png :portrait-credit
    :flatten?})

(def ^:private image-url-shape
  "Cheap shape check before any lookup: refuses file:// and ftp:// without
   touching DNS. pdf/safe-image-bytes does the real address validation."
  #"^https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")

(defn- well-formed-image-url? [url]
  (boolean (and (string? url) (re-matches image-url-shape url))))

(def ^:private probe-ttl-ms
  "How long a probe's answer stands. Long enough that the export following a
   probe reuses it, short enough that a host having a bad minute is not written
   off for the afternoon."
  (* 10 60 1000))

(def ^:private probe-max-entries
  "Entries kept. These hold image bytes and the endpoint that fills them needs no
   login, so the cache is bounded in count as well as in age: 64 x 128 KB."
  64)

(def ^:private probed-images
  "url -> {:image {:data :jpg?} or nil, :reason ..}.

   A nil :image is an answer too, and worth keeping: a host that would not serve
   this server a moment ago will not serve the export either, and remembering it
   is what stops the export repeating the fetch."
  (bounded-cache/cache {:max-entries probe-max-entries :max-age-ms probe-ttl-ms}))

(defn- probed-outcome
  "Fetches `url` once and remembers the outcome -- the picture, or why not -- so a
   probe and the export that follows it cost the host one request rather than two,
   even when they ask at the same moment."
  [url]
  (bounded-cache/fetch! probed-images url #(pdf/fetch-image-outcome url)))

(def ^:private probe-max-concurrency
  "Outbound fetches the probe endpoint may have running at once. This endpoint
   needs no login and answers one URL per request, so without a bound a caller
   can hand it many distinct not-yet-cached addresses and park a Jetty worker
   on someone else's slow host per request. Small on purpose: this is one
   request's worth of curiosity about one picture, not sheet generation."
  8)

(def ^:private probe-pool
  "Never waits: a probe that finds every fetch slot taken is answered rate-limited at once."
  (work-pool/pool {:size (constantly probe-max-concurrency) :timeout-ms (constantly 0) :seed-ms 500}))

(defn image-probe
  "Whether this server can fetch the picture at the posted `:url`, asked by the builder before it exports one the browser couldn't read.
   Answers 200 with a reason: ok, blocked-address, the fetch's failure reason, unknown, or rate-limited; fetched bytes are kept for the export that follows.
   A cache hit always answers; a miss is gated by a per-host hourly limit and the concurrency bound, since misses are what let an unauthenticated caller park Jetty workers on hosts of their choosing -- both refusals surface as rate-limited.

   GOTCHA: no login is required, so this must never return the picture itself; that would make it an open proxy."
  [{:keys [transit-params remote-addr]}]
  (let [url (:url transit-params)
        cached (some-> (bounded-cache/lookup probed-images url) first)
        reason (cond
                 (not (well-formed-image-url? url))
                 :blocked-address

                 cached
                 (let [{:keys [image reason]} cached]
                   (if image :ok (or reason :unknown)))

                 (not (security/image-probe-allowed? remote-addr))
                 :rate-limited

                 :else
                 (let [{:keys [busy result]} (work-pool/run probe-pool #(probed-outcome url))]
                   (cond busy :rate-limited
                         (:image result) :ok
                         :else (or (:reason result) :unknown))))]
    ;; The HOST only, never the URL: an image address can carry a signed query
    ;; string. This is how the genuinely unreachable set gets measured rather than
    ;; guessed at.
    (when (and (not= :ok reason) (well-formed-image-url? url))
      (println "pdf: no route to a picture at" (some-> url java.net.URI. .getHost)
               "-" (name reason)))
    {:status 200 :body (name reason)}))

(def ^:private export-pool
  "Permits for sheet generation, one per concurrent export, sized by ORCPUB_PDF_CONCURRENCY and
   waited on for ORCPUB_PDF_QUEUE_TIMEOUT_MS (orcpub.config). Held for the PDF work only, not
   the whole request, so parsing and the response write stay outside it. Seeded at a plausible
   250 ms per export; the mean converges on the real figure within a few."
  (work-pool/pool {:size #(config/get-pdf-concurrency)
                   :timeout-ms #(config/get-pdf-queue-timeout-ms)
                   :seed-ms 250}))

(defn export-queue-depth
  "How many export requests are waiting for a slot right now."
  []
  (work-pool/queue-depth export-pool))

(def ^:private busy-page-js
  "(function(){
     var el = document.getElementById('countdown');
     var form = document.getElementById('retry-form');
     if (!el || !form) { return; }
     var base = parseInt(el.getAttribute('data-seconds'), 10);
     if (!base || base < 1) { base = 3; }
     /* Jitter so a crowd turned away together does not come back together. */
     var left = Math.max(1, Math.round(base * (0.75 + Math.random() * 0.5)));
     (function tick(){
       if (left <= 0) { el.textContent = 'Starting your sheet now.'; form.submit(); return; }
       el.textContent = left === 1 ? 'Trying again in 1 second.'
                                   : 'Trying again in ' + left + ' seconds.';
       left -= 1;
       setTimeout(tick, 1000);
     })();
   })();")

(defn- busy-page
  "The page a turned-away export lands on. The export is a form POST into a new tab, so this
   response IS what the person sees: it retries by resubmitting the original body from a
   hidden field, and past `max-retries` (`attempt` counts retries spent) waits to be clicked.
   GOTCHA: that body is caller-supplied and reflected into the field, so render only through
   hiccup2, which escapes it. Builder markup and scripts are absent in this tab, so it uses
   plain card classes; its own rules live in orcpub.styles.core."
  [{:keys [body attempt max-retries retry-seconds nonce action]}]
  (let [auto? (< attempt max-retries)]
    (str
     "<!DOCTYPE html>"
     (h/html
      [:html {:lang "en"}
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:title (str "Busy right now - " branding/app-name)]
        [:link {:rel "stylesheet" :type "text/css" :href "/css/style.css"}]
        [:link {:rel "stylesheet" :type "text/css" :href "/css/compiled/styles.css"}]]
       [:body.sans.busy-body
        [:div.app-header-bar.container {:style "background-color:#2c3445"}
         [:div.content
          [:div.flex.justify-cont-s-b.align-items-c.w-100-p.p-l-20.p-r-20
           [:a {:href "/"} [:img.h-72.pointer {:src branding/logo-path :alt branding/app-name}]]]]]
        [:div.busy-wrap
         [:div.busy-card
          [:h1 "Lots of sheets are being made right now"]
          (if auto?
            [:p "Your character sheet is queued. This page will keep trying on its own."]
            [:p "We tried " (str max-retries) " times and could not get through. "
             "The rush should pass shortly."])
          (when auto?
            [:p#countdown.busy-countdown {:data-seconds (str retry-seconds)}
             "Trying again in " (str retry-seconds) " seconds."])
          [:form#retry-form {:method "POST" :action action}
           [:input {:type "hidden" :name "body" :value body}]
           [:input {:type "hidden" :name "retry" :value (str (inc attempt))}]
           [:button.form-button {:type "submit"} (if auto? "Try now" "Try again")]]]]
        (when auto? [:script {:nonce nonce} (h/raw busy-page-js)])]]))))

(defn- attempt-count
  "Retries already spent, from the hidden field the busy page posts back. Anything
   that is not a non-negative number counts as a first try, so a hand-edited value
   cannot buy extra attempts."
  [req]
  (let [raw (get-in req [:form-params :retry])
        n (try (Integer/parseInt (str raw)) (catch Exception _ 0))]
    (if (and (nat-int? n) (<= n 1000)) n 0)))

(defn- with-export-slot
  "Runs `f` holding one export slot, or answers 503 with a Retry-After and the busy page if
   none frees up within the configured wait. Bounding the work rather than the request keeps
   memory predictable; sizing is in docs/PDF-EXPORT-CAPACITY.md."
  [req f]
  (let [{:keys [result busy waiting]} (work-pool/run export-pool f)]
    (if-not busy
      result
      (let [attempt (attempt-count req)]
        (println (format "pdf: no export slot within %d ms, %d waiting, attempt %d"
                         (config/get-pdf-queue-timeout-ms) waiting attempt))
        {:status 503
         :headers {"Retry-After" (str busy)
                   "Content-Type" "text/html; charset=utf-8"}
         :body (busy-page {:body (get-in req [:form-params :body])
                           :attempt attempt
                           :max-retries (config/get-pdf-max-retries)
                           :retry-seconds busy
                           :nonce (:csp-nonce req)
                           :action (or (:uri req) "/character.pdf")})}))))

(defn- spell-annotation
  "The marks for the spell printed under `nm`, or nil.

   By NAME, because that is all the row carries: the server is handed a flat map
   of field names to values. A homebrew spell, or one whose name does not match
   the data, gets no marks rather than a wrong one."
  [nm]
  (some-> (get spells/spell-map (common/name-to-kw nm))
          spell-annotations/annotation))

(defn- generate-character-pdf*
  "The export itself. `fields` is already parsed and bounded; the caller binds the page
   and the notice collector around it."
  [req fields]
  (let [{:keys [image-url image-url-failed image-data faction-image-url faction-image-url-failed faction-image-data spells-known custom-spells spell-save-dcs spell-attack-mods print-spell-cards? magic-items-known print-magic-item-cards? print-character-sheet-style? print-spell-card-dc-mod? print-card-back-logo? card-back-logo-faded? print-bw? bw-faded? print-spell-annotations? spell-relabels spell-headings character-name class-level player-name flatten? portrait-png portrait-credit spellbook spell-order page-size]} fields

        ;; Printer-friendly mode: monochrome spell-card icons + a forced solid-black
        ;; card-back logo (no color anywhere on the cards). bw-faded? picks the
        ;; icon style: default solid black (white-halo labels) vs faded grayscale.
        bw? (true? print-bw?)
        bw-faded? (true? bw-faded?)

        ;; Resolve the card-back logo to a concrete resource once. nil = off.
        ;; Default is the solid-black mark; the faded brand-orange watermark is an
        ;; opt-in for color printing, and B&W mode overrides it back to solid black.
        card-back-logo-img (when print-card-back-logo?
                             (if (and card-back-logo-faded? (not bw?))
                               "public/image/dmv-mark-faded-orange.png"
                               "public/image/dmv-mark-black.png"))

        ;; The id is interpolated into a resource name, so an unrecognised value
        ;; resolves to a missing resource and throws. Restrict it to ids with a
        ;; template on disk; see valid-sheet-styles.
        print-character-sheet-style? (if (contains? valid-sheet-styles
                                                    print-character-sheet-style?)
                                       print-character-sheet-style?
                                       default-sheet-style)
        ;; One master per style, grown to the character's shape; pdf/sheet-masters has the
        ;; reasoning. Clamped: the count comes from the field NAMES the caller sent, so
        ;; "spellcasting-class-9999" would otherwise ask for 9,998 cloned pages.
        requested-casters (->> (keys fields)
                               (keep #(second (re-matches #"spellcasting-class-(\d+)" (name %))))
                               (keep #(try (Integer/parseInt %) (catch Exception _ nil)))
                               (reduce max 0))
        casters (min requested-casters (config/get-pdf-max-caster-sections))
        {:keys [file marks without-casters site-line prints-site-line?]}
        (get pdf/sheet-masters print-character-sheet-style?)
        ;; A character who casts nothing opens the variant that has no spell page
        ;; rather than one with its spell page taken out: removing a page leaves
        ;; the resources it referenced behind, and for style 4 it would take the
        ;; licence line with it.
        no-casters? (and (zero? casters) (some? without-casters))
        input (.openStream (io/resource (if no-casters? without-casters file)))
        output (ByteArrayOutputStream.)
        filename (cond
                   (and (s/blank? player-name) (s/blank? character-name)) "character.pdf"
                   (s/blank? player-name) (str character-name " - " class-level ".pdf")
                   :else (str player-name " - " character-name " - " class-level ".pdf"))]
        
    ;; PDFBox 3.x: Loader/loadPDF accepts byte[], File, or RandomAccessRead —
    ;; NOT InputStream. Read the resource stream into a byte array first.
    (with-open [doc (Loader/loadPDF (.readAllBytes input))]
      ;; The document's one Nimbus Sans: every face loads once, when first drawn.
      (let [sans (draw/load-sans doc)
            ;; Fillable in every browser by default: write-fields! bakes real appearance streams, so
            ;; values render everywhere and the form stays editable. `:flatten? true` gives a static PDF.
            fields (apply dissoc fields pdf-option-keys)
            ;; No prune: the masters are pruned by dev/prepare_templates.clj and growing only adds
            ;; pages. add-missing-spell-pages! still prunes where it generates pages.
            ;; Both run before write-fields!, so the fields they create or trim exist by then.
            _ (pdf/grow-spell-sections! doc casters (if no-casters? :all marks))
            _ (pdf/add-missing-spell-pages! doc fields (config/get-pdf-max-caster-sections))
            ;; Once the template pages exist and before anything is drawn on them.
            printed (pdf/site-line-pages doc (boolean prints-site-line?))]
        ;; Merge before spilling, so a style's shared box is measured as the one
        ;; value it prints rather than as its parts.
        (let [fields (pdf/merge-style-fields print-character-sheet-style? fields)]
          ;; Narrow the rows before the values are written: the rows auto-size, so this is what
          ;; makes a long name shrink to clear the annotation columns. The builder decides the
          ;; packing and sends this instruction list; it arrives from the client, so it is
          ;; bounds-checked against the sections this document actually grew.
          (when (seq spell-relabels)
            (let [[applied refused]
                  (pdf/apply-relabel-instructions! doc sans spell-relabels casters
                                                   print-character-sheet-style?)]
              (when (pos? refused)
                (errors/note! "Spell slot renumbering"
                              (format "%d of %d instructions were not valid for this sheet style"
                                      refused (+ applied refused)))
                (println (format "pdf: refused %d of %d relabel instruction(s)"
                                 refused (+ applied refused))))))
          (when print-spell-annotations?
            (pdf/reserve-annotation-columns! doc))
          ;; The style's declared drops are not degradation -- see unsupported-fields.
          (pdf/write-fields! doc (pdf/spill-overflow! doc fields) (true? flatten?) font-sizes
                             (get pdf/unsupported-fields print-character-sheet-style?))
          (when print-spell-annotations?
            (pdf/annotate-spell-rows! doc sans spell-annotation))
          ;; After the values, so a heading is drawn over a finished bar. Names
          ;; each packed column with the class holding it; caller-supplied, so the
          ;; box and section are checked the way the relabels are.
          (doseq [{:keys [box section class] :as heading} spell-headings
                  :when (and (integer? box) (<= 0 box 9)
                             (integer? section) (<= 1 section casters)
                             (string? class) (<= 1 (count class) 60))]
            (pdf/draw-column-heading! doc sans print-character-sheet-style?
                                      box section class heading)))
        ;; After the overflow pages exist, so they are stamped too, and before the
        ;; card pages are appended -- those carry the line on their backs already.
        (pdf/stamp-site-line! doc sans site-line printed)
        ;; Before the card and spellbook pages, which are already drawn at the right size:
        ;; this re-papers the TEMPLATE pages only, and is a no-op on Letter.
        (pdf/fit-sheet-to-page! doc)
        ;; One set of fonts and one image embedder for the whole document. Both are
        ;; per-document: a second load-fonts embeds a second subset of every face,
        ;; and a second loader re-embeds the 998x998 card-back mark. Built only when
        ;; a card page is actually coming, since a plain sheet needs neither.
        (let [spell-cards? (and print-spell-cards? (seq spells-known))
              item-cards? (and print-magic-item-cards? (seq magic-items-known))
              book (when (seq spells-known) (spellbook-options spellbook character-name))
              spell-order (one-of #{:level :alpha} spell-order :level)
              fonts (when (or spell-cards? item-cards? book) (draw/load-fonts doc sans))
              img (when (or spell-cards? item-cards? book) (draw/make-image-loader doc))]
          ;; Order: sheet, spellbook, then cards.
          (when book
            (add-spellbook! doc fonts img book spells-known custom-spells))
          (when spell-cards?
            (add-spell-cards! doc fonts img spells-known spell-save-dcs spell-attack-mods
                              custom-spells print-spell-card-dc-mod? card-back-logo-img
                              bw? bw-faded? spell-order))
          (when item-cards?
            (add-magic-item-cards! doc fonts img magic-items-known card-back-logo-img
                                   bw? bw-faded?)))

        ;; Both images are resolved BEFORE either is drawn, fetched concurrently: a fetch holds an
        ;; export slot, so in sequence two slow images would cost two worst cases.
        ;; No pdf/safe-image-url? here: pdf/fetch-image validates through safe-image-bytes, pinned to
        ;; the addresses it resolved. The regex stays; it refuses file:// and ftp:// without a lookup.
        (let [wanted (fn [url failed?]
                       (and url (not failed?) (well-formed-image-url? url) url))
              ;; Bytes the browser read beat the URL and skip the fetch entirely.
              ;; Every arm derefs to {:image} or {:reason}, so nothing below has to
              ;; know which it got; nil when no picture was asked for.
              image (fn [supplied url failed?]
                      (when-not (and (s/blank? (str supplied)) (s/blank? (str url)))
                        (if-let [bytes (pdf/decode-image-bytes supplied)]
                          (delay {:image bytes})
                          ;; probed-outcome, not fetch-image: the builder usually
                          ;; asked about this URL a moment ago, and that answer is
                          ;; cached.
                          (if-let [u (wanted url failed?)]
                            (future (probed-outcome u))
                            (delay {:reason (cond failed? :failed-in-builder
                                                  (s/blank? (str url)) :unreadable
                                                  :else :not-an-image-address)})))))
              ;; A composed (paper-doll) portrait has no URL that could produce
              ;; it -- the client bakes its CSS-mask layers -- and it arrives
              ;; larger than an uploaded picture may be, so it goes through
              ;; decode-artwork-bytes, which fits it instead of refusing it.
              ;;
              ;; Decoded eagerly rather than in a delay: it is local CPU with no
              ;; network in it, and a delay that derefs to nil would be truthy
              ;; here, so a portrait that failed to decode would suppress the
              ;; pasted image-url that should have taken over.
              composed (pdf/decode-artwork-bytes portrait-png)
              portrait (if composed
                         (delay {:image composed})
                         (image image-data image-url image-url-failed))
              faction (image faction-image-data faction-image-url faction-image-url-failed)]
          (when-let [{:keys [data jpg?]} (some-> portrait deref :image)]
            (case print-character-sheet-style?
              1 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 0.45 1.75 2.35 3.15)
              2 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 0.45 1.75 2.35 3.15)
              3 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 0.45 1.75 2.35 3.15)
              4 (pdf/draw-image-bytes! doc (pdf/get-page doc 0) data jpg? 0.50 0.85 2.35 3.15))
            ;; The credit is baked into the composed PNG itself now (see
            ;; portrait/draw-credit!), so there is nothing to print here: a
            ;; second drawn line landed 0.12in under the first and said the
            ;; same thing.
            )
          (when-let [{:keys [data jpg?]} (some-> faction deref :image)]
            (case print-character-sheet-style?
              1 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 5.88 2.4 1.905 1.52)
              2 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 5.88 2.4 1.905 1.52)
              3 (pdf/draw-image-bytes! doc (pdf/get-page doc 1) data jpg? 5.88 2.0 1.905 1.52)
              4 nil))
          ;; A picture asked for and not printed is named on the notice page. Style 4
          ;; has no faction box, so a faction image there is left out by design.
          (doseq [[what outcome] [["Portrait" portrait]
                                  ["Faction image" (when (not= 4 print-character-sheet-style?) faction)]]
                  :let [{:keys [image reason]} (some-> outcome deref)]
                  :when (and outcome (nil? image))]
            (errors/note! what (str "was left out (" (s/replace (name (or reason :unknown)) "-" " ") ")")))
          ;; Inside this let, so it can see whether the composed portrait was
          ;; actually used. Keyed on the decode, not on the field being present:
          ;; a portrait-png that fails to decode falls back to the pasted
          ;; image-url above, and crediting the illustrator for somebody else's
          ;; photograph is the one thing this feature must not do.
          (stamp-document-info! doc {:character-name character-name
                                     :credit (when composed portrait-credit)
                                     :art-mark (when composed
                                                 (some-> (try (.decode (java.util.Base64/getDecoder) ^String portrait-png)
                                                              (catch Exception _ nil))
                                                         art-mark/read-mark))}))
        ;; Last, so every notice is in: it prepends a page naming what was left out, and
        ;; adds nothing at all when nothing was.
        (pdf/add-notice-page! doc sans)
        (.save doc output)))
    (let [a (.toByteArray output)]
      {:status 200
       :headers {"Content-Disposition" (str "inline; filename=\"" filename "\"")}
       :body (ByteArrayInputStream. a)})))

(defn- generate-character-pdf
  "Parses the request once, then runs the export with the notice collector and the page
   bound for its whole length: a section that degrades can say so, and every drawing
   call agrees on the paper. An unknown :page-size falls back to :letter rather than
   throwing -- a bad option must not cost someone their sheet."
  [req]
  ;; The collector opens BEFORE the parse: bound-request truncates an oversized
  ;; request, and a notice raised outside this binding is logged and then dropped.
  (binding [errors/*notices* (atom [])]
    (let [fields (try
                   (-> req :form-params :body edn/read-string bound-request)
                   (catch Exception e
                     (throw (ex-info "Invalid character data format. Unable to parse PDF request."
                                     {:error :invalid-pdf-data}
                                     e))))]
      (binding [draw/*page* (one-of valid-page-sizes (:page-size fields) :letter)]
        (generate-character-pdf* req fields)))))

(defn character-pdf-2 [req]
  (with-export-slot req #(generate-character-pdf req)))

(defn html-response
  [html & [response]]
  (let [merged (merge
                response
                {:status 200
                 :body html
                 :headers {"Content-Type" "text/html"}})]
    merged))

(def user-by-password-reset-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/password-reset-key ?key]])

(def default-title branding/default-page-title)

(def default-description branding/app-tagline)

(defn default-image-url
  "OG meta image URL. Uses https:// for social sharing compatibility."
  [host]
  (str "https://" host branding/og-image-filename))

(defn index-page-response [{:keys [headers uri csp-nonce] :as request}
                           {:keys [title description image-url]}
                           & [response]]
  (let [host (headers "host")]
    ;; before index-page reads the credits into __BRANDING__
    (when-let [db (:db request)] (artist-credit/refresh! db))
    (merge
     response
     {:status 200
      :headers (merge {"Content-Type" "text/html"} (:headers response))
      :body
      (index-page
       {:url (str "http://" host uri)
        :title (or title default-title)
        :description (or description default-description)
        :image (or image-url (default-image-url host))
        :nonce csp-nonce
        :spinner-kind (spinner/pick (get-in request [:query-params :spinner]))}
       (= "/" uri))})))

(defn default-index-page [request & [response]]
  (index-page-response request {} response))

(defn index [{:keys [headers scheme uri server-name] :as request} & [response]]
  (default-index-page request response))

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

(defn check-field [query value db]
  {:status 200
   :body (-> (d/q query db value)
             seq
             boolean
             str)})

(defn check-username [{:keys [db query-params]}]
  (check-field username-query (:username query-params) db))

(defn check-email [{:keys [db query-params]}]
  (check-field email-query (user-email/email-key (:email query-params)) db))

(defn character-for-id [db id]
  (d/pull db '[*] id))

(defn diff-branch [ids]
  (fn [n]
    (or
     (and (map? n)
          (ids (:db/id n)))
     (sequential? n))))

(defn get-new-id [temp-id result]
  (-> result :tempids (get temp-id)))

(defn create-entity [conn username entity owner-prop]
  (try
    (as-> entity $
      (entity/remove-ids $)
      (assoc $
             :db/id "tempid"
             owner-prop username)
      @(d/transact conn [$])
      (get-new-id "tempid" $)
      (d/pull (d/db conn) '[*] $))
    (catch Exception e
      (println "ERROR: Failed to create entity for user" username ":" (.getMessage e))
      (throw (ex-info "Unable to create entity. Please try again or contact support."
                      {:error :entity-creation-failed
                       :username username}
                      e)))))

(defn email-for-username [db username]
  (d/q '[:find ?email .
         :in $ ?username
         :where
         [?e :orcpub.user/username ?username]
         [?e :orcpub.user/email ?email]]
       db
       username))

(defn update-entity [conn username entity owner-prop]
  (try
    (let [id (:db/id entity)
          current (d/pull (d/db conn) '[*] id)
          owner (get current owner-prop)
          email (email-for-username (d/db conn) username)]
      (if ((set [username email]) owner)
        (let [current-ids (entity/db-ids current)
              new-ids (entity/db-ids entity)
              retract-ids (sets/difference current-ids new-ids)
              retractions (map
                           (fn [retract-id]
                             [:db/retractEntity retract-id])
                           retract-ids)
              remove-ids (sets/difference new-ids current-ids)
              with-ids-removed (entity/remove-specific-ids entity remove-ids)
              new-entity (assoc with-ids-removed owner-prop username)
              result @(d/transact conn (concat retractions [new-entity]))]
          (d/pull (d/db conn) '[*] id))
        (throw (ex-info "Not user entity"
                        {:error :not-user-entity}))))
    (catch clojure.lang.ExceptionInfo e
      (throw e))
    (catch Exception e
      (println "ERROR: Failed to update entity for user" username ":" (.getMessage e))
      (throw (ex-info "Unable to update entity. Please try again or contact support."
                      {:error :entity-update-failed
                       :username username
                       :entity-id (:db/id entity)}
                      e)))))

(defn save-entity [conn username e owner-prop]
  (let [without-empty-fields (entity/remove-empty-fields e)]
    (if (:db/id without-empty-fields)
      (update-entity conn username without-empty-fields owner-prop)
      (create-entity conn username without-empty-fields owner-prop))))

(defn owns-entity? [db username entity-id]
  (let [user (find-user-by-username db username)
        username (:orcpub.user/username user)
        email (:orcpub.user/email user)
        entity (d/pull db '[:orcpub.entity.strict/owner] entity-id)
        owner (:orcpub.entity.strict/owner entity)]
    (or (= email owner)
        (= username owner))))

(defn entity-problem [desc actual expected]
  (str desc ", expected: " expected ", actual: " actual))

(defn entity-type-problems [expected-game expected-version expected-type {:keys [::se/type ::se/game ::se/game-version]}]
  (cond-> nil
    (not= expected-game game) (conj (entity-problem "Entity is from the wrong game" game expected-game))
    (not= expected-version game-version) (conj (entity-problem "Entity is from the wrong game version" game-version expected-version))
    (not= expected-type type) (conj (entity-problem "Entity is wrong type" type expected-type))))

(def dnd-e5-char-type-problems (partial entity-type-problems :dnd :e5 :character))

(defn add-dnd-5e-character-tags [character]
  (assoc character
         ::se/game :dnd
         ::se/game-version :e5
         ::se/type :character))

(defn update-character
  "Stores `character` (strict, with its :db/id) over the stored one, as owned by `username`, and
   returns the stored character pulled after the save. Throws ExceptionInfo :character-gone when
   nothing is stored at its id (deleted), :not-user-character when someone else owns it."
  [db conn character username]
  (let [id (:db/id character)]
    (if (owns-entity? db username id)
      (let [current-character (d/pull db '[*] id)
            problems [] #_(dnd-e5-char-type-problems current-character)
            current-valid? (spec/valid? ::se/entity current-character)]
        (when-not current-valid?
          (prn "INVALID CHARACTER FOUND, REPLACING" #_current-character)
          (prn "INVALID CHARACTER EXPLANATION" #_(spec/explain-data ::se/entity current-character)))
        (if (seq problems)
          (throw (ex-info "Character has problems"
                          {:error :character-problems :problems problems}))
          (if-not current-valid?
            (let [new-character (entity/remove-ids character)
                  tx [[:db/retractEntity (:db/id current-character)]
                      (-> new-character
                          (assoc :db/id "tempid"
                                 :orcpub.entity.strict/owner username)
                          add-dnd-5e-character-tags)]
                  result @(d/transact conn tx)]
              (d/pull (d/db conn) '[*] (-> result :tempids (get "tempid"))))
            (let [new-character (entity/remove-orphan-ids character)
                  current-ids (entity/db-ids current-character)
                  new-ids (entity/db-ids new-character)
                  retract-ids (sets/difference current-ids new-ids)
                  retractions (map
                               (fn [retract-id]
                                 [:db/retractEntity retract-id])
                               retract-ids)
                  tx (conj retractions
                           (-> new-character
                               (assoc :orcpub.entity.strict/owner username)
                               add-dnd-5e-character-tags))]
              @(d/transact conn tx)
              (d/pull (d/db conn) '[*] id)))))
      (throw (if (empty? (d/datoms db :eavt id))
               (ex-info "Character gone" {:error :character-gone})
               (ex-info "Not user character" {:error :not-user-character}))))))

(defn create-new-character
  "Creates a D&D 5e character owned by `username` and returns the pulled entity.
  Throws ExceptionInfo :character-creation-failed on database failure."
  [conn character username]
  (errors/with-db-error-handling :character-creation-failed
    {:username username}
    "Unable to create character. Please try again or contact support."
    (let [result @(d/transact conn
                              [(-> character
                                   (assoc :db/id "tempid"
                                          ::se/owner username)
                                   add-dnd-5e-character-tags)])
          new-id (get-new-id "tempid" result)]
      (d/pull (d/db conn) '[*] new-id))))

(defn clean-up-character [character]
  (cond-> character
    (-> character ::se/values ::char5e/xps string?)
    (update-in [::se/values ::char5e/xps]
               #(try
                  (if-not (s/blank? %)
                    (Long/parseLong %)
                    0)
                  (catch NumberFormatException e 0)))
    ;; a portrait the server cannot read is not stored: the save goes through without it
    (let [p (-> character ::se/values ::char5e/portrait)]
      (and (some? p) (not (s/blank? (str p))) (nil? (char5e/parse-portrait p))))
    (-> (update ::se/values dissoc ::char5e/portrait)
        (as-> c (cond-> c (empty? (::se/values c)) (dissoc ::se/values))))))

(defn- note-saved-portrait!
  "Tell the portrait store a character was saved, so its shared-link picture is drawn if the
   portrait changed (orcpub.portrait-store/saved!). Never fails the save."
  [conn id]
  (try
    (when id
      (portrait-store/saved! id (some-> (d/pull (d/db conn) '[{::se/values [::char5e/portrait]}] id)
                                        ::se/values ::char5e/portrait)))
    (catch Exception e (println "portrait-store: not told about" id "-" (.getMessage e)))))

(defn do-save-character
  "Saves the strict character `transit-params` for the user in `identity`: creates it when it has
   no :db/id, else updates it. Returns a response map: 200 with the stored character, 400 with the
   spec or problem data, 401 when another player owns it, 410 {:error :character-gone} when it
   has been deleted."
  [db conn transit-params identity]
  (let [character (entity/remove-empty-fields transit-params)
        username (:user identity)
        current-id (:db/id character)]
    (try
      (if-let [data (spec/explain-data ::se/entity character)]
        {:status 400 :body data}
        (let [clean-character (clean-up-character character)
              updated-character (if (:db/id clean-character)
                                  (update-character db conn clean-character username)
                                  (create-new-character conn clean-character username))]
          (note-saved-portrait! conn (:db/id updated-character))
          {:status 200 :body updated-character}))
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (case (:error data)
            :character-problems {:status 400 :body (:problems data)}
            :not-user-character {:status 401 :body "You do not own this character"}
            :character-gone {:status 410 :body {:error :character-gone}}
            (throw e))))   ; re-throw unrecognised ExceptionInfo (e.g. :db/error from Datomic)
      (catch Exception e (prn "ERROR" e) (throw e)))))

(defn save-character [{:keys [db transit-params body conn identity] :as request}]
  (do-save-character db conn transit-params identity))

(defn owns-item [db username item-id]
  (let [item (d/pull db '[::mi5e/owner] item-id)]
    (= username (::mi5e/owner item))))

(defn save-item [{:keys [db transit-params body conn identity] :as request}]
  (if-let [data (spec/explain-data ::mi5e/magic-item transit-params)]
    {:status 400 :body data}
    (let [username (:user identity)
          result (save-entity conn username transit-params ::mi5e/owner)]
      {:status 200
       :body result})))

(defn get-item
  "A custom item, for its owner only. Anyone else gets the 404 a missing item gets, so an id
   says nothing about whether it exists."
  [{:keys [db username] {:keys [:id]} :path-params}]
  (let [item (d/pull db '[*] id)]
    (if (and (::mi5e/owner item) (= username (::mi5e/owner item)))
      {:status 200
       :body item}
      {:status 404})))

(defn delete-item
  "Deletes the magic item at path-param :id if the requesting user owns it.
  Returns 200, or 401 if not owned. Throws ExceptionInfo :item-deletion-failed on database
  failure."
  [{:keys [db conn username] {:keys [:id]} :path-params}]
  (let [{:keys [::mi5e/owner]} (d/pull db '[::mi5e/owner] id)]
    (if (= username owner)
      (errors/with-db-error-handling :item-deletion-failed
        {:item-id id}
        "Unable to delete item. Please try again or contact support."
        @(d/transact conn [[:db/retractEntity id]])
        {:status 200})
      {:status 401})))

(defn item-list [{:keys [db identity]}]
  (let [username (:user identity)
        items (d/q '[:find (pull ?e [*])
                     :in $ ?username
                     :where
                     [?e ::mi5e/owner ?username]]
                   db
                   username)]
    {:status 200 :body (map first items)}))

(defn character-list [{:keys [db identity] :as request}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)
        ids (d/q '[:find ?e
                   :in $ [?idents ...]
                   :where
                   [?e ::se/owner ?idents]]
                 db
                 [(:orcpub.user/username user)
                  (:orcpub.user/email user)])
        characters (d/pull-many db '[*] (map first ids))]
    {:status 200 :body characters}))

(defn character-summary-list [{:keys [db body conn identity] :as request}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)
        following-ids (map :db/id (:orcpub.user/following user))
        following-usernames (following-usernames db following-ids)
        results (d/q '[:find (pull ?e [:db/id
                                       ::se/summary
                                       ::se/owner])
                       :in $ [?idents ...]
                       :where
                       [?e ::se/owner ?idents]]
                     db
                     (concat
                      [(:orcpub.user/username user)
                       (:orcpub.user/email user)]
                      following-usernames))
        characters (mapv
                    (fn [[{:keys [:db/id ::se/owner ::se/summary]}]]
                      (assoc
                       summary
                       :db/id id
                       ::se/owner (if (= owner (:orcpub.user/email user))
                                    (:orcpub.user/username user)
                                    owner)))
                    results)]
    {:status 200 :body characters}))

(defn follow-user
  "Adds path-param :user to the authenticated user's following list. Returns 200.
  Throws ExceptionInfo :follow-user-failed on database failure."
  [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    (errors/with-db-error-handling :follow-user-failed
      {:follower username :followed user}
      "Unable to follow user. Please try again or contact support."
      @(d/transact conn [{:db/id user-id
                          :orcpub.user/following other-user-id}])
      {:status 200})))

(defn unfollow-user
  "Removes path-param :user from the authenticated user's following list. Returns 200.
  Throws ExceptionInfo :unfollow-user-failed on database failure."
  [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    (errors/with-db-error-handling :unfollow-user-failed
      {:follower username :unfollowed user}
      "Unable to unfollow user. Please try again or contact support."
      @(d/transact conn [[:db/retract user-id :orcpub.user/following other-user-id]])
      {:status 200})))

(defn delete-character
  "Deletes the character at path-param :id if the authenticated user owns it.
  Returns 200, 400 with the problems found, or 401 if not owned. Throws ExceptionInfo on an
  invalid ID or database failure."
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [parsed-id (errors/with-validation :invalid-character-id
                    {:id id}
                    "Invalid character ID format"
                    (Long/parseLong id))
        username (:user identity)
        character (d/pull db '[*] parsed-id)
        problems [] #_(dnd-e5-char-type-problems character)]
    (if (owns-entity? db username parsed-id)
      (if (empty? problems)
        (errors/with-db-error-handling :character-deletion-failed
          {:character-id parsed-id}
          "Unable to delete character. Please try again or contact support."
          @(d/transact conn (cons [:db/retractEntity parsed-id]
                                  (share/retractions-for-character db parsed-id)))
          {:status 200})
        {:status 400 :body problems})
      {:status 401 :body "You do not own this character"})))

(defn- public-owner
  "The owner as a character read shows it: the username, or nil. For nine days in May 2017 the
   login token carried whatever the user typed (e3cdf5f to cf170fc), so characters saved then name
   their owner by email address; this read needs no login, so the address must not go out."
  [db owner]
  (if-let [user (find-user-by-username-or-email db owner)]
    (:orcpub.user/username user)
    (when-not (s/includes? owner "@") owner)))

(defn- equipped-custom-items
  "The owner's custom items this character has equipped, without database ids or owner. Whoever may
   read the character sees these, because the sheet is drawn from them; the owner's other items stay
   out. Any failure sends none rather than failing the character read."
  [db username character]
  (try
    (sb/used-custom-items (entity/from-strict character)
                          (d/q '[:find [(pull ?e [*]) ...] :in $ ?owner :where [?e ::mi5e/owner ?owner]]
                               db username)
                          #(mi5e/expand-magic-items [%]))
    (catch Exception e
      (println "WARNING: custom items not attached to character" (:db/id character) ":" (.getMessage e))
      nil)))

(defn get-character-for-id [db id]
  (let [{:keys [::se/owner] :as character} (d/pull db '[*] id)
        problems [] #_(dnd-e5-char-type-problems character)]
    (if (or (not owner) (seq problems))
      {:status 400 :body problems}
      (let [shown (public-owner db owner)
            items (when shown (equipped-custom-items db shown character))]
        {:status 200 :body (cond-> (if shown
                                     (assoc character ::se/owner shown)
                                     (dissoc character ::se/owner))
                             (seq items) (assoc ::char5e/custom-items items))}))))

(defn character-summary-for-id
  "The share-card data for a character: the whole pull, {::se/summary … ::se/values …}.
   GOTCHA: character-page destructures both keys, so returning only the summary submap
   blanks every og:title and og:image."
  [db id]
  (d/pull db
          '[::se/summary
            {::se/values [::char5e/description ::char5e/image-url ::char5e/portrait]}]
          id))

(defn get-character
  "Retrieves the character at path-param :id via `get-character-for-id`: 200 with the
  character, or 400 if it has no owner. Throws ExceptionInfo on an invalid ID format."
  [{:keys [db] {:keys [:id]} :path-params}]
  (let [parsed-id (errors/with-validation :invalid-character-id
                    {:id id}
                    "Invalid character ID format"
                    (Long/parseLong id))]
    (get-character-for-id db parsed-id)))

(defn report-character-problem
  "User-initiated report that a character failed to load. Auth required. Emails
   the client-supplied diagnostic (char-id, reader error, raw undecodable data)
   to the configured support address, cc'ing the reporting user. Rate-limited
   and gated on email config inside email/send-character-report; returns its
   {:sent? .. :reason ..} so the client can fall back to the copyable report."
  [{:keys [db transit-params identity]}]
  (let [username   (:user identity)
        user       (find-user-by-username-or-email db username)
        user-email (:orcpub.user/email user)
        {:keys [char-id error raw]} transit-params]
    {:status 200
     :body   (email/send-character-report
              {:char-id char-id :user-email user-email :error error :raw raw})}))

(defn get-user [{:keys [db identity]}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)]
    {:status 200 :body (user-body db user)}))

(defn delete-user
  "Deletes the authenticated user's account and logs it. Returns 200.
  Throws ExceptionInfo :user-deletion-failed on database failure."
  [{:keys [db conn identity] :as request}]
  (let [username (:user identity)
        user (d/q '[:find ?u .
                    :in $ ?username
                    :where [?u :orcpub.user/username ?username]]
                  db
                  username)]
    (errors/with-db-error-handling :user-deletion-failed
      {:username username}
      "Unable to delete user account. Please try again or contact support."
      @(d/transact conn [[:db/retractEntity user]])
      (security/audit! :account-deleted request username)
      {:status 200})))

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
                                (base-url request)
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
                                                   (base-url request)
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

(defn character-summary-description [{:keys [::char5e/race-name ::char5e/subrace-name ::char5e/classes]}]
  (str race-name
       " "
       (when subrace-name (str "(" subrace-name ") "))
       " "
       (when (seq classes)
         (s/join
          " / "
          (map
           (fn [{:keys [::char5e/class-name
                        ::char5e/subclass-name
                        ::char5e/level]}]
             (str class-name " (" level ")"))
           classes)))))

(def index-page-paths
  [[route-map/dnd-e5-char-list-page-route]
   [route-map/dnd-e5-char-parties-page-route]
   [route-map/dnd-e5-monster-list-page-route]
   [route-map/dnd-e5-monster-page-route :key ":key"]
   [route-map/dnd-e5-spell-list-page-route]
   [route-map/dnd-e5-spell-page-route :key ":key"]
   [route-map/dnd-e5-rules-page-route]
   [route-map/dnd-e5-rule-section-page-route :key ":key"]
   [route-map/dnd-e5-condition-list-page-route]
   [route-map/dnd-e5-condition-page-route :key ":key"]
   [route-map/dnd-e5-spell-builder-page-route]
   [route-map/dnd-e5-monster-builder-page-route]
   [route-map/dnd-e5-selection-builder-page-route]
   [route-map/dnd-e5-background-builder-page-route]
   [route-map/dnd-e5-encounter-builder-page-route]
   [route-map/dnd-e5-combat-tracker-page-route]
   [route-map/dnd-e5-race-builder-page-route]
   [route-map/dnd-e5-subrace-builder-page-route]
   [route-map/dnd-e5-subclass-builder-page-route]
   [route-map/dnd-e5-class-builder-page-route]
   [route-map/dnd-e5-language-builder-page-route]
   [route-map/dnd-e5-invocation-builder-page-route]
   [route-map/dnd-e5-boon-builder-page-route]
   [route-map/dnd-e5-feat-builder-page-route]
   [route-map/dnd-e5-item-list-page-route]
   [route-map/dnd-e5-item-page-route :key ":key"]
   [route-map/dnd-e5-item-builder-page-route]
   [route-map/dnd-e5-char-builder-route]
   [route-map/dnd-e5-newb-char-builder-route]
   [route-map/dnd-e5-my-content-route]
   [route-map/send-password-reset-page-route]
   [route-map/my-account-page-route]
   [route-map/register-page-route]
   [route-map/login-page-route]
   [route-map/verify-sent-route]
   [route-map/password-reset-sent-route]
   ;; Was the only one of these missing. The client routes here after a reset,
   ;; so it renders inside a session -- but a refresh, a back button or a
   ;; bookmark asked the server for it and got "Not Found", on the one page
   ;; whose whole job is to confirm the password was changed.
   [route-map/password-reset-success-route]
   [route-map/password-reset-expired-route]
   [route-map/password-reset-used-route]
   [route-map/verify-failed-route]
   [route-map/verify-success-route]
   [route-map/unsubscribe-success-route]
   [route-map/dnd-e5-orcacle-page-route]])

(defn- any-representation-requested?
  "Whether the If-None-Match `header` is the wildcard.
   GOTCHA: `*` is the whole header value, never a list member (RFC 7232 s3.2); splitting a
   quoted tag on commas can invent one and answer 304 to a stale client."
  [header]
  (= "*" (some-> header s/trim)))

(defn- covered-by-if-none-match?
  "Whether an If-None-Match `header` lists `tag`, by weak comparison (W/\"x\" covers \"x\"),
   ignoring a --gzip suffix. Tags are matched out of the header, not split on commas.
   The wildcard is not handled here; see any-representation-requested?."
  [header tag]
  (boolean
   (when (and (not (s/blank? header)) tag)
     (let [bare #(-> % s/trim (s/replace #"(?i)^W/" "") (s/replace #"--gzip$" ""))
           offered (into #{} (map bare) (re-seq #"(?i)W/\"[^\"]*\"|\"[^\"]*\"" header))]
       (contains? offered (bare tag))))))

(defn character-portrait-png
  "Ring handler: PNG of character :id's composed portrait, for og:image. Unauthenticated,
   like the character page, whose meta tags already expose the character. 304 when
   If-None-Match covers portrait-etag; 404 when nothing composed is drawable. A `v` query
   param matching the tag's version makes the response long-cacheable."
  [{:keys [db headers query-params] {:keys [id]} :path-params}]
  (artist-credit/refresh! db)
  (let [stored (some-> (d/pull db '[{::se/values [::char5e/portrait]}] id)
                       ::se/values
                       ::char5e/portrait)
        portrait (some-> stored char5e/parse-portrait)
        tag (portrait-store/tag stored)
        png-headers {"Content-Type" "image/png"
                     ;; The share page names the picture by version (?v=), so that address
                     ;; never changes content and can be kept for good; a bare or stale one
                     ;; is revalidated after five minutes.
                     "Cache-Control" (if (and tag (= (:v query-params) (portrait-store/version tag)))
                                       "public, max-age=31536000, immutable"
                                       "public, max-age=300")
                     ;; This is contributed artwork. The header is the per-response
                     ;; twin of the page's `noai` meta -- a crawler that fetches the
                     ;; PNG directly never parses the HTML that carries the meta.
                     "X-Robots-Tag" "noai, noimageai"
                     "ETag" tag}]
    (cond
      (nil? portrait) {:status 404 :body "no composed portrait"}

      ;; BEFORE rendering, which is the point. A 304 here costs a pull and a
      ;; hash; the alternative is decoding up to ten PNGs, scaling, tinting,
      ;; compositing and encoding, to produce bytes the caller already has.
      (covered-by-if-none-match? (get headers "if-none-match") tag)
      {:status 304 :headers (dissoc png-headers "Content-Type")}

      :else
      ;; The wildcard cannot take the path above. `*` asks whether a
      ;; representation EXISTS, and a stored portrait selecting nothing drawable
      ;; has none -- it 404s. Answering 304 there would tell a client its cached
      ;; copy is current when the resource has no current copy at all. So it
      ;; renders first and lets the outcome decide, which costs the saving on a
      ;; wildcard request and keeps it on every request that names a tag.
      (let [[outcome x] (portrait-store/png id tag stored)
            png (when (= :ok outcome) x)]
        (cond
          (= :busy outcome)
          {:status 503 :headers {"Retry-After" (str x) "Content-Type" "text/plain"} :body "busy"}

          (nil? png) {:status 404 :body "no composed portrait"}

          (any-representation-requested? (get headers "if-none-match"))
          {:status 304 :headers (dissoc png-headers "Content-Type")}

          :else {:status 200 :headers png-headers :body (ByteArrayInputStream. png)})))))

(defn portrait-artists
  "The artists' credits in force, as JSON for the browser: their own edits under the deployment's
   settings (orcpub.artist-credit/refresh!). Public, as the credits are; cached for a minute so an
   artist's edit shows within one. Served as data rather than written into the page, so text an
   artist typed never becomes page source."
  [{:keys [db]}]
  (when db (artist-credit/refresh! db))
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"
             "Cache-Control" "public, max-age=60"}
   :body (cheshire/generate-string (portrait-assets5e/current-overrides))})

(defn character-page [{:keys [db conn identity headers scheme uri] {:keys [id]} :path-params :as request}]
  ;; before the credit line and the image version below are worked out from it
  (when db (artist-credit/refresh! db))
  (let [host (headers "host")
        {:keys [::se/summary ::se/values]} (character-summary-for-id db id)
        {:keys [::char5e/character-name]} summary
        {:keys [::char5e/description
                ::char5e/image-url
                ::char5e/portrait]} values
        ;; A composed portrait wins over a pasted URL, the same precedence the
        ;; sheet, the summary and the PDF use. It is served as a real PNG
        ;; because crawlers will not render CSS masks -- or, mostly, SVG.
        parsed-portrait (char5e/parse-portrait portrait)
        ;; drawable?, not (seq :layers): a selection of assets this deployment lacks is
        ;; non-empty, draws nothing, and would point the card at a 404 instead of
        ;; falling back to image-url.
        composed? (portrait-assets5e/drawable? parsed-portrait)
        ;; Versioned, so a link preview made after the portrait changes fetches the new
        ;; picture instead of the copy every chat app kept under the old address.
        share-image (if composed?
                      (str "https://" host
                           (route-map/path-for route-map/dnd-e5-char-portrait-route :id id)
                           "?v=" (portrait-store/version (portrait-store/tag portrait)))
                      image-url)
        ;; A shared link is where the art actually travels, so the card names
        ;; the artists too -- the same line the sheet prints.
        credit (when composed? (portrait-assets5e/credit-line parsed-portrait))]
    (index-page-response request
                         {:title character-name
                          :description (str (character-summary-description summary)
                                            ". "
                                            description
                                            (when credit (str " · " credit)))
                          :image-url share-image}
                         {"X-Frame-Options" "ALLOW-FROM https://www.worldanvil.com/"})))

(defn artists-page
  "The list of portrait artists. The page itself is the SPA's; this only
   gives the share card a title of its own."
  [request]
  (index-page-response request {:title "Portrait artists"
                                :description "The artists who drew the portrait pieces here."}))

(defn artist-page
  "An artist's profile. The body is the SPA's; the share tags are set here because a crawler never
   runs the JavaScript. A page its artist switched off redirects (302) to /artists; an unknown
   slug is a real 404, so a mistyped link is not indexed (the SPA says there is nobody here)."
  [{:keys [headers db] {:keys [slug]} :path-params :as request}]
  ;; before the artist's own switches are read
  (when db (artist-credit/refresh! db))
  (if-let [artist (artist-profile/artist-by-slug slug)]
    (index-page-response
     request
     (cond-> {:title (artist-profile/page-title artist)
              :description (artist-profile/share-description artist)}
       (artist-profile/shows? artist :examples)
       (assoc :image-url (str "https://" (headers "host") (artist-profile/example-path artist 1)))))
    (if (artist-profile/registered-by-slug slug)
      ;; switched off: 302, never 301, so switching it back on works at once
      {:status 302 :headers {"Location" (artist-profile/index-path)} :body ""}
      (assoc (index-page-response request {:title "No such artist"}) :status 404))))

(def ^:private example-jpegs
  "Rendered examples by [artist n watermark]. Single-flight, so a burst of requests for a new page
   shares one render; bounded, since every edit to an artist's links makes new watermark keys."
  (bounded-cache/cache {:max-entries 64}))

(def ^:private example-failures
  "Examples that failed to render, remembered for a minute so a broken one is not redrawn on every
   request."
  (bounded-cache/cache {:max-entries 64 :max-age-ms 60000}))

(defn- example-jpeg
  "JPEG bytes of example `n` (1-based) for `artist-id`, watermarked with `text`: nil when there is
   none or it failed within the last minute, {:busy retry-seconds} when no draw slot frees. Drawn
   once through the portrait draw pool and kept."
  [artist-id n text]
  (let [k [artist-id n text]]
    (when-not (bounded-cache/lookup example-failures k)
      (try
        (bounded-cache/fetch!
         example-jpegs k
         #(let [{:keys [result busy]}
                (work-pool/run portrait-store/draw-pool
                               (fn [] (some-> (artist-profile/example-portraits artist-id)
                                              (get (dec n))
                                              (portrait-render/render-example-jpeg text))))]
            (cond busy (throw (ex-info "example draw busy" {::busy busy}))
                  result result
                  :else (throw (ex-info "no example" {::failed true})))))
        (catch clojure.lang.ExceptionInfo e
          (let [{::keys [busy]} (ex-data e)]
            (if busy
              {:busy busy}
              (do (bounded-cache/put! example-failures k true) nil))))
        (catch Exception e
          (println "artist example" k "failed -" (.getMessage e))
          (bounded-cache/put! example-failures k true)
          nil)))))

(defn artist-example-jpeg
  "Ring handler: example image :n (1 to example-count) of the artist at :slug, small,
   flattened and watermarked. 404 for an unknown slug, a page or examples switched off, or any
   other :n;
   503 with a Retry-After when every draw slot is taken."
  [{:keys [db] {:keys [slug n]} :path-params}]
  ;; before the watermark is worked out from the artist's links
  (when db (artist-credit/refresh! db))
  (let [artist (artist-profile/artist-by-slug slug)
        n (some->> n (re-matches #"[1-9]") parse-long)
        jpeg (when (and artist n (<= n artist-profile/example-count) (artist-profile/shows? artist :examples))
               (example-jpeg (:artist/id artist) n
                             (artist-profile/watermark-text
                              artist (or (portrait-render/site-mark) branding/app-name))))]
    (cond
      (:busy jpeg)
      {:status 503 :headers {"Retry-After" (str (:busy jpeg)) "Content-Type" "text/plain"} :body "busy"}

      jpeg
      {:status 200
       :headers {"Content-Type" "image/jpeg"
                 "Cache-Control" "public, max-age=86400"
                 ;; contributed artwork -- see character-portrait-png
                 "X-Robots-Tag" "noai, noimageai"}
       :body (ByteArrayInputStream. jpeg)}

      :else {:status 404 :body "no such example"})))

(def header-style
  {:style "color:#2c3445"})

(defn terms-page [body-fn]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (body-fn)})

(defn privacy-policy-page [req]
  (terms-page privacy/privacy-policy))

(defn terms-of-use-page [req]
  (terms-page privacy/terms-of-use))

(defn community-guidelines-page [_]
  (terms-page privacy/community-guidelines))

(defn cookie-policy-page [_]
  (terms-page privacy/cookie-policy))

(defn health-check [_]
  {:status 200 :body "OK"})

(def index-page-routes
  (mapv
   (fn [[route & args]]
     [(apply route-map/path-for route args) :get `default-index-page :route-name route])
   index-page-paths))

(def expanded-index-routes
  (route/expand-routes
   (set index-page-routes)))

(def service-error-handler
  (error-int/error-dispatch [ctx ex]
                            :else (do
                                    (email/send-error-email ctx ex)
                                    (assoc ctx :io.pedestal.interceptor.chain/error ex))))

(def file-hashes (atom {}))

(defn get-file [{:keys [uri] :as request}]
  (ring-resource/resource-request request "public"))

(def get-css get-file)

(def get-js get-file)

(def ^:private portrait-files
  "Every URL the art directory answers: each registered piece, and a strand file beside each
   hair piece. Anything else there (an unregistered piece, a manifest) is a 404."
  (delay (set (for [{:artist/keys [layers]} portrait-assets5e/registry
                    [layer assets] layers
                    {:asset/keys [url]} assets
                    u (cond-> [url]
                        (portrait-fx/hair-layer? layer) (conj (s/replace url #"\.png$" ".strands.png")))]
                u))))

(defn- strands-on-the-fly
  "PNG bytes of a registered hair piece's strand field when no .strands.png was deployed beside
   its art (orcpub.portrait-pack.strands/worked-out-png), so the builder's request does not 404.
   nil for anything else. The build step stays the fast path."
  [uri]
  (when (and (contains? @portrait-files uri) (s/ends-with? uri ".strands.png"))
    (strands/worked-out-png (s/replace uri #"\.strands\.png$" ".png"))))

(defn- served-path
  "`uri` as the static file server will read it: percent-decoded, with `.` and `..` segments
   resolved. The guard judges this, or an encoded path (/image/%70ortraits/...) walks past it."
  [uri]
  (let [decoded (or (codec/url-decode (str uri)) (str uri))]
    (str "/" (s/join "/" (reduce (fn [acc seg]
                                   (case seg
                                     ("" ".") acc
                                     ".." (if (seq acc) (pop acc) acc)
                                     (conj acc seg)))
                                 [] (s/split decoded #"/")))
         (when (s/ends-with? decoded "/") "/"))))

(defn- portrait-art?
  "Whether `uri`, as the file server reads it, is in the portrait art directory."
  [uri]
  (s/starts-with? (served-path uri) "/image/portraits/"))

(defn get-image [{:keys [uri] :as request}]
  (or (get-file request)
      (when-let [bytes (strands-on-the-fly uri)]
        {:status 200
         :headers {"Content-Type" "image/png" "Cache-Control" "public, max-age=86400"}
         :body bytes})))

(def portrait-art-guard
  "First in the chain, ahead of Pedestal's static files, which answer before any route: the
   portrait art directory serves the registered pieces and their strand files, nothing else (a
   manifest or stray file unpacked beside the art would otherwise be public), and every piece it
   serves asks not to be used for AI."
  (interceptor/interceptor
   {:name ::portrait-art-guard
    :enter (fn [ctx]
             (let [uri (get-in ctx [:request :uri])]
               (if (and (portrait-art? uri) (not (contains? @portrait-files (served-path uri))))
                 (assoc ctx :response {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"})
                 ctx)))
    :leave (fn [ctx]
             (if (and (portrait-art? (get-in ctx [:request :uri])) (:response ctx))
               (assoc-in ctx [:response :headers "X-Robots-Tag"] "noai, noimageai")
               ctx))}))

(def get-favicon get-file)

(def webjars-root "META-INF/resources/webjars/")

(defn get-webjar
  "Get a resource containd within a webjar.
   Expects route to be /assets/*"
  [request]
  (let [path (subs (codec/url-decode (req/path-info request)) 1)
        new-path (s/replace-first path #"^assets/" webjars-root)]
    (-> (ring-resp/resource-response new-path)
        (head/head-response request))))

(def routes
  (concat
   (route/expand-routes
    [[["/" {:get `index}
       ^:interceptors [(body-params/body-params) service-error-handler]
       ["/js/*" {:get `get-js}]
       ["/css/*" {:get `get-css}]
       ["/assets/*" {:get `get-webjar}]
       ["/image/*" {:get `get-image}]
       ["/favicon/*" {:get `get-favicon}]
       [(route-map/path-for route-map/register-route)
        {:post `register}]
       [(route-map/path-for route-map/user-route) ^:interceptors [check-auth]
        {:get `get-user
         :put `update-user-preferences
         :delete `delete-user}]
       [(route-map/path-for route-map/user-email-route) ^:interceptors [check-auth]
        {:put `request-email-change}]
       [(route-map/path-for route-map/user-artist-credit-route) ^:interceptors [check-auth]
        {:put `update-artist-credit}]
       [(route-map/path-for route-map/user-sessions-route) ^:interceptors [check-auth]
        {:delete `withdraw-sessions}]
       [(route-map/path-for route-map/follow-user-route :user ":user") ^:interceptors [check-auth]
        {:post `follow-user
         :delete `unfollow-user}]

       ;; Items
       [(route-map/path-for route-map/dnd-e5-items-route) ^:interceptors [check-auth]
        {:post `save-item
         :get `item-list}]
       [(route-map/path-for route-map/dnd-e5-item-route :id ":id") ^:interceptors [check-auth parse-id]
        {:get `get-item
         :delete `delete-item}]

       ;; Characters
       [(route-map/path-for route-map/dnd-e5-char-list-route) ^:interceptors [check-auth]
        {:post `save-character
         :get `character-list}]
       [(route-map/path-for route-map/dnd-e5-char-summary-list-route) ^:interceptors [check-auth]
        {:get `character-summary-list}]
       [(route-map/path-for route-map/dnd-e5-char-report-route) ^:interceptors [check-auth]
        {:post `report-character-problem}]
       [(route-map/path-for route-map/dnd-e5-char-route :id ":id") ^:interceptors [check-auth]
        {:delete `delete-character}]
       [(route-map/path-for route-map/dnd-e5-char-route :id ":id")
        {:get `get-character}]
       [(route-map/path-for route-map/dnd-e5-char-share-route :id ":id" :token ":token") ^:interceptors [parse-id]
        {:get `share/get-share}]
       [(route-map/path-for route-map/dnd-e5-char-share-route :id ":id" :token ":token") ^:interceptors [check-auth parse-id]
        {:put `share/put-share}]
       [(route-map/path-for route-map/dnd-e5-char-share-token-route :id ":id") ^:interceptors [check-auth parse-id]
        {:get `share/get-token
         :put `share/create-token
         :post `share/new-token
         :delete `share/stop-sharing}]

       [(route-map/path-for route-map/dnd-e5-char-page-route :id ":id") ^:interceptors [parse-id]
        {:get `character-page}]
       [(route-map/path-for route-map/dnd-e5-char-portrait-route :id ":id") ^:interceptors [parse-id]
        {:get `character-portrait-png}]
       [(route-map/path-for route-map/artists-page-route)
        {:get `artists-page}]
       [(route-map/path-for route-map/artist-page-route :slug ":slug")
        {:get `artist-page}]
       [(route-map/path-for route-map/artist-example-route :slug ":slug" :n ":n")
        {:get `artist-example-jpeg}]
       [(route-map/path-for route-map/dnd-e5-portrait-artists-route)
        {:get `portrait-artists}]
       [(route-map/path-for route-map/dnd-e5-char-parties-route) ^:interceptors [check-auth]
        {:post `party/create-party
         :get `party/parties}]
       [(route-map/path-for route-map/dnd-e5-char-party-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:delete `party/delete-party}]
       [(route-map/path-for route-map/dnd-e5-char-party-name-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:put `party/update-party-name}]
       [(route-map/path-for route-map/dnd-e5-char-party-characters-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:post `party/add-character}]
       [(route-map/path-for route-map/dnd-e5-char-party-character-route :id ":id" :character-id ":character-id") ^:interceptors [check-auth parse-id check-party-owner]
        {:delete `party/remove-character}]
       [(route-map/path-for route-map/dnd-e5-char-folders-route) ^:interceptors [check-auth]
        {:post `folder/create-folder
         :get `folder/folders}]
       [(route-map/path-for route-map/dnd-e5-char-folder-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:delete `folder/delete-folder}]
       [(route-map/path-for route-map/dnd-e5-char-folder-name-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:put `folder/update-folder-name}]
       [(route-map/path-for route-map/dnd-e5-char-folder-characters-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:post `folder/add-character}]
       [(route-map/path-for route-map/dnd-e5-char-folder-character-route :id ":id" :character-id ":character-id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:delete `folder/remove-character}]
       [(route-map/path-for route-map/login-route)
        {:post `login}]
       [(route-map/path-for route-map/character-pdf-route)
        {:post `character-pdf-2}]
       [(route-map/path-for route-map/image-probe-route)
        {:post `image-probe}]
       [(route-map/path-for route-map/verify-route)
        {:get `verify}]
       [(route-map/path-for route-map/re-verify-route)
        {:get `re-verify}]
       [(route-map/path-for route-map/unsubscribe-route)
        {:get `unsubscribe}]
       [(route-map/path-for route-map/reset-password-route) ^:interceptors [ring/cookies]
        {:post `reset-password}]
       [(route-map/path-for route-map/reset-password-page-route) ^:interceptors [ring/cookies]
        {:get `reset-password-page}]
       [(route-map/path-for route-map/send-password-reset-route)
        {:get `send-password-reset}]
       [(route-map/path-for route-map/privacy-policy-route)
        {:get `privacy-policy-page}]
       [(route-map/path-for route-map/terms-of-use-route)
        {:get `terms-of-use-page}]
       [(route-map/path-for route-map/community-guidelines-route)
        {:get `community-guidelines-page}]
       [(route-map/path-for route-map/cookies-policy-route)
        {:get `cookie-policy-page}]
       [(route-map/path-for route-map/check-email-route)
        {:get `check-email}]
       [(route-map/path-for route-map/check-username-route)
        {:get `check-username}]
       ["/health"
        {:get `health-check}]]]])
   expanded-index-routes))


