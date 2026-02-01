(ns orcpub.dnd.e5.ac-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.entity :as entity]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

;;; ---------------------------------------------------------------------------
;;; AC stacking / override test suite
;;;
;;; D&D 5e PHB p.14: "If you have multiple features that give you different
;;; ways to calculate your AC, you choose which one to use."
;;;
;;; BUG 1 — Stacking (template_base.cljc:38-41,60):
;;;   ?base-armor-class = 10 + DEX + (if unarmored > natural: 0 else: natural)
;;;   ?unarmored-armor-class = ?base-armor-class + ?unarmored-ac-bonus
;;;   When both are present, they stack instead of using the better formula.
;;;
;;; BUG 2 — Stale closure in race AC overrides:
;;;   Lizardfolk/Tortle races override ?armor-class-with-armor via
;;;   (mod/modifier ?armor-class-with-armor ...). The modifier macro captures
;;;   the entity at modifier-application time. Because race modifiers run
;;;   before class modifiers, the override's closure never sees class-based
;;;   unarmored-ac-bonus (barbarian CON, monk WIS). This means a Lizardfolk
;;;   Barbarian's displayed AC ignores Unarmored Defense entirely.
;;;
;;; Tests 1-3: single-source AC (should pass).
;;; Test 4: class multiclass stacking bug (Barbarian/Draconic).
;;; Tests 5-6: race override + class unarmored defense interaction.
;;; ---------------------------------------------------------------------------

;;; ---------------------------------------------------------------------------
;;; Inline configs (language-map and elf race from warlock_test.clj)
;;; ---------------------------------------------------------------------------

(def test-languages
  [{:name "Common" :key :common}
   {:name "Elvish" :key :elvish}
   {:name "Dwarvish" :key :dwarvish}
   {:name "Giant" :key :giant}
   {:name "Gnomish" :key :gnomish}
   {:name "Goblin" :key :goblin}
   {:name "Halfling" :key :halfling}
   {:name "Orc" :key :orc}
   {:name "Abyssal" :key :abyssal}
   {:name "Celestial" :key :celestial}
   {:name "Draconic" :key :draconic}
   {:name "Deep Speech" :key :deep-speech}
   {:name "Infernal" :key :infernal}
   {:name "Primordial" :key :primordial}
   {:name "Sylvan" :key :sylvan}
   {:name "Undercommon" :key :undercommon}])

(def language-map (common/map-by-key test-languages))

;; Elf race with Drow subrace — adds DEX +2 (elf) and CHA +1 (drow).
;; All base ability values below account for these racial bonuses.
(def elf-race-cfg
  {:name "Elf"
   :key :elf
   :abilities {:orcpub.dnd.e5.character/dex 2}
   :size :medium
   :speed 30
   :languages ["Elvish" "Common"]
   :darkvision 60
   :modifiers [(mod5e/saving-throw-advantage [:charmed])
               (mod5e/immunity :magical-sleep)
               (mod5e/skill-proficiency :perception)]
   :subraces
   [{:name "Dark Elf (Drow)"
     :abilities {:orcpub.dnd.e5.character/cha 1}
     :darkvision 120
     :modifiers [(mod5e/weapon-proficiency :rapier)
                 (mod5e/weapon-proficiency :shortsword)
                 (mod5e/weapon-proficiency :crossbow-hand)]
     :traits [{:name "Sunlight Sensitivity"
               :summary "Disadvantage on attack and perception rolls in direct sunlight"}]}]
   :traits [{:name "Fey Ancestry" :summary "advantage on charmed saves; immune to sleep magic"}
            {:name "Trance" :summary "Trance 4 hrs. instead of sleep 8"}]})

;;; ---------------------------------------------------------------------------
;;; Template — barbarian, monk, sorcerer
;;; ---------------------------------------------------------------------------

(def test-template
  (t5e/template
   (t5e/template-selections
    nil                                       ; magic-weapon-options
    nil                                       ; magic-armor-options
    nil                                       ; other-magic-item-options
    weapons5e/weapons-map                     ; weapon-map
    weapons5e/weapons                         ; custom-and-standard-weapons
    sl5e/spell-lists                          ; spell-lists
    spells5e/spell-map                        ; spells-map
    nil                                       ; backgrounds
    [elf-race-cfg]                            ; races
    [(classes5e/barbarian-option              ; classes
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/monk-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/sorcerer-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)]
    nil                                       ; feats
    language-map)))

