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

**The hand-rewritten categories are sound.** Spells, feats, backgrounds, races, weapons and
armor — the ones carrying "Rewrite … in orcpub schema" commits — validate at essentially
100%, and that work is reusable as it stands.

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
