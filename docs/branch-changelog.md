# Branch changelog — `refactor/pdf-canvas`

## Why this branch exists

`orcpub.pdf` was a 3,272-line file holding the character sheet, the cards and the shared
drawing code, with the page height typed in as a literal `11` at 39 sites. That made any
paper but US Letter impossible, so this branch separated the drawing layer from the two
things that use it and made the page a parameter.

Three faults turned up on the way, each invisible on Letter and each found by generating
a PDF and counting what was on it rather than by reading the code: cards past the ninth
were silently dropped on any sheet that holds fewer, a short overflow vector threw an
exception that the enclosing `try` swallowed along with every card, and every page was
created at PDFBox's Letter default whatever size had been asked for. The suite, the
pinned baselines and the linter all passed with every one of them present.

Scope a reviewer needs: the character-sheet templates are 612x792 with fixed artwork, so
A4 for the sheet is a re-papering (the page window moves over the content), not a redraw.
Everything we draw ourselves — cards, backs, spellbook, overflow pages — genuinely lays
out to the chosen paper.

## Added

- **Printing:** A4 and A5 as well as US Letter, for spell cards, magic item cards, the
  spellbook and the character sheet. An A4 spellbook page carries about 7% more than a
  Letter one; A5 fits four cards to a sheet instead of nine.
- **Printing:** when part of an export cannot be produced, the PDF now opens with a page
  naming what was left out and why. A clean export is unchanged and gains no page.

## Changed

- **Printing:** a character portrait whose host is briefly unreachable is fetched a second
  time before the export gives up on it.

## Fixed

- **Printing:** a magic item or spell whose name the embedded font cannot draw no longer
  costs the card sheet it is on, and every sheet after it.

## Security
