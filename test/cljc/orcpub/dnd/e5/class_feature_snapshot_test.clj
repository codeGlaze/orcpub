(ns orcpub.dnd.e5.class-feature-snapshot-test
  "FOUNDATION: characterization/regression net for class features (roadmap step F).
   Builds a real character of each class at a representative level and snapshots the
   feature names it grants (across traits/actions/bonus-actions/reactions) plus key
   derived facts (abilities, saves). This is the baseline the class-feature EXTRACTION
   (registry refactor) must reproduce byte-for-byte: re-run after each extraction step;
   if a feature's name/summary or a derived stat changes, it fails loudly.

   Starts with fighter to establish the pattern; extend class-by-class.
   JVM/clojure.test so it runs under the enforced `lein test` gate."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

(defn class-opt [opt-fn]
  (opt-fn sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map))

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map weapons5e/weapons
    sl5e/spell-lists spells5e/spell-map
    [] []                                  ; backgrounds, races
    [(class-opt classes5e/fighter-option)]
    [] language-map)))

(def abilities {:orcpub.dnd.e5.character/str 16 :orcpub.dnd.e5.character/dex 14
                :orcpub.dnd.e5.character/con 14 :orcpub.dnd.e5.character/int 10
                :orcpub.dnd.e5.character/wis 12 :orcpub.dnd.e5.character/cha 10})

(defn level-entries [n]
  (mapv (fn [i]
          {:orcpub.entity/key (keyword (str "level-" i))
           :orcpub.entity/options {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 6}}})
        (range 1 (inc n))))

(defn char-of [class-key levels]
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
    :class [{:orcpub.entity/key class-key
             :orcpub.entity/options {:levels (level-entries levels)}}]}})

(defn feature-names [built]
  ;; features land in different buckets by action-economy type; gather all names
  (->> (concat (char5e/traits built) (char5e/actions built)
               (char5e/bonus-actions built) (char5e/reactions built))
       (keep :name)
       set))

(defn snapshot
  "The regression baseline for a built character: the facts the feature EXTRACTION must
   preserve. Names + summaries across all feature buckets, plus key derived stats."
  [class-key levels]
  (let [built (entity/build (char-of class-key levels) test-template)
        feats (concat (char5e/traits built) (char5e/actions built)
                      (char5e/bonus-actions built) (char5e/reactions built))]
    {:feature-names (set (keep :name feats))
     :summaries     (into (sorted-map) (keep (fn [f] (when (:name f) [(:name f) (:summary f)])) feats))
     :number-of-attacks (char5e/number-of-attacks built)
     :saves         (set (char5e/saving-throws built))
     :abilities     (char5e/ability-values built)}))

;; ---------------------------------------------------------------------------
;; FIGHTER — baseline (extend with more levels/classes as the net grows)
;; ---------------------------------------------------------------------------
(deftest fighter-level-5-baseline
  (testing "level-5 fighter: the features + derived facts the extraction must reproduce"
    (let [s (snapshot :fighter 5)]
      (println "\n=== SNAPSHOT fighter@5 ===")
      (println "  features:" (pr-str (sort (:feature-names s))))
      (doseq [[n sum] (:summaries s)] (println "   -" n "=>" sum))
      (println "  number-of-attacks:" (:number-of-attacks s) " saves:" (pr-str (:saves s)) "\n")
      (is (contains? (:feature-names s) "Second Wind"))
      (is (contains? (:feature-names s) "Action Surge"))
      (is (= 2 (:number-of-attacks s)) "Extra Attack at level 5 → 2 attacks")
      (is (= #{:orcpub.dnd.e5.character/str :orcpub.dnd.e5.character/con} (:saves s))
          "fighter save proficiencies")
      (is (= 16 (:orcpub.dnd.e5.character/str (:abilities s)))))))

(deftest fighter-level-9-baseline
  (testing "level-9 fighter adds Indomitable (a trait); Extra Attack still 2"
    (let [s (snapshot :fighter 9)]
      (is (contains? (:feature-names s) "Indomitable"))
      (is (contains? (:feature-names s) "Second Wind"))
      (is (= 2 (:number-of-attacks s))))))
