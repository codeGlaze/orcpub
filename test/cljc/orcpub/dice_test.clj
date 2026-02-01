(ns orcpub.dice-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dice :as dice]))

;; dice-roll-text parses user-typed dice notation (e.g. "2d8+3") from
;; the search bar in events.cljs. The regex has optional fields (num,
;; modifier, sign) that can interact in non-obvious ways, and it
;; handles both "2d8+3" and "d20" (implied 1). Worth testing because
;; it processes arbitrary user input.

(deftest dice-roll-text-parses-standard-notation
  (testing "parses NdS+M format"
    (let [result (dice/dice-roll-text "2d8+3")]
      (is (some? result) "should match valid dice notation")
      (is (= 2 (count (:rolls result))))
      (is (= 3 (:mod result)))
      (is (= 3 (:raw-mod result)))
      (is (= 1 (:plus-minus result)))))
  (testing "parses subtracted modifier"
    (let [result (dice/dice-roll-text "1d6-2")]
      (is (= -2 (:mod result)))
      (is (= 2 (:raw-mod result)))
      (is (= -1 (:plus-minus result)))))
  (testing "implied 1 die when num omitted"
    (let [result (dice/dice-roll-text "d20")]
      (is (some? result))
      (is (= 1 (count (:rolls result))))))
  (testing "no modifier defaults to 0"
    (let [result (dice/dice-roll-text "3d6")]
      (is (= 0 (:mod result)))
      (is (= 0 (:raw-mod result)))))
  (testing "returns nil for non-dice input"
    (is (nil? (dice/dice-roll-text "fireball")))
    (is (nil? (dice/dice-roll-text "")))
    (is (nil? (dice/dice-roll-text "abc123")))))

(deftest dice-roll-text-total-is-consistent
  (testing "total equals sum of rolls plus mod"
    (dotimes [_ 50]
      (let [result (dice/dice-roll-text "3d6+2")]
        (is (= (:total result)
               (+ (:mod result) (apply + (:rolls result)))))))))

;; dice-roll-text-2 is the display variant used in views.cljs for
;; advantage/disadvantage rolls. It returns a formatted string and
;; has special nat-20 detection logic.

(deftest dice-roll-text-2-returns-string
  (testing "returns a string for valid input"
    (is (string? (dice/dice-roll-text-2 "1d20+5"))))
  (testing "returns nil for invalid input"
    (is (nil? (dice/dice-roll-text-2 "not-dice")))))
