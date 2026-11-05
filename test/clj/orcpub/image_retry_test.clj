(ns orcpub.image-retry-test
  "One retry, for the two failures where a second go is both plausible and cheap.

   An image fetch holds an export slot and has a 20s transfer deadline, so retrying is
   not free: it is slot time taken from everyone else. That budget, not the taxonomy,
   is what decides which reasons are worth a second attempt."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.pdf :as pdf]))

(defn- attempts-for
  "How many times a fetch is tried when every attempt fails with `ex`, and the outcome."
  [ex]
  (let [n (atom 0)]
    (with-redefs [pdf/safe-image-bytes (fn [_] (swap! n inc) (throw ex))]
      (let [out (pdf/fetch-image-outcome "https://example.test/x.png")]
        [@n (:reason out)]))))

(deftest transient-failures-are-retried-once
  (testing "a connect blip and a 5xx fail fast, so a second attempt is cheap"
    (is (= [2 :unreachable] (attempts-for (java.net.ConnectException. "refused"))))
    (is (= [2 :unreachable] (attempts-for (java.net.UnknownHostException. "no dns"))))))

(deftest a-timeout-is-not-retried
  (testing "it already spent the whole transfer deadline holding an export slot.

            Retrying doubles the worst case for a case that usually fails the same way
            twice -- the budget is the reason, not the taxonomy."
    (is (= [1 :timeout] (attempts-for (java.net.SocketTimeoutException. "slow"))))))

(deftest permanent-failures-are-not-retried
  (testing "a 404 is a 404 twice, and a refusal is not reconsidered"
    (is (= [1 :unknown] (attempts-for (IllegalStateException. "broken"))))))

(deftest a-retry-that-succeeds-returns-the-image
  (testing "the first attempt fails transiently, the second works"
    (let [n (atom 0)]
      (with-redefs [pdf/safe-image-bytes
                    (fn [_] (if (zero? (first (swap-vals! n inc)))
                              (throw (java.net.ConnectException. "refused"))
                              {:data (byte-array 10) :jpg? false}))
                    pdf/fit-for-sheet identity]
        (let [out (pdf/fetch-image-outcome "https://example.test/x.png")]
          (is (= 2 @n) "tried twice")
          (is (some? (:image out)) "and the second attempt's image is returned")
          (is (nil? (:reason out))))))))
