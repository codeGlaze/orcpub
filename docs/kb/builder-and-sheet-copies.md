# The builder and the sheet hold two copies of one character

**Fixed on `feature/portrait-builder` (PR #64, `db8539101`, 2026-10-15). Live on `integration` and the
deployed site until #64 merges.**

The builder edits `db :character`. The character sheet (the character's page,
`/pages/dnd/5e/characters/<id>`: hit points, XP, slots, prepared spells, features used, notes, worn
and wielded gear, attunement, Level Up) and the same controls in the character list edit
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
| A builder Save within 7.5 s of a sheet edit | The builder's stale copy stored the old hit points (7, not 9) and the sheet's edit was lost |
| An edit made while its save was in flight | The reply put the older value back in the stored copy (4 became 3), and the next autosave stored 3 |
| An edit undone before the autosave (12, then back to 9) | Not a loss: a save was still sent, the whole character, one transaction |

The last three were found on 2026-10-15 by testing the "theoretical" cases rather than reasoning
about them; the browser test now has 15 checks, 11 of which fail on the pre-fix source.

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
- Every copy the server sends (a load or a save's reply, through `::char5e/set-character`) is
  recorded in `::events/stored-shape`, strict and without ids; `::char5e/save-character` sends
  nothing for a copy that matches it.
- `::events/in-flight` marks a character while its autosave is out; an edit then sets
  `::events/edited-in-flight`, and the reply is recorded as stored without replacing the newer copy
  (`3c110ab3a`).

## Why not a three-way merge here

A merge needs the copy as it was first loaded, and after a reload that copy only exists if it is
stored with the draft: the per-character draft store (#704,
[multi-tab-character-contamination.md](multi-tab-character-contamination.md)). Recording what the
sheet changed at the moment it changes it needs nothing stored. Agreed with the character-rescue
session, 2026-10-15; the full merge belongs with that store (its plan item 9).

## Still open

- **Notes cannot collide in one tab:** the builder has no notes field, so only the sheet edits them.
  (An earlier version of this page listed it as an open risk; that was wrong.)
- **A changed save still sends the whole character.** Each save is a transaction, which bears on the
  database-size question.
- **Two requests racing** can still store duplicate selections (R10 on `feature/character-rescue`).
  That branch plans a server version check (`:db/cas` on a revision) in place of a duplicate-key
  guard, pending the owner's OK.
- **Two tabs sharing the one draft slot** (#704) is untouched by any of this.
