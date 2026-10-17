(ns orcpub.dnd.e5.damage-parts-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.damage-parts :as dp]
            [orcpub.dnd.e5.magic-items :as mi]))

(def srd-extras
  "The extra damage each SRD 5.1 magic weapon deals, as written in its description."
  {"Frost Brand" [{::dp/die-count 1 ::dp/die 6 ::dp/type :cold ::dp/when :always}]
   "Flame Tongue" [{::dp/die-count 2 ::dp/die 6 ::dp/type :fire ::dp/when {::dp/switch :ablaze}}]
   "Sun Blade" [{::dp/die-count 1 ::dp/die 8 ::dp/type :radiant ::dp/when {::dp/vs #{:undead}}}]
   "Holy Avenger" [{::dp/die-count 2 ::dp/die 10 ::dp/type :radiant ::dp/when {::dp/vs #{:fiend :undead}}}]
   "Mace of Disruption" [{::dp/die-count 2 ::dp/die 6 ::dp/type :radiant ::dp/when {::dp/vs #{:fiend :undead}}}]
   "Giant Slayer" [{::dp/die-count 2 ::dp/die 6 ::dp/type :weapon ::dp/when {::dp/vs #{:giant}}}]
   "Dragon Slayer" [{::dp/die-count 3 ::dp/die 6 ::dp/type :weapon ::dp/when {::dp/vs #{:dragon}}}]
   "Vicious Weapon" [{::dp/die-count 2 ::dp/die 6 ::dp/type :weapon ::dp/when :crit}]})

(defn weapon
  "The weapon with this key from the static weapons map."
  [k]
  (or (mi/all-weapons-map k) (throw (ex-info "No such weapon" {:key k}))))

(defn variants
  "Every expanded weapon (one per base weapon) of the magic item with this name."
  [item-name]
  (filter #(= item-name (::mi/name %)) (vals mi/all-weapons-map)))

(deftest srd-weapons-carry-their-extra-damage
  (doseq [[item-name parts] srd-extras]
    (testing item-name
      (is (seq (variants item-name)))
      (doseq [w (variants item-name)]
        (is (= parts (::mi/damage-parts w)) (:name w)))))
  (testing "no other weapon declares extra damage"
    (is (= (set (keys srd-extras))
           (set (keep #(when (::mi/damage-parts %) (::mi/name %)) (vals mi/all-weapons-map)))))))

(deftest srd-weapon-data-conforms
  (doseq [w (vals mi/all-weapons-map)]
    (when-let [parts (::mi/damage-parts w)]
      (is (spec/valid? ::dp/parts parts) (:name w)))
    (when-let [effects (::mi/effects w)]
      (is (spec/valid? ::dp/effects effects) (:name w)))
    (testing "every switch a part names is declared on the item"
      (let [declared (set (map ::dp/id (::mi/switches w)))]
        (doseq [s (keep (comp ::dp/switch ::dp/when) (::mi/damage-parts w))]
          (is (contains? declared s) (:name w)))))))

(deftest save-effects
  (is (= [[15 :orcpub.dnd.e5.character/str :prone]]
         (map (juxt ::dp/dc ::dp/ability ::dp/on-fail) (::mi/effects (weapon :giant-slayer-greataxe)))))
  (is (= [[15 :orcpub.dnd.e5.character/wis :destroyed]]
         (map (juxt ::dp/dc ::dp/ability ::dp/on-fail) (::mi/effects (weapon :mace-of-disruption))))))

(deftest base-part
  (testing "one hand, then a Versatile weapon in two"
    (is (= [{::dp/die-count 1 ::dp/die 8 ::dp/bonus 3 ::dp/type :slashing ::dp/when :always}]
           (dp/weapon-parts (weapon :longsword) 3 false)))
    (is (= [{::dp/die-count 1 ::dp/die 10 ::dp/bonus 3 ::dp/type :slashing ::dp/when :always}]
           (dp/weapon-parts (weapon :longsword) 3 true))))
  (testing "two hands changes nothing on a weapon that is not Versatile"
    (is (= (dp/weapon-parts (weapon :greatsword) 2 false)
           (dp/weapon-parts (weapon :greatsword) 2 true))))
  (testing "the Net has no damage dice, so no parts"
    (is (= [] (dp/weapon-parts (weapon :net) 2 false))))
  (testing "Sun Blade deals radiant instead of slashing"
    (is (= :radiant (::dp/type (first (dp/weapon-parts (weapon :sun-blade-longsword) 2 false)))))))

(deftest weapon-type-extras-resolve
  (is (= [:slashing :slashing]
         (map ::dp/type (dp/weapon-parts (weapon :giant-slayer-greataxe) 1 false))))
  (is (= [:bludgeoning :bludgeoning]
         (map ::dp/type (dp/weapon-parts (weapon :vicious-weapon-mace) 0 false))))
  (is (= [:piercing :piercing]
         (map ::dp/type (dp/weapon-parts (weapon :dragon-slayer-rapier) 1 false)))))

(defn rolled
  "The [die-count die type] of each part a roll adds up for a weapon in a situation."
  [k situation]
  (map (juxt ::dp/die-count ::dp/die ::dp/type)
       (dp/applying-parts (weapon k) 0 false situation)))

(deftest which-parts-apply
  (testing "Frost Brand's cold applies on every hit"
    (is (= [[1 8 :slashing] [1 6 :cold]] (rolled :frost-brand-longsword {}))))
  (testing "Flame Tongue's fire applies only while ablaze"
    (is (= [[1 8 :slashing]] (rolled :flame-tongue-longsword {})))
    (is (= [[1 8 :slashing]] (rolled :flame-tongue-longsword {:switches #{:other}})))
    (is (= [[1 8 :slashing] [2 6 :fire]] (rolled :flame-tongue-longsword {:switches #{:ablaze}}))))
  (testing "Holy Avenger's radiant applies against fiends and undead only"
    (is (= [[1 8 :slashing]] (rolled :holy-avenger-longsword {})))
    (is (= [[1 8 :slashing]] (rolled :holy-avenger-longsword {:vs :giant})))
    (is (= [[1 8 :slashing] [2 10 :radiant]] (rolled :holy-avenger-longsword {:vs :fiend})))
    (is (= [[1 8 :slashing] [2 10 :radiant]] (rolled :holy-avenger-longsword {:vs :undead}))))
  (testing "Vicious Weapon's extra applies on a natural 20 only"
    (is (= [[1 6 :bludgeoning]] (rolled :vicious-weapon-mace {})))
    (is (= [[1 6 :bludgeoning] [2 6 :bludgeoning]] (rolled :vicious-weapon-mace {:crit? true}))))
  (testing "miss, charge and per-rest parts are never part of an ordinary roll"
    (is (false? (dp/applies? {::dp/when :miss} {:crit? true})))
    (is (false? (dp/applies? {::dp/when {::dp/charge 1}} {:crit? true :switches #{:a} :vs :undead})))
    (is (false? (dp/applies? {::dp/when {::dp/per-rest :long}} {:crit? true})))))

(def homebrew-weapon
  "A made-up weapon using every `when` and effect kind the SRD has no example of."
  (merge (weapon :longsword)
         {::mi/name "Test Blade"
          ::mi/charges 3
          ::mi/damage-parts [{::dp/die-count 1 ::dp/die 4 ::dp/bonus 1 ::dp/type :force ::dp/when {::dp/charge 1}}
                             {::dp/die-count 3 ::dp/die 6 ::dp/type :thunder ::dp/when {::dp/per-rest :short}}
                             {::dp/die-count 1 ::dp/die 4 ::dp/type :weapon ::dp/when :miss}]
          ::mi/effects [{::dp/kind :heal ::dp/text "You regain 1d4 hit points." ::dp/when :always}
                        {::dp/kind :condition ::dp/text "The target is deafened." ::dp/when {::dp/charge 1}}]}))

(deftest homebrew-scope-fits-the-model
  (is (spec/valid? ::dp/parts (::mi/damage-parts homebrew-weapon)))
  (is (spec/valid? ::dp/effects (::mi/effects homebrew-weapon)))
  (is (spec/valid? ::dp/charges (::mi/charges homebrew-weapon)))
  (testing "an unknown when or damage type is rejected"
    (is (not (spec/valid? ::dp/part {::dp/die-count 1 ::dp/die 4 ::dp/type :force ::dp/when :sometimes})))
    (is (not (spec/valid? ::dp/part {::dp/die-count 1 ::dp/die 4 ::dp/type :sonic ::dp/when :always}))))
  (testing "an ordinary hit rolls only the base part"
    (is (= [[1 8 :slashing]]
           (map (juxt ::dp/die-count ::dp/die ::dp/type)
                (dp/applying-parts homebrew-weapon 0 false {:crit? true :vs :undead})))))
  (testing "a miss part resolves to the weapon's type"
    (is (= :slashing (::dp/type (last (dp/weapon-parts homebrew-weapon 0 false)))))))
