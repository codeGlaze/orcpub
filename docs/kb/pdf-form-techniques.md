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

## Text capacity: fields AUTO-SIZE, so the cutoff is legibility

The default (fillable) export does NOT use the sizes in `routes.clj/font-sizes`.
Those are consulted only when flattening -- `write-fields!` says so, and it
checks out: 20 words into `features-and-traits-2` with `{:features-and-traits-2 8}`
still bakes at 12pt. The template's `/Helv 0 Tf` means auto-size, and PDFBox
shrinks the text to fit the box.

So text is not cropped at some fixed word count. It SHRINKS:

    features-and-traits-2:   600w -> 11pt    1200w -> 8pt
                            2400w ->  5pt    4800w -> 4pt    9600w -> 4pt
    bonds / ideals:            5w -> 10pt      15w -> 6pt      40w -> 4pt

**Auto-size floors at 4.0 pt.** It will not go below that, so past the 4pt point
the text clips as well. The real failure mode is therefore worse than plain
cropping: the sheet silently becomes illegible first, then starts losing text.

That is why an overflow cutoff has to be a MINIMUM FONT SIZE, not a character
count. Word budgets before each box drops below a floor, measured by binary
search on the baked appearance stream:

| field | words @ >=7pt | words @ >=6pt |
|---|---|---|
| `ideals` / `bonds` / `flaws` | 25 | 42 |
| `personality-traits` | 44 | 63 |
| `attacks-and-spellcasting` | 127 | 169 |
| `other-profs` | 147 | 204 |
| `features-and-traits` (equipment list) | 447 | 593 |
| `treasure` | 447 | 593 |
| `backstory` | 987 | 1339 |
| `features-and-traits-2` | 3369 | >=4000 |

7pt is comfortable in print; 6pt is small but readable; 5pt and below is not
worth printing. Pick the floor, then spill whatever does not fit at that size.

Budgets must be COMPUTED from the widget box at runtime, not hardcoded from this
table -- the table is for orientation and for catching drift in tests.

A production level 20 wizard used 1398 characters of `features-and-traits-2`,
about a tenth of its budget. The pressure is on the small page 1 boxes: two or
three sentences in "Bonds" already pushes below 7pt.

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
