(ns orcpub.dnd.e5.demo-content-build-test
  "The demo pack's job #2: it doubles as a built-in test that its content actually
   works in a built character, not just that it loads. This builds a real character
   that USES a demo item and reads the result off the derived sheet — the same
   entity/build path the app runs, on the JVM under `lein test`.

   It pulls the content straight from orcpub.dnd.e5.demo-content/plugins (the source
   the bundled pack is generated from), so growing the pack grows the coverage.
   Mirrors the plugin merge the app does in spell_subs.cljs (plugin spells fold into
   the spells map and their :spell-lists fold into the class spell lists) so the
   build sees exactly what a user's builder would. Copy of the divine_soul e2e
   pattern."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.demo-content :as demo]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def demo-spells
  "The demo pack's spells, {key spell}, taken from the recipe."
  (get-in demo/plugins [demo/source-name ::e5/spells]))

;; --- reproduce the app's pure plugin-spell merge (spell_subs.cljs) ---

(def spells-map
  ;; ::spells5e/spells-map — plugin spells assoc'd into the spell map by key.
  (reduce-kv (fn [m k spell] (assoc m (or (:key spell) k) spell))
             spells5e/spell-map
             demo-spells))

(def spell-lists
  ;; ::spells5e/spell-lists merged with ::spells5e/plugin-spell-lists: each demo
  ;; spell's :spell-lists {class true} folds its key into [class level] of the list.
  (let [plugin-lists (reduce (fn [lists {:keys [key level spell-lists]}]
                               (reduce-kv (fn [l k v]
                                            (if v (update-in l [k level] conj key) l))
                                          lists
                                          spell-lists))
                             {}
                             (vals demo-spells))]
    (merge-with (fn [& ls] (apply merge-with concat ls))
                sl5e/spell-lists
                plugin-lists)))

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

(def wizard-option
  (classes5e/wizard-option spell-lists spells-map {} language-map weapons5e/weapons-map))

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map weapons5e/weapons
    spell-lists spells-map
    []                 ; backgrounds
    []                 ; races
    [wizard-option]    ; classes
    []                 ; feats
    language-map)))

;; A level-1 wizard who takes the demo cantrip. Cantrip/spell choices live at the
;; class root (their selection carries a :ref that re-roots the path — see the
;; divine_soul test note), not under :levels.
(def wizard-entity
  {:orcpub.entity/options
   {:ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value {:orcpub.dnd.e5.character/str 10
                           :orcpub.dnd.e5.character/dex 10
                           :orcpub.dnd.e5.character/con 10
                           :orcpub.dnd.e5.character/int 16
                           :orcpub.dnd.e5.character/wis 10
                           :orcpub.dnd.e5.character/cha 10}}
    :class
    [{:orcpub.entity/key :wizard
      :orcpub.entity/options
      {:wizard-cantrips-known
       [{:orcpub.entity/key :demo-spark}
        {:orcpub.entity/key :fire-bolt}
        {:orcpub.entity/key :light}]
       :levels
       [{:orcpub.entity/key :level-1
         :orcpub.entity/options
         {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 6}}}]}}]}})

(defn known-spell-keys [built]
  ;; spells-known is {spell-level {[class-name spell-key] entry}}; cantrips are level 0.
  (set (map second (mapcat keys (vals (char5e/spells-known built))))))

(deftest demo-cantrip-is-offered-to-a-wizard
  (testing "the demo spell folds into the wizard cantrip list — it can't be known if it isn't offered"
    (is (contains? (set (get-in spell-lists [:wizard 0])) :demo-spark)
        "demo-spark is on the wizard level-0 (cantrip) list after the plugin merge")
    (is (not (contains? (set (get-in sl5e/spell-lists [:wizard 0])) :demo-spark))
        "and it is NOT in the base SRD list — so it came from the demo pack, not SRD")))

