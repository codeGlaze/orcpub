(ns orcpub.dnd.e5.character-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.character.equipment :as equip]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.entity :as entity]))

(deftest equipment-to-namespaced
  (let [item {:name "Juancho"
              :quantity 2
              :equipped? true
              :background-starting-equipment? true
              :class-starting-equipment? false}
        expected-item {::equip/name "Juancho"
                       ::equip/quantity 2
                       ::equip/equipped? true
                       ::equip/background-starting-equipment? true
                       ::equip/class-starting-equipment? false}]
    (is (= (equip/to-namespaced item) expected-item))))

(deftest add-ability-namespaces
  (let [character {:orcpub.entity/options
                   {:ability-scores
                    {:orcpub.entity/key :standard-roll,
                     :orcpub.entity/value
                     {:str 15,
                      :dex 14,
                      :con 13,
                      :int 12,
                      :wis 10,
                      :cha 8}}}}
        expected {:orcpub.entity/options
                  {:ability-scores
                   {:orcpub.entity/key :standard-roll,
                    :orcpub.entity/value
                    {:orcpub.dnd.e5.character/str 15,
                     :orcpub.dnd.e5.character/dex 14,
                     :orcpub.dnd.e5.character/con 13,
                     :orcpub.dnd.e5.character/int 12,
                     :orcpub.dnd.e5.character/wis 10,
                     :orcpub.dnd.e5.character/cha 8}}}}]
    (is (= (char/add-ability-namespaces character) expected))))

(deftest unnamespaced-character
  (let [character {:orcpub.entity/options
                   {:magic-armor
                    [{:orcpub.entity/key :animated-shield,
                      :orcpub.entity/value {:quantity 1, :equipped? true}}]}}
        character-2 {:orcpub.entity/values
                     {:custom-treasure [],
                      :custom-equipment
                      [{:name "Scoll of Pedigree",
                        :quantity 1,
                        :equipped? true,
                        :background-starting-equipment? true}], 
                      :character-name "Hcak"}}
        character-3 {:orcpub.entity/options
                     {:ability-scores
                      {:orcpub.entity/key :standard-roll,
                       :orcpub.entity/value
                       {:str 15,
                        :dex 14,
                        :con 13,
                        :int 12,
                        :wis 10,
                        :cha 8}}}}
        namespaced {:orcpub.entity/options
                    {:magic-armor
                     [{:orcpub.entity/key :animated-shield,
                       :orcpub.entity/value {::equip/quantity 1,
                                             ::equip/:equipped? true}}]
                     :ability-scores
                     {:orcpub.entity/key :standard-roll,
                      :orcpub.entity/value
                      {:orcpub.dnd.e5.character/str 15
                       :orcpub.dnd.e5.character/dex 14,
                       :orcpub.dnd.e5.character/con 13,
                       :orcpub.dnd.e5.character/int 12,
                       :orcpub.dnd.e5.character/wis 10,
                       :orcpub.dnd.e5.character/cha 8}}}
                    :orcpub.entity/values
                     {::char/custom-treasure [],
                      ::char/custom-equipment
                      [{:name "Scoll of Pedigree",
                        :quantity 1,
                        :equipped? true,
                        :background-starting-equipment? true}], 
                      ::char/character-name "Hcak"}}]
    (is (spec/valid? ::char/unnamespaced-character character))
    (is (spec/valid? ::char/unnamespaced-character character-2))
    (is (spec/valid? ::char/unnamespaced-character character-3))
    (is (not (spec/valid? ::char/unnamespaced-character namespaced)))))

(deftest fix-quantities
  (let [character {:orcpub.entity/options {:weapons [{:orcpub.entity/key :javelin, :orcpub.entity/value {:orcpub.dnd.e5.character.equipment/quantity 4, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/class-starting-equipment? true}} {:orcpub.entity/key :handaxe, :orcpub.entity/value {:orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/quantity "2"}} {:orcpub.entity/key :greataxe, :orcpub.entity/value {:orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}}]}}
        expected {:orcpub.entity/options {:weapons [{:orcpub.entity/key :javelin, :orcpub.entity/value {:orcpub.dnd.e5.character.equipment/quantity 4, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/class-starting-equipment? true}} {:orcpub.entity/key :handaxe, :orcpub.entity/value {:orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/quantity 2}} {:orcpub.entity/key :greataxe, :orcpub.entity/value {:orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}}]}}]
    (is (= (char/fix-quantities character) expected))))


