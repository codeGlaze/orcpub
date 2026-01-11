# Homebrew Class Props Schema Design

**Created:** 2026-01-11
**Status:** Schema Design - Ready for Review
**Purpose:** Define the data format for exposing class features to homebrew

---

## Overview

This document defines the `:props` schema that allows homebrew classes to configure and use the same features as built-in classes. Each prop type maps to specific modifier functions that already exist in the codebase.

**Design Principles:**
1. **Data-Driven** - All configuration via data, no code required
2. **DRY** - Reuse existing modifier functions
3. **Backward Compatible** - Extends current `:props` system
4. **Progressive** - Simple features easy, complex features possible
5. **Self-Documenting** - Schema includes examples and descriptions

---

## Prop Type Summary

| Prop Type | Priority | Complexity | Use Cases |
|-----------|----------|------------|-----------|
| `:resource-pool` | High | Low | Rage, Ki, Bardic Inspiration, Channel Divinity |
| `:static-trait` | High | Low | Danger Sense, Feral Instinct, Proficiencies |
| `:proficiency` | High | Low | Skill/save/tool/language proficiencies |
| `:spell-feature` | High | Medium | Domain spells, Magical Secrets, Arcane Recovery |
| `:action-feature` | Medium | Medium | Cunning Action, Wild Shape, Metamagic |
| `:scaling-damage` | Medium | Medium | Sneak Attack, Divine Strike, Martial Arts |
| `:defense` | Medium | Low | Resistances, immunities, condition immunities |
| `:ac-calculation` | Low | High | Unarmored Defense, Draconic Resilience |
| `:dynamic-trait` | Low | High | Jack of All Trades, Aura of Protection |
| `:movement` | Low | Medium | Speed bonuses, flying, conditional movement |

---

## 1. Resource Pool

**Purpose:** Features with limited uses that restore on rest (Rage, Ki, Bardic Inspiration, Channel Divinity, Action Surge, Sorcery Points, etc.)

### Schema

```clojure
:resource-pool {
  :feature-key {
    :enabled true
    :name "Feature Name"
    :action-type :action | :bonus-action | :reaction | :other
    :uses {
      :scaling :level | :ability | :fixed | :level-breakpoints

      ;; For :fixed
      :amount integer

      ;; For :ability
      :ability ::char5e/cha | ::char5e/wis | ::char5e/int | ::char5e/str | ::char5e/dex | ::char5e/con
      :min integer                    ;; Minimum uses (e.g., 1)

      ;; For :level (simple multiplier)
      :multiplier integer             ;; e.g., 5 for Lay on Hands

      ;; For :level-breakpoints (stepped progression)
      :breakpoints {level amount}     ;; e.g., {1 2, 3 3, 6 4}
    }
    :recovery :long-rest | :short-rest | :daily
    :duration {
      :amount integer | :formula
      :unit :round | :minute | :hour | :unlimited

      ;; For :formula
      :formula-type :level-divided      ;; e.g., (level / 2) for Wild Shape
      :divisor integer
      :round :up | :down
    }
    :summary "Static text or template with {variables}"
    :summary-variables {              ;; For dynamic summaries
      :variable-name {
        :type :level-value | :ability-bonus | :formula
        :formula "..."
      }
    }
    :page integer
  }
}
```

### Examples

#### Barbarian - Rage
```clojure
:resource-pool {
  :rage {
    :enabled true
    :name "Rage"
    :action-type :bonus-action
    :uses {
      :scaling :level-breakpoints
      :breakpoints {1 2, 3 3, 6 4, 12 5, 17 6}
    }
    :recovery :long-rest
    :duration {:amount 1 :unit :minute}
    :summary "Advantage on STR checks/saves; melee damage bonus {damage}; resistance to bludgeoning, piercing, slashing"
    :summary-variables {
      :damage {
        :type :level-value
        :breakpoints {1 2, 9 3, 16 4}
      }
    }
    :page 48
  }
}
```

#### Bard - Bardic Inspiration
```clojure
:resource-pool {
  :bardic-inspiration {
    :enabled true
    :name "Bardic Inspiration"
    :action-type :bonus-action
    :uses {
      :scaling :ability
      :ability ::char5e/cha
      :min 1
    }
    :recovery :long-rest
    :duration {:amount 10 :unit :minute}
    :summary "Grant ally {die} to add to ability check, attack, or save"
    :summary-variables {
      :die {
        :type :level-value
        :breakpoints {1 "d6", 5 "d8", 10 "d10", 15 "d12"}
      }
    }
    :page 53
  }
}
```

