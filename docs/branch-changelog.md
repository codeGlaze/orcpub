# Branch changelog — `fix/header-flyout-under-sticky-toolbar`

## Why this branch exists

Every header dropdown rendered under the page's button row, so most of the items
in them could not be clicked. The sticky header landed with `z-index: 100`, the
same value the hovered header tab already used; the tab is positioned and
z-indexed, so it forms a stacking context and the flyout's own `z-index: 10000`
is resolved inside it. At an equal 100 the button row won on document order.

The browser probes covered the sticky header itself — one copy, stuck at the
right time, no sideways scroll — but nothing checked that what opens ABOVE it
stayed reachable, and visibility alone would have passed anyway.

## Fixed

- **Header dropdowns are clickable again** — Characters, Spells, Monsters, Items,
  Encounters and My Content all rendered under the button row, which swallowed
  the click on most of their items.
