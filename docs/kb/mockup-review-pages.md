# Mockup review pages for the owner's phone

The owner reviews designs on a phone. The way that works best (owner, 2026-10-20: "a great mobile
harness for artifact mockups"): shots of the **real app** with the proposed parts drawn in, laid out
on one Artifact page with a numbered label, a line on what to look at, and the open questions at the
end. First used for Weapon Mastery: https://claude.ai/artifact/2AKaCTen9rCcchJfphJGMr

## The three pieces (all in `scripts/design/` on this branch)

| File | What it does |
|---|---|
| `mastery-mockups.js` | The worked example. Loads the real builder on a phone context (and desktop), stands in data the branch lacks, draws the new parts into the live page with the app's own classes plus a few `mk-` styles, and saves a shot per scene. Copy it for a new design |
| `mockup-review-template.html` | The review page: one column, phone shots at their true 390px width, a label chip per shot, jump links, a Questions box. Theme-aware. Replace the sections |
| `embed-shots.py` | Inlines every `shots/*.png` as a data URI, so the page publishes as one file |

## Steps

1. Copy `mastery-mockups.js` beside the job's scratch files and change the scenes. Run it from the
   code worktree on your own port (writing-browser-tests.md rule 7):
   `NODE_PATH=$PWD/scripts/e2e/node_modules SHOTS=<dir> E2E_PORT=8897 ./scripts/e2e/run.sh <script>`.
   It needs the dev bundle (`// Needs: dev bundle` on line 1) to reach re-frame.
2. Look at every shot yourself before anything else (clipping, overflow, text cut off).
3. Put the page and a `shots/` folder together, fill the template, then
   `embed-shots.py page.html page-embedded.html` and publish `page-embedded.html` with the Artifact
   tool. Republish the same path to keep the URL.

## PDF sheets

For a change to the printed sheet (owner, 2026-11-11: "this display harness is awesome"). First
used for the Traits & Feats box: https://claude.ai/artifact/JcHSPnwdJb5R5AbQZnGiWc

| File | What it does |
|---|---|
| `pdf-mockups.clj` | Fills a real sheet through the export handler (`routes/character-pdf-2`, as `routes_pdf_test` does), draws the proposed parts with PDFBox (`draw-text!`, `draw-boxes!` at a fraction of a field's width), renders each page to a 150 dpi PNG and writes `shots.json`. The helpers are reusable; copy the scenes section |
| `pdf-review-page.py` | Builds `review.html` from `shots.json`: a close-up per shot (its crop, in page pixels) with a "Show the whole page" button, every image inlined, theme-aware |

Steps, from a code worktree (no server, no browser; about a minute):
`lein run -m clojure.main <agents>/scripts/design/pdf-mockups.clj <dir>`, look at each PNG, then
`pdf-review-page.py <dir> "<title>" "<lead>" "<questions html>"` and publish `<dir>/review.html`.

- **Feed the app's own instructions** rather than drawing what it already draws: blank a level
  numeral with `:spell-relabels [{:section :box :label nil}]` and draw a box heading with
  `:spell-headings [{:class :box :section :cantrips? :ability :dc :attack}]`.
- **Render the fillable PDF.** Packing's headings find their place through the form fields; on a
  flattened export they were not drawn (seen 2026-11-11; whether real flattened exports lose them is
  unchecked).
- **Check which box a field fills:** on style 1, `features-and-traits` sits in the page 1 box
  labelled Equipment; the traits list the app prints is `features-and-traits-2`.
- **The `user` namespace already has an alias `s`** (dev profile): alias `clojure.string` as
  something else in a script run with `lein run -m clojure.main`.
- **Look at every PNG before publishing.** A round went out with columns printed over each other,
  labelled as a known flaw; the owner: "Did you LOOK at those results". Never publish a shot you
  would not sign off.
- **Match the sheet, do not guess sizes.** Spell rows are auto-sized Nimbus Sans: about 6 pt as
  printed. `render!` measures the names' printed width and sets drawn text to match; columns are
  as wide as their widest cell (`text-pt`), never fixed fractions of the row.
- **Send `spellcasting-class-N` for every spell page a mock wants.** The export picks its template
  and adds spell pages by counting those fields (`routes/character-pdf-2`, `add-missing-spell-pages!`);
  the builder sends one per page of spells, a race's included, so a Champion Tiefling gets a page
  headed "Tiefling" today. A mock that leaves the field out gets no spell page and nil row lookups.
- **Render a saved copy.** Drawn bold text rendered lighter than the app's own headings on the
  same page until `render!` saved the document to bytes and reloaded it (2026-11-11): fonts loaded
  into an open document are only subset and embedded on save. Compare drawn text with an
  app-drawn heading on the same page before publishing.
- **Draw long text, do not write it into row fields:** each row field sizes its own text, so lines
  of different length came out at different sizes (`draw-write-up!` draws every line at one size).
- **Box 1 has printed labels above its bar** (SLOTS TOTAL, SLOTS EXPENDED) and SPELL NAME under
  it: numbers drawn above that bar collide. Boxes 2-9 are clear above.
- **Use the bar the way packed sheets do:** caption in the narrow compartment (as CANTRIPS), the
  source in the wide one, numbers above (`draw-bar!`, after `pdf/draw-column-heading!`).

## Keep it easy to look through

The owner (2026-10-22): "you keep adding things to this table without making it easier to look
through". Each round, rework the page, do not append to it:

- **Summary first:** Open (what needs the owner), Decided, Bugs fixed. Short lines.
- **Group shots by topic** (where they sit in the app), not by the round they were made in.
- **Show today's screen** beside a redesign of something that already exists (owner, 2026-10-22: "I
  don't know what the rest buttons look like right now").
- **Mark every shot** Decided, Open or Fixed. Drop shots a later round replaced.
- Two-column grid on wide screens, one column on a phone.
- **When a piece is not settled, show several variants at once** (owner, 2026-10-22: "try a handful of
  designs to find the best look/layout instead of one at a time"). Hold the agreed basics fixed across
  them, vary only the open choice, and label them V1, V2... **Iterate on what the owner liked**, not only on what
  they criticised (2026-10-22: T4 was called fun and got no variants).

## Traps

- **The Artifact tool only takes `files` from the working directory or the session scratchpad.** A
  background job's `$CLAUDE_JOB_DIR/tmp` is neither, so supporting PNGs are refused; embedding them
  avoids this (8 phone shots came to about 3 MB, the limit is 16 MB).
- **Drawn-in cards need `box-sizing: border-box`.** The app's `.srd-preview` lacks it and runs past a
  phone's edge (rule-preview-cards.md); a mockup that copies it copies the bug.
- **Hide sticky and fixed bars** before shooting (`unstick` in the script), or the builder header
  covers the content. Keep the sheet or card you are showing visible.
- **A stand-in made with custom items needs a remount** of the Combat tab (writing-browser-tests.md
  rule 6); the script does the tab switch.
- **Say what is real.** Label a shot of the unchanged app as today's app, not a mockup.
