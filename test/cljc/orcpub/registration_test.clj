(ns orcpub.registration-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.registration :as reg]))

(deftest bad-email?-format
  (testing "valid emails pass"
    (is (not (reg/bad-email? "user@example.com")))
    (is (not (reg/bad-email? "foo.bar+tag@domain.co.uk")))
    (is (not (reg/bad-email? "a@b.cc"))))
  (testing "invalid emails fail"
    (is (reg/bad-email? ""))
    (is (reg/bad-email? "not-an-email"))
    (is (reg/bad-email? "@nodomain.com"))
    (is (reg/bad-email? "user@"))
    (is (reg/bad-email? nil))))

(deftest bad-username?-format
  (testing "alphanumeric usernames pass"
    (is (not (reg/bad-username? "alice")))
    (is (not (reg/bad-username? "Bob123")))
    (is (not (reg/bad-username? "A"))))
  (testing "non-alphanumeric usernames fail"
    (is (reg/bad-username? "has spaces"))
    (is (reg/bad-username? "special!char"))
    (is (reg/bad-username? "under_score"))
    (is (reg/bad-username? ""))
    (is (reg/bad-username? nil))))

(deftest bad-gmail?-typos
  (testing "common gmail misspellings are caught"
    (is (some? (reg/bad-gmail? "user@gmial.com")))
    (is (some? (reg/bad-gmail? "user@gmal.com")))
    (is (some? (reg/bad-gmail? "user@gmil.com"))))
  (testing "bad gmail TLD is caught"
    (is (some? (reg/bad-gmail? "user@gmail.cm")))
    (is (some? (reg/bad-gmail? "user@gmail.co"))))
  (testing "correct gmail passes"
    (is (not (reg/bad-gmail? "user@gmail.com")))))

(deftest bad-hotmail?-typos
  (testing "common hotmail misspellings are caught"
    (is (some? (reg/bad-hotmail? "user@hotmil.com")))
    (is (some? (reg/bad-hotmail? "user@htmail.com"))))
  (testing "correct hotmail passes"
    (is (not (reg/bad-hotmail? "user@hotmail.com")))))

(deftest password-strength-scoring
  (testing "strong password scores 5"
    (is (= 5 (reg/password-strength "Str0ng!Pass"))))
  (testing "nil password scores 0"
    (is (= 0 (reg/password-strength nil))))
  (testing "simple lowercase only scores 1 (length alone)"
    (is (= 1 (reg/password-strength "abcdefgh")))))

(deftest validate-password-length
  (testing "passwords under 6 chars produce error"
    (let [errors (reg/validate-password "short")]
      (is (some? (:password errors)))))
  (testing "passwords 6+ chars produce no error"
    (let [errors (reg/validate-password "longenough")]
      (is (empty? errors))))
  (testing "nil password produces error"
    (let [errors (reg/validate-password nil)]
      (is (some? (:password errors))))))

(deftest validate-registration-happy-path
  (testing "valid registration produces no errors"
    (let [reg-data {:email "user@gmail.com"
                    :verify-email "user@gmail.com"
                    :username "validuser"
                    :password "goodpassword"
                    :first-and-last-name "Test User"}
          errors (reg/validate-registration reg-data false false)]
      (is (empty? errors)))))

(deftest validate-registration-catches-problems
  (testing "email taken"
    (let [errors (reg/validate-registration
                   {:email "user@gmail.com"
                    :verify-email "user@gmail.com"
                    :username "validuser"
                    :password "goodpassword"}
                   true false)]
      (is (some #(re-find #"already associated" %) (:email errors)))))
  (testing "username taken"
    (let [errors (reg/validate-registration
                   {:email "user@gmail.com"
                    :verify-email "user@gmail.com"
                    :username "validuser"
                    :password "goodpassword"}
                   false true)]
      (is (some #(re-find #"already taken" %) (:username errors)))))
  (testing "emails don't match"
    (let [errors (reg/validate-registration
                   {:email "user@gmail.com"
                    :verify-email "different@gmail.com"
                    :username "validuser"
                    :password "goodpassword"}
                   false false)]
      (is (some? (:verify-email errors)))))
  (testing "username too short"
    (let [errors (reg/validate-registration
                   {:email "user@gmail.com"
                    :verify-email "user@gmail.com"
                    :username "ab"
                    :password "goodpassword"}
                   false false)]
      (is (some? (:username errors)))))
  (testing "bad email format"
    (let [errors (reg/validate-registration
                   {:email "not-an-email"
                    :verify-email "not-an-email"
                    :username "validuser"
                    :password "goodpassword"}
                   false false)]
      (is (some? (:email errors))))))
