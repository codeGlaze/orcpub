(ns orcpub.dnd.e5.hidden-pick-tools-equipment-research-test
  "Research pins for `hidden-selection-picks.md`, class tools and starting equipment: a pick stored
   while its class was first, built after the class order changes. Each deftest names its claim
   (D1–D4 of plan-hidden-pick-fix-and-grant-fields.md, Step 5) and asserts what the build does today."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.set :as set]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private abilities
  {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
   ::char5e/int 13 ::char5e/wis 13 ::char5e/cha 13})

(defn- class-opts []
  (mapv #(% sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
        [classes5e/fighter-option classes5e/rogue-option classes5e/bard-option
         classes5e/cleric-option classes5e/monk-option classes5e/sorcerer-option]))

(def ^:private the-template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [] [] (class-opts) [] language-map))))

(defn- lvl [n] {::entity/key (keyword (str "level-" n))
                ::entity/options {:hit-points {::entity/key :average ::entity/value 5}}})

(defn- cls [k opts] {::entity/key k ::entity/options (assoc opts :levels [(lvl 1)])})
(defn- pick [k] [{::entity/key k}])
(defn- picks [& ks] (mapv (fn [k] {::entity/key k}) ks))
(defn- nested [k sel-k & ks] [{::entity/key k ::entity/options {sel-k (apply picks ks)}}])

(def ^:private instruments-sel (keyword "tool-selection--:musical-instruments"))
(def ^:private mc-instruments-sel (keyword "multiclass-tool-selection--:musical-instruments"))

(defn- char-of [classes]
  {::entity/options {:ability-scores {::entity/key :standard-roll ::entity/value abilities}
                     :class classes}})

(defn- build [classes] (entity/build (char-of classes) @the-template))

(defn- offered-paths [classes]
  (->> (entity/get-all-selections-2 @the-template (entity/make-path-map (char-of classes))
                                    (build classes))
       (map ::entity/path)
       set))

(defn- tools-of [classes] (set (keys (char5e/tool-proficiencies (build classes)))))

(defn- inventory [built]
  (set (concat (keys (char5e/normal-weapons-inventory built))
               (keys (char5e/normal-armor-inventory built))
               (keys (char5e/normal-equipment-inventory built)))))

(defn- inv-of [classes] (inventory (build classes)))

(def ^:private fighter-plain (cls :fighter {}))

;; ---------------------------------------------------------------- D1: tools

(def ^:private bard-tool-first (cls :bard {instruments-sel (picks :lute :flute :drum)}))
(def ^:private bard-tool-mc    (cls :bard {mc-instruments-sel (pick :viol)}))
(def ^:private monk-tool-first
  (cls :monk {:tool-selection (nested :musical-instruments instruments-sel :horn)}))

