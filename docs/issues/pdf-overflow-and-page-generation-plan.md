# Plan: generate pages on demand, and stop cropping text silently

Techniques are proven and written up in `docs/kb/pdf-form-techniques.md`.
Findings and false starts are in `docs/issues/pdf-export-size.md`. This is the
build plan.

## What is wrong today

1. Seven pre-baked variants per style, chosen by counting spellcasting classes.
   Each carries every field for six classes, so ~1600 of ~1900 widgets on a real
   export point at no page. That is 88% of a 2.6 MB download.
2. Six spellcasting classes maximum. A seventh or eighth is dropped entirely —
   50 values on the eight-class fixture, with no error.
3. `write-fields!` skips names the template lacks, in silence. That is how 2 and
   4 went unnoticed for years, and how styles 2-4 shipped without `backstory`.
4. Text longer than its box is silently shrunk toward a 4pt floor and only then
   cropped. A few sentences in "Bonds" already drops below 7pt. Nothing warns.
5. Any modifier of three characters (+10 or worse than -9) is cropped.

## Order of work

Each step is independently shippable and independently verifiable.

### 1. `pdf/prune-orphan-widgets!` — biggest win, smallest change

Drop widgets belonging to no page, then fields left with no widgets. Per-widget,
not per-field. Call from `write-fields!` before writing.

Halves a real export (2679 KB to 1313 KB). Verify by value diff, not pixels: all
248 valued fields present and unchanged. Ships on its own with no other change.

### 2. `write-fields!` reports names it could not place

Return or log the field names with no home instead of skipping silently.
`dev/sample_character.clj` already does exactly this diff in six lines and it
immediately found `spells-3-11`. Without it, every later step fails quietly.

Not a warning users see — a log line and a return value tests can assert on.

### 3. `pdf/fit-text` — the SSOT for "does this fit"

    (fit-text s {:width w :height h :font-size fs}) => {:head "..." :tail "..." :lines n}

Greedy word wrap at the widget's real width, count lines against height. Used by
steps 4 and 5 so overflow decisions are made in one place, and by tests to assert
capacity without rendering anything.

Must measure the ON-PAGE widget. Every field has two; the first is the orphan.

### 4. Overflow driven by a minimum font size

The fields AUTO-SIZE (see the KB). They do not crop at a fixed word count; they
shrink to a 4pt floor and only then start losing text. So the cutoff is a
minimum legible size, not a character limit.

    (fit-text s widget {:min-font-size 7})
      => {:head "what fits at >=7pt" :tail "the rest" :font-size 7.0}

Budgets at 7pt: ideals/bonds/flaws 25 words, personality-traits 44,
attacks-and-spellcasting 127, other-profs 147, equipment 447, backstory 987,
features-and-traits-2 3369.

Small page 1 boxes cannot grow -- the artwork is fixed -- so their `tail` spills
to a continuation page under a heading naming the field, with a marker in the box
pointing at it. Large boxes spill to another continuation page of their own.

Open question for review: what floor? 7pt is comfortable in print, 6pt is small
but readable and buys roughly 60% more before spilling. 7pt is the safer default
and the one assumed here.

### 5. Continuation pages that grow

`features-and-traits-2` holds ~3369 words at 7pt and a real level 20 wizard used
about a tenth of that, so this is not urgent — but it is the same mechanism as step 6 and costs
little once that exists. Generate `features-and-traits-3`, `-4` as `fit-text`
reports a remainder.

### 6. Spell pages per class, unbounded

Replace the `sheet0..sheet6` cond in `routes.clj` with: load a base, add one
spell page per spellcasting class, each with uniquely-named fields.

Proven end to end by a spike, now removed and superseded by
`pdf/add-spell-pages!` — eight classes, zero duplicate
names, 757 KB, smaller than today's six-class file. Also removes the ceiling in
step 2 of the problem list and makes the pre-baked variants redundant.

Requires `pdf_spec` to emit names past the sixth class; it stops there today
because nothing could receive them.

### 7. Modifier clipping

Separate from the rest. Either widen the widgets in the templates, or let those
fields auto-size like the class-level field already does. The auto-size path is
code-only and matches behaviour the sheet already has elsewhere.

## What must not regress

- No value may be dropped. Verify by diffing field values before and after, not
  by comparing renders.
