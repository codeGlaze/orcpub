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

## Correction again: styles 2-4 are lean because they are INCOMPLETE

The previous section called styles 2-4 "correctly built" because their field
count scales with page count. That reasoning was wrong, and backwards in the way
that matters. Comparing field NAMES rather than counts (ignoring the anonymous
"Check Box N" fields):

- Style 4 has no `backstory` field and no `equipment` field. pdf_spec.cljc emits
  both. write-fields! looks fields up by exact name and skips misses with a bare
  `(when field ...)` -- no warning, no error. A user on style 4 fills in their
  backstory and equipment, exports, and the PDF comes back without them, silently.
- Styles 2 and 3 lack `cantrips-6/7/8` and a set of `spells-N-M` names, so
  high-level casters lose spells off the export the same way.

These are absences, not renames. Style 4's extra field names are things like
Notes, other, Conditions and Insanities, Pronouns, Carry Capacity -- none of
which the code writes to. And a rename the code does not know about behaves
identically to a missing field, since the lookup is by exact name.

So: style 1 is bloated but COMPLETE. Styles 2-4 are lean because they drop data.
Do not treat them as the model for rebuilding style 1 -- rebuilding style 1 by
page extraction to look like them would mean rebuilding it to lose data.

This is a live bug for whoever can select those styles (fork/premium tiers).
Worth its own issue: either fill the missing fields into the templates, or have
write-fields! report names it could not place instead of skipping in silence.
The silent skip is what let this sit unnoticed.

What survives from all of the above is one narrow change: prune widgets that
belong to no page, at export time, in write-fields!. That only ever removes
fields with nowhere to draw, so it cannot drop a value the way these templates
do. Everything else here is a note, not a plan.

## Generating spell pages on demand (spiked, works)

Rather than pruning ghost fields, remove the reason they exist. routes.clj picks
one of seven pre-baked variants per style (sheet0..sheet6) by counting
spellcasting classes. Instead: ship a base plus ONE spell-page master, clone the
master as many times as the character needs, and build that page's fields in
code with the names pdf_spec already emits.

Spiked end to end against the real assets. What holds:

- A page clones by making a new page dictionary that REFERENCES the source
  /Contents, /Resources and /MediaBox. No pixel data copied: the master alone is
  421 KB, master plus six shared clones is 422 KB.
- Fields can be built per cloned page with arbitrary names, filled, and they
  render. Verified by filling a level-1 spell slot on a CLONED page and
  rendering it. About 12 KB of field structure per extra page.
- Clean masters can be cut from the existing assets using the orphan bug itself:
  delete every page but the one you want, then prune the fields left behind.
  Yields a 1-page 214-field spell master and a 2-page 118-field base, both with
  zero orphans.

Traps found, all of them load-bearing:

- Splitter DROPS THE ACROFORM. An isolated page comes out with zero fields. Do
  not use it to cut a page out.
- PDFMergerUtility duplicates the background image per merged copy (base plus 3
  spell pages ballooned to 1804 KB, larger than the original 9-page file) AND
  renames colliding fields, turning spells-1-1 into spells-1-1-1. Every name
  pdf_spec writes would silently stop matching. Do not use it either.
- Orphan pruning MUST work at the widget level, not the field level. Keeping a
  field because any one of its widgets is live left 101 orphans, since some
  fields own widgets across several pages. Filter each field's widget list and
  drop fields left with none.

Payoff: seven files per style become two, ghost fields stop existing rather than
being pruned after the fact, and the six-spellcasting-class ceiling goes away --
extra pages cost about 12 KB each.

Not yet done: wiring this into routes.clj in place of the sheet0..sheet6 cond,
and generating the per-page field names for pages beyond the sixth.

## Test with a sheet that has data on it

dev/sample_character.clj builds a level 20 evoker with every spell level filled
from the real wizard list and writes it through pdf/write-fields!:

    lein with-profile init-db run -m clojure.main dev/sample_character.clj

Rendering an EMPTY form proves almost nothing -- it was rendering empty forms
that hid the death-save checkboxes earlier in this work. Filling one turned up
three bugs immediately, none of them visible on a blank page:

1. **Page 1 fields sit one box off from the printed labels.** The tall right
   column is the field `features-and-traits`, and the artwork beneath it reads
   EQUIPMENT. The middle box holds `equipment`, printed as TREASURE. So a
   character's features and traits print under a heading that says EQUIPMENT.
   This is on style 1, the default and only public sheet.

2. **`spells-3-11-1` does not exist in style 1.** The level 3 column offers 13
   rows but the template skips the 11th, leaving a dead row that renders as an
   empty line with a bullet. Styles 2 and 4 have the field. A wizard with 13
   third-level spells silently loses one.

