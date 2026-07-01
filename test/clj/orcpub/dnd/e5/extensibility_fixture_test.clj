(ns orcpub.dnd.e5.extensibility-fixture-test
  "Validates test/extensibility-fixtures.orcbrew so the live/e2e runner can import it
   to drive the subrace / subclass / pact-boon / eldritch-invocation builder checks
   (items 2-5 and 9 of docs/kb/content-extensibility-e2e.md), which the existing
   test/*.orcbrew fixtures couldn't cover. Also guards the fixture against rot: if a
   spec changes, this fails instead of the e2e runner wasting a session on a bad import."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.content-types :as ct]
            ;; side effect: register the homebrew-* specs the fixture is validated against
            [orcpub.dnd.e5.classes]
            [orcpub.dnd.e5.races]))

(def fixture
  (edn/read-string (slurp "test/extensibility-fixtures.orcbrew")))

(def spec-by-plugin-key
  (into {} (map (juxt :plugin-key :spec)) ct/content-types))

(deftest fixture-covers-the-e2e-gap-content
  (testing "supplies exactly the content the existing .orcbrew fixtures were missing"
    (is (contains? fixture :orcpub.dnd.e5/subraces))
    (is (contains? fixture :orcpub.dnd.e5/subclasses))
    (is (contains? fixture :orcpub.dnd.e5/boons))
    (is (contains? fixture :orcpub.dnd.e5/invocations))))

(deftest fixture-items-validate-against-registry-specs
  (testing "every fixture item satisfies the spec the content-types registry maps to it"
    (doseq [[plugin-key items] fixture
            [item-key item] items]
      (let [s (spec-by-plugin-key plugin-key)]
        (is (some? s) (str "registry should know plugin-key " plugin-key))
        (is (spec/valid? s item)
            (str item-key " must satisfy " s " — " (spec/explain-str s item)))))))

(deftest subrace-and-subclass-target-builtin-parents
  (testing "parents are built-in (SRD) so the e2e runner verifies injection under SRD
            races/classes, not homebrew ones (item 2/3 caveats)"
    (is (= :elf (get-in fixture [:orcpub.dnd.e5/subraces :starlit-elf :race])))
    (is (= :sorcerer (get-in fixture [:orcpub.dnd.e5/subclasses :storm-soul :class])))))
