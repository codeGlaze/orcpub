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
4. Text longer than its box is cropped by the appearance stream's clip, with no
   warning. `ideals`, `bonds` and `flaws` hold about 14 words each.
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

### 4. Overflow for the small page 1 boxes

`ideals`, `bonds`, `flaws`, `personality-traits`, `attacks-and-spellcasting`.
These cannot grow — the layout is fixed art.

Proposal, in order: shrink the font toward a floor (8pt down to 6pt buys roughly
a third more), and if it still does not fit, keep what fits, append a marker, and
spill the remainder onto the continuation page under a heading naming the field.

Open question for review: is a shrink-then-spill acceptable, or is truncating
with a visible "(continued on p.N)" marker better? Silently cropping is the one
option that is not acceptable, since that is today's behaviour.

### 5. Continuation pages that grow

`features-and-traits-2` holds ~1476 words and a real level 20 wizard used 10% of
it, so this is not urgent — but it is the same mechanism as step 6 and costs
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
