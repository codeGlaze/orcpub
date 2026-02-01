(ns orcpub.dnd.e5.template-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapon5e]))

;; This test constructs the full D&D 5e template -- the same data
;; structure the app builds on startup. If any class, race, or option
;; definition in the CLJC source files has a structural error (wrong
;; arity call, nil where a map is expected, bad keyword, etc.), this
;; test will throw rather than silently producing a broken UI.
;;
;; The template construction pulls in ~10,000 lines across classes.cljc,
;; options.cljc, template.cljc, weapons.cljc, armor.cljc, equipment.cljc,
;; spell-lists.cljc, and spells.cljc.

;; --- Static data available from CLJC ---

(def language-map
  (common/map-by-key
    [{:name "Common"      :key :common}
     {:name "Dwarvish"    :key :dwarvish}
     {:name "Elvish"      :key :elvish}
     {:name "Giant"       :key :giant}
     {:name "Gnomish"     :key :gnomish}
     {:name "Goblin"      :key :goblin}
     {:name "Halfling"    :key :halfling}
     {:name "Orc"         :key :orc}
     {:name "Abyssal"     :key :abyssal}
     {:name "Celestial"   :key :celestial}
     {:name "Draconic"    :key :draconic}
     {:name "Deep Speech" :key :deep-speech}
     {:name "Infernal"    :key :infernal}
     {:name "Primordial"  :key :primordial}
     {:name "Sylvan"      :key :sylvan}
     {:name "Undercommon" :key :undercommon}]))

(def spell-lists sl5e/spell-lists)

(def spells-map spells5e/spell-map)

(def weapons-map weapon5e/weapons-map)

;; --- Class construction (mirrors spell_subs.cljs:861-873) ---

(defn base-class-options
  "Construct all 12 PHB class options from static CLJC data.
  This is the JVM equivalent of the base-class-options fn in spell_subs.cljs."
  []
  [(classes5e/barbarian-option spell-lists spells-map {} language-map weapons-map)
   (classes5e/bard-option      spell-lists spells-map {} language-map weapons-map)
   (classes5e/cleric-option    spell-lists spells-map {} language-map weapons-map)
   (classes5e/druid-option     spell-lists spells-map {} language-map weapons-map)
   (classes5e/fighter-option   spell-lists spells-map {} language-map weapons-map)
   (classes5e/monk-option      spell-lists spells-map {} language-map weapons-map)
   (classes5e/paladin-option   spell-lists spells-map {} language-map weapons-map)
   (classes5e/ranger-option    spell-lists spells-map {} language-map weapons-map)
   (classes5e/rogue-option     spell-lists spells-map {} language-map weapons-map)
   (classes5e/sorcerer-option  spell-lists spells-map {} language-map weapons-map)
   (classes5e/warlock-option   spell-lists spells-map {} language-map weapons-map nil nil)
   (classes5e/wizard-option    spell-lists spells-map {} language-map weapons-map)])

;; --- Tests ---

(deftest all-class-options-construct
  (testing "each PHB class option builds without throwing"
    (let [classes (base-class-options)]
      (is (= 12 (count classes)))
      (doseq [cls classes]
        (is (some? (::t/key cls))
            (str "class option should have a key: " cls))))))

(deftest template-constructs-with-all-nils
  (testing "template-selections accepts all nils (minimum viable construction)"
    (let [selections (t5e/template-selections
                       nil nil nil nil nil nil nil nil nil nil nil nil)
          tmpl (t5e/template selections)]
      (is (some? tmpl))
      (is (some? (::t/selections tmpl)))
      (is (some? (::t/base tmpl))))))

(deftest template-constructs-with-classes
  (testing "template builds with real class data from CLJC sources"
    (let [classes (base-class-options)
          selections (t5e/template-selections
                       nil           ; magic-weapon-options
                       nil           ; magic-armor-options
                       nil           ; other-magic-item-options
                       weapons-map   ; weapon-map
                       nil           ; custom-and-standard-weapons
                       spell-lists   ; spell-lists
                       spells-map    ; spells-map
                       nil           ; backgrounds (CLJS-only)
                       nil           ; races (CLJS-only)
                       classes       ; classes
                       nil           ; feats (plugin-only)
                       language-map) ; language-map
          tmpl (t5e/template selections)]
      (is (some? tmpl))
      (is (some? (::t/selections tmpl)))
      ;; The class selection should contain our 12 classes
      (let [class-sel (some #(when (= :class (::t/key %)) %)
                            (::t/selections tmpl))]
        (is (some? class-sel) "template should have a :class selection")
        (is (= 12 (count (::t/options class-sel)))
            "class selection should contain all 12 PHB classes")))))