(deftest strict-round-trip
  (let [strict {:db/id 17592186056152, :orcpub.entity.strict/selections [{:db/id 17592186056153, :orcpub.entity.strict/key :ability-scores, :orcpub.entity.strict/option {:db/id 17592186056154, :orcpub.entity.strict/key :standard-scores, :orcpub.entity.strict/map-value {:db/id 17592186056155, :orcpub.dnd.e5.character/str 15, :orcpub.dnd.e5.character/dex 14, :orcpub.dnd.e5.character/con 13, :orcpub.dnd.e5.character/int 12, :orcpub.dnd.e5.character/wis 10, :orcpub.dnd.e5.character/cha 8}}} {:db/id 17592186056156, :orcpub.entity.strict/key :class, :orcpub.entity.strict/options [{:db/id 17592186056157, :orcpub.entity.strict/key :barbarian, :orcpub.entity.strict/selections [{:db/id 17592186056158, :orcpub.entity.strict/key :levels, :orcpub.entity.strict/options [{:db/id 17592186056159, :orcpub.entity.strict/key :level-1}]}]}]} {:db/id 17592186056160, :orcpub.entity.strict/key :weapons, :orcpub.entity.strict/options [{:db/id 17592186056161, :orcpub.entity.strict/key :javelin, :orcpub.entity.strict/map-value {:db/id 17592186056162, :orcpub.dnd.e5.character.equipment/quantity 4, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/class-starting-equipment? true}}]} {:db/id 17592186056163, :orcpub.entity.strict/key :equipment, :orcpub.entity.strict/options [{:db/id 17592186056164, :orcpub.entity.strict/key :explorers-pack, :orcpub.entity.strict/map-value {:db/id 17592186056165, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/class-starting-equipment? true}}]}]}
        round-trip (-> strict char/from-strict char/to-strict)]
    (is (= strict round-trip))))

