# Branch changelog — `refactor/banner-parts-and-design`

## Why this branch exists

The import banner's parts were joined with `\n` and rendered as HTML, which
collapses them — so three sentences arrived with no punctuation between them. The
previous fix made CSS honour the newlines, which treated the symptom: display text
should be structured, not a string with sentinels in it.

The banner also had no hierarchy — three bold lines of equal weight, an emoji
standing in for an icon, a saturated warning-orange slab for a *success*, a close
glyph with no touch target, and its most useful suggestion written as advice
rather than offered as an action.

## Changed

- **Import messages are structured** — `format-import-result` returns
  `{:title :details}`, one idea per line, and the banner renders each line itself.
  No newline sentinels, no `white-space: pre-line`, no regex collapsing blank
  lines. A plain string is still accepted and split at the edge, so the remaining
  legacy callers keep working while they move over.
- **The banner has a hierarchy** — a severity icon carrying the colour, a headline,
  supporting detail at a lighter weight, a tinted box instead of a filled slab,
  and a dismiss with a real touch target.
- **A successful import reads as success** — green, not warning-orange.

## Added

- **"Export a backup" in the import banner** — the advice line becomes the action
  it was describing. It uses the pretty-print export (a blob and a save) rather
  than the gated one, which can open the export-warning or conflict modal, and it
  stops its own click so the banner's click-to-close cannot pull the surface out
  from under it.

## Fixed

- **Callout action buttons carry a React key again** — the key was attached to the
  `let` form rather than the element it returns, so every callout with actions
  logged a missing-key warning.
