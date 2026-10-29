(ns orcpub.dnd.e5.save-dc-test
  "An item's spell save DC bonus (Robe of the Archmagi, the item builder's spell DC field) raises
   spell save DCs only. A feature whose own text gives its DC as 8 + proficiency + a modifier
   (ki, Breath Weapon) does not take it; one that says \"your spell save DC\" (Channel Divinity)
   does. Wis 16 (+3) throughout."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private item-bonus
  "A selection standing in for an attuned item: its one option carries the +2 spell save DC
   modifier the item list emits."
  (t/selection-cfg {:name "Item Bonus" :tags #{}
                    :options [(t/option-cfg {:name "Plus Two"
                                             :modifiers [(mod5e/spell-save-dc-bonus 2)]})]}))

(def ^:private the-template
  (delay
   (t5e/template
    (conj (t5e/template-selections
           nil nil nil weapons5e/weapons-map weapons5e/weapons
           sl5e/spell-lists spells5e/spell-map
           [] []
           [(classes5e/monk-option sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
            (classes5e/cleric-option sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)]
           [] language-map)
          item-bonus))))

(defn- lvl
  "Class level `n` with average hit points."
  [n] {::entity/key (keyword (str "level-" n))
                ::entity/options {:hit-points {::entity/key :average ::entity/value 5}}})

(defn- build
  "Builds a single-class character of `cls` at `levels`, with the item bonus when `bonus?`."
  [cls levels bonus?]
  (entity/build
   {::entity/options
    (cond-> {:ability-scores {::entity/key :standard-roll
                              ::entity/value {::char5e/str 10 ::char5e/dex 14 ::char5e/con 12
                                              ::char5e/int 10 ::char5e/wis 16 ::char5e/cha 10}}
             :class [{::entity/key cls
                      ::entity/options {:levels (mapv lvl (range 1 (inc levels)))}}]}
      bonus? (assoc :item-bonus {::entity/key :plus-two}))}
   @the-template))

(defn- summary
  "The summary of the feature named `nm` among the character's traits and actions."
  [built nm]
  (some #(when (= nm (:name %)) (:summary %)) (concat (char5e/traits built) (char5e/actions built))))

(deftest the-item-bonus-reaches-the-character
  (is (= 13 ((char5e/spell-save-dc-fn (build :cleric 2 false)) ::char5e/wis)) "8 + 2 + 3")
  (is (= 15 ((char5e/spell-save-dc-fn (build :cleric 2 true)) ::char5e/wis)) "and +2 from the item"))

(deftest a-ki-dc-ignores-spell-bonuses
  (testing "Stunning Strike: ki save DC = 8 + proficiency + Wis = 14 at level 5"
    (is (re-find #"DC 14 CON" (str (summary (build :monk 5 false) "Stunning Strike"))))
    (is (re-find #"DC 14 CON" (str (summary (build :monk 5 true) "Stunning Strike")))
        "an item's spell save DC bonus does not apply")))

(deftest channel-divinity-uses-the-spell-save-dc
  (testing "Turn Undead: the cleric spell save DC, item bonus included"
    (is (re-find #"DC 13 Wisdom" (str (summary (build :cleric 2 false) "Channel Divinity: Turn Undead"))))
    (is (re-find #"DC 15 Wisdom" (str (summary (build :cleric 2 true) "Channel Divinity: Turn Undead"))))))
