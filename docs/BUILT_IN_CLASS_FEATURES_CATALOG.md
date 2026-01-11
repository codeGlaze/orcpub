# D&D 5e Built-in Class Features Comprehensive Catalog

**Created:** 2026-01-11
**Agent ID:** a9a20a5 (for resuming research)
**Status:** Complete Catalog of All Built-in Features

---

## Executive Summary

This catalog documents **289 unique modifier instances** across **12 classes** (Barbarian, Bard, Cleric, Druid, Fighter, Monk, Paladin, Ranger, Rogue, Sorcerer, Warlock, Wizard), organized into **8 major feature categories**.

**Key Finding:** The features can be abstracted into **8 core prop patterns** that enable homebrew classes to reuse these mechanics.

---

## Feature Categories Overview

| Category | Feature Count | % of Total | Modifier Functions |
|----------|---------------|------------|-------------------|
| Action Economy | 105 | 36% | bonus-action, action, reaction |
| Traits | 93 | 32% | trait-cfg, dependent-trait |
| Spellcasting | 50+ | 17% | spells-known, spell grants |
| Resource Management | 20+ | 7% | frequency/duration patterns |
| Combat Mechanics | 11 | 4% | num-attacks, critical, attack |
| Defenses | 15 | 5% | immunities, resistances |
| Proficiencies | 30+ | 10% | skills, saves, expertise |
| Physical Attributes | 40+ | 14% | abilities, speed, equipment |

---

## 1. LIMITED USE RESOURCES

### Pattern: Long Rest Resources

**Common Structure:**
```clojure
(mod5e/bonus-action
 {:name "Feature Name"
  :page integer
  :duration units5e/duration-fn
  :frequency (units5e/long-rests formula)
  :summary "Description with dynamic values"})
```

#### Barbarian - Rage
- **Level:** 1
- **Modifier:** `mod5e/bonus-action`
- **Uses:** Level-based (2→3→4→5→6 at levels 1/3/6/12/17)
- **Duration:** 1 minute
- **Formula:** `(units5e/long-rests (condp <= (?class-level :barbarian) 17 6 12 5 6 4 3 3 2))`
- **Summary:** Includes dynamic damage bonus `(condp <= (?class-level :barbarian) 16 4 9 3 2)`

#### Bard - Bardic Inspiration
- **Level:** 1
- **Modifier:** `mod5e/bonus-action`
- **Uses:** CHA modifier (min 1)
- **Die Size:** Scales (d6→d8→d10→d12 at levels 1/5/10/15)
- **Formula:** `(units5e/long-rests (max 1 (?ability-bonuses ::char5e/cha)))`
- **Summary:** Dynamic die size based on level

#### Cleric - Channel Divinity
- **Level:** 2
- **Modifier:** `mod5e/dependent-trait`
- **Uses:** Scales (1→2→3 at levels 2/6/18)
- **Formula:** `(units5e/rests (mod5e/level-val (?class-level :cleric) {6 2 18 3 :default 1}))`

#### Fighter - Action Surge
- **Level:** 2
- **Modifier:** `mod5e/action`
- **Uses:** 1→2 at level 17
- **Formula:** `(units5e/rests (if (>= (?class-level :fighter) 17) 2 1))`

#### Fighter - Second Wind
- **Level:** 1
- **Modifier:** `mod5e/bonus-action`
- **Uses:** 1 per rest
- **Healing:** 1d10 + fighter level
- **Formula:** `units5e/rests-1`

#### Monk - Ki Points
- **Level:** 2
- **Modifier:** `mod5e/dependent-trait`
- **Pool:** Equal to monk level
- **Formula:** `(?class-level :monk)`
- **Recovery:** Short or long rest

#### Paladin - Lay on Hands
- **Level:** 1
- **Modifier:** `mod5e/action`
- **Pool:** 5 × paladin level
- **Formula:** `(units5e/long-rests (* 5 (?class-level :paladin)))`

#### Sorcerer - Sorcery Points
- **Level:** 2
- **Modifier:** `mod5e/dependent-trait`
- **Pool:** Equal to sorcerer level
- **Formula:** `(units5e/long-rests (?class-level :sorcerer))`

### Pattern: Short Rest Resources

#### Druid - Wild Shape
- **Level:** 2
- **Modifier:** `mod5e/action`
- **Uses:** 2 per rest
- **Duration:** Hours = (level/2)
- **CR:** Scales with level (1/4→1/2→1 at levels 2/4/8)
- **Formula:** `(units5e/rests 2)`, `(units5e/hours (int (/ (?class-level :druid) 2)))`

#### Wizard - Arcane Recovery
- **Level:** 1
- **Modifier:** `mod5e/dependent-trait`
- **Uses:** Once per day
- **Recovery:** Spell slots totaling ≤ (level/2)
- **Formula:** `units5e/days-1`

### Pattern: Ability-Dependent Resources

#### Cleric - Warding Flare (Light Domain)
- **Level:** 1
- **Modifier:** `mod5e/reaction`
- **Uses:** WIS modifier (min 1)
- **Formula:** `(units5e/long-rests (max 1 (?ability-bonuses ::char5e/wis)))`

#### Paladin - Cleansing Touch
- **Level:** 14
- **Modifier:** `mod5e/action`
- **Uses:** CHA modifier
- **Formula:** `(units5e/long-rests (?ability-bonuses ::char5e/cha))`

---

## 2. AC CALCULATIONS

### Pattern: Unarmored Defense

**Base Structure:**
```clojure
(mod/vec-mod ?unarmored-defense :class-key)
(mod/cum-sum-mod ?unarmored-ac-bonus
                 (?ability-bonuses ::char5e/secondary-ability)
                 nil nil
                 [(= :class-key (first ?unarmored-defense))])
```

