# Universal Ability System - Backward Compatibility & Architecture Clarification

**CRITICAL ADDENDUM** to UNIVERSAL_ABILITY_SYSTEM.md
**Date**: 2026-01-12
**Status**: Architecture Revision

---

## CRITICAL CONSTRAINT: Backward Compatibility

### The Requirement

**NOTHING can break**:
- ✅ Existing orcbrew files must load without modification
- ✅ Existing characters must continue to work
- ✅ Existing saved items must remain functional
- ✅ All exposed data formats must remain compatible

**This is NON-NEGOTIABLE** and takes absolute priority over architectural elegance.

### What This Means

**We CANNOT**:
- ❌ Change the plugin spec format (breaking change)
- ❌ Modify how existing feats are stored
- ❌ Alter character save format
- ❌ Remove or rename existing props without compatibility layer

**We CAN**:
- ✅ Add new optional fields to specs
- ✅ Create new conversion functions that handle old AND new formats
- ✅ Share internal code while maintaining external format
- ✅ Add compatibility layers for new features

---

## Architecture Clarification: Separate Builders, Shared Core

### What Was Proposed (INCORRECT)

❌ **Unified Builder UI** - Single "ability builder" for everything
- This would confuse users
- Breaks mental model separation
- Not what we want!

### What We ACTUALLY Want (CORRECT)

✅ **Separate Builder UIs with Shared Core System**

```
User-Facing Layer (SEPARATE):
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Feat Builder    │  │ Fighting Style  │  │ Class Feature   │
│ /feat-builder   │  │ Builder         │  │ Builder         │
│                 │  │ /fighting-style │  │ /class-feature  │
│ [Unique UI for  │  │ [Unique UI for  │  │ [Unique UI for  │
│  feat concept]  │  │  style concept] │  │  feature        │
│                 │  │                 │  │  concept]       │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                     │
         └────────────────────┼─────────────────────┘
                              │
                    Shared UI Components:
                    - Modifier selection widgets
                    - Spell selection widgets
                    - Resource configuration
                    - Attack/damage/AC inputs
                              │
         ┌────────────────────┼─────────────────────┐
         │                    │                     │
┌────────▼────────┐  ┌────────▼────────┐  ┌────────▼────────┐
│ Feat Wrapper    │  │ Fighting Style  │  │ Class Feature   │
│ feat-option-    │  │ Wrapper         │  │ Wrapper         │
│ from-cfg        │  │ fs-option-      │  │ cf-option-      │
│                 │  │ from-cfg        │  │ from-cfg        │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                     │
         └────────────────────┼─────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │ SHARED CORE SYSTEM │
                    │ (under the hood)   │
                    │                    │
                    │ • plugin-modifiers │
                    │ • props→modifiers  │
                    │ • spell selections │
                    │ • resource system  │
                    └────────────────────┘
```

**Key Points**:
1. **Users see**: Three separate builders with distinct UIs
2. **Developers see**: Shared core conversion logic (DRY)
3. **Mental models**: Preserved (feats ≠ fighting styles ≠ class features)
4. **Code reuse**: High (70-80% shared under the hood)

---

## Part 1: Backward Compatibility Strategy

### Current Orcbrew Plugin Format

**Existing format must continue to work**:

```clojure
;; EXISTING - Must still work!
{::e5/plugin
 {::e5/feats
  [{:name "Custom Feat"
    :key :custom-feat
    :description "..."
    :props {:initiative 5
            :skill-prof {:perception true}}}]}}
```

**How to handle**: Conversion functions must accept BOTH old and new formats

```clojure
(defn feat-option-from-cfg [cfg]
  ;; BACKWARD COMPATIBLE - handles old format
  (let [normalized-cfg (normalize-legacy-feat cfg)]  ;; Migration layer
    (ability->option-cfg
     (assoc normalized-cfg :package-type :feat))))

(defn normalize-legacy-feat [cfg]
  "Ensure old feat format works with new system"
  ;; Old format already uses :props, :name, :key, :description
  ;; No changes needed! Just pass through.
  cfg)
```

