# Feature System Architecture & Catalog

**Created:** 2026-01-11
**Status:** Living Document - Understanding May Evolve
**Purpose:** Document how features work and catalog available modifiers

---

## ⚠️ Important Note

This document represents our **current understanding** of the feature system. As we explore the codebase further, we may discover additional mechanisms, helpers, or patterns. **This is a living document** that should be updated as our understanding grows.

---

## Core Architecture Understanding

### How Features Actually Work

**Initial Misunderstanding:** We thought homebrew needed a new "feature language" to represent complex class features.

**Actual Reality:** The infrastructure already exists. Here's how it works:

```
Built-in Classes                    Homebrew Classes
      ↓                                   ↓
Use modifier functions              Store props as data
(mod5e/bonus-action {...})         {:props {:armor-prof {:light true}}}
      ↓                                   ↓
Create modifiers directly           → plugin-modifiers() converts to modifiers
      ↓                                   ↓
           Both produce the same modifier objects
                          ↓
                Applied to character
```

### The Conversion Pipeline

**For Built-in Classes:**
```clojure
;; In classes.cljc
:modifiers [(mod5e/bonus-action {:name "Rage" :duration ... :frequency ...})]
                ↓
;; Directly creates modifier objects
```

**For Homebrew Classes:**
```clojure
;; Stored in plugins (local storage)
:props {:armor-prof {:light true :medium true}
        :damage-resistance {:bludgeoning true}}
                ↓
;; spell_subs.cljs:421 - plugin-classes subscription
(opt5e/plugin-modifiers (:props class) (:key class))
                ↓
;; options.cljc:3288 - plugin-modifiers function
(make-feat-modifiers k v option-key)  ; for each prop
                ↓
;; options.cljc:3230 - case statement converts props to modifiers
(case k
  :armor-prof (collect-map-modifiers v #(modifiers/armor-proficiency %))
  :weapon-prof (collect-map-modifiers v #(modifiers/weapon-proficiency %))
  ...)
                ↓
;; Creates the same modifier objects as built-in classes
```

### The Key Insight

**Homebrew doesn't need to call the functions directly** - it just needs to:
1. Set flags/keys in `:props` indicating which features it wants
2. Optionally provide configuration data (numbers, strings, booleans)
3. Let the `plugin-modifiers` → `make-feat-modifiers` pipeline convert those props into the actual modifier function calls

**This is already DRY** - the modifier functions exist once, and both built-in and homebrew use them. Homebrew just has an extra conversion layer.

---

## What's Already Exposed

### Currently Supported Props

These props are already wired up in `make-feat-modifiers` (options.cljc:3230-3286):

#### Movement & Senses
- `:speed` → `(modifiers/speed v)`
- `:flying-speed` → `(modifiers/flying-speed-override v)`
- `:flying-speed-equals-walking-speed` → `(modifiers/flying-speed-equal-to-walking)`
- `:swimming-speed` → `(modifiers/swimming-speed-override v)`

#### Abilities & Combat
- `:initiative` → `(modifiers/initiative v)`
- `:max-hp-bonus` → Custom modifier for hit points
- `:passive-investigation-5` → `(modifiers/passive-investigation 5)`
- `:passive-perception-5` → `(modifiers/passive-perception 5)`

#### Proficiencies
- `:armor-prof` → `(modifiers/armor-proficiency %)`
  - Map format: `{:light true :medium true :heavy true :shields true}`
- `:weapon-prof` → `(modifiers/weapon-proficiency %)`
  - Map format: `{:simple true :martial true :longsword true}`
- `:skill-prof` → `(modifiers/skill-proficiency %)`
  - Map format: `{:athletics true :stealth true}`
- `:tool-prof-or-expertise` → Special modifier
- `:skill-prof-or-expertise` → Special modifier
- `:language` → `(modifiers/language %)`
  - Map format: `{:elvish true :dwarvish true}`

#### Defenses
- `:damage-resistance` → `(modifiers/damage-resistance %)`
  - Map format: `{:fire true :cold true :bludgeoning true}`
- `:damage-immunity` → `(modifiers/damage-immunity %)`
  - Map format: `{:poison true :psychic true}`
