(ns orcpub.dnd.e5.coverage-pak-test
  "Each feature of test/fixtures/coverage-pak.orcbrew reaches a built character, through the app's
   own homebrew conversion, so tests relying on the pack can trust what it holds."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.dnd.e5.coverage-pak :as coverage-pak]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]))

(def ^:private template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [] []
     [(classes5e/ranger-option sl5e/spell-lists spells5e/spell-map
                               (coverage-pak/plugin-subclasses-map)
                               (common/map-by-key [{:name "Common" :key :common}])
                               weapons5e/weapons-map)]
     []
     (common/map-by-key [{:name "Common" :key :common}])))))

(defn- wayfarer [n]
  {::entity/options
   {:ability-scores {::entity/key :standard-roll
                     ::entity/value {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
                                     ::char5e/int 13 ::char5e/wis 13 ::char5e/cha 10}}
    :class [{::entity/key :ranger
             ::entity/options
             {:levels (vec (for [i (range 1 (inc n))]
                             {::entity/key (keyword (str "level-" i))
                              ::entity/options
                              (when (= i 3)
                                {:ranger-archetype
                                 {::entity/key :wayfarer
                                  ::entity/options
                                  {:wayfarer-tricks {::entity/key :lantern-step}}}})}))}}]}})

(deftest the-pack-loads-through-the-import-parser
  (is (= #{:wayfarer} (set (keys (:orcpub.dnd.e5/subclasses @coverage-pak/pak)))))
  (is (= #{:wayfarer-tricks :wayfarer-routes}
         (set (keys (:orcpub.dnd.e5/selections @coverage-pak/pak))))))

(deftest every-wayfarer-feature-reaches-a-built-character
  (let [built (entity/build (wayfarer 18) @template)
        prop #(char5e/get-prop built %)
        has? (fn [coll k]
               (some #(or (= k %) (= k (:value %)) (= k (:key %)) (= k (:types %))) coll))]
    (testing "level modifiers"
      (is (has? (char5e/weapon-proficiencies built) :martial) "weapon proficiency")
      (is (has? (char5e/armor-proficiencies built) :heavy) "armor proficiency")
      (is (has? (keys (char5e/tool-proficiencies built)) :cartographers-tools) "tool proficiency")
      (is (contains? (char5e/skill-proficiencies built) :athletics) "skill proficiency")
      (is (= 2 (char5e/number-of-attacks built)) "attacks")
      (is (= 30 (char5e/base-swimming-speed built)) "swimming speed")
      (is (has? (char5e/damage-resistances built) :cold) "damage resistance")
      (is (has? (char5e/damage-immunities built) :poison) "damage immunity")
      (is (has? (prop :saving-throw-advantage) :frightened) "saving throw advantage")
      (is (some? (char5e/base-flying-speed built)) "flying speed")
      (is (re-find #":guidance" (pr-str (char5e/spells-known built))) "spell"))
    (testing "traits, of each action type, and the level-3 choice"
      (is (= #{"Wayfarer Lore" "Waymark" "Quick Step" "Turn Aside" "Far Wanderer" "Lantern Step"}
             (set (filter #{"Wayfarer Lore" "Waymark" "Quick Step" "Turn Aside" "Far Wanderer"
                            "Lantern Step"}
                          (map :name (char5e/traits built)))))))
    (testing "the skill and expertise choices and both level selections are offered"
      (let [offered (set (map (comp vec entity/actual-path)
                              (entity/available-selections (wayfarer 18) built @template)))
            under [:class :ranger :levels :level-3 :ranger-archetype :wayfarer]]
        (is (offered (conj under :wayfarer-tricks)))
        (is (offered (conj under :wayfarer-routes)))
        (is (some #(and (= under (vec (take 6 %)))
                        (#{:skill-proficiency :skill-expertise} (peek %)))
                  offered))))))
