(ns orcpub.dnd.e5.cr-characterization-test
  "Characterization for CR, pinned BEFORE the normalization in plan-cr-normalization.md.

   Step 0 of that plan. Everything here describes the app as it is on 2026-09-16, so the
   conversion of `1/4` to `0.25` shows up as a diff in expected values rather than as silence.
   A wrong CR changes a monster's XP with no error and no visible symptom; these are the
   assertions that make that loud.

   TWO KINDS OF ASSERTION, and the difference matters when step 2 lands:

   INVARIANT — must NOT change. Total XP, the per-CR monster counts, the sort order, and the
   rendered Wild Shape sentence. If any of these move, the conversion is wrong.

   CONVERTED AT STEP 2 — `cr-is-one-numeric-type`. It was written to assert the JVM `Ratio` and
   the nil lookup by double; step 2 inverted it, and that inversion IS the record of the change.
   The counts and totals above did not move, which is what made the conversion safe.

   See docs/kb/decision-cr-representation.md for why, and plan-cr-normalization.md for the order."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.entity-spec :as es]
            [orcpub.common :as common]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]))

(defn- xp-of [monster] (monsters5e/challenge-ratings (:challenge monster)))
(defn- total-xp [ms] (reduce + (map #(or (xp-of %) 0) ms)))

;; ---------------------------------------------------------------- the monster data

(deftest monster-cr-counts-are-invariant
  ;; Compared as PLAIN maps, deliberately. An earlier version compared against a `sorted-map`
  ;; and silently lost its teeth: a plain map on the left looks keys up in a sorted map with
  ;; `compare`, and `(compare 1/8 0.125)` is 0, so the ratio-to-decimal conversion slipped
  ;; straight through. `=` on maps is not symmetric when one side is sorted —
  ;; `(= expected actual)` was true while `(= actual expected)` was false, and the idiomatic
  ;; clojure.test argument order is the insensitive one.
  (testing "how many monsters sit at each CR — sensitive to any single row changing"
    (is (= {0 29, 0.125 17, 0.25 32, 0.5 28, 1 27, 2 41, 3 20, 4 11, 5 25, 6 10, 7 6, 8 10,
            9 8, 10 6, 11 7, 12 2, 13 6, 14 3, 15 4, 16 5, 17 4, 19 1, 20 3, 21 4, 22 2,
            23 3, 24 2, 30 1}
           (frequencies (map :challenge monsters5e/monsters-raw)))))
  (testing "317 monsters, 28 distinct CRs"
    (is (= 317 (count monsters5e/monsters-raw)))
    (is (= 28 (count (distinct (map :challenge monsters5e/monsters-raw)))))))

(deftest every-monster-resolves-to-an-xp-value
  (testing "no monster falls through the CR -> XP table"
    (is (= 0 (count (remove xp-of monsters5e/monsters-raw)))))
  (testing "total XP across the whole list — one number that moves if any CR is mistyped"
    (is (= 1355565 (total-xp monsters5e/monsters-raw)))))

(deftest sort-by-challenge-order-is-invariant
  (testing "the order the monster list subscription produces (spell_subs.cljs:1412)"
    (let [sorted (sort-by :challenge monsters5e/monsters-raw)]
      (is (= ["Lemure" "Shrieker" "Homunculus" "Awakened Shrub" "Baboon"]
             (mapv :name (take 5 sorted))))
      (is (= ["Ancient Red Dragon" "Ancient Gold Dragon" "Tarrasque"]
             (mapv :name (take-last 3 sorted)))))))

;; ---------------------------------------------------------------- the runtime split

(deftest cr-is-one-numeric-type
  ;; This assertion was inverted at step 2, and that inversion is the point. Before the
  ;; conversion it read: type is Ratio, lookup by 1/8 gives 25, lookup by 0.125 gives nil.
  (testing "fractional CRs are doubles in both runtimes now — the `.cljc` split is gone"
    (is (= #?(:clj java.lang.Double :cljs js/Number)
           (type (first (filter #(and (number? %) (< 0 % 1))
                                (map :challenge monsters5e/monsters-raw))))))
    (is (= 25 (monsters5e/challenge-ratings 0.125))
        "a homebrew CR — always a double — now finds the table on the JVM too")
    ;; JVM-only, and not for tidiness: `1/8` is not a valid ClojureScript constant at all
    ;; ("clojure.lang.Ratio is not a valid ClojureScript constant" — the compiler's words).
    ;; That is the premise of this whole change, demonstrated by the reader.
    #?(:clj
       (is (nil? (monsters5e/challenge-ratings 1/8))
           "and the ratio no longer resolves, which is correct: nothing writes one any more"))))

;; ---------------------------------------------------------------- the character side

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private abilities
  {::char5e/str 10 ::char5e/dex 10 ::char5e/con 10
   ::char5e/int 10 ::char5e/wis 15 ::char5e/cha 10})

(def ^:private the-template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [] []
     [(classes5e/druid-option sl5e/spell-lists spells5e/spell-map {} language-map
                              weapons5e/weapons-map)]
     [] language-map))))

(defn- lvl [n]
  {::entity/key (keyword (str "level-" n))
   ::entity/options {:hit-points {::entity/key :average ::entity/value 5}}})

(defn- wild-shape-summary
  "The Wild Shape action's printed sentence for a druid of `n` levels."
  [n]
  (->> (char5e/actions
        (entity/build
         {::entity/options
          {:ability-scores {::entity/key :standard-roll ::entity/value abilities}
           :class [{::entity/key :druid
                    ::entity/options {:levels (mapv lvl (range 1 (inc n)))}}]}}
         @the-template))
       (filter #(= "Wild Shape" (:name %)))
       first
       :summary))

(deftest wild-shape-sentence-is-invariant
  (testing "?wild-shape-cr is a display string today and becomes a number at step 4.
            These sentences must read identically either way — that is what cr->label is for."
    (is (= "You can transform into a beast you have seen with CR 1/4 and no flying or swimming speed"
           (wild-shape-summary 2)))
    (is (= "You can transform into a beast you have seen with CR 1/2 and no flying speed"
           (wild-shape-summary 4)))
    (is (= "You can transform into a beast you have seen with CR 1"
           (wild-shape-summary 8)))))

;; ---------------------------------------------------------------- the pin must be able to fail

(deftest the-characterization-is-sensitive
  (testing "a characterization that cannot go red pins nothing. Perturb one monster's CR and the
            invariants above must move."
    ;; Perturb a monster that sits in the pinned window. Bumping a high-CR monster moves the
    ;; totals but not the first five, which is itself worth knowing: the sort pin only guards
    ;; the ends of the list.
    (let [i (->> monsters5e/monsters-raw
                 (map-indexed vector)
                 (filter #(= "Lemure" (:name (second %))))
                 ffirst)
          perturbed (assoc-in (vec monsters5e/monsters-raw) [i :challenge] 30)]
      (is (not= (total-xp monsters5e/monsters-raw) (total-xp perturbed))
          "total XP must react to a single altered CR")
      (is (not= (frequencies (map :challenge monsters5e/monsters-raw))
                (frequencies (map :challenge perturbed)))
          "the per-CR counts must react to a single altered CR")
      (is (not= (mapv :name (take 5 (sort-by :challenge monsters5e/monsters-raw)))
                (mapv :name (take 5 (sort-by :challenge perturbed))))
          "the sort order must react to a single altered CR"))))
