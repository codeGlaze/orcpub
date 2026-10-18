(ns orcpub.work-pool-test
  (:require [clojure.test :refer [deftest is]]
            [orcpub.work-pool :as wp]))

(defn- pool [n timeout] (wp/pool {:size (constantly n) :timeout-ms (constantly timeout) :seed-ms 100}))

(deftest runs-and-releases
  (let [p (pool 1 50)]
    (is (= {:result 3} (wp/run p (constantly 3))))
    (is (thrown? Exception (wp/run p #(throw (Exception. "boom")))))
    (is (= 1 (.availablePermits (wp/permits p))) "a failure does not leak the permit")
    (is (zero? (wp/queue-depth p)))))

(deftest a-full-pool-turns-callers-away-with-a-wait
  (let [p (pool 1 30)
        started (promise) release (promise)
        holder (future (wp/run p #(do (deliver started true) @release)))]
    @started
    (let [{:keys [busy result]} (wp/run p (constantly :never))]
      (is (nil? result))
      (is (<= 1 busy 30) "Retry-After is whole seconds, never zero, at most 30"))
    (deliver release true) @holder))

(deftest retry-after-rises-with-the-queue
  (let [p (pool 2 0)]
    (is (= 1 (wp/retry-after-seconds p 0)))
    (is (<= (wp/retry-after-seconds p 10) (wp/retry-after-seconds p 1000)))
    (is (= 30 (wp/retry-after-seconds p 10000000)))))

(deftest a-running-job-is-not-counted-as-waiting
  ;; queue-depth and the Retry-After estimate are about callers still waiting for a permit
  (let [p (pool 1 1000)
        started (promise) release (promise)
        holder (future (wp/run p #(do (deliver started true) @release)))]
    @started
    (is (zero? (wp/queue-depth p)) "one job running, nobody waiting")
    (deliver release true) @holder))
