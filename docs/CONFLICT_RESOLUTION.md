# Conflict Resolution & Duplicate Key Handling

## Overview

The Conflict Resolution system detects and resolves duplicate content keys during import, preventing data corruption and allowing users to merge homebrew content from multiple sources without losing data.

## The Problem

When importing `.orcbrew` files, duplicate keys can occur when:
- Two plugins define the same class/race/item with the same key (e.g., both have `:artificer`)
- Importing content that conflicts with already-loaded content
- Merging multiple homebrew collections
- Re-importing modified versions of existing content

**Before this feature:**
- ❌ Last import silently overwrites existing content
- ❌ No warning about conflicts
- ❌ Data loss with no way to recover
- ❌ Confusion about which version is loaded
- ❌ Broken character references when content replaced

**After this feature:**
- ✅ Automatic conflict detection (internal + external)
- ✅ Interactive modal for resolving conflicts
- ✅ Option to rename, skip, or replace conflicting items
- ✅ Automatic reference updates (subclasses → parent class, etc.)
- ✅ Bulk operations (rename all, skip all)
- ✅ No data loss

## How It Works

### 1. Duplicate Key Detection

The system scans imported content for duplicate keys before applying changes.

**Detection types:**

**External conflicts** - Between existing and imported content:
```
Already loaded: :artificer from "Player's Handbook"
Importing:      :artificer from "Homebrew Classes"
→ CONFLICT: Same key, different sources
```

**Internal conflicts** - Within the imported file:
```
Importing from "My Pack":
  - :artificer (Battle Smith subclass)
  - :artificer (Armorer subclass)
→ CONFLICT: Same key used twice in one file
```

**Implemented in:** `src/cljs/orcpub/dnd/e5/import_validation.cljs:162-280`

**Detection process:**
1. Extract all keys from imported content by type
2. Check against already-loaded content (external conflicts)
3. Check for duplicates within import (internal conflicts)
4. Group conflicts by content type (classes, races, etc.)
5. Present to user for resolution

### 2. Conflict Resolution Modal

When conflicts are detected, an interactive modal allows users to choose how to handle each conflict.

**Modal UI features:**
- Clear conflict description
- Source information for both versions
- Multiple resolution options per conflict
- Bulk actions for mass operations
- Visual feedback (color-coded)

**Implemented in:** `src/cljs/orcpub/dnd/e5/views.cljs:530-670`

**Resolution options:**

**1. Rename** - Give new key to imported item
```
Option: Rename imported :artificer to :artificer-2
Result: Both versions exist with different keys
Use when: You want to keep both versions
```

**2. Skip** - Don't import this item
```
Option: Skip :artificer from "Homebrew Classes"
Result: Existing version unchanged, new version discarded
Use when: You prefer the existing version
```

**3. Replace** - Overwrite existing with imported
```
Option: Replace :artificer with new version
Result: Existing version removed, new version loaded
Use when: You want to update to the new version
```

**Bulk actions:**

- **Rename All** - Rename all conflicts with auto-generated keys
- **Skip All** - Skip all conflicting imports
- **Replace All** - Replace all existing with imported

### 3. Key Renaming with Reference Updates

When choosing "Rename", the system automatically updates all internal references to maintain consistency.

**What gets updated:**

**Parent-child relationships:**
```clojure
;; Original
{:key :artificer
 :name "Artificer"}

{:key :battle-smith
 :class :artificer        ; ← Reference to parent
 :name "Battle Smith"}

;; After renaming :artificer → :artificer-2
{:key :artificer-2        ; ← Renamed
 :name "Artificer"}

{:key :battle-smith
 :class :artificer-2      ; ← Auto-updated!
 :name "Battle Smith"}
```

**Supported reference types:**
- Subclass → parent class (`:class` field)
- Subrace → parent race (`:race` field)
- Items → class restrictions (`:classes` field)
- Spells → class spell lists (`:spell-lists` field)

**Implemented in:** `src/cljs/orcpub/dnd/e5/import_validation.cljs:282-380`

**Algorithm:**
1. User chooses "Rename :old-key to :new-key"
2. Update `:key` field in main item
3. Scan all items in plugin for references
4. Replace `:old-key` with `:new-key` in reference fields
5. Log all updates for debugging
6. Return updated plugin

### 4. Auto-generated Keys

