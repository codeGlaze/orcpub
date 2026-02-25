(ns orcpub.errors-test
  "Tests for error handling utilities."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [orcpub.errors :as errors]))

;; Prefix all error output with TEST_ERROR so CI logs can distinguish
;; expected error-path output from real failures.
(use-fixtures :each
  (fn [f]
    (binding [errors/*error-prefix* "TEST_ERROR:"]
      (f))))

(deftest test-log-error
  (testing "log-error logs message without context"
    (is (nil? (errors/log-error "ERROR:" "Test message"))))

  (testing "log-error logs message with context"
    (is (nil? (errors/log-error "ERROR:" "Test message" {:id 123})))))

(deftest test-create-error
  (testing "create-error creates ExceptionInfo without cause"
    (let [ex (errors/create-error "User message" :test-error {:id 123})]
      (is (instance? clojure.lang.ExceptionInfo ex))
      (is (= "User message" (.getMessage ex)))
      (is (= :test-error (:error (ex-data ex))))
      (is (= 123 (:id (ex-data ex))))))

  (testing "create-error creates ExceptionInfo with cause"
    (let [cause (Exception. "Original error")
          ex (errors/create-error "User message" :test-error {:id 123} cause)]
      (is (instance? clojure.lang.ExceptionInfo ex))
      (is (= "User message" (.getMessage ex)))
      (is (= :test-error (:error (ex-data ex))))
      (is (= cause (.getCause ex))))))

(deftest test-with-error-handling*
  (testing "with-error-handling* returns result on success"
    (let [result (errors/with-error-handling*
                  (fn [] 42)
                  {:operation-name "test operation"
                   :user-message "Test failed"
                   :error-code :test-error
                   :context {:test true}})]
      (is (= 42 result))))

  (testing "with-error-handling* re-throws ExceptionInfo as-is"
    (let [original-ex (ex-info "Original" {:error :original})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Original"
           (errors/with-error-handling*
            (fn [] (throw original-ex))
            {:operation-name "test operation"
             :user-message "Test failed"
             :error-code :test-error
             :context {:test true}})))))

  (testing "with-error-handling* wraps generic exceptions"
    (try
      (errors/with-error-handling*
       (fn [] (throw (Exception. "Generic error")))
       {:operation-name "test operation"
        :user-message "User-friendly message"
        :error-code :test-error
        :context {:user-id 456}})
      (is false "Should have thrown exception")
      (catch clojure.lang.ExceptionInfo e
        (is (= "User-friendly message" (.getMessage e)))
        (is (= :test-error (:error (ex-data e))))
        (is (= 456 (:user-id (ex-data e))))
        (is (instance? Exception (.getCause e)))
        (is (= "Generic error" (.getMessage (.getCause e)))))))

  (testing "with-error-handling* calls on-error callback"
    (let [error-captured (atom nil)]
      (try
        (errors/with-error-handling*
         (fn [] (throw (Exception. "Test error")))
         {:operation-name "test operation"
          :user-message "Failed"
          :error-code :test-error
          :context {}
          :on-error (fn [e] (reset! error-captured e))})
        (catch Exception _))
      (is (some? @error-captured))
      (is (= "Test error" (.getMessage @error-captured))))))

(deftest test-with-db-error-handling
  (testing "with-db-error-handling returns result on success"
    (let [result (errors/with-db-error-handling :test-error
                   {:id 123}
                   "Database operation failed"
                   (+ 1 2 3))]
      (is (= 6 result))))

  (testing "with-db-error-handling wraps exceptions with proper context"
    (try
      (errors/with-db-error-handling :db-test-error
        {:user-id 789}
        "Unable to save to database"
        (throw (Exception. "Connection timeout")))
      (is false "Should have thrown exception")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Unable to save to database" (.getMessage e)))
        (is (= :db-test-error (:error (ex-data e))))
        (is (= 789 (:user-id (ex-data e))))
        (is (= "Connection timeout" (.getMessage (.getCause e))))))))

(deftest test-with-email-error-handling
  (testing "with-email-error-handling returns result on success"
    (let [result (errors/with-email-error-handling :test-error
                   {:email "test@example.com"}
                   "Email operation failed"
                   {:status :SUCCESS})]
      (is (= {:status :SUCCESS} result))))

  (testing "with-email-error-handling wraps exceptions with proper context"
    (try
      (errors/with-email-error-handling :email-send-failed
        {:email "user@example.com" :username "alice"}
        "Unable to send email"
        (throw (Exception. "SMTP server unavailable")))
      (is false "Should have thrown exception")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Unable to send email" (.getMessage e)))
        (is (= :email-send-failed (:error (ex-data e))))
        (is (= "user@example.com" (:email (ex-data e))))
        (is (= "alice" (:username (ex-data e))))
        (is (= "SMTP server unavailable" (.getMessage (.getCause e))))))))

(deftest test-with-validation
  (testing "with-validation returns result on success"
    (let [result (errors/with-validation :test-error
                   {:input "123"}
                   "Invalid input"
                   (Long/parseLong "123"))]
      (is (= 123 result))))

  (testing "with-validation handles NumberFormatException"
    (try
      (errors/with-validation :invalid-number
        {:input "abc"}
        "Invalid number format"
        (Long/parseLong "abc"))
      (is false "Should have thrown exception")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Invalid number format" (.getMessage e)))
        (is (= :invalid-number (:error (ex-data e))))
        (is (= "abc" (:input (ex-data e))))
        (is (instance? NumberFormatException (.getCause e))))))

  (testing "with-validation re-throws ExceptionInfo as-is"
    (let [original-ex (ex-info "Original validation error" {:error :original})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Original validation error"
           (errors/with-validation :test-error
             {:test true}
             "Validation failed"
             (throw original-ex))))))

  (testing "with-validation wraps other exceptions"
    (try
      (errors/with-validation :validation-error
        {:data "test"}
        "Validation failed"
        (throw (RuntimeException. "Unexpected error")))
      (is false "Should have thrown exception")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Validation failed" (.getMessage e)))
        (is (= :validation-error (:error (ex-data e))))
        (is (= "test" (:data (ex-data e))))
        (is (instance? RuntimeException (.getCause e)))))))

(deftest test-error-constants
  (testing "error code constants are keywords"
    (is (= :bad-credentials errors/bad-credentials))
    (is (= :unverified errors/unverified))
    (is (= :unverified-expired errors/unverified-expired))
    (is (= :no-account errors/no-account))
    (is (= :username-required errors/username-required))
    (is (= :password-required errors/password-required))
    (is (= :too-many-attempts errors/too-many-attempts))))
