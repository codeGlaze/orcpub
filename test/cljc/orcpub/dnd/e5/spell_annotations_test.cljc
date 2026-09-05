(ns orcpub.dnd.e5.spell-annotations-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.spell-annotations :as ann]))

(deftest concentration-is-read-from-the-duration
  ;; 5e gives concentration no field of its own; it is the first word of the
  ;; duration, so anything that looks only for a flag finds nothing.
  (testing "a concentration duration"
    (is (ann/concentration? {:duration "Concentration, up to 1 minute"}))
    (is (ann/concentration? {:duration "concentration, up to 10 minutes"})))
  (testing "anything else"
    (is (not (ann/concentration? {:duration "Instantaneous"})))
    (is (not (ann/concentration? {:duration "8 hours"})))
    (is (not (ann/concentration? {:duration "Until dispelled"})))
    (is (not (ann/concentration? {})))
    (is (not (ann/concentration? {:duration nil})))))

(deftest casting-tag-marks-only-what-changes-the-turn
  (testing "bonus action and reaction"
    (is (= "BA" (ann/casting-tag {:casting-time "1 bonus action"})))
    (is (= "RE" (ann/casting-tag {:casting-time "1 reaction, in response to a spell"}))))
  (testing "an ordinary or long casting time is not marked"
    (is (nil? (ann/casting-tag {:casting-time "1 action"})))
    (is (nil? (ann/casting-tag {:casting-time "10 minutes"})))
    (is (nil? (ann/casting-tag {:casting-time "8 hours"})))
    (is (nil? (ann/casting-tag {})))))

(deftest material-cost-takes-the-price-out-of-the-prose
  (testing "the figure, with its comma and without the space"
    (is (= "100gp" (ann/material-cost
                    {:components {:material-component
                                  "diamond dust worth at least 100 gp, which the spell consumes"}})))
    (is (= "5,000gp" (ann/material-cost
                      {:components {:material-component
                                    "a powder of diamond and ruby dust worth at least 5,000 gp"}})))
    (is (= "25gp" (ann/material-cost
                   {:components {:material-component
                                 "specially marked sticks or bones worth at least 25 gp"}}))))
  (testing "a material with no price is not a cost"
    (is (nil? (ann/material-cost {:components {:material-component "a pinch of soot and salt"}})))
    (is (nil? (ann/material-cost {:components {:verbal true :somatic true}})))
    (is (nil? (ann/material-cost {})))))

(deftest annotation-is-nil-when-there-is-nothing-to-say
  ;; Two thirds of rows carry no mark, and drawing is per row, so the caller
  ;; needs to be able to skip a row outright rather than test three falses.
  (testing "an unremarkable spell"
    (is (nil? (ann/annotation {:duration "Instantaneous" :casting-time "1 action"}))))
  (testing "a spell with all three"
    (is (= {:concentration? true :tag "BA" :material "300gp"}
           (ann/annotation {:duration "Concentration, up to 1 hour"
                            :casting-time "1 bonus action"
                            :components {:material-component "a diamond worth 300 gp"}}))))
  (testing "only what applies"
    (is (= {:concentration? true} (ann/annotation {:duration "Concentration, up to 1 minute"})))))
