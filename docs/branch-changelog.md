# Branch changelog — `test/overlay-probe-user-menu-and-modals`

## Why this branch exists

Kept open while the import UI's wording is still moving. The helper every homebrew
probe depends on clicks the import modal's primary button by label, and that label
has changed three times: "Import", then "Import with these fixes" (`f7285198`),
now "Import with default fixes" (`2661be88`).

## Fixed

- **The import helper follows the button's latest wording**, with the bare-word
  entries kept last so the next rewording of the same control still lands.