**Result**: Existing orcbrew files load without ANY changes!

### Adding New Features (Backward Compatible)

**Example: Adding fighting styles to plugins**

```clojure
;; NEW format (optional)
{::e5/plugin
 {::e5/feats [{...}]          ;; Old format still works
  ::e5/fighting-styles        ;; NEW - optional field
  [{:name "Custom Fighting Style"
    :key :custom-style
    :description "..."
    :props {:ranged-attack-bonus 2}}]}}
```

**Spec change** (backward compatible):
```clojure
;; BEFORE
(spec/def ::plugin
  (spec/keys :opt [::feats ::classes ::subclasses]))

;; AFTER - just add optional field
(spec/def ::plugin
  (spec/keys :opt [::feats ::classes ::subclasses
                   ::fighting-styles]))  ;; NEW but OPTIONAL
```

**Old plugins** (without `::fighting-styles`) still valid! ✅

### Character Save Compatibility

**Existing character format**:
```clojure
{:character-name "Aragorn"
 :feats [:alert :tough]
 :classes {:fighter {:level 5
                     :fighting-style :archery}}}  ;; Hardcoded key
```

**How fighting styles work now**: Hardcoded in `fighting-style-options` vector

**After our changes**:
- Hardcoded styles remain in same vector
- Plugin styles get merged in dynamically
- Character save format **doesn't change** - still stores `:archery` keyword

```clojure
(defn all-fighting-style-options [plugins]
  "Backward compatible - merges hardcoded + plugin styles"
  (let [hardcoded-styles fighting-style-options  ;; Original 6 PHB styles
        plugin-styles (mapcat ::e5/fighting-styles (vals plugins))
        plugin-options (map fighting-style-option-from-cfg plugin-styles)]
    (concat hardcoded-styles plugin-options)))  ;; Merge, don't replace!
```

**Old characters** with `:archery` still find it in merged list! ✅

---

## Part 2: Separate Builders with Shared Core

### Mental Model Preservation

**Why separate builders matter**:

**Feat** (to a user):
- "Special training or talent"
- Chosen at ASI levels
- Has prerequisites (ability scores, level)
- Permanent once taken
- Mental model: "I trained to get this"

**Fighting Style** (to a user):
- "Combat specialization"
- Granted by class at low level
- Usually class-restricted
- Fundamental to character identity
- Mental model: "This is HOW I fight"

**Class Feature** (to a user):
- "Ability from my class"
- Automatic at specific level
- Defines class identity
- Can't choose different one
- Mental model: "This is what my class does"

**These are DIFFERENT concepts** to users, even if mechanically similar!

### UI Architecture (Revised)

#### Feat Builder
**Route**: `/feat-builder`
**UI Unique Elements**:
- Prerequisite section (ability scores, armor, spellcasting)
- Ability score increase options (half feats)
- "Available at level" display
- Feat-specific flavor (training, talent)

**UI Shared Components**:
- Modifier selection (attack bonuses, AC, skills, etc.)
- Spell selection (Magic Initiate, Ritual Caster, etc.)
- Resource configuration (if needed)

#### Fighting Style Builder
**Route**: `/fighting-style-builder`
**UI Unique Elements**:
- Class restriction checkboxes (Fighter, Paladin, Ranger, etc.)
- Tier selection (Standard, Advanced, Custom)
- Weapon restriction section (category, property, specific)
- Fighting style-specific flavor (combat technique)

**UI Shared Components**:
- Modifier selection (same widgets as feat builder!)
- Spell selection (Blessed Warrior, Druidic Warrior)
- Resource configuration (Superior Technique)

#### Class Feature Builder
**Route**: `/class-feature-builder`
**UI Unique Elements**:
- Class selection dropdown
- Level selection (1-20)
- Subclass association (if applicable)
- Frequency (always-on, per short rest, per long rest)
- Class feature-specific flavor (class ability)

