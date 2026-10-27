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
            [orcpub.outbox :as outbox]
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
   `after` (credits) for `artist-id`. Inside a window: marks the change pending (keeping the
   earliest `before`) for `flush-due!`. Outside one: queues the email in the outbox and opens a
   window in one transaction, from an overdue batch's `before` when one is still waiting. Returns
   :sent or :pending."
  [conn {:keys [user artist-id before after]}]
  (let [id (:db/id user)
        now (heartbeat/now)
        {:orcpub.user/keys [artist-credit-window artist-credit-pending]}
        (d/pull (d/db conn) [:orcpub.user/artist-credit-window :orcpub.user/artist-credit-pending] id)
        overdue (when-not (s/blank? artist-credit-pending) artist-credit-pending)]
    (if (window-open? artist-credit-window now)
      (do (when-not overdue
            @(d/transact conn [{:db/id id :orcpub.user/artist-credit-pending (pr-str (or before {}))}]))
          :pending)
      (let [from (if overdue (not-empty (edn/read-string overdue)) before)]
        ;; the cas fails if a flush took the overdue batch first; that flush then emails it
        @(d/transact conn (into [(if overdue
                                   [:db/cas id :orcpub.user/artist-credit-pending overdue ""]
                                   {:db/id id :orcpub.user/artist-credit-pending ""})
                                 {:db/id id :orcpub.user/artist-credit-window now}]
                                (artist-email/credit-changed-tx {:artist-id artist-id :user user
                                                                 :before from :after after})))
        (@outbox/nudge)
        :sent))))

(defn- send-batch!
  "Claim the pending batch `pending` of account `id` (read from `db`) and queue its email in one
   transaction: the claim clears the pending field by compare-and-set, so of several callers one
   commits, and the email exists exactly when the claim does. No email when the changes undid
   themselves. Returns true when this caller's claim committed with an email."
  [conn db id pending]
  (let [user (d/pull db '[*] id)
        before (not-empty (edn/read-string pending))
        after (some-> (:orcpub.user/artist-credit user) artist-credit/read-stored)
        emails (when (not= before after)
                 (artist-email/credit-changed-tx {:artist-id (:orcpub.user/artist user) :user user
                                                  :before before :after after}))]
    (try @(d/transact conn (into [[:db/cas id :orcpub.user/artist-credit-pending pending ""]] emails))
         (boolean (seq emails))
         (catch Exception _ false))))

(defn flush-due!
  "Queue the email of every batch on `conn` whose window has ended, once each: the credit from
   before its first change to the credit now (none when the changes undid themselves). Returns how
   many were queued."
  [conn]
  (let [now (heartbeat/now)
        db (d/db conn)
        due (d/q '[:find ?u ?pending ?start
                   :where [?u :orcpub.user/artist-credit-pending ?pending]
                          [(not= ?pending "")]
                          [?u :orcpub.user/artist-credit-window ?start]]
                 db)
        n (count (filter (fn [[id pending start]]
                           (and (not (window-open? start now)) (send-batch! conn db id pending)))
                         (vec due)))]
    (when (pos? n) (@outbox/nudge))
    n))

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
