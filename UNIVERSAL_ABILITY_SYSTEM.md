# Universal Ability System - Architectural Vision

**Concept**: Unified system for Fighting Styles, Feats, Class Features, Magic Items, and more
**Date**: 2026-01-12
**Status**: Architectural Design Proposal
**Priority**: CRITICAL - Could revolutionize builder architecture

---

## Executive Summary

Fighting styles, feats, class features, subclass features, magic item abilities, racial traits, and background features are all **the same thing** at the architectural level: **abilities with mechanical effects**.

The only differences are:
- **How they're acquired** (class level vs feat slot vs magic item)
- **When they're available** (restrictions)
- **How they're presented** in the UI

**Proposal**: Build a **Universal Ability System** with different "packaging" layers, rather than separate systems for each type.

---

## Part 1: The Pattern Recognition

### What Do These All Have in Common?

Let's compare Fighting Styles, Feats, and Class Features:

| Aspect | Fighting Style | Feat | Class Feature | Magic Item |
|--------|---------------|------|---------------|------------|
| **Name** | ✅ "Archery" | ✅ "Alert" | ✅ "Action Surge" | ✅ "Tome of Advanced FS" |
| **Description** | ✅ Text | ✅ Text | ✅ Text | ✅ Text |
| **Prerequisites** | ✅ Class | ✅ Ability/level | ✅ Class/level | ✅ Attunement |
| **Modifiers** | ✅ +2 ranged attack | ✅ +5 initiative | ✅ Extra action | ✅ Grant ability |
| **Selections** | ❌ Usually none | ✅ Sometimes | ✅ Often | ✅ Choose from list |
| **Resources** | ❌ Usually passive | ✅ Sometimes | ✅ Often | ✅ Sometimes |
| **Source tracking** | ✅ Class name | ✅ "Feat" | ✅ Class/level | ✅ Item name |

**Observation**: They all have the same structure! The only differences are **restrictions** and **acquisition method**.

### Real Examples Comparison

#### Example 1: Alert Feat vs Feral Instinct (Barbarian 7)

**Alert Feat**:
```clojure
{:name "Alert"
 :description "Always alert to danger..."
 :props {:initiative 5
         :no-surprise true
         :no-hidden-advantage true}}
```

**Feral Instinct** (Barbarian class feature):
```clojure
{:name "Feral Instinct"
 :level 7
 :description "Your instincts are honed..."
 :modifiers [(modifiers/initiative-advantage)
             (modifiers/trait-cfg
               {:summary "Can act during surprise if you rage"})]}
```

**Common structure**:
- Name + description
- Initiative bonus/advantage (different mechanics, same category)
- Source: Feat vs Class level 7

#### Example 2: Blessed Warrior (Fighting Style) vs Magic Initiate (Feat)

**Blessed Warrior**:
```clojure
{:name "Blessed Warrior"
 :class-restrictions #{:paladin}
 :spell-selections [{:spell-list :cleric
                     :num 2
                     :spell-level 0
                     :spellcasting-ability ::char5e/cha}]}
```

**Magic Initiate**:
```clojure
{:name "Magic Initiate"
 :selections [{:name "Spell Class"
               :options [bard-option cleric-option druid-option...]}]
 :spell-selections [{:spell-list :chosen  ;; From selection above
                     :num 2
                     :spell-level 0}
                    {:spell-list :chosen
                     :num 1
                     :spell-level 1
                     :frequency :long-rest-1}]}
```

**Common structure**:
- Grant cantrips from a spell list
- Blessed Warrior = Paladin only, always Cleric list
- Magic Initiate = Anyone, choose class

**They're the same ability with different restrictions!**

#### Example 3: Superior Technique (Fighting Style) vs Battle Master Maneuvers (Class)

**Superior Technique** (Fighting Style):
```clojure
{:name "Superior Technique"
 :class-restrictions #{:fighter}
 :maneuver-selection {:num 1 :source :battle-master}
 :resources [{:type :superiority-die
              :die-size 6
              :quantity 1
              :recovery :short-rest}]}
```

**Battle Master** (Fighter subclass):
```clojure
{:name "Battle Master"
 :levels {3 {:maneuver-selection {:num 3 :source :battle-master}
             :resources [{:type :superiority-die
                          :die-size 8
                          :quantity 4
                          :recovery :short-rest}]}
          7 {:maneuver-selection {:num 2}}  ;; +2 more
          ;; etc.
          }}
```

**Common structure**:
- Grant Battle Master maneuvers
- Grant superiority dice resource
- Superior Technique = 1 maneuver, d6, via fighting style
- Battle Master = 3+ maneuvers, d8, via subclass

