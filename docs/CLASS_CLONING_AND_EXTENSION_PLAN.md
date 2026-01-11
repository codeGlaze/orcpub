# Class Cloning and Extension Feature Plan

**Created:** 2026-01-11
**Status:** Research Complete - Planning Phase
**Branch:** `upgrade/class-creation`

---

## 🚨 CRITICAL REQUIREMENT: BACKWARD COMPATIBILITY 🚨

**ALL CHANGES MUST MAINTAIN 100% BACKWARD COMPATIBILITY**

### Non-Negotiable Rules:

1. **NEVER rename or delete existing class keys** (`:barbarian`, `:wizard`, etc.)
   - Existing characters reference these keys in local storage
   - Changing keys will break saved character data
   - Users must not log in to find their characters broken

2. **NEVER remove fields from existing data structures**
   - Only ADD optional fields with sensible defaults
   - Existing data must continue to load without errors

3. **NEVER change the structure of plugin storage in breaking ways**
   - Extend schemas additively only
   - Old homebrew content must load correctly after updates

4. **NEVER modify how built-in classes aggregate if it changes keys/references**
   - Class aggregation can be enhanced but not altered fundamentally
   - Existing character references must resolve correctly

5. **If a breaking change is ABSOLUTELY unavoidable:**
   - Implement data migration/shim layer FIRST
   - Test migration with real saved data
   - Provide rollback mechanism
   - Document migration process thoroughly
   - Consider this the LAST RESORT

### Testing Requirements:

Before ANY feature ships:
- [ ] Test loading characters created before changes
- [ ] Test loading homebrew classes created before changes
- [ ] Test loading characters with homebrew classes
- [ ] Test that all class references still resolve
- [ ] Test that no modifiers/traits are lost
- [ ] Verify local storage structure compatibility

---

## Problem Statement

### Current Limitations:

1. **Built-in classes cannot be cloned** - Users cannot use official classes (Barbarian, Wizard, etc.) as starting templates for homebrew classes
2. **Built-in classes cannot be modified** - No way to create variants with small tweaks (e.g., d10 Barbarian, modified features)
3. **No inheritance system** - Homebrew classes start from scratch with no parent relationship
4. **Features locked to built-in classes** - Class abilities are hardcoded and cannot be reused

### User Wants:

1. Create new base class using existing built-in class as template
2. Override/extend built-in class properties (hit die, features, saves, etc.)
3. Better UX for creating class variants without full reimplementation

---

## Current Architecture

### Built-in Classes

**Location:** `/home/user/orcpub/src/cljc/orcpub/dnd/e5/classes.cljc` (3145 lines)

**Implementation:**
- Defined as **functions** returning configuration via `opt5e/class-option()`
- 11 classes total: Barbarian, Bard, Cleric, Druid, Fighter, Monk, Paladin, Ranger, Rogue, Sorcerer, Warlock, Wizard
- Each has unique `:key` (e.g., `:barbarian`) **← IMMUTABLE - NEVER CHANGE**

**Structure:**
```clojure
(defn barbarian-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells spells-map plugin-subclasses-map language-map weapon-map
   {:name "Barbarian"
    :key :barbarian              ; ← CRITICAL: Referenced in saved characters
    :hit-die 12
    :ability-increase-levels [4 8 12 16 19]
    :profs {:armor {...} :weapon {...} :save {...}}
    :modifiers [...]             ; Global class modifiers
    :levels {5 {:modifiers [...]} 9 {...}}  ; Level-specific features
    :traits [...]                ; Static traits
    :subclass-level 3
    :subclass-title "Primal Path"
    :subclasses [...]}))
```

### Homebrew Classes

**Location:** Local storage plugins (`"plugins"` key)

**Implementation:**
- Stored as **data maps** in browser local storage
- Retrieved via Redux subscription `::classes5e/plugin-classes`
- Merged with built-in classes in `::classes5e/classes` subscription

**Structure:**
```clojure
{:name "My Custom Class"
 :key :custom-class-abc123      ; ← CRITICAL: Referenced in saved characters
 :option-pack "My Homebrew"
 :hit-die 8
 :ability-increase-levels [4 8 12 16 19]
 :traits []
 :level-modifiers []            ; Converted to :levels map on load
 :level-selections []
 :props {}}                     ; Converted to :modifiers on load
```

