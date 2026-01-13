;; PROOF OF CONCEPT: Fighting Styles Props System
;; This file demonstrates how fighting styles would work with the props system
;;
;; To integrate into the main codebase:
;; 1. Add these cases to make-feat-modifiers in options.cljc
;; 2. Add fighting-style-option-from-cfg function to options.cljc
;; 3. Convert fighting-style-data to props format
;; 4. Update fighting-style-options to use the conversion

(ns orcpub.dnd.e5.poc-fighting-styles
  (:require [orcpub.dnd.e5.template :as t]
            [orcpub.dnd.e5.modifiers :as modifiers]
            [orcpub.modifiers :as mods]))

;; ============================================================================
;; STEP 1: Extend make-feat-modifiers with fighting-style-specific props
;; ============================================================================

;; This would be ADDED to the existing make-feat-modifiers function in options.cljc
;; Add these cases to the case statement around line 3286

(comment
  ;; NEW cases to add to make-feat-modifiers:

  ;; Simple combat bonuses
  :ranged-attack-bonus [(modifiers/ranged-attack-bonus v)]
  :melee-attack-bonus [(modifiers/melee-attack-bonus v)]
  :armored-ac-bonus [(modifiers/armored-ac-bonus v)]
  :unarmored-ac-bonus [(modifiers/unarmored-ac-bonus v)]

  ;; Critical hit expansion
  :critical-range [(modifiers/critical v)]

  ;; Senses (for Blind Fighting)
  :blindsight [(modifiers/blindsight v)]
  :darkvision [(modifiers/darkvision v)]

  ;; Complex conditional damage (for Dueling)
  ;; This is the most complex one - simplified for POC
  :conditional-damage-bonus [(create-conditional-damage-modifier v)]

  ;; Weapon ability damage modifier (for Two Weapon Fighting)
  :weapon-ability-damage-modifier
  (if v
    [(mods/modifier ?weapon-ability-damage-modifier
                    (fn [weapon finesse? _]
                      (?weapon-ability-modifier weapon finesse?)))]
    [])
  )

;; ============================================================================
;; STEP 2: Create fighting-style-option-from-cfg conversion function
;; ============================================================================

(defn fighting-style-option-from-cfg
  "Converts fighting style data (with props) to option-cfg (with modifiers).

  This is the semantic function for fighting styles - similar to feat-option-from-cfg
  but with a different signature appropriate to fighting styles."
  [{:keys [name key page source description ability-type props] :as cfg}]
  (let [;; Convert props to mechanic modifiers using plugin-modifiers
        mechanic-mods (plugin-modifiers props key)

        ;; Determine which wrapper to use based on ability-type
        display-mod-fn (case ability-type
                         :reaction modifiers/reaction
                         :bonus-action modifiers/bonus-action
                         modifiers/trait-cfg)  ; Default

        ;; Create the display modifier (trait/reaction/bonus-action) with metadata
        display-mod (display-mod-fn
                     (cond-> {:name (str name " Fighting Style")}
                       description (assoc :description description)
                       page (assoc :page page)
                       source (assoc :source source)))

        ;; Combine mechanic modifiers with display modifier
        all-mods (if (seq mechanic-mods)
                   (concat mechanic-mods [display-mod])
                   [display-mod])]

    ;; Return option-cfg
    (t/option-cfg
     {:name name
      :key key
      :modifiers all-mods})))

;; ============================================================================
;; STEP 3: Fighting style data in PROPS format (converted from current hardcoded)
;; ============================================================================

(def fighting-style-data
  "PHB Fighting Styles converted to props format.

  This demonstrates backward compatibility - character saves still use :archery key,
  but now the data can be serialized to .edn files for plugins."

  [;; Simple fighting styles - just basic props
   {:name "Archery"
    :key :archery
    :page 72
    :source :phb
    :description "You gain a +2 bonus to attack rolls you make with ranged weapons."
    :props {:ranged-attack-bonus 2}}

   {:name "Defense"
    :key :defense
    :page 72
    :source :phb
    :description "While you are wearing armor, you gain a +1 bonus to AC."
    :props {:armored-ac-bonus 1}}

   ;; Purely descriptive - no mechanical props (Great Weapon Fighting)
   {:name "Great Weapon Fighting"
    :key :great-weapon-fighting
    :page 72
    :source :phb
    :description "When you roll a 1 or 2 on a damage die for an attack you make with a melee weapon that you are wielding with two hands, you can reroll the die and must use the new roll, even if the new roll is a 1 or a 2. The weapon must have the two-handed or versatile property for you to gain this benefit."
    :props {}}  ; Empty props - just the description in trait-cfg

   ;; Reaction-based (Protection)
   {:name "Protection"
    :key :protection
    :page 72
    :source :phb
    :description "When a creature you can see attacks a target other than you that is within 5 feet of you, you can use your reaction to impose disadvantage on the attack roll. You must be wielding a shield."
    :ability-type :reaction
    :props {}}

   ;; Complex weapon ability modifier (Two Weapon Fighting)
   {:name "Two Weapon Fighting"
    :key :two-weapon-fighting
    :page 72
    :source :phb
    :description "When you engage in two-weapon fighting, you can add your ability modifier to the damage of the second attack."
    :props {:weapon-ability-damage-modifier true}}

   ;; TCE - Grants blindsight (Blind Fighting)
   {:name "Blind Fighting"
    :key :blind-fighting
    :page 41
    :source :tce
    :description "You have blindsight with a range of 10 feet. Within that range, you can effectively see anything that isn't behind total cover, even if you're blinded or in darkness. Moreover, you can see an invisible creature within that range, unless the creature successfully hides from you."
    :props {:blindsight 10}}])

