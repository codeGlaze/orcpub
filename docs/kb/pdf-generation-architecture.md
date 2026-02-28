# PDF Generation Architecture

How character PDF export works end-to-end: template selection, field filling,
spell card rendering, and known failure modes.

## Overview

PDF generation lives in two files:
- `routes.clj` — orchestration (`character-pdf-2`, `add-spell-cards!`)
- `pdf.clj` — low-level drawing (grid, text, lines for spell cards)

Client-side, `pdf_spec.cljc` builds the field spec that tells the server what
to fill in.

## Template Selection

Templates live in `resources/` with naming convention:
```
fillable-char-sheetstyle-{style}-{N}-spells.pdf
```

Where:
- `style` = 1 (default), 2, or 3
- `N` = number of spellcasting class groups (0 = no spell pages)

Selection logic in `routes.clj` `character-pdf-2`:
```clojure
(find fields :spellcasting-class-0)  ; → has at least 1 spell group
(find fields :spellcasting-class-1)  ; → has at least 2 spell groups
```

Style 1 templates always end with a `features-and-traits-2` overflow page
(1 form annotation). This page exists even in the 0-spells template.

## Spell Page Field Spec (`pdf_spec.cljc`)

The client builds spell page fields via:

1. `make-page-map` — groups spells by `:ability` keyword (e.g., CHA for Bard,
   CHA for Tiefling racial). Each unique ability = one spellcasting class group.
2. `make-pages` — splits each ability group's spells into pages using
   `level-max-spells` for overflow (how many spells fit per page at each level).
3. `spell-page-fields` — generates named fields via `map-indexed`:
   `spellcasting-class-0`, `spellcasting-class-1`, etc.

A Tiefling Bard has 2 spell groups (both CHA, but one racial, one class) — or
possibly 1, depending on how the abilities merge. Check the actual `make-page-map`
output for edge cases.

## Spell Card Rendering (`add-spell-cards!`)

### Lifecycle (critical for understanding blank page bugs)

```
1. Create new PDPage
2. Add page to PDDocument (page is now IN the PDF)
3. Create PDPageContentStream for the page
4. Draw grid, text, fields (this is where PDFBox API calls happen)
5. Close content stream
6. Create BACK page
7. Add back page to PDDocument
8. Draw back page content
9. Close back content stream
```

**The bug pattern**: If step 4 crashes, the front page is already added (step 2)
but has 0 bytes of content. The back page (steps 6-9) never executes. Result:
orphaned blank page in the final PDF.

### Silent Exception Swallowing

```clojure
(defn add-spell-cards! [doc fields]
  (try
    ;; ... all rendering code ...
    (catch Exception e
      (prn "FAILED ADDING SPELLS CARDS!" e))))
```

This catch block means spell card failures are invisible to the user. The PDF
exports "successfully" but with a blank page where spell cards should be.
There's no error in the HTTP response, no visible error in the UI.

**Debugging approach**: If a PDF has a blank page, check the server stdout/stderr
for the `"FAILED ADDING SPELLS CARDS!"` message. In production, this goes to the
log file but may be hard to find.

## PDFBox 3.x Migration

PDFBox was upgraded from 2.x to 3.x. The migration broke spell card rendering
because the silent catch hid all errors. See handoff.md for the full API change
table.

### Key 3.x differences for future work
- Color values: `0.0-1.0` float range, NOT `0-255` int range
- `setStrokingColor` / `setNonStrokingColor` require explicit `(float x)` casts
  from Clojure (integer reflection picks wrong overload)
- `drawLine` removed — use `moveTo`/`lineTo`/`stroke`
- `moveTextPositionByAmount` → `newLineAtOffset`
- `drawString` → `showText`
- `PDType1Font/HELVETICA` → `(PDType1Font. Standard14Fonts$FontName/HELVETICA)`

### Known remaining risks
- `PDType0Font/load` for Vollkorn fonts works but uses reflection (no type hint)
- Any untested code path with color values could still be using 0-255 range
- Tests don't exercise spell card generation — must test with real PDF export

## PDF Field Filling

Non-spell-card fields are filled via Apache PDFBox's `PDAcroForm`:
```clojure
(.getField acro-form field-name)  ; → PDField
(.setValue field value)            ; → fills the form field
```

The field names in the template must match exactly what `pdf_spec.cljc` generates.
Mismatched names = silently unfilled fields (no error thrown).

## Testing PDF Changes

There are NO automated tests for PDF generation. To verify:

1. Start the dev server
2. Create/load a character with spellcasting (Bard, Wizard, Cleric, or any
   class with spells; Tiefling adds racial spells as a second group)
3. Export to PDF
4. Check every page: no blank pages, spell cards render, text is positioned correctly
5. For programmatic checks, use PDFBox to inspect:
   - Page count matches expected
   - Content stream byte count > 0 for all pages
   - Annotation count matches template
