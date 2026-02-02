(ns orcpub.dnd.e5.magic-items-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.common :as common]))

;;; ---------------------------------------------------------------------------
;;; Magic item modifier integration tests
;;;
;;; Tests that magic item modifiers (ability overrides, saving throw bonuses,
;;; damage resistances, speed, darkvision, skill bonuses, spell modifiers,
;;; AC bonuses) produce correct values when built into a character entity.
;;;
;;; Approach: inject modifiers into custom race configs (same pattern as
;;; ac_test.clj) rather than going through the equipment system. This
;;; isolates the modifier functions from the equip/attune flow.
;;; ---------------------------------------------------------------------------

;;; ---------------------------------------------------------------------------
;;; Shared infrastructure
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

;;; ---------------------------------------------------------------------------
;;; Base race (no modifiers) for control group
;;; ---------------------------------------------------------------------------

(def base-race-cfg
  {:name "Test Human"
   :key :test-human
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers []})

;;; ---------------------------------------------------------------------------
;;; Custom races simulating magic item modifiers
;;; ---------------------------------------------------------------------------

;; Belt of Hill Giant Strength: ability-override STR 21
(def belt-of-hill-giant-str-race
  {:name "Hill Giant Belt Bearer"
   :key :hill-giant-belt-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/ability-override ::char5e/str 21)]})

;; Amulet of Health: ability-override CON 19
(def amulet-of-health-race
  {:name "Amulet of Health Bearer"
   :key :amulet-of-health-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/ability-override ::char5e/con 19)]})

;; Gauntlets of Ogre Power: ability-override STR 19
(def gauntlets-of-ogre-power-race
  {:name "Gauntlets Bearer"
   :key :gauntlets-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/ability-override ::char5e/str 19)]})

;; Belt of Giant Strength STR 21 + Gauntlets of Ogre Power STR 19 (stacking)
(def belt-plus-gauntlets-race
  {:name "Belt+Gauntlets Bearer"
   :key :belt-gauntlets-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/ability-override ::char5e/str 21)
               (mod5e/ability-override ::char5e/str 19)]})

;; Belt of Dwarvenkind: CON +2, darkvision 60, language dwarvish
(def belt-of-dwarvenkind-race
  {:name "Dwarvenkind Belt Bearer"
   :key :dwarvenkind-belt-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/ability ::char5e/con 2)
               (mod5e/darkvision 60)
               (mod5e/language :dwarvish)
               (mod5e/saving-throw-advantage ["poison"])]})

;; Ring of Protection: saving-throw-bonuses +1
(def ring-of-protection-race
  {:name "Ring of Protection Bearer"
   :key :ring-protection-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/saving-throw-bonuses 1)]})

;; Cloak of Protection: saving-throw-bonuses +1
(def cloak-of-protection-race
  {:name "Cloak of Protection Bearer"
   :key :cloak-protection-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/saving-throw-bonuses 1)]})

;; Ring + Cloak of Protection stacked: saving-throw-bonuses +1 each = +2
(def ring-plus-cloak-race
  {:name "Ring+Cloak Bearer"
   :key :ring-cloak-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/saving-throw-bonuses 1)
               (mod5e/saving-throw-bonuses 1)]})

;; Staff of Fire: damage-resistance fire
(def staff-of-fire-race
  {:name "Staff of Fire Bearer"
   :key :staff-fire-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/damage-resistance :fire)]})

;; Brooch of Shielding: damage-resistance force + damage-immunity magic-missile
(def brooch-of-shielding-race
  {:name "Brooch of Shielding Bearer"
   :key :brooch-shielding-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/damage-resistance :force)
               (mod5e/damage-immunity :magic-missile)]})

;; Boots of Striding and Springing: speed-override 30
(def boots-of-striding-race
  {:name "Boots of Striding Bearer"
   :key :boots-striding-bearer
   :abilities {}
   :size :medium
   :speed 25  ;; dwarf-like base speed to test override
   :languages ["Common"]
   :modifiers [(mod5e/speed-override 30)]})

