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
- **The banner says what happened and stops there.** The advice line — "To be
  safe, export all content now" — is gone rather than promoted to a button: on My
  Content it would have sat directly above the page's own Export All. A message
  pointing at a control already on screen is noise.

- **The browser-storage warning leads with the point.** It was one
  200-character sentence opening with "IMPORTANT!:" and ending with the export
  link — the part that matters, last. Now: "Spell saved — in this browser only",
  and under it "Clearing browser data loses it. Export this source to keep a copy."
- **The banner is sized to what it says** rather than to one fixed width. A flat
  cap fixed the five-word slab and then squeezed a two-sentence warning into a
  narrow column; `fit-content` with a ceiling does both jobs.
- **A save confirmation says what it saved** — "Saved “Flame Tongue”" rather than
  "Your item has been saved.", for characters too when they have a name.

## Fixed

- **A message that is markup renders as markup.** The builders' "please fill in
  X" carries a bolded field name and a clickable "Save anyway with placeholders",
  and the new banner ran `str` over it, printing the hiccup at the reader. Vectors
  now pass through untouched.

- **Callout action buttons carry a React key again** — the key was attached to the
  `let` form rather than the element it returns, so every callout with actions
  logged a missing-key warning.
