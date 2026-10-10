# open5e — issues found, for fixing upstream

**Notes on problems in open5e's API and data, kept so useful fixes can be PR'd back.** We use
open5e's per-publisher repo data (`open5e/open5e-api`, `data/v2/wizards-of-the-coast/`) as a
structured source checked against the SRD PDFs; see [srd-2024-roadmap.md](srd-2024-roadmap.md).
Everything below was found while doing that.

## Where the fixes live

Corrections are commits, not just notes: a local clone at `/home/codeglaze/projects/open5e-api`,
branch `srd-corrections` off their `staging` (their default branch), one commit per fix with the
SRD page in the message. Unpushed until a fork and PR are approved. Our canonical files are
generated from that corrected clone. Their `AGENTS.md` asks for `uv run python manage.py
quicksetup` and their tests before work is called done: run both before any PR.

## Filed upstream (2026-10-08)

**Status 2026-10-10: all four merged** (#995-#997 by morning; #999 after the maintainer added
`8629d961`, see the markdown section). Issue #998 still open.
**Earlier, 2026-10-09:** all four PRs approved by eepMoody; calumbell (maintainer) on #995: "these
fixes are great pickups, very very helpful", aiming for production that weekend, and asked us to
flag more. Not merged yet. Descriptions and commit messages were rewritten plainer the same day
(owner's voice; commits reworded and re-signed, content byte-identical).

Fork `codeGlaze/open5e-api`, one branch per topic off their `staging`, each re-checked there:
their `quicksetup` loads every fixture and `uv run pytest` passes (93) with all three applied.

| what | where |
|---|---|
| Rules: the missing Resting, Movement and Position and Damage and Healing text; Critical Hits; text errors; names (note 7) | [PR #995](https://github.com/open5e/open5e-api/pull/995), branch `srd-2014-rules-fixes` |
| Conditions: encoding artifacts and formatting, both documents (notes 4 and 6) | [PR #996](https://github.com/open5e/open5e-api/pull/996), branch `srd-conditions-fixes` |
| Spells: higher-level rules for Heroism, Chain Lightning, Dissonant Whispers (note 2) | [PR #997](https://github.com/open5e/open5e-api/pull/997), branch `srd-spell-upcasts` |
| Equipment, both documents: Heavy never assigned in 2014, other missing or mis-pointed weapon properties, the Shortsword's damage type, missing costs and weights, three names (see coverage map, *Equipment*) | [PR #999](https://github.com/open5e/open5e-api/pull/999), branch `srd-equipment-fixes` |
| `/v2/magicitems/` ignores `document__key` (note 1), counts re-checked on the live API the same day | [Issue #998](https://github.com/open5e/open5e-api/issues/998) |

**Kept local, on purpose:** the "thunder-wave" correction (a typo in the SRD itself; open5e
transcribes the SRD as printed). The Sage Advice paraphrase finding is raised in PR #995's
description, not as a change. Their guide also invites a Discord or issue conversation before
large contributions; PR #995 is the large one, so watch it for a request to split.



- **Re-verify on their current `main`.** Their data moves; a note here is a snapshot.
- One issue per report. Cite the file path, the record `pk`, and for data issues the SRD PDF
  page. API behaviour suits an issue; data fixes suit a PR.
- Our PRs go out as codeGlaze (owner, 2026-10-07): author, signing key and the account that
  opens the PR all codeGlaze, never mixed with another handle. Check authorship and trailers
  first (`check-authorship-before-publishing`).

## Markdown in open5e data: match the document you're editing (owner decision, 2026-10-09)

open5e's `desc` fields are markdown with no enforced style, and the documents drift: srd-2014
mostly writes `_italic_` and `* ` bullets, srd-2024 mostly `*italic*` and `- ` bullets. Both write
SRD run-in labels in bold italic as `**_Label._**`. When we add or edit text:

- **Italics and bullets:** follow the majority of the file being edited (above).
- **Always:** `**_Label._**` for labels the SRD prints in bold italic; `**bold**` for bolded terms;
  `###` subheadings; `> ` sidebars; tables with a `**Name (table)**` caption line.
- **Our own SRD files** stay markdown-free: the generators read every form above.

**Learned from the maintainer's own edit to #999 (calumbell, 2026-10-10, commit `8629d961`):**
- **Names are Title Case,** even where the SRD's table uses sentence case: they retitled all 11
  srd-2014 poisons ("Drow poison" -> "Drow Poison"), including our "Essence of Ether".
- **Fixing a pk is fine with them:** they renamed `srd_essense-of-either` to
  `srd_essense-of-ether`, which we had left alone to avoid breaking references.
- **Literal `\n` text in a desc is a defect:** they turned 26 item descriptions' backslash-n
  characters into real line breaks. More remain on `staging`: 54 lines in srd-2014
  `MagicItem.json`, 6 in `Item.json`, 1 in `Environment.json`. A ready candidate for a catch PR.

 (its added text uses `*italic*` and two plain-bold labels) and is left as
it is; it was approved as sent.

## 1. `/v2/magicitems/` silently ignores `document__key` — **verified 2026-10-06**

The filter spelling that works on `/v2/spells/` does nothing on `/v2/magicitems/`, and the API
drops unrecognised filter parameters without error, so a plausible query returns the entire
third-party catalogue labelled as if it were filtered.

| query on `/v2/magicitems/` | total |
|---|---|
| `document__key=srd-2024` | **2322** — every document |
| `document=srd-2024` | 760 |
| `document__key__in=srd-2024` | 760 |
| `document__slug=srd-2024` | unfiltered, same as no filter |

760 is exactly what `srd-2024/MagicItem.json` holds, and that file is clean. The extra records
come from other documents: `Bloodprice Breastplate` reports `document = vom` (Kobold Press, Vault
of Magic) and is returned under `document__key=srd-2024`. Control: `/v2/spells/?document__key=srd-2024`
filters correctly.

**Impact.** A consumer who learns the filter on `/spells/` and reuses it on `/magicitems/` gets
third-party content believing it is SRD. This happened to a real downstream import: roughly three
quarters of the base items it saved were not SRD content.

**Suggested fix.** Make the filterset fields consistent across v2 endpoints (accept
`document__key` on `/magicitems/`), and consider rejecting unknown filter parameters with a 400
rather than ignoring them, so this class fails loudly.

## 2. Missing upcast data on three spells — **verified against the PDFs**

Each has an empty `higher_level` and no `slot_level_*` record in `SpellCastingOption.json`,
while the SRD gives it a higher-level rule.

| document | spell | SRD page | SRD text |
|---|---|---|---|
| `srd-2014` | Heroism | SRD 5.1 p.154 | "At Higher Levels. When you cast this spell using a spell slot of 2nd level or higher…" |
| `srd-2024` | Chain Lightning | SRD 5.2.1 p.114 | "Using a Higher-Level Spell Slot. One additional bolt leaps from the first target…" |
| `srd-2024` | Dissonant Whispers | SRD 5.2.1 p.124 | "Using a Higher-Level Spell Slot. The damage increases by 1d6 for each spell slot level above 1." |

5etools, an independent transcription, carries all three upcasts in both editions.

**Fixed on `srd-corrections`** (2026-10-07): `42b8a95` Heroism, `43d3879` Chain Lightning,
`ee658a3` Dissonant Whispers. Each adds the `higher_level` text and slot options, with new option
pks 10734-10752 above the highest in `data/v2`. Re-check those pks against `staging` before the
PR, because new ones may have landed there.

## 3. Upcasting is structured unevenly between the two documents — **verified**

open5e records upcasting twice: as `higher_level` text, and as per-slot `SpellCastingOption`
records (`slot_level_2` with `target_count: 2`, and so on). `srd-2024` has slot-level options
for 107 of the 109 spells the PDF says upcast. `srd-2014` has them for only 47, against 92 "At
Higher Levels" sections in the SRD 5.1 PDF; the rest exist as text only. Backfilling the 2014
options would make the two documents consistent and make 2014 upcasting usable as data.

## 4. SRD 5.1 encoding artifacts in `srd-2014` text — **verified, extent not measured**

Text carries the soft hyphens of the 5.1 PDF's text layer: `ConditionDescription` for
exhaustion reads `long-­‐term` (hyphen, U+00AD, U+2010). These register as differences in any text
comparison and as odd characters in display. **Measured 2026-10-07: 47 soft hyphens across 10
`srd-2014` files** (Spell 13, Item 11, Environment 8, ItemSet 4, CreatureTypeDescription 3, and two
each or fewer in five more).

**Fixed for conditions on `srd-corrections`:** `a11a5de` (both soft hyphens, two doubled spaces),
`c5ca059` (Blinded's one straight apostrophe), `5e245a1` (Poisoned's effect had no `* ` bullet
marker, unlike the other conditions), and `9be39dc`, a typo in the SRD itself rather than in
open5e (*thunder-wave*). That one may not suit an upstream PR, since open5e transcribes the SRD as
printed. The other files wait for their content type.

## 5. `srd-2014` spell text that does not match the SRD — **candidates, not yet verified**

Found in the three-way spell check (`e5` / open5e / SRD 5.1 PDF). For each, the app's `e5` text
occurs word for word in the PDF while open5e's mostly does not, measured as the share of 8-word
runs found in the PDF transcript:

| spell | open5e text in PDF | `e5` text in PDF |
|---|---|---|
| Water Breathing | 0% | 100% |
| Hold Monster | 13% | 100% |
| Fire Shield | 24% | 100% |
| See Invisibility | 33% | 100% |

**Confirm each on the rendered PDF page before filing or correcting.** The measure is good at
saying which source matches the transcript, but the transcript is extractor output.

## 6. A kept line-break hyphen in `srd-2024` Prone — **verified 2026-10-07**

`ConditionDescription` `srd-2024_prone` reads "an amount of `move- ment` equal to half your
Speed". The SRD 5.2.1 (p. 186) has "movement"; the hyphen is the PDF's line break. The other 29
conditions across both documents match the SRD verbatim, so this looks like a one-off rather than
a pattern — worth a quick search for `\w- \w` across the 2024 fixtures before filing.

**Fixed on `srd-corrections`** (2026-10-07): `278778f`.

## 7. `srd-2014` rules: missing sections, a duplicate, and text errors — **verified 2026-10-08**

Found by aligning all 268 rule and rule-set texts against the SRD 5.1 text, then the reverse
(SRD rules text no record covers), then a duplicate check across records. All fixed on
`srd-corrections`, each commit citing its SRD pages:

| problem | commit |
|---|---|
| **About 2,500 words of SRD rules have no record**: Resting with Short and Long Rest (p. 87); all of Movement and Position (pp. 91-92); Damage and Healing after Damage Resistance — Healing, Dropping to 0 Hit Points (Instant Death, Falling Unconscious, Death Saving Throws, Stabilizing, Monsters and Death), Knocking a Creature Out, Temporary Hit Points (pp. 97-99). open5e's own `srd:movement-and-position` reference pointed at the missing section | `6f2164f` |
| Critical Hits is a word-for-word copy of Damage Rolls; the critical-hit rule itself is missing | `aa51bd8` |
| 95 places with words run together ("relyingon", "1round"), mostly Movement and Environment | `db392f4` |
| Four sentences in Player's Handbook wording ("in chapter 1", "this chapter") | `c80e1d0` |
| Four wording errors: "The old piece" for gold; a Constitution score of 18 that the example then raises "from 17 to 18"; "movingover"; "later in this chapter" | `665049c` |
| Three misnamed rules: "Rnged Attacks", Darkvision named "Blindsight", Use an Object named "Search" with a duplicate index | `fe97b1f` |
| "10 b 10 ft." for "by" in the Size table; a U+02BC apostrophe | `f2f648d` |
| "three--- quarters" for "three-quarters" | `aa51bd8` |

**Sage Advice: left in open5e as they made it (owner, 2026-10-08), checked for accuracy.** Two
rules (Rolling 1 or 20, Bonus Actions) quote Sage Advice rulings and link the Sage Advice
Compendium v1.01. Checked against that PDF: both are right in substance, neither is verbatim.
"Spell attacks can score critical hits, just like any other attack" paraphrases "A spell attack can
definitely score a critical hit. The rule on critical hits applies to attack rolls of any sort."
The bonus-action note's first sentence matches ("Actions and bonus actions aren't
interchangeable"); its second ("If you have two abilities that require bonus actions … you can
only use one") is not in the Compendium, though it is true by the SRD's own one-bonus-action rule.
A possible upstream suggestion: quote the Compendium, or label these as paraphrases. Our SRD file
leaves them out (not SRD text, not under its licence) and `emit-rules.py` reports where; our own
clarification notes are the wanted alternative (srd-improvements.md).

**Our tooling note:** the word alignment cannot see a duplicate (both copies match real SRD text)
or missing text (it only checks what a record holds). Both checks now run as well.

## Observations, not yet issues

- **Cantrip scaling lives in `higher_level`** (9 spells in `srd-2014`, 15 in `srd-2024`). The SRD
  writes it in the description with no higher-level heading. A defensible modelling choice;
  worth documenting rather than changing.
- **`CharacterClass.primary_abilities` was empty** on every record sampled. 2024 multiclassing
  prerequisites depend on it. Unverified across all records and not yet checked against the
  SRD. Do not file until both are done.
- `srd-2024/MagicItem.json` holds 760 records over roughly 403 base names, against about 237
  magic items in the SRD. Looks like per-base-item expansion (Adamantine Armor per armor type),
  which is intended. Not an issue unless reconciliation shows otherwise.

## Not open5e's

`adkinn/srd-5.2.1` republishes open5e's `wotc-srd` document — SRD 5.1 — with every record
labelled `source: srd-5.2.1`. A different project's mislabelling, recorded here so it is not
mistaken for an open5e problem.
