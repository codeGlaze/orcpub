# PDF form techniques that work (and the ones that don't)

Everything here was measured against the real templates in `resources/` and a
production export. Numbers are reproducible with `dev/sample_character.clj` and
`dev/on_demand_pages.clj`. The page-generation spike there is superseded by
`pdf/add-missing-spell-pages!`, which does this in production; it is kept because
it is the smallest readable demonstration of the cloning technique.

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

## A field with widgets on two pages is the same rule, hidden

The name rule above has a second form that is easy to miss, because nothing about
the field names looks wrong: **one field can own widgets on several pages.** It
is still one field with one value, so it shows the same value in both places.

The style 1 six-caster template ships 101 of these — 92 prepared ticks and the
nine SLOTS EXPENDED blanks, shared between the first two classes' spell pages. A
Wizard's prepared spells appeared on the Cleric's page and their expended slots
were literally the same field.

`split-fields-across-pages!` gives each page its own field. Two constraints keep
it working:

- **It must run before the naming passes.** They name a field after the row
  beside one of its widgets and then skip it, since it no longer looks unnamed —
  so a spanning field keeps the first page's name and goes on mirroring. That
  ordering is why `dev/prepare_templates.clj` lists its steps in a fixed order.
- **The passes that recognise template names must accept the split's suffix.**
  The split names its copies `Check Box 25-p2`. A pattern matching only
  `Check Box 25` sees that as already named and leaves it anonymous — which is
  exactly how class 2's ticks were missed on the first attempt. `unnamed-checkbox?`
  and `unnamed-slots-expended?` in `pdf.clj` are the shared predicates; the same
  pattern is in `dev/inspect_export.clj` as `still-unnamed`.

The invariant is worth asserting rather than remembering: no field should carry
widgets on more than one page. `dev/inspect_export.clj` checks it on every
exported sheet, and `pdf_test.clj` checks it on the templates.

## Place a patch by measuring the artwork, not by eye

Anything drawn over the sheet has a printed counterpart to match, and guessing at
it is wasted work — every value can be read out of the PDF:

- **Positions and font size**: `PDFTextStripper`, overriding `writeString` to
  collect each `TextPosition`'s `getXDirAdj` / `getYDirAdj` / `getFontSizeInPt`.
- **Colour**: render at high DPI and sample the pixels. At 300 DPI antialiasing
  still tints the darkest pixel; at 1200 the flat value is unambiguous.
- **Distances between repeated blocks**: take them from printed landmarks rather
  than tracing. The spell level numerals give the box-to-box offset exactly.

Worked example, the SLOTS TOTAL / SLOTS EXPENDED line the reused cantrips box
needs. The page prints it once, above level 1, and the other level boxes are read
from that one line by position:

    SLOTS TOTAL     x  50.83   baseline 483.17   size 5.00
    SLOTS EXPENDED  x 127.71   baseline 483.17   grey 0.59  (renders [150 151 151])

    level 1 numeral baseline 463.99
    level 3 numeral baseline 631.72   <- top of the middle column, level with cantrips
    cantrips box is therefore 167.73pt higher

Placed by eye first, that line came out at 4pt in grey 0.55 on a baseline a point
low. Measured, it lands on 50.83 / 127.71 / 650.90 at 5pt exactly. Those numbers
are `printed-slot-labels` and `cantrips-box-rise` in `pdf.clj`, and the test
asserts them by flattening the result and reading the text back.

Derive rather than hardcode where a field already gives you the geometry:
`cantrips-hexagon-box` is whatever `spell-level-numeral-box` measures for level 1,
raised by the same offset, so it tracks the artwork if the page is re-cut.

## How many spellcasting sections a sheet carries

The stock templates come in seven sizes, `-0-spells` through `-6-spells`, and the
number is how many spellcasting sections the file has — **not** a class list. The
trailing number on a field name is the section index: `prepared-1-1-4` is level 1,
row 1, section 4. `routes.clj` picks the smallest file that fits by looking for
the highest `spellcasting-class-N` key `pdf_spec` emitted.