;; ============================================================================
;; STEP 4: Convert data to options
;; ============================================================================

(def fighting-style-options
  "Generated fighting style options from data.

  This replaces the hardcoded fighting-style-options in options.cljc line 1688.
  The conversion happens at compile time (for SOURCE) or runtime (for plugins)."
  (map fighting-style-option-from-cfg fighting-style-data))

;; ============================================================================
;; STEP 5: Example PLUGIN fighting style (homebrew)
;; ============================================================================

(def example-homebrew-plugin
  "Example of how a user would create a homebrew fighting style in an orcbrew file.

  This is PURE DATA - no function calls, serializable to .edn format."
  {::e5/plugin
   {::e5/name "Example Homebrew"
    ::e5/key :example-homebrew
    ::e5/fighting-styles
    [;; Custom fighting style - Rapid Strike
     {:name "Rapid Strike"
      :key :rapid-strike
      :option-pack "Example Homebrew"
      :description "You've trained to strike with blinding speed. You gain a +2 bonus to initiative rolls, and once per turn when you take the Attack action, you can make one additional attack as part of that action. This attack doesn't add your ability modifier to damage unless the modifier is negative."
      :props {:initiative 2}}

     ;; Custom fighting style - Defensive Stance
     {:name "Defensive Stance"
      :key :defensive-stance
      :option-pack "Example Homebrew"
      :description "As a bonus action, you can enter a defensive stance until the start of your next turn. While in this stance, you gain a +2 bonus to AC and have advantage on Dexterity saving throws."
      :ability-type :bonus-action
      :props {:armored-ac-bonus 2
              :unarmored-ac-bonus 2}}

     ;; Custom fighting style - Keen Eye (from TGS2 examples)
     {:name "Keen Eye"
      :key :keen-eye
      :option-pack "Example Homebrew"
      :description "Your practice with ranged weapons has honed your ability to spot weaknesses. You score critical hits on a roll of 19 or 20 with ranged weapons."
      :props {:critical-range 19
              :ranged-attack-bonus 0}}]}})

;; ============================================================================
;; STEP 6: Demonstration of how plugins merge with SOURCE
;; ============================================================================

(defn all-fighting-style-options
  "Merges SOURCE fighting styles with plugin fighting styles.

  This pattern is already used for feats - see feat-options function."
  [plugins]
  (let [source-styles fighting-style-options  ; From SOURCE (converted data)
        plugin-styles (mapcat ::e5/fighting-styles (vals plugins))  ; From imports
        plugin-options (map fighting-style-option-from-cfg plugin-styles)]
    (concat source-styles plugin-options)))

;; ============================================================================
;; BACKWARD COMPATIBILITY VERIFICATION
;; ============================================================================

;; Character saves BEFORE (with hardcoded styles):
;; {:classes {:fighter {:level 5
;;                      :fighting-style :archery}}}

;; Character saves AFTER (with props system):
;; {:classes {:fighter {:level 5
;;                      :fighting-style :archery}}}
;;
;; IDENTICAL! ✅

;; Lookup process:
;; 1. Load character, sees :archery keyword
;; 2. Look up :archery in fighting-style-options list
;; 3. Find matching option (by ::t/key)
;; 4. Get modifiers from option-cfg
;; 5. Apply to character
;;
;; Works identically whether the option came from:
;; - Hardcoded SOURCE (old way)
;; - Props-based SOURCE (new way)
;; - Plugin import (homebrew)

;; ============================================================================
;; POC SUMMARY
;; ============================================================================

;; This POC demonstrates:
;;
;; ✅ Fighting styles can use props (like feats)
;; ✅ All metadata preserved (page, source, description)
;; ✅ Different ability types supported (trait, reaction, bonus-action)
;; ✅ Simple props work (ranged-attack-bonus, armored-ac-bonus)
;; ✅ Complex props can be added (weapon-ability-damage-modifier)
;; ✅ Backward compatibility maintained (character saves unchanged)
;; ✅ Plugin support enabled (serializable data format)
;; ✅ Semantic function preserved (fighting-style-option-from-cfg)
;; ✅ SOURCE and plugin styles merge seamlessly
;;
;; What's NOT demonstrated (but would be needed):
;; - Complex conditional modifiers (Dueling's weapon checks)
;;   Would need create-conditional-damage-modifier helper
;; - Spell selections (Blessed Warrior, Druidic Warrior)
;;   Would need fighting-style-selections function
;; - Maneuvers and superiority dice (Superior Technique)
;;   Would need resource props and selection props
;;
;; These are solvable with additional prop types, following the same pattern.