### Key Differences:

| Aspect | Built-in | Homebrew |
|--------|----------|----------|
| Format | Functions | Data maps |
| Storage | Hardcoded in code | Local storage (plugins) |
| Modifiers | `:modifiers` array | `:props` map → converted |
| Level Features | `:levels` map | `:level-modifiers` array → converted |
| Keys | Fixed (`:barbarian`, etc.) | User-generated (`:custom-xyz`) |

### Class Aggregation

**Location:** `/home/user/orcpub/src/cljs/orcpub/dnd/e5/spell_subs.cljs` (lines 875-901)

```clojure
(reg-sub
 ::classes5e/classes
 :<- [::classes5e/plugin-classes]  ; Homebrew
 (fn [[... plugin-classes ...] _]
   (vec
    (into
     (sorted-set-by #(compare (::t/key %1) (::t/key %2)))
     (concat
      (map opt5e/class-option plugin-classes)  ; Homebrew converted
      (base-class-options ...))))))            ; Built-in
```

**⚠️ BACKWARD COMPATIBILITY NOTE:**
This aggregation logic determines which classes are available. Any changes here must NOT affect how existing class keys resolve.

---

## Implementation Plan

### Phase 1: Basic Cloning (Quick Win) ✅ BACKWARD COMPATIBLE

**Goal:** Allow one-click cloning of built-in classes as homebrew templates

**Changes Required:**

1. **Add conversion function** - `src/cljc/orcpub/dnd/e5/options.cljc`
   ```clojure
   (defn class-option->homebrew-data
     "Converts a built-in class option to homebrew class data structure.
      BACKWARD COMPATIBLE: Only creates NEW data, doesn't modify existing."
     [class-option]
     {:name (str (:name class-option) " (Homebrew)")
      :key (keyword (str (name (::t/key class-option)) "-clone-" (random-uuid)))
      :option-pack "Cloned Classes"
      :hit-die (:hit-die class-option)
      :ability-increase-levels (:ability-increase-levels class-option)
      :traits (extract-traits class-option)           ; Convert to homebrew format
      :level-modifiers (extract-level-modifiers class-option)
      :level-selections (extract-level-selections class-option)
      :props (extract-props class-option)})
   ```

2. **Add UI button** - `src/cljs/orcpub/dnd/e5/views.cljs`
   - Add "Clone as Template" button next to built-in class displays
   - Button opens class-builder with pre-filled data
   - New unique key generated (no conflicts with existing)

3. **Add event handler** - `src/cljs/orcpub/dnd/e5/events.cljs`
   ```clojure
   (reg-event-fx
    ::classes/clone-class
    [class-interceptors]
    (fn [{db :db} [_ source-class-key]]
      ;; Get built-in class option
      ;; Convert to homebrew format
      ;; Generate unique key (NEW key, doesn't affect existing)
      ;; Load into builder-item
      {:db (assoc-in db [:builder-item] (class-option->homebrew-data ...))}))
   ```

**✅ Backward Compatibility Analysis:**
- Creates NEW classes with NEW keys
- Does NOT modify built-in classes
- Does NOT change existing homebrew classes
- Does NOT alter aggregation logic
- Existing characters unaffected

---

### Phase 2: Parent-Child Inheritance ⚠️ REQUIRES CAREFUL DESIGN

**Goal:** Allow homebrew classes to reference a parent class and inherit properties

**Changes Required:**

1. **Extend homebrew class spec** - `src/cljc/orcpub/dnd/e5/classes.cljc`
   ```clojure
   (spec/def ::parent-class keyword?)  ; NEW optional field

   (spec/def ::homebrew-class
     (spec/keys :req-un [::name ::key ::option-pack]
                :opt-un [::parent-class]))  ; ← NEW optional field
   ```

2. **Add parent selection UI** - `src/cljs/orcpub/dnd/e5/views.cljs`
   ```clojure
   ;; In class-builder component
   [:div
    [:label "Base Class (Optional)"]
    [select-field
     {:options (conj built-in-class-options {:key nil :name "None (Start Fresh)"})
      :value (:parent-class @builder-item)
      :on-change #(dispatch [::classes/set-class-prop :parent-class %])}]]
   ```

