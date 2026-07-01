# Homebrew Required Fields

This document tracks which fields are required for each homebrew content type.
Fields marked "SPEC REQUIRED" are validated by clojure.spec.
Fields marked "FUNCTIONAL REQUIRED" will break features if missing (PDF export, character building, etc.)

## Legend
- **SPEC**: Defined in spec as `:req-un`
- **FUNCTIONAL**: Will break something if missing/empty
- **DEFAULT**: Can have a sensible default applied
- **OPTIONAL**: Truly optional, no issues if missing

---

## Classes (::homebrew-class)

**Spec file**: `src/cljc/orcpub/dnd/e5/classes.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |
| `:hit-die` | NO | **YES** | 6 | `options.cljc:2630` - string interpolation without nil-guard |
| `:ability-increase-levels` | NO | **YES** | [4,8,12,16,19] | `options.cljc:2742` - passed to `set()` without nil-guard |
| `:level-modifiers` | NO | CONDITIONAL | [] | Breaks only if `:traits` also nil |
| `:traits` | NO | **YES** | [] | `options.cljc:2782` - passed to `filter()` without nil-guard |
| `:spellcasting` | NO | NO | - | Uses `some->` with graceful nil-handling |

**Breaking code locations:**
- `options.cljc:2630`: `{::t/name (str "Roll (1D" die ")")}` → produces "Roll (1Dnil)"
- `options.cljc:2635`: `(dice/die-mean-round-up die)` → dies if nil
- `options.cljc:2742`: `(set ability-increase-levels)` → fails if nil
- `options.cljc:2782`: `(filter ... traits)` → can't iterate nil

---

## Subclasses (::homebrew-subclass)

**Spec file**: `src/cljc/orcpub/dnd/e5/classes.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:class` | YES | YES | - | Parent class key |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |
| `:level-modifiers` | NO | NO | [] | Handled gracefully with `some->` |

---

## Races (::homebrew-race)

**Spec file**: `src/cljc/orcpub/dnd/e5/races.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |
| `:languages` | OPT | NO | - | Optional in spec, not in critical paths |
| `:speed` | NO | **YES** | 30 | `options.cljc:1984` - compared without nil-guard in subrace |
| `:abilities` | NO | NO | {} | Uses `:or` defaults |
| `:size` | NO | NO | "Medium" | Uses `some->` or defaults |
| `:darkvision` | NO | NO | - | Uses conditional checks |

**Breaking code location:**
- `options.cljc:1984`: `(not= speed (:speed race))` - accessed without nil-checking

---

## Subraces (::homebrew-subrace)

**Spec file**: `src/cljc/orcpub/dnd/e5/races.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:race` | YES | YES | - | Parent race key |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |

---

## Backgrounds (::homebrew-background)

**Spec file**: `src/cljc/orcpub/dnd/e5/backgrounds.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |

---

## Feats (::homebrew-feat)

**Spec file**: `src/cljc/orcpub/dnd/e5/feats.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |

---

## Spells (::homebrew-spell)

**Spec file**: `src/cljc/orcpub/dnd/e5/spells.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |
| `:level` | YES | YES | - | `spells.cljc:26` - required by spec |
| `:school` | YES | YES | - | `spells.cljc:26` - required by spec, used in spell card |
| `:spell-lists` | YES | YES | - | `spells.cljc:45-47` - required for homebrew |

---

## Monsters (::homebrew-monster)

**Spec file**: `src/cljc/orcpub/dnd/e5/monsters.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |
| `:hit-points` | YES | YES | - | `monsters.cljc:15` - required by spec |

---

## Languages (::homebrew-language)

**Spec file**: `src/cljc/orcpub/dnd/e5/languages.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |

---

## Invocations (::homebrew-invocation)

**Spec file**: `src/cljc/orcpub/dnd/e5/classes.cljc`

| Field | SPEC | FUNCTIONAL | DEFAULT | Notes |
|-------|------|------------|---------|-------|
| `:name` | YES | YES | - | Display name |
| `:key` | YES | YES | - | Unique identifier |
| `:option-pack` | YES | YES | "Unnamed Content" | Source/library name |

---

## Known Issues

### nil nil key-value pairs
- **Symptom**: `{nil nil, :key :foo}` in exported content
- **Cause**: Empty fields serialized during export, likely from missing functional-required fields
- **Impact**: Causes PDF export black screen
- **Fix**: Cleaned on import (v0.05), should prevent at export with validation

### Empty option-pack
- **Symptom**: `:option-pack ""`
- **Cause**: User didn't fill in source name
- **Impact**: Content appears under unnamed source
- **Fix**: Cleaned on import, defaults to "Unnamed Content"

---

## Recommendations

### Immediate: Add nil-guards
These code paths should have nil-guards added:

1. `options.cljc:2630` - Add `(or die 6)` before string interpolation
2. `options.cljc:2742` - Add `(or ability-increase-levels [])` before set creation
3. `options.cljc:2782` - Add `(or traits [])` before filter
4. `options.cljc:1984` - Add nil-check before `(not= speed (:speed race))`

### Export Validation
Add these to spec as `:req-un` for homebrew content:
- `::homebrew-class` needs `:hit-die`
- `::homebrew-class` needs `:ability-increase-levels`
- `::homebrew-class` needs `:traits`
- `::homebrew-race` needs `:speed`

---

## Completed (2026-01-15)

- [x] Test each "?" field to determine if it breaks functionality
- [ ] Add export validation to prevent incomplete content
- [x] Document what breaks (PDF, character builder, etc.) for each field
- [x] Identify likely source of nil nil entries (empty functional-required fields)