**UI Shared Components**:
- Modifier selection (same widgets!)
- Spell selection (same widgets!)
- Resource configuration (same widgets!)

### Code Organization

```
src/cljs/orcpub/dnd/e5/views/
├── builders/
│   ├── shared/                    # SHARED COMPONENTS
│   │   ├── modifier_section.cljs  # Attack/damage/AC inputs
│   │   ├── spell_section.cljs     # Spell selection widgets
│   │   ├── resource_section.cljs  # Resource pool configuration
│   │   └── name_description.cljs  # Basic fields
│   │
│   ├── feat_builder.cljs          # UNIQUE: Feat-specific UI + shared components
│   ├── fighting_style_builder.cljs # UNIQUE: FS-specific UI + shared components
│   └── class_feature_builder.cljs  # UNIQUE: CF-specific UI + shared components
│
└── [other files]
```

**Example - Feat Builder**:
```clojure
(ns orcpub.dnd.e5.views.builders.feat-builder
  (:require [orcpub.dnd.e5.views.builders.shared.modifier-section :as mods]
            [orcpub.dnd.e5.views.builders.shared.spell-section :as spells]))

(defn feat-builder []
  [:div.feat-builder
   ;; UNIQUE to feats
   [feat-prerequisite-section]
   [ability-score-increase-section]

   ;; SHARED components
   [mods/modifier-section :feat]
   [spells/spell-selection-section :feat]

   ;; UNIQUE to feats
   [feat-specific-help-text]])
```

**Example - Fighting Style Builder**:
```clojure
(ns orcpub.dnd.e5.views.builders.fighting-style-builder
  (:require [orcpub.dnd.e5.views.builders.shared.modifier-section :as mods]
            [orcpub.dnd.e5.views.builders.shared.spell-section :as spells]))

(defn fighting-style-builder []
  [:div.fighting-style-builder
   ;; UNIQUE to fighting styles
   [class-restriction-section]
   [weapon-restriction-section]
   [tier-selection-section]

   ;; SHARED components (SAME as feat builder!)
   [mods/modifier-section :fighting-style]
   [spells/spell-selection-section :fighting-style]

   ;; UNIQUE to fighting styles
   [fighting-style-examples]])
```

**Result**:
- **Users**: See distinct builders with appropriate mental models
- **Developers**: Share 70% of UI code (modifier widgets, spell selection, etc.)
- **Maintainability**: Fix modifier widget bug once → fixed in all builders!

---

## Part 3: Conversion Layer Architecture

### The Compatibility Pattern

```clojure
;; Core conversion (NEW - shared)
(defn ability->option-cfg [ability-data package-type context]
  "Universal conversion - handles ALL ability types"
  (let [modifiers (plugin-modifiers (:props ability-data) (:key ability-data))
        selections (make-selections ability-data context)]
    (t/option-cfg
     {:name (:name ability-data)
      :key (:key ability-data)
      :modifiers modifiers
      :selections selections
      :prereqs (build-prereqs ability-data package-type)})))

;; Feat wrapper (EXISTING - backward compatible)
(defn feat-option-from-cfg [language-map spells-map spell-lists weapons cfg]
  "Backward compatible - still accepts same parameters as before"
  (ability->option-cfg
   cfg
   :feat
   {:language-map language-map
    :spells-map spells-map
    :spell-lists spell-lists
    :weapons weapons}))

;; Fighting style wrapper (NEW)
(defn fighting-style-option-from-cfg [cfg]
  "New but follows same pattern as feat-option-from-cfg"
  (ability->option-cfg
   cfg
   :fighting-style
   {:plugins @(subscribe [::e5/plugins])}))
```

### Migration Example

**OLD (current feat system)**:
```clojure
(defn feat-option-from-cfg [language-map spells-map spell-lists weapons
                            {:keys [name key description props ...]}]
  (let [feat-mods (feat-modifiers key name description props ...)
        feat-sels (feat-selections ...)]
    (t/option-cfg
     {:name name
      :key key
      :modifiers feat-mods
      :selections feat-sels
      ...})))
```