(deftest strict-round-trip-2
  (let [strict {:db/id 17592186056344, :orcpub.entity.strict/owner "larry", :orcpub.entity.strict/values {:db/id 17592186056463, :orcpub.dnd.e5.character/custom-equipment [{:db/id 17592186056464, :orcpub.dnd.e5.character.equipment/name "Scroll of Pedigree", :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}]}, :orcpub.entity.strict/selections [{:db/id 17592186056345, :orcpub.entity.strict/key :magic-armor, :orcpub.entity.strict/options [{:db/id 17592186056346, :orcpub.entity.strict/key :armor-of-resistance-half-plate, :orcpub.entity.strict/map-value {:db/id 17592186056347, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}}]} {:db/id 17592186056348, :orcpub.entity.strict/key :weapons, :orcpub.entity.strict/options [{:db/id 17592186056349, :orcpub.entity.strict/key :halberd, :orcpub.entity.strict/map-value {:db/id 17592186056350, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}} {:db/id 17592186056351, :orcpub.entity.strict/key :greataxe, :orcpub.entity.strict/map-value {:db/id 17592186056352, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}}]} {:db/id 17592186056353, :orcpub.entity.strict/key :race, :orcpub.entity.strict/option {:db/id 17592186056354, :orcpub.entity.strict/key :human, :orcpub.entity.strict/selections [{:db/id 17592186056355, :orcpub.entity.strict/key :subrace, :orcpub.entity.strict/option {:db/id 17592186056356, :orcpub.entity.strict/key :damaran}} {:db/id 17592186056357, :orcpub.entity.strict/key :variant, :orcpub.entity.strict/option {:db/id 17592186056358, :orcpub.entity.strict/key :standard-human}}]}} {:db/id 17592186056359, :orcpub.entity.strict/key :ability-scores, :orcpub.entity.strict/option {:db/id 17592186056360, :orcpub.entity.strict/key :standard-roll, :orcpub.entity.strict/map-value {:db/id 17592186056361, :orcpub.dnd.e5.character/str 18, :orcpub.dnd.e5.character/dex 7, :orcpub.dnd.e5.character/con 13, :orcpub.dnd.e5.character/int 12, :orcpub.dnd.e5.character/wis 8, :orcpub.dnd.e5.character/cha 9}}} {:db/id 17592186056362, :orcpub.entity.strict/key :alignment, :orcpub.entity.strict/option {:db/id 17592186056363, :orcpub.entity.strict/key :lawful-evil}} {:db/id 17592186056364, :orcpub.entity.strict/key :other-magic-items, :orcpub.entity.strict/options [{:db/id 17592186056365, :orcpub.entity.strict/key :amulet-of-the-planes, :orcpub.entity.strict/map-value {:db/id 17592186056366, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}}]} {:db/id 17592186056367, :orcpub.entity.strict/key :background, :orcpub.entity.strict/option {:db/id 17592186056368, :orcpub.entity.strict/key :noble, :orcpub.entity.strict/selections [{:db/id 17592186056369, :orcpub.entity.strict/key :noble-feature, :orcpub.entity.strict/option {:db/id 17592186056370, :orcpub.entity.strict/key :retainers}}]}} {:db/id 17592186056371, :orcpub.entity.strict/key :equipment, :orcpub.entity.strict/options [{:db/id 17592186056372, :orcpub.entity.strict/key :clothes-fine, :orcpub.entity.strict/map-value {:db/id 17592186056373, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}} {:db/id 17592186056374, :orcpub.entity.strict/key :signet-ring, :orcpub.entity.strict/map-value {:db/id 17592186056375, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}} {:db/id 17592186056376, :orcpub.entity.strict/key :purse, :orcpub.entity.strict/map-value {:db/id 17592186056377, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}}]} {:db/id 17592186056378, :orcpub.entity.strict/key :treasure, :orcpub.entity.strict/options [{:db/id 17592186056379, :orcpub.entity.strict/key :gp, :orcpub.entity.strict/map-value {:db/id 17592186056380, :orcpub.dnd.e5.character.equipment/quantity 25, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}}]} {:db/id 17592186056381, :orcpub.entity.strict/key :feats, :orcpub.entity.strict/options [{:db/id 17592186056382, :orcpub.entity.strict/key :ritual-caster, :orcpub.entity.strict/selections [{:db/id 17592186056383, :orcpub.entity.strict/key :ritual-caster-spell-class, :orcpub.entity.strict/option {:db/id 17592186056384, :orcpub.entity.strict/key :druid, :orcpub.entity.strict/selections [{:db/id 17592186056385, :orcpub.entity.strict/key :level-1-ritual, :orcpub.entity.strict/options [{:db/id 17592186056386, :orcpub.entity.strict/key :purify-food-and-drink} {:db/id 17592186056387, :orcpub.entity.strict/key :detect-magic}]}]}}]}]} {:db/id 17592186056388, :orcpub.entity.strict/key :class, :orcpub.entity.strict/options [{:db/id 17592186056389, :orcpub.entity.strict/key :fighter, :orcpub.entity.strict/selections [{:db/id 17592186056390, :orcpub.entity.strict/key :starting-equipment-armor, :orcpub.entity.strict/option {:db/id 17592186056391, :orcpub.entity.strict/key :leather-armor-longbow-20-arrows}} {:db/id 17592186056392, :orcpub.entity.strict/key :starting-equipment-weapons, :orcpub.entity.strict/option {:db/id 17592186056393, :orcpub.entity.strict/key :martial-weapon-and-shield, :orcpub.entity.strict/selections [{:db/id 17592186056394, :orcpub.entity.strict/key :starting-equipment-martial-weapon, :orcpub.entity.strict/option {:db/id 17592186056395, :orcpub.entity.strict/key :longbow}}]}} {:db/id 17592186056396, :orcpub.entity.strict/key :levels, :orcpub.entity.strict/options [{:db/id 17592186056397, :orcpub.entity.strict/key :level-1} {:db/id 17592186056398, :orcpub.entity.strict/key :level-2, :orcpub.entity.strict/selections [{:db/id 17592186056399, :orcpub.entity.strict/key :hit-points, :orcpub.entity.strict/option {:db/id 17592186056400, :orcpub.entity.strict/key :roll, :orcpub.entity.strict/int-value 1}}]} {:db/id 17592186056401, :orcpub.entity.strict/key :level-3, :orcpub.entity.strict/selections [{:db/id 17592186056402, :orcpub.entity.strict/key :hit-points, :orcpub.entity.strict/option {:db/id 17592186056403, :orcpub.entity.strict/key :roll, :orcpub.entity.strict/int-value 6}} {:db/id 17592186056404, :orcpub.entity.strict/key :martial-archetype, :orcpub.entity.strict/option {:db/id 17592186056405, :orcpub.entity.strict/key :eldritch-knight, :orcpub.entity.strict/selections [{:db/id 17592186056406, :orcpub.entity.strict/key :abjuration-or-evocation-spells-known, :orcpub.entity.strict/options [{:db/id 17592186056407, :orcpub.entity.strict/key :acid-arrow} {:db/id 17592186056408, :orcpub.entity.strict/key :magic-missile} {:db/id 17592186056409, :orcpub.entity.strict/key :witch-bolt} {:db/id 17592186056410, :orcpub.entity.strict/key :burning-hands}]} {:db/id 17592186056411, :orcpub.entity.strict/key :spells-known-any-school, :orcpub.entity.strict/options [{:db/id 17592186056412, :orcpub.entity.strict/key :jump} {:db/id 17592186056413, :orcpub.entity.strict/key :rope-trick}]} {:db/id 17592186056414, :orcpub.entity.strict/key :cantrips-known, :orcpub.entity.strict/options [{:db/id 17592186056415, :orcpub.entity.strict/key :message} {:db/id 17592186056416, :orcpub.entity.strict/key :shocking-grasp}]}]}}]} {:db/id 17592186056417, :orcpub.entity.strict/key :level-4, :orcpub.entity.strict/selections [{:db/id 17592186056418, :orcpub.entity.strict/key :hit-points, :orcpub.entity.strict/option {:db/id 17592186056419, :orcpub.entity.strict/key :roll, :orcpub.entity.strict/int-value 5}} {:db/id 17592186056420, :orcpub.entity.strict/key :asi-or-feat, :orcpub.entity.strict/option {:db/id 17592186056421, :orcpub.entity.strict/key :ability-score-improvement, :orcpub.entity.strict/selections [{:db/id 17592186056422, :orcpub.entity.strict/key :asi, :orcpub.entity.strict/options [{:db/id 17592186056423, :orcpub.entity.strict/key :orcpub.dnd.e5.character/cha} {:db/id 17592186056424, :orcpub.entity.strict/key :orcpub.dnd.e5.character/wis}]}]}}]} {:db/id 17592186056425, :orcpub.entity.strict/key :level-5, :orcpub.entity.strict/selections [{:db/id 17592186056426, :orcpub.entity.strict/key :hit-points, :orcpub.entity.strict/option {:db/id 17592186056427, :orcpub.entity.strict/key :roll, :orcpub.entity.strict/int-value 2}}]} {:db/id 17592186056428, :orcpub.entity.strict/key :level-6, :orcpub.entity.strict/selections [{:db/id 17592186056429, :orcpub.entity.strict/key :hit-points, :orcpub.entity.strict/option {:db/id 17592186056430, :orcpub.entity.strict/key :roll, :orcpub.entity.strict/int-value 9}} {:db/id 17592186056431, :orcpub.entity.strict/key :asi-or-feat, :orcpub.entity.strict/option {:db/id 17592186056432, :orcpub.entity.strict/key :feat}}]} {:db/id 17592186056433, :orcpub.entity.strict/key :level-7, :orcpub.entity.strict/selections [{:db/id 17592186056434, :orcpub.entity.strict/key :hit-points, :orcpub.entity.strict/option {:db/id 17592186056435, :orcpub.entity.strict/key :roll, :orcpub.entity.strict/int-value 2}}]} {:db/id 17592186056436, :orcpub.entity.strict/key :level-8, :orcpub.entity.strict/selections [{:db/id 17592186056437, :orcpub.entity.strict/key :hit-points, :orcpub.entity.strict/option {:db/id 17592186056438, :orcpub.entity.strict/key :roll, :orcpub.entity.strict/int-value 2}} {:db/id 17592186056439, :orcpub.entity.strict/key :asi-or-feat, :orcpub.entity.strict/option {:db/id 17592186056440, :orcpub.entity.strict/key :ability-score-improvement, :orcpub.entity.strict/selections [{:db/id 17592186056441, :orcpub.entity.strict/key :asi, :orcpub.entity.strict/options [{:db/id 17592186056442, :orcpub.entity.strict/key :orcpub.dnd.e5.character/wis} {:db/id 17592186056443, :orcpub.entity.strict/key :orcpub.dnd.e5.character/cha}]}]}}]}]} {:db/id 17592186056444, :orcpub.entity.strict/key :starting-equipment-equipment-pack, :orcpub.entity.strict/option {:db/id 17592186056445, :orcpub.entity.strict/key :dungeoneers-pack}} {:db/id 17592186056446, :orcpub.entity.strict/key :starting-equipment-additional-weapons, :orcpub.entity.strict/option {:db/id 17592186056447, :orcpub.entity.strict/key :two-handaxes}} {:db/id 17592186056448, :orcpub.entity.strict/key :fighting-style, :orcpub.entity.strict/options [{:db/id 17592186056449, :orcpub.entity.strict/key :protection}]}]}]} {:db/id 17592186056450, :orcpub.entity.strict/key :skill-profs, :orcpub.entity.strict/options [{:db/id 17592186056451, :orcpub.entity.strict/key :animal-handling} {:db/id 17592186056452, :orcpub.entity.strict/key :intimidation}]} {:db/id 17592186056453, :orcpub.entity.strict/key :armor, :orcpub.entity.strict/options [{:db/id 17592186056454, :orcpub.entity.strict/key :leather, :orcpub.entity.strict/map-value {:db/id 17592186056455, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}}]} {:db/id 17592186056456, :orcpub.entity.strict/key :languages, :orcpub.entity.strict/options [{:db/id 17592186056457, :orcpub.entity.strict/key :primordial} {:db/id 17592186056458, :orcpub.entity.strict/key :dwarvish}]} {:db/id 17592186056459, :orcpub.entity.strict/key :optional-content} {:db/id 17592186056460, :orcpub.entity.strict/key :magic-weapons, :orcpub.entity.strict/options [{:db/id 17592186056461, :orcpub.entity.strict/key :club-1, :orcpub.entity.strict/map-value {:db/id 17592186056462, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true}}]}]}
        round-trip (-> strict char/from-strict char/to-strict)]
    (is (= strict round-trip))))

