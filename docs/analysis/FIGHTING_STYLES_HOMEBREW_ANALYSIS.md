# Fighting Styles - Homebrew "Advanced Fighting Styles" Analysis

**Source**: Tome of Advanced Fighting Styles (TGS2 p176)
**Item Type**: Wondrous item, Very Rare (Major tier)
**Date**: 2026-01-12
**Status**: Homebrew Pattern Analysis

---

## Executive Summary

The **Tome of Advanced Fighting Styles** (TGS2) introduces 6 homebrew "Advanced Fighting Styles" that demonstrate:

1. ✅ **Fighting styles from magic items** (not just class features)
2. ✅ **Weapon-specific fighting styles** (nets, simple weapons, heavy weapons, versatile weapons)
3. ✅ **NEW combat mechanics** (firing reach = ranged opportunity attacks!)
4. ✅ **Critical hit modifications** (expanded crit range, bonus crit damage)
5. ✅ **Weapon property manipulation** (damage dice override, range doubling)
6. ✅ **Ultra-specific conditional bonuses** (+2 AC vs opportunity attacks only)

**Critical Insight**: These styles target **specific weapon types/properties**, showing that fighting style restrictions should be granular (not just class-based).

---

## Part 1: The Magic Item Pattern

### Tome of Advanced Fighting Styles
**Source**: TGS2 p176
**Rarity**: Very Rare
**Major Tier Item**

```
This book's magically charged pages are filled with detailed descriptions
and illustrations that teach unique styles of combat. If you spend 48 hours
over a period of 6 days or fewer studying the book's contents and practicing
its guidelines, you gain one of the following Advanced Fighting Styles:

[6 fighting styles listed]

You can't take an Advanced Fighting Style option more than once, even if
you later get to choose again. Once the book has been used to grant an
Advanced Fighting Style, it loses its magic, but regains it in a century.
```