Six is the largest file, and characters go past it. `add-missing-spell-pages!`
clones a page per class beyond what the template carries, so the ceiling is the
character, not the sheet. The eight-class fixture in `dev/sample_character.clj`
exports eight sections over eight pages — 11 pages in all, no shared names, no
orphans, nothing mirroring.

A class whose spells outgrow one page takes another, marked `(continued)` in its
heading by `pdf_spec`, and slots are written only on a class's first page so the
continuation does not repeat them.

## Spell rows are sized by height, so they are wider than they look

A spell row is 157.95 x 9.94pt and its text auto-sizes. The binding constraint is
the height: every row lands at **6.4173pt** regardless of content, and at that
size the row holds far more than it appears to.

    longest name in the SRD                              29 chars    85.6pt
    + a short source, "(Life)"                           36 chars   102.0pt
    + a full source, "(Life Domain)"                     43 chars   125.9pt
    + an unreasonable one, "(Circle of the Land: ...)"   61 chars   175.5pt   over

None of the 319 spell names overflow the row with a realistic source suffix
appended. Do not reason about spell-row capacity from the 8pt overflow floor:
that floor governs the prose fields in `overflow-labels`, and a spell row never
reaches it.

The scarce resource on a spell page is rows, not width. A sheet has 100 of them,
spread unevenly:

    level    0   1   2   3   4   5   6   7   8   9
    rows     8  12  13  13  13   9   9   9   7   7

Anything that moves a level into another level's box has to check the
destination's row count, not just that it is free.

## Computed marks: draw them, do not add fields

Anything the exporter derives and the player never edits -- a concentration
flag, component letters, a class label, a relabelled level numeral -- can either
be a read-only form field or content drawn onto the page. Drawing is two orders
of magnitude cheaper.

Measured on the six-caster template with all six spell pages filled, 594 rows
annotated with a concentration mark and a component string:

    names only, as today          1161.7 KB   1403 fields
    annotations drawn             1168.3 KB   1403 fields    +6.6 KB   +0.6%
    annotations as form fields    1550.8 KB   2591 fields   +389.1 KB  +33%

Per annotated row that is 11 bytes drawn against 671 bytes as fields. A field
costs a field dictionary, a widget dictionary, an appearance stream and an entry
in the page's annotations array; drawn text is a few operators appended to a
content stream the page already has.

Use a field only where the value has to be editable, addressable by name from
`pdf_spec`, or both. `reuse-cantrips-box!` uses read-only fields for its hexagon
and bar patch because they are few -- three per reused box -- and being
addressable makes them removable. At the scale of one per spell row, draw.

### If fields are unavoidable, share the appearance streams

The cost of a field is not its dictionary. Measured on the same 594 rows, with
1188 annotation fields added:

    fields, as created            1550.3 KB
    field dictionary merged
      with its widget             1531.3 KB    -19 KB
    appearance streams shared     1245.4 KB   -305 KB

Merging the field and widget dictionaries -- legal for a single-widget field, per
PDF 32000-1 12.7.3.1 -- saves almost nothing. The weight is in the generated
appearance stream, one per field.

Those repeat heavily. The 1200 annotation values across the sheet are only **20
distinct strings**, so 1200 appearance streams can be 20. Point every widget
showing the same value in the same size box at one stream:

    key = [value, widget width, widget height]

The stream's coordinates are local to its own BBox, so two widgets of equal size
showing equal text are genuinely interchangeable. Flattening both versions and
extracting the text gives identical output, line for line.

The key must include the geometry, not just the value. Sharing an `/AP` between
fields that differ is the bug that once printed one spellcasting class's data
under another's heading -- both widgets then show whichever content the shared
stream holds.

**Shared appearances break editing, demonstrated.** Setting a new value on one of
six fields sharing a stream rewrote that stream in place, so all six then drew
the new text:

    before   spells-6-2-6-m = "V S M 500gp"   spells-6-2-4-m = "V S M 500gp"
    after    spells-6-2-6-m draws "EDITED"    spells-6-2-4-m draws "EDITED"

PDFBox does this itself, so it is not a question of how some third-party viewer
behaves -- our own code corrupts the neighbours if it re-writes a shared field.

