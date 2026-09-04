(ns orcpub.dnd.e5.builder-fields-test
  "Pure tests for the field-schema validators (fields->spec + validate-fields). JVM-runnable.
   These are the SINGLE validators the form, the save spec, and import/export verification share."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
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
    (let [s (bf/fields->spec fields)]   ; now a spec (spec/keys + field checks), not a bare predicate
      (is (spec/valid? s ok-item))
      (is (not (spec/valid? s (update ok-item :bw dissoc :damage-type))))   ; missing required
      (is (not (spec/valid? s (assoc-in ok-item [:bw :damage-type] :banana)))) ; bad enum
      (is (spec/valid? s (dissoc ok-item :note))))))                        ; missing optional ok

;; ── The shared :props fragments ───────────────────────────────────────────────────────────────
;; A form field that writes a path the :props compiler doesn't read is the silent failure here:
;; the builder looks right, saves fine, and produces no mechanical effect. These pin the two ends
;; against each other.

(def ^:private ac-spec (bf/fields->spec bf/ac-bonus-fields))
(def ^:private base {:name "X" :key :x :option-pack "Pack"})

(deftest ac-bonus-fields-validate-what-the-form-can-produce
  (testing "a Defense-shaped style: +1 AC while wearing armor"
    (is (spec/valid? ac-spec (assoc base :props {:ac-bonus {:ac-bonus 1 :armor? true}}))))
  (testing "the tags are optional — absent means either way"
    (is (spec/valid? ac-spec (assoc base :props {:ac-bonus {:ac-bonus 1}}))))
  (testing "and the whole fragment is optional, so existing content stays valid (D9)"
    (is (spec/valid? ac-spec base)))
  (testing "a non-number bonus is rejected"
    (is (not (spec/valid? ac-spec (assoc base :props {:ac-bonus {:ac-bonus "one"}})))))
  (testing "a tag value outside the declared options is rejected"
    (is (not (spec/valid? ac-spec (assoc base :props {:ac-bonus {:ac-bonus 1 :armor? "yes"}}))))))

(deftest ac-bonus-field-paths-match-what-the-props-compiler-reads
  (testing "every field path is [:props :ac-bonus <k>], and <k> is a key ac-bonus-modifiers reads.
            If these drift, the form writes data the compiler ignores and the feature silently
            does nothing."
    (doseq [{:keys [key]} bf/ac-bonus-fields]
      (is (= [:props :ac-bonus] (vec (take 2 key))) (str key " must live under :props :ac-bonus"))
      (is (contains? #{:ac-bonus :armor? :shield?} (last key))
          (str (last key) " must be a key the :ac-bonus prop compiler understands")))))
