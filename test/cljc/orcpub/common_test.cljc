(ns orcpub.common-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.common :as common]))

;; ---------------------------------------------------------------------------
;; aloof-sort-by
;; ---------------------------------------------------------------------------

(deftest aloof-sort-by-normal-string-names
  (testing "case-insensitive alphabetical order for plain string :name keys"
    (let [items [{:name "Zebra"} {:name "apple"} {:name "Mango"}]
          result (common/aloof-sort-by :name items)]
      (is (= ["apple" "Mango" "Zebra"]
             (mapv :name result)))))

  (testing "already-sorted collection is unchanged"
    (let [items [{:name "Alpha"} {:name "Beta"} {:name "Gamma"}]
          result (common/aloof-sort-by :name items)]
      (is (= ["Alpha" "Beta" "Gamma"]
             (mapv :name result)))))

  (testing "single-element collection is returned unchanged"
    (let [items [{:name "Solo"}]
          result (common/aloof-sort-by :name items)]
      (is (= [{:name "Solo"}] (vec result)))))

  (testing "empty collection yields empty sequence"
    (is (empty? (common/aloof-sort-by :name [])))))

(deftest aloof-sort-by-nil-name-does-not-throw
  (testing "item with nil :name sorts before items with non-empty names"
    ;; nil -> (str nil) -> \"\" -> (s/lower-case \"\") -> \"\"
    ;; \"\" sorts before any non-empty lowercase string
    (let [items [{:name "Fireball"} {:name nil} {:name "Acid Splash"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      (is (= nil (first names))
          "nil :name should sort first (as empty string)")
      (is (= #{"Acid Splash" "Fireball"} (set (rest names))))))

  (testing "multiple nil :name items do not throw"
    (let [items [{:name nil} {:name "Shield"} {:name nil}]
          result (common/aloof-sort-by :name items)]
      (is (= 3 (count result)))
      (is (= "Shield" (:name (last result)))))))

(deftest aloof-sort-by-blank-whitespace-name
  (testing "blank string sorts before non-blank strings"
    (let [items [{:name "Bless"} {:name ""} {:name "Aid"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      (is (= "" (first names)))
      (is (= ["Aid" "Bless"] (vec (rest names))))))

  (testing "whitespace-only name sorts near the front (before letters)"
    (let [items [{:name "Cure Wounds"} {:name "   "} {:name "Aid"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      ;; \"   \" lower-cased is still \"   \" which precedes \"aid\"
      (is (= "   " (first names))))))

(deftest aloof-sort-by-mixed-nil-and-non-nil
  (testing "mixed nil and non-nil :name values sort correctly without throwing"
    (let [items [{:name "Fireball"}
                 {:name nil}
                 {:name "acid splash"}
                 {:name nil}
                 {:name "Bless"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      ;; Two nils sort as \"\" at the front, then alphabetical
      (is (= [nil nil "acid splash" "Bless" "Fireball"] names)))))

(deftest aloof-sort-by-title-sorter
  (testing ":title sorter works identically to :name sorter"
    (let [items [{:title "Sword"} {:title "axe"} {:title "Bow"}]
          result (common/aloof-sort-by :title items)]
      (is (= ["axe" "Bow" "Sword"]
             (mapv :title result)))))

  (testing "nil :title does not throw"
    (let [items [{:title "Rapier"} {:title nil}]
          result (common/aloof-sort-by :title items)]
      (is (= 2 (count result)))
      (is (= nil (:title (first result)))))))

(deftest aloof-sort-by-non-string-sorter-values
  (testing "numeric sorter value is coerced via str without throwing"
    ;; str on a number produces its decimal representation, e.g. (str 42) -> \"42\"
    (let [items [{:level 3} {:level 1} {:level 2}]
          result (common/aloof-sort-by :level items)]
      ;; Lexicographic string sort: \"1\" < \"2\" < \"3\"
      (is (= [1 2 3] (mapv :level result)))))

  (testing "keyword sorter value is coerced via str without throwing"
    ;; (str :fireball) -> \":fireball\"; all sort as their keyword printed forms
    (let [items [{:key :shield} {:key :fireball} {:key :bless}]
          result (common/aloof-sort-by :key items)]
      (is (= 3 (count result)))
      ;; Just verify no exception and count is preserved; ordering is deterministic
      ;; but depends on keyword print form \":bless\" < \":fireball\" < \":shield\"
      (is (= :bless (:key (first result)))))))

(deftest aloof-sort-by-missing-key
  (testing "item missing the sort key entirely (returns nil from sorter) does not throw"
    (let [items [{:name "Fireball"} {:other "data"} {:name "Acid Splash"}]
          result (common/aloof-sort-by :name items)]
      (is (= 3 (count result)))
      ;; The item with no :name has nil->\"\" so sorts first
      (is (nil? (:name (first result)))))))
