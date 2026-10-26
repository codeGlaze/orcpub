(ns orcpub.dnd.e5.weapon-rolls-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.srd-conditions :as srd-conditions]
            [orcpub.dnd.e5.weapons :as weapons]
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
    (is (= [{:label "Target saves" :text "The target makes a DC 15 Str save or falls Prone."}] (:after r))))
  (is (= [] (:after (rolls/roll-damage (weapon :giant-slayer-greataxe) 4 false {} false (dice 7))))
      "no save against a target that is not a giant"))

(deftest a-negative-bonus-reads-as-minus
  (is (= "1d8 [4] - 1" (:detail (first (:parts (rolls/roll-damage (weapon :longsword) -1 false {} false (dice 4))))))))

(deftest the-sheet-offers-damage-then-each-target
  (is (= [["Damage" "1d8+5"] ["vs undead" "1d8+5 +1d8"]]
         (map (juxt :label :value) (rolls/damage-options (weapon :sun-blade-longsword) 5 false #{} false))))
  (is (= [["Critical damage" "2d8+5"] ["Critical vs undead" "2d8+5 +2d8"]]
         (map (juxt :label :value) (rolls/damage-options (weapon :sun-blade-longsword) 5 false #{} true))))
  (is (= [["Damage" "1d8+2"]]
         (map (juxt :label :value) (rolls/damage-options (weapon :longsword) 2 false #{} false)))
      "a plain weapon offers Damage alone")
  (is (= {:against :undead} (:situation (second (rolls/damage-options (weapon :sun-blade-longsword) 5 false #{} false))))))

(def fireball
  "Fireball's rolling data as spell-rolls.edn writes it."
  {:save :dex :save-half? true :damage [{:dice "8d6" :type :fire :per-slot "1d6"}]})

(deftest a-spell-rolls-more-dice-at-a-higher-slot
  (let [{:keys [parts total]} (rolls/roll-spell fireball 3 5 nil (constantly 2))]
    (is (= ["fire"] (map :label parts)))
    (is (= 10 (count (:rolls (first parts)))) "8d6 at 3rd, +2d6 at 5th")
    (is (= 20 total))))

(deftest every-dart-rolls-on-its-own
  (let [missile {:damage [{:dice "1d4 + 1" :type :force}] :count {:n 3 :per-slot 1}}
        {:keys [parts total]} (rolls/roll-spell missile 1 3 nil (constantly 1))]
    (is (= 5 (count parts)) "three darts at 1st, five at 3rd")
    (is (= 10 total) "each dart 1 + 1")))

(deftest some-spells-scale-every-two-levels
  (let [weapon {:attack :melee :damage [{:dice "1d8" :type :force :mod? true :per-slot "1d8" :slot-step 2}]}]
    (is (= 2 (count (:rolls (first (:parts (rolls/roll-spell weapon 2 4 3 (constantly 1))))))) "2d8 at 4th")
    (is (= 1 (count (:rolls (first (:parts (rolls/roll-spell weapon 2 3 3 (constantly 1))))))) "still 1d8 at 3rd")
    (is (= 5 (:total (rolls/roll-spell weapon 2 3 3 (constantly 2)))) "the modifier is added")))

(deftest a-choice-of-type-adds-its-own-part
  (let [strike {:damage [{:dice "4d6" :type :fire} {:dice "4d6" :type :radiant}]
                :per-slot {:dice "1d6" :types [:fire :radiant]}}]
    (is (= 2 (count (:parts (rolls/roll-spell strike 5 5 nil (constantly 1))))) "nothing extra at 5th")
    (is (= 2 (count (:rolls (last (:parts (rolls/roll-spell strike 5 7 nil (constantly 1))))))) "2d6 more at 7th")))

(deftest a-flat-amount-and-no-damage
  (is (= 20 (:total (rolls/roll-spell {:damage [{:dice "20" :type :radiant}]} 4 4 nil (constantly 6)))))
  (is (nil? (rolls/roll-spell {:save :wis} 1 1 nil (constantly 1))) "a spell with no damage rolls nothing"))

(def referencing-weapon
  "A made-up longsword whose extra damage and effect name the attack's values, not numbers:
  damage equal to the ability modifier on a miss, and a save at the weapon's own DC on a hit."
  {:name "Test Blade" ::weapons/damage-die 8 ::weapons/damage-die-count 1 ::weapons/damage-type :slashing
   ::weapons/melee? true
   ::mi/extra-damage [{::mi/damage-bonus :ability-mod ::mi/on :miss}]
   ::mi/effects [{::mi/effect-type :save ::mi/save-dc :attack-save-dc ::mi/save-ability :con
                  ::mi/save-fail :prone ::mi/text "Prone on a failed save."}]})

(deftest references-are-valid-item-data
  (is (spec/valid? ::mi/extra-damage (::mi/extra-damage referencing-weapon)))
  (is (spec/valid? ::mi/effect (first (::mi/effects referencing-weapon)))))

(deftest a-weapons-references-become-its-numbers
  (let [w (mi/resolve-values referencing-weapon {:ability-mod 3 :save-dc 13})]
    (testing "the text"
      (is (= "+3" (disp/damage-dice-str (first (::mi/extra-damage w)))))
      (is (= "DC 13 Con or prone" (disp/effect-summary (first (::mi/effects w))))))
    (testing "the roll: a miss deals the modifier"
      (is (= 3 (:total (rolls/roll-damage w 3 false {:miss? true} false (constantly 1))))))))


(deftest a-miss-damage-weapon-offers-missed
  (let [w (mi/resolve-values referencing-weapon {:ability-mod 3 :save-dc 13})
        missed (some #(when (= "Missed?" (:label %)) %) (rolls/damage-options w 3 false nil false))]
    (is (= "+3" (:value missed)))
    (is (:pulse? missed) "it pulses, as an option that continues the turn")
    (is (not-any? #(= "Missed?" (:label %)) (rolls/damage-options w 3 false nil true)) "not after a crit")
    (is (not-any? #(= "Missed?" (:label %)) (rolls/damage-options (dissoc w ::mi/extra-damage) 3 false nil false))
        "not for a weapon with no damage on a miss")))

(deftest effects-read-as-sentences-a-link-can-find
  (let [w (mi/resolve-values referencing-weapon {:ability-mod 3 :save-dc 13})]
    (is (= [{:label "Target saves" :text "The target makes a DC 13 Con save or falls Prone."}]
           (rolls/after-total w {})))
    (is (= [{:label "Effect" :text "It is pushed."}]
           (rolls/after-total (assoc w ::mi/effects [{::mi/effect-type :condition ::mi/text "It is pushed."}]) {}))
        "a text-only effect is listed too")
    (is (some vector? (srd-conditions/link-conditions "The target makes a DC 13 Con save or falls Prone."))
        "the condition link finds \"falls Prone\"")))

(deftest a-click-mode-wins-over-the-switch
  (is (= :adv (rolls/attack-mode :adv :dis)) "ctrl-click over the header's Dis")
  (is (= :dis (rolls/attack-mode nil :dis)) "no click mode: the header's")
  (is (= :adv (rolls/attack-mode :normal :adv)) "a plain click keeps the header's Adv")
  (is (= :normal (rolls/attack-mode nil nil))))

(deftest an-effect-can-set-the-next-attacks-mode
  (let [vexing (assoc referencing-weapon ::mi/extra-damage []
                      ::mi/effects [{::mi/effect-type :condition ::mi/text "Advantage on your next attack against it."
                                     ::mi/next-attack :adv}])]
    (is (spec/valid? ::mi/effects (::mi/effects vexing)))
    (is (= :adv (:next-mode (rolls/roll-damage vexing 3 false {} false (constantly 1)))) "after a hit")
    (is (nil? (:next-mode (rolls/roll-damage vexing 3 false {:miss? true} false (constantly 1)))) "not after a miss")
    (is (nil? (:next-mode (rolls/roll-damage referencing-weapon 3 false {} false (constantly 1)))) "not without the field")))
