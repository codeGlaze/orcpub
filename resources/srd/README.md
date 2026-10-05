# SRD source documents

The primary artifacts this project's SRD content is derived from, and a text transcript of
each so nothing has to re-run a PDF extractor just to read or grep the rules.

| file | what it is |
|---|---|
| `SRD-5.2.1.pdf` | SRD 5.2.1, the 2024 rules. The authority: anything disputed is settled here. |
| `SRD-5.2.1.txt` | Transcript of the above, one `<<<PAGE n>>>` marker per page so a quote can cite a page. |

Not under `public/`: these are for tooling and reading, not served to users. The 5.1 PDF the
app already ships stays at `resources/public/dnld/SRD-OGL_V5.1.pdf`, which is a download link.

## How the transcript was made

    java -cp "$(lein classpath)" clojure.main scripts/srd/extract.clj SRD-5.2.1

(`scripts/srd/extract.clj` lives on `agents/develop`.) It uses PDFBox, already a project
dependency. The output is then normalized: non-breaking spaces, tabs and stray carriage
returns collapsed, and U+2010/U+2011 folded to `-`.

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

SRD 5.2.1 is © Wizards of the Coast LLC, licensed under CC BY 4.0
(https://creativecommons.org/licenses/by/4.0/). Redistributable with attribution; the
transcript is a derivative of it and carries the same terms.
