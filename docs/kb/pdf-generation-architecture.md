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

As of 2026-04-22 there ARE automated tests:

- `test/clj/orcpub/pdf_test.clj` — unit tests for `fix-widget-page-refs!` and
  integration tests for `write-fields!` against the bundled templates
- `test/clj/orcpub/routes_pdf_test.clj` — handler-level tests for
  `character-pdf-2` covering the `:flatten?` round-trip and the 4×7
  style/spell-count smoke matrix
- E2E spec at `e2e/scenarios/pdf-export.spec.ts` on `testing/develop` —
  covers real HTTP round-trip, `pdf-lib` field counting, and native-viewer
  rendering in Chromium + Firefox (see "Cross-browser PDF rendering" below)

For ad-hoc checks beyond what the tests cover, use PDFBox to inspect:
- Page count matches expected
- Content stream byte count > 0 for all pages
- Annotation count matches template

## Fillable-by-default (2026-04-22)

Branch landed: `bugfix/pdf-widget-warnings` (off `develop`). Worked through
session: `claude/fix-pdf-widget-warnings-hUt9i`.

Three changes:

1. **`/P` widget back-refs populated before flatten** — new private helper
   `fix-widget-page-refs!` in `pdf.clj` sets the `/P` (page reference) entry
   on every orphaned `PDAnnotationWidget` it can find via the page-annotations
   walk. Runs only on the flatten path (`when flatten?` in `write-fields!`).

2. **Removed Chrome User-Agent sniffing** — `routes.clj` `character-pdf-2` no
   longer force-flattens for non-Chrome User-Agent strings. The 2017
   workaround was for Firefox's pdf.js lacking AcroForm rendering, fixed by
   Mozilla in Firefox 84 (Dec 2020). Default is now interactive/fillable for
   every browser.

3. **Strict `(true? flatten?)` check** — clients can opt back into a
   locked/static PDF via `:flatten? true` in the EDN payload. Loose Clojure
   truthiness was rejected: a malformed payload (`"yes"`, `1`, `{}`,
   `:true`) falls through to the safer interactive default rather than
   silently locking the sheet.

Font-size override (in `routes.clj` `font-sizes`) is still gated on
`flatten?` — interactive PDFs rely on the template's `/Helv 0 Tf` auto-size
default appearance, which readers honor natively. Flatten paths bake in
size 8 for long-text fields (`personality-traits`, `bonds`, `backstory`,
etc.).

## PDFBox 3.0.6 verified facts

These are the things a future agent should know without re-deriving from
scratch. All verified against source at
https://github.com/apache/pdfbox/tree/3.0.6 (don't accept claims that
contradict the pinned source — fetch fresh).

- **`PDPage` has no `addAnnotation(widget)` method.** Verified via
  `javap -p` on `pdfbox-3.0.6.jar`. Only `getAnnotations()`,
  `getAnnotations(filter)`, and `setAnnotations(list)` exist. To attach a
  synthetic widget in tests, append directly to `/Annots`:
  `(.add (.getAnnotations page) widget)` or write to the COSArray.
- **`PDPage.getAnnotations()` does not backref `/P`.** Despite empirical
  appearances it is read-only. See `PDPage.java` source — it builds a list
  of `PDAnnotation` wrappers via `PDAnnotation.createAnnotation`, no `/P`
  mutation.
- **`PDAnnotation.getPage()` returns a fresh `PDPage` wrapper every call.**
  `return page != null ? new PDPage(page) : null;`. Tests must compare
  the underlying `COSDictionary` (`(.getCOSObject ...)`) for identity
  rather than the `PDPage` wrapper itself.
- **`PDAcroForm.flatten()` walks `field.getWidgets()` (NOT
  `page.getAnnotations()`)** and emits `"missing /P entry (page reference)
  in a widget for field: X"` for any widget whose `getPage()` returns nil.
  See `PDAcroForm.java` `buildPagesWidgetsMap`. Setting `/P` on the
  widget's COSDict is sufficient to suppress.
- **`PDField.getWidgets()` constructs new `PDAnnotationWidget` instances**
  on every call (each wrapping the same underlying COSDict).
- **`Loader/loadPDF` accepts byte[], File, or RandomAccessRead — NOT
  InputStream.** Read the resource stream into a byte array first.
- Standard fonts: 2.x `PDType1Font/HELVETICA` → 3.x
  `(PDType1Font. Standard14Fonts$FontName/HELVETICA)`.
- `PDPageContentStream` constructor: 2.x boolean flags → 3.x
  `PDPageContentStream$AppendMode/APPEND` enum.
- Color values for `setStrokingColor` / `setNonStrokingColor`: must be
  0.0–1.0 floats with explicit `(float x)` casts (Clojure reflection
  picks the wrong overload otherwise).
- `drawLine` removed → `moveTo` / `lineTo` / `stroke`.
- `moveTextPositionByAmount` → `newLineAtOffset`; `drawString` → `showText`.

## Known unknown — `fix-widget-page-refs!` mechanism

The 28 bundled fillable templates show **zero** orphan widgets when probed
via `(.getAnnotations page)` then `(.getPage widget)` — every widget already
has `/P` populated at rest. Yet bare `AcroForm.flatten()` (without prior
iteration of `getPages`/`getAnnotations`) emits hundreds of
`"missing /P entry"` WARN lines per PDF.

The two facts are not obviously consistent. `getAnnotations()` in 3.0.6
source does NOT mutate `/P` — verified — so the resolution isn't a side
effect of the helper's iteration.

Hypotheses still untested:
- `PDAcroForm.flatten` walks widgets via field.getWidgets, which may
  produce different widget objects than the page-annotations path. Some
  of those field-widget objects may have null `/P` even when the
  same-COSDict widgets reachable via the page have `/P` populated. (PDFBox
  may set `/P` lazily somewhere in the page-annotation walk that I missed.)
- Some widgets may exist in `AcroForm.fields[].kids` without being on any
  page's `/Annots` — those would be invisible to a page walk but visible
  to flatten's field walk.

Tests prove the *effect* (`fix-widget-page-refs!` populates `/P` on synthetic
orphan widgets, and the flatten WARN does not fire after the helper runs).
The *cause* of why the helper is needed against the bundled templates is
not closed. Worth a future investigation but not a merge blocker.

## Cross-browser PDF rendering

Discovered while writing the E2E spec on `testing/develop`. Full matrix is
documented in `e2e/AGENT-GUIDE.md` on that branch; key facts:

- **Chromium**: full build (with PDFium) renders PDFs inline; the
  `chromium-headless-shell` variant Playwright pulls by default does NOT
  include PDFium and produces blank screenshots. Run
  `./node_modules/.bin/playwright install chromium` (without `--shell`) to
  pull the full build.
- **Firefox**: pdf.js viewer renders inline, but Playwright's default
  `firefoxUserPrefs` treat `application/pdf` as a download. Set
  `pdfjs.disabled=false` (and `pdfjs.firstRun=false`) to enable inline
  rendering. Form widgets render with light-blue highlighting — useful
  visual signal that the form is fillable.
- **WebKit on Linux**: no inline PDF viewer at all. Linux WebKit
  (Cairo/GTK) doesn't have an equivalent of macOS Safari's PDFKit. Falls
  back to download. Auto-skip the native-render assertion via
  `process.platform !== 'darwin'`.

For programmatic field-counting (no native viewer required), use the
vendored `pdf-lib` UMD bundle in `e2e/fixtures/pdf-lib.min.js`.
