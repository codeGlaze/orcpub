# PDF form techniques that work (and the ones that don't)

Everything here was measured against the real templates in `resources/` and a
production export. Numbers are reproducible with `dev/sample_character.clj` and
`dev/on_demand_pages.clj`.

Background on how the current exporter got into its present state is in
`docs/issues/pdf-export-size.md`; this file is the how-to.

## The one rule that explains most of the weirdness

**Fields sharing a fully-qualified name ARE the same field and share one value.**
Tick a checkbox on page 3 and its same-named twin on page 7 ticks too. There is
no per-page scoping.

Everything a form has to do across repeated pages — spell pages per class,
continuation pages, overflow — has to mint unique names. That constraint is why
the style 1 templates carry hundreds of uniquely-numbered `Check Box NNNN`
fields, and why the variants were built by deleting pages from a master. Deleting
a page removes the page and its annotations but leaves the FIELDS in the
AcroForm, which is where the ~1,600 orphaned widgets came from.

## Cloning a page without copying its artwork

Build a new page dictionary that REFERENCES the source's `/Contents`,
`/Resources` and `/MediaBox`. Nothing is duplicated:

    master alone           421 KB
    master + 6 clones      422 KB

One kilobyte for six pages. Then create fresh widgets per clone and rename each
field. See `add-class-page!` in `dev/on_demand_pages.clj`.

Result on the hardest case — eight spellcasting classes, which the current sheet
cannot represent at all:

    10 pages, 1830 fields, ZERO duplicate names, 757 KB

against the current six-class template's 9 pages, 1407 fields, 1596 orphans and
~1.2 MB. More classes, more pages, fewer bytes.

## Do NOT use these

- **`Splitter`** drops the AcroForm entirely. An isolated page comes out with
  zero fields.
- **`PDFMergerUtility`** duplicates the background image once per merged copy
  (base + 3 spell pages ballooned to 1804 KB, larger than the whole 9-page
  original) AND renames colliding fields, turning `spells-1-1` into
  `spells-1-1-1`. Every name `pdf_spec` writes would silently stop matching.

## Pruning orphans must be per-widget, not per-field

Some fields own widgets on several pages. Keeping a whole field because *any*
one of its widgets is live leaves the rest orphaned — that mistake left 101
orphans behind. Filter each field's widget list, then drop fields left with none.

Measured on a production export: 1407 fields / 2679 KB down to 333 fields /
1313 KB, a 51% cut, with all 248 valued fields present and unchanged.

## Text capacity, and what overflow actually does

Overflowing text is NOT lost and NOT truncated. PDFBox writes every wrapped line
into the appearance stream; the stream's own clip rectangle (`re W n`) then hides
whatever falls outside the box. The value stays complete in the PDF, the ink is
generated, and the excess is cropped with no warning.

Capacities at the 8pt these fields use, measuring only widgets that are on a page
(every field has two widgets — one live, one orphan — so measuring the first one
gives the wrong box):

| field | box | lines | ~words |
|---|---|---|---|
| `features-and-traits-2` | 573 x 755 | 81 | ~1476 |
| `backstory` | 354 x 369 | 39 | ~437 |
| `features-and-traits` (equipment list) | 165 x 370 | 39 | ~199 |
| `treasure` | 164 x 370 | 39 | ~199 |
| `other-profs` | 166 x 129 | 13 | ~66 |
| `attacks-and-spellcasting` | 166 x 114 | 11 | ~56 |
| `personality-traits` | 153 x 49 | 4 | ~19 |
| `ideals` / `bonds` / `flaws` | 153 x 35 | 3 | ~14 |

The continuation page is not the pressure point — a real level 20 wizard used
1398 of its ~13000 characters. The small page 1 boxes are: two sentences in
"Bonds" overflows at about 14 words and is cropped silently.

## Modifier boxes clip at three characters

The skill and save boxes are 14.4 x 8.6 pt with a 12.4 pt clip. At 8pt Helvetica:

    "+7" / "-1"   9.1 pt   fits
    "+11"        13.6 pt   clips

Any three-character modifier — +10 and above, or -10 and below — is cropped. This
is in the appearance stream's clip, so it is not renderer-specific.

## Field names do not mean what they say

Verified against a production export, not inferred:

| name | actually holds | prints under |
|---|---|---|
| `features-and-traits` | the EQUIPPED item list | EQUIPMENT |
| `treasure` | unequipped items and valuables | TREASURE (page 2) |
| `features-and-traits-2` | the real features, actions, reactions | FEATURES & TRAITS |
| `equipment` | nothing — `pdf_spec` never emits it | (coin fields only) |
| `cha` | the MODIFIER (large box) | CHARISMA |
| `cha-mod` | the SCORE (small circle) | CHARISMA |

Read `pdf_spec/equipment-fields` and `pdf_spec/traits-fields` before trusting a
name. Two bugs were reported in this repo purely from believing the names.

## PDFBox renders are not evidence about what users see

Every screenshot in these docs comes from PDFBox's renderer. It is fine for
comparing two PDFBox renders — same renderer both sides, so a real difference
still shows. It is NOT evidence about a real viewer. A production PDF that
renders with missing-glyph boxes here displays correctly in an actual PDF viewer.

Glyph-level and layout claims need a real viewer, or a measurement (string width
against the clip rectangle) that does not involve rendering at all.

## The 0xAD hyphen

Production exports encode `-` as octal 255 (0xAD) in the appearance stream where
current output uses 0x2D. Both are legal — WinAnsiEncoding lists "hyphen" at both
codes — but 0xAD is the soft-hyphen slot. Consequences: PDFBox declines to draw
it, and flattening then extracting text yields U+00AD where minus signs belong,
so copied values lose their sign.

This is an upstream PDFBox bug that has since been fixed. The current version
maps "hyphen" to 45 only, so the correct byte comes from the version, not from
anything in this repo's code.
