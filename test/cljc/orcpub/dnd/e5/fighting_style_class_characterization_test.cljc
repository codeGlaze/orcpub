(ns orcpub.dnd.e5.fighting-style-class-characterization-test
  "D34 characterization gate: pins what fighting-style-selection offers each class TODAY,
   before homebrew eligibility is threaded in. The homebrew change must keep every
   assertion here green (with no homebrew present, per-class output is unchanged) — that
   is the 'uniformity without blind regression' guarantee."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.options :as opt5e]))

(defn- offered-keys [sel] (set (map ::t/key (::t/options sel))))

(def paladin-set #{:defense :dueling :great-weapon-fighting :protection})
(def ranger-set  #{:archery :defense :dueling :two-weapon-fighting})

(deftest fighter-offers-every-built-in-style
  (is (= (set (map ::t/key opt5e/fighting-style-options))
         (offered-keys (opt5e/fighting-style-selection :fighter)))
      "Fighter is unrestricted — offers every built-in fighting style"))

(deftest paladin-and-ranger-offer-exactly-their-restricted-sets
  (is (= paladin-set (offered-keys (opt5e/fighting-style-selection :paladin paladin-set)))
      "Paladin offers exactly its whitelisted styles")
  (is (= ranger-set (offered-keys (opt5e/fighting-style-selection :ranger ranger-set)))
      "Ranger offers exactly its whitelisted styles"))

(deftest the-selection-carries-its-load-bearing-ref-and-tag
  (testing "a class's own fighting-style selection is top-level and stores the pick by :ref"
    (let [sel (opt5e/fighting-style-selection :fighter)]
      (is (= [:class :fighter :fighting-style] (::t/ref sel))
          "the :ref is where the character stores the chosen style — must survive the change")
      (is (contains? (::t/tags sel) :class) "carries the :class tag"))))
