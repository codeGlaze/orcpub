(ns orcpub.dnd.e5.ac-experiments-test
  "Comparison harness for the OUTER AC reconciler ('best AC across owned armor/shield'). The
   shipping implementation is orcpub.dnd.e5.armor-class/best-ac (the 'bucketed' one). This file
   keeps a NAIVE brute-force implementation as a reference ORACLE and checks the two agree on a
   shared spec, then measures the efficiency difference and documents the one hardening tradeoff.

   VOCABULARY (matches armor-class):
     formula = {:reads-armor? bool  :fn (fn [armor shield] n)}
       :reads-armor? true  -> the fn reads the worn armor's own fields (:base-ac/:type); must be
                              evaluated per owned armor (the worn-armor formula — usually the ONLY one).
       :reads-armor? false -> reads armor only as (nil? armor); value depends on (armored?, shield),
                              a few states, NOT the armor list (unarmored defense, natural armor, floors).
     bonus   = (fn [armor shield] n)  -> summed onto the winning formula."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.armor-class :as ac]))

(def dex 2)  ; Dex 14 (+2), matching the reconciliation spec
(def ability-mods {:dex dex :wis 3 :con 3})        ; test ability modifiers (Dex 14, Wis/Con 16)

;; ---- reusable formulas ------------------------------------------------------
(defn armor-dex
  "Dex contribution from worn armor. Honors the armor's OWN :max-dex cap when present (custom
   material: 'heavy armor that still allows a Dex bonus'), else the type default — the single
   seam where custom-armor properties enter AC."
  [armor]
  (if-let [md (:max-dex armor)]
    (min md dex)
    (case (:type armor) :light dex :medium (min 2 dex) :heavy 0)))

(def base-formula                                  ; the SRD base: worn armor, else 10+Dex (reads-armor)
  {:reads-armor? true
   :fn (fn [armor _]
         (if armor
           (+ (:base-ac armor)
              (armor-dex armor)
              (get armor ::magic 0)                                 ; magic bound to THIS armor
              (reduce + 0 (keep ability-mods (:add-abilities armor)))) ; abilities this armor adds to AC
           (+ 10 dex)))})

(defn unarmored-formula [n]                        ; "AC = 10 + Dex + n" while unarmored (armor-blind)
  {:reads-armor? false :fn (fn [armor _] (if armor 0 (+ 10 dex n)))})

(def floor-16 {:reads-armor? false :fn (fn [_ _] 16)})  ; Barkskin-style floor: constant formula
(def ring-1   (fn [_ _] 1))                        ; universal +1 (Ring of Protection)
(defn shield-bonus [] (fn [_ shield] (if shield 2 0)))

