(ns orcpub.datomic
  "Datomic database component with connection management and error handling.

  Provides a component that manages the database connection lifecycle,
  including database creation, connection establishment, and schema initialization.
  All operations include error handling with clear error messages."
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [orcpub.db.schema :as schema]))

(defrecord DatomicComponent [uri conn]
  component/Lifecycle
  (start [this]
    (if (:conn this)
      this
      (try
        (when (nil? uri)
          (throw (ex-info "Database URI is required but not configured"
                          {:error :missing-db-uri})))

        (println "Creating/connecting to Datomic database:" uri)
        (d/create-database uri)

        (let [connection (try
                           (d/connect uri)
                           (catch Exception e
                             (throw (ex-info "Failed to connect to Datomic database. Please verify the database URI and that Datomic is running."
                                             {:error :db-connection-failed
                                              :uri uri}
                                             e))))]
          (try
            @(d/transact connection schema/all-schemas)
            (println "Successfully initialized database schema")
            (catch Exception e
              (throw (ex-info "Failed to initialize database schema. The database may be in an inconsistent state."
                              {:error :schema-initialization-failed
                               :uri uri}
                              e))))
          (assoc this :conn connection))
        (catch clojure.lang.ExceptionInfo e
          (throw e))
        (catch Exception e
          (throw (ex-info "Unexpected error during database initialization"
                          {:error :db-init-failed
                           :uri uri}
                          e))))))
  (stop [this]
    (assoc this :conn nil)))

(defn new-datomic [uri]
  (map->DatomicComponent {:uri uri}))
