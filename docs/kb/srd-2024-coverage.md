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
| spells | 319 | `Spell.json` (339) | **DONE** | `e55/spells.cljc`, 109-entry delta, verified by loading and against the rendered page |
| magic items | 283 | `MagicItem.json` | **DONE** | `e55/magic_items.cljc`, 32-entry delta, verified |
| species / races | in `template.cljc` | `Species` (9), `SpeciesTrait` (51) | **SHAPE ONLY** | lineages need level-gated grants |
| backgrounds | 1, in `spell_subs.cljs` | `Background` (4), `BackgroundBenefit` (20) | **SHAPE ONLY** | needs ASI + origin-feat grants; no data namespace exists |
| feats | — | `Feat` (17), `FeatBenefit` (35) | **COUNTED** | 1 -> 17; four are fighting styles. Not extracted |
| monsters | 1780 | `Creature`, `CreatureAction`, `CreatureTrait`, `CreatureActionAttack` | **NOT STARTED** | stat-block format differs; editions need separate parsers |
| classes | 506 | `CharacterClass`, `ClassFeature`, `ClassFeatureItem` | **NOT STARTED** | largest unknown, ~620KB of 2024 features |
| equipment | 179 | `Item.json` | **NOT STARTED** | |
| weapons | 45 | `Weapon`, `WeaponProperty`, `WeaponPropertyAssignment` | **NOT STARTED** | the "Light property" question lives here |
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

- `e55/spells.cljc`: one description (Guards and Wards) still ends early; cause not established.
- The spell delta keys on FIELDS only. A spell whose wording changed but whose level, school,
  casting time, range and duration did not is currently counted as identical. The text diff that
  would catch those was attempted and retracted as unreliable, so **109 is a floor**.
- Magic items were never checked against a rendered page, only against extracted text.
