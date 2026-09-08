# Branch changelog — `test/overlay-probe-user-menu-and-modals`

## Why this branch exists

Kept open while the import UI's wording is still moving. The helper every homebrew
probe depends on clicks the import modal's primary button by label, and that label
has changed three times: "Import", then "Import with these fixes" (`f7285198`),
now "Import with default fixes" (`2661be88`).

## Added

- The overlay probe walks **My Content's delete-all guard** — the quiet `Delete…`
  button, the `.mc-liftpop` it unfurls, and the `.mc-confirmbar` underneath. It
  cancels at the last step and then asserts the stored library is the same size it
  was, so a run can never be one stray click from wiping a library. None of the
  three steps is a modal, which is why the earlier version of this probe waited on
  a selector belonging to the item builder's confirmation and skipped.

## Fixed

- **The import helper follows the button's latest wording**, with the bare-word
  entries kept last so the next rewording of the same control still lands.