;;; ---------------------------------------------------------------------------
;;; Race AC configs — Lizardfolk and Tortle
;;;
;;; These race definitions mirror the commented-out lizardfolk-option-cfg in
;;; template.cljc:265 and the :tortle-ac property in options.cljc:3253.
;;; Both are available to users via the homebrew race property system.
;;; ---------------------------------------------------------------------------

;; Lizardfolk: Natural Armor (PHB-equivalent, VGM p.113)
;; "You have tough, scaly skin. When you aren't wearing armor, your AC is
;; 13 + your Dexterity modifier."
;; Implementation: sets ?natural-ac-bonus to 3, overrides ?armor-class-with-armor
;; to take max of natural and armored/unarmored AC.
(def lizardfolk-race-cfg
  {:name "Lizardfolk"
   :key :lizardfolk
   :abilities {:orcpub.dnd.e5.character/con 2
               :orcpub.dnd.e5.character/wis 1}
   :size :medium
   :speed 30
   :languages ["Common" "Draconic"]
   :modifiers [(mod/modifier ?natural-ac-bonus 3)
               (mod/modifier ?armor-class-with-armor
                             (fn [armor & [shield]]
                               (max (+ ?base-armor-class
                                       (if shield (?shield-ac-bonus shield) 0))
                                    (?armor-class-with-armor armor shield))))]})

;; Tortle: Natural Armor (XGE/TTP)
;; "Your shell provides ample protection ... your AC is 17."
;; Implementation: sets ?natural-ac-bonus to 7, overrides ?armor-class-with-armor
;; to always return flat 17 + shield bonus.
(def tortle-race-cfg
  {:name "Tortle"
   :key :tortle
   :abilities {:orcpub.dnd.e5.character/str 2
               :orcpub.dnd.e5.character/wis 1}
   :size :medium
   :speed 30
   :languages ["Common" "Aquan"]
   :modifiers [(mod/modifier ?natural-ac-bonus 7)
               (mod/modifier ?armor-class-with-armor
                             (fn [armor & [shield]]
                               (+ 17
                                  (if shield (?shield-ac-bonus shield) 0))))]})

;;; ---------------------------------------------------------------------------
;;; Template with race-based AC — includes Lizardfolk, Tortle + Barbarian, Monk
;;; ---------------------------------------------------------------------------

(def race-ac-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map
    weapons5e/weapons
    sl5e/spell-lists
    spells5e/spell-map
    nil
    [lizardfolk-race-cfg tortle-race-cfg]
    [(classes5e/barbarian-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/monk-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)]
    nil
    language-map)))

;;; ---------------------------------------------------------------------------
;;; Helper
;;; ---------------------------------------------------------------------------

(defn unarmored-ac [built-char]
  (char5e/get-prop built-char :unarmored-armor-class))

(defn ac-bonus [built-char k]
  (char5e/get-prop built-char k))

(defn displayed-ac
  "Get the effective unarmored AC through the armor-class-with-armor function,
   which is the code path max-armor-class uses to compute the value shown to
   users. For races with AC overrides (Lizardfolk, Tortle), this is the
   override function; for others, it's the standard pipeline."
  [built-char]
  (let [ac-fn (char5e/armor-class-with-armor built-char)]
    (ac-fn nil)))

;;; ---------------------------------------------------------------------------
;;; Entity definitions
;;;
;;; All use Drow Elf race (DEX +2, CHA +1). Base ability values are set
;;; so that the FINAL (post-racial) values produce clean modifier numbers.
;;; ---------------------------------------------------------------------------