**NEW (shared system)**:
```clojure
(defn feat-option-from-cfg [language-map spells-map spell-lists weapons cfg]
  ;; Just delegate to shared system
  (ability->option-cfg cfg :feat
                       {:language-map language-map
                        :spells-map spells-map
                        :spell-lists spell-lists
                        :weapons weapons}))

;; The shared system does the same work as before!
(defn ability->option-cfg [cfg package-type context]
  (let [mods (plugin-modifiers (:props cfg) (:key cfg))  ;; Same as before!
        sels (make-selections cfg context)]              ;; Same as before!
    (t/option-cfg {...})))                                ;; Same as before!
```

**External format**: Unchanged! ✅
**Internal implementation**: Shared! ✅

---

## Part 4: Implementation Plan (Revised for Compatibility)

### Phase 0: Prep (No Breaking Changes) - 1 week

**Goal**: Set up shared infrastructure without touching existing code

**Tasks**:
1. Create `src/cljs/orcpub/dnd/e5/views/builders/shared/` directory
2. Extract modifier input widgets from feat builder → shared components
3. Create tests for backward compatibility
4. Document existing feat format (baseline)

**Deliverable**: Shared components exist, nothing uses them yet

**Compatibility check**: ✅ No changes to existing systems

### Phase 1: Fighting Style Core (Additive Only) - 2 weeks

**Goal**: Add fighting style support without changing existing code

**Tasks**:
1. Add `::e5/fighting-styles` to plugin spec (OPTIONAL field)
2. Create `fighting-style-option-from-cfg` (new function)
3. Extend `make-feat-modifiers` with fighting-style props (ADDITIVE)
4. Create `all-fighting-style-options` (merges hardcoded + plugin)

**Deliverable**: Fighting styles work, feats untouched

**Compatibility check**: ✅ Old plugins still valid (no ::fighting-styles = ok)

### Phase 2: Fighting Style Builder UI - 2 weeks

**Goal**: Build fighting style builder using shared components

**Tasks**:
1. Create fighting style builder route
2. Use shared modifier widgets from Phase 0
3. Add fighting-style-specific sections (class restrictions, weapon targeting)
4. Event handlers for save/load

**Deliverable**: Can create fighting styles via UI

**Compatibility check**: ✅ New feature, no existing data affected

### Phase 3: Feat Builder Enhancement (Optional) - 1 week

**Goal**: Refactor feat builder to use shared components (NO FORMAT CHANGE)

**Tasks**:
1. Replace feat builder modifier UI with shared components
2. Keep feat-option-from-cfg signature unchanged (wrapper)
3. Ensure all existing feats still work (regression test)

**Deliverable**: Feat builder uses shared code, no user-visible changes

**Compatibility check**: ✅ Must pass regression tests with existing orcbrews

### Phase 4: Expand Modifier Support - 2 weeks

**Goal**: Add new modifiers from TCE/TGS2 analysis

