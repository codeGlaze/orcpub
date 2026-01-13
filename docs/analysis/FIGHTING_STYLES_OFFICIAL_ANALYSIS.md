# Fighting Styles - Official TCE Examples Analysis

**Addendum to**: FIGHTING_STYLES_EXPANDED_SCOPE.md
**Source**: Tasha's Cauldron of Everything (TCE) + The Lord of the Rings Roleplaying (TLotRR)
**Date**: 2026-01-12
**Status**: Critical Design Insights

---

## Executive Summary

The official fighting styles from **Tasha's Cauldron of Everything** (2020) demonstrate that fighting styles have evolved **FAR beyond simple combat bonuses**. They now include:

- ✅ **Spell casting** (cantrips from spell lists)
- ✅ **Senses** (blindsight)
- ✅ **Resources** (superiority dice)
- ✅ **Maneuvers** (Battle Master techniques)
- ✅ **Complex reactions** (damage reduction calculations)
- ✅ **Action economy changes** (draw weapons as part of attacks)

**Critical requirement**: Class restrictions must be customizable - some styles are Paladin-only, Ranger-only, or Fighter-only.

---

## Part 1: TCE Fighting Styles by Complexity

### Tier 1: Spell-Casting Fighting Styles 🌟 **GAME CHANGER**

#### Blessed Warrior (Paladin Only)
**Source**: TCE p52

```
You learn two cantrips of your choice from the cleric spell list.
They count as paladin spells for you, and Charisma is your spellcasting
ability for them. Whenever you gain a level in this class, you can replace
one of these cantrips with another cantrip from the cleric spell list.
```

**Requirements for Implementation**:
- ✅ Spell selection UI (choose 2 cantrips from Cleric list)
- ✅ Spell substitution on level-up
- ✅ Spellcasting ability mapping (CHA for Paladin)
- ✅ Spell source tracking ("counts as paladin spells")
- ✅ Class restriction enforcement (Paladin only)

**Data Structure**:
```clojure
{:name "Blessed Warrior"
 :key :blessed-warrior
 :class-restrictions #{:paladin}
 :description "You learn two cantrips..."
 :selections [{:name "Blessed Warrior Cantrip"
               :spell-list :cleric
               :spell-level 0
               :num 2
               :spellcasting-ability ::char5e/cha
               :replaceable-on-level-up true}]}
```

#### Druidic Warrior (Ranger Only)
**Source**: TCE p57

```
You learn two cantrips of your choice from the druid spell list.
They count as ranger spells for you, and Wisdom is your spellcasting
ability for them. Whenever you gain a level in this class, you can replace
one of these cantrips with another cantrip from the druid spell list.
```

**Same pattern as Blessed Warrior**, but:
- Druid spell list (not Cleric)
- WIS spellcasting ability (not CHA)
- Ranger restriction (not Paladin)

---

### Tier 2: Sense-Granting Fighting Styles 🌟 **NEW CAPABILITY**

#### Blind Fighting (Fighter/Paladin/Ranger)
**Source**: TCE p41

```
You have blindsight with a range of 10 feet. Within that range, you can
effectively see anything that isn't behind total cover, even if you're
blinded or in darkness. Moreover, you can see an invisible creature within
that range, unless the creature successfully hides from you.
```

**Requirements for Implementation**:
- ✅ Blindsight modifier (10 ft range)
- ✅ Trait explanation text
- ✅ Works even when blinded
- ✅ Detects invisible creatures (unless hiding)

**Data Structure**:
```clojure
{:name "Blind Fighting"
 :key :blind-fighting
 :class-restrictions #{:fighter :paladin :ranger}
 :description "You have blindsight with a range of 10 feet..."
 :props {:blindsight 10}}
```

**Modifier Implementation**:
```clojure
;; In modifiers.cljc (add alongside darkvision)
(defn blindsight [value & [order-number]]
  (mods/modifier
   ?blindsight
   value
   "Blindsight"
   (str value " feet")
   nil
   order-number))
```

---

### Tier 3: Resource-Granting Fighting Styles 🌟 **REVOLUTIONARY**

#### Superior Technique (Fighter Only)
**Source**: TCE p41

```
You learn one maneuver of your choice from among those available to the
Battle Master archetype. If a maneuver you use requires your target to make
a saving throw to resist the maneuver's effects, the saving throw DC equals
8 + your proficiency bonus + your Strength or Dexterity modifier (your choice).

You gain one superiority die, which is a d6 (this die is added to any
superiority dice you have from another source). This die is used to fuel
your maneuvers. A superiority die is expended when you use it. You regain
your expended superiority dice when you finish a short or long rest.
```

