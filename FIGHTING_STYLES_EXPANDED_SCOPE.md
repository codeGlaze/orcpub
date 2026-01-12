# Fighting Styles - Expanded Scope & Modifier Capabilities

**Addendum to**: FIGHTING_STYLES_EXPLORATION.md
**Date**: 2026-01-12
**Status**: Design Specification

---

## Executive Summary

After reviewing official fighting style implementations and the existing modifier system, **fighting styles should be treated as "half feats" or "quarter feats"** - focused combat abilities that can modify almost ANY aspect of a character sheet. This document outlines the full scope of modifiers that fighting styles should support.

### Key Insights

1. **Fighting styles evolve significantly** - from simple (+2 ranged attack) to complex multi-ability features
2. **The feat prop system already supports ~30+ modifier types** that can be reused for fighting styles
3. **Champion's Way pattern** - some subclasses grant additional fighting styles (e.g., Champion at level 10)
4. **Generic function reusability** - fighting style builder should share code with feat builder for maximum flexibility

---

## Part 1: The Evolution of Fighting Styles

### Simple Fighting Styles (PHB - Level 1)

**Archery** (options.cljc:1690-1695)
```clojure
{:modifiers [(modifiers/ranged-attack-bonus 2)
             (modifiers/trait-cfg {...description...})]}
```
- Single modifier: +2 to ranged attack rolls
- Pure combat bonus

**Defense** (options.cljc:1697-1702)
```clojure
{:modifiers [(modifiers/armored-ac-bonus 1)
             (modifiers/trait-cfg {...description...})]}
```
- Single modifier: +1 AC while wearing armor
- Conditional combat bonus

### Moderate Complexity (PHB)

**Dueling** (options.cljc:1704-1728)
```clojure
{:modifiers [(modifiers/trait-cfg {...})
             (mods/vec-mod ?damage-bonus-fns
                           (fn [weapon _]
                             (if (or (weapon ::weapons/two-handed?)
                                     (weapon ::weapons/ranged?))
                               0
                               2))
                           nil nil
                           [(complex-condition-checking-main-and-off-hand)])]}
```
- **Complex conditional logic**: checks weapon type, handedness, off-hand status
- **Dynamic damage calculation**: function-based modifier
- **Multiple checks**: main hand weapon, off-hand weapon, weapon properties

**Protection** (options.cljc:1736-1740)
```clojure
{:modifiers [(modifiers/reaction
               {:name "Protection Fighting Style"
                :description "impose disadvantage on attack..."})}
```
- **Action economy**: grants a reaction ability
- **Not just bonuses**: adds a new combat option

### Advanced Fighting Styles (UA)

**Mariner** (ua_base.cljc:698-711) - **This is the template for complex styles**
```clojure
{:name "Mariner"
 :modifiers [opt5e/ua-al-illegal
             (mod5e/ac-bonus-fn
              (fn [armor shield]
                (if (and (nil? shield)
                         (not (= :heavy (:type armor))))
                  1
                  0)))
             (mod5e/trait-cfg
              {:name "Mariner Fighting Style"
               :summary "while not wearing heavy armor, gain +1 AC bonus
                        and you have swimming speed and climbing speed
                        equal to your land speed"})]}
```

**What Mariner demonstrates**:
- ✅ Conditional AC bonus (armor type + shield status)
- ✅ Movement speed modifiers (swimming, climbing)
- ✅ Multiple benefits in one style
- ✅ Complex prerequisite logic

---

## Part 2: Full Modifier Capabilities (Props System)

The `make-feat-modifiers` function (options.cljc:3230-3286) already supports **30+ prop types**. Fighting styles should have access to ALL of these.

### Complete Prop-to-Modifier Mapping

Based on `make-feat-modifiers` analysis:

#### Combat Modifiers

| Prop Key | Creates | Example Value | Use Case |
|----------|---------|---------------|----------|
| `:initiative` | Initiative bonus | `2` | Alert-style fighting style |
| `:max-hp-bonus` | HP per level bonus | `1` | Tough-style fighting style |
| `:two-weapon-ac-1` | +1 AC with two weapons | `true` | Dual wielder style |
| `:two-weapon-any-one-handed` | Can dual wield any one-handed | `true` | Advanced two-weapon style |
| `:passive-perception-5` | +5 passive perception | `true` | Observant warrior style |
| `:passive-investigation-5` | +5 passive investigation | `true` | Tactical fighter style |

