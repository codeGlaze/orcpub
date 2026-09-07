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

### Can the pools be registered? Yes — all four, today. Are they extensible? No, and that is fine.

Checked the raw material for each (2026-09-07):

| pool | built-ins | entries carry `:key`? | option constructor | homebrew half |
|---|---|---|---|---|
| `:weapons` | `weapons/weapons` | ✅ | `weapon-proficiency-option` ✅ exists, sets `:key` | custom weapons exist — but via `::mi5e/custom-weapons`, the **server-backed magic-item seam**, not `plugin-vals` (D14 excludes it from the registry). Register **closed** over built-ins; custom weapons are a separate D14 question |
| `:armor` | `armor/armor-types` = `[:light :medium :heavy]` + `:shields` | keywords | inline in `homebrew-armor-prof-selection` (`options.cljc:1400`) — lift to a fn | none — closed |
| `:tools` | `equipment/tools` = musical ++ artisan ++ misc | ✅ | `tool-option` ✅ exists, sets `:key` | none — closed |
| damage types | `damage-types` — **defined twice**, `options.cljc:124` and `damage_types.cljc:3`, identical | keywords | inline in the resistance widgets | none — closed |

"Extensible" for these would mean a homebrew pack adding a new tool or damage type. None has a
plugin key or a builder; they are **vocabularies, not content types**. The direction doc's PINS
already place this: *"New skills (creating a brand-new skill, not granting one): adds to the skill
registry itself — different shape. Defer."* Same for the other three. A closed pool is exactly what
`:skills` already is; nothing about the registry needs them open.

**Damage types are not one pool — they are three.** A `:damage-types` entry "cold" has no single
modifier: resistance, immunity and vulnerability are different `mod5e/*` primitives. Expressing
that as a mode on the grant — `{:pool :damage-types :key :cold :as :resistance}` — puts a `cond`
over kinds inside `grant`, which is discipline 1's named failure. So: `:damage-resistances`,
`:damage-immunities`, `:damage-vulnerabilities` — three registry entries over the same 13 keywords,
each entry's `:options-fn` carrying its own modifier. **The same argument resolves the two points
left open above:** expertise is a second pool over the skill entries (`:skill-expertise`, whose
options carry `skill-prof-or-expertise`), not a flag on the `:skills` grant; and
`:saving-throw-advantage` stays an EFFECT — a pool per save-advantage flavour would be a pool of
one modifier over six fixed keywords, which is a `:multi-enum` parameter wearing a pool's clothes.

**Pools to register, final:** `:weapons`, `:armor`, `:tools`, `:damage-resistances`,
`:damage-immunities`, `:skill-expertise`, and `:skills-or-tools` for feat (a union — `skilled-selection`
hand-builds it today). Seven entries. `:damage-vulnerabilities` only if a character silo ever grants
one — today only monster writes it, and monster is a stat block.

## The 35 deletions — replacement and shim, one row each

> ⚠️ **CORRECTION (2026-09-07, same day).** An earlier version of this section said "zero new shim
> code." That was true of the compile path and false of the builder path, and the builder path is
> where users hit it. D34 says a released shape gets *a characterization test and a read-shim*; the
> arm is the test's subject, not the shim. Two shims are needed, described below.

**Compile path — nothing moves.** Deleting a widget removes what *writes* a key. Every arm that
*reads* one — `make-feat-modifiers`, `make-feat-selections`, `race-option`, `background-option`, the
cljs assembly — stays exactly as it is (D9). An old pack compiles through the unchanged arm and the
*character* is right. Import does not strip unknown keys (`bf/fields->spec` is optional-by-default;
`strip-export-blanks` drops only nils and empties), so `:grants` and legacy keys coexist in one item.

**Builder path — the read-shim (D34).** The author imports the old pack and opens the race to edit it.
`grant-rows` reads `:grants`; the old race has none; the widget that used to show `:props :skill-prof`
is deleted. **The form is blind to the item's own grants.** The author re-adds what looks missing and
saves — the item now carries both the invisible legacy key and the new row, and every character built
on it is double-granted. So `grant-rows` must derive rows from the legacy keys too — one reader per
key in the "writes today" column:

```
:props  {:skill-prof {:athletics true}}     →  row {:pool :skills :key :athletics}
:profs  {:language-options {:choose 2 …}}   →  row {:pool :languages :count 2 :filter #{…}}
:languages {"Elvish" true}                  →  row {:pool :languages :key :elvish}   ; via name-to-kw (D10)
```

