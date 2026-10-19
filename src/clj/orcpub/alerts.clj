(ns orcpub.alerts
  "Admin alerts that never depend on the database: one email per problem with a count of repeats,
   an hourly cap, a pause after repeated mail failures, and a bounded spool file for what could not
   be sent, retried each minute and on shutdown. Every alert is also logged. All state changes run
   on one thread. plan-email-and-alerts.md on agents/develop."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [com.stuartsierra.component :as component])
  (:import [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(def defaults
  "Settings an alerter starts from; `new-alerter` takes overrides."
  {:repeat-window-ms (* 15 60 1000)
   :max-per-hour 20
   :breaker-failures 3
   :breaker-pause-ms (* 5 60 1000)
   :spool-max 200})

(defn- log!
  "Prints `msg` as one log line prefixed `alerts:`. Returns nil."
  [msg]
  (println (str "alerts: " msg)))

(defn- read-spool
  "The alerts stored in spool file `f`, oldest first; [] when it is missing or unreadable."
  [f]
  (try
    (if (and f (.exists (io/file f)))
      (vec (keep #(when-not (s/blank? %) (edn/read-string %)) (s/split-lines (slurp f))))
      [])
    (catch Exception e (log! (str "could not read the spool " f ": " (.getMessage e))) [])))

(defn- write-spool!
  "Replaces spool file `f` with `messages`, one EDN map per line; deletes it when empty. Returns nil."
  [f messages]
  (when f
    (try
      (if (seq messages)
        (do (io/make-parents f)
            (spit f (s/join "\n" (map pr-str messages))))
        (io/delete-file f true))
      (catch Exception e (log! (str "could not write the spool " f ": " (.getMessage e)))))
    nil))

(defn new-alerter
  "An alerter. `opts`: :send! (a function of a message map {:subject :body}, truthy when sent,
   may throw; nil to only log), :now (a function returning epoch ms), :spool-file (path, or nil for none),
   :executor (a single-thread ScheduledExecutorService, or nil to run every step inline), and any
   key of `defaults`. Loads what the spool file already holds."
  [opts]
  (let [opts (merge defaults {:now #(System/currentTimeMillis)} opts)]
    (assoc opts :state (atom {:kinds {} :sent [] :failures 0 :open-until 0 :capped 0
                              :dropped 0 :spool (read-spool (:spool-file opts))}))))

(defn- on-thread!
  "Runs `f` on the alerter's thread, or now when it has no executor. Never throws. Returns nil."
  [{:keys [^ScheduledExecutorService executor]} f]
  (let [safe #(try (f) (catch Throwable t (log! (str "internal error: " t))))]
    (if executor (.execute executor ^Runnable safe) (safe))
    nil))

(defn- notes
  "The lines a delivered message adds for alerts the cap skipped and the spool dropped, given
   state `st`; empty when there were none."
  [{:keys [capped dropped]}]
  (cond-> []
    (pos? capped) (conj (str capped " alert(s) were not emailed: the hourly cap was reached. See the server log."))
    (pos? dropped) (conj (str dropped " older alert(s) were dropped from a full spool. See the server log."))))

(defn- spool!
  "Adds `message` to the spool, dropping the oldest past :spool-max and counting them. Returns nil."
  [{:keys [state spool-file spool-max]} message]
  (let [{:keys [spool]} (swap! state (fn [st]
                                       (let [all (conj (:spool st) message)
                                             over (max 0 (- (count all) spool-max))]
                                         (-> st (assoc :spool (vec (drop over all)))
                                             (update :dropped + over)))))]
    (write-spool! spool-file spool)))

(defn- attempt!
  "Tries to send `message` now. On success records the send and clears the failure count; on
   failure counts it and, at :breaker-failures in a row, pauses sending for :breaker-pause-ms.
   Returns whether it was sent."
  [{:keys [state send! now breaker-failures breaker-pause-ms]} message]
  (let [st @state
        message (update message :body #(s/join "\n\n" (cons % (notes st))))
        sent? (try (boolean (send! message))
                   (catch Throwable t (log! (str "send failed: " (.getMessage t))) false))
        t (now)]
    (if sent?
      (swap! state #(-> % (update :sent conj t) (assoc :failures 0 :capped 0 :dropped 0)))
      (let [{:keys [failures]} (swap! state update :failures inc)]
        (when (>= failures breaker-failures)
          (swap! state assoc :open-until (+ t breaker-pause-ms) :failures 0)
          (log! (str "sending paused for " (quot breaker-pause-ms 60000) " min after "
                     breaker-failures " failures in a row")))))
    sent?))

(defn- deliver!
  "Sends `message`, or logs it when the hourly cap is reached, or spools it when sending is paused
   or fails. With no :send! (no channel configured) the log line is all there is. Returns nil."
  [{:keys [state now max-per-hour] :as alerter} message]
  (let [t (now)
        {:keys [sent open-until]} (swap! state update :sent (fn [ts] (filterv #(> % (- t 3600000)) ts)))]
    (cond
      (nil? (:send! alerter)) nil
      (>= (count sent) max-per-hour)
      (do (swap! state update :capped inc)
          (log! (str "not emailed, hourly cap of " max-per-hour " reached: " (:subject message))))
      (< t open-until) (spool! alerter message)
      (not (attempt! alerter message)) (spool! alerter message))
    nil))

(defn raise!
  "Reports problem `kind` (a string naming it, like an error's class and place) with `subject` and
   `body`. Logs and emails it, unless the same kind was reported within :repeat-window-ms: then it
   is only counted, for the next summary line and email. Never blocks or throws. Returns nil."
  [{:keys [state now repeat-window-ms] :as alerter} kind subject body]
  (on-thread! alerter
        (fn []
          (let [t (now)
                last-sent (get-in @state [:kinds kind :last-sent])]
            (if (and last-sent (< (- t last-sent) repeat-window-ms))
              (swap! state update-in [:kinds kind :repeats] (fnil inc 0))
              (do (log! (str "[" kind "] " subject))
                  (swap! state update-in [:kinds kind] assoc :last-sent t :subject subject :repeats 0)
                  (deliver! alerter {:kind kind :subject subject :body body :at t})))))))

(defn resolve!
  "Reports that problem `kind` has ended. Emails `subject` as recovered only when that kind was
   reported before; forgets the kind either way. Returns nil."
  [{:keys [state now] :as alerter} kind subject]
  (on-thread! alerter
        (fn []
          (when (get-in @state [:kinds kind])
            (swap! state update :kinds dissoc kind)
            (log! (str "[" kind "] recovered: " subject))
            (deliver! alerter {:kind kind :subject (str "Recovered: " subject)
                               :body (str subject " has recovered.") :at (now)})))))

(defn- send-spool!
  "Sends spooled alerts oldest first, stopping at the first failure or once `deadline` (epoch ms)
   passes; with `respect-pause?` does nothing while sending is paused. Returns nil."
  [{:keys [state now spool-file] :as alerter} respect-pause? deadline]
  (loop []
    (let [{:keys [spool open-until]} @state
          t (now)]
      (when (and (seq spool) (< t deadline) (or (not respect-pause?) (>= t open-until)))
        (when (attempt! alerter (first spool))
          (let [{:keys [spool]} (swap! state update :spool #(vec (rest %)))]
            (write-spool! spool-file spool))
          (recur))))))

(defn tick!
  "The once-a-minute step: emails one summary for each kind that repeated after its last email
   once :repeat-window-ms has passed, then retries the spool unless sending is paused. Returns nil."
  [{:keys [state now repeat-window-ms] :as alerter}]
  (on-thread! alerter
        (fn []
          (let [t (now)]
            (doseq [[kind {:keys [last-sent repeats subject]}] (:kinds @state)
                    :when (and (pos? (or repeats 0)) (>= (- t last-sent) repeat-window-ms))]
              (swap! state update-in [:kinds kind] assoc :last-sent t :repeats 0)
              (log! (str "[" kind "] " subject ": " repeats " more times"))
              (deliver! alerter {:kind kind :at t
                                 :subject (str subject " (" repeats " more times)")
                                 :body (str subject "\n\nHappened " repeats " more times in the last "
                                            (quot repeat-window-ms 60000) " minutes.")}))
            (send-spool! alerter true Long/MAX_VALUE)))))

(defn flush!
  "Sends what the spool holds, even while sending is paused, until `budget-ms` has passed; for
   shutdown. Runs on the calling thread. Returns the number of alerts still spooled."
  [{:keys [state now] :as alerter} budget-ms]
  (send-spool! alerter false (+ (now) budget-ms))
  (count (:spool @state)))

(defonce ^{:doc "The running alerter, set by the Alerts component; nil when it is not started."}
  current
  (atom nil))

(defn raise-current!
  "`raise!` on the running alerter; only logs when none is started (tests, a REPL). Returns nil."
  [kind subject body]
  (if-let [a @current]
    (raise! a kind subject body)
    (log! (str "[" kind "] " subject " (alerts not started, logged only)"))))

(defn- shutdown!
  "Stops the alerter's thread, then sends what the spool holds within `budget-ms`, logging what is
   left for the next start. Returns nil."
  [{:keys [^ScheduledExecutorService executor] :as alerter} budget-ms]
  (when executor
    (.shutdown executor)
    (.awaitTermination executor 2 TimeUnit/SECONDS))
  (let [left (flush! alerter budget-ms)]
    (when (pos? left) (log! (str left " alert(s) left in the spool for the next start"))))
  nil)

(defrecord Alerts [opts alerter hook]
  component/Lifecycle
  (start [this]
    (let [executor (Executors/newSingleThreadScheduledExecutor)
          alerter (new-alerter (assoc opts :executor executor))
          hook (Thread. ^Runnable #(shutdown! alerter 8000))]
      (.scheduleWithFixedDelay executor ^Runnable #(tick! alerter) 1 1 TimeUnit/MINUTES)
      (.addShutdownHook (Runtime/getRuntime) hook)
      (reset! current alerter)
      (log! (if (:send! opts)
              (str "emailed to " (:to opts) ", unsent kept in " (:spool-file opts))
              "logged only: set EMAIL_ERRORS_TO and EMAIL_SERVER_URL to email them"))
      (tick! alerter)
      (assoc this :alerter alerter :hook hook)))
  (stop [this]
    (when hook
      (try (.removeShutdownHook (Runtime/getRuntime) hook) (catch IllegalStateException _ nil)))
    (when alerter
      (compare-and-set! current alerter nil)
      (shutdown! alerter 8000))
    (assoc this :alerter nil :hook nil)))

(defn new-alerts
  "The Alerts component, from `new-alerter` options plus :to (who is emailed, for the start line)."
  [opts]
  (map->Alerts {:opts opts}))