#### Monk - Ki Points
```clojure
:resource-pool {
  :ki-points {
    :enabled true
    :name "Ki"
    :action-type :other
    :uses {
      :scaling :level
      :multiplier 1        ;; ki = level × 1
    }
    :recovery :short-rest
    :duration {:amount 0 :unit :unlimited}
    :summary "Spend to fuel monk abilities"
    :page 78
  }
}
```

#### Paladin - Lay on Hands
```clojure
:resource-pool {
  :lay-on-hands {
    :enabled true
    :name "Lay on Hands"
    :action-type :action
    :uses {
      :scaling :level
      :multiplier 5        ;; pool = level × 5
    }
    :recovery :long-rest
    :duration {:amount 0 :unit :unlimited}
    :summary "Heal {pool} HP total, or spend 5 to cure disease/poison"
    :summary-variables {
      :pool {:type :formula :formula "level * 5"}
    }
    :page 84
  }
}
```

#### Druid - Wild Shape
```clojure
:resource-pool {
  :wild-shape {
    :enabled true
    :name "Wild Shape"
    :action-type :action
    :uses {
      :scaling :fixed
      :amount 2
    }
    :recovery :short-rest
    :duration {
      :amount 0
      :unit :hour
      :formula-type :level-divided
      :divisor 2
      :round :down
    }
    :summary "Transform into beast of CR {cr} or lower for {hours} hours"
    :summary-variables {
      :cr {:type :level-value :breakpoints {2 "1/4", 4 "1/2", 8 "1"}}
      :hours {:type :formula :formula "(level / 2) rounded down"}
    }
    :page 66
  }
}
```

### Conversion to Modifiers

```clojure
(defn resource-pool-modifiers [config class-key]
  "Converts :resource-pool prop to modifier"
  (let [{:keys [name action-type uses recovery duration summary summary-variables page]} config
        frequency-fn (case recovery
                       :long-rest units5e/long-rests
                       :short-rest units5e/rests
                       :daily (fn [_] units5e/days-1))
        uses-value (calculate-uses uses class-key)
        duration-value (calculate-duration duration class-key)
        action-fn (case action-type
                    :action mod5e/action
                    :bonus-action mod5e/bonus-action
                    :reaction mod5e/reaction
                    mod5e/dependent-trait)]
    [(action-fn
      {:name name
       :page page
       :frequency (frequency-fn uses-value)
       :duration duration-value
       :summary (render-summary summary summary-variables class-key)})]))
```

---

## 2. Static Trait

**Purpose:** Class features that are always-on with static descriptions (Danger Sense, Feral Instinct, Evasion, etc.)

### Schema

```clojure
:static-trait {
  :feature-key {
    :enabled true
    :name "Trait Name"
    :level integer
    :summary "Description of the trait"
    :page integer
  }
}
```

### Examples

#### Barbarian - Danger Sense
```clojure
:static-trait {
  :danger-sense {
    :enabled true
    :name "Danger Sense"
    :level 2
    :summary "Advantage on DEX saves against effects you can see"
    :page 48
  }
}
```

#### Rogue - Evasion
```clojure
:static-trait {
  :evasion {
    :enabled true
    :name "Evasion"
    :level 7
    :summary "No damage on successful DEX save (half normally), half on fail"
    :page 96
  }
}
```

### Conversion to Modifiers

```clojure
(defn static-trait-modifiers [traits]
  "Converts :static-trait props to modifiers"
  (map
   (fn [[trait-key config]]
     (mod5e/trait
      (:name config)
      nil
      (:level config)
      (:summary config)))
   (filter (fn [[_ config]] (:enabled config)) traits)))
```

---

## 3. Proficiency

**Purpose:** Grant skill, save, tool, language, weapon, or armor proficiencies (with optional expertise)

### Schema