(deftest built-wizard-knows-the-demo-cantrip
  (testing "a level-1 wizard who picks the demo cantrip actually knows it on the derived sheet — full build, JVM"
    (let [built (entity/build wizard-entity test-template)
          known (known-spell-keys built)]
      (is (some? built) "build must not throw")
      (is (contains? known :demo-spark)
          "the demo cantrip is known on the built character — the pack produces a working character"))))

;; --- the demo feat: exercises the ability-increase spread (fixed + floating) ---

(def CON :orcpub.dnd.e5.character/con)
(def DEX :orcpub.dnd.e5.character/dex)

(def demo-feat-cfg (get-in demo/plugins [demo/source-name ::e5/feats :demo-tough]))

(def demo-feat-option
  (opt5e/feat-option-from-cfg language-map spells-map spell-lists
                              weapons5e/weapons-map {} {} demo-feat-cfg))

(def feat-template
  (t5e/template
   (concat
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     spell-lists spells-map [] [] [] [] language-map)
    ;; the demo feat as a direct feat selection
    [(t/selection-cfg {:name "Bonus Feat" :key :bonus-feat :tags #{:feats}
                       :options [demo-feat-option] :min 1 :max 1})])))

;; A character who takes the demo feat and puts its floating +1 on DEX. The feat's
;; spread is [[1 :con] [1 :any]]: increment 0 is fixed CON, increment 1 floats
;; (options keyed asi-1-<ability>).
(def feat-entity
  {:orcpub.entity/options
   {:ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value (zipmap char5e/ability-keys (repeat 10))}
    :bonus-feat
    {:orcpub.entity/key :demo-tough
     :orcpub.entity/options {:asi [{:orcpub.entity/key :asi-1-dex}]}}}})

(deftest built-character-gets-the-demo-feat-ability-increases
  (testing "a character who takes the demo feat has its fixed +1 CON AND the chosen floating +1 on the derived sheet"
    (let [built (entity/build feat-entity feat-template)
          a (char5e/ability-values built)]
      (is (some? built) "build must not throw")
      (is (= 11 (get a CON)) "fixed +1 CON from the demo feat's spread")
      (is (= 11 (get a DEX)) "the floating +1 the player assigned to DEX")
      ;; feat ASIs are non-racial ('other' column), never racial
      (is (zero? (get (char5e/race-ability-increases built) CON 0))
          "the feat's CON increase is not attributed as racial"))))

;; --- the demo race: ASI spread (race silo) + save proficiencies + :props ---

(def STR :orcpub.dnd.e5.character/str)
(def FIRE :fire)
(def base-10 (zipmap char5e/ability-keys (repeat 10)))

(def demo-race-cfg (get-in demo/plugins [demo/source-name ::e5/races :demo-tideborn]))

;; Compile the race exactly as ::races5e/plugin-races does: ability/save grants via
;; compile-ability-grants (default :race attribution) + :props via plugin-modifiers.
(def demo-race-option
  (let [{ai-mods :modifiers ai-sels :selections} (opt5e/compile-ability-grants demo-race-cfg)]
    (t/option-cfg {:name (:name demo-race-cfg) :key (:key demo-race-cfg)
                   :modifiers (concat (opt5e/plugin-modifiers (:props demo-race-cfg) (:key demo-race-cfg))
                                      ai-mods)
                   :selections ai-sels})))

(def race-template
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             spell-lists spells-map [] [] [] [] language-map)
    [(t/selection-cfg {:name "Race" :key :race :tags #{:race}
                       :options [demo-race-option] :min 1 :max 1})])))

;; Spread [[2 :dex] [1 :any]]: idx 0 fixed +2 DEX; idx 1 floats -> asi-1-<ability>, put on STR.
(def race-entity
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value base-10}
    :race {:orcpub.entity/key :demo-tideborn
           :orcpub.entity/options {:asi [{:orcpub.entity/key :asi-1-str}]}}}})

