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
            [clojure.string :as str]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.units :as units5e]
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

(deftest rogue-sneak-attack-detail-baseline
  (testing "rogue Sneak Attack — exact summary (incl. source typo) + once/turn frequency"
    (is (= {:summary "3d6 extra damage on attack where you have advantage or another enemy of creature is within 5 ft."
            :frequency {:units :orcpub.dnd.e5.units/turn :amount 1}}
           (feat-detail :rogue 5 "Sneak Attack")))
    (is (= "6d6 extra damage on attack where you have advantage or another enemy of creature is within 5 ft."
           (:summary (feat-detail :rogue 11 "Sneak Attack")))
        "dice scale to round-up(level/2)")))

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

;; ===========================================================================
;; STEP 2 — `compile-feature`: the data → cfg compiler (proof of concept).
;;
;; The catch (see docs/kb/class-features-and-mechanization.md "code-capture catch"):
;; a real feature's :frequency/:summary are CODE, not data — the fighter writes
;;   :frequency (units5e/rests (if (>= (?class-level :fighter) 17) 2 1))
;;   :summary   (str "regain 1d10 " (common/mod-str (?class-level :fighter)) " HPs")
;; and the rogue writes
;;   :summary   (str (common/round-up (/ (?class-level :rogue) 2)) "d6 extra damage ...")
;; where ?class-level is an entity-spec attr resolved at build time. To make features
;; data-addressable AND overridable, a compile step turns a DATA spec + the granting class's
;; level into the same cfg the action/bonus-action/dependent-trait macros already consume.
;;
;; The DATA shape (corrected from the first sketch):
;;   - NO class named in the feature. The level is supplied by whoever grants it, so a custom
;;     class can grant the same feature and feed its own level. (De-siloing's whole point.)
;;   - :effect is a MAP {:kind ... + params}, not a [kind params] tuple — a tuple can't
;;     deep-merge its inner params, and override IS a deep-merge. The map makes
;;     {:overrides {:effect {:die "d8"}}} change one field and nothing else.
;;   - :text is a fillable template; {name} prints a value, {+name} signs it (via mod-str).
;;     One rule, because a heal bonus signs ("+5") but a dice count does not ("3d6").
;;   - scaling lives as DATA: a {level→n} schedule resolved by level-lookup (runtime analogue
;;     of the level-val macro). Arithmetic scaling (rogue's round-up(level/2)) is expressed as
;;     the equivalent threshold table; a formula form would compact it (noted, not built).
;;
;; Proven below: the compiled use-count, frequency, AND rendered summary match the REAL built
;; fighter (Action Surge, Second Wind) and rogue (Sneak Attack); a :uses override changes only
;; the count; and a :die override changes the rendered summary (3d6 → 3d8) and nothing else.
;; A prototype in the test ns, validated against the real build; touches no class source.
;;
;; SCOPE — this is a deliberate SLICE, not the whole compiler. The per-class catalogue
;; (docs/kb/class-feature-catalogue.md, all 10 classes) surfaced cases compile-feature does NOT
;; yet handle, and that the registry will need before extracting the resource classes:
;;   - :uses from sources beyond a level schedule — ability-modifier-derived (Bardic = max 1 CHA),
;;     formula (Lay on Hands = 5*level), or :level itself (ki/sorcery points);
;;   - class-wide resource POOLS (ki/sorcery/Lay-on-Hands) spent by many features — a separate
;;     mechanism (roadmap B3), not per-feature :frequency;
;;   - summaries that interpolate the BUILD CONTEXT (?spell-save-dc, ?ability-bonuses, user
;;     selections like ?ranger-favored-enemies), not just the feature's own params;
;;   - multi-part features (Aura of Protection, Martial Arts) -> compile to a SEQ of modifiers.
;; Fighter/rogue are the clean, self-contained cases; start extraction here, defer monk/paladin.
;; ===========================================================================

(defn- deep-merge [a b]
  (merge-with (fn [x y] (if (and (map? x) (map? y)) (deep-merge x y) y)) a b))

(defn level-lookup
  "Runtime analogue of the compile-time `mod5e/level-val` macro: given a level and a
   threshold map {threshold value ... :default v}, return the value for the highest
   threshold <= level, else :default. Lets a schedule live as DATA instead of a hand-written
   `(if (>= (?class-level ..) 17) 2 1)` or a `level-val` table baked into code."
  [level mappings]
  (or (some (fn [[t v]] (when (>= level t) v))
            (sort-by key > (dissoc mappings :default)))
      (:default mappings)))

(def ^:private period->units
  {:rest ::units5e/rest :long-rest ::units5e/long-rest :turn ::units5e/turn})

(defn- resolve-param
  "A feature param is a literal (\"1d10\", \"d6\"), the keyword :level (the granting class's
   level), or a {level→n} schedule resolved by level-lookup."
  [v level]
  (cond
    (map? v)      (level-lookup level v)
    (= :level v)  level
    :else         v))

(defn- fill
  "Render a :text template against resolved effect params. {name} prints the value as-is;
   {+name} signs a number (via mod-str), since a heal bonus reads '+5' but a dice count '3'."
  [text vals]
  (str/replace text #"\{(\+?)([\w-]+)\}"
               (fn [[_ sign k]]
                 (let [v (get vals (keyword k))]
                   (if (= sign "+") (common/mod-str v) (str v))))))

(defn compile-feature
  "DATA feature spec + the granting class's level -> the exact cfg the action/bonus-action/
   dependent-trait macros consume ({:name :frequency :summary}). :overrides deep-merge first,
   so an editable reference ({:overrides {:effect {:die \"d8\"}}}) changes one field only."
  [spec level & [{:keys [overrides]}]]
  (let [{:keys [name per uses effect text]} (deep-merge spec overrides)
        resolved (into {} (for [[k v] (dissoc effect :kind)] [k (resolve-param v level)]))
        amount   (if (map? uses) (level-lookup level uses) uses)]
    {:name name
     :frequency (units5e/units (period->units per) amount)
     :summary   (fill text resolved)}))

(def action-surge-spec
  "Action Surge as DATA. :uses schedule replaces classes.cljc:1086's hand-written if/threshold."
  {:name   "Action Surge"
   :action :action
   :per    :rest
   :uses   {:default 1, 17 2}
   :effect {:kind :extra-action}
   :text   "take an extra action"})

(def second-wind-spec
  "Second Wind as DATA. The heal die/bonus are now FIELDS (overridable); the summary renders
   from them — {+bonus} signs the level-sourced bonus."
  {:name   "Second Wind"
   :action :bonus-action
   :per    :rest
   :uses   1
   :effect {:kind :heal :dice "1d10" :bonus :level}
   :text   "regain {dice} {+bonus} HPs"})

(def sneak-attack-spec
  "Sneak Attack as DATA. The d6 is a FIELD ({:die \"d6\"}); the dice count is the schedule
   equivalent of round-up(rogue-level/2). Overriding :die regenerates the summary."
  {:name   "Sneak Attack"
   :per    :turn
   :uses   1
   :effect {:kind :extra-damage
            :die  "d6"
            :count {1 1, 3 2, 5 3, 7 4, 9 5, 11 6, 13 7, 15 8, 17 9, 19 10}}
   :text   "{count}{die} extra damage on attack where you have advantage or another enemy of creature is within 5 ft."})

(deftest level-lookup-reproduces-level-val
  (testing "level-lookup matches the level-val threshold table used by Indomitable {13 2 17 3 :default 1}"
    (let [tbl {13 2, 17 3, :default 1}]
      (is (= 1 (level-lookup 9 tbl)))
      (is (= 2 (level-lookup 13 tbl)))
      (is (= 2 (level-lookup 16 tbl)))
      (is (= 3 (level-lookup 17 tbl)))
      (is (= 3 (level-lookup 20 tbl))))
    (is (= 3 (get-in (feat-detail :fighter 17 "Indomitable") [:frequency :amount]))
        "and the real built fighter agrees at 17")))

(deftest compile-feature-reproduces-action-surge
  (testing "the data schedule reproduces the REAL built fighter's Action Surge frequency + summary"
    (is (= (feat-detail :fighter 5 "Action Surge")
           (select-keys (compile-feature action-surge-spec 5) [:summary :frequency]))
        "1 use @5, summary 'take an extra action'")
    (is (= (feat-detail :fighter 17 "Action Surge")
           (select-keys (compile-feature action-surge-spec 17) [:summary :frequency]))
        "2 uses @17 — the schedule reproduces the hand-written if/threshold"))
  (testing "a :uses override changes ONLY the use count"
    (let [r (compile-feature action-surge-spec 5 {:overrides {:uses {:default 5}}})]
      (is (= 5 (get-in r [:frequency :amount])) "override bumps uses to 5")
      (is (= ::units5e/rest (get-in r [:frequency :units])) "units unchanged")
      (is (= "take an extra action" (:summary r)) "summary unchanged"))))

(deftest compile-feature-reproduces-second-wind
  (testing "use-count, frequency AND the level-scaled summary all match the real build"
    (is (= (feat-detail :fighter 5 "Second Wind")
           (select-keys (compile-feature second-wind-spec 5) [:summary :frequency]))
        "regain 1d10 +5 HPs @5")
    (is (= (feat-detail :fighter 17 "Second Wind")
           (select-keys (compile-feature second-wind-spec 17) [:summary :frequency]))
        "regain 1d10 +17 HPs @17")))

(deftest compile-feature-reproduces-sneak-attack-and-die-swap
  (testing "structured effect reproduces the REAL built rogue's Sneak Attack at 5 and 11"
    (is (= (feat-detail :rogue 5 "Sneak Attack")
           (select-keys (compile-feature sneak-attack-spec 5) [:summary :frequency]))
        "3d6 @5, once per turn")
    (is (= (feat-detail :rogue 11 "Sneak Attack")
           (select-keys (compile-feature sneak-attack-spec 11) [:summary :frequency]))
        "6d6 @11"))
  (testing "overriding :die regenerates the summary (3d6 -> 3d8) and changes NOTHING else"
    (let [base (compile-feature sneak-attack-spec 5)
          swap (compile-feature sneak-attack-spec 5 {:overrides {:effect {:die "d8"}}})]
      (is (= "3d8 extra damage on attack where you have advantage or another enemy of creature is within 5 ft."
             (:summary swap))
          "the die field flows through to the rendered summary")
      (is (= (:frequency base) (:frequency swap)) "frequency unchanged")
      (is (not= (:summary base) (:summary swap)) "and it actually differs from the d6 baseline"))))
