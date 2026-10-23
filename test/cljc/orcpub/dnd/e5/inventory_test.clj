(ns orcpub.dnd.e5.inventory-test
  "Packs becoming kits: their items added as ordinary picks (to existing quantities), the kit held
   at full, the repair of saved characters (once only), the owner's rule for a pack's holy symbol,
   and kits kept out of the Choices list. KB: plan-inventory-kits.md, phase 2a."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.inventory :as inventory]
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
  (let [c (inventory/materialize {} :explorers-pack {})
        kit (first (inventory/kits c))]
    (is (= 10 (qty c :torch)))
    (is (= 1 (qty c :backpack)))
    (is (= :explorers-pack (::entity/key kit)))
    (is (= 10 (get (inventory/held kit) :torch)) "held at full")))

(deftest a-pack-adds-to-what-the-character-already-owns
  (let [c (inventory/materialize {::entity/options {:equipment [(gear :torch 5)]}} :explorers-pack {})]
    (is (= 15 (qty c :torch)) "5 owned plus the pack's 10, not replaced")
    (is (= 1 (count (filter #(= :torch (::entity/key %)) (get-in c [::entity/options :equipment]))))
        "one torch pick, not two")
    (is (= 10 (get (inventory/held (first (inventory/kits c))) :torch)) "the kit holds only its own 10")))

(deftest the-holy-symbol-follows-what-the-character-owns
  (testing "none owned: nothing is added and the kit asks which one, from every member"
    (let [{:keys [character]} (inventory/repair (fighter-choosing :monster-hunters-pack []))]
      (is (nil? (qty character :holy-symbol)) "no generic row: it is not a real item")
      (is (= [{:kind :choice :pack :monster-hunters-pack :group :holy-symbol
               :options [:amulet :emblem :reliquary]}]
             (inventory/questions character)))
      (let [answered (inventory/answer-choice character :monster-hunters-pack :holy-symbol :emblem)]
        (is (= 1 (qty answered :emblem)) "answering adds the one picked")
        (is (= 1 (get (inventory/held (first (inventory/kits answered))) :emblem)))
        (is (empty? (inventory/questions answered))))))
  (testing "one owned: it is the choice, nothing is added"
    (let [{:keys [character ask]} (inventory/repair (fighter-choosing :monster-hunters-pack [(gear :amulet 1)]))]
      (is (= 1 (qty character :amulet)))
      (is (nil? (qty character :holy-symbol)))
      (is (= 1 (get (inventory/held (first (inventory/kits character))) :amulet)))
      (is (empty? ask))))
  (testing "several owned: the player is asked"
    (let [{:keys [character ask]} (inventory/repair (fighter-choosing :monster-hunters-pack [(gear :amulet 1) (gear :emblem 1)]))]
      (is (nil? (qty character :holy-symbol)))
      (is (= [{:pack :monster-hunters-pack :group :holy-symbol :options [:amulet :emblem]}] ask)))))

(deftest repair-runs-once
  (let [once (:character (inventory/repair (fighter-choosing :explorers-pack [])))
        twice (:character (inventory/repair once))]
    (is (= 10 (qty once :torch)))
    (is (= once twice) "a repaired character is left alone")))

(deftest a-pack-stored-as-an-item-becomes-a-kit
  (let [{:keys [character]} (inventory/repair {::entity/options {:equipment [(gear :explorers-pack 1)]}})]
    (is (= 1 (qty character :explorers-pack)) "the pack's pick stays: it is the kit's source")
    (is (= 10 (qty character :torch)))
    (is (= [:explorers-pack] (map ::entity/key (inventory/kits character))))))

(deftest kits-are-not-listed-as-choices
  (let [c (inventory/materialize {} :explorers-pack {})
        rows (picks/choices {::t/selections []} c)]
    (is (not-any? #(= :kits (first (:address %))) rows))))

(defn- set-qty
  "`c` with its Equipment pick `k` at quantity `n`."
  [c k n]
  (update-in c [::entity/options :equipment]
             (fn [ps] (mapv #(if (= k (::entity/key %)) (assoc-in % [::entity/value ::char-equip/quantity] n) %) ps))))

(defn- two-kits
  "Torch 23: Explorer's holds 5 of 10, Dungeoneer's 10 of 10, 8 spare; both packs chosen as items."
  []
  (-> {::entity/options {:equipment [(gear :explorers-pack 1) (gear :dungeoneers-pack 1)]}}
      inventory/repair :character
      (set-qty :torch 23)
      (update-in [::entity/options :kits 0 ::entity/options :held]
                 (fn [hs] (mapv #(if (= :torch (::entity/key %)) (assoc % ::entity/value 5) %) hs)))))

(defn- held-of
  "How many of gear `k` the kit for pack `pk` holds in `c`, or nil."
  [c pk k]
  (get (inventory/held (some #(when (= pk (::entity/key %)) %) (inventory/kits c))) k))

(deftest using-takes-from-the-top-kit-first-then-spare
  (let [c (two-kits)]
    (is (= 23 (qty c :torch)) "fixture")
    (is (= 8 (inventory/spare c :torch)))
    (let [used (inventory/take-from-kits c :torch 7)]
      (is (= 0 (held-of used :explorers-pack :torch)) "top kit gives up its 5 first")
      (is (= 8 (held-of used :dungeoneers-pack :torch)) "then the next kit, before any spare")
      (is (= 8 (inventory/spare (set-qty used :torch 16) :torch))))))

(deftest refill-reorganize-and-restock
  (let [c (two-kits)]
    (testing "refill claims spare up to the target"
      (let [{r :character moved :moved} (inventory/refill c :explorers-pack)]
        (is (= 10 (held-of r :explorers-pack :torch)))
        (is (= [[:torch 5]] moved))
        (is (= 3 (inventory/spare r :torch)))))
    (testing "reorganize fills every kit in order and reports by kit"
      (is (= [[:explorers-pack [[:torch 5]]]] (:moved (inventory/reorganize c)))))
    (testing "restock: mine first uses spare, all new buys and leaves spare alone"
      (let [low (set-qty c :torch 17)]
        (is (= [[:torch 2]] (inventory/restock-could-use-spare low :explorers-pack)))
        (let [{m :character} (inventory/restock low :explorers-pack :mine-first)]
          (is (= 20 (qty m :torch)) "2 spare claimed, 3 bought")
          (is (= 10 (held-of m :explorers-pack :torch))))
        (let [{a :character added :added} (inventory/restock low :explorers-pack :all-new)]
          (is (= [[:torch 5]] added))
          (is (= 22 (qty a :torch)))
          (is (= 2 (inventory/spare a :torch))))))))

(deftest a-kit-shows-what-runs-out-and-what-is-low
  (let [c (two-kits)
        kit (first (inventory/kits c))]
    (is (= [{:key :torch :held 5 :target 10 :low? true}
            {:key :rations-1-day :held 10 :target 10 :low? false}]
           (inventory/tracked kit)) "targets over 1, not the bedroll")
    (is (= {:kits [{:pack :explorers-pack :key :torch :held 5 :target 10 :low? true}
                   {:pack :dungeoneers-pack :key :torch :held 10 :target 10 :low? false}]
            :spare 8}
           (inventory/shares c :torch)))
    (is (nil? (inventory/shares c :bedroll)))
    (is (= "5 torches" (inventory/list-label (inventory/fillable c))) "what the kits are short of, not all 8 spare")))

(deftest removing-and-ordering-kits
  (let [c (two-kits)]
    (is (= [:dungeoneers-pack :explorers-pack]
           (map ::entity/key (inventory/kits (inventory/use-first c :dungeoneers-pack)))))
    (let [r (inventory/remove-kit c :explorers-pack)]
      (is (= [:dungeoneers-pack] (map ::entity/key (inventory/active-kits r))))
      (is (= 23 (qty r :torch)) "its items stay")
      (is (= r (:character (inventory/repair r))) "a pack added as an item is not made again"))
    (testing "a chosen pack's kit leaves a marker, so repair does not make it again"
      (let [chosen (:character (inventory/repair (fighter-choosing :explorers-pack [])))
            r (inventory/remove-kit chosen :explorers-pack)]
        (is (empty? (inventory/active-kits r)))
        (is (= r (:character (inventory/repair r))))
        (is (= 10 (qty r :torch)))))))

(deftest a-kit-whose-pack-is-gone-asks-only-when-used
  (let [chosen (:character (inventory/repair (fighter-choosing :explorers-pack [(gear :torch 2)])))
        gone (assoc-in chosen [::entity/options :class] [])
        used (inventory/take-from-kits (set-qty gone :torch 9) :torch 3)]
    (testing "untouched: no question, and repair takes its items off quietly"
      (is (empty? (inventory/questions gone #{})))
      (let [r (:character (inventory/repair gone #{}))]
        (is (= 2 (qty r :torch)) "only what the kit held; the player's own 2 stay")
        (is (nil? (qty r :bedroll)))
        (is (empty? (inventory/kits r)))))
    (testing "untouched but the new class offers the pack: kept, no question"
      (let [r (:character (inventory/repair gone #{:explorers-pack}))]
        (is (= [:explorers-pack] (map ::entity/key (inventory/kits r))))
        (is (empty? (inventory/questions r #{:explorers-pack})))))
    (testing "once the new class's pack choice is answered with another pack, the offer is gone"
      (let [other (-> gone
                      (assoc-in [::entity/options :class]
                                [{::entity/key :fighter
                                  ::entity/options {:starting-equipment-equipment-pack [{::entity/key :dungeoneers-pack}]}}])
                      (inventory/repair #{:explorers-pack :dungeoneers-pack}) :character)]
        (is (= [:dungeoneers-pack] (map ::entity/key (inventory/kits other))) "the Explorer's kit went quietly")
        (is (empty? (inventory/questions other #{:explorers-pack :dungeoneers-pack})))))
    (testing "without the template (on load) nothing is removed"
      (is (= gone (:character (inventory/repair gone)))))
    (testing "used: asked, unless the new class offers the pack"
      (is (= [{:kind :orphan :pack :explorers-pack}] (inventory/questions used #{})))
      (is (empty? (inventory/questions used #{:explorers-pack})))
      (is (= used (:character (inventory/repair used #{}))) "a used kit is never removed quietly"))
    (testing "the answers"
      (let [r (inventory/resolve-orphan used :explorers-pack :remove)]
        (is (= 2 (qty r :torch)) "takes off only what the kit still holds")
        (is (empty? (inventory/kits r))))
      (let [k (inventory/resolve-orphan used :explorers-pack :keep)]
        (is (= 9 (qty k :torch)))
        (is (empty? (inventory/questions k #{})) "kept as a kit the player added")))))

(deftest add-a-kit-from-the-panel
  (let [{c :character} (inventory/add-kit {::entity/options {:equipment [(gear :torch 4)]}} :explorers-pack)]
    (is (= 14 (qty c :torch)))
    (is (= 10 (held-of c :explorers-pack :torch)))
    (is (= 4 (inventory/spare c :torch)))))

(deftest words
  (is (= "3 torches" (inventory/count-label :torch 3)))
  (is (= "1 torch" (inventory/count-label :torch 1)))
  (is (= "6 days of rations" (inventory/count-label :rations-1-day 6)))
  (is (= "2 flasks of oil" (inventory/count-label :oil 2)))
  (is (= "3 spare torches and 2 spare days of rations"
         (inventory/list-label [[:torch 3] [:rations-1-day 2]] true)))
  (is (= "3 torches, 2 candles and 1 day of rations"
         (inventory/list-label [[:torch 3] [:candle 2] [:rations-1-day 1]]))))