(deftest built-character-gets-the-demo-race-grants
  (testing "a character of the demo race has its spread ASI (race column), the standalone CON save, the fire resistance, and the swim speed on the derived sheet"
    (let [built (entity/build race-entity race-template)
          a (char5e/ability-values built)]
      (is (some? built) "build must not throw")
      (is (= 12 (get a DEX)) "fixed +2 DEX from the race spread")
      (is (= 11 (get a STR)) "the floating +1 put on STR")
      (is (= 2 (get (char5e/race-ability-increases built) DEX 0))
          "the fixed +2 DEX is attributed to the RACE column")
      (is (contains? (set (char5e/saving-throws built)) CON)
          "the standalone :save-proficiencies granted a CON save")
      (is (contains? (set (map :value (char5e/damage-resistances built))) FIRE)
          "the :props damage-resistance granted fire resistance")
      (is (= 30 (char5e/base-swimming-speed built))
          "the :props swimming-speed lands"))))

;; --- the demo background: :save rider, attributed to :general (not racial) ---

(def demo-bg-cfg (get-in demo/plugins [demo/source-name ::e5/backgrounds :demo-traveler]))

(def demo-bg-option
  (let [{m :modifiers s :selections} (opt5e/compile-ability-grants demo-bg-cfg {:attribution :general})]
    (t/option-cfg {:name (:name demo-bg-cfg) :key (:key demo-bg-cfg) :modifiers m :selections s})))

(def bg-template
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             spell-lists spells-map [] [] [] [] language-map)
    [(t/selection-cfg {:name "Background" :key :background :tags #{:background}
                       :options [demo-bg-option] :min 1 :max 1})])))

(def bg-entity
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value base-10}
    :background {:orcpub.entity/key :demo-traveler}}})

(deftest built-character-gets-the-demo-background-save-rider
  (testing "the background's [[1 :con :save]] applies +1 CON AND a CON save — attributed to :general, so it is NOT a racial increase"
    (let [built (entity/build bg-entity bg-template)
          a (char5e/ability-values built)]
      (is (some? built) "build must not throw")
      (is (= 11 (get a CON)) "fixed +1 CON from the rider increment")
      (is (contains? (set (char5e/saving-throws built)) CON)
          "the :save rider granted the CON save")
      (is (zero? (get (char5e/race-ability-increases built) CON 0))
          "a background ASI is :general, never racial"))))

;; --- the demo feat's generic :grant from the built-in fighting-styles pool ---

(def grantable-pools
  {:fighting-styles {:name "Fighting Style" :options opt5e/fighting-style-options}})

(def demo-grant-feat-cfg (get-in demo/plugins [demo/source-name ::e5/feats :demo-versatile]))

(def demo-grant-feat-option
  (opt5e/feat-option-from-cfg language-map spells-map spell-lists
                              weapons5e/weapons-map {} grantable-pools demo-grant-feat-cfg))

(def grant-template
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             spell-lists spells-map [] [] [] [] language-map)
    [(t/selection-cfg {:name "Bonus Feat" :key :bonus-feat :tags #{:feats}
                       :options [demo-grant-feat-option] :min 1 :max 1})])))

(def grant-entity
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value base-10}
    :bonus-feat {:orcpub.entity/key :demo-versatile
                 :orcpub.entity/options {:fighting-style {:orcpub.entity/key :archery}}}}})

(deftest demo-feat-grant-offers-the-pool-and-builds
  (testing "the feat's :grant compiles to a choice offering the built-in fighting styles, and a character who takes the feat and picks one builds"
    (let [fs-sel (first (filter #(= "Fighting Style" (::t/name %))
                                (::t/selections demo-grant-feat-option)))]
      (is (some? fs-sel) "the feat carries the granted Fighting Style selection")
      (is (contains? (set (map ::t/name (::t/options fs-sel))) "Archery")
          "the grant offers the built-in Archery style (the app-wired pool)"))
    (is (some? (entity/build grant-entity grant-template))
        "a character who takes the feat and picks Archery builds without throwing")))
