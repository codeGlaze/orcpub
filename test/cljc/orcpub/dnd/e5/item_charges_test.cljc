(ns orcpub.dnd.e5.item-charges-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dice :as dice]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.character.equipment :as char-equip]))

(defn dice-fn
  "A die fn that returns these numbers in order, whatever the sides."
  [& results]
  (let [left (atom results)]
    (fn [_] (let [r (first @left)] (swap! left rest) r))))

(def wand
  "A Wand of Fireballs as the plan writes it: 7 charges, 1d6+1 back at dawn, Fireball from 3rd."
  {::mi/name "Wand of Fireballs" ::mi/charges 7 ::mi/charges-recharge :orcpub.dnd.e5.units/dawn
   ::mi/charges-regain "1d6+1"
   ::mi/casts [{::mi/cast-spell :fireball ::mi/cast-level 3 ::mi/cast-cost 1 ::mi/cast-per-level 1 ::mi/save-dc 15}]})

(deftest dice-and-numbers-parse
  (is (= {:num 1 :sides 6 :mod 1} (dice/parse-dice "1d6+1")))
  (is (= {:num 4 :sides 6 :mod 2} (dice/parse-dice "4d6 + 2")))
  (is (= {:num 0 :sides nil :mod 5} (dice/parse-dice "5")) "a plain number")
  (is (nil? (dice/parse-dice "all")))
  (is (= {:total 7 :rolls [3 3]} (dice/roll-dice (dice/parse-dice "2d6+1") (dice-fn 3 3)))))

(defn used
  "An entry's spent charges by position, for comparing: {index used}."
  [entry]
  (into {} (map (juxt ::char-equip/charge-index ::char-equip/charges-used)) (::char-equip/charges entry)))

(defn rec
  "A charges record: the item at position i in its stack, n charges spent."
  [i n] {::char-equip/charge-index i ::char-equip/charges-used n})

(deftest each-item-in-a-stack-has-its-own-pool
  (let [entry {::char-equip/quantity 2 ::char-equip/charges [(rec 1 7) (rec 0 2)]}]
    (is (= 5 (mi/charges-left wand entry 0)))
    (is (= 0 (mi/charges-left wand entry 1)))
    (is (= 7 (mi/charges-left wand {::char-equip/quantity 1} 0)) "nothing spent yet")))

(deftest spending-takes-from-one-item-only
  (let [entry {::char-equip/charges [(rec 0 2)]}]
    (is (= {0 5} (used (mi/spend-charges wand entry 0 3))))
    (is (nil? (mi/spend-charges wand entry 0 6)) "only 5 left")
    (is (= {0 2 2 4} (used (mi/spend-charges wand entry 2 4))) "the third wand gets its own record")))

(deftest regaining-never-passes-the-maximum
  (let [entry {::char-equip/charges [(rec 0 6)]}]
    (is (= {0 1} (used (mi/regain-charges wand entry 0 5))))
    (is (= {0 0} (used (mi/regain-charges wand entry 0 9))))
    (is (= {0 0} (used (mi/regain-charges wand entry 0 :all))))))

(deftest a-regain-is-rolled-or-all
  (is (= {:amount 5 :rolls [4] :dice "1d6+1"} (mi/roll-regain wand (dice-fn 4))))
  (is (= :all (:amount (mi/roll-regain {::mi/charges 3} (dice-fn 4)))) "no regain named: all of them"))

(deftest a-rolled-start-sets-the-maximum
  (let [stealer {::mi/charges-start "1d8+1"}]
    (is (= 6 (:total (mi/roll-charge-max stealer (dice-fn 5)))))
    (is (= 6 (mi/charge-max stealer {::char-equip/charges [{::char-equip/charge-index 0 ::char-equip/charges-max 6}]} 0)) "the rolled maximum is what counts")
    (is (nil? (mi/roll-charge-max wand (dice-fn 5))) "a fixed maximum rolls nothing")))

(deftest a-cast-is-priced-in-charges
  (testing "Wand of Fireballs: 1 charge for 3rd, 1 more per level, up to 9th"
    (is (= [[3 1 true] [4 2 true] [5 3 true] [6 4 false] [7 5 false] [8 6 false] [9 7 false]]
           (map (juxt :level :cost :affordable?) (mi/cast-levels (first (::mi/casts wand)) 3)))))
  (testing "a fixed cost (a staff): one level only"
    (is (= [{:level 3 :cost 3 :affordable? true}]
           (mi/cast-levels {::mi/cast-spell :fireball ::mi/cast-level 3 ::mi/cast-cost 3} 10)))))

(deftest the-first-item-that-can-pay-pays
  (let [entry {::char-equip/quantity 2 ::char-equip/charges [(rec 0 6) (rec 1 2)]}]
    (is (= 0 (mi/able-to-pay wand entry 1)) "the first wand has 1 left")
    (is (= 1 (mi/able-to-pay wand entry 3)) "only the second has 3")
    (is (nil? (mi/able-to-pay wand entry 6)) "neither has 6")))

(deftest a-stack-casts-from-its-fullest-item
  (let [entry {::char-equip/quantity 2 ::char-equip/charges [(rec 0 6) (rec 1 2)]}]
    (is (= 5 (mi/most-charges-left wand entry)) "the second wand has 5")
    (is (= [true true true true true false false]
           (map :affordable? (mi/cast-levels (first (::mi/casts wand)) (mi/most-charges-left wand entry))))
        "up to 7th with 5 charges")))

(deftest the-higher-levels-sentence-is-found
  (is (= "The damage increases by 1d6 for each slot level above 3rd."
         (mi/higher-levels-text {:description "A bright streak.\nAt Higher Levels. The damage increases by 1d6 for each slot level above 3rd."})))
  (is (nil? (mi/higher-levels-text {:description "No scaling."}))))

(deftest a-saved-weapons-powers-load-and-save-cleanly
  (is (= #{:undead} (get-in (mi/to-internal-item {::mi/name "X" ::mi/extra-damage [{::mi/damage-bonus 2 ::mi/against [:undead]}]})
                            [::mi/extra-damage 0 ::mi/against]))
      "creature types saved as a vector load as a set")
  (is (= [{::mi/damage-bonus 2}]
         (::mi/extra-damage (mi/complete-powers {::mi/extra-damage [{::mi/damage-bonus 2} {::mi/damage-bonus :ability-mod}]})))
      "a row holding a reference is not sent to the save"))