This is real code — ~20 readers — and it is the first time on this branch the generator introduces a
**new key for an existing fact**. `effect-rows` never needed this: it reads `:props :ac-bonus`
directly, which *is* the storage. `grant-rows` is different in kind.

**Save path — do NOT migrate.** Legacy `language-selection` carries `:ref [:languages]`;
`grant-selection` deliberately carries no `:ref` (the prototype verified one breaks nested
addressing) and is addressed by nesting. **Those are different storage paths for a character's
pick.** If the builder rewrites an old race from `:profs` to `:grants` on save, every character
built on that race has its language choice at a path the race no longer produces — orphaned, the
D10 failure. So: legacy rows render **read-only** ("authored in the older format") and keep their
keys; only rows the author *adds* write to `:grants`. Converting an item is an explicit act, if
offered at all, and needs a test that a character's pick survives it — not built here.

**The one deliberate incompatibility** runs the other way: a pack carrying `:grants` exports as
format v2 and an *old* build declines it rather than loading it with the grants silently missing.

Per D34: each deleted widget is `#_`-struck with a date, and the arm it wrote to gets a
characterization test proving the grant row compiles to an equivalent `selection-cfg` / modifier —
the "pin" column. Removal of the struck widget after ~3 months, tracked in `backfill-ledger.md`.

| # | builder | widget (deleted) | writes today | kept readable by (the shim) | replaced by | pin |
|---|---|---|---|---|---|---|
| 1 | race | `language-checkboxes` | `[:languages "Elvish"]` (by NAME) | `race-option` → `(modifiers/language (name-to-kw …))` | `{:pool :languages :key :elvish}` | same `modifiers/language` on the built char |
| 2 | race | `option-language-proficiency-choice` | `[:profs :language-options {:choose n :options}]` | `language-selection` | `{:pool :languages :count n :filter #{…}}` | same options, min/max, `:profs` tag |
| 3 | race | `option-skill-proficiency` | `[:props :skill-prof k]` | `make-feat-modifiers :skill-prof` | `{:pool :skills :key k}` | `modifiers/skill-proficiency` |
| 4 | race | `option-skill-proficiency-choice` | `[:profs :skill-options {…}]` | `skill-selection` | `{:pool :skills :count n :filter}` | options + tag |
| 5 | race | `option-weapon-proficiency` | `[:props :weapon-prof k]` | `make-feat-modifiers :weapon-prof` | `{:pool :weapons :key k}` | `modifiers/weapon-proficiency` |
| 6 | race | `option-weapon-proficiency-choice` | `[:profs :weapon-proficiency-options {…}]` | `weapon-proficiency-selection-2` | `{:pool :weapons :count n :filter}` | options + tag |
| 7 | race | `option-armor-proficiency` | `[:props :armor-prof type]` | `make-feat-modifiers :armor-prof` | `{:pool :armor :key type}` | `modifiers/armor-proficiency` |
| 8 | race | `option-tool-proficiency` | `[:profs :tool k]` | `race-option` | `{:pool :tools :key k}` | `modifiers/tool-proficiency` |
| 9 | race | `option-damage-resistance` | `[:props :damage-resistance type]` | `make-feat-modifiers :damage-resistance` | `{:pool :damage-resistances :key type}` | `modifiers/damage-resistance` |
| 10 | race | `option-damage-immunity` | `[:props :damage-immunity type]` | `make-feat-modifiers :damage-immunity` | `{:pool :damage-immunities :key type}` | `modifiers/damage-immunity` |
| 11 | subrace | `option-languages` | `[:props :language k]` | `make-feat-modifiers :language` | `{:pool :languages :key k}` | `modifiers/language` |
| 12–13 | subrace | `option-skill-proficiency`, `-choice` | as 3–4 | as 3–4 | as 3–4 | |
| 14 | subrace | `option-weapon-proficiency` | as 5 | | | |
| 15 | subrace | `option-armor-proficiency` | as 7 | | | |
| 16 | subrace | `option-tool-proficiency` | as 8 (via `subrace-option`) | | | |
| 17–18 | subrace | `option-damage-resistance`, `-immunity` | as 9–10 | | | |
| 19 | class | `option-skill-proficiency-choice` | as 4 (via `level-option`) | `skill-selection` | as 4 | |
| 20 | class | `option-skill-expertise-choice` | `[:profs :skill-expertise-options {…}]` | `skill-prof-or-expertise` | `{:pool :skill-expertise :count n :filter}` | expertise-or-prof modifier |
| 21–22 | subclass | `option-skill-proficiency-choice`, `option-skill-expertise-choice` | as 19–20 | | | |
| 23 | background | `background-skill-proficiencies` | `[:profs :skill k]` | `background-option` | `{:pool :skills :key k}` | |
| 24 | background | `background-tool-proficiencies` | `[:profs :tool k]`, `[:profs :tool-options …]` | `background-option` | `{:pool :tools :key k}` / `{:pool :tools :count n}` | |
| 25 | background | `background-languages` | `[:profs :language-options :choose]` (count only) | `background-option` | `{:pool :languages :count n}` | |
| 26 | feat | `feat-skill-proficiency` | `[:props :skill-tool-choice n]` | `make-feat-selections :skill-tool-choice` | `{:pool :skills-or-tools :count n}` | `skilled-selection` ×n equivalent |
| 27 | feat | `feat-languages` | `[:props :language-choice n]` | `make-feat-selections :language-choice` | `{:pool :languages :count n}` | `language-selection-aux` equivalent |
| 28 | feat | `feat-weapon-proficiency` (choice half) | `[:props :weapon-prof-choice n]` | `make-feat-selections :weapon-prof-choice` | `{:pool :weapons :count n}` | |
| 29 | feat | `feat-armor-proficiency` (armor half) | `[:props :armor-prof type]` | as 7 | as 7 | |
| 30 | feat | `feat-damage-resistance` | as 9 | as 9 | as 9 | |
| 31 | feat | `option-skill-proficiency-or-expertise` | `[:props :skill-prof-or-expertise k]` | `make-feat-modifiers :skill-prof-or-expertise` | `{:pool :skill-expertise :key k}` | |
| 32 | feat | `option-tool-proficiency-or-expertise` | `[:props :tool-prof-or-expertise k]` | `make-feat-modifiers :tool-prof-or-expertise` | `{:pool :tools :key k}` — **open:** tool expertise has no pool yet | |
| 33 | feat | `feat-ability-increase-options` | `:ability-increases #{:str :con}` (legacy SET) | `feat-option-from-cfg` dual-format reader — set stays legacy forever | the SHARED `ability-increase-choices` spread widget (not a grant row) | `ability_increase_grant_test` feat-legacy-* already pins the set path |
| 34 | feat | `feat-speed-bonuses` | `[:props :speed n]` | `make-feat-modifiers :speed` | an EFFECT kind (`:number`), not a grant row | `modifiers/speed` |
| 35 | feat | `feat-spellcasting` ×3 | `[:props :magic-novice\|:ritual-casting\|:attack-spell true]` | `make-feat-selections` templates | **not yet** — waits for the spell pool + nested grants; stays as passthrough hiccup | |

