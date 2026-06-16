(ns orcpub.dnd.e5.ac-characterization-test
  "VISUALIZATION / characterization of how the app computes AC today (the 'before'
   snapshot). Builds representative characters and prints their unarmored AC with the
   formula breakdown, then asserts the numbers so it doubles as a regression baseline:
   re-run after any AC reconciliation change to get the 'after' and diff.

   Also settles a specific question: a single-class Monk's Unarmored Defense works
   (10 + Dex + Wis). The (first ?unarmored-defense) gate only matters when TWO unarmored
   defenses are present; for a lone Monk, Monk is the first (only) source.

   JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

(defn class-opt [opt-fn]
  (opt-fn sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map))

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map weapons5e/weapons
    sl5e/spell-lists spells5e/spell-map
    [] []                                  ; backgrounds, races
    [(class-opt classes5e/monk-option)
     (class-opt classes5e/barbarian-option)
     (class-opt classes5e/fighter-option)]
    [] language-map)))

;; str10 dex14(+2) con16(+3) int10 wis16(+3) cha10 — chosen so the ability adders are visible
(def abilities {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 14
                :orcpub.dnd.e5.character/con 16 :orcpub.dnd.e5.character/int 10
                :orcpub.dnd.e5.character/wis 16 :orcpub.dnd.e5.character/cha 10})

(defn level-1-of [class-key]
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
    :class [{:orcpub.entity/key class-key
             :orcpub.entity/options
             {:levels [{:orcpub.entity/key :level-1
                        :orcpub.entity/options
                        {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 4}}}]}}]}})

(defn ac-breakdown
  "Build a level-1 character of `class-key` and pull the AC-relevant channels the app
   actually uses — so you can see WHAT FEEDS the number, not just the number."
  [class-key]
  (let [built (entity/build (level-1-of class-key) test-template)
        ac-fn (char5e/armor-class-with-armor built)]    ; ?armor-class-with-armor is (fn [armor shield])
    {:base       (char5e/base-armor-class built)         ; 10 + Dex (+ natural tie-break + magical)
     :unarmored-ac-bonus (char5e/get-prop built :unarmored-ac-bonus)  ; class second ability (Con/Wis)
     :natural-ac-bonus   (char5e/get-prop built :natural-ac-bonus)
     :magical-ac-bonus   (char5e/get-prop built :magical-ac-bonus)
     :unarmored  (ac-fn nil nil)}))                       ; nil armor + nil shield = displayed unarmored AC

(deftest unarmored-ac-snapshot
  (testing "BEFORE snapshot — what the app computes for level-1 chars (Dex 14, Con/Wis 16) and the channels feeding it"
    (let [chars [:monk :barbarian :fighter]]
      (println "\n=== AC characterization (BEFORE) — level 1, Dex 14 / Con 16 / Wis 16 ===")
      (println (format "  %-10s %-6s %-12s %-10s %-10s %-9s"
                       "class" "base" "unarmored+" "natural+" "magical+" "AC(unarm)"))
      (doseq [k chars]
        (let [b (ac-breakdown k)]
          (println (format "  %-10s %-6s %-12s %-10s %-10s %-9s"
                           (name k) (str (:base b)) (str (:unarmored-ac-bonus b))
                           (str (:natural-ac-bonus b)) (str (:magical-ac-bonus b))
                           (str (:unarmored b))))))
      (println "  (base = 10+Dex; unarmored+ = class ability into unarmored defense; AC(unarm) = no armor)\n")
      ;; assertions = the regression baseline
      (is (= 15 (:unarmored (ac-breakdown :monk)))
          "single-class Monk Unarmored Defense WORKS: 10 + Dex(2) + Wis(3) = 15")
      (is (= 15 (:unarmored (ac-breakdown :barbarian)))
          "single-class Barbarian Unarmored Defense: 10 + Dex(2) + Con(3) = 15")
      (is (= 12 (:unarmored (ac-breakdown :fighter)))
          "Fighter has no unarmored defense: 10 + Dex(2) = 12"))))
