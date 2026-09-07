# Builder disposition audit — race, subrace, class, subclass, background, monster

For each widget in each unconverted builder: what it writes, what reads it, and what it BECOMES
under the pool/grant + generated-builder design. Same method as `feat-builder-audit.md`
(builder-key → compiler-arm), plus a **disposition** column so the E5 conversions are deletions
where they can be and ports only where they must be. Measured 2026-09-07 from `views.cljs` and
the assembly paths; nothing here is estimated.

## Legend

| disposition | meaning |
|---|---|
| **GRANT** | becomes one `grant-rows` row writing `:grants [{:pool p …}]`; the bespoke widget is deleted after a characterization test (D34) |
| **EFFECT** | becomes an `effect-rows` kind writing `:props`; needs a kind entry if there is not one yet |
| **FIELD** | a plain typed field (`:text` `:number` `:enum` `:multi-enum` `:boolean`) in the type's schema |
| **SHARED** | already a silo-generic widget (ASI / saves); wrap as a schema node, no rewrite |
| **HEADER** | name / source / description — `simple-content-builder` already |
| **BESPOKE** | stays hand-written for now; the reason is named |

**Where `:props` compiles** — the fact the whole table rests on. Not in the cljc `*-option` fns:
`plugin-modifiers` runs in the **cljs assembly subs** for race (`spell_subs.cljs:351`), subrace
(`:368`), subclass (`:674`), class (`:710`), and ASI via `compile-ability-grants` for background
(`:302`), race, subrace, subclass. **Background has no `:props` path** — `plugin-backgrounds` never
calls `plugin-modifiers` — so no EFFECT row can land there until it does (one line, same as the
others). Monster has its own path (`:1344`) that turns the same `:props` into stat-block text.

## Race — 152 lines, 19 widgets

| widget | writes | read by | disposition | pool |
|---|---|---|---|---|
| `race-input-field`, `plugin-datalist`, `textarea-field` | name / source / description | — | HEADER | |
| `labeled-dropdown` ×6 | size, speed, darkvision, … | `race-option` | FIELD | |
| `ability-increase-choices`, `ability-save-notes`, `save-proficiency-choices` | `:ability-increases`, `:save-proficiencies` | `compile-ability-grants` | SHARED | |
| `language-checkboxes` | `[:languages "Elvish"]` — **keyed by display NAME** | `race-option` → `(name-to-kw language)` | GRANT `:key` | `:languages` ✅ |
| `option-language-proficiency-choice` | `[:profs :language-options {:choose n :options {k true}}]` | `language-selection` | GRANT `:count` + `:filter` | `:languages` ✅ |
| `option-skill-proficiency` | `[:props :skill-prof k]` | `plugin-modifiers` | GRANT `:key` | `:skills` ✅ |
| `option-skill-proficiency-choice` | `[:profs :skill-options {:choose n :options}]` | `skill-selection` | GRANT `:count` + `:filter` | `:skills` ✅ |
| `option-weapon-proficiency` | `[:props :weapon-prof k]` | `plugin-modifiers` | GRANT `:key` | `:weapons` ✗ |
| `option-weapon-proficiency-choice` | `[:profs :weapon-proficiency-options {…}]` | `weapon-proficiency-selection-2` | GRANT `:count` + `:filter` | `:weapons` ✗ |
| `option-armor-proficiency` | `[:props :armor-prof type]` | `plugin-modifiers` | GRANT `:key` | `:armor` ✗ |
| `option-tool-proficiency` | `[:profs :tool k]` | `race-option` | GRANT `:key` | `:tools` ✗ |
| `option-damage-resistance` | `[:props :damage-resistance type]` | `plugin-modifiers` | GRANT `:key` | `:damage-types` ✗ |
| `option-damage-immunity` | `[:props :damage-immunity type]` | `plugin-modifiers` | GRANT `:key` | `:damage-types` ✗ |
| `option-spells` | `:spells [...]` | `race-option` | BESPOKE — spell pool, later | |
| `option-traits` | `:traits [...]` | `race-option` | BESPOKE — E3 vector rows | |

**10 GRANT rows.** `language-checkboxes` keys by display name — the D10 footgun — and the grant row
fixes it as a side effect. Three storage shapes for "which languages" on this one builder (§1 of
`pool-grant-map.md`).

## Subrace — 129 lines, 15 widgets