Rows 1–32 are grant-row deletions and need the seven pool registrations, the node, **and the ~20
legacy readers** — the read-shim — before any widget is struck.
Row 33 is a widget swap to a shared widget that already exists. Row 34 is an `effect-rows` kind.
Row 35 stays until spells are a pool. **Not one of the 35 touches a compiler arm; all 32 grant rows need the read-shim.**

Two things the table makes visible that the summary did not:

- The three residue widgets that write **the same key from different silos** — `:armor-prof`,
  `:damage-resistance`, `:skill-prof` — collapse to one pin each, not one per builder: the
  characterization test is on the arm, and the arm is shared.
- **Feat's `feat-armor-proficiency` and `feat-weapon-proficiency` are half grant, half effect.**
  The armor widget also carries `:medium-armor-stealth` and `:medium-armor-max-dex-3` (effects); the
  weapon widget carries the dead `:improvised-weapons-prof`. Deleting the widget means the effect
  halves move to `effect-rows` kinds in the same commit, or they vanish from the form.

## OMV (`port/redesign-on-refactor`)

Checked. OMV's `views.cljs` diff is 1290+/3054− and touches **none** of the `option-*` builder
widgets; every OMV defn is a generic primitive — `option-menu`, `checkbox-options`, `chips-tray`,
`count-line`, `section-card`, `select-menu` (already ported), … So there is nothing to check these
six builders *against*. OMV is what a grant row is *built from*: `option-menu` is the natural pool
picker and `checkbox-options` the `:filter` control. That was recorded as the next borrow in
`frontend-redesign-parallel-work.md`; nothing here changes it.
