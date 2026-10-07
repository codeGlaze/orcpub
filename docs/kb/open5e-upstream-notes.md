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

## Before filing anything

- **Re-verify on their current `main`.** Their data moves; a note here is a snapshot.
- One issue per report. Cite the file path, the record `pk`, and for data issues the SRD PDF
  page. API behaviour suits an issue; data fixes suit a PR.
- Our PRs go out as codeGlaze (owner, 2026-10-07): author, signing key and the account that
  opens the PR all codeGlaze, never mixed with another handle. Check authorship and trailers
  first (`check-authorship-before-publishing`).

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
`c5ca059` (Blinded's one straight apostrophe). The other files wait for their content type.

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