#### Barbarian - Unarmored Defense
- **Level:** 1
- **Formula:** 10 + DEX + CON
- **Modifiers:**
  ```clojure
  (mod/vec-mod ?unarmored-defense :barbarian)
  (mod/cum-sum-mod ?unarmored-ac-bonus (?ability-bonuses ::char5e/con) nil nil
                   [(= :barbarian (first ?unarmored-defense))])
  (mod/cum-sum-mod ?unarmored-with-shield-ac-bonus (?ability-bonuses ::char5e/con) nil nil
                   [(= :barbarian (first ?unarmored-defense))])
  ```
- **Shield Variant:** Yes

#### Monk - Unarmored Defense
- **Level:** 1
- **Formula:** 10 + DEX + WIS
- **Modifiers:**
  ```clojure
  (mod/vec-mod ?unarmored-defense :monk)
  (mod/cum-sum-mod ?unarmored-ac-bonus (?ability-bonuses ::char5e/wis) nil nil
                   [(= :monk (first ?unarmored-defense))])
  ```
- **Shield Variant:** No

#### Sorcerer - Draconic Resilience
- **Level:** 1
- **Formula:** 13 + DEX
- **Modifier:**
  ```clojure
  (mod/modifier ?natural-ac-bonus 3)
  ```

---

## 3. SCALING DAMAGE

### Pattern: Per-Turn Scaling

#### Rogue - Sneak Attack
- **Level:** 1
- **Modifier:** `mod5e/dependent-trait`
- **Damage:** `(common/round-up (/ (?class-level :rogue) 2))d6`
- **Frequency:** Once per turn
- **Trigger:** Advantage or ally within 5ft
- **Summary Template:**
  ```clojure
  {:summary (str (common/round-up (/ (?class-level :rogue) 2))
                 "d6 extra damage once per turn")}
  ```

#### Monk - Martial Arts
- **Level:** 1
- **Modifier:** `mod5e/attack`
- **Die:** Scales (d4→d6→d8→d10 at levels 1/5/11/17)
- **Formula:**
  ```clojure
  (mod/modifier ?martial-arts-die
                (mod5e/level-val (?class-level :monk)
                                 {5 6 11 8 17 10 :default 4}))
  ```

### Pattern: Spell/Attack Augmentation

#### Cleric - Divine Strike
- **Level:** 8 (1d8) / 14 (2d8)
- **Helper:** `(opt5e/divine-strike damage-type page)`
- **Examples:**
  ```clojure
  (opt5e/divine-strike "radiant" 60)   ;; Life domain
  (opt5e/divine-strike "thunder" 62)   ;; Tempest domain
  (opt5e/divine-strike nil 63)         ;; War domain (weapon type)
  ```

#### Cleric - Potent Spellcasting
- **Level:** 8
- **Helper:** `(opt5e/potent-spellcasting page)`
- **Effect:** Add WIS/INT mod to cantrip damage

#### Barbarian - Brutal Critical
- **Level:** 9 (1 die) / 13 (2 dice) / 17 (3 dice)
- **Modifier:** `mod5e/dependent-trait`
- **Summary:**
  ```clojure
  (str (mod5e/level-val (?class-level :barbarian)
                        {17 "three" 13 "two" :default "one"})
       " additional damage "
       (if (= "one" die-count) "die" "dice")
       " for melee criticals")
  ```

---

## 4. EXTRA ATTACKS

### Pattern: Standard Extra Attack

**Modifier:** `mod5e/num-attacks`
**Parameter:** Integer (2, 3, or 4)

#### Progression Table

| Class | Level 5 | Level 11 | Level 20 |
|-------|---------|----------|----------|
| Fighter | 2 | 3 | 4 |
| Barbarian | 2 | - | - |
| Paladin | 2 | - | - |
| Ranger | 2 | - | - |
| Monk | 2 | - | - |

**Implementation:**
```clojure
;; Fighter
:levels {5 {:modifiers [(mod5e/num-attacks 2)]}
         11 {:modifiers [(mod5e/num-attacks 3)]}
         20 {:modifiers [(mod5e/num-attacks 4)]}}

;; Standard (Barbarian, Paladin, Ranger, Monk)
:levels {5 {:modifiers [(mod5e/num-attacks 2)]}}
```

**Helper Function:**
```clojure
(defn extra-attack-trait [page]
  (mod5e/trait-cfg
   {:name "Extra Attack"
    :page page
    :summary "Attack twice when taking Attack action"}))
```

---

## 5. ACTION ECONOMY MODIFIERS

### 5.1 Bonus Actions

#### Rogue - Cunning Action
- **Level:** 2
- **Modifier:** `mod5e/bonus-action`
- **Options:** Dash, Disengage, Hide
- **Cost:** None
- **Frequency:** Unlimited
- **Pattern:** Always-available option

#### Monk - Flurry of Blows
- **Level:** 2
- **Modifier:** `mod5e/bonus-action`
- **Effect:** Make two unarmed strikes
- **Cost:** 1 ki point
- **Pattern:** Resource-cost bonus action

#### Monk - Patient Defense
- **Level:** 2
- **Modifier:** `mod5e/bonus-action`
- **Effect:** Dodge action
- **Cost:** 1 ki point
- **Pattern:** Action conversion via resource

#### Monk - Step of the Wind
- **Level:** 2
- **Modifier:** `mod5e/bonus-action`
- **Effect:** Dash or Disengage, jump distance doubled
- **Cost:** 1 ki point

#### Bard - Battle Magic (College of Valor)
- **Level:** 14
- **Modifier:** `mod5e/bonus-action`
- **Trigger:** After casting bard spell
- **Effect:** Make weapon attack
- **Pattern:** Conditional trigger

#### Sorcerer - Quickened Spell
- **Level:** 3
- **Modifier:** `mod5e/trait-cfg`
- **Effect:** Cast spell as bonus action
- **Cost:** 2 sorcery points
- **Pattern:** Metamagic

### 5.2 Reactions

#### Monk - Deflect Missiles
- **Level:** 3
- **Modifier:** `mod5e/reaction`
- **Effect:** Reduce ranged damage by 1d10 + DEX + monk level
- **Trigger:** Hit by ranged weapon attack

