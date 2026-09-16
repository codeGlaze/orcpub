(ns orcpub.dnd.e5.cr-label-test
  "Unit tests for the CR display pair (plan-cr-normalization.md step 1).

   No callers yet — the two existing formatters in views.cljs are replaced at step 3. These
   pin the pair's own behaviour so that replacement is a swap rather than a rewrite."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.monsters :as monsters5e]))

(deftest labels-match-how-the-books-print-cr
  (testing "fractions"
    (is (= "1/8" (disp/cr->label 0.125)))
    (is (= "1/4" (disp/cr->label 0.25)))
    (is (= "1/2" (disp/cr->label 0.5))))
  (testing "whole numbers carry no decimal point"
    (is (= "0" (disp/cr->label 0)))
    (is (= "1" (disp/cr->label 1.0)))
    (is (= "2" (disp/cr->label 2)))
    (is (= "30" (disp/cr->label 30.0)))))

(deftest cr->label-reads-the-data-before-and-after-the-conversion
  (testing "the SRD data is ratio-valued today and becomes doubles at step 2; the same
            formatter has to serve both, or step 3 could not land independently"
    (is (= (mapv disp/cr->label [(/ 1 8) (/ 1 4) (/ 1 2) 1 2])
           (mapv disp/cr->label [0.125 0.25 0.5 1.0 2.0])
           ["1/8" "1/4" "1/2" "1" "2"]))))

(deftest label->cr-parses-back
  (is (= 0.125 (disp/label->cr "1/8")))
  (is (= 0.25 (disp/label->cr "1/4")))
  (is (= 0.5 (disp/label->cr "1/2")))
  (is (= 0.0 (disp/label->cr "0")))
  (is (= 7.0 (disp/label->cr "7")))
  (testing "a number passes through, so calling it twice is safe"
    (is (= 0.25 (disp/label->cr 0.25)))
    (is (= 0.25 (disp/label->cr (disp/label->cr "1/4"))))))

(deftest every-srd-cr-round-trips
  (testing "all 28 distinct CRs in the monster data survive label -> cr -> label"
    (let [crs (distinct (map :challenge monsters5e/monsters-raw))]
      (is (= 28 (count crs)))
      (is (every? #(= (double %) (disp/label->cr (disp/cr->label %))) crs)))))

(deftest unreadable-input-gives-nil-rather-than-throwing
  (testing "the callers are a text field and an import, so neither may blow up"
    (doseq [x [nil "" "abc" "1/0" :keyword [1]]]
      (is (nil? (disp/cr->label x)) (str "cr->label " (pr-str x)))
      (is (nil? (disp/label->cr x)) (str "label->cr " (pr-str x))))))