**Same system, different quantities!**

---

## Part 2: The Universal Ability Model

### Core Ability Schema

```clojure
(spec/def ::universal-ability
  (spec/keys :req-un [::name ::key]
             :opt-un [::description
                      ::summary
                      ::icon

                      ;; Prerequisites & Restrictions
                      ::prereqs
                      ::class-restrictions
                      ::level-restrictions
                      ::ability-score-requirements
                      ::feature-requirements

                      ;; Mechanical Effects
                      ::props
                      ::modifiers
                      ::selections
                      ::spell-selections
                      ::maneuver-selections
                      ::resources

                      ;; Metadata
                      ::source
                      ::page
                      ::tier
                      ::tags

                      ;; Acquisition
                      ::acquisition-type  ;; :class-feature :feat :fighting-style :magic-item :racial-trait
                      ::acquisition-level
                      ::acquisition-restrictions]))
```

### Packaging Types

Instead of separate systems, we have **one ability system** with different **packages**:

```clojure
(def ability-packages
  {:fighting-style
   {:display-name "Fighting Style"
    :builder-route "/fighting-style-builder"
    :default-acquisition :class-feature
    :default-level 1  ;; or 2
    :plugin-key ::e5/fighting-styles
    :selection-function fighting-style-selection}

   :feat
   {:display-name "Feat"
    :builder-route "/feat-builder"
    :default-acquisition :asi-replacement
    :default-level nil  ;; ASI levels
    :plugin-key ::e5/feats
    :selection-function feat-selection}

   :class-feature
   {:display-name "Class Feature"
    :builder-route "/class-feature-builder"
    :default-acquisition :class-level
    :default-level nil  ;; Varies
    :plugin-key ::e5/class-features
    :selection-function class-feature-at-level}

   :magic-item-ability
   {:display-name "Magic Item Ability"
    :builder-route "/magic-item-builder"
    :default-acquisition :item-attunement
    :plugin-key ::e5/magic-items
    :selection-function magic-item-ability}

   :racial-trait
   {:display-name "Racial Trait"
    :builder-route "/racial-trait-builder"
    :default-acquisition :race-selection
    :plugin-key ::e5/racial-traits}

   :background-feature
   {:display-name "Background Feature"
    :builder-route "/background-builder"
    :default-acquisition :background-selection
    :plugin-key ::e5/background-features}})
```

---

## Part 3: Shared vs Unique Aspects

### Shared Across All Types (Core System)

**These should be in ONE universal system**:

1. **Modifier System**
   - Attack bonuses, damage bonuses, AC, HP, saves, skills, etc.
   - Already exists as `plugin-modifiers` - just needs expansion

2. **Selection System**
   - Spell selections, maneuver selections, skill selections, etc.
   - Already exists as various selection types

3. **Resource System**
   - Spell slots, superiority dice, rage uses, ki points, etc.
   - Partially exists, needs unification

4. **Conditional Logic**
   - "While wearing armor", "When wielding heavy weapon", etc.
   - Needs standardization

5. **Prerequisite System**
   - Ability scores, level, class, existing features
   - Already exists for feats

### Package-Specific (Wrapper Layer)

**These differ by package type**:

1. **Acquisition Method**
   - Fighting Style: Granted by class at specific level
   - Feat: Chosen at ASI levels or as bonus feat
   - Class Feature: Automatic at class level
   - Magic Item: Found/bought, may require attunement

2. **Restrictions**
   - Fighting Style: Usually class-restricted
   - Feat: Ability score or feature prerequisites
   - Class Feature: Class and level
   - Magic Item: Attunement, rarity, alignment

3. **UI Presentation**
   - Fighting Style: Dropdown during class selection
   - Feat: List of all feats with filtering
   - Class Feature: Automatic display on level-up
   - Magic Item: Item sheet with abilities

4. **Replacement/Swapping**
   - Fighting Style: Usually permanent (except Blessed Warrior cantrips)
   - Feat: Permanent (no replacement in standard rules)
   - Class Feature: Permanent
   - Magic Item: Lost if item is lost

---

## Part 4: Current State Analysis

### What Already Exists

Looking at `options.cljc`:

```clojure
;; FEAT SYSTEM (lines 3230-3400+)
(defn make-feat-modifiers [k v option-key]
  ;; 30+ prop types supported
  )

(defn plugin-modifiers [props option-key]
  ;; Converts props -> modifiers
  )

(defn feat-option-from-cfg [...]
  ;; Converts feat data -> option-cfg
  )
```

**This already exists and works!** Feats use it extensively.

### What's Missing

