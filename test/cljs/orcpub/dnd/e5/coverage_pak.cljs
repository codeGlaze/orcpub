(ns orcpub.dnd.e5.coverage-pak
  "test/fixtures/coverage-pak.orcbrew, parsed by the app's import parser and converted the way the
   app converts homebrew, for tests that need real homebrew content."
  (:require [orcpub.fixtures :refer-macros [fixture-text]]
            [orcpub.common :as common]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]
            [orcpub.dnd.e5.spell-subs :as spell-subs]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spells :as spells5e]))

(def source "Coverage Pak")

(def pak
  (delay (get (:data (orcbrew-val/parse-edn (fixture-text "test/fixtures/coverage-pak.orcbrew")))
              source)))

(defn selection-map [] (common/map-by-key (vals (:orcpub.dnd.e5/selections @pak))))

(defn plugin-subclasses-map
  "The pack's subclasses by class, as `spell-subs/plugin-subclass` builds them for class options."
  []
  (group-by :class
            (for [[k subclass] (:orcpub.dnd.e5/subclasses @pak)]
              (spell-subs/plugin-subclass sl5e/spell-lists spells5e/spell-map (selection-map)
                                          source k subclass))))