```clojure
:proficiency {
  :skills {keyword true/false}                  ;; :athletics, :stealth, etc.
  :skills-expertise {keyword true/false}        ;; Expertise in specific skills
  :saves {keyword true/false}                   ;; ::char5e/str, ::char5e/dex, etc.
  :tools {keyword true/false}                   ;; :thieves-tools, :smiths-tools, etc.
  :tools-expertise {keyword true/false}
  :languages {keyword true/false}               ;; :elvish, :draconic, etc.
  :all-languages? boolean                       ;; Grant all languages (Monk level 13)
  :weapons {keyword true/false}                 ;; :longsword, :martial, :simple, etc.
  :armor {keyword true/false}                   ;; :light, :medium, :heavy, :shields
}
```

### Examples

#### Warlock - Beguiling Influence (Invocation)
```clojure
:proficiency {
  :skills {:deception true :persuasion true}
}
```

#### Cleric - Blessings of Knowledge (Domain)
```clojure
:proficiency {
  :skills {:arcana true :history true}           ;; Player chooses 2
  :skills-expertise {:arcana true :history true} ;; Expertise in chosen skills
  :languages {:language-choice-1 true :language-choice-2 true}
}
```

#### Monk - Tongue of Sun and Moon
```clojure
:proficiency {
  :all-languages? true
}
```

### Conversion to Modifiers

```clojure
(defn proficiency-modifiers [config]
  "Converts :proficiency prop to modifiers"
  (concat
   ;; Skills
   (map #(mod5e/skill-proficiency %) (keys (filter val (:skills config))))

   ;; Expertise
   (map #(mod5e/skill-expertise %) (keys (filter val (:skills-expertise config))))

   ;; Saves
   (when (seq (:saves config))
     [(apply mod5e/saving-throws nil (keys (filter val (:saves config))))])

   ;; Languages
   (if (:all-languages? config)
     (map #(mod5e/language %) (vals language-map))
     (map #(mod5e/language %) (keys (filter val (:languages config)))))

   ;; Weapons
   (map #(mod5e/weapon-proficiency %) (keys (filter val (:weapons config))))

   ;; Armor
   (map #(mod5e/armor-proficiency %) (keys (filter val (:armor config))))))
```

---

## 4. Spell Feature

**Purpose:** Grant spells (domain spells, magical secrets, arcane recovery, etc.)

### Schema

```clojure
:spell-feature {
  :feature-key {
    :enabled true
    :grant-type :always-prepared | :known | :recovery | :secrets

    ;; For :always-prepared (Cleric domains, Paladin oaths)
    :spells {
      spell-level [spell-keys]
    }
    :min-class-level integer      ;; When these spells become available

    ;; For :recovery (Arcane Recovery, Natural Recovery)
    :recovery-type :short-rest | :daily
    :recovery-amount {
      :formula :level-divided
      :divisor integer
      :round :up | :down
      :max-slot-level integer     ;; Max 5th level for Arcane Recovery
    }

    ;; For :secrets (Bard Magical Secrets)
    :num-spells integer
    :min-spell-level integer
    :max-spell-level integer
    :any-class? boolean

    :name "Feature Name"
    :summary "Description"
    :page integer
  }
}
```

### Examples

#### Cleric - Life Domain Spells
```clojure
:spell-feature {
  :life-domain-spells {
    :enabled true
    :grant-type :always-prepared
    :spells {
      1 [:bless :cure-wounds]
      2 [:lesser-restoration :spiritual-weapon]
      3 [:beacon-of-hope :revivify]
      4 [:death-ward :guardian-of-faith]
      5 [:mass-cure-wounds :raise-dead]
    }
    :min-class-level 1
    :name "Life Domain Spells"
    :summary "These spells are always prepared"
    :page 60
  }
}
```

#### Wizard - Arcane Recovery
```clojure
:spell-feature {
  :arcane-recovery {
    :enabled true
    :grant-type :recovery
    :recovery-type :daily
    :recovery-amount {
      :formula :level-divided
      :divisor 2
      :round :down
      :max-slot-level 5
    }
    :name "Arcane Recovery"
    :summary "Once per day during short rest, recover spell slots totaling ≤ (level/2), max 5th level"
    :page 115
  }
}
```

#### Bard - Magical Secrets
```clojure
:spell-feature {
  :magical-secrets-10 {
    :enabled true
    :grant-type :secrets
    :num-spells 2
    :min-spell-level 0
    :max-spell-level 5          ;; Based on bard level 10
    :any-class? true
    :name "Magical Secrets"
    :summary "Learn 2 spells from any class"
    :page 54
  }
}
```

### Conversion to Modifiers

