<!-- Branch changelog. Fold into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. -->

# Branch changelog — `feature/inventory-kits`

## Why this branch exists

The second round on kits after #85: the owner's review of the Manage inventory controls. The custom
"+ Adding items" and "Packs ×" pills and the greyed "Add a kit" slot are replaced, after research on
how search boxes with filter pills work (Apple search tokens, Salt tokenized input, NN/g scoped
search): a Packs pill inside the search box with a "Packs filter" row to bring it back, the same
filter on the sheet's list, an "Add equipment" label on the adding box, and one Add a kit tile at
every width. Also the auth pages' card, which overflowed above the window when the register form's
password hints made it taller, so its header could not be scrolled to. Decisions:
`plan-inventory-kits.md` on agents/develop, "Packs chips and the Add a kit tile".

## Added

- **Character sheet:** the inventory list's search has a Packs filter that shows only what your kits hold.

## Changed

## Fixed

## Security
