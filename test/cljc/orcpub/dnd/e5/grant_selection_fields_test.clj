(ns orcpub.dnd.e5.grant-selection-fields-test
  "grant-selection builds a selection-cfg and used to drop four of its fields: :key, :order,
   :prereq-fn, and a :min apart from :max. Plan: plan-hidden-pick-fix-and-grant-fields.md, Part B."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.template :as t]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.grants :as grants]
            [orcpub.dnd.e5.grant-pools :as gp]))

(def ^:private pools (gp/assemble []))

(defn- sel [grant] (opt5e/grant-selection grant pools))

(deftest a-grant-without-the-fields-compiles-as-before
  (let [s (sel {:pool :skills :count 2})]
    (is (= 2 (::t/min s)))
    (is (= 2 (::t/max s)))
    (is (nil? (::t/order s)))
    (is (nil? (::t/prereq-fn s)))
    (is (= (::t/key (sel {:pool :skills})) (::t/key s)) "the key still derives from the pool's name")))

(deftest each-field-reaches-the-selection
  (testing ":min apart from :count: choose up to two"
    (let [s (sel {:pool :skills :count 2 :min 0})]
      (is (= 0 (::t/min s)))
      (is (= 2 (::t/max s)))))
  (testing ":order"
    (is (= 5 (::t/order (sel {:pool :skills :count 1 :order 5})))))
  (testing ":selection-key, so a converted built-in keeps the address its picks are stored at"
    (is (= :skill-proficiency (::t/key (sel {:pool :skills :selection-key :skill-proficiency})))))
  (testing ":prereq-fn"
    (let [gate (fn [_] false)]
      (is (identical? gate (::t/prereq-fn (sel {:pool :skills :prereq-fn gate})))))))

(deftest compile-grants-passes-them-through
  (let [gate (fn [_] true)
        [s] (:selections (opt5e/compile-grants
                          [{:pool :tools :count 2 :min 1 :order 3 :prereq-fn gate
                            :selection-key :tool-proficiency}]
                          pools))]
    (is (= [1 2 3 :tool-proficiency] [(::t/min s) (::t/max s) (::t/order s) (::t/key s)]))
    (is (identical? gate (::t/prereq-fn s)))))

(deftest the-spec-takes-the-data-fields-and-refuses-the-internal-ones
  (testing ":min and :order are authored data"
    (is (spec/valid? ::grants/grants [{:pool :skills :count 2 :min 0 :order 1}]))
    (is (spec/valid? ::grants/grants [{:pool :skills :min 1}]) ":count defaults to 1"))
  (testing ":min above :count, or negative, is malformed"
    (is (not (spec/valid? ::grants/grants [{:pool :skills :count 1 :min 2}])))
    (is (not (spec/valid? ::grants/grants [{:pool :skills :count 2 :min -1}]))))
  (testing "a file may not carry the internal fields"
    (is (not (spec/valid? ::grants/grants [{:pool :skills :selection-key :skill-proficiency}])))
    (is (not (spec/valid? ::grants/grants [{:pool :skills :key :athletics
                                            :selection-key :skill-proficiency}])))))