3. **Modifier boxes are too small for two digits.** The widgets are 14.4 x 8.6 pt
   and anything reaching "+10" clips -- "+11" renders as "+1" plus a fragment.
   This is NOT limited to saves: the second fixture (a face-caster) clips
   Deception, Persuasion AND the Charisma save on one sheet. Any modifier of +10
   or better is affected, which is routine at high level and unavoidable for a
   level 20 caster in their own casting stat.

Keep using this fixture for template work. Blank-form comparisons are how the
earlier mistakes in this document happened.

### Second fixture: four spellcasting classes

target/sample-multi.pdf is warlock 5 / sorcerer 5 / wizard 5 / cleric 5. One
spellcasting class only ever exercises the "-1" field suffix; this one exercises
-1 through -4 and the sheet4 template, which nothing else here touches.

It reproduces all three bugs above independently, and widens the third from
saves to every modifier.

What it confirms works: the four-class string fits CLASS & LEVEL by auto-sizing,
hit dice wrap to two lines, warlock pact slots stay separate from shared slots
(3rd level only, 1st and 2nd correctly blank), and long subclass names in the
spellcasting-class header auto-size rather than clip.

One thing that LOOKS wrong on this sheet but is correct: slots at 4th through
8th level with no spells listed beneath them. Caster level is 15 for slot
purposes, but no single class is above 5th, so nothing above 3rd level can be
prepared. Do not "fix" that.

### Third fixture: eight spellcasting classes (the real edge case)

target/sample-eight.pdf is bard 2 / cleric 2 / druid 2 / paladin 4 / ranger 4 /
sorcerer 2 / warlock 2 / wizard 2 -- twenty levels across eight casting classes.

**The sheet has room for six.** The template provides spellcasting sections -1
through -6, and routes.clj tops out at sheet6. Classes seven and eight have
nowhere to print, and write-fields! drops names it cannot find in silence, so
they simply are not there. On this character that is 50 values gone: both
classes' names, abilities, save DCs, attack bonuses, every slot and every spell.
A player prints their character and two of their eight classes are missing, with
nothing on the page or in the logs to say why.

This is bug 4 (the silent skip) and the six-class template ceiling landing
together, and it is the strongest argument for fixing the silent skip first. The
fixture now reports dropped names itself -- build! diffs the field map against
the template before writing -- which is exactly the check write-fields! should
be doing at runtime.

Worth noting the same reporting immediately caught spells-3-11 on the other two
fixtures without anyone looking for it.

Two more things this character surfaced:

- **Page 9 of every six-caster export is blank.** The sheet6 template ends with
  an empty FEATURES & TRAITS continuation page -- a label in the corner and
  nothing else. It has no filled fields and ships in every multi-caster PDF.
- **Long class strings shrink rather than clip.** "Brd2/Clr2/Drd2/Pal4/Rgr4/
  Sor2/Wlk2/Wiz2" renders visibly smaller than neighbouring fields but intact.
  That is the right failure mode, and worth preserving if the modifier boxes get
  fixed to auto-size.

No modifier on this character reaches +10, and nothing clips -- consistent with
the +10 threshold recorded above.

## Retraction: two of the reported bugs were fixture errors

Corrected after reading pdf_spec instead of trusting the template's field names.
The names do not describe their contents:

    features-and-traits    <- the EQUIPPED item list      (prints under EQUIPMENT)
    treasure               <- unequipped items, valuables (page 2)
    features-and-traits-2  <- the real features, actions and reactions, on the
                              continuation page           (prints under FEATURES & TRAITS)
    equipment              <- nothing. pdf_spec never emits this key; the box it
                              sits in carries the coin fields only.

See pdf_spec/equipment-fields and pdf_spec/traits-fields.

**RETRACTED -- "page 1 fields sit one box off from the printed labels."** They do
not. `features-and-traits` receives the equipment list and prints under
EQUIPMENT, which is correct. The fixture put features text into it and the
render looked mislabelled. The labels were right the whole time; the field NAME
is the misleading part.

**RETRACTED -- "page 9 of every six-caster export is blank."** It is not.
features-and-traits-2 is emitted by pdf_spec (line 143), given a font size in
routes.clj (line 575), and present on a real page in all seven style 1
templates. Page 9 was blank because the fixture never set that key.

Both mistakes have the same cause: reading the field names and inferring
behaviour rather than reading the code that fills them. The fixtures now set
these keys the way the app does, and carry a comment naming the trap.

Still standing, unaffected by this: spells-3-11 missing, the +10 modifier
clipping, the silent skip in write-fields!, and the six-class ceiling that drops
50 values on an eight-class character.

One small live oddity, not a bug as such: pdf_spec emits no :equipment key, so
the page 1 text field of that name is never filled. Its box shows the coin
fields only. Probably deliberate; noted so the next person does not "fix" it.