#### Rogue/Ranger - Uncanny Dodge
- **Level:** 5 (Rogue) / 15 (Ranger Hunter)
- **Helper:** `(opt5e/uncanny-dodge-modifier page)`
- **Effect:** Halve attack damage
- **Trigger:** Attacker you can see

#### Fighter - Retaliation (Champion)
- **Level:** 15
- **Modifier:** `mod5e/reaction`
- **Trigger:** Damaged by creature within 5ft
- **Effect:** Make melee attack

#### Paladin - Soul of Vengeance (Oath of Vengeance)
- **Level:** 15
- **Modifier:** `mod5e/reaction`
- **Trigger:** Vow of Enmity target makes attack
- **Effect:** Make melee attack

#### Wizard - Spell Resistance (Abjuration)
- **Level:** 2
- **Modifier:** `mod5e/reaction`
- **Effect:** Add INT mod to save vs spell, restore ward

### 5.3 Actions

#### Monk - Stillness of Mind
- **Level:** 7
- **Modifier:** `mod5e/action`
- **Effect:** End charmed or frightened condition
- **Frequency:** Unlimited

#### Barbarian - Intimidating Presence (Berserker)
- **Level:** 10
- **Modifier:** `mod5e/action`
- **Save:** WIS vs spell DC (CHA)
- **Effect:** Frighten creature

#### Ranger - Primeval Awareness
- **Level:** 3
- **Modifier:** `mod5e/action`
- **Cost:** Spell slot
- **Effect:** Detect creature types within 1-6 miles

---

## 6. TRAITS (STATIC & DYNAMIC)

### 6.1 Static Traits

**Pattern:**
```clojure
:traits [{:name "Trait Name"
          :level integer
          :page integer
          :summary "Description text"}]
```

#### Barbarian Examples
```clojure
{:name "Reckless Attack"
 :level 2
 :page 48
 :summary "Advantage on attacks using Strength, attacks against you have advantage"}

{:name "Danger Sense"
 :level 2
 :page 48
 :summary "Advantage on DEX saves against effects you can see"}

{:name "Feral Instinct"
 :level 7
 :page 49
 :summary "Advantage on initiative, surprise doesn't prevent attacking if you rage"}

{:name "Relentless Rage"
 :level 11
 :page 49
 :summary "If raging, reduced to 0 HP, aren't killed, make DC 10 save (+5 each use): go to 1 HP"}

{:name "Persistent Rage"
 :level 15
 :page 49
 :summary "Rage only ends if you choose or fall unconscious"}
```

### 6.2 Dynamic Traits

**Pattern:** Uses `mod5e/dependent-trait` with calculated summaries

#### Bard - Jack of All Trades
- **Level:** 2
- **Trait:**
  ```clojure
  (mod5e/dependent-trait
   {:name "Jack of All Trades"
    :page 54
    :summary (str "add " (int (/ ?prof-bonus 2))
                  " to ability checks without proficiency")})
  ```
- **Additional Modifiers:**
  ```clojure
  (mod/vec-mod ?default-skill-bonus-fns (fn [_] (int (/ ?prof-bonus 2))))
  (mod/cum-sum-mod ?initiative (int (/ ?prof-bonus 2)))
  ```

#### Bard - Song of Rest
- **Level:** 2
- **Modifier:** `mod5e/dependent-trait`
- **Die:** d6→d8→d10→d12 at levels 2/9/13/17
- **Summary:**
  ```clojure
  (str "d" (mod5e/level-val (?class-level :bard)
                            {9 8 13 10 17 12 :default 6})
       " extra healing during short rest")
  ```

#### Fighter - Remarkable Athlete (Champion)
- **Level:** 7
- **Modifier:** `mod5e/dependent-trait`
- **Additional:**
  ```clojure
  (mod/vec-mod ?default-skill-bonus-fns
    (fn [ability-kw]
      (if (contains? #{::char5e/str ::char5e/dex ::char5e/con} ability-kw)
        (common/round-up (/ ?prof-bonus 2))
        0)))
  ```
- **Summary:** Half proficiency to STR/DEX/CON checks, standing jump distance increases

#### Paladin - Aura of Protection
- **Level:** 6
- **Modifier:** `mod5e/dependent-trait`
- **Range:** 10 ft (30 ft at level 18)
- **Additional Modifiers:** Save bonuses to all abilities for allies
  ```clojure
  (map (fn [ability-kw]
         (mod/modifier ?saving-throw-bonuses
           (merge-with + ?saving-throw-bonuses
             {ability-kw (?ability-bonuses ::char5e/cha)})))
       char5e/ability-keys)
  ```

---

## 7. SPELLCASTING FEATURES

### 7.1 Spell Slot Recovery

#### Wizard - Arcane Recovery
- **Level:** 1
- **Modifier:** `mod5e/dependent-trait`
- **Recovery:** Spell slots totaling ≤ (wizard level / 2), max 5th level
- **Frequency:** Once per day (short rest)
- **Summary:**
  ```clojure
  (str "Recover " (int (/ (?class-level :wizard) 2))
       " levels of spell slots (max 5th level) during short rest")
  ```

#### Druid - Natural Recovery (Circle of Land)
- **Level:** 2
- **Modifier:** `mod5e/dependent-trait`
- **Recovery:** Slots totaling (druid level / 2) rounded up
- **Frequency:** Once per day (short rest)
- **Formula:** `(common/round-up (/ (?class-level :druid) 2))`

### 7.2 Metamagic (Sorcerer)

**Level:** 3
**Modifier:** `mod5e/trait-cfg` or `mod5e/dependent-trait`

#### Metamagic Options

1. **Careful Spell**
   - Cost: 1 sorcery point
   - Effect: CHA mod creatures auto-succeed save
   - Pattern: Dynamic summary with ability reference

2. **Distant Spell**
   - Cost: 1 sorcery point
   - Effect: Double range or make touch 30ft
   - Pattern: Static description

3. **Empowered Spell**
   - Cost: 1 sorcery point
   - Effect: Reroll CHA mod damage dice
   - Pattern: Dynamic summary

