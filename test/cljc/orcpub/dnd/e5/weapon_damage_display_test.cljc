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

(defn lines
  "The row lines of the weapon with this key, with these toggles on."
  ([k] (lines k #{}))
  ([k on] (disp/weapon-row-lines (weapon k) on)))

(deftest one-every-hit-extra-sits-on-the-tile
  (is (= {:tile {:count 1 :text "+1d6" :types [:cold]} :lines []} (lines :frost-brand-longsword))))

(deftest a-toggle-adds-its-extra-only-while-on
  (is (= {:tile nil :lines []} (lines :flame-tongue-longsword)))
  (is (= {:count 1 :text "+2d6" :types [:fire]} (:tile (lines :flame-tongue-longsword #{:ablaze})))))

(deftest conditional-extras-get-a-line-of-their-own
  (is (= [{:label "vs fiends and undead"
           :chips [{:text "+2d10 radiant" :type :radiant :style :tint :ringed? false}]}]
         (:lines (lines :holy-avenger-longsword))))
  (is (= [{:label "vs giants"
           :chips [{:text "+2d6 slashing" :type :slashing :style :tint :ringed? false}
                   {:text "DC 15 Str or prone" :style :outline}]}]
         (:lines (lines :giant-slayer-greataxe))))
  (is (= ["on a ranged hit, not vs giants" "on a ranged hit, vs giants"]
         (map :label (:lines (lines :dwarven-thrower))))))

(deftest crit-and-once-per-dawn-extras-stay-off-the-row
  (is (= {:tile nil :lines []} (lines :vicious-weapon-mace)))
  (is (= {:tile nil :lines []} (lines :javelin-of-lightning)))
  (is (= {:tile nil :lines []} (lines :dagger-of-venom))))

(deftest two-every-hit-extras-get-a-line-and-a-count
  (let [w (assoc (weapon :longsword)
                 ::mi/toggles [{::mi/toggle-id :lit ::mi/toggle-name "Lit"}]
                 ::mi/extra-damage [{:orcpub.dnd.e5.weapons/damage-die-count 1
                                     :orcpub.dnd.e5.weapons/damage-die 6
                                     :orcpub.dnd.e5.weapons/damage-type :thunder}
                                    {:orcpub.dnd.e5.weapons/damage-die-count 2
                                     :orcpub.dnd.e5.weapons/damage-die 6
                                     :orcpub.dnd.e5.weapons/damage-type :fire
                                     ::mi/while :lit}])]
    (is (= {:count 2 :text "+2 more" :types [:thunder :fire]} (:tile (disp/weapon-row-lines w #{:lit}))))
    (is (= [{:label "Every hit"
             :chips [{:text "+1d6 thunder" :type :thunder :style :fill :ringed? false}
                     {:text "+2d6 fire" :type :fire :style :fill :ringed? true}]}]
           (:lines (disp/weapon-row-lines w #{:lit}))))))

(defn menu
  "The + menu of the weapon with this key: [label sub value] per item, or nil."
  ([k] (menu k 3 #{}))
  ([k modifier on]
   (some->> (disp/weapon-menu-items (weapon k) modifier false on)
            (mapv (juxt :label :sub :value)))))

(deftest a-plain-weapon-has-no-menu
  (is (nil? (menu :longsword)))
  (is (nil? (menu :frost-brand-longsword)) "an every-hit extra is not conditional"))

(deftest a-target-gets-roll-vs-and-critical-vs
  (is (= [["Roll damage" nil "1d8+3"]
          ["Roll vs undead" "base + bonus" "1d8+3 +1d8"]
          ["Critical damage" "all dice doubled" "2d8+3"]
          ["Critical vs undead" "all dice doubled" "2d8+3 +2d8"]]
         (menu :sun-blade-longsword))))

(deftest a-rolled-20-extra-shows-only-in-critical-damage
  (is (= [["Roll damage" nil "1d6+3"]
          ["Critical damage" "all dice doubled" "2d6+3 +4d6"]]
         (menu :vicious-weapon-mace))))

(deftest once-per-dawn-uses-are-their-own-items
  (is (= [["+4d6 lightning" "once per dawn" "+4d6 lightning"]
          ["DC 13 Dex save" "once per dawn" "4d6 lightning"]]
         (take-last 2 (menu :javelin-of-lightning))))
  (is (= ["DC 15 Con or poisoned" "once per dawn" "2d10 poison"]
         (last (menu :dagger-of-venom))))
  (is (= :orcpub.dnd.e5.units/dawn
         (:recharge (last (disp/weapon-menu-items (weapon :dagger-of-venom) 3 false #{}))))))

(deftest a-switched-on-extra-joins-every-roll
  (let [w (assoc (weapon :holy-avenger-longsword)
                 ::mi/toggles [{::mi/toggle-id :lit ::mi/toggle-name "Lit"}]
                 ::mi/extra-damage (conj (::mi/extra-damage (weapon :holy-avenger-longsword))
                                         {:orcpub.dnd.e5.weapons/damage-die-count 1
                                          :orcpub.dnd.e5.weapons/damage-die 6
                                          :orcpub.dnd.e5.weapons/damage-type :fire
                                          ::mi/while :lit}))
        values #(mapv :value (take 2 (disp/weapon-menu-items w 3 false %)))]
    (is (= ["1d8+3" "1d8+3 +2d10"] (values #{})))
    (is (= ["1d8+3 +1d6" "1d8+3 +2d10 +1d6"] (values #{:lit})))))

(def made-up-masteries
  "Mastery definitions shaped like the 5.5 line's, with placeholder text."
  {:topple {:name "Topple" :trigger :hit :text "Topple rule."}
   :graze {:name "Graze" :trigger :miss :text "Graze rule."}})

(deftest mastery-band-needs-a-mastery-a-definition-and-training
  (let [sword (assoc (weapon :longsword) :orcpub.dnd.e5.weapons/mastery :topple)
        ctx {:ability-mod 3 :prof 2 :damage-type :slashing}]
    (is (nil? (disp/mastery-band (weapon :longsword) #{:longsword} made-up-masteries ctx)) "no mastery, no band")
    (is (nil? (disp/mastery-band sword #{:longsword} {} ctx)) "no definitions (2014 data), no band")
    (is (= {:key :topple :name "Topple" :trigger :hit :text "Topple rule."
            :summary "DC 13 Con or prone" :trained? true}
           (disp/mastery-band sword #{:longsword} made-up-masteries ctx)))
    (is (false? (:trained? (disp/mastery-band sword #{:greatsword} made-up-masteries ctx)))
        "trained in another weapon kind is not trained in this one")
    (is (true? (:trained? (disp/mastery-band (assoc (weapon :flame-tongue-longsword)
                                                    :orcpub.dnd.e5.weapons/mastery :topple)
                                             #{:longsword} made-up-masteries ctx)))
        "a magic longsword counts as a longsword")
    (is (= ["Graze" :miss "3 slashing"]
           ((juxt :name :trigger :summary)
            (disp/mastery-band (assoc (weapon :greatsword) :orcpub.dnd.e5.weapons/mastery :graze)
                               #{:greatsword} made-up-masteries ctx))))))

(deftest charged-powers-join-the-menu-with-their-cost
  (let [staff (mi/all-weapons-map :staff-of-striking)
        items (filter :cost (disp/weapon-menu-items staff 3 false #{}))]
    (is (some? staff) "the staff is in the weapons map")
    (is (= [["+1d6 force" 1] ["+2d6 force" 2] ["+3d6 force" 3]] (map (juxt :label :cost) items)))
    (is (= "2 charges" (:sub (second items))))))