- No duplicate field names, ever. A duplicate silently ties two pages' values
  together. Assert zero duplicates in a test.
- Existing exports must keep working: the same `pdf_spec` field names, same
  fillable behaviour, same flatten option.

## Testing

- Unit: `fit-text` against known box sizes; prune preserves all valued fields;
  generated pages produce no duplicate names.
- Fixtures: the three characters in `dev/sample_character.clj` (level 20 single
  class, four classes, eight classes) each build with zero dropped names.
- A long-text fixture that overflows every small box, to prove spill works.
- Compare against `docs/kb/pdf-form-techniques.md` capacities so drift is caught.

---

## Done, and what is left

Items 1 through 7 shipped on `fix/pdf-endpoint-hardening`, plus two that were not
in the original plan: a field carrying widgets on two pages was found to mirror
one class's ticks and expended slots onto another's page, and a spell level box
can be relabelled so a spare one — the cantrips box included — can carry a
different level.

### 8. Spell level packing — designed, not built

The mechanism works: `reuse-cantrips-box!` and `relabel-spell-level!` will put
any level in any box, with the numeral, the slot labels and two slot inputs.
What decides *which* level goes in which box does not exist. `pdf_spec` still
emits one level per box, so a character with three level 1 spells and two level 2s
spends two pages on five spells.

Packing raises questions about what a spell list on a printed sheet is for, and
they want answering before any of it is built:

- **Which spells belong on the sheet at all?** Only the ones the player chose, or
  also the ones granted for free — by class, subclass, background, race or
  subrace? A Cleric already gets its whole domain list; a Sorcerer does not.
- **If granted spells are included, does the sheet say where each came from?**
  Measured, and row width is not the obstacle it looks like. A spell row is
  157.95 x 9.94pt and the text auto-sizes to the HEIGHT, landing at 6.42pt, where
  the longest name in the SRD -- Protection from Evil and Good -- uses 85.6pt,
  just over half the row. Appending "(Life Domain)" reaches 125.9pt and still
  fits: none of the 319 spell names overflow with a realistic source suffix.
  Only something like "(Circle of the Land: Underdark)" does, at 175.5pt.

  So a parenthesised source is close to free, and the question is whether it
  reads well rather than whether it fits. `target/mock-source.png` shows the
  cases side by side at real size.
- **Can two classes share a page?** Two small lists — a Paladin's and a Ranger's
  at low level, say — could sit in one sheet's boxes rather than taking one page
  each. That needs a per-box class label rather than a per-page one, since the
  page heading currently names a single class.

  The room for that label already exists: the band above every level bar is
  empty except level 1's, which carries the printed SLOTS TOTAL / SLOTS EXPENDED
  legend and still has clear space to its right. A class name set there in the
  legend's own grey costs no rows and no width. Combined with
  `relabel-spell-level!`, which renumbers a box to the level it actually holds,
  a Cleric and a Paladin fit on one sheet with each box saying whose it is and
  what level it is. Mocked up in `target/mock-shared.png`.
- **What does a box mean once packed?** Today a box is a level. If two levels
  share a page, or two classes share a sheet, the numeral and the slot totals
  have to say which class as well as which level, or a player reading the printed
  sheet cannot tell whose slots those are.

- **How much room is there to pack into?** A sheet has 100 spell rows, spread
  8 / 12 / 13 / 13 / 13 / 9 / 9 / 9 / 7 / 7 across levels 0 to 9. Rows, not
  width, are the scarce resource, and they are unevenly distributed: a level 9
  box holds 7. A packer that moves a level into another box has to check the
  destination is big enough, not just free.

The last one is the constraint the others hang off: a box carries a level, and a
page carries a class. Packing across classes breaks the second, and that is a
larger change than packing levels within one class.


#### What each option is worth, measured

The eight-class fixture in `dev/sample_character.clj` is the case that wastes the
most paper. Its eight classes hold spells at only 14 class-levels between them,
and a sheet has ten boxes:

    Bard 2      levels 0,1      Paladin 4   level 1
    Cleric 2    levels 0,1      Ranger 4    level 1
    Druid 2     levels 0,1      Sorcerer 2  levels 0,1
    Warlock 2   levels 0,1      Wizard 2    levels 0,1

    today, a page per class                 8 pages
    a column per class, 3 columns a sheet   3 pages
    a box per class-level, 10 a sheet       2 pages
    packed by rows, 100 rows a sheet        2 pages   (floor; ignores box fit)

