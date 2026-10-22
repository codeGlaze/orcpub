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