(deftest d1-first-class-tool-pick-survives-class-moving-second
  ;; D1 first-class arm: CONFIRMED. modifiers/tool-proficiency is built without conditions.
  (testing "control: single class keeps its tools"
    (is (= #{:lute :flute :drum} (tools-of [bard-tool-first])))
    (is (= #{:horn} (tools-of [monk-tool-first]))))
  (testing "class first in a multiclass: tools apply"
    (is (= #{:lute :flute :drum} (tools-of [bard-tool-first fighter-plain]))))
  (testing "class moved second: the first-class picks still apply"
    (is (= #{:lute :flute :drum} (tools-of [fighter-plain bard-tool-first])))
    (is (= #{:horn} (tools-of [fighter-plain monk-tool-first])))))

(deftest d1-first-class-tool-control-is-hidden-when-second
  ;; D1 first-class arm: CONFIRMED. The stored pick has no control once the class is second.
  (is (contains? (offered-paths [bard-tool-first]) [:class :bard instruments-sel]))
  (is (not (contains? (offered-paths [fighter-plain bard-tool-first]) [:class :bard instruments-sel])))
  (is (not (contains? (offered-paths [fighter-plain monk-tool-first]) [:class :monk :tool-selection]))))

(deftest d1-multiclass-tool-pick-survives-class-moving-first
  ;; D1 multiclass arm: CONFIRMED. The nested "Tool Proficiency" pick applies with bard first.
  (is (= #{:viol} (tools-of [fighter-plain bard-tool-mc])))
  (is (= #{:viol} (tools-of [bard-tool-mc])))
  (is (= #{:viol} (tools-of [bard-tool-mc fighter-plain]))))

;; ---------------------------------------------------------------- D2: the multiclass tool gate

(defn- bard-selection-gate [sel-k]
  (let [bard (some #(when (= :bard (::t/key %)) %) (class-opts))]
    (some #(when (= sel-k (::t/key %)) (::t/prereq-fn %)) (::t/selections bard))))

(deftest d2-raw-classes-vs-character-classes
  ;; D2: REFUTED. (:classes c) and character/classes return the same vector on a built character.
  (doseq [classes [[bard-tool-mc] [bard-tool-mc fighter-plain] [fighter-plain bard-tool-mc]]]
    (let [built (build classes)]
      (is (= (char5e/classes built) (:classes built)) (pr-str (map ::entity/key classes))))))

(deftest d2-multiclass-tool-gate-agrees-with-first-class-gate
  ;; D2: REFUTED. The multiclass tool arm is offered exactly when the first-class arm is not.
  (let [mc-gate (bard-selection-gate mc-instruments-sel)
        fc-gate (bard-selection-gate instruments-sel)]
    (doseq [[classes mc? fc?] [[[bard-tool-mc] false true]
                               [[bard-tool-mc fighter-plain] false true]
                               [[fighter-plain bard-tool-mc] true false]]]
      (let [built (build classes)
            paths (offered-paths classes)]
        (is (= mc? (boolean (mc-gate built))))
        (is (= fc? (boolean (fc-gate built))))
        (is (= mc? (contains? paths [:class :bard mc-instruments-sel])))
        (is (= fc? (contains? paths [:class :bard instruments-sel])))))))

;; ---------------------------------------------------------------- D3: chosen starting equipment

(def ^:private d3-cases
  "[label class-kw stored-picks] — each pick is a chosen starting item."
  [["weapon pick (weapon-option-2)" :bard {:starting-equipment-weapon (pick :rapier)}]
   ["any simple weapon (weapon-option-2 :simple -> weapon-options)" :bard
    {:starting-equipment-weapon (nested :any-simple-weapon :starting-equipment-simple-weapon :club)}]
   ["armor pick (armor-option)" :cleric {:starting-equipment-armor (pick :scale-mail)}]
   ["equipment pack (equipment-option :items)" :bard
    {:starting-equipment-equipment-pack (pick :diplomats-pack)}]
   ["musical instrument (equipment-option plain)" :bard
    {:starting-equipment-musical-instrument (pick :shawm)}]
   ["arcane focus group (equipment-option :values, nested)" :sorcerer
    {:starting-equipment-spellcasting-equipment (nested :arcane-focus :arcane-focus :crystal)}]
   ["holy symbol (classes.cljc hand-built, starting-equipment-option)" :cleric
    {:starting-equipment-holy-symbol (pick :amulet)}]
   ["hand-built weapon bundle (classes.cljc :selections)" :cleric
    {:starting-equipment-additional-weapon (pick :light-crossbow-and-20-bolts)}]])

(defn- gained
  "Items the picks add when their class is the only class."
  [cls-kw stored]
  (set/difference (inv-of [(cls cls-kw stored)]) (inv-of [(cls cls-kw {})])))

(deftest d3-chosen-starting-items-survive-class-moving-second
  ;; D3: CONFIRMED for every case. modifiers/weapon, armor, equipment are map-mods with no condition.
  (doseq [[label cls-kw stored] d3-cases]
    (let [g (gained cls-kw stored)]
      (testing label
        (is (seq g) "control: the pick adds something with its class alone")
        (is (set/subset? g (inv-of [(cls cls-kw stored) fighter-plain])) "class first")
        (is (set/subset? g (inv-of [fighter-plain (cls cls-kw stored)]))
            "class second: still applies")))))

(deftest d3-chosen-starting-controls-are-hidden-when-second
  ;; D3: CONFIRMED. Every case loses its control when its class moves second.
  (doseq [[label cls-kw stored] d3-cases
          :let [path [:class cls-kw (key (first stored))]]]
    (testing label
      (is (contains? (offered-paths [(cls cls-kw stored)]) path))
      (is (not (contains? (offered-paths [fighter-plain (cls cls-kw stored)]) path))))))

(deftest d3-no-built-in-class-uses-equipment-selections
  ;; D3 rich :equipment-selections: no built-in class carries the shape, so that sub-case is skipped.
  (is (not (re-find #":equipment-selections" (slurp "src/cljc/orcpub/dnd/e5/classes.cljc")))))

;; ---------------------------------------------------------------- D4: fixed starting gear

(defn- class-opt [k] (some #(when (= k (::t/key %)) %) (class-opts)))

(def ^:private rogue-fixed #{:dagger :leather :thieves-tools})
(def ^:private bard-fixed #{:dagger :leather})

(defn- stored-gear [c]
  (set (for [k [:weapons :armor :equipment]
             e (get-in c [::entity/options k])
             :when (get-in e [::entity/value ::char-equip/class-starting-equipment?])]
         (::entity/key e))))

(defn- swap-classes [c] (update-in c [::entity/options :class] (comp vec reverse)))

(deftest d4-fixed-gear-is-stored-data-written-by-set-class-at-index-0
  ;; D4: REFUTED as stated. set-class writes fixed gear into the character only at index 0; the
  ;; build never consults class order, so after a reorder the gear stays with the old first class.
  (let [base   (assoc-in (char-of []) [::entity/options :class] [])
        rogue0 (char5e/set-class base :rogue 0 (class-opt :rogue))
        both   (char5e/set-class rogue0 :bard 1 (class-opt :bard))
        moved  (swap-classes both)]
    (testing "set-class at index 0 stores the class's fixed gear; at index 1 it stores none"
      (is (= rogue-fixed (stored-gear rogue0)))
      (is (= rogue-fixed (stored-gear both))))
    (testing "built: rogue first carries rogue's fixed gear"
      (is (= [:rogue :bard] (char5e/classes (entity/build both @the-template))))
      (is (set/subset? rogue-fixed (inventory (entity/build both @the-template)))))
    (testing "reordered so bard is first: rogue's fixed gear, thieves' tools included, stays"
      (is (= [:bard :rogue] (char5e/classes (entity/build moved @the-template))))
      (is (set/subset? rogue-fixed (inventory (entity/build moved @the-template))))
      (is (= rogue-fixed (stored-gear moved))))
    (testing "set-class at index 0 replaces the old first class's fixed gear"
      (is (= bard-fixed (stored-gear (char5e/set-class rogue0 :bard 0 (class-opt :bard))))))))