(deftest strict-round-trip-3
  (let [strict {:db/id 17592186549033, :orcpub.entity.strict/selections [{:db/id 17592186549034, :orcpub.entity.strict/key :ability-scores, :orcpub.entity.strict/option {:db/id 17592186549035, :orcpub.entity.strict/key :standard-roll, :orcpub.entity.strict/map-value {:db/id 17592186549036, :orcpub.dnd.e5.character/str 14, :orcpub.dnd.e5.character/dex 10, :orcpub.dnd.e5.character/con 13, :orcpub.dnd.e5.character/int 10, :orcpub.dnd.e5.character/wis 17, :orcpub.dnd.e5.character/cha 17}}} {:db/id 17592186549037, :orcpub.entity.strict/key :class, :orcpub.entity.strict/options [{:db/id 17592186549038, :orcpub.entity.strict/key :warlock, :orcpub.entity.strict/selections [{:db/id 17592186549039, :orcpub.entity.strict/key :levels, :orcpub.entity.strict/options [{:db/id 17592186549040, :orcpub.entity.strict/key :level-1}]}]} {:db/id 17592186549041, :orcpub.entity.strict/key :druid, :orcpub.entity.strict/selections [{:db/id 17592186549042, :orcpub.entity.strict/key :levels, :orcpub.entity.strict/options [{:db/id 17592186549043, :orcpub.entity.strict/key :level-1}]}]}]} {:db/id 17592186549044, :orcpub.entity.strict/key :equipment, :orcpub.entity.strict/options [{:db/id 17592186549045, :orcpub.entity.strict/key :crowbar, :orcpub.entity.strict/map-value {:db/id 17592186549046, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}} {:db/id 17592186549047, :orcpub.entity.strict/key :clothes-common, :orcpub.entity.strict/map-value {:db/id 17592186549048, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}} {:db/id 17592186549049, :orcpub.entity.strict/key :pouch, :orcpub.entity.strict/map-value {:db/id 17592186549050, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}}]} {:db/id 17592186549051, :orcpub.entity.strict/key :background, :orcpub.entity.strict/option {:db/id 17592186549052, :orcpub.entity.strict/key :spy}} {:db/id 17592186549053, :orcpub.entity.strict/key :treasure, :orcpub.entity.strict/options [{:db/id 17592186549054, :orcpub.entity.strict/key :gp, :orcpub.entity.strict/map-value {:db/id 17592186549055, :orcpub.dnd.e5.character.equipment/quantity 15, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/background-starting-equipment? true}}]} {:db/id 17592186549056, :orcpub.entity.strict/key :weapons, :orcpub.entity.strict/options [{:db/id 17592186549057, :orcpub.entity.strict/key :dagger, :orcpub.entity.strict/map-value {:db/id 17592186549058, :orcpub.dnd.e5.character.equipment/quantity 2, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/class-starting-equipment? true}}]} {:db/id 17592186549059, :orcpub.entity.strict/key :armor, :orcpub.entity.strict/options [{:db/id 17592186549060, :orcpub.entity.strict/key :leather, :orcpub.entity.strict/map-value {:db/id 17592186549061, :orcpub.dnd.e5.character.equipment/quantity 1, :orcpub.dnd.e5.character.equipment/equipped? true, :orcpub.dnd.e5.character.equipment/class-starting-equipment? true}}]}]}
        round-trip (-> strict char/from-strict char/to-strict)]
    (is (= strict round-trip))))

