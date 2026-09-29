(ns orcpub.artist-accounts
  "Artist accounts, provisioned from deployment config.

   Each portrait artist in PORTRAIT_ARTISTS may name an account:

     {\"house-pack\": {\"name\": \"Fusspot\",
                     \"account\": \"hello@fusspot.rip\",
                     \"username\": \"fusspot\",
                     \"preferred_name\": \"Fuss\",
                     \"welcome_note\": \"We did it! ...\"}}

   On server start `reconcile!` works through that list:

     no account with that email  -> create one, linked, with a single-use
                                    set-password link that lasts a week
     a confirmed account         -> link it
     already linked              -> nothing, so restarts never resend
     an unconfirmed account      -> nothing yet; `on-email-confirmed!` links it
                                    when its owner confirms the address

   Linking to a new account unlinks any other account that spoke for the same
   artist, so the config is the one record of who that is. \"account\": null
   revokes. Removing an artist's entry does NOTHING -- grant-only, so a bad
   edit cannot silently strip an artist of access.

   An artist account can only change how that artist is CREDITED. Which art is
   whose stays in the registry in code, so a compromised artist account cannot
   re-attribute anyone's work.

   Nothing here faces the web: no admin page, no invite endpoint. The only
   inputs are the deployment's own config and an email the owner confirmed."
  (:require [clojure.string :as s]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [cheshire.core :as cheshire]
            [buddy.hashers :as hashers]
            [com.stuartsierra.component :as component]
            [orcpub.crypto :as crypto]
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.util Date UUID]))

(def welcome-link-days
  "How long a new artist's set-password link lasts. Longer than a normal
   reset's 24 hours: a welcome can sit unread for days."
  7)

(def preferred-name-purpose "user/preferred-name")

;; ---------------------------------------------------------------------------
;; Config
;; ---------------------------------------------------------------------------

(defn parse-config
  "The account part of PORTRAIT_ARTISTS as {artist-id {...}}, only for entries
   that mention an account. :account is a lower-cased email, or :revoke for an
   explicit null. Server-side only: none of this is sent to browsers."
  [raw]
  (when-not (s/blank? raw)
    (try
      (into {}
            (for [[id fields] (cheshire/parse-string raw true)
                  :when (and (map? fields) (contains? fields :account))
                  :let [acct (:account fields)]]
              [(keyword (name id))
               (cond-> {:account (if (string? acct)
                                   (s/lower-case (s/trim acct))
                                   :revoke)}
                 (string? (:username fields)) (assoc :username (s/trim (:username fields)))
                 (string? (:preferred_name fields)) (assoc :preferred-name (s/trim (:preferred_name fields)))
                 (string? (:welcome_note fields)) (assoc :welcome-note (:welcome_note fields)))]))
      (catch Exception e
        (println "artist-accounts: PORTRAIT_ARTISTS ignored --" (.getMessage e))
        nil))))

(defn configured
  "This deployment's artist-account config."
  []
  (parse-config (not-empty (or (env :portrait-artists) (System/getenv "PORTRAIT_ARTISTS")))))

;; ---------------------------------------------------------------------------
;; Lookups
;; ---------------------------------------------------------------------------

(defn- users-with-email [db email]
  (d/q '[:find [(pull ?e [:db/id :orcpub.user/username :orcpub.user/email
                          :orcpub.user/verified? :orcpub.user/artist
                          :orcpub.user/preferred-name]) ...]
         :in $ ?email
         :where [?e :orcpub.user/email ?e-mail]
                [(clojure.string/lower-case ?e-mail) ?email]]
       db email))

(defn- accounts-for-artist [db artist-id]
  (d/q '[:find [(pull ?e [:db/id :orcpub.user/username :orcpub.user/email
                          :orcpub.user/preferred-name]) ...]
         :in $ ?a
         :where [?e :orcpub.user/artist ?a]]
       db artist-id))

(defn- username-taken? [db username]
  (boolean (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] db username)))