#### Armor & Defense

| Prop Key | Creates | Example Value | Use Case |
|----------|---------|---------------|----------|
| `:medium-armor-max-dex-3` | Max DEX bonus +3 in medium armor | `true` | Medium armor master style |
| `:medium-armor-stealth` | No stealth disadvantage | `true` | Stealthy armor style |
| `:lizardfolk-ac` / `:tortle-ac` | Custom AC calculation | `true` | Natural armor style |
| `:armor-prof` | Armor proficiency | `{:light true :medium true}` | Armor training style |

#### Movement

| Prop Key | Creates | Example Value | Use Case |
|----------|---------|---------------|----------|
| `:speed` | Base speed bonus | `10` | Mobile warrior (+10 ft) |
| `:flying-speed` | Flying speed | `30` | Winged warrior style |
| `:flying-speed-equals-walking-speed` | Fly = walk speed | `true` | Flight mastery |
| `:swimming-speed` | Swimming speed | `30` | Mariner/aquatic style |

#### Proficiencies & Skills

| Prop Key | Creates | Example Value | Use Case |
|----------|---------|---------------|----------|
| `:skill-prof` | Skill proficiency | `{:athletics true :acrobatics true}` | Skilled combatant |
| `:skill-prof-or-expertise` | Prof or expertise | `{:perception true}` | Perceptive fighter |
| `:tool-prof-or-expertise` | Tool prof/expertise | `{:smiths-tools true}` | Weaponsmith style |
| `:weapon-prof` | Weapon proficiency | `{:longsword true}` | Weapon mastery style |

#### Resistances & Immunities

| Prop Key | Creates | Example Value | Use Case |
|----------|---------|---------------|----------|
| `:damage-resistance` | Damage resistance | `{:fire true :cold true}` | Elemental warrior |
| `:damage-immunity` | Damage immunity | `{:poison true}` | Toxin-immune fighter |
| `:saving-throw-advantage` | Advantage on saves | `{:poison true :disease true}` | Hardy warrior |
| `:saving-throw-advantage-traps` | Advantage vs traps | `true` | Trap-aware style |

#### Language & Senses

| Prop Key | Creates | Example Value | Use Case |
|----------|---------|---------------|----------|
| `:language` | Language proficiency | `{:draconic true :elvish true}` | Multilingual warrior |
| `:darkvision` (from modifiers.cljc:86-100) | Darkvision | `60` | Night fighter |
| `:darkvision-bonus` | Darkvision bonus | `30` | Enhanced darkvision |

---

## Part 3: Additional Modifiers NOT in Feat Props (But Available)

Beyond the feat props system, fighting styles could also use modifiers found elsewhere in the codebase:

### Actions, Bonus Actions, Reactions

```clojure
;; From modifiers.cljc
(modifiers/action {:name "Shield Bash" :summary "..."})
(modifiers/bonus-action {:name "Quick Strike" :summary "..."})
(modifiers/reaction {:name "Parry" :summary "..."})
```

**Examples**:
- **Shield Bash** fighting style: grants bonus action to bash with shield
- **Riposte** fighting style: grants reaction to counter-attack
- **Quick Draw** fighting style: grants bonus action to draw/stow weapons

### Senses (Beyond Darkvision)

Based on pattern from darkvision modifier:

```clojure
(modifiers/blindsight 10)      ;; 10 ft blindsight
(modifiers/tremorsense 30)     ;; 30 ft tremorsense
(modifiers/truesight 60)       ;; 60 ft truesight (rare)
```

**Examples**:
- **Echo Location** fighting style: grants 10 ft blindsight
- **Ground Sense** fighting style: grants 15 ft tremorsense while on ground

### Resource Management

```clojure
;; Following patterns from classes.cljc
(modifiers/dependent-trait
  {:name "Battle Surge"
   :frequency units5e/short-rests-1
   :summary "gain temporary HP equal to your fighter level"})
```

**Examples**:
- **Second Wind Enhancement**: +1 use of second wind per short rest
- **Battle Trance**: gain temp HP when bloodied (below half HP)

### Spell Casting (Rare but Possible)

From Svirfneblin Magic feat pattern (options.cljc:3134-3137):

```clojure
(modifiers/spells-known 1 :shield ::character/int "Fighter" 0 "once per long rest")
```

**Examples**:
- **Arcane Warrior**: cast *shield* spell once per long rest
- **Divine Strike**: cast *bless* on self once per long rest