Correction to the third line: "a column per class" assumed one class per column,
which is not the constraint. What matters to a player is not hunting for their
Warlock list, and that is protected by never splitting a class across columns --
a column can hold several classes as long as each is a contiguous run of boxes
within one. First-fit under that rule puts the eight-class fixture on **2 pages**:

    page 1   left    Bard, Paladin        page 2   left    Sorcerer
             middle  Cleric, Ranger                middle  Warlock
             right   Druid                         right   Wizard

So the simple rule reaches the same 2 pages the general packer does, and gets
there without a search: walk the classes, put each in the first column with room.

The columns are unequal, and both limits bind:

    left    boxes 0,1,2    3 boxes, 33 rows
    middle  boxes 3,4,5    3 boxes, 35 rows
    right   boxes 6,7,8,9  4 boxes, 32 rows

A class needing four spell levels only fits the right column. On page 1 above it
is rows that fill the columns, not boxes -- Bard 20 + Paladin 11 leaves 2 of 33.

That fixture is the worst case for rows because it fills every row of every level
it touches; a real character picks far fewer, so real sheets pack tighter than
this.

#### Three shapes, mocked up on the real sheet

- **Classes down columns, never split** (`target/mock-columns.png`). Cleric,
  Paladin and Ranger on one sheet, each column headed by its class in the empty
  band above the bar, each box renumbered by `relabel-spell-level!` to the level
  it actually holds. This is the chosen shape: a player finds a class by looking
  down one column rather than flipping pages, which is the cost that actually
  matters. A column takes more than one class when they fit.
- **Two classes in one box** (`target/mock-inbox.png`). A rule across the rows,
  then the second class, its own save DC, and a box for slots left. Costs one row
  of twelve. For the case where a class has one short list and a whole box is
  more than it needs.
- **A source beside each granted spell** (`target/mock-source.png`). Free on
  width, as measured above.

#### What mocking them up turned up

Two details that only appear once it is on the page:

- The divider row is a heading, not a spell, so **its prepared checkbox has to
  go**. Left in place it invites a tick against a class name.
- In a shared column, the boxes a class does not use **still carry their printed
  numeral**. A Paladin column with an empty box numbered 4 reads as Paladin level
  4 spells. Unused boxes need blanking or relabelling, not just leaving.


#### Where a page should break, and two corrections

**A section is a spellcasting ability, not a class.** `make-pages` in `pdf_spec`
groups by `:ability` and collects every class sharing it into `ability-classes`,
which is what the page heading lists. There are three casting abilities, so a
character has **at most three spell sections, ever**:

    INT  wizard
    WIS  cleric, druid, ranger
    CHA  bard, paladin, sorcerer, warlock

The earlier "eight classes, eight pages" figure was wrong. It came from
`dev/sample_character.clj`, which writes eight suffixes directly and bypasses
`pdf_spec`. Through the real pipeline those eight classes make three sections.
Three sections against three columns is not a coincidence worth designing around,
but it does mean the column work is bounded and small.

**`pdf_spec/level-max-spells` is already the box row capacity** -- the same
`{0 8, 1 12, 2 13, ...}` measured off the artwork here. The two were derived
independently and agree.

The current rule splits each level into chunks of its own box and takes the worst
level's chunk count as the page count. It breaks on **one crowded level**, so a
section with a fat level 1 and thin everything else spends whole pages carrying
level 1 leftovers while nine boxes sit empty:

    cleric 20, whole preparable list      105 spells    now 2   packed 2
    druid 20, whole preparable list       106 spells    now 2   packed 2
    wizard 20, whole list                 204 spells    now 3   packed 3
    paladin 20 / ranger 20                 31 / 37      now 1   packed 1

    wizard book, 27 at level 1, 4 elsewhere 59 spells   now 3   packed 1
    bard, 20 at level 1, nothing over 3     35 spells   now 2   packed 1
    the eight-class character, by ability   88 spells   now 6   packed 3