;; Barbarian: final DEX 14(+2), CON 14(+2) → AC = 10 + 2 + 2 = 14
(def barbarian-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :elf
     :orcpub.entity/options
     {:subrace {:orcpub.entity/key :dark-elf-drow-}}}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 15
      :orcpub.dnd.e5.character/dex 12  ; +2 elf = 14
      :orcpub.dnd.e5.character/con 14
      :orcpub.dnd.e5.character/int 8
      :orcpub.dnd.e5.character/wis 10
      :orcpub.dnd.e5.character/cha 10}} ; +1 drow = 11
    :class
    [{:orcpub.entity/key :barbarian
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :athletics}
        {:orcpub.entity/key :survival}]
       :levels
       [{:orcpub.entity/key :level-1}]}}]}})

;; Monk: final DEX 16(+3), WIS 14(+2) → AC = 10 + 3 + 2 = 15
(def monk-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :elf
     :orcpub.entity/options
     {:subrace {:orcpub.entity/key :dark-elf-drow-}}}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 8
      :orcpub.dnd.e5.character/dex 14  ; +2 elf = 16
      :orcpub.dnd.e5.character/con 10
      :orcpub.dnd.e5.character/int 10
      :orcpub.dnd.e5.character/wis 14
      :orcpub.dnd.e5.character/cha 10}} ; +1 drow = 11
    :class
    [{:orcpub.entity/key :monk
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :acrobatics}
        {:orcpub.entity/key :insight}]
       :levels
       [{:orcpub.entity/key :level-1}]}}]}})

;; Sorcerer (Draconic Bloodline): final DEX 14(+2) → AC = 13 + 2 = 15
;; Draconic Resilience sets ?natural-ac-bonus to 3 (i.e. 10 + 3 = 13 base).
(def sorcerer-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :elf
     :orcpub.entity/options
     {:subrace {:orcpub.entity/key :dark-elf-drow-}}}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 8
      :orcpub.dnd.e5.character/dex 12  ; +2 elf = 14
      :orcpub.dnd.e5.character/con 12
      :orcpub.dnd.e5.character/int 10
      :orcpub.dnd.e5.character/wis 10
      :orcpub.dnd.e5.character/cha 14}} ; +1 drow = 15
    :class
    [{:orcpub.entity/key :sorcerer
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :arcana}
        {:orcpub.entity/key :deception}]
       :levels
       [{:orcpub.entity/key :level-1
         :orcpub.entity/options
         {:sorcerous-origin
          {:orcpub.entity/key :draconic-bloodline
           :orcpub.entity/options
           {:draconic-ancestry-type
            {:orcpub.entity/key :black}}}}}]}}]}})

;; Barbarian 1 / Sorcerer (Draconic) 1: multiclass that triggers stacking bug.
;; Final DEX 14(+2), CON 14(+2), natural-ac-bonus 3 (draconic).
;; RAW: max(Barbarian 10+2+2=14, Draconic 13+2=15) = 15
;; Bug:  10 + DEX(2) + natural(3) + unarmored(2) = 17
(def barb-sorc-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :elf
     :orcpub.entity/options
     {:subrace {:orcpub.entity/key :dark-elf-drow-}}}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 14
      :orcpub.dnd.e5.character/dex 12  ; +2 elf = 14
      :orcpub.dnd.e5.character/con 14
      :orcpub.dnd.e5.character/int 8
      :orcpub.dnd.e5.character/wis 10
      :orcpub.dnd.e5.character/cha 12}} ; +1 drow = 13
    :class
    [{:orcpub.entity/key :barbarian
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :athletics}
        {:orcpub.entity/key :survival}]
       :levels
       [{:orcpub.entity/key :level-1}]}}
     {:orcpub.entity/key :sorcerer
      :orcpub.entity/options
      {:levels
       [{:orcpub.entity/key :level-1
         :orcpub.entity/options
         {:sorcerous-origin
          {:orcpub.entity/key :draconic-bloodline
           :orcpub.entity/options
           {:draconic-ancestry-type
            {:orcpub.entity/key :black}}}}}]}}]}})

;;; ---------------------------------------------------------------------------
;;; Race-AC entities (use race-ac-template, NOT test-template)
;;;
;;; Lizardfolk: CON +2, WIS +1 from race.
;;; Tortle: STR +2, WIS +1 from race.
;;; ---------------------------------------------------------------------------

