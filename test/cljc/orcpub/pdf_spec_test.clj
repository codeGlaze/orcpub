(ns orcpub.pdf-spec-test
  "Tests for PDF spec utilities — specifically trait-string nil/blank handling.
   Homebrew content frequently has nil or missing descriptions; these tests
   verify PDF export won't crash on that data."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.pdf-spec :as pdf-spec]))

(deftest test-trait-string-normal
  (testing "Normal case: name and description both present"
    (let [result (pdf-spec/trait-string "Darkvision" "you can see in the dark.")]
      (is (string? result))
      (is (.contains result "Darkvision"))
      ;; sentensize capitalizes first char and ensures trailing period
      (is (.contains result "You can see in the dark.")))))

(deftest test-trait-string-nil-desc
  (testing "Nil description doesn't crash (was NPE on subs/count before fix)"
    (let [result (pdf-spec/trait-string "Darkvision" nil)]
      (is (string? result))
      (is (.contains result "Darkvision")))))

(deftest test-trait-string-nil-name-and-desc
  (testing "Both nil — worst case from totally empty homebrew trait"
    (let [result (pdf-spec/trait-string nil nil)]
      (is (string? result))
      (is (.contains result "(Unnamed Trait)")))))

(deftest test-trait-string-nil-name-falls-back-to-desc
  (testing "Nil name uses first 30 chars of description as display name"
    (let [desc "You can see in the dark up to 60 feet including dim light"
          result (pdf-spec/trait-string nil desc)]
      (is (string? result))
      ;; Should truncate to 30 chars + "..."
      (is (.contains result "...")))))

(deftest test-trait-string-blank-and-whitespace-desc
  (testing "Blank and whitespace-only descriptions treated as absent"
    (let [blank-result (pdf-spec/trait-string "Darkvision" "")
          ws-result (pdf-spec/trait-string "Darkvision" "   ")]
      ;; Neither should crash, both should use the name
      (is (.contains blank-result "Darkvision"))
      (is (.contains ws-result "Darkvision")))))

(deftest test-trait-string-wrong-type-desc
  (testing "Non-string description from corrupted data doesn't crash"
    (let [result (pdf-spec/trait-string "Darkvision" 42)]
      (is (string? result))
      (is (.contains result "Darkvision")))))
