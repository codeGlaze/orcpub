(ns orcpub.dnd.e5.options-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.spec.alpha :as spec]
            [clojure.data :refer [diff]]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.entity :as entity]))

(deftest test-total-slots
  (is (= {1 2} (opt/total-slots 3 3)))
  (is (= {1 4
          2 3
          3 3
          4 1}
         (opt/total-slots 20 3))))

(deftest test-rage-modifiers
  (testing "Rage with uses > 0 creates modifiers"
    (let [rage-cfg {:uses 2 :damage 2}
          result (opt/rage-modifiers rage-cfg :test-class)]
      (is (seq result) "Should return a sequence of modifiers")
      (is (= 5 (count result)) "Should have 5 modifiers: bonus-action + 3 resistances + 1 save advantage")))

  (testing "Rage with uses = 0 returns nil"
    (let [rage-cfg {:uses 0 :damage 2}
          result (opt/rage-modifiers rage-cfg :test-class)]
      (is (nil? result) "Should return nil when uses is 0")))

  (testing "Rage with nil config returns nil"
    (let [result (opt/rage-modifiers nil :test-class)]
      (is (nil? result) "Should return nil when config is nil")))

  (testing "Rage with missing uses returns nil"
    (let [rage-cfg {:damage 2}
          result (opt/rage-modifiers rage-cfg :test-class)]
      (is (nil? result) "Should return nil when uses is missing")))

  (testing "Rage uses default damage value if not specified"
    (let [rage-cfg {:uses 3}
          result (opt/rage-modifiers rage-cfg :test-class)]
      (is (seq result) "Should return modifiers with default damage value"))))

(deftest test-make-feat-modifiers-rage
  (testing "make-feat-modifiers handles :rage case"
    (let [rage-cfg {:uses 2 :damage 3}
          result (opt/make-feat-modifiers :rage rage-cfg :test-class)]
      (is (seq result) "Should return modifiers for :rage key")
      (is (= 5 (count result)) "Should have 5 modifiers")))

  (testing "make-feat-modifiers returns nil for :rage with 0 uses"
    (let [rage-cfg {:uses 0 :damage 2}
          result (opt/make-feat-modifiers :rage rage-cfg :test-class)]
      (is (nil? result) "Should return nil when rage has 0 uses"))))
