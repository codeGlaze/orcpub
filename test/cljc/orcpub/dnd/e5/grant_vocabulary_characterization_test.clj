(ns orcpub.dnd.e5.grant-vocabulary-characterization-test
  "Characterization of grant vocabulary A (`:props` -> plugin-modifiers/make-feat-modifiers,
   options.cljc, cljc). Pins what :props compiles to and that it shares the underlying mod5e/*
   primitive with vocabulary B — the 'shared effect, real duplication' half of D31.
   NOTE: vocabulary B (level-modifier + make-levels) lives in spell_subs.cljs (cljs), so its
   level-gated assembly is NOT reachable from this JVM gate — see the diagnostic + the doc note."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.modifiers :as mod5e]))

(deftest ^:diagnostic dump-vocab-a
  (println "\n=== vocab A: plugin-modifiers output (observe shape before asserting) ===")
  (let [m   (opt5e/plugin-modifiers {:damage-resistance {:fire true}} :t)
        dir [(mod5e/damage-resistance :fire)]]
    (println "plugin-modifiers :damage-resistance =>" (pr-str m))
    (println "direct mod5e/damage-resistance      =>" (pr-str dir))
    (println "equal? " (= m dir))
    (println "multi  =>" (pr-str (opt5e/plugin-modifiers
                                  {:damage-resistance {:fire true :cold true} :speed 10} :t)))))

;; A built modifier carries a :fn (an opaque fn object) so two semantically-equal modifiers are NOT
;; `=`. The stable, comparable projection is everything except the fn.
(defn- sans-fn [mods] (mapv #(dissoc % :orcpub.modifiers/fn) mods))

(deftest vocab-a-shares-the-primitive-with-b
  (testing ":props :damage-resistance compiles to the SAME modifier mod5e/damage-resistance produces"
    ;; This pins the 'shared effect / real duplication' half of D31: vocab A's :damage-resistance arm
    ;; IS (mod5e/damage-resistance v). Vocab B's arm (spell_subs.cljs:177) is literally the same call
    ;; `(mod5e/damage-resistance value)` — verified by source; not runnable here (B is cljs, see below).
    (is (= (sans-fn [(mod5e/damage-resistance :fire)])
           (sans-fn (opt5e/plugin-modifiers {:damage-resistance {:fire true}} :t))))))

(deftest vocab-a-value-shape-is-map-of-flags
  (testing "A's value convention: a map-of-flags fans out to one modifier per true key"
    (let [ms (sans-fn (opt5e/plugin-modifiers {:damage-resistance {:fire true :cold true}} :t))]
      (is (= 2 (count ms)))
      (is (= #{"fire" "cold"} (set (map :orcpub.modifiers/name ms))))
      (is (every? #(= :damage-resistances (:orcpub.modifiers/key %)) ms))))
  (testing "a scalar prop (:speed) compiles to one modifier"
    (is (= [{:orcpub.modifiers/name "speed" :orcpub.modifiers/value "+10"
             :orcpub.modifiers/key :speed :orcpub.modifiers/deps #{}
             :orcpub.modifiers/conditions [] :orcpub.modifiers/order nil}]
           (sans-fn (opt5e/plugin-modifiers {:speed 10} :t))))))

;; ---------------------------------------------------------------------------
;; SCOPE: vocabulary B (`level-modifier` + `make-levels`) lives in `spell_subs.cljs` (ClojureScript), so
;; its level-gated assembly is not reachable from THIS JVM gate — it is characterized in the cljs harness
;; instead: see `test/cljs/orcpub/dnd/e5/grant_vocabulary_cljs_test.cljs` (level-modifier shares the same
;; mod5e/* primitive proven here for A; make-levels places a :level-3 modifier at level 3). The
;; entity-level gating of a :levels map is separately pinned by class-feature-snapshot-test (Indomitable
;; @9). So both halves of D31 are test-backed across both layers.
;; ---------------------------------------------------------------------------
