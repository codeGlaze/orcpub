<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `feature/weapons-table`

## Why this branch exists

The character sheet's Weapons table showed one attack roll and one damage roll per weapon, with
none of what magic weapons add (Flame Tongue's fire, a Javelin of Lightning's bolt), and nothing
on the sheet tracked an item's charges. This branch rebuilds the table around a roll engine, then
follows the charges where they lead: charges on the sheet, a Rest box that brings them back at the
right time, casting spells from charged items, and the item builder fields to make such items.
Plans: `handoff-weapons-table.md`, `plan-charges-and-casting.md`, `handoff-rest-controls.md` on
agents/develop. The SRD spell rolling data (`spell-rolls.edn`) was built on `feature/srd-pipeline`
and cherry-picked here; those two commits will also arrive with that branch.

## Added

- **Characters:** a new Weapons table: attack and damage tiles that roll, results in a sheet that chains the turn's attacks, conditional damage from a + menu, main-hand and off-hand marks, and a desktop layout with a Details column.
- **Characters:** every SRD magic weapon's extra damage and once-per-dawn powers, on the sheet and in the PDF.
- **Characters:** charges for the SRD's charged items, shown as pips to tap; staffs spend them on their powers.
- **Characters:** a Rest box on the Features tab, and a floating Rest button on the other tabs: short and long rests, dawn and dusk, recharging items by their roll or yours, with Undo.
- **Characters:** cast spells from charged items, priced in charges per level, from the item or from the Spells tab, and roll the spell's damage at the level cast.
- **Homebrew:** custom items can have charges, a recharge time and the spells they cast.

## Changed

- **Printing:** each weapon is named once on the PDF, with its two-handed damage and extras as bullets beneath it.
- **Characters:** a long rest no longer counts as dawn; items that recharge at dawn or dusk wait for it.
- **Content:** SRD weapon and armor names in Title Case, without the Wikipedia links.

## Fixed

- **Characters:** a magic item's spell DC bonus no longer raises ki, Breath Weapon and other DCs that are not spell DCs.
- **Characters:** uses that come back once a day were never reset; they now reset at dawn.
- **Rules:** a rule's preview card fits on a phone screen.

## Security
