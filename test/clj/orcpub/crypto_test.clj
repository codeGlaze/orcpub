(ns orcpub.crypto-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [orcpub.crypto :as crypto])
  (:import [java.util Base64]))

(defn- b64key [fill]
  (.encodeToString (Base64/getEncoder) (byte-array 32 (byte fill))))

(def k1 (crypto/parse-keys (str "k1:" (b64key 1))))
(def k2-then-k1 (crypto/parse-keys (str "k2:" (b64key 2) ",k1:" (b64key 1))))

(deftest a-name-comes-back-out
  (let [stored (crypto/encrypt k1 "user/preferred-name" "Fuss")]
    (is (str/starts-with? stored "v1:k1:"))
    (is (not (str/includes? stored "Fuss")) "the name is not visible in what is stored")
    (is (= "Fuss" (crypto/decrypt k1 "user/preferred-name" stored)))))

(deftest the-same-name-never-encrypts-the-same-way-twice
  (testing "a fresh IV each time, so two accounts called Fuss cannot be spotted
            as matching in a stolen database"
    (is (not= (crypto/encrypt k1 "p" "Fuss") (crypto/encrypt k1 "p" "Fuss")))))

(deftest unreadable-without-the-right-key
  (let [stored (crypto/encrypt k1 "p" "Fuss")
        other (crypto/parse-keys (str "k1:" (b64key 9)))]
    (is (nil? (crypto/decrypt other "p" stored)) "same id, wrong key: nil, not an exception")
    (is (nil? (crypto/decrypt nil "p" stored)) "no keys configured: nil")))

(deftest tampering-is-detected
  (let [stored (crypto/encrypt k1 "p" "Fuss")
        flipped (str (subs stored 0 (- (count stored) 3)) "AAA")]
    (is (nil? (crypto/decrypt k1 "p" flipped)))))

(deftest a-value-is-bound-to-its-purpose
  (testing "copying a ciphertext into a different field does not make it
            readable there"
    (is (nil? (crypto/decrypt k1 "user/other-field" (crypto/encrypt k1 "user/preferred-name" "Fuss"))))))

(deftest keys-can-rotate
  (let [old (crypto/encrypt k1 "p" "Fuss")
        new (crypto/encrypt k2-then-k1 "p" "Fuss")]
    (is (str/starts-with? new "v1:k2:") "new values use the first key")
    (is (= "Fuss" (crypto/decrypt k2-then-k1 "p" old)) "old values still read after rotation")
    (is (= "Fuss" (crypto/decrypt k2-then-k1 "p" new)))))

(deftest non-ascii-names-survive
  (is (= "Zoë 🌙 Ōkami" (crypto/decrypt k1 "p" (crypto/encrypt k1 "p" "Zoë 🌙 Ōkami")))))

(deftest nothing-to-encrypt-with
  (is (nil? (crypto/encrypt nil "p" "Fuss"))))

(deftest malformed-key-lists-say-what-is-wrong
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"id:base64" (crypto/parse-keys "justonething")))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"base64" (crypto/parse-keys "k1:not base64!!")))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"32 bytes"
        (crypto/parse-keys (str "k1:" (.encodeToString (Base64/getEncoder) (byte-array 16))))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unique"
        (crypto/parse-keys (str "k1:" (b64key 1) ",k1:" (b64key 2)))))
  (is (nil? (crypto/parse-keys "")) "unset is simply off, not an error"))