The obvious answer, marking the fields read-only, is not free: read-only means the
player cannot change the value, and the sheet's whole point is that it stays
fillable in every browser (see the comment in `routes.clj` about not flattening).
Trading a fillable field for 300 KB is a bad trade on a character sheet.

Which is why drawing wins twice over for computed marks. Drawn text is page
content, not a form control: it needs no field, cannot be corrupted by an edit
elsewhere, and leaves the neighbouring spell-name field fully editable. Sharing
appearances is only worth reaching for when a value must be addressable by name
AND is genuinely never rewritten -- a narrow case, and one where drawing is
usually available instead.

### Why the same trick barely helps text fields

Two widgets share an appearance only when they draw the *same bytes*. Matching
size and position is neither necessary nor sufficient -- the stream holds the
glyphs, so a field's appearance is a function of its value.

That is the whole difference, and it shows up in the bytes. Measured on two real
exports:

    eight-class sheet
      checkboxes    582 streams   47.0 KB  ->   2.4 KB   saves 44.6 KB  (95%)
      text fields   869 streams   39.0 KB  ->  22.6 KB   saves 16.4 KB  (42%)

    single-class wizard
      checkboxes    122 streams   13.5 KB  ->   2.4 KB   saves 11.1 KB  (82%)
      text fields   209 streams   24.2 KB  ->  22.9 KB   saves  1.4 KB  ( 6%)

A checkbox draws one of about fourteen things -- a circle, ticked or not, at a
few sizes -- and each is a real vector path. Many duplicates of something big, so
nearly all of it collapses.

A text field draws its own text. The wizard sheet has 187 filled text fields
carrying **175 distinct values**: almost nothing repeats, because a field that
repeats its neighbour's value is a field with nothing to say. The eight-class
sheet does better only because eight casters draw on overlapping spell lists.

And the duplicates that do exist are mostly the empty ones. Of the eight-class
sheet's 869 text appearances, 577 are empty and account for **2.3 KB between
them** -- `q Q` is four bytes, so collapsing 577 of them saves nothing worth
having. The bytes live in the 292 filled streams, and 163 of those are unique.

So the rule is not "same geometry, share". It is:

    saving = number of duplicates x size of the thing duplicated

Checkboxes score on both terms. Text fields score on neither: many duplicates of
almost nothing, or almost no duplicates of something. On top of that the little
that is available is the unsafe kind, since editing a shared text appearance
rewrites it for every field pointing at it.

### What a field actually weighs

An empty field is not the expensive thing, and the intuition that it must be is
worth correcting with the numbers. Strip every field and widget from the style 1
six-caster template and re-save to see what the page art alone weighs:

    original, before this branch   1409.9 KB   353.2 KB artwork   1056.7 KB form
                                   1407 fields, 1931 widgets  ->  769 bytes each

    after pruning and tick sharing  564.9 KB   350.4 KB artwork    214.5 KB form
                                   1403 fields, 1405 widgets  ->  157 bytes each

The artwork is a constant 350 KB in both -- embedded fonts and the vector paths of
the printed sheet -- and it is the single largest thing in the file at 62% of the
current template. It is not compressible without changing how the sheet looks.

A field costs 157 bytes now: a name, a rectangle, a page reference, a default
appearance string, and an entry in its page's annotation array, all inside a
compressed object stream. That is close to irreducible for something that has to
be named, positioned and fillable. An *empty* one's appearance stream is `q Q` --
four bytes.

The 769 bytes a field used to cost was not the field. It was 1931 widgets for
1407 fields, half of them orphaned, each dragging its own appearance stream. What
looked like "fields are expensive" was really duplication:

    prune orphaned widgets          1931 widgets -> 1405
    share identical tick appearances 582 streams -> 14

Where the bytes sit in a finished export, measured on the eight-class fixture:

    page content (the artwork)  272.6 KB  42.7%
    object streams (the dicts)  131.4 KB  20.6%
    appearance streams           39.6 KB   6.2%
    embedded fonts               15.2 KB   2.4%
    object framing and xref     179.4 KB  28.1%

### PDFBox 3 already compresses

`PDDocument.save(file)` writes object streams by default -- the output carries 60
of them -- so passing `CompressParameters` changes nothing. Saving with and
without it produced byte-identical sizes. There is no compression win left to
take at the save call.