Where a section's spells genuinely exceed a sheet, the current rule is already
optimal -- a Cleric 20's 105 spells need two pages however they are arranged. It
wastes only on the lopsided shapes, which is where the complaint comes from.

**The rule to adopt: break when the section's rows exceed the sheet, not when one
level exceeds its box.** Fill boxes in level order; when a level outgrows its own
box, spill into the next free box, relabelled to that level; start a page only
when no box on the page has room. Single class, multiclass and granted spells all
fall out of that one rule, because they are all just rows in an ability's section.

#### Granted spells are already modelled

`mod5e/spells-known` takes `[level spell-key spellcasting-ability class &
[min-level qualifier class-key]]`, so a spell granted by a feat, background or
boon already carries:

- an **ability**, which decides the section it lands in;
- a **class**, used as a source label -- a skill feat grants with `"Arcanist"` --
  which reaches `ability-classes` and so the page heading;
- a **qualifier**, `"at will"` or `"once per long rest"`.

And `pdf_spec` already prints the qualifier beside the name as `(qualifier)`.
So the row-level marking asked about above partly exists: what a granted spell
does not show on its row is its **source**, which currently only reaches the
heading. Putting the source there too is a change to one `str` in `pdf_spec`,
and the measurement above says the row has the width for it.


#### What 5e actually keeps separate, and where the sheet does not

The grouping should follow the rules rather than the layout, and the rules split
three ways (PHB p.164, Multiclassing):

- **Spells known and prepared are per class.** "You determine what spells you know
  and can prepare for each class individually, as if you were a single-classed
  member of that class." A Cleric 5 / Druid 5 prepares WIS mod + 5 cleric spells
  from the cleric list, and WIS mod + 5 druid spells from the druid list. Two
  lists, two budgets.
- **Spell slots are one pool** shared across every Spellcasting class, from the
  combined caster-level table.
- **Pact Magic is a separate pool**, usable on Spellcasting spells and vice versa.

`pdf_spec` groups a section by `:ability`, which crosses the first of those. Both
a Cleric and a Druid cast off WIS, so they land in one section and their lists
interleave alphabetically. Verified by calling `make-pages` directly:

    section  ability wis   heading: Cleric, Druid
      level 1
        Bless             (Cleric, not printed)
        Cure Wounds       (Cleric, not printed)
        Entangle          (Druid, not printed)
        Faerie Fire       (Druid, not printed)
        Goodberry         (Druid, not printed)
        Guiding Bolt      (Cleric, not printed)
        ...

The row is built as `spell-name` plus `(qualifier)`; `:class` is carried on every
spell but never reaches the page. A player cannot tell which list a spell came
from, and it decides which preparation budget it counts against.

Ability is the right grouping for the **header** -- save DC and attack bonus are
per ability, so one header serves both classes. It is the wrong grouping for the
**rows**. So the section can stay as it is, and the fix is at the box: a run of
boxes per class, labelled, which is the column shape already chosen above. That
lines up: three abilities, three columns, and each class's list kept whole.

Slots are the character's, not the class's -- which contradicts the comment now
in `spell-page-fields` saying "slots belong to the class". They are repeated per
section today, harmless while every section shows the same combined pool, but the
reasoning should be corrected when this is next touched.

Pact Magic is a real defect and is written up in
`docs/issues/pact-magic-slots-overwrite.md`: the pact schedule is merged over the
Spellcasting table, so a Cleric 3 / Warlock 2 shows 2 first-level slots where the
character has 4 plus 2 pact.


#### Flagging concentration, components and casting time on the row

Everything needed is already on the spell, though not always as its own field:

    :duration      "Concentration, up to 1 minute"   -> concentration
    :ritual        true
    :casting-time  "1 bonus action" / "1 reaction, ..." / "1 action" / "10 minutes"
    :components    {:verbal true :somatic true :material true
                    :material-component "diamonds worth 300 gp, which the spell consumes"}

Concentration has no field of its own; it is the start of `:duration`. A costly
material is a `\d+ gp` in the prose of `:material-component`.

How much of the list each flag touches, of 319 spells:

    concentration          126   39%
    longer casting time     59   18%
    costly material (gp)    52   16%
    ritual                  29    9%
    bonus action            14    4%
    reaction                 4    1%

