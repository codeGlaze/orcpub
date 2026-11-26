(ns orcpub.email-alert-test
  "A server error reaches the admin alerts, scrubbed, once per problem, and never holds up the
   request that hit it."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as s]
            [orcpub.alerts :as alerts]
            [orcpub.email :as email])
  (:import [java.util.concurrent Executors TimeUnit]))

(def ^:private context
  "A Pedestal context for a failing POST, carrying a cookie that must not reach an email."
  {:request {:uri "/dnd/5e/characters" :request-method :post
             :headers {"cookie" "token=hunter2" "user-agent" "Firefox"}}})

(defn- with-alerter
  "Runs `f` with `a` as the running alerter, then restores the one before."
  [a f]
  (let [before @alerts/current]
    (try (reset! alerts/current a) (f)
         (finally (reset! alerts/current before)))))

(deftest a-server-error-becomes-one-scrubbed-alert
  (let [sent (atom [])
        a (alerts/new-alerter {:send! #(do (swap! sent conj %) true) :executor nil})]
    (with-alerter a
      (fn []
        (email/send-error-email context (ex-info "boom" {:x 1}))
        (email/send-error-email context (ex-info "boom" {:x 1}))
        (is (= 1 (count @sent)) "the same error twice is one alert")
        (is (s/includes? (:subject (first @sent)) "POST /dnd/5e/characters") (:subject (first @sent)))
        (is (s/includes? (:body (first @sent)) "Firefox") "safe headers are kept")
        (is (not (s/includes? (:body (first @sent)) "hunter2")) "the cookie is not")))))

(deftest the-request-does-not-wait-for-the-mail-server
  (let [executor (Executors/newSingleThreadScheduledExecutor)
        a (alerts/new-alerter {:send! (fn [_] (Thread/sleep 3000) true) :executor executor})]
    (try
      (with-alerter a
        (fn []
          (let [start (System/nanoTime)]
            (email/send-error-email context (ex-info "slow mail" {}))
            (is (< (/ (- (System/nanoTime) start) 1e6) 500)
                "the error handler returns before the email is sent"))))
      (finally (.shutdownNow executor) (.awaitTermination executor 5 TimeUnit/SECONDS)))))

(deftest with-no-alerter-an-error-is-logged
  (with-alerter nil
    (fn []
      (is (s/includes? (with-out-str (email/send-error-email context (ex-info "boom" {}))) "logged only")))))

(deftest alerts-are-not-emailed-without-a-mail-server
  (with-redefs [email/configured? (constantly false)]
    (is (nil? (email/alert-channel)))))
