# SRD 2024 — coverage map

**What has actually been analysed, what has not, and what each still needs.** Written
because the work to this point was driven turn by turn rather than from a list, so coverage
was uneven and nobody could tell what was missing. Update the status column as things land;
`srd-2024-implementation-notes.md` holds the design detail.

Left column is what the app ships for 5e today; right is what SRD 2024 provides in the open5e
per-publisher data (`data/v2/wizards-of-the-coast/srd-2024`).

## Content

| type | e5 ships | 2024 source | status | note |
|---|---|---|---|---|
| spells | 319 | `Spell.json` (339) | **PARTIAL** | `e55/spells.cljc` holds 109 entries. Text compared by word, not by meaning: 312 of 317 shared spells differ in some wording, which mixes terminology, flavour, moved clauses, real rule changes and source errors. Classification pending. Fields verified; text and mechanical fields not carried |
| magic items | **805** | `MagicItem.json` | **DONE** | `e55/magic_items.cljc`, 32-entry delta, verified |
| species / races | in `template.cljc` | `Species` (9), `SpeciesTrait` (51) | **SHAPE ONLY** | lineages need level-gated grants |
| backgrounds | 1, in `spell_subs.cljs` | `Background` (4), `BackgroundBenefit` (20) | **SHAPE ONLY** | needs ASI + origin-feat grants; no data namespace exists |
| feats | — | `Feat` (17), `FeatBenefit` (35) | **COUNTED** | 1 -> 17; four are fighting styles. Not extracted |
| monsters | **317** | `Creature`, `CreatureAction`, `CreatureTrait`, `CreatureActionAttack` | **NOT STARTED** | stat-block format differs; editions need separate parsers |
| classes | **12** | `CharacterClass`, `ClassFeature`, `ClassFeatureItem` | **NOT STARTED** | largest unknown, ~620KB of 2024 features |
| equipment | 162 | `Item.json` | **NOT STARTED** | |
| weapons | 40 | `Weapon`, `WeaponProperty`, `WeaponPropertyAssignment` | **NOT STARTED** | the "Light property" question lives here |
| armor | 14 | `Armor.json` | **NOT STARTED** | |
| languages | 16 | — | **NOT CHECKED** | no obvious 2024 counterpart file |
| skills | 18 | `SkillDescription.json` | **NOT CHECKED** | |

## Rules and reference (no e5 data namespace)

| type | 2024 source | status |
|---|---|---|
| conditions | `ConditionDescription.json` | **NOT CHECKED** |
| rules / rulesets | `Rule.json`, `RuleSet.json` | **NOT CHECKED** — likely where grapple/exhaustion/surprise changes live |
| spellcasting options | `SpellCastingOption.json` (230KB) | **NOT CHECKED** — may carry the higher-level/upcast structure |
| damage types, creature types, alignments, abilities | several small `*Description.json` | **NOT CHECKED** |
| services, cross-references | `Service(s).json`, `CrossReference.json` | **NOT CHECKED** |

## App-side work, no SRD counterpart

| item | status |
|---|---|
| the option-source gate (fails closed, no writer since 2017) | characterized by test; unfixed |
| version filter at the pool layer | designed, not built |
| render-time name qualifier for colliding entries | designed, not built |
| `scan-licensed-content.sh` allowlist for content outside `dnd/e5/` | will fail when 2024 content lands; not addressed |

## Known gaps in what IS done

- **`e5` spell text vs SRD 5.1:** 274 verbatim, 30 near-verbatim, 9 edited, 6 rewritten or
  abridged. Of the 15 that depart, `e5` is the one that differs in 8, open5e in 4, and in 3 both
  are SRD text covering different parts. Detail in the roadmap's known debt.

- `e55/spells.cljc`: one description (Guards and Wards) still ends early; cause not established.
- The spell delta keyed on FIELDS only and called 230 spells identical. Comparing open5e's `desc`
  text for both editions, **5** are word-identical. The PDF-based text diff tried earlier was
  retracted as unreliable; open5e's structured text is clean enough to settle it.
- Magic items were never checked against a rendered page, only against extracted text.


## Counting these files correctly

The e5 counts above were wrong four times before they were right, always the same way: a
`grep -c ':name "'` counts **nested** names — a monster's actions and traits, a class's
features — and the result looks plausible. Monsters were reported as 1780 when there are 317;
classes as 506 "entries" when the namespace has 38 public vars; magic items as 283 when there
are 805, because that file keys items with the symbol `name-key` rather than a literal
`:name`, so a grep for one convention misses the other.

Parsing the file as EDN is not the fix either. These files carry many `def` forms — magic
items has 24, equipment 23, spells 29 — so reading the first vector counts one category and
reports it as the file.

Counting the namespace's public vars is no better: `orcpub.dnd.e5.classes` exports 38, of
which 12 are classes and the rest are helpers and sub-features. The SRD has 12 classes, and so
does the app.

**Count by loading the namespace and counting the var** — the specific var that holds the
content, not the namespace's exports. It is the only method that cannot be
fooled by nesting or by a file's internal conventions:

    (require '[orcpub.dnd.e5.monsters :as mon])
    (count @(resolve 'orcpub.dnd.e5.monsters/monsters))   ;; => 317

The same caution applies to imported data. The open5e magic-item contamination figure was
first measured at the expanded level (396 of 1959 names) and only made sense at base-item
level (906 of 1218), where it is nearly four times worse.