;; Cloak of the Manta Ray: swimming-speed 60
(def cloak-of-manta-ray-race
  {:name "Cloak of Manta Ray Bearer"
   :key :cloak-manta-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/swimming-speed 60)]})

;; Goggles of Night: darkvision-bonus +60
(def goggles-of-night-race
  {:name "Goggles of Night Bearer"
   :key :goggles-night-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/darkvision-bonus 60)]})

;; Belt of Dwarvenkind (darkvision 60) + Goggles of Night (+60 bonus)
(def belt-plus-goggles-race
  {:name "Belt+Goggles Bearer"
   :key :belt-goggles-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/darkvision 60)
               (mod5e/darkvision-bonus 60)]})

;; Gloves of Thievery: skill-bonus sleight-of-hand +5
(def gloves-of-thievery-race
  {:name "Gloves of Thievery Bearer"
   :key :gloves-thievery-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/skill-bonus :sleight-of-hand 5)]})

;; Stone of Good Luck (Luckstone): all-skills-bonus +1
(def luckstone-race
  {:name "Luckstone Bearer"
   :key :luckstone-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/all-skills-bonus 1)]})

;; Rod of the Pact Keeper +1: spell-save-dc-bonus 1, spell-attack-modifier-bonus 1
(def rod-of-pact-keeper-race
  {:name "Rod of Pact Keeper Bearer"
   :key :rod-pact-keeper-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/spell-save-dc-bonus 1)
               (mod5e/spell-attack-modifier-bonus 1)]})

;; Robe of the Archmagi spell bonuses: spell-save-dc-bonus 2, spell-attack-modifier-bonus 2
(def robe-spell-bonus-race
  {:name "Robe Spell Bonus Bearer"
   :key :robe-spell-bonus-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/spell-save-dc-bonus 2)
               (mod5e/spell-attack-modifier-bonus 2)]})

;; Bracers of Defense: unarmored-ac-bonus +2
(def bracers-of-defense-race
  {:name "Bracers of Defense Bearer"
   :key :bracers-defense-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/unarmored-ac-bonus 2)]})

;; Staff of Power: ac-bonus-fn always +2
(def staff-of-power-race
  {:name "Staff of Power Bearer"
   :key :staff-power-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/ac-bonus-fn (fn [_ _] 2))]})

;; Multiple resistances: fire + cold + poison (simulating multiple items)
(def multi-resistance-race
  {:name "Multi-Resistance Bearer"
   :key :multi-resist-bearer
   :abilities {}
   :size :medium
   :speed 30
   :languages ["Common"]
   :modifiers [(mod5e/damage-resistance :fire)
               (mod5e/damage-resistance :cold)
               (mod5e/damage-resistance :poison)]})

;;; ---------------------------------------------------------------------------
;;; Template with all custom races
;;; ---------------------------------------------------------------------------

(def all-races
  [base-race-cfg
   belt-of-hill-giant-str-race
   amulet-of-health-race
   gauntlets-of-ogre-power-race
   belt-plus-gauntlets-race
   belt-of-dwarvenkind-race
   ring-of-protection-race
   cloak-of-protection-race
   ring-plus-cloak-race
   staff-of-fire-race
   brooch-of-shielding-race
   boots-of-striding-race
   cloak-of-manta-ray-race
   goggles-of-night-race
   belt-plus-goggles-race
   gloves-of-thievery-race
   luckstone-race
   rod-of-pact-keeper-race
   robe-spell-bonus-race
   bracers-of-defense-race
   staff-of-power-race
   multi-resistance-race])

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
    all-races                                 ; races
    [(classes5e/barbarian-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/monk-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/sorcerer-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
     (classes5e/warlock-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map [] [])]
    nil                                       ; feats
    language-map)))

;;; ---------------------------------------------------------------------------
;;; Entity builder helper
;;; ---------------------------------------------------------------------------

(defn make-entity
  "Build entity with given race-key and class config.
   class-cfg is a vector of class option maps."
  [race-key ability-scores class-cfg]
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key race-key}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value ability-scores}
    :class class-cfg}})

