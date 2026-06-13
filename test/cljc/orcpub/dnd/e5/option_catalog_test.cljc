(ns orcpub.dnd.e5.option-catalog-test
  "Phase 1 of the content-extensibility work (docs/kb/content-extensibility-plan.md).

   `by-parent` is the generic seam that subraces (and, in Phase 2, subclasses)
   route through instead of open-coding `group-by`. These tests pin that it is
   behaviour-identical to `group-by`, so the subscription re-point is provably a
   no-op."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.option-catalog :as catalog]))

(def subraces
  [{:name "Hill Dwarf"     :key :hill-dwarf     :race :dwarf}
   {:name "Mountain Dwarf" :key :mountain-dwarf :race :dwarf}
   {:name "High Elf"       :key :high-elf       :race :elf}])

(deftest by-parent-matches-group-by
  (testing "by-parent is identical to group-by on the same key"
    (is (= (group-by :race subraces)
           (catalog/by-parent :race subraces))))
  (testing "buckets each option under its parent key, preserving input order"
    (let [grouped (catalog/by-parent :race subraces)]
      (is (= [:hill-dwarf :mountain-dwarf] (map :key (grouped :dwarf))))
      (is (= [:high-elf] (map :key (grouped :elf))))))
  (testing "empty input yields an empty grouping"
    (is (= {} (catalog/by-parent :race [])))))
