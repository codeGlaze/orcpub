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
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.armor :as armor5e]
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

;; ===========================================================================
;; DEEPER AC characterization (traced up+down):
;; - the displayed AC = `?armor-class-with-armor` applied to worn/best armor (subs.cljs:760-786);
;;   the LOGIC is the cljc fn, so invoking it directly characterizes the model.
;; - the model (template_base.cljc:79): (apply max base + each ?ac-fn) then + each ?ac-bonus-fn.
;; JVM-ism (verification-discipline lesson 5): the armored branch does (+ … (::mi5e/magical-ac-bonus
;; armor) …); non-magical armor has no such key -> nil. cljs treats nil as 0 in (+ ), JVM NPEs, so
;; armor maps here carry an explicit magical-ac-bonus 0 (the numeric result is identical to cljs).
;; ===========================================================================

(def ^:private mag0 #:orcpub.dnd.e5.magic-items{:magical-ac-bonus 0})
(defn- armor-by [k] (merge (first (filter #(= k (:key %)) armor5e/armor)) mag0))

(defn- ac-fn-of [class-key]
  (char5e/armor-class-with-armor (entity/build (level-1-of class-key) test-template)))

(deftest armored-ac-characterization
  (testing "Fighter (Dex 14) — the armored branch: base-ac + dex capped BY ARMOR TYPE (not the armor's :max-dex-mod field)"
    (let [ac (ac-fn-of :fighter)]
      (is (= 13 (ac (armor-by :leather) nil))    "light: 11 base + full Dex(2)")
      (is (= 16 (ac (armor-by :scale-mail) nil)) "medium: 14 base + min(2,Dex)")
      (is (= 16 (ac (armor-by :chain-mail) nil)) "heavy: 16 base + 0 Dex")
      (is (= 18 (ac (armor-by :chain-mail) {:type :shield})) "heavy + shield(+2)")
      (is (= 14 (ac nil {:type :shield}))        "no armor + shield: 10 + Dex(2) + 2"))))

(defn- monk-barb-multiclass []
  {:orcpub.entity/options
   {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
    :class [{:orcpub.entity/key :monk
             :orcpub.entity/options {:levels [{:orcpub.entity/key :level-1
                                               :orcpub.entity/options {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 4}}}]}}
            {:orcpub.entity/key :barbarian
             :orcpub.entity/options {:levels [{:orcpub.entity/key :level-1
                                               :orcpub.entity/options {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 4}}}]}}]}})

(deftest two-unarmored-defenses-tie-break
  (testing "monk + barbarian both grant Unarmored Defense; the (first ?unarmored-defense) gate picks ONE ability adder"
    ;; Con 16 (+3) and Wis 16 (+3) are equal here, so the AC is the same number either way; the point
    ;; is the gate resolves to a single source rather than stacking BOTH (+3+3). 10 + Dex(2) + one(+3) = 15.
    (let [built (entity/build (monk-barb-multiclass) test-template)
          ac    ((char5e/armor-class-with-armor built) nil nil)]
      (is (= 15 ac) "ONE unarmored bonus applies (15), not both stacked (would be 18)"))))

;; Natural-AC vocabulary A (:props). This pinned the tortle/lizardfolk DUPLICATION (D31): two
;; bespoke arms, each emitting a ?natural-ac-bonus AND its own ?armor-class-with-armor override.
;; RESOLVED 2026-09 — neither one overrides ?armor-class-with-armor any more.
;;   :lizardfolk-ac compiles to the universal :ac shape, {:ac 13 :abilities [:dex]}.
;;   :tortle-ac was two things welded together and is now both of them: {:ac 17 :abilities []}
;;   plus (armor-gives-no-ac). Its old (+ 17 shield) replacement was a CEILING standing in for
;;   "a tortle can't wear armor" — the app had no way to say that, so it faked it by making
;;   armor unable to win. Modelled honestly the restriction is not a cap on AC at all; worn armor
;;   simply contributes nothing, which composes with ?ac-fns' max instead of fighting it.
;; Both verified behaviour-identical: the parity sweep (0 divergences) covers lizardfolk, and
;; tortle-decomposes-into-a-calculation-and-a-restriction checks composed == welded in all 7
;; equipment states.
(defn- props-mod-keys [props]
  (->> (opt5e/plugin-modifiers props :t) (map :orcpub.modifiers/key) set))

(deftest natural-ac-props-are-duplicated-bespoke
  (testing "both bespoke props are on the universal mechanism now — no AC-function overrides left"
    (let [liz (props-mod-keys {:lizardfolk-ac true})
          tor (props-mod-keys {:tortle-ac true})]
      (is (contains? liz :ac-fns)
          "a competing calculation registered on ?ac-fns, not an override of the AC function")
      (is (= #{:ac-fns} liz)
          "FLIPPED 2026-09: the ?natural-ac-bonus write is gone too. It only ever fed the
           no-stacking tie-break in ?base-armor-class, and that tie-break is gone — ?ac-fns takes
           the best calculation by max, so there is nothing left to arbitrate.")
      (is (= #{:ac-fns :armor-ac-suppressed?} tor)
          ":tortle-ac is exactly its two halves: the flat calculation, and the restriction")
      (is (empty? (filter #{:armor-class-with-armor} (concat liz tor)))
          "FLIPPED: neither hand-written override survives")
      ;; Draconic Bloodline used to share the ?natural-ac-bonus channel with these props. It now
      ;; registers its own calculation instead, so the shared channel — and its constructor — are
      ;; gone. ?ac-fns is the one place every "your AC = ..." feature lands.
      (is (= :ac-fns (:orcpub.modifiers/key (mod5e/ac-formula (fn [_ _] 0))))
          "the shared primitive is now ?ac-fns"))))
