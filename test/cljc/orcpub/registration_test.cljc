(ns orcpub.registration-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.registration :as reg]))

(defn- messages [password & [context]]
  (:password (reg/validate-password password context)))

(deftest length-floor
  (testing "below the floor is rejected, at the floor is not"
    (is (some #(re-find #"at least 8" %) (messages "Sh0rt!")))
    (is (nil? (messages "vault of seven ravens")))
    (is (some? (messages nil)))
    (is (some? (messages "")))))

(deftest repeated-characters
  (is (reg/repeated-run? "aaa"))
  (is (reg/repeated-run? "passsword"))
  (is (reg/repeated-run? "hello!!!there"))
  (testing "two in a row is ordinary English and must pass"
    (is (not (reg/repeated-run? "kettle")))
    (is (not (reg/repeated-run? "aa")))
    (is (not (reg/repeated-run? nil)))))

(deftest sequential-runs
  (testing "counting up or down"
    (is (reg/sequential-run? "abcd"))
    (is (reg/sequential-run? "4321"))
    (is (reg/sequential-run? "vault1234")))
  (testing "keyboard rows, either direction"
    (is (reg/sequential-run? "qwerty"))
    (is (reg/sequential-run? "ytrewq"))
    (is (reg/sequential-run? "asdfgh")))
  (testing "ordinary words are not runs"
    (is (not (reg/sequential-run? "raven")))
    (is (not (reg/sequential-run? "vault")))
    (is (not (reg/sequential-run? "abc")))
    (is (not (reg/sequential-run? nil)))))

(deftest identifier-in-password
  (let [ctx {:username "thornwood" :email "ana@example.com"}]
    (is (reg/contains-identifier? "thornwood99" ctx))
    (is (reg/contains-identifier? "myTHORNWOODpass" ctx))
    (testing "the local part of the email counts"
      (is (reg/contains-identifier? "ana-is-here" ctx)))
    (testing "unrelated passwords pass, and short fragments are ignored"
      (is (not (reg/contains-identifier? "seven gilded ravens" ctx)))
      (is (not (reg/contains-identifier? "anything" {:username "an"}))))
    (testing "only checked when context is supplied"
      (is (nil? (messages "thornwood thornwood"))))))

(deftest no-composition-rules
  (testing "NIST 800-63B-4: character-class requirements shall not be imposed"
    (is (nil? (messages "correct horse battery staple")))
    (is (nil? (messages "ALLUPPERCASELETTERS")))
    (is (nil? (messages "9174620835")))))

(deftest registration-threads-context-through
  (let [errs (reg/validate-registration
              {:email "ana@example.com" :verify-email "ana@example.com"
               :username "thornwood" :password "thornwood1"}
              false false)]
    (testing "the identifier rule fires through the registration path"
      (is (some #(re-find #"username and email" %) (:password errs))))))
