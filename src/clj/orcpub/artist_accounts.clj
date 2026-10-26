(ns orcpub.artist-accounts
  "Artist accounts from PORTRAIT_ARTISTS: on start `reconcile!` creates, links, claims or
   revokes each configured artist's account (the cases are tabled in ARTISTS.md). Grant-only:
   removing an entry changes nothing. An artist account changes only how that artist is
   credited; which art is whose stays in the registry. Nothing here faces the web."
  (:require [orcpub.reset-key :as reset-key]
            [clojure.string :as s]
            [datomic.api :as d]
            [orcpub.env :as env]
            [orcpub.user-email :as user-email]
            [cheshire.core :as cheshire]
            [buddy.hashers :as hashers]
            [com.stuartsierra.component :as component]
            [orcpub.crypto :as crypto]
            [orcpub.artist-email :as artist-email]
            [orcpub.artist-credit :as artist-credit]
            [orcpub.dnd.e5.portrait-assets :as pa])
  (:import [java.util Date UUID]))

(def ^:private user-pull
  [:db/id :orcpub.user/username :orcpub.user/email :orcpub.user/verified?
   :orcpub.user/artist :orcpub.user/preferred-name :orcpub.user/password-reset-key
   :orcpub.user/verification-key :orcpub.user/verification-sent :orcpub.user/artist-welcome-sent])

(def welcome-link-days
  "How long a new artist's set-password link lasts. Longer than a normal
   reset's two hours: a welcome can sit unread for days."
  7)

(def preferred-name-purpose crypto/preferred-name-purpose)

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
                                   (user-email/email-key acct)
                                   :revoke)}
                 (string? (:username fields)) (assoc :username (s/trim (:username fields)))
                 (string? (:preferred_name fields)) (assoc :preferred-name (s/trim (:preferred_name fields)))
                 (string? (:welcome_note fields)) (assoc :welcome-note (:welcome_note fields))
                 (string? (:welcome_subject fields)) (assoc :welcome-subject (:welcome_subject fields)))]))
      (catch Exception e
        (println "artist-accounts: PORTRAIT_ARTISTS ignored --" (.getMessage e))
        nil))))

(defn configured
  "This deployment's artist-account config."
  []
  (parse-config (env/value :portrait-artists)))

;; ---------------------------------------------------------------------------
;; Lookups
;; ---------------------------------------------------------------------------

