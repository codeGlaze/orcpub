(ns orcpub.dnd.e5.weapon-ability-modifier-test
  "The ability modifier an attack uses, as the mastery band reads it: Str or Dex by the weapon,
   the better for a finesse weapon, and no damage bonus in it (a stand-in for Dueling's +2)."
  (:require [clojure.test :refer [deftest is]]
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

(def ^:private damage-bonus
  "A selection standing in for Dueling: +2 to every weapon's damage."
  (t/selection-cfg {:name "Damage Bonus" :tags #{}
                    :options [(t/option-cfg {:name "Plus Two"
                                             :modifiers [(mod5e/damage-bonus-fn (constantly 2))]})]}))

(def ^:private the-template
  (delay
   (t5e/template
    (conj (t5e/template-selections
           nil nil nil weapons5e/weapons-map weapons5e/weapons
           sl5e/spell-lists spells5e/spell-map [] []
           [(classes5e/monk-option sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)]
           [] language-map)
          damage-bonus))))

(defn- build
  "A level 1 monk with Str `str` and Dex `dex`, and the +2 damage bonus."
  [str dex]
  (entity/build
   {::entity/options
    {:ability-scores {::entity/key :standard-roll
                      ::entity/value {::char5e/str str ::char5e/dex dex ::char5e/con 12
                                      ::char5e/int 10 ::char5e/wis 10 ::char5e/cha 10}}
     :class [{::entity/key :monk
              ::entity/options {:levels [{::entity/key :level-1
                                          ::entity/options {:hit-points {::entity/key :average ::entity/value 5}}}]}}]
     :damage-bonus {::entity/key :plus-two}}}
   @the-template))

(deftest the-attack-uses-its-ability-not-its-damage
  (let [built (build 16 12)
        ability (char5e/best-weapon-ability-modifier-fn built)
        damage (char5e/best-weapon-damage-modifier-fn built)
        longsword (weapons5e/weapons-map :longsword)]
    (is (= 5 (damage longsword)) "the damage modifier has the +2 in it")
    (is (= 3 (ability longsword)) "Str 16, nothing else")))

(deftest a-finesse-weapon-takes-the-better-ability
  (let [ability (char5e/best-weapon-ability-modifier-fn (build 10 18))]
    (is (= 4 (ability (weapons5e/weapons-map :rapier))) "Dex 18")
    (is (= 0 (ability (weapons5e/weapons-map :longsword))) "Str 10, the longsword is not finesse")))
