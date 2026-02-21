(ns orcpub.pdf-spec-test
  "Tests for pure PDF spec helper functions.
   pdf_spec.cljc no longer has any re-frame subscribes,
   so all helpers are testable on JVM."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.pdf-spec :as pdf]))

;; -- class-string --

(deftest class-string-single-class
  (testing "single class without subclass"
    (is (= "Fighter (5)"
           (pdf/class-string
            [:fighter]
            {:fighter {:class-name "Fighter"
                       :class-level 5
                       :subclass-name ""}})))))

(deftest class-string-single-class-with-subclass
  (testing "single class with subclass shows brackets"
    (is (= "Wizard [Evocation] (10)"
           (pdf/class-string
            [:wizard]
            {:wizard {:class-name "Wizard"
                      :class-level 10
                      :subclass-name "Evocation"}})))))

(deftest class-string-multiclass
  (testing "multiclass joins with slash"
    (is (= "Fighter (5) / Wizard [Evocation] (3)"
           (pdf/class-string
            [:fighter :wizard]
            {:fighter {:class-name "Fighter"
                       :class-level 5
                       :subclass-name ""}
             :wizard {:class-name "Wizard"
                      :class-level 3
                      :subclass-name "Evocation"}})))))

(deftest class-string-empty-classes
  (testing "empty class list returns empty string"
    (is (= "" (pdf/class-string [] {})))))

;; -- entity-vals --

(deftest entity-vals-keyword-keys
  (testing "keyword keys use same key for to and from"
    ;; entity-val does (entity k), so built-char is a flat map
    (let [built-char {:str 18 :dex 14}
          result (pdf/entity-vals built-char [:str :dex])]
      (is (= 18 (:str result)))
      (is (= 14 (:dex result))))))

(deftest entity-vals-vector-keys
  (testing "vector [to from] renames the output key"
    (let [built-char {:strength 16}
          result (pdf/entity-vals built-char [[:str :strength]])]
      (is (= 16 (:str result)))
      (is (nil? (:strength result))))))

(deftest entity-vals-missing-key
  (testing "missing keys return nil"
    (let [built-char {}
          result (pdf/entity-vals built-char [:nonexistent])]
      (is (nil? (:nonexistent result))))))

;; -- trait-string nil/blank handling (homebrew data hardening) --

(deftest trait-string-normal
  (testing "Normal case: name and description both present"
    (let [result (pdf/trait-string "Darkvision" "you can see in the dark.")]
      (is (string? result))
      (is (.contains result "Darkvision"))
      ;; sentensize capitalizes first char and ensures trailing period
      (is (.contains result "You can see in the dark.")))))

(deftest trait-string-nil-desc
  (testing "Nil description doesn't crash (was NPE on subs/count before fix)"
    (let [result (pdf/trait-string "Darkvision" nil)]
      (is (string? result))
      (is (.contains result "Darkvision")))))

(deftest trait-string-nil-name-and-desc
  (testing "Both nil — worst case from totally empty homebrew trait"
    (let [result (pdf/trait-string nil nil)]
      (is (string? result))
      (is (.contains result "(Unnamed Trait)")))))

(deftest trait-string-nil-name-falls-back-to-desc
  (testing "Nil name uses first 30 chars of description as display name"
    (let [desc "You can see in the dark up to 60 feet including dim light"
          result (pdf/trait-string nil desc)]
      (is (string? result))
      ;; Should truncate to 30 chars + "..."
      (is (.contains result "...")))))

(deftest trait-string-blank-and-whitespace-desc
  (testing "Blank and whitespace-only descriptions treated as absent"
    (let [blank-result (pdf/trait-string "Darkvision" "")
          ws-result (pdf/trait-string "Darkvision" "   ")]
      ;; Neither should crash, both should use the name
      (is (.contains blank-result "Darkvision"))
      (is (.contains ws-result "Darkvision")))))

(deftest trait-string-wrong-type-desc
  (testing "Non-string description from corrupted data doesn't crash"
    (let [result (pdf/trait-string "Darkvision" 42)]
      (is (string? result))
      (is (.contains result "Darkvision")))))