**Design Patterns**:
- ✅ Requires **48 hours over 6 days** to learn (attunement-like)
- ✅ **One-time use** (loses magic after granting style)
- ✅ **Long cooldown** (regains magic in 100 years)
- ✅ **Choice from list** (player picks 1 of 6)
- ✅ **Non-repeatable** (can't take same style twice)

**Implications**:
1. Fighting styles can be granted by magic items (not just classes/feats)
2. May need "source" tracking (class vs feat vs magic item)
3. Magic item implementation needs selection UI
4. "Advanced" fighting styles are more powerful (hence Very Rare)

---

## Part 2: Individual Style Analysis

### 1. Advanced Simplicity 🎯 Critical Range Expansion

```
Your attacks made with simple weapons score a critical hit on a roll of 19 or 20.
```

**Mechanics**:
- Critical range: 19-20 (expanded from 20)
- **Weapon restriction**: Simple weapons only
- Similar to Champion's Improved Critical (19-20)

**Implementation Requirements**:
```clojure
{:name "Advanced Simplicity"
 :key :advanced-simplicity
 :option-pack "Tome of Advanced Fighting Styles"
 :description "Your attacks made with simple weapons score a critical hit on a roll of 19 or 20."
 :props {:critical-range-weapon-specific {:range 19
                                          :weapon-category :simple}}}
```

**New Modifier Needed**:
```clojure
;; In make-feat-modifiers
:critical-range-weapon-specific
  [(mod5e/critical-for-weapons
     (:range v)
     (:weapon-category v))]  ;; Conditional crit range
```

**Why interesting**: First fighting style that **buffs simple weapons specifically** (usually the weakest weapons).

---

### 2. Crushing Blows 💥 Critical Damage Bonus

```
When you score a critical hit using a melee weapon that has the heavy property,
the target takes extra bludgeoning damage equal to 5 + the ability score
modifier you used for your attack and damage rolls.
```

**Mechanics**:
- Trigger: Critical hit
- **Weapon restriction**: Heavy property
- **Weapon type**: Melee only
- **Damage formula**: 5 + ability modifier (STR or DEX depending on weapon)
- **Damage type**: Bludgeoning (regardless of weapon type!)

**Implementation Requirements**:
```clojure
{:name "Crushing Blows"
 :key :crushing-blows
 :option-pack "Tome of Advanced Fighting Styles"
 :description "When you score a critical hit using a melee weapon that has the heavy property, the target takes extra bludgeoning damage equal to 5 + the ability score modifier you used for your attack and damage rolls."
 :props {:critical-bonus-damage {:base 5
                                 :add-ability-modifier true
                                 :weapon-property :heavy
                                 :weapon-type :melee
                                 :damage-type :bludgeoning}}}
```

**New Modifier Needed**:
```clojure
:critical-bonus-damage
  [(mods/vec-mod ?damage-bonus-fns
     (fn [weapon character]
       (if (and (critical-hit? context)
                (weapon-has-property? weapon :heavy)
                (weapon-is-melee? weapon))
         {:amount (+ 5 (get-weapon-ability-modifier character weapon))
          :type :bludgeoning}
         0)))]
```

**Why interesting**:
- First style with **critical-triggered damage**
- Uses **dynamic ability modifier** (STR or DEX from attack)
- Forces damage type (bludgeoning) **regardless of weapon**

---

### 3. Net Mastery 🕸️ Weapon Modification

```
When you throw a net, its normal and long range is doubled. In addition, the
first time that a creature attempts to escape from a net that you threw, the
escape DC is equal to 8 + your proficiency bonus + your Strength or Dexterity
modifier (your choice), unless it's already higher.
```

**Mechanics**:
- **Weapon-specific**: Nets only (ultra-specific!)
- **Range doubling**: Normal range 5→10, Long range 15→30
- **Custom escape DC**: 8 + prof + STR/DEX (choice)
- **Minimum clause**: Only applies if higher than net's default

**Implementation Requirements**:
```clojure
{:name "Net Mastery"
 :key :net-mastery
 :option-pack "Tome of Advanced Fighting Styles"
 :description "When you throw a net, its normal and long range is doubled. In addition, the first time that a creature attempts to escape from a net that you threw, the escape DC is equal to 8 + your proficiency bonus + your Strength or Dexterity modifier (your choice), unless it's already higher."
 :weapon-modifications [{:weapon :net
                         :range-multiplier 2
                         :escape-dc {:base 8
                                     :prof-bonus true
                                     :ability-choice #{::char5e/str ::char5e/dex}
                                     :minimum-only true}}]}
```

**New Modifier Needed**:
```clojure
:weapon-modifications
  [(weapon-property-modifier
     (:weapon v)
     {:range-multiplier (:range-multiplier v)
      :escape-dc (:escape-dc v)})]
```

**Why interesting**:
- First **single-weapon fighting style** (nets only!)
- Modifies **weapon properties** (range)
- Introduces **escape DC** (usually a monster stat)
- Player choice of ability (STR or DEX)

---

### 4. Quick Shot 🏹 NEW MECHANIC - Ranged Opportunity Attacks

```
You gain a special reach with ranged weapons called a firing reach that's
a number of feet equal to 5 + your normal reach. While you are wielding a
ranged weapon, other creatures provoke an opportunity attack from you when
they move out of your firing reach.
```

**Mechanics**:
- **NEW CONCEPT**: "Firing reach" (doesn't exist in 5e!)
- **Formula**: 5 + normal reach (usually 5 + 5 = 10 ft)
- **Enables**: Opportunity attacks with ranged weapons
- **Trigger**: Creatures leaving firing reach

**This is REVOLUTIONARY**:
- Normally, ranged weapons **cannot make opportunity attacks** (PHB rule)
- This creates an **exception to core rules**
- Introduces a **new range type** (firing reach vs normal reach vs weapon range)

**Implementation Requirements**:
```clojure
{:name "Quick Shot"
 :key :quick-shot
 :option-pack "Tome of Advanced Fighting Styles"
 :description "You gain a special reach with ranged weapons called a firing reach that's a number of feet equal to 5 + your normal reach. While you are wielding a ranged weapon, other creatures provoke an opportunity attack from you when they move out of your firing reach."
 :props {:firing-reach {:formula "5 + normal-reach"
                        :enables-ranged-opportunity-attacks true}}}
```

**New Modifier Needed**:
```clojure
:firing-reach
  [(mods/modifier ?firing-reach
     (+ 5 ?normal-reach)
     "Firing Reach"
     (str (+ 5 ?normal-reach) " feet"))
   (mods/trait-cfg
     {:name "Ranged Opportunity Attacks"
      :description "You can make opportunity attacks with ranged weapons when creatures leave your firing reach"})]
```

**Why interesting**:
- **Completely new mechanic** not in any official material
- Requires UI to show "firing reach" alongside normal reach
- Changes fundamental combat rules (ranged weapons + opportunity attacks)
- Most innovative homebrew style in the list

---

### 5. Readied Bulwark 🛡️ Situational AC Bonus

```
You gain a +2 bonus to AC against opportunity attacks while holding a shield.
```

**Mechanics**:
- **Ultra-specific**: Only vs opportunity attacks (not all attacks!)
- **Equipment requirement**: Shield
- **Bonus**: +2 AC

**Implementation Requirements**:
```clojure
{:name "Readied Bulwark"
 :key :readied-bulwark
 :option-pack "Tome of Advanced Fighting Styles"
 :description "You gain a +2 bonus to AC against opportunity attacks while holding a shield."
 :props {:conditional-ac-bonus {:amount 2
                                :condition :vs-opportunity-attacks
                                :requires :shield}}}
```

**New Modifier Needed**:
```clojure
:conditional-ac-bonus
  [(mods/modifier ?conditional-ac
     (fn [attack-type armor equipment]
       (if (and (= attack-type :opportunity-attack)
                (has-shield? equipment))
         (:amount v)
         0)))]
```

**Why interesting**:
- Most **specific conditional bonus** seen yet
- Not vs all attacks, not vs melee/ranged, but vs **opportunity attacks only**
- Shows need for **attack-type tracking** in combat system

---

### 6. Versatile Expert ⚔️ Weapon Property Override

```
When you hit with an attack using a weapon that has the versatile property,
you can use the two-handed damage die even if you're only wielding the weapon
with one hand.
```

**Mechanics**:
- **Weapon property**: Versatile only
- **Effect**: Use 2H damage die while wielding 1H
- **Example**: Longsword does 1d10 (instead of 1d8) even when wielded one-handed

**This is POWERFUL**:
- Longsword 1d8→1d10 (+1 average damage)
- Battleaxe 1d8→1d10 (+1 average damage)
- Warhammer 1d8→1d10 (+1 average damage)
- **Doesn't occupy second hand** (can still hold shield, focus, etc.)

**Implementation Requirements**:
```clojure
{:name "Versatile Expert"
 :key :versatile-expert
 :option-pack "Tome of Advanced Fighting Styles"
 :description "When you hit with an attack using a weapon that has the versatile property, you can use the two-handed damage die even if you're only wielding the weapon with one hand."
 :props {:versatile-damage-override {:weapon-property :versatile
                                     :use-two-handed-die true
                                     :while-one-handed true}}}
```

**New Modifier Needed**:
```clojure
:versatile-damage-override
  [(mods/modifier ?weapon-damage-die
     (fn [weapon wielding-style]
       (if (and (weapon-has-property? weapon :versatile)
                (= wielding-style :one-handed))
         (get-versatile-die weapon)  ;; Use 2H die
         (get-normal-die weapon))))]
```

**Why interesting**:
- **Alters weapon properties** fundamentally
- Enables "longsword + shield" with 1d10 damage (normally requires 2H)
- Very powerful for DEX fighters (can use shield + finesse versatile weapons)
- Shows weapon die **override** system needed

---

## Part 3: Pattern Analysis

### Weapon Targeting Patterns

| Style | Weapon Target | Specificity Level |
|-------|---------------|-------------------|
| Advanced Simplicity | Simple weapons | Category (broad) |
| Crushing Blows | Heavy + Melee | Property + Type (moderate) |
| Net Mastery | Nets only | Single weapon (ultra-specific) |
| Quick Shot | Ranged weapons | Type (broad) |
| Readied Bulwark | N/A (shield) | Equipment (moderate) |
| Versatile Expert | Versatile property | Property (moderate) |

**Key Insight**: Weapon targeting is **highly granular**:
- By **category** (simple, martial)
- By **type** (melee, ranged)
- By **property** (heavy, versatile, thrown)
- By **specific weapon** (nets)

### Modifier Complexity Tiers

**Tier 1: Simple Bonuses**
- Readied Bulwark: +2 AC (conditional)

**Tier 2: Conditional Calculations**
- Advanced Simplicity: Crit range 19-20 (weapon-conditional)
- Crushing Blows: 5 + ability mod damage (crit-conditional)

**Tier 3: Property Modifications**
- Net Mastery: Double range, custom DC
- Versatile Expert: Damage die override

**Tier 4: New Mechanics**
- Quick Shot: Firing reach + ranged opportunity attacks

### Power Level Comparison

Compared to official styles:

| Style | Power Level | Official Equivalent |
|-------|-------------|---------------------|
| Advanced Simplicity | Medium | Champion Improved Critical |
| Crushing Blows | Medium-High | Piercer feat (but better) |
| Net Mastery | Low-Medium | Niche (nets only) |
| Quick Shot | High | No equivalent (unique) |
| Readied Bulwark | Low | Minor bonus |
| Versatile Expert | **Very High** | No equivalent (borderline OP) |

**Versatile Expert** is arguably the most powerful:
- Free +1 damage die increase
- No trade-off (still have free hand)
- Stacks with Dueling (+2 damage) if wielding nothing in off-hand
- Enables shield + 1d10 weapon (normally incompatible)

---

## Part 4: Implementation Requirements Matrix

### New Props Needed

| Prop Key | Purpose | Example Value |
|----------|---------|---------------|
| `:critical-range-weapon-specific` | Crit range for specific weapons | `{:range 19 :weapon-category :simple}` |
| `:critical-bonus-damage` | Extra damage on crits | `{:base 5 :add-ability-mod true :weapon-property :heavy}` |
| `:weapon-modifications` | Modify weapon properties | `{:weapon :net :range-multiplier 2}` |
| `:firing-reach` | Ranged opportunity attack range | `{:formula "5 + reach" :enables-ranged-oa true}` |
| `:conditional-ac-bonus` | Situational AC | `{:amount 2 :condition :vs-opportunity-attacks}` |
| `:versatile-damage-override` | Use 2H die 1H | `{:weapon-property :versatile :use-two-handed-die true}` |

### New Modifiers Needed

```clojure
;; In modifiers.cljc

(defn critical-for-weapons [range weapon-filter]
  "Critical hit range for specific weapons"
  (mods/modifier
   ?critical-range-conditional
   {:range range :filter weapon-filter}))

(defn critical-bonus-damage [config]
  "Extra damage on critical hits"
  (mods/vec-mod ?damage-bonus-fns
    (fn [weapon character attack-context]
      (if (critical-hit? attack-context)
        (calculate-bonus config weapon character)
        0))))

(defn weapon-property-modifier [weapon-key modifications]
  "Modify weapon properties (range, DC, etc.)"
  (mods/modifier
   ?weapon-property-overrides
   {weapon-key modifications}))

(defn firing-reach [formula]
  "Ranged opportunity attack range"
  (mods/modifier
   ?firing-reach
   (eval-reach-formula formula)))

(defn versatile-die-override []
  "Use 2H versatile die while 1H"
  (mods/modifier
   ?weapon-damage-die-override
   (fn [weapon wielding-style]
     (if (and (versatile? weapon)
              (= wielding-style :one-handed))
       (get-two-handed-die weapon)
       nil))))  ;; nil = no override
```

### UI Requirements

**Weapon Targeting UI**:
```
═══ Weapon Restrictions ═══
Applies to:
  ○ All weapons
  ○ Weapon category: [Simple ▾] [Martial ▾]
  ○ Weapon type: [Melee ▾] [Ranged ▾]
  ○ Weapon property: [Heavy ▾] [Versatile ▾] [Thrown ▾] [Finesse ▾]
  ○ Specific weapon: [Net ▾] [Longsword ▾] [...]
  ☑ Combine filters (e.g., Heavy + Melee)
```

**Critical Hit Modifications**:
```
═══ Critical Hits ═══
□ Expand critical range to: [19 ▾] [18 ▾] [17 ▾]
□ Bonus damage on critical hits: [___] + ☑ Ability Modifier
  Damage type: [Bludgeoning ▾]
```

**Weapon Property Overrides**:
```
═══ Weapon Modifications ═══
□ Double weapon range
□ Custom escape DC: 8 + Prof + [STR/DEX ▾]
□ Use two-handed die while one-handed (versatile weapons)
□ Enable ranged opportunity attacks
  Firing reach: [5 + reach ▾] ft
```

---

## Part 5: Magic Item Integration

### Source Tracking

Fighting styles can come from:
1. **Class features** (Fighter 1, Paladin 2, Ranger 2)
2. **Feats** (Fighting Initiate)
3. **Magic items** (Tome of Advanced Fighting Styles)
4. **Subclass features** (Champion 10)

**Need to track**:
```clojure
{:fighting-style :advanced-simplicity
 :source {:type :magic-item
          :name "Tome of Advanced Fighting Styles"
          :learned-date "2024-03-15"}}
```

**Why important**:
- Character sheet display ("learned from Tome of...")
- Some styles may be lost if item is lost
- Some items may have restrictions (e.g., can't unlearn style)

### Magic Item Selection UI

When using Tome of Advanced Fighting Styles:

```
╔════════════════════════════════════════════════════════════╗
║  Tome of Advanced Fighting Styles                          ║
║  (Very Rare Magic Item)                                    ║
╠════════════════════════════════════════════════════════════╣
║  After studying this tome for 48 hours over 6 days,        ║
║  choose one Advanced Fighting Style to learn permanently:  ║
║                                                            ║
║  ○ Advanced Simplicity                                     ║
║    Critical hits on 19-20 with simple weapons              ║
║                                                            ║
║  ○ Crushing Blows                                          ║
║    +5 + ability mod bludgeoning damage on crits (heavy)    ║
║                                                            ║
║  ○ Net Mastery                                             ║
║    Double net range, custom escape DC                      ║
║                                                            ║
║  ○ Quick Shot                                              ║
║    Ranged opportunity attacks within firing reach          ║
║                                                            ║
║  ○ Readied Bulwark                                         ║
║    +2 AC vs opportunity attacks with shield                ║
║                                                            ║
║  ○ Versatile Expert                                        ║
║    Use 2H damage die while wielding versatile weapon 1H    ║
║                                                            ║
║  [Select]  [Cancel]                                        ║
╚════════════════════════════════════════════════════════════╝
```

---

## Part 6: Comparative Analysis

### Advanced vs Standard Fighting Styles

| Aspect | Standard Styles | Advanced Styles |
|--------|----------------|-----------------|
| **Source** | Class features | Magic items |
| **Rarity** | Common | Very Rare |
| **Acquisition** | Level 1-2 | Found/bought |
| **Complexity** | Low-Medium | Medium-High |
| **Power Level** | Balanced | Higher |
| **Weapon Focus** | Broad | Often specific |

### Power Creep Examples

**Standard: Archery**
- Effect: +2 to attack rolls with ranged weapons
- Simple, straightforward, balanced

**Advanced: Quick Shot**
- Effect: Ranged opportunity attacks + firing reach
- Creates new mechanic, fundamentally changes combat
- Much more impactful

**Standard: Defense**
- Effect: +1 AC with armor
- Simple bonus

**Advanced: Versatile Expert**
- Effect: +1 damage die size with no trade-off
- Enables previously impossible combinations (shield + 1d10)
- Significantly more powerful

---

## Part 7: Balance Considerations

### Versatile Expert Balance Issues

**Why it's potentially overpowered**:

1. **No trade-off**: Get 2H damage without using 2 hands
2. **Stacks with Dueling**: Longsword 1d10 + 2 (Dueling) = avg 7.5 damage per hit
3. **Enables shield**: 1d10 damage + shield AC bonus
4. **DEX abuse**: Finesse versatile weapons (rare, but exist in homebrew)

**Potential fixes**:
- Require weapon to be wielded with both hands at some point during turn
- Don't stack with Dueling
- Limit to STR weapons only
- Reduce to +1 die size (1d8→1d10) rather than full 2H (some versatile weapons go 1d8→1d10, others 1d6→1d8)

### Net Mastery Niche Problem

**Why it's underpowered**:
- Only affects nets (ultra-niche weapon)
- Most characters never use nets
- Taking this style = wasted if you change weapons

**When it's good**:
- Dedicated net-user builds (rare)
- Gladiator/arena fighter concepts
- Support characters who net then allies attack

### Quick Shot Power Spike

**Why it's strong**:
- Enables ranged opportunity attacks (normally impossible)
- Controls space at range (normally only melee can do this)
- Synergizes with features that grant extra reactions
- Punishes enemy movement heavily

**Counterplay**:
- Enemies can ready actions instead of moving
- Only works within 10 ft (close range for ranged weapons)
- Doesn't work if you're in melee combat yourself

---

## Part 8: Implementation Priority

### Phase A: Weapon Targeting System (High Priority)
**Required for**: All 6 Advanced styles

**Tasks**:
1. Weapon filter system (category, type, property, specific)
2. Weapon property checking (has-property? weapon :heavy)
3. Conditional modifier application based on weapon

**Estimated effort**: 1 week

### Phase B: Critical Hit System (High Priority)
**Required for**: Advanced Simplicity, Crushing Blows

**Tasks**:
1. Conditional critical range (by weapon type)
2. Critical bonus damage calculation
3. Ability modifier injection into damage

**Estimated effort**: 3 days

### Phase C: Weapon Property Modification (Medium Priority)
**Required for**: Net Mastery, Versatile Expert

**Tasks**:
1. Range override system
2. Escape DC customization
3. Damage die override system
4. Wielding style detection (1H vs 2H)

**Estimated effort**: 1 week

### Phase D: New Combat Mechanics (Low Priority)
**Required for**: Quick Shot

**Tasks**:
1. Firing reach concept
2. Ranged opportunity attack enabling
3. Reach-based opportunity attack triggers
4. Combat UI updates (show firing reach)

**Estimated effort**: 2 weeks

**Total for Advanced Styles**: 4-5 weeks

---

## Part 9: Integration with Existing System

### How Advanced Styles Fit

**Option 1: Separate Category**
- Keep "Fighting Styles" and "Advanced Fighting Styles" separate
- Different selection pools
- Different UI sections

**Option 2: Single Category with Tags**
- All fighting styles in one system
- Tag with `:standard` or `:advanced`
- Filter in UI by tag

**Option 3: Power Level Tiers**
- Tier 1: Standard styles (PHB)
- Tier 2: Enhanced styles (TCE)
- Tier 3: Advanced styles (TGS2)
- Allow users to choose tier when homebrewing

**Recommendation**: Option 2 (single category with tags)
- Most flexible for homebrew
- Easiest to implement
- Allows mixed selection if DM permits

### Schema Extension

```clojure
(spec/def ::fighting-style-tier #{:standard :advanced :legendary})

(spec/def ::homebrew-fighting-style
  (spec/keys :req-un [::name ::key ::option-pack]
             :opt-un [::description
                      ::props
                      ::class-restrictions
                      ::tier  ;; NEW
                      ::weapon-restrictions  ;; NEW
                      ::critical-modifications  ;; NEW
                      ::weapon-property-overrides  ;; NEW
                      ]))
```

---

## Part 10: Homebrew Builder UI Additions

### Weapon Restriction Section

```
═══ Weapon Restrictions ═══
This fighting style only applies to:
  ○ All weapons (no restriction)
  ○ Specific weapon categories
    ☑ Simple weapons    ☐ Martial weapons
  ○ Specific weapon types
    ☑ Melee weapons     ☐ Ranged weapons
  ○ Specific weapon properties
    ☑ Heavy    ☐ Light    ☐ Finesse    ☐ Versatile    ☐ Two-handed
    ☐ Thrown   ☐ Reach   ☐ Loading    ☐ Ammunition
  ○ Single weapon only: [Choose weapon ▾]

Advanced options:
  ☑ Require ALL selected properties (AND)
  ☐ Require ANY selected property (OR)
```

### Critical Hit Modification Section

```
═══ Critical Hit Modifications ═══
□ Expand critical hit range
  Score critical hits on: [19 ▾] or higher
  (Normal = 20, Champion = 19, Brutal = 18)

□ Bonus damage on critical hits
  Extra damage: [5] + ☑ Ability Modifier
  Damage type: [Bludgeoning ▾]
  Only with weapons matching restrictions above: ☑
```

### Weapon Property Override Section

```
═══ Weapon Property Overrides ═══
□ Modify weapon range
  Multiplier: [2x ▾]  (Normal → Long both doubled)
  OR Custom: Normal [___] ft, Long [___] ft

□ Set custom save/escape DC
  DC = 8 + ☑ Proficiency + [STR/DEX choice ▾]
  Only applies if: ☑ Higher than default

□ Damage die override
  ○ Increase die by one size (d6→d8, d8→d10, d10→d12)
  ○ Use two-handed damage while wielding one-handed (versatile only)
  ○ Custom die: [d10 ▾]
```

### New Mechanics Section

```
═══ Advanced Mechanics ═══ ⚠️ Creates new rules!
□ Enable ranged opportunity attacks
  Firing reach: [5 + normal reach ▾]
  (Creates "firing reach" zone for ranged weapons)

□ Situational AC bonus
  Bonus: [+2]
  Only against: [Opportunity attacks ▾]
  Requires: ☑ Shield  ☐ Specific armor type

□ Special combat actions
  [+ Add custom action/reaction/bonus action]
```

---

## Conclusion

The **Tome of Advanced Fighting Styles** showcases homebrew fighting styles that:

1. ✅ **Target specific weapons** (from broad categories to single weapons)
2. ✅ **Modify weapon properties** (range, damage dice, escape DC)
3. ✅ **Introduce new mechanics** (firing reach, ranged opportunity attacks)
4. ✅ **Add critical hit modifications** (range expansion, bonus damage)
5. ✅ **Create ultra-specific conditionals** (only vs opportunity attacks)
6. ✅ **Come from magic items** (not just class features)

### Critical Implementation Needs

**Must-have systems**:
- Weapon targeting/filtering (category, type, property, specific)
- Critical hit modification (range, bonus damage)
- Weapon property overrides (range, dice, DC)
- New mechanic support (firing reach)

**Estimated additional effort**: 4-5 weeks for Advanced style support

### Integration Strategy

**Recommended approach**:
1. Implement as **single system** with tier tags
2. **Weapon restrictions** as first-class feature
3. **Property overrides** as extensible system
4. **New mechanics** as opt-in advanced features

### Power Level Warning

Some Advanced styles (especially **Versatile Expert**) are **significantly more powerful** than standard styles. DMs should:
- Review balance before allowing
- Consider as magic item rewards (Very Rare)
- Potentially nerf overpowered styles
- Use tier system to gate access

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent
**Status**: Homebrew Analysis Complete - Awaiting Review
