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

(deftest test-custom-inline-content-not-flagged
  (testing "Inline :custom content is never flagged missing — every 'Custom'
            option derives ::entity/key :custom and its real data lives on the
            entity, not in a plugin. Regression for the persistent
            'Missing Content — Background: :custom' false positive; without the
            guard this character reports 2 missing (custom race + background)."
    (let [character {::entity/options
                     {:class [{::entity/key :fighter}]
                      :race {::entity/key :custom}
                      :background {::entity/key :custom}}}
          char-keys (reconcile/extract-content-keys character)
          ;; empty available content — a genuinely-missing homebrew key WOULD be
          ;; flagged here, so an empty result proves :custom is treated as inline
          missing (reconcile/check-content-availability char-keys {})]
      (is (empty? missing)
          ":custom race + background are inline content, not missing"))))

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

;; ============================================================================
;; Spell Selection Key Reconciliation
;; ============================================================================
;;
;; Saved characters had spell-selection keys derived from a
;; mutated class :name (e.g., :cleric-source-cantrips-known instead of the
;; canonical :cleric-cantrips-known). After reverting the mutation, the
;; loaded plugin-class data has its canonical :name back, so the canonical
;; selection keys can be reconstructed and used to heal orphaned saves.

(defn- spell-selection-fixture
  "Build a one-class character with a spell-selection options map under
   the class entry. Returns the entity."
  [class-key spell-options]
  {::entity/options
   {:class [{::entity/key class-key
             ::entity/options spell-options}]}})

(deftest test-reconcile-rewrites-name-derived-orphan-to-key-derived
  (testing "Saved selection key (name-derived) gets rewritten to
            the key-derived shape when the class entry's key disambiguates.
            Example: conflict-renamed homebrew :artificer-kibbles-tasty had
            saved selections under :artificer-cantrips-known (slug from :name
            'Artificer'); the key-based derivation produces
            :artificer-kibbles-tasty-cantrips-known."
    (let [character (spell-selection-fixture
                     :artificer-kibbles-tasty
                     {:artificer-cantrips-known
                      [{::entity/key :prestidigitation}
                       {::entity/key :guidance}]})
          loaded #{:artificer-kibbles-tasty}
          {:keys [character rewrote]}
          (reconcile/reconcile-spell-selection-keys character loaded)
          new-options (-> character ::entity/options :class first ::entity/options)]
      (is (= 1 (count rewrote)))
      (is (= {:class-key :artificer-kibbles-tasty
              :from :artificer-cantrips-known
              :to :artificer-kibbles-tasty-cantrips-known}
             (first rewrote)))
      (is (contains? new-options :artificer-kibbles-tasty-cantrips-known))
      (is (not (contains? new-options :artificer-cantrips-known)))
      (is (= 2 (count (:artificer-kibbles-tasty-cantrips-known new-options)))))))

