<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `feature/inventory-kits`

## Why this branch exists

Choosing an equipment pack used to drop its contents onto the sheet as items the player could not
touch, and the same item from two sources overwrote itself (a pack's rations and rations picked by
hand showed as one). This branch makes a pack's items ordinary inventory rows, adds kits that keep
count of what a pack runs out of, and brings that to the character sheet, where items are used in
play. The rules live in `orcpub.dnd.e5.inventory` and the screens in `orcpub.dnd.e5.views.inventory`
(the builder's inventory lists moved there from `character_builder.cljs`). Plan and decisions:
`plan-inventory-kits.md` on agents/develop.

## Added

- **Inventory:** equipment packs become kits: the pack's items are ordinary rows, and the kit keeps count of what runs out (torches, rations) with Restock, Re-bundle and Undo; spent items come out of the top kit first.
- **Inventory:** two of the same pack make one kit with a count (Explorer's Pack ×2), with Remove one pack.
- **Character sheet:** kits on the Equipment tab, and a Manage inventory switch that adds items, changes quantities and coins on the sheet, and turns itself off when done (#229).
- **Character sheet:** a backpack Worn | Off switch; set down, the backpack's contents show as not carried, still counted.
- **Builder:** Add a kit opens the item search on the packs; the search box can open on any preset list.
- **Builder:** a Developer mode switch hides the empty "select any number / complete" pick counts.

## Changed

## Fixed

- **Inventory:** the same gear from several sources adds up instead of one replacing the rest, such as a pack's rations plus rations picked by hand (#138).
- **Inventory:** two of the same weapon or armor count as two (#340).
- **Character sheet:** packs' rations show on the sheet.
- **Character sheet:** magic armor is listed with the other magic items.
- **Character sheet:** when the same character was saved from another tab or device first, the sheet's item-charge changes were lost on the retry; they are now kept, as hit points and notes were.

## Security
