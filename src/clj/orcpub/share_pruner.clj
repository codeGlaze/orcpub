;; Deletes share links nobody has used: a minute after the server starts, then hourly. Each run records a
;; beat first, so the time the server was off is not counted against any share.
(ns orcpub.share-pruner
  (:require [com.stuartsierra.component :as component]
            [orcpub.routes.share :as share])
  (:import [java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit]))

(defn- sweep [conn]
  (try
    (share/record-beat! conn)
    (let [n (share/prune! conn)]
      (when (pos? n) (println "Deleted" n "share links unused past ORCPUB_SHARE_PRUNE_DAYS")))
    (catch Throwable e
      (println "WARNING: share link pruning failed and deleted nothing; it runs again in an hour:" (.getMessage e)))))

(defrecord SharePruner [conn ^ScheduledExecutorService executor]
  component/Lifecycle
  (start [this]
    (if executor
      this
      (let [ex (Executors/newSingleThreadScheduledExecutor
                (reify ThreadFactory
                  (newThread [_ r] (doto (Thread. ^Runnable r "share-pruner") (.setDaemon true)))))]
        (.scheduleAtFixedRate ex ^Runnable (fn [] (sweep (:conn conn))) 1 60 TimeUnit/MINUTES)
        (assoc this :executor ex))))
  (stop [this]
    (when executor (.shutdownNow executor))
    (assoc this :executor nil)))

(defn new-pruner [] (map->SharePruner {}))