### Number of Attacks

From Fighter class (classes.cljc:1094-1095):

```clojure
(mod5e/num-attacks 3)
```

**Examples**:
- **Flurry** fighting style: make 1 additional attack when using Attack action (once per short rest)
- **Off-Hand Mastery**: extra off-hand attack

### Critical Hit Range

From Champion subclass (classes.cljc:1145):

```clojure
(mod5e/critical 19)  ;; Crit on 19-20
```

**Examples**:
- **Precision Strike** fighting style: increase critical range by 1
- **Brutal Strikes**: deal extra die of damage on critical hits

---

## Part 4: Champion's Way - Adjacent Feature Pattern

### Champion Additional Fighting Style

**Location**: classes.cljc:1164

The Champion martial archetype gets a **second fighting style** at level 10:

```clojure
{:name "Champion"
 :levels {3 {:modifiers [(mod5e/critical 19)]}
          7 {:modifiers [(athletics/acrobatics skill bonuses)]}
          10 {:selections [(opt5e/fighting-style-selection :fighter)]}  ;; <-- SECOND STYLE
          15 {:modifiers [(mod5e/critical 18)]}
          18 {:modifiers [(survivor trait)]}}}
```

### Pattern Analysis

This demonstrates a **generic pattern** for granting fighting styles:

1. **Selection-based**: Uses `fighting-style-selection` function
2. **Class-specific**: Passed `:fighter` keyword for restrictions
3. **Reusable**: Same function works at any level
4. **Stacks**: Player now has TWO fighting styles active

### Implications for Design

**Fighting style builder must support**:
1. Being called from anywhere (class features, feats, magic items)
2. Restrictions (class-specific styles)
3. Multiselect (Champion gets 2 total)
4. Dynamic options (includes homebrew + SOURCE styles)

