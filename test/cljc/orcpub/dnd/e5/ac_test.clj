(ns orcpub.dnd.e5.ac-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.common :as common]))

;;; ---------------------------------------------------------------------------
;;; AC stacking bug test suite
;;;
;;; D&D 5e PHB p.14: "If you have multiple features that give you different
;;; ways to calculate your AC, you choose which one to use."
;;;
;;; The AC pipeline in template_base.cljc has a stacking bug:
;;;
;;;   ?base-armor-class = 10 + DEX + (if unarmored > natural: 0 else: natural)
;;;   ?unarmored-armor-class = ?base-armor-class + ?unarmored-ac-bonus
;;;
;;; When natural-ac-bonus >= unarmored-ac-bonus, BOTH are added to the final
;;; AC. For example, a Barbarian 1 / Sorcerer(Draconic) 1 with DEX +2, CON +2
;;; gets 10 + 2 (DEX) + 3 (natural) + 2 (CON) = 17, when RAW says
;;; max(Barbarian: 10+2+2=14, Draconic: 13+2=15) = 15.
;;;
;;; Tests 1-3 verify single-class AC formulas (these should pass).
;;; Test 4 asserts the RAW-correct value for multiclass, exposing the bug.
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
;;; Helper
;;; ---------------------------------------------------------------------------

(defn unarmored-ac [built-char]
  (char5e/get-prop built-char :unarmored-armor-class))

(defn ac-bonus [built-char k]
  (char5e/get-prop built-char k))

(defn displayed-ac
  "The AC returned by ?armor-class-with-armor when called with no armor/shield.
   This goes through any overrides (e.g. lizardfolk-ac, tortle-ac) and may
   differ from ?unarmored-armor-class due to stale modifier closures."
  [built-char]
  (let [ac-fn (char5e/get-prop built-char :armor-class-with-armor)]
    (ac-fn nil)))

(defn ac-with-shield
  "The AC returned by ?armor-class-with-armor with a basic (non-magical) shield.
   Shield AC bonus is +2 by default."
  [built-char]
  (let [ac-fn (char5e/get-prop built-char :armor-class-with-armor)]
    (ac-fn nil {})))

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

;; Monk (high WIS) for shield interaction test.
;; Final DEX 14(+2), WIS 16(+3) → AC without shield: 10+2+3=15
;; AC with shield: 10+2+0+2=14 (monk loses WIS with shield; PHB p.78)
(def monk-shield-entity
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
      :orcpub.dnd.e5.character/con 10
      :orcpub.dnd.e5.character/int 10
      :orcpub.dnd.e5.character/wis 16
      :orcpub.dnd.e5.character/cha 10}} ; +1 drow = 11
    :class
    [{:orcpub.entity/key :monk
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :acrobatics}
        {:orcpub.entity/key :insight}]
       :levels
       [{:orcpub.entity/key :level-1}]}}]}})

;;; ---------------------------------------------------------------------------
;;; Homebrew AC modifiers via plugin-modifiers (options.cljc)
;;;
;;; :lizardfolk-ac and :tortle-ac are generic homebrew AC options with legacy
;;; names for backward compatibility. They can be applied to any custom race
;;; or feat through the plugin system.
;;; ---------------------------------------------------------------------------

(def natural-armor-mods
  (opt5e/plugin-modifiers {:lizardfolk-ac true} :custom-natural-armor))

(def shell-armor-mods
  (opt5e/plugin-modifiers {:tortle-ac true} :custom-shell-armor))

(def natural-armor-race-cfg
  {:name "Custom Natural Armor"
   :key :custom-natural-armor
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers natural-armor-mods})

(def shell-armor-race-cfg
  {:name "Custom Shell Armor"
   :key :custom-shell-armor
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers shell-armor-mods})

(def homebrew-ac-template
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
    [natural-armor-race-cfg shell-armor-race-cfg] ; races
    [(classes5e/barbarian-option              ; classes
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/monk-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/sorcerer-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)]
    nil                                       ; feats
    language-map)))

;; Custom Natural Armor + Barbarian: DEX 14(+2), CON 14(+2)
;; :lizardfolk-ac sets ?natural-ac-bonus 3 + overrides ?armor-class-with-armor
(def natural-armor-barb-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :custom-natural-armor}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 15
      :orcpub.dnd.e5.character/dex 14
      :orcpub.dnd.e5.character/con 14
      :orcpub.dnd.e5.character/int 8
      :orcpub.dnd.e5.character/wis 10
      :orcpub.dnd.e5.character/cha 10}}
    :class
    [{:orcpub.entity/key :barbarian
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :athletics}
        {:orcpub.entity/key :survival}]
       :levels
       [{:orcpub.entity/key :level-1}]}}]}})

