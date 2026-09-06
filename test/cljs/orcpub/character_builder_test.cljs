(ns orcpub.character-builder-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.character-builder :as cb]))

;; highlight-match backs the combobox's match highlighting. It returns either the name
;; unchanged or a hiccup :span splitting it into before / hit / after, so the tests assert
;; that split directly rather than rendering.

(defn- parts
  "before, hit, after -- or :plain when the name came back unchanged."
  [v]
  (if (vector? v)
    (let [[_ before [_ hit] after] v] [before hit after])
    :plain))

(deftest highlight-match-no-highlight
  (testing "blank query leaves the name alone"
    (is (= :plain (parts (cb/highlight-match "Battleaxe" ""))))
    (is (= :plain (parts (cb/highlight-match "Battleaxe" "   "))))
    (is (= "Battleaxe" (cb/highlight-match "Battleaxe" ""))))
  (testing "a query that does not occur leaves the name alone"
    (is (= :plain (parts (cb/highlight-match "Battleaxe" "zzz")))))
  (testing "query longer than the name"
    (is (= :plain (parts (cb/highlight-match "Axe" "axelotl"))))))

(deftest highlight-match-positions
  (testing "match at the start"
    (is (= ["" "Batt" "leaxe"] (parts (cb/highlight-match "Battleaxe" "batt")))))
  (testing "match in the middle"
    (is (= ["Batt" "lea" "xe"] (parts (cb/highlight-match "Battleaxe" "lea")))))
  (testing "match at the end -- the case that motivated highlighting at all"
    (is (= ["Battleaxe " "+1" ""] (parts (cb/highlight-match "Battleaxe +1" "+1")))))
  (testing "whole name matches"
    (is (= ["" "Axe" ""] (parts (cb/highlight-match "Axe" "axe"))))))

(deftest highlight-match-is-not-a-regex
  (testing "regex metacharacters are literals, so + ( ) . * behave"
    (is (= ["Sword " "+1" ""] (parts (cb/highlight-match "Sword +1" "+1"))))
    (is (= ["Crossbow" ", h" "and"] (parts (cb/highlight-match "Crossbow, hand" ", h"))))
    (is (= :plain (parts (cb/highlight-match "Battleaxe" ".*"))))
    (is (= ["Bag " "(large)" ""] (parts (cb/highlight-match "Bag (large)" "(large)"))))))

(deftest highlight-match-case
  (testing "matching is case-insensitive, and the HIGHLIGHTED SLICE KEEPS THE ITEM'S OWN
            CASING rather than echoing what was typed -- it is a slice of the name, not the
            query. Highlighting \"batt\" in \"BATTLEAXE\" must not render a lower-case \"batt\"."
    (is (= ["" "BATT" "LEAXE"] (parts (cb/highlight-match "BATTLEAXE" "batt")))))
  (testing "the query must arrive lower-cased -- the caller lower-cases once per render.
            An upper-case query finding nothing documents that contract."
    (is (= :plain (parts (cb/highlight-match "Battleaxe" "BATT"))))))

(deftest highlight-match-hostile-input
  (testing "a nil or non-string name does not throw"
    (is (= :plain (parts (cb/highlight-match nil "batt"))))
    (is (= :plain (parts (cb/highlight-match nil ""))))
    (is (= ["" "12" ""] (parts (cb/highlight-match 12 "12")))))
  (testing "the highlighted slice is the query's LENGTH, never more -- it stops at the first
            occurrence and does not run to the end of the name"
    (let [[_ hit after] (parts (cb/highlight-match "Longsword, +1 flame" "+1"))]
      (is (= "+1" hit))
      (is (= " flame" after)))))

(deftest highlight-match-preserves-the-name
  (testing "the three pieces always rejoin to the original"
    (doseq [[nm q] [["Battleaxe" "lea"] ["Sword +1" "+1"] ["Crossbow, hand" "and"]
                    ["Bag (large)" "(l"] ["AXE" "ax"]]]
      (let [[before hit after] (parts (cb/highlight-match nm q))]
        (is (= nm (str before hit after)) (str "rejoining " nm " around " q))))))
