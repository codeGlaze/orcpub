# Builder Design Philosophy - Source Material Mirroring

**CRITICAL INSIGHT**: Builders as Data Entry Tools
**Date**: 2026-01-12
**Status**: Design Philosophy Documentation

---

## The Core Principle: Match the Source Material

### Why Separate Builders Matter (The Real Reason)

Builders need to be separate not just for mental models, but because they're **data transcription tools**. Users should be able to:

1. Open the source book (PHB, TCE, TGS2, etc.)
2. Find a feature (feat, fighting style, spell, etc.)
3. **Transcribe it field-by-field** into the builder
4. Get accurate, working content

**The builder layout should match the source material layout.**

---

## Part 1: Source Material as UI Design Document

### Example: Feat from PHB

**Alert** (Player's Handbook, p165)
```
Alert

Always on the lookout for danger, you gain the following benefits:

• You can't be surprised while you are conscious.
• You gain a +5 bonus to initiative.
• Other creatures don't gain advantage on attack rolls against you as a
  result of being unseen by you.
```

**Feat Builder Should Match**:
```
╔════════════════════════════════════════════════╗
║  Feat Builder                                  ║
╠════════════════════════════════════════════════╣
║  Name: [Alert________________________________] ║
║                                                ║
║  Description:                                  ║
║  ┌────────────────────────────────────────────┐║
║  │ Always on the lookout for danger, you gain │║
║  │ the following benefits:                    │║
║  └────────────────────────────────────────────┘║
║                                                ║
║  Benefits:                                     ║
║  ☑ Can't be surprised                         ║
║  ☑ Initiative bonus: [5]                      ║
║  ☑ No advantage from unseen attackers         ║
╚════════════════════════════════════════════════╝
```

**User workflow**:
1. See "Alert" in book → type "Alert" in Name field ✅
2. See description → copy into Description field ✅
3. See bullet points → check corresponding boxes ✅
4. See "+5 initiative" → enter "5" in Initiative field ✅

**Perfect transcription with minimal cognitive load!**

---

### Example: Fighting Style from TCE

**Blessed Warrior** (Tasha's Cauldron of Everything, p52)
```
Blessed Warrior

You learn two cantrips of your choice from the cleric spell list. They count
as paladin spells for you, and Charisma is your spellcasting ability for them.
Whenever you gain a level in this class, you can replace one of these cantrips
with another cantrip from the cleric spell list.
```

**Fighting Style Builder Should Match**:
```
╔════════════════════════════════════════════════╗
║  Fighting Style Builder                        ║
╠════════════════════════════════════════════════╣
║  Name: [Blessed Warrior___________________]   ║
║                                                ║
║  Class Restrictions: ☑ Paladin only           ║
║                                                ║
║  Description:                                  ║
║  ┌────────────────────────────────────────────┐║
║  │ You learn two cantrips of your choice from │║
║  │ the cleric spell list...                   │║
║  └────────────────────────────────────────────┘║
║                                                ║
║  Spell Selection:                              ║
║  Spell List: [Cleric ▾]                       ║
║  Number of Cantrips: [2]                      ║
║  Spellcasting Ability: [Charisma ▾]           ║
║  ☑ Replaceable on level-up                    ║
╚════════════════════════════════════════════════╝
```

**User workflow**:
1. See "Blessed Warrior" → type in Name ✅
2. See "paladin" → check Paladin restriction ✅
3. See "two cantrips from cleric spell list" → set Spell List: Cleric, Num: 2 ✅
4. See "Charisma is your spellcasting ability" → set Ability: Charisma ✅
5. See "you can replace" → check Replaceable ✅

**Direct transcription from book to builder!**

---

### Example: Advanced Fighting Style from TGS2

**Net Mastery** (Tome of Advanced Fighting Styles, TGS2 p176)
```
Net Mastery

When you throw a net, its normal and long range is doubled. In addition, the
first time that a creature attempts to escape from a net that you threw, the
escape DC is equal to 8 + your proficiency bonus + your Strength or Dexterity
modifier (your choice), unless it's already higher.
```

**Fighting Style Builder Should Match**:
```
╔════════════════════════════════════════════════╗
║  Fighting Style Builder                        ║
╠════════════════════════════════════════════════╣
║  Name: [Net Mastery_______________________]   ║
║                                                ║
║  Description:                                  ║
║  ┌────────────────────────────────────────────┐║
║  │ When you throw a net, its normal and long  │║
║  │ range is doubled...                        │║
║  └────────────────────────────────────────────┘║
║                                                ║
║  Weapon Modifications:                         ║
║  Applies to: [Net (specific weapon) ▾]        ║
║  ☑ Double weapon range                        ║
║                                                ║
║  Escape DC:                                    ║
║  Formula: 8 + Prof + [STR or DEX (choice) ▾]  ║
║  ☑ Only if higher than default                ║
╚════════════════════════════════════════════════╝
```

**User workflow**:
1. See "Net Mastery" → type in Name ✅
2. See "when you throw a net" → set Applies to: Net ✅
3. See "range is doubled" → check Double weapon range ✅
4. See "8 + prof + STR or DEX" → configure DC formula ✅
5. See "unless it's already higher" → check minimum clause ✅

**Every piece of text maps to a UI element!**

---

## Part 2: Why This Matters for Architecture

### The Transcription Use Case

**Primary user workflow**: "I found this cool homebrew online, let me add it to OrcPub"

**Steps**:
1. Open source material (PDF, website, book)
2. Open appropriate builder in OrcPub
3. **Transcribe field by field** without needing to "translate"
4. Save and use

**If the builder matches the source layout**, this is:
- ⚡ **Fast**: Straight transcription, no thinking
- ✅ **Accurate**: No interpretation errors
- 😊 **Low cognitive load**: Copy, don't translate

**If the builder doesn't match the source**, user has to:
- 🤔 "Where does this go?"
- 🔄 "How do I represent this?"
- ❓ "What's the OrcPub way to say this?"
- **Higher error rate, slower entry, frustration**

### Why Feats and Fighting Styles MUST Be Separate Builders

**Feats in source books look like**:
```
FEAT NAME
Prerequisites: [Ability scores, features, etc.]

Description of what the feat does.

• Benefit 1
• Benefit 2
• Sometimes ability score increases
```

**Fighting Styles in source books look like**:
```
STYLE NAME

Description of combat technique and when/how it applies.
[No prerequisites section, simpler structure]
```

**Different information architecture** in the source material!

A feat builder needs:
- Prerequisites section (ability scores, armor proficiency, spellcasting)
- Ability score increase options (half feats)
- Multiple benefit bullets

A fighting style builder needs:
- Class restriction (which classes can use it)
- Weapon applicability (which weapons it affects)
- Tier/power level (Standard vs Advanced)

**Trying to use one builder for both** would require:
- User to map feat structure → fighting style structure (cognitive load!)
- Lots of "N/A" or hidden sections (confusing!)
- Loss of source material mirroring (slower transcription!)

---

## Part 3: Shared Components Under the Hood

### The Insight: Same Mechanics, Different Presentation

**What the user sees** (different):
```
Feat Builder:                    Fighting Style Builder:
┌─────────────────┐             ┌─────────────────┐
│ Prerequisites:  │             │ Class Restrict: │
│ ☐ STR 13        │             │ ☑ Fighter       │
│ ☐ Heavy Armor   │             │ ☐ Paladin       │
│                 │             │                 │
│ Benefits:       │             │ Weapon Type:    │
│ ☑ +5 init       │             │ ○ All           │
│                 │             │ ● Ranged only   │
└─────────────────┘             └─────────────────┘
```

**What the developer uses** (same):
```clojure
;; Both use the SAME underlying component!
[shared/initiative-bonus-input]

;; Just wrapped differently:
(defn feat-builder []
  [:div
   [feat-prerequisites-section]     ;; Unique wrapper
   [shared/initiative-bonus-input]  ;; Shared component!
   ])

(defn fighting-style-builder []
  [:div
   [fighting-style-class-section]   ;; Unique wrapper
   [shared/initiative-bonus-input]  ;; Same shared component!
   ])
```

**Result**:
- User sees layout that matches source material ✅
- Developer maintains one initiative input widget ✅
- Best of both worlds!

---

## Part 4: Extensibility via Props (Clojure Advantage)

### The Clojure Way: Additive Modification

**In Clojure, extending data is natural**:

```clojure
;; Original feat props
{:name "Alert"
 :props {:initiative 5
         :no-surprise true}}

;; Extended with new props (additive!)
{:name "Alert"
 :props {:initiative 5
         :no-surprise true
         :passive-perception 5}}  ;; NEW prop, old props still work!
```

**No breaking changes** - old data still valid!

### How This Helps Fighting Styles

**Current feat props** (already exist):
```clojure
:initiative 5
:skill-prof {:perception true}
:damage-resistance {:fire true}
:armor-prof {:medium true}
```

**New fighting style props** (additive):
```clojure
:ranged-attack-bonus 2          ;; NEW
:melee-attack-bonus 1           ;; NEW
:critical-range 19              ;; NEW
:firing-reach {:formula "5 + reach"}  ;; NEW
```

**Both coexist in the same `plugin-modifiers` function**:

```clojure
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; Existing props (already working)
    :initiative [(modifiers/initiative v)]
    :skill-prof (map-modifiers v modifiers/skill-proficiency)

    ;; NEW props (additive, don't break old)
    :ranged-attack-bonus [(modifiers/ranged-attack-bonus v)]
    :melee-attack-bonus [(modifiers/melee-attack-bonus v)]
    :critical-range [(modifiers/critical v)]
    :firing-reach [(modifiers/firing-reach v)]

    ;; Default
    nil))
```

**Old feats** use `:initiative`, `:skill-prof` → still work! ✅
**New fighting styles** use `:ranged-attack-bonus`, `:firing-reach` → work! ✅

### The Shims Problem (and Solution)

**User observation**: "There are odd shims in the code that could have been better if things had been planned to be extensible via plugin from the start"

**Why shims exist** (hypothesis):
- Original system: Hardcoded options in source files
- Plugin system: Added later as an afterthought
- Result: Awkward compatibility layers ("shims")

**Example of a shim** (common pattern):
```clojure
;; Hardcoded options (original)
(def fighting-style-options
  [(t/option-cfg {:name "Archery" ...})
   (t/option-cfg {:name "Defense" ...})])

;; Plugin options (added later)
(defn all-fighting-styles [plugins]
  (concat fighting-style-options        ;; Old hardcoded
          (plugin-fighting-styles)))    ;; New plugin system
```

**The shim**: Merging hardcoded + plugin instead of one unified system

**Better approach** (what we're proposing):
```clojure
;; Everything goes through the same conversion
(defn all-fighting-styles [plugins]
  (let [source-styles (map fighting-style-option-from-cfg SOURCE_STYLES)
        plugin-styles (map fighting-style-option-from-cfg PLUGIN_STYLES)]
    (concat source-styles plugin-styles)))  ;; Same conversion function!
```

**No shim** - unified conversion, different data sources.

### Additive Props: The Right Pattern

**Instead of**:
```clojure
;; Bad: Fighting-style-specific conversion function
(defn fighting-style-modifiers [cfg]
  ;; Duplicate logic from feat-modifiers
  ...)
```

**Do this**:
```clojure
;; Good: Extend existing system
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; ... existing props ...

    ;; Add fighting style props to SAME function (additive!)
    :ranged-attack-bonus [(modifiers/ranged-attack-bonus v)]
    :critical-range [(modifiers/critical v)]

    nil))

;; Both feats and fighting styles use the SAME function
(plugin-modifiers props key)  ;; Works for both!
```

**Benefits**:
- No code duplication
- One place to maintain
- Naturally extensible (just add more cases!)
- Backward compatible (old cases still work)

---

## Part 5: Builder Design Patterns

### Pattern: Source Material Section Mapping

**For each section in the source book**, create a corresponding UI section:

**PHB Feat Structure**:
```
Name: [...]
Prerequisites: [...]
Description: [...]
Benefits:
  • [...]
  • [...]
```

**Builder Structure**:
```clojure
(defn feat-builder []
  [:div.feat-builder
   [name-section]          ;; Maps to "Name"
   [prerequisites-section] ;; Maps to "Prerequisites"
   [description-section]   ;; Maps to "Description"
   [benefits-section]])    ;; Maps to "Benefits" bullets
```

**TCE Fighting Style Structure**:
```
Name: [...]
Description: [...]
[Mechanical effect description]
```

**Builder Structure**:
```clojure
(defn fighting-style-builder []
  [:div.fighting-style-builder
   [name-section]        ;; Maps to "Name"
   [class-section]       ;; Inferred from which classes get it
   [description-section] ;; Maps to "Description"
   [mechanics-section]]) ;; Maps to mechanical effects
```

### Pattern: Shared Widgets, Unique Layout

**Shared widget** (developer sees):
```clojure
(defn initiative-bonus-input [ability-type]
  "Reusable input for initiative bonuses"
  [:div.initiative-input
   [:label "Initiative Bonus"]
   [:input {:type "number"
            :on-change #(dispatch [::set-prop ability-type :initiative %])}]])
```

**Used in different contexts** (user sees):

**Feat builder**:
```clojure
[:div.feat-benefits
 [:h3 "Benefits"]
 [initiative-bonus-input :feat]  ;; In "Benefits" section
 [skill-bonus-input :feat]]
```

**Fighting style builder**:
```clojure
[:div.combat-bonuses
 [:h3 "Combat Bonuses"]
 [initiative-bonus-input :fighting-style]  ;; In "Combat" section
 [attack-bonus-input :fighting-style]]
```

**Same widget, different section names!** User sees what makes sense for that ability type.

---

## Part 6: Implementation Guidelines

### Guideline 1: Start with Source Material

**Before building any builder**:
1. Collect 10+ examples of that type from source books
2. Identify common sections/patterns
3. Design UI to match those patterns
4. Map each section to shared components where possible

**Example for Fighting Styles**:
1. Collect: Archery, Defense, Dueling (PHB), Blessed Warrior (TCE), Net Mastery (TGS2)
2. Identify patterns:
   - All have name + description
   - Most have simple mechanical effect (bonus to X)
   - Some have spell selection (Blessed Warrior)
   - Some have weapon restrictions (Net Mastery)
3. Design UI with these sections
4. Use shared widgets for common mechanics

### Guideline 2: Unique Sections First, Shared Components Second

**Build in this order**:

1. **Unique layout/sections** (source material mirroring)
   ```clojure
   (defn fighting-style-builder []
     [:div
      [fighting-style-header]      ;; Unique
      [class-restriction-section]  ;; Unique
      [weapon-targeting-section]   ;; Unique
      ;; Leave placeholders for shared components
      ])
   ```

2. **Then integrate shared components**
   ```clojure
   (defn fighting-style-builder []
     [:div
      [fighting-style-header]      ;; Unique
      [class-restriction-section]  ;; Unique
      [weapon-targeting-section]   ;; Unique
      [shared/modifier-section]    ;; Shared!
      [shared/spell-section]])     ;; Shared!
   ```

**Why this order**: Ensures source material mapping is correct first, then optimize with shared code.

### Guideline 3: Additive Props Only

**When adding new capability**:

✅ **Do this** (additive):
```clojure
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; Existing props
    :initiative [...]
    :skill-prof [...]

    ;; NEW props (additive)
    :blindsight [(modifiers/blindsight v)]
    :firing-reach [(modifiers/firing-reach v)]

    nil))
```

❌ **Don't do this** (breaking):
```clojure
(defn make-feat-modifiers [k v option-key]
  (case k
    ;; CHANGED: Renamed :initiative to :initiative-bonus
    :initiative-bonus [...]  ;; BREAKS old feats using :initiative!

    nil))
```

**If you need to change behavior**, keep old prop and add new one:
```clojure
(case k
  :initiative [(modifiers/initiative v)]           ;; Old (still works)
  :initiative-bonus [(modifiers/initiative v)]     ;; New (alias)
  :initiative-advantage [(modifiers/init-adv v)]  ;; New (different mechanic)
  nil)
```

---

## Conclusion

### The Three Pillars of Builder Design

1. **Source Material Mirroring**
   - Builder layout matches source book layout
   - Enables fast, accurate transcription
   - Users don't need to "translate" mentally

2. **Shared Components Under the Hood**
   - Same modifier widgets across all builders
   - Same conversion logic (`plugin-modifiers`)
   - DRY principle without sacrificing UX

3. **Additive Extensibility**
   - New props coexist with old props
   - Clojure makes additive changes natural
   - Backward compatibility by default

### Why Fighting Style Builder Must Be Separate

1. **Mental model**: Fighting styles ≠ feats (conceptually different)
2. **Source material**: Different layout in books (different sections)
3. **Data entry UX**: Transcription workflow requires matching layout
4. **Unique sections**: Class restrictions, weapon targeting, tier selection

### How Shared Components Work

**Users see**: Separate builders with source-matching layouts
**Developers see**: 70% shared code (modifier widgets, spell selection, etc.)
**Result**: Best UX for data entry + best DX for maintenance

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent (with critical user insight on source material mirroring)
**Status**: Builder Design Philosophy - Core Principle Documented