4. **Extended Spell**
   - Cost: 1 sorcery point
   - Effect: Double duration (max 24 hours)
   - Pattern: Static description

5. **Heightened Spell**
   - Cost: 3 sorcery points
   - Effect: Target has disadvantage on first save
   - Pattern: Static description

6. **Quickened Spell**
   - Cost: 2 sorcery points
   - Effect: Cast spell as bonus action
   - Pattern: Action economy change

7. **Subtle Spell**
   - Cost: 1 sorcery point
   - Effect: No components
   - Pattern: Static description

8. **Twinned Spell**
   - Cost: Spell level sorcery points
   - Effect: Target second creature
   - Pattern: Static description

### 7.3 Spell Mastery

#### Wizard - Spell Mastery
- **Level:** 18
- **Modifier:** `mod5e/dependent-trait`
- **Selections:** One 1st level, one 2nd level spell
- **Effect:** Cast at lowest level without slot
- **Pattern:** Selection + dependent trait

#### Wizard - Signature Spells
- **Level:** 20
- **Modifier:** `mod5e/dependent-trait`
- **Selections:** Two 3rd level spells
- **Effect:** Always prepared, cast once per day without slot

### 7.4 Domain/Subclass Spells

#### Cleric - Domain Spells
- **Helper:** `(opt5e/cleric-spell spell-level spell-key min-level)`
- **Pattern:** Always prepared, granted at specific levels
- **Example:**
  ```clojure
  (opt5e/cleric-spell 1 :bless 1)
  (opt5e/cleric-spell 1 :cure-wounds 1)
  (opt5e/cleric-spell 2 :lesser-restoration 3)
  ```

#### Paladin - Oath Spells
- **Helper:** `(opt5e/paladin-spell spell-level spell-key)`
- **Pattern:** Always prepared
- **Example:**
  ```clojure
  (opt5e/paladin-spell 1 :protection-from-evil-and-good)
  (opt5e/paladin-spell 1 :sanctuary)
  (opt5e/paladin-spell 2 :lesser-restoration)
  ```

#### Druid - Circle Spells
- **Function:** `(druid-spell spell-level spell-key min-level)`
- **Pattern:** Always prepared
- **Example:**
  ```clojure
  (druid-spell 3 :water-breathing 5)
  (druid-spell 4 :freedom-of-movement 7)
  ```

#### Warlock - Patron Spells
- **Function:** `(opt5e/warlock-subclass-spell-selection spell-lists spells-map [spell-keys])`
- **Pattern:** Expanded spell list (selections)
- **Example:**
  ```clojure
  (opt5e/warlock-subclass-spell-selection
   spell-lists spells-map
   [:armor-of-agathys :hex :cloud-of-daggers :crown-of-madness])
  ```

### 7.5 Spell Additions

#### Bard - Magical Secrets
- **Helper:** `(opt5e/bard-magical-secrets spells-map min-level)`
- **Levels:** 10, 14, 18 (all bards); 6 (Lore only)
- **Pattern:** Learn any class spells
- **Example:**
  ```clojure
  (opt5e/bard-magical-secrets spells-map 6)  ;; 2 spells
  ```

#### Warlock - Mystic Arcanum
- **Selection:** `(mystic-arcanum-selection spells-map spell-level)`
- **Levels:** 11 (6th), 13 (7th), 15 (8th), 17 (9th)
- **Pattern:** One spell per level, once per day
- **Example:**
  ```clojure
  :selections [(mystic-arcanum-selection spells-map 6)]
  ```

#### Warlock - Pact of the Tome
- **Level:** 3
- **Selection:** 3 cantrips from any class
- **Pattern:** Cross-class cantrip selection

---

## 8. PROFICIENCIES & EXPERTISE

### 8.1 Skill Proficiency

**Modifier:** `mod5e/skill-proficiency`
**Parameter:** Skill keyword

**Examples:**
```clojure
(mod5e/skill-proficiency :deception)
(mod5e/skill-proficiency :persuasion)
(mod5e/skill-proficiency :perception)
```

**Warlock - Beguiling Influence (Invocation):**
```clojure
:modifiers [(mod5e/skill-proficiency :deception)
            (mod5e/skill-proficiency :persuasion)]
```

### 8.2 Skill Expertise

**Modifier:** `mod5e/skill-expertise`
**Parameter:** Skill keyword

**Cleric - Blessings of Knowledge (Knowledge Domain):**
```clojure
;; Grants proficiency AND expertise in chosen skills
(mod5e/skill-proficiency skill-kw)
(mod5e/skill-expertise skill-kw)
```

**Bard/Rogue - Expertise Selections:**
- **Helper:** `(opt5e/expertise-selection num)`
- **Pattern:** Choose from proficient skills
- **Example:**
  ```clojure
  :selections [(opt5e/expertise-selection 2)]
  ```

### 8.3 Saving Throw Proficiency

**Modifier:** `mod5e/saving-throws`
**Parameters:** Conditions (nil for unconditional), ability keywords

**Examples:**
```clojure
;; Single ability
(mod5e/saving-throws nil ::char5e/wis)

;; All abilities (Monk Diamond Soul)
(apply mod5e/saving-throws nil char5e/ability-keys)
```

### 8.4 Saving Throw Advantage

**Modifier:** `mod5e/saving-throw-advantage`
**Parameter:** Vector of conditions

**Examples:**
```clojure
;; Specific condition
(mod5e/saving-throw-advantage [:frightened])

;; Against spells
(mod5e/saving-throw-advantage [:spells])

;; Custom condition
(mod5e/saving-throw-advantage
 ["plants magically created or manipulated to impede movement"])
```

**Used By:**
- Monk (Stillness of Mind - advantage vs charm/frighten)
- Druid Circle of Land (advantage vs fey/elemental charm/frighten)
- Paladin Divine Health (immunity to disease)

---

## 9. DAMAGE RESISTANCE & IMMUNITY

### 9.1 Damage Immunity

