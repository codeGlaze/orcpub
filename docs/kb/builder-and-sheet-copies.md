# The builder and the sheet hold two copies of one character

**Fixed on `feature/portrait-builder` (PR #64, `db8539101`, 2026-10-15). Live on `integration` and the
deployed site until #64 merges.**

The builder edits `db :character`. The character sheet and the character list edit
`db ::char5e/character-map`, keyed by id, and autosave that copy (`update-character-fx`, then the
throttled `::char5e/save-character-throttled`, 7.5 s after the last edit). When both hold the same
character, they used to be reconciled by replacing one with the other.

## What was lost, proven on a real server

`test/browser/builder_keeps_unsaved_edits_e2e.js` (11 checks; 7 failed on #64 before the fix, all
pass after). Each loss was reproduced before anything was patched.

| Path | What happened |
| --- | --- |
| Unsaved builder edit, then a sheet edit to the same character | `:character-save-success` installed the autosave's reply as the builder's copy. The builder's unsaved edit was gone, `:changed` was cleared, and the localStorage draft lost it too |
| Level Up from the sheet with unsaved builder edits | `::char5e/level-up` dispatched `:set-character` with the copy read BEFORE `add-level` ran: the builder showed the old level and lost its edits; the level's autosave then replaced it again |
| A sheet autosave that fails | `dispatch-throttled-saves` removed the `beforeunload` warning when the save was SENT, so after a failure the tab closed with no prompt and the edit was gone |

Saving a different character while the builder had another open was the other half of the first
row; #64 had already fixed that (`saved-character-is-the-one-being-edited?`).

## The fix

- A sheet or list edit to the character open in the builder is applied to both copies, and the
  `::entity/values` keys it changed are recorded in `::events/sheet-edited`.
- An autosave reply for that character, while the builder has `:changed`, leaves the builder's copy
  and copies in only the recorded keys (they carry the server's new ids). With nothing unsaved, the
  reply is installed as before.
- Level Up applies the level through the same path and only opens the stored copy when the builder
  holds a different character (`::char5e/open-stored-in-builder`, which reads the map when it runs).
- `autosave_fx/unconfirmed-saves` keeps the close warning until `::autosave-fx/saved` reports each id.

## Why not a three-way merge here

A merge needs the copy as it was first loaded, and after a reload that copy only exists if it is
stored with the draft: the per-character draft store (#704,
[multi-tab-character-contamination.md](multi-tab-character-contamination.md)). Recording what the
sheet changed at the moment it changes it needs nothing stored. Agreed with the character-rescue
session, 2026-10-15; the full merge belongs with that store (its plan item 9).

## Still open

- **Not proven, not patched:** notes edited in both the builder and the sheet (the sheet's
  `set-notes` replaces the builder's text when mirrored); an edit made while that character's save
  is in flight (the reply's older value is copied over the newer one).
- **Unchanged saves are still sent** (tick then untick), the whole character every time. Waste, not
  loss. Each save is a transaction, which bears on the database-size question.
- **Two requests racing** can still store duplicate selections (R10 on `feature/character-rescue`).
  That branch plans a server version check (`:db/cas` on a revision) in place of a duplicate-key
  guard, pending the owner's OK.
- **Two tabs sharing the one draft slot** (#704) is untouched by any of this.
