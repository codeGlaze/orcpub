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
400 for sentences). Button contrast is an opt-in (white on amber is 2.1:1). Open before merge: the My Content
header tab wrapping (the label needs 92.5px of a fixed 90px). Captures of both are on
`claude/artist-profile-pages` under `docs/design/style-guide/`.

## Added

- A "Dark Button Text" option beside "Light Theme": dark text on the yellow buttons, which are hard to read in white. Off by default; remembered in the browser and on the account.
- A style guide for the site: palette and roles, type, spacing, radius, focus, buttons, and rules for new work (`docs/design/style-guide.md`). (`4159164c`)

## Fixed

- White text on the yellow buttons has a faint dark edge, and the optional dark text a faint light one, so the letters stand out from the amber.
- Text marked bold now renders bold: Open Sans is loaded at 400, 600 and 700, not 400 alone. (`4159164c`)
- Buttons use the site font instead of the browser's default Arial. (`4159164c`)

## Changed

- Bold text at 18px and up renders at 600, not 700: page titles, headings, spell and monster names. One stylesheet rule; 700 looked plump at those sizes.
- The builder's info boxes are regular weight, not bold; their CLICK HERE links stay bold.
- The yellow buttons are 700, restoring the stroke weight they had in Arial Bold.
- Page titles are 600 rather than bold, which looked heavy at 36px. (`4159164c`)
