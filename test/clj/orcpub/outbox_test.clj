(ns orcpub.outbox-test
  "Player email in the outbox: sent and deleted, retried after a temporary failure, given up on
   after a permanent one or too many, never sent twice by two workers, and taken back from a
   worker that died holding it."
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [orcpub.alerts :as alerts]
            [orcpub.db.schema :as schema]
            [orcpub.outbox :as outbox])
  (:import [java.util Date UUID]
           [javax.mail.internet InternetAddress]
           [com.sun.mail.smtp SMTPAddressFailedException]))

(defn- fresh-conn
  "A connection to a new in-memory database with the full schema."
  []
  (let [uri (str "datomic:mem://outbox-test-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      conn)))

(def ^:private message
  "A postal message with an HTML part, as the verification email is."
  {:from "Vault <no-reply@example.test>" :to "player@example.test" :subject "Verify"
   :body [{:type "text/html;charset=utf-8" :content "<p>Click <a href=\"https://x/verify?key=abc\">here</a></p>"}]})

(defn- queue!
  "Queues `message` as a :verification email in `conn`, with a user written in the same
   transaction, as registration does. Returns the user's entity id."
  [conn]
  (let [r @(d/transact conn [{:db/id "u" :orcpub.user/username "kaylee"} (outbox/email-tx :verification message)])]
    (get (:tempids r) "u")))

(defn- emails
  "Every email in the outbox of `conn`, as maps."
  [conn]
  (map #(d/pull (d/db conn) '[*] %)
       (d/q '[:find [?e ...] :where [?e :orcpub.outbox/id]] (d/db conn))))

(defn- at
  "A clock fixed `minutes` after `t0`."
  [^Date t0 minutes]
  #(Date. (long (+ (.getTime t0) (* minutes 60 1000)))))

(deftest a-queued-email-is-sent-and-deleted
  (let [conn (fresh-conn) sent (atom [])]
    (queue! conn)
    (is (= 1 (count (emails conn))) "written with the action that caused it")
    (is (= 1 (outbox/send-due! conn #(swap! sent conj %) #(Date.))))
    (is (= [message] @sent) "the message arrives as it was queued, HTML included")
    (is (empty? (emails conn)) "and is deleted once sent, links and all")))

(deftest a-failed-action-queues-no-email
  (let [conn (fresh-conn)]
    (is (thrown? Exception
                 @(d/transact conn [{:db/id "u" :orcpub.user/username 42} (outbox/email-tx :verification message)])))
    (is (empty? (emails conn)) "the email goes with its action or not at all")))

(deftest a-temporary-failure-is-retried-later
  (let [conn (fresh-conn) _ (queue! conn) t0 (Date.) tries (atom 0)
        down (fn [_] (swap! tries inc) (throw (javax.mail.MessagingException. "Connection refused")))]
    (is (zero? (outbox/send-due! conn down (at t0 0))))
    (let [[e] (emails conn)]
      (is (= :pending (:orcpub.outbox/status e)) "kept for another try")
      (is (= 1 (:orcpub.outbox/attempts e)))
      (is (re-find #"Connection refused" (:orcpub.outbox/last-error e))))
    (outbox/send-due! conn down (at t0 0.5))
    (is (= 1 @tries) "not retried before its gap")
    (is (= 1 (outbox/send-due! conn (constantly true) (at t0 2))) "sent once the gap has passed")
    (is (empty? (emails conn)))))

(deftest a-permanent-refusal-is-not-retried
  (let [conn (fresh-conn) raised (atom [])
        refused (fn [_] (throw (SMTPAddressFailedException.
                                (InternetAddress. "player@example.test") "RCPT TO" 550 "No such user")))]
    (queue! conn)
    (with-redefs [alerts/raise-current! (fn [k s _] (swap! raised conj [k s]))]
      (outbox/send-due! conn refused #(Date.)))
    (let [[e] (emails conn)]
      (is (= :failed (:orcpub.outbox/status e)) "a 5xx refusal is final")
      (is (= 1 (:orcpub.outbox/attempts e))))
    (is (= 1 (count @raised)) "and the admin is told")))

(deftest it-gives-up-after-the-last-attempt
  (let [conn (fresh-conn) t0 (Date.) raised (atom 0)
        down (fn [_] (throw (javax.mail.MessagingException. "timeout")))]
    (queue! conn)
    (with-redefs [alerts/raise-current! (fn [& _] (swap! raised inc))]
      (doseq [day (range 0 (* outbox/max-attempts 2))]
        (outbox/send-due! conn down (at t0 (* day 24 60)))))
    (let [[e] (emails conn)]
      (is (= :failed (:orcpub.outbox/status e)))
      (is (= outbox/max-attempts (:orcpub.outbox/attempts e))))
    (is (= 1 @raised) "one alert, when it gives up")))

(deftest two-workers-never-send-the-same-email
  (let [conn (fresh-conn) sends (atom {}) start (promise)]
    (dotimes [_ 5] (queue! conn))
    (let [slow (fn [m] @start (Thread/sleep 50) (swap! sends update (:subject m) (fnil inc 0)))
          workers (doall (repeatedly 2 #(future (outbox/send-due! conn slow (fn [] (Date.))))))]
      (deliver start true)
      (is (= 5 (reduce + (map deref workers))) "between them, each email sent once")
      (is (= {"Verify" 5} @sends)))
    (is (empty? (emails conn)))))

(deftest an-email-another-worker-already-sent-is-not-sent-again
  ;; The race two-workers-never-send-the-same-email can only hit by timing, staged on purpose:
  ;; worker B works from a view of the database taken before worker A sent and deleted the email.
  (let [conn (fresh-conn) _ (queue! conn) stale (d/db conn) sends (atom 0)
        real-db d/db]
    (is (= 1 (outbox/send-due! conn (fn [_] (swap! sends inc)) #(Date.))) "worker A sends it")
    (with-redefs [d/db (fn [c] (if (= c conn) stale (real-db c)))]
      (is (zero? (outbox/send-due! conn (fn [_] (swap! sends inc)) #(Date.)))
          "worker B, seeing it still due, cannot claim it"))
    (is (= 1 @sends) "sent once")))

(deftest a-claim-left-by-a-dead-worker-is-taken-back
  (let [conn (fresh-conn) t0 (Date.)
        [e] (do (queue! conn) (map :db/id (emails conn)))]
    @(d/transact conn [{:db/id e :orcpub.outbox/claimed-until (Date. (+ (.getTime t0) (* outbox/claim-minutes 60 1000)))}])
    (is (zero? (outbox/send-due! conn (constantly true) (at t0 1))) "held while the claim is live")
    (is (= 1 (outbox/send-due! conn (constantly true) (at t0 (inc outbox/claim-minutes))))
        "sent once the claim has run out")))

(deftest permanent-means-a-5xx-or-a-bad-address
  (is (outbox/permanent? (SMTPAddressFailedException. (InternetAddress. "a@b.test") "RCPT" 550 "no")))
  (is (not (outbox/permanent? (SMTPAddressFailedException. (InternetAddress. "a@b.test") "RCPT" 451 "later"))))
  (is (outbox/permanent? (javax.mail.internet.AddressException. "bad address")))
  (is (not (outbox/permanent? (javax.mail.MessagingException. "Connection refused")))))