;; Default ability scores: all 10s, straightforward modifiers (+0)
(def default-abilities
  {::char5e/str 10
   ::char5e/dex 10
   ::char5e/con 10
   ::char5e/int 10
   ::char5e/wis 10
   ::char5e/cha 10})

;; Base fighter-like class config (no special AC or ability features)
(def barbarian-class
  [{:orcpub.entity/key :barbarian
    :orcpub.entity/options
    {:skill-proficiency
     [{:orcpub.entity/key :athletics}
      {:orcpub.entity/key :survival}]
     :levels
     [{:orcpub.entity/key :level-1}]}}])

(def monk-class
  [{:orcpub.entity/key :monk
    :orcpub.entity/options
    {:skill-proficiency
     [{:orcpub.entity/key :acrobatics}
      {:orcpub.entity/key :insight}]
     :levels
     [{:orcpub.entity/key :level-1}]}}])

(def warlock-class
  [{:orcpub.entity/key :warlock
    :orcpub.entity/options
    {:skill-proficiency
     [{:orcpub.entity/key :arcana}
      {:orcpub.entity/key :deception}]
     :levels
     [{:orcpub.entity/key :level-1
       :orcpub.entity/options
       {:pact-magic
        [{:orcpub.entity/key :eldritch-blast}
         {:orcpub.entity/key :minor-illusion}]}}]}}])

(defn build [race-key abilities classes]
  (entity/build (make-entity race-key abilities classes) test-template))

(defn get-prop [built k]
  (char5e/get-prop built k))

(defn displayed-ac
  "AC from ?armor-class-with-armor called with no armor/shield."
  [built]
  (let [ac-fn (get-prop built :armor-class-with-armor)]
    (ac-fn nil)))

;;; ===========================================================================
;;; TESTS
;;; ===========================================================================

;;; ---------------------------------------------------------------------------
;;; 1. Ability score overrides
;;; ---------------------------------------------------------------------------

(deftest ability-override-sets-minimum
  (testing "Belt of Hill Giant Strength sets STR to at least 21"
    (let [;; Base STR 10, override to 21
          built (build :hill-giant-belt-bearer default-abilities barbarian-class)
          abilities (char5e/ability-values built)]
      (is (= 21 (::char5e/str abilities))
          "STR should be overridden to 21")
      ;; Other abilities unaffected
      (is (= 10 (::char5e/dex abilities))
          "DEX should remain 10"))))

(deftest ability-override-does-not-lower
  (testing "Gauntlets of Ogre Power (STR 19) don't lower STR 20"
    (let [;; Base STR 20 > override 19, so override has no effect
          abilities (assoc default-abilities ::char5e/str 20)
          built (build :gauntlets-bearer abilities barbarian-class)
          result (char5e/ability-values built)]
      (is (= 20 (::char5e/str result))
          "STR 20 should not be lowered by override 19"))))

(deftest ability-override-multiple-pick-highest
  (testing "Belt STR 21 + Gauntlets STR 19 → STR 21 (pick highest override)"
    (let [built (build :belt-gauntlets-bearer default-abilities barbarian-class)
          abilities (char5e/ability-values built)]
      (is (= 21 (::char5e/str abilities))
          "Multiple overrides: pick highest (21)"))))

(deftest ability-override-affects-derived-stats
  (testing "Amulet of Health CON 19 affects HP and save bonus"
    (let [built (build :amulet-of-health-bearer default-abilities barbarian-class)
          abilities (char5e/ability-values built)
          bonuses (char5e/ability-bonuses built)]
      (is (= 19 (::char5e/con abilities))
          "CON should be 19")
      (is (= 4 (::char5e/con bonuses))
          "CON 19 → modifier +4"))))

;;; ---------------------------------------------------------------------------
;;; 2. Ability score bonuses (additive)
;;; ---------------------------------------------------------------------------