When using "Rename All", the system generates unique keys automatically.

**Key generation strategy:**

```clojure
;; Pattern: original-key + numeric suffix
:artificer     → :artificer-2
:artificer-2   → :artificer-3
:blood-hunter  → :blood-hunter-2

;; Collision detection
:artificer-2 (exists)  → :artificer-3
:artificer-3 (exists)  → :artificer-4
```

**Implemented in:** `src/cljs/orcpub/dnd/e5/events.cljs:450-520`

**Rules:**
1. Append `-2` to original key
2. If `-2` exists, try `-3`, `-4`, etc.
3. Check against all existing content (loaded + importing)
4. Guarantee unique key across entire dataset

## User Experience

### Scenario 1: Single Conflict

**User actions:**
1. Import file with one `:artificer` class
2. Already have `:artificer` from PHB

**Modal appears:**
```
┌─ Conflict Resolution ─────────────────────────┐
│                                                │
│ Found 1 duplicate key:                        │
│                                                │
│ Classes:                                       │
│   :artificer                                   │
│     Existing: "Player's Handbook"              │
│     Importing: "Homebrew Classes"              │
│                                                │
│   ○ Rename to: :artificer-2                   │
│   ○ Skip (keep existing)                       │
│   ○ Replace (use imported)                     │
│                                                │
│         [Cancel]  [Apply Resolutions]         │
└────────────────────────────────────────────────┘
```

**User chooses:** Rename to `:artificer-2`

**Result:**
- PHB Artificer remains as `:artificer`
- Homebrew Artificer added as `:artificer-2`
- Both available in class selection

### Scenario 2: Multiple Conflicts

**User actions:**
1. Import file with 5 classes
2. 3 have name conflicts

**Modal appears:**
```
┌─ Conflict Resolution ─────────────────────────┐
│                                                │
│ Found 3 duplicate keys:                       │
│                                                │
│ Classes:                                       │
│   :artificer                                   │
│   :blood-hunter                                │
│   :mystic                                      │
│                                                │
│         [Rename All]  [Skip All]              │
│         [Cancel]      [Apply Resolutions]     │
└────────────────────────────────────────────────┘
```

**User clicks:** Rename All

**Result:**
- All 3 renamed with auto-generated keys
- All 5 classes from import added successfully
- No data loss

### Scenario 3: Subclass References

**User actions:**
1. Import "Custom Fighters" with:
   - `:custom-fighter` class
   - `:rune-knight` subclass (references `:custom-fighter`)
2. Conflict with existing `:custom-fighter`
3. Choose "Rename to :custom-fighter-2"

**What happens:**
```
Before rename:
  Class: :custom-fighter
  Subclass: :rune-knight → parent: :custom-fighter

After rename:
  Class: :custom-fighter-2        ← Renamed
  Subclass: :rune-knight → parent: :custom-fighter-2  ← Auto-updated!
```

**Result:**
- Both class and subclass work correctly
- References automatically maintained
- No broken links

## Implementation Details

### File Structure

**Core implementation:**
```
src/cljs/orcpub/dnd/e5/
├── import_validation.cljs      # Conflict detection & key renaming
│   ├── detect-duplicate-keys   # Find conflicts (v0.08)
│   ├── rename-key-in-content   # Update references
│   └── generate-unique-key     # Auto-naming
├── events.cljs                 # Re-frame events
│   ├── :start-conflict-resolution     (v0.05)
│   ├── :set-conflict-decision
│   ├── :apply-conflict-resolutions
│   └── :rename-all-conflicts
└── views.cljs                  # Conflict modal UI
    └── conflict-resolution-modal       (v0.06)
```

**State management:**
```
src/cljs/orcpub/dnd/e5/
├── db.cljs                     # App state schema
│   └── :conflict-resolution    # Conflict state
└── subs.cljs                   # Subscriptions
    └── :conflicts              # Conflict data
```

**Tests:**
```
test/cljs/orcpub/dnd/e5/
└── import_validation_test.cljs
    ├── detect-duplicate-keys tests
    ├── rename-key-in-content tests
    └── reference-update tests
```

### Key Functions

**Conflict detection:**
```clojure
(detect-duplicate-keys imported-content existing-content)
;; Returns: {:classes #{:artificer :blood-hunter}
;;           :races #{:dragonborn}}
```