Cost, with the annotation appended as `C  R  BA  V S M(300gp)`: **none of the 319
rows overflow.** The widest is `Instant Summons   R  1 minute  V S M(1,000gp)` at
136.6pt of the 154 available; typical rows land between 44 and 67pt. Rendered at
print size in `target/mock-annotated.png`.

Worth doing, but not all of it is worth the same. Ranked by what changes a
decision at the table:

- **Concentration** is the one to have. It touches 39% of spells and is the only
  flag that makes two spells mutually exclusive.
- **A costly material** stops the spell happening at all if it is not in the pack,
  and the gp figure is the part worth printing, not the prose.
- **Bonus action and reaction** change what else can be done that turn. Only 5%
  of spells between them, so they cost almost nothing to carry.
- **Plain V S M** with no cost is the least useful and the widest, being on
  nearly everything. It is the part to make optional if any of it is.

#### Inline flags do not scan, and the fix is alignment rather than spacing

Appending the flags to the name -- `Bless   C  V S M` -- fits, but it does not
work. `C` is the same visual class as the letters beside it: single capital, same
weight, same spacing. Finding a letter among other letters is a serial search, so
the eye has to read every row. Shape, weight, size and position are found in
parallel; alphanumeric identity is not. Equal spacing makes it worse by grouping
`C` with the components rather than separating it from them.

More whitespace does not fix a serial search. A fixed column does, by turning
"read each row" into one vertical sweep.

The geometry allows it. At the 6.42pt the rows draw at, spell names run:

    min 8.6pt   median 37.1pt   90th 56.0pt   99th 73.8pt   max 85.6pt

so the longest name ends at x 127.9 in a row spanning x 40.32 to 198.27. A
concentration column at x+98 and a components column at x+110 clear the longest
name by 10pt, and the widest component string -- `V S M 25,000gp` at 46.0pt --
fits the remainder.

Three variants rendered at print size in `target/mock-scan.png`: inline as
proposed, a bold `C` in a fixed column, and a filled square in a fixed column.
Both aligned versions are found in one sweep; the inline one is not. The bold `C`
is the one to take, because the square needs a legend and reads like another
checkbox next to the prepared column.

Correcting what was written above: the annotation does NOT have to match the
name's size and colour. That is only true while it lives in the same text field.
Drawn separately -- or written into its own field -- it takes its own size,
weight and grey, which is what lets the components recede to 5.6pt grey while the
name stays 6.42pt black and the concentration mark stays 7pt bold. The mock draws
them, since they are computed values a player never edits.

Drawing is also what keeps the file from growing back. Measured on the
six-caster template with all six spell pages filled and 594 rows annotated:

    names only, as today          1161.7 KB   1403 fields
    annotations drawn             1168.3 KB   1403 fields    +6.6 KB   +0.6%
    annotations as form fields    1550.8 KB   2591 fields   +389.1 KB  +33%

11 bytes a row drawn against 671 as fields. This branch took a production export
from 2679 KB to 1313 KB by pruning orphaned widgets; adding a field per column
per row would give a third of that back.


### 10. Ship one template per style, not seven

The 44.3 MB of templates in `resources/` is not 44 MB of artwork. Hashing every
image across all 28 files:

    436 image objects, 74 distinct
    32.7 MB stored, 13.2 MB of unique pixels

    style 2   396 objects,  62 distinct   4.6x duplication
    style 3    20 objects,   3 distinct   6.8x duplication
    style 4    20 objects,   9 distinct   2.2x duplication

The variants were cut from a master by deleting pages, so each of a style's seven
files carries its own copy of the base sheet's artwork. Style 3 ships three
distinct images twenty times.

Shipping one master per style and generating the rest:

    style 1   2.7 MB -> 0.3      style 3    8.7 MB -> 1.0
    style 2   3.2 MB -> 0.4      style 4   29.7 MB -> 4.2

    44.3 MB -> 5.9 MB, 86% less

The machinery exists and is in production: `add-missing-spell-pages!` already
clones a spell page for every class past the template's last, and cloning is
nearly free because the copy references the master's `/Contents`, `/Resources`
and `/MediaBox` rather than duplicating them -- measured at 421 KB for a master
and 422 KB for the master plus six clones.

