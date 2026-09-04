(ns orcpub.dnd.e5.ac-experiments-test
  "Comparison harness for the OUTER AC reconciler ('best AC across owned armor/shield'). The
   implementation being proved out is orcpub.dnd.e5.armor-class/best-ac — NOT yet wired into the app
   (it works out the other formulas once instead of per armor). This file keeps a NAIVE brute-force
   implementation as a reference ORACLE, checks the two agree on a shared spec, measures the
   efficiency difference, and documents the one tradeoff.

   Vocabulary (matches armor-class): the ARMOR formula is the AC from the worn armor (the only one
   that changes per armor); the OTHER formulas (unarmored defense, natural armor, floors, homebrew)
   give the same number regardless of which armor is worn. Each is a plain (fn [armor shield] -> n);
   bonuses are (fn [armor shield] -> n) summed onto the winning formula."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.armor-class :as ac]))

(def dex 2)  ; Dex 14 (+2), matching the reconciliation spec
(def ability-mods {:dex dex :wis 3 :con 3})        ; test ability modifiers (Dex 14, Wis/Con 16)

;; ---- reusable formulas ------------------------------------------------------
(defn armor-dex
  "Dex contribution from worn armor. Honors the armor's OWN :max-dex cap when present (custom
   material: 'heavy armor that still allows a Dex bonus'), else the type default — the single
   seam where custom-armor properties enter AC.

   NOT SAFE TO COPY INTO THE APP AS-IS. Preferring the armor's cap over the type default would
   break Medium Armor Master, which raises the medium cap to 3 by setting ?max-medium-armor-bonus
   (options.cljc:1461) while the armor itself still says :max-dex-mod 2. The real version has to
   combine the armor's limit with anything that raises it. See docs/kb/armor-class-refactor.md."
  [armor]
  (if-let [md (:max-dex armor)]
    (min md dex)
    (case (:type armor) :light dex :medium (min 2 dex) :heavy 0)))

(defn armor-formula                                ; THE armor formula: worn armor, else 10 + Dex
  [armor _shield]
  (if armor
    (+ (:base-ac armor)
       (armor-dex armor)
       (get armor ::magic 0)                                    ; magic bound to THIS armor
       (reduce + 0 (keep ability-mods (:add-abilities armor)))) ; abilities this armor adds to AC
    (+ 10 dex)))

(defn unarmored [n]                                ; "AC = 10 + Dex + n" while unarmored (an OTHER formula)
  (fn [armor _] (if armor 0 (+ 10 dex n))))

(defn floor [n] (fn [_ _] n))                      ; Barkskin-style floor: a constant OTHER formula
(def ring-1 (fn [_ _] 1))                          ; +1 regardless of armor (Ring of Protection)
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
;; Run reconcile-ac for every (armor, shield) combo, take the max. Correct but wasteful — exists
;; only to check ac/best-ac against.
(defn best-ac-naive [config armors shields]
  (reduce max 0
   (for [armor  (cons nil armors)
         shield (cons nil shields)]
     (ac/reconcile-ac config armor shield))))

(def candidates {:naive best-ac-naive :bucketed ac/best-ac})

