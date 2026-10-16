# Plan: A4 and A5 for everything we draw

**Branch:** `refactor/pdf-canvas`. **Status:** written 2026-10-16, after the measurement in
`pdf-namespace-family.md` §11 found the page-size work already "done" was decorative.

## The problem in one paragraph

`draw/pt-from-top` takes a page key, and **no caller passes one** — all four call sites use the
2-arity form, which supplies `default-page` (`:letter`). So `page-sizes` is consulted and always
answers 612x792. Replacing a literal `11` with a table lookup that always returns 11 is
behaviour-neutral, which is why all 57 baseline pages stayed byte-identical and why it read as
progress. Separately, every text and box primitive in `draw.clj` hardcodes `(* 72 x)`, so the
shared layer speaks one unit (inches) and two opposite y conventions. That is why `spellbook.clj`,
which works in points, re-implemented its own drawing.

Two defects, not one:

| | Defect | Fix |
| --- | --- | --- |
| **D1** | Page size is asserted in 4 places and never passed in | bind it once per document; delete the escape hatch |
| **D2** | Primitives are inches-only with two y conventions, so 30 call sites flip by hand | give the primitives a y-from-top entry |

D1 alone makes A4/A5 reachable. D2 is what makes it *cheap* and removes the duplication.

## Order, and why this order

The risk is not the cards. It is that `draw-text` has **6 callers in `pdf.clj`** — the AcroForm
character sheet — and the geometry baselines cover 24 card pages, 9 ornament pages and 24
spellbook pages: **none of the character sheet.** Touching a shared primitive before the sheet is
pinned is changing the live export for every character with nothing watching.

So:

### Step 1 — pin the AcroForm sheet (BLOCKING)

A characterization test over a filled character sheet, same method as the card baselines
(`test/clj/orcpub/pdf_ops.clj`: operator stream -> disjoint geom/colour/text hashes) but aimed at
the sheet. It must cover the drawn-on content, not just field values: the site line, the column
headings, the spell numerals, the annotation columns. Without this, steps 3 and 4 are unverifiable.

Acceptance: perturb `draw-text`'s y by 0.003in and the new baseline reddens; revert and it is green.
A test that cannot fail does not pin anything.

### Step 2 — the page becomes a binding (D1)

**Revised 2026-10-16 while building it.** This step first read "drop `pt-from-top`'s 2-arity
form, so a missing page is a compile error". That contradicts the dynamic-var choice three
paragraphs below it: forcing every caller to pass a page explicitly IS the 66-call-site option
this plan rejects. The 2-arity form stays and reads the var. The guard against a decorative fix
is not a compile error, it is a test that binds a non-Letter page and asserts the output MOVES --
`page_size_test.clj`. Nothing like it existed, which is why the first attempt passed as done.

- `default-page`, a constant, becomes `^:dynamic *page*` with root `:letter`; `pt-from-top`'s
  2-arity reads it.
- `draw-grid`'s hardcoded `8.5`/`8.5`/`11.0` and `draw-svg-icon!`'s `(- 11 y size)` read the page.
- Add a `page-height`/`page-width` accessor in the caller's unit, for the 5 card sites that ask
  "how tall is the page" rather than flipping (`remaining-height`, `margin-y`, two footer offsets).

How the page reaches 66 call sites: **a dynamic var bound once per document.** Measured: there is
no `future`, `pmap`, `agent`, thread or core.async anywhere in `pdf.clj`, `cards.clj`, `draw.clj`
or `spellbook.clj`, so drawing runs on the request thread and a dynamic binding holds. The
alternative — an extra parameter on every primitive — changes 66 call sites and every intermediate
that must forward it, and `print-items` already takes 11 positional parameters.

Risk accepted and written down: if anyone later moves PDF generation onto a worker thread or a
`future`, the binding is lost and the page silently reverts to Letter.

