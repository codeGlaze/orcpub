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

(def all-class-opts
  [[:barbarian classes5e/barbarian-option] [:bard classes5e/bard-option]
   [:cleric classes5e/cleric-option] [:fighter classes5e/fighter-option]
   [:monk classes5e/monk-option] [:paladin classes5e/paladin-option]
   [:ranger classes5e/ranger-option] [:rogue classes5e/rogue-option]
   [:sorcerer classes5e/sorcerer-option] [:wizard classes5e/wizard-option]])

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map weapons5e/weapons
    sl5e/spell-lists spells5e/spell-map
    [] []                                  ; backgrounds, races
    (map (fn [[_ f]] (class-opt f)) all-class-opts)
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
   preserve. Names + summaries + frequencies across all feature buckets, plus key derived stats."
  [class-key levels]
  (let [built (entity/build (char-of class-key levels) test-template)
        feats (concat (char5e/traits built) (char5e/actions built)
                      (char5e/bonus-actions built) (char5e/reactions built))]
    {:feature-names (set (keep :name feats))
     ;; per-feature detail: the summary (scaling text) and frequency (uses) the override work targets
     :details   (into (sorted-map)
                      (keep (fn [f] (when (:name f)
                                      [(:name f) {:summary (:summary f) :frequency (:frequency f)}]))
                            feats))
     :number-of-attacks (char5e/number-of-attacks built)
     :saves         (set (char5e/saving-throws built))
     :abilities     (char5e/ability-values built)}))

;; ---------------------------------------------------------------------------
;; Baseline @ level 5 for ALL classes — captured from observed built output (dump-all).
;; This is the regression net: if the feature-name set, num-attacks, or saves change after
;; the class-feature extraction, the matching class fails. Update only with intent.
;; (Auto-granted features only; choice-gated ones like Fighting Style/Expertise are absent.)
;; ---------------------------------------------------------------------------
(def ^:private S :orcpub.dnd.e5.character/str)
(def ^:private D :orcpub.dnd.e5.character/dex)
(def ^:private C :orcpub.dnd.e5.character/con)
(def ^:private I :orcpub.dnd.e5.character/int)
(def ^:private W :orcpub.dnd.e5.character/wis)
(def ^:private Ch :orcpub.dnd.e5.character/cha)

(def baseline-5
  {:barbarian {:attacks 2 :saves #{S C} :features #{"Danger Sense" "Extra Attack" "Fast Movement" "Rage" "Reckless Attack"}}
   :bard      {:attacks 1 :saves #{D Ch} :features #{"Bardic Inspiration" "Font of Inspiration" "Jack of All Trades" "Song of Rest"}}
   :cleric    {:attacks 1 :saves #{W Ch} :features #{"Channel Divinity" "Channel Divinity: Turn Undead" "Destroy Undead"}}
   :fighter   {:attacks 2 :saves #{S C} :features #{"Action Surge" "Second Wind"}}
   :monk      {:attacks 2 :saves #{S D} :features #{"Deflect Missiles" "Flurry of Blows" "Ki" "Martial Arts" "Patient Defense" "Slow Fall" "Step of the Wind" "Stunning Strike"}}
   :paladin   {:attacks 2 :saves #{W Ch} :features #{"Channel Divinity" "Divine Health" "Divine Sense" "Divine Smite" "Lay on Hands"}}
   :ranger    {:attacks 2 :saves #{S D} :features #{"Favored Enemy" "Natural Explorer" "Primeval Awareness"}}
   :rogue     {:attacks 1 :saves #{D I} :features #{"Cunning Action" "Sneak Attack" "Thieves' Cant" "Uncanny Dodge"}}
   :sorcerer  {:attacks 1 :saves #{C Ch} :features #{"Flexible Casting" "Sorcery Points"}}
   :wizard    {:attacks 1 :saves #{W I} :features #{"Arcane Recovery"}}})

(deftest all-classes-level-5-baseline
  (testing "every class @5 grants exactly its baselined auto-features, num-attacks, and saves"
    (doseq [[k expected] baseline-5]
      (let [s (snapshot k 5)]
        (is (= (:features expected) (:feature-names s)) (str (name k) " feature set"))
        (is (= (:attacks expected) (:number-of-attacks s)) (str (name k) " number-of-attacks"))
        (is (= (:saves expected) (:saves s)) (str (name k) " save proficiencies"))))))

(deftest fighter-level-9-baseline
  (testing "level-9 fighter adds Indomitable (a trait); Extra Attack still 2"
    (let [s (snapshot :fighter 9)]
      (is (contains? (:feature-names s) "Indomitable"))
      (is (contains? (:feature-names s) "Second Wind"))
      (is (= 2 (:number-of-attacks s))))))

;; ---------------------------------------------------------------------------
;; FIGHTER detail baseline (the Step-2 extraction target) — captures summaries + use-counts
;; (the :frequency :amount), so a change to Action Surge's uses or Second Wind's scaling FAILS.
;; This is the depth the extraction of these features must reproduce.
;; ---------------------------------------------------------------------------
(defn- feat-detail [class-key levels feature-name]
  (get-in (snapshot class-key levels) [:details feature-name]))

(deftest fighter-feature-detail-baseline
  (testing "fighter @5 — Action Surge / Second Wind summary + uses"
    (is (= {:summary "take an extra action"
            :frequency {:units :orcpub.dnd.e5.units/rest :amount 1}}
           (feat-detail :fighter 5 "Action Surge")))
    (is (= {:summary "regain 1d10 +5 HPs"
            :frequency {:units :orcpub.dnd.e5.units/rest :amount 1}}
           (feat-detail :fighter 5 "Second Wind"))))
  (testing "fighter @17 — Action Surge uses bump to 2; Second Wind heal scales to +17; Indomitable 3/long-rest"
    (is (= 2 (get-in (feat-detail :fighter 17 "Action Surge") [:frequency :amount]))
        "Action Surge gains a second use at 17")
    (is (= "regain 1d10 +17 HPs" (:summary (feat-detail :fighter 17 "Second Wind")))
        "Second Wind heal scales with level")
    (is (= {:units :orcpub.dnd.e5.units/long-rest :amount 3}
           (:frequency (feat-detail :fighter 17 "Indomitable"))))))

;; ---------------------------------------------------------------------------
;; Dump every class @5 to baseline the rest (resilient: one bad class won't kill the run)
;; ---------------------------------------------------------------------------
(deftest ^:diagnostic dump-all-classes-5
  (println "\n=== ALL CLASSES @ level 5 — features / num-attacks / saves ===")
  (doseq [[k _] all-class-opts]
    (try
      (let [s (snapshot k 5)]
        (println (format "  %-10s attacks=%s saves=%s\n     features=%s"
                         (name k) (:number-of-attacks s) (pr-str (:saves s))
                         (pr-str (sort (:feature-names s))))))
      (catch Throwable e
        (println (format "  %-10s BUILD ERROR: %s" (name k) (.getMessage e)))))))

(deftest ^:diagnostic dump-fighter-details
  (doseq [lvl [5 17]]
    (println (format "\n=== FIGHTER @ %d — feature details (summary / frequency) ===" lvl))
    (doseq [[n {:keys [summary frequency]}] (:details (snapshot :fighter lvl))]
      (println (format "   %-14s freq=%s  | %s" n (pr-str frequency) summary)))))
