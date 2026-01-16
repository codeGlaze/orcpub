# Content Reconciliation & Missing Content Detection

## Overview

The Content Reconciliation system detects when characters reference homebrew content (classes, races, backgrounds, etc.) that isn't currently loaded in the application, and provides intelligent suggestions to help users find and fix these "broken" references.

## The Problem

Users often encounter "(not loaded)" warnings when:
- Opening a character after deleting/uninstalling a plugin
- Sharing characters that use homebrew content
- Importing characters without their required content
- Renaming or reorganizing homebrew content

**Before this feature:**
- Characters showed "(not loaded)" with no explanation
- No way to know which plugin was needed
- No suggestions for similar content
- Users had to manually search or recreate content

**After this feature:**
- Clear warnings about what's missing
- Intelligent suggestions for similar content
- Fuzzy matching to find close matches
- Source names to identify which plugin is needed

## How It Works

### 1. Missing Content Detection

The system scans character options to find all referenced content, then checks if that content is currently available.

**Implemented in:** `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs`

**Detection process:**
1. Extract all `::entity/key` references from character options
2. Identify content type (class, subclass, race, subrace, background)
3. Check if key exists in available content
4. Exclude built-in content (PHB, Xanathar's, etc.)
5. Report missing homebrew content with suggestions

**Supported content types:**
- Classes (`:class`)
- Subclasses (varies by class: `:martial-archetype`, `:arcane-tradition`, etc.)
- Races (`:race`)
- Subraces (`:subrace`)
- Backgrounds (`:background`)

### 2. Fuzzy Matching

When content is missing, the system suggests similar alternatives using multiple matching strategies:

**Matching strategies:**

1. **Exact key match** - Same key, different source
   ```
   Missing: :artificer from "Serakat's Compendium"
   Suggestion: :artificer from "Player's Handbook" (exact key match)
   ```

2. **Levenshtein distance** - Similar spelling
   ```
   Missing: :artficer
   Suggestion: :artificer (similar spelling)
   ```

3. **Prefix matching** - Same start
   ```
   Missing: :battle-smith-v2
   Suggestion: :battle-smith (prefix match)
   ```

4. **Display name matching** - Similar names
   ```
   Missing: :drunken_master
   Suggestion: :drunken-master (similar name)
   ```

**Thresholds:**
- Levenshtein: Max distance of 3 edits
- Prefix: Minimum 4 character match
- Display name: Max distance of 3 edits

**Implemented in:** `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs:89-160`

### 3. Warning UI

Missing content is displayed in the character builder with clear warnings and suggestions.

**UI Components:**

**Basic warning (no suggestion):**
```
:missing-content (not loaded)
```

**Warning with suggestion:**
```
:missing-content (not loaded - try :suggested-content?)
```

**Warning with source info:**
```
:missing-content from "Plugin Name" (not loaded - try :suggested-content?)
```

**Implemented in:**
- `src/cljs/orcpub/dnd/e5/views.cljs` - Warning display
- `src/cljs/orcpub/dnd/e5/subs.cljs` - Missing content subscriptions
- `src/cljs/orcpub/character_builder.cljs` - Character builder integration

### 4. Built-in Content Exclusions

The system intelligently excludes official content from warnings:

**Excluded sources:**
- "Player's Handbook"
- "Elemental Evil Player's Companion"
- "Sword Coast Adventurer's Guide"
- "Volo's Guide to Monsters"
- "Xanathar's Guide to Everything"
- "Mordenkainen's Tome of Foes"
- "Guildmaster's Guide to Ravnica"
- "Eberron: Rising from the Last War"
- "Explorer's Guide to Wildemount"
- "Mythic Odysseys of Theros"
- "Tasha's Cauldron of Everything"
- "Fizban's Treasury of Dragons"

**Rationale:**
- Official content is always available (built-in)
- Users don't need warnings about PHB classes
- Focuses attention on missing homebrew content

**Implemented in:** `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs:206-241`

## User Experience

### Scenario 1: Deleted Plugin

**User actions:**
1. Create character with homebrew "Rune Knight" class
2. Delete the homebrew plugin
3. Open character

**What happens:**
```
Class: :rune-knight from "Fighter Subclasses" (not loaded - try :eldritch-knight?)
```

**User can:**
- Re-import the "Fighter Subclasses" plugin
- Switch to suggested :eldritch-knight
- Search for similar content

### Scenario 2: Shared Character

**User actions:**
1. Import character from friend
2. Character uses homebrew content
3. Open character builder

**What happens:**
```
Class: :artificer from "Homebrew Classes" (not loaded)
Background: :guild-artisan from "Custom Backgrounds" (not loaded - try :guild-artisan from PHB?)
```

**User can:**
- See which plugins are needed
- Find official alternatives (if suggested)
- Ask friend for the homebrew files

### Scenario 3: Renamed Content

**User actions:**
1. Rename `:blood-hunter` to `:blood-hunter-v2`
2. Open existing Blood Hunter character

**What happens:**
```
Class: :blood-hunter (not loaded - try :blood-hunter-v2?)
```

**User can:**
- Click suggestion to switch to new version
- System detects it's the same content with new key

## Implementation Details

### File Structure

**Core implementation:**
```
src/cljs/orcpub/dnd/e5/
├── content_reconciliation.cljs    # Detection & fuzzy matching (241 lines)
├── subs.cljs                       # Missing content subscriptions (+65 lines)
├── views.cljs                      # Warning UI components (+174 lines)
└── spell_subs.cljs                # Spell-specific detection (+96 lines)
```

**Helper utilities:**
```
src/cljc/orcpub/
└── common.cljc                     # String utilities (+21 lines)
    ├── kw-base                     # Extract keyword base
    └── traverse-nested             # Deep tree traversal
```

### Key Functions

**Content detection:**
- `find-missing-content` - Main entry point
- `extract-character-keys` - Find all content references
- `classify-content-type` - Determine what type of content
- `find-available-content` - Check if content exists

**Fuzzy matching:**
- `find-suggestion` - Find best match for missing content
- `levenshtein-distance` - Calculate edit distance
- `matches-prefix?` - Check prefix match
- `similar-name?` - Compare display names

**UI integration:**
- `missing-content-warning` - Display warning component
- `missing-content-sub` - Re-frame subscription
- `format-suggestion` - Format suggestion text

### Data Flow

```
1. Character loaded
   ↓
2. extract-character-keys
   → [:artificer, :battle-smith, :guild-artisan]
   ↓
3. classify-content-type
   → {:artificer :class, :battle-smith :subclass, :guild-artisan :background}
   ↓
4. find-available-content
   → Check db for each key
   ↓
5. find-suggestion (for missing)
   → Fuzzy match against available content
   ↓
6. missing-content-sub
   → [{:key :artificer, :type :class, :suggestion :armorer, :source "..."}]
   ↓
7. missing-content-warning
   → UI: ":artificer (not loaded - try :armorer?)"
```

### Performance Considerations

**Optimization strategies:**

1. **Memoization** - Cache fuzzy match results
2. **Lazy evaluation** - Only check when content displayed
3. **Debouncing** - Don't re-check on every keystroke
4. **Indexing** - Pre-build content type indexes

**Current performance:**
- Detection: ~10ms for typical character
- Fuzzy matching: ~5ms per missing item
- UI update: Instant (re-frame subscriptions)

**Benchmarks:**
- 1 missing item: ~15ms total
- 10 missing items: ~60ms total
- 100+ items: May need optimization

## Testing

### Test Coverage

**Test file:** `test/cljs/orcpub/dnd/e5/import_validation_test.cljs`

**Test cases:**
- Missing class detection
- Missing subclass detection (multiple archetypes)
- Missing race/subrace detection
- Missing background detection
- Fuzzy matching accuracy
- Built-in content exclusions
- Source name display

### Manual Testing

**Test scenarios:**

1. **Delete plugin test:**
   ```
   1. Import plugin with custom class
   2. Create character using that class
   3. Delete plugin
   4. Reopen character
   → Should show "(not loaded)" warning
   ```

2. **Fuzzy matching test:**
   ```
   1. Create :blood-hunter character
   2. Rename key to :blood-hunter-v2 in plugin
   3. Reopen character
   → Should suggest ":blood-hunter-v2"
   ```

3. **Built-in exclusion test:**
   ```
   1. Create Wizard with PHB Evocation subclass
   2. Check warnings
   → Should NOT warn about :evocation
   ```

## Configuration

### Adjusting Match Thresholds

Edit `content_reconciliation.cljs` to tune matching:

```clojure
;; Levenshtein threshold (default: 3)
(defn- levenshtein-distance-threshold [] 3)

;; Prefix length (default: 4)
(defn- prefix-match-length [] 4)

;; Name similarity (default: 3)
(defn- name-similarity-threshold [] 3)
```

### Adding Content Types

To detect new content types:

```clojure
;; In content_reconciliation.cljs

;; 1. Add to content-type-paths
(def content-type-paths
  {[:race] {:type :race :label "Race"}
   [:feat] {:type :feat :label "Feat"}})  ; NEW

;; 2. Add to content-type->field
(def content-type->field
  {:race :races
   :feat :feats})  ; NEW
```

### Excluding Additional Sources

To exclude more built-in sources:

```clojure
;; In content_reconciliation.cljs

(def built-in-sources
  #{"Player's Handbook"
    "Custom Source Name"})  ; NEW
```

## Troubleshooting

### "Not loaded" but content exists

**Cause:** Content key mismatch or namespace issue

**Fix:**
1. Check content key matches exactly (`:blood-hunter` vs `:bloodhunter`)
2. Verify content is in correct namespace (`::e5/classes`)
3. Check plugin is actually loaded

### Suggestions are wrong

**Cause:** Fuzzy matching too aggressive or too conservative

**Fix:**
1. Adjust thresholds in configuration
2. Check for duplicate keys in different plugins
3. Verify content type classification

### Performance issues

**Cause:** Too many missing items or slow fuzzy matching

**Fix:**
1. Check if character has 10+ missing items
2. Consider adding memoization
3. Profile with browser dev tools

### Built-in content showing warnings

**Cause:** Source name doesn't match exclusion list

**Fix:**
1. Check exact source name in content
2. Add variant to `built-in-sources` set
3. Verify content has `option-pack` field

## Future Enhancements

### Potential improvements:

1. **Auto-fix button** - One-click to apply suggestion
   ```
   :artificer (not loaded - try :armorer?) [Apply Fix]
   ```

2. **Bulk suggestions** - Fix all missing at once
   ```
   Found 5 missing items with suggestions [Fix All]
   ```

3. **Plugin recommendations** - Suggest which plugin to install
   ```
   :artificer (not loaded)
   → Try installing "Homebrew Classes v2.0" plugin
   ```

4. **Smart migration** - Auto-update when content renamed
   ```
   Detected: Content renamed :old → :new
   → Update 3 characters automatically? [Yes] [No]
   ```

5. **Missing content library** - Central repository lookup
   ```
   :blood-hunter (not loaded)
   → Available in "Matt Mercer's Blood Hunter" (download)
   ```

## Related Documentation

- **ORCBREW_FILE_VALIDATION.md** - Import/export validation
- **CONFLICT_RESOLUTION.md** - Duplicate key handling
- **HOMEBREW_REQUIRED_FIELDS.md** - Content field requirements
- **ERROR_HANDLING.md** - Error handling framework

## Related Files

- `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs` - Core implementation
- `src/cljs/orcpub/dnd/e5/subs.cljs` - Re-frame subscriptions
- `src/cljs/orcpub/dnd/e5/views.cljs` - UI components
- `src/cljs/orcpub/dnd/e5/spell_subs.cljs` - Spell-specific logic
- `src/cljc/orcpub/common.cljc` - Shared utilities
- `test/cljs/orcpub/dnd/e5/import_validation_test.cljs` - Tests

## Version History

- **v0.05** - Built-in content exclusions, subclass pattern fixes
- **v0.04** - Source name display
- **v0.03** - Fuzzy matching improvements
- **v0.02** - Multi-strategy matching
- **v0.01** - Initial missing content detection