```clojure
(defn spell-feature-modifiers [config class-key]
  "Converts :spell-feature prop to modifiers"
  (case (:grant-type config)
    :always-prepared
    (mapcat
     (fn [[spell-level spell-keys]]
       (map #(cleric-spell-modifier spell-level % (:min-class-level config))
            spell-keys))
     (:spells config))

    :recovery
    [(mod5e/dependent-trait
      {:name (:name config)
       :page (:page config)
       :frequency units5e/days-1
       :summary (recovery-summary config class-key)})]

    :secrets
    ;; Creates selection
    []))
```

---

## 5. Action Feature

**Purpose:** Grant actions, bonus actions, or reactions (Cunning Action, Flurry of Blows, Deflect Missiles, etc.)

### Schema

```clojure
:action-feature {
  :feature-key {
    :enabled true
    :name "Feature Name"
    :action-type :action | :bonus-action | :reaction
    :level integer
    :cost {
      :type :none | :ki | :sorcery-points | :spell-slot | :custom
      :amount integer | :variable
    }
    :trigger string                   ;; For reactions
    :effect string
    :frequency :unlimited | :resource-limited
    :page integer
  }
}
```

### Examples

#### Rogue - Cunning Action
```clojure
:action-feature {
  :cunning-action {
    :enabled true
    :name "Cunning Action"
    :action-type :bonus-action
    :level 2
    :cost {:type :none}
    :effect "Dash, Disengage, or Hide"
    :frequency :unlimited
    :page 96
  }
}
```

#### Monk - Flurry of Blows
```clojure
:action-feature {
  :flurry-of-blows {
    :enabled true
    :name "Flurry of Blows"
    :action-type :bonus-action
    :level 2
    :cost {:type :ki :amount 1}
    :effect "Make two unarmed strikes"
    :frequency :resource-limited
    :page 78
  }
}
```

#### Monk - Deflect Missiles
```clojure
:action-feature {
  :deflect-missiles {
    :enabled true
    :name "Deflect Missiles"
    :action-type :reaction
    :level 3
    :cost {:type :ki :amount 1}  ;; Only if throwing back
    :trigger "Hit by ranged weapon attack"
    :effect "Reduce damage by 1d10 + DEX + monk level; catch and throw back for 1 ki"
    :frequency :unlimited
    :page 78
  }
}
```

### Conversion to Modifiers

```clojure
(defn action-feature-modifiers [config class-key]
  "Converts :action-feature prop to modifiers"
  (let [{:keys [name action-type level cost trigger effect frequency page]} config
        action-fn (case action-type
                    :action mod5e/action
                    :bonus-action mod5e/bonus-action
                    :reaction mod5e/reaction)
        summary (if trigger
                  (str "(" trigger ") " effect)
                  effect)
        freq (when (= :resource-limited frequency)
               (resource-frequency cost class-key))]
    [(action-fn
      (merge
       {:name name
        :page page
        :level level
        :summary summary}
       (when freq {:frequency freq})))]))
```

---

## 6. Scaling Damage

**Purpose:** Damage that increases with level (Sneak Attack, Divine Strike, Martial Arts, Brutal Critical)

### Schema

```clojure
:scaling-damage {
  :feature-key {
    :enabled true
    :name "Feature Name"
    :damage {
      :dice-count {
        :type :level-formula | :level-breakpoints | :fixed

        ;; For :level-formula (e.g., Sneak Attack)
        :formula :level-divided-rounded-up
        :divisor integer

        ;; For :level-breakpoints (e.g., Divine Strike)
        :breakpoints {level count}

        ;; For :fixed
        :amount integer
      }
      :dice-size {
        :type :fixed | :level-breakpoints
        :size integer                   ;; For :fixed (d6, d8, etc.)
        :breakpoints {level size}       ;; For :level-breakpoints (Martial Arts)
      }
      :damage-type keyword | :weapon    ;; :radiant, :thunder, :weapon, etc.
      :modifier keyword | nil           ;; Ability to add (::char5e/str, etc.)
    }
    :frequency :per-turn | :per-attack | :on-crit
    :trigger string
    :conditions [string]
    :level integer
    :page integer
  }
}
```

### Examples