(deftest test-reconcile-leaves-orphan-when-class-not-loaded
  (testing "Class entry whose :key isn't in the loaded set is passed through
            untouched. The existing missing-content banner handles the
            user-facing alert; no silent rewriting against arbitrary classes."
    (let [character (spell-selection-fixture
                     :artificer-kibbles-tasty
                     {:artificer-cantrips-known
                      [{::entity/key :guidance}]})
          {:keys [character rewrote]}
          (reconcile/reconcile-spell-selection-keys character #{})
          new-options (-> character ::entity/options :class first ::entity/options)]
      (is (empty? rewrote))
      (is (contains? new-options :artificer-cantrips-known)
          "orphan data preserved unchanged"))))

(deftest test-reconcile-leaves-healthy-key-alone
  (testing "Healthy canonical (key-derived) selection key is not touched"
    (let [character (spell-selection-fixture
                     :artificer-kibbles-tasty
                     {:artificer-kibbles-tasty-cantrips-known
                      [{::entity/key :guidance}]})
          loaded #{:artificer-kibbles-tasty}
          {:keys [character rewrote]}
          (reconcile/reconcile-spell-selection-keys character loaded)
          new-options (-> character ::entity/options :class first ::entity/options)]
      (is (empty? rewrote))
      (is (contains? new-options :artificer-kibbles-tasty-cantrips-known)))))

(deftest test-reconcile-leaves-builtin-cleric-untouched
  (testing "Built-in class with canonical keys is healthy and stays untouched"
    (let [character (spell-selection-fixture
                     :cleric
                     {:cleric-cantrips-known
                      [{::entity/key :guidance}]})
          loaded #{:cleric}
          {:keys [character rewrote]}
          (reconcile/reconcile-spell-selection-keys character loaded)
          new-options (-> character ::entity/options :class first ::entity/options)]
      (is (empty? rewrote))
      (is (contains? new-options :cleric-cantrips-known)))))

(deftest test-reconcile-preserves-non-spell-selection-keys
  (testing "Non-spell-selection keys are passed through unchanged"
    (let [character (spell-selection-fixture
                     :artificer-kibbles-tasty
                     {:divine-domain {::entity/key :life-domain}
                      :skill-proficiency [{::entity/key :medicine}]
                      :artificer-cantrips-known [{::entity/key :guidance}]})
          loaded #{:artificer-kibbles-tasty}
          {:keys [character]}
          (reconcile/reconcile-spell-selection-keys character loaded)
          new-options (-> character ::entity/options :class first ::entity/options)]
      (is (= {::entity/key :life-domain} (:divine-domain new-options)))
      (is (= [{::entity/key :medicine}] (:skill-proficiency new-options)))
      (is (contains? new-options :artificer-kibbles-tasty-cantrips-known))
      (is (not (contains? new-options :artificer-cantrips-known))))))

(deftest test-reconcile-rewrites-spells-known-suffix
  (testing "spells-known suffix follows the same rebind path as cantrips-known"
    (let [character (spell-selection-fixture
                     :wizard-kibbles-tasty
                     {:wizard-spells-known
                      [{::entity/key :magic-missile}
                       {::entity/key :shield}]})
          loaded #{:wizard-kibbles-tasty}
          {:keys [character rewrote]}
          (reconcile/reconcile-spell-selection-keys character loaded)
          new-options (-> character ::entity/options :class first ::entity/options)]
      (is (= :wizard-kibbles-tasty-spells-known (:to (first rewrote))))
      (is (contains? new-options :wizard-kibbles-tasty-spells-known))
      (is (= 2 (count (:wizard-kibbles-tasty-spells-known new-options)))))))

(deftest test-reconcile-handles-character-with-no-classes
  (testing "Character without :class entries returns unchanged result"
    (let [character {::entity/options {}}
          {:keys [character rewrote]}
          (reconcile/reconcile-spell-selection-keys character #{})]
      (is (empty? rewrote))
      (is (= {::entity/options {}} character)))))

(deftest test-reconcile-multiclass-each-class-isolated
  (testing "Two classes each reconcile against their own expected keys.
            Conflict-renamed homebrew with a saved name-derived orphan
            rewrites; built-in alongside it stays healthy."
    (let [character {::entity/options
                     {:class [{::entity/key :artificer-kibbles-tasty
                               ::entity/options
                               {:artificer-cantrips-known
                                [{::entity/key :guidance}]}}
                              {::entity/key :wizard
                               ::entity/options
                               {:wizard-cantrips-known
                                [{::entity/key :fire-bolt}]}}]}}
          loaded #{:artificer-kibbles-tasty :wizard}
          {:keys [character rewrote]}
          (reconcile/reconcile-spell-selection-keys character loaded)
          classes (-> character ::entity/options :class)]
      (is (= 1 (count rewrote)))
      (is (= :artificer-kibbles-tasty (:class-key (first rewrote))))
      (is (contains? (-> classes first ::entity/options) :artificer-kibbles-tasty-cantrips-known))
      (is (contains? (-> classes second ::entity/options) :wizard-cantrips-known)
          "built-in class entry untouched"))))

;; ── Former keys ─────────────────────────────────────────────────────────────

(def ^:private ct :orcpub.dnd.e5/subraces)

(deftest former-key-index-maps-old-to-new
  (testing "a renamed item points its old key at its new one"
    (is (= {:dark-elf-drow- :dark-elf-drow}
           (reconcile/former-key-index
            {"Pak" {ct {:dark-elf-drow {:key :dark-elf-drow
                                        :former-key :dark-elf-drow-
                                        :name "Dark Elf (Drow)"}}}}))))

  (testing "an item that was never renamed contributes nothing"
    (is (= {} (reconcile/former-key-index
               {"Pak" {ct {:elf {:key :elf :name "Elf"}}}}))))

  (testing "TWO items claiming the same former key are both dropped"
    ;; Rebinding would pick whichever was walked first, which is a coin flip
    ;; dressed as a repair.
    (is (= {} (reconcile/former-key-index
               {"A" {ct {:one {:key :one :former-key :shared}}}
                "B" {ct {:two {:key :two :former-key :shared}}}}))))

  (testing "a former key that is some item's LIVE key is dropped"
    ;; :elf still exists and still resolves; rebinding it away would break a
    ;; character that is working fine.
    (is (= {} (reconcile/former-key-index
               {"A" {ct {:elf {:key :elf :name "Elf"}
                         :high-elf {:key :high-elf :former-key :elf}}}})))))

(deftest reconcile-former-keys-rewrites-stored-selections
  (let [index {:dark-elf-drow- :dark-elf-drow}
        character {:orcpub.entity/options
                   {:race {:orcpub.entity/key :elf
                           :orcpub.entity/options
                           {:subrace {:orcpub.entity/key :dark-elf-drow-}}}
                    :feats [{:orcpub.entity/key :keen-mind}]}}]

    (testing "a nested key is translated"
      (let [{:keys [character rewrote]} (reconcile/reconcile-former-keys character index)]
        (is (= :dark-elf-drow
               (get-in character [:orcpub.entity/options :race
                                  :orcpub.entity/options :subrace
                                  :orcpub.entity/key])))
        (is (= [{:from :dark-elf-drow- :to :dark-elf-drow}] rewrote))))

    (testing "keys with no entry are left exactly as they were"
      (let [{:keys [character]} (reconcile/reconcile-former-keys character index)]
        (is (= :elf (get-in character [:orcpub.entity/options :race :orcpub.entity/key])))
        (is (= :keen-mind (get-in character [:orcpub.entity/options :feats 0
                                             :orcpub.entity/key])))))

    (testing "an empty index is a no-op"
      (is (= character (:character (reconcile/reconcile-former-keys character {})))))

    (testing "a character with no options is left alone"
      (is (= {} (:character (reconcile/reconcile-former-keys {} index)))))))

(deftest reconcile-former-keys-reaches-inside-a-multi-select
  (testing "a chosen option inside a vector is translated too"
    ;; :feats and friends store a VECTOR of chosen options, so a walk that only
    ;; descended maps would miss them.
    (let [{:keys [character]}
          (reconcile/reconcile-former-keys
           {:orcpub.entity/options {:feats [{:orcpub.entity/key :keen-mind-}]}}
           {:keen-mind- :keen-mind})]
      (is (= :keen-mind (get-in character [:orcpub.entity/options :feats 0
                                           :orcpub.entity/key]))))))

(deftest relink-rewrites-through-the-same-path-as-an-automatic-rebind
  (testing "a one-entry index is how a manual relink is expressed"
    ;; ::char5e/relink-content builds exactly this and routes through
    ;; :set-character, so a person's choice and an automatic rebind rewrite the
    ;; character by the same code rather than two implementations that can drift.
    (let [character {:orcpub.entity/options
                     {:class [{:orcpub.entity/key :artificer-kibbles-tasty}]}}
          {:keys [character rewrote]}
          (reconcile/reconcile-former-keys character
                                           {:artificer-kibbles-tasty :artificer})]
      (is (= :artificer (get-in character [:orcpub.entity/options :class 0
                                           :orcpub.entity/key])))
      (is (= [{:from :artificer-kibbles-tasty :to :artificer}] rewrote))))

  (testing "an unrelated key in the same character is untouched"
    (let [{:keys [character]}
          (reconcile/reconcile-former-keys
           {:orcpub.entity/options {:race {:orcpub.entity/key :elf}
                                    :background {:orcpub.entity/key :spy}}}
           {:elf :high-elf})]
      (is (= :high-elf (get-in character [:orcpub.entity/options :race
                                          :orcpub.entity/key])))
      (is (= :spy (get-in character [:orcpub.entity/options :background
                                     :orcpub.entity/key]))))))

;; ============================================================================
;; SRD options orphaned by a deliberate key-VALUE change
;;
;; Closes the [UNVERIFIED] in name-to-kw-audit.md section 6: reconciliation
;; targets MISSING HOMEBREW, and it was never confirmed what it does when an SRD
;; option's key changes value instead. That gates bulk key renames, because a
;; rename orphans the stored ::strict/key in every saved character that used it.
;;
;; The answer these tests establish: DETECTED, NOT REPAIRED.
;; ============================================================================

(deftest srd-key-value-change-is-detected-as-missing
  ;; A character stores the key that was current when it was saved. Rename that
  ;; SRD key and the stored value is, by definition, in neither place
  ;; check-content-availability looks: not in loaded content, and not in the
  ;; hardcoded builtin set (which now holds the NEW value). So it is flagged.
  (let [before {::entity/options {:race {::entity/key :half-elf}}}
        after  {::entity/options {:race {::entity/key :half-elf-phb-2014}}}
        check  (fn [c] (reconcile/check-content-availability
                        (reconcile/extract-content-keys c) {}))]
    (is (empty? (check before))
        "the key as it stands today is builtin, so it is not flagged")
    (is (= 1 (count (check after)))
        "the same option under a changed key IS flagged — detection works")
    (is (= :race (:content-type (first (check after)))))))

(deftest srd-key-value-change-is-not-automatically-repaired
  ;; Detection is not repair. The former-key rung is built from PLUGIN items, and
  ;; SRD content is not a plugin, so an SRD rename records nothing to rebind
  ;; against. Rung 3 (canonical-key) only reconciles a trailing separator, which a
  ;; deliberate value change is not. That leaves rung 4, the relink UI, and it is
  ;; why bulk renaming SRD keys needs a migration rather than a load-time fix.
  (let [orphaned {::entity/options {:race {::entity/key :half-elf-phb-2014}}}
        ;; plugins carrying no :former-key — which is every SRD rename
        plugins {"Some Source" {:orcpub.dnd.e5/races
                                {:half-elf {:key :half-elf :name "Half-Elf"}}}}
        index (reconcile/former-key-index plugins)
        {:keys [character rewrote]} (reconcile/reconcile-former-keys orphaned index)]
    (is (empty? index) "an SRD rename records no former key anywhere")
    (is (= orphaned character) "so the character is returned untouched")
    (is (empty? rewrote) "and nothing is reported as healed")))

(deftest homebrew-key-change-IS-repaired-because-it-records-a-former-key
  ;; The contrast that makes the SRD gap concrete: the identical orphan, when the
  ;; content is homebrew and the rename went through the import path, rebinds on
  ;; load. This is the difference a recorded former-key makes.
  (let [orphaned {::entity/options {:race {::entity/key :half-elf-phb-2014}}}
        plugins {"Some Source" {:orcpub.dnd.e5/races
                                {:half-elf-ua {:key :half-elf-ua
                                               :former-key :half-elf-phb-2014
                                               :name "Half-Elf (UA)"}}}}
        index (reconcile/former-key-index plugins)
        {:keys [character rewrote]} (reconcile/reconcile-former-keys orphaned index)]
    (is (= {:half-elf-phb-2014 :half-elf-ua} index))
    (is (= :half-elf-ua (get-in character [::entity/options :race ::entity/key]))
        "the stored key is rewritten to the item's current key")
    (is (= [{:from :half-elf-phb-2014 :to :half-elf-ua}] rewrote)
        "and the rebind is reported, so it can be shown rather than done silently")))

(deftest non-srd-content-is-flagged-when-its-plugin-is-absent
  ;; The builtin sets hold SRD ONLY, which is what the site serves itself.
  ;; Everything else -- including plenty of PHB content -- arrives as a plugin and
  ;; SHOULD be reported when that plugin is not loaded, exactly as homebrew is.
  ;; :eladrin is the example: real PHB content, not SRD, so being flagged is the
  ;; design working rather than a false positive.
  (let [srd     {::entity/options {:race {::entity/key :elf
                                          ::entity/options
                                          {:subrace {::entity/key :drow}}}}}
        plugin' {::entity/options {:race {::entity/key :elf
                                          ::entity/options
                                          {:subrace {::entity/key :eladrin}}}}}
        check (fn [c] (reconcile/check-content-availability
                       (reconcile/extract-content-keys c) {}))]
    (is (empty? (check srd))
        "SRD content is served by the site, so it is never reported missing")
    (is (= 1 (count (check plugin')))
        "non-SRD content is reported when the plugin providing it is not loaded")))

(deftest keys-the-trim-changed-still-resolve-for-saved-characters
  ;; Dropping the trailing separator from name-to-kw changed the key derived for
  ;; every built-in name ending in punctuation. A saved character still stores the
  ;; OLD form. A scan of src/ found 15 such names that are live and derive their
  ;; key rather than declaring one; these are the ones that can appear in a saved
  ;; character rather than only on screen.
  ;;
  ;; They resolve through entity/index-matching-key's canonical pass, which is
  ;; what it was built for. Asserted here so a future change to canonical-key
  ;; cannot quietly orphan them.
  (let [tk :orcpub.template/key
        resolves? (fn [stored template]
                    (= 0 (entity/index-matching-key [{tk template}] tk stored)))]
    (testing "a selection key (options.cljc skill-expertise-selection)"
      (is (resolves? :skill-expertise-double-proficiency-
                     :skill-expertise-double-proficiency)))
    (testing "equipment keys derived from names with a parenthesised suffix"
      (is (resolves? :ladder-10-foot- :ladder-10-foot))
      (is (resolves? :pole-10-foot- :pole-10-foot))
      (is (resolves? :rations-1-day- :rations-1-day)))
    (testing "a subrace key (template.cljc)"
      (is (resolves? :gray-dwarf-duerger- :gray-dwarf-duerger)))
    (testing "but it still refuses to guess between two candidates"
      (is (nil? (entity/index-matching-key [{tk :foo} {tk :foo-}] tk :foo--))))))

;; ============================================================================
;; class-binding-report — which class failed, and is the subclass even its own
;; ============================================================================

(def ^:private binding-plugins
  {"Pak" {:orcpub.dnd.e5/subclasses
          {:alchemist {:key :alchemist :class :artificer}
           :evocation {:key :evocation :class :wizard}}}})

(defn- char-with [class-key sel-key subclass-key]
  {::entity/options
   {:class [{::entity/key class-key
             ::entity/options {sel-key {::entity/key subclass-key}}}]}})

(deftest binding-report-names-the-class-that-failed-to-bind
  ;; "Something is missing" is useless for a class: the builder resets every
  ;; choice downstream of one, so the person has to know which.
  (let [r (reconcile/class-binding-report
           (char-with :artificer-kt :artificer-specialist :alchemist)
           #{:wizard :fighter}
           {})]
    (is (= [{:class-key :artificer-kt :subclass-key :alchemist}]
           (:unbound-classes r))
        "the subclass rides along so both can be offered for relink"))
  (testing "a loaded class is not reported"
    (is (empty? (:unbound-classes
                 (reconcile/class-binding-report
                  (char-with :wizard :arcane-tradition :evocation)
                  #{:wizard} {}))))))

(deftest binding-report-catches-a-subclass-filed-under-the-wrong-class
  ;; This one binds cleanly and grants the wrong features, so nothing LOOKS
  ;; broken -- which is why it needs detecting rather than waiting for a crash.
  (let [idx (reconcile/subclass->class-index binding-plugins)
        r (reconcile/class-binding-report
           (char-with :wizard :arcane-tradition :alchemist)
           #{:wizard :artificer}
           idx)]
    (is (= [{:class-key :wizard
             :subclass-key :alchemist
             :belongs-to :artificer
             :selection-key :arcane-tradition}]
           (:subclass-mismatches r)))
    (is (empty? (:unbound-classes r)) "the class itself is fine"))
  (testing "a subclass under its own class is not a mismatch"
    (is (empty? (:subclass-mismatches
                 (reconcile/class-binding-report
                  (char-with :artificer :artificer-specialist :alchemist)
                  #{:artificer}
                  (reconcile/subclass->class-index binding-plugins)))))))

(deftest binding-report-does-not-accuse-content-it-does-not-know
  ;; An unloaded subclass is absent from the index. Calling that a mismatch would
  ;; turn every missing plugin into a second, wrong complaint.
  (is (empty? (:subclass-mismatches
               (reconcile/class-binding-report
                (char-with :wizard :arcane-tradition :some-homebrew-thing)
                #{:wizard}
                (reconcile/subclass->class-index binding-plugins))))))

(deftest subclass-index-drops-a-subclass-two-classes-both-claim
  ;; Same rule as former-key-index: a contested claim is not an answer.
  (let [contested {"A" {:orcpub.dnd.e5/subclasses {:shared {:key :shared :class :wizard}}}
                   "B" {:orcpub.dnd.e5/subclasses {:shared {:key :shared :class :cleric}}}}]
    (is (= {} (reconcile/subclass->class-index contested))))
  (testing "but two sources agreeing is still usable"
    (let [agreed {"A" {:orcpub.dnd.e5/subclasses {:shared {:key :shared :class :wizard}}}
                  "B" {:orcpub.dnd.e5/subclasses {:shared {:key :shared :class :wizard}}}}]
      (is (= {:shared :wizard} (reconcile/subclass->class-index agreed))))))

(deftest binding-report-is-empty-for-a-character-with-no-classes
  (is (= {:unbound-classes [] :subclass-mismatches []}
         (reconcile/class-binding-report {::entity/options {}} #{:wizard} {}))))