1. **Fighting styles don't use `plugin-modifiers`**
   - They're hardcoded in `fighting-style-options` (lines 1688-1748)
   - Should be converted to use the same system as feats

2. **Class features don't use `plugin-modifiers`**
   - They're defined inline in class definitions
   - Could benefit from shared system

3. **No unified builder UI**
   - Feat builder exists
   - Fighting style builder doesn't exist yet
   - Class feature builder doesn't exist
   - Could share 80%+ of UI code

---

## Part 5: Proposed Architecture

### Layer 1: Core Ability System

**Purpose**: Universal modifier/selection/resource system

**Location**: `src/cljc/orcpub/dnd/e5/abilities.cljc` (NEW FILE)

```clojure
(ns orcpub.dnd.e5.abilities
  "Universal ability system for fighting styles, feats, class features, etc.")

(spec/def ::ability
  (spec/keys :req-un [::name ::key ::package-type]
             :opt-un [::description ::props ::modifiers ::selections
                      ::prereqs ::restrictions ::resources]))

(defn ability->modifiers [ability]
  "Convert ability data to modifiers (uses plugin-modifiers)"
  (let [{:keys [props key]} ability]
    (plugin-modifiers props key)))

(defn ability->selections [ability spell-lists spells-map]
  "Convert ability data to selections"
  (let [{:keys [spell-selections maneuver-selections skill-selections]} ability]
    (concat
     (spell-selections->re-frame spell-selections spell-lists spells-map)
     (maneuver-selections->re-frame maneuver-selections)
     (skill-selections->re-frame skill-selections))))

(defn ability->option-cfg [ability context]
  "Convert ability to option-cfg format"
  (t/option-cfg
   {:name (:name ability)
    :key (:key ability)
    :modifiers (ability->modifiers ability)
    :selections (ability->selections ability context)
    :prereqs (build-prereqs ability)}))
```

### Layer 2: Package Wrappers

**Purpose**: Package-specific logic and presentation

**Fighting Style Package** (`fighting_styles.cljc`):
```clojure
(defn fighting-style-option-from-cfg [cfg]
  "Wrapper around universal ability system for fighting styles"
  (ability->option-cfg
   (assoc cfg :package-type :fighting-style)
   {:context :fighting-style}))

(defn all-fighting-style-options [plugins]
  "Merge SOURCE and plugin fighting styles"
  (let [source-styles fighting-style-options  ;; Hardcoded
        plugin-styles (mapcat ::e5/fighting-styles (vals plugins))
        plugin-options (map fighting-style-option-from-cfg plugin-styles)]
    (concat source-styles plugin-options)))
```

**Feat Package** (already exists, minimal changes):
```clojure
(defn feat-option-from-cfg [...]
  "Wrapper around universal ability system for feats"
  (ability->option-cfg
   (assoc cfg :package-type :feat)
   {:context :feat
    :language-map language-map
    :spells-map spells-map
    :spell-lists spell-lists}))
```

**Class Feature Package** (NEW):
```clojure
(defn class-feature-option-from-cfg [cfg]
  "Wrapper around universal ability system for class features"
  (ability->option-cfg
   (assoc cfg :package-type :class-feature)
   {:context :class-feature}))
```

### Layer 3: Builder UI

**Purpose**: Unified UI for building abilities

**Shared Components** (`src/cljs/orcpub/dnd/e5/views/ability_builder.cljs`):
```clojure
(defn ability-builder-core [ability-type]
  "Core builder UI shared by all ability types"
  [:div.ability-builder
   ;; Name & Description (shared)
   [name-description-section ability-type]

   ;; Modifiers Section (shared)
   [modifiers-section ability-type]

   ;; Spell Selections (shared)
   [spell-selection-section ability-type]

   ;; Resources (shared)
   [resource-section ability-type]

   ;; Package-specific sections
   (case ability-type
     :fighting-style [class-restriction-section]
     :feat [prereq-section]
     :class-feature [level-section]
     :magic-item [attunement-section])])
```

**Package-Specific Builders**:
```clojure
(defn fighting-style-builder []
  [ability-builder-core :fighting-style])

(defn feat-builder []
  [ability-builder-core :feat])  ;; Migrate existing feat builder

(defn class-feature-builder []
  [ability-builder-core :class-feature])
```

---

## Part 6: Migration Path

### Phase 1: Extract Core System (2 weeks)

**Goal**: Create universal ability system without breaking existing code

**Tasks**:
1. Create `abilities.cljc` with core functions
2. Extract `plugin-modifiers` to be package-agnostic
3. Create universal conversion functions
4. Add comprehensive tests

**Deliverable**: Core system exists, nothing uses it yet