#### Rogue - Sneak Attack
```clojure
:scaling-damage {
  :sneak-attack {
    :enabled true
    :name "Sneak Attack"
    :damage {
      :dice-count {
        :type :level-formula
        :formula :level-divided-rounded-up
        :divisor 2
      }
      :dice-size {:type :fixed :size 6}
      :damage-type :weapon
      :modifier nil
    }
    :frequency :per-turn
    :trigger "Advantage or ally within 5ft of target"
    :conditions ["Finesse or ranged weapon"]
    :level 1
    :page 96
  }
}
```

#### Cleric - Divine Strike (Life Domain)
```clojure
:scaling-damage {
  :divine-strike {
    :enabled true
    :name "Divine Strike"
    :damage {
      :dice-count {
        :type :level-breakpoints
        :breakpoints {8 1, 14 2}
      }
      :dice-size {:type :fixed :size 8}
      :damage-type :radiant
      :modifier nil
    }
    :frequency :per-attack
    :trigger "Once per turn when you hit with weapon attack"
    :level 8
    :page 60
  }
}
```

#### Monk - Martial Arts
```clojure
:scaling-damage {
  :martial-arts {
    :enabled true
    :name "Martial Arts"
    :damage {
      :dice-count {:type :fixed :amount 1}
      :dice-size {
        :type :level-breakpoints
        :breakpoints {1 4, 5 6, 11 8, 17 10}
      }
      :damage-type :weapon
      :modifier ::char5e/str-or-dex
    }
    :frequency :per-attack
    :trigger "Unarmed strike or monk weapon"
    :level 1
    :page 78
  }
}
```

#### Barbarian - Brutal Critical
```clojure
:scaling-damage {
  :brutal-critical {
    :enabled true
    :name "Brutal Critical"
    :damage {
      :dice-count {
        :type :level-breakpoints
        :breakpoints {9 1, 13 2, 17 3}
      }
      :dice-size {:type :weapon}  ;; Use weapon's dice
      :damage-type :weapon
      :modifier nil
    }
    :frequency :on-crit
    :trigger "Critical hit with melee attack"
    :level 9
    :page 49
  }
}
```

### Conversion to Modifiers

```clojure
(defn scaling-damage-modifiers [config class-key]
  "Converts :scaling-damage prop to modifiers"
  (let [{:keys [name damage frequency trigger level page]} config
        dice-count (calculate-dice-count (:dice-count damage) class-key)
        dice-size (calculate-dice-size (:dice-size damage) class-key)
        summary (format-damage-summary config class-key)]
    [(mod5e/dependent-trait
      {:name name
       :level level
       :page page
       :frequency (when (not= :unlimited frequency) (frequency-modifier frequency))
       :summary summary})
     ;; Additional modifiers for actual damage mechanics
     ]))
```

---

## 7. Defense

**Purpose:** Grant resistances, immunities, or condition immunities

### Schema

```clojure
:defense {
  :damage-resistance {keyword true/false}  ;; :fire, :cold, :necrotic, etc.
  :damage-immunity {keyword true/false}
  :condition-immunity {
    keyword {:enabled true/false :qualifier string}
  }
  :general-immunity {keyword true/false}  ;; :disease, etc.
}
```

### Examples

#### Monk - Purity of Body
```clojure
:defense {
  :damage-immunity {:poison true}
  :condition-immunity {:poisoned {:enabled true}}
  :general-immunity {:disease true}
}
```

#### Wizard - Necromancy School
```clojure
:defense {
  :damage-resistance {:necrotic true}
}
```

#### Druid - Circle of Land (Arctic)
```clojure
:defense {
  :damage-immunity {:poison true}
  :condition-immunity {
    :poisoned {:enabled true}
    :charmed {:enabled true :qualifier "by elementals or fey"}
    :frightened {:enabled true :qualifier "by elementals or fey"}
  }
  :general-immunity {:disease true}
}
```

### Conversion to Modifiers

```clojure
(defn defense-modifiers [config]
  "Converts :defense prop to modifiers"
  (concat
   (map #(mod5e/damage-resistance %) (keys (filter val (:damage-resistance config))))
   (map #(mod5e/damage-immunity %) (keys (filter val (:damage-immunity config))))
   (map #(mod5e/condition-immunity (key %) (:qualifier (val %)))
        (filter #(:enabled (val %)) (:condition-immunity config)))
   (map #(mod5e/immunity %) (keys (filter val (:general-immunity config))))))
```

---

## 8. AC Calculation

**Purpose:** Alternative AC calculations (Unarmored Defense, Draconic Resilience, etc.)