;; ---- the shared correctness spec (both must reproduce) ----------------------
(def spec
  [{:name "base only, offer leather"        :armor armor-formula :others []                    :bonuses [] :armors [leather] :shields [] :expected 13}
   {:name "unarmored defense, no armor"     :armor armor-formula :others [(unarmored 3)]       :bonuses [] :armors []        :shields [] :expected 15}
   {:name "unarmored loses to armor"        :armor armor-formula :others [(unarmored 3)]       :bonuses [] :armors [scale]   :shields [] :expected 16}
   {:name "floor lifts a low AC"            :armor armor-formula :others [(floor 16)]          :bonuses [] :armors [leather] :shields [] :expected 16}
   {:name "floor doesn't cap a high AC"     :armor armor-formula :others [(unarmored 8) (floor 16)] :bonuses [] :armors [] :shields [] :expected 20}
   {:name "homebrew formula wins + ring"    :armor armor-formula :others [(unarmored 8)]       :bonuses [ring-1] :armors [chain] :shields [] :expected 21}
   {:name "shield adds to the winner"       :armor armor-formula :others [(unarmored 3)]       :bonuses [(shield-bonus)] :armors [] :shields [{:shield true}] :expected 17}
   ;; custom-armor axis — the armor formula reading the item's own fields:
   {:name "standard heavy: no Dex"          :armor armor-formula :others []                    :bonuses [] :armors [chain]         :shields [] :expected 16}
   {:name "custom heavy ALLOWS Dex"         :armor armor-formula :others []                    :bonuses [] :armors [mithril-plate] :shields [] :expected 18}
   {:name "custom magical armor"            :armor armor-formula :others []                    :bonuses [] :armors [magic-leather] :shields [] :expected 14}
   {:name "air-elemental: armor adds Wis"   :armor armor-formula :others []                    :bonuses [] :armors [air-elemental] :shields [] :expected 20}
   ;; armor-bound magic must NOT leak: homebrew unarmored 20 + a +3 magic studded. Unarmored the
   ;; +3 is absent (20); worn it's 12+2+3=17. Best 20 (not 23) proves no leak.
   {:name "armor magic stays with its armor":armor armor-formula :others [(unarmored 8)]       :bonuses [] :armors [magic-studded] :shields [] :expected 20}
   ;; owning leather +1/+2/+3: the +3 (11 + Dex 2 + 3 = 16) must surface as the best pick.
   {:name "best magic armor surfaces (+3)"  :armor armor-formula :others []
    :bonuses [] :shields []
    :armors [{:base-ac 11 :type :light ::magic 1}    ; 14
             {:base-ac 11 :type :light ::magic 2}    ; 15
             {:base-ac 11 :type :light ::magic 3}]   ; 16  <- winner
    :expected 16}])

(defn config-of [{:keys [armor others bonuses]}]
  {:armor-formula armor :other-formulas others :bonuses bonuses})

(deftest candidates-satisfy-the-shared-spec
  (doseq [[cand-name f] candidates]
    (testing (str "candidate " cand-name)
      (doseq [{:keys [name armors shields expected] :as case} spec]
        (is (= expected (f (config-of case) armors shields))
            (str cand-name " / " name))))))

;; ---- efficiency: count formula evaluations on an adversarial config ---------
(defn counting [f counter] (fn [a s] (swap! counter inc) (f a s)))

(deftest efficiency-bucketed-beats-naive
  (testing "20 other homebrew formulas + 8 armors: bucketed evaluates far fewer"
    (let [others  (into [(floor 16)] (map unarmored (range 1 21)))  ; 21 other formulas + 1 armor formula
          armors  (vec (repeat 8 scale))
          shields [{:shield true}]
          config  (fn [counter]
                    {:armor-formula  (counting armor-formula counter)
                     :other-formulas (mapv #(counting % counter) others)
                     :bonuses        [ring-1 (shield-bonus)]})
          cn (atom 0) cb (atom 0)
          ac-n ((:naive candidates)    (config cn) armors shields)
          ac-b ((:bucketed candidates) (config cb) armors shields)]
      (println (format "\n[EFFICIENCY] armors=8 formulas=%d  ->  naive=%d formula-evals, bucketed=%d\n"
                       (inc (count others)) @cn @cb))
      (is (= ac-n ac-b) "both compute the SAME best AC")
      (is (< @cb @cn) "bucketed evaluates strictly fewer formulas"))))

;; ---- hardening: an armor-reading formula put in the WRONG group ------------
;; If a formula that reads armor fields is placed in :other-formulas (it belongs as :armor-formula),
;; bucketed evaluates it with the ::worn stand-in and crashes; the naive oracle, which passes real
;; armor, stays correct. In the real app this can't happen — homebrew formulas can't read armor
;; fields, and template_base owns which formula is the armor one. Documents the tradeoff.
(deftest hardening-armor-reading-formula-in-wrong-group
  (testing "a formula that reads armor fields, mistakenly grouped with the OTHER formulas"
    (let [reads-armor (fn [armor _] (if armor (+ (:base-ac armor) 5) 0))   ; should be the armor formula
          config {:armor-formula armor-formula :other-formulas [reads-armor] :bonuses []}
          ac-n ((:naive candidates) config [scale] [])
          ac-b (try ((:bucketed candidates) config [scale] [])
                    (catch Throwable t (str "threw " (.getSimpleName (class t)))))]
      (println (format "[HARDENING] armor-reader in wrong group:  naive=%s  bucketed=%s" ac-n ac-b))
      (is (= 19 ac-n) "naive passes real armor and is correct: scale 14 + 5 = 19")
      (is (not= ac-n ac-b)
          "bucketed is fooled by the wrong grouping — wrong number OR a crash. The measured tradeoff."))))