## The raster sheets are images, and their compression was leaving 25% behind

Style 1 is vector art with no images at all, which is why the field work moved it
so much. Styles 3 and 4 are raster sheets -- a full-page background per page --
and there the images are 61% and 86% of the file, so field work barely registers.

Those backgrounds were stored as Flate with **no PNG predictor**:

    DecodeParms: BitsPerComponent 8, Colors 1, Columns 2550     <- no /Predictor

A predictor stores each byte as its difference from a neighbour, which for a
shaded background compresses far better than the raw values. Style 4's page
background, 2550x3300 at 8bpc grey:

    as shipped                            1629.9 KB   ratio  5.0:1
    re-deflated at level 9, no predictor  1523.9 KB   -6%
    Paeth predictor, level 9              1237.0 KB   -24%    lossless

The compression level is not the lever; the predictor is. And Paeth alone beat
trying all five PNG filters per row and keeping the cheapest -- 1237.0 KB against
1238.1 -- for a seventeenth of the work, so `add-image-predictors!` uses Paeth.

Across the 28 templates this took 50.2 MB to 44.3 MB, and with the earlier passes
59.4 MB to 44.3 MB.

Verified two ways, because "lossless" is a claim rather than an observation:
`add-image-predictors!` decodes its own output and throws unless the bytes match
what went in, and rendering the before and after at 150 DPI gives 0 differing
pixels of 2,102,475 across all 16 pages of the two raster styles.

What is left in those files is genuinely photographic. Two of style 4's three
images are JPEG already, and all three are greyscale despite two being tagged
DeviceRGB -- max chroma 0 over 5312 samples. Dropping those empty chroma planes
needs a lossless JPEG transform rather than a re-encode, and the saving is small
because flat chroma already compresses to nearly nothing. Beyond that the options
are downsampling from 300 DPI or re-encoding to JPEG, both of which change how
the sheet prints.


## What a character sheet export costs (2026-09)

Measure churn and residency separately. `totalMemory - freeMemory` around an
operation answers neither, since it counts allocation not yet collected and
misses allocation already collected. Use
`ThreadMXBean.getThreadAllocatedBytes` for churn and a settled heap with the
document still referenced for what is held.

**Residency is small.** An export holds 3.2 MB for a non-caster, 3.6 for one
caster, 5.3 for six on style 1, and 16.3 for six on style 4, whose master carries
4.4 MB of images. Concurrency is bounded by this.

**Churn is the cost, and it is large in absolute terms**, for a complete export
with the character's fields written, as production does:

    casters       pre-cut file      grown master
    0                 33.0 MB           34.0 MB
    1                 72.1 MB           81.3 MB
    2                129.1 MB          159.6 MB
    3                201.7 MB          252.3 MB
    6                499.2 MB          607.7 MB

Half a gigabyte of garbage for a 400 KB sheet, and most of it predates the
generation work: `write-fields!` builds an appearance stream for every filled
field and dominates the total. Generating pages adds 0 to 22%, weighted to the
rare shapes -- a non-caster is unchanged and one caster costs 9 MB more.

Two things made that worse before measurement caught them:

- A `prune-orphan-widgets!` on the request path. The masters are pruned at bake
  time and growing only adds pages, so it scanned the whole form on every export
  to find nothing. Removing it took a non-caster from 64.2 MB to 57.5.
- `spell-sections` asked `spell-page-for-suffix` for each n in turn, walking every
  page's annotations once per n -- nineteen scans to find nothing on a sheet with
  no spell pages. One pass over the form instead.

If sustained load is the concern, `write-fields!` is where the remaining
half-gigabyte is, not page generation.

### Churn is not footprint, and the difference is worth proving (2026-09)

The numbers above are bytes passed through the allocator, not bytes held. Said
without that qualifier they sound like a server requirement, and they are not.
Two hundred six-caster exports back to back, 322 filled fields each, complete in
a **48 MB** heap and fail at 40 MB. The live heap before any export is 35 MB --
the runtime, the loaded classes, the template bytes -- so the working set of the
worst export the sheet supports is roughly 8 to 13 MB.