**⚠️ NOTE:** This is the most complex prop type due to conditional logic and modifier priorities.

### Schema

```clojure
:ac-calculation {
  :feature-key {
    :enabled true
    :name "Feature Name"
    :calculation-type :unarmored-defense | :natural-armor

    ;; For :unarmored-defense
    :base-ac integer                    ;; Usually 10
    :primary-ability keyword            ;; Usually ::char5e/dex
    :secondary-ability keyword          ;; ::char5e/con, ::char5e/wis, etc.
    :shield-allowed? boolean
    :condition "when not wearing armor"

    ;; For :natural-armor
    :base-ac integer                    ;; e.g., 13 for Draconic Resilience
    :add-dex? boolean
    :max-dex integer | nil

    :level integer
    :page integer
  }
}
```

### Examples

#### Barbarian - Unarmored Defense
```clojure
:ac-calculation {
  :barbarian-unarmored-defense {
    :enabled true
    :name "Unarmored Defense"
    :calculation-type :unarmored-defense
    :base-ac 10
    :primary-ability ::char5e/dex
    :secondary-ability ::char5e/con
    :shield-allowed? true
    :condition "when not wearing armor"
    :level 1
    :page 48
  }
}
```

#### Monk - Unarmored Defense
```clojure
:ac-calculation {
  :monk-unarmored-defense {
    :enabled true
    :name "Unarmored Defense"
    :calculation-type :unarmored-defense
    :base-ac 10
    :primary-ability ::char5e/dex
    :secondary-ability ::char5e/wis
    :shield-allowed? false
    :condition "when not wearing armor or using shield"
    :level 1
    :page 78
  }
}
```

#### Sorcerer - Draconic Resilience
```clojure
:ac-calculation {
  :draconic-resilience {
    :enabled true
    :name "Draconic Resilience"
    :calculation-type :natural-armor
    :base-ac 13
    :add-dex? true
    :max-dex nil
    :level 1
    :page 102
  }
}
```

### Conversion to Modifiers

```clojure
(defn ac-calculation-modifiers [config class-key]
  "Converts :ac-calculation prop to modifiers"
  (case (:calculation-type config)
    :unarmored-defense
    [(mod/vec-mod ?unarmored-defense class-key)
     (mod/cum-sum-mod ?unarmored-ac-bonus
                      (?ability-bonuses (:secondary-ability config))
                      nil nil
                      [(= class-key (first ?unarmored-defense))])
     (when (:shield-allowed? config)
       (mod/cum-sum-mod ?unarmored-with-shield-ac-bonus
                        (?ability-bonuses (:secondary-ability config))
                        nil nil
                        [(= class-key (first ?unarmored-defense))]))]

    :natural-armor
    [(mod/modifier ?natural-ac-bonus (- (:base-ac config) 10))]))
```

---

## 9. Dynamic Trait

**Purpose:** Traits with summaries that change based on character state (Jack of All Trades, Song of Rest, Aura of Protection)

**⚠️ NOTE:** High complexity - requires template system and character state access.

### Schema

```clojure
:dynamic-trait {
  :feature-key {
    :enabled true
    :name "Feature Name"
    :level integer
    :summary-template "Text with {variables}"
    :variables {
      :variable-name {
        :source :proficiency-bonus | :class-level | :ability-bonus | :formula
        :ability keyword           ;; If :ability-bonus
        :formula string            ;; If :formula
        :transform :divide-2 | :multiply | :level-lookup
        :round :up | :down | :none
        :lookup-table {level value}  ;; If :level-lookup
      }
    }
    :additional-modifiers [
      {
        :type :skill-bonus | :initiative | :save-bonus | :other
        :target keyword | :all
        :value {:source ... :formula ...}  ;; Same as variables
      }
    ]
    :page integer
  }
}
```

### Examples

#### Bard - Jack of All Trades
```clojure
:dynamic-trait {
  :jack-of-all-trades {
    :enabled true
    :name "Jack of All Trades"
    :level 2
    :summary-template "Add {bonus} to ability checks without proficiency"
    :variables {
      :bonus {
        :source :proficiency-bonus
        :transform :divide-2
        :round :down
      }
    }
    :additional-modifiers [
      {:type :skill-bonus
       :target :all-non-proficient
       :value {:source :proficiency-bonus :transform :divide-2 :round :down}}
      {:type :initiative
       :target :initiative
       :value {:source :proficiency-bonus :transform :divide-2 :round :down}}
    ]
    :page 54
  }
}
```

