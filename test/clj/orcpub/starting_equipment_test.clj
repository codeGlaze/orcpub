(ns orcpub.starting-equipment-test
  "Regression: a homebrew class expressed with the shorthand starting-equipment keys
   (:weapons/:armor/:equipment for fixed grants, :*-choices for choice groups) is
   consumed by opt5e/class-option with NO extra wiring — the same path SRD classes use.
   This underpins the class-builder starting-equipment UI (feat/starting-equipment):
   the UI only has to write these keys; runtime consumption is already in place."
  (:require [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.srd-starting-equipment :as srd]))

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

(deftest rich-equipment-selections-are-consumed
  ;; The full SRD form as serializable data: an option can grant a BUNDLE of items
  ;; and/or offer a nested sub-choice (Fighter's "(a) chain mail, or (b) leather +
  ;; longbow + 20 arrows" and "a martial weapon and a shield").
  (let [class-map {:name "Bundle Class" :key :bundle-class :hit-die 10
                   :equipment-selections
                   [{:name "Armor"
                     :options [{:name "Chain Mail" :grants [{:kind :armor :key :chain-mail}]}
                               {:name "Leather Armor, Longbow, 20 Arrows"
                                :grants [{:kind :armor :key :leather}
                                         {:kind :weapon :key :longbow}
                                         {:kind :equipment :key :arrow :qty 20}]}]}
                    {:name "Weapon"
                     :options [{:name "A martial weapon and a shield"
                                :grants [{:kind :armor :key :shield}]
                                :choose [{:name "Martial Weapon" :from :martial}]}]}]}
        result (opt/class-option {} {} {} {} weapons/weapons-map class-map)
        strings (set (collect string? result))
        ;; the bundle option, by its namespaced name key
        bundle-opt (first (collect #(and (map? %)
                                         (= "Leather Armor, Longbow, 20 Arrows"
                                            (:orcpub.template/name %)))
                                   result))]
    ;; both top-level choice groups compiled to starting-equipment selections
    (is (contains? strings "Starting Equipment: Armor"))
    (is (contains? strings "Starting Equipment: Weapon"))
    ;; the bundle option exists and carries all THREE grants as modifiers
    (is (= 3 (count (:orcpub.template/modifiers bundle-opt)))
        "bundle option grants leather + longbow + 20 arrows")
    ;; the nested sub-choice ("choose a martial weapon") compiled to its own selection
    (is (contains? strings "Starting Equipment: Martial Weapon")
        ":choose produced a nested martial-weapon selection")))