What churn costs is CPU, not capacity. The same 200 exports take 362 ms each in
a 64 MB heap and 543 ms in 48 MB: give the collector room and the garbage is
nearly free, starve it and the export slows by half.

Heap sizing follows from that. Eight threads each exporting a six-caster sheet
run in 96 MB and fail at 80, so the cost is about 35 MB fixed plus 8 MB per
export in flight -- nothing per user who is not exporting at that instant, and
nothing resembling the churn figure. Headroom buys speed rather than capacity:
those eight threads take 110 ms an export at 256 MB, 163 at 128 and 232 at 96.

Concurrency is capped by the container, not by the number of people clicking.
Pedestal sizes Jetty's pool at `(max 50 (needed-pool-size))`, which is 50 until
about sixteen cores, so at most 50 exports are ever in flight and the rest queue.
The floor rises with that, sublinearly:

    concurrent exports    heap floor    per export
     8                        96 MB        7.6 MB
    32                       384 MB       10.9 MB
    50                       448 MB        8.3 MB

A thousand people exporting at once is therefore a queue, not a memory problem.
On four cores with a 1 GB heap, a thousand exports drain in 28.9 s for one
casting class, 46.7 for two and 73.3 for six -- 13.6 to 34.6 sheets a second.
Budget for the request bodies too: the handler caps a body at 2 MB, so 50 in
flight can hold 100 MB before parsing. 1 GB is comfortable for the whole path.

### Most of it was work already done (2026-09)

Four places asked a question they had already answered, or asked before knowing
whether the answer was wanted. Fixing them took a six-caster style 1 export from
607 MB to 162, and one casting class -- what most characters are -- from 77 to 51.

- `PDAcroForm.getField` walks the field tree. `write-fields!` called it twice per
  value, once to report names the template has no field for and once to write:
  284 values against 1403 fields, twice. Index the tree once. 294 MB to 39 MB.
- `spill-overflow!` located each prose field and measured its widget box before
  testing whether the value was blank, and found the continuation page whether or
  not anything spilled. Most sheets leave all ten empty. 113 MB to nothing.
- A cloned spell page re-read its source's five widget entries once per clone.
  `getDictionaryObject` returns the same COS object every call, so the clones
  shared those entries already and the repeat bought nothing. 131 MB of 165.
- The clone-loop setup ran when the loop would not, which is the one-class case,
  and `grow-spell-sections!` scanned for spell sections before returning 0 for a
  character who casts nothing.

### The floor is PDFBox's writer

What is left is not ours. Opening the 244 KB non-caster master costs 1.8 MB and
writing fifteen fields costs 1.7, but saving allocates 19.6 MB -- eighty times
the file it produces. `CompressParameters/NO_COMPRESSION` is worse on both counts,
25.7 MB for a 704 KB file, so the default object-stream save is already the cheap
option. Short of replacing `COSWriter`, roughly 20 MB per export is the price of
saving a PDF with this library.

## Bound the work, not the request (2026-09)

Sheet generation takes a permit from a semaphore sized by `ORCPUB_PDF_CONCURRENCY`;
the HTTP pool is separate and larger. Two decisions are worth keeping.

**The permit covers the PDF work only.** Parsing the body and writing the response
sit outside it. Holding it across the whole request would tie the limit to network
speed as well as to generation, and a slow client would occupy a slot it was not
using.

**Bounding the work rather than the pool keeps the rest of the site alive.** If
exports could take every Jetty worker, a rush of them would take logins and saves
down with them. Sized against the heap, an export in flight holds roughly 11 MB,
so the ceiling is a number an operator can actually compute. Throughput is bounded
by cores, though, so raising the limit past what the cores can chew through
lengthens the queue without shortening anyone's wait.

The semaphore is fair. Without fairness a thread can be starved indefinitely under
sustained load, which is exactly the traffic the limit exists for.

Release it in a `finally`. A failed export that keeps its permit bleeds capacity
away one error at a time, and the loss is invisible until the site stops serving.
There is a test for it.

## The 503 is the page they are looking at (2026-09)

The export is a form POST with `target="_blank"`, so a turned-away request does not
come back to JavaScript that could retry it -- the response *renders*, in the tab
where the sheet would have appeared. That makes the response itself the place to
put the retry: a page carrying the original request body in a hidden field, a
countdown, and a resubmit. Nothing in the builder changed, and how a finished sheet
arrives did not change either.