#### Bard - Song of Rest
```clojure
:dynamic-trait {
  :song-of-rest {
    :enabled true
    :name "Song of Rest"
    :level 2
    :summary-template "Allies regain extra d{die} during short rest"
    :variables {
      :die {
        :source :class-level
        :transform :level-lookup
        :lookup-table {2 6, 9 8, 13 10, 17 12}
      }
    }
    :page 54
  }
}
```

#### Paladin - Aura of Protection
```clojure
:dynamic-trait {
  :aura-of-protection {
    :enabled true
    :name "Aura of Protection"
    :level 6
    :summary-template "You and allies within {range} ft gain +{bonus} to saves"
    :variables {
      :range {
        :source :class-level
        :transform :level-lookup
        :lookup-table {6 10, 18 30}
      }
      :bonus {
        :source :ability-bonus
        :ability ::char5e/cha
      }
    }
    :additional-modifiers [
      {:type :save-bonus
       :target :all-saves
       :value {:source :ability-bonus :ability ::char5e/cha}}
    ]
    :page 85
  }
}
```

### Conversion to Modifiers

```clojure
(defn dynamic-trait-modifiers [config class-key]
  "Converts :dynamic-trait prop to modifiers"
  (concat
   [(mod5e/dependent-trait
     {:name (:name config)
      :level (:level config)
      :page (:page config)
      :summary (render-template (:summary-template config)
                                (:variables config)
                                class-key)})]
   (map #(additional-modifier % class-key) (:additional-modifiers config))))
```

---

## 10. Movement

**Purpose:** Modify movement speeds (Monk speed, Barbarian Fast Movement, Flying)

### Schema

```clojure
:movement {
  :feature-key {
    :enabled true
    :name "Feature Name"
    :movement-type :speed | :flying | :swimming | :climbing
    :bonus {
      :type :fixed | :cumulative | :formula
      :amount integer             ;; For :fixed
      :increments [               ;; For :cumulative (Monk)
        {:level integer :amount integer}
      ]
      :formula string             ;; For :formula
    }
    :condition string | nil       ;; e.g., "when not wearing heavy armor"
    :equals-walking? boolean      ;; Flying speed = walking speed
    :level integer
    :page integer
  }
}
```

### Examples

#### Monk - Unarmored Movement
```clojure
:movement {
  :unarmored-movement {
    :enabled true
    :name "Unarmored Movement"
    :movement-type :speed
    :bonus {
      :type :cumulative
      :increments [
        {:level 2 :amount 10}
        {:level 6 :amount 5}
        {:level 10 :amount 5}
        {:level 14 :amount 5}
        {:level 18 :amount 5}
      ]
    }
    :condition "when not wearing armor or using shield"
    :level 2
    :page 78
  }
}
```

#### Barbarian - Fast Movement
```clojure
:movement {
  :fast-movement {
    :enabled true
    :name "Fast Movement"
    :movement-type :speed
    :bonus {:type :fixed :amount 10}
    :condition "when not wearing heavy armor"
    :level 5
    :page 49
  }
}
```

#### Cleric - Stormborn (Tempest Domain)
```clojure
:movement {
  :stormborn {
    :enabled true
    :name "Stormborn"
    :movement-type :flying
    :equals-walking? true
    :level 17
    :page 62
  }
}
```

### Conversion to Modifiers

```clojure
(defn movement-modifiers [config class-key]
  "Converts :movement prop to modifiers"
  (case (:movement-type config)
    :speed
    (if (:condition config)
      [(mod/modifier ?speed-with-armor
                     (fn [armor]
                       (if (meets-condition? armor (:condition config))
                         (+ (calculate-bonus (:bonus config) class-key) ?speed)
                         ?speed)))]
      (map #(mod5e/unarmored-speed-bonus (:amount %))
           (filter #(<= (:level %) (?class-level class-key))
                   (:increments (:bonus config)))))

    :flying
    (if (:equals-walking? config)
      [(mod5e/flying-speed-equal-to-walking)]
      [(mod5e/flying-speed-override (calculate-bonus (:bonus config) class-key))])))
```

---

## Implementation Roadmap

### Phase 1: Foundation (High Impact, Low Complexity)