(deftest equipment-group-subchoice-compiles
  ;; A sub-choice over a grouped-equipment key (holy symbol / focus / instrument / pack)
  ;; expands to a pick among the group's members — the full SRD sub-choice vocabulary.
  (let [result (opt/class-option {} {} {} {} weapons/weapons-map
                                 {:name "Cleric-like" :key :cleric-like :hit-die 8
                                  :equipment-selections
                                  [{:name "Focus"
                                    :options [{:name "A holy symbol" :choose [{:from :holy-symbol}]}]}]})
        strings (set (collect string? result))]
    (is (contains? strings "Starting Equipment: Focus"))
    ;; The nested grouped-equipment pick mirrors the live equipment-option: named for the
    ;; group with NO "Starting Equipment: " prefix (so a class filled from an SRD class
    ;; reproduces the SRD's own nested selection name).
    (is (contains? strings "Holy Symbol")
        "equipment-group sub-choice compiled to a pick among holy symbols")))

(deftest edn-round-trip-then-consumed
  ;; The UI writes plain data (no fn-valued modifiers), so the class survives the
  ;; .orcbrew save/export/import round-trip (EDN) unchanged AND still applies.
  (let [round-tripped (edn/read-string (pr-str homebrew-class-with-equipment))
        equip (equip-quantities (opt/class-option {} {} {} {} weapons/weapons-map round-tripped))]
    (is (= homebrew-class-with-equipment round-tripped)
        "class + starting equipment is EDN-serializable and unchanged by round-trip")
    (is (= 4 (get equip :javelin))        "fixed weapon still granted after round-trip")
    (is (= 1 (get equip :explorers-pack)) "fixed equipment still granted after round-trip")))

;; ---------------------------------------------------------------------------
;; SRD equipment extraction: the srd-class-equipment data table must compile to
;; the same equipment the LIVE class produces (the live class is ground truth).
;; Signature = fixed grants (exact qty) + starting-equipment selection names +
;; the set of item-keys referenced anywhere in those selections (catches a wrong
;; item or a dropped/added option).
;; ---------------------------------------------------------------------------

;; top-level starting-equipment choice groups (direct children).
(defn- se-selections [built]
  (filter #(contains? (:orcpub.template/tags %) :starting-equipment)
          (:orcpub.template/selections built)))

;; EVERY starting-equipment selection name, nested sub-choices included. A nested
;; selection's name is user-visible and feeds its minted key, so a rename there is a
;; real divergence — not an "internal detail". (Comparing only top-level names hid a
;; "Starting Equipment: " prefix the recompiler added to grouped-focus picks.)
(defn- se-selection-names [built]
  (set (map :orcpub.template/name
            (collect #(and (map? %) (contains? (:orcpub.template/tags %) :starting-equipment))
                     built))))

;; item-key -> total quantity granted by CHOICE-option modifiers, recovered by applying
;; each modifier fn (the app's own mechanism). :choice-item-keys is a set and ignores
;; counts, so without this a dropped/mangled qty on a choice grant — e.g. Fighter's
;; "longbow + 20 arrows", where the 20 lives in a choice option, not a fixed grant —
;; slips through unnoticed.
(defn- choice-grant-qtys [built]
  (reduce
   (fn [acc m]
     (reduce (fn [a [k v]]
               (update a k (fnil + 0) (get v :orcpub.dnd.e5.character.equipment/quantity 1)))
             acc
             (get ((:orcpub.modifiers/fn m) {}) (:orcpub.modifiers/key m))))
   {}
   (collect #(and (map? %) (fn? (:orcpub.modifiers/fn %))
                  (#{:weapons :armor :equipment} (:orcpub.modifiers/key %)))
            (se-selections built))))

;; Pool/chooser keys ("any martial weapon", "an arcane focus", …) are selection
;; identifiers, not grantable items — a nested selection derives a name-key from them
;; that can collide with a real item key, so exclude them from the item-key signature.
(def ^:private chooser-keys
  #{:simple :martial :any-weapon
    :holy-symbol :arcane-focus :druidic-focus :musical-instrument :pack :artisans-tool :gaming-set})

(defn- item-key? [k]
  (boolean (and (not (chooser-keys k))
                (or (get weapons/weapons-map k) (get armor/armor-map k) (get equip/equipment-map k)))))

(defn- equipment-signature [built]
  {:fixed            (equip-quantities built)
   :selection-names  (se-selection-names built)
   :choice-item-keys (set (filter item-key? (mapcat #(collect keyword? %) (se-selections built))))
   :choice-qtys      (choice-grant-qtys built)})

(defn- live-class-option [class-kw]
  ;; call <class>-option in classes.cljc with inert spell/subclass/language args; the
  ;; equipment portion of the built option doesn't depend on them. (warlock takes 2 extra.)
  (let [f (ns-resolve 'orcpub.dnd.e5.classes (symbol (str (name class-kw) "-option")))]
    (if (= :warlock class-kw)
      (f {} {} {} {} weapons/weapons-map {} {})
      (f {} {} {} {} weapons/weapons-map))))

(defn- signature-of [equip]
  (equipment-signature (opt/class-option {} {} {} {} weapons/weapons-map
                                         (merge {:name "Probe" :key :probe :hit-die 8} equip))))

(deftest srd-equipment-decompile-matches-live
  ;; builder-equipment DECOMPILES each live class into serializable :equipment-selections;
  ;; recompiling that must reproduce the live class's equipment (round-trip), for all 12.
  (doseq [class-kw srd/srd-class-keys]
    (let [live (equipment-signature (live-class-option class-kw))
          b    (signature-of (srd/builder-equipment class-kw))]
      (is (= (:fixed live) (:fixed b))                       (str class-kw " — fixed grants round-trip"))
      (is (= (:selection-names live) (:selection-names b))   (str class-kw " — selection names round-trip"))
      (is (= (:choice-item-keys live) (:choice-item-keys b)) (str class-kw " — item keys round-trip"))
      (is (= (:choice-qtys live) (:choice-qtys b))           (str class-kw " — choice grant quantities round-trip")))))