**Tasks**:
1. Add blindsight, tremorsense modifiers
2. Add critical-range modifiers
3. Add weapon-specific targeting
4. All ADDITIVE (new props, don't change existing)

**Deliverable**: Enhanced capabilities for new fighting styles

**Compatibility check**: ✅ New props, old props still work

**Total Timeline**: 8 weeks (vs 10 weeks in original plan)
**Risk**: Low (additive changes only, comprehensive testing)

---

## Part 5: Testing Strategy for Compatibility

### Regression Test Suite

**Critical tests**:

```clojure
(deftest backward-compatibility-feats-test
  (testing "Existing feat orcbrews load correctly"
    (let [old-feat-data {:name "Alert"
                         :key :alert
                         :props {:initiative 5
                                 :no-surprise true}}
          loaded-feat (feat-option-from-cfg {} {} {} {} old-feat-data)]
      (is (= "Alert" (::t/name loaded-feat)))
      (is (some #(= :initiative %) (map :key (::t/modifiers loaded-feat)))))))

(deftest backward-compatibility-plugins-test
  (testing "Old plugin format still loads"
    (let [old-plugin {::e5/feats [{:name "Custom" :key :custom :props {:initiative 2}}]}]
      (is (spec/valid? ::e5/plugin old-plugin))
      ;; Plugin should load without ::fighting-styles key
      )))

(deftest backward-compatibility-characters-test
  (testing "Old character saves work with new fighting style system"
    (let [old-char {:feats [:alert]
                    :classes {:fighter {:level 5 :fighting-style :archery}}}
          fighting-styles (all-fighting-style-options {})]
      ;; :archery should still be found in merged list
      (is (some #(= :archery (::t/key %)) fighting-styles)))))
```

### Migration Checklist

Before deploying ANY change:

- [ ] All existing orcbrew test files still load
- [ ] All existing character saves still load
- [ ] Feat builder still works with old feats
- [ ] Plugin spec validator accepts old format
- [ ] No changes to character save format
- [ ] No changes to plugin export format (unless additive)
- [ ] Regression test suite passes 100%

---

## Part 6: What Changes, What Doesn't

### DOES NOT CHANGE (Backward Compatible)

✅ **Plugin format** for existing types:
```clojure
{::e5/feats [{:name "..." :props {...}}]}  ;; Same as before
```

✅ **Feat builder signature**:
```clojure
(feat-option-from-cfg language-map spells-map spell-lists weapons cfg)
;; Same parameters, same order
```

✅ **Character save format**:
```clojure
{:feats [:alert :tough]
 :classes {:fighter {:fighting-style :archery}}}  ;; Same structure
```

✅ **Existing modifiers**:
```clojure
:initiative 5           ;; Still works
:skill-prof {:perception true}  ;; Still works
:damage-resistance {:fire true} ;; Still works
```

### DOES CHANGE (Additive/Internal Only)

✅ **Plugin spec** (additive):
```clojure
;; NEW optional field
(spec/def ::plugin
  (spec/keys :opt [::feats ::fighting-styles]))  ;; Added ::fighting-styles
```

✅ **Shared UI components** (internal):
- Extracted to shared directory
- Same functionality, different location

✅ **Conversion logic** (internal):
- Uses shared `ability->option-cfg`
- Same output, refactored implementation

✅ **New modifiers** (additive):
```clojure
:blindsight 10              ;; NEW prop (doesn't break old props)
:critical-range-weapon-specific {...}  ;; NEW prop
:firing-reach {...}         ;; NEW prop
```

---

## Conclusion

### Architectural Principles (Revised)

1. **Separate Builders, Shared Core**
   - Users: See distinct builders (feat ≠ fighting style ≠ class feature)
   - Developers: Share 70% of code under the hood

2. **Backward Compatibility is Absolute**
   - Old orcbrews MUST work
   - Old characters MUST work
   - All changes must be additive or have migration layers

3. **Progressive Enhancement**
   - Add new features without changing existing ones
   - New props coexist with old props
   - Optional plugin fields

### Recommendation (Updated)

**Short-term (Fighting Style Launch)**: 8 weeks
1. Extract shared UI components (no breaking changes)
2. Add fighting style system (additive only)
3. Build fighting style builder (new route, shared components)
4. Comprehensive regression testing

**Long-term (6-12 months)**:
5. Gradually refactor feat builder to use shared components
6. Add class feature builder (when ready)
7. Continuous backward compatibility testing

### Success Criteria

✅ Users get separate, familiar builders for each concept
✅ Developers get code reuse and shared infrastructure
✅ Zero breaking changes to existing orcbrews or characters
✅ New features are additive and optional
✅ 70% code reduction in builder UI components

---

**Document Version**: 2.0 (REVISED)
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent
**Status**: Architecture Revision - Addresses Backward Compatibility & Separate Builders
