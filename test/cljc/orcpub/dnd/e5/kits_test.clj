(ns orcpub.dnd.e5.kits-test
  "Packs becoming kits: their items added as ordinary picks (to existing quantities), the kit held
   at full, the repair of saved characters (once only), the owner's rule for a pack's holy symbol,
   and kits kept out of the Choices list. KB: plan-inventory-kits.md, phase 2a."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.kits :as kits]
            [orcpub.dnd.e5.picks :as picks]))

(defn- gear
  "Stored Equipment pick of `k`, quantity `n`, carried."
  [k n] {::entity/key k ::entity/value {::char-equip/quantity n ::char-equip/equipped? true}})

(defn- qty
  "Quantity of Equipment pick `k` in `character`, or nil."
  [character k]
  (some #(when (= k (::entity/key %)) (get-in % [::entity/value ::char-equip/quantity]))
        (get-in character [::entity/options :equipment])))

(defn- fighter-choosing
  "A stored fighter whose starting-equipment pack choice is `pack-key`, with Equipment picks `picks`."
  [pack-key picks]
  {::entity/options {:class [{::entity/key :fighter
                              ::entity/options {:starting-equipment-equipment-pack [{::entity/key pack-key}]}}]
                     :equipment picks}})

(deftest a-pack-becomes-ordinary-picks-and-a-full-kit
  (let [c (kits/materialize {} :explorers-pack {})
        kit (first (kits/kits c))]
    (is (= 10 (qty c :torch)))
    (is (= 1 (qty c :backpack)))
    (is (= :explorers-pack (::entity/key kit)))
    (is (= 10 (get (kits/held kit) :torch)) "held at full")))

(deftest a-pack-adds-to-what-the-character-already-owns
  (let [c (kits/materialize {::entity/options {:equipment [(gear :torch 5)]}} :explorers-pack {})]
    (is (= 15 (qty c :torch)) "5 owned plus the pack's 10, not replaced")
    (is (= 1 (count (filter #(= :torch (::entity/key %)) (get-in c [::entity/options :equipment]))))
        "one torch pick, not two")
    (is (= 10 (get (kits/held (first (kits/kits c))) :torch)) "the kit holds only its own 10")))

(deftest the-holy-symbol-follows-what-the-character-owns
  (testing "none owned: the generic entry is added"
    (let [{:keys [character ask]} (kits/repair (fighter-choosing :monster-hunters-pack []))]
      (is (= 1 (qty character :holy-symbol)))
      (is (empty? ask))))
  (testing "one owned: it is the choice, nothing is added"
    (let [{:keys [character ask]} (kits/repair (fighter-choosing :monster-hunters-pack [(gear :amulet 1)]))]
      (is (= 1 (qty character :amulet)))
      (is (nil? (qty character :holy-symbol)))
      (is (= 1 (get (kits/held (first (kits/kits character))) :amulet)))
      (is (empty? ask))))
  (testing "several owned: the player is asked"
    (let [{:keys [character ask]} (kits/repair (fighter-choosing :monster-hunters-pack [(gear :amulet 1) (gear :emblem 1)]))]
      (is (nil? (qty character :holy-symbol)))
      (is (= [{:pack :monster-hunters-pack :group :holy-symbol :options [:amulet :emblem]}] ask)))))

(deftest repair-runs-once
  (let [once (:character (kits/repair (fighter-choosing :explorers-pack [])))
        twice (:character (kits/repair once))]
    (is (= 10 (qty once :torch)))
    (is (= once twice) "a repaired character is left alone")))

(deftest a-pack-stored-as-an-item-is-replaced-by-its-items
  (let [{:keys [character]} (kits/repair {::entity/options {:equipment [(gear :explorers-pack 1)]}})]
    (is (nil? (qty character :explorers-pack)))
    (is (= 10 (qty character :torch)))
    (is (= [:explorers-pack] (map ::entity/key (kits/kits character))))))

(deftest kits-are-not-listed-as-choices
  (let [c (kits/materialize {} :explorers-pack {})
        rows (picks/choices {::t/selections []} c)]
    (is (not-any? #(= :kits (first (:address %))) rows))))