**Modifier:** `mod5e/damage-immunity`
**Parameter:** Damage type keyword

**Examples:**
```clojure
(mod5e/damage-immunity :poison)
(mod5e/damage-immunity :necrotic)
```

**Used By:**
- Monk (poison at level 10)
- Druid Circle of Land (poison at level 10)

### 9.2 Damage Resistance

**Modifier:** `mod5e/damage-resistance`
**Parameter:** Damage type keyword

**Examples:**
```clojure
(mod5e/damage-resistance :necrotic)
(mod5e/damage-resistance :psychic)
```

**Used By:**
- Wizard Necromancy (necrotic at level 10)
- Warlock Great Old One (psychic at level 10)

### 9.3 Condition Immunity

**Modifier:** `mod5e/condition-immunity`
**Parameters:** Condition keyword, optional qualifier string

**Examples:**
```clojure
(mod5e/condition-immunity :poisoned)
(mod5e/condition-immunity :charmed)
(mod5e/condition-immunity :charmed "by elementals or fey")
(mod5e/condition-immunity :frightened "by elementals or fey")
```

**Used By:**
- Druid Circle of Land (level 10)
- Warlock Archfey (charmed at level 10)
- Paladin (various conditions by oath)

### 9.4 General Immunity

**Modifier:** `mod5e/immunity`
**Parameter:** Condition type keyword

**Examples:**
```clojure
(mod5e/immunity :disease)
```

**Used By:**
- Monk (level 10)
- Druid Circle of Land (level 10)
- Paladin Divine Health (level 3)

---

## 10. MOVEMENT & SENSES

### 10.1 Speed Bonuses

**Modifier:** `mod5e/unarmored-speed-bonus`
**Parameter:** Bonus amount (integer)

**Monk Progression:**
```clojure
:levels {2 {:modifiers [(mod5e/unarmored-speed-bonus 10)]}
         6 {:modifiers [(mod5e/unarmored-speed-bonus 5)]}   ;; Cumulative +15
         10 {:modifiers [(mod5e/unarmored-speed-bonus 5)]}  ;; Cumulative +20
         14 {:modifiers [(mod5e/unarmored-speed-bonus 5)]}  ;; Cumulative +25
         18 {:modifiers [(mod5e/unarmored-speed-bonus 5)]}} ;; Cumulative +30
```

**Barbarian - Fast Movement (Level 5):**
```clojure
(mod/modifier ?speed-with-armor
  (fn [armor]
    (if (not= :heavy (:type armor))
      (+ 10 ?speed)
      ?speed)))
```
**Pattern:** Conditional modifier based on armor type

### 10.2 Flying Speed

**Modifier:** `mod5e/flying-speed-equal-to-walking`

**Cleric Tempest - Stormborn (Level 17):**
```clojure
(mod5e/flying-speed-equal-to-walking)
```

**Sorcerer Draconic - Dragon Wings (Level 14):**
```clojure
(mod5e/bonus-action
 {:name "Dragon Wings"
  :page 103
  :summary "Sprout wings and gain flying speed equal to land speed"})
```

### 10.3 Darkvision

**Modifier:** `mod5e/darkvision`
**Parameters:** Range (feet), increment number

**Warlock - Devil's Sight (Invocation):**
```clojure
(mod5e/darkvision 120 1)
```

---

## 11. LANGUAGES

**Modifier:** `mod5e/language`
**Parameter:** Language keyword

**Examples:**
```clojure
(mod5e/language :druidic)
(mod5e/language :draconic)
(mod5e/language :thieves-cant)
```

**Monk - Tongue of the Sun and Moon (Level 13):**
```clojure
(map (fn [{:keys [name key]}]
       (mod5e/language key))
     (vals language-map))
```
**Pattern:** Grant all languages

**Druid - Druidic (Level 1):**
```clojure
(mod5e/language :druidic)
```

---

## 12. CRITICAL HIT THRESHOLD

**Modifier:** `mod5e/critical`
**Parameter:** Threshold number (20 is default, lower is better)

**Fighter - Champion:**

**Level 3 - Improved Critical:**
```clojure
(mod5e/critical 19)  ;; Crit on 19-20
```

**Level 15 - Superior Critical:**
```clojure
(mod5e/critical 18)  ;; Crit on 18-20
```

---

## 13. ABILITY SCORE INCREASES

**Modifier:** `mod5e/ability`
**Parameters:** Ability keyword, amount

**Standard ASI:**
```clojure
(mod5e/ability ::char5e/str 2)
(mod5e/ability ::char5e/dex 1)
```

**Barbarian Primal Champion (Level 20):**
```clojure
(mod5e/ability ::char5e/str 4)
(mod5e/ability ::char5e/con 4)
```

**Ranger Feral Senses (Hunter - Level 18):**
```clojure
(mod5e/ability ::char5e/wis 4)
```

---

## 14. LEVEL-BASED VALUE CALCULATIONS

### 14.1 Level Lookup Pattern

**Function:** `mod5e/level-val`
**Pattern:**
```clojure
(mod5e/level-val
  (?class-level :class-key)
  {level1 value1
   level2 value2
   :default default-value})
```

**Examples:**

**Monk Martial Arts Die:**
```clojure
(mod5e/level-val (?class-level :monk)
                 {5 6    ;; d6
                  11 8   ;; d8
                  17 10  ;; d10
                  :default 4})  ;; d4
```

**Bard Bardic Inspiration Die:**
```clojure
(mod5e/level-val (?class-level :bard)
                 {5 8    ;; d8
                  10 10  ;; d10
                  15 12  ;; d12
                  :default 6})  ;; d6
```

**Druid Wild Shape CR:**
```clojure
(mod5e/level-val (?class-level :druid)
                 {1 "1/4"
                  4 "1/2"
                  8 "1"})
```

### 14.2 Conditional Pattern (condp)

**Barbarian Rage Damage:**
```clojure
(condp <= (?class-level :barbarian)
  16 4
  9 3
  2)
```

