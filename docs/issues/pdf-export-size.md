# PDF export size — measurements

Investigated while hardening `/character.pdf`. No code changed; this records
what was measured and, importantly, one conclusion that was wrong before it was
corrected.

## Where the bytes are, per style

The four sheet styles are unrelated documents with unrelated size problems.

| style | size (0-spells) | pages | images | what dominates |
|-------|-----------------|-------|--------|----------------|
| 1 (default) | 1,236 KB | 3 | none | 1,407 AcroForm fields |
| 2 | 241 KB | 3 | 17 small PNGs | already lean |
| 3 | 1,659 KB | 2 | 2 × 1275×1650 PNG | full-page raster |
| 4 | 2,610 KB | 2 | 2 × 2550×3300 JPG | full-page raster, 300 DPI |

Styles 3 and 4 are scans: 2550×3300 is letter size at 300 DPI, with form fields
laid over the image. Their size is inherent to print quality and field pruning
does nothing for them. Reducing DPI is the only lever and it costs exactly the
thing users care about.

Style 1 is pure vector with no images at all. Its weight is form fields.

## The style-1 form is one form, reused

Every one of the 28 templates carries the SAME 1,407 fields. The variants
differ only in how many spell pages are physically bound in:

    template    pages  fields  anon-boxes  orphaned widgets
    0-spells      3     1407      466        926
    1-spells      4     1407      466        834   (-92)
    ...                                       ...
    6-spells      9     1407      466        374   (-92)

Exactly 92 checkbox widgets attach per spell page present. The form has capacity
for roughly ten spell pages (926 / 92); the largest template uses six. So every
file carries fields for pages it does not contain — a non-caster sheet ships
with a complete spellcasting apparatus that has nowhere to render.

## Pruning fields whose widgets have no page

Rule: drop a field only when ALL of its widgets have no page reference. This is
structural, not name-based, so it cannot catch a functional field.

    style 1, 0-spells   1,236 KB -> 313 KB   (74.7%)   fields 1407 -> 221
    style 1, 1-spells   1,264 KB -> 511 KB   (59.5%)
    style 1, 3-spells   1,340 KB -> 791 KB   (41.0%)
    style 1, 6-spells   1,410 KB -> 1,472 KB (-4.4%)
    ALL 28 TEMPLATES     59.4 MB -> 55.6 MB   (6.4%)

The headline number is 6.4%, not 75%. Styles 2-4 have no orphans at all and get
marginally LARGER from the re-save. The win is confined to style 1, and shrinks
as spell pages are added.

It is still worth considering, because style 1 is the default and most
characters have zero or one spellcasting class — the two cases that save 75% and
60%. But "6.4% across the assets" is the honest figure for the work involved.

## A wrong conclusion, and why the test missed it

The first attempt pruned every field named "Check Box N" — 466 of them — and
measured 1,236 KB -> 440 KB with zero differing pixels across the rendered
pages. That was reported as safe. It was not.

Six of those 466 are the DEATH SAVE circles on page 1. Pruning them leaves
circles that can never be filled.

Two compounding errors hid it:

  * The comparison rendered an EMPTY form. An unticked checkbox draws nothing,
    so it compared "absent" against "empty" and found them identical. Every
    visible circle on the sheet is drawn in the page content stream; the widget
    contributes only when ticked.
  * It rendered pages 1-2 of a 3-page document.

The correct test ticks every checkbox and renders every page. Under it, the
name-based prune differs by 299 pixels on page 1 (the death saves) and the
structural orphan-only prune differs by zero on all three pages.

Any future work here must tick the boxes and render every page before claiming
a prune is safe.


## How the orphans got there, and the better fix

Widget counts across the style-1 family, distinguishing "missing the /P
back-pointer" from "not in any page's annotation list at all":

    template   widgets   no /P   on-a-page   TRUE orphans
    0-spells      1931    1810         121           1810
    1-spells      1931    1596         335           1596
    ...                                               ...
    6-spells      1931     526        1405            526

The two columns are identical, so every widget missing /P really is absent from
every page. Exactly 214 widgets attach per spell page present -- the page's full
complement of spell name fields, prepared checkboxes and slot fields.

That linear pattern is the signature of how the variants were made: one 9-page
master form, saved repeatedly with pages deleted. Deleting a page removes the
page and its annotations but leaves the fields in the AcroForm, orphaning 214
widgets each time. Nothing is "riding on one page" -- they are on no page.

pdf/fix-widget-page-refs! is a related but different workaround. It walks
pages -> annotations and sets /P on widgets that ARE on a page but lack the
back-pointer. It cannot see these, because they are not in any page's
annotation list. Anyone reading that function should not assume it covers this.

