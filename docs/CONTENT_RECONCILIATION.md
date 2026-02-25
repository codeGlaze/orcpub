# Content Reconciliation & Missing Content Detection

## Overview

Detects when characters reference missing homebrew content and suggests alternatives using fuzzy matching.

**Why this exists:** User deletes homebrew plugin, reopens character, sees missing content with no context. No way to know which plugin to reinstall or what similar content exists.

**Design decision:** Use multiple fuzzy matching strategies (exact key, prefix matching, keyword-base comparison, display name matching) to catch common cases: typos, versioning (`:blood-hunter-v2`), and renamed content.

**Key gotcha:** Must exclude SRD built-in content. The `available-content` subscription only includes plugin content, so SRD content (hardcoded in the app) would be false-flagged as missing without explicit exclusion sets.

## How It Works

### Missing Content Detection

Scans character options tree for `::entity/key` references, classifies each by content type, checks if key exists in loaded content, reports missing with suggestions.

**Supported types:** Classes, subclasses, races, subraces, backgrounds, feats

**Implementation:** `content_reconciliation.cljs`

### Content Type Detection

The character entity stores options differently based on selection type:
- **Single-select** (race, background, subrace) — stored as maps, path has no index
- **Multi-select** (class, feats) — stored as vectors, path includes indices

`annotate-content-type` strips integer indices from paths before matching:
- `[:class 0]` → `[:class]` → class
- `[:class 0 :martial-archetype]` → `[:class :martial-archetype]` → subclass
- `[:race]` → race (already index-free, single-select)
- `[:class 0 :levels 3 :asi-or-feat :feats 0]` → detected by `:feats` parent keyword → feat

### Fuzzy Matching

Three strategies find similar content (`find-similar-content`):

**1. Exact key match** (similarity 1.0)
```
Missing: :artificer
Available: :artificer (from different source)
```

**2. Prefix matching** (similarity 0.7)
```
Missing: :battle-smith-v2
Suggests: :battle-smith
```

**3. Keyword-base comparison** (similarity 0.8 via `common/kw-base`)
```
Missing: :blood-hunter-order-of-the-lycan
Suggests: :blood-hunter (same base before first dash)
```

**4. Display name similarity** (similarity 0.6)
```
Missing: :drunken_master
Suggests: :drunken-master (name matches after normalization)
```

### Built-in Content Exclusions

Excludes **SRD-only** content from warnings. This is NOT all PHB content — only what's hardcoded in the app:

| Type | SRD Built-ins | Everything else |
|------|--------------|-----------------|
| Classes | All 12 base classes | — |
| Races | 9 races + their subraces | — |
| Subclasses | 1 per class (Champion, Berserker, Lore, Life, Land, Open Hand, Devotion, Hunter, Thief, Draconic, Fiend, Evocation) | All others from plugins |
| Backgrounds | Acolyte only | All others from plugins |
| Feats | None | All from plugins |

**Critical distinction:** Non-SRD PHB content (Battle Master, Totem Warrior, Folk Hero, etc.) comes from plugins and SHOULD be flagged when plugins are removed. Earlier versions incorrectly excluded all PHB content.

### Warning UI

Displays in character builder via the import log overlay. Shows missing content with type, inferred source, and suggestions.

**Implementation:** `views/conflict_resolution.cljs` (display), `subs.cljs` (subscriptions)

## Data Flow

```
Character loaded
  → extract-content-keys (walks ::entity/options tree via traverse-nested)
  → annotate-content-type (classifies by path shape)
  → check-content-availability (compares against available-content subscription)
  → find-similar-content (fuzzy matching for suggestions)
  → generate-missing-content-report
  → ::char5e/missing-content-report subscription
  → UI overlay
```

## Key Files

- `content_reconciliation.cljs` — Detection, classification, fuzzy matching
- `subs.cljs` — `::char5e/available-content`, `::char5e/missing-content-report` subscriptions
- `common.cljc` — `kw-base`, `traverse-nested`, `name-to-kw` utilities
- `views/conflict_resolution.cljs` — UI display

## Common Scenarios

**Deleted plugin:** Character shows missing class/subclass/background/feat with suggestions for similar available content

**Shared character:** Friend's character uses homebrew → Warnings show which content types are missing

**Renamed content:** Updated `:blood-hunter` to `:blood-hunter-v2` → Old characters detect missing, prefix matching suggests new version

## Extending

**Add content types:** Add to `content-type-paths`, `content-type->field`, and `available-content` subscription in `subs.cljs`

**Adjust thresholds:** Edit similarity cutoff (0.3) in `find-similar-content`

**Add SRD exclusions:** Only add truly hardcoded content to `builtin-*` sets. Plugin content should NOT be excluded.

## Related Documentation

- [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md) — Import/export validation
- [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md) — Duplicate key handling
- [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md) — Content field requirements
