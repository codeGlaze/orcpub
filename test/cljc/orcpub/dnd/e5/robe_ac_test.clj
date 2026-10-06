(ns orcpub.dnd.e5.robe-ac-test
  "The Robe of the Archmagi's AC is a whole calculation (15 + Dex) that competes with the others,
   and Ring/Cloak of Protection magic is a bonus on whichever calculation wins. Runs template-base
   with modifiers applied, as the app does. Dex 14 (+2), Con 16 (+3)."
  (:require [clojure.test :refer [deftest is]]
            [orcpub.dnd.e5.template-base :as tb]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.modifiers :as mods]
            [orcpub.entity-spec :as es]))

(def abilities (mods/modifier ?abilities {::char5e/dex 14 ::char5e/con 16 ::char5e/wis 16}))

(def robe
  "The Robe's own modifiers, as the item list defines them."
  (::mi5e/modifiers (first (filter #(= "Robe of the Archmagi" (::mi5e/name %)) mi5e/magic-items))))

(def old-robe
  "The Robe as it was: a +5 bonus with no armor. Kept to show the tests catch it."
  [(mod5e/ac-bonus-fn (fn [armor _] (if (nil? armor) 5 0)))])

(def ring
  "What deferred-magic-item-fn emits for an equipped Ring of Protection (+1)."
  [(mods/vec-mod ?ac-bonus-fns (fn [_ _] 1))])

(def draconic [(mods/modifier ?natural-ac-bonus 3)])
(def barbarian
  "Barbarian Unarmored Defense: 10 + Dex + Con, with or without a shield."
  [(mods/modifier ?unarmored-ac-bonus 3) (mods/modifier ?unarmored-with-shield-ac-bonus 3)])
(def lizardfolk (opt5e/make-feat-modifiers :lizardfolk-ac true nil))
(def tortle (opt5e/make-feat-modifiers :tortle-ac true nil))

(def a-shield {:type :shield :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})
(def leather {:base-ac 11 :type :light :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})

(defn ac
  "Armor class for a character with `modifiers`, wearing `armor` and holding `shield` (either nil)."
  [modifiers armor shield]
  ((es/entity-val (mods/apply-modifiers tb/template-base (into [abilities] (apply concat modifiers)))
                  :armor-class-with-armor)
   armor shield))

(deftest the-robe-is-its-own-ac-calculation
  (is (= 17 (ac [robe] nil nil)) "alone: 15 + Dex 2")
  (is (= 17 (ac [robe draconic] nil nil)) "with Draconic Resilience (13 + Dex) the Robe wins; never 13 + Dex + 5")
  (is (= 17 (ac [robe barbarian] nil nil)) "with Unarmored Defense (10 + Dex + Con = 15) the better of the two, never both")
  (is (= 18 (ac [robe ring] nil nil)) "a Ring of Protection adds its +1 on top")
  (is (= 19 (ac [robe] nil a-shield)) "a shield adds its +2")
  (is (= 13 (ac [robe] leather nil)) "with armor on the Robe gives nothing: leather 11 + Dex 2"))

(deftest the-old-plus-five-is-caught
  (is (not= 17 (ac [old-robe draconic] nil nil)) "the +5 stacked on Draconic Resilience")
  (is (not= 17 (ac [old-robe barbarian] nil nil)) "the +5 stacked on Unarmored Defense"))

(deftest protection-magic-without-the-robe-is-unchanged
  (is (= 13 (ac [ring] nil nil)) "unarmored: 10 + Dex 2 + 1")
  (is (= 15 (ac [ring] nil a-shield)) "unarmored with a shield: 12 + 2 + 1")
  (is (= 14 (ac [ring] leather nil)) "armored: leather 13 + 1")
  (is (= 16 (ac [ring lizardfolk] nil nil)) "lizardfolk after the ring: natural armor 15 + 1")
  ;; GOTCHA: the lizardfolk wrapper keeps the AC calculation as it stood when it applied, so a ring
  ;; applied after it is dropped. That predates this change; see lizardfolk-ac-drops-later-bonuses.md.
  (is (= 15 (ac [lizardfolk ring] nil nil)) "lizardfolk before the ring: the ring is dropped, as before")
  (is (= 17 (ac [tortle ring] nil nil)) "tortle is a flat 17 and ignored the ring before as well"))
