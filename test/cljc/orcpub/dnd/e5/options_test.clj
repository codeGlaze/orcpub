(ns orcpub.dnd.e5.options-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.template :as t]
            [orcpub.entity :as entity]))

(deftest test-total-slots
  (is (= {1 2} (opt/total-slots 3 3)))
  (is (= {1 4
          2 3
          3 3
          4 1}
         (opt/total-slots 20 3))))

;; -- feat-prereqs --

(deftest feat-prereqs-ability-prereq
  (testing "ability key produces ability prereq with min 13"
    (let [result (opt/feat-prereqs [::char5e/str] nil {})]
      (is (= 1 (count result)))
      (is (= "Requires STR 13 or higher" (::t/label (first result))))
      (is (fn? (::t/prereq-fn (first result)))))))

(deftest feat-prereqs-spellcasting-prereq
  (testing ":spellcasting produces can-cast-spell prereq"
    (let [result (opt/feat-prereqs [:spellcasting] nil {})]
      (is (= 1 (count result)))
      (is (= "Requires the ability to cast at least one spell."
             (::t/label (first result)))))))

(deftest feat-prereqs-armor-prereq
  (testing "non-ability non-spellcasting key produces armor prereq"
    (let [result (opt/feat-prereqs [:heavy] nil {})]
      (is (= 1 (count result)))
      (is (re-find #"(?i)heavy" (::t/label (first result)))))))

(deftest feat-prereqs-race-prereq-from-map
  (testing "race prereq resolves names from race-map parameter"
    (let [race-map {:elf {:name "Elf"} :dwarf {:name "Dwarf"}}
          path-prereqs {:race {:elf true :dwarf false}}
          result (opt/feat-prereqs [] path-prereqs race-map)
          labels (map ::t/label result)]
      ;; Only :elf has truthy value, :dwarf is false
      (is (= 1 (count result)))
      (is (some #(re-find #"Elf" %) labels)))))

(deftest feat-prereqs-no-race-prereq-when-empty
  (testing "no race prereqs when path-prereqs has no :race key"
    (let [result (opt/feat-prereqs [] {} {:elf {:name "Elf"}})]
      (is (empty? result))))
  (testing "no race prereqs when race map values are all false"
    (let [result (opt/feat-prereqs [] {:race {:elf false}} {:elf {:name "Elf"}})]
      (is (empty? result)))))

(deftest feat-prereqs-mixed-ability-and-race
  (testing "both ability and race prereqs combine"
    (let [race-map {:human {:name "Human"}}
          path-prereqs {:race {:human true}}
          result (opt/feat-prereqs [::char5e/str] path-prereqs race-map)]
      ;; 1 ability + 1 race
      (is (= 2 (count result))))))
