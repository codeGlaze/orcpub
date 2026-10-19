(ns orcpub.dnd.e5.weapon-rolls-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.weapon-rolls :as rolls]))

(defn dice
  "A die fn that returns these numbers in order, whatever the sides."
  [& results]
  (let [left (atom results)]
    (fn [_] (let [r (first @left)] (swap! left rest) r))))

(defn weapon
  "The weapon with this key from the static weapons map."
  [k]
  (or (mi/all-weapons-map k) (throw (ex-info "No such weapon" {:key k}))))

(deftest an-attack-keeps-the-right-die
  (is (= {:dice [13] :kept 13 :bonus 7 :total 20 :crit? false :mode :normal}
         (rolls/roll-attack {:bonus 7} (dice 13))))
  (testing "advantage keeps the higher, disadvantage the lower"
    (is (= [14 21] ((juxt :kept :total) (rolls/roll-attack {:bonus 7 :mode :adv} (dice 6 14)))))
    (is (= [6 13] ((juxt :kept :total) (rolls/roll-attack {:bonus 7 :mode :dis} (dice 6 14))))))
  (testing "a crit is the kept die reaching the crit range"
    (is (:crit? (rolls/roll-attack {:bonus 7} (dice 20))))
    (is (not (:crit? (rolls/roll-attack {:bonus 7} (dice 19)))))
    (is (:crit? (rolls/roll-attack {:bonus 7 :crit-min 19} (dice 19))))
    (is (not (:crit? (rolls/roll-attack {:bonus 7 :mode :dis} (dice 20 3)))) "disadvantage keeps the 3")))

(deftest damage-is-one-line-per-part-then-the-total
  (is (= {:parts [{:label "radiant" :type :radiant :rolls [6] :flat 5 :detail "1d8 [6] + 5" :subtotal 11}
                  {:label "radiant · vs undead" :type :radiant :rolls [3] :flat 0 :detail "1d8 [3]" :subtotal 3}]
          :total 14 :after []}
         (rolls/roll-damage (weapon :sun-blade-longsword) 5 false {:against :undead} false (dice 6 3)))))

(deftest a-switched-on-extra-reads-as-its-switch
  (is (= ["slashing" "fire · ablaze"]
         (map :label (:parts (rolls/roll-damage (weapon :flame-tongue-longsword) 2 false
                                                 {:toggles #{:ablaze}} false (dice 5 3 6)))))))

(deftest a-crit-doubles-every-die-but-not-the-bonus
  (let [r (rolls/roll-damage (weapon :sun-blade-longsword) 5 false {:against :undead :crit? true :roll 20} true
                             (dice 6 2 3 1))]
    (is (= ["2d8 [6,2] + 5" "2d8 [3,1]"] (map :detail (:parts r))))
    (is (= 17 (:total r)))))

(deftest saves-come-after-the-total
  (let [r (rolls/roll-damage (weapon :giant-slayer-greataxe) 4 false {:against :giant} false (dice 7 3 4))]
    (is (= 18 (:total r)) "1d12+4 (7+4) and 2d6 (3+4): the save adds nothing")
    (is (= [{:label "Target saves" :text "DC 15 Str or prone"}] (:after r))))
  (is (= [] (:after (rolls/roll-damage (weapon :giant-slayer-greataxe) 4 false {} false (dice 7))))
      "no save against a target that is not a giant"))

(deftest a-negative-bonus-reads-as-minus
  (is (= "1d8 [4] - 1" (:detail (first (:parts (rolls/roll-damage (weapon :longsword) -1 false {} false (dice 4))))))))