**This is HUGE** - fighting styles can now:
- ✅ Grant resource pools (superiority dice)
- ✅ Grant special abilities (maneuvers)
- ✅ Stack with existing resources ("added to any you have")
- ✅ Have complex mechanics (save DC calculations)

**Requirements for Implementation**:
- ✅ Maneuver selection UI (Battle Master list)
- ✅ Superiority dice tracking (resource pool)
- ✅ Save DC calculation
- ✅ Short rest recovery
- ✅ Stacking with existing Battle Master dice

**Data Structure**:
```clojure
{:name "Superior Technique"
 :key :superior-technique
 :class-restrictions #{:fighter}
 :description "You learn one maneuver..."
 :selections [{:name "Battle Master Maneuver"
               :options battle-master-maneuvers
               :num 1}]
 :resources [{:name "Superiority Die (Fighting Style)"
              :type :superiority-die
              :die-size 6
              :quantity 1
              :recovery :short-rest
              :stacks-with :battle-master}]
 :save-dc {:base 8
           :prof-bonus true
           :ability-choice #{::char5e/str ::char5e/dex}}}
```

---

### Tier 4: Action Economy Fighting Styles

#### Interception (Fighter/Paladin)
**Source**: TCE p41

```
When a creature you can see hits a target, other than you, within 5 feet of
you with an attack, you can use your reaction to reduce the damage the target
takes by 1d10 + your proficiency bonus (to a minimum of 0 damage). You must be
wielding a shield or a simple or martial weapon to use this reaction.
```

**More complex than Protection** - includes:
- ✅ Reaction ability
- ✅ Damage calculation (1d10 + prof bonus)
- ✅ Minimum damage clause (0 floor)
- ✅ Equipment requirements (shield or weapon)
- ✅ Range limitation (5 ft)

**Data Structure**:
```clojure
{:name "Interception"
 :key :interception
 :class-restrictions #{:fighter :paladin}
 :description "When a creature you can see hits a target..."
 :reactions [{:name "Interception"
              :frequency :unlimited
              :range 5
              :requirements [:shield-or-weapon]
              :effect {:type :reduce-damage
                       :formula "1d10 + prof-bonus"
                       :minimum 0}}]}
```

#### Thrown Weapon Fighting (Fighter/Ranger)
**Source**: TCE p42

```
You can draw a weapon that has the thrown property as part of the attack
you make with the weapon.

In addition, when you hit with a ranged attack using a thrown weapon,
you gain a +2 bonus to the damage roll.
```

**Dual benefits**:
- ✅ Action economy change (draw as part of attack)
- ✅ Damage bonus (+2)
- ✅ Weapon type restriction (thrown property)

**Data Structure**:
```clojure
{:name "Thrown Weapon Fighting"
 :key :thrown-weapon-fighting
 :class-restrictions #{:fighter :ranger}
 :description "You can draw a weapon that has the thrown property..."
 :props {:thrown-weapon-damage-bonus 2
         :draw-thrown-weapon-free true}}
```

---

### Tier 5: TLotRR Variants (Different Mechanics)

#### Great Weapon Fighting (TLotRR)
**Source**: TLotRR p49

```
When you roll damage for an attack you make with a melee weapon that you are
wielding with two hands, you can treat any roll of 4 or less on a damage die
as a 5. The weapon must have the two-handed or versatile property for you to
gain this benefit.
```

**Different from PHB version**:
- PHB: Reroll 1s and 2s (must use new roll)
- TLotRR: Treat 1-4 as 5 (guaranteed increase!)

**This shows**: Different game systems have different interpretations of same-named fighting styles.

#### Protection (TLotRR)
**Source**: TLotRR p49

```
When a creature you can see hits you or a target other than you that is within
5 feet of you with a melee attack, you can use your reaction to add your
proficiency bonus to the target's AC for that attack, potentially causing the
attack to miss. You must be wielding a shield or a finesse weapon.
```

**Different from PHB**:
- PHB: Impose disadvantage on attack roll
- TLotRR: Add prof bonus to AC for that attack

**Equipment requirement differs**:
- PHB: Shield only
- TLotRR: Shield OR finesse weapon

---

## Part 2: Class Restriction Matrix

From the examples file, here's the **official class availability**:

| Fighting Style        | Bard (Swords) | Fighter | Paladin | Ranger |
|-----------------------|---------------|---------|---------|--------|
| Archery               |               | ✅      |         | ✅     |
| **Blessed Warrior**   |               |         | ✅      |        |
| **Blind Fighting**    |               | ✅      | ✅      | ✅     |
| Defense               |               | ✅      | ✅      | ✅     |
| **Druidic Warrior**   |               |         |         | ✅     |
| Dueling               | ✅            | ✅      | ✅      | ✅     |
| Great Weapon Fighting |               | ✅      | ✅      |        |
| **Interception**      |               | ✅      | ✅      |        |
| Protection            |               | ✅      | ✅      |        |
| **Superior Technique**| |✅          |         |        |        |
| **Thrown Weapon Fighting**| ✅       | ✅      |         | ✅     |
| Two-Weapon Fighting   |               | ✅      |         | ✅     |
| Unarmed Fighting      |               | ✅      |         |        |

**Key Insights**:
1. **Most restrictive**: Blessed Warrior (Paladin only), Druidic Warrior (Ranger only), Superior Technique (Fighter only)
2. **Most available**: Dueling (all 4 classes/subclasses)
3. **Subclass access**: Bard (College of Swords) gets limited options
4. **No universal styles**: Every style has at least one class that can't access it

---

## Part 3: Fighting Initiate Feat Pattern

**Source**: TCE (referenced in examples)

```
Fighting Initiate feat makes fighting styles available to any character,
but it is limited to options available to the Fighter. This means that
Blessed Warrior and Druidic Warrior are not available outside of the
Paladin and Ranger classes, respectively.
```

**Design Implications**:
1. **Feats can grant fighting styles** (already anticipated)
2. **Feat restrictions differ from class restrictions**:
   - Via feat: Any Fighter-available style
   - Via class: Class-specific styles only
3. **Blessed Warrior & Druidic Warrior** remain exclusive even through feats

**For Homebrew**:
> "These limitations do not necessarily apply when creating homebrew with
> similar ideas in mind. So which class can access them and under what
> terms must be optional/customizable"

**CRITICAL REQUIREMENT**: Homebrew fighting styles must allow:
- ✅ Custom class restrictions (not just Fighter/Paladin/Ranger)
- ✅ "Available to all" option
- ✅ "Available via feat" option
- ✅ "Exclusive to specific class" option

---

## Part 4: Champion Way (Adjacent Features)

The examples include **Champion Way** subclass features from The Lord of the Rings Roleplaying game, showing similar patterns:

### Sharp-Shooter Way
- **Bonus Proficiency** (3rd level): Perception proficiency or expertise
- **Mighty Shot** (3rd level): Trade attack roll for extra damage dice

### Slayer Way
- **Sterner Than Steel** (3rd level): +3 HP at this level, +1 HP per level thereafter
- **Battle-Fury** (3rd level): Reckless-attack-like feature with advantage, damage bonus, and damage resistance

**Pattern Recognition**:
These show that **"Way" features** (similar to fighting styles conceptually) can:
- Grant proficiencies
- Add HP per level
- Create complex attack trade-offs
- Grant temporary advantages with drawbacks

While these are from a different system, they **validate the "quarter feat" model** - combat-focused features with significant mechanical depth.

---

## Part 5: Critical Implementation Requirements (Updated)

### Minimum Viable Product MUST Include:

#### 1. Class Restrictions System
```clojure
(spec/def ::class-restrictions (spec/coll-of keyword? :kind set?))
;; Examples:
;; #{:paladin}              - Paladin only
;; #{:fighter :paladin}     - Fighter or Paladin
;; #{}                      - Available to all
```

#### 2. Spell Selection Support
```clojure
(spec/def ::spell-selection
  (spec/keys :req-un [::spell-list ::spell-level ::num ::spellcasting-ability]
             :opt-un [::replaceable-on-level-up]))
```

#### 3. Sense Modifiers
```clojure
;; Add to make-feat-modifiers:
:blindsight [(modifiers/blindsight v)]
:tremorsense [(modifiers/tremorsense v)]
```

#### 4. Resource Pools
```clojure
(spec/def ::resource
  (spec/keys :req-un [::name ::type ::quantity ::recovery]
             :opt-un [::die-size ::stacks-with]))
```

#### 5. Maneuver/Special Ability Selection
```clojure
(spec/def ::maneuver-selection
  (spec/keys :req-un [::name ::options ::num]
             :opt-un [::save-dc]))
```

---

## Part 6: Updated Modifier Priority Matrix

### PRIORITY 0: CRITICAL (Blocks TCE styles)

**Must have for TCE fighting styles**:

| Modifier Type | Example | Fighting Style Using It |
|---------------|---------|-------------------------|
| **Spell selection** | Choose 2 cleric cantrips | Blessed Warrior |
| **Blindsight** | 10 ft blindsight | Blind Fighting |
| **Resource pool** | Superiority die d6 x1 | Superior Technique |
| **Maneuver selection** | Choose 1 Battle Master maneuver | Superior Technique |
| **Class restrictions** | Paladin only | Blessed Warrior |
| **Complex reactions** | Reduce damage by 1d10+prof | Interception |

### Priority 1: Core Combat (Original)
- Attack bonuses, damage bonuses, AC bonuses, initiative, traits, actions

### Priority 2-4: Extended Features
- (As previously defined in FIGHTING_STYLES_EXPANDED_SCOPE.md)

---

## Part 7: UI Mockup Updates

### Add New Sections:

```
╔════════════════════════════════════════════════════════════════════╗
║  Fighting Style Builder                             [New] [Save]   ║
╠════════════════════════════════════════════════════════════════════╣
║  Name: [___________________________]                               ║
║  Option Source Name: [___________________________]                 ║
║  Description: [multiline text area]                                ║
║                                                                    ║
║  ═══ Class Restrictions ═══ 🌟 NEW                                ║
║  Available to:                                                     ║
║    ☑ Fighter    ☑ Paladin    ☑ Ranger    ☐ Bard (Swords)         ║
║    ☐ All Classes    ☐ Custom: [____________]                      ║
║                                                                    ║
║  ═══ Spell Casting ═══ 🌟 NEW                                     ║
║  □ Grants Cantrips                                                ║
║    Spell List: [Cleric ▾]    Number: [2]                          ║
║    Spellcasting Ability: [Charisma ▾]                             ║
║    ☑ Replaceable on level-up                                      ║
║                                                                    ║
║  ═══ Senses ═══ 🌟 EXPANDED                                       ║
║  □ Darkvision: [___] ft                                           ║
║  □ Blindsight: [___] ft  🌟 NEW                                   ║
║  □ Tremorsense: [___] ft                                          ║
║  □ Truesight: [___] ft                                            ║
║                                                                    ║
║  ═══ Resources ═══ 🌟 NEW                                         ║
║  □ Grant Resource Pool                                            ║
║    Name: [Superiority Die]                                        ║
║    Type: [Superiority Die ▾]  Die Size: [d6 ▾]                    ║
║    Quantity: [1]    Recovery: [Short Rest ▾]                      ║
║    ☑ Stacks with existing resources                               ║
║                                                                    ║
║  ═══ Special Abilities ═══ 🌟 EXPANDED                            ║
║  □ Grant Maneuver/Technique                                       ║
║    Source: [Battle Master ▾]  Number: [1]                         ║
║    Save DC: ☑ 8 + Prof + [STR/DEX ▾]                              ║
║                                                                    ║
║  □ Grants Reaction: [+ Add Reaction]                              ║
║    Name: [Interception]                                           ║
║    Effect: [Reduce damage by 1d10 + prof bonus]                   ║
║    Requirements: [Shield or weapon ▾]                              ║
║                                                                    ║
║  ═══ Combat Bonuses ═══                                           ║
║  [... existing combat bonus UI ...]                               ║
╚════════════════════════════════════════════════════════════════════╝
```

---

## Part 8: Example Implementations (TCE Styles)

### Blessed Warrior (Complete Implementation)

```clojure
;; Data format (orcbrew plugin)
{:name "Blessed Warrior"
 :key :blessed-warrior
 :option-pack "Tasha's Cauldron of Everything"
 :class-restrictions #{:paladin}
 :description "You learn two cantrips of your choice from the cleric spell list. They count as paladin spells for you, and Charisma is your spellcasting ability for them. Whenever you gain a level in this class, you can replace one of these cantrips with another cantrip from the cleric spell list."
 :spell-selections [{:name "Blessed Warrior Cantrip"
                     :spell-list :cleric
                     :spell-level 0
                     :num 2
                     :spellcasting-ability ::char5e/cha
                     :class-name "Paladin"
                     :replaceable-on-level-up true}]}

;; Conversion function
(defn fighting-style-spell-selections [spell-selections]
  (map
   (fn [{:keys [name spell-list spell-level num spellcasting-ability class-name replaceable-on-level-up]}]
     (spell-selection-from-list
      {:name name
       :spell-list spell-list
       :level spell-level
       :num num
       :ability spellcasting-ability
       :class-name class-name
       :replaceable? replaceable-on-level-up}))
   spell-selections))

;; In fighting-style-option-from-cfg
(defn fighting-style-option-from-cfg [{:keys [name key description props spell-selections class-restrictions]}]
  (let [style-mods (fighting-style-modifiers key name description props)
        style-selections (fighting-style-spell-selections spell-selections)]
    (t/option-cfg
     {:name name
      :key key
      :modifiers style-mods
      :selections style-selections
      :class-restrictions class-restrictions})))
```