(defn username-base
  "A username from a display name: lower case, letters and digits only."
  [s]
  (let [base (-> (str s) s/lower-case (s/replace #"[^a-z0-9]+" ""))]
    (if (s/blank? base) "artist" (subs base 0 (min 20 (count base))))))

(defn- free-username [db wanted]
  (let [base (username-base wanted)]
    (first (remove #(username-taken? db %)
                   (cons base (map #(str base %) (iterate inc 2)))))))

(defn preferred-name
  "The account's preferred name, decrypted, or nil."
  [user]
  (crypto/decrypt preferred-name-purpose (:orcpub.user/preferred-name user)))

;; ---------------------------------------------------------------------------
;; Linking
;; ---------------------------------------------------------------------------

(defn- cas-tx
  "Set :orcpub.user/artist from `from` (nil = unset) to `to` (nil = remove),
   failing the transaction if another container got there first."
  [id from to]
  (if to
    [[:db/cas id :orcpub.user/artist from to]]
    [[:orcpub.fn/retract-if-equal id :orcpub.user/artist from]]))

(defn- try-transact
  "Transact; true if it committed, false if a guard (cas or unique claim)
   rejected it because another container did the same work first."
  [conn tx]
  (try @(d/transact conn tx) true
       (catch Exception e
         (let [msg (str (.getMessage e) (some-> e .getCause .getMessage))]
           (if (re-find #"(?i)cas|compare|unique" msg)
             false
             (throw e))))))

(defn- unlink-others!
  "Unlink every other account that speaks for `artist-id`, telling each."
  [conn artist-id keep-id notify]
  (doseq [{:keys [:db/id] :as u} (accounts-for-artist (d/db conn) artist-id)
          :when (not= id keep-id)]
    (when (try-transact conn (cas-tx id artist-id nil))
      ((:unlinked notify) {:artist-id artist-id :user u}))))

(defn link-existing!
  "Link a confirmed account to `artist-id`. Returns :linked, :already,
   :other-artist (the account speaks for someone else; left alone) or :raced."
  [conn artist-id {:keys [:db/id :orcpub.user/artist] :as user} spec notify]
  (cond
    (= artist artist-id) :already
    (some? artist) (do (println "artist-accounts:" (:orcpub.user/username user)
                                "already speaks for" artist "- not relinked to" artist-id)
                       :other-artist)
    (try-transact conn (cas-tx id nil artist-id))
    (do (unlink-others! conn artist-id id notify)
        ((:upgraded notify) {:artist-id artist-id :user user :spec spec})
        :linked)
    :else :raced))

(defn create!
  "Create a linked account for `artist-id` with a week-long set-password link.
   Returns :created or :raced."
  [conn artist-id {:keys [account username preferred-name] :as spec} notify]
  (let [db (d/db conn)
        artist-name (:artist/name (pa/artist-info artist-id))
        uname (free-username db (or username artist-name account))
        reset-key (str (UUID/randomUUID))
        now (Date.)
        expires (Date. (+ (.getTime now) (* welcome-link-days 24 60 60 1000)))
        name-enc (when preferred-name (crypto/encrypt preferred-name-purpose preferred-name))
        user (cond-> {:db/id "artist-user"
                      :orcpub.user/username uname
                      :orcpub.user/email account
                      ;; nobody knows this; the account is locked until the
                      ;; artist chooses a password through the link
                      :orcpub.user/password (hashers/encrypt (str (UUID/randomUUID) (UUID/randomUUID)))
                      ;; confirmed by the act of using the emailed link
                      :orcpub.user/verified? false
                      :orcpub.user/created now
                      :orcpub.user/artist artist-id
                      :orcpub.user/password-reset-key reset-key
                      :orcpub.user/password-reset-sent now
                      :orcpub.user/password-reset-expires expires}
               name-enc (assoc :orcpub.user/preferred-name name-enc))]
    (if (try-transact conn [{:orcpub.artist-provision/key (str (name artist-id) " " account)}
                            user])
      (let [id (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] (d/db conn) uname)]
        (unlink-others! conn artist-id id notify)
        ((:created notify) {:artist-id artist-id
                            :user {:db/id id :orcpub.user/username uname :orcpub.user/email account}
                            :spec spec :reset-key reset-key :expires expires})
        :created)
      :raced)))

(defn revoke!
  "Unlink every account for `artist-id`."
  [conn artist-id notify]
  (unlink-others! conn artist-id nil notify))

(defn- reconcile-one! [conn artist-id {:keys [account] :as spec} notify]
  (cond
    (nil? (pa/artist-info artist-id))
    (do (println "artist-accounts: no artist" artist-id "in the registry; skipped") :unknown-artist)

    (= :revoke account) (do (revoke! conn artist-id notify) :revoked)

    :else
    (let [users (users-with-email (d/db conn) account)
          confirmed (filter :orcpub.user/verified? users)]
      (cond
        (some #(= artist-id (:orcpub.user/artist %)) users) :already
        (= 1 (count confirmed)) (link-existing! conn artist-id (first confirmed) spec notify)
        (> (count confirmed) 1)
        (do (println "artist-accounts: several confirmed accounts share" account "- linking none") :ambiguous)
        (seq users)
        (do (println "artist-accounts:" account "has an unconfirmed account; it links on confirmation") :waiting)
        :else (create! conn artist-id spec notify)))))

(def log-only-notify
  "What happens on each change when no emailer is supplied: a log line."
  {:created  (fn [{:keys [artist-id user]}]
               (println "artist-accounts: created" (:orcpub.user/username user) "for" artist-id))
   :upgraded (fn [{:keys [artist-id user]}]
               (println "artist-accounts: linked" (:orcpub.user/username user) "to" artist-id))
   :unlinked (fn [{:keys [artist-id user]}]
               (println "artist-accounts: unlinked" (:orcpub.user/username user) "from" artist-id))})

(def notify
  "What the running site does on each change. Log lines until the emails
   are wired in."
  log-only-notify)

(defn reconcile!
  "Bring accounts in line with `config`. Returns {artist-id outcome}. Safe to
   run repeatedly and from several containers at once."
  ([conn] (reconcile! conn (configured) log-only-notify))
  ([conn config notify]
   (into {}
         (for [[artist-id spec] config]
           [artist-id (try (reconcile-one! conn artist-id spec notify)
                           (catch Exception e
                             (println "artist-accounts: failed for" artist-id "-" (.getMessage e))
                             :error))]))))

(defn on-email-confirmed!
  "Called when an account confirms an email address: if that address is an
   artist's configured account, link it now."
  ([conn user-id] (on-email-confirmed! conn user-id (configured) log-only-notify))
  ([conn user-id config notify]
   (let [user (d/pull (d/db conn) [:db/id :orcpub.user/username :orcpub.user/email
                                  :orcpub.user/verified? :orcpub.user/artist
                                  :orcpub.user/preferred-name] user-id)
         email (some-> (:orcpub.user/email user) s/lower-case)]
     (when (and email (:orcpub.user/verified? user))
       (some (fn [[artist-id spec]]
               (when (and (= email (:account spec)) (pa/artist-info artist-id))
                 (link-existing! conn artist-id user spec notify)))
             config)))))

;; ---------------------------------------------------------------------------
;; Startup
;; ---------------------------------------------------------------------------

(defrecord ArtistAccounts [conn]
  component/Lifecycle
  (start [this]
    ;; off the startup path: a slow mail server must not hold up the site
    (when-let [c (:conn conn)]
      (when (seq (configured))
        (future
          (try (println "artist-accounts:" (reconcile! c (configured) (or (:notify this) notify)))
               (catch Throwable t (println "artist-accounts: reconcile failed -" (.getMessage t)))))))
    this)
  (stop [this] this))

(defn new-artist-accounts
  ([] (map->ArtistAccounts {}))
  ([notify] (map->ArtistAccounts {:notify notify})))