### Phase 2: Migrate Fighting Styles (2 weeks)

**Goal**: Fighting styles use universal system

**Tasks**:
1. Create `fighting-style-option-from-cfg` using core system
2. Migrate hardcoded styles to data format (optional)
3. Implement fighting style builder using shared UI
4. Plugin support for fighting styles

**Deliverable**: Fighting styles work via universal system

### Phase 3: Enhance Feat System (1 week)

**Goal**: Feats use enhanced universal system

**Tasks**:
1. Migrate `feat-option-from-cfg` to use core system
2. Enhance with new modifiers from fighting style analysis
3. Update feat builder UI to use shared components

**Deliverable**: Feats and fighting styles share 90% of code

### Phase 4: Class Features (3 weeks)

**Goal**: Class features can be built with same system

**Tasks**:
1. Create class feature package wrapper
2. Build class feature builder UI
3. Extract some existing class features as examples
4. Plugin support for custom class features

**Deliverable**: Full class feature builder using shared system

### Phase 5: Magic Items & Others (2 weeks)

**Goal**: Complete the ecosystem

**Tasks**:
1. Magic item ability wrapper
2. Racial trait wrapper
3. Background feature wrapper
4. Unified "My Content" page

**Deliverable**: All ability types use universal system

**Total Timeline**: 10 weeks for complete migration

---

## Part 7: Benefits Analysis

### Code Reuse

**Current state**: Duplicate code everywhere
- Feat builder: ~2000 lines
- Fighting style builder: ~1500 lines (estimated)
- Class feature builder: ~2000 lines (if built)
- Magic item builder: ~1500 lines (if built)
- **Total**: ~7000 lines

**With universal system**:
- Core ability system: ~1500 lines
- Package wrappers: ~500 lines each × 4 = 2000 lines
- Shared UI components: ~2000 lines
- **Total**: ~5500 lines

**Savings**: ~1500 lines (21% reduction) + much easier to maintain

### Feature Parity

**Current state**: Features added to one system don't propagate
- Fighting styles get spell selections → feats don't get it
- Feats get complex prereqs → fighting styles can't use them
- Inconsistent UX across builders

**With universal system**:
- Add feature once → available everywhere
- Spell selections work for fighting styles AND feats AND class features
- Consistent UX across all builders
- Users learn one system, use everywhere

### Extensibility

**Current state**: Adding new ability types is expensive
- Need new builder UI (weeks of work)
- Need new conversion logic
- Need new storage/event handlers

**With universal system**:
- New package type = ~2 days of work
- Wrapper + route + minor UI tweaks
- Most work is configuration, not code

### User Experience

**For Players**:
- Consistent interface across all builders
- Same modifier selection UI for feats, fighting styles, class features
- Learn once, use everywhere
- Easy to understand homebrew (all same structure)

**For Homebrewers**:
- One system to learn for creating custom content
- Can create feat-like fighting styles
- Can create class features with same power as feats
- Flexible packaging (same ability as feat or fighting style)

---

## Part 8: Real-World Examples

### Example 1: Alert as Fighting Style

With universal system, you could create:

**Alert Fighting Style** (homebrew):
```clojure
{:name "Alert Fighting Style"
 :key :alert-fighting-style
 :package-type :fighting-style
 :class-restrictions #{:fighter :ranger}
 :description "Your combat training has honed your reflexes..."
 :props {:initiative 5
         :no-surprise true}}
```

**vs Alert Feat** (official):
```clojure
{:name "Alert"
 :key :alert
 :package-type :feat
 :description "Always alert to danger..."
 :props {:initiative 5
         :no-surprise true
         :no-hidden-advantage true}}
```

**Same props system!** Fighting style version is slightly weaker (no hidden advantage), class-restricted, but available at level 1.

### Example 2: Magic Initiate as Fighting Style

**Arcane Initiate Fighting Style** (homebrew):
```clojure
{:name "Arcane Initiate"
 :key :arcane-initiate
 :package-type :fighting-style
 :class-restrictions #{:fighter}
 :description "You've learned basic magic..."
 :spell-selections [{:spell-list :wizard
                     :spell-level 0
                     :num 2
                     :spellcasting-ability ::char5e/int}]}
```

**vs Blessed Warrior** (official fighting style):
```clojure
{:name "Blessed Warrior"
 :key :blessed-warrior
 :package-type :fighting-style
 :class-restrictions #{:paladin}
 :spell-selections [{:spell-list :cleric
                     :spell-level 0
                     :num 2
                     :spellcasting-ability ::char5e/cha}]}
```

**Same structure!** Just different spell list and class.

### Example 3: Quick Shot as Feat

