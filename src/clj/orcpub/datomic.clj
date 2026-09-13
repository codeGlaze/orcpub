(ns orcpub.datomic
  "Datomic database component with connection management and error handling.

  Provides a component that manages the database connection lifecycle,
  including database creation, connection establishment, and schema initialization.
  All operations include error handling with clear error messages."
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [orcpub.config :as config]
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

        ;; NEVER log the raw uri: a datomic:sql one carries the database password.
        (println "Creating/connecting to Datomic database:" (config/redact-secrets uri))
        ;; Unset, the uri defaults to a LOCAL DEV transactor. In production that fails with
        ;; "cannot connect to localhost:4334", which names the symptom and not the cause.
        (when-not (config/datomic-env)
          (println (str "NOTE: DATOMIC_URL is not set, so the local development default is in use.\n"
                        "      If this is not a development machine, set DATOMIC_URL and restart.")))
        (d/create-database uri)

        (let [connection (try
                           (d/connect uri)
                           (catch Exception e
                             (throw (ex-info "Failed to connect to Datomic database. Please verify the database URI and that Datomic is running."
                                             {:error :db-connection-failed
                                              :uri (config/redact-secrets uri)}
                                             e))))]
          (try
            @(d/transact connection schema/all-schemas)
            (println "Successfully initialized database schema")
            (catch Exception e
              (throw (ex-info "Failed to initialize database schema. The database may be in an inconsistent state."
                              {:error :schema-initialization-failed
                               :uri (config/redact-secrets uri)}
                              e))))
          (assoc this :conn connection))
        (catch clojure.lang.ExceptionInfo e
          (throw e))
        (catch Exception e
          (throw (ex-info "Unexpected error during database initialization"
                          {:error :db-init-failed
                           :uri (config/redact-secrets uri)}
                          e))))))
  (stop [this]
    (assoc this :conn nil)))

(defn new-datomic [uri]
  ;; The real uri, not a redacted one -- this is what d/connect is given.
  (map->DatomicComponent {:uri uri}))