**The layouts were checked before proposing this**, since the four styles are
laid out differently and sharing a master across them would be wrong. Sharing is
per style, and within a style the seven variants are the same sheet:

    page 1 rendered and compared against the 6-spell variant, per style

    style 2   every variant pixel-identical
    style 3   every variant pixel-identical
    style 1   identical but for 392 px (1-spell) and 44 px (3-spell)
    style 4   every variant differs, 0-spell by 45,907 px

The style 4 and style 1 differences are encoding, not design. Rendering style 4's
0-, 1- and 6-spell page 1 side by side shows the same Cthulhu Mythos sheet in all
three; its backgrounds are DCTDecode, so each variant carries a separately
encoded JPEG of the same art and the differences are speckle along every edge.
Styles 2 and 3 come out identical because their images are lossless Flate, which
re-encodes deterministically. Style 1's 392 pixels sit entirely in one region
around the EQUIPMENT box and, cropped at 300 DPI, the two are indistinguishable
-- scattered along the outline and glyph edges, which is coordinate precision in
the content stream.

**Page 1 is the wrong page to check this on**, and checking only it was the first
mistake. The variants differ by how many SPELL pages they carry, so the spell
pages are what a cloning scheme would copy. Comparing every spell page of the
6-spell variant against its first spell page:

    style 1   page 4 differs by 1396 px, the rest identical
    style 4   page 8 differs by 628 px, the rest identical

Style 1's is precision noise -- the two pages are the same layout down to the
numerals and the Wizards footer, with the differences scattered across the whole
page rather than gathered anywhere.

Style 4's is not. Its last spell page carries a licence line the others do not:

    dungeonmastersvault.com by permission - Petersen Games LLC 2021

Where that line sits differs by style, and it is the real constraint on
generating pages:

    style 1, 2   on every page except the last
    style 3      absent entirely
    style 4      on the LAST page only -- page 2 of the 0-spell, page 8 of the 6-spell

So for style 4 the structure is not "N identical spell pages". It is N-1 plain
spell pages plus one carrying an attribution. Cloning its first spell page to
make the rest would drop that line from the document altogether, and cloning the
1-spell master -- whose only spell page is also its last, and so carries the line
-- would stamp it on every page instead. Neither is acceptable for a licence
attribution.

A master per style is still sound, but generation has to place exactly one
attribution, at the end, per that style's convention.

The attribution does not have to be redrawn. The attributed page already exists
in the template, so a master keeps two spell pages -- a plain one to clone and
the real attributed one to finish with -- and the licence line stays the original
artwork rather than something reproduced.

Proved on style 4, the hardest case. A four-page master (character, background,
plain spell page, attributed spell page), then four clones of the plain page
inserted before the attributed one:

    8 pages, attribution on page 8 only, as the original
    artwork identical on all 8 pages, 0 differing pixels at 100 DPI

The clone copies `/Contents`, `/Resources`, `/MediaBox` and `/Rotate` and nothing
else, so a cloned page arrives without form widgets and renders 6836 px lighter
until they are added -- the prepared checkboxes are what those pixels are.
Creating the widgets is `add-spell-page!`'s existing job, not a new problem.

**The saving is not in making the master smaller.** A four-page master is 4891.6
KB against the eight-page template's 4958.6 -- only 67 KB less, because the pages
of one template already share their images, so dropping four spell pages drops no
image. The saving is shipping one file per style instead of seven:

    style 4   seven files 29.7 MB   one master 4.9 MB

Not done here, because it is a change to what ships and to how `routes.clj`
chooses a template, not a change inside one. The selection would go from picking
one of seven files by caster count to always loading the one-spell-page master
and cloning up, with a character that casts nothing needing its spell page
removed rather than added. That makes every multi-caster export depend on the
cloning path, which today only carries the cases past six classes. It wants its
own branch and its own before-and-after render comparison per style.

Worth keeping in proportion: this is deployment size -- the container and the
uberjar. A player downloads one sheet, and those are 250 to 640 KB.


#### Which file to use as each style's master

Not the narrowest file, but the smallest one containing every distinct *page kind*
that style needs. Style 4 marks only its last page, so its 1-spell file's single
spell page is the marked one and cloning it repeats the mark. Its 2-spell file
carries both kinds:

    style 4 master = the 2-spell file, 4454.7 KB
      page 1 character   page 2 background   page 3 spell   page 4 spell + attribution

    grown to six spell sections -> 8 pages, 4454.8 KB, attribution on page 8 only
    the shipped 6-spell file is 4958.6 KB