3. **Modify class aggregation** - `src/cljs/orcpub/dnd/e5/spell_subs.cljs`
   ```clojure
   (defn apply-class-inheritance
     "Merges parent class properties with child overrides.
      BACKWARD COMPATIBLE: Only applies if :parent-class exists."
     [class-option class-map]
     (if-let [parent-key (:parent-class class-option)]
       (let [parent (get class-map parent-key)]
         (merge-with
          (fn [parent-val child-val]
            (if (nil? child-val) parent-val child-val))  ; Child wins if set
          parent
          class-option))
       class-option))  ; No parent = unchanged
   ```

4. **Update plugin class processing** - `src/cljs/orcpub/dnd/e5/spell_subs.cljs`
   ```clojure
   (reg-sub
    ::classes5e/plugin-classes
    :<- [::e5/plugin-vals]
    :<- [::classes5e/built-in-class-map]  ; NEW dependency for parent lookup
    (fn [[plugins built-in-map]]
      (map
       (fn [class]
         (let [class-with-inheritance (apply-class-inheritance class built-in-map)
               levels (make-levels ... class-with-inheritance)]
           (assoc class-with-inheritance :levels levels)))
       (mapcat (comp vals ::e5/classes) plugins))))
   ```

**⚠️ Backward Compatibility Analysis:**

✅ **SAFE:**
- `:parent-class` is OPTIONAL - old classes don't have it
- Classes without `:parent-class` process exactly as before
- No changes to existing class keys
- No changes to built-in classes
- Aggregation logic extended, not replaced