**A second trap, found by measuring rather than by reasoning.** A binding does not reach a
load-time constant derived from the page. `spellbook.clj` had `page-w` and `page-h` as top-level
`def`s evaluated at namespace load, plus `body-bottom` derived from `page-h` — so binding `:a4`
would have moved the cards and silently left the spellbook on Letter. Scanned all four
namespaces: three instances, all in the spellbook, none in `cards.clj`, `pdf.clj` or `draw.clj`,
whose page literals sit inside function bodies. All three are now fns, commented as to why.

Also corrected: `page-sizes` carried A4 as 595.0x842.0 and A5 as 420.0x595.0, rounded by an
earlier pass in this session. ISO 216 is **595.28x841.89** and **419.53x595.28**. 842 is a 0.11pt
error, and it is the kind of number that becomes load-bearing. The test asserts the exact values,
and that A5's width is half A4's height.

### Step 3 — y-from-top entries on the primitives (D2)

`draw-text`, `draw-text-to-box`, `draw-lines-to-box` take y from the page BOTTOM; `draw-imagex`,
`draw-imagex-alpha`, `draw-svg-icon!` take it from the TOP. Card code works top-down, so it flips
by hand at every text call.

Give the bottom-up three a top-down entry that goes through `pt-from-top`. Then of the 30 card
flips, **~24 are coordinate arguments and simply stop existing**; 5 become the page-height
accessor from step 2; 1 (`card-pt`) is already the single choke point for every shape helper.

### Step 4 — units, so the spellbook can stop duplicating

The primitives assume inches. The spellbook works in points, uses exactly **one** of the eight
drawing primitives, and to call it divides by 72 to manufacture inches for a function that
multiplies by 72 and subtracts a literal 11 (`spellbook.clj:309`). Points -> inches -> points,
correct only because 792pt happens to equal 11in exactly. On A4 that coincidence breaks silently.

What the spellbook's 107 lines of its own primitives actually are, read rather than assumed:

| Lines | Kind | Names |
| --- | --- | --- |
| 28 | already delegating | `py`, `polyline!`, `circle-path!`, `wrap` |
| 29 | **pure unit adapter -- vanishes** | `text!` 18, `line!` 5, `gray!` 6 |
| 45 | **unique capability -- PROMOTE into `draw`** | `outline-text!` 30, `clip` 15 |
| 5 | superset | `spaced-width` (`string-width` + letter-spacing) |

**Correction to record:** this doc and earlier notes have said the spellbook "sheds ~90 lines of
duplicate primitives". It does not. It sheds **29**. `outline-text!` draws glyph outlines from the
TTF to reach Vollkorn's lining figures because `showText` cannot select the `one.lf` variant —
without it "15" reads as "I5". `clip` is word-boundary ellipsis truncation, one of the seven
overflow strategies, with no equivalent in `draw`. Those 45 lines are capability the shared layer
is MISSING, not duplication to delete.

### Step 5 — actually offer the sizes

Thread a page choice from the export request to the binding. `draw-grid` already computes a grid
from box and page dimensions, so a 2-up A5 grid on A4 is the same call with a different binding.

**Corrected 2026-10-16:** this plan, the family doc and the TODO all said "A5 is exactly half an
A4". It is not, and `page_size_test` caught it. ISO 216 halves the previous size and rounds DOWN
to a whole millimetre: half of A4's 297mm is 148.5, but A5 is 148. So half an A4's height is
420.94pt against A5's 419.53pt width — a 1.42pt (0.5mm) sliver. Harmless to print, wrong to
assert, and the sort of number that becomes load-bearing once something divides by it.

## Not in this plan

- **An A4 character sheet.** See §"The shim question" below.
- The orchestrator move out of `routes.clj` (349 lines). Needs step 1's test too, and is its own job.
- `pdf.image`, `pdf.character`. Packaging, not capability.
- The card frame as its own namespace. Still one consumer.

## Two dead publics found while measuring