**Key renaming:**
```clojure
(rename-key-in-content content old-key new-key content-type)
;; Updates main item + all references
;; Returns: updated content map
```

**Reference updates:**
```clojure
(update-references item old-key new-key)
;; Scans item for references to old-key
;; Replaces with new-key
;; Returns: updated item
```

**Unique key generation:**
```clojure
(generate-unique-key base-key existing-keys)
;; :artificer → :artificer-2 (if unique)
;; :artificer → :artificer-3 (if -2 exists)
```

### Data Structures

**Conflict state:**
```clojure
{:conflict-resolution
 {:conflicts
  {:classes #{{:key :artificer
               :existing-source "PHB"
               :importing-source "Homebrew"
               :decision :rename
               :new-key :artificer-2}}
   :races #{}}
  :decisions
  {:artificer :rename}
  :new-keys
  {:artificer :artificer-2}}}
```

**Resolution decision:**
```clojure
{:key :artificer           ; Conflicting key
 :decision :rename         ; :rename | :skip | :replace
 :new-key :artificer-2     ; Only for :rename
 :content-type :classes}   ; What type of content
```

### Event Flow

```
1. User imports file
   ↓
2. detect-duplicate-keys
   → [{:key :artificer, :type :classes, ...}]
   ↓
3. Conflicts found?
   ├─ No  → Import directly
   └─ Yes → Show modal
      ↓
4. User makes decisions
   ↓
5. :apply-conflict-resolutions
   ├─ :rename → rename-key-in-content
   ├─ :skip   → Remove from import
   └─ :replace → Replace existing
   ↓
6. Import with resolutions applied
   ↓
7. Update app state
```

### Reference Update Algorithm

```clojure
;; Pseudo-code for reference updates
(defn update-all-references [content old-key new-key]
  (for each item in content
    (for each field in reference-fields
      (if field contains old-key
        replace old-key with new-key))))

;; Reference fields by content type
{:subclass [:class]                    ; Parent class
 :subrace [:race]                      ; Parent race
 :spell [:spell-lists]                 ; Which classes can cast
 :item [:classes]                      ; Class restrictions
 :feature [:parent-class :parent-race] ; Belongs to
}
```

## Testing

### Test Coverage

**Test file:** `test/cljs/orcpub/dnd/e5/import_validation_test.cljs`

**Test scenarios:**

1. **Duplicate detection:**
   - External conflicts (existing vs importing)
   - Internal conflicts (within import)
   - Multiple content types
   - No conflicts

2. **Key renaming:**
   - Simple rename
   - Subclass parent reference update
   - Multiple references in one item
   - Nested references

3. **Unique key generation:**
   - Basic suffix appending
   - Collision avoidance
   - Edge cases (key-2, key-10, etc.)

4. **Modal interactions:**
   - Single conflict resolution
   - Bulk rename
   - Bulk skip
   - Cancel operation

### Manual Testing

**Test scenario 1: Basic conflict**
```
1. Load PHB content (has :artificer)
2. Import file with :artificer class
3. Modal should appear
4. Choose "Rename to :artificer-2"
5. Verify both classes exist
6. Verify :artificer-2 is selectable
```

**Test scenario 2: Subclass references**
```
1. Create plugin with:
   - :custom-fighter class
   - :rune-knight subclass (class: :custom-fighter)
2. Export to file
3. Delete plugin
4. Import file (conflicts with if re-run)
5. Rename :custom-fighter to :custom-fighter-2
6. Verify :rune-knight.class = :custom-fighter-2
```

**Test scenario 3: Bulk operations**
```
1. Import file with 10 conflicting classes
2. Click "Rename All"
3. Verify all 10 renamed with unique keys
4. Verify no naming collisions
```

## Configuration

### Adjusting Auto-naming Pattern

Edit `events.cljs` to change key generation:

```clojure
;; Current: :artificer → :artificer-2
(defn generate-unique-key [base-key existing-keys]
  (loop [n 2]
    (let [candidate (keyword (str (name base-key) "-" n))]
      (if (contains? existing-keys candidate)
        (recur (inc n))
        candidate))))

;; Alternative: :artificer → :artificer-v2
(defn generate-unique-key [base-key existing-keys]
  (loop [n 2]
    (let [candidate (keyword (str (name base-key) "-v" n))]
      ...)))
```

