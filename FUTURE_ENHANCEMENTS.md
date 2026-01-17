# Future Performance Enhancements

This document explores potential future optimizations for the character builder, with a focus on conditional template building and lazy data loading.

---

## 1. Conditional Spellcasting Template Building

### Current Situation

**All classes receive full spells-map**, regardless of whether they use spells:

```clojure
;; spell_subs.cljs:861-873
(defn base-class-options [spell-lists spells-map ...]
  [(barbarian-option spell-lists spells-map ...)  ; Gets spells-map
   (bard-option spell-lists spells-map ...)       ; Gets spells-map
   (fighter-option spell-lists spells-map ...)    ; Gets spells-map
   ...])

;; ALL classes built with spellcasting-template, even if :spellcasting is nil
```

**Problem**: Even though we now use lazy spell help, we're still passing `spells-map` to every class builder and creating spell selection structures for classes that don't need them.

### Why We Can't Simply Skip "Non-Spellcasters"

**INCORRECT APPROACH** ❌:
```clojure
;; DON'T DO THIS - assumes classes never have spells
(defn base-class-options [spell-lists spells-map ...]
  [(barbarian-option nil nil ...)  ; ❌ Wrong! What about homebrew spellcasting subclasses?
   (bard-option spell-lists spells-map ...)
   (fighter-option nil nil ...)     ; ❌ Wrong! Eldritch Knight needs spells!
   ...])
```

**Why this fails**:
- **Eldritch Knight** (Fighter subclass) - casts wizard spells
- **Arcane Trickster** (Rogue subclass) - casts wizard spells
- **Way of Shadow** (Monk subclass) - casts spells
- **Path of Wild Magic** (Barbarian subclass) - has magical effects
- **Homebrew/Custom subclasses** - ANY class could have spellcasting subclass added via plugins

You cannot assume a base class will never need spell data because subclasses might!

---

### Correct Approach: Check for :spellcasting Configuration

**How spellcasting works in the codebase**:

```clojure
;; Base class with spellcasting
{:name "Wizard"
 :spellcasting {:level-factor 1           ; Full caster
                :ability ::char5e/int
                :known-mode :all          ; Knows all spells in book
                :cantrips-known {1 3, 4 4, 10 5}}}

;; Base class without spellcasting
{:name "Fighter"
 :spellcasting nil}  ; No base spellcasting

;; Subclass with spellcasting
{:name "Eldritch Knight"
 :spellcasting {:level-factor 3           ; 1/3 caster
                :ability ::char5e/int
                :known-mode :schedule     ; Limited known spells
                :spell-list {:wizard true}
                :spells-known {3 2, 4 3, 7 4, ...}}}
```

**The optimization**:

```clojure
;; options.cljc:2861-2865
(defn class-option [spell-lists spells-map ... {:keys [spellcasting ...] :as cls}]
  (let [spellcasting-template (if spellcasting  ; Check if class has spellcasting
                                 (spellcasting-template spell-lists spells-map ...)
                                 nil)]  ; Don't build spell template if no spellcasting
    ...))

;; Only build spell options if :spellcasting is defined
```