(def leather {:base-ac 11 :type :light})
(def scale   {:base-ac 14 :type :medium})
(def chain   {:base-ac 16 :type :heavy})
;; ---- custom homebrew armor (the friend's weird-materials supplement) --------
(def mithril-plate {:base-ac 16 :type :heavy :max-dex 2})              ; heavy AC, but ALLOWS a Dex bonus
(def magic-leather {:base-ac 11 :type :light ::magic 1})              ; +1 magical armor
(def air-elemental {:base-ac 15 :type :medium :add-abilities [:wis]}) ; adds Wis mod to AC
(def magic-studded {:base-ac 12 :type :light ::magic 3})             ; +3 magic — bound to THIS armor only

;; ---- reference ORACLE: naive brute force ------------------------------------
;; Run every formula for every (armor, shield) combo, take the max. Correct but wasteful; exists
;; only to check ac/best-ac against (and it ignores :reads-armor?, so it can't be fooled by it).
(defn best-ac-naive [{:keys [formulas bonuses]} armors shields]
  (reduce max 0
   (for [armor  (cons nil armors)
         shield (cons nil shields)]
     (ac/reconcile-ac {:formulas (map :fn formulas) :bonuses bonuses} armor shield))))

(def candidates {:naive best-ac-naive :bucketed ac/best-ac})

;; ---- the shared correctness spec (both must reproduce) ----------------------
(def spec
  [{:name "base only, offer leather"        :formulas [base-formula]                        :bonuses [] :armors [leather] :shields [] :expected 13}
   {:name "unarmored defense, no armor"     :formulas [base-formula (unarmored-formula 3)]  :bonuses [] :armors []        :shields [] :expected 15}
   {:name "unarmored loses to armor"        :formulas [base-formula (unarmored-formula 3)]  :bonuses [] :armors [scale]   :shields [] :expected 16}
   {:name "floor lifts a low AC"            :formulas [base-formula floor-16]               :bonuses [] :armors [leather] :shields [] :expected 16}
   {:name "floor doesn't cap a high AC"     :formulas [base-formula (unarmored-formula 8) floor-16] :bonuses [] :armors [] :shields [] :expected 20}
   {:name "homebrew formula wins + ring"    :formulas [base-formula (unarmored-formula 8)]  :bonuses [ring-1] :armors [chain] :shields [] :expected 21}
   {:name "shield adds to the winner"       :formulas [base-formula (unarmored-formula 3)]  :bonuses [(shield-bonus)] :armors [] :shields [{:shield true}] :expected 17}
   ;; custom-armor axis — the worn-armor formula reading the item's own fields:
   {:name "standard heavy: no Dex"          :formulas [base-formula]                        :bonuses [] :armors [chain]         :shields [] :expected 16}
   {:name "custom heavy ALLOWS Dex"         :formulas [base-formula]                        :bonuses [] :armors [mithril-plate] :shields [] :expected 18}
   {:name "custom magical armor"            :formulas [base-formula]                        :bonuses [] :armors [magic-leather] :shields [] :expected 14}
   {:name "air-elemental: armor adds Wis"   :formulas [base-formula]                        :bonuses [] :armors [air-elemental] :shields [] :expected 20}
   ;; armor-bound magic must NOT leak: homebrew unarmored 20 + a +3 magic studded. Unarmored the
   ;; +3 is absent (20); worn it's 12+2+3=17. Best 20 (not 23) proves no leak.
   {:name "armor magic stays with its armor":formulas [base-formula (unarmored-formula 8)]  :bonuses [] :armors [magic-studded] :shields [] :expected 20}
   ;; owning leather +1/+2/+3: the +3 (11 + Dex 2 + 3 = 16) must surface as the best pick.
   {:name "best magic armor surfaces (+3)"  :formulas [base-formula]
    :bonuses [] :shields []
    :armors [{:base-ac 11 :type :light ::magic 1}    ; 14
             {:base-ac 11 :type :light ::magic 2}    ; 15
             {:base-ac 11 :type :light ::magic 3}]   ; 16  <- winner
    :expected 16}])

(deftest candidates-satisfy-the-shared-spec
  (doseq [[cand-name f] candidates]
    (testing (str "candidate " cand-name)
      (doseq [{:keys [name formulas bonuses armors shields expected]} spec]
        (is (= expected (f {:formulas formulas :bonuses bonuses} armors shields))
            (str cand-name " / " name))))))

;; ---- efficiency: count formula evaluations on an adversarial config ---------
(defn counted [formulas counter]
  (mapv (fn [m] (update m :fn (fn [f] (fn [a s] (swap! counter inc) (f a s))))) formulas))

(deftest efficiency-bucketed-beats-naive
  (testing "20 armor-blind homebrew formulas + 8 armors: bucketed evaluates far fewer"
    (let [formulas (into [base-formula floor-16] (map unarmored-formula (range 1 21)))  ; 22 formulas
          armors   (vec (repeat 8 scale))
          shields  [{:shield true}]
          bonuses  [ring-1 (shield-bonus)]
          cn (atom 0) cb (atom 0)
          ac-n ((:naive candidates)    {:formulas (counted formulas cn) :bonuses bonuses} armors shields)
          ac-b ((:bucketed candidates) {:formulas (counted formulas cb) :bonuses bonuses} armors shields)]
      (println (format "\n[EFFICIENCY] armors=8 formulas=%d  ->  naive=%d formula-evals, bucketed=%d\n"
                       (count formulas) @cn @cb))
      (is (= ac-n ac-b) "both compute the SAME best AC")
      (is (< @cb @cn) "bucketed evaluates strictly fewer formulas"))))

;; ---- hardening: a MIS-DECLARED formula (reads fields but :reads-armor? false) ----
;; The bucketed reconciler's one fragility: it trusts :reads-armor?. The naive oracle, which
;; ignores the flag, can't be fooled. In the real app this can't happen — homebrew formulas are
;; declarative and can't read armor fields, so the flag is constructor-owned. Documents the tradeoff.
(deftest hardening-mis-declared-reads-armor-flag
  (testing "a formula that reads armor fields but is flagged :reads-armor? false"
    (let [bad {:reads-armor? false                             ; WRONG: it reads :base-ac
               :fn (fn [armor _] (if armor (+ (:base-ac armor) 5) 0))}
          cfg {:formulas [base-formula bad] :bonuses []}
          ac-n ((:naive candidates) cfg [scale] [])
          ac-b (try ((:bucketed candidates) cfg [scale] [])
                    (catch Throwable t (str "threw " (.getSimpleName (class t)))))]
      (println (format "[HARDENING] mis-flagged formula:  naive=%s  bucketed=%s" ac-n ac-b))
      (is (= 19 ac-n) "naive ignores the flag and is correct: scale 14 + 5 = 19")
      (is (not= ac-n ac-b)
          "bucketed is fooled by the wrong flag — wrong number OR a crash. The measured tradeoff."))))
