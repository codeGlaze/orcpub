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

Proven end to end in `dev/on_demand_pages.clj` — eight classes, zero duplicate
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

### 9. Styles 2, 3 and 4 — a later branch

Everything measured for the relabelling is style 1 only: `printed-slot-labels`,
`cantrips-box-rise`, `cantrips-bar` and `hexagon-offset` in `pdf.clj` are that
sheet's artwork. The other three styles need their own numbers.

The method is written down in `docs/kb/pdf-form-techniques.md` under "Place a
patch by measuring the artwork" — PDFTextStripper for positions and size, a high
DPI render for colour, printed landmarks for the offsets between blocks — so this
is measurement rather than design. Style 1 is the only public sheet, which is why
it went first.
