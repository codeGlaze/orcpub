# SRD source documents

The primary artifacts this project's SRD content is derived from, and a text transcript of
each so nothing has to re-run a PDF extractor just to read or grep the rules.

| file | what it is |
|---|---|
| `SRD-5.2.1.pdf` | SRD 5.2.1, the 2024 rules. The authority: anything disputed is settled here. |
| `SRD-5.2.1.txt` | Transcript of the above, one `<<<PAGE n>>>` marker per page so a quote can cite a page. |
| `SRD-5.2.1.readable.txt` | The same, for reading and grepping: split words rejoined, page footers removed, markers kept. |
| `SRD-5.1.txt` | Transcript of SRD 5.1, the 2014 rules, made the same way. |
| `SRD-5.1.readable.txt` | The readable version of it. |

**Grep the readable files.** The plain transcripts keep the PDF's line breaks, so 5.2.1 has
2,473 words split across lines ("experi-" / "ence") that a search for the whole word misses.

Not under `public/`: these are for tooling and reading, not served to users. The 5.1 PDF the
app already ships is `resources/public/dnld/SRD-OGL_V5.1.pdf`, a download link; the 5.1
transcripts are made from that file and it is not duplicated here.

## The served data files

The SRD content the app will serve lives beside these, under `resources/public/srd/<edition>/`, one
EDN file per content type per edition (`2014/conditions.edn`, `2024/conditions.edn`). Those are
generated, not hand-edited: each names the script and the open5e-api `srd-corrections` commit it
came from. Errors are fixed in that source and the file regenerated.

## How the transcript was made

    java -cp "$(lein classpath)" clojure.main scripts/srd/extract.clj SRD-5.2.1

(`scripts/srd/extract.clj` lives on `agents/develop`.) It uses PDFBox, already a project
dependency. The output is then normalized: non-breaking spaces, tabs and stray carriage
returns collapsed, and U+2010/U+2011 folded to `-`.

The readable files come from the normalized transcripts:

    python3 scripts/srd/readable.py SRD-5.2.1.txt SRD-5.2.1.readable.txt --report

A hyphen at a line end is either a split word or a real compound broken at its hyphen. The
script decides from the document's own vocabulary, and where that is silent it follows what the
document usually does: 5.1 breaks only at real hyphens, 5.2.1 hyphenates by syllable.
Those it cannot decide are listed once, with the decision and its evidence, in
`scripts/srd/hyphenation.tsv` on `agents/develop`: 105 forms, 81 confirmed against 5etools' text
and 25 read by eye. `--report` lists any form not yet in that file, which is what a new PDF or
version will produce. Apart from
those hyphens and the footers, every non-space character is unchanged from the transcript.

That normalization matters far more for 5.1 than for 5.2.1. The 5.1 text layer writes an
ordinary space as tab + carriage return + non-breaking space, so a raw `grep -c 'Casting Time'`
finds **2** occurrences where there are 321.

## Reading a transcript is not verifying against the source

A transcript is extractor output. Checking extracted text against extracted text is circular,
and it hid five separate defects in this work — wrapped headers, page footers, wrapped
attunement notes, truncated bodies, and a `Component:` label that only 12 spells use. When a
value matters, render the page and look at it:

    java -cp "$(lein classpath)" clojure.main scripts/srd/render.clj SRD-5.2.1.pdf 112 page.png

## Licence

SRD 5.1 and SRD 5.2.1 are © Wizards of the Coast LLC, licensed under CC BY 4.0. The 5.1 PDF in
this repository is the earlier Open Game License edition; Wizards re-released the same SRD 5.1 text
under CC BY 4.0 in 2023, and that is the licence the generated files cite
(https://creativecommons.org/licenses/by/4.0/). Redistributable with attribution; the
transcripts are derivatives and carry the same terms.
