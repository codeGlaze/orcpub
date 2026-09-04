(ns orcpub.dnd.e5.ac-outer-loop-analysis-test
  "Should the outer AC loop — \"best AC across every owned armor and shield\" — use the bucketed
   strategy in armor-class/best-ac, or stay naive?

   The loop lives in subs.cljs's ::char5e/best-armor-combo, and it must return the WINNING COMBO
   (views.cljs reads :armor :key and :shield :key to preselect the equipment dropdowns), not just a
   number. Both candidates here return a combo so the comparison is fair; armor-class/best-ac as
   written returns only the max and could not serve that call site unchanged.

   NAIVE    — run the full reconciler on every (armor, shield) pair. What ships today.
   BUCKETED — the calculations that ignore WHICH armor is worn give the same answer for every armor
              in the same (armored?, shield) state, so evaluate them once per state instead of once
              per armor. This is what best-ac does, and it needs authors to declare which group a
              calculation is in.

   Measured on the JVM. The app runs this in JS, so treat the RATIO as indicative and the absolute
   numbers as not transferable."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.armor-class :as ac]))

(def dex 3)
(def magic :orcpub.dnd.e5.magic-items/magical-ac-bonus)

(defn armor-n [i] {:base-ac (+ 11 (mod i 6)) :type :medium :max-dex-mod 2 magic (mod i 3)})
(def shield-n {:type :shield magic 1})

;; item-INDEPENDENT: only looks at whether armor is present, never at which one
(defn unarmored-calc [n] (fn [armor _] (if armor 0 (+ 10 dex n))))
(defn floor-calc [n] (fn [_ _] n))
(def ring (fn [_ _] 1))
(def shield-bonus-fn (fn [_ shield] (if shield (ac/shield-bonus shield) 0)))

(defn- combos [armors shields] (for [a (cons nil armors) s (cons nil shields)] [a s]))

(defn naive
  "Full reconcile per combo — today's behaviour."
  [calcs bonuses armors shields]
  (apply max-key :ac
         (for [[a s] (combos armors shields)]
           {:ac (ac/reconcile (ac/worn-armor-ac dex {:light nil :medium 2 :heavy 0} a) calcs bonuses a s) :armor a :shield s})))

(defn bucketed
  "Item-independent calculations evaluated once per (armored?, shield) state."
  [calcs bonuses armors shields]
  (let [states (into {} (for [worn? [true false] s (cons nil shields)]
                          [[worn? s] (reduce max 0 (map #(% (when worn? ::worn) s) calcs))]))]
    (apply max-key :ac
           (for [[a s] (combos armors shields)]
             {:ac (+ (max (states [(some? a) s]) (ac/worn-armor-ac dex {:light nil :medium 2 :heavy 0} a))
                     (reduce + 0 (map #(% a s) bonuses)))
              :armor a :shield s}))))

(defn- ms
  "Mean ms per call. Warms the JIT first and takes the best of several timed rounds — without the
  warmup the first scenario measured pays for compilation and the numbers invert."
  [f n]
  (dotimes [_ 3000] (f))                                   ; warm up
  (->> (repeatedly 3 #(let [t (System/nanoTime)]
                        (dotimes [_ n] (f))
                        (/ (- (System/nanoTime) t) 1e6 n)))
       (reduce min)))

(def scenarios
  [["typical    (2 armor, 1 shield, 2 calcs)"  2 1 2]
   ["kitted     (5 armor, 2 shields, 6 calcs)" 5 2 6]
   ["adversarial(8 armor, 2 shields, 21 calcs)" 8 2 21]
   ;; The two larger points that located the crossover (20/3/40 -> 1.82x, 40/4/80 -> 3.01x) are
   ;; recorded in the AC refactor doc rather than run every suite — they dominated its runtime.
   ["absurd     (20 armor, 3 shields, 40 calcs)" 20 3 40]])

;; Outer-loop correctness cases, carried over from ac_experiments_test when that file and the
;; unwired best-ac it exercised were retired. These are about searching ACROSS owned items, which
;; armor_class_test (single-combo arithmetic) does not cover.
(deftest outer-loop-picks-the-genuinely-best-combination
  (let [plain-11 {:base-ac 11 :type :light magic 0}
        m1 {:base-ac 11 :type :light magic 1}
        m2 {:base-ac 11 :type :light magic 2}
        m3 {:base-ac 11 :type :light magic 3}]
    (testing "the best magic armor owned surfaces, not merely the first"
      (is (= 17 (:ac (naive [] [] [m1 m2 m3] [])))  "11 + Dex 3 + 3")
      (is (= m3 (:armor (naive [] [] [m1 m2 m3] []))) "and it reports WHICH armor won"))
    (testing "ITEM magic never leaks to a combination that isn't wearing that item"
      ;; homebrew "AC = 20 while unarmored" + a +3 armor worth 11+3+3 = 17. Best is 20, not 23.
      (is (= 20 (:ac (naive [(unarmored-calc 7)] [] [m3] [])))
          "if the armor's +3 leaked onto the unarmored calculation this would read 23")
      (is (nil? (:armor (naive [(unarmored-calc 7)] [] [m3] []))) "the winner is the unarmored combo"))
    (testing "a worn-armor combination wins when it is actually better"
      (is (= 14 (:ac (naive [(unarmored-calc 1)] [] [plain-11] []))) "11 + Dex 3 beats 10 + Dex + 1"))
    (testing "shields are searched too and reported"
      (let [best (naive [] [ring shield-bonus-fn] [plain-11] [shield-n])]
        (is (= 18 (:ac best)) "11 + Dex 3 + ring 1 + shield 3")
        (is (= shield-n (:shield best)))))))

(deftest bucketing-analysis
  (testing "both strategies must agree, then compare cost"
    (println "\n[OUTER LOOP] naive vs bucketed — ms per call, JVM, ratio is the transferable part")
    (println (format "  %-42s %-10s %-10s %s" "scenario" "naive" "bucketed" "speedup"))
    (doseq [[label n-armor n-shield n-calc] scenarios]
      (let [armors  (mapv armor-n (range n-armor))
            shields (vec (repeat n-shield shield-n))
            calcs   (into [(floor-calc 16)] (map unarmored-calc (range 1 n-calc)))
            bonuses [ring shield-bonus-fn]
            a (naive calcs bonuses armors shields)
            b (bucketed calcs bonuses armors shields)]
        (is (= (:ac a) (:ac b)) (str "same best AC / " label))
        (let [tn (ms #(naive calcs bonuses armors shields) 500)
              tb (ms #(bucketed calcs bonuses armors shields) 500)]
          (println (format "  %-42s %-10.4f %-10.4f %.2fx" label tn tb (/ tn tb))))))
    (println "")))

(deftest bucketing-is-wrong-if-a-calculation-is-misgrouped
  (testing "the cost of the strategy: a calculation that DOES read the worn armor's fields, placed
            with the item-independent ones, is evaluated against a ::worn placeholder"
    (let [reads-armor (fn [armor _] (if armor (+ (:base-ac armor) 5) 0))
          armors [(armor-n 0)]
          a (naive [reads-armor] [] armors [])
          b (try (bucketed [reads-armor] [] armors [])
                 (catch Throwable t {:ac (str "threw " (.getSimpleName (class t)))}))]
      (println (format "[MISGROUPED] naive=%s bucketed=%s\n" (:ac a) (:ac b)))
      (is (not= (:ac a) (:ac b))
          "naive passes the real armor and is right; bucketed is fooled — a wrong number or a
           crash. Nothing in the type system prevents the mistake; it is on the author."))))
