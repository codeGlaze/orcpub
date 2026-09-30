<!-- Branch changelog. Fold into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. See docs/branch-changelog.template.md. -->

# Branch changelog — `feature/style-guide`

Parent: `integration` at `614c17ff`.

## Why this branch exists

A style guide for the site, written from the Garden stylesheet (`docs/design/style-guide.md`),
and the first fixes it turned up. Open Sans was loaded at regular weight only, so nothing the
stylesheet marks bold rendered bold in Chrome; buttons had no font set and drew in Arial. The
header weights (tabs 700, page title 600) were chosen by the owner from captures of the running
site. Two side effects are open for review before merge: every element marked bold across the
site is now visibly bold, and the My Content header tab wraps at 1280px. Captures of both are on
`claude/artist-profile-pages` under `docs/design/style-guide/`.

## Added

- A style guide for the site: palette and roles, type, spacing, radius, focus, buttons, and rules for new work (`docs/design/style-guide.md`). (`4159164c`)

## Fixed

- Text marked bold now renders bold: Open Sans is loaded at 400, 600 and 700, not 400 alone. (`4159164c`)
- Buttons use the site font instead of the browser's default Arial. (`4159164c`)

## Changed

- Page titles are 600 rather than bold, which looked heavy at 36px. (`4159164c`)