| widget | writes | read by | disposition | pool |
|---|---|---|---|---|
| header ×2 | | | HEADER | |
| `labeled-dropdown` ×4 | | `subrace-option` | FIELD | |
| `option-languages` | `[:props :language k]` | `plugin-modifiers` | GRANT `:key` | `:languages` ✅ |
| `option-skill-proficiency` / `-choice` | as race | | GRANT | `:skills` ✅ |
| `option-weapon-proficiency`, `option-armor-proficiency`, `option-tool-proficiency` | as race | | GRANT `:key` | `:weapons` `:armor` `:tools` ✗ |
| `option-damage-resistance` / `-immunity` | as race | | GRANT `:key` | `:damage-types` ✗ |
| `option-saving-throw-advantages` | `[:props :saving-throw-advantage ability]` | `plugin-modifiers` | EFFECT (multi-enum param) | |
| `option-hps` | `[:props :max-hp-bonus n]` | `plugin-modifiers` | EFFECT (number) | |
| `option-spells`, `option-traits` | | | BESPOKE | |

**8 GRANT, 2 EFFECT.** Note the language shape differs from race's (`:props :language` by key vs
`:languages` by name) for the same fact — the grant row unifies them.

## Class — 268 lines, 15 widgets

| widget | writes | read by | disposition |
|---|---|---|---|
| `class-input-field` ×3, `plugin-datalist`, `textarea-field` ×2 | | | HEADER |
| `labeled-dropdown` ×8, `level-factor`, `spellcaster` | hit die, spellcasting, … | `level-option` / cljs | FIELD — D27 reworks spell slots; leave |
| `option-skill-proficiency-choice` | `[:profs :skill-options]` | | GRANT `:count` + `:filter` — `:skills` ✅ |
| `option-skill-expertise-choice` | `[:profs :skill-expertise-options]` | `skill-prof-or-expertise` | GRANT — **open:** a `:skills` grant with an expertise mode, or its own arm |
| `option-level-modifiers` | `:level-modifiers [{:level n :type …}]` | `level-modifier` (vocab B) | BESPOKE — level-gated; D31 keeps the mode |
| `option-level-selections` | `:level-selections` | `level-selection` | BESPOKE |
| `starting-equipment-section` | equipment events | `srd_starting_equipment` | BESPOKE — its own vocabulary |
| `option-traits` | `:traits` | | BESPOKE — E3 |

**2 GRANT.** Class is mostly its own vocabularies (levels, equipment, spellcasting), which is why
it was always last. The grant node still lands here for skills.

## Subclass — 105 lines, 13 widgets

| widget | writes | disposition |
|---|---|---|
| header ×2 | | HEADER |
| `labeled-dropdown` ×2, `spellcasting` | | FIELD |
| ASI + saves ×3 | `:ability-increases` (behind the opt-in toggle) | SHARED |
| `option-skill-proficiency-choice`, `option-skill-expertise-choice` | as class | GRANT ×2 — `:skills` ✅ |
| `option-level-modifiers`, `option-level-selections`, `option-traits` | as class | BESPOKE |
| `subclass-spells` ×3 | `[<spells-kw> level i]` — spells by level | BESPOKE — spell pool, later |

## Background — 46 lines, 11 widgets

| widget | writes | read by | disposition | pool |
|---|---|---|---|---|
| `background-input-field`, `plugin-datalist`, `textarea-field` | name / source / **`:help`** | | HEADER — ⚠️ prose is in `:help`, not `:description` (`builder-form-schemas.md` §5) | |
| ASI + saves ×3 | | `compile-ability-grants` (`:302`) | SHARED | |
| `background-skill-proficiencies` | `[:profs :skill k]` | `background-option` | GRANT `:key` | `:skills` ✅ |
| `background-tool-proficiencies` | `[:profs :tool k]`, `[:profs :tool-options …]` | `background-option` | GRANT `:key` / `:count` | `:tools` ✗ |
| `background-languages` | `[:profs :language-options :choose]` — count only | `background-option` | GRANT `:count` | `:languages` ✅ |
| `background-starting-equipment` | `:equipment`, `[:treasure :gp]` | | BESPOKE | |
| `option-traits` | | | BESPOKE — E3 | |

**3 GRANT.** No `:props` path, so no EFFECT rows until `plugin-backgrounds` gets the one line.
Background's language widget is a bare count — a fourth language shape.

## Monster — 233 lines, 12 widgets — a stat block, not a character

