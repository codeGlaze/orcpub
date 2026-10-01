<!-- Branch changelog. Fold into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. See docs/branch-changelog.template.md. -->

# Branch changelog — `feature/style-guide`

Parent: `integration` at `614c17ff`.

## Why this branch exists

A style guide for the site, written from the Garden stylesheet (`docs/design/style-guide.md`),
and the first fixes it turned up. Open Sans was loaded at regular weight only, so nothing the
stylesheet marks bold rendered bold in Chrome; buttons had no font set and drew in Arial. The
header weights (tabs 700, page title 600) were chosen by the owner from captures of the running
site. Bold across the site was then tuned by a size rule (700 small capitals, 600 at 18px and up,
400 for sentences). Button contrast is an opt-in (white on amber is 2.1:1). Captures are on
`claude/artist-profile-pages` under `docs/design/style-guide/`.

## Added

- A "Dark Button Text" option beside "Light Theme": dark text on the yellow buttons, which are hard to read in white. Off by default; remembered in the browser and on the account.
- A style guide for the site: palette and roles, type, spacing, radius, focus, buttons, and rules for new work (`docs/design/style-guide.md`). (`4159164c`)

## Fixed

- On phones the header tabs share the row evenly with small gaps, instead of spreading out with wide gaps from an empty slot on the left; the top bar fits the screen (the login button ran off the right edge), and the page title is 28px so it fits a 320px screen.
- On phones the search button is a square with its magnifier centred, the LOGIN label is centred in its button, and the two are the same height.
- On phones the logo, header tabs, page title, buttons and builder all start the same 10px from the edge; they started at 20, 12, 10 and 15px.
- A desktop browser narrowed to phone width now gets the phone layout, and the desktop one back when widened; before, the layout was fixed by the device at load and a narrow desktop window drew the desktop page squeezed into phone width. A phone keeps the phone layout at any width.
- The builder stays on Description when the window crosses phone width; it used to jump to the character sheet.
- On phones, all six ability buttons fit on the row; the sixth (CHA) used to be cut off at the right edge.
- The Light Theme and Dark Button Text toggles no longer touch the right edge of the screen.
- White text on the yellow buttons has a faint dark edge, and the optional dark text a faint light one, so the letters stand out from the amber. The header tabs get the same dark edge, and the small ability buttons a stronger glow plus a thin yellow outline.
- "MY CONTENT" fits on one line in the header: the tab titles are 4% tighter.
- Text marked bold now renders bold: Open Sans is loaded at 400, 600 and 700, not 400 alone. (`4159164c`)
- Buttons use the site font instead of the browser's default Arial. (`4159164c`)

## Changed

- Bold text at 18px and up renders at 600, not 700: page titles, headings, spell and monster names. One stylesheet rule; 700 looked plump at those sizes.
- The builder's info boxes are regular weight, not bold; their CLICK HERE links stay bold.
- The roll buttons on the character sheet (abilities, saves, skills, weapons, spells, tools) have 16px labels instead of 14px, at the same 31px height.
- The yellow buttons, including the ability buttons, are 700, restoring the stroke weight they had in Arial Bold.
- Page titles are 600 rather than bold, which looked heavy at 36px. (`4159164c`)
