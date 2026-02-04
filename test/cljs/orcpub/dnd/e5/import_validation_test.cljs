(ns orcpub.dnd.e5.import-validation-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.dnd.e5.import-validation :as import-val]
            [orcpub.dnd.e5 :as e5]
            [cljs.spec.alpha :as spec]))

;; ============================================================================
;; Test Data
;; ============================================================================

(def valid-plugin-edn
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"My Homebrew\"
                :name \"Fireball\"
                :level 3}
     :lightning-bolt {:option-pack \"My Homebrew\"
                      :name \"Lightning Bolt\"
                      :level 3}}}")

(def invalid-plugin-edn-parse-error
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"My Homebrew\"")  ; Missing closing braces

(def plugin-with-missing-option-pack
  "{:orcpub.dnd.e5/spells
    {:fireball {:name \"Fireball\"
                :level 3}}}")  ; Missing :option-pack

(def plugin-with-empty-option-pack
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"\"
                :name \"Fireball\"
                :level 3}}}")


(def plugin-with-disabled-nil
  "{:disabled? nil
    :orcpub.dnd.e5/spells
    {:fireball {:option-pack \"My Homebrew\"
                :name \"Fireball\"
                :level 3}}}")

(def multi-plugin-edn
  "{\"Plugin 1\" {:orcpub.dnd.e5/spells
                   {:fireball {:option-pack \"Plugin 1\"
                               :name \"Fireball\"}}}
    \"Plugin 2\" {:orcpub.dnd.e5/races
                   {:elf {:option-pack \"Plugin 2\"
                          :name \"Elf\"}}}}")

(def plugin-with-mixed-validity
  "{:orcpub.dnd.e5/spells
    {:valid-spell {:option-pack \"Test\"
                   :name \"Valid Spell\"}
     :invalid-spell {:name \"No Option Pack\"}
     :another-valid {:option-pack \"Test\"
                     :name \"Another Valid\"}}}")

;; ============================================================================
;; Parse Tests
;; ============================================================================

(deftest test-parse-edn-success
  (testing "Parsing valid EDN"
    (let [result (import-val/parse-edn valid-plugin-edn)]
      (is (:success result))
      (is (map? (:data result)))
      (is (contains? (:data result) :orcpub.dnd.e5/spells)))))

(deftest test-parse-edn-failure
  (testing "Parsing invalid EDN"
    (let [result (import-val/parse-edn invalid-plugin-edn-parse-error)]
      (is (not (:success result)))
      (is (:error result))
      (is (string? (:hint result))))))

(deftest test-parse-edn-empty-string
  (testing "Parsing empty string"
    (let [result (import-val/parse-edn "")]
      (is (not (:success result))))))

;; ============================================================================
;; Validation Tests
;; ============================================================================

(deftest test-validate-item-valid
  (testing "Validating a valid item"
    (let [item {:option-pack "Test Pack" :name "Test Item"}
          result (import-val/validate-item :test-item item)]
      (is (:valid result)))))

(deftest test-validate-item-missing-option-pack
  (testing "Validating item without option-pack"
    (let [item {:name "Test Item"}
          result (import-val/validate-item :test-item item)]
      (is (not (:valid result)))
      (is (:errors result)))))

(deftest test-validate-content-group
  (testing "Validating a content group with mixed items"
    (let [items {:valid1 {:option-pack "Test" :name "Valid 1"}
                 :invalid1 {:name "No Pack"}
                 :valid2 {:option-pack "Test" :name "Valid 2"}}
          result (import-val/validate-content-group :orcpub.dnd.e5/spells items)]
      (is (= 2 (:valid-count result)))
      (is (= 1 (:invalid-count result)))
      (is (= 1 (count (:invalid-items result)))))))

(deftest test-validate-plugin-progressive
  (testing "Progressive validation of plugin"
    (let [plugin (read-string plugin-with-mixed-validity)
          result (import-val/validate-plugin-progressive plugin)]
      (is (not (:valid result)))  ; Has invalid items
      (is (= 2 (:valid-items-count result)))
      (is (= 1 (:invalid-items-count result))))))

;; ============================================================================
;; Pre-Export Validation Tests
;; ============================================================================

(deftest test-validate-before-export-valid
  (testing "Pre-export validation of valid plugin"
    (let [plugin (read-string valid-plugin-edn)
          result (import-val/validate-before-export plugin)]
      (is (:valid result))
      (is (or (nil? (:warnings result))
             (empty? (:warnings result)))))))

(deftest test-validate-before-export-empty-option-pack
  (testing "Pre-export validation detects empty option-pack"
    (let [plugin (read-string plugin-with-empty-option-pack)
          result (import-val/validate-before-export plugin)]
      (is (seq (:warnings result)))
      (is (some #(re-find #"option-pack" %) (:warnings result))))))

(deftest test-validate-before-export-nil-values
  (testing "Pre-export validation detects nil values"
    (let [plugin {:orcpub.dnd.e5/spells {:test {:option-pack nil}}
                  :some-key nil}
          result (import-val/validate-before-export plugin)]
      (is (seq (:warnings result))))))

;; ============================================================================
;; Import Strategy Tests
;; ============================================================================

(deftest test-import-all-or-nothing-valid
  (testing "All-or-nothing import with valid data"
    (let [plugin (read-string valid-plugin-edn)
          result (import-val/import-all-or-nothing plugin)]
      (is (:success result))
      (is (= :single-plugin (:strategy result)))
      (is (= plugin (:data result))))))

(deftest test-import-all-or-nothing-invalid
  (testing "All-or-nothing import with invalid data"
    (let [plugin (read-string plugin-with-missing-option-pack)
          result (import-val/import-all-or-nothing plugin)]
      (is (not (:success result)))
      (is (:errors result)))))

(deftest test-import-progressive-with-errors
  (testing "Progressive import recovers valid items"
    (let [plugin (read-string plugin-with-mixed-validity)
          result (import-val/import-progressive plugin)]
      (is (:success result))
      (is (:had-errors result))
      (is (= 2 (:imported-count result)))
      (is (= 1 (:skipped-count result)))
      (is (= 1 (count (:skipped-items result))))
      ;; Verify cleaned plugin only has valid items
      (let [cleaned-spells (get-in result [:data :orcpub.dnd.e5/spells])]
        (is (= 2 (count cleaned-spells)))
        (is (contains? cleaned-spells :valid-spell))
        (is (contains? cleaned-spells :another-valid))
        (is (not (contains? cleaned-spells :invalid-spell)))))))

(deftest test-import-progressive-all-valid
  (testing "Progressive import with all valid items"
    (let [plugin (read-string valid-plugin-edn)
          result (import-val/import-progressive plugin)]
      (is (:success result))
      (is (not (:had-errors result)))
      (is (= 2 (:imported-count result)))
      (is (= 0 (:skipped-count result))))))

;; ============================================================================
;; Auto-Cleaning Tests
;; ============================================================================

(deftest test-validate-import-with-auto-clean
  (testing "Auto-clean fixes disabled? nil"
    (let [result (import-val/validate-import plugin-with-disabled-nil
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; After cleaning, disabled? should be false instead of nil
      (let [disabled (get-in result [:data :disabled?])]
        (is (= false disabled))))))

(deftest test-validate-import-empty-option-pack-auto-clean
  (testing "Auto-clean fixes empty option-pack"
    (let [result (import-val/validate-import plugin-with-empty-option-pack
                                             {:strategy :progressive
                                              :auto-clean true})]
      ;; Should succeed after cleaning
      (is (:success result)))))

;; ============================================================================
;; Complete Workflow Tests
;; ============================================================================

(deftest test-full-import-workflow-valid
  (testing "Complete import workflow with valid file"
    (let [result (import-val/validate-import valid-plugin-edn
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (is (not (:had-errors result)))
      (is (map? (:data result))))))

(deftest test-full-import-workflow-parse-error
  (testing "Complete import workflow with parse error"
    (let [result (import-val/validate-import invalid-plugin-edn-parse-error
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (not (:success result)))
      (is (:parse-error result))
      (is (:error result))
      (is (:hint result)))))

(deftest test-full-import-workflow-progressive
  (testing "Complete import workflow with progressive strategy"
    (let [result (import-val/validate-import plugin-with-mixed-validity
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (is (:had-errors result))
      ;; Should have imported 2 valid items and skipped 1 invalid
      (is (= 2 (:imported-count result)))
      (is (= 1 (:skipped-count result))))))

(deftest test-full-import-workflow-strict
  (testing "Complete import workflow with strict strategy"
    (let [result (import-val/validate-import plugin-with-mixed-validity
                                             {:strategy :strict
                                              :auto-clean true})]
      ;; Strict mode should fail because not all items are valid
      (is (not (:success result)))
      (is (:errors result)))))

;; ============================================================================
;; Multi-Plugin Tests
;; ============================================================================

(deftest test-import-multi-plugin
  (testing "Importing multi-plugin file"
    (let [result (import-val/validate-import multi-plugin-edn
                                             {:strategy :strict
                                              :auto-clean true})]
      (is (:success result))
      (is (= :multi-plugin (:strategy result)))
      (is (map? (:data result)))
      (is (contains? (:data result) "Plugin 1"))
      (is (contains? (:data result) "Plugin 2")))))

;; ============================================================================
;; Error Message Formatting Tests
;; ============================================================================

(deftest test-format-import-result-success
  (testing "Formatting successful import result"
    (let [result {:success true :imported-count 5}
          message (import-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"✅" message))
      (is (re-find #"successful" message)))))

(deftest test-format-import-result-with-warnings
  (testing "Formatting import result with warnings"
    (let [result {:success true
                  :had-errors true
                  :imported-count 2
                  :skipped-count 1}
          message (import-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"⚠️" message))
      (is (re-find #"warning" message)))))

(deftest test-format-import-result-parse-error
  (testing "Formatting parse error result"
    (let [result {:success false
                  :parse-error true
                  :error "Unexpected token"
                  :line 5
                  :hint "Check brackets"}
          message (import-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"⚠️" message))
      (is (re-find #"Could not read" message))
      (is (re-find #"Line: 5" message)))))

(deftest test-format-import-result-validation-error
  (testing "Formatting validation error result"
    (let [result {:success false
                  :errors ["Error 1" "Error 2"]}
          message (import-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"⚠️" message))
      (is (re-find #"Invalid" message)))))
;; ============================================================================
;; Data-Level Cleaning Tests
;; ============================================================================

(def plugin-with-preserved-nil
  "{:orcpub.dnd.e5/classes
    {:wizard {:option-pack \"Test\"
              :name \"Wizard\"
              :spellcasting {:spell-list-kw nil}}}}")

(def plugin-with-removed-nil
  "{:orcpub.dnd.e5/monsters
    {:monster {:option-pack \"Test\"
               :name \"Test Monster\"
               :saving-throws {:str nil, :dex 5, :con nil}}}}")

(def plugin-with-ability-nils
  "{:orcpub.dnd.e5/monsters
    {:monster {:option-pack \"Test\"
               :name \"Test Monster\"
               :abilities {:str nil, :dex nil, :con 10}}}}")

(def plugin-with-trailing-comma
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"Test\"
                :name \"Fireball\",}}}")

(def multi-plugin-with-empty-key
  "{\"\" {:orcpub.dnd.e5/races {:elf {:option-pack \"\" :name \"Elf\"}}}
    \"Existing Pack\" {:orcpub.dnd.e5/spells {:fireball {:option-pack \"Existing Pack\" :name \"Fireball\"}}}}")

(deftest test-data-clean-preserves-semantic-nil
  (testing "Data cleaning preserves nil for semantic fields like spell-list-kw"
    (let [result (import-val/validate-import plugin-with-preserved-nil
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; spell-list-kw nil should be PRESERVED (it means custom spell list)
      (let [wizard (get-in result [:data :orcpub.dnd.e5/classes :wizard])]
        (is (= "Wizard" (:name wizard)))
        (is (contains? (:spellcasting wizard) :spell-list-kw))
        (is (nil? (get-in wizard [:spellcasting :spell-list-kw])))))))

(deftest test-data-clean-removes-numeric-nil
  (testing "Data cleaning removes nil for numeric fields like ability scores"
    (let [result (import-val/validate-import plugin-with-removed-nil
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; :str nil and :con nil should be REMOVED (accidental leftovers)
      (let [saving-throws (get-in result [:data :orcpub.dnd.e5/monsters :monster :saving-throws])]
        (is (not (contains? saving-throws :str)))
        (is (not (contains? saving-throws :con)))
        (is (= 5 (:dex saving-throws)))))))

(deftest test-data-clean-ability-nils
  (testing "Data cleaning removes nil ability scores"
    (let [result (import-val/validate-import plugin-with-ability-nils
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (let [abilities (get-in result [:data :orcpub.dnd.e5/monsters :monster :abilities])]
        (is (not (contains? abilities :str)))
        (is (not (contains? abilities :dex)))
        (is (= 10 (:con abilities)))))))

(deftest test-string-clean-trailing-comma
  (testing "String cleaning removes trailing commas before closing braces"
    (let [result (import-val/validate-import plugin-with-trailing-comma
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (is (= "Fireball" (get-in result [:data :orcpub.dnd.e5/spells :fireball :name]))))))

(deftest test-data-clean-renames-empty-plugin-key
  (testing "Data cleaning renames empty string plugin key"
    (let [result (import-val/validate-import multi-plugin-with-empty-key
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; Empty key should be renamed to "Unnamed Content"
      (let [data (:data result)]
        (is (not (contains? data "")))
        (is (contains? data "Unnamed Content"))
        (is (contains? data "Existing Pack"))))))

(deftest test-data-clean-fixes-empty-option-pack
  (testing "Data cleaning fixes empty option-pack strings"
    (let [result (import-val/validate-import multi-plugin-with-empty-key
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; Empty option-pack should be replaced with "Unnamed Content"
      (let [elf (get-in result [:data "Unnamed Content" :orcpub.dnd.e5/races :elf])]
        (is (= "Unnamed Content" (:option-pack elf)))))))

;; ============================================================================
;; Duplicate Key Detection Tests
;; ============================================================================

(def multi-plugin-with-internal-duplicate
  "{\"Source A\" {:orcpub.dnd.e5/classes {:artificer {:option-pack \"Source A\" :name \"Artificer A\"}}}
    \"Source B\" {:orcpub.dnd.e5/classes {:artificer {:option-pack \"Source B\" :name \"Artificer B\"}}}}")

(def plugin-external-conflict
  "{:orcpub.dnd.e5/classes {:wizard {:option-pack \"New Source\" :name \"My Wizard\"}}}")

(def existing-plugins
  {"PHB" {:orcpub.dnd.e5/classes {:wizard {:option-pack "PHB" :name "Wizard"}}}})

(deftest test-detect-internal-duplicate-keys
  (testing "Detecting duplicate keys within a multi-plugin import"
    (let [data (read-string multi-plugin-with-internal-duplicate)
          conflicts (import-val/detect-duplicate-keys data nil "Test")]
      (is (= 1 (count (:internal-conflicts conflicts))))
      (is (= :artificer (-> conflicts :internal-conflicts first :key)))
      (is (= 2 (count (-> conflicts :internal-conflicts first :sources)))))))

(deftest test-detect-external-duplicate-keys
  (testing "Detecting duplicate keys against existing plugins"
    (let [data (read-string plugin-external-conflict)
          conflicts (import-val/detect-duplicate-keys data existing-plugins "New Source")]
      (is (= 0 (count (:internal-conflicts conflicts))))
      (is (= 1 (count (:external-conflicts conflicts))))
      (is (= :wizard (-> conflicts :external-conflicts first :key))))))

(deftest test-no-false-positive-duplicates
  (testing "No false positives when keys don't conflict"
    (let [data {:orcpub.dnd.e5/classes {:sorcerer {:option-pack "Test" :name "Sorcerer"}}}
          conflicts (import-val/detect-duplicate-keys data existing-plugins "Test")]
      (is (empty? (:internal-conflicts conflicts)))
      (is (empty? (:external-conflicts conflicts))))))

;; ============================================================================
;; Key Renaming Tests
;; ============================================================================

(deftest test-generate-new-key
  (testing "Generating new key with source suffix"
    (is (= :artificer-kibbles-tasty
           (import-val/generate-new-key :artificer "Kibbles' Tasty")))
    (is (= :wizard-my-homebrew
           (import-val/generate-new-key :wizard "My Homebrew")))
    (is (= :monk-test-123
           (import-val/generate-new-key :monk "Test 123")))))

(deftest test-rename-key-in-plugin
  (testing "Renaming a key in a plugin"
    (let [plugin {:orcpub.dnd.e5/classes
                  {:artificer {:option-pack "Test" :name "Artificer"}}}
          result (import-val/rename-key-in-plugin
                  plugin
                  :orcpub.dnd.e5/classes
                  :artificer
                  :artificer-test)]
      (is (not (contains? (get result :orcpub.dnd.e5/classes) :artificer)))
      (is (contains? (get result :orcpub.dnd.e5/classes) :artificer-test))
      (is (= "Artificer" (get-in result [:orcpub.dnd.e5/classes :artificer-test :name]))))))

(deftest test-rename-key-updates-subclass-references
  (testing "Renaming a class key updates subclass references"
    (let [plugin {:orcpub.dnd.e5/classes
                  {:artificer {:option-pack "Test" :name "Artificer"}}
                  :orcpub.dnd.e5/subclasses
                  {:alchemist {:option-pack "Test" :name "Alchemist" :class :artificer}
                   :armorer {:option-pack "Test" :name "Armorer" :class :artificer}
                   :other-subclass {:option-pack "Other" :name "Other" :class :wizard}}}
          result (import-val/rename-key-in-plugin
                  plugin
                  :orcpub.dnd.e5/classes
                  :artificer
                  :artificer-kibbles)]
      ;; Class should be renamed
      (is (contains? (get result :orcpub.dnd.e5/classes) :artificer-kibbles))
      (is (not (contains? (get result :orcpub.dnd.e5/classes) :artificer)))
      ;; Subclasses should have updated :class references
      (is (= :artificer-kibbles (get-in result [:orcpub.dnd.e5/subclasses :alchemist :class])))
      (is (= :artificer-kibbles (get-in result [:orcpub.dnd.e5/subclasses :armorer :class])))
      ;; Other subclass should be unchanged
      (is (= :wizard (get-in result [:orcpub.dnd.e5/subclasses :other-subclass :class]))))))

(deftest test-rename-key-updates-subrace-references
  (testing "Renaming a race key updates subrace references"
    (let [plugin {:orcpub.dnd.e5/races
                  {:elf {:option-pack "Test" :name "Elf"}}
                  :orcpub.dnd.e5/subraces
                  {:high-elf {:option-pack "Test" :name "High Elf" :race :elf}
                   :wood-elf {:option-pack "Test" :name "Wood Elf" :race :elf}
                   :hill-dwarf {:option-pack "Test" :name "Hill Dwarf" :race :dwarf}}}
          result (import-val/rename-key-in-plugin
                  plugin
                  :orcpub.dnd.e5/races
                  :elf
                  :elf-homebrew)]
      ;; Race should be renamed
      (is (contains? (get result :orcpub.dnd.e5/races) :elf-homebrew))
      (is (not (contains? (get result :orcpub.dnd.e5/races) :elf)))
      ;; Subraces should have updated :race references
      (is (= :elf-homebrew (get-in result [:orcpub.dnd.e5/subraces :high-elf :race])))
      (is (= :elf-homebrew (get-in result [:orcpub.dnd.e5/subraces :wood-elf :race])))
      ;; Other subrace should be unchanged
      (is (= :dwarf (get-in result [:orcpub.dnd.e5/subraces :hill-dwarf :race]))))))

(deftest test-apply-key-renames-batch
  (testing "Applying batch of key renames"
    (let [data {"Source A" {:orcpub.dnd.e5/classes
                            {:artificer {:option-pack "Source A" :name "Artificer A"}}
                            :orcpub.dnd.e5/subclasses
                            {:alchemist {:option-pack "Source A" :name "Alchemist" :class :artificer}}}
                "Source B" {:orcpub.dnd.e5/classes
                            {:artificer {:option-pack "Source B" :name "Artificer B"}}}}
          renames [{:source "Source A"
                    :content-type :orcpub.dnd.e5/classes
                    :old-key :artificer
                    :new-key :artificer-source-a}]
          result (import-val/apply-key-renames data renames)]
      ;; Source A's artificer should be renamed
      (is (contains? (get-in result ["Source A" :orcpub.dnd.e5/classes]) :artificer-source-a))
      (is (not (contains? (get-in result ["Source A" :orcpub.dnd.e5/classes]) :artificer)))
      ;; Source A's subclass should have updated reference
      (is (= :artificer-source-a (get-in result ["Source A" :orcpub.dnd.e5/subclasses :alchemist :class])))
      ;; Source B's artificer should be unchanged
      (is (contains? (get-in result ["Source B" :orcpub.dnd.e5/classes]) :artificer)))))

;; ============================================================================
;; Option Auto-Fill Tests
;; ============================================================================

(def selection-with-empty-options
  {:name "My Selection"
   :key :my-selection
   :option-pack "Test Pack"
   :options [{} {:name ""} {:name "Valid Option"}]})

(def selection-no-options
  {:name "No Options Selection"
   :key :no-options
   :option-pack "Test Pack"})

(deftest test-fill-missing-option-fields
  (testing "Empty option gets placeholder name with index"
    (let [[filled changes] (import-val/fill-missing-option-fields 0 {})]
      (is (= "[Option 1]" (:name filled)))
      (is (= [:name] changes))))
  (testing "Option with blank name gets filled"
    (let [[filled changes] (import-val/fill-missing-option-fields 2 {:name ""})]
      (is (= "[Option 3]" (:name filled)))
      (is (= [:name] changes))))
  (testing "Option with valid name is unchanged"
    (let [[filled changes] (import-val/fill-missing-option-fields 0 {:name "Fireball"})]
      (is (= "Fireball" (:name filled)))
      (is (empty? changes))))
  (testing "Option with description but no name gets filled"
    (let [[filled changes] (import-val/fill-missing-option-fields 4 {:description "A cool option"})]
      (is (= "[Option 5]" (:name filled)))
      (is (= "A cool option" (:description filled)))
      (is (= [:name] changes)))))

(deftest test-fill-options-in-item
  (testing "Item with empty options gets filled"
    (let [[filled count] (import-val/fill-options-in-item selection-with-empty-options)]
      (is (= "[Option 1]" (get-in filled [:options 0 :name])))
      (is (= "[Option 2]" (get-in filled [:options 1 :name])))
      (is (= "Valid Option" (get-in filled [:options 2 :name])))
      (is (= 2 count))))
  (testing "Item without options is unchanged"
    (let [[filled count] (import-val/fill-options-in-item selection-no-options)]
      (is (nil? (:options filled)))
      (is (= 0 count))))
  (testing "Item with all valid options has zero changes"
    (let [[filled count] (import-val/fill-options-in-item
                          {:options [{:name "A"} {:name "B"}]})]
      (is (= "A" (get-in filled [:options 0 :name])))
      (is (= "B" (get-in filled [:options 1 :name])))
      (is (= 0 count)))))

(deftest test-fill-all-missing-fields-includes-options
  (testing "fill-all-missing-fields processes options"
    (let [item {:options [{} {:name "Good"}]}
          result (import-val/fill-all-missing-fields item :orcpub.dnd.e5/selections)]
      (is (= "[Option 1]" (get-in result [:item :options 0 :name])))
      (is (= "Good" (get-in result [:item :options 1 :name])))
      (is (= 1 (get-in result [:changes :options-fixed])))))
  (testing "fill-all-missing-fields handles item with no options"
    (let [result (import-val/fill-all-missing-fields {:name "Test"} :orcpub.dnd.e5/selections)]
      (is (= 0 (get-in result [:changes :options-fixed])))))
  (testing "fill-all-missing-fields processes both traits and options"
    (let [item {:traits [{:name "Good Trait"} {}]
                :options [{} {:name "Good Option"}]}
          result (import-val/fill-all-missing-fields item :orcpub.dnd.e5/races)]
      (is (= "[Missing Trait Name]" (get-in result [:item :traits 1 :name])))
      (is (= "[Option 1]" (get-in result [:item :options 0 :name])))
      (is (= 1 (get-in result [:changes :traits-fixed])))
      (is (= 1 (get-in result [:changes :options-fixed]))))))