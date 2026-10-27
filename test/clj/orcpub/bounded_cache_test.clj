(ns orcpub.bounded-cache-test
  (:require [clojure.test :refer [deftest is]]
            [orcpub.bounded-cache :as bc]))

(deftest least-recently-used-goes-first
  (let [c (bc/cache {:max-entries 2})]
    (bc/put! c :a 1) (bc/put! c :b 2)
    (bc/lookup c :a)
    (bc/put! c :c 3)
    (is (= [1] (bc/lookup c :a)) "read recently, so kept")
    (is (nil? (bc/lookup c :b)) "the least recently used went")
    (is (= 2 (bc/size c)))))

(deftest entries-age-out-on-read
  (let [c (bc/cache {:max-entries 10 :max-age-ms 30})]
    (bc/put! c :a 1)
    (is (= [1] (bc/lookup c :a)))
    (Thread/sleep 60)
    (is (nil? (bc/lookup c :a)) "aged entries are not served, even with nothing added since")
    (is (= 2 (bc/fetch! c :a (constantly 2))) "and are worked out again")))

(deftest a-cached-nil-is-an-answer
  (let [c (bc/cache {:max-entries 4}) calls (atom 0)]
    (bc/fetch! c :k #(do (swap! calls inc) nil))
    (bc/fetch! c :k #(do (swap! calls inc) nil))
    (is (= 1 @calls))
    (is (= [nil] (bc/lookup c :k)))))

(deftest concurrent-misses-share-one-computation
  (let [c (bc/cache {:max-entries 4}) calls (atom 0)
        rs (mapv deref (doall (repeatedly 10 #(future (bc/fetch! c :k (fn [] (swap! calls inc) (Thread/sleep 100) :v))))))]
    (is (= 1 @calls))
    (is (every? #{:v} rs))))

(deftest a-failure-is-not-kept
  (let [c (bc/cache {:max-entries 4})]
    (is (thrown? Exception (bc/fetch! c :k #(throw (Exception. "boom")))))
    (is (nil? (bc/lookup c :k)))
    (is (= :ok (bc/fetch! c :k (constantly :ok))) "the next caller tries again")))
