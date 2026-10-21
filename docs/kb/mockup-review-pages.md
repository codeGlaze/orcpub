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
