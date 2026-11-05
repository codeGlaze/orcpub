;; When the server was running, kept in the database: an hourly beat, and each gap between beats long
;; enough to mean the server was off or its clock jumped ahead. Scheduled jobs run on the same tick, after
;; the beat, so a job that measures time (share link pruning) can leave out the time the server was off.
(ns orcpub.heartbeat
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d])
  (:import [java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit]))

(defn now [] (java.util.Date.))

(def ^:private hour-ms (* 60 60 1000))

(def ^:private gap-ms
  "Beats come hourly; a longer gap than this means the server was off or its clock jumped ahead."
  (+ hour-ms (* 10 60 1000)))

(defn last-beat
  "When the server last recorded a beat, or nil."
  [db]
  (d/q '[:find ?t . :where [?e :db/ident :orcpub.heartbeat/clock] [?e :orcpub.heartbeat/beat ?t]] db))

(defn beat!
  "Records that the server is running, and records the time since the last beat as an outage when the gap
   is longer than hourly beats explain. A clock set back records no outage."
  [conn]
  (let [at       (now)
        previous ^java.util.Date (last-beat (d/db conn))]
    @(d/transact conn (cond-> [{:db/ident :orcpub.heartbeat/clock :orcpub.heartbeat/beat at}]
                        (and previous (> (- (.getTime at) (.getTime previous)) gap-ms))
                        (conj {:orcpub.outage/from previous :orcpub.outage/to at})))))

(defn outages
  "Every recorded outage, as [from to] dates."
  [db]
  (d/q '[:find ?from ?to :where [?o :orcpub.outage/from ?from] [?o :orcpub.outage/to ?to]] db))

(defn running-ms
  "Milliseconds from `from` to `to` that the server was running, going by `off`, the [from to] pairs
   outages returns. Negative when `from` is later than `to`, as after a clock set back."
  [off ^java.util.Date from ^java.util.Date to]
  (let [a (.getTime from)
        b (.getTime to)]
    (- b a (reduce + (for [[^java.util.Date start ^java.util.Date end] off]
                       (max 0 (- (min b (.getTime end)) (max a (.getTime start)))))))))

(defn tick
  "One heartbeat: the beat, then each job. Jobs wait for the next tick when the beat cannot be recorded,
   since an unrecorded outage would count against whatever they measure; one job failing does not stop
   the others."
  [conn jobs]
  (when (try (beat! conn) true
             (catch Throwable e
               (println "WARNING: the heartbeat was not recorded, so scheduled jobs wait an hour:" (.getMessage e))
               false))
    (doseq [[job-name job] jobs]
      (try (job conn)
           (catch Throwable e
             (println "WARNING:" job-name "failed and runs again in an hour:" (.getMessage e)))))))

(defrecord Heartbeat [conn jobs ^ScheduledExecutorService executor]
  component/Lifecycle
  (start [this]
    (if executor
      this
      (let [ex (Executors/newSingleThreadScheduledExecutor
                (reify ThreadFactory
                  (newThread [_ r] (doto (Thread. ^Runnable r "heartbeat") (.setDaemon true)))))]
        (.scheduleAtFixedRate ex ^Runnable (fn [] (tick (:conn conn) jobs)) 1 60 TimeUnit/MINUTES)
        (assoc this :executor ex))))
  (stop [this]
    (when executor (.shutdownNow executor))
    (assoc this :executor nil)))

(defn new-heartbeat
  "A heartbeat that runs `jobs`, a map of a name for the log to (fn [conn]), a minute after the server
   starts and then hourly, each time after the beat."
  [jobs]
  (map->Heartbeat {:jobs jobs}))
