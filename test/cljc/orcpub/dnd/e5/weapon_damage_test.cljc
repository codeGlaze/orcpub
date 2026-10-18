(ns orcpub.dnd.e5.weapon-damage-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.weapon-damage :as wd]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.units :as units]))

(defn dice
  "An entry's dice, and its type when given, as a map with the weapons' own keys."
  ([n die] {::weapons/damage-die-count n ::weapons/damage-die die})
  ([n die type] (assoc (dice n die) ::weapons/damage-type type)))

(def srd-extras
  "The extra damage each SRD 5.1 magic weapon deals, as written in its description."
  {"Frost Brand" [(dice 1 6 :cold)]
   "Flame Tongue" [(assoc (dice 2 6 :fire) ::mi/while :ablaze)]
   "Sun Blade" [(assoc (dice 1 8 :radiant) ::mi/against #{:undead})]
   "Holy Avenger" [(assoc (dice 2 10 :radiant) ::mi/against #{:fiend :undead})]
   "Mace of Disruption" [(assoc (dice 2 6 :radiant) ::mi/against #{:fiend :undead})]
   "Giant Slayer" [(assoc (dice 2 6) ::mi/against #{:giant})]
   "Dragon Slayer" [(assoc (dice 3 6) ::mi/against #{:dragon})]
   "Vicious Weapon" [(assoc (dice 2 6) ::mi/min-roll 20)]
   "Oathbow" [(assoc (dice 3 6 :piercing) ::mi/while :sworn-enemy)]
   "Dwarven Thrower" [(assoc (dice 1 8) ::mi/attack-type :ranged ::mi/not-against #{:giant})
                      (assoc (dice 2 8) ::mi/attack-type :ranged ::mi/against #{:giant})]
   "Mace of Smiting" [{::mi/damage-bonus 2 ::mi/against #{:construct}}
                      (assoc (dice 2 6 :bludgeoning) ::mi/min-roll 20 ::mi/not-against #{:construct})
                      (assoc (dice 4 6 :bludgeoning) ::mi/min-roll 20 ::mi/against #{:construct})]
   "Javelin of Lightning" [(assoc (dice 4 6 :lightning) ::mi/attack-type :ranged ::mi/recharge ::units/dawn)]
   "Sword of Life Stealing" [(assoc (dice 3 6 :necrotic) ::mi/min-roll 20 ::mi/not-against #{:construct :undead})]
   "Sword of Sharpness" [(assoc (dice 4 6 :slashing) ::mi/min-roll 20)]
   "Vorpal Sword" [(assoc (dice 6 8 :slashing) ::mi/min-roll 20)]})

(defn weapon
  "The weapon with this key from the static weapons map."
  [k]
  (or (mi/all-weapons-map k) (throw (ex-info "No such weapon" {:key k}))))

(defn variants
  "Every expanded weapon (one per base weapon) of the magic item with this name."
  [item-name]
  (filter #(= item-name (::mi/name %)) (vals mi/all-weapons-map)))

(deftest srd-weapons-carry-their-extra-damage
  (doseq [[item-name extras] srd-extras]
    (testing item-name
      (is (seq (variants item-name)))
      (doseq [w (variants item-name)]
        (is (= extras (::mi/extra-damage w)) (:name w)))))
  (testing "no other weapon declares extra damage"
    (is (= (set (keys srd-extras))
           (set (keep #(when (::mi/extra-damage %) (::mi/name %)) (vals mi/all-weapons-map)))))))

(deftest srd-weapon-data-conforms
  (doseq [w (vals mi/all-weapons-map)]
    (when-let [extras (::mi/extra-damage w)]
      (is (spec/valid? ::mi/extra-damage extras) (:name w)))
    (when-let [effects (::mi/effects w)]
      (is (spec/valid? ::mi/effects effects) (:name w)))
    (when-let [toggles (::mi/toggles w)]
      (is (spec/valid? ::mi/toggles toggles) (:name w)))
    (testing "every toggle an extra names is declared on the item"
      (let [declared (set (map ::mi/toggle-id (::mi/toggles w)))]
        (doseq [t (keep ::mi/while (::mi/extra-damage w))]
          (is (contains? declared t) (:name w)))))))

(deftest save-effects
  (is (= [[13 :orcpub.dnd.e5.character/dex nil]]
         (map (juxt ::mi/save-dc ::mi/save-ability ::mi/save-fail)
              (::mi/effects (weapon :javelin-of-lightning)))))
  (is (= [[15 :orcpub.dnd.e5.character/con :poisoned [2 10 :poison] ::units/dawn]]
         (map (juxt ::mi/save-dc ::mi/save-ability ::mi/save-fail
                    (juxt ::weapons/damage-die-count ::weapons/damage-die ::weapons/damage-type)
                    ::mi/recharge)
              (::mi/effects (weapon :dagger-of-venom)))))
  (is (= [[15 :orcpub.dnd.e5.character/str :prone #{:giant}]]
         (map (juxt ::mi/save-dc ::mi/save-ability ::mi/save-fail ::mi/against)
              (::mi/effects (weapon :giant-slayer-greataxe)))))
  (is (= [[15 :orcpub.dnd.e5.character/wis :destroyed #{:fiend :undead}]]
         (map (juxt ::mi/save-dc ::mi/save-ability ::mi/save-fail ::mi/against)
              (::mi/effects (weapon :mace-of-disruption))))))

(deftest base-damage
  (testing "one hand, then a Versatile weapon in two"
    (is (= [(assoc (dice 1 8 :slashing) ::wd/bonus 3)]
           (wd/weapon-damage (weapon :longsword) 3 false)))
    (is (= [(assoc (dice 1 10 :slashing) ::wd/bonus 3)]
           (wd/weapon-damage (weapon :longsword) 3 true))))
  (testing "two hands changes nothing on a weapon that is not Versatile"
    (is (= (wd/weapon-damage (weapon :greatsword) 2 false)
           (wd/weapon-damage (weapon :greatsword) 2 true))))
  (testing "the Net has no damage dice, so no entries"
    (is (= [] (wd/weapon-damage (weapon :net) 2 false))))
  (testing "Sun Blade deals radiant instead of slashing"
    (is (= :radiant (::weapons/damage-type (first (wd/weapon-damage (weapon :sun-blade-longsword) 2 false)))))))

(deftest untyped-extras-take-the-weapons-type
  (is (= [:slashing :slashing]
         (map ::weapons/damage-type (wd/weapon-damage (weapon :giant-slayer-greataxe) 1 false))))
  (is (= [:bludgeoning :bludgeoning]
         (map ::weapons/damage-type (wd/weapon-damage (weapon :vicious-weapon-mace) 0 false))))
  (is (= [:piercing :piercing]
         (map ::weapons/damage-type (wd/weapon-damage (weapon :dragon-slayer-rapier) 1 false)))))

(defn rolled
  "The [die-count die type] of each entry a roll adds up for a weapon in a situation."
  [k situation]
  (map (juxt ::weapons/damage-die-count ::weapons/damage-die ::weapons/damage-type)
       (wd/applying-damage (weapon k) 0 false situation)))

(deftest which-entries-apply
  (testing "Frost Brand's cold applies on every hit"
    (is (= [[1 8 :slashing] [1 6 :cold]] (rolled :frost-brand-longsword {}))))
  (testing "nothing applies on a miss"
    (is (= [] (rolled :frost-brand-longsword {:miss? true}))))
  (testing "Flame Tongue's fire applies only while ablaze"
    (is (= [[1 8 :slashing]] (rolled :flame-tongue-longsword {})))
    (is (= [[1 8 :slashing]] (rolled :flame-tongue-longsword {:toggles #{:other}})))
    (is (= [[1 8 :slashing] [2 6 :fire]] (rolled :flame-tongue-longsword {:toggles #{:ablaze}}))))
  (testing "Holy Avenger's radiant applies against fiends and undead only"
    (is (= [[1 8 :slashing]] (rolled :holy-avenger-longsword {})))
    (is (= [[1 8 :slashing]] (rolled :holy-avenger-longsword {:against :giant})))
    (is (= [[1 8 :slashing] [2 10 :radiant]] (rolled :holy-avenger-longsword {:against :fiend})))
    (is (= [[1 8 :slashing] [2 10 :radiant]] (rolled :holy-avenger-longsword {:against :undead}))))
  (testing "Vicious Weapon's extra needs a rolled 20, not just a crit"
    (is (= [[1 6 :bludgeoning]] (rolled :vicious-weapon-mace {})))
    (is (= [[1 6 :bludgeoning]] (rolled :vicious-weapon-mace {:roll 19 :crit? true})))
    (is (= [[1 6 :bludgeoning] [2 6 :bludgeoning]] (rolled :vicious-weapon-mace {:roll 20 :crit? true})))))

(def homebrew-weapon
  "A made-up weapon using every condition field and effect type the SRD has no example of."
  (merge (weapon :longsword)
         {::mi/name "Test Blade"
          ::mi/crit-from 18
          ::mi/charges 3
          ::mi/charges-recharge ::units/dawn
          ::mi/extra-damage [(assoc (dice 1 4 :force) ::mi/charge-cost 1)
                             (assoc (dice 3 6 :thunder) ::mi/recharge ::units/short-rest)
                             (assoc (dice 1 4) ::mi/on :miss)
                             (assoc (dice 2 8 :psychic) ::mi/on :crit)]
          ::mi/effects [{::mi/effect-type :heal ::mi/text "You regain 1d4 hit points."}
                        (merge (dice 2 10 :poison)
                               {::mi/effect-type :save ::mi/save-dc 15 ::mi/save-ability :orcpub.dnd.e5.character/con
                                ::mi/save-fail :poisoned ::mi/recharge ::units/dawn ::mi/text "x"})]}))

(deftest homebrew-scope-fits-the-model
  (is (spec/valid? ::mi/extra-damage (::mi/extra-damage homebrew-weapon)))
  (is (spec/valid? ::mi/effects (::mi/effects homebrew-weapon)))
  (is (spec/valid? ::mi/charges-recharge (::mi/charges-recharge homebrew-weapon)))
  (testing "an unknown trigger, damage type or recharge period is rejected"
    (is (not (spec/valid? ::mi/extra-damage-entry (assoc (dice 1 4) ::mi/on :sometimes))))
    (is (not (spec/valid? ::mi/extra-damage-entry (dice 1 4 :sonic))))
    (is (not (spec/valid? ::mi/extra-damage-entry (assoc (dice 1 4) ::mi/recharge :weekly)))))
  (testing "an ordinary hit rolls only the base damage"
    (is (= [[1 8 :slashing]]
           (map (juxt ::weapons/damage-die-count ::weapons/damage-die ::weapons/damage-type)
                (wd/applying-damage homebrew-weapon 0 false {})))))
  (testing "a miss rolls only the miss entry, in the weapon's type"
    (is (= [[1 4 :slashing]]
           (map (juxt ::weapons/damage-die-count ::weapons/damage-die ::weapons/damage-type)
                (wd/applying-damage homebrew-weapon 0 false {:miss? true})))))
  (testing "a crit adds the crit entry; charge and recharge entries never join a roll"
    (is (= [[1 8 :slashing] [2 8 :psychic]]
           (map (juxt ::weapons/damage-die-count ::weapons/damage-die ::weapons/damage-type)
                (wd/applying-damage homebrew-weapon 0 false {:roll 18 :crit? true}))))))

(deftest crit-range
  (is (= 20 (wd/crit-min nil (weapon :longsword))))
  (is (= 19 (wd/crit-min #{19 20} (weapon :longsword))))
  (is (= 18 (wd/crit-min #{19 20} homebrew-weapon)))
  (is (= 17 (wd/crit-min #{17 18 19 20} homebrew-weapon))))

(deftest a-long-rest-clears-dawn
  (is (= #{::units/long-rest ::units/rest ::units/dawn} (set units/long-rest-clears))))

(deftest conditions-added-for-the-other-srd-weapons
  (testing "Oathbow's extra applies only against the sworn enemy"
    (is (= [[1 8 :piercing]] (rolled :oathbow {})))
    (is (= [[1 8 :piercing] [3 6 :piercing]] (rolled :oathbow {:toggles #{:sworn-enemy}}))))
  (testing "Dwarven Thrower: nothing extra in melee; thrown, 1d8, or 2d8 against a giant"
    (is (= [[1 8 :bludgeoning]] (rolled :dwarven-thrower {})))
    (is (= [[1 8 :bludgeoning] [1 8 :bludgeoning]] (rolled :dwarven-thrower {:attack-type :ranged})))
    (is (= [[1 8 :bludgeoning] [2 8 :bludgeoning]]
           (rolled :dwarven-thrower {:attack-type :ranged :against :giant}))))
  (testing "Sword of Life Stealing's necrotic skips constructs and undead"
    (is (= [[1 8 :slashing] [3 6 :necrotic]] (rolled :sword-of-life-stealing-longsword {:roll 20})))
    (is (= [[1 8 :slashing]] (rolled :sword-of-life-stealing-longsword {:roll 20 :against :undead}))))
  (testing "Mace of Smiting adds a flat +2 against constructs, and 4d6 instead of 2d6 on a 20"
    (is (= [nil 2]
           (map ::mi/damage-bonus (wd/applying-damage (weapon :mace-of-smiting) 1 false {:against :construct}))))
    (is (= [[1 6 :bludgeoning] [2 6 :bludgeoning]] (rolled :mace-of-smiting {:roll 20})))
    (is (= [[1 6 :bludgeoning] [nil nil :bludgeoning] [4 6 :bludgeoning]]
           (rolled :mace-of-smiting {:roll 20 :against :construct}))))
  (testing "Javelin of Lightning's 4d6 is spent from its own button"
    (is (= [[1 6 :piercing]] (rolled :javelin-of-lightning {:attack-type :ranged})))))

(deftest attack-type-defaults-to-the-weapons-own
  (let [bow (assoc (weapon :longbow) ::mi/extra-damage [(assoc (dice 1 4) ::mi/attack-type :ranged)])]
    (is (= 2 (count (wd/applying-damage bow 0 false {}))))
    (is (= 1 (count (wd/applying-damage bow 0 false {:attack-type :melee}))))))
