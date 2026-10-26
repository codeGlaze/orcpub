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
  "The accounts in `db` with an email but no email key, as {:db/id :orcpub.user/email-key} ready to
   transact. An account whose email is blank has no key to give and is left out."
  [db]
  (->> (d/q '[:find ?e ?email
              :where [?e :orcpub.user/email ?email]
                     [(missing? $ ?e :orcpub.user/email-key)]]
            db)
       (keep (fn [[e email]] (when-let [k (email-key email)] {:db/id e :orcpub.user/email-key k})))))

(defn backfill!
  "Gives every account in `conn` without an email key its key: reads them once, then transacts
   `batch` accounts at a time. Returns the number of accounts updated."
  [conn batch]
  (reduce (fn [done tx] @(d/transact conn tx) (+ done (count tx)))
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
