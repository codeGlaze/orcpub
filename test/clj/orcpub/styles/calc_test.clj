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

(deftest no-calc-adds-with-a-plus
  (let [plus (distinct (filter #(re-find #"calc\([^)]*\+" %) (strings styles/app)))]
    (is (empty? plus)
        (str "calc() with +, which the build turns into one browsers ignore; write - -n instead: "
             (vec plus)))))
