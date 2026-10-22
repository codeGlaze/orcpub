(ns orcpub.dnd.e5.equipment-totals-test
  "Gear from several sources adds up on the built character (#138): a pack's rations plus rations
   picked by hand, starting arrows plus arrows picked by hand. A single source is counted once.
   KB: plan-inventory-kits.md, phase 1 part A."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.equipment :as equipment5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private the-template
  (delay
   (let [language-map (common/map-by-key [{:name "Common" :key :common}])]
     (t5e/template
      (t5e/template-selections
       nil nil nil weapons5e/weapons-map weapons5e/weapons
       sl5e/spell-lists spells5e/spell-map
       [] []
       [(classes5e/fighter-option sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)]
       [] language-map)))))

(defn- hand-picked
  "Stored Equipment pick of item key `k`, quantity `n`, equipped. Returns the entry map."
  [k n] {::entity/key k ::entity/value {::char-equip/quantity n ::char-equip/equipped? true}})

(defn- fighter
  "Built level 1 fighter with Explorer's Pack and the leather armor, longbow and 20 arrows choice,
   plus the stored Equipment picks `picks`. Returns the built character."
  [picks]
  (entity/build
   {::entity/options
    {:ability-scores {::entity/key :standard-roll
                      ::entity/value {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
                                      ::char5e/int 13 ::char5e/wis 13 ::char5e/cha 13}}
     :class [{::entity/key :fighter
              ::entity/options {:levels [{::entity/key :level-1
                                          ::entity/options {:hit-points {::entity/key :average ::entity/value 5}}}]
                                :starting-equipment-equipment-pack [{::entity/key :explorers-pack}]
                                :starting-equipment-armor [{::entity/key :leather-armor-longbow-20-arrows}]}}]
     :equipment picks}}
   @the-template))

(defn- qty
  "Quantity of item `k` in the built character's gear, or nil."
  [built k] (::char-equip/quantity (get (char5e/normal-equipment-inventory built) k)))

(deftest sources-of-the-same-item-add-up
  (let [built (fighter [(hand-picked :arrow 1) (hand-picked :rations-1-day 1)])]
    (testing "a pack's rations plus a ration picked by hand (#138)"
      (is (= 11 (qty built :rations-1-day))))
    (testing "starting arrows plus an arrow picked by hand"
      (is (= 21 (qty built :arrow))))))

(deftest a-single-source-counts-once
  (let [built (fighter [])]
    (is (= 10 (qty built :torch)) "the pack's torches, not doubled")
    (is (= 1 (qty built :backpack)))
    (is (= 20 (qty built :arrow)) "the starting choice's arrows alone")))

(deftest every-pack-item-is-a-real-item
  (doseq [{:keys [name items]} equipment5e/packs
          k (keys items)]
    (testing name
      (is (some? (equipment5e/equipment-map k)) (str k " names no item")))))
