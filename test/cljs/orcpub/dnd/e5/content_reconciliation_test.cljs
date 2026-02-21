(ns orcpub.dnd.e5.content-reconciliation-test
  "Tests for content reconciliation — detecting missing homebrew references
   in characters and suggesting replacements.

   Real scenario: user loads a character that references :artificer-kibbles-tasty
   but doesn't have Kibbles' Tasty Homebrew loaded. The reconciliation module
   should detect this and suggest similar content."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.dnd.e5.content-reconciliation :as reconcile]
            [orcpub.entity :as entity]))

;; ============================================================================
;; Test Data — realistic character and content structures
;; ============================================================================

(def test-character
  "Character with a mix of built-in and homebrew content references."
  {::entity/options
   {:race {::entity/key :tiefling
           ::entity/options
           {:subrace {::entity/key :winged-tiefling}}}
    :class [{::entity/key :artificer-kibbles-tasty
             ::entity/options
             {:artificer-specialist
              {::entity/key :alchemist-kibbles}}}
            {::entity/key :wizard
             ::entity/options
             {:arcane-tradition
              {::entity/key :school-of-chronurgy}}}]
    :background {::entity/key :sage}}})

(def available-content
  "Content currently loaded — has built-in + some homebrew, but not all."
  {:classes [{:key :barbarian :name "Barbarian"}
             {:key :wizard :name "Wizard"}
             {:key :artificer :name "Artificer"}]
   :subclasses [{:key :alchemist :name "Alchemist"}
                {:key :abjuration :name "Abjuration"}
                {:key :school-of-chronurgy :name "School of Chronurgy"}]
   :races [{:key :tiefling :name "Tiefling"}
           {:key :human :name "Human"}]
   :subraces [{:key :winged-tiefling :name "Winged Tiefling"}]
   :backgrounds [{:key :sage :name "Sage"}
                  {:key :acolyte :name "Acolyte"}]})

;; ============================================================================
;; Key Extraction
;; ============================================================================

(deftest test-extract-content-keys
  (testing "Extracts all content keys with correct content types"
    (let [keys (reconcile/extract-content-keys test-character)
          by-key (zipmap (map :key keys) keys)]
      (is (contains? by-key :tiefling))
      (is (contains? by-key :artificer-kibbles-tasty))
      (is (contains? by-key :wizard))
      (is (contains? by-key :sage))))
  (testing "Annotates content types correctly"
    (let [keys (reconcile/extract-content-keys test-character)
          by-key (zipmap (map :key keys) keys)]
      (is (= :race (:content-type (get by-key :tiefling))))
      (is (= :subrace (:content-type (get by-key :winged-tiefling))))
      (is (= :class (:content-type (get by-key :wizard))))
      (is (= :class (:content-type (get by-key :artificer-kibbles-tasty))))
      (is (= :subclass (:content-type (get by-key :alchemist-kibbles))))
      (is (= :subclass (:content-type (get by-key :school-of-chronurgy))))
      (is (= :background (:content-type (get by-key :sage)))))))

(deftest test-extract-empty-character
  (testing "Character with no options returns empty seq"
    (let [keys (reconcile/extract-content-keys {})]
      (is (empty? keys)))))

;; ============================================================================
;; Missing Content Detection
;; ============================================================================

(deftest test-detects-missing-homebrew
  (testing "Homebrew class not in available content is flagged"
    (let [char-keys (reconcile/extract-content-keys test-character)
          missing (reconcile/check-content-availability char-keys available-content)
          missing-keys (set (map :key missing))]
      (is (contains? missing-keys :artificer-kibbles-tasty))
      (is (not (contains? missing-keys :wizard)))
      (is (not (contains? missing-keys :sage))))))

(deftest test-builtin-content-not-flagged
  (testing "Built-in SRD content is never flagged as missing"
    (let [character {::entity/options
                     {:class [{::entity/key :fighter}]
                      :race {::entity/key :elf}
                      :background {::entity/key :acolyte}}}
          char-keys (reconcile/extract-content-keys character)
          ;; Empty available content — builtins should still not be flagged
          missing (reconcile/check-content-availability char-keys {})]
      (is (empty? missing)))))

;; ============================================================================
;; Similarity & Suggestions
;; ============================================================================

