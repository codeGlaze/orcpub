(ns orcpub.dnd.e5.spell-slot-characterization-test
  "CHARACTERIZATION of the CURRENT spell-slot computation — the baseline the A3 slot-table rework
   must reproduce, and simultaneously the falsifiable check on the spell-slot-progression analysis
   (spell-slot-progression.md / D27). Per the verification-discipline standing rule: a behavioral
   claim becomes a test that builds the REAL character through the REAL code and asserts its actual
   `?spell-slots`, so a misread fails HERE and NOW rather than shipping as confident prose.

   Reuses the 12-class build harness from class-feature-snapshot-test. Verified chain:
   class `:spellcasting :level-factor` -> class-option emits `spell-slot-factor` (options.cljc:2996)
   -> `?spell-slot-factors` -> `?spell-slots` (template_base.cljc:285) -> `char5e/spell-slots`
   (character.cljc:522); warlock has no `:level-factor` -> pact path via `char5e/pact-magic?`.

   The asserted slot maps below are CAPTURED from observed build output (the dump-* diagnostic),
   not hand-computed — characterization freezes what the code does, not what I think it should."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.class-feature-snapshot-test :as net]))

;; high, even array so the caster multiclass prereqs (cha/int 13) are satisfied and can't silently
;; block a class from applying — keeps the test about slots, not prereq gating.
(def caster-abilities
  {:orcpub.dnd.e5.character/str 14 :orcpub.dnd.e5.character/dex 14
   :orcpub.dnd.e5.character/con 14 :orcpub.dnd.e5.character/int 14
   :orcpub.dnd.e5.character/wis 14 :orcpub.dnd.e5.character/cha 14})

(defn- class-entry [[k levels]]
  {:orcpub.entity/key k
   :orcpub.entity/options {:levels (net/level-entries levels)}})

(defn- char-of
  "classes = [[:sorcerer 5]] single, or [[:sorcerer 5] [:warlock 5]] multiclass."
  [classes]
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value caster-abilities}
    :class (mapv class-entry classes)}})

(defn slots-of [classes]
  (let [built (entity/build (char-of classes) net/test-template)]
    {:slots (char5e/spell-slots built)
     :pact? (boolean (char5e/pact-magic? built))}))

(deftest ^:diagnostic dump-spell-slots
  (println "\n=== SPELL SLOTS (observed) ===")
  (doseq [c [[[:sorcerer 5]] [[:paladin 5]] [[:warlock 5]]
             [[:sorcerer 5] [:wizard 5]] [[:sorcerer 5] [:warlock 5]]
             [[:wizard 1]] [[:warlock 1]]]]
    (println (pr-str (mapv first c)) "=>" (pr-str (slots-of c)))))

;; ---------------------------------------------------------------------------
;; BASELINE (captured from dump-spell-slots). A3 (the slot-table rework) must reproduce these for
;; the unchanged classes; an intended change (e.g. pact as a separate pool) makes a specific line
;; go red, showing exactly what moved.
;; ---------------------------------------------------------------------------

(deftest single-class-slot-baseline
  (testing "single FULL caster (sorcerer 5) — full-caster table"
    (is (= {:slots {1 4, 2 3, 3 2} :pact? false} (slots-of [[:sorcerer 5]]))))
  (testing "single HALF caster (paladin 5) — half-caster table (note: no level-1 slots; cf. artificer)"
    (is (= {:slots {1 4, 2 2} :pact? false} (slots-of [[:paladin 5]]))))
  (testing "warlock 5 — PACT slots, pact? flagged"
    (is (= {:slots {3 2} :pact? true} (slots-of [[:warlock 5]]))))
  (testing "level-1 start: full caster has a 1st slot; warlock has a pact slot at 1"
    (is (= {:slots {1 2} :pact? false} (slots-of [[:wizard 1]])))
    (is (= {:slots {1 1} :pact? true} (slots-of [[:warlock 1]])))))

(deftest multiclass-slot-baseline
  (testing "two NORMAL casters POOL to a combined caster level (sorcerer 5 + wizard 5 = level-10 full table)"
    (is (= {:slots {1 4, 2 3, 3 3, 4 3, 5 2} :pact? false}
           (slots-of [[:sorcerer 5] [:wizard 5]]))
        "the standard Multiclass Spellcaster rule: 5+5 -> caster level 10"))
  (testing "warlock does NOT pool — sorcerer 5 keeps its solo L5 table; pact slots merge-with-+ on top"
    (is (= {:slots {1 4, 2 3, 3 4} :pact? true}
           (slots-of [[:sorcerer 5] [:warlock 5]]))
        "3rd-level count = 2 (sorcerer) + 2 (warlock pact) = 4")
    ;; the load-bearing distinction, made falsifiable: the warlock multiclass is NOT the pooled result
    (is (not= (:slots (slots-of [[:sorcerer 5] [:wizard 5]]))
              (:slots (slots-of [[:sorcerer 5] [:warlock 5]])))
        "pact magic stays separate from the multiclass caster-level pool")
    ;; pins the merge-with-+ SIMPLIFICATION (pact summed into the regular count, not a separate pool):
    ;; if A3 makes pact a separate pool, this 3rd-level=4 conflation changes and the line above goes red.
    (is (= 4 (get-in (slots-of [[:sorcerer 5] [:warlock 5]]) [:slots 3]))
        "current model conflates pact + regular slots at the same spell level via merge-with +")))
