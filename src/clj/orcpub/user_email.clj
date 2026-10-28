(ns orcpub.user-email
  "An account's email as typed (:orcpub.user/email, for display and sending) and as looked up
   (:orcpub.user/email-key: trimmed and lowercased, indexed). Every writer of the email sets both;
   a backfill at start gives the key to accounts written before it existed.
   plan-lookup-indexes.md on agents/develop."
  (:require [clojure.string :as s]
            [com.stuartsierra.component :as component]
            [datomic.api :as d])
  (:import [java.util Locale]))

(defn email-key
  "The lookup form of email text `email`: trimmed and lowercased in the root locale (a Turkish JVM
   must not turn I into a dotless i). Nil when `email` is nil or blank."
  [email]
  (some-> email str s/trim not-empty (.toLowerCase Locale/ROOT)))

(defn email-attrs
  "The attributes that store `email` on an account: the address as given, and its `email-key`."
  [email]
  {:orcpub.user/email email
   :orcpub.user/email-key (email-key email)})

(defn missing-keys
  "The accounts in `db` with an email but no email key, as [:db/cas id :orcpub.user/email-key nil key]
   ops: each writes only while the account still has no key, so an email change confirmed meanwhile
   keeps its own. An account whose email is blank has no key to give and is left out."
  [db]
  (->> (d/q '[:find ?e ?email
              :where [?e :orcpub.user/email ?email]
                     [(missing? $ ?e :orcpub.user/email-key)]]
            db)
       (keep (fn [[e email]] (when-let [k (email-key email)] [:db/cas e :orcpub.user/email-key nil k])))))

(defn- cas-failed?
  "Whether `e` or one of its causes is a rejected compare-and-set."
  [e]
  (some #(= :db.error/cas-failed (:db/error (ex-data %))) (take-while some? (iterate ex-cause e))))

(defn- transact-keys!
  "Transacts the `ops` (from `missing-keys`) in `conn` as one transaction, or one at a time when one
   of them is rejected because its account got a key meanwhile. Returns how many were written."
  [conn ops]
  (try @(d/transact conn ops)
       (count ops)
       (catch Exception e
         (if-not (cas-failed? e)
           (throw e)
           (count (filter (fn [op] (try @(d/transact conn [op]) true
                                        (catch Exception e2 (if (cas-failed? e2) false (throw e2)))))
                          ops))))))

(defn backfill!
  "Gives every account in `conn` without an email key its key: reads them once, then transacts
   `batch` accounts at a time. Returns the number of accounts updated."
  [conn batch]
  (reduce (fn [done ops] (+ done (transact-keys! conn ops)))
          0
          (partition-all batch (missing-keys (d/db conn)))))

(defrecord EmailKeys [conn]
  component/Lifecycle
  (start [this]
    ;; off the startup path: on the first start after the upgrade this reads every account
    (when-let [c (:conn conn)]
      (future
        (try (let [n (backfill! c 500)]
               (when (pos? n) (println "user-email: gave" n "accounts their lookup key")))
             (catch Throwable t (println "user-email: backfill failed -" (.getMessage t))))))
    this)
  (stop [this] this))

(defn new-email-keys
  "The component that runs `backfill!` in the background once the database is up."
  []
  (map->EmailKeys {}))
