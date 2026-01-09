(ns orcpub.dev-init
  (:require [datomic.api :as d]
            [orcpub.db.schema :as schema]
            [orcpub.config :refer [get-datomic-uri]])
  (:gen-class))

(defn -main [& _]
  (let [uri (get-datomic-uri)]
    (println "Ensuring database exists at" uri)
    (println "Note: Datomic may emit logs to this terminal; if you prefer, run this in the background or tail /tmp/datomic-transactor.log in another terminal.")
    (try
      ;; create-database returns true if created, false if it already exists
      (when-not (d/create-database uri)
        (println "Database already exists or create-database returned false."))
      (let [conn (d/connect uri)]
        (println "Applying schema...")
        (d/transact conn schema/all-schemas)
        (println "DB init done."))
      (catch Exception e
        (binding [*out* *err*]
          (println "DB init failed:" (.getMessage e)))
        (System/exit 1)))))