# The SRD PDFs as source of record, and what validating against them found

The app is a character builder and an SRD reference, and only SRD content ships (5etools is
convenient to mine as a crosscheck and nothing more). That makes the SRD PDFs the natural
source of record. This records how to read them, two traps that cost real time, and what
checking the existing SRD 5.2 import against them turned up.

All three editions are already in the repository's history and need no network:

    git show gitea/srd-2024-intergration:resources/public/dnld/SRD-5.2.1.pdf
    git show gitea/srd-2024-intergration:resources/public/dnld/SRD-5.2.0.pdf
    git show gitea/srd-2024-intergration:resources/public/dnld/SRD-OGL_V5.1.pdf

## Extracting

`scripts/srd/extract.clj` writes one text file per PDF with `<<<PAGE n>>>` markers, so any
later claim can cite a page. It uses PDFBox, which the project already depends on for PDF
generation, so extraction costs no new dependency:

    java -cp "$(lein classpath)" clojure.main scripts/srd/extract.clj SRD-5.2.1

Run it against a copy of the PDF, not the repository — the output is large and regenerable.

### Trap 1: the 5.1 text layer is pathological

SRD 5.2.x extracts clean and structured. SRD 5.1 does not. Its text layer encodes an ordinary
space as tab + carriage return + non-breaking space, and breaks words with U+2010 and U+2011.
Raw, `grep -c 'Casting Time'` finds **2** occurrences where there are 321. Normalizing those
runs to single spaces, and the hyphen variants to `-`, recovers all 321 — which matches the
322 spells in `dnd/e5/spells.cljc` and is itself good evidence the extraction is faithful.

### Trap 2: Python's newline translation silently defeats the fix

Reading that text with `open(path)` destroys it before any replacement can run: universal
newline mode converts the intra-line carriage returns to newlines on READ, splitting every
word onto its own line. `newline=''` is load-bearing, not a detail.

## What validating the SRD 5.2 import found

The existing `e520`/`e521` import came from the open5e API. Its own script says it "validates
each category against the SRD PDFs", but no such code was ever written — it stores a
`:pdf-path` and prints a reminder for a human. Nobody had checked it.

Comparing every item name in `dnd/e521` against the extracted SRD 5.2.1 text:

| category | names | in SRD 5.2.1 | absent |
|---|---|---|---|
| spells | 28 | 28 | 0 |
| feats | 17 | 17 | 0 |
| backgrounds | 6 | 6 | 0 |
| armor | 13 | 13 | 0 |
| weapons | 65 | 65 | 0 |
| races | 37 | 36 | 1 (`Size Choice`, a schema artifact) |
| equipment | 276 | 259 | 17 |
| magic items | 1959 | 1515 | 444 |

**The hand-rewritten categories have correct NAMES.** Spells, feats, backgrounds, races,
weapons and armor — the ones carrying "Rewrite … in orcpub schema" commits — validate at
essentially 100% *by name*.

**That is not the same as being correct, and an earlier revision of this page wrongly said
it was.** Checking field VALUES on the spells immediately found errors: of the 25 spells
cross-checkable against a parsed SRD entry, **8 carry the wrong school — 32%** — Ice Knife is
Conjuration in the SRD and `:school evocation` in the import, Befuddlement is Enchantment,
Ray of Sickness is Necromancy, Tsunami is Conjuration, Aura of Life is Abjuration. Levels
agreed in every case; only schools were wrong.

The cause is visible in the distribution: `evocation` appears on 13 of 28 spells, about half,
which is not a plausible spread across eight schools, and six of the eight wrong entries claim it. It is the import's fallback when it
could not determine one. So the error is systematic, not scattered, and a name-level check
cannot see it.

**Validate fields, not just names.** A missing item is obvious; an item with the right name
and a wrong field is not, and nothing downstream flags it.

**The bulk-imported magic items are not.** Of 1959 names, 764 are legitimate expansions of a
real SRD item (SRD "Demon Armor" becomes "Demon Breastplate", "Demon Chain Mail" and so on),
48 are SRD 5.1 items sitting in a file labelled 5.2.1, and **396 appear in no SRD at all** —
`Bloodprice Breastplate`, `Blood-Soaked Hide` and similar. open5e aggregates third-party OGL
content beyond the SRD, and the magic-items query evidently pulled the wider set. Equipment is
in much better shape, with 1 non-SRD item and 2 malformed names (`Holy Symbol, Amulet)` has an
unmatched parenthesis, so some names are broken rather than merely different).

So roughly a fifth of a file whose header claims "includes all SRD 5.2.0 items" is content
that does not belong in the app.

### Why no existing guard catches this

`scripts/scan-licensed-content.sh` hunts WotC product titles and trademarked spell wizards.
"Bloodprice" is neither, so the scan passes. A name-level check against the SRD text is a
different guard and the only one that finds this class.

## How to reuse this

`scripts/srd/validate-names.py` classifies every name in a content file as exact, expansion,
stale (present in an older SRD), non-srd, or malformed, and writes a manifest. Salvaging the
magic items is then mechanical rather than a judgement call per item. Re-run it against any
imported content before trusting it.

## Deriving the edition delta from the SRDs alone

`scripts/srd/spell-delta.py` parses both editions and reports what actually differs. open5e
is not an input, so nothing third-party can reach the result:

    python3 scripts/srd/spell-delta.py SRD-OGL_V5.1.norm.txt SRD-5.2.1.norm.txt out.json

Both editions parse completely — 319 of 319 spell entries in 5.1, 339 of 339 in 5.2.1. The
anchor is the `Casting Time:` line, with the header and name read backwards from it; matching
headers forwards instead catches table captions ("Level 1 Bard Spells") and misses every
cantrip, which uses "<School> Cantrip" rather than "Level N School".

Two traps, each of which silently ruins the answer:

**Headers wrap.** "Level 3 Evocation (Cleric, Druid, Paladin, Ranger," continues on the next
line, so the line above `Casting Time:` is often just "Ranger)". Missing that drops 38 of 339
entries in 5.2.1 — and because those spells then look absent from 5.2.1, it also inflates
"removed in 5.2.1" from 2 to 39. A parser gap shows up as a content finding.

**2024 renamed values without changing them.** "1 action" became "Action". Comparing raw
strings reports 250 of 280 shared spells as changed, which tells a reader nothing. Normalizing
values before comparison, and lifting ritual out as its own flag, brings that to 87 real
changes.

### What the delta says

| | |
|---|---|
| added in 5.2.1 | 22 |
| removed in 5.2.1 | 2 |
| mechanically changed | 87 |
| identical | 230 |

The changes are substantial, not cosmetic: Chill Touch goes from 120 feet to touch and from
1 round to instantaneous; Blindness/Deafness changes school *and* range (30 to 120 feet);
Banishment halves its range; Barkskin becomes a Bonus Action and drops concentration; Acid
Splash moves from Conjuration to Evocation.

### Against the import's hand-made delta

The open5e import ships a 28-entry spell delta described as "only spells that are NEW or
MECHANICALLY CHANGED vs SRD 5.1". Measured against the SRD-derived 109:

- it captured 26 of them,
- it **missed 83**, including every example listed above,
- 2 of its entries are not in the SRD delta at all.

So the import's delta is roughly a quarter complete, and a third of what it did capture
carries the wrong school. Its *shape* was right — delta-encoding against 5.1 is the correct
model — and its content is not a usable basis. Derive the delta; use the import to check
against, not to build from.
