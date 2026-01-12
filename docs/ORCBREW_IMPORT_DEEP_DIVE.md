# Orcbrew File Import: Deep Dive

> A comprehensive guide for agents working on the import system. Documents lessons learned, architecture decisions, and pitfalls encountered.

---

## Overview

Orcbrew files (`.orcbrew`) are EDN (Extensible Data Notation) files containing homebrew D&D 5e content. Users create these in OrcPub or other tools and import them to add custom races, classes, spells, monsters, etc.

**Key Challenge**: Real-world orcbrew files are often malformed due to:
- Manual editing mistakes
- Export bugs from other tools
- Accumulated cruft from years of edits
- Merging multiple sources

---

## File Structure

### Basic Structure
```clojure
{
  "Plugin Name" {
    :orcpub.dnd.e5/races { ... }
    :orcpub.dnd.e5/classes { ... }
    :orcpub.dnd.e5/spells { ... }
    :orcpub.dnd.e5/monsters { ... }
    :orcpub.dnd.e5/backgrounds { ... }
    ;; etc.
  }
}
```

### Multi-Plugin Structure
Some files contain multiple plugins at the top level:
```clojure
{
  "Plugin A" { :orcpub.dnd.e5/races { ... } }
  "Plugin B" { :orcpub.dnd.e5/spells { ... } }
}
```

---

## Common Problems Encountered

### 1. Nil Values in Unexpected Places

**Problem**: Fields like `:str nil`, `:spell-list-kw nil`, `:option-pack nil` appear throughout files.

**Root Causes**:
- UI toggles that were enabled then disabled
- Incomplete form submissions
- Copy-paste errors

**Critical Insight**: Not all nils are errors!

| Field Type | Example | Nil Meaning | Action |
|------------|---------|-------------|--------|
| Semantic | `:spell-list-kw` | "Custom spell list, define later" | **Preserve** |
| Numeric | `:str`, `:dex`, `:ac` | Accidental, breaks calculations | **Remove** |
| Toggle | `:disabled?` | Should be `false` | **Replace with default** |
| String | `:option-pack` | Empty string intended | **Replace with default** |

### 2. Empty String Keys

**Problem**: Top-level plugin key is empty string `""`
```clojure
{"" {:orcpub.dnd.e5/races {...}}}
```

**Cause**: User didn't name their content pack

**Solution**: Rename to "Unnamed Content" (with uniqueness check to avoid duplicate keys)

### 3. Empty Option Pack

**Problem**: `:option-pack ""` causes items to not appear in UI

**Solution**: Replace with `"Unnamed Content"` or default source name

### 4. Trailing Commas

**Problem**: EDN doesn't allow trailing commas like JSON does
```clojure
{:name "Elf", :speed 30,}  ;; Invalid!
```

**Solution**: String-level regex replacement before parsing

### 5. `disabled? nil`

**Problem**: Toggle artifacts from UI interactions
```clojure
{:name "Darkvision" :disabled? nil}
```

**Solution**: Replace with `disabled? false`

---

## Two-Phase Cleaning Architecture

### Why Two Phases?

**Failed Approach**: Initially tried to fix everything with string-level regex replacements.

**Problem Discovered**: Replacing `{"" {` with `{"Default Option Source" {` at string level caused **duplicate key errors** when "Default Option Source" already existed in the file.

**Solution**: Split into two phases:

### Phase 1: String-Level (Pre-Parse)
Only syntax fixes that don't require understanding the data structure:
- `disabled? nil` → `disabled? false`
- Trailing commas `,}` → `}`
- Trailing commas `,]` → `]`

**Why these are safe**: They're purely syntactic and can't cause semantic conflicts.

### Phase 2: Data-Level (Post-Parse)
Semantic fixes after EDN is parsed into Clojure data structures:
- Rename empty plugin keys (with uniqueness check)
- Fix empty `:option-pack` values
- Field-specific nil handling

**Why data-level**: Can inspect existing keys, check for duplicates, understand context.

