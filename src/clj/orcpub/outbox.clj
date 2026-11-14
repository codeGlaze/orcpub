(ns orcpub.outbox
  "Player email that is never lost to a busy mail server. An email is written to the database in the
   same transaction as the action that causes it (`email-tx`), and one worker sends what is due,
   retrying with growing gaps, giving up on a permanent refusal or after `max-attempts`, and
   deleting each email once sent. Claims are compare-and-set, so two servers never send the same
   email. plan-email-and-alerts.md on agents/develop."
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [orcpub.alerts :as alerts])
  (:import [java.util Date UUID]
           [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(def retry-minutes
  "Minutes to wait before each retry, by attempt: about 1 minute, 5, 30, 2 hours, then 6."
  [1 5 30 120 360])

(def max-attempts
  "Sends tried before an email is marked failed and an admin alert is raised: about a day of retries."
  8)

(def claim-minutes
  "How long a claim holds before another worker may take the email back: longer than any send."
  10)

(def batch-size
  "Emails one pass of the worker sends at most."
  10)

(defn- log!
  "Prints `msg` as one log line prefixed `outbox:`. Returns nil."
  [msg]
  (println (str "outbox: " msg)))

(defn email-tx
  "Transaction data that queues postal message `message` (a map with :to, :subject, :body …) as
   an email of `kind` (a keyword naming it, like :verification), due now. Add it to the transaction
   of the action that causes the email."
  [kind message]
  {:db/id (str "outbox-" (UUID/randomUUID))
   :orcpub.outbox/id (UUID/randomUUID)
   :orcpub.outbox/kind kind
   :orcpub.outbox/to (str (:to message))
   :orcpub.outbox/message (pr-str message)
   :orcpub.outbox/status :pending
   :orcpub.outbox/attempts 0
   :orcpub.outbox/created (Date.)
   :orcpub.outbox/next-at (Date.)})

(defonce ^{:doc "Runs the worker now; set by the Outbox component, a no-op when it is not started."}
  nudge
  (atom (constantly nil)))

(defonce ^{:doc "The database connection of the running Outbox component, or nil when it is not started."}
  current-conn
  (atom nil))

(defn enqueue!
  "Queues postal message `message` as an email of `kind` on its own and asks the worker to send it
   now, for an email no other write goes with. Returns the transaction result."
  [conn kind message]
  (let [result @(d/transact conn [(email-tx kind message)])]
    (@nudge)
    result))

(defn enqueue-current!
  "`enqueue!` on the running outbox's connection, for code with no connection at hand. Returns true
  when the email was queued, false when no outbox is running or the write failed."
  [kind message]
  (if-let [conn @current-conn]
    (try (enqueue! conn kind message) true
         (catch Exception e (log! (str "could not queue a " (name kind) " email: " (.getMessage e))) false))
    (do (log! (str "not running; a " (name kind) " email was not queued")) false)))

(defn sent!
  "Asks the worker to send what was just queued with `email-tx`. Call after the transaction.
   Returns nil."
  []
  (@nudge)
  nil)

(defn retry-delay-ms
  "Milliseconds before the retry after failed attempt number `attempts` (1-based), with up to 20%
   random spread so many emails failing together do not retry together."
  [attempts]
  (let [minutes (nth retry-minutes (min (dec attempts) (dec (count retry-minutes))))
        base (* minutes 60 1000)]
    (long (+ base (* base 0.2 (rand))))))

(defn- reply-code
  "The SMTP reply code in exception `e` or any of its causes, or nil."
  [^Throwable e]
  (some (fn [^Throwable x]
          (when-let [m (some #(when (= "getReturnCode" (.getName ^java.lang.reflect.Method %)) %)
                             (.getMethods (class x)))]
            (let [code (.invoke ^java.lang.reflect.Method m x (object-array 0))]
              (when (and (number? code) (pos? code)) code))))
        (take-while some? (iterate #(.getCause ^Throwable %) e))))

(defn permanent?
  "Whether send failure `e` will not go away on retry: the mail server refused with a 5xx reply, or
   the address is malformed."
  [^Throwable e]
  (boolean
   (or (some-> (reply-code e) (>= 500))
       (some #(= "javax.mail.internet.AddressException" (.getName (class %)))
             (take-while some? (iterate #(.getCause ^Throwable %) e))))))

(defn due
  "Entity ids of up to `n` emails due at `now`: pending, their time come, and not claimed by a live
   claim."
  [db now n]
  (->> (d/q '[:find ?e ?next
              :in $ ?now
              :where [?e :orcpub.outbox/status :pending]
                     [?e :orcpub.outbox/next-at ?next]
                     [(<= ?next ?now)]
                     [(get-else $ ?e :orcpub.outbox/claimed-until #inst "1970-01-01") ?claim]
                     [(< ?claim ?now)]]
            db now)
       (sort-by second)
       (take n)
       (map first)))

(defn- claim!
  "Claims email `e` until `until`. Compare-and-set on its claim as read in `db`, and on its status
   still being :pending, so only one worker wins and an email another worker has already sent and
   deleted cannot be claimed. Returns true when this worker holds it, false when another does;
   throws any other failure (the database not answering)."
  [conn db e ^Date until]
  (let [current (:orcpub.outbox/claimed-until (d/entity db e))]
    (try
      @(d/transact conn [[:db/cas e :orcpub.outbox/status :pending :pending]
                         [:db/cas e :orcpub.outbox/claimed-until current until]])
      true
      (catch Exception e
        (if (some #(= :db.error/cas-failed (:db/error (ex-data %))) (take-while some? (iterate ex-cause e)))
          false
          (throw e))))))

(defn- fail!
  "Records failed attempt `ex` on email `ent` at `now`: retried later, or marked failed with an
   admin alert when permanent or at `max-attempts`. Returns nil."
  [conn ent ^Throwable ex now]
  (let [id (:db/id ent)
        attempts (inc (or (:orcpub.outbox/attempts ent) 0))
        why (str (.getName (class ex)) ": " (.getMessage ex))
        give-up? (or (permanent? ex) (>= attempts max-attempts))]
    @(d/transact conn (remove nil? [{:db/id id
                        :orcpub.outbox/attempts attempts
                        :orcpub.outbox/last-error (subs why 0 (min 500 (count why)))
                        :orcpub.outbox/status (if give-up? :failed :pending)
                        :orcpub.outbox/next-at (Date. (+ (.getTime ^Date now) (retry-delay-ms attempts)))}
                       (when-let [c (:orcpub.outbox/claimed-until ent)]
                         [:db/retract id :orcpub.outbox/claimed-until c])]))
    (if give-up?
      (do (log! (str "gave up on a " (name (:orcpub.outbox/kind ent)) " email after " attempts
                     " attempt(s): " why))
          (alerts/raise-current! (str "outbox-failed:" (name (:orcpub.outbox/kind ent)))
                                 (str "A " (name (:orcpub.outbox/kind ent)) " email could not be sent")
                                 (str "A player email was given up on after " attempts " attempt(s).\n"
                                      "Kind: " (name (:orcpub.outbox/kind ent)) "\nLast error: " why
                                      "\n\nIt is kept in the database with status :failed.")))
      (log! (str "a " (name (:orcpub.outbox/kind ent)) " email will be retried (attempt " attempts
                 "): " why)))
    nil))

(defn send-due!
  "One pass of the worker: claims and sends up to `batch-size` due emails with `send!` (a function
   of a postal message that returns when the server took it and throws when it did not), deleting
   each sent one (one that cannot be deleted is logged and sent again once its claim runs out).
   `now` is a function returning the current Date. Returns the number sent; throws when the
   database cannot be updated."
  [conn send! now]
  (let [t (now)
        db (d/db conn)
        until (Date. (+ (.getTime ^Date t) (* claim-minutes 60 1000)))]
    (reduce
     (fn [sent e]
       (if-not (claim! conn db e until)
         sent
         (let [ent (d/entity (d/db conn) e)]
           (if-let [ex (try (send! (edn/read-string (:orcpub.outbox/message ent))) nil
                            (catch Exception ex ex))]
             (do (fail! conn (d/touch ent) ex t) sent)
             (do (try @(d/transact conn [[:db/retractEntity e]])
                      (catch Exception ex
                        (log! (str "an email was sent but could not be deleted; it may be sent again: "
                                   (.getMessage ex)))))
                 (inc sent))))))
     0
     (due db t batch-size))))

(defn- pass!
  "Runs `send-due!` and keeps the worker alive through any failure, raising an admin alert while
   the database cannot be read and resolving it when it can. Returns nil."
  [conn send!]
  (try
    (send-due! conn send! #(Date.))
    (alerts/resolve-current! "outbox-database" "Player emails cannot be read from the database")
    (catch Throwable t
      (alerts/raise-current! "outbox-database" "Player emails cannot be read from the database"
                             (str "The email worker could not read or update the outbox: " t
                                  "\nEmails wait in the database and are sent once it answers."))))
  nil)

(defrecord Outbox [conn send! executor]
  component/Lifecycle
  (start [this]
    (reset! current-conn (:conn conn))
    (if-not send!
      (do (log! "no mail server configured; player emails wait in the database until one is") this)
      (let [ex (Executors/newSingleThreadScheduledExecutor)
            c (:conn conn)
            run #(pass! c send!)]
        (reset! nudge #(.execute ex ^Runnable run))
        (.scheduleWithFixedDelay ex ^Runnable run 5 30 TimeUnit/SECONDS)
        (assoc this :executor ex))))
  (stop [this]
    (reset! nudge (constantly nil))
    (reset! current-conn nil)
    (when executor
      (.shutdown ^ScheduledExecutorService executor)
      (.awaitTermination ^ScheduledExecutorService executor 10 TimeUnit/SECONDS))
    (assoc this :executor nil)))

(defn new-outbox
  "The Outbox component, sending with `send!` (nil when no mail server is configured: emails stay
   queued). Needs :conn."
  [send!]
  (map->Outbox {:send! send!}))

(defn summary
  "Counts of queued emails by status in `db`, for logs and tests: {:pending n :failed n}."
  [db]
  (into {} (d/q '[:find ?status (count ?e)
                  :where [?e :orcpub.outbox/status ?status]]
                db)))
