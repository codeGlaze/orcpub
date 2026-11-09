# Class proficiency flags: `false` means "also when multiclassing"

A class's `:profs :armor`, `:profs :weapon` and `:profs :tool` maps do not follow the usual
"`true` = on, `false` = off" rule for maps of flags. A key that is **present** grants the
proficiency; its **value** says who gets it:

| Stored | Means |
|---|---|
| `false` | proficient, as the starting class **and** when multiclassing into the class |
| `true` | proficient **only** as the starting class |
| key absent | not proficient |

The SRD data follows 5e's multiclassing table exactly (`classes.cljc`): Fighter is
`{:light false :medium false :heavy true :shields false}` and `:weapon {:simple false :martial
false}` (multiclassing into fighter gives everything but heavy armor); Barbarian is
`{:light true :medium true :shields false}` (multiclassing in gives shields only); Bard, Rogue and
Warlock are `{:light false}`.

## Who reads it, and how

- **The character build, classes:** `options.cljc` `class-option` passes the map's entries to
  `armor-prof-modifiers` / `weapon-prof-modifiers` / `tool-prof-modifiers`, which take each
  `[key first-class?]` pair; `modifiers/armor-proficiency` adds the starting-class check only when
  the value is truthy. They also accept a bare keyword or a `[:light true]` pair.
- **The character build, subclasses and backgrounds:** pass `(keys ...)`, so every key is granted
  whatever its value. Races and subraces pass tools through `common/true-keys` (a `false` tool is
  not granted there). The same field name means different things in different content types.
- **The class builder** has no armor, weapon or tool checkboxes, so it never writes these values;
  they come from hand-written or imported homebrew in the SRD's style.
- **The `.orcbrew` export** (`orcbrew_validation.cljs` `strip-export-blanks`) dropped every `false`
  map value as blank, so a class in the SRD's shape came back from an export with no weapon
  proficiencies and only its starting-class armor. Since `bd08631dc` (`fix/health-banner-contrast`,
  #82) it keeps `false` inside `:profs` `:armor`, `:weapon` and `:tool` (`strip-prof-blanks`).
  This was live: `strip-export-blanks` is on upstream `develop`.
- **The Markdown export** (`homebrew_markdown.cljc` `prof-grant-lines`) listed only `true` keys.
  Since the same commit a class lists `false` keys under "Armor" and `true` keys under
  "Armor (starting class only)"; a subclass lists every key.

Any new generic reader of homebrew (a cleaner, a diff, a validator) has to special-case these three
maps, or it repeats the export bug.

## A clearer shape (refactor, not started)

Say it in the data instead of in a boolean: a set for the starting class and one for
multiclassing, as 5e's own table does, e.g. `:armor #{:light :medium :heavy :shields}` and
`:multiclass-armor #{:light :medium :shields}`. Homebrew keys are user-facing, so it needs a
mapping that reads the old maps and a migration. Noted in `branches/refactor/main.md`, "Deferred
follow-ups".