;; Custom Shell Armor + Monk: DEX 14(+2), WIS 14(+2)
;; :tortle-ac sets ?natural-ac-bonus 7 + flat 17 ?armor-class-with-armor
(def shell-armor-monk-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :custom-shell-armor}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 8
      :orcpub.dnd.e5.character/dex 14
      :orcpub.dnd.e5.character/con 10
      :orcpub.dnd.e5.character/int 10
      :orcpub.dnd.e5.character/wis 14
      :orcpub.dnd.e5.character/cha 10}}
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
;;; Shield interaction tests
;;; ---------------------------------------------------------------------------

(deftest barbarian-shield-ac
  (testing "Barbarian AC with shield: CON applies (PHB p.48 allows shield)"
    (let [built (entity/build barbarian-entity test-template)]
      ;; Without shield: 10 + DEX(2) + CON(2) = 14 (verified above)
      (is (= 14 (displayed-ac built))
          "displayed AC matches unarmored AC")
      ;; With shield: barbarian sets ?unarmored-with-shield-ac-bonus = CON(+2)
      ;; 10 + DEX(2) + CON(2) + shield(2) = 16
      (is (= 16 (ac-with-shield built))
          "Barbarian + shield: 10+DEX(2)+CON(2)+shield(2)=16"))))

(deftest monk-shield-ac
  (testing "Monk AC with shield: WIS does NOT apply (PHB p.78 requires no shield)"
    (let [built (entity/build monk-shield-entity test-template)]
      ;; Monk unarmored without shield: 10 + DEX(+2) + WIS(+3) = 15
      (is (= 3 (ac-bonus built :unarmored-ac-bonus))
          "WIS +3 unarmored bonus")
      (is (= 0 (ac-bonus built :unarmored-with-shield-ac-bonus))
          "Monk does NOT set unarmored-with-shield bonus")
      (is (= 15 (unarmored-ac built))
          "no shield: 10+DEX(2)+WIS(3)=15")
      ;; With shield: monk does NOT set ?unarmored-with-shield-ac-bonus
      ;; AC = 10 + DEX(2) + 0 + shield(2) = 14 (WIS lost)
      (is (= 14 (ac-with-shield built))
          "with shield: 10+DEX(2)+shield(2)=14, WIS NOT applied"))))

;;; ---------------------------------------------------------------------------
;;; Homebrew AC formula tests (plugin-modifiers code path)
;;;
;;; These exercise the :lizardfolk-ac and :tortle-ac feat modifier functions
;;; from options.cljc via plugin-modifiers, using generic custom race names.
;;;
;;; Key interactions tested:
;;;   - ?natural-ac-bonus stacking with ?unarmored-ac-bonus (bug)
;;;   - ?armor-class-with-armor override vs ?unarmored-armor-class divergence
;;;   - Shield stacking through the override function
;;;   - DEX inclusion/exclusion per formula
;;; ---------------------------------------------------------------------------

(deftest natural-armor-barbarian-stacking
  (testing "Custom Natural Armor + Barbarian: :lizardfolk-ac path"
    (let [built (entity/build natural-armor-barb-entity homebrew-ac-template)]
      ;; Intermediate bonus values
      (is (= 3 (ac-bonus built :natural-ac-bonus))
          ":lizardfolk-ac sets natural AC bonus to 3")
      (is (= 2 (ac-bonus built :unarmored-ac-bonus))
          "Barbarian CON +2")
      (is (= 2 (ac-bonus built :unarmored-with-shield-ac-bonus))
          "Barbarian CON +2 applies with shield too")

      ;; ?unarmored-armor-class resolves from the FINAL entity where both
      ;; natural and unarmored bonuses are present.
      ;; RAW: max(Natural 13+DEX(2)=15, Barbarian 10+DEX(2)+CON(2)=14) = 15
      ;; BUG: base=10+DEX(2)+natural(3)=15, unarmored=15+CON(2)=17
      (is (= 15 (unarmored-ac built))
          (str "RAW: max(Natural=15, Barbarian=14)=15, got " (unarmored-ac built)))

      ;; The :lizardfolk-ac override of ?armor-class-with-armor captures a
      ;; stale closure (entity at race-modifier time, before Barbarian's
      ;; CON bonus is applied). This may produce a different value than
      ;; ?unarmored-armor-class.
      (is (= 15 (displayed-ac built))
          (str "displayed AC should be 15, got " (displayed-ac built)))

      ;; Shield: RAW max(Natural 13+DEX(2)+shield(2)=17,
      ;;              Barbarian 10+DEX(2)+CON(2)+shield(2)=16) = 17
      (is (= 17 (ac-with-shield built))
          (str "with shield: max(Natural=17, Barbarian=16)=17, got "
               (ac-with-shield built))))))

