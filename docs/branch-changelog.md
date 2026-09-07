# Branch changelog — `hotfix/my-content-dropdown-and-probe-cost`

## Why this branch exists

The "+ add content…" dropdown in My Content opened as a white void: a global rule
gives every `select` white text on a transparent background, and with no
`color-scheme` the browser draws the native option list light — white text on a
white popup, so the entries were invisible. The control itself carried none of the
app's dropdown styling either.

And the release-panel probe had grown to nearly two minutes.

## Fixed

- **The "+ add content…" dropdown in My Content is readable and matches the app** —
  its option list follows the theme instead of opening as a white box with
  invisible entries, and the control takes the same border and text as every other
  dropdown. `color-scheme` is set on `select` for both themes, so any unclassed
  dropdown is covered too.

## Changed

- **The release-panel probe runs in 30 seconds instead of 124** — same 18 checks.
  Cases that did not need their own app boot are folded together, and the two
  independent stories (the ordinary one, and the cookie notice) run in parallel
  contexts, so the wall clock is the longer lane rather than the sum.