(deftest a-character-keeps-both-pictures-and-shows-one
  (let [portrait "{:layers {:head {:asset/id :h}}}"
        url "https://i.imgur.com/2O4oP3l.png"
        both {::char/portrait portrait ::char/image-url url}]
    (testing "using either keeps the other and makes it the one shown"
      (let [v (char/with-image-url {::char/portrait portrait} url)]
        (is (= portrait (::char/portrait v)))
        (is (= :image (char/shown-picture v))))
      (let [v (char/with-saved-portrait {::char/image-url url} {:layers {:head {:asset/id :h}}})]
        (is (= url (::char/image-url v)))
        (is (= :portrait (char/shown-picture v)))))
    (testing "the choice decides; without one the portrait wins, as before"
      (is (= :image (char/shown-picture (assoc both ::char/picture-shown "image"))))
      (is (= :portrait (char/shown-picture (assoc both ::char/picture-shown "portrait"))))
      (is (= :portrait (char/shown-picture both)))
      (is (= :image (char/shown-picture {::char/image-url url ::char/picture-shown "portrait"}))
          "a choice of something empty falls back to what there is")
      (is (nil? (char/shown-picture {}))))
    (testing "showing the image hides the portrait; showing the portrait keeps the image as its fallback"
      (is (= {::char/image-url url ::char/picture-shown "image"}
             (char/shown-values (assoc both ::char/picture-shown "image"))))
      (is (= both (char/shown-values both)))
      (is (= url (::char/image-url (char/shown-values (assoc both ::char/picture-shown "portrait"))))))))