- `:saving-throw-advantage` → `(modifiers/saving-throw-advantage [%])`
  - Map format: `{:poison true :charm true}`
- `:saving-throw-advantage-traps` → `(modifiers/saving-throw-advantage [:traps])`

#### Armor Class
- `:medium-armor-max-dex-3` → Medium Armor Master max DEX bonus
- `:medium-armor-stealth` → Medium Armor Master stealth benefit
- `:lizardfolk-ac` → Lizardfolk natural armor (if v is truthy)
- `:tortle-ac` → Tortle shell armor (if v is truthy)

#### Dual Wielding
- `:two-weapon-ac-1` → AC bonus when dual wielding
- `:two-weapon-any-one-handed` → Dual wielder weapon flexibility

#### Special Cases
- `:weapon-prof-choice` → Language/weapon prof selection (creates selection UI)
- `:language-choice` → Language selection (creates selection UI)
- `:skill-tool-choice` → Skill or tool selection (creates selection UI)
- `:ritual-casting` → Ritual Caster feat mechanics
- `:magic-novice` → Magic Initiate feat mechanics
- `:attack-spell` → Spell Sniper feat mechanics

---

## What's NOT Yet Exposed

### Class-Specific Features

These modifier functions exist but are **not yet wired into `make-feat-modifiers`**:

#### Actions & Features
- `mod5e/action` - Add action to character sheet
- `mod5e/bonus-action` - Add bonus action (like Rage, Cunning Action)
- `mod5e/reaction` - Add reaction (like Opportunity Attack variants)
- `mod5e/dependent-trait` - Trait with dynamic text based on character state

#### Combat Features
- `mod5e/num-attacks` - Set number of attacks (Extra Attack)
- `mod5e/critical` - Modify critical hit mechanics
- `mod5e/attack` - Add special attack
- `mod/modifier` with `?unarmored-ac-bonus` - Unarmored Defense calculations
- `mod/vec-mod` with `?unarmored-defense` - Unarmored Defense flag

#### Spellcasting
- `mod5e/spells-known` - Grant specific spells
- `mod5e/spells-known-cfg` - More complex spell granting
- `opt5e/paladin-spell` - Paladin spell at specific level
- `opt5e/cleric-spell` - Cleric domain spell

#### Class-Specific Mechanics
- `opt5e/divine-strike` - Cleric divine strike
- `opt5e/potent-spellcasting` - Cleric potent spellcasting
- `opt5e/evasion` - Rogue/Monk evasion
- `opt5e/uncanny-dodge-modifier` - Rogue uncanny dodge
- `opt5e/fighting-style-selection` - Fighter/Paladin fighting styles
- `opt5e/expertise-selection` - Bard/Rogue expertise
- `opt5e/eldritch-invocation-selection` - Warlock invocations
- `opt5e/monk-elemental-disciplines` - Monk elemental disciplines

#### Selections (Player Choices)
- `opt5e/spell-selection` - Spell selection UI
- `opt5e/tool-selection` - Tool proficiency selection
- `opt5e/language-selection` - Language selection
- `opt5e/simple-weapon-selection` - Weapon selection
- `opt5e/starting-equipment-option` - Equipment choices

---

## Complete Modifier Function Catalog

### Base Modifier System (`orcpub.modifiers`)

Low-level modifier constructors used by all higher-level functions:

- `mod/modifier` - Basic modifier (most common)
- `mod/cum-sum-mod` - Cumulative sum modifier (stacking bonuses)
- `mod/set-mod` - Set value modifier (replaces, doesn't stack)
- `mod/vec-mod` - Vector modifier (for collections)
- `mod/map-mod` - Map modifier (for key-value data)

### D&D 5e Modifiers (`orcpub.dnd.e5.modifiers`)

All available from the `mod5e` namespace (sorted by category):

#### Character Identity
- `alignment` - Set character alignment
- `race` - Set race
- `deferred-race` - Race determined later
- `subrace` - Set subrace
- `deferred-subrace` - Subrace determined later
- `background` - Set background
- `deferred-background` - Background determined later
- `cls` - Set class
- `subclass` - Set subclass by key
- `subclass-name` - Set subclass by name
- `deferred-subclass-name` - Subclass determined later
- `size` - Set creature size

#### Abilities & Stats
- `ability` - Increase ability score
- `conditional-ability` - Conditional ability increase
- `ability-override` - Set ability to specific value
- `level-ability-increase` - Ability increase at specific level
- `race-ability` - Ability increase from race
- `subrace-ability` - Ability increase from subrace
- `abilities` - Set multiple abilities at once
- `deferred-abilities` - Abilities determined later
- `deferred-ability-increases` - Ability increases determined later

#### Saving Throws
- `saving-throws` - Grant saving throw proficiencies
- `saving-throw-bonus` - Bonus to specific save
- `saving-throw-bonuses` - Bonus to all saves
- `saving-throw-advantage` - Advantage on specific saves

#### Skills & Proficiency
- `skill-proficiency` - Grant skill proficiency (macro)
- `skill-expertise` - Grant skill expertise (macro)
- `skill-bonus` - Bonus to specific skill
- `all-skills-bonus` - Bonus to all skills
- `proficiency-bonus` - Override proficiency bonus
- `proficiency-bonus-increase` - Increase proficiency bonus
- `tool-proficiency` - Grant tool proficiency
- `tool-expertise` - Grant tool expertise (macro)

#### Proficiencies (Armor, Weapons, Languages)
- `weapon-proficiency` - Grant weapon proficiency
- `armor-proficiency` - Grant armor proficiency
- `light-armor-proficiency` - Grant light armor prof
- `medium-armor-proficiency` - Grant medium armor prof
- `heavy-armor-proficiency` - Grant heavy armor prof
- `shield-armor-proficiency` - Grant shield prof
- `language` - Grant language proficiency

#### Movement
- `speed` - Set base speed
- `speed-override` - Override calculated speed
- `flying-speed-bonus` - Bonus to flying speed
- `flying-speed-override` - Set flying speed
- `flying-speed-equal-to-walking` - Flying speed = walking speed
- `swimming-speed` - Add swimming speed
- `swimming-speed-override` - Set swimming speed
- `swimming-speed-equal-to-walking` - Swimming speed = walking speed
- `climbing-speed` - Add climbing speed
- `climbing-speed-override` - Set climbing speed
- `climbing-speed-equal-to-walking` - Climbing speed = walking speed
- `unarmored-speed-bonus` - Speed bonus when unarmored

#### Senses
- `darkvision` - Grant darkvision
- `darkvision-bonus` - Increase darkvision range
- `passive-perception` - Bonus to passive perception
- `passive-investigation` - Bonus to passive investigation

#### Defenses
- `damage-resistance` - Grant damage resistance
- `damage-vulnerability` - Grant damage vulnerability
- `damage-immunity` - Grant damage immunity
- `immunity` - Grant immunity (general)
- `condition-immunity` - Grant condition immunity

#### Hit Points
- `max-hit-points` - Bonus to max HP
- `deferred-max-hit-points` - HP bonus determined later

#### Combat
- `initiative` - Bonus to initiative
- `extra-attack` - Grant Extra Attack feature
- `num-attacks` - Set number of attacks
- `ranged-attack-bonus` - Bonus to ranged attacks
- `attack-modifier-fn` - Custom attack modifier function
- `melee-damage-bonus-fn` - Custom melee damage function
- `weapon-attack-bonus-mod` - Attack bonus for specific weapons
- `weapon-damage-bonus-mod` - Damage bonus for specific weapons
- `critical` - Modify critical hit range
- `attack` - Add special attack (macro)

#### Armor Class
- `armored-ac-bonus` - AC bonus when armored
- `unarmored-ac-bonus` - AC bonus when unarmored
- `natural-ac-bonus` - Natural armor bonus
- `ac-bonus-fn` - Custom AC calculation (macro)
- `unarmored-defense` - Unarmored Defense feature

#### Actions
- `action` - Add action (macro)
- `bonus-action` - Add bonus action (macro)
- `reaction` - Add reaction (macro)

#### Traits
- `trait` - Add trait with static text
- `trait-cfg` - Add trait with config
- `prop-trait` - Trait with level requirements (macro)
- `dependent-trait` - Trait with dynamic text (macro)
- `dependent-trait-2` - Alternate dependent trait (macro)

#### Spellcasting
- `spells-known-cfg` - Grant spell with config (macro)
- `spells-known` - Grant specific spell
- `spells-known-mode` - Set spell learning mode
- `spell-slot-factor` - Set spell slot progression
- `spell-save-dc-bonus` - Bonus to spell save DC
- `spell-attack-modifier-bonus` - Bonus to spell attack

#### Equipment
- `equipment-cfg` - Add equipment with config
- `weapon` - Add weapon
- `magic-weapon` - Add magic weapon
- `deferred-weapon` - Weapon determined later
- `deferred-magic-weapon` - Magic weapon determined later
- `armor` - Add armor
- `deferred-armor` - Armor determined later
- `deferred-magic-armor` - Magic armor determined later
- `equipment` - Add equipment
- `deferred-equipment` - Equipment determined later
- `deferred-treasure` - Treasure determined later
- `magic-item` - Add magic item
- `deferred-magic-item` - Magic item determined later

#### Utility
- `level` - Set class level
- `level-val` - Get value based on level (macro)
- `used-resource` - Track resource usage
- `al-illegal` - Mark as Adventurers League illegal
- `add-bonus` - Helper to add bonuses
- `enough-levels?` - Check if character has enough levels
- `build-modifiers` - Build multiple modifiers from configs

---

## What Needs to Be Done

To expose class features to homebrew, we need to:

### 1. Add New Cases to `make-feat-modifiers`

Extend the case statement in `options.cljc:3230` with new prop types:

```clojure
(defn make-feat-modifiers [k v option-key]
  (if v
    (case k
      ;; ... existing cases ...

      ;; NEW: Action-based features
      :bonus-action-feature (bonus-action-feature-modifiers v option-key)
      :reaction-feature (reaction-feature-modifiers v option-key)
      :action-feature (action-feature-modifiers v option-key)

      ;; NEW: Combat features
      :extra-attack (extra-attack-modifiers v)
      :num-attacks [(mod5e/num-attacks v)]
      :unarmored-defense (unarmored-defense-modifiers v option-key)

      ;; NEW: Class-specific mechanics
      :rage (rage-modifiers v option-key)
      :sneak-attack (sneak-attack-modifiers v option-key)
      :bardic-inspiration (bardic-inspiration-modifiers v option-key)
      :ki-points (ki-points-modifiers v option-key)

      nil)))
```

### 2. Create Helper Functions

For each new prop type, create a helper that constructs the appropriate modifiers:

```clojure
(defn rage-modifiers [config class-key]
  "Creates modifiers for a Rage-like feature.
   Config example: {:uses-per-rest :level-based
                    :damage-bonus :level-based
                    :resistances [:bludgeoning :piercing :slashing]}"
  [(mod5e/bonus-action
    {:name (:name config "Rage")
     :page (:page config)
     :duration (get config :duration units5e/minutes-1)
     :frequency (calculate-uses config class-key)
     :summary (rage-summary config class-key)})
   ;; Add resistance modifiers if configured
   (when (:resistances config)
     (map mod5e/damage-resistance (:resistances config)))])

(defn unarmored-defense-modifiers [config class-key]
  "Creates modifiers for Unarmored Defense.
   Config example: {:abilities [:dex :con] :base-ac 10}"
  [(mod/vec-mod ?unarmored-defense class-key)
   (mod/cum-sum-mod ?unarmored-ac-bonus
                    (?ability-bonuses (second (:abilities config)))
                    nil nil
                    [(= class-key (first ?unarmored-defense))])])
```

### 3. Add UI Controls

In `class-builder` (views.cljs), add sections to configure these props:

```clojure
[:div.m-b-30
 [:div.f-s-24.f-w-b.m-b-10 "Class Features"]

 ;; Rage-like feature
 [:div.m-b-20
  [comps/labeled-checkbox
   "Limited Use Feature (Rage, Channel Divinity, etc.)"
   (get-in class [:props :limited-use-feature :enabled])]
  (when (get-in class [:props :limited-use-feature :enabled])
    [:div.m-l-20
     [text-field "Feature Name" ...]
     [dropdown "Uses per Rest"
      {:options ["Level-based (Barbarian style)"
                 "Proficiency bonus"
                 "Fixed number"]}]
     [dropdown "Duration"
      {:options ["1 minute" "10 minutes" "1 hour" "Permanent"]}]])]

 ;; Unarmored Defense
 [:div.m-b-20
  [comps/labeled-checkbox
   "Unarmored Defense"
   (get-in class [:props :unarmored-defense :enabled])]
  (when (get-in class [:props :unarmored-defense :enabled])
    [:div.m-l-20
     [:div "Choose abilities for AC calculation:"]
     [multi-select ["DEX" "CON" "WIS" "INT" "CHA"] ...]])]]
```

### 4. Document Each Feature Type

For each new prop type, document:
- What it does
- Example configuration
- What modifiers it creates
- Which built-in classes use it
- UI controls needed

---

## Research Checklist

As we explore the codebase, investigate:

- [ ] Are there other namespaces with modifier functions we haven't found?
- [ ] Are there hidden features in the UA files we can learn from?
- [ ] How do subclass-specific features work differently from class features?
- [ ] Are there patterns in how built-in classes use modifiers that we can template?
- [ ] What about race/background features - do they use different patterns?
- [ ] Are there deprecated or alternative modifier systems we should know about?
- [ ] How do conditional modifiers work (only active under certain conditions)?
- [ ] What's the difference between immediate and deferred modifiers?

---

## Next Steps

### Immediate Priority: Catalog Built-in Class Features

Go through each built-in class and extract:
1. Every unique feature they use
2. What modifiers implement that feature
3. What configuration data is needed
4. Categorize by pattern (scaling damage, limited resource, conditional, etc.)

This will give us the blueprint for what props need to be created.

### Medium Priority: Design Feature Prop Schema

For each feature pattern, design the `:props` format:
```clojure
{:props
 {:rage {:enabled true
         :uses-per-rest :level-based  ; or number
         :damage-bonus :level-based   ; or number
         :resistances [:bludgeoning :piercing :slashing]}

  :unarmored-defense {:enabled true
                      :abilities [:dex :con]
                      :base-ac 10}

  :extra-attack {:enabled true
                 :num-attacks 2}}}
```

### Long-term: Build Feature Library UI

Create a user-friendly interface where users can:
1. Browse available features
2. See examples of classes that use each feature
3. Configure feature parameters
4. Preview how the feature will work

---

## Open Questions

- How should level-scaling work? (e.g., "damage increases at levels 5, 11, 17")
- Should we create "feature templates" for common patterns?
- How do we handle features that interact with each other? (e.g., Persistent Rage extends Rage)
- What's the right granularity? (One `:rage` prop vs separate `:limited-use-feature` that's configured?)
- Should configuration be in `:props` or somewhere else?
- How do we version features if we need to change their implementation?

---

## Related Files

### Core Files
- **`src/cljc/orcpub/dnd/e5/modifiers.cljc`** - All modifier functions
- **`src/cljc/orcpub/dnd/e5/options.cljc`** - Helper functions, `make-feat-modifiers`, `plugin-modifiers`
- **`src/cljc/orcpub/dnd/e5/classes.cljc`** - Built-in class definitions (examples of modifier usage)

### Conversion Pipeline
- **`src/cljs/orcpub/dnd/e5/spell_subs.cljs:421`** - Calls `plugin-modifiers` for homebrew classes
- **`src/cljc/orcpub/dnd/e5/options.cljc:3288`** - `plugin-modifiers` function
- **`src/cljc/orcpub/dnd/e5/options.cljc:3230`** - `make-feat-modifiers` case statement

### UI
- **`src/cljs/orcpub/dnd/e5/views.cljs:5429`** - Class builder UI

### Experimental
- **`src/cljc/orcpub/dnd/e5/templates/ua_*.cljc`** - Unearthed Arcana examples (commented out)

---

**Last Updated:** 2026-01-11
**Document Version:** 1.0
**Status:** Initial Understanding - Subject to Revision