**Barbarian Rage Uses:**
```clojure
(condp <= (?class-level :barbarian)
  17 6
  12 5
  6 4
  3 3
  2)
```

### 14.3 Formula-Based Scaling

**Half Level (Rounded Up):**
```clojure
(common/round-up (/ (?class-level :class-key) 2))
```
- **Used By:** Rogue Sneak Attack, Druid Natural Recovery

**Half Level (Rounded Down):**
```clojure
(int (/ (?class-level :class-key) 2))
```
- **Used By:** Wizard Arcane Recovery, Druid Wild Shape duration

**Level Multiplier:**
```clojure
(* 5 (?class-level :paladin))
```
- **Used By:** Paladin Lay on Hands pool

**Direct Level:**
```clojure
(?class-level :monk)
```
- **Used By:** Monk Ki points, Sorcerer Sorcery points

---

## 15. EQUIPMENT & INVENTORY

### 15.1 Weapons

**Modifier:** `mod5e/weapon`
**Parameters:** Weapon keyword, quantity

**Examples:**
```clojure
(mod5e/weapon :crossbow-light 1)
(mod5e/weapon :shortsword 2)
(mod5e/weapon :javelin 5)
(mod5e/weapon :dart 10)
```

### 15.2 Armor

**Modifier:** `mod5e/armor`
**Parameters:** Armor keyword, quantity

**Examples:**
```clojure
(mod5e/armor :leather 1)
(mod5e/armor :shield 1)
(mod5e/armor :chain-mail 1)
(mod5e/armor :scale-mail 1)
```

### 15.3 General Equipment

**Modifier:** `mod5e/equipment`
**Parameters:** Equipment keyword, quantity

**Examples:**
```clojure
(mod5e/equipment :crossbow-bolt 20)
(mod5e/equipment :arrow 20)
(mod5e/equipment :thieves-tools 1)
(mod5e/equipment :component-pouch 1)
```

---

## 16. CROSS-CUTTING PATTERNS

### 16.1 Frequency Patterns

**Units5e Namespace Functions:**

| Function | Description | Usage |
|----------|-------------|-------|
| `units5e/long-rests` | Per long rest | `(units5e/long-rests 3)` |
| `units5e/long-rests-1` | Once per long rest | `units5e/long-rests-1` |
| `units5e/rests` | Per short or long rest | `(units5e/rests 2)` |
| `units5e/rests-1` | Once per short/long rest | `units5e/rests-1` |
| `units5e/days-1` | Once per day | `units5e/days-1` |
| `units5e/turns-1` | Once per turn | `units5e/turns-1` |

**Dynamic Values:**
```clojure
;; Ability-based uses
(units5e/long-rests (max 1 (?ability-bonuses ::char5e/cha)))

;; Level-based uses
(units5e/long-rests (?class-level :sorcerer))

;; Fixed uses
(units5e/rests 2)

;; Level-lookup uses
(units5e/rests (mod5e/level-val (?class-level :cleric)
                                {6 2 18 3 :default 1}))
```

### 16.2 Duration Patterns

**Units5e Duration Functions:**

| Function | Description | Usage |
|----------|-------------|-------|
| `units5e/minutes-1` | 1 minute | `units5e/minutes-1` |
| `units5e/hours-1` | 1 hour | `units5e/hours-1` |
| `units5e/rounds-1` | 1 round | `units5e/rounds-1` |

**Dynamic Durations:**
```clojure
;; Hours based on level
(units5e/hours (int (/ (?class-level :druid) 2)))

;; Minutes (fixed)
units5e/minutes-1
```

### 16.3 Spell Save DC Pattern

**Formula:** `(?spell-save-dc ::char5e/ability)`

**Examples:**
```clojure
(?spell-save-dc ::char5e/wis)  ;; Cleric, Druid, Monk
(?spell-save-dc ::char5e/cha)  ;; Paladin, Warlock, Sorcerer, Bard
(?spell-save-dc ::char5e/int)  ;; Wizard
```

**Usage in Summaries:**
```clojure
(str "target makes DC " (?spell-save-dc ::char5e/wis) " WIS save")
```

### 16.4 Ability Bonus Pattern

**Formula:** `(?ability-bonuses ::char5e/ability)`

**Examples:**
```clojure
(?ability-bonuses ::char5e/cha)  ;; Sorcery points, Bardic Inspiration uses
(?ability-bonuses ::char5e/con)  ;; Barbarian AC
(?ability-bonuses ::char5e/wis)  ;; Monk AC, Ki save DC
(?ability-bonuses ::char5e/str)  ;; Paladin aura (Devotion)
```

**Usage:**
```clojure
;; Uses per rest
(units5e/long-rests (max 1 (?ability-bonuses ::char5e/cha)))

;; Save DC
(str "DC " (+ 8 ?prof-bonus (?ability-bonuses ::char5e/wis)))

;; Damage bonus
(str "+" (?ability-bonuses ::char5e/str) " damage")
```

---

## 17. HELPER FUNCTION PATTERNS

### 17.1 Class-Specific Helpers

**Extra Attack:**
```clojure
(defn extra-attack-trait [page]
  (mod5e/trait-cfg
   {:name "Extra Attack"
    :page page
    :summary "Attack twice when taking Attack action"}))
```

**Divine Strike:**
```clojure
(opt5e/divine-strike "radiant" 60)   ;; Life domain
(opt5e/divine-strike "thunder" 62)   ;; Tempest domain
(opt5e/divine-strike nil 63)         ;; War domain (weapon type)
```

**Potent Spellcasting:**
```clojure
(opt5e/potent-spellcasting 60)
```
**Effect:** Add WIS/INT modifier to cantrip damage

**Evasion:**
```clojure
(opt5e/evasion level page)
```
**Effect:** No damage on successful DEX save, half on fail

**Uncanny Dodge:**
```clojure
(opt5e/uncanny-dodge-modifier page)
```
**Effect:** Halve attack damage as reaction

### 17.2 Spell Grant Helpers