⚠️ **RISKS:**
- If parent class key changes (it won't, per our rules), child would break
- Solution: Validate parent-class exists before applying inheritance
- Fallback: If parent not found, log warning and process as standalone

**Migration Path:**
```clojure
(defn validate-parent-class [class-option class-map]
  "Validates parent-class reference still exists. Logs warning if not.
   BACKWARD COMPATIBLE: Falls back to standalone class if parent missing."
  (if-let [parent-key (:parent-class class-option)]
    (if (contains? class-map parent-key)
      class-option
      (do
        (js/console.warn (str "Parent class " parent-key " not found for "
                             (:key class-option) ". Processing as standalone."))
        (dissoc class-option :parent-class)))
    class-option))
```

---

### Phase 3: Feature-Level Overrides ⚠️ COMPLEX - NEEDS CAREFUL PLANNING

**Goal:** Allow disabling, replacing, or modifying inherited features

**Changes Required:**

1. **Extend homebrew spec with override fields**
   ```clojure
   (spec/def ::disabled-traits (spec/coll-of keyword?))  ; Trait keys to disable
   (spec/def ::modified-levels (spec/map-of int? map?))  ; Level overrides

   (spec/def ::homebrew-class
     (spec/keys :req-un [::name ::key ::option-pack]
                :opt-un [::parent-class
                         ::disabled-traits    ; ← NEW optional
                         ::modified-levels])) ; ← NEW optional
   ```

2. **Add feature override UI**
   - Show inherited features in collapsible panel
   - Toggle switches for each feature (enable/disable)
   - "Override" button to replace feature with custom version

3. **Implement override logic in trait/modifier rendering**
   ```clojure
   (defn apply-feature-overrides
     "Applies disabled-traits and modified-levels to inherited class.
      BACKWARD COMPATIBLE: Only applies if override fields exist."
     [class-option]
     (cond-> class-option
       (:disabled-traits class-option)
       (update :traits #(remove (fn [t] (contains? (:disabled-traits class-option)
                                                    (:key t))) %))

       (:modified-levels class-option)
       (update :levels #(merge-with merge % (:modified-levels class-option)))))
   ```

**⚠️ Backward Compatibility Analysis:**

✅ **SAFE:**
- Both fields are OPTIONAL
- Old classes without these fields process normally
- No changes to existing data structures
- Feature override only applies when explicitly set

⚠️ **RISKS:**
- Complex modifier dependencies could break if features removed carelessly
- User error (disabling Rage on Barbarian) could create broken classes

**Safeguards:**
- Show warning when disabling core features
- Validate modifier dependencies before saving
- Allow "reset to parent" button to undo overrides

---

### Phase 4: Advanced Features (Future)

These are lower priority and require Phase 1-3 to be stable:

1. **Versioning System**
   - Track edit history for homebrew classes
   - Revert to previous versions
   - Compare versions side-by-side

2. **Enhanced Export/Import**
   - Export variant classes WITH parent references
   - Import validates parent dependencies exist
   - Share homebrew variants efficiently

3. **Class Template Builder**
   - Save homebrew classes as reusable templates
   - Community sharing via export codes
   - Template marketplace/library

4. **Subclass Inheritance**
   - Extend same parent-child system to subclasses
   - Clone Berserker → Frost Berserker
   - Maintain relationship to parent subclass

---

## Critical Files Reference

### Must Modify:

1. **`/home/user/orcpub/src/cljc/orcpub/dnd/e5/classes.cljc`** (3145 lines)
   - Add conversion functions (Phase 1)
   - Extend homebrew spec (Phase 2-3)
   - Add validation functions

2. **`/home/user/orcpub/src/cljs/orcpub/dnd/e5/spell_subs.cljs`** (lines 344-901)
   - Modify class aggregation (Phase 2)
   - Add inheritance logic
   - Update `make-levels()` for overrides (Phase 3)

3. **`/home/user/orcpub/src/cljs/orcpub/dnd/e5/views.cljs`** (lines 5429-5694)
   - Add clone button (Phase 1)
   - Add parent selector (Phase 2)
   - Add feature override UI (Phase 3)

4. **`/home/user/orcpub/src/cljs/orcpub/dnd/e5/events.cljs`**
   - Add `::classes/clone-class` event (Phase 1)
   - Add `::classes/set-parent-class` event (Phase 2)
   - Add `::classes/toggle-trait-override` event (Phase 3)

5. **`/home/user/orcpub/src/cljs/orcpub/dnd/e5/db.cljs`** (lines 112-115)
   - Extend `default-class` with new optional fields
   - Add `default-variant-class` template

### Must NOT Modify (Without Extreme Caution):

1. **Built-in class `:key` values** - Referenced in saved characters
2. **Plugin storage structure root fields** - Breaking change for local storage
3. **Class aggregation subscription keys** - Components depend on these

---

## Data Migration Strategy

### Current Local Storage Schema:

```javascript
// localStorage key: "plugins"
{
  "My Homebrew Pack": {
    "orcpub.dnd.e5/classes": {
      ":custom-class-123": {
        name: "My Custom Class",
        key: ":custom-class-123",
        hit-die: 8,
        // ... other fields
      }
    }
  }
}
```

### Extended Schema (Backward Compatible):

```javascript
// NEW optional fields added, old fields unchanged
{
  "My Homebrew Pack": {
    "orcpub.dnd.e5/classes": {
      ":custom-class-123": {
        name: "My Custom Class",
        key: ":custom-class-123",
        hit-die: 8,
        // ... existing fields ...

        // NEW optional fields (Phase 2)
        parent-class: ":barbarian",        // ← NEW (optional)

        // NEW optional fields (Phase 3)
        disabled-traits: [":danger-sense"], // ← NEW (optional)
        modified-levels: {                 // ← NEW (optional)
          5: {modifiers: [...]}
        }
      }
    }
  }
}
```

### Migration Function (If Needed):

```clojure
(defn migrate-plugin-classes
  "Migrates old plugin class data to new schema.
   ONLY adds new fields with defaults, NEVER removes or renames.
   BACKWARD COMPATIBLE: Old data loads unchanged."
  [plugin-data]
  ;; No migration needed currently - all fields are additive
  ;; Future migrations would only ADD fields with defaults:
  (reduce-kv
   (fn [acc class-key class-data]
     (assoc acc class-key
            (merge
             {:disabled-traits []      ; Default for old classes
              :modified-levels {}}     ; Default for old classes
             class-data)))             ; Old data takes precedence
   {}
   (get-in plugin-data [::e5/classes])))
```

---

## Testing Checklist

### Before ANY commit:

- [ ] Existing characters load without errors
- [ ] Existing homebrew classes load without errors
- [ ] Characters with homebrew classes display correctly
- [ ] All built-in class keys still resolve (`:barbarian`, etc.)
- [ ] No fields removed from any data structure
- [ ] No class keys renamed
- [ ] Local storage schema remains compatible

### Phase 1 Testing:

- [ ] Clone built-in class creates new unique key
- [ ] Cloned class loads in class-builder with all fields populated
- [ ] Cloned class can be saved as homebrew
- [ ] Original built-in class unchanged
- [ ] Character using original class still works

### Phase 2 Testing:

- [ ] Classes without `:parent-class` load normally
- [ ] Classes with `:parent-class` inherit correctly
- [ ] Missing parent class logs warning and falls back
- [ ] Parent properties override correctly
- [ ] Characters using parent class unaffected
- [ ] Characters using variant class display correctly

### Phase 3 Testing:

- [ ] Disabled traits don't appear in character sheet
- [ ] Modified levels apply overrides correctly
- [ ] Classes without overrides process normally
- [ ] Override UI shows inherited features
- [ ] Reset to parent works correctly

---

## Risk Assessment

### Low Risk (Safe to Implement):

✅ **Phase 1: Basic Cloning**
- Only creates new data
- No modifications to existing systems
- Easy to test and validate

### Medium Risk (Requires Testing):

⚠️ **Phase 2: Parent-Child Inheritance**
- Extends data structures additively
- Requires validation of parent references
- Need fallback for missing parents
- Must test inheritance merge logic thoroughly

### High Risk (Requires Extreme Care):

🔴 **Phase 3: Feature-Level Overrides**
- Complex modifier dependencies
- User errors could create broken classes
- Requires extensive validation
- Need safeguards and warnings
- Must test all edge cases

---

## Success Criteria

### Phase 1:
- [ ] Users can clone any built-in class with one click
- [ ] Cloned class appears in homebrew list
- [ ] Cloned class fully editable in class-builder
- [ ] No impact on existing characters or homebrew

### Phase 2:
- [ ] Users can select parent class when creating homebrew
- [ ] Variant classes inherit all parent properties
- [ ] Overridden properties display correctly
- [ ] Character sheets show variant classes properly
- [ ] 100% backward compatibility maintained

### Phase 3:
- [ ] Users can disable inherited features
- [ ] Users can replace level features
- [ ] UI shows clear diff from parent
- [ ] Validation prevents broken configurations
- [ ] Characters remain stable

---

## Design Decisions (Resolved)

### 1. Clone Naming
**Decision:** Offer rename as part of clone process with "(Clone)" default
- Default name: `"[Original Class Name] (Clone)"`
- User can edit immediately in the clone dialog
- Can always rename later in class builder

### 2. Parent Changes (Errata)
**Decision:** Auto-inherit by default, allow overrides
- If parent class updates, variants inherit changes automatically
- Users can override specific features if they prefer old version
- Respects user's ability to be "picky about errata" while providing sensible defaults

### 3. Export Format
**Decision:** Parent reference + overrides only
- More efficient (smaller export codes)
- Requires parent class to exist on import
- Import validation checks for parent availability
- Can fall back to full export if parent is custom/unavailable

### 4. UI Placement
**Decision:** Both locations initially, refine based on feedback
- Add "Clone" button in class selection view
- Add "Clone" button in class detail view
- Monitor usage and remove less-used location later

### 5. Validation Strategy
**Decision:** Three-tier approach with configurable levels (Tier 1 always enforced)

#### Tier 1: PREVENT (Non-negotiable, always enforced)
**Purpose:** Prevent data corruption and application breakage

**Enforcement:** UI-level data validation
- Hit die: Number input with min=4, max=20 (or dropdown: 4, 6, 8, 10, 12, 20)
- Required fields: Validate before allowing save
- Data types: Enforce through form controls (no string in number field)
- Circular dependencies: Check parent-class chain for loops

**Never allow:**
- Missing required fields (name, key, option-pack)
- Invalid data types (string where number expected)
- Circular inheritance (Class A → Class B → Class A)

**Implementation:**
```clojure
(defn validate-tier1 [class-data]
  (let [errors []]
    (when-not (:name class-data)
      (conj errors "Name is required"))
    (when-not (number? (:hit-die class-data))
      (conj errors "Hit die must be a number"))
    (when (and (:parent-class class-data)
               (circular-dependency? class-data))
      (conj errors "Circular dependency detected in parent class chain"))
    errors))
```

#### Tier 2: WARN (Enabled by default, user can dismiss)
**Purpose:** Help users avoid common mistakes

**Show warning modal for:**
- Disabling features that other features depend on
  - Example: Disabling "Rage" but keeping "Persistent Rage"
- Conflicting modifiers at same level
  - Example: Two features both modifying attack count at level 5
- Removing core class identity features
  - Example: Removing spellcasting from Wizard

**Implementation:**
```clojure
(defn validate-tier2 [class-data parent-class]
  (let [warnings []]
    (when (and (disabled? class-data :rage)
               (has-feature? class-data :persistent-rage))
      (conj warnings "Persistent Rage depends on Rage. Disabling Rage may cause issues."))
    warnings))
```

**UI Flow:**
- Show modal: "⚠️ Warnings Detected"
- List all warnings
- Buttons: "Cancel" | "Save Anyway"

#### Tier 3: INFO (Helpful hints, non-intrusive)
**Purpose:** Provide helpful information without interrupting

**Show info banner for:**
- Factual differences from parent
  - Example: "This variant has higher hit die than parent class"
  - Example: "This variant has 2 more ASIs than parent"
- Feature counts
  - Example: "This class has 12 custom features"

**DO NOT warn about:**
- Power level / balance concerns
  - Users know their own game better than we do
  - "Overpowered" is subjective and campaign-dependent

**Implementation:**
```clojure
(defn validate-tier3 [class-data parent-class]
  (let [info []]
    (when (and parent-class
               (> (:hit-die class-data) (:hit-die parent-class)))
      (conj info (str "This variant has higher hit die than parent class (d"
                     (:hit-die class-data) " vs d" (:hit-die parent-class) ")")))
    info))
```

**UI Display:**
- Small info banner below save button
- Dismissible (X button)
- Non-blocking

#### Configurable Validation Levels (Future Enhancement)

**User Preference Setting:**
```clojure
;; In user preferences
{:validation-level :moderate  ; :strict | :moderate | :minimal}
```

**Levels:**
- **Strict:** All three tiers active
- **Moderate (Default):** Tiers 1 + 2 active, Tier 3 optional
- **Minimal:** Tier 1 only (expert users)

**CRITICAL:** Tier 1 can NEVER be disabled
- Data integrity is non-negotiable
- UI controls enforce valid data types
- Required fields always required

---

## Implementation Priority

**Immediate Next Steps:**

1. Review this document with team
2. Confirm backward compatibility requirements acceptable
3. Create test suite for backward compatibility
4. Implement Phase 1 (basic cloning)
5. Test extensively with real saved data
6. Collect user feedback before proceeding to Phase 2

**Do Not Proceed Until:**
- [ ] Backward compatibility test suite created
- [ ] Test data from real characters available
- [ ] Migration strategy validated
- [ ] Rollback plan documented

---

## Notes

- All built-in class keys are IMMUTABLE: `:barbarian`, `:bard`, `:cleric`, `:druid`, `:fighter`, `:monk`, `:paladin`, `:ranger`, `:rogue`, `:sorcerer`, `:warlock`, `:wizard`
- Homebrew class keys are user-generated (e.g., `:custom-class-abc123`)
- Plugin storage uses namespaced keys (`::e5/classes`)
- Local storage key: `"plugins"`
- Character data references class keys directly - breaking these breaks characters

---

## References

### Code Locations:

- Built-in classes: `src/cljc/orcpub/dnd/e5/classes.cljc`
- Class aggregation: `src/cljs/orcpub/dnd/e5/spell_subs.cljs:875-901`
- Class builder UI: `src/cljs/orcpub/dnd/e5/views.cljs:5429-5694`
- Homebrew spec: `src/cljc/orcpub/dnd/e5/classes.cljc:19-29`
- Default class template: `src/cljs/orcpub/dnd/e5/db.cljs:112-115`
- Plugin class subscription: `src/cljs/orcpub/dnd/e5/spell_subs.cljs:410-424`
- Level creation: `src/cljs/orcpub/dnd/e5/spell_subs.cljs:344-391`

### Research Agent ID:
- Initial research: `a6d1f9b` (can resume with Task tool if needed)

---

**Last Updated:** 2026-01-11
**Document Version:** 1.1
**Status:** Design Decisions Finalized - Ready for Implementation
