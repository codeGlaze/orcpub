# fix/pdf-export

**Purpose:** three PDF export bugs testers reported on the dev build (filename, PDF Options
scrolling, a freeze on ticking Print Spell Cards / Print Spellbook). **Base:** `integration`
(`fd343a843`). **PR into:** `integration`; no PR until the owner says.

## State (updated 2026-11-09)

- Done: PDF Options can be scrolled to its end (`b265f8486`), with the browser test
  `scripts/e2e/pdf-options-reach.js` (`cf669789d`, fail output in its message).
- Not reproduced, so not changed (one attempt each, per the brief):
  - **Filename "character.pdf".** In the real builder (prod bundle, Chromium, signed out, Wizard 5,
    names typed in Description) the POST carries `:player-name "Probe Player" :character-name
    "Probe Hero" :class-level "Wizard (5)"` and the response says
    `inline; filename="Probe Player - Probe Hero - Wizard (5).pdf"`. Calling
    `routes/generate-character-pdf` directly gives the same. No interceptor replaces headers
    (`pedestal.clj` only adds). "character.pdf" is also the URL's last segment, which a browser
    uses when it ignores an inline filename (not checked which browsers do) — so the tester's browser, and whether
    the export came from the builder, the character list or a saved character's page, decide the
    next step. Paths not measured: a saved character, the character-list Print button
    (`views.cljs` ~10797), the portrait-bake path (export opens a named tab first).
  - **Freeze on ticking Print Spell Cards / Print Spellbook.** Same build, Wizard 5 with 8 spells,
    1280x800: first paint after the click 28-49 ms, no long tasks (PerformanceObserver
    `longtask`) in the 3 s after, four toggles. The events only flip a flag
    (`events.cljs` ~8270, ~8334). Not measured: a big spell list (a Wizard 20 or Cleric with
    everything prepared), the dev bundle, a slow phone. Candidate seen in reading, not measured:
    `export-pdf-handler` is `memoize`d on `built-char` and `plugin-data`, so every render of the
    panel hashes those maps and keeps every miss forever (`views.cljs` ~4807).
- Next: the owner's answers to the questions below.

## Open questions for the owner

- Which browser and which page did the filename testers use? Needed to reproduce bug 1.
- A character that freezes (export it, or its class/level/spell count)? Needed to reproduce bug 3.
- PDF Options is still taller than a phone (765 px under a 189 px header on 390x844), so it
  scrolls with the page; making it fit without scrolling is a layout choice (collapsed groups,
  two columns on desktop) not made here.

## Gotchas for the next agent

- The PDF Options panel renders inside the page header (`content-page`, `views.cljs` ~1543). Since
  `6cd529ee0` (2026-09-05) that header is `position: sticky`; anything opened in it that is taller
  than the window gets cut off unless the header is unstuck while it shows.
- Chromium hands a PDF response to its viewer, so `response.body()` is the viewer's HTML; read the
  `content-disposition` header from the response event, the body through `context.route`
  (`scripts/e2e/export-character-pdf.js`, `exportPdf`).
