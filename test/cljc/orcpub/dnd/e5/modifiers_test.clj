(ns orcpub.dnd.e5.modifiers_test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.modifiers :as dnd5-mods]))

;; add-bonus is called throughout the modifier system to accumulate
;; bonuses. It treats nil as 0, which matters because entity attributes
;; start as nil before any modifiers apply. Getting this wrong means
;; every bonus computation breaks silently.

(deftest add-bonus-nil-handling
  (testing "add-bonus treats nil as 0"
    (is (= 5 (dnd5-mods/add-bonus nil 5)))
    (is (= 3 (dnd5-mods/add-bonus 3 nil)))
    (is (= 0 (dnd5-mods/add-bonus nil nil)))
    (is (= 7 (dnd5-mods/add-bonus 3 4)))))

;; enough-levels? gates whether a modifier applies based on character
;; level. It has three distinct paths: nil level (always applies),
;; total-levels check (multiclass), and class-specific level check.
;; Bugs here cause features to appear/disappear at wrong levels.

(deftest enough-levels?-logic
  (testing "nil level always passes (unconditional modifiers)"
    (is (true? (dnd5-mods/enough-levels? nil 1 {} nil))))
  (testing "checks total-levels when class-key is nil (multiclass features)"
    (is (true? (dnd5-mods/enough-levels? nil 5 {} 3)))
    (is (false? (dnd5-mods/enough-levels? nil 2 {} 3))))
  (testing "checks class-level map when class-key is provided"
    (let [class-level {:fighter 5 :wizard 3}]
      (is (true? (dnd5-mods/enough-levels? :fighter 8 class-level 5)))
      (is (false? (dnd5-mods/enough-levels? :fighter 8 class-level 6)))
      (is (true? (dnd5-mods/enough-levels? :wizard 8 class-level 3)))
      (is (false? (dnd5-mods/enough-levels? :wizard 8 class-level 4))))))

;; add-spell accumulates spells into a nested map keyed by
;; [spell-level [class spell-key]]. The merge behavior matters:
;; adding a second spell at the same level must not clobber the first.

(deftest add-spell-accumulation
  (testing "inserts into empty spells-known"
    (let [cfg {:class :wizard :key :magic-missile}
          result (dnd5-mods/add-spell {} 1 cfg)]
      (is (= cfg (get-in result [1 [:wizard :magic-missile]])))))
  (testing "merges into existing spell level without clobbering"
    (let [existing {1 {[:wizard :shield] {:class :wizard :key :shield}}}
          cfg {:class :wizard :key :fireball}
          result (dnd5-mods/add-spell existing 1 cfg)]
      (is (some? (get-in result [1 [:wizard :shield]]))
          "pre-existing spell should still be present")
      (is (some? (get-in result [1 [:wizard :fireball]]))
          "new spell should be added"))))
