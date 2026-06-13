(ns orcpub.dnd.e5.option-catalog-test
  "Phase 1 of the content-extensibility work (docs/kb/content-extensibility-plan.md).

   `by-parent` is the generic seam that subraces (and, in Phase 2, subclasses)
   route through instead of open-coding `group-by`. These tests pin that it is
   behaviour-identical to `group-by`, so the subscription re-point is provably a
   no-op."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5 :as e5]
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

;; plugin-vals shape: a seq of plugin maps, each holding content under namespaced
;; content-keys mapping option-key -> option (mirrors ::e5/plugin-vals).
(def plugin-vals
  [{::e5/boons       {:pact-of-x {:name "Pact of X" :key :pact-of-x}}
    ::e5/invocations {:agonizing {:name "Agonizing" :key :agonizing}}}
   {::e5/boons       {:pact-of-y {:name "Pact of Y" :key :pact-of-y}}}])

(deftest plugin-options-matches-legacy-extraction
  (testing "plugin-options is identical to the per-type mapcat extraction it replaces"
    (is (= (mapcat #(-> % ::e5/boons vals) plugin-vals)
           (catalog/plugin-options ::e5/boons plugin-vals)))
    (is (= (mapcat (comp vals ::e5/invocations) plugin-vals)
           (catalog/plugin-options ::e5/invocations plugin-vals))))
  (testing "collects a content-key across all plugins"
    (is (= #{:pact-of-x :pact-of-y}
           (set (map :key (catalog/plugin-options ::e5/boons plugin-vals))))))
  (testing "a content-key absent from a plugin contributes nothing (no error)"
    (is (= [:agonizing]
           (map :key (catalog/plugin-options ::e5/invocations plugin-vals)))))
  (testing "an unknown content-key yields nothing"
    (is (empty? (catalog/plugin-options ::e5/spells plugin-vals))))
  (testing "empty plugin-vals yields nothing"
    (is (empty? (catalog/plugin-options ::e5/boons [])))))
