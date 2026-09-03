(ns orcpub.dnd.e5.ac-experiments-test
  "SANDBOX (D23 prototype-then-converge). Candidate implementations of the OUTER AC reconciler
   — 'best AC across owned armor/shield combos' — proved against ONE shared spec and compared on
   EFFICIENCY (method-eval count) and HARDENING (adversarial / mis-declared input). The winner is
   promoted into orcpub.dnd.e5.armor-class; these parallels are test-only and temporary.

   MODEL
     method = {:item? bool  :fn (fn [armor shield] n)}
       :item? true  -> the fn reads the equipped armor's FIELDS (:base-ac/:type); must be
                       evaluated per owned armor (the worn-armor formula — usually the ONLY one).
       :item? false -> reads armor only as (nil? armor); its value depends on (armored?, shield),
                       a handful of states, NOT the armor list (unarmored defense, natural armor,
                       floors, homebrew '10 + Dex + N' methods).
     bonus  = (fn [armor shield] n)  -> summed onto the winning method (shield, ring, Defense style…).

   The inner reconcile (best method + sum of bonuses for ONE combo) is orcpub.dnd.e5.armor-class."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.armor-class :as ac]))

(def dex 2) ; Dex 14 (+2), matching the reconciliation spec

;; ---- reusable pieces --------------------------------------------------------
(defn armor-dex
  "Dex contribution from worn armor. Honors the armor's OWN :max-dex cap when present (custom
   material: 'heavy armor that still allows a Dex bonus'), else the type default. This is the
   single seam where custom-armor properties enter AC — reading the item, not hardcoding :type."
  [armor]
  (if-let [md (:max-dex armor)]
    (min md dex)
    (case (:type armor) :light dex :medium (min 2 dex) :heavy 0)))

(def ability-mods {:dex dex :wis 3 :con 3})        ; test ability modifiers (Dex 14, Wis/Con 16)

(def base-method                                   ; the SRD base: worn armor, else 10+Dex (item-dependent)
  {:item? true
   :fn (fn [armor _]
         (if armor
           (+ (:base-ac armor)
              (armor-dex armor)
              (get armor ::magic 0)
              ;; ENUMERATED custom-armor property: armor that grants EXTRA ability mods to AC
              ;; ("armor of the air elemental": :add-abilities [:wis]). One field the worn-armor
              ;; method reads — expressible, but not automatic (the method must know the property).
              (reduce + 0 (keep ability-mods (:add-abilities armor))))
           (+ 10 dex)))})

(defn unarmored-method [n]                          ; "AC = 10 + Dex + n" while unarmored (item-independent)
  {:item? false :fn (fn [armor _] (if armor 0 (+ 10 dex n)))})

(def floor-16 {:item? false :fn (fn [_ _] 16)})     ; Barkskin-style floor: constant method
(def ring-1   (fn [_ _] 1))                         ; universal +1 (Ring of Protection)
(defn shield-bonus [] (fn [_ shield] (if shield 2 0)))

