# Entity Build Pipeline — Architecture Reference

## Overview

`entity/build` is the core function that transforms a raw character entity (user's choices)
into a fully computed character sheet with derived stats. Every character in the app goes
through this pipeline.

```
Raw Entity  +  Template  →  entity/build  →  Built Character
(choices)      (rules)                        (derived stats)
```

## Key Files

| File | Role |
|------|------|
| `src/cljc/orcpub/entity.cljc` | `entity/build`, `apply-options`, `collect-modifiers-2` |
| `src/cljc/orcpub/template.cljc` | `selection-cfg`, `option-cfg` — template primitives |
| `src/cljc/orcpub/dnd/e5/template.cljc` | `template-selections` (12 params), `template` wrapper |
| `src/cljc/orcpub/dnd/e5/template_base.cljc` | `template-base` — all derived property calculations |
| `src/cljc/orcpub/dnd/e5/modifiers.cljc` | Modifier constructors: `race-ability`, `speed`, `level`, etc. |
| `src/cljc/orcpub/dnd/e5/options.cljc` | Option builders: `race-option`, `background-option`, `class-option` |
| `src/cljc/orcpub/dnd/e5/classes.cljc` | Class option functions: `warlock-option`, `fighter-option`, etc. |
| `src/cljc/orcpub/dnd/e5/character.cljc` | Reader functions: `ability-values`, `race`, `skill-proficiencies` |
| `src/cljc/orcpub/entity-spec.cljc` | `entity-val` — lazy property resolution |

## Build Pipeline Trace

```
entity/build (line 615)
  → build-aux (line 613)
    → apply-options (line 592-608)
      1. flatten-options: raw entity ::options tree → flat list with paths
      2. collect-modifiers-2: match flat options against template → extract modifiers
      3. sort by ::mods/order, compute dependency graph (kahn-sort)
      4. merge ::t/base (template-base) with ::values from raw entity
      5. apply-modifiers in topological order → built character
```

### collect-modifiers-2 (line 574-590)
This is where raw entity choices meet the template rules:
1. `make-path-map raw-entity` → `{[:race :elf] {}, [:class :warlock] {}, ...}`
2. `get-all-selections-aux-2 template path-map` → walks the template tree,
   includes only selections whose paths match user's choices
3. `make-template-option-map selections` → `{[:race :elf] option-cfg-for-elf, ...}`
4. For each user option, looks up template option → extracts `::t/modifiers`

## Data Structures

### Raw Entity
User's character choices. Nested options with `::entity/key` selectors:
```clojure
{::entity/options
 {:ability-scores {::entity/key :standard-roll
                   ::entity/value {::char5e/str 10 ::char5e/dex 14 ...}}
  :race {::entity/key :elf
         ::entity/options {:subrace {::entity/key :drow}}}
  :class [{::entity/key :warlock
           ::entity/options {:levels [{::entity/key :level-1 ...}]
                             :eldritch-invocations [...]}}]
  :background {::entity/key :spy}
  :feats [{::entity/key :keen-mind}]}}
```

### Template
Rules tree that defines available selections and their modifiers:
```clojure
{::t/base template-base              ; derived property calculations (300+ lines)
 ::t/selections [                     ; vector of selection-cfg
   (t/selection-cfg {:name "Race" :key :race
                     :options [(t/option-cfg {:name "Elf" :key :elf
                                             :modifiers [...]
                                             :selections [subrace-selection]})]})
   ...]}
```

### Built Character
Map of computed properties accessed via `entity-val` / `char5e/*` reader functions.
Properties are stored as metadata-wrapped closures that resolve lazily.

## template-selections: The 12 Parameters

```clojure
(defn template-selections
  [magic-weapon-options       ; 1. pre-built magic weapon option cfgs
   magic-armor-options        ; 2. pre-built magic armor option cfgs
   other-magic-item-options   ; 3. pre-built other magic item option cfgs
   weapon-map                 ; 4. {:club {weapon-data} ...}
   custom-and-standard-weapons; 5. vector of weapon objects
   spell-lists                ; 6. {:warlock {0 [:eldritch-blast] 1 [...]} ...}
   spells-map                 ; 7. {:eldritch-blast {spell-data} ...}
   backgrounds                ; 8. raw config maps → passed to opt5e/background-option
   races                      ; 9. raw config maps → passed to opt5e/race-option
   classes                    ; 10. ALREADY-BUILT option objects (from classes5e/*-option)
   feats                      ; 11. raw config maps → passed to opt5e/feat-option-from-cfg
   language-map]              ; 12. {:common {:name "Common" :key :common} ...}
  ...)
```

**Critical distinction:**
- `races`, `backgrounds`, `feats` = raw config MAPS (processed by option builders internally)
- `classes` = already-built option OBJECTS (output of `classes5e/warlock-option` etc.)
- All others = data maps/vectors

## Data Sources (cljc — cross-platform)

These can be used directly in `.clj` tests without re-frame:

| Data | Module | Var |
|------|--------|-----|
| Spell lists | `orcpub.dnd.e5.spell-lists` | `spell-lists` (also `phb-spell-lists`) |
| Spell map | `orcpub.dnd.e5.spells` | `spell-map` |
| Weapons map | `orcpub.dnd.e5.weapons` | `weapons-map`, `weapons` |
| All weapons (incl magic) | `orcpub.dnd.e5.magic-items` | `all-weapons-map` |
| Magic weapons | `orcpub.dnd.e5.magic-items` | `magic-weapons` |
| Magic armor | `orcpub.dnd.e5.magic-items` | `magic-armor` |
| Other magic items | `orcpub.dnd.e5.magic-items` | `other-magic-items` |

## Data Sources (cljs only — need inline configs for tests)

These are assembled in re-frame subscriptions in `spell_subs.cljs`:

| Data | Why cljs-only | Test workaround |
|------|--------------|-----------------|
| Race configs | `elf-option-cfg` function in `spell_subs.cljs:555` | Inline the config map in test |
| Background configs | `acolyte-bg` def in `spell_subs.cljs:444` | Inline minimal config map |
| Feat configs | Most `#_` commented in `options.cljc:1181+` | Inline config map |
| Language map | Built from list in `spell_subs.cljs:476` | `common/map-by-key` on inline list |
| Class options | `base-class-options` in `spell_subs.cljs:861` | Call `classes5e/*-option` directly (cljc!) |

### Race config format (input to opt5e/race-option)
```clojure
{:name "Elf"
 :key :elf
 :abilities {::char5e/dex 2}
 :size :medium
 :speed 30
 :darkvision 60
 :languages ["Elvish" "Common"]
 :modifiers [...]        ; additional modifiers
 :subraces [{:name "Drow" :key :drow :abilities {::char5e/cha 1} ...}]
 :traits [{:name "Fey Ancestry" :summary "..."}]}
```

### Background config format (input to opt5e/background-option)
```clojure
{:name "Spy"
 :key :spy
 :profs {:skill {:deception true :stealth true}
         :tool {:thieves-tools true}
         :tool-options {:choose 1 :options {:dice-set true ...}}}
 :traits [{:name "Criminal Contact" :summary "..."}]}
```

### Feat config format (input to opt5e/feat-option-from-cfg)
```clojure
{:name "Keen Mind"
 :key :keen-mind
 :description "increase INT by 1; always know north; recall anything within a month"
 :ability-increases #{::char5e/int}}
```
**IMPORTANT:** `feat-option-from-cfg` uses `:ability-increases` (a SET of ability keywords)
and `:description`, NOT `:modifiers` and `:summary`. The function internally calls
`feat-modifiers` which intersects the set with `character/ability-keys` to determine
which ability to increase by 1. For a single ability, it creates `(modifiers/ability kw 1)`.
For multiple abilities, it creates a selection to choose one.

The old `feat-option` function (used inline in `options.cljc:1181+`) takes a different
format with `:modifiers` and `:summary` — don't confuse the two.

## Option Builder Signatures (all in options.cljc)

```clojure
(opt5e/race-option spell-lists spells-map language-map weapon-map race-cfg)
(opt5e/background-option language-map weapon-map bg-cfg)
(opt5e/feat-option-from-cfg language-map spells-map spell-lists weapons feat-cfg)
```

## Class Option Signatures (in classes.cljc)

```clojure
(classes5e/warlock-option spell-lists spells-map plugin-subclasses-map
                          language-map weapon-map invocations boons)
;; plugin-subclasses-map: {} for no plugins
;; invocations: [] for no plugin invocations
;; boons: [] for no plugin boons
```

Most classes take 5 params (no invocations/boons):
```clojure
(classes5e/fighter-option spell-lists spells-map plugin-subclasses-map
                          language-map weapon-map)
```

## template-base Derived Properties

`template_base.cljc` defines 300+ lines of derived property calculations.
Key property chain for ability scores:

```
?base-abilities          ← from deferred-abilities modifier (raw entity value)
?race-ability-increases  ← from race-ability modifiers
?ability-increases       ← race + feat + other increases
?abilities               ← base + increases (with max/override logic)
?ability-bonuses         ← floor((ability - 10) / 2) for each
?str-mod, ?dex-mod, etc. ← individual ability bonuses
```

Other important derived properties:
- `?speed` (default 0, set by race `mod5e/speed`)
- `?levels` (map of class → level, set by `mod5e/level`)
- `?total-darkvision` (`?darkvision + ?darkvision-bonus`)
- `?race`, `?subrace` (string names, set by `mod5e/race`, `mod5e/subrace`)
- `?skill-profs` (map, built from `mod5e/skill-proficiency`)
- `?max-hit-points` (complex: CON mod × levels + class HD + bonuses)
- `?armor-class` (complex: depends on armor, shield, DEX, bonuses)

## Reader Functions (character.cljc)

```clojure
(char5e/ability-values built)    ; → {::char5e/str 10, ::char5e/dex 14, ...}
(char5e/race built)              ; → "Elf"
(char5e/subrace built)           ; → "Drow"
(char5e/base-land-speed built)   ; → 30
(char5e/levels built)            ; → {:warlock {:class-name "Warlock" :class-level 1}}
(char5e/darkvision built)        ; → 120
(char5e/skill-proficiencies built) ; → {:perception true, :deception true, ...}
(char5e/spells-known built)      ; → {level {[class-name spell-key] spell-data}}
```

## Modifier Constructors (modifiers.cljc) — Key Gotchas

- `(mod5e/race-ability ::char5e/dex 2)` → returns a **VECTOR** of 2 modifiers
  (one for `?ability-increases`, one for `?race-ability-increases`).
  **You don't need to flatten manually** — `entity.cljc` calls `(flatten modifiers)`
  at lines 553 and 589 during `collect-modifiers-2`. Both `race-option` and `subrace-option`
  in `options.cljc` rely on this automatic flattening.
- `(mod5e/subrace-ability ::char5e/cha 1)` → also returns a vector. Same flatten handling.
- `(mod5e/skill-proficiency :perception)` → **MACRO**, not a function. Cannot `map` over it.
  All other modifier constructors are regular functions:
  `weapon-proficiency`, `saving-throw-advantage`, `immunity`, `darkvision`, `speed`,
  `race`, `subrace`, `trait-cfg`, `alignment`, `language`, `equipment`.
- `(mod5e/deferred-abilities)` → reads `::entity/value` from raw entity for base ability scores.
- `(mod5e/level :warlock "Warlock" 1 8)` → `(class-key class-name level hit-die)`.

## Subrace Processing (options.cljc `subrace-option`)

Subraces are processed by `subrace-option` which:
- Auto-generates key from name via `common/name-to-kw` if no `:key` provided
  (e.g. "Dark Elf (Drow)" → `:dark-elf-drow-`)
- Sets `(modifiers/subrace name)` for the subrace name
- Compares subrace `:darkvision` against parent race's `:darkvision` —
  only adds `(modifiers/darkvision value)` if they differ
- Maps `(modifiers/subrace-ability k v)` over `:abilities`
- Concatenates `:modifiers` from the subrace config

## entity-val — Lazy Property Resolution (entity_spec.cljc)

Built character properties are stored as metadata-wrapped closures:
```clojure
(defn entity-val [entity k]
  (let [v (entity k)
        entity-fn? (:entity-fn? (meta v))]
    (if entity-fn? (v entity) v)))
```
Reader functions like `char5e/ability-values` call `entity-val` which checks for
`:entity-fn?` metadata. If present, it calls the function with the built entity
to resolve the lazy property.

## Spells: Known vs Schedule

Warlock (and other Pact Magic classes) uses **schedule mode**:
- `:spells-known-modes {"Warlock" :schedule}` — spells are determined by level
- Regular cantrips/spells (eldritch-blast, charm-person) do NOT appear in `spells-known`
- Only special spells appear in `spells-known`:
  - Book of Ancient Secrets rituals (level 1, qualifier "Book of Ancient Secrets Ritual")
  - Book of Shadows cantrips (level 0, qualifier "uses Book of Shadows")
  - At-will invocation spells (e.g. speak-with-animals from Beast Speech, qualifier "at will")

## Unmatched Entity Options

Entity options that don't match any template selection are silently skipped in
`collect-modifiers-2`. No error, no modifiers — just ignored. This means:
- Templates can be simpler than the entity expects (useful for testing)
- Missing feat configs just mean those feats produce no modifiers
- Missing tool/equipment options are harmless

## Strict Format Serialization (Datomic Persistence)

Characters are stored in Datomic in "strict" format and converted to entity format for
in-memory use. The strict format uses ordered vectors; the entity format uses maps.

```
Datomic (strict)  ←→  entity/from-strict / entity/to-strict  ←→  In-memory (entity)
```

Key functions in `entity.cljc`:
- `from-strict-selections` — vector → `array-map` (preserves insertion order at any size)
- `to-strict-selections` — iterates map entries → vector (order depends on map type)
- `from-strict` / `to-strict` — top-level converters, handle `:db/id`, `::values`, `::owner`

`character.cljc` wraps these with extra processing:
- `char/from-strict` = `entity/from-strict` → `vectorize-equipment` → `update-values-from-strict`
- `char/to-strict` = `clean-values` → `entity/to-strict`

**Gotcha — PersistentArrayMap threshold**: Clojure uses `PersistentArrayMap` (insertion-ordered)
for ≤8 keys, promotes to `PersistentHashMap` (arbitrary order) beyond 8. `from-strict-selections`
uses `apply array-map` to force `PersistentArrayMap` at any size. `assoc` on existing keys
does NOT promote; only adding a 9th+ NEW key promotes.

## Production Data Flow

In the running app, the 12 params come from re-frame subscriptions:

```
DB/plugins → re-frame subscriptions (spell_subs.cljs, equipment_subs.cljs)
           → template-selections (template.cljc)
           → template (template.cljc)
           → entity/build (entity.cljc)
           → built character
           → UI rendering
```

For tests, replace re-frame subscriptions with direct cljc data + inline configs.

## Drow/Spy/Keen Mind Status

These are `#_` commented in the production codebase (content management):
- Drow subrace: `spell_subs.cljs:578` — `#_` in `elf-option-cfg`
- Drow magic mods: `spell_subs.cljs:550` — `#_`
- Keen Mind feat: `options.cljc:1310` — `#_` in `feat-options`
- Spy background: `template.cljc:834` — `#_` as `criminal-background "Spy"`
- All built-in feats: `options.cljc:1181+` — every `feat-option` is `#_` commented,
  so `feat-options` returns an empty vector `[]`

For tests, inline these configs directly rather than trying to uncomment production code.
The test Drow config simplifies drow-magic-mods (skips spells, keeps ability/darkvision/
weapon profs) since testing spell modifiers from subrace magic isn't the goal.

## Built-in Feat Selection

`template-selections` line 1526-1531 builds the feat selection from TWO sources:
```clojure
(concat
  (opt5e/feat-options spell-lists spells-map)      ; built-in (ALL #_ commented → [])
  (map (partial opt5e/feat-option-from-cfg ...) feats))  ; from feats param
```
Since `feat-options` returns `[]`, ALL feats must come through the `feats` parameter.
The feat selection has `:min 0 :max 0` — feats are granted via class ASI selections
at specific levels (e.g. warlock levels 4, 8), which reference the feat selection
through the `::t/ref` system.