(defn- users-with-email [db email]
  (d/q '[:find [(pull ?e pattern) ...]
         :in $ pattern ?email
         :where [?e :orcpub.user/email-key ?email]]
       db user-pull email))

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
  (let [base (-> (.toLowerCase (str s) java.util.Locale/ROOT) (s/replace #"[^a-z0-9]+" ""))]
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

(defn- lost-race?
  "Whether `e`, or anything that caused it, is a cas or unique-claim refusal."
  [e]
  (boolean (some #(#{:db.error/cas-failed :db.error/unique-conflict} (:db/error (ex-data %)))
                 (take-while some? (iterate #(.getCause ^Throwable %) e)))))

(defn- try-transact
  "Transact; true if it committed, false if a guard (cas or unique claim)
   rejected it because another container did the same work first."
  [conn tx]
  (try @(d/transact conn tx) true
       (catch Exception e
         (if (lost-race? e) false (throw e)))))

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

(defn- new-welcome-link
  "A fresh welcome key, and the transaction data that stores its digest on `id`."
  [id]
  (let [reset-key (str (UUID/randomUUID))
        now (Date.)
        expires (Date. (+ (.getTime now) (* welcome-link-days 24 60 60 1000)))]
    {:reset-key reset-key
     :expires expires
     :tx {:db/id id
          ;; the digest: the reset form looks keys up by it
          :orcpub.user/password-reset-key (reset-key/digest reset-key)
          :orcpub.user/password-reset-sent now
          :orcpub.user/password-reset-expires expires}}))

(defn- locked-password
  "A password nobody knows; the account is locked until the link sets one."
  []
  (hashers/encrypt (str (UUID/randomUUID) (UUID/randomUUID))))

(defn- welcome!
  "Send the welcome for `user`, recording that it went out only if the notifier says so."
  [conn artist-id user spec notify {:keys [reset-key expires]}]
  (when ((:created notify) {:artist-id artist-id
                            :user (select-keys user [:db/id :orcpub.user/username :orcpub.user/email])
                            :spec spec :reset-key reset-key :expires expires})
    @(d/transact conn [[:db/add (:db/id user) :orcpub.user/artist-welcome-sent (Date.)]])))

(defn create!
  "Create a linked account for `artist-id` with a week-long set-password link.
   Returns :created or :raced."
  [conn artist-id {:keys [account username preferred-name] :as spec} notify]
  (let [db (d/db conn)
        artist-name (:artist/name (pa/artist-info artist-id))
        uname (free-username db (or username artist-name account))
        link (new-welcome-link "artist-user")
        name-enc (when preferred-name (crypto/encrypt preferred-name-purpose preferred-name))
        user (cond-> (merge (:tx link)
                            (user-email/email-attrs account)
                            {:orcpub.user/username uname
                             :orcpub.user/password (locked-password)
                             ;; confirmed by the act of using the emailed link
                             :orcpub.user/verified? false
                             :orcpub.user/created (Date.)
                             :orcpub.user/artist artist-id})
               name-enc (assoc :orcpub.user/preferred-name name-enc))]
    (if (try-transact conn [{:orcpub.artist-provision/key (str (name artist-id) " " account)}
                            user])
      (let [id (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] (d/db conn) uname)
            u {:db/id id :orcpub.user/username uname :orcpub.user/email account}]
        (unlink-others! conn artist-id id notify)
        (welcome! conn artist-id u spec notify link)
        :created)
      :raced)))

(defn claim!
  "Take over an unconfirmed account holding the artist's address: lock it with a new password,
   cancel its confirmation link, link it, and send the welcome. Returns :claimed, :other-artist
   or :raced."
  [conn artist-id {:keys [:db/id :orcpub.user/artist] :as user} {:keys [preferred-name] :as spec} notify]
  (if (some? artist)
    (do (println "artist-accounts:" (:orcpub.user/username user) "already speaks for" artist
                 "- not claimed for" artist-id)
        :other-artist)
    (let [link (new-welcome-link id)
          tx (cond-> [[:db/cas id :orcpub.user/artist nil artist-id]
                      [:db/cas id :orcpub.user/password-reset-key
                       (:orcpub.user/password-reset-key user) (get-in link [:tx :orcpub.user/password-reset-key])]
                      (-> (:tx link)
                          (dissoc :orcpub.user/password-reset-key)
                          (assoc :orcpub.user/password (locked-password)))]
               (:orcpub.user/verification-key user)
               (conj [:db/retract id :orcpub.user/verification-key (:orcpub.user/verification-key user)])
               (:orcpub.user/verification-sent user)
               (conj [:db/retract id :orcpub.user/verification-sent (:orcpub.user/verification-sent user)])
               preferred-name
               (conj [:db/add id :orcpub.user/preferred-name (crypto/encrypt preferred-name-purpose preferred-name)])
               (and (not preferred-name) (:orcpub.user/preferred-name user))
               (conj [:db/retract id :orcpub.user/preferred-name (:orcpub.user/preferred-name user)]))]
      (if (try-transact conn tx)
        (do (unlink-others! conn artist-id id notify)
            (welcome! conn artist-id user spec notify link)
            :claimed)
        :raced))))

(defn resend-welcome!
  "A fresh welcome link for a linked account whose welcome never went out. The old key is
   unknowable (only its digest is kept), so it is replaced. Returns :resent or :raced."
  [conn artist-id user spec notify]
  (let [id (:db/id user)
        link (new-welcome-link id)]
    (if (try-transact conn [[:db/cas id :orcpub.user/password-reset-key
                             (:orcpub.user/password-reset-key user)
                             (get-in link [:tx :orcpub.user/password-reset-key])]
                            (dissoc (:tx link) :orcpub.user/password-reset-key)])
      (do (welcome! conn artist-id user spec notify link) :resent)
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
          confirmed (filter :orcpub.user/verified? users)
          linked (some #(when (= artist-id (:orcpub.user/artist %)) %) users)]
      (cond
        (and linked (not (:orcpub.user/verified? linked)) (not (:orcpub.user/artist-welcome-sent linked)))
        (resend-welcome! conn artist-id linked spec notify)
        linked :already
        (= 1 (count confirmed)) (link-existing! conn artist-id (first confirmed) spec notify)
        (> (count confirmed) 1)
        (do (println "artist-accounts: several confirmed accounts share" account "- linking none") :ambiguous)
        (= 1 (count users)) (claim! conn artist-id (first users) spec notify)
        (seq users)
        (do (println "artist-accounts: several unconfirmed accounts share" account "- claiming none") :ambiguous)
        :else (create! conn artist-id spec notify)))))

(def log-only-notify
  "What happens on each change when no emailer is supplied: a log line. :created answers
   true, as there is no welcome to send again."
  {:created  (fn [{:keys [artist-id user]}]
               (println "artist-accounts: welcomed" (:orcpub.user/username user) "to" artist-id)
               true)
   :upgraded (fn [{:keys [artist-id user]}]
               (println "artist-accounts: linked" (:orcpub.user/username user) "to" artist-id))
   :unlinked (fn [{:keys [artist-id user]}]
               (println "artist-accounts: unlinked" (:orcpub.user/username user) "from" artist-id))})

(def notify
  "What the running site does on each change: the emails in
   orcpub.artist-email, each also logged. Answers what the email answers."
  (into {}
        (for [k [:created :upgraded :unlinked]]
          [k (fn [event]
               ((get log-only-notify k) event)
               ((get artist-email/notify k) event))])))

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
  "Called when a confirmed account confirms a change to its email address: if the new
   address is an artist's configured account, link it now."
  ([conn user-id] (on-email-confirmed! conn user-id (configured) log-only-notify))
  ([conn user-id config notify]
   (let [user (d/pull (d/db conn) [:db/id :orcpub.user/username :orcpub.user/email
                                  :orcpub.user/verified? :orcpub.user/artist
                                  :orcpub.user/preferred-name] user-id)
         email (user-email/email-key (:orcpub.user/email user))]
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
      ;; artists' own credit edits, so the emails below use their chosen name
      (try (artist-credit/refresh! (d/db c))
           (catch Exception e (println "artist-accounts: credits not refreshed -" (.getMessage e))))
      (when (seq (configured))
        (future
          (try (println "artist-accounts:" (reconcile! c (configured) (or (:notify this) notify)))
               (catch Throwable t (println "artist-accounts: reconcile failed -" (.getMessage t)))))))
    this)
  (stop [this] this))

(defn new-artist-accounts
  ([] (map->ArtistAccounts {}))
  ([notify] (map->ArtistAccounts {:notify notify})))
