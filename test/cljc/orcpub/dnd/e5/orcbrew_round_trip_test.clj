(ns orcpub.dnd.e5.orcbrew-round-trip-test
  "CHARACTERIZATION: a homebrew content pack (.orcbrew) survives export -> import losslessly.
   Export is literally `(str plugin)` -> a .orcbrew file (events.cljs:3651); import parses it back
   via validate-import, with the parsed content at `(:data result)` (events.cljs:3889). This pins the
   SERIALIZATION-FIDELITY half: the EDN round-trip is identity for the pack's data. Uses the REAL
   fixture test/extensibility-fixtures.orcbrew (a genuine exported pack).

   Out of scope here (covered elsewhere): the validate-import TRANSFORMS — dedup / auto-clean /
   key-reconciliation (cljs import-validation-test, content-reconciliation-test) — and build-after-import
   (draconic-ancestry-test). JVM/.clj so slurp + clojure.edn stay off the cljs path and it runs under
   the `lein test` gate.

   NOTE on (str m): for a Clojure map, (str m) == (pr-str m) (both emit the print-readable EDN form,
   strings quoted), so using either to mirror export is equivalent."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.edn :as edn]))

(def fixture (edn/read-string (slurp "test/extensibility-fixtures.orcbrew")))

(deftest fixture-is-a-real-pack
  (testing "the fixture is a non-trivial plugins map, so the round-trip below isn't vacuous"
    (is (map? fixture))
    (is (contains? fixture :orcpub.dnd.e5/subraces))
    (is (= "Starlit Elf" (get-in fixture [:orcpub.dnd.e5/subraces :starlit-elf :name])))
    (is (contains? fixture :orcpub.dnd.e5/subclasses))
    (is (contains? fixture :orcpub.dnd.e5/boons))))

(deftest orcbrew-edn-round-trips-losslessly
  (testing "export (str) -> import (edn read) is identity for the real pack"
    (let [once  (edn/read-string (str fixture))
          twice (edn/read-string (str once))]
      (is (= fixture once) "one round-trip is lossless")
      (is (= once twice)   "idempotent")))
  (testing "a representative RICH homebrew class round-trips (the data shapes a real .orcbrew carries)"
    ;; namespaced content key, namespaced ability keyword, nested :props map, vector of level-modifiers
    (let [pack {:orcpub.dnd.e5/classes
                {:tinkerer
                 {:key :tinkerer :name "Tinkerer" :option-pack "P"
                  :props {:damage-resistance {:fire true :cold true} :speed 10 :skill-prof {:arcana true}}
                  :level-modifiers [{:level 3 :type :damage-resistance :value :fire}
                                    {:level 1 :type :weapon-prof :value :longsword}]
                  :spellcasting {:level-factor 2 :ability :orcpub.dnd.e5.character/int :known-mode :schedule}}}}]
      (is (= pack (edn/read-string (str pack))) "props + level-modifiers + spellcasting survive verbatim"))))