Three things this needs that are easy to miss:

- **Escape the reflected body.** It is caller-supplied and lands in an attribute.
  The page renders through `hiccup2`, which escapes content and attributes;
  `hiccup.page`, which the privacy and terms pages use, does not.
- **Nonce the script.** The app runs strict CSP with per-request nonces. The
  request carries `:csp-nonce`, and the interceptor builds the header from it on
  every response, so the busy page's inline script must carry that nonce or it is
  blocked with no error.
- **Jitter the countdown.** A crowd turned away together comes back together
  otherwise, onto a server that is already saturated. 25% either way is enough.

Retry state travels in a hidden field, so it is caller-supplied too: anything that
is not a plain non-negative number counts as a first try rather than buying extra
attempts.

Keep the status at 503. A browser logs `Failed to load resource: 503` in its
console for each turned-away attempt, which looks like a defect and is not -- it is
the browser reporting an HTTP status, and `200` would lie to caches, crawlers and
every non-browser client.

## Styling a page served outside the app (2026-09)

The busy page and the privacy and terms pages are server-rendered into a tab where
the builder's markup and scripts are absent. They still belong to the site, so they
link `/css/style.css` and `/css/compiled/styles.css` and use the site's classes --
`.app-header-bar` for the header, `.form-button` for buttons.

Their own rules go in `orcpub.styles.core` with everything else. A `<style>` block
in the response is a page the stylesheet cannot restyle, and it drifts the first
time the site's colours change.

Two traps, both found the hard way:

- `text-color` is a style map, `{:color :white}`, not a colour value. Passing it
  where a colour belongs compiles to `color-color: white`, which is invalid and
  silently dropped. Merge it: `(merge text-color {:font-size "24px"})`.
- The app is dark -- `#080A0D` under a fixed `linear-gradient(182deg, #313A4D,
  #080A0D)`, panels at `#1a1e28`, white type, orange accents. A page built on white
  reads as a stranger even when every other detail is right. Open the app and look
  before choosing colours.

## Magic items hold enough to print as cards (2026-09)

Checked against the spell-card layout, which is the closest existing thing:

    items                        805
    with a rarity                805
    needing attunement           427
    with a description           798   (7 have none: a data gap, not a bug)
    description length, median   142 characters
    spell text, median           656 characters

Descriptions are a quarter the length of spell text, so the card layout has room to
spare and the existing spill-to-the-back behaviour would rarely be needed.

Found while counting: `caster-bonus-item` wrote its description under
`::decription`. Nothing reads that key -- `views.cljs` destructures
`::mi/description` everywhere it shows an item -- so three Wand of the War Mage
entries carried a description nobody could see. A misspelled Clojure keyword is
still a valid keyword, so nothing complains; only counting the items exposed it.

## Verify card pages in a browser, not in PDFBox (2026-09)

PDFBox's own renderer cannot rasterise the embedded Vollkorn faces the cards use
-- `PDType0Font/load` with Identity-H encoding -- and draws them as mojibake. The
icons and the layout come out correctly, so the page looks like a text encoding
bug in the card code. It is not: the same page opened in a browser is perfect.

Save the document and open it in Chromium. Rendering a card page with
`PDFRenderer` will send you hunting for a bug that is not there. Saving and
reloading first does not help either -- it is the renderer, not the font subset.

The spell card path has always been this way; nothing about it is new.

## Drawing a card, and what a generated one owes nothing to a template (2026-09)

The magic item card is drawn, not composed from images: a chamfered border, rarity
diamonds, the rule under the header and the charge track are all path operators in
the content stream. That is real vector -- sharp at any size, correct in black and
white, and close to free in the file. There is no arc operator in a PDF stream, so
circles are four cubic curves with control points at 0.5523 of the radius.

Card coordinates run DOWN from the page top. `in-to-coord-y` is `72 * (11 - y)`,
which is why `draw-imagex` takes `(+ y 0.02)` for the top of a card while
`draw-text-to-box`, which is page-bottom referenced, takes `(- 11.0 y)` for the
same place. Mixing the two up puts art and text at opposite ends of the page.

