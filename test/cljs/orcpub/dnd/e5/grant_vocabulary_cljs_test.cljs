(ns orcpub.dnd.e5.grant-vocabulary-cljs-test
  "CLJS-side characterization of grant vocabulary B (`level-modifier` + `make-levels`,
   spell_subs.cljs — cljs-only, not reachable from the JVM gate). Pairs with the JVM
   grant-vocabulary-characterization-test (vocab A). Together they pin D31:
   (1) shared effect primitive across A and B — both compile :damage-resistance to the SAME
       mod5e/* modifier; (2) B is LEVEL-GATED — make-levels places each :level-modifier under
       its :level (the real distinction from A's flat/unconditional :props)."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.dnd.e5.spell-subs :as ss]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spells :as spells5e]))

;; a built modifier carries an opaque :fn, so compare the projection without it
(defn- sans-fn [mods] (mapv #(dissoc % :orcpub.modifiers/fn) mods))

(def ^:private synthetic-class
  {:key :tinkerer :class :tinkerer
   :level-modifiers [{:type :damage-resistance :value :fire :level 3}
                     {:type :weapon-prof :value :longsword :level 1}]})

(deftest ^:diagnostic dump-vocab-b
  (let [levels (ss/make-levels sl5e/spell-lists spells5e/spell-map {} synthetic-class)]
    (println "\n=== vocab B: level-modifier + make-levels (observe) ===")
    (println "level-modifier :damage-resistance =>"
             (pr-str (sans-fn [(ss/level-modifier :tinkerer {:type :damage-resistance :value :fire})])))
    (println "make-levels =>" (pr-str (into (sorted-map)
                                            (map (fn [[k v]] [k (sans-fn (:modifiers v))]) levels))))))

(deftest vocab-b-shares-the-primitive-with-a
  (testing "level-modifier :damage-resistance compiles to the SAME modifier as mod5e/damage-resistance"
    (is (= (sans-fn [(mod5e/damage-resistance :fire)])
           (sans-fn [(ss/level-modifier :tinkerer {:type :damage-resistance :value :fire})])))))

(deftest vocab-b-is-level-gated
  (testing "make-levels places each :level-modifier under its :level (level-gating = the distinction from flat :props)"
    (let [levels (ss/make-levels sl5e/spell-lists spells5e/spell-map {} synthetic-class)]
      (is (= (sans-fn [(mod5e/damage-resistance :fire)])
             (sans-fn (get-in levels [3 :modifiers])))
          "fire resistance lands at level 3")
      (is (empty? (get-in levels [2 :modifiers])) "nothing at level 2")
      (is (= (sans-fn [(mod5e/weapon-proficiency :longsword)])
             (sans-fn (get-in levels [1 :modifiers])))
          "weapon prof lands at level 1"))))
