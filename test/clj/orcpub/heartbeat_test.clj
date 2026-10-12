;; The heartbeat: what counts as an outage, how running time leaves outages out, and the order of a tick.
(ns orcpub.heartbeat-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [orcpub.heartbeat :as heartbeat]
            [orcpub.db.schema :as schema])
  (:import [java.util Date UUID]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:heartbeat-test-" (UUID/randomUUID))
         ~conn-binding (do
                         (d/create-database uri#)
                         (d/connect uri#))]
     (try @(d/transact ~conn-binding schema/all-schemas)
          ~@body
          (finally (d/delete-database uri#)))))

(defn- minutes-later [^Date d n] (Date. (+ (.getTime d) (* n 60 1000))))

(defn- days-later [^Date d n] (minutes-later d (* n 24 60)))

(def ^:private day-ms (* 24 60 60 1000))

(defn- beat-at! [conn t] (with-redefs [heartbeat/now (constantly t)] (heartbeat/beat! conn)))

(deftest a-gap-between-beats-is-an-outage
  (with-conn conn
    (let [t0      (Date.)
          outages #(set (map vec (heartbeat/outages (d/db conn))))]
      (beat-at! conn t0)
      (beat-at! conn (minutes-later t0 60))
      (beat-at! conn (minutes-later t0 125))
      (is (empty? (outages)) "beats about an hour apart")
      (is (= (minutes-later t0 125) (heartbeat/last-beat (d/db conn))))
      (beat-at! conn (days-later t0 3))
      (is (= #{[(minutes-later t0 125) (days-later t0 3)]} (outages)) "three days without a beat")
      (beat-at! conn (days-later t0 1))
      (is (= 1 (count (outages))) "a clock set back is not an outage"))))

(deftest running-time-leaves-out-outages
  (let [t0      (Date. 0)
        outages #{[(days-later t0 10) (days-later t0 100)]}]
    (is (= (* 185 day-ms) (heartbeat/running-ms outages t0 (days-later t0 275))))
    (is (= (* 5 day-ms) (heartbeat/running-ms outages t0 (days-later t0 5))) "all before the outage")
    (is (= (* 20 day-ms) (heartbeat/running-ms outages (days-later t0 50) (days-later t0 120))) "from inside it")
    (is (neg? (heartbeat/running-ms outages (days-later t0 5) t0)) "a start after the end")))

(deftest a-tick-beats-first-and-jobs-wait-when-it-cannot
  (with-conn conn
    (let [ran (atom #{})]
      (heartbeat/tick conn {"first"  (fn [c] (swap! ran conj [:first (some? (heartbeat/last-beat (d/db c)))]))
                            "broken" (fn [_] (throw (ex-info "a broken job" {})))
                            "last"   (fn [_] (swap! ran conj [:last]))})
      (is (= #{[:first true] [:last]} @ran) "jobs run after the beat, and one failing stops no other")
      (reset! ran #{})
      (with-redefs [heartbeat/beat! (fn [_] (throw (ex-info "the database is down" {})))]
        (heartbeat/tick conn {"job" (fn [_] (swap! ran conj :ran))}))
      (is (empty? @ran) "no beat, no jobs"))))