**A generated card is not a blank one.** Every printable card template in
circulation spends its face on labelled slots -- NAME, TYPE, RARITY, CHARGES,
ATTUNED yes/no -- because a person has to write in them. This card already knows
all of it, so those slots would be dead space. The room goes to the description,
which is the part anyone rereads at the table, and the facts are set as text:
kind and rarity under the name, attunement in italic at the foot, and only when
the item needs it.

Two things earn their space:

- **Rarity as ranked diamonds**, five along the top edge, filled to the item's
  rank. Fanned through, a deck sorts itself; no amount of setting the word in type
  does that. `:varies` draws none rather than inventing a rank.
- **A charge track sized to the item.** The count comes off the description --
  the data has no charge field, it is prose -- and a die expression takes its
  maximum so the best roll still has a circle. An item with no charges draws no
  circles: an empty row is furniture, and marking charges is the reason to have
  the card in your hand.

  Sixty-four items say "charge". Fifty-five get a track. The nine that do not are
  the Manuals and Tomes whose *words are charged with magic*, which is a turn of
  phrase; the word alone must never be enough to draw one.

  The track sits in its own band between the header rule and a hairline, not at
  the foot. At the foot it arrived after the prose and got squeezed between the
  description and the attunement line -- last in the reading order and cramped,
  for the one thing on the card anybody touches mid-game. The order that works is
  name, what the thing is, what you have left to spend, what it does.

  Past twelve the track becomes a rule to write the remaining count on, over the
  total. Capping the PARSE at twelve instead -- the first attempt -- drew nothing
  at all for the Staff of the Magi, the Staff of Power, the Cube of Force and the
  Gem of Brightness, which are precisely the items whose charges anyone tracks. A
  card cannot show fifty circles, but nobody ticks fifty boxes either: they write
  a number, so the card gives them somewhere to write it.

Three collisions worth knowing, all found by rendering rather than reasoning: the
title has to stop clear of the diamonds, the body has to stop clear of the CHARGES
label, and the overflow mark belongs at the foot because the head is taken.

The rank marks went on their own rule across the top rather than beside the name.
At the name's shoulder the two compete and neither reads first, and the name is
pushed against the cut corner. On its own row the rail also reads as ornament,
which is what lets a second one at the foot -- one unfilled diamond between two
hairlines -- close the card without claiming to mean anything.

The name block always reserves two lines even for a name that needs one, so the
rule under the header falls at the same height on every card and a printed sheet
cuts square. A one-line name is dropped into the middle of that block instead of
sitting on top of the empty line, which is otherwise a visible hole.

Rarity gets a second treatment beyond the count: a legendary is drawn with a
hairline frame inside the border. The diamonds say which rank, but the rank that
matters should be obvious across a table without anyone counting.

## Frame decoration is a ladder, not a set (2026-09)

The card frame escalates with rarity alongside the rank marks, because the marks
answer "which rank" to someone who counts and the frame answers "is this worth
picking up" to someone across the table who does not.

Three families are implemented, and `dev/compare_item_cards.clj` renders all of
them across the five ranks so they can be looked at rather than argued about:

    :nested     more rules inside the border, and a heavy outer one at legendary
    :brackets   right-angled cornerwork, growing then doubling
    :diamonds   corner diamonds, outlined then filled, plus a mark at the foot

Each is a ladder: whatever a common gets, a legendary gets that and more. Families
that merely differ per rank read as five unrelated cards; families that accumulate
read as one card with a rank.

`:diamonds` is what ships. Its first attempt separated rare from very rare by
filling the corner marks and legendary by adding one small mark at the foot, and
neither step could be seen from a foot away. **Fill against outline is close to
invisible at card size; count, reach and weight are not.** The steps are now a
second rule, then small corner marks, then larger ones with strokes running out
along the edges, then a heavy outer border. Every step adds mass rather than
restating the last one in a different way.

The legendary mark in `:diamonds` sits at the FOOT. At the head it lands on the
rarity rail, which is the thing actually carrying the rank -- two marks in one
place, neither reading. The foot was empty and already had an ornament to grow.

