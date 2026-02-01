(ns orcpub.dice-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.test.alpha :as stest]
            [orcpub.dice :as dice]))

(deftest die-roll-bounds
  (testing "die-roll returns values within [1, sides]"
    (doseq [sides dice/dice-sides]
      (let [results (repeatedly 100 #(dice/die-roll sides))]
        (is (every? #(<= 1 % sides) results)
            (str "d" sides " should produce values in [1," sides "]"))))))

(deftest roll-n-count
  (testing "roll-n returns the requested number of dice"
    (is (= 1 (count (dice/roll-n 1 6))))
    (is (= 4 (count (dice/roll-n 4 6))))
    (is (= 20 (count (dice/roll-n 20 20))))))

(deftest dice-roll-basic
  (testing "dice-roll sums dice and applies modifier"
    (let [result (dice/dice-roll {:num 1 :sides 6})]
      (is (<= 1 result 6)))
    (let [result (dice/dice-roll {:num 1 :sides 6 :modifier 10})]
      (is (<= 11 result 16))))
  (testing "dice-roll drops lowest when drop-num specified"
    ;; 4d6 drop 1 should return sum of best 3 of 4 dice
    (let [result (dice/dice-roll {:num 4 :sides 6 :drop-num 1})]
      (is (<= 3 result 18)))))

(deftest die-mean-round-down-values
  (testing "die-mean-round-down computes floor of average"
    ;; d4: mean = 2.5, floor = 2
    (is (= 2 (dice/die-mean-round-down 4)))
    ;; d6: mean = 3.5, floor = 3
    (is (= 3 (dice/die-mean-round-down 6)))
    ;; d8: mean = 4.5, floor = 4
    (is (= 4 (dice/die-mean-round-down 8)))
    ;; d10: mean = 5.5, floor = 5
    (is (= 5 (dice/die-mean-round-down 10)))
    ;; d12: mean = 6.5, floor = 6
    (is (= 6 (dice/die-mean-round-down 12)))
    ;; d20: mean = 10.5, floor = 10
    (is (= 10 (dice/die-mean-round-down 20)))))

(deftest die-mean-round-up-values
  (testing "die-mean-round-up computes ceil of average"
    (is (= 3 (dice/die-mean-round-up 4)))
    (is (= 4 (dice/die-mean-round-up 6)))
    (is (= 5 (dice/die-mean-round-up 8)))
    (is (= 6 (dice/die-mean-round-up 10)))
    (is (= 7 (dice/die-mean-round-up 12)))
    (is (= 11 (dice/die-mean-round-up 20)))))

(deftest dice-mean-round-down-values
  (testing "dice-mean-round-down for multiple dice with modifier"
    ;; 2d6+0 = 7.0, floor = 7
    (is (= 7 (dice/dice-mean-round-down 2 6 0)))
    ;; 1d8+3 = 7.5, floor = 7
    (is (= 7 (dice/dice-mean-round-down 1 8 3)))
    ;; 3d6+0 = 10.5, floor = 10
    (is (= 10 (dice/dice-mean-round-down 3 6 0)))))

(deftest dice-string-formatting
  (testing "dice-string produces correct notation"
    (is (= "1d6+2" (dice/dice-string 1 6 2)))
    (is (= "2d8-1" (dice/dice-string 2 8 -1)))
    (is (= "1d20+0" (dice/dice-string 1 20 0)))))

(deftest dice-regex-parsing
  (testing "dice-regex matches standard dice notation"
    (is (some? (re-matches dice/dice-regex "1d6")))
    (is (some? (re-matches dice/dice-regex "2d8+3")))
    (is (some? (re-matches dice/dice-regex "3d6-1")))
    (is (some? (re-matches dice/dice-regex "d20")))
    (is (some? (re-matches dice/dice-regex "4d6 + 2")))
    (is (nil? (re-matches dice/dice-regex "not-dice")))))