(deftest test-find-similar-content
  (testing "Finds similar content by key prefix"
    (let [candidates [{:key :artificer :name "Artificer"}
                      {:key :wizard :name "Wizard"}
                      {:key :bard :name "Bard"}]
          results (reconcile/find-similar-content :artificer-kibbles-tasty :class candidates)]
      (is (seq results))
      (is (= :artificer (-> results first :key)))))
  (testing "No suggestions for completely unrelated keys"
    (let [candidates [{:key :wizard :name "Wizard"}]
          results (reconcile/find-similar-content :blood-hunter-order-of-the-lycan :class candidates)]
      (is (empty? results)))))

;; ============================================================================
;; Full Report Generation
;; ============================================================================

(deftest test-generate-missing-content-report
  (testing "Report correctly identifies missing vs present content"
    (let [report (reconcile/generate-missing-content-report test-character available-content)]
      (is (:has-missing? report))
      (is (pos? (:missing-count report)))
      (let [missing-artificer (first (filter #(= :artificer-kibbles-tasty (:key %))
                                             (:items report)))]
        (is (some? missing-artificer))
        (is (seq (:suggestions missing-artificer)))))))

(deftest test-report-no-missing-content
  (testing "Report for character with only built-in content"
    (let [character {::entity/options
                     {:class [{::entity/key :wizard}]
                      :race {::entity/key :human}
                      :background {::entity/key :sage}}}
          report (reconcile/generate-missing-content-report character available-content)]
      (is (not (:has-missing? report)))
      (is (= 0 (:missing-count report)))
      (is (empty? (:items report))))))

(deftest test-report-includes-inferred-source
  (testing "Missing items include inferred source from key suffix"
    (let [report (reconcile/generate-missing-content-report test-character available-content)
          missing-artificer (first (filter #(= :artificer-kibbles-tasty (:key %))
                                           (:items report)))]
      (is (some? (:inferred-source missing-artificer)))
      (is (string? (:inferred-source missing-artificer))))))

;; ============================================================================
;; Feat Detection
;; ============================================================================

(def feat-test-character
  "Character with top-level feats (some with nested ability score selections)
   and class-level ASI-or-feat choices. Mimics the Datomic entity structure
   for a Dragon Knight character using the exported.orcbrew test data."
  {::entity/options
   {:race {::entity/key :tegokka}
    :class [{::entity/key :dragon-knight
             ::entity/options
             {:levels [{::entity/key :level-1}
                       {::entity/key :level-2}
                       {::entity/key :level-3}
                       {::entity/key :level-4
                        ::entity/options
                        {:asi-or-feat {::entity/key :feat}}}]}}]
    :background {::entity/key :folk-hero}
    ;; Top-level feat selection: multi-select vector of chosen feats.
    ;; Metabolic Control has a nested :asi sub-selection for ability score.
    :feats [{::entity/key :blade-mastery}
            {::entity/key :brawny}
            {::entity/key :metabolic-control
             ::entity/options
             {:asi [{::entity/key :orcpub.dnd.e5.character/con}]}}]}})

(deftest test-feat-detection-top-level
  (testing "Top-level feats are detected as :feat content type"
    (let [keys (reconcile/extract-content-keys feat-test-character)
          by-key (zipmap (map :key keys) keys)]
      (is (= :feat (:content-type (get by-key :blade-mastery))))
      (is (= :feat (:content-type (get by-key :brawny))))
      (is (= :feat (:content-type (get by-key :metabolic-control)))))))

(deftest test-ability-score-under-feat-not-extracted
  (testing "Ability score nested under a feat is NOT extracted at all"
    (let [keys (reconcile/extract-content-keys feat-test-character)
          key-set (set (map :key keys))]
      ;; Direct extraction only gets feat keys, not their sub-selections
      (is (not (contains? key-set :orcpub.dnd.e5.character/con))
          "ability score under a feat must not be extracted as content"))))

(deftest test-feat-not-flagged-when-loaded
  (testing "Feats present in available content are not flagged as missing"
    (let [content (assoc available-content
                         :feats [{:key :blade-mastery :name "Blade Mastery"}
                                 {:key :brawny :name "Brawny"}
                                 {:key :metabolic-control :name "Metabolic Control"}])
          report (reconcile/generate-missing-content-report feat-test-character content)]
      (is (not (some #(= :feat (:content-type %)) (:items report)))
          "no feats should be missing when all are in available content"))))

(deftest test-feat-flagged-when-missing
  (testing "Feats absent from available content ARE flagged as missing"
    (let [report (reconcile/generate-missing-content-report feat-test-character {})
          missing-feats (filter #(= :feat (:content-type %)) (:items report))]
      (is (= 3 (count missing-feats))
          "all 3 feats should be flagged as missing")
      (is (= #{:blade-mastery :brawny :metabolic-control}
             (set (map :key missing-feats)))))))