;; Lizardfolk Barbarian: high CON so Barbarian AC > Lizardfolk natural AC.
;; After race: STR 15, DEX 10(+0), CON 18(+4), INT 8, WIS 11(+0), CHA 8
;; Barbarian Unarmored Defense: 10 + DEX(+0) + CON(+4) = 14
;; Lizardfolk Natural Armor:   13 + DEX(+0)            = 13
;; RAW: max(14, 13) = 14
(def liz-barb-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :lizardfolk}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 15
      :orcpub.dnd.e5.character/dex 10
      :orcpub.dnd.e5.character/con 16  ; +2 lizardfolk = 18
      :orcpub.dnd.e5.character/int 8
      :orcpub.dnd.e5.character/wis 10  ; +1 lizardfolk = 11
      :orcpub.dnd.e5.character/cha 8}}
    :class
    [{:orcpub.entity/key :barbarian
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :athletics}
        {:orcpub.entity/key :survival}]
       :levels
       [{:orcpub.entity/key :level-1}]}}]}})

;; Tortle Monk: moderate DEX/WIS so Monk AC < Tortle flat 17.
;; After race: STR 10(+0), DEX 14(+2), CON 10, INT 10, WIS 15(+2), CHA 8
;; Monk Unarmored Defense: 10 + DEX(+2) + WIS(+2) = 14
;; Tortle Natural Armor:   flat 17
;; RAW: max(17, 14) = 17
(def tortle-monk-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :tortle}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 8   ; +2 tortle = 10
      :orcpub.dnd.e5.character/dex 14
      :orcpub.dnd.e5.character/con 10
      :orcpub.dnd.e5.character/int 10
      :orcpub.dnd.e5.character/wis 14  ; +1 tortle = 15
      :orcpub.dnd.e5.character/cha 8}}
    :class
    [{:orcpub.entity/key :monk
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :acrobatics}
        {:orcpub.entity/key :insight}]
       :levels
       [{:orcpub.entity/key :level-1}]}}]}})

;;; ---------------------------------------------------------------------------
;;; Tests
;;; ---------------------------------------------------------------------------

(deftest barbarian-unarmored-defense
  (testing "Barbarian AC = 10 + DEX + CON when unarmored (no stacking issue)"
    (let [built (entity/build barbarian-entity test-template)]
      ;; Intermediate values
      (is (= 2 (ac-bonus built :unarmored-ac-bonus))
          "CON +2 should be the unarmored AC bonus")
      (is (= 0 (ac-bonus built :natural-ac-bonus))
          "no natural AC source")
      ;; Final AC: 10 + DEX(+2) + CON(+2) = 14
      (is (= 14 (unarmored-ac built))))))

(deftest monk-unarmored-defense
  (testing "Monk AC = 10 + DEX + WIS when unarmored (no stacking issue)"
    (let [built (entity/build monk-entity test-template)]
      (is (= 2 (ac-bonus built :unarmored-ac-bonus))
          "WIS +2 should be the unarmored AC bonus")
      (is (= 0 (ac-bonus built :natural-ac-bonus))
          "no natural AC source")
      ;; Final AC: 10 + DEX(+3) + WIS(+2) = 15
      (is (= 15 (unarmored-ac built))))))

(deftest draconic-resilience-natural-ac
  (testing "Sorcerer (Draconic) AC = 13 + DEX when unarmored (no stacking issue)"
    (let [built (entity/build sorcerer-entity test-template)]
      (is (= 0 (ac-bonus built :unarmored-ac-bonus))
          "no unarmored defense class feature")
      (is (= 3 (ac-bonus built :natural-ac-bonus))
          "Draconic Resilience adds 3 to natural AC")
      ;; Final AC: 10 + DEX(+2) + natural(3) + unarmored(0) = 15
      ;; Equivalent to 13 + DEX(+2) = 15
      (is (= 15 (unarmored-ac built))))))

