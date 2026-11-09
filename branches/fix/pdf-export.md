# fix/pdf-export

**Purpose:** three PDF export bugs testers reported on the dev build (filename, PDF Options
scrolling, a freeze on ticking Print Spell Cards / Print Spellbook). **Base:** `integration`
(`fd343a843`). **PR into:** `integration`; no PR until the owner says.

## State (updated 2026-11-09)

- Done: PDF Options can be scrolled to its end (`b265f8486`, test `scripts/e2e/pdf-options-reach.js`).
  The download filename keeps any name (`filename*=UTF-8`) and drops an empty slot (`34947cf7d`,
  tests in `routes_test.clj`).
- **Filename "character.pdf": cause found.** Names are generated only on Save (`save-character-fx`,
  since `364d971b1`), so an unsaved character, and every signed-out one, exported with a blank
  name. The owner's rule (2026-11-09): no character is unnamed, and every one has a class and level.
  Export now generates the name (`named-spec` in `views.cljs`, `::char5e/fill-blank-name`), and the
  server falls back to the class and level (`3a9ea1777`). Eleven name permutations otherwise
  exported fine; they found the empty-slot and Latin-1 defects fixed in `34947cf7d`.
- **"Freeze" on Print Spell Cards / Spellbook: the browser is not blocked; the server is slow and
  the page says nothing.** Wizard 20, 440 spells picked (150 homebrew, ~1.3 KB descriptions), at
  390 px with the CPU throttled (CDP, 1x / 4x / 6x): ticks paint in 65 / 155 / 200 ms, long tasks
  at most 68 ms; Create PDF blocks at most 147 ms. Server, cards + spellbook: 4.3 / 3.5 / 3.1 s;
  nothing ticked: 0.2-0.4 s (idle local server; queueing adds to it). Create PDF closes the panel
  at once and the PDF opens in a new tab, so nothing shows the wait. Not changed: how to show it
  is the owner's choice (below).
- Next: the owner's answer on export feedback.

## Open questions for the owner

- Export feedback during the 0.3-2.6 s (longer under load) server wait: a message on the builder
  (unseen while the new tab has focus), a "Making your PDF..." page in the new tab, or fetching the
  PDF in the page with a spinner and then opening it (popup-blocker risk: see `export-pdf`'s GOTCHA).
- PDF Options still scrolls on a phone (765 px under a 189 px header at 390x844); fitting it
  without scrolling is a layout choice.

## Gotchas for the next agent

- The PDF Options panel renders inside the page header (`content-page`, `views.cljs` ~1543). Since
  `6cd529ee0` (2026-09-05) that header is `position: sticky`; anything opened in it that is taller
  than the window gets cut off unless the header is unstuck while it shows.
- Chromium hands a PDF response to its viewer, so `response.body()` is the viewer's HTML; read the
  `content-disposition` header from the response event, the body through `context.route`
  (`scripts/e2e/export-character-pdf.js`, `exportPdf`).