**Similar features that could use this**:
- Feats that grant fighting styles (like "Fighting Initiate" in Tasha's)
- Magic items that grant temporary fighting styles
- Boon/epic boons that grant fighting styles
- Multiclass dip rewards

---

## Part 5: Design Philosophy - "Half Feat" Model

### What Makes Fighting Styles Like Feats?

| Aspect | Feats | Fighting Styles |
|--------|-------|-----------------|
| **Mechanical benefits** | ✅ Yes | ✅ Yes |
| **Ability score increases** | ✅ Some (half feats) | ❌ No |
| **Prerequisites** | ✅ Sometimes | ✅ Sometimes (class restriction) |
| **Combat focused** | ⚠️ Mixed | ✅ Primarily |
| **Stacking** | ❌ Usually unique | ✅ Can have multiple |
| **Complexity range** | ✅ Simple → Complex | ✅ Simple → Complex |
| **Props system** | ✅ Yes | ✅ Should use same |

### "Quarter Feat" Designation

Fighting styles are **quarter feats** because:
- ❌ No ability score increases
- ❌ No complex prerequisites (beyond class)
- ✅ Single combat-focused benefit (usually)
- ✅ Simpler than most feats
- ✅ More narrowly scoped

**But they can grow to "half feat" complexity**:
- Mariner: AC + swimming + climbing (3 benefits)
- Protection: grants reaction ability (action economy)
- Future complex styles could rival feat complexity

---

## Part 6: Comprehensive Modifier Support Matrix

### Priority 1: Core Combat (MVP)

These must be supported for initial release:

- [x] Attack bonuses (ranged, melee, specific weapons)
- [x] Damage bonuses (flat, conditional, weapon-specific)
- [x] AC bonuses (flat, conditional, armor-dependent)
- [x] Initiative bonuses
- [x] Traits (description/summary text)
- [x] Actions, bonus actions, reactions

### Priority 2: Extended Combat (Phase 2)

- [ ] Critical hit range modification
- [ ] Number of attacks modification
- [ ] Weapon proficiencies
- [ ] Armor proficiencies
- [ ] Damage resistances
- [ ] Saving throw advantages

### Priority 3: Utility & Mobility (Phase 3)

- [ ] Speed bonuses (walking, swimming, climbing, flying)
- [ ] Skill proficiencies
- [ ] Tool proficiencies
- [ ] Languages
- [ ] Passive perception/investigation bonuses
- [ ] HP bonuses

### Priority 4: Advanced Features (Phase 4)

- [ ] Senses (darkvision, blindsight, tremorsense)
- [ ] Spell casting (limited, like feats)
- [ ] Resource management (uses per rest)
- [ ] Condition immunities
- [ ] Custom AC calculations
- [ ] Complex conditional modifiers

---

## Part 7: Implementation Strategy (Updated)

### Phase 1: Foundation + Core Combat

**Goal**: Support Priority 1 modifiers

**Changes to original plan**:
- Use `plugin-modifiers` function directly (already exists!)
- Define props schema based on feat props
- No need to reinvent the wheel - reuse feat conversion logic

**Props Schema** (fighting_styles.cljc):
```clojure
(spec/def ::props
  (spec/keys :opt-un [;; Priority 1: Core Combat
                      ::initiative
                      ::ranged-attack-bonus   ;; NEW prop type
                      ::melee-attack-bonus    ;; NEW prop type
                      ::ac-bonus              ;; NEW prop type
                      ::damage-bonus          ;; NEW prop type

                      ;; Existing feat props
                      ::speed
                      ::max-hp-bonus
                      ::armor-prof
                      ::weapon-prof
                      ::damage-resistance
                      ::damage-immunity
                      ::skill-prof
                      ::tool-prof-or-expertise
                      ::language
                      ::saving-throw-advantage
                      ;; ... all other feat props
                      ]))
```

**New Prop Types Needed**:
```clojure
;; In make-feat-modifiers, add:
:ranged-attack-bonus [(modifiers/ranged-attack-bonus v)]
:melee-attack-bonus [(modifiers/melee-attack-bonus v)]
:ac-bonus [(modifiers/armored-ac-bonus v)]  ;; or conditional version
:damage-bonus [(custom-damage-bonus v)]     ;; needs design
```

### Phase 2-4: Incremental Expansion

Each phase adds support for the next priority level of modifiers.

**Key principle**: Leverage `plugin-modifiers` for ALL conversion, just expand the case statement in `make-feat-modifiers`.

---

## Part 8: UI Mockup (Updated for Expanded Scope)

```
╔════════════════════════════════════════════════════════════════════╗
║  Fighting Style Builder                             [New] [Save]   ║
╠════════════════════════════════════════════════════════════════════╣
║  Name: [___________________________]                               ║
║  Option Source Name: [___________________________]                 ║
║  Description: [multiline text area]                                ║
║                                                                    ║
║  ═══ Combat Bonuses ═══                                            ║
║  □ Ranged Attack Bonus: [___]                                     ║
║  □ Melee Attack Bonus:  [___]                                     ║
║  □ Damage Bonus: [___]  Weapon Type: [All ▾]                      ║
║  □ AC Bonus: [___]                                                ║
║    Conditions: ☐ Wearing Armor ☐ No Shield ☐ Not Heavy Armor     ║
║  □ Initiative Bonus: [___]                                        ║
║  □ Critical Hit Range: [19-20 ▾]                                  ║
║                                                                    ║
║  ═══ Defenses & Resistances ═══                                   ║
║  □ Damage Resistances: [Select...▾]                               ║
║  □ Damage Immunities: [Select...▾]                                ║
║  □ Saving Throw Advantages: [Select...▾]                          ║
║                                                                    ║
║  ═══ Movement & Senses ═══                                        ║
║  □ Speed Bonus: [___] ft                                          ║
║  □ Swimming Speed: [___] ft or ☐ Equal to walking                ║
║  □ Climbing Speed: [___] ft or ☐ Equal to walking                ║
║  □ Flying Speed: [___] ft or ☐ Equal to walking                  ║
║  □ Darkvision: [___] ft                                           ║
║  □ Blindsight: [___] ft                                           ║
║  □ Tremorsense: [___] ft                                          ║
║                                                                    ║
║  ═══ Proficiencies ═══                                            ║
║  □ Weapon Proficiencies: [Select...▾]                             ║
║  □ Armor Proficiencies: [Select...▾]                              ║
║  □ Skill Proficiencies: [Select...▾]                              ║
║  □ Tool Proficiencies: [Select...▾]                               ║
║  □ Languages: [Select...▾]                                        ║
║                                                                    ║
║  ═══ Special Abilities ═══                                        ║
║  □ Grants Action: [+ Add Action]                                  ║
║  □ Grants Bonus Action: [+ Add Bonus Action]                      ║
║  □ Grants Reaction: [+ Add Reaction]                              ║
║  □ Grant Spell Casting: [+ Add Spell]                             ║
║                                                                    ║
║  ═══ Other Bonuses ═══                                            ║
║  □ Max HP Bonus per Level: [___]                                  ║
║  □ Passive Perception Bonus: [___]                                ║
║  □ Passive Investigation Bonus: [___]                             ║
║                                                                    ║
║  Custom Modifiers: [+ Add Custom]                                 ║
║                                                                    ║
╚════════════════════════════════════════════════════════════════════╝
```

### Progressive Disclosure

**Don't show all options at once!**

Use **accordion/collapsible sections**:
- Start with just "Combat Bonuses" expanded
- Collapse "Defenses", "Movement", "Proficiencies", "Special Abilities", "Other"
- User expands sections as needed
- Prevents overwhelming new users
- Advanced users can access everything

---

## Part 9: Example Fighting Styles (Comprehensive Range)

### Simple: Archery (Baseline)
```clojure
{:name "Archery"
 :key :archery
 :option-pack "Player's Handbook"
 :description "You gain a +2 bonus to attack rolls you make with ranged weapons."
 :props {:ranged-attack-bonus 2}}
```

### Moderate: Mariner (UA)
```clojure
{:name "Mariner"
 :key :mariner
 :option-pack "UA: Waterborne"
 :description "As long as you are not wearing heavy armor or using a shield, you have a swimming speed and a climbing speed equal to your normal speed, and you gain a +1 bonus to AC."
 :props {:ac-bonus 1                      ;; Conditional in implementation
         :swimming-speed-equals-walking true
         :climbing-speed-equals-walking true}}
```

### Advanced: Tactical Superiority
```clojure
{:name "Tactical Superiority"
 :key :tactical-superiority
 :option-pack "Homebrew: Advanced Combat"
 :description "You've mastered battlefield awareness. You gain a +5 bonus to passive Perception, advantage on initiative rolls, and can take the Help action as a bonus action."
 :props {:passive-perception-5 true
         :initiative-advantage true}
 :bonus-actions [{:name "Tactical Help"
                  :summary "Use the Help action"}]}
```

### Complex: Elemental Warrior
```clojure
{:name "Elemental Warrior"
 :key :elemental-warrior
 :option-pack "Homebrew: Elemental Combat"
 :description "You've trained to fight in harsh elements. Choose two damage types from fire, cold, lightning, or acid. You gain resistance to those damage types and can use a bonus action to imbue your weapon with that energy, dealing an extra 1d4 damage of that type on your next hit."
 :props {:damage-resistance {:fire true :cold true}}  ;; Selected by user
 :selections [{:name "Elemental Resistance Type"
               :options [...]}
              {:name "Energy Imbue"
               :type :bonus-action
               :frequency :short-rest-3
               :effect "Next weapon hit deals +1d4 elemental damage"}]}
```

### Expert: Battle Trance
```clojure
{:name "Battle Trance"
 :key :battle-trance
 :option-pack "Homebrew: Warrior's Focus"
 :description "When you drop below half your hit point maximum, you enter a battle trance until the end of your next turn. While in the trance, you gain +2 AC, +10 ft speed, and advantage on Strength and Dexterity saving throws. Once you use this feature, you can't use it again until you finish a short or long rest."
 :props {:conditional-ac-bonus 2          ;; When bloodied
         :conditional-speed-bonus 10
         :conditional-save-advantage {:str true :dex true}}
 :frequency :short-rest-1
 :trigger :bloodied}  ;; Special trigger type
```

---

## Part 10: Code Reusability Matrix

### Shared with Feat Builder

| Component | Feat Builder | Fighting Style Builder | Reuse Strategy |
|-----------|-------------|------------------------|----------------|
| **Props Schema** | ✅ Extensive | ✅ Subset + additions | Extend feat props schema |
| **`plugin-modifiers`** | ✅ Yes | ✅ Yes | Direct reuse |
| **`make-feat-modifiers`** | ✅ Yes | ✅ Yes | Extend case statement |
| **UI Components** | ✅ Many inputs | ✅ Most same | Extract to shared components |
| **Validation** | ✅ Spec-based | ✅ Spec-based | Reuse patterns |
| **localStorage** | ✅ Yes | ✅ Yes | Same pattern |
| **Plugin Format** | ✅ `::e5/feats` | ✅ `::e5/fighting-styles` | Parallel structure |

### Generic Functions Needed

```clojure
;; In options.cljc
(defn fighting-style-modifiers [key name description props]
  (concat
   (plugin-modifiers props key)  ;; <-- REUSE existing function!
   [(modifiers/trait-cfg
     {:name name
      :description description})]))

(defn fighting-style-option-from-cfg [{:keys [name key description props]}]
  (let [style-mods (fighting-style-modifiers key name description props)]
    (t/option-cfg
     {:name name
      :key key
      :modifiers style-mods})))
```

**Key insight**: `plugin-modifiers` already does 90% of the work!

---

## Part 11: Open Design Questions

### 1. Conditional Modifiers

**Question**: How to handle complex conditionals like Mariner's AC bonus (only if not wearing heavy armor and not using shield)?

**Options**:
A. **Pre-defined conditional props** (easier UI)
   ```clojure
   :ac-bonus-no-heavy-armor 1
   :ac-bonus-no-shield 1
   ```
B. **Condition builder UI** (more flexible)
   ```clojure
   :ac-bonus {:value 1
              :conditions {:no-heavy-armor true
                          :no-shield true}}
   ```
C. **Custom modifier fallback** (advanced users)
   ```clojure
   :custom-modifiers [(mod5e/ac-bonus-fn
                       (fn [armor shield] ...))]
   ```

**Recommendation**: Start with A (pre-defined), add C (custom) for advanced users, potentially add B in future.

### 2. Selection-based Fighting Styles

**Question**: Should fighting styles be able to have sub-selections (like Elemental Warrior choosing damage types)?

**Options**:
A. **No selections** - keep it simple (properties only)
B. **Limited selections** - allow choosing from lists (damage types, skills, etc.)
C. **Full selections** - same power as feats

**Recommendation**: Start with A, add B in Phase 2+.

### 3. Class Restrictions

**Question**: Should homebrew fighting styles be restrictable to specific classes?

**Example**:
```clojure
{:name "Rage Strike"
 :class-restrictions #{:barbarian}
 :description "While raging, you deal an extra 1d6 damage"}
```

**Recommendation**: Yes, add `:class-restrictions` prop. Champion already shows multi-class support.

### 4. Frequency/Uses Per Rest

**Question**: Should fighting styles have limited-use abilities?

**Examples**:
- "Once per short rest, you can..."
- "3 times per long rest, you can..."

**Recommendation**: Yes, reuse the `:frequency` pattern from traits/actions.

---

## Part 12: Updated Implementation Timeline

### Phase 1: Foundation (1 week)
- Data schema with full props support
- Plugin spec additions
- Basic conversion function
- **Deliverable**: Can define fighting styles in data format

### Phase 2: Core Combat Modifiers (1 week)
- Extend `make-feat-modifiers` with fighting-style-specific props
- Conversion function handles Priority 1 modifiers
- Integration with `fighting-style-selection`
- **Deliverable**: Homebrew styles appear in character builder with basic combat bonuses

### Phase 3: Builder UI - Basic (1 week)
- Name, description, option pack fields
- Priority 1 modifier UI (attack, damage, AC, initiative)
- Save/load functionality
- **Deliverable**: Users can create basic fighting styles via UI

### Phase 4: Builder UI - Extended (1 week)
- Priority 2 modifiers UI (resistances, proficiencies)
- Priority 3 modifiers UI (movement, senses, skills)
- Actions/bonus actions/reactions UI
- **Deliverable**: Users can create complex fighting styles

### Phase 5: Testing & Polish (1 week)
- Comprehensive testing
- Edge cases
- Documentation
- Example fighting styles pack
- **Deliverable**: Production-ready feature

**Total**: 5 weeks (vs original 3-4 weeks, but with much broader scope)

---

## Conclusion

Fighting styles should be designed as **"quarter to half feats"** with access to nearly all character sheet modifiers. By leveraging the existing `plugin-modifiers` system and extending it with fighting-style-specific props, we can create a flexible, powerful system that supports everything from simple "+2 to ranged attacks" to complex multi-ability fighting styles like Mariner.

**Key Success Factors**:
1. ✅ Reuse feat builder patterns extensively
2. ✅ Start simple (core combat) and expand incrementally
3. ✅ Support full modifier range from day one (backend)
4. ✅ Progressive disclosure in UI (don't overwhelm users)
5. ✅ Keep generic functions for reusability

**Next Steps**:
1. Review and approve this expanded scope
2. Update original exploration document or merge
3. Create detailed task breakdown
4. Begin Phase 1 implementation

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent
**Status**: Awaiting Review & Approval
