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

## SRD membership per 5etools (verified 2026-10-06)

5etools marks SRD entries with `srd` (5.1) and `srd52` (5.2.1). Counts, with what they match:

| content | `srd` | `srd52` | check |
|---|---|---|---|
| spells | 319 | 339 | both SRDs exactly |
| monsters | 322 | 330 | 2014 matches open5e (322) and the PDF (323); 2024 within 2 of the PDF's 332 |
| species | 9 | 9 | matches |
| backgrounds | 1 | 4 | matches |
| feats | 1 | 17 | matches |
| conditions | 15 | 15 | matches |
| items | 493 | 456 | **reconcile** — includes ordinary gear; the magic-item share does not match the PDF's ~231 / ~237 |
| 2014 subraces | 9 | — | **reconcile** — open5e records 4 |

## Breadth survey — phase 1 (2026-10-06)

Run on 5etools data (both editions, one format, SRD flags), with open5e and `e5` for counts.
"Same name" pairs 2014 and 2024 entries by SRD name. Text similarity is word-level over the
entry's descriptive text with 5etools markup stripped; 60% is the provisional diff threshold.

| type | 5.1 | 5.2.1 | same name | 2014 only | 2024 only | text: identical / ≥60% / <60% | storage model that fits |
|---|---|---|---|---|---|---|---|
| spells | 319 | 339 | 317 | 2 | 22 | 7 / 220 / 90 | diff, mostly |
| monsters | 322 | 330 | 285 | 37 | 45 | 0 / 49 / 236 | **mostly full**; identity map for renames and splits |
| magic items | 313 | 320 | 287 | 26 | 33 | 23 / 199 / 65 | diff, mostly |
| gear | 180 | 136 | 101 | 79 | 35 | 24 / 32 / 45 | mixed; many renames |
| weapons | 37 | 38 | 36 | 1 | 2 | *not meaningful* | data rows; new `mastery` field |
| armor | 13 | 13 | 13 | 0 | 0 | *not meaningful* | data rows |
| species | 9 | 9 | 7 | 2 | 2 | 0 / 0 / 7 | **neither** — structure changed |
| backgrounds | 1 | 4 | 1 | 0 | 3 | — | **neither** — structure changed |
| feats | 1 | 17 | 1 | 0 | 16 | — | **neither** — structure changed |
| conditions | 15 | 15 | 15 | 0 | 0 | 0 / 3 / 12 | full (all restyled) |

Open5e counts for comparison: creatures 325 / 331, magic items 499 / 760 (expanded per base
item), items 237 / 203, weapons 37 / 38, armor 12 / 13.

**What it says, type by type**

- **Monsters were rewritten, not edited.** Of 285 same-name pairs none is identical and only 49
  reach 60%. A diff model buys little here. 82 entries do not pair by name at all — renames and
  splits (Acolyte, Androsphinx, Bugbear on the 2014 side; the 2024 side adds dinosaurs and
  animated objects). Monsters need a cross-edition identity map before anything is stored.
  *Measurement note:* a first pass read 5etools' `entries` field, which monsters do not use
  (their text is in `trait`, `action`, `legendary`…), and reported all 285 as identical. Corrected.
- **Spells and magic items suit the diff model**: 220 of 317 and 222 of 287 same-name pairs reach
  60%. Spell renames exist too: Feeblemind (2014 only) and Befuddlement (2024 only) are the same
  spell under a new name.
- **Species, backgrounds and feats changed structure**, confirmed from fields. Species lose
  `ability` (no more racial ability scores) and gain `creatureTypes`, `sizeEntry` and `_versions`
  (lineages). Backgrounds gain `ability` and `feats`. Feats gain `ability` and `category`. These
  need grant vocabulary, not diffs.
- **Weapons gain `mastery`** — weapon mastery, a 2024 subsystem with no 2014 counterpart. Net is
  2014 only; Musket and Pistol are 2024 only.
- **Conditions**: all 15 restyled; 3 reach 60%.

**`e5` name coverage, and why most of it is not yet usable**

`e5` holds all 319 SRD spells by name, and 313 of 322 monsters (the 4 misses are naming:
`elf, drow`, `succubus/incubus`). For **magic items (215 of 313), gear (70 of 180) and armor (6 of
13)** the low figures are mostly **naming conventions**, not missing content: `e5` writes
`adamantine armor, breastplate` and `acid` where 5etools writes other forms. Name normalisation is
needed before `e5` coverage means anything for those types.

**Flag:** `e5` weapons include `firearm, burst (dmv)` — DMV-labelled content in the core weapon
list on this line. Not investigated; check whether it is meant to be here.

## Rules and reference (no e5 data namespace)

| type | 2024 source | status |
|---|---|---|
| conditions | `ConditionDescription.json` | **Check B done 2026-10-07** — see *Conditions, check B* below |
| rules / rulesets | `Rule.json`, `RuleSet.json` | **2014: check B done 2026-10-08**, 2024 not yet — see *Rules (2014), check B* below |
| spellcasting options | `SpellCastingOption.json` (230KB) | **NOT CHECKED** — may carry the higher-level/upcast structure |
| damage types, creature types, alignments, abilities | several small `*Description.json` | **NOT CHECKED** |
| services, cross-references | `Service(s).json`, `CrossReference.json` | **NOT CHECKED** |

