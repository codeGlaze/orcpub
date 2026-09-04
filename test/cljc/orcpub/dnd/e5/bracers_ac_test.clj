(ns orcpub.dnd.e5.bracers-ac-test
  "Bracers of Defense must stack onto a natural-armor character's AC.

   The bonus used to be written into ?unarmored-ac-bonus, a channel that also carries Barbarian's
   and Monk's Unarmored Defense ability modifiers. Those compete as whole AC calculations and are
   zeroed by the tie-break in ?unarmored-armor-class when ?natural-ac-bonus wins — which silently
   took the flat bonus with them. A Lizardfolk or Draconic Sorcerer wearing Bracers of Defense came
   out at 15 where the rules give 17.

   Exercises the real engine: template-base with modifiers applied, same as the app."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.template-base :as tb]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.modifiers :as mods]
            [orcpub.entity-spec :as es]))

;; Dex 14 (+2). Con/Wis 16 (+3) so Unarmored Defense is distinguishable if it ever appears.
(def abilities (mods/modifier ?abilities {::char5e/dex 14 ::char5e/con 16 ::char5e/wis 16}))
(def natural-3 (mods/modifier ?natural-ac-bonus 3))          ; Draconic Sorcerer / lizardfolk shape
(def bracers   (mod5e/unarmored-ac-bonus 2))

;; A +1 shield, contributing 3. A plain shield contributes 2, which collides with the bracers' 2 and
;; would make "bonus applied" and "bonus skipped" produce the same number.
(def magic-shield {:type :shield :orcpub.dnd.e5.magic-items/magical-ac-bonus 1})
;; ?armor-class-with-armor-base adds (::mi5e/magical-ac-bonus armor) unguarded, so test armor must
;; carry the key explicitly — nil there is an arithmetic error, not a zero.
(def leather {:base-ac 11 :type :light :orcpub.dnd.e5.magic-items/magical-ac-bonus 0})

(defn ac [modifiers armor shield]
  ((es/entity-val (mods/apply-modifiers tb/template-base (into [abilities] modifiers))
                  :armor-class-with-armor)
   armor shield))

(deftest bracers-stack-onto-natural-armor
  (testing "the defect: a flat unarmored bonus must survive the natural-armor tie-break"
    (is (= 15 (ac [natural-3] nil nil))
        "natural armor alone: 10 + Dex(2) + 3")
    (is (= 17 (ac [natural-3 bracers] nil nil))
        "REGRESSION GUARD: natural armor 15 + the bracers' flat 2. Was 15 — the tie-break zeroed
         the channel the bonus was sitting in.")))

(deftest bracers-clauses-still-hold
  (testing "'+2 if you are wearing no armor and using no shield', measured as a delta so the
            bonus is attributable rather than merely consistent with the total"
    (let [delta (fn [armor shield] (- (ac [bracers] armor shield) (ac [] armor shield)))]
      (is (= 12 (ac [] nil nil))              "control, unarmored: 10 + Dex(2)")
      (is (= 2 (delta nil nil))               "no armor, no shield: the bonus applies")
      (is (= 0 (delta nil magic-shield))      "shield held: excluded")
      (is (= 0 (delta leather nil))           "armor worn: excluded"))))

(deftest bracers-is-a-bonus-not-a-calculation
  (testing "structural: it must land in ?ac-bonus-fns, which is summed onto the winning
            calculation, and never back in the ?unarmored-ac-bonus channel"
    (is (= :ac-bonus-fns (:orcpub.modifiers/key bracers)))))