(deftest multiclass-ac-natural-and-unarmored-should-not-stack
  (testing "Barbarian/Sorcerer(Draconic): RAW says use best formula, not both"
    (let [built (entity/build barb-sorc-entity test-template)
          unarmored (ac-bonus built :unarmored-ac-bonus)
          natural   (ac-bonus built :natural-ac-bonus)
          ac        (unarmored-ac built)]
      ;; Individual bonus values should be correct
      (is (= 2 unarmored) "Barbarian CON +2")
      (is (= 3 natural)   "Draconic Resilience natural AC +3")
      ;; D&D 5e RAW: you choose ONE AC formula.
      ;;   Barbarian Unarmored Defense: 10 + DEX(+2) + CON(+2) = 14
      ;;   Draconic Resilience:         13 + DEX(+2)            = 15
      ;;   Best formula = 15
      ;;
      ;; BUG in template_base.cljc:38-41,60:
      ;;   base-armor-class adds natural(3) because natural >= unarmored
      ;;   then unarmored-armor-class adds unarmored(2) on top
      ;;   giving 10 + 2 + 3 + 2 = 17 instead of 15
      (is (= 15 ac)
          (str "RAW: max(Barbarian=14, Draconic=15) = 15, got " ac)))))

;;; ---------------------------------------------------------------------------
;;; Race AC override tests — Lizardfolk, Tortle
;;;
;;; These tests use the race-ac-template (not test-template) and access AC
;;; through both the displayed path (armor-class-with-armor, what users see)
;;; and the pipeline intermediate (unarmored-armor-class).
;;; ---------------------------------------------------------------------------

(deftest lizardfolk-barbarian-ac-override
  (testing "Lizardfolk Barbarian: barbarian CON bonus should not be lost"
    (let [built (entity/build liz-barb-entity race-ac-template)]
      ;; Intermediate bonus values
      (is (= 4 (ac-bonus built :unarmored-ac-bonus))
          "Barbarian CON +4")
      (is (= 3 (ac-bonus built :natural-ac-bonus))
          "Lizardfolk natural AC +3")
      ;; D&D 5e RAW: pick the best AC formula.
      ;;   Barbarian Unarmored Defense: 10 + DEX(+0) + CON(+4) = 14
      ;;   Lizardfolk Natural Armor:   13 + DEX(+0)            = 13
      ;;   Best formula = 14
      ;;
      ;; BUG (stale closure): The lizardfolk ?armor-class-with-armor override
      ;; captures the entity at race-modifier time, before barbarian modifiers
      ;; run. The override's closure has ?unarmored-ac-bonus = 0, so it always
      ;; returns the lizardfolk natural AC (13 + DEX = 13), ignoring barbarian.
      ;;
      ;; BUG (stacking pipeline): ?unarmored-armor-class adds both natural(3)
      ;; and unarmored(4) giving 10 + 0 + 3 + 4 = 17 instead of max(14, 13).
      (is (= 14 (displayed-ac built))
          (str "displayed AC should be max(Barbarian=14, Lizardfolk=13) = 14, "
               "got " (displayed-ac built)))
      (is (= 14 (unarmored-ac built))
          (str "pipeline AC should be 14, got " (unarmored-ac built))))))

(deftest tortle-monk-ac-override
  (testing "Tortle Monk: tortle flat 17 should win over monk's 14"
    (let [built (entity/build tortle-monk-entity race-ac-template)]
      ;; Intermediate bonus values
      (is (= 2 (ac-bonus built :unarmored-ac-bonus))
          "Monk WIS +2")
      (is (= 7 (ac-bonus built :natural-ac-bonus))
          "Tortle natural AC +7")
      ;; D&D 5e RAW:
      ;;   Monk Unarmored Defense: 10 + DEX(+2) + WIS(+2) = 14
      ;;   Tortle Natural Armor:  flat 17
      ;;   Best formula = 17
      ;;
      ;; The tortle override hardcodes 17, so the displayed AC is correct
      ;; for these stats. But the pipeline intermediate (?unarmored-armor-class)
      ;; has the stacking bug: 10 + DEX(2) + natural(7) + unarmored(2) = 21.
      (is (= 17 (displayed-ac built))
          (str "displayed AC should be 17 (tortle shell), got "
               (displayed-ac built)))
      ;; The pipeline should not give an inflated value
      (is (<= (unarmored-ac built) 17)
          (str "pipeline AC should not exceed tortle's 17, got "
               (unarmored-ac built))))))