;; Every casting mode of plan-spell-uses-by-source.md that the 2014 SRD data uses, read by the one
;; rule the Spells tab and the PDF share.
(deftest each-casting-mode-reads-as-the-rules-say
  (let [lr units5e/long-rests-1]
    (testing "a class spell: slots, no note"
      (is (char/slot-castable? {:key :bless}))
      (is (nil? (char/casting-note {:key :bless}))))
    (testing "free casts, no slots, at a fixed level (2014 Tiefling)"
      (let [c {:casting {:free (assoc lr :level 2) :slots :none}}]
        (is (not (char/slot-castable? c)))
        (is (= "2nd level, once/long rest" (char/casting-note c)))))
    (testing "free casts, and slots too (2024 lineages, Magic Initiate by ruling)"
      (let [c {:casting {:free (units5e/long-rests 2)}}]
        (is (char/slot-castable? c))
        (is (= "twice/long rest, or a slot" (char/casting-note c)))))
    (testing "at will, no slot (invocations)"
      (let [c {:casting {:free {:at-will true} :slots :none}}]
        (is (not (char/slot-castable? c)))
        (is (= "at will" (char/casting-note c)))))
    (testing "a slot, but only N per rest (Mire the Mind)"
      (let [c {:casting {:slots lr}}]
        (is (char/slot-castable? c))
        (is (= "once/long rest, using a slot" (char/casting-note c)))))
    (testing "any count per any period"
      (is (= "3 times/day, or a slot"
             (char/casting-note {:casting {:free (units5e/units ::units5e/day 3)}}))))))

(deftest a-free-grant-never-closes-a-class-spells-slots
  (let [free {:key :slow :class "Warlock" :casting {:free {:at-will true} :slots :none}}
        plain {:key :slow :class "Warlock"}]
    (testing "either order: the class spell keeps slot casting, the free casts stay"
      (doseq [[a b] [[free plain] [plain free]]]
        (let [entry (get-in (-> {} (mod5e/add-spell 3 a) (mod5e/add-spell 3 b)) [3 ["Warlock" :slow]])]
          (is (char/slot-castable? entry) (str entry))
          (is (= {:at-will true} (get-in entry [:casting :free])) (str entry)))))
    (testing "two free grants: the later one stands"
      (let [entry (get-in (-> {} (mod5e/add-spell 3 free)
                              (mod5e/add-spell 3 (assoc-in free [:casting :slots] :none)))
                          [3 ["Warlock" :slow]])]
        (is (not (char/slot-castable? entry)))))))
