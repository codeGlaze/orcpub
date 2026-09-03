(ns orcpub.export-capacity-test
  "Covers the bound on how many character sheets are generated at once."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.config :as config]
            [orcpub.routes :as routes])
  (:import (java.util.concurrent Semaphore)))

(def ^:private with-export-slot #'routes/with-export-slot)
(def ^:private busy-page #'routes/busy-page)
(def ^:private attempt-count #'routes/attempt-count)
(def ^:private retry-after-seconds #'routes/retry-after-seconds)
(defn- slots
  "The live permit pool. The var holds a delay, so this derefs twice."
  ^Semaphore []
  @@#'routes/export-slots)

(deftest defaults-are-usable
  (testing "concurrency defaults to something a host can actually serve"
    (is (pos? (config/get-pdf-concurrency)))
    (is (>= (config/get-pdf-concurrency) 8)
        "at least eight so a small host still overlaps exports"))
  (testing "the queue wait is bounded"
    (is (pos? (config/get-pdf-queue-timeout-ms)))))

(deftest retry-after-is-a-usable-number
  (testing "never zero, or the client comes straight back into the same queue"
    (is (= 1 (retry-after-seconds 0)))
    (is (= 1 (retry-after-seconds 1))))
  (testing "capped, so a spike cannot tell someone to come back in an hour"
    (is (= 30 (retry-after-seconds 10000000))))
  (testing "rises with the queue ahead of the caller"
    (is (<= (retry-after-seconds 10) (retry-after-seconds 1000)))))

(deftest a-free-slot-runs-the-work
  (is (= {:status 200} (with-export-slot {} (fn [] {:status 200})))))

(deftest saturation-answers-503-rather-than-hanging
  (let [held (atom 0)]
    (try
      ;; Hold every slot so the next caller cannot get one.
      (dotimes [_ (config/get-pdf-concurrency)]
        (.acquire (slots))
        (swap! held inc))
      (with-redefs [config/get-pdf-queue-timeout-ms (constantly 50)]
        (let [ran (atom false)
              response (with-export-slot {} (fn [] (reset! ran true) {:status 200}))]
          (testing "the work is refused, not queued forever"
            (is (= 503 (:status response)))
            (is (false? @ran)))
          (testing "and says when to come back"
            (is (pos? (Integer/parseInt (get-in response [:headers "Retry-After"])))))))
      (finally
        (dotimes [_ @held] (.release (slots)))))))

(deftest a-slot-is-released-when-the-work-throws
  (let [before (.availablePermits (slots))]
    (is (thrown? Exception (with-export-slot {} (fn [] (throw (Exception. "boom"))))))
    (is (= before (.availablePermits (slots)))
        "a failed export must not leak its slot, or capacity bleeds away")))

(deftest attempt-count-cannot-be-gamed
  (testing "a fresh export has spent no retries"
    (is (= 0 (attempt-count {})))
    (is (= 0 (attempt-count {:form-params {}}))))
  (testing "the busy page's own counter is read back"
    (is (= 2 (attempt-count {:form-params {:retry "2"}}))))
  (testing "a hand-edited value cannot buy extra attempts"
    (is (= 0 (attempt-count {:form-params {:retry "-5"}})))
    (is (= 0 (attempt-count {:form-params {:retry "nonsense"}})))
    (is (= 0 (attempt-count {:form-params {:retry "999999999"}})))))

(deftest busy-page-retries-then-hands-over
  (let [render (fn [attempt]
                 (busy-page {:body "{:character-name \"T\"}"
                             :attempt attempt
                             :max-retries 3
                             :retry-seconds 4
                             :nonce "abc123"
                             :action "/character.pdf"}))]
    (testing "while retries remain the page counts down and carries the nonce"
      (let [html (render 0)]
        (is (.contains html "id=\"countdown\""))
        (is (.contains html "nonce=\"abc123\""))
        (is (.contains html "data-seconds=\"4\""))))
    (testing "the next attempt is numbered so the ladder advances"
      ;; Attribute order is the renderer's business, so match the field not the string.
      (is (re-find #"<input[^>]*name=\"retry\"[^>]*value=\"2\"" (render 1))))
    (testing "past the limit it stops retrying and waits to be clicked"
      (let [html (render 3)]
        (is (not (.contains html "id=\"countdown\"")))
        (is (not (.contains html "<script")))
        (is (.contains html "Try again"))))
    (testing "the original request is carried forward so the retry is the same export"
      (is (.contains (render 0) "character-name")))))

(deftest busy-page-escapes-the-body-it-reflects
  (testing "the request body is attacker-controlled and lands in an attribute"
    (let [html (busy-page {:body "\"><script>alert(1)</script>"
                           :attempt 3
                           :max-retries 3
                           :retry-seconds 1
                           :nonce nil
                           :action "/character.pdf"})]
      (is (not (.contains html "<script>alert(1)</script>")))
      (is (.contains html "&lt;script&gt;")))))
