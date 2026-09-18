(ns orcpub.pwned-test
  "The breach check is advisory. These tests care less about the happy path than
   about every way the service can misbehave, because none of them may stop
   someone signing up."
  (:require [clojure.test :refer [deftest testing is]]
            [clj-http.client :as client]
            [orcpub.pwned :as pwned]))

;; "password" -> 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
(def ^:private password-suffix "1E4C9B93F3F0682250B6CF8331B7EE68FD8")

(defn- responding [body & [status]]
  (fn [_ _] {:status (or status 200) :body body}))

(deftest counts-a-known-password
  (with-redefs [client/get (responding (str password-suffix ":52372427\nAAAAAAA:3"))]
    (is (= 52372427 (pwned/check "password")))
    (is (true? (pwned/breached? "password")))))

(deftest absent-password-scores-zero
  (with-redefs [client/get (responding "AAAAAAA:3\nBBBBBBB:9")]
    (is (= 0 (pwned/check "password")))
    (is (false? (pwned/breached? "password")))))

(deftest suffix-match-is-case-insensitive
  (with-redefs [client/get (responding (str (clojure.string/lower-case password-suffix) ":7"))]
    (is (= 7 (pwned/check "password")))))

(deftest every-failure-is-survivable
  (testing "a timeout or connection failure never blocks"
    (with-redefs [client/get (fn [_ _] (throw (java.net.SocketTimeoutException. "too slow")))]
      (is (= :unknown (pwned/check "password")))
      (is (false? (pwned/breached? "password")))))
  (testing "rate limiting and server errors read as no objection"
    (doseq [status [429 500 503]]
      (with-redefs [client/get (responding "" status)]
        (is (= :unknown (pwned/check "password")) (str "status " status))
        (is (false? (pwned/breached? "password"))))))
  (testing "a truncated or nonsense body does not throw"
    (doseq [body ["" "not:a:number" "GARBAGE" nil]]
      (with-redefs [client/get (responding body)]
        (is (contains? #{0 :unknown} (pwned/check "password")) (str "body " (pr-str body)))))))

(deftest the-password-never-leaves
  (testing "only the first five hex characters of the hash are sent"
    (let [sent (atom nil)]
      (with-redefs [client/get (fn [url _] (reset! sent url) {:status 200 :body ""})]
        (pwned/check "password")
        ;; Whole-URL equality, not a substring search: the host is
        ;; api.pwnedpasswords.com, which contains the word "password" itself.
        (is (= "https://api.pwnedpasswords.com/range/5BAA6" @sent))
        (is (not (clojure.string/includes? @sent password-suffix))
            "the rest of the hash stays here")))))

(deftest blank-input-asks-nobody
  (let [called (atom false)]
    (with-redefs [client/get (fn [_ _] (reset! called true) {:status 200 :body ""})]
      (is (= :unknown (pwned/check "")))
      (is (= :unknown (pwned/check nil)))
      (is (false? @called) "no request for an empty password"))))