So the original convention comes out exactly right, 500 KB smaller than what
ships, with no attribution stamped or reproduced. Styles 1, 2 and 3 need only
their 1-spell file, since every spell page they have is alike.

#### Attribution placement as a setting

Worth having, and mostly achievable by choosing which page kind to clone where
rather than by drawing anything:

    :all           clone the marked page          every style with a footer
    :last          clone plain, finish marked     needs a plain page to clone
    :first         marked first, then plain       same
    :every-other   alternate the two kinds        same

The limit is that a footer baked into a page's content stream **cannot be
removed**, only added, so a placement other than `:all` needs a PLAIN spell page
to clone -- and as of the one-page-per-style masters, no style ships one. Style 4
did, in the 2-spell file that was its master until the marked page was proved to
be the plain page plus an appended BT/ET block; that file is retired.

So every style is `:all` today, and the other placements mean DRAWING the footer
rather than shipping a second page kind. For style 4 that is 4pt CartaMarinaBold
at x 24.9, y 12.1, an embedded subset already present in the document and
reusable rather than re-embedded -- which is cheaper than the 4.5 MB a second
master costs, and is the route to take if the setting is built.

### 8b. Packing belongs in the browser (2026-09)

Where the packing decision runs was left open above. It should run client-side, and
not only because it is cheaper.

The server receives a flat map of field names to values. It does not know what a
spell level is, how many a character knows, or which class granted what -- packing
server-side means giving it a domain layer it does not have. The builder already
computes all of it.

So the browser decides which level goes in which box, emits the field map to match,
and sends a small instruction list alongside it:

    {:relabel [{:section 1 :box 0 :label "3"} ...]}

The server applies `relabel-spell-level!` and `reuse-cantrips-box!` per instruction
and needs nothing else. Those instructions are caller-supplied, so section, box and
label all need bounds-checking before use -- the same hole as the sheet style id,
which reached a resource path before it was validated.

### 8c. Packing is a choice, not one layout (2026-09)

Because packing runs in the builder it costs nothing to offer several ways to
organise a list, and the right one differs by character. The shapes worth having:

- **One level per box.** What ships today. Predictable, and wasteful for a
  character with three level 1 spells and two level 2s.
- **Packed.** Fill boxes in order regardless of level, relabelling as it goes.
  Fewest pages.
- **One page per class.** Today's multiclass behaviour, and what a player wants
  when two lists are both long.
- **Classes sharing a page.** Two short lists side by side, each labelled. The
  case that started this.

The default should follow the build rather than being asked for every time:

- A prepared caster with a large list -- Wizard, Cleric, Druid -- wants prepared
  spells, not the whole list. A known caster -- Sorcerer, Warlock, Bard -- has
  nothing to hide, so everything.
- Domain, circle and oath spells are always prepared and do not count against the
  limit. They belong on the sheet but want distinguishing from chosen spells.
- **Pact Magic must not merge with the others.** A Warlock's slots are a separate
  pool at a separate level; packing its spells in with a Wizard's would put them
  under the wrong slot count. This is the one hard constraint, not a preference.
- A single-class caster below about level 5 already fits one page, so packing
  gains nothing and should not be the default there. A multiclass gains the most.

So: a default computed from the build, and an override in the PDF options
alongside the existing print choices.

### 8e. Pact Magic is what packing FIXES, not what blocks it (2026-09)

An earlier reading of this said packing was blocked on Pact Magic. That was
wrong, and wrong in a way worth recording, because it nearly stopped the feature.

The claim was that packing merges lists into fewer boxes and so would deepen the
pact problem. It rested on the slot total being per SECTION. It is not: each of
the nine level boxes carries its own `spell-slots-LEVEL-SUFFIX` field. Measured on
the masters -- style 1 has `spell-slots-1-1` through `spell-slots-9-1`, nine
fields, one a box, and style 4 the same.

So what is actually true:

- **Today** `make-page-map` groups by `:ability`, so a Warlock and a Sorcerer are
  merged into one CHA section, and every box in it is written the character-wide
  `(spell-slots level)`. The pact pool is invisible on the sheet already.