| widget | writes | read by | disposition |
|---|---|---|---|
| `monster-input-field` ×4, `plugin-datalist`, `textarea-field` ×2, `input-builder-field` | | | HEADER / FIELD |
| `labeled-dropdown` ×10 | size, type, alignment, AC, CR, … | monster sub | FIELD |
| `option-languages` | `[:props :language k]` | `spell_subs.cljs:1344` → `"Common, Elvish"` | **FIELD** (`:multi-enum` → display text) |
| `option-damage-resistance` / `-immunity` / `-vulnerability`, `option-condition-immunity` | `[:props :damage-* type]` | `:1344` → display string | **FIELD** (`:multi-enum` → display text) |
| `option-traits` | | | BESPOKE — E3 |

**0 GRANT, 0 EFFECT — by design.** Monster reuses five `option-*` widgets, and on monster every one
of them means *"print this on the stat block"*, not *"give this to a character"*. The widget is
shared; the compile is not. Consequences:

- **Monster never embeds `grant-rows`.** Nothing a monster authors is a grant.
- **`:monster` must not appear in any pool's `:offerable-by`.** It did, on `:languages` — corrected
  2026-09-07.
- The five widgets become ordinary `:multi-enum` fields on monster and grant rows everywhere else.
  Same data key, two schemas — which is fine, because the schema is per type.

## What the six tables add up to

| | GRANT | EFFECT | FIELD | SHARED | BESPOKE |
|---|---:|---:|---:|---:|---:|
| race | 10 | 0 | 6 | 3 | 2 |
| subrace | 8 | 2 | 4 | 0 | 2 |
| class | 2 | 0 | 10 | 0 | 4 |
| subclass | 2 | 0 | 3 | 3 | 5 |
| background | 3 | 0 | 0 | 3 | 2 |
| monster | 0 | 0 | 15 | 0 | 1 |
| **feat** (own audit) | 10 | 8 | 0 | 1 | 3 |
| **total** | **35** | **10** | | | |

**Thirty-five widget instances become one node.** That is the E5 conversion, and it is deletion,
not porting. The BESPOKE column is three things in every builder: `:traits` (E3 vector rows —
six builders), `:spells` / `subclass-spells` (the spell pool), and class/subclass's level
vocabularies (D31 keeps the level-gated mode; not this work).

### Pools to register before the grant node can replace those widgets

Four entries in `grant_pools.cljc`, each a fixed vocabulary with no homebrew half (like `:skills`):

| pool | built-ins | option constructor exists? |
|---|---|---|
| `:weapons` | `weapons/weapons` (+ custom, via `custom-and-standard-weapons`) | `weapon-proficiency-option` ✅ |
| `:armor` | `armor/armor-types` + `:shields` | `homebrew-armor-prof-selection` builds them inline |
| `:tools` | `equipment/tools` | `tool-proficiency-selection-2` builds them inline |
| `:damage-types` | `opt/damage-types` | inline in the resistance widgets |

Plus `:skills-or-tools` for feat (a union pool — `skilled-selection` hand-builds it today).

### Two open design points the tables surface

1. **Expertise.** `option-skill-expertise-choice` writes `:skill-expertise-options`, compiled by
   `skill-prof-or-expertise` (proficiency, or expertise if already proficient). As a grant that is
   either a `:skills` grant carrying `:expertise? true`, or a separate `:skill-expertise` pool over
   the same entries. The first is one flag on the row; the second is a second pool for the same
   things. Lean first; decide before E4 lands on class.
2. **Resistance-shaped effects.** `:saving-throw-advantage` is a map-of-flags over abilities, like
   `:damage-resistance` over damage types — but "advantage on saves" reads as a *mechanic with a
   parameter*, not "gain a thing from a pool". Tabled as EFFECT above. If `:damage-resistance` is a
   GRANT, the line between them is the pool: damage types are a real vocabulary people extend
   (FTD adds them); abilities are six and fixed. Revisit if it feels wrong when the rows render.

## OMV (`port/redesign-on-refactor`)

Checked. OMV's `views.cljs` diff is 1290+/3054− and touches **none** of the `option-*` builder
widgets; every OMV defn is a generic primitive — `option-menu`, `checkbox-options`, `chips-tray`,
`count-line`, `section-card`, `select-menu` (already ported), … So there is nothing to check these
six builders *against*. OMV is what a grant row is *built from*: `option-menu` is the natural pool
picker and `checkbox-options` the `:filter` control. That was recorded as the next borrow in
`frontend-redesign-parallel-work.md`; nothing here changes it.
