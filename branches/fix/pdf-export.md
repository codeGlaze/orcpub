# fix/pdf-export

**Purpose:** three PDF export bugs testers reported on the dev build (filename, PDF Options
scrolling, a freeze on ticking Print Spell Cards / Print Spellbook). **Base:** `integration`
(`fd343a843`). **PR into:** `integration`; **#84 open** (2026-11-10, owner: "good RC"); CodeRabbit full review requested once. Next: answer its threads, push fixes, reply naming the fixing commit. Fold the changelog only when the owner says it is about to merge.

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
- **The "freeze" was most likely scroll chaining (owner, 2026-11-11).** On a phone, swiping the old
  in-page PDF Options scrolled the page behind it until the page hit bottom, and only then the
  menu; with mobile's hair-thin scrollbar that read as frozen. The full-window sheet (fixed,
  covering the page, `.pdf-sheet-body` with `overscroll-behavior: contain`) should end it. To
  confirm on a real phone with this build; until then the measurements below stand.
- **"Freeze" on Print Spell Cards / Spellbook: the browser is not blocked; the server is slow and
  the page says nothing.** Wizard 20, 440 spells picked (150 homebrew, ~1.3 KB descriptions), at
  390 px with the CPU throttled (CDP, 1x / 4x / 6x): ticks paint in 65 / 155 / 200 ms, long tasks
  at most 68 ms; Create PDF blocks at most 147 ms. Server, cards + spellbook: 4.3 / 3.5 / 3.1 s;
  nothing ticked: 0.2-0.4 s (idle local server; queueing adds to it). Create PDF closes the panel
  at once and the PDF opens in a new tab, so nothing shows the wait. Not changed: how to show it
  is the owner's choice (below).
- **Tick freeze, dev bundle (2026-11-09):** still not reproduced. Same Wizard 20 / 440 spells, dev
  bundle (`Needs: dev bundle`), CPU profile around each tick: paint 250-280 ms, no long tasks,
  under 15 ms of script in the 5 s after. Differences from the owner's report still untested: a
  character with a portrait (`bake-portrait` sits in the panel and re-bakes on every update), a
  saved character opened from the list, the owner's own homebrew library.
- File names drop emoji and Windows-illegal characters (`file-safe` in `routes.clj`).
- **2026-11-09, later:** file name choices (on the account), the Making-your-PDF tab, and
  goog.DEBUG off in the production build are pushed. The full-screen PDF Options panel (title bar,
  scrolling body, footer with Create PDF; sticky header restored) is built but uncommitted,
  waiting on the owner's look at screenshots. Freeze still not reproduced with a homebrew race,
  class (level 20, 325 spells) and a portrait on the dev bundle: ticks paint in 233-491 ms.
  The production bundle is 924 KB compressed against the page-load suite's 900 KB ceiling on
  integration itself. CSS trap found: garden-calc-plus.md.
- Next: the owner's answers (the full-screen panel; the three broken calc rules; the bundle; the
  freeze character; the panel keeping the character from when it opened).

## Open questions for the owner

- **Race and feat spell pages print class slots (found 2026-11-11, not changed).** Per-class spell
  pages group by casting ability, so a race's or feat's spells with their own ability get a page of
  their own, and `spell-page-fields` (`pdf_spec.cljc`) gives every first page the character's
  shared slots. A Tiefling Wizard's "Tiefling" page shows 4 and 3 slots beside Hellish Rebuke,
  which is once a day; a non-caster's page gets a blank box. Owner asked for a report only.
  The packed layout has the same fault (`packing-classes` gives every group `:slots shared-slots`).
  **The app is wrong too** (owner, 2026-11-11): `spells-table` (`views.cljs`) heads each level with
  "N remaining" from the slot pool, so a race-spell-only character reads "0 remaining". Rules
  (2014): race spells are their own free castings, usually 1/long rest, no slots; Magic Initiate
  is a free use plus your slots; items use charges; 2024 species and feats allow both. Next: check
  whether spell grants store their uses, then plan app + PDF together; owner sees the plan first.
  **PDF side done 2026-11-11** (`54e609b18`, `43e00ec1e`); app side is steps 4-6 of
  `docs/kb/plan-spell-uses-by-source.md`. Also fixed: the reference pages did not render on this
  branch (`bf3407500`, an undeclared `views/make-arg-event-handler` after the integration merge).
  **Where it stands (2026-11-11, before a compact):** planning and mockups for how non-class
  spells print; nothing built past steps 1-3. Resume from `docs/kb/plan-spell-uses-by-source.md`,
  sections "Next mockup round" and "Build plan". #84 is not ready to merge (owner).

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

## Update 2026-11-09, end of day

- Pushed: full-window PDF Options (title bar, scrolling body, footer; sticky header restored),
  theme colours (`.main-text-color` + the app's sheet surface; contrast checked in both themes by
  `pdf-options-reach.js`), a Spells group, dependent options marked, column-width pickers, the
  panel reading the live character, the three `calc(... + ...)` popover offsets fixed with a test,
  and PDF export + the panel moved to `views/pdf_options.cljs` (views.cljs 11,735 -> 11,230).
- Full suites green at `57501bbf7` plus the suite-port fix: lein test 1,528, cljs 989, lint 0
  errors, export 61/61, reach 15/15, portrait 11/11.
- Open: the owner is unsure the phone order of the groups is logical (wants to use it first);
  the bundle (924 KB vs 900 KB, see BRANCH.md "Trim the app bundle").
