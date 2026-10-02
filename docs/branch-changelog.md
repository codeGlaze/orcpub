<!-- Branch changelog. Fold into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. See docs/branch-changelog.template.md. -->

# Branch changelog — `feature/style-guide`

Parent: `integration` at `614c17ff`, merged up to `1aa80ec3`.

## Why this branch exists

A style guide for the site, written from the Garden stylesheet (`docs/design/style-guide.md`),
and the fixes it turned up. Open Sans was loaded at regular weight only, so nothing marked bold
rendered bold in Chrome, and buttons drew in Arial. Bold was then tuned by a size rule, button
contrast became an opt-in, and checking the builder on phones turned up a run of layout faults,
the largest being that the layout was chosen by the device's user agent rather than the window
width. Every visual decision was made by the owner from captures of the running site; those
captures are on `claude/artist-profile-pages` under `docs/design/style-guide/`.

## Highlights

The page now lays itself out by window width: a desktop browser narrowed to phone size gets the
phone layout, and switches back when widened. On phones the header, buttons and builder share
one 10px gutter, and the header's tab menus open on screen again.

## Added

- A "Dark Button Text" option beside "Light Theme" puts dark text on the yellow buttons for anyone who finds white hard to read. Off by default; remembered in the browser and on the account. (`97639a41`)
- A style guide for the site: palette and roles, type, spacing, radius, focus, buttons, and rules for new work (`docs/design/style-guide.md`). (`4ef09256`)

## Fixed

- A desktop browser narrowed to phone width gets the phone layout, and the desktop one back when widened; it used to keep the desktop page squeezed into the narrow window. A phone keeps the phone layout at any width. (`77fcb2e6`)
- The builder stays on Description when the window crosses phone width; it used to jump to the character sheet. (`77fcb2e6`)
- On phones the header tab menus open on screen; the header clipped them out of view. (`66ccbcb6`)
- On phones the first tab's menu no longer hangs off the left edge: the three left-hand tabs open their menus to the right. (`66ccbcb6`)
- On phones the header tabs share the row evenly, instead of spreading out with wide gaps. (`b58d2af3`)
- On phones the top bar fits the screen; the login button ran off the right edge. (`3b652700`, `b58d2af3`)
- On phones the search button is a square with its magnifier centred, and LOGIN is centred in its button. (`942c177d`)
- On phones the logo, tabs, title, buttons and builder all start 10px from the edge; they started at 20, 12, 10 and 15px. (`bea36ada`)
- On phones all six ability buttons fit on the row; the sixth was cut off at the right edge. (`6ce56c00`)
- The Light Theme and Dark Button Text toggles no longer touch the right edge of the screen. (`6ce56c00`)
- "MY CONTENT" fits on one line in the header. (`844c15ca`)
- Text marked bold renders bold: Open Sans is loaded at 400, 600 and 700, not 400 alone. (`4159164c`)
- Buttons use the site font instead of the browser's default Arial. (`4159164c`)

## Changed

- Text on the yellow buttons has a faint edge in the opposite tone, so the letters stand out from the amber. (`bf7861a7`)
- The header tabs get the same faint dark edge. (`844c15ca`)
- With Dark Button Text on, the ability buttons get a stronger glow and a thin yellow outline, because their short labels read weaker than the other buttons. (`844c15ca`, `d579d94b`)
- The character sheet's roll buttons have 16px labels instead of 14px, at the same height. (`1e677a3f`)
- The yellow buttons are 700, restoring the stroke weight they had in Arial Bold. (`a891db35`, `844c15ca`)
- Bold text at 18px and up renders at 600, not 700: page titles, headings, spell and monster names. (`a891db35`)
- The builder's info boxes are regular weight, not bold; their CLICK HERE links stay bold. (`a891db35`)
- Page titles are 28px on phones, so "Character Builder" fits a 320px screen. (`b58d2af3`)
