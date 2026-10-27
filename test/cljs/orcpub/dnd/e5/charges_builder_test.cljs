(ns orcpub.dnd.e5.charges-builder-test
  "The item builder's Charges group: typing charges, picking a cast's spell, and the line that
  says what a spell rolls."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.views.charges-builder :as cb]))

(deftest typed-charges-are-a-number-or-a-rolled-start
  (is (= {::mi/charges 7} (cb/set-charges {} "7")))
  (is (= {::mi/charges-start "1d8+1"} (cb/set-charges {::mi/charges 7} "1d8+1")) "dice replace a number")
  (is (= {::mi/charges 3} (cb/set-charges {::mi/charges-start "1d8+1"} " 3 ")) "a number replaces dice")
  (is (= {} (cb/set-charges {::mi/charges 7} "")) "blank clears")
  (is (= {} (cb/set-charges {} "0")) "no charges is not a maximum of 0"))

(deftest picking-a-spell-takes-its-level
  (let [item {::mi/casts [{::mi/cast-level 1 ::mi/cast-cost 2}]}]
    (testing "the cast's level becomes the spell's, its price is kept"
      (is (= {::mi/cast-spell :fireball ::mi/cast-level 3 ::mi/cast-cost 2}
             (get-in (cb/set-cast item 0 ::mi/cast-spell :fireball 3) [::mi/casts 0]))))
    (testing "a cleared field is removed, not stored as nil"
      (is (= {::mi/cast-level 1}
             (get-in (cb/set-cast item 0 ::mi/cast-cost nil nil) [::mi/casts 0]))))))

(deftest a-spell-says-what-it-rolls
  (is (= "8d6 fire · Dex save, half on success · +1d6 per level above 3rd"
         (cb/roll-hint {:save :dex :save-half? true :damage [{:dice "8d6" :type :fire :per-slot "1d6"}]} 3)))
  (is (= "Heals 1d8 + modifier · +1d8 per level above 1st"
         (cb/roll-hint {:heal {:dice "1d8" :mod? true :per-slot "1d8"}} 1)))
  (is (= "1d4 + 1 force, 3 times"
         (cb/roll-hint {:damage [{:dice "1d4 + 1" :type :force}] :count {:n 3 :per-slot 1}} 1)))
  (is (= "1d8 force + modifier · Melee spell attack · +1d8 per 2 levels above 2nd"
         (cb/roll-hint {:attack :melee :damage [{:dice "1d8" :type :force :mod? true :per-slot "1d8" :slot-step 2}]} 2)))
  (is (nil? (cb/roll-hint {:save :wis} 1)) "a save with no roll says nothing")
  (is (nil? (cb/roll-hint nil nil)) "a cast with no spell chosen yet, or a spell with no rolling data"))

(deftest typed-dice-set-the-damage-fields
  (is (= {:orcpub.dnd.e5.weapons/damage-die 6 :orcpub.dnd.e5.weapons/damage-die-count 2} (cb/set-power-dice {} "2d6")))
  (is (= {:orcpub.dnd.e5.weapons/damage-die 8 :orcpub.dnd.e5.weapons/damage-die-count 1 ::mi/damage-bonus 2}
         (cb/set-power-dice {} "1d8+2")))
  (is (= {::mi/damage-bonus 3} (cb/set-power-dice {:orcpub.dnd.e5.weapons/damage-die 6} "3")) "a flat amount")
  (is (= {::mi/on :miss} (cb/set-power-dice {::mi/on :miss :orcpub.dnd.e5.weapons/damage-die 6} "")) "blank clears the dice only"))

(deftest choosing-a-save-makes-an-effect-a-save
  (let [item {::mi/effects [{::mi/effect-type :condition ::mi/text "Knocked down."}]}]
    (is (= :save (get-in (cb/set-power item ::mi/effects 0 ::mi/save-ability :str) [::mi/effects 0 ::mi/effect-type])))
    (is (= :condition (get-in (-> item (cb/set-power ::mi/effects 0 ::mi/save-ability :str)
                                  (cb/set-power ::mi/effects 0 ::mi/save-ability nil))
                              [::mi/effects 0 ::mi/effect-type]))
        "and no save makes it a plain effect again")))

(deftest a-switch-keeps-its-id-and-leaves-cleanly
  (let [item (-> {} (cb/add-toggle "Ablaze") (cb/add-toggle "Frost"))
        item (assoc item ::mi/extra-damage [{::mi/while :switch-0 ::mi/damage-bonus 2}])]
    (is (= [:switch-0 :switch-1] (map ::mi/toggle-id (::mi/toggles item))))
    (is (= [{::mi/damage-bonus 2}] (::mi/extra-damage (cb/remove-toggle item 0))) "its rows no longer wait for it")
    (is (= [:switch-1] (map ::mi/toggle-id (::mi/toggles (cb/remove-toggle item 0)))))
    (is (= #{:undead} (get-in (cb/set-power item ::mi/extra-damage 0 ::mi/against :undead) [::mi/extra-damage 0 ::mi/against]))
        "a creature type is stored as a set of one")))

