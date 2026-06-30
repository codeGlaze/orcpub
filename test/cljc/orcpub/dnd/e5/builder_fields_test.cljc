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

;; ─── Boolean toggle hardening: malformed (nil/garbage) values can't survive a click ──────────────
(deftest toggle-next-is-always-a-literal-boolean
  (testing "from ANY prior value — nil, false, true, a string, 0 — the next value is a real boolean"
    (doseq [v [nil false true "false" "true" 0 :x]]
      (is (boolean? (bf/toggle-next v)) (str "toggle-next of " (pr-str v) " is a boolean"))))
  (testing "semantics: only literal true is 'on' (so nil/garbage read as off and turn ON next)"
    (is (= false (bf/toggle-next true)))
    (is (= true  (bf/toggle-next false)))
    (is (= true  (bf/toggle-next nil)))      ; absent/never-set -> off -> turns on
    (is (= true  (bf/toggle-next "false"))))) ; a stray string is NOT 'on'

(deftest toggle-never-produces-nil-over-a-click-sequence
  (testing "hammering the toggle (incl. starting from a malformed nil) never yields nil; always bool"
    (loop [v nil, clicks 50]
      (when (pos? clicks)
        (let [nxt (bf/toggle-next v)]
          (is (boolean? nxt) "every intermediate value is a real boolean, never nil")
          (recur nxt (dec clicks)))))))

(deftest boolean-field-validation
  (testing ":boolean field — a present NON-nil value must be a real boolean; nil/absent = off (fine)"
    (let [bfields [{:key :flag :type :boolean :label "Flag"}]]
      (is (empty? (bf/validate-fields bfields {:flag true})))
      (is (empty? (bf/validate-fields bfields {:flag false})))
      (is (empty? (bf/validate-fields bfields {})) "absent boolean = off, not a problem")
      (is (empty? (bf/validate-fields bfields {:flag nil})) "nil = absent = off (read coerces); not flagged")
      (is (seq (bf/validate-fields bfields {:flag "true"})) "a stringy boolean (genuine garbage) is rejected")
      (is (seq (bf/validate-fields bfields {:flag 1})) "a numeric value is rejected"))))