**Priority 1:** Resource Pools
- Most common pattern (20+ instances)
- Clear examples (Rage, Ki, Bardic Inspiration)
- Immediate user value

**Priority 2:** Static Traits
- Simple data structure
- No dynamic calculation
- Many instances (93 total)

**Priority 3:** Proficiencies
- Partially supported already
- Straightforward conversion
- Essential for class identity

**Priority 4:** Spell Features (simple)
- Domain spells (always-prepared)
- Easy to implement
- High user demand

### Phase 2: Core Features (Medium Complexity)

**Priority 5:** Action Features
- Bonus actions, reactions, actions
- 105 total instances
- Core gameplay mechanics

**Priority 6:** Scaling Damage
- Sneak Attack, Divine Strike
- Requires formula system
- High impact on combat

**Priority 7:** Defense
- Resistances, immunities
- Straightforward mapping
- Important for survivability

### Phase 3: Advanced Features (High Complexity)

**Priority 8:** Movement
- Conditional modifiers
- Less critical than core features

**Priority 9:** Dynamic Traits
- Requires template system
- Complex variable resolution
- Can be approximated with static text initially

**Priority 10:** AC Calculation
- Most complex
- Conditional logic
- Modifier priority issues
- Implement last, test thoroughly

---

## Validation & Error Handling

### Schema Validation

```clojure
(spec/def ::resource-pool
  (spec/keys :req-un [::enabled ::name ::action-type ::uses ::recovery]
             :opt-un [::duration ::summary ::page]))

(spec/def ::enabled boolean?)
(spec/def ::name string?)
(spec/def ::action-type #{:action :bonus-action :reaction :other})
;; ... etc
```

### Error Messages

**Missing Required Fields:**
```
"Resource pool ':rage' is missing required field :uses"
```

**Invalid Values:**
```
"Resource pool ':rage' has invalid :action-type ':free-action'.
 Must be one of: :action, :bonus-action, :reaction, :other"
```

**Configuration Conflicts:**
```
"Resource pool ':rage' has :scaling :level but no :multiplier specified"
```

---

## Testing Strategy

### Unit Tests

Test each conversion function with example data:

```clojure
(deftest resource-pool-conversion
  (testing "Rage configuration converts to correct modifiers"
    (let [rage-config {:enabled true
                       :name "Rage"
                       :action-type :bonus-action
                       :uses {:scaling :level-breakpoints
                              :breakpoints {1 2, 3 3, 6 4, 12 5, 17 6}}
                       :recovery :long-rest
                       :duration {:amount 1 :unit :minute}}
          modifiers (resource-pool-modifiers rage-config :barbarian)]
      (is (= 1 (count modifiers)))
      (is (= "Rage" (:name (first modifiers)))))))
```

### Integration Tests

Create full homebrew class and verify it works:

```clojure
(deftest full-homebrew-class
  (testing "Homebrew 'Berserker' class with Rage works"
    (let [homebrew-class {:name "Berserker"
                          :key :berserker-123
                          :hit-die 12
                          :props {:resource-pool {:rage {...}}}}
          character (create-character homebrew-class)]
      (is (has-feature? character "Rage"))
      (is (= 2 (uses-remaining character :rage 1)))
      (is (= 3 (uses-remaining character :rage 3))))))
```

### Backward Compatibility Tests

Ensure old characters still work:

```clojure
(deftest backward-compatibility
  (testing "Characters created before prop system still load"
    (let [old-character (load-character "old-character-id")]
      (is (not (nil? old-character)))
      (is (= "Barbarian" (get-class-name old-character))))))
```

---

## Documentation Requirements

For each prop type, document:

1. **Purpose** - What it's for
2. **Schema** - Complete data structure
3. **Examples** - 3-5 real class examples
4. **Conversion** - How it maps to modifiers
5. **Validation** - What errors can occur
6. **UI Controls** - What form fields are needed

---

## Next Steps

1. **Review & Refine** this schema design
2. **Update planning document** with schema details
3. **Prototype one feature** (Rage) end-to-end:
   - Add `:resource-pool` case to `make-feat-modifiers`
   - Implement conversion function
   - Add UI controls to class-builder
   - Test with real character
4. **Iterate** based on prototype learnings
5. **Scale** to other prop types

---

**Document Version:** 1.0
**Last Updated:** 2026-01-11
**Status:** Schema Design Complete - Ready for Review & Prototype