`draw-line` and `draw-line-in` are public with **zero** callers outside `draw.clj`
(`draw-line-in` is used only by `draw-grid`, `draw-line` only by `draw-line-in`). Both should be
`defn-`. Not urgent, but they are part of the "public by `defn` default, not by design" surface.

## The shim question: can the AcroForm templates be padded into A4?

Asked by the owner 2026-10-16: can the character-sheet PDFs be shimmed with padding so they
print alongside A4/A5 cards and spellbooks?

**Height: yes, free. Width: no — "padding" is the wrong operation, because A4 is NARROWER.**

| | Letter | A4 | A5 |
| --- | --- | --- | --- |
| points | 612.00 x 792.00 | 595.28 x 841.89 | 419.53 x 595.28 |
| vs Letter | — | 16.72pt narrower, 49.89pt taller | — |

A4 is 5.9mm narrower and 17.6mm taller than Letter. So height gains 49.89pt of free margin
(24.9 top and bottom), and width must LOSE 16.72pt — 8.36pt per side if centred. Padding adds; it
cannot do that.

### Measured: how much width is there to lose

Rendered all 8 templates at 72dpi and scanned for the leftmost/rightmost non-white column, and
took the bounding box of every AcroForm widget rect:

| Template | widgets | widget x | INK x | ink slack L / R | 8.36pt crop? |
| --- | --- | --- | --- | --- | --- |
| style 1-0 / 1-1 | 121 / 335 | 20.5 .. 593.2 | **2 .. 610** | 2 / 2 | **NO** |
| style 2-0 / 2-1 | 120 / 334 | 21.8 .. 594.2 | **2 .. 610** | 2 / 2 | **NO** |
| style 3-0 | 120 | 29.3 .. 581.7 | 22 .. 600 | 22 / 12 | **yes** |
| style 3-1 | 335 | 29.2 .. 581.7 | 15 .. 600 | 15 / 12 | **yes** |
| style 4-0 / 4-1 | 129 / 346 | 42.3 .. 569.4 | 11 .. 600 | 11 / 12 | **yes** |

All 8 are MediaBox 612x792.

- **Styles 3 and 4 work by translate alone.** Ink slack is 11-22pt left and 12pt right, both more
  than the 8.36pt per side needed. Set MediaBox to A4, translate page content by (-8.36, +24.9),
  translate every widget rect by the same, done. No scaling, no artwork change, nothing clipped.
- **Styles 1 and 2 cannot.** Their ink reaches x=2 on both edges — the border artwork is
  effectively full-bleed. Centring on A4 shaves ~6.4pt off that border on each side, which is
  visible. Either accept a trimmed border or redraw the artwork.

### Why scaling is the expensive route, not the easy one

Scaling by 612 -> 595.28 (0.97267) would fit all 8 styles. It is the wrong tool here:
**field appearances are BAKED.** `pdf.clj:822` calls `(.setNeedAppearances form false)` with the
comment that viewers do not honour NeedAppearances, so every widget carries its own baked
appearance stream — 121 to 346 of them per document. A page-level scale leaves all of those at the
old size, so scaling means regenerating every appearance stream.

A **translate** needs none of that: a widget's appearance is positioned by its `/Rect`, with its
own BBox in widget-local coordinates, so moving the rect moves the appearance with it. And
`.setRectangle` is already a proven operation in this codebase — `reserve-annotation-columns!`
(`pdf.clj:1723`) and `shrink-slots-expended!` (`pdf.clj:1928`) both already move widget rects.

### What this means for the owner's goal

"One print job, same paper" is achievable now for **styles 3 and 4**: A5 cards and the spellbook
from code (this plan), plus a translate shim on those two sheet styles. Styles 1 and 2 need A4
artwork, or a knowingly trimmed border.

Not in scope for this plan — the shim is a separate, self-contained job on `pdf.clj`, and it wants
step 1's characterization test first for the same reason everything else does.
