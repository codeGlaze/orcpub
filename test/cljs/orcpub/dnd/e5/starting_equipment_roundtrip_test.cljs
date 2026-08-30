(ns orcpub.dnd.e5.starting-equipment-roundtrip-test
  "End-to-end: a homebrew class's starting equipment survives a real .orcbrew
   export -> re-import cycle AND still applies afterward. Exercises the actual
   export transform (strip-export-blanks), the .orcbrew text (pr-str), the real
   import processor (validate-import), and consumption (class-option). Several
   configs: fixed-only, choices with pseudo-keys, mixed multi-group, multi-item."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [clojure.walk :as walk]
            [orcpub.dnd.e5.orcbrew-validation :as val]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.weapons :as weapons]))

(def ^:private equipment-keys
  [:weapons :armor :equipment :weapon-choices :armor-choices :equipment-choices])

(defn- orcbrew-roundtrip
  "content-map -> strip blanks -> .orcbrew text -> import processor -> :data"
  [content]
  (-> content
      val/strip-export-blanks
      pr-str
      (val/validate-import {:strategy :progressive
                            :auto-clean true
                            :import-source-name "Roundtrip Source"})
      :data))

(defn- imported-class [data class-key]
  (some (fn [m] (when (map? m) (get-in m [:orcpub.dnd.e5/classes class-key])))
        (tree-seq coll? seq data)))

(defn- collect [pred x]
  (let [found (atom [])]
    (walk/postwalk (fn [n] (when (pred n) (swap! found conj n)) n) x)
    @found))

(defn- granted-quantities [class-map]
  "item-key -> qty for every class-starting-equipment entry class-option emits."
  (let [built (opt/class-option {} {} {} {} weapons/weapons-map class-map)]
    (into {}
          (map (juxt :orcpub.entity/key
                     #(get-in % [:orcpub.entity/value
                                 :orcpub.dnd.e5.character.equipment/quantity])))
          (collect #(and (map? %)
                         (get-in % [:orcpub.entity/value
                                    :orcpub.dnd.e5.character.equipment/class-starting-equipment?]))
                   built))))

(defn- selection-names [class-map]
  (set (collect string? (opt/class-option {} {} {} {} weapons/weapons-map class-map))))

(defn- class-with [equipment]
  ;; :option-pack is required by ::homebrew-class (reg-save-homebrew adds it from the
  ;; Option Source field); without it the import legitimately skips the class.
  (merge {:name "Roundtrip Class" :key :roundtrip-class :hit-die 10
          :option-pack "Roundtrip Source"}
         equipment))

;; A single-source .orcbrew's content: {content-type {item-key item}}.
(defn- orcbrew-content [class-map]
  {:orcpub.dnd.e5/classes {:roundtrip-class class-map}})

(defn- check-roundtrip [equipment]
  (let [original (class-with equipment)
        imported (imported-class (orcbrew-roundtrip (orcbrew-content original))
                                 :roundtrip-class)]
    (is (some? imported) "class survived the export/import round-trip")
    ;; the shorthand equipment keys are byte-for-byte preserved
    (is (= (select-keys original equipment-keys)
           (select-keys imported equipment-keys))
        "starting-equipment keys unchanged by export + import")
    imported))

(deftest fixed-only-round-trips
  (let [imported (check-roundtrip {:weapons {:javelin 4}
                                   :equipment {:spellbook 1 :explorers-pack 1}})]
    (let [q (granted-quantities imported)]
      (is (= 4 (get q :javelin)))
      (is (= 1 (get q :spellbook)))
      (is (= 1 (get q :explorers-pack))))))

(deftest choices-with-pseudo-keys-round-trip
  (let [imported (check-roundtrip
                  {:weapon-choices [{:name "Any Martial Weapon" :options {:martial 1}}
                                    {:name "Two Handaxes or a Simple Weapon"
                                     :options {:handaxe 2 :simple 1}}]})]
    (is (contains? (selection-names imported) "Starting Equipment: Any Martial Weapon"))
    (is (contains? (selection-names imported) "Starting Equipment: Two Handaxes or a Simple Weapon"))))

(deftest mixed-multi-group-round-trips
  (let [imported (check-roundtrip
                  {:weapons {:dagger 2}
                   :armor {:leather 1}
                   :equipment {:thieves-tools 1}
                   :weapon-choices [{:name "Rapier or Shortsword"
                                     :options {:rapier 1 :shortsword 1}}]
                   :armor-choices [{:name "Shield or nothing" :options {:shield 1}}]
                   :equipment-choices [{:name "A Pack"
                                        :options {:burglars-pack 1 :explorers-pack 1}}]})]
    (let [q (granted-quantities imported)]
      (is (= 2 (get q :dagger)))
      (is (= 1 (get q :leather)))
      (is (= 1 (get q :thieves-tools))))
    (is (contains? (selection-names imported) "Starting Equipment: Rapier or Shortsword"))
    (is (contains? (selection-names imported) "Starting Equipment: A Pack"))))
