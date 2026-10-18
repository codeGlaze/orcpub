(ns orcpub.dnd.e5.weapon-damage-display-test
  (:require [clojure.test :refer [deftest is]]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.weapon-damage :as wd]))

(defn weapon
  "The weapon with this key from the static weapons map."
  [k]
  (or (mi/all-weapons-map k) (throw (ex-info "No such weapon" {:key k}))))

(defn are-extras
  "Checks each weapon key's one-line extras text against the expected string."
  [expected]
  (doseq [[k text] expected]
    (is (= text (disp/weapon-extras-str (weapon k))) (name k))))

(deftest base-damage-text
  (is (= "1d8+3 slashing" (disp/damage-str (wd/base-damage (weapon :longsword) 3 false))))
  (is (= "1d10-1 slashing" (disp/damage-str (wd/base-damage (weapon :longsword) -1 true))))
  (is (= "2d6 slashing" (disp/damage-str (wd/base-damage (weapon :greatsword) 0 false))))
  (is (= "1d8+4 radiant" (disp/damage-str (wd/base-damage (weapon :sun-blade-longsword) 4 false)))))

(deftest every-srd-weapon-reads-as-its-rule
  (are-extras
   {:frost-brand-longsword "+1d6 cold"
    :flame-tongue-longsword "+2d6 fire while Ablaze"
    :sun-blade-longsword "+1d8 radiant vs undead"
    :holy-avenger-longsword "+2d10 radiant vs fiends and undead"
    :mace-of-disruption "+2d6 radiant vs fiends and undead; DC 15 Wis or destroyed, vs fiends and undead"
    :giant-slayer-greataxe "+2d6 slashing vs giants; DC 15 Str or prone, vs giants"
    :dragon-slayer-longsword "+3d6 slashing vs dragons"
    :vicious-weapon-mace "+2d6 bludgeoning on a 20"
    :oathbow "+3d6 piercing while Sworn enemy"
    :dwarven-thrower "+1d8 bludgeoning on a ranged hit, not vs giants; +2d8 bludgeoning on a ranged hit, vs giants"
    :mace-of-smiting (str "+2 bludgeoning vs constructs; +2d6 bludgeoning on a 20, not vs constructs; "
                          "+4d6 bludgeoning on a 20, vs constructs")
    :javelin-of-lightning (str "+4d6 lightning on a ranged hit, once per dawn; "
                               "DC 13 Dex save, 4d6 lightning, once per dawn")
    :dagger-of-venom "DC 15 Con or poisoned, 2d10 poison, once per dawn"
    :sword-of-life-stealing-longsword "+3d6 necrotic on a 20, not vs constructs or undead"
    :sword-of-sharpness-longsword "+4d6 slashing on a 20"
    :vorpal-sword-longsword "+6d8 slashing on a 20"
    :longsword nil}))

(deftest condition-phrases-cover-every-field
  (is (= ["on a miss"] (disp/condition-phrases {::mi/on :miss} {})))
  (is (= ["on a crit"] (disp/condition-phrases {::mi/on :crit} {})))
  (is (= ["on an 18+"] (disp/condition-phrases {::mi/min-roll 18} {})))
  (is (= ["on a 19+"] (disp/condition-phrases {::mi/min-roll 19} {})))
  (is (= ["2 charges"] (disp/condition-phrases {::mi/charge-cost 2} {})))
  (is (= ["once per short rest"]
         (disp/condition-phrases {::mi/recharge :orcpub.dnd.e5.units/short-rest} {})))
  (is (= [] (disp/condition-phrases {} {}))))
