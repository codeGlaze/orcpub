(ns orcpub.alerts-test
  "Admin alerts: one email per problem, a cap, a pause when mail fails, a bounded spool that
   survives a restart, and nothing that needs the database."
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.alerts :as alerts]))

(defn- alerter
  "An inline alerter on a fake clock `clock` (an atom of epoch ms) that records sends in `sent`
   (an atom of messages); `ok?` (an atom) says whether the mail server accepts. `opts` override."
  [clock sent ok? & [opts]]
  (alerts/new-alerter (merge {:now #(deref clock)
                              :send! (fn [m] (if @ok? (do (swap! sent conj m) true) (throw (Exception. "SMTP down"))))
                              :executor nil}
                             opts)))

(defn- minutes
  "`n` minutes in ms."
  [n]
  (* n 60 1000))

(defn- temp-spool
  "A spool path in a fresh temp directory."
  []
  (str (io/file (str (System/getProperty "java.io.tmpdir") "/alerts-test-" (random-uuid)) "spool.edn")))

(deftest the-same-problem-is-one-email-with-a-count
  (let [clock (atom 0) sent (atom []) ok? (atom true) a (alerter clock sent ok?)]
    (dotimes [_ 300] (alerts/raise! a "NPE+routes/save" "NullPointerException @ POST /save" "trace"))
    (is (= 1 (count @sent)) "300 of the same error send one email at once")
    (alerts/tick! a)
    (is (= 1 (count @sent)) "and nothing more before the window ends")
    (reset! clock (minutes 15))
    (alerts/tick! a)
    (is (= 2 (count @sent)) "then one summary")
    (is (re-find #"299 more times" (:subject (last @sent))) (:subject (last @sent)))
    (alerts/tick! a)
    (is (= 2 (count @sent)) "and no summary while it is quiet")))

(deftest different-problems-each-send
  (let [clock (atom 0) sent (atom []) ok? (atom true) a (alerter clock sent ok?)]
    (alerts/raise! a "a" "A" "")
    (alerts/raise! a "b" "B" "")
    (is (= ["A" "B"] (map :subject @sent)))))

(deftest the-hourly-cap-holds-and-is-reported
  (let [clock (atom 0) sent (atom []) ok? (atom true) a (alerter clock sent ok? {:max-per-hour 3})]
    (doseq [k ["a" "b" "c" "d" "e"]] (alerts/raise! a k k ""))
    (is (= 3 (count @sent)) "past the cap, nothing is emailed")
    (reset! clock (+ (minutes 61)))
    (alerts/raise! a "f" "f" "")
    (is (= 4 (count @sent)) "an hour later sending resumes")
    (is (re-find #"2 alert\(s\) were not emailed" (:body (last @sent))) "and says what the cap held back")))

(deftest a-failing-mail-server-spools-then-pauses
  (let [clock (atom 0) sent (atom []) ok? (atom false) attempts (atom 0)
        a (alerter clock sent ok? {:send! (fn [m] (swap! attempts inc)
                                            (if @ok? (do (swap! sent conj m) true) (throw (Exception. "down"))))})]
    (doseq [k ["a" "b" "c" "d" "e"]] (alerts/raise! a k k ""))
    (is (= 3 @attempts) "after 3 failures in a row, sending pauses")
    (is (= 5 (count (:spool @(:state a)))) "every alert waits in the spool")
    (reset! ok? true)
    (alerts/tick! a)
    (is (empty? @sent) "nothing is retried while paused")
    (reset! clock (minutes 5))
    (alerts/tick! a)
    (is (= ["a" "b" "c" "d" "e"] (map :subject @sent)) "after the pause the spool is sent, oldest first")
    (is (empty? (:spool @(:state a))))))

(deftest a-full-spool-drops-the-oldest-and-says-so
  (let [clock (atom 0) sent (atom []) ok? (atom false)
        a (alerter clock sent ok? {:spool-max 3 :breaker-failures 100})]
    (doseq [k ["a" "b" "c" "d" "e"]] (alerts/raise! a k k ""))
    (is (= ["c" "d" "e"] (map :subject (:spool @(:state a)))))
    (reset! ok? true)
    (alerts/tick! a)
    (is (re-find #"2 older alert\(s\) were dropped" (:body (first @sent))) (:body (first @sent)))))

(deftest the-spool-survives-a-restart
  (let [f (temp-spool) clock (atom 0) sent (atom []) ok? (atom false)
        a (alerter clock sent ok? {:spool-file f})]
    (alerts/raise! a "a" "A" "body")
    (is (.exists (io/file f)) "written to disk")
    (reset! ok? true)
    (let [b (alerter clock sent ok? {:spool-file f})]
      (alerts/tick! b)
      (is (= ["A"] (map :subject @sent)) "a new server sends what the last one could not")
      (is (not (.exists (io/file f))) "and the emptied spool is removed"))))

(deftest shutdown-sends-the-spool-even-while-paused
  (let [clock (atom 0) sent (atom []) ok? (atom false) a (alerter clock sent ok?)]
    (doseq [k ["a" "b" "c"]] (alerts/raise! a k k ""))
    (reset! ok? true)
    (is (zero? (alerts/flush! a 8000)) "nothing left")
    (is (= 3 (count @sent)))))

(deftest recovered-only-for-a-problem-that-was-reported
  (let [clock (atom 0) sent (atom []) ok? (atom true) a (alerter clock sent ok?)]
    (alerts/resolve! a "db" "Database unreachable")
    (is (empty? @sent) "nothing to recover from")
    (alerts/raise! a "db" "Database unreachable" "")
    (alerts/resolve! a "db" "Database unreachable")
    (is (= ["Database unreachable" "Recovered: Database unreachable"] (map :subject @sent)))))

(deftest raising-never-throws
  (let [a (alerts/new-alerter {:send! (fn [_] (throw (Error. "boom"))) :executor nil})]
    (is (nil? (alerts/raise! a "x" "X" "")))))

(deftest every-alert-is-logged-once-per-window
  (let [clock (atom 0)
        a (alerts/new-alerter {:send! (constantly true) :executor nil :now #(deref clock)})
        out (with-out-str (dotimes [_ 300] (alerts/raise! a "k" "Something broke" "")))]
    (is (= 1 (count (re-seq #"Something broke" out))) "the first is logged, not all 300")
    (reset! clock (minutes 15))
    (is (re-find #"Something broke: 299 more times" (with-out-str (alerts/tick! a)))
        "and the repeats are logged as one count")))