(deftest ability-bonus-additive
  (testing "Belt of Dwarvenkind CON +2 adds to base"
    (let [;; Base CON 14, +2 from belt = 16
          abilities (assoc default-abilities ::char5e/con 14)
          built (build :dwarvenkind-belt-bearer abilities barbarian-class)
          result (char5e/ability-values built)]
      (is (= 16 (::char5e/con result))
          "CON 14 + 2 (belt) = 16"))))

;;; ---------------------------------------------------------------------------
;;; 3. Saving throw bonuses
;;; ---------------------------------------------------------------------------

(deftest saving-throw-bonus-single-item
  (testing "Ring of Protection +1 to all saving throws"
    (let [built (build :ring-protection-bearer default-abilities barbarian-class)
          save-bonuses (char5e/save-bonuses built)]
      ;; Barbarian is proficient in STR and CON saves.
      ;; All abilities have +0 modifier (scores are 10).
      ;; Prof bonus at level 1 = +2.
      ;; Ring adds +1 to all saves.
      ;; Proficient save: 0 (ability) + 2 (prof) + 1 (ring) = 3
      ;; Non-proficient save: 0 (ability) + 0 (prof) + 1 (ring) = 1
      (is (= 3 (::char5e/str save-bonuses))
          "STR save: proficient (+2) + ring (+1) = 3")
      (is (= 3 (::char5e/con save-bonuses))
          "CON save: proficient (+2) + ring (+1) = 3")
      (is (= 1 (::char5e/dex save-bonuses))
          "DEX save: not proficient, ring (+1) = 1")
      (is (= 1 (::char5e/wis save-bonuses))
          "WIS save: not proficient, ring (+1) = 1"))))

(deftest saving-throw-bonus-stacking
  (testing "Ring + Cloak of Protection stack: +2 to all saves"
    (let [built (build :ring-cloak-bearer default-abilities barbarian-class)
          save-bonuses (char5e/save-bonuses built)]
      ;; Proficient: 0 + 2 (prof) + 2 (ring+cloak) = 4
      ;; Non-proficient: 0 + 0 + 2 = 2
      (is (= 4 (::char5e/str save-bonuses))
          "STR save: proficient (+2) + items (+2) = 4")
      (is (= 2 (::char5e/dex save-bonuses))
          "DEX save: not proficient, items (+2) = 2"))))

;;; ---------------------------------------------------------------------------
;;; 4. Damage resistances and immunities
;;; ---------------------------------------------------------------------------

