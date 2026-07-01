# Ranger Favored Enemy Language Fix

## Overview

Fixes GitHub issue #296: Ranger favored enemy language selection produces nil options, corrupting character data.

**Root cause:** `language-selection` did `(map language-map keys)` on language keys from `favored-enemy-types`. Keys like `:aquan`, `:gith`, `:bullywug` aren't in the base 16 languages, so the lookup returned nil. Nil flowed through `language-option` → `modifiers/language nil` → corrupted character data.

**Fix:** Three-layer fallback in `language-selection`: language-map lookup → corrections shim → generated entry. Never returns nil.

## The Corruption Chain (Before Fix)

```
favored-enemy-types
  → creature type has language keys (e.g., :fey → [:draconic :elvish ... :aquan])
    → language-selection does (map language-map keys)
      → language-map has 16 base languages, :aquan is NOT one of them
        → returns nil for :aquan
          → language-option receives nil name/key
            → modifiers/language nil
              → nil language persisted to character
```

24 of 53 unique language keys across `favored-enemy-types` and `humanoid-enemies` are not in the base 16 languages. These are exotic/creature-specific D&D languages (Aquan, Gith, Bullywug, etc.) that homebrew plugins may define.

## The Fix

### Three-Layer Fallback (`options.cljc:819-828`)

```clojure
(defn language-selection [language-map language-options]
  (let [{lang-num :choose lang-options :options} language-options
        languages (if (:any lang-options)
                    (vals language-map)
                    (map (fn [k]
                           (or (language-map k)                          ; 1. Exact match
                               (language-map (language-key-corrections k)) ; 2. Corrections shim
                               {:name (key-to-name k) :key k}))         ; 3. Generated fallback
                         (keys lang-options)))]
    (language-selection-aux languages lang-num)))
```

**Layer 1 - Language map lookup:** Checks the dynamic language-map (base 16 + any plugin-defined languages). If a homebrew plugin defines Aquan, it's used directly.

**Layer 2 - Corrections shim:** Handles legacy/misspelled keys. Currently maps `:primoridial` → `:primordial`. Existing characters may have saved the typo; this ensures they resolve correctly.

**Layer 3 - Generated fallback:** Creates `{:name (key-to-name k) :key k}` from the keyword itself. `:aquan` becomes `{:name "Aquan" :key :aquan}`. Guarantees a non-nil result for any key.

### Corrections Map (`options.cljc:812-817`)

```clojure
(def ^:private language-key-corrections
  {:primoridial :primordial})
```

The `:primoridial` typo existed in the fey enemy type data. Fixing the typo directly would break existing characters that saved the misspelled key. The corrections map acts as a backwards-compatible shim.

The typo was also fixed in the source data (`:fey` now uses `:primordial`), so new characters get the correct key. Old characters with `:primoridial` still resolve via the shim.

## Key Insight: Two Different "Key" Concepts

This is the most important architectural detail for understanding this fix.

### Language `:key` (data key)

The keyword like `:aquan`, `:elvish`. Stored in `favored-enemy-types`, used by `modifiers/language` to apply the language proficiency to a character. This is what the fix operates on.

### Option `::entity/key` (template key)

Derived from the display name via `name-to-kw`. When `language-option` creates an option, it calls `option-cfg` which does:

```clojure
{::key (or key (common/name-to-kw name))}
```

`language-option` does NOT pass `:key` to `option-cfg`, so the option `::key` is always derived from the display name. For "Aquan" → `:aquan`. This is what gets saved in character data for option matching.

**Why this matters:** The option tree matching system (`entity-item-with-key`) matches by `::entity/key`, not by language `:key`. So `key-to-name` and `name-to-kw` must round-trip correctly for saved characters to match their options.

### Round-Trip Safety

`key-to-name` (`:aquan` → `"Aquan"`) and `name-to-kw` (`"Aquan"` → `:aquan`) are inverses for standard naming. The fallback entry `{:name "Aquan" :key :aquan}` produces an option with `::key :aquan`, which matches any saved character selection with `::key :aquan`.

This breaks for non-standard naming (e.g., if a plugin defines Aquan as "Water Primordial"). In that case the option `::key` would be `:water-primordial`, not `:aquan`. But the fallback only activates when the plugin ISN'T loaded, so this is the correct behavior -- when the plugin IS loaded, layer 1 uses the plugin's definition.

## Language Sources in the System

### Where languages come from

The language-map is dynamic: base 16 languages + plugin-defined languages.

```
spell_subs.cljs subscription chain:
  ::language-map ← ::languages ← ::plugin-languages ← ::plugin-vals

  plugin-languages = (mapcat (comp vals ::e5/languages) plugins)
  final = (concat base-languages plugin-languages)
```

Languages are ONLY added to the map from the `:orcpub.dnd.e5/languages` content type in orcbrew files. There is a dedicated Language Builder in the homebrew UI for creating them.

### Where languages do NOT come from