### Conditions, check B (2026-10-07)

Transcripts for both editions, plain and readable, are in `resources/srd/` on `srd52/develop`.

All 15 conditions in each edition, open5e and 5etools each aligned word by word against the SRD
PDF text (5.1 pp. 358-359, 5.2.1 pp. 177-191), page footers removed.

- **open5e matches the SRD verbatim on all 30.** One artifact: 2024 Prone reads `move- ment`
  (a PDF line-break hyphen kept with a space) where the SRD has "movement" — upstream note 6.
  The 2014 Exhaustion soft hyphens are upstream note 4.
- **5etools departs, always on its side, never the SRD's.** 2014: it drops "(see the condition)"
  from Grappled, Paralyzed, Petrified, Stunned and Unconscious, and its Exhaustion differs
  (0.96 against open5e). 2024: "throws" for the SRD's "throw" in five conditions, "crawling" for
  "crawl" in Prone, "effect" for "effects" in Charmed. That is PHB wording, not SRD wording —
  which is why 5etools detects and never fixes.
- **Comparison A**: none of the 15 pairs is identical and 3 reach 60%, so both editions are stored
  in full.
- **Canonical files done** (2026-10-07): `resources/public/srd/2014/conditions.edn` and
  `2024/conditions.edn` on `srd52/develop`, generated by `scripts/srd/emit-conditions.py` from the
  corrected open5e clone. The generator refuses to write if any source word is lost; both files
  parse, 15 conditions each, pages as checked. Nothing in the app reads them yet.
- **Typos in the SRD itself, corrected** (owner decision: corrected and noted, not preserved):
  SRD 5.1 p. 358 (Grappled) prints the spell as *thunder-wave*, checked on the rendered page;
  corrected to "thunderwave" in open5e-api `9be39dc`.
- **The 2014 file ships** in PR #58 (`fix/srd-2014-data-defects` to `integration`), with
  conditions pages, Orcacle results and stat-block links.

Method: `cond_check.py` in the session scratchpad; the alignment trims boundary words so only
interior differences report. A first pass without that flagged 23 of 30 on heading words and
footers — window noise, not text differences.

### Rules (2014), check B (2026-10-08)

All 268 open5e rule and rule-set texts aligned word for word against SRD 5.1, then the reverse
pass, then a duplicate check. After the corrections in open5e upstream note 7, every record matches
the SRD except for differences that are not text: table rows the PDF lists in another order,
open5e's `srd:` references and "(table)" captions, quote-mark tokenizing. Added from the SRD:
Resting, Movement and Position, and the end of Damage and Healing (about 2,500 words).

**Canonical file:** `resources/public/srd/2014/rules.edn` (srd52/develop; also shipped in PR #58),
by `scripts/srd/emit-rules.py`: 43 sections in six groups, each with its pages; 172 links (spells
79, conditions 83, rules 10), every one checked to resolve by `srd_rules_test`. Every heading has
an anchor, so subsections are reachable.

**Links left as text, on purpose or as known gaps:** 16 condition words — headings, ordinary uses
("invisible strands", "poisoned darts", "dead or incapacitated crawler"), and the second condition
in a pair ("is blinded (25%) or deafened"), which the patterns do not reach. Monster names in
rules text occur only in two tables (Size, Mounts and Vehicles) and are not linked.

### Equipment: weapons, armor, gear, check B (2026-10-08)

Every open5e item's cost and weight, every weapon's damage, properties and (2024) mastery, and every
armor's AC, Strength and Stealth, checked against the SRD equipment tables (5.1 pp. 62-74, 5.2.1
pp. 89-103); 2024 tools against their "(N GP)" headings and Weight lines, instruments and gaming
sets against their Variants lists. Script: `equip_check.py` approach, recorded here for reuse.

- **2014 errors, fixed (open5e PR #999):** Heavy never assigned (8 weapons); Ammunition missing on
  the heavy crossbow, Thrown on the Handaxe, Finesse on the Whip; the light crossbow's Two-Handed
  record pointed at the Trident; Shortsword slashing for piercing; Acid, Blanket and Greatsword
  missing cost or weight; crossbow bolts 0.080 for 0.075 lb.; "Essense of either".
- **2024 errors, fixed (same PR):** Dart 1 lb. for 1/4; Entertainer's Pack 58 for 58½; Dragonchess
  1 lb. for none; two holy symbol names with a stray ")".
- **Agree:** everything else, including all 38 2024 weapons' properties and mastery.
- **Modelling, not errors:** ammunition priced per piece; "Special (Lance)"; staffs linked to the
  quarterstaff; 2024 tool names carrying their price (raised as a question in #999).

**Canonical files:** `resources/public/srd/{2014,2024}/equipment.edn` (weapons and armor), by
`scripts/srd/emit-equipment.py`. 2024 carries `:mastery` on all 38 weapons. The 2014 values are in
the app's weapons and armor (PR #61), tied to the file by `srd_equipment_test`.

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
