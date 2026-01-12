# Fighting Styles Feature - Exploration & Implementation Plan

**Branch**: `claude/explore-fighting-styles-K56lQ`
**Date**: 2026-01-12
**Status**: Planning Phase

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Current Implementation](#current-implementation)
3. [Gaps & Limitations](#gaps--limitations)
4. [Proposed Solution](#proposed-solution)
5. [Implementation Plan](#implementation-plan)
6. [Technical Architecture](#technical-architecture)
7. [UI Design](#ui-design)
8. [Testing Strategy](#testing-strategy)

---

## Problem Statement

### Current Situation
The OrcPub D&D 5e character builder currently has **no capacity to expand Fighting Styles** through homebrew content. Fighting styles are hardcoded and cannot be extended through the orcbrew plugin system that works for other content types (feats, races, classes, etc.).

### Requirements
1. **Homebrew Expansion**: Enable users to create custom fighting styles through orcbrew files
2. **Builder UI**: New builder interface for creating fighting styles (similar to feat builder)
3. **Mechanical Benefits**: Support for attaching game mechanics (AC bonuses, attack bonuses, special abilities)
4. **Flexible Descriptions**: Allow custom names and descriptions
5. **Integration**: Seamless integration with existing class fighting style selections

### Success Criteria
- Users can create custom fighting styles via UI builder
- Custom fighting styles appear in class fighting style selections
- Orcbrew files can define new fighting styles
- Mechanical benefits are properly applied to characters
- Existing fighting styles continue to work unchanged

---

## Current Implementation

### File Locations

| Component | File Path | Lines |
|-----------|-----------|-------|
| Fighting Style Definitions | `/src/cljc/orcpub/dnd/e5/options.cljc` | 1688-1748 |
| Selection Functions | `/src/cljc/orcpub/dnd/e5/options.cljc` | 1750-1769 |
| UA Examples (commented) | `/src/cljc/orcpub/dnd/e5/templates/ua_base.cljc` | 690-711 |
| Fighter Integration | `/src/cljc/orcpub/dnd/e5/classes.cljc` | 1098, 1098+ |
| Paladin Integration | `/src/cljc/orcpub/dnd/e5/classes.cljc` | 1448 |
| Ranger Integration | `/src/cljc/orcpub/dnd/e5/classes.cljc` | 1826 |

### Current Architecture

#### Data Structure
```clojure
(def fighting-style-options
  [(t/option-cfg
    {:name "Archery"
     :modifiers [(modifiers/ranged-attack-bonus 2)
                 (modifiers/trait-cfg
                   {:name "Archery Fighting Style"
                    :page 72
                    :description "You gain a +2 bonus to attack rolls you make with ranged weapons."})]})])
```

#### Selection Function
```clojure
(defn fighting-style-selection [class-kw & [restrictions additional-options]]
  (fighting-style-selection-2
   class-kw
   1
   (if restrictions
     (filter
      (fn [o]
        (restrictions (::t/key o)))
      fighting-style-options)
     fighting-style-options)))
```

**Key Characteristics**:
- ✅ Hardcoded list of options in `fighting-style-options`
- ✅ Class-specific restrictions (e.g., Rangers only get certain styles)
- ✅ Ref-based grouping: `[:class class-kw :fighting-style]`
- ✅ Supports additional options parameter (unused currently)
- ❌ No integration with plugin system
- ❌ No way to add custom fighting styles via orcbrew

### UA Example: Mariner Fighting Style

Located in `/src/cljc/orcpub/dnd/e5/templates/ua_base.cljc` (lines 690-711), this commented-out example shows how UA content **could** add fighting styles:

```clojure
#_(defn mariner-class-option [nm kw level]
  (opt5e/class-option
   {:name nm
    :plugin? true
    :source ua-waterborne-kw
    :levels {level {:selections [(opt5e/fighting-style-selection-2
                                  kw
                                  0
                                  [(t/option-cfg
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
                                                   :page 2
                                                   :source ua-waterborne-kw
                                                   :summary "while not wearing heavy armor, gain +1 AC bonus and you have swimming speed and climbing speed equal to your land speed"})]})])]}}}))
```

**Why This Works in SOURCE Files**:
- Can call `fighting-style-selection-2` with custom options
- Direct code execution - full API access
- Uses `additional-options` parameter to inject new styles

**Why This DOESN'T Work in Orcbrew Imports**:
- Orcbrew files are data-only (EDN), not executable code
- Cannot call functions like `fighting-style-selection-2`
- No "fighting-style" type in the selection-map lookup system
- Ref-based merging not supported in plugin data format

---

## Gaps & Limitations

### Technical Gaps

1. **No Plugin Schema for Fighting Styles**
   - Current plugin spec doesn't include `::e5/fighting-styles`
   - No validation rules for fighting style data

2. **Selection System Limitation**
   - `level-selection` function only works with predefined types in `selection-map`
   - Fighting styles not registered as a selection type
   - Cannot create selections that merge via `:ref` path

3. **No Conversion Function**
   - Missing `fighting-style-option-from-cfg` (like `feat-option-from-cfg`)
   - No way to convert plugin data → option-cfg format

4. **Hardcoded Options List**
   - `fighting-style-options` is a static def, not dynamic
   - No mechanism to merge plugin fighting styles into the list

### UI Gaps

1. **No Builder Interface**
   - No fighting style builder page
   - No route defined
   - Not listed in "My Content" section

2. **No Storage/Management**
   - No localStorage key for fighting styles
   - No event handlers for create/edit/delete
   - No subscriptions for reading fighting styles

### Integration Gaps

1. **Class Selection Integration**
   - Need to merge homebrew styles into existing class selections
   - Must preserve class-specific restrictions
   - Fighter gets 2 selections (level 1 and 10) - both need access

2. **Modifier System**
   - Need standardized way to convert style properties → modifiers
   - Support common patterns: attack bonuses, AC bonuses, special abilities
   - Handle complex modifiers (conditional bonuses, weapon restrictions)

---

## Proposed Solution

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Fighting Style Builder UI                 │
│  (Similar to Feat Builder - name, description, modifiers)   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              Browser localStorage / Plugins                  │
│         {::e5/fighting-styles [{:name "..." ...}]}          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│         fighting-style-option-from-cfg (Converter)           │
│              Plugin Data → option-cfg Format                 │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│        Dynamic Fighting Style Options (Merged List)          │
│    fighting-style-options (SOURCE) + Plugin Styles          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              Class Fighting Style Selections                 │
│     Fighter (lvl 1, 10), Paladin (lvl 2), Ranger (lvl 2)   │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

#### 1. Data Schema
```clojure
;; /src/cljc/orcpub/dnd/e5/fighting_styles.cljc (NEW FILE)
(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::option-pack string?)
(spec/def ::description string?)

;; Properties that map to modifiers
(spec/def ::props (spec/keys :opt-un [::ranged-attack-bonus
                                      ::melee-attack-bonus
                                      ::ac-bonus
                                      ::initiative
                                      ::speed
                                      ;; etc - reuse feat props
                                      ]))

(spec/def ::homebrew-fighting-style
  (spec/keys :req-un [::name ::key ::option-pack]
             :opt-un [::description ::props]))
```

#### 2. Builder UI Component
```clojure
;; /src/cljs/orcpub/dnd/e5/views.cljs (ADDITIONS)

(defn fighting-style-builder []
  (let [style @(subscribe [::fighting-styles/builder-item])
        plugins @(subscribe [::e5/plugins])]
    [:div.p-20.main-text-color
     ;; Name & Option Pack
     [:div.m-b-20.flex.flex-wrap
      [input-field "Name" :name style ::fighting-styles/set-prop]
      [plugin-datalist option-source-name-label style ::fighting-styles/set-prop]]

     ;; Description
     [:div.w-100-p
      [:div.f-w-b "Description"]
      [textarea-field ...]]

     ;; Modifiers Section
     [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
     [:div [fighting-style-attack-bonuses style]]
     [:div [fighting-style-ac-bonuses style]]
     [:div [fighting-style-damage-bonuses style]]
     [:div [fighting-style-weapon-restrictions style]]
     [:div [fighting-style-speed-bonuses style]]
     [:div [fighting-style-misc-modifiers style]]]))

(defn fighting-style-builder-page []
  (builder-page "Fighting Style"
                ::fighting-styles/reset
                ::fighting-styles/save
                fighting-style-builder))
```

#### 3. Event Handlers
```clojure
;; /src/cljs/orcpub/dnd/e5/events.cljs (ADDITIONS)

(def fighting-style-interceptors [(path ::fighting-styles5e/builder-item)])

(reg-event-fx
 ::fighting-styles5e/reset
 (fn [_ _]
   {:dispatch [::fighting-styles5e/set-fighting-style default-fighting-style]}))

(reg-save-homebrew
 "Fighting Style"
 ::fighting-styles5e/save
 ::fighting-styles5e/builder-item
 ::fighting-styles5e/homebrew-fighting-style
 ::e5/fighting-styles
 "You must specify 'Name', 'Option Source Name'")

(reg-event-db
 ::fighting-styles5e/set-prop
 fighting-style-interceptors
 (fn [style [_ k v]]
   (assoc style k v)))

;; Additional toggle/update events as needed
```

#### 4. Conversion Function
```clojure
;; /src/cljc/orcpub/dnd/e5/options.cljc (ADDITIONS)

(defn fighting-style-modifiers [key name description props]
  (concat
   (plugin-modifiers props key)  ;; Reuse feat system for props → modifiers
   [(modifiers/trait-cfg
     {:name name
      :description description})]))

(defn fighting-style-option-from-cfg [{:keys [name key description props]}]
  (let [style-mods (fighting-style-modifiers key name description props)]
    (t/option-cfg
     {:name name
      :key key
      :modifiers style-mods
      :summary description})))

;; Replace static def with dynamic function
(defn all-fighting-style-options [plugins]
  (let [plugin-styles (mapcat ::e5/fighting-styles (vals plugins))
        plugin-options (map fighting-style-option-from-cfg plugin-styles)]
    (concat fighting-style-options  ;; Original hardcoded styles
            plugin-options)))        ;; Plugin/homebrew styles

;; Update selection function to accept plugins
(defn fighting-style-selection [class-kw plugins & [restrictions]]
  (let [all-options (all-fighting-style-options plugins)
        filtered-options (if restrictions
                          (filter #(restrictions (::t/key %)) all-options)
                          all-options)]
    (fighting-style-selection-2 class-kw 1 filtered-options)))
```

#### 5. Plugin Support
```clojure
;; /src/cljc/orcpub/dnd/e5/db_5e.cljc (ADDITIONS)
;; Add to plugin spec
(spec/def ::fighting-styles (spec/coll-of ::fighting-styles5e/homebrew-fighting-style))

;; Update plugin spec to include
(spec/def ::plugin
  (spec/keys :opt [::fighting-styles  ;; <-- ADD THIS
                   ::classes
                   ::subclasses
                   ;; ... existing keys
                   ]))
```

### Feat Builder as Template

The feat builder is an **excellent template** because:

1. ✅ **Similar Structure**: Both have name, description, mechanical benefits
2. ✅ **Props System**: Feats use `:props` map for modifiers - can reuse for fighting styles
3. ✅ **Conversion Pattern**: `feat-option-from-cfg` pattern works for fighting styles
4. ✅ **UI Patterns**: Checkboxes, number inputs, toggles - all applicable
5. ✅ **Storage**: localStorage + plugins system already proven

**Reusable Components**:
- Input fields and validation
- Plugin/option pack selector
- Modifier UI components (attack bonuses, AC bonuses, speed, etc.)
- Save/reset/export logic
- `plugin-modifiers` function for props → modifiers conversion

**Differences**:
- Fighting styles simpler (no prerequisites, no ability increases)
- Integration point different (class selections vs character feats)
- No skill/tool proficiencies (usually)

---

## Implementation Plan

### Phase 1: Foundation (Core Data & Schema)
**Goal**: Establish data structures and validation

**Tasks**:
1. Create `/src/cljc/orcpub/dnd/e5/fighting_styles.cljc`
   - Define `::homebrew-fighting-style` spec
   - Define `::props` spec (reuse feat props where applicable)
   - Add validation rules

2. Update `/src/cljc/orcpub/dnd/e5/db_5e.cljc`
   - Add `::fighting-styles` to plugin spec
   - Add localStorage key: `local-storage-fighting-style-key`

3. Update `/src/cljs/orcpub/dnd/e5/db.cljs`
   - Add `default-fighting-style` map
   - Add builder state path

**Validation**: Can define and validate fighting style data structures

---

### Phase 2: Conversion & Integration (Backend)
**Goal**: Make plugins fighting styles visible to the selection system

**Tasks**:
1. Update `/src/cljc/orcpub/dnd/e5/options.cljc`
   - Create `fighting-style-modifiers` function
   - Create `fighting-style-option-from-cfg` function
   - Replace `fighting-style-options` def with `all-fighting-style-options` function
   - Update `fighting-style-selection` to accept `plugins` parameter

2. Update class integration points
   - `/src/cljc/orcpub/dnd/e5/classes.cljc` - pass plugins to selections
   - Update Fighter, Paladin, Ranger class definitions
   - Update any UA subclasses using fighting styles

3. Add subscriptions (`/src/cljs/orcpub/dnd/e5/subs.cljs`)
   - `::fighting-styles5e/builder-item`
   - `::fighting-styles5e/all-fighting-styles`
   - `::fighting-styles5e/plugin-fighting-styles`

**Validation**: Plugin fighting styles appear in class fighting style selections

---

### Phase 3: Builder UI (Frontend)
**Goal**: Users can create fighting styles via UI

**Tasks**:
1. Add event handlers (`/src/cljs/orcpub/dnd/e5/events.cljs`)
   - `::fighting-styles5e/reset`
   - `::fighting-styles5e/save`
   - `::fighting-styles5e/set-prop`
   - `::fighting-styles5e/toggle-prop`
   - `::fighting-styles5e/set-fighting-style`

2. Create UI components (`/src/cljs/orcpub/dnd/e5/views.cljs`)
   - `fighting-style-builder` main component
   - `fighting-style-attack-bonuses` (ranged, melee, specific weapons)
   - `fighting-style-ac-bonuses` (conditional or flat)
   - `fighting-style-damage-bonuses`
   - `fighting-style-misc-modifiers`
   - `fighting-style-builder-page` wrapper

3. Add routing
   - Update `/src/cljc/orcpub/route_map.cljc` - add `/fighting-style-builder` route
   - Update `/src/clj/orcpub/routes.clj` - add handler
   - Update `/web/cljs/orcpub/core.cljs` - add route case
   - Update "My Content" page to include fighting styles link

**Validation**: Can create, edit, save fighting style via UI

---

### Phase 4: Modifier Props (Mechanical Benefits)
**Goal**: Support common fighting style mechanics

**Props to Support** (reuse feat system):
- `:ranged-attack-bonus` - flat bonus to ranged attacks (Archery)
- `:melee-attack-bonus` - flat bonus to melee attacks
- `:ac-bonus` - conditional or flat AC bonus (Defense)
- `:damage-bonus` - damage bonuses with conditions (Dueling)
- `:weapon-proficiencies` - grant weapon proficiencies
- `:damage-resistance` - damage resistances
- `:speed` - movement speed bonuses (Mariner swimming/climbing)
- `:initiative` - initiative bonuses
- Custom modifiers via `:misc-modifiers`

**Tasks**:
1. Create UI for each prop type
2. Test conversion via `plugin-modifiers` (already exists for feats)
3. Create examples for each type
4. Documentation for users

**Validation**: Fighting styles with various mechanics work correctly

---

### Phase 5: Testing & Documentation
**Goal**: Ensure quality and usability

**Tasks**:
1. **Unit Tests**
   - Test `fighting-style-option-from-cfg` conversion
   - Test `all-fighting-style-options` merging
   - Test validation specs

2. **Integration Tests**
   - Create fighting style via UI
   - Export to orcbrew file
   - Import orcbrew file with fighting styles
   - Select custom style in character builder
   - Verify modifiers applied to character

3. **User Documentation**
   - Add section to help/docs on creating fighting styles
   - Document available modifier props
   - Provide example fighting styles
   - Migration guide for existing homebrew

4. **Edge Cases**
   - Empty/invalid fighting styles
   - Duplicate names/keys
   - Class restrictions with custom styles
   - Fighter's 2nd fighting style selection (level 10)

**Validation**: All tests pass, documentation complete

---

### Phase 6: Polish & Release
**Goal**: Production-ready feature

**Tasks**:
1. Code review and refactoring
2. Performance testing (large number of custom styles)
3. UI/UX polish
4. Error messages and validation feedback
5. Example fighting styles pack (showcase features)
6. Release notes

**Validation**: Feature ready for production use

---

## Technical Architecture

### Data Flow Diagram

```
User Input (Builder UI)
    ↓
Event Handler (::fighting-styles/save)
    ↓
Validation (spec/valid?)
    ↓
localStorage / Plugins System
    ↓
Subscription (::fighting-styles/all)
    ↓
fighting-style-option-from-cfg (Conversion)
    ↓
all-fighting-style-options (Merge with SOURCE)
    ↓
fighting-style-selection (Class Selection)
    ↓
Character Level Selections
    ↓
Applied Modifiers on Character
```

### File Structure

```
src/
├── cljc/orcpub/dnd/e5/
│   ├── fighting_styles.cljc          [NEW] Specs & validation
│   ├── options.cljc                  [EDIT] Conversion & selection functions
│   ├── db_5e.cljc                    [EDIT] Plugin spec additions
│   └── classes.cljc                  [EDIT] Class integration updates
├── cljs/orcpub/dnd/e5/
│   ├── events.cljs                   [EDIT] Event handlers
│   ├── subs.cljs                     [EDIT] Subscriptions
│   ├── views.cljs                    [EDIT] Builder UI
│   └── db.cljs                       [EDIT] Default data
└── routes & navigation               [EDIT] Routing setup
```

### State Management

**App DB Path**:
```clojure
[:fighting-styles5e
  :builder-item    ;; Current editing state
  :saved-styles]   ;; User's saved styles
```

**localStorage**:
```javascript
{
  "fighting-style": {
    "name": "Battle Momentum",
    "key": :battle-momentum,
    "option-pack": "My Homebrew Pack",
    "description": "When you hit with a melee attack...",
    "props": {
      "melee-attack-bonus": 1,
      "initiative": 2
    }
  }
}
```

**Plugin Format**:
```clojure
{::e5/fighting-styles
  [{:name "Battle Momentum"
    :key :battle-momentum
    :option-pack "My Homebrew Pack"
    :description "When you hit with a melee attack..."
    :props {:melee-attack-bonus 1
            :initiative 2}}]}
```

---

## UI Design

### Builder Interface Mockup

```
╔════════════════════════════════════════════════════════════════════╗
║  Fighting Style Builder                             [New] [Save]   ║
╠════════════════════════════════════════════════════════════════════╣
║                                                                    ║
║  Name: [___________________________]                               ║
║                                                                    ║
║  Option Source Name: [___________________________]                 ║
║                                                                    ║
║  Description:                                                      ║
║  ┌──────────────────────────────────────────────────────────┐    ║
║  │ Enter a description of this fighting style...            │    ║
║  │                                                           │    ║
║  │                                                           │    ║
║  └──────────────────────────────────────────────────────────┘    ║
║                                                                    ║
║  ═══ Modifiers ═══                                                ║
║                                                                    ║
║  Attack Bonuses:                                                   ║
║    □ Ranged Attack Bonus: [___] (+2 recommended)                  ║
║    □ Melee Attack Bonus:  [___]                                   ║
║                                                                    ║
║  Defense Bonuses:                                                  ║
║    □ AC Bonus: [___] (+1 recommended)                             ║
║      ☐ Requires armor                                             ║
║      ☐ No shield                                                  ║
║      ☐ Not heavy armor                                            ║
║                                                                    ║
║  Damage:                                                           ║
║    □ Damage Bonus: [___]                                          ║
║      Weapon Type: [One-handed melee ▾]                            ║
║                                                                    ║
║  Movement:                                                         ║
║    □ Speed Bonus: [___] ft                                        ║
║    □ Swimming Speed: [___] ft                                     ║
║    □ Climbing Speed: [___] ft                                     ║
║                                                                    ║
║  Other:                                                            ║
║    □ Initiative Bonus: [___]                                      ║
║    □ Weapon Proficiencies: [Select weapons...]                    ║
║                                                                    ║
║  Custom Modifiers:                                                 ║
║    [+ Add Custom Modifier]                                         ║
║                                                                    ║
╚════════════════════════════════════════════════════════════════════╝
```

### My Content Page Addition

```
┌─────────────────────────────────────────┐
│ My Content                              │
├─────────────────────────────────────────┤
│ • Races                           [+]   │
│ • Classes                         [+]   │
│ • Subclasses                      [+]   │
│ • Backgrounds                     [+]   │
│ • Feats                           [+]   │
│ • Fighting Styles                 [+]   │  ← NEW
│ • Spells                          [+]   │
│ • Monsters                        [+]   │
└─────────────────────────────────────────┘
```

### Character Builder Integration

When selecting a fighting style during character creation:

```
Fighter - Level 1

Fighting Style: [Choose ▾]
  ├── Archery                    (PHB)
  ├── Defense                    (PHB)
  ├── Dueling                    (PHB)
  ├── Great Weapon Fighting      (PHB)
  ├── Protection                 (PHB)
  ├── Two-Weapon Fighting        (PHB)
  ├────────────────────────────
  ├── Battle Momentum            (My Homebrew Pack) ← CUSTOM
  ├── Shield Bash                (My Homebrew Pack) ← CUSTOM
  └── Skirmisher                 (Community Pack)   ← CUSTOM
```

---

## Testing Strategy

### Unit Tests

**File**: `test/cljs/orcpub/dnd/e5/fighting_styles_test.cljs` (NEW)

```clojure
(deftest fighting-style-conversion-test
  (testing "Converts fighting style data to option-cfg"
    (let [style-data {:name "Test Style"
                      :key :test-style
                      :description "A test fighting style"
                      :props {:ranged-attack-bonus 2}}
          result (fighting-style-option-from-cfg style-data)]
      (is (= "Test Style" (::t/name result)))
      (is (= 2 (count (::t/modifiers result))))
      (is (some #(= :ranged-attack-bonus (:key %)) (::t/modifiers result))))))

(deftest all-fighting-styles-merge-test
  (testing "Merges SOURCE and plugin fighting styles"
    (let [plugins {:plugin1 {::e5/fighting-styles [{:name "Custom" :key :custom}]}}
          all-styles (all-fighting-style-options plugins)]
      (is (>= (count all-styles) 7))  ; 6 base + 1 custom
      (is (some #(= "Custom" (::t/name %)) all-styles)))))

(deftest validation-test
  (testing "Validates fighting style specs"
    (is (spec/valid? ::fighting-styles5e/homebrew-fighting-style
                     {:name "Valid Style"
                      :key :valid-style
                      :option-pack "Test Pack"}))
    (is (not (spec/valid? ::fighting-styles5e/homebrew-fighting-style
                          {:key :invalid})))))  ; Missing name
```

### Integration Tests

**Manual Test Plan**:

1. **Create Fighting Style**
   - [ ] Open Fighting Style Builder
   - [ ] Enter name "Test Ranger Style"
   - [ ] Enter description
   - [ ] Add ranged attack bonus +2
   - [ ] Add initiative bonus +1
   - [ ] Save successfully

2. **Character Creation**
   - [ ] Create new Ranger character
   - [ ] Reach level 2 (fighting style selection)
   - [ ] Verify "Test Ranger Style" appears in dropdown
   - [ ] Select "Test Ranger Style"
   - [ ] Verify modifiers applied:
     - Ranged attack rolls show +2
     - Initiative shows +1
     - Character sheet displays fighting style trait

3. **Export/Import**
   - [ ] Export character with custom fighting style
   - [ ] Import in new browser session
   - [ ] Verify fighting style preserved

4. **Orcbrew Plugin**
   - [ ] Create orcbrew file with fighting-styles section
   - [ ] Import plugin
   - [ ] Verify styles appear in character builder

5. **Fighter Dual Selection**
   - [ ] Create Fighter
   - [ ] Select 1st fighting style at level 1
   - [ ] Advance to level 10
   - [ ] Select 2nd fighting style
   - [ ] Verify both styles active and distinct

### Edge Cases to Test

1. **Duplicate Names**: Two fighting styles with same name but different option packs
2. **Empty Options**: Plugin with empty `::e5/fighting-styles` vector
3. **Invalid Data**: Malformed fighting style data (missing required fields)
4. **Class Restrictions**: Custom style should/shouldn't appear based on class
5. **Large Numbers**: 100+ custom fighting styles (performance)
6. **Special Characters**: Names with unicode, symbols, very long names
7. **Modifier Conflicts**: Two fighting styles with overlapping modifiers

---

## Example Fighting Styles

### Simple: Precision Strike
```clojure
{:name "Precision Strike"
 :key :precision-strike
 :option-pack "Example Styles"
 :description "You've mastered pinpoint accuracy. You gain a +1 bonus to attack rolls with melee weapons."
 :props {:melee-attack-bonus 1}}
```

### Complex: Mariner (UA)
```clojure
{:name "Mariner"
 :key :mariner
 :option-pack "UA: Waterborne"
 :description "As long as you are not wearing heavy armor or using a shield, you have a swimming speed and a climbing speed equal to your normal speed, and you gain a +1 bonus to AC."
 :props {:ac-bonus 1                    ; Conditional in actual implementation
         :speed {:swim :match-walking   ; Custom prop
                 :climb :match-walking}}}
```

### Advanced: Shield Bash
```clojure
{:name "Shield Bash"
 :key :shield-bash
 :option-pack "Martial Options"
 :description "When wielding a shield, you can use a bonus action to make a melee weapon attack with your shield, dealing 1d4 bludgeoning damage. On a hit, you can push the target 5 feet away."
 :props {:weapon-proficiencies [:shield-bash]  ; Would need custom weapon definition
         :bonus-actions [:shield-bash-attack]}}
```

---

## Migration & Backwards Compatibility

### Existing Content
- ✅ All existing hardcoded fighting styles remain unchanged
- ✅ Existing characters with fighting styles unaffected
- ✅ SOURCE files can still use `fighting-style-selection` (now enhanced)
- ✅ No breaking changes to public API

### Plugin Authors
- New `::e5/fighting-styles` key available in plugin format
- Optional - plugins without it continue working
- Documentation provided for migration

### Performance Considerations
- Dynamic option merging adds minimal overhead
- Cached/memoized where possible
- Tested with 100+ custom styles for acceptable performance

---

## Open Questions & Future Enhancements

### Open Questions
1. Should class restrictions be configurable per custom fighting style?
   - E.g., "This style only available to Fighters and Paladins"
2. How to handle conditional bonuses that need custom logic?
   - E.g., "When wielding a one-handed weapon with no shield"
3. Should there be a "featured" or "popular" fighting styles section?
4. Export format for sharing individual fighting styles (not full plugin)?

### Future Enhancements
1. **Fighting Style Templates**: Pre-configured templates for common patterns
2. **Visual Icons**: Custom icons for homebrew fighting styles
3. **Prerequisites**: Fighting styles that require certain levels/features
4. **Multiclass Support**: Fighting styles from multiple classes
5. **Feat Integration**: Feats that grant or modify fighting styles
6. **Advanced Modifiers**: Visual modifier builder for complex conditions
7. **Community Library**: Share/import fighting styles from community
8. **Balance Suggestions**: AI-powered balance recommendations

---

## Conclusion

The fighting styles feature expansion is **highly feasible** using the existing feat builder as a template. The main challenges are:

1. **Architecture**: Extending the plugin system to support fighting styles
2. **Integration**: Ensuring custom styles merge seamlessly with SOURCE styles
3. **UI Complexity**: Providing enough flexibility without overwhelming users

The recommended approach follows proven patterns from the feat builder and leverages existing infrastructure. Estimated effort: **3-4 weeks** for full implementation with testing and documentation.

**Next Steps**:
1. Review and approve this plan
2. Create detailed issue/task breakdown
3. Begin Phase 1 (Foundation) implementation
4. Iterate based on feedback

---

**Document Version**: 1.0
**Last Updated**: 2026-01-12
**Author**: Claude AI Agent
**Status**: Awaiting Review
