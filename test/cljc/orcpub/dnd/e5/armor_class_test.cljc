(ns orcpub.dnd.e5.armor-class-test
  "Unit tests for the AC engine as plain functions — no entity, no character template. This is the
   point of the extraction: the arithmetic can be checked directly, and ac_reconciliation_test is
   left to prove the same rules hold once content and the entity spec are wired in."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.armor-class :as ac]))

(def magic :orcpub.dnd.e5.magic-items/magical-ac-bonus)

(def leather {:base-ac 11 :type :light  magic 0})
(def scale   {:base-ac 14 :type :medium :max-dex-mod 2 magic 0})  ; as shipped
(def plate   {:base-ac 18 :type :heavy  :max-dex-mod 0 magic 0})  ; as shipped
(def mithril {:base-ac 16 :type :heavy  :max-dex-mod 2 magic 0})  ; custom: heavy that allows Dex
(def plate+1 {:base-ac 18 :type :heavy  :max-dex-mod 0 magic 1})

(def caps     {:light nil :medium 2 :heavy 0})              ; the defaults
(def caps-mam (ac/raise-dex-cap caps :medium 3))            ; Medium Armor Master

(deftest dex-cap-takes-the-more-permissive-of-type-and-item
  (testing "light is uncapped unless the item says otherwise"
    (is (nil? (ac/dex-cap caps leather)))
    (is (= 1 (ac/dex-cap caps {:type :light :max-dex-mod 1})) "a light item may cap itself"))
  (testing "medium: the type cap is a raisable entry, so a feat can lift it"
    (is (= 2 (ac/dex-cap caps scale)))
    (is (= 3 (ac/dex-cap caps-mam scale))
        "Medium Armor Master raises it to 3 and the item's printed 2 does NOT veto that —
         reading :max-dex-mod alone would silently disable the feat"))
  (testing "heavy: 0 unless the item declares an allowance"
    (is (= 0 (ac/dex-cap caps plate)))
    (is (= 2 (ac/dex-cap caps mithril)) "custom heavy may allow more than its type")
    (is (= 0 (ac/dex-cap caps-mam plate)) "a MEDIUM feat does not leak into heavy"))
  (testing "an unknown armor type is treated as capped at 0, never uncapped"
    (is (= 0 (ac/dex-cap caps {:type :exotic})))))

(deftest raise-dex-cap-raises-only
  (testing "the generalisation: any type's cap can be lifted, not just medium's"
    (is (= 2 (ac/dex-cap (ac/raise-dex-cap caps :heavy 2) plate))
        "a heavy-armor feat is now expressible — it was not when the cap was a lone medium scalar")
    (is (= 3 (:medium (ac/raise-dex-cap caps :medium 3))))
    (is (= 2 (:medium (ac/raise-dex-cap caps :medium 1))) "a LOWER value is ignored")
    (is (nil? (:light (ac/raise-dex-cap caps :light 1)))
        "an already-uncapped type stays uncapped — raising cannot accidentally impose a limit")))

(deftest armor-dex-bonus-clamps-but-never-invents
  (is (= 3 (ac/armor-dex-bonus 3 caps leather)) "uncapped: all of it")
  (is (= 2 (ac/armor-dex-bonus 3 caps scale))   "clamped to the cap")
  (is (= 1 (ac/armor-dex-bonus 1 caps scale))   "a low Dex is not raised to the cap")
  (is (= 0 (ac/armor-dex-bonus 3 caps plate))))

(deftest worn-armor-ac-includes-item-magic-only
  (is (= 12 (ac/worn-armor-ac 2 caps nil))     "unarmored: 10 + Dex")
  (is (= 16 (ac/worn-armor-ac 3 caps scale))   "14 + min(2, 3)")
  (is (= 18 (ac/worn-armor-ac 3 caps plate))   "18 + 0")
  (is (= 19 (ac/worn-armor-ac 3 caps plate+1)) "the armor's OWN magic is part of its value")
  (is (= 18 (ac/worn-armor-ac 2 caps mithril)) "16 + min(2, 2)"))

(deftest shield-bonus-is-flat-two-plus-its-own-magic
  (is (= 2 (ac/shield-bonus {:type :shield})))
  (is (= 3 (ac/shield-bonus {:type :shield magic 1}))))

(deftest reconcile-takes-the-best-calculation-and-sums-every-bonus
  (let [unarmored-defense (fn [armor _] (if armor 0 15))   ; "your AC = 15 while unarmored"
        floor-16          (fn [_ _] 16)                    ; Barkskin: applies either way
        ring              (fn [_ _] 1)
        shield-fn         (fn [_ shield] (if shield 2 0))]
    (testing "calculations compete — the best wins, they never stack"
      (is (= 15 (ac/reconcile 12 [unarmored-defense] [] nil nil))
          "the calculation beats the unarmored 12")
      (is (= 16 (ac/reconcile 12 [unarmored-defense floor-16] [] nil nil))
          "16 beats 15 — not 31")
      (is (= 18 (ac/reconcile 18 [unarmored-defense] [] plate nil))
          "worn armor competes like any calculation, and here it wins"))
    (testing "a calculation that does not apply contributes 0, never a negative floor"
      (is (= 18 (ac/reconcile 18 [unarmored-defense] [] plate nil))))
    (testing "bonuses sum onto whichever calculation won"
      (is (= 18 (ac/reconcile 12 [unarmored-defense] [ring shield-fn] nil {:type :shield}))
          "15 + ring 1 + shield 2 — the bonuses land on the WINNER, not on the base")
      (is (= 13 (ac/reconcile 12 [] [ring] nil nil))))
    (testing "no contributors at all is just the armor value"
      (is (= 12 (ac/reconcile 12 [] [] nil nil))))))