The name is 12pt with a little letter spacing, not 10pt plain. A card title has to
carry across a table; at 10 it read as a heading on a page instead. `print-items`
takes the face, size and tracking as options for the same reason the flourish is
an option: so alternatives can be rendered side by side.

Two things at the foot of the card, both found by rendering it:

- **The attunement clause was being printed twice**, in the subtitle and at the
  foot, and the subtitle copy clipped mid-phrase. It belongs at the foot only.
- **The article goes on the first name alone** -- "by a sorcerer, warlock, or
  wizard", as the books set it. One per name is wrong and runs past the frame.
  Even set correctly the longest clause is 2.7in against 2.1in of card, so it
  wraps to two lines at 6.8pt rather than shrinking to the 5pt that would fit one.

Three more from looking at it at print size:

- **The name is indented further than everything else on the card**, and set at
  13pt. It is the only line set large, and without the extra air either side it
  reads as a wide block of type rather than a title.
- **It shrinks a step at a time until it fits two lines.** Holding the size loses
  the half of "Amulet of Proof against Detection and Location" that identifies
  it, and a card you cannot find in a stack has failed at its only job. The block
  still reserves two lines whatever the size, so the header rule stays level
  across a sheet.
- **Centre by measuring the string, never by halving a guessed width.** Both the
  charge label and the continuation line were positioned with a hardcoded number
  standing in for how wide the text sets. That lands near the middle and no
  nearer, and the miss moves with the font and the size. `string-width` returns
  INCHES, not points, which is its own trap. Where a rule and a label sit side by
  side, centre the pair as one group: centring the rule alone leaves the label
  hanging off its end and pulls the whole thing off axis.
- **The overflow marker is a phrase, not an icon.** At the bottom right the
  recharge glyph sat on the corner diamond and its arms, and every other spot down
  there belongs to the clause or the foot ornament. "continued on the back" is
  centred under the description, in room reserved before the text is drawn --
  whether it will spill is worked out first, by measuring the lines against the
  box, rather than discovered afterwards with nowhere to put the notice.

## Measure the card, do not look at it (2026-09)

`pdf/card-layout` holds every vertical position on the item card in one map --
`:down` from the top edge, `:up` from the bottom -- and
`dev/measure_item_card.clj` builds the worst card the layout can be asked for and
reports the clear space between each pair of elements. A sixteenth of an inch is
invisible on a screen and obvious in the hand, so the numbers find what the eye
does not, and once rather than one card at a time.

The worst case is deliberate: the longest name, the longest attunement list in
the data (bard, cleric, druid, sorcerer, warlock or wizard), a charge track, a
legendary frame with all its cornerwork, and a description long enough to run
onto the back. Nothing hides behind an easy case.

**Measure ink, not anchors.** The first version of that report compared the y a
box is anchored at, and read fine while the card looked cramped. Text is set a
leading BELOW its anchor and runs downward, so the anchor itself is empty; ink
reaches roughly seven tenths of the size above a baseline and two tenths below.
Comparing anchors also invented an overlap that was not there, between the
subtitle and the attunement badge, which share a row rather than stacking.

Two real faults came out of it:

- **The name shrank to 8.5pt**, smaller than the 8pt description under it, which
  is not a title any more. Shrinking to fit two lines had no floor. It stops at
  `:name-floor` now and takes a third line instead.
- **The header had 0.04in gaps** either side of the subtitle once a name ran to
  three lines. The whole header rhythm moved down rather than one gap being
  shaved, which is the difference between a layout and a pile of adjustments.

## The attunement badge, and centring the foot (2026-09)

A boxed **A** sits at the right of the subtitle line when an item needs
attunement. The foot says WHO may attune; the badge says THAT it must be, next to
the rarity, which is the pair anyone sorting a handful of cards is looking for. A
letter, not an invented glyph: there is no artwork for it, and a letter cannot be
mistaken for decoration. The subtitle's own box is narrowed when the badge is
present so the two can never meet.

The clause at the foot is centred. Everything else down there -- the ornament, the
continuation note -- is on the card's axis, and flush left it was the only thing
that was not. `draw-text-to-box` only sets flush left, so wrapped centred captions
need their lines split and placed individually.
