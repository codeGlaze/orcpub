(ns orcpub.starting-equipment-test
  "Regression: a homebrew class expressed with the shorthand starting-equipment keys
   (:weapons/:armor/:equipment for fixed grants, :*-choices for choice groups) is
   consumed by opt5e/class-option with NO extra wiring — the same path SRD classes use.
   This underpins the class-builder starting-equipment UI (feat/starting-equipment):
   the UI only has to write these keys; runtime consumption is already in place."
  (:require [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.weapons :as weapons]))

(defn- collect [pred x]
  (let [found (atom [])]
    (walk/postwalk (fn [n] (when (pred n) (swap! found conj n)) n) x)
    @found))

;; The exact shape the class-builder starting-equipment UI writes onto the class map.
(def ^:private homebrew-class-with-equipment
  {:name "Test Homebrew Class"
   :key :test-homebrew-class
   :hit-die 8
   :weapons   {:javelin 4}
   :equipment {:explorers-pack 1}
   :weapon-choices [{:name "Martial Weapon" :options {:greataxe 1 :martial 1}}]})

(defn- equip-quantities [built]
  "item-key -> quantity, for every class-starting-equipment entry in a built option."
  (into {}
        (map (juxt :orcpub.entity/key
                   #(get-in % [:orcpub.entity/value
                               :orcpub.dnd.e5.character.equipment/quantity])))
        (collect #(and (map? %)
                       (get-in % [:orcpub.entity/value
                                  :orcpub.dnd.e5.character.equipment/class-starting-equipment?]))
                 built)))

(deftest shorthand-starting-equipment-is-consumed
  (let [result (opt/class-option {} {} {} {} weapons/weapons-map
                                 homebrew-class-with-equipment)
        equip (equip-quantities result)
        selection-names (set (collect string? result))]
    ;; fixed grants land as flagged, quantified equipment entries
    (is (= 4 (get equip :javelin))        "fixed :weapons {:javelin 4} granted with qty")
    (is (= 1 (get equip :explorers-pack)) "fixed :equipment {:explorers-pack 1} granted with qty")
    ;; the choice group becomes a starting-equipment selection
    (is (contains? selection-names "Starting Equipment: Martial Weapon")
        ":weapon-choices produced a starting-equipment selection")))

(deftest edn-round-trip-then-consumed
  ;; The UI writes plain data (no fn-valued modifiers), so the class survives the
  ;; .orcbrew save/export/import round-trip (EDN) unchanged AND still applies.
  (let [round-tripped (edn/read-string (pr-str homebrew-class-with-equipment))
        equip (equip-quantities (opt/class-option {} {} {} {} weapons/weapons-map round-tripped))]
    (is (= homebrew-class-with-equipment round-tripped)
        "class + starting equipment is EDN-serializable and unchanged by round-trip")
    (is (= 4 (get equip :javelin))        "fixed weapon still granted after round-trip")
    (is (= 1 (get equip :explorers-pack)) "fixed equipment still granted after round-trip")))