### Prune at export, not in the assets

The templates are static, but the exported PDF is built at runtime by
write-fields!. Dropping fields whose widgets are all page-less THERE gets every
user a smaller download with no asset migration, no risk to the source files,
and no 28-file regeneration to verify. It is a code change in one function
rather than an art pipeline.

The asset-side fix -- rebuilding variants by page EXTRACTION rather than page
deletion -- is the real cure, but it is a much bigger job and the runtime prune
makes it optional rather than urgent.

## Which styles are actually in colour

Measured per-pixel chroma (max channel minus min) on a rendered page 1 of each:

    style       coloured px     max chroma
    1                 0.00%              3
    2                 0.00%              3
    3                 8.10%            192
    4                 0.00%              4

Max chroma of 3-4 out of 255 is render noise. Styles 1, 2 and 4 are genuinely
black and white. Style 3 -- the official WotC sheet with the teal/gold ram crest
-- is the only one with real colour, and only ~8% of its pixels.

Template sizes:

    style 1   1.21 MB
    style 2   0.24 MB
    style 3   1.62 MB
    style 4   2.55 MB

### Style 4 is the biggest lever

Style 4 ("Cthulhu Mythos Sagas") is the largest file and is visually grayscale,
yet pages 1-2 are stored as DeviceRGB JPEG -- three channels carrying identical
values. DeviceGray is lossless by inspection here and should take roughly a
third off. No quality argument to have.

### Style 3 is the one real colour sheet, stored in CMYK

Grayscale would destroy style 3 specifically -- but ONLY style 3. An earlier
revision of this document said the same of style 4, which is wrong: style 4 has
no colour at all.

It is stored as DeviceCMYK at 8 bits per component: four channels, for a
document that is viewed on screen and printed on home printers. Converting the
page-1 image to RGB, losslessly:

    original CMYK 8bpc (Flate)    684 KB
    as RGB PNG                    335 KB   (51% smaller, lossless)
    as RGB JPEG q=0.92            243 KB   (64%)
    as RGB JPEG q=0.85            199 KB   (71%)

RGB PNG halves it with no quality question at all -- it drops a channel the
display path never uses. The JPEG options trade quality for more and should not
be taken without someone looking at a printed sheet.

Style 4 does not have the CMYK problem, but has the RGB-for-gray one above.
Images ARE shared across repeated pages in
both styles -- one object serves pages 3-8 -- so there is no duplication to fix.

## Not investigated

  * Whether pruning breaks a user who duplicates a spell page by hand in
    Acrobat. Orphaned widgets are not reachable and duplicating a page copies
    its own annotations, so it should be fine — but that is reasoning, not a
    test.
  * Whether styles 3 and 4 could drop to 200 DPI without visible loss in print.

## Related, already available

`pdf/write-fields!` already takes a `flatten?` argument and `routes.clj` already
passes it through; nothing in the UI ever sets it. Flattening style 1 gives
1,236 KB -> 252 KB but produces a non-editable PDF, so it belongs as an option
rather than a default — people reasonably want to keep editing their sheet.

## Correction: this is a style 1 problem, and style 1 is the only public style

Two facts change the scope of everything above.

**Only style 1 has orphans.** Checked all four families, fewest and most spell
pages:

    style 1, 0 spell pages    1931 widgets   1810 orphaned   94%
    style 1, 6 spell pages    1931 widgets    526 orphaned   27%
    style 2, 0 / 6            120 / 1404              0       0%
    style 3, 0 / 6            112 /  844              0       0%
    style 4, 0 / 6            129 /  807              0       0%

Styles 2-4 scale their field count with page count, which is what a correctly
built variant looks like. Style 1 carries all 1931 widgets in every variant no
matter how many pages it has.

**Only style 1 is reachable.** integrations/sheet-styles returns a single entry
("Original 5e Character sheet", value 1). Styles 2-4 are fork overrides gated by
user tier; the assets ship in the repo but the dropdown never offers them.

So the fix is 7 files, not 28, and the runtime prune in write-fields! only has
to handle style 1 -- where it removes up to 94% of the widgets. The style 3
CMYK->RGB saving noted above is real but unreachable in the open app, and the
style 4 RGB-for-gray one likewise. Leave both recorded, do neither first.

Note also that routes.clj accepts valid-sheet-styles #{1 2 3 4} while the UI
offers only 1. A hand-crafted request can select a non-public style. These are
static assets in the repo, so this is not a disclosure issue, but the server
being deliberately broader than the client is worth knowing rather than
rediscovering.