### Blind Fighting (Complete Implementation)

```clojure
{:name "Blind Fighting"
 :key :blind-fighting
 :option-pack "Tasha's Cauldron of Everything"
 :class-restrictions #{:fighter :paladin :ranger}
 :description "You have blindsight with a range of 10 feet. Within that range, you can effectively see anything that isn't behind total cover, even if you're blinded or in darkness. Moreover, you can see an invisible creature within that range, unless the creature successfully hides from you."
 :props {:blindsight 10}}
```

### Superior Technique (Complete Implementation)

```clojure
{:name "Superior Technique"
 :key :superior-technique
 :option-pack "Tasha's Cauldron of Everything"
 :class-restrictions #{:fighter}
 :description "You learn one maneuver of your choice from among those available to the Battle Master archetype. If a maneuver you use requires your target to make a saving throw to resist the maneuver's effects, the saving throw DC equals 8 + your proficiency bonus + your Strength or Dexterity modifier (your choice). You gain one superiority die, which is a d6 (this die is added to any superiority dice you have from another source). This die is used to fuel your maneuvers. A superiority die is expended when you use it. You regain your expended superiority dice when you finish a short or long rest."
 :maneuver-selection {:source :battle-master
                      :num 1
                      :save-dc {:base 8
                                :prof-bonus true
                                :ability-choice #{::char5e/str ::char5e/dex}}}
 :resources [{:name "Superiority Die (Fighting Style)"
              :type :superiority-die
              :die-size 6
              :quantity 1
              :recovery :short-rest
              :stacks-with :battle-master}]}
```

---

## Part 9: Architecture Implications

### New Components Needed

#### 1. Class Restriction Enforcement
```clojure
(defn class-restricted-fighting-styles [character all-styles]
  (let [char-classes (get-character-classes character)]
    (filter
     (fn [style]
       (let [restrictions (:class-restrictions style)]
         (or (empty? restrictions)  ;; No restriction = available to all
             (some restrictions char-classes))))
     all-styles)))
```

#### 2. Spell Selection Integration
```clojure
;; Reuse existing spell selection machinery
;; But track source as "Fighting Style: Blessed Warrior"
;; Allow replacement on level-up (new feature)
```

#### 3. Resource Pool System
```clojure
;; May need new resource tracking if superiority dice
;; from fighting style should stack with Battle Master
;; Requires resource source tracking
```

#### 4. Maneuver Selection
```clojure
;; Reuse Battle Master maneuver selection
;; But available to non-Battle Master fighters
;; May require separate maneuver tracking
```

---

## Part 10: Updated Implementation Timeline

### Phase 0: Critical Infrastructure (2 weeks) 🌟 NEW PHASE
**Goal**: Support TCE fighting styles

**Tasks**:
1. Class restriction system
2. Blindsight modifier (+ tremorsense, truesight)
3. Spell selection integration for fighting styles
4. Resource pool system (superiority dice)
5. Maneuver selection integration
6. Complex reaction system (damage calculations)

**Deliverable**: Can implement all TCE fighting styles

### Phase 1: Foundation (1 week)
(As originally planned - data schema, specs, etc.)

### Phase 2: Conversion & Integration (1 week)
(As originally planned - but now includes Phase 0 work)

### Phase 3-5: UI & Polish (3 weeks)
(As originally planned)

**Total**: 7 weeks (vs original 5 weeks, but now includes ALL TCE complexity)

---

## Conclusion

The official TCE fighting styles reveal that fighting styles are **far more powerful and complex** than currently implemented. They can:

1. ✅ Grant spell casting (cantrips from spell lists)
2. ✅ Grant senses (blindsight)
3. ✅ Grant resources (superiority dice)
4. ✅ Grant special abilities (maneuvers)
5. ✅ Have complex class restrictions
6. ✅ Modify action economy
7. ✅ Include complex calculations (1d10 + prof bonus)

**This validates and EXCEEDS the "half feat" model** - some fighting styles are as powerful as full feats!

**Critical Success Factor**: Don't try to support everything at once. Implement in phases:
1. **Phase 0**: TCE infrastructure (spell selection, senses, resources)
2. **Phase 1+**: Progressive expansion as originally planned

**Next Steps**:
1. Review this analysis
2. Prioritize which TCE styles to support in MVP
3. Design spell selection + resource system
4. Begin Phase 0 implementation

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent
**Status**: Critical - Awaiting Review