(def leather {:base-ac 11 :type :light})
(def scale   {:base-ac 14 :type :medium})
(def chain   {:base-ac 16 :type :heavy})
;; ---- custom homebrew armor (the friend's weird-materials supplement) --------
(def mithril-plate {:base-ac 16 :type :heavy :max-dex 2})       ; heavy AC, but ALLOWS a Dex bonus
(def magic-leather {:base-ac 11 :type :light ::magic 1})        ; non-standard: +1 magical armor
(def air-elemental {:base-ac 15 :type :medium :add-abilities [:wis]})  ; adds Wis mod to AC
(def magic-studded {:base-ac 12 :type :light ::magic 3})       ; +3 magic — bound to THIS armor only

;; ---- candidate A: naive Cartesian ------------------------------------------
;; Evaluate every method for every (armor, shield) combo. Simple; can't be fooled by a wrong
;; :item? flag (ignores it). Cost ~ (A+1)(S+1) x (M+B).
(defn best-ac-naive [{:keys [methods bonuses]} armors shields]
  (apply max
   (for [armor  (cons nil armors)
         shield (cons nil shields)]
     (ac/reconcile-ac {:methods (map :fn methods) :bonuses bonuses} armor shield))))

;; ---- candidate B: bucketed --------------------------------------------------
;; Item-independent methods depend only on (armored?, shield) — precompute their max ONCE over
;; those 2 x (S+1) states; evaluate item-dependent methods per owned armor. Cost ~ O(M) + O(A).
;; Trades a dependence on a correct :item? flag for the saving.
(defn best-ac-bucketed [{:keys [methods bonuses]} armors shields]
  (let [indep         (remove :item? methods)
        deps          (filter :item? methods)
        shield-states (cons nil shields)
        indep-max     (into {}
                            (for [armored? [false true]
                                  shield   shield-states]
                              [[armored? shield]
                               (transduce (map #((:fn %) (when armored? ::some-armor) shield)) max 0 indep)]))]
    (apply max
     (for [armor  (cons nil armors)
           shield shield-states]
       (+ (max (indep-max [(some? armor) shield])
               (transduce (map #((:fn %) armor shield)) max 0 deps))
          (transduce (map #(% armor shield)) + 0 bonuses))))))

(def candidates {:naive best-ac-naive :bucketed best-ac-bucketed})

;; ---- the shared correctness spec (both candidates must reproduce) -----------
(def spec
  [{:name "base only, offer leather"        :methods [base-method]                        :bonuses [] :armors [leather] :shields [] :expected 13}
   {:name "unarmored defense, no armor"     :methods [base-method (unarmored-method 3)]    :bonuses [] :armors []        :shields [] :expected 15}
   {:name "unarmored defense loses to armor":methods [base-method (unarmored-method 3)]    :bonuses [] :armors [scale]   :shields [] :expected 16}
   {:name "floor lifts a low AC"            :methods [base-method floor-16]                :bonuses [] :armors [leather] :shields [] :expected 16}
   {:name "floor doesn't cap a high AC"     :methods [base-method (unarmored-method 8) floor-16] :bonuses [] :armors [] :shields [] :expected 20}
   {:name "homebrew method wins + ring"     :methods [base-method (unarmored-method 8)]    :bonuses [ring-1] :armors [chain] :shields [] :expected 21}
   {:name "shield adds to the winner"       :methods [base-method (unarmored-method 3)]    :bonuses [(shield-bonus)] :armors [] :shields [{:shield true}] :expected 17}
   ;; custom-armor axis — the worn-armor method reading the item's own fields:
   {:name "standard heavy: no Dex"          :methods [base-method]                         :bonuses [] :armors [chain]         :shields [] :expected 16}
   {:name "custom heavy ALLOWS Dex"         :methods [base-method]                         :bonuses [] :armors [mithril-plate] :shields [] :expected 18}
   {:name "custom magical armor"            :methods [base-method]                         :bonuses [] :armors [magic-leather] :shields [] :expected 14}
   {:name "air-elemental: armor adds Wis"   :methods [base-method]                         :bonuses [] :armors [air-elemental] :shields [] :expected 20}
   ;; armor-bound magic must NOT leak: a homebrew unarmored AC of 20, plus a +3 magic studded.
   ;; Unarmored the +3 is absent (20); wearing the studded it's 12+2+3=17. Best = 20. If the
   ;; armor magic leaked to the unarmored value it would be 23. Expected 20 proves no leak.
   {:name "armor magic stays with its armor":methods [base-method (unarmored-method 8)]     :bonuses [] :armors [magic-studded] :shields [] :expected 20}
   ;; owning leather +1/+2/+3: the +3 (11 + Dex 2 + 3 = 16) must surface as the best pick.
   {:name "best magic armor surfaces (+3)"  :methods [base-method]
    :bonuses [] :shields []
    :armors [{:base-ac 11 :type :light ::magic 1}    ; 14
             {:base-ac 11 :type :light ::magic 2}    ; 15
             {:base-ac 11 :type :light ::magic 3}]   ; 16  <- winner
    :expected 16}])

(deftest candidates-satisfy-the-shared-spec
  (doseq [[cand-name f] candidates]
    (testing (str "candidate " cand-name)
      (doseq [{:keys [name methods bonuses armors shields expected]} spec]
        (is (= expected (f {:methods methods :bonuses bonuses} armors shields))
            (str cand-name " / " name))))))

;; ---- efficiency: count method evaluations on an adversarial config ----------
(defn counted [methods counter]
  (mapv (fn [m] (update m :fn (fn [f] (fn [a s] (swap! counter inc) (f a s))))) methods))

(deftest efficiency-bucketed-beats-naive
  (testing "20 item-independent homebrew methods + 8 armors: bucketed evaluates far fewer"
    (let [methods (into [base-method floor-16] (map unarmored-method (range 1 21)))  ; 22 methods
          armors  (vec (repeat 8 scale))
          shields [{:shield true}]
          config  {:methods methods :bonuses [ring-1 (shield-bonus)]}
          cn (atom 0) cb (atom 0)
          ac-n ((:naive candidates)    (assoc config :methods (counted methods cn)) armors shields)
          ac-b ((:bucketed candidates) (assoc config :methods (counted methods cb)) armors shields)]
      (println (format "\n[EFFICIENCY] armors=8 methods=%d  ->  naive=%d method-evals, bucketed=%d\n"
                       (count methods) @cn @cb))
      (is (= ac-n ac-b) "both candidates compute the SAME best AC")
      (is (< @cb @cn) "bucketed evaluates strictly fewer methods"))))

;; ---- hardening: a MIS-DECLARED method (reads fields but :item? false) -------
;; Surfaces the bucketed candidate's one fragility: it trusts :item?. The naive candidate,
;; which ignores the flag, can't be fooled. This documents the tradeoff, doesn't 'fail' either.
(deftest hardening-mis-declared-item-flag
  (testing "a method that reads armor fields but is flagged :item? false"
    (let [bad {:item? false                                   ; WRONG: it reads :base-ac
               :fn (fn [armor _] (if armor (+ (:base-ac armor) 5) 0))}
          config {:methods [base-method bad] :bonuses []}
          armors [scale] shields []
          ac-n ((:naive candidates) config armors shields)
          ac-b (try ((:bucketed candidates) config armors shields)
                    (catch Throwable t (str "threw " (.getSimpleName (class t)))))]
      (println (format "[HARDENING] mis-flagged method:  naive=%s  bucketed=%s" ac-n ac-b))
      (is (= 19 ac-n) "naive ignores the flag and is correct: scale 14 + 5 = 19")
      (is (not= ac-n ac-b)
          "bucketed is fooled by the wrong flag — wrong number OR a crash (sentinel has no fields). The tradeoff."))))
