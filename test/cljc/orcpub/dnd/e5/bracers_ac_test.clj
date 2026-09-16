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
            [orcpub.dnd.e5.options :as opt5e]
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

;; ── The requirements registry, through a built character ───────────────────────────
;; The registry is unit-tested at the predicate level elsewhere. These prove the whole path:
;; authored :props -> make-feat-modifiers -> mod5e/ac-bonus -> ?ac-bonus-fns -> the AC on the sheet.
;; Without this, "authorable" only meant the data compiled, not that a number moved.

(def ^:private sword {:name "Shortsword"})

(defn- wielding [main off]
  (into [] (remove nil?)
        [(mods/modifier ?orcpub.dnd.e5.character/main-hand-weapon main)
         (mods/modifier ?orcpub.dnd.e5.character/off-hand-weapon  off)]))

(deftest authored-dual-wielding-bonus-moves-the-ac-on-a-built-character
  (testing "{:ac-bonus {:bonus 1 :dual-wielding? true}} — unauthorable before the registry, because
            a contributor is called (f armor shield) and could never see the weapons"
    (let [bonus (opt5e/ac-bonus-modifiers {:bonus 1 :dual-wielding? true})]
      (is (= 13 (ac (concat (wielding sword sword) bonus) nil nil))
          "both hands full: 10 + Dex(2) + 1")
      (is (= 12 (ac (concat (wielding sword nil) bonus) nil nil))
          "one weapon: the requirement fails, no bonus")
      (is (= 12 (ac (concat (wielding nil nil) bonus) nil nil))
          "empty handed: no bonus"))))

(deftest the-deprecated-prop-key-still-moves-the-same-ac
  (testing "DEPRECATION SHIM: :two-weapon-ac-1 compiled to the hand-written dual-wield-ac-mod and
            now compiles through the registry. Same key, same number on the sheet (D9)."
    (let [via-prop (#'opt5e/make-feat-modifiers :two-weapon-ac-1 true nil)]
      (is (= 13 (ac (concat (wielding sword sword) via-prop) nil nil)))
      (is (= 12 (ac (concat (wielding sword nil) via-prop) nil nil))))))

(deftest robe-of-the-archmagi-still-grants-five-while-unarmored
  (testing "converted from a hand-written (if (nil? armor) 5 0) to {:armor? false}"
    (let [robe (opt5e/ac-bonus-modifiers {:bonus 5 :armor? false})]
      (is (= 17 (ac robe nil nil))          "unarmored: 10 + Dex(2) + 5")
      (is (= 13 (ac robe leather nil))      "armored: 11 + Dex(2), robe excluded"))))
