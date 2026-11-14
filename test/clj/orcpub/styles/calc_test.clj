(ns orcpub.styles.calc-test
  "No calc() in the styles adds with +. Garden keeps the spaces, but the stylesheet build strips
   them round a + inside calc(), and browsers then ignore the rule (docs/kb/garden-calc-plus.md);
   so this checks the style data, where the + is still visible."
  (:require [clojure.test :refer [deftest is]]
            [orcpub.styles.core :as styles]))

(defn- strings
  "Every string anywhere in the garden data `x` (rules, maps, media queries). Returns a seq."
  [x]
  (filter string? (tree-seq coll? seq x)))

(defn- calc-bodies
  "The text inside each calc( ... ) in `s`, nested parentheses included (calc(var(--w) + 1px)
   yields \"var(--w) + 1px\"). Returns a seq of strings."
  [^String s]
  (for [start (keep-indexed (fn [i _] (when (.startsWith s "calc(" i) (+ i 5))) s)]
    (loop [i start depth 1]
      (cond (>= i (count s)) (subs s start)
            (= \( (.charAt s i)) (recur (inc i) (inc depth))
            (= \) (.charAt s i)) (if (= 1 depth) (subs s start i) (recur (inc i) (dec depth)))
            :else (recur (inc i) depth)))))

(deftest calc-bodies-see-past-a-nested-function
  (is (= ["var(--w) + 1px"] (calc-bodies "width: calc(var(--w) + 1px)")))
  (is (= ["100% - 2px" "1em"] (calc-bodies "calc(100% - 2px) calc(1em)"))))

(deftest no-calc-adds-with-a-plus
  (let [plus (distinct (filter #(some (fn [body] (.contains ^String body "+")) (calc-bodies %))
                               (strings styles/app)))]
    (is (empty? plus)
        (str "calc() with +, which the build turns into one browsers ignore; write - -n instead: "
             (vec plus)))))