- **Monster definitions:** Monster languages are stored as display strings on the monster object. They are NOT added to the language-map. Creating 100 custom monsters with "Aquan" listed as a language does not add `:aquan` to the language-map.

- **Race definitions:** Creating a custom race does NOT auto-create a language. The race builder lets you select from existing languages in the map. If you want a new language for your custom race, you must create it separately in the Language Builder.

- **Ranger favored enemy feature:** The ranger `favored-enemy-option` function (`classes.cljc:1659-1704`) only consumes from `favored-enemy-types` / `humanoid-enemies` data and passes keys to `language-selection`. It does not inspect monster data, extract language strings, or dynamically create language entries.

## Lifecycle: Pick → Remove Plugin → Re-add Plugin

What happens when a player picks a language, the plugin providing it is removed, then re-added:

1. **Pick:** Player selects "Aquan" from favored enemy languages. Character saves `::entity/key :aquan` in option tree, plus `modifiers/language :aquan` applies the proficiency.

2. **Plugin removed:** Language-map no longer has `:aquan`. Fallback (layer 3) generates `{:name "Aquan" :key :aquan}`. Option `::key` is `:aquan` (from `name-to-kw "Aquan"`). Saved character still matches because `::entity/key :aquan` = generated option `::key :aquan`.

3. **Plugin re-added:** Language-map has `:aquan` again. Layer 1 finds it. Plugin's definition is used instead of fallback. Option still matches because plugin likely names it "Aquan" too.

**Edge case:** If the plugin uses a non-standard name like "Aquan (Water Primordial)", the option `::key` becomes `:aquan--water-primordial-`, which won't match the saved `:aquan`. The character would need to re-select the language. This is inherent to how the option matching system works, not specific to this fix.

## Testing

### Test file: `test/cljc/orcpub/dnd/e5/favored_enemy_language_test.cljc`

6 tests, 384 assertions, 0 failures.

| Test | What it verifies |
|------|-----------------|
| `test-language-lookup-fallback-never-returns-nil` | Known keys use map entry, unknown keys get generated fallback |
| `test-no-nil-in-favored-enemy-language-lookups` | Every key in `favored-enemy-types` resolves to non-nil |
| `test-no-nil-in-humanoid-enemy-language-lookups` | Every key in `humanoid-enemies` resolves to non-nil |
| `test-primoridial-typo-corrected` | Fey uses `:primordial`; legacy `:primoridial` resolves via shim |
| `test-homebrew-languages-used-when-available` | Plugin-defined languages are preferred over fallback |
| `test-key-to-name-generates-readable-names` | `key-to-name` converts keywords to readable display names |

The test helper mirrors the fix logic:

```clojure
(def known-corrections {:primoridial :primordial})

(defn lookup-with-fallback [lang-map k]
  (or (lang-map k)
      (lang-map (known-corrections k))
      {:name (opt5e/key-to-name k) :key k}))
```

This is duplicated (not referencing the private var) because `@#'ns/var` doesn't work in ClojureScript. The comment in the test file notes to keep it in sync.

### Running the tests

```bash
# ClojureScript tests (includes this test file)
lein doo phantom test once
```

## Implementation Files

| File | What changed |
|------|-------------|
| `src/cljc/orcpub/dnd/e5/options.cljc:812-828` | `language-key-corrections` map + `language-selection` fallback |
| `src/cljc/orcpub/dnd/e5/options.cljc:3019` | Fixed `:primoridial` → `:primordial` in fey enemy type |
| `test/cljc/orcpub/dnd/e5/favored_enemy_language_test.cljc` | 6 behavioral tests |

## Design Decisions

### Why not remove exotic keys from `favored-enemy-types`?

Keys like `:aquan`, `:gith`, `:bullywug` are legitimate D&D languages. Homebrew plugins define them. Removing the keys would mean players never see those language options, even when the appropriate plugin is loaded. The fallback approach preserves the full D&D language ecosystem while preventing nil corruption.

### Why not remap exotic keys to base languages?

Mapping `:aquan` → `:primordial` (its parent elemental language) would be semantically wrong. A player who picks "Elemental" as their favored enemy and gets "Aquan" as their language choice should get Aquan, not Primordial. The fallback generates the correct display name from the key.

### Why a corrections map instead of just fixing the typo?

Existing characters may have `:primoridial` saved in their option tree. The corrections map ensures these characters still resolve to "Primordial" instead of getting a generated fallback named "Primoridial" (with the typo visible to the user). New characters get `:primordial` (the typo is fixed in the source data), while old characters are silently corrected.

### Why duplicate the corrections map in tests?

Clojure's `@#'ns/private-var` deref syntax doesn't work in ClojureScript. Since the test file is `.cljc` (cross-platform), it can't access the private var. Duplicating is the pragmatic choice -- the map has one entry and changes rarely.

## Related Documentation

- [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md) - Import/export validation
- [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md) - Missing content detection
- [ERROR_HANDLING.md](ERROR_HANDLING.md) - Error handling patterns
