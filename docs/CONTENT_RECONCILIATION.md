# Content Reconciliation & Missing Content Detection

## Overview

Detects when characters reference missing homebrew content and suggests alternatives using fuzzy matching.

**Why this exists:** User deletes homebrew plugin, reopens character, sees `:artificer (not loaded)` with no context. No way to know which plugin to reinstall or what similar content exists.

**Design decision:** Use multiple fuzzy matching strategies (exact key, Levenshtein distance, prefix matching) to catch common cases: typos, versioning (`:blood-hunter-v2`), and renamed content.

**Key gotcha:** Must exclude built-in content (PHB, Xanathar's, etc.) or system suggests switching from homebrew Artificer to PHB Artificer (which doesn't exist in most books).

## How It Works

### Missing Content Detection

Scans character options tree for `::entity/key` references → Checks if key exists in loaded content → Reports missing with suggestions

**Supported types:** Classes, subclasses, races, subraces, backgrounds

**Implementation:** `content_reconciliation.cljs`

### Fuzzy Matching

Four strategies find similar content:

**1. Exact key, different source**
```
Missing: :artificer from "Serakat's Compendium"
Suggests: :artificer from "Player's Handbook"
```

**2. Levenshtein distance** (max 3 edits for typos)
```
Missing: :artficer
Suggests: :artificer
```

**3. Prefix matching** (min 4 chars, for versioning)
```
Missing: :battle-smith-v2
Suggests: :battle-smith
```

**4. Display name similarity** (max 3 edits)
```
Missing: :drunken_master
Suggests: :drunken-master
```

**Why multiple strategies:** Single strategy missed too many cases. Levenshtein alone doesn't catch versioning (`:fighter-v2`). Prefix alone doesn't catch typos (`:artficer`). Combined approach catches ~80% of common cases.

### Warning UI

Displays in character builder:
```
:missing-content (not loaded)
:missing-content (not loaded - try :suggested-content?)
:missing-content from "Plugin Name" (not loaded - try :suggested-content?)
```

**Implementation:** `views.cljs` (display), `subs.cljs` (subscriptions)

### Built-in Content Exclusions

Excludes PHB, Xanathar's, Tasha's, and 9 other official books from warnings.

**Why:** Built-in content is always available. Without exclusion, system suggests "try PHB Artificer" when user's homebrew Artificer is missing (but PHB doesn't have Artificer in 5e).

## Common Scenarios

**Deleted plugin:** Character shows `:rune-knight from "Fighter Subclasses" (not loaded - try :eldritch-knight?)` → Re-import plugin or use suggested alternative

**Shared character:** Friend's character uses homebrew → Warnings show which plugins needed → Ask friend for files or use suggested official alternatives

**Renamed content:** Updated `:blood-hunter` to `:blood-hunter-v2` → Old characters suggest new version → Prefix matching catches versioning

## Implementation

**Key files:**
- `content_reconciliation.cljs` - Detection, fuzzy matching (`find-missing-content`, `find-suggestion`, `levenshtein-distance`)
- `subs.cljs`, `views.cljs` - UI integration (subscriptions, warning display)
- `common.cljc` - Utilities (`kw-base`, `traverse-nested`)
- `import_validation_test.cljs` - Tests

**Data flow:**
Character loaded → `extract-character-keys` → `classify-content-type` → `find-available-content` → Missing? → `find-suggestion` → Display warning with suggestion

**Performance:** ~10ms detection + ~5ms per missing item for fuzzy matching. 100+ missing items may need optimization.

## Testing

**Automated:** `import_validation_test.cljs` - Covers detection, fuzzy matching accuracy, built-in exclusions

**Critical manual tests:**
1. Delete plugin → Reopen character → Should show "(not loaded)" warning
2. Rename `:blood-hunter` to `:blood-hunter-v2` → Should suggest new version
3. PHB Wizard with Evocation → Should NOT warn (built-in exclusion)

## Extending

**Adjust thresholds:** Edit `levenshtein-distance-threshold`, `prefix-match-length`, `name-similarity-threshold` in `content_reconciliation.cljs` (defaults: 3, 4, 3)

**Add content types:** Add to `content-type-paths` and `content-type->field` maps

**Exclude sources:** Add to `built-in-sources` set

## Troubleshooting

**"Not loaded" but exists:** Check key matches exactly (`:blood-hunter` vs `:bloodhunter`), verify plugin loaded

**Wrong suggestions:** Adjust matching thresholds, check for duplicate keys

**Built-in showing warnings:** Source name doesn't match exclusion list exactly, add variant to `built-in-sources`

## Future Enhancements

**Auto-fix button:** One-click apply suggestion

**Smart migration:** Auto-update characters when content renamed (detect renames, prompt to update all affected characters)

**Plugin recommendations:** Suggest which plugin to install based on missing content library lookup

## Related Documentation

- [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md) - Import/export validation
- [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md) - Duplicate key handling
- [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md) - Content field requirements
