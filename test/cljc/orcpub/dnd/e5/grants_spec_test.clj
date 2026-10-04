(ns orcpub.dnd.e5.grants-spec-test
  "`:grants` arrives in .orcbrew files users trade, so it is untrusted input. Every other authored
   shape has a spec; this one had none until now, and a malformed grant surfaced as a nil
   somewhere downstream instead of at import."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.grants :as grants]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.races :as races]))

(deftest the-four-shapes-the-compilers-read-are-valid
  (testing "fixed — the entry's modifiers, no pick"
    (is (spec/valid? ::grants/grants [{:pool :skills :key :athletics}])))
  (testing "choice over the whole pool"
    (is (spec/valid? ::grants/grants [{:pool :skills :count 2}])))
  (testing "choice, narrowed by filter"
    (is (spec/valid? ::grants/grants [{:pool :skills :count 2 :filter #{:athletics :stealth}}])))
  (testing "bare pool — :count defaults to 1 in grant-selection"
    (is (spec/valid? ::grants/grants [{:pool :skills}])))
  (testing "several grants in one vector"
    (is (spec/valid? ::grants/grants [{:pool :skills :key :athletics}
                                      {:pool :languages :count 2}]))))

(deftest an-unregistered-pool-is-not-a-spec-error
  (testing "shape, never membership — unknown pools drop at compile time on purpose, which is
            the seam that lets a pack from a newer build load with its unknown grants ignored"
    (is (spec/valid? ::grants/grants [{:pool :pool-from-a-future-version :key :whatever}]))))

(deftest malformed-grants-are-rejected
  (testing ":key and :count together is contradictory — grant-selection returns nil when :key is
            present, so the :count would be silently ignored"
    (is (not (spec/valid? ::grants/grants [{:pool :skills :key :athletics :count 2}]))))
  (testing "a grant with no pool names nothing"
    (is (not (spec/valid? ::grants/grants [{:key :athletics}]))))
  (testing ":count must be positive"
    (is (not (spec/valid? ::grants/grants [{:pool :skills :count 0}]))))
  (testing ":grants is a vector — one key, always a vector (D33-era decision)"
    (is (not (spec/valid? ::grants/grants (list {:pool :skills})))))
  (testing ":filter is a non-empty set of keywords"
    (is (not (spec/valid? ::grants/grants [{:pool :skills :count 1 :filter [:athletics]}])))
    (is (not (spec/valid? ::grants/grants [{:pool :skills :count 1 :filter #{}}])))))

(deftest the-silos-that-compile-grants-now-validate-them
  (testing "a feat carrying a well-formed grant validates"
    (is (spec/valid? ::feats/homebrew-feat
                     {:name "Grantful" :key :grantful :option-pack "Test"
                      :grants [{:pool :skills :key :athletics}]})))
  (testing "…and a malformed one does not"
    (is (not (spec/valid? ::feats/homebrew-feat
                          {:name "Grantful" :key :grantful :option-pack "Test"
                           :grants [{:key :athletics}]}))))
  (testing "same for a race"
    (is (spec/valid? ::races/homebrew-race
                     {:name "Grantfolk" :key :grantfolk :option-pack "Test"
                      :grants [{:pool :languages :count 2}]}))
    (is (not (spec/valid? ::races/homebrew-race
                          {:name "Grantfolk" :key :grantfolk :option-pack "Test"
                           :grants [{:pool :languages :count 0}]}))))
  (testing "and content with no :grants is unaffected"
    (is (spec/valid? ::feats/homebrew-feat
                     {:name "Plain" :key :plain :option-pack "Test"}))))