(deftest shell-armor-monk-stacking
  (testing "Custom Shell Armor + Monk: :tortle-ac path"
    (let [built (entity/build shell-armor-monk-entity homebrew-ac-template)]
      ;; Intermediate bonus values
      (is (= 7 (ac-bonus built :natural-ac-bonus))
          ":tortle-ac sets natural AC bonus to 7")
      (is (= 2 (ac-bonus built :unarmored-ac-bonus))
          "Monk WIS +2")
      (is (= 0 (ac-bonus built :unarmored-with-shield-ac-bonus))
          "Monk does NOT set shield bonus (correct)")

      ;; ?unarmored-armor-class has the stacking bug
      ;; RAW: max(Shell=17, Monk 10+DEX(2)+WIS(2)=14) = 17
      ;; BUG: base=10+DEX(2)+natural(7)=19, unarmored=19+WIS(2)=21
      (is (= 17 (unarmored-ac built))
          (str "RAW: max(Shell=17, Monk=14)=17, got " (unarmored-ac built)))

      ;; The :tortle-ac override of ?armor-class-with-armor returns flat 17.
      ;; DEX is NOT added (correct for shell armor).
      (is (= 17 (displayed-ac built))
          (str "displayed AC should be flat 17, got " (displayed-ac built)))

      ;; Shield: shell armor 17 + shield(2) = 19
      (is (= 19 (ac-with-shield built))
          (str "with shield: 17+shield(2)=19, got "
               (ac-with-shield built))))))

;;; ---------------------------------------------------------------------------
;;; Full-stack stacking test: race feat AC + multiclass + shield
;;;
;;; Combines ALL AC modifier sources in one character:
;;;   - Race feat: :lizardfolk-ac (natural-ac-bonus 3, armor-class-with-armor override)
;;;   - Class 1:  Barbarian (unarmored-ac-bonus = CON, shield bonus = CON)
;;;   - Class 2:  Sorcerer/Draconic (natural-ac-bonus 3, redundant with race)
;;;   - Shield:   basic +2
;;; ---------------------------------------------------------------------------

;; Custom Natural Armor + Barbarian 1/Sorcerer(Draconic) 1 + shield
;; DEX 14(+2), CON 14(+2), CHA 14(+2)
;; Three AC formulas in play:
;;   Natural Armor (race feat):      13 + DEX(2) = 15
;;   Barbarian Unarmored Defense:    10 + DEX(2) + CON(2) = 14
;;   Draconic Resilience (class):    13 + DEX(2) = 15  (same as race feat)
;; RAW: best formula = 15
(def natural-armor-barb-sorc-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :custom-natural-armor}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value
     {:orcpub.dnd.e5.character/str 14
      :orcpub.dnd.e5.character/dex 14
      :orcpub.dnd.e5.character/con 14
      :orcpub.dnd.e5.character/int 8
      :orcpub.dnd.e5.character/wis 10
      :orcpub.dnd.e5.character/cha 14}}
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

(deftest full-stack-race-multiclass-shield
  (testing "Custom Natural Armor + Barbarian/Sorcerer(Draconic) + shield"
    (let [built (entity/build natural-armor-barb-sorc-entity homebrew-ac-template)]
      ;; Modifier chain: race sets natural-ac-bonus=3, then draconic
      ;; overwrites it to 3 (same value, last-write-wins).
      ;; Barbarian sets unarmored-ac-bonus=CON(+2).
      (is (= 3 (ac-bonus built :natural-ac-bonus))
          "natural AC bonus = 3 (race feat and draconic both set 3)")
      (is (= 2 (ac-bonus built :unarmored-ac-bonus))
          "Barbarian CON +2")
      (is (= 2 (ac-bonus built :unarmored-with-shield-ac-bonus))
          "Barbarian CON +2 with shield")

      ;; RAW: max(Natural=15, Barbarian=14, Draconic=15) = 15
      ;; BUG: base=10+DEX(2)+natural(3)=15, unarmored=15+CON(2)=17
      (is (= 15 (unarmored-ac built))
          (str "RAW: best of 3 formulas = 15, got " (unarmored-ac built)))

      ;; Displayed AC through the :lizardfolk-ac override (stale closure).
      ;; The override captures entity at race-modifier time (before
      ;; barbarian CON and draconic natural-ac are applied).
      (is (= 15 (displayed-ac built))
          (str "displayed AC should be 15, got " (displayed-ac built)))

      ;; With shield: RAW max(Natural 15+2=17, Barbarian 14+2=16) = 17
      (is (= 17 (ac-with-shield built))
          (str "with shield: best formula + shield = 17, got "
               (ac-with-shield built))))))
