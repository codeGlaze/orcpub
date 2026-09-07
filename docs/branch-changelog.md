# Branch changelog — `test/overlay-reachability`

## Why this branch exists

Two bugs in a row were of the same kind: something on screen that could not be
clicked — header dropdowns under the button row, then My Content running off the
bottom of a short window. Both looked correct in a screenshot, and no probe
covered either, because visibility is what probes usually assert.

This adds one probe for that whole class, and it is built to be cheap: one cold
page load, every state driven on the same page, no reloads, no `networkidle`, a
bounded budget rather than an open-ended wait. It runs in 17 seconds.

## Added

- `test/browser/overlay_reachability_e2e.js` — hit-tests every control an overlay
  shows (header chrome, Orcacle, the PDF options panel, the release panel) and
  fails if one is covered or off the window inside a floating layer. `SELFTEST=1`
  proves it can fail; a state that audits nothing fails as a stale selector, so it
  cannot quietly stop asserting. Overlays needing a login, an import or saved
  content are listed as skips rather than left unmentioned.
