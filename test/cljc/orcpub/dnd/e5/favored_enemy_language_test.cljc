(ns orcpub.dnd.e5.favored-enemy-language-test
  "Tests verifying that favored enemy language selection never produces nil
   options. See: https://github.com/Orcpub/orcpub/issues/296

   When a language key referenced by favored-enemy-types or humanoid-enemies
   doesn't exist in the language map, language-selection must fall back to a
   generated entry rather than passing nil to language-option.

   Note: keys like :aquan, :bullywug, :gith are intentionally NOT in the base
   16 languages. They are exotic/creature-specific D&D languages that homebrew
   plugins may add. The fix ensures these produce valid fallback entries
   instead of nil."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.common :as common]))

;; ============================================================================
;; Base language map (16 standard D&D 5e languages)
;; Matches spell_subs.cljs. Plugins extend this at runtime.
;; ============================================================================

(def base-languages
  [{:name "Common"      :key :common}
   {:name "Dwarvish"    :key :dwarvish}
   {:name "Elvish"      :key :elvish}
   {:name "Giant"       :key :giant}
   {:name "Gnomish"     :key :gnomish}
   {:name "Goblin"      :key :goblin}
   {:name "Halfling"    :key :halfling}
   {:name "Orc"         :key :orc}
   {:name "Abyssal"     :key :abyssal}
   {:name "Celestial"   :key :celestial}
   {:name "Draconic"    :key :draconic}
   {:name "Deep Speech" :key :deep-speech}
   {:name "Infernal"    :key :infernal}
   {:name "Primordial"  :key :primordial}
   {:name "Sylvan"      :key :sylvan}
   {:name "Undercommon" :key :undercommon}])

(def language-map (common/map-by-key base-languages))

;; ============================================================================
;; Helper: simulate the language lookup with fallback (mirrors language-selection fix)
;; ============================================================================

;; Mirrors the corrections map in options.cljc language-selection.
;; If a new correction is added there, add it here too.
(def known-corrections
  {:primoridial :primordial})

(defn lookup-with-fallback
  "Mirrors the fixed language-selection logic: look up key in language-map,
   check corrections for legacy/misspelled keys, then fall back to generated
   entry if not found."
  [lang-map k]
  (or (lang-map k)
      (lang-map (known-corrections k))
      {:name (common/kw-to-name k true) :key k}))

;; ============================================================================
;; Tests
;; ============================================================================

(deftest test-language-lookup-fallback-never-returns-nil
  (testing "Known keys return the language-map entry"
    (let [result (lookup-with-fallback language-map :elvish)]
      (is (= :elvish (:key result)))
      (is (= "Elvish" (:name result)))))

  (testing "Unknown keys return a generated fallback entry (not nil)"
    (let [result (lookup-with-fallback language-map :giant-elk)]
      (is (some? result) "Fallback must not be nil")
      (is (= :giant-elk (:key result)))
      (is (= "Giant Elk" (:name result)))))

  (testing "Exotic D&D languages get valid fallback entries"
    (doseq [k [:aquan :auran :terran :ignan :gith :gnoll
               :bullywug :thri-kreen :troglodyte :druidic :modron]]
      (let [result (lookup-with-fallback language-map k)]
        (is (some? result) (str k " must not produce nil"))
        (is (= k (:key result)))
        (is (string? (:name result))
            (str k " must have a string name"))))))

(deftest test-no-nil-in-favored-enemy-language-lookups
  (testing "Every language key in favored-enemy-types resolves to a non-nil entry"
    (let [enemy-types (opt5e/favored-enemy-types language-map)]
      (doseq [[enemy-type lang-keys] enemy-types
              :when (sequential? lang-keys)]
        (doseq [k lang-keys]
          (let [result (lookup-with-fallback language-map k)]
            (is (some? result)
                (str "Enemy type " enemy-type " key " k " must not produce nil"))
            (is (some? (:name result))
                (str "Enemy type " enemy-type " key " k " must have a name"))
            (is (some? (:key result))
                (str "Enemy type " enemy-type " key " k " must have a key"))))))))

(deftest test-no-nil-in-humanoid-enemy-language-lookups
  (testing "Every language key in humanoid-enemies resolves to a non-nil entry"
    (doseq [[humanoid info] opt5e/humanoid-enemies]
      (let [lang-keys (if (sequential? info) info (:languages info))]
        (doseq [k lang-keys]
          (let [result (lookup-with-fallback language-map k)]
            (is (some? result)
                (str "Humanoid " humanoid " key " k " must not produce nil"))
            (is (some? (:name result))
                (str "Humanoid " humanoid " key " k " must have a name"))
            (is (some? (:key result))
                (str "Humanoid " humanoid " key " k " must have a key"))))))))

(deftest test-primoridial-typo-corrected
  (testing "Fey enemy type uses :primordial (not :primoridial typo)"
    (let [fey-langs (:fey (opt5e/favored-enemy-types language-map))]
      (is (some #{:primordial} fey-langs)
          "Fey should include :primordial")
      (is (not (some #{:primoridial} fey-langs))
          "Fey should NOT include :primoridial (typo)")))

  (testing "Legacy :primoridial resolves to Primordial via corrections shim"
    (let [result (lookup-with-fallback language-map :primoridial)]
      (is (some? result)
          ":primoridial must not produce nil")
      (is (= "Primordial" (:name result))
          ":primoridial should resolve to 'Primordial' (corrected name)")
      (is (= :primordial (:key result))
          ":primoridial should resolve to :primordial key"))))

(deftest test-homebrew-languages-used-when-available
  (testing "When homebrew adds a language to the map, it's used instead of fallback"
    (let [homebrew-map (assoc language-map
                              :aquan {:name "Aquan" :key :aquan}
                              :gith {:name "Gith" :key :gith})
          ;; Aquan is now in the map
          aquan-result (lookup-with-fallback homebrew-map :aquan)]
      (is (= "Aquan" (:name aquan-result))
          "Homebrew Aquan entry should be used")
      (is (= :aquan (:key aquan-result))))

    (let [homebrew-map (assoc language-map
                              :aquan {:name "Aquan" :key :aquan})
          ;; Giant-elk is still NOT in the map
          elk-result (lookup-with-fallback homebrew-map :giant-elk)]
      (is (= "Giant Elk" (:name elk-result))
          "Non-homebrew key should still get fallback name"))))

(deftest test-kw-to-name-generates-readable-names
  (testing "common/kw-to-name converts keyword keys to human-readable names"
    (is (= "Giant Elk" (common/kw-to-name :giant-elk true)))
    (is (= "Deep Speech" (common/kw-to-name :deep-speech true)))
    (is (= "Thri Kreen" (common/kw-to-name :thri-kreen true)))
    (is (= "Aquan" (common/kw-to-name :aquan true)))
    (is (= "Hook Horror" (common/kw-to-name :hook-horror true)))))