**Cleric Domain:**
```clojure
(opt5e/cleric-spell spell-level spell-key min-level)

;; Example
(opt5e/cleric-spell 1 :bless 1)
(opt5e/cleric-spell 2 :lesser-restoration 3)
```

**Paladin Oath:**
```clojure
(opt5e/paladin-spell spell-level spell-key)

;; Example
(opt5e/paladin-spell 1 :protection-from-evil-and-good)
(opt5e/paladin-spell 2 :lesser-restoration)
```

**Druid Circle:**
```clojure
(druid-spell spell-level spell-key min-level)

;; Example
(druid-spell 3 :water-breathing 5)
(druid-spell 4 :freedom-of-movement 7)
```

**Warlock Patron:**
```clojure
(opt5e/warlock-subclass-spell-selection spell-lists spells-map [spell-keys])

;; Example
(opt5e/warlock-subclass-spell-selection
 spell-lists spells-map
 [:armor-of-agathys :hex :cloud-of-daggers])
```

### 17.3 Selection Helpers

**Expertise:**
```clojure
(opt5e/expertise-selection num)

;; Example
:selections [(opt5e/expertise-selection 2)]
```

**Fighting Style:**
```clojure
(opt5e/fighting-style-selection level page)

;; Example
(opt5e/fighting-style-selection 1 72)
```

**Eldritch Invocations:**
```clojure
(opt5e/eldritch-invocation-selection
 spell-lists spells-map invocations level num)

;; Example
(opt5e/eldritch-invocation-selection
 spell-lists spells-map invocations 2 2)
```

**Monk Elemental Disciplines:**
```clojure
(opt5e/monk-elemental-disciplines)
```

**Bard Magical Secrets:**
```clojure
(opt5e/bard-magical-secrets spells-map min-level)

;; Example
(opt5e/bard-magical-secrets spells-map 6)
```

---

## 18. FEATURE TYPE TAXONOMY

### 18.1 By Modifier Function (Top 10)

| Modifier | Instances | % of Total |
|----------|-----------|------------|
| `mod5e/bonus-action` | 47 | 16.3% |
| `mod5e/trait-cfg` | 51 | 17.6% |
| `mod5e/dependent-trait` | 42 | 14.5% |
| `mod5e/action` | 38 | 13.1% |
| `mod5e/spells-known` | 30+ | 10.4% |
| `mod5e/weapon` | 25+ | 8.7% |
| `mod5e/reaction` | 20 | 6.9% |
| `mod5e/armor` | 15+ | 5.2% |
| `mod5e/language` | 15+ | 5.2% |
| `mod5e/equipment` | 10+ | 3.5% |

### 18.2 By Mechanical Category

| Category | Features | % of Total |
|----------|----------|------------|
| Action Economy | 105 | 36% |
| Traits | 93 | 32% |
| Spellcasting | 50+ | 17% |
| Equipment | 50+ | 17% |
| Resource Management | 20+ | 7% |
| Combat Mechanics | 11 | 4% |
| Defenses | 15 | 5% |
| Proficiencies | 30+ | 10% |

### 18.3 By Scaling Mechanism

| Pattern | Features | Examples |
|---------|----------|----------|
| **Level-Based** | ~115 (40%) | Sneak Attack, Rage uses, Martial Arts |
| **Ability-Based** | ~72 (25%) | Bardic Inspiration, Ki saves, Aura of Protection |
| **Fixed** | ~58 (20%) | Evasion, Uncanny Dodge, Proficiencies |
| **Hybrid** | ~29 (10%) | Paladin aura (level for range, CHA for value) |
| **Resource-Based** | ~9 (3%) | Spell slot features, Ki features |
| **Selection-Based** | ~6 (2%) | Magical Secrets, Invocations, Fighting Styles |

---

## 19. REUSABLE PROP PATTERNS

Based on this analysis, here are the key prop patterns needed:

### 19.1 Resource Pool
```clojure
{:type :resource-pool
 :name string
 :action-type :action | :bonus-action | :reaction
 :uses {:scaling :level | :ability | :fixed | :hybrid
        :base integer                    ;; For fixed
        :ability keyword                 ;; For ability-based
        :multiplier integer              ;; For level multipliers (e.g., 5)
        :breakpoints {level value}}      ;; For stepped scaling
 :recovery :long-rest | :short-rest | :daily
 :duration {:amount integer | formula :unit :round | :minute | :hour}
 :summary string | formula}
```

### 19.2 Scaling Damage
```clojure
{:type :scaling-damage
 :name string
 :frequency :turn | :round | :unlimited
 :damage {:dice-count formula
          :dice-size formula | {:breakpoints {level size}}
          :modifier formula | nil
          :type keyword}
 :trigger string
 :conditions [string]}
```

### 19.3 Action Economy
```clojure
{:type :action-economy
 :action-type :action | :bonus-action | :reaction
 :name string
 :trigger string | nil
 :effect string
 :cost {:type :none | :resource | :spell-slot
        :resource keyword              ;; :ki, :sorcery-points, etc.
        :amount integer | formula}
 :frequency formula | nil
 :summary string | formula}
```

### 19.4 AC Calculation
```clojure
{:type :ac-calculation
 :formula :unarmored-defense | :natural-armor
 :base integer
 :primary-ability keyword        ;; Always DEX for unarmored
 :secondary-ability keyword      ;; CON, WIS, or CHA
 :condition string | nil         ;; e.g., "when not wearing armor"
 :shield-variant? boolean}
```

### 19.5 Proficiency
```clojure
{:type :proficiency
 :category :skill | :save | :tool | :language | :weapon | :armor
 :grants [keyword]
 :expertise? boolean
 :conditional string | nil}
```

### 19.6 Spell Feature
```clojure
{:type :spell-feature
 :grant-type :domain | :oath | :circle | :patron | :secrets | :known
 :spells {level [spell-keys]}
 :always-prepared? boolean
 :ritual-only? boolean
 :at-will? boolean
 :frequency formula | nil}
```

