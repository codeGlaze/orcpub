# Views Builders Split

Deep-dive reference for the `builders.cljs` decomposition — Phase 2 of the views extraction refactor.

## Context

After the v2 views extraction (commit `a6990423`), `builders.cljs` was the largest remaining monolith at ~3,193 lines — 14 homebrew builder pages, their helpers, and shared infrastructure all in one file. This doc covers the split into 10 domain files + a shared toolkit.

## Architecture

### Dependency Rule (Critical)

```
builders.cljs (shared toolkit)
  ^        ^        ^
  |        |        |
feat   classes   race  ... (10 child files)
```

- **All child files import from builders.cljs only.**
- **No child→child dependencies.** Never `race.cljs` → `classes.cljs`.
- **No reverse deps.** builders.cljs never imports from any child.
- **Page wrappers move WITH their builder** to prevent circular deps. If a page wrapper stayed in builders.cljs, it would need to import the builder fn from the child, creating a cycle.

### What Stays in builders.cljs (~640 lines)

Pure shared infrastructure. No builder-specific code.

**Field factories:** `input-builder-field`, `value-to-item`, `builder-input-field`

**Plugin datalist:** `option-source-name-label`, `get-plugin-names`, `plugin-datalist`

**Proficiency choice helpers (used by 2+ builders):**
- `option-proficiency-choice` (base helper)
- `option-skill-proficiency-choice` (def, partial of above)
- `option-skill-expertise-choice` (def, partial of above)
- `option-skill-proficiency`, `option-languages`, `option-skill-proficiency-or-expertise`
- `option-tool-proficiency`, `option-tool-proficiency-or-expertise`
- `option-armor-proficiency`, `option-hps`
- `option-damage-resistance`, `option-damage-immunity`
- `option-weapon-proficiency`, `option-traits`, `option-saving-throw-advantages`

**Spell/level infrastructure:**
- `spell-selector`, `modifier-level-selector` — stay here because `option-spell` depends on them
- `option-spell`, `option-spells` — used by both race.cljs and classes.cljs

**Builder page infrastructure:** `builder-page`, `get-owner?`, `deletion-modal-with`, `title-with-help`, `selection-help`

**Not a homebrew builder:** `newb-character-builder-page` (character creation wizard)

### What Moves to Child Files

Every `*-builder`, `*-builder-page`, `*-input-field` wrapper, and domain-specific helper.

## File Manifest

| File | Lines | Contents |
|------|-------|----------|
| `builders/feat.cljs` | ~360 | 15 feat-* helpers + feat-builder + page |
| `builders/classes.cljs` | ~750 | class/subclass builders + modifier/level system |
| `builders/item.cljs` | ~530 | item builder + armor/weapon selectors + validation |
| `builders/monster.cljs` | ~300 | monster builder + 2 exclusive option helpers |
| `builders/race.cljs` | ~400 | race/subrace builders + proficiency choices |
| `builders/background.cljs` | ~210 | background builder + 5 checkbox helpers + 4 section helpers |
| `builders/spell.cljs` | ~120 | spell builder + component-checkbox |
| `builders/selection.cljs` | ~130 | selection builder + duplicate detection |
| `builders/language.cljs` | ~40 | language builder |
| `builders/warlock.cljs` | ~75 | invocation + pact boon (both Warlock features) |

## Decisions & Gotchas

### 1. `class` is a JS reserved keyword
File named `classes.cljs` (not `class.cljs`) because Google Closure munges `class` to `class$` with a compile warning. The data-layer namespace is already `classes` so this is consistent.

### 2. `spell-selector` and `modifier-level-selector` stay shared
These are NOT class-specific even though they were originally next to class-builder code. `option-spell` (shared, used by race + class) calls both. Moving them to classes.cljs would force `option-spell` to move too, which would create a child→child dep (race → classes).

Early in implementation, the classes.cljs agent created local copies. These were removed and replaced with imports from builders.cljs to avoid duplication and maintain the single-source-of-truth.

### 3. `option-*` naming convention — when to move
Most `option-*` helpers stay shared because 2+ builders use them. Exceptions:
- `option-damage-vulnerability` → monster.cljs (truly monster-only)
- `option-condition-immunity` → monster.cljs (truly monster-only)
- `option-weapon-proficiency-choice` → race.cljs (race-only)
- `option-language-proficiency-choice` → race.cljs (race-only)

Rule of thumb: move it only if verified single-caller AND the name prefix matches the domain.

### 4. `option-skill-expertise-choice` and `option-skill-proficiency-choice` are `def`s
These are `(partial option-proficiency-choice ...)`, not `defn`s. Easy to miss when grepping for function definitions.

### 5. `damage-dropdown-values` is a `def`, not `defn`
Only used by `modifier-values` (classes.cljs). Both moved together.

### 6. Small files are OK for consistency
`language.cljs` is ~40 lines. That's fine — a dev looking for "the language builder" will always find `builders/language.cljs`. Consistency matters more than minimum file size.

### 7. Boon + invocation share `warlock.cljs`
Both are Warlock class features, structurally identical (name + option-pack + description). Combining them is thematically natural and avoids two 30-line files.

### 8. combat.cljs needs NO changes
All of combat.cljs's imports from builders.cljs (`input-builder-field`, `value-to-item`) remain in the shared toolkit.

## core.cljs Route Updates

10 new requires added, 13 route references changed. The `newb-character-builder-page` route stays as `views-builders/newb-character-builder-page` since it's not a homebrew builder.

## Verification

- `lein fig:build` — 0 warnings, 0 errors
- `lein test` — 206 tests, 945 assertions, 0 failures
