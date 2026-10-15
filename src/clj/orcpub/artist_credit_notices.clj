(ns orcpub.artist-credit-notices
  "Emails about an artist's credit changes, to the artist and the admin, batched without letting
   anyone delay them: the first change after a quiet spell is emailed at once and opens a ten-minute
   window; changes inside it are collected and sent together, once, when it ends. The window starts
   at that first change and never extends. State is in the database (:orcpub.user/artist-credit-window
   and -pending), so a batch survives a restart and is sent once however many containers run;
   `flush-due!` runs every minute."
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [orcpub.artist-credit :as artist-credit]
            [orcpub.artist-email :as artist-email]
            [orcpub.heartbeat :as heartbeat])
  (:import [java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit]))

(def window-ms
  "How long after an emailed change further changes are collected into one email."
  (* 10 60 1000))

(defn- window-open?
  "Whether a window that started at `start` (a Date or nil) is still open at `now` (a Date)."
  [^java.util.Date start ^java.util.Date now]
  (boolean (and start (< (.getTime now) (+ (.getTime start) window-ms)))))

(defn saved!
  "Call after artist `user` (an account entity map) saved a change on `conn`, from `before` to
   `after` (credits) for `artist-id`. Outside a window: emails at once and opens one. Inside: marks
   the change pending (keeping the earliest `before`) for `flush-due!`. Returns :sent or :pending."
  [conn {:keys [user artist-id before after]}]
  (let [id (:db/id user)
        now (heartbeat/now)
        {:orcpub.user/keys [artist-credit-window artist-credit-pending]}
        (d/pull (d/db conn) [:orcpub.user/artist-credit-window :orcpub.user/artist-credit-pending] id)]
    (if (window-open? artist-credit-window now)
      (do (when (s/blank? artist-credit-pending)
            @(d/transact conn [{:db/id id :orcpub.user/artist-credit-pending (pr-str (or before {}))}]))
          :pending)
      (do @(d/transact conn [{:db/id id :orcpub.user/artist-credit-window now
                              :orcpub.user/artist-credit-pending ""}])
          (future (artist-email/credit-changed! {:artist-id artist-id :user user
                                                 :before before :after after}))
          :sent))))

(defn- claim!
  "Take the pending batch `pending` of account `id`, clearing it by compare-and-set; true only for
   the one caller whose clear commits, so each batch is sent once."
  [conn id pending]
  (try @(d/transact conn [[:db/cas id :orcpub.user/artist-credit-pending pending ""]]) true
       (catch Exception _ false)))

(defn flush-due!
  "Email every batch on `conn` whose window has ended, once each: the credit from before its first
   change to the credit now (nothing when the changes undid themselves). Returns how many went."
  [conn]
  (let [now (heartbeat/now)
        db (d/db conn)
        due (d/q '[:find ?u ?pending ?start
                   :where [?u :orcpub.user/artist-credit-pending ?pending]
                          [(not= ?pending "")]
                          [?u :orcpub.user/artist-credit-window ?start]]
                 db)]
    (reduce (fn [n [id pending start]]
              (if (and (not (window-open? start now)) (claim! conn id pending))
                (let [user (d/pull db '[*] id)
                      before (not-empty (edn/read-string pending))
                      after (some-> (:orcpub.user/artist-credit user) artist-credit/read-stored)]
                  (if (= before after)
                    n
                    (do (artist-email/credit-changed! {:artist-id (:orcpub.user/artist user) :user user
                                                       :before before :after after})
                        (inc n))))
                n))
            0 due)))

(defrecord CreditNotices [conn ^ScheduledExecutorService executor]
  component/Lifecycle
  (start [this]
    (if executor
      this
      (let [ex (Executors/newSingleThreadScheduledExecutor
                (reify ThreadFactory
                  (newThread [_ r] (doto (Thread. ^Runnable r "credit-notices") (.setDaemon true)))))]
        (.scheduleAtFixedRate ex ^Runnable (fn [] (try (flush-due! (:conn conn))
                                                       (catch Throwable e
                                                         (println "WARNING: credit change emails not sent this minute:"
                                                                  (.getMessage e)))))
                              1 1 TimeUnit/MINUTES)
        (assoc this :executor ex))))
  (stop [this]
    (when executor (.shutdownNow executor))
    (assoc this :executor nil)))

(defn new-credit-notices
  "The component that sends due credit-change batches every minute; needs :conn."
  []
  (map->CreditNotices {}))