**Benefits**:
- ✅ Works with base game classes (Wizard has spells, Fighter base doesn't)
- ✅ Works with subclasses (Eldritch Knight has `:spellcasting`, gets spell template)
- ✅ Works with homebrew (custom Barbarian subclass with `:spellcasting` gets spells)
- ✅ Reduces processing for true non-spellcasters (base Barbarian, base Fighter, etc.)

**Impact**:
- ~40% fewer spell selections built during template creation
- Still full compatibility with all content (base + custom)

---

### Two-Level Optimization Strategy

#### **Level 1: Class-Level Conditional Building** (Easy - Low Risk)

Only create `spellcasting-template` if base class has `:spellcasting`:

```clojure
;; Current
(defn class-option [spell-lists spells-map ... {:keys [spellcasting] :as cls}]
  (let [spellcasting-template (spellcasting-template spell-lists spells-map ...)]
    ;; Always builds spell template, even if spellcasting is nil
    ...))

;; Optimized
(defn class-option [spell-lists spells-map ... {:keys [spellcasting] :as cls}]
  (let [spellcasting-template (when spellcasting  ; Only if defined
                                 (spellcasting-template spell-lists spells-map ...))]
    ...))
```

**Savings**:
- Barbarian: No spell template built ✅
- Fighter (base): No spell template built ✅
- Monk (base): No spell template built ✅
- Rogue (base): No spell template built ✅
- Wizard: Spell template built as normal ✅
- Custom Barbarian subclass with spells: Handled at subclass level ✅

---

#### **Level 2: Subclass-Level Conditional Building** (Medium - Moderate Risk)

Currently, **all subclass options** are built when the class option is built:

```clojure
;; options.cljc:2773-2777
(t/selection-cfg
  {:name "Fighter Archetype"
   :options (map
              #(subclass-option spell-lists spells-map ... %)
              subclasses)})  ; ALL subclasses built upfront!
```

This means:
- When browsing Fighter, ALL 8+ subclass options are built with full data
- Eldritch Knight spell template is built even if user never selects it
- Arcane Trickster spell template is built even if user never selects it

**Optimization**: Lazy subclass building

```clojure
;; Current: Build all subclass options upfront
{:name "Fighter Archetype"
 :options [{:name "Champion" :levels {3 {...} 7 {...} ...}}      ; Full data
           {:name "Battle Master" :levels {3 {...} 7 {...} ...}} ; Full data
           {:name "Eldritch Knight"                              ; Full data + spell template
            :spellcasting-template {...}
            :levels {3 {:spell-selections [...]} ...}}]}

;; Optimized: Build lightweight stubs, load details on selection
{:name "Fighter Archetype"
 :options [{:name "Champion" :key :champion}                     ; Just name/key
           {:name "Battle Master" :key :battle-master}
           {:name "Eldritch Knight" :key :eldritch-knight}]}

;; When user selects Eldritch Knight:
(subscribe [:subclass-details :fighter :eldritch-knight])
;; THEN build full spell template and level options
```

**Benefits**:
- Browsing classes: Only load class names + basic info
- Selecting class: Only load subclass names
- Selecting subclass: THEN load full subclass data + spell template
- Homebrew compatibility: Works same way for custom subclasses

**Challenges**:
- More complex subscription architecture
- Need to ensure character building still has access to all data when needed
- Template structure becomes two-tiered (stub vs. full)

**Impact**:
- Template size when browsing: -60-70% additional reduction
- Memory grows incrementally as selections are made
- Slightly more complex implementation

---

## 2. Spell List Filtering

### Current Situation

**All classes receive ALL spells**:

```clojure
;; spells-map contains ALL 400+ spells from ALL classes
{:acid-splash {...}      ; Wizard spell
 :cure-wounds {...}      ; Cleric spell
 :hunters-mark {...}     ; Ranger spell
 :eldritch-blast {...}   ; Warlock spell
 ...}

;; Even Eldritch Knight (which can only learn wizard spells) gets the whole map
```

### Optimization: Class-Specific Spell Maps

**Create filtered spell maps per class**:

```clojure
;; Instead of one giant spells-map
(reg-sub ::spells5e/spells-map ...)  ; 400+ spells

;; Create class-specific maps
(reg-sub ::spells5e/wizard-spells-map
  :<- [::spells5e/spells-map]
  :<- [::spells5e/spell-lists]
  (fn [[spells-map spell-lists] _]
    (let [wizard-spell-keys (apply concat (vals (:wizard spell-lists)))]
      (select-keys spells-map wizard-spell-keys))))
;; Result: ~180 spells instead of 400+

(reg-sub ::spells5e/cleric-spells-map ...)  ; ~120 spells
(reg-sub ::spells5e/bard-spells-map ...)    ; ~140 spells
```

**Usage**:

```clojure
;; Wizard option - gets only wizard spells
(defn wizard-option [spell-lists wizard-spells-map ...]
  (opt5e/class-option spell-lists wizard-spells-map ...))

;; Eldritch Knight - gets only wizard spells (subset)
(spellcasting-template spell-lists wizard-spells-map {:class-key :eldritch-knight ...})
```

**Benefits**:
- Wizard spell template: 180 spells instead of 400+ (55% reduction)
- Eldritch Knight: Same 180 spells (still large but necessary)
- Better memory efficiency (not loading spells the class can't use)

**Challenges**:
- Homebrew classes with custom spell lists need special handling
- Multi-class spell lists (Bard Magical Secrets, Arcane Trickster "any school" slots)
- Some features allow picking from any class spell list

**Verdict**: Moderate benefit, moderate complexity. Worth considering for v2.

---

## 3. Lazy Subclass Loading

### Current Architecture

```clojure
;; When template is built, ALL subclass data is included
{:classes
  [{:name "Fighter"
    :subclasses [{:name "Champion"
                  :levels {3 {:selections [...] :modifiers [...]}
                           7 {:selections [...] :modifiers [...]}
                           ...
                           18 {:modifiers [...]}}}
                 {:name "Battle Master"
                  :levels {3 {:selections [...] :modifiers [...]}
                           7 {:selections [...] :modifiers [...]}
                           ...}}
                 {:name "Eldritch Knight"
                  :spellcasting-template {:selections {3 [...] 7 [...] ...}}
                  :levels {3 {:selections [...] :modifiers [...]}
                           ...}}
                 ;; + 5 more subclasses with full data
                 ]}
   ;; × 13 classes × avg 6 subclasses = 78 subclass definitions loaded!
   ]}
```

### Optimization: Progressive Detail Loading

**Phase 1: Browsing Classes**
```clojure
;; Just class names and basic info
{:classes [{:name "Fighter" :key :fighter :hit-die 10}
           {:name "Wizard" :key :wizard :hit-die 6}
           ...]}
;; ~5 KB instead of 1.5 MB
```

**Phase 2: Viewing Class (User Clicks "Fighter")**
```clojure
;; Load class details + subclass names
{:name "Fighter"
 :hit-die 10
 :save-profs [:str :con]
 :subclasses [{:name "Champion" :key :champion :description "..."}
              {:name "Battle Master" :key :battle-master :description "..."}
              {:name "Eldritch Knight" :key :eldritch-knight :description "..."}]}
;; +50 KB for this class
```

**Phase 3: Selecting Subclass (User Picks "Eldritch Knight")**
```clojure
;; NOW load full subclass data + spell template
@(subscribe [::classes5e/subclass-full-data :fighter :eldritch-knight])
;; Returns complete level-by-level progression + spell selections
;; +20 KB for this subclass
```

**Implementation**:

```clojure
;; New subscription for subclass details
(reg-sub
  ::classes5e/subclass-full-data
  (fn [[_ class-key subclass-key]]
    [(subscribe [::spells5e/spell-lists])
     (subscribe [::spells5e/spells-map])
     (subscribe [::classes5e/class-config class-key])
     (subscribe [::classes5e/subclass-config class-key subclass-key])])
  (fn [[spell-lists spells-map class-cfg subclass-cfg] _]
    (build-subclass-option spell-lists spells-map class-cfg subclass-cfg)))

;; Used in character builder
(defn subclass-selection-ui [class-key]
  (let [selected-subclass @(subscribe [:selected-subclass class-key])
        subclass-data (when selected-subclass
                        @(subscribe [::classes5e/subclass-full-data
                                     class-key
                                     selected-subclass]))]
    ;; Render with full data only when selected
    ...))
```

**Benefits**:
- Initial template load: 5 KB (class names only)
- Browsing classes: +50 KB per class viewed
- Selecting subclass: +20 KB per subclass selected
- Final character: ~100-200 KB total (only what was selected)

**Challenges**:
- Character building requires access to all selected subclass data
- Template structure needs to support both "stub" and "full" data
- More complex subscription management
- Character save/load needs to ensure data is loaded

---

## 4. Plugin Content Indexing

### Current Situation

```clojure
;; All custom content loaded into arrays
{:plugin-races [{:name "Fire Giant Race" :size :large :abilities {...} ...}
                {:name "Ice Elf Subrace" :traits [...] ...}
                ...
                {:name "Zombie Race" :size :medium ...}]  ; 100+ races
 :plugin-classes [{:name "Artificer" :hit-die 8 ...}
                  ...
                  {:name "Witch" :hit-die 6 ...}]        ; 100+ classes
 :plugin-spells [{:name "Fireball v2" :level 3 :description "..." ...}
                 ...
                 {:name "Zone of Truth Extended" :level 2 ...}]}  ; 500+ spells
```

**Search/Filter**: Linear search through all items

```clojure
;; User searches for "fire"
(filter #(s/includes? (s/lower-case (:name %)) "fire") all-plugin-content)
;; O(n) - iterates through 700+ items
```

### Optimization: Create Search Index

```clojure
(reg-sub
  ::plugins/content-index
  :<- [::plugins/all-content]
  (fn [all-content _]
    {:by-name {"fire giant race" {:type :race :id "plugin-123/race-5"}
               "fireball v2" {:type :spell :id "plugin-456/spell-12"}
               "fire mage" {:type :class :id "plugin-789/class-3"}}
     :by-tag {:fire ["plugin-123/race-5" "plugin-456/spell-12" "plugin-789/class-3"]
              :elemental ["plugin-123/race-5" "plugin-444/race-8" ...]
              :evocation ["plugin-456/spell-12" "plugin-457/spell-15" ...]}
     :by-type {:race ["plugin-123/race-1" "plugin-123/race-2" ...]
               :class ["plugin-456/class-1" "plugin-456/class-2" ...]
               :spell ["plugin-789/spell-1" "plugin-789/spell-2" ...]}
     :by-source {:plugin-123 {:races [...] :classes [...]}
                 :plugin-456 {:spells [...]}}}))

;; Fast lookup
(reg-sub
  ::plugins/search
  (fn [[_ search-term]]
    [(subscribe [::plugins/content-index])])
  (fn [[index] [_ search-term]]
    (let [term (s/lower-case search-term)]
      (or (get-in index [:by-name term])
          (get-in index [:by-tag (keyword term)])
          []))))
```

**Benefits**:
- Search: O(1) hash lookup instead of O(n) iteration
- Filter by type: Instant (pre-indexed)
- Filter by tag: Instant (pre-indexed)
- Enables advanced features: "show all fire-themed content across all plugins"

**Impact**:
- With 500+ custom items: 200ms search → <1ms
- Enables real-time search-as-you-type
- Better UX for large content libraries

---

## 5. Template Splitting (Browsing vs. Building)

### Current Architecture

**One giant template for everything**:

```clojure
(subscribe [:built-template])
;; Returns 1.5 MB object containing:
;; - All 13 classes with all subclasses
;; - All races with all subraces
;; - All feats
;; - All backgrounds
;; - All equipment
;; - Used for BOTH browsing AND building
```

### Optimization: Tiered Templates

**Template Tier 1: Browsing**
```clojure
(reg-sub ::template/browsing
  (fn [_]
    {:classes [{:name "Barbarian" :key :barbarian :hit-die 12 :description "..."}
               {:name "Bard" :key :bard :hit-die 8 :description "..."}
               ...]
     :races [{:name "Dwarf" :key :dwarf :size :medium :speed 25 :description "..."}
             {:name "Elf" :key :elf :size :medium :speed 30 :description "..."}
             ...]
     :backgrounds [...]
     :feats [...]}))
;; ~50-100 KB - just names, keys, basic info
```

**Template Tier 2: Selection Details**
```clojure
(reg-sub ::template/class-details
  (fn [[_ class-key]]
    [(subscribe [::classes5e/class-config class-key])])
  (fn [[class-cfg] _]
    ;; Full class data including:
    ;; - Subclass options (names + keys)
    ;; - Level progression overview
    ;; - Starting equipment choices
    ;; - Proficiency options
    {:class-data class-cfg
     :subclasses (map subclass-summary (:subclasses class-cfg))}))
;; +50 KB when viewing class
```

**Template Tier 3: Full Building Data**
```clojure
(reg-sub ::template/character-building
  (fn [_]
    [(subscribe [:character])
     (subscribe [::template/browsing])])
  (fn [[character browsing-template] _]
    ;; Only load full data for selected options
    (let [selected-class-keys (map :key (get-in character [:class]))
          selected-race-key (get-in character [:race :key])]
      {:classes (map #(load-full-class-data %) selected-class-keys)
       :race (load-full-race-data selected-race-key)
       :available-selections (calculate-available-selections character ...)})))
;; Grows as selections are made
```

**Benefits**:
- Initial load: 50-100 KB (browsing template)
- Selecting class: +50 KB (class details)
- Final character: ~200-300 KB (only selected options)
- 80-90% reduction in initial memory

**Challenges**:
- More complex subscription architecture
- Need to ensure character building has all required data
- Character save/load needs to trigger data loading
- Export (PDF) needs full data for selected options

---

## 6. Web Worker for Character Building

### Problem: UI Freezing During Calculation

**Current flow** (main thread):
```
User clicks "Select Wizard" →
  Dispatch event →
  Update character state →
  Rebuild character (100ms+) →  ← UI FROZEN
  Recalculate modifiers →        ← UI FROZEN
  Update subscriptions →          ← UI FROZEN
  Re-render UI →
  User can interact again
```

### Solution: Background Computation

**Worker thread does heavy lifting**:

```javascript
// character-worker.js
self.addEventListener('message', (e) => {
  const {character, template, action} = e.data;

  // Heavy computation happens here (off main thread)
  const builtCharacter = buildCharacter(character, template);
  const availableSelections = calculateAvailableSelections(character, builtCharacter, template);

  // Send result back
  self.postMessage({
    builtCharacter,
    availableSelections
  });
});
```

**Main thread stays responsive**:

```clojure
;; events.cljs
(reg-event-fx
  :select-option
  (fn [{:keys [db]} [_ option-data]]
    ;; Optimistic update
    {:db (update-character-state db option-data)
     :ui {:show-loading true}
     :worker {:action :rebuild-character
              :data {:character (:character db)
                     :template (:template db)}}}))

(reg-event-fx
  :character-rebuilt
  (fn [{:keys [db]} [_ {built-character :result}]]
    {:db (assoc db
            :built-character built-character
            :loading false)
     :ui {:hide-loading true}}))
```

**Benefits**:
- UI stays at 60fps during character building
- User can scroll, hover, read descriptions while calculating
- Perceived performance: "instant" (no freeze)
- Better mobile experience (slower CPUs)

**Challenges**:
- **ClojureScript in worker**: Need separate compilation for worker context
- **Data serialization**: Can't pass ClojureScript data structures directly (must serialize)
- **No re-frame in worker**: Workers don't have access to subscriptions (pure functions only)
- **Debugging**: More complex (errors in worker vs. main thread)

**Implementation complexity**: High - requires significant refactoring

**When to use**: Only if character building still freezes after other optimizations

---

## Summary: Optimization Priority & Impact

| Optimization | Difficulty | Impact | Risk | Priority |
|--------------|-----------|--------|------|----------|
| **Conditional Spellcasting Template** | Easy | Medium | Low | ⭐⭐⭐ High |
| **Spell List Filtering** | Medium | Low-Medium | Low | ⭐⭐ Medium |
| **Lazy Subclass Loading** | Medium | High | Medium | ⭐⭐⭐ High |
| **Plugin Content Indexing** | Easy | Medium | Low | ⭐⭐ Medium |
| **Template Splitting** | Hard | High | High | ⭐ Low (future) |
| **Web Workers** | Very Hard | Medium | High | ⭐ Low (only if needed) |

**Recommended Implementation Order**:

1. **Conditional Spellcasting Template** - Quick win, low risk, immediate benefit
2. **Lazy Subclass Loading** - High impact, moderate complexity
3. **Plugin Content Indexing** - Enables better search/filtering UX
4. **Spell List Filtering** - Nice-to-have optimization
5. **Template Splitting** - Major refactor, save for when library grows much larger
6. **Web Workers** - Only if other optimizations aren't enough

---

## Notes on Homebrew/Plugin Compatibility

**Critical consideration**: Any optimization MUST work with:
- Base game content (PHB, Xanathar's, Tasha's, etc.)
- User-created homebrew classes/races/spells
- Plugin-provided custom content

**Safe patterns**:
- ✅ Check for `:spellcasting` key presence (works for all content)
- ✅ Use subscription-based lazy loading (works for all content)
- ✅ Index-based search (works for all content)

**Unsafe patterns**:
- ❌ Hardcoding "these classes never have spells" (breaks with homebrew)
- ❌ Assumptions about class structure (homebrew might differ)
- ❌ Filtering based on source (`:phb`, `:xgte`, etc.) - excludes custom content

**Validation approach**:
1. Implement optimization
2. Test with base content
3. Test with sample homebrew content
4. Test with multiple plugins enabled
5. Verify backward compatibility (existing characters load correctly)