- **Packed by class**, a class holds its own contiguous run of boxes in one
  column, and each of those boxes has its own slot field. The browser writes THAT
  class's slot counts into them. A Warlock in its own column shows its own pact
  slots at its own level.

Packing by class is therefore the mechanism that separates the pools, and the
per-column class heading in 8c is what says which column belongs to whom. No
per-class pact flag is needed for it: the packer already keeps a class whole and
in one column, and a class is what the builder groups by before it ever reaches
an ability.

The ability grouping is the thing to replace, not to preserve.

#### The per-column class heading has nowhere to go, measured (2026-09)

8c wants each column headed with the class it holds, so a reader can tell whose
list is whose. The artwork does not have room for it.

Scanned at 200 dpi for an ink-free band 1.6in wide and 0.11in tall above each
box's numeral hexagon, on style 1 section 1:

    box 1   clear 0.32in above
    box 6   clear 0.22in above
    boxes 2, 3, 4, 5, 7, 8, 9   no clear band at any offset up to 0.4in

So eight of the ten boxes have nothing above them. The wide band beside each
numeral is the SLOTS EXPENDED input, which the player writes in, so a heading
cannot go there either.

What ships instead: the section header lists the classes in COLUMN ORDER, so
"Warlock, Sorcerer, Paladin, Bard" reads left to right against the columns. That
is weaker than a heading per column -- it does not survive two classes sharing a
column, which the packer allows -- and it is why packed layouts are opt-in rather
than the default.

Resolving it properly means artwork: a band above each column, or a heading slot
in each box's bar. That is a change to the templates, not to the code.

### 8f. The row counts had three copies (2026-09)

`pdf_spec` carried its own `level-max-spells` -- style 1's numbers -- and split a
character's spells by them whatever style was being exported. A style 4 sheet was
handed 8 cantrips for a box with 7 fields and lost one to the unplaceable report,
and 12 first-level spells for a box that holds 13. It now reads
`spell-packing/sheet-geometry`, which a test ties to the templates themselves.

### 8d. The browser can wrap text exactly, and will need to (2026-09)

Packing has to answer how many rows a list takes and whether a description fits,
which is the same question the server answers when it wraps text. The browser can
answer it identically, and this was checked rather than assumed:

- `string-width` is `getStringWidth / 1000 * size`, a plain sum of per-glyph
  ADVANCE widths. No kerning, no ligatures, no shaping.
- `split-lines` is a greedy word wrap on whitespace.
- Every description, spell and item name in the data uses **84 distinct
  characters**. A table of their advance widths is about 0.5 KB per face.
- Summing that table against PDFBox over 300 real descriptions: **0 mismatches**.

So wrapping is arithmetic over a small table, not a font rendering problem, and
the browser reproduces the server's line breaks exactly rather than approximately.

Do NOT build this for speed. Wrapping is 2.5% of an export -- 6.9ms of 275.7ms
for a sheet and eight item cards -- and shipping a width table to save that alone
is not worth the second implementation to keep in step. Build it as part of
packing, where the browser needs the answer for its own decisions and pre-wrapped
lines come along for nothing.

If it is built, ship the table from the same TTFs the server loads, so the two
cannot drift; a hand-maintained copy of the numbers is how the line breaks would
quietly stop matching.

### 9. Styles 2, 3 and 4 — a later branch

Worth separating, because half of this work did cover all four styles. Masters,
growing to a caster count, the no-caster variants and the attribution marks handle
styles 1 to 4 today -- `pdf/sheet-masters` names all four.

What is style 1 only is the RELABELLING: `printed-slot-labels`, `cantrips-box-rise`,
`cantrips-bar` and `hexagon-offset` in `pdf.clj` are that sheet's artwork, single
values with no style dimension. The other three styles need their own numbers.

Note also that nothing in production calls `relabel-spell-level!` or
`reuse-cantrips-box!` yet -- grep finds them only in tests. The mechanism works and
waits on item 8 to decide when to use it.

The method is written down in `docs/kb/pdf-form-techniques.md` under "Place a
patch by measuring the artwork" — PDFTextStripper for positions and size, a high
DPI render for colour, printed landmarks for the offsets between blocks — so this
is measurement rather than design. Style 1 is the only public sheet, which is why
it went first.
