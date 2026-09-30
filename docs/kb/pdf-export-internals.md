# PDF export internals

Measurements and reasons behind the character-sheet and card export: templates, spell pages, spell
packing, `pdf_spec`, cards, the export route, and the character picture that goes onto the sheet.

Related: `docs/issues/pdf-overflow-and-page-generation-plan.md`, `docs/PDF-EXPORT-CAPACITY.md`,
`docs/CHARACTER-IMAGE-FETCH.md`; on `agents/develop` only: `pdf-form-techniques.md`,
`pdf-generation-architecture.md`, `pdf-generated-vs-uploaded-images.md`, `character-image-routes.md`.

## Templates and masters — `pdf.clj`, `routes.clj`

- **`pdf/sheet-masters`, one master per style (2026-09).** Deriving a small sheet by trimming a wider
  file cost more than growing a small one: trimming grew style 1's one-caster sheet from 276 KB to
  654 KB, while growing from the master gave 328 KB against the 565 KB file that shipped.
- **Non-caster masters are separate files.** Removing the spell pages from a master gave style 2 a
  453 KB non-caster sheet, against 241 KB from its own file. The extra files cost 1.2 MB of the
  44.3 MB of templates they replace.
- **Style 4's master was once two pages**, kept because the licence footer was thought to be baked
  into one of them. Both pages shared one background XObject; the marked page was the plain page
  plus a BT/ET text block.
- **The site line's position is measured, not read from page text.** Placed first from the page's
  text, it ran through the corner flourish on styles 1 and 2 and through the frame on style 4.
- **`share-duplicate-images!`**: style 3 ships the same 192 KB image twice.

## Spell pages — `pdf.clj`

- **`add-spell-pages!` batches.** Calling `add-spell-page!` in a loop took 65–87 ms and 39–51 MB per
  clone; the batch takes 321 ms and 44 MB for six sections.
- **Insert order.** Styles 3 and 4 threw `StackOverflowError` at two or more casters when pages went
  in through `PDPageTree.add`; pages are placed with `insertBefore` / `insertAfter`.
- **`add-missing-spell-pages!`** still prunes orphan widgets, in case it meets a template that was
  not baked (pruned at prepare time).

## Form fields — `pdf.clj`

- **`disambiguate-duplicate-fields!`**: the templates carried 103 duplicated field names — 92
  `Check Box N`, the SlotsRemaining bubbles, and 2 image placeholders.
- **`merged-fields`**: style 4's Notes box is 263x252pt, against style 1's two boxes of 354x369 and
  176x219.
- **`name-death-save-checkboxes!`** was verified by ticking the upper row and rendering the page.
- **`valid-relabel?`** checks the relabel instructions the browser sends the way the sheet style id
  is checked, because the style id once reached a resource path before anything validated it.
- Faults the packing and relabel work found by rendering: the Paladin column had no name;
  "Sorcerer" printed as "Sorce…"; a level 5 Cleric had 13 spells moved to another page while 59 rows
  sat empty.

## Spell packing — `spell_packing.cljc`, `pdf/numeral-boxes`

- **`sheet-geometry` had miscounts.** Styles 1 and 3 at level 3 had fields 1–10, 12, 13, 14, so
  `spells-3-11` went nowhere; style 4 was recorded as 12/13 where the template has 13/11.
- **Box 0 holds cantrips only.** Letting it take levelled spells on style 3 drew the numeral outside
  the ring and clipped the class name.
- **Relabelled sections once counted from zero**, naming a section no template has.
- **Unplaced spells are reported.** Example: a Wizard 20 + Cleric + Druid printed without the Cleric,
  33 spells lost.
- **`numeral-boxes` are measured per style.** The dropped alternative, tracing style 1's hexagon,
  limited packing to one style.

## `pdf_spec.cljc`

- The `:per-class` layout as shipped grouped by `:ability`, so a Warlock and a Sorcerer shared one
  section.
- On a style whose numerals have not been measured, a packed page prints the template's old level
  number beside the new one.

## Spell annotations — `spell_annotations.cljc`

Of 319 spells: concentration 126, costly material 52, bonus action 14, reaction 4.

- **Ritual is not marked**: `R` beside `RE` would be two unrelated single capitals.
- **V, S and M are not marked**: they are on nearly every spell. Only a priced material is carried,
  because it stops the spell if it is missing.

## Cards — `config/get-pdf-max-cards`, `pdf/print-items`

- **The card cap is 198, not 200.** 200 left a ragged last page (22 sheets plus two cards).
- **Item names have a size floor.** Without one, "Instrument of the Bards, Anstruth Harp" set its
  name at 8.5pt.
- **Card backs use the blank-card logo PNGs** (997x997), not the front's `card-logo.png` (22x30),
  which pixelates at that size.

## Export route — `routes.clj`

- **PDF flattening.** The old flattening for non-Chrome browsers worked around Firefox ignoring
  `NeedAppearances`.

## Sheet styles — `views` `sheet-styles`

- The paid-style check fails in one direction or the other: failing closed costs a paying user
  sheets they will report; failing open gives paid content away silently.

## Character picture — `image_capture.cljs`, `image_url.cljc`, `events.cljs`

- **Picture bytes are kept off the character** because of localStorage's ~5 MB ceiling.
- **The server's image check runs early** (`events.cljs`), so the builder warns instead of printing a
  sheet with a hole where the picture should be. `image_url.cljc` checks what it can locally
  because waiting for a fetch to fail costs a round trip.
- **`image_capture`**: the fetch route is avoided because it logs a CSP violation on every export.
- **`print-edge`**: the in-app thumbnail is 200x100, so the printed size decides the edge.