(deftest damage-resistance-single
  (testing "Staff of Fire grants fire resistance"
    (let [built (build :staff-fire-bearer default-abilities barbarian-class)
          resistances (char5e/damage-resistances built)]
      (is (some #(= :fire (:value %)) resistances)
          "Should have fire resistance"))))

(deftest damage-resistance-and-immunity
  (testing "Brooch of Shielding: force resistance + magic-missile immunity"
    (let [built (build :brooch-shielding-bearer default-abilities barbarian-class)
          resistances (char5e/damage-resistances built)
          immunities (char5e/damage-immunities built)]
      (is (some #(= :force (:value %)) resistances)
          "Should have force resistance")
      (is (some #(= :magic-missile (:value %)) immunities)
          "Should have magic-missile immunity"))))

(deftest damage-resistance-multiple
  (testing "Multiple resistance sources: fire, cold, poison"
    (let [built (build :multi-resist-bearer default-abilities barbarian-class)
          resistances (char5e/damage-resistances built)
          res-values (set (map :value resistances))]
      (is (res-values :fire) "Should have fire resistance")
      (is (res-values :cold) "Should have cold resistance")
      (is (res-values :poison) "Should have poison resistance"))))

;;; ---------------------------------------------------------------------------
;;; 5. Speed modifiers
;;; ---------------------------------------------------------------------------

(deftest speed-override-raises-minimum
  (testing "Boots of Striding and Springing: speed override to 30 from base 25"
    (let [built (build :boots-striding-bearer default-abilities barbarian-class)
          speed (char5e/base-land-speed built)]
      ;; Race has base speed 25, boots override to 30
      ;; Speed = max(base, ...overrides) = max(25, 30) = 30
      (is (= 30 speed)
          "Speed should be overridden to 30"))))

(deftest swimming-speed-from-item
  (testing "Cloak of the Manta Ray: swimming speed 60"
    (let [built (build :cloak-manta-bearer default-abilities barbarian-class)
          swim-speed (char5e/base-swimming-speed built)]
      (is (= 60 swim-speed)
          "Swimming speed should be 60"))))

;;; ---------------------------------------------------------------------------
;;; 6. Darkvision
;;; ---------------------------------------------------------------------------

(deftest darkvision-from-item
  (testing "Belt of Dwarvenkind grants darkvision 60"
    (let [built (build :dwarvenkind-belt-bearer default-abilities barbarian-class)
          dv (char5e/darkvision built)]
      (is (= 60 dv)
          "Should have darkvision 60 from belt"))))

(deftest darkvision-bonus-stacks
  (testing "Goggles of Night +60 darkvision bonus"
    (let [;; No base darkvision, goggles add +60 bonus
          built (build :goggles-night-bearer default-abilities barbarian-class)
          dv (char5e/darkvision built)]
      ;; Base darkvision is 0 (human-like race), bonus adds 60
      (is (= 60 dv)
          "Darkvision bonus should be 60 (0 base + 60 bonus)"))))

(deftest darkvision-base-plus-bonus
  (testing "Belt (darkvision 60) + Goggles (+60 bonus) = 120"
    (let [built (build :belt-goggles-bearer default-abilities barbarian-class)
          dv (char5e/darkvision built)]
      (is (= 120 dv)
          "60 base + 60 bonus = 120"))))

;;; ---------------------------------------------------------------------------
;;; 7. Skill bonuses
;;; ---------------------------------------------------------------------------

(deftest skill-bonus-single-skill
  (testing "Gloves of Thievery: +5 to sleight of hand"
    (let [built (build :gloves-thievery-bearer default-abilities barbarian-class)
          skill-bonuses (char5e/skill-bonuses built)
          soh-bonus (:sleight-of-hand skill-bonuses)
          athletics-bonus (:athletics skill-bonuses)]
      ;; Sleight of hand: DEX mod (0) + gloves (+5) = 5
      ;; Athletics: STR mod (0) + proficiency (+2, barbarian) = 2
      (is (= 5 soh-bonus)
          "Sleight of hand should be +5 (DEX 0 + gloves 5)")
      (is (= 2 athletics-bonus)
          "Athletics should be +2 (STR 0 + proficiency 2, unaffected by gloves)"))))

(deftest skill-bonus-all-skills
  (testing "Luckstone: +1 to all skill checks"
    (let [built (build :luckstone-bearer default-abilities barbarian-class)
          skill-bonuses (char5e/skill-bonuses built)
          athletics-bonus (:athletics skill-bonuses)
          arcana-bonus (:arcana skill-bonuses)]
      ;; Athletics: STR mod (0) + proficiency (+2) + luckstone (+1) = 3
      ;; Arcana: INT mod (0) + no proficiency + luckstone (+1) = 1
      (is (= 3 athletics-bonus)
          "Athletics: STR(0) + prof(2) + luckstone(1) = 3")
      (is (= 1 arcana-bonus)
          "Arcana: INT(0) + luckstone(1) = 1"))))

;;; ---------------------------------------------------------------------------
;;; 8. Spell attack / save DC bonuses
;;; ---------------------------------------------------------------------------

(deftest spell-save-dc-bonus
  (testing "Rod of Pact Keeper +1 to spell save DC and attack"
    (let [;; Warlock with CHA 14 (+2)
          abilities (assoc default-abilities ::char5e/cha 14)
          built (build :rod-pact-keeper-bearer abilities warlock-class)
          ;; Spell save DC = 8 + prof(2) + CHA(+2) + rod(+1) = 13
          ;; Spell attack = prof(2) + CHA(+2) + rod(+1) = 5
          dc-fn (get-prop built :spell-save-dc)
          atk-fn (get-prop built :spell-attack-modifier)]
      (is (= 13 (dc-fn ::char5e/cha))
          "Spell save DC: 8 + prof(2) + CHA(2) + rod(1) = 13")
      (is (= 5 (atk-fn ::char5e/cha))
          "Spell attack: prof(2) + CHA(2) + rod(1) = 5"))))

(deftest spell-modifier-stacking
  (testing "Robe of Archmagi +2 spell bonuses"
    (let [abilities (assoc default-abilities ::char5e/cha 14)
          built (build :robe-spell-bonus-bearer abilities warlock-class)
          ;; Spell save DC = 8 + prof(2) + CHA(+2) + robe(+2) = 14
          ;; Spell attack = prof(2) + CHA(+2) + robe(+2) = 6
          dc-fn (get-prop built :spell-save-dc)
          atk-fn (get-prop built :spell-attack-modifier)]
      (is (= 14 (dc-fn ::char5e/cha))
          "Spell save DC: 8 + prof(2) + CHA(2) + robe(2) = 14")
      (is (= 6 (atk-fn ::char5e/cha))
          "Spell attack: prof(2) + CHA(2) + robe(2) = 6"))))

;;; ---------------------------------------------------------------------------
;;; 9. AC bonus items
;;; ---------------------------------------------------------------------------

(deftest bracers-of-defense-unarmored-ac
  (testing "Bracers of Defense +2 unarmored AC"
    (let [;; DEX 14 (+2): base unarmored = 10 + 2 = 12, bracers add +2 = 14
          abilities (assoc default-abilities ::char5e/dex 14)
          built (build :bracers-defense-bearer abilities barbarian-class)
          unarmored (get-prop built :unarmored-armor-class)]
      ;; Barbarian CON 10 (+0): unarmored-ac-bonus = 0 (barbarian) + 2 (bracers) = 2
      ;; Unarmored AC = 10 + DEX(2) + unarmored(2) = 14
      (is (= 14 unarmored)
          "Bracers: 10 + DEX(2) + bracers(2) = 14"))))

(deftest bracers-of-defense-with-barbarian
  (testing "Bracers of Defense stack with Barbarian Unarmored Defense"
    (let [;; DEX 14 (+2), CON 14 (+2): barbarian + bracers
          abilities (assoc default-abilities ::char5e/dex 14 ::char5e/con 14)
          built (build :bracers-defense-bearer abilities barbarian-class)
          unarmored (get-prop built :unarmored-armor-class)]
      ;; unarmored-ac-bonus: CON(+2) from barbarian + bracers(+2) = 4
      ;; Unarmored AC = 10 + DEX(2) + unarmored(4) = 16
      (is (= 16 unarmored)
          "Barbarian + Bracers: 10 + DEX(2) + CON(2) + bracers(2) = 16"))))

(deftest staff-of-power-ac-bonus
  (testing "Staff of Power +2 AC always-on via ac-bonus-fn"
    (let [abilities (assoc default-abilities ::char5e/dex 14)
          built (build :staff-power-bearer abilities barbarian-class)
          ac (displayed-ac built)]
      ;; Barbarian CON 10 (+0): unarmored = 10 + DEX(2) + 0 = 12
      ;; Staff of Power adds +2 via ac-bonus-fn (additive, not formula)
      ;; Displayed AC = 12 + 2 = 14
      (is (= 14 ac)
          "Staff of Power: base(12) + staff(+2) = 14"))))

;;; ---------------------------------------------------------------------------
;;; 10. Multi-item stacking interactions
;;; ---------------------------------------------------------------------------

(deftest ability-override-plus-bonus-interaction
  (testing "Amulet of Health (CON 19) with Barbarian Unarmored Defense"
    (let [;; CON overridden to 19 → modifier +4
          ;; Barbarian Unarmored = 10 + DEX + CON
          abilities (assoc default-abilities ::char5e/dex 14)
          built (build :amulet-of-health-bearer abilities barbarian-class)
          con-val (::char5e/con (char5e/ability-values built))
          con-mod (::char5e/con (char5e/ability-bonuses built))
          unarmored (get-prop built :unarmored-armor-class)]
      (is (= 19 con-val)
          "CON should be 19 from Amulet")
      (is (= 4 con-mod)
          "CON 19 → +4 modifier")
      ;; Unarmored AC = 10 + DEX(+2) + CON(+4) = 16
      (is (= 16 unarmored)
          "Barbarian with Amulet: 10 + DEX(2) + CON(4) = 16"))))

;;; ===========================================================================
;;; EQUIP/UNEQUIP TESTS — deferred modifier system
;;;
;;; These tests exercise the real magic item equipment flow:
;;; - Items are included as template options via deferred-magic-item
;;; - Entity selects item with equipped?=true or false
;;; - deferred-magic-item-fn gates modifier application on equipped? flag
;;; ===========================================================================

;;; ---------------------------------------------------------------------------
;;; Magic item option builders (replicates equipment_subs.cljs logic for JVM)
;;; ---------------------------------------------------------------------------

(defn make-magic-item-option
  "Build a template option for a magic item, using the deferred modifier system.
   This replicates what equipment_subs.cljs:magic-item-options does on the
   frontend, but callable from JVM tests."
  [{:keys [name key] :as item}]
  (let [item-key (or key (common/name-to-kw name))
        ;; build-modifiers resolves data-based modifier configs to functions
        full-item (update item ::mi/modifiers mod5e/build-modifiers)]
    (t/option-cfg
     {:name name
      :key item-key
      :modifiers [(mod5e/deferred-magic-item item-key full-item)]})))

;; Test magic items (simplified versions of real items)
(def test-bracers-of-defense
  {:name "Bracers of Defense"
   :key :bracers-of-defense
   ::mi/type :wondrous-item
   ::mi/rarity :rare
   ::mi/attunement [:any]
   ::mi/modifiers [(mod5e/unarmored-ac-bonus 2)]})

(def test-cloak-of-protection
  {:name "Cloak of Protection"
   :key :cloak-of-protection
   ::mi/type :wondrous-item
   ::mi/rarity :uncommon
   ::mi/attunement [:any]
   ::mi/modifiers [(mod5e/saving-throw-bonuses 1)]})

(def test-staff-of-fire
  {:name "Staff of Fire"
   :key :staff-of-fire
   ::mi/type :staff
   ::mi/rarity :very-rare
   ::mi/attunement [:any]
   ::mi/modifiers [(mod5e/damage-resistance :fire)]})

(def test-gauntlets-of-ogre-power
  {:name "Gauntlets of Ogre Power"
   :key :gauntlets-of-ogre-power
   ::mi/type :wondrous-item
   ::mi/rarity :uncommon
   ::mi/attunement [:any]
   ::mi/modifiers [(mod5e/ability-override ::char5e/str 19)]})

;; Template that includes these items as selectable equipment
(def equip-test-template
  (t5e/template
   (t5e/template-selections
    nil                                         ; magic-weapon-options
    nil                                         ; magic-armor-options
    (map make-magic-item-option                 ; other-magic-item-options
         [test-bracers-of-defense
          test-cloak-of-protection
          test-staff-of-fire
          test-gauntlets-of-ogre-power])
    weapons5e/weapons-map
    weapons5e/weapons
    sl5e/spell-lists
    spells5e/spell-map
    nil                                         ; backgrounds
    [base-race-cfg]                             ; races (no modifiers)
    [(classes5e/barbarian-option
      sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)]
    nil                                         ; feats
    language-map)))

(defn make-equip-entity
  "Build entity that selects a magic item, with equipped?=true or false."
  [item-key equipped? & [abilities]]
  {:orcpub.entity/options
   {:race {:orcpub.entity/key :test-human}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value (or abilities default-abilities)}
    :class
    [{:orcpub.entity/key :barbarian
      :orcpub.entity/options
      {:skill-proficiency
       [{:orcpub.entity/key :athletics}
        {:orcpub.entity/key :survival}]
       :levels
       [{:orcpub.entity/key :level-1}]}}]
    :other-magic-items
    [{:orcpub.entity/key item-key
      ;; Value must be a map — deferred-magic-item-fn checks
      ;; (::char-equip/equipped? cfg) directly on the raw value.
      ;; Integer values (e.g. 1) return nil for keyword lookup.
      :orcpub.entity/value {::char-equip/quantity 1
                            ::char-equip/equipped? equipped?}}]}})

(defn build-equip [item-key equipped? & [abilities]]
  (entity/build (make-equip-entity item-key equipped? abilities)
                equip-test-template))

;;; ---------------------------------------------------------------------------
;;; 11. Equip/unequip: modifiers only apply when equipped
;;; ---------------------------------------------------------------------------

(deftest bracers-of-defense-equipped-vs-unequipped
  (testing "Bracers of Defense: +2 unarmored AC only when equipped"
    (let [equipped (build-equip :bracers-of-defense true)
          unequipped (build-equip :bracers-of-defense false)
          eq-ac (get-prop equipped :unarmored-armor-class)
          uneq-ac (get-prop unequipped :unarmored-armor-class)]
      ;; All abilities are 10 (+0). Barbarian CON +0.
      ;; Base unarmored: 10 + DEX(0) + CON(0) = 10
      ;; Equipped: 10 + bracers(2) = 12
      ;; Unequipped: 10
      (is (= 12 eq-ac)
          "Equipped bracers: 10 + bracers(2) = 12")
      (is (= 10 uneq-ac)
          "Unequipped bracers: no AC bonus, stays 10")
      (is (= 2 (- eq-ac uneq-ac))
          "Difference should be exactly +2"))))

(deftest cloak-of-protection-equipped-vs-unequipped
  (testing "Cloak of Protection: +1 all saves only when equipped"
    (let [equipped (build-equip :cloak-of-protection true)
          unequipped (build-equip :cloak-of-protection false)
          eq-saves (char5e/save-bonuses equipped)
          uneq-saves (char5e/save-bonuses unequipped)]
      ;; Barbarian proficient: STR, CON. All abilities 10 (+0). Prof +2.
      ;; Equipped: proficient = 0+2+1=3, non-prof = 0+0+1=1
      ;; Unequipped: proficient = 0+2=2, non-prof = 0+0=0
      (is (= 3 (::char5e/str eq-saves))
          "Equipped: STR save = prof(2) + cloak(1) = 3")
      (is (= 2 (::char5e/str uneq-saves))
          "Unequipped: STR save = prof(2) = 2")
      (is (= 1 (::char5e/dex eq-saves))
          "Equipped: DEX save = cloak(1) = 1")
      (is (= 0 (::char5e/dex uneq-saves))
          "Unequipped: DEX save = 0"))))

(deftest staff-of-fire-equipped-vs-unequipped
  (testing "Staff of Fire: fire resistance only when equipped"
    (let [equipped (build-equip :staff-of-fire true)
          unequipped (build-equip :staff-of-fire false)
          eq-res (char5e/damage-resistances equipped)
          uneq-res (char5e/damage-resistances unequipped)]
      (is (some #(= :fire (:value %)) eq-res)
          "Equipped: should have fire resistance")
      (is (not (some #(= :fire (:value %)) uneq-res))
          "Unequipped: should NOT have fire resistance"))))

(deftest gauntlets-equipped-vs-unequipped
  (testing "Gauntlets of Ogre Power: STR 19 override only when equipped"
    (let [equipped (build-equip :gauntlets-of-ogre-power true)
          unequipped (build-equip :gauntlets-of-ogre-power false)
          eq-str (::char5e/str (char5e/ability-values equipped))
          uneq-str (::char5e/str (char5e/ability-values unequipped))]
      (is (= 19 eq-str)
          "Equipped: STR overridden to 19")
      (is (= 10 uneq-str)
          "Unequipped: STR stays at base 10"))))
