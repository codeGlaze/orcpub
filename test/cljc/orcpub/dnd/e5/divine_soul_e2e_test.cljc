(ns orcpub.dnd.e5.divine-soul-e2e-test
  "E2E behavioral check (not call-graph reading) for the conclusions in
   docs/kb/decision-vocabulary.md about custom-class spellcasting.

   Runs the REAL compile fn `opt5e/spellcasting-template` (the class-level
   spellcasting path) with a custom :spell-list and OBSERVES what spells the
   built selection actually offers. The question being settled empirically:
   does the class-builder's custom :spell-list really let a sorcerer-chassis
   class offer CLERIC spells (the community Divine Soul workaround), or did I
   only see a function get called?

   JVM/clojure.test so it runs under the enforced `lein test` gate. (The
   subclass-side gate lives in spell_subs.cljs = cljs-only; a separate cljs
   harness test covers that.)"
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def spell-lists sl5e/spell-lists)
(def spells-map spells5e/spell-map)

;; :cure-wounds and :bless are CLERIC spells, NOT on the sorcerer list.
;; :magic-missile is a sorcerer spell. If the custom list works, all three are
;; offered to this class; if the engine ignored :spell-list and fell back to the
;; sorcerer list, the cleric spells would be absent.
(def divine-soul-spellcasting
  {:level-factor 1                                   ; full caster
   :known-mode :schedule
   :ability :orcpub.dnd.e5.character/cha
   :spells-known {1 2}                               ; know 2 at level 1 (enough to observe options)
   :cantrips? true
   :cantrips-known {1 2}
   :spell-list {0 #{:sacred-flame :fire-bolt}        ; 0 = cantrips (sacred-flame is cleric)
                1 #{:cure-wounds :bless :magic-missile}}})

(def divine-soul-cls
  {:name "Sorcerer (Divine Soul)"
   :key :sorcerer-divine-soul-})

(defn offered-spell-keys
  "Run the real spellcasting compile and collect every spell key offered at
   class level `lvl` (unions cantrip + spells-known selections, which merge-with
   concat under the same level key)."
  [lvl]
  (let [template (opt5e/spellcasting-template
                  spell-lists spells-map
                  (assoc divine-soul-spellcasting :class-key (:key divine-soul-cls))
                  divine-soul-cls)
        sels (get-in template [:selections lvl])]
    (set (mapcat (fn [sel] (keep ::t/key (::t/options sel))) sels))))

(deftest custom-spell-list-offers-cleric-spells-to-a-sorcerer-chassis-class
  (testing "the class-builder custom :spell-list actually surfaces cleric spells (the Divine Soul workaround), observed from the compiled selection"
    (let [offered (offered-spell-keys 1)]
      (is (seq offered)
          "the level-1 spells-known selection must offer SOME spells (spellcasting actually compiled)")
      (is (contains? offered :cure-wounds)
          "CLERIC spell cure-wounds is offered — proves the custom list is honored in practice")
      (is (contains? offered :bless)
          "CLERIC spell bless is offered too")
      (is (contains? offered :magic-missile)
          "the sorcerer spell is also offered"))))

(deftest control-without-custom-list-falls-back-and-omits-cleric-spells
  (testing "with NO custom :spell-list (spell-list-kw points at the real sorcerer list), cleric spells are NOT offered — isolates the custom list as the cause"
    (let [template (opt5e/spellcasting-template
                    spell-lists spells-map
                    {:level-factor 1 :known-mode :schedule
                     :ability :orcpub.dnd.e5.character/cha
                     :spells-known {1 2}
                     :spell-list-kw :sorcerer            ; real sorcerer list, no custom map
                     :class-key :sorcerer-divine-soul-}
                    divine-soul-cls)
          sels (get-in template [:selections 1])
          offered (set (mapcat (fn [sel] (keep ::t/key (::t/options sel))) sels))]
      (is (seq offered) "sorcerer still offers its own spells")
      (is (not (contains? offered :cure-wounds))
          "cure-wounds is NOT a sorcerer spell — confirms the cleric spells above came from the custom list, not a fluke"))))

;; ---------------------------------------------------------------------------
;; The full last mile: BUILD A CHARACTER that takes the custom Divine Soul class
;; and chooses a cleric spell, then read it off the derived sheet. This is the
;; warlock_test standard ("how it works in practice"), not a compile snapshot.
;; ---------------------------------------------------------------------------

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

(def divine-soul-class-full
  {:name "Sorcerer (Divine Soul)"
   :key :sorcerer-divine-soul-
   :hit-die 6
   :ability-increase-levels [4 8 12 16 19]
   :subclass-title "Affinity"
   :subclass-level 1
   :subclasses []
   :profs {}
   :spellcasting divine-soul-spellcasting})

(def divine-soul-option
  (opt5e/class-option sl5e/spell-lists spells-map {} language-map
                      weapons5e/weapons-map divine-soul-class-full))

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map weapons5e/weapons
    sl5e/spell-lists spells-map
    []                                    ; backgrounds
    []                                    ; races
    [divine-soul-option]                  ; classes
    []                                    ; feats
    language-map)))

;; Level-1 Divine Soul who picks the CLERIC spell cure-wounds (from the custom list)
;; and the cleric cantrip sacred-flame.
(def divine-soul-entity
  {:orcpub.entity/options
   {:ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value {:orcpub.dnd.e5.character/str 10
                           :orcpub.dnd.e5.character/dex 10
                           :orcpub.dnd.e5.character/con 10
                           :orcpub.dnd.e5.character/int 10
                           :orcpub.dnd.e5.character/wis 10
                           :orcpub.dnd.e5.character/cha 16}}
    :class
    [{:orcpub.entity/key :sorcerer-divine-soul-
      :orcpub.entity/options
      {:levels
       [{:orcpub.entity/key :level-1
         :orcpub.entity/options
         {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 4}
          :sorcerer-divine-soul--cantrips-known
          [{:orcpub.entity/key :sacred-flame}
           {:orcpub.entity/key :fire-bolt}]
          :sorcerer-divine-soul--spells-known
          [{:orcpub.entity/key :cure-wounds}
           {:orcpub.entity/key :bless}]}}]}}]}})

(deftest built-divine-soul-character-is-a-real-spellcaster
  (testing "a level-1 character of the custom Divine Soul class is recognized as a functioning spellcaster on the built sheet (gains caster levels), and the build does not throw"
    (let [built (entity/build divine-soul-entity test-template)]
      (is (some? built) "build must not throw")
      (is (= 1 (char5e/total-spellcaster-levels built))
          "the custom class actually grants spellcasting end-to-end (1 caster level)"))))

;; OPEN / NOT YET RESOLVED (recorded honestly, not asserted as working):
;; In this hand-built entity the CLASS-level spellcasting applies (caster level = 1
;; above), but the per-level *chosen* spells did NOT populate `char5e/spells-known`
;; (it came back {}). Either the entity nesting for a spellcasting-template's
;; per-level spell selections differs from what I constructed, or entity/build
;; applies those selected-option modifiers via a path I haven't traced. This is the
;; genuine remaining "last mile" — it needs entity.cljc investigation, not another
;; guess. The custom list IS offered (proven above) and the class DOES cast; whether
;; a specific chosen cleric spell lands on the sheet via this path is still open.