**Quick Shot Feat** (convert from fighting style):
```clojure
{:name "Quick Shot"
 :key :quick-shot-feat
 :package-type :feat
 :description "You've mastered ranged combat positioning..."
 :prereqs [{:type :ability-score :ability ::char5e/dex :min 13}]
 :props {:firing-reach {:formula "5 + reach"
                        :enables-ranged-oa true}}}
```

**vs Quick Shot Fighting Style** (from TGS2):
```clojure
{:name "Quick Shot"
 :key :quick-shot
 :package-type :fighting-style
 :props {:firing-reach {:formula "5 + reach"
                        :enables-ranged-oa true}}}
```

**Identical mechanics!** Just different acquisition (feat vs fighting style).

---

## Part 9: Plugin Format Impact

### Current Plugin Format

```clojure
{::e5/plugin
 {::e5/name "My Homebrew"
  ::e5/feats [{:name "..." :props {...}}]
  ::e5/fighting-styles [{:name "..." :props {...}}]
  ::e5/classes [{:name "..." :levels {...}}]}}
```

**Problem**: Separate keys for each type, duplicate structure

### Enhanced Plugin Format

```clojure
{::e5/plugin
 {::e5/name "My Homebrew"
  ::e5/abilities
  [{:name "Alert Fighting Style"
    :package-type :fighting-style
    :props {...}}

   {:name "Alert Feat"
    :package-type :feat
    :props {...}}

   {:name "Feral Instinct"
    :package-type :class-feature
    :class :barbarian
    :level 7
    :props {...}}]}}
```

**OR keep separate keys but share structure**:
```clojure
{::e5/plugin
 {::e5/name "My Homebrew"
  ::e5/fighting-styles
  [{:name "Alert Fighting Style"
    :props {...}}]  ;; Uses universal ability schema

  ::e5/feats
  [{:name "Alert"
    :props {...}}]  ;; Uses universal ability schema

  ::e5/class-features
  [{:name "Feral Instinct"
    :class :barbarian
    :level 7
    :props {...}}]}}  ;; Uses universal ability schema
```

**Benefit**: All use same `:props` structure, same conversion logic

---

## Part 10: Implementation Priorities

### Critical Path

**What MUST be done for fighting styles**:
1. ✅ Extract `plugin-modifiers` to be package-agnostic
2. ✅ Create `fighting-style-option-from-cfg` using shared logic
3. ✅ Build fighting style builder UI (can reuse feat UI patterns)

**What SHOULD be done (high value)**:
4. ✅ Unify modifier prop system across feats and fighting styles
5. ✅ Create shared UI components for modifiers
6. ✅ Spell selection system that works for both

**What COULD be done (future)**:
7. ⏳ Full universal ability system
8. ⏳ Class feature builder
9. ⏳ Magic item ability system
10. ⏳ Migration of all systems to universal core

### Minimum Viable Universal System

**For fighting style launch**, we need:

1. **Shared Props System** (1 week)
   - Extend `make-feat-modifiers` with fighting-style-specific props
   - Document all prop types
   - Ensure feats and fighting styles can use same props

2. **Shared Conversion Logic** (3 days)
   - `ability->modifiers` function
   - `ability->selections` function
   - Works for both feats and fighting styles

3. **Shared UI Components** (1 week)
   - Modifier selection UI (attack bonuses, AC, etc.)
   - Spell selection UI
   - Resource UI
   - Reusable by feat builder and fighting style builder

**Total MVP**: 2.5 weeks

**Delivers**: Fighting styles and feats share 70% of code, easy to add more later

---

## Conclusion

The fighting style system has revealed that **all ability types** (feats, class features, fighting styles, magic items) are essentially:

```
Ability = Name + Description + Prerequisites + Modifiers + Selections + Resources
```

The only differences are **how they're acquired** and **how they're presented**.

### Recommendation

**Short-term** (fighting style launch):
- Extend existing `plugin-modifiers` system
- Share code between feats and fighting styles
- Build shared UI components
- **Don't** do full architecture overhaul yet

**Long-term** (6-12 months):
- Migrate to full universal ability system
- Unify all builders
- Enable cross-package abilities (feat that grants class feature, fighting style that grants spell selection, etc.)
- Massive code reduction and maintainability improvement

### Next Steps

1. ✅ Approve architectural direction
2. ✅ Prioritize: MVP shared system or full universal system?
3. ✅ Update fighting style implementation plan with shared code approach
4. ✅ Begin extraction of shared components

**The universal ability system is the right long-term architecture**, but we can deliver fighting styles with shared code principles without a full rewrite.

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent
**Status**: Architectural Proposal - Critical Review Requested