---

## Field-Specific Nil Handling

Defined in `import_validation.cljs`:

```clojure
;; Fields where nil should be replaced with a default value
(def nil-replace-defaults
  {:disabled? false
   :option-pack "Unnamed Content"})

;; Fields where nil is semantically meaningful and should be preserved
(def nil-preserve-fields
  #{:spell-list-kw :spellcasting :ability :class-key})

;; Fields where nil should be removed entirely (numeric/calculated fields)
(def nil-remove-in-maps
  #{:str :dex :con :int :wis :cha :ac :hp :speed
    :level :modifier :die :die-count})
```

---

## Import Strategies

### Progressive (Default)
- Imports valid items, skips invalid ones
- User gets partial content rather than nothing
- Logs skipped items for review

### Strict
- All-or-nothing validation
- Fails if any item is invalid
- For users who want to fix their files

---

## Change Tracking System

The import system tracks all cleaning operations for transparency:

### Change Types
```clojure
:string-fix        ;; Pre-parse syntax fixes
:renamed-plugin-key ;; Empty "" key renamed
:fixed-option-pack  ;; Empty option-pack filled
:removed-nil       ;; Nil removed from numeric field
:replaced-nil      ;; Nil replaced with default
:preserved-nil     ;; Nil kept (semantic field)
```

### Data Structure
```clojure
{:type :removed-nil
 :field :str
 :path [:orcpub.dnd.e5/races :elf :abilities]}
```

### UI
- `import-log-panel`: Slide-in panel from right
- `import-log-button`: Floating button (bottom-right)
- Auto-expands after import if changes were made

---

## Key Files

| File | Purpose |
|------|---------|
| `import_validation.cljs` | Core validation & cleaning logic |
| `import_validation_test.cljs` | Tests for validation |
| `events.cljs` | `::e5/import-plugin` event handler |
| `views.cljs` | Import log UI components |
| `db.cljs` | `:import-log` state definition |
| `subs.cljs` | Import log subscriptions |

---

## Lessons Learned

### 1. Don't Assume All Nils Are Errors
Early version removed all nils. Users reported broken spell lists because `:spell-list-kw nil` means "I'll define my own spell list" - it's intentional.

### 2. String Manipulation is Dangerous for Semantic Operations
Regex replacing top-level keys without checking for duplicates caused hard-to-debug errors. Always parse first for semantic operations.

### 3. Large Files Require Careful Analysis
The problematic 3.5MB file had 37 nil values. Scanning tools needed to handle size gracefully (use PowerShell/CLI, not loading entire file into context).

### 4. Progressive Import is User-Friendly
Users with 1000 items don't want import to fail because of 1 bad item. Progressive import with detailed logging is the right default.

### 5. Transparency Builds Trust
The changelog panel shows users exactly what was "fixed" - they can verify the changes make sense rather than wondering what magic happened.

---

## Testing Approach

### Unit Tests
Test individual cleaning functions in isolation:
```clojure
(deftest test-clean-nil-preserves-semantic
  (is (= {:spell-list-kw nil}
         (clean-nil-in-map {:spell-list-kw nil}))))

(deftest test-clean-nil-removes-numeric
  (is (= {}
         (clean-nil-in-map {:str nil}))))
```

### Integration Tests
Test full `validate-import` pipeline with realistic data.

### Real-World Testing
Keep problematic orcbrew files for regression testing. The 3.5MB file that triggered this work is invaluable.

---

## Future Considerations

1. **Schema Validation**: Could add clojure.spec validation for deeper structural checks
2. **Repair Suggestions**: Instead of auto-fixing, suggest fixes for user approval
3. **Diff View**: Show before/after for each change
4. **Export Cleaning**: Clean files on export too, not just import

---

## Related Documentation

- [CODEBASE.md](./CODEBASE.md) - Overall codebase overview
- [ORCBREW_FILE_VALIDATION.md](./ORCBREW_FILE_VALIDATION.md) - Original validation docs
- [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Error handling patterns