### Adding Reference Fields

To track new reference types:

```clojure
;; In import_validation.cljs

(def reference-fields
  "Fields that reference other content keys"
  {:subclass [:class]
   :subrace [:race]
   :feat [:prerequisite-class]})  ; NEW
```

## UI/UX Details

### Modal Styling

**Implemented in:** `src/clj/orcpub/styles/core.clj`

**CSS classes:**
```clojure
:conflict-modal           ; Main modal container
:conflict-header          ; Title section
:conflict-item            ; Individual conflict
:conflict-decision        ; Decision radio buttons
:conflict-actions         ; Button row
```

**Colors:**
```
Background: White (#fff)
Border: Light gray (#ddd)
Header: Dark blue (#2c3e50)
Rename: Blue (#3498db)
Skip: Orange (#e67e22)
Replace: Red (#e74c3c)
```

### Keyboard Navigation

**Supported keys:**
- `Tab` - Navigate between options
- `Space` / `Enter` - Select option
- `Esc` - Close modal (cancel)
- `1`, `2`, `3` - Quick select option

**Not yet implemented** - Future enhancement

## Troubleshooting

### Modal doesn't appear

**Cause:** No conflicts detected or modal state not initialized

**Fix:**
1. Check browser console for conflict detection logs
2. Verify import contains duplicate keys
3. Check `:conflict-resolution` in app state

### References not updated after rename

**Cause:** Reference field not in `reference-fields` list

**Fix:**
1. Identify which field needs updating
2. Add to `reference-fields` in `import_validation.cljs`
3. Re-run rename operation

### "Rename All" generates same key twice

**Cause:** Bug in unique key generation or existing key collision

**Fix:**
1. Check `generate-unique-key` logic
2. Verify `existing-keys` includes all loaded content
3. File bug report with example data

### Bulk operations don't work

**Cause:** Event not wired up or state not updating

**Fix:**
1. Check browser console for errors
2. Verify `:rename-all-conflicts` event exists
3. Check re-frame event registration

## Future Enhancements

### Planned improvements:

1. **Smart suggestions** - Suggest renaming strategy
   ```
   :artificer conflicts with PHB
   Suggested: Rename to :artificer-homebrew
   ```

2. **Preview changes** - Show what will happen before applying
   ```
   Renaming :artificer → :artificer-2 will:
   - Rename 1 class
   - Update 3 subclasses
   - Update 12 spell references
   ```

3. **Conflict history** - Remember previous decisions
   ```
   Last time you chose: Rename
   Apply same decision? [Yes] [No]
   ```

4. **Merge wizard** - Step-by-step guided merging
   ```
   Step 1/3: Choose base version
   Step 2/3: Select features to merge
   Step 3/3: Review and apply
   ```

5. **Diff view** - Compare conflicting versions
   ```
   Existing vs Importing:
   - hit-die: 8 → 10
   - traits: 5 items → 7 items (2 new)
   - spellcasting: Yes → No
   ```

## Related Documentation

- **ORCBREW_FILE_VALIDATION.md** - Import/export validation
- **CONTENT_RECONCILIATION.md** - Missing content detection
- **HOMEBREW_REQUIRED_FIELDS.md** - Content field requirements
- **ERROR_HANDLING.md** - Error handling framework

## Related Files

- `src/cljs/orcpub/dnd/e5/import_validation.cljs` - Core conflict detection
- `src/cljs/orcpub/dnd/e5/events.cljs` - Resolution events
- `src/cljs/orcpub/dnd/e5/views.cljs` - Modal UI
- `src/cljs/orcpub/dnd/e5/db.cljs` - State schema
- `src/cljs/orcpub/dnd/e5/subs.cljs` - Subscriptions
- `src/clj/orcpub/styles/core.clj` - CSS styling
- `test/cljs/orcpub/dnd/e5/import_validation_test.cljs` - Tests

## Version History

- **v0.08** - Debug logging for key rename operations
- **v0.06** - Conflict resolution modal UI
- **v0.05** - Resolution events (start, set-decision, apply)
- **v0.04** - Reference update algorithm
- **v0.03** - Bulk operations (rename all, skip all)
- **v0.02** - Auto-generated unique keys
- **v0.01** - Initial duplicate detection
