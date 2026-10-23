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

(deftest each-item-in-a-stack-has-its-own-pool
  (let [entry {::char-equip/quantity 2 ::char-equip/charges-used [2 7]}]
    (is (= 5 (mi/charges-left wand entry 0)))
    (is (= 0 (mi/charges-left wand entry 1)))
    (is (= 7 (mi/charges-left wand {::char-equip/quantity 1} 0)) "nothing spent yet")))

(deftest spending-takes-from-one-item-only
  (let [entry {::char-equip/charges-used [2]}]
    (is (= [5] (::char-equip/charges-used (mi/spend-charges wand entry 0 3))))
    (is (nil? (mi/spend-charges wand entry 0 6)) "only 5 left")
    (is (= [2 4] (::char-equip/charges-used (mi/spend-charges wand entry 1 4))))
    (is (= [2 0 4] (::char-equip/charges-used (mi/spend-charges wand entry 2 4)))
        "the third wand: the untouched second one is padded in as 0")))

(deftest regaining-never-passes-the-maximum
  (let [entry {::char-equip/charges-used [6]}]
    (is (= [1] (::char-equip/charges-used (mi/regain-charges wand entry 0 5))))
    (is (= [0] (::char-equip/charges-used (mi/regain-charges wand entry 0 9))))
    (is (= [0] (::char-equip/charges-used (mi/regain-charges wand entry 0 :all))))))

(deftest a-regain-is-rolled-or-all
  (is (= {:amount 5 :rolls [4] :dice "1d6+1"} (mi/roll-regain wand (dice-fn 4))))
  (is (= :all (:amount (mi/roll-regain {::mi/charges 3} (dice-fn 4)))) "no regain named: all of them"))

(deftest a-rolled-start-sets-the-maximum
  (let [stealer {::mi/charges-start "1d8+1"}]
    (is (= 6 (:total (mi/roll-charge-max stealer (dice-fn 5)))))
    (is (= 6 (mi/charge-max stealer {::char-equip/charges-max [6]} 0)) "the rolled maximum is what counts")
    (is (nil? (mi/roll-charge-max wand (dice-fn 5))) "a fixed maximum rolls nothing")))

(deftest a-cast-is-priced-in-charges
  (testing "Wand of Fireballs: 1 charge for 3rd, 1 more per level, up to 9th"
    (is (= [[3 1 true] [4 2 true] [5 3 true] [6 4 false] [7 5 false] [8 6 false] [9 7 false]]
           (map (juxt :level :cost :affordable?) (mi/cast-levels (first (::mi/casts wand)) 3)))))
  (testing "a fixed cost (a staff): one level only"
    (is (= [{:level 3 :cost 3 :affordable? true}]
           (mi/cast-levels {::mi/cast-spell :fireball ::mi/cast-level 3 ::mi/cast-cost 3} 10)))))