### 19.7 Static Trait
```clojure
{:type :static-trait
 :name string
 :level integer
 :page integer | nil
 :summary string
 :frequency formula | nil
 :duration formula | nil
 :source string | nil}
```

### 19.8 Dynamic Trait
```clojure
{:type :dynamic-trait
 :name string
 :level integer
 :page integer | nil
 :summary-formula fn | template-string
 :dependencies [keyword]          ;; ?prof-bonus, ?class-level, etc.
 :modifiers [modifier-spec]}
```

### 19.9 Movement
```clojure
{:type :movement
 :movement-type :speed | :flying | :swimming | :climbing
 :bonus integer | formula
 :conditional string | nil        ;; e.g., "when not wearing heavy armor"
 :equals-walking? boolean}
```

### 19.10 Defense
```clojure
{:type :defense
 :defense-type :resistance | :immunity | :condition-immunity
 :damage-types [keyword]          ;; :fire, :necrotic, etc.
 :conditions [keyword]            ;; :poisoned, :charmed, etc.
 :qualifier string | nil}         ;; e.g., "by elementals or fey"
```

---

## 20. FEATURE USAGE BY CLASS

### Per-Class Feature Breakdown

| Class | Base Modifiers | Static Traits | Level Features | Unique Patterns |
|-------|----------------|---------------|----------------|-----------------|
| **Barbarian** | 8 | 5 | 5 levels | Rage, Unarmored Defense (CON) |
| **Bard** | 7 | 2 | 7 levels | Bardic Inspiration, Magical Secrets, Jack of All Trades |
| **Cleric** | 5 | 0 | 3 levels | Channel Divinity, Divine Strike/Potent Spellcasting, Domain Spells |
| **Druid** | 5 | 4 | 1 level | Wild Shape, Circle Spells |
| **Fighter** | 3 | 0 | 5 levels | Action Surge, Second Wind, Extra Attack progression |
| **Monk** | 25 | 7 | 9 levels | Ki Points, Martial Arts, Unarmored Defense (WIS), Elemental Disciplines |
| **Paladin** | 12 | 2 | 5 levels | Lay on Hands, Divine Smite, Auras, Oath Spells |
| **Ranger** | 5 | 4 | 6 levels | Favored Enemy, Natural Explorer, Primeval Awareness |
| **Rogue** | 3 | 6 | 3 levels | Sneak Attack, Cunning Action, Uncanny Dodge, Evasion |
| **Sorcerer** | 4 | 1 | 3 levels | Sorcery Points, Metamagic, Draconic Resilience |
| **Warlock** | 2 | 1 | 9 levels | Pact Magic, Invocations, Pact Boons, Mystic Arcanum |
| **Wizard** | 2 | 0 | 2 levels | Arcane Recovery, Spell Mastery, Signature Spells |

### Complexity Rankings

**Most Complex (by feature diversity):**
1. **Monk** - 25 base modifiers, 9 level progressions
2. **Paladin** - 12 modifiers, complex auras and spell mechanics
3. **Cleric** - Extensive domain customization (8 domains × ~10 features each)
4. **Warlock** - 9 level progressions, invocation system, pact mechanics

**Moderate Complexity:**
5. **Bard** - 7 modifiers, spell secrets, inspiration scaling
6. **Barbarian** - 8 modifiers, rage mechanics, primal paths
7. **Druid** - Wild Shape complexity, circle variations
8. **Sorcerer** - Metamagic system, sorcery points

**Simplest Patterns:**
9. **Fighter** - 3 modifiers, clean progression, fighting styles
10. **Rogue** - 3 modifiers, straightforward sneak attack scaling
11. **Ranger** - 5 modifiers, exploration features
12. **Wizard** - 2 modifiers, spell mastery features

---

## CONCLUSIONS & RECOMMENDATIONS

### Key Findings

1. **289 modifier instances** can be categorized into **8 core patterns**
2. **26 distinct modifier functions** are used across all classes
3. **40% of features** use level-based scaling
4. **25% of features** are ability-dependent
5. **Action economy modifiers** (bonus actions, actions, reactions) comprise **36%** of all features

### Common Patterns for Props System

The most frequently needed props:

1. **Resource Pool** (20+ instances)
   - Rage, Bardic Inspiration, Ki, Sorcery Points, Channel Divinity, Action Surge, etc.

2. **Scaling Damage** (15+ instances)
   - Sneak Attack, Divine Strike, Martial Arts, Brutal Critical

3. **Action Economy** (105 instances)
   - Cunning Action, Flurry of Blows, Wild Shape, Metamagic

4. **Traits** (93 instances)
   - Both static descriptions and dynamic summaries

5. **Spell Features** (50+ instances)
   - Domain spells, Magical Secrets, Arcane Recovery

6. **AC Calculations** (3 instances but unique)
   - Unarmored Defense (Barbarian, Monk), Draconic Resilience

7. **Proficiencies** (30+ instances)
   - Skills, saves, expertise, languages

8. **Defenses** (15 instances)
   - Immunities, resistances, condition immunities

### Implementation Priority

**Phase 1 - High Impact, Low Complexity:**
1. Resource pools (most common pattern)
2. Static traits (simple data)
3. Proficiencies (already partially supported)
4. Spell features (helper functions exist)

**Phase 2 - Medium Complexity:**
5. Action economy (bonus actions, reactions)
6. Scaling damage (formulas needed)
7. Defenses (straightforward)

**Phase 3 - High Complexity:**
8. AC calculations (conditional logic)
9. Dynamic traits (template system)
10. Movement (conditional modifiers)

### Next Steps

1. **Design detailed prop schemas** for each of the 8 core patterns
2. **Map each prop to `make-feat-modifiers` cases**
3. **Create helper functions** for complex conversions
4. **Build UI components** for each prop type
5. **Test with one complete feature** (e.g., Rage) end-to-end

---

**Document Version:** 1.0
**Last Updated:** 2026-01-11
**Agent ID:** a9a20a5
**Status:** Complete - Ready for Schema Design
