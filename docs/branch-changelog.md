# Branch changelog — `refactor/garden-harvest`

## Why this branch exists

`refactor/garden-inline-styles` converted inline `:style` maps to named Garden classes, reached
about 60% done in July 2026, and stalled — it had become a base for other branches rather than
something anyone had to finish. It cannot simply be merged: `integration-local` is 648 commits
past their merge-base, a real test merge conflicts in `styles/core.clj` (one 256-line hunk where
both sides rewrote the same section) and `views.cljs` (one 72-line hunk over a dev-mode footer the
branch has never seen), and the conversion is stale — it would convert June's inline styles and
leave the ones integration has added since.

So the work is **harvested** instead. The class definitions — the expensive part, the judgement
about what to name and what to group — transfer wholesale: 72% of integration's remaining inline
styles are shapes that branch already solved. This branch applies them to integration's CURRENT
code, plus the one header fix integration still lacks.

Deliberately excluded: the branch's header-dropdown z-index fix and its CSS-only flyout change
(integration already solves both, better — taking them would regress mobile focus handling and the
flyout height cap), and its two unfinished `wip:` commits of visual polish.

Reviewer context and the full measurement:
`git show agents/develop:docs/kb/garden-inline-styles-harvest.md`.

**Parent:** `integration-local` @ `593cb50c`.
**Return path:** merges back to `integration-local`. Nothing is stacked on this branch.
**Source quarried:** `refactor/garden-inline-styles` @ `59c22902`, left in place and not merged.

<!-- Entries below as work lands. One change per bullet, ending with (`shorthash`). -->

## Added

- **The Garden class definitions from `refactor/garden-inline-styles`** — 49 classes lifted from
  that branch into `styles/core.clj` without merging it, covering the header bar, registration and
  password-strength pages, the loading and Orcacle overlays, form inputs, SVG icons and a handful
  of utilities. Four collided with classes integration already had and were left alone. Nothing
  renders differently yet: the call sites that still use inline styles are converted separately.

## Fixed

- **The mobile header no longer crowds itself** — on a phone the logo is capped, a full-width
  child no longer measures wider than the bar it sits in, the import log panel cannot exceed the
  screen, and a child that still outruns the bar is clipped rather than scrolling the whole page
  sideways.

## Changed

- **The registration, login and password pages use named classes instead of inline styles** —
  28 of the 75 inline style maps in the views are gone, replaced by the classes harvested above:
  the success and heading text, the submit buttons, the password-strength bars and their label,
  the updates checkbox, the social icon and several layout wrappers. The password meter keeps an
  inline width, because that width IS the measurement. Nothing renders differently.
