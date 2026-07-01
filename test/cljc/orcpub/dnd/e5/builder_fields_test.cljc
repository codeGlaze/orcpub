(ns orcpub.dnd.e5.builder-fields-test
  "Pure tests for the field-schema validators (fields->spec + validate-fields). JVM-runnable.
   These are the SINGLE validators the form, the save spec, and import/export verification share."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.builder-fields :as bf]))

(def fields
  [{:key [:bw :damage-type] :type :enum :required? true :label "Damage Type"
    :options [{:value :acid} {:value :fire}]}
   {:key [:bw :width] :type :number :label "Width"}                 ; optional
   {:key :note :type :text :label "Note"}])                         ; optional, top-level

(def ok-item {:name "X" :key :x :option-pack "P" :bw {:damage-type :acid}})

(deftest validate-fields-flags-only-real-problems
  (testing "a valid item (required present, no bad values) → no problems"
    (is (empty? (bf/validate-fields fields ok-item))))
  (testing "missing a REQUIRED field → one labeled problem"
    (is (= ["Damage Type is required"]
           (bf/validate-fields fields (update ok-item :bw dissoc :damage-type)))))
  (testing "missing an OPTIONAL field → no problem (optional-by-default)"
    (is (empty? (bf/validate-fields fields (dissoc ok-item :note)))))
  (testing "a bad enum value → invalid-value problem"
    (is (= ["Damage Type has an invalid value"]
           (bf/validate-fields fields (assoc-in ok-item [:bw :damage-type] :banana)))))
  (testing "a bad number value → invalid-value problem"
    (is (seq (bf/validate-fields fields (assoc-in ok-item [:bw :width] "wide"))))))

(deftest fields->spec-matches-validate-fields
  (testing "the generated spec agrees with validate-fields on valid/invalid"
    (let [spec-pred (bf/fields->spec fields)]
      (is (spec-pred ok-item))
      (is (not (spec-pred (update ok-item :bw dissoc :damage-type))))   ; missing required
      (is (not (spec-pred (assoc-in ok-item [:bw :damage-type] :banana))) ) ; bad enum
      (is (spec-pred (dissoc ok-item :note))))))                        ; missing optional ok
