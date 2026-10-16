# Plan: one draft per character, and restoring without nagging

**Status:** steps 1-4 built on `feature/portrait-builder` (PR #64): storage `7d6187f75`, the notice
box lifted from `fix/hidden-multiclass-skill-pick` `ecb3776af`, the offer, hold and lists
`7120493cc` (2026-10-16). R0-R7 and R9 pass in `character_drafts_e2e.js` (29 checks), each seen
failing. Step 5 (expiry) waits on the owner's answer to R8's amendment; step 6 (Data View) belongs
to the rescue branch.

## The restore rules (agreed with the owner, 2026-10-15)

These are requirements. Each has a browser check in `test/browser/character_drafts_e2e.js`, and the
build is not done until every check has been seen failing on the old code and passing on the new.

| # | Rule | Check |
| --- | --- | --- |
| R1 | **Your own tab never asks.** A reload restores that tab's own draft silently. Only a leftover from a closed tab is ever offered | Unsaved edit in tab 1, reload tab 1: the edit is there, no notice |
| R2 | **A draft that changes nothing is dropped silently.** If it matches the saved character it is deleted without a word | Edit then undo, close the tab, open the character elsewhere: no notice, and the draft key is gone |
| R3 | **A draft older than your saved work is never offered over it.** Each draft records the saved version it started from; if the character has been saved since, the builder opens the saved version and does not prompt. The draft stays reachable from the drafts list until it expires | Unsaved draft in tab 1; the sheet saves the character in tab 2; close tab 1; open the character in tab 3: no notice, draft listed |
| R4 | **Never a popup.** The character opens as saved, with one line in the notice box: "Unsaved changes from Tue 14:02 · Restore" and a close mark for "not now" (Discard is in the drafts list; owner, 2026-10-16). Ignoring it keeps the saved version | A leftover shows the line, not a dialog; the builder holds the saved version until Restore |
| R5 | **Asked once.** Discard deletes the draft. Ignored, it is not raised again on later opens; it stays in the drafts list until it expires | Open, ignore, open again: no line. Discard: the key is gone |
| R6 | **Restore can be undone.** It changes only the builder; the saved version is untouched until Save, and Undo puts it back | Restore, then Undo: the builder holds the saved version, and nothing was sent |
| R7 | **A draft open in another tab is not a leftover.** An open tab keeps its draft's heartbeat fresh; opening the same character elsewhere holds editing with "Open in another tab · Edit here" instead of offering the draft (owner, 2026-10-16) | Tab 1 open with unsaved edits; tab 2 opens the same character: that line, no Restore, and the options do not take edits until Edit here |
| R8 | **Old drafts expire** after 30 days untouched. **Amended for safety (owner to confirm):** a draft of a character that has never been saved is the only copy, so it never expires | Seeded drafts: a saved character's draft 31 days old is deleted at start-up; a never-saved one 31 days old is kept |
| R10 | **A draft belongs to the account that made it** (owner, 2026-10-16). Drafts made logged out are anyone's, so they carry over to whoever logs in. After another account logs in on the same browser, the last account's drafts are not restored, offered or listed; nothing is deleted. The player's own Log Out starts a fresh builder; an expired session does not touch it. Chosen over clearing drafts on logout, which would lose work and could not tell a Log Out from a dropped session | `soak_e2e.js` part 6; unit tests in `events_test.cljs`. Built `523e799cc` |

Two more checks the build must pass, for the bug itself and the move:

- **R0** Two tabs on two characters, each with unsaved edits: reloading either shows its own character
  and its own edits (the #704 case, which fails today).
- **R9** The old single `"character"` slot is moved to its own key on first load and deleted; nothing
  in it is lost.

## How it works today (`feature/portrait-builder`, which carries integration's code)

- One key, `"character"` (`db.cljs:35`), written by `character->local-store` (`db.cljs:201`) from the
  `->local-store` step of `character-interceptors` and from `db-char->local-store` (`events.cljs:143`):
  every builder edit, `:set-character`, `:character-updated`.
- Read once, at start-up: the `:local-store-character` cofx (`db.cljs:442`) in `:initialize-db`
  (`events.cljs:363`). Unreadable: deleted (`handle-unreadable`; characters get no `:corrupt` copy).
- The builder's address has no id (`route_map.cljc:199`).
- Saving logged out answers 401 and routes to login, so a logged-out character exists only as the draft.
- Also reads the slot: the Data View draft page on `feature/character-rescue` (`ledger.cljs:178, 289`),
  and tests (`fixes_persist_e2e.js`, `db_test.cljs`, `events_test.cljs`, rescue's `character-data*.js`).
- `stored-shape` and `::events/stored-shape` (#64, `3c110ab3a`) already record what the server holds
  for each character: R2 and R3 compare against it.

## Design

- **Keys.** `"character-draft/<key>"`, where `<key>` is the character's id, or for a character never
  saved, a random id made in the browser when it is created (New, Clone, Random, a fresh builder). No
  server request, so it works logged out; nothing to reserve or recycle.
- **A draft** is the strict character plus `:changed`, `:base` (the stored shape's hash when the copy
  was opened, nil for never-saved), `:touched` (time of the last edit) and `:asked?` (R5).
- **The tab's key** lives in `sessionStorage` (`"builder-draft-key"`), so a reload knows which draft is
  its own (R1) and other tabs do not share it.
- **Heartbeat (R7).** While the builder holds a key, the tab writes `"draft-live/<key>"` with its tab id
  and the time every 20 s, and clears it on `pagehide`. Fresher than 60 s means open elsewhere.
- **First save** moves the draft from its browser key to the real id, and sets the tab's key.
- **Start-up:** the tab's own key if it has one (R1); otherwise the most recent draft that is not open
  elsewhere (today's "continue where you left off"); otherwise a new character. Then expire (R8) and
  migrate the old slot (R9).
- **Opening a saved character** (Edit, Level Up, a list): look for its draft. None, identical (R2),
  stale (R3), already asked (R5) or live elsewhere (R7): open the saved version, with R7's line if it
  applies. Otherwise open the saved version with R4's line.
- **New and Clone with unsaved changes** do not delete the previous draft: another tab may hold the
  same character's draft under the same key, and deleting it would lose that tab's work. It stays a
  leftover, which R2 drops if it changes nothing and R4 offers once otherwise.
- **A tab with no key of its own** continues only the latest never-saved character's draft that no
  other tab holds; a saved character's leftover waits to be offered when it is opened (R4). The one
  exception is the move from the old single slot: that tab takes the moved draft as its own, so the
  first load after the change opens as before.
- **Opening the character the builder already holds** (Edit on the sheet or list) keeps the builder's
  copy and its unsaved edits instead of reloading the stored one.
- **"R4: no popup"** has no check of its own: the offer is drawn by the notice box, which the R4
  check reads, and a break that adds a dialog would not test anything real.
- **Two tabs on the same character** share one key, so the later write wins. Not a mix-up between
  characters; the rescue plan's item 9 mirroring is the fix for that case.
- **Storage space.** Drafts share the browser's ~5 MB with the homebrew library
  (`localstorage_ceiling_e2e.js`). Saved characters' drafts are deleted on save and expire; a failed
  write is reported, never silent.

## Build steps, in order

1. A `drafts` namespace (cljc where it can be): key naming, read, write, list, delete, expire, the
   heartbeat. Unit tests first, each seen failing.
2. Point `character->local-store` and `:local-store-character` at it; the tab key in sessionStorage.
   R0 and R1 pass here.
3. Browser ids for New, Clone and Random; the move on first save. R9's migration.
4. The open path: R2, R3, R5, R7 decisions, then R4's line and R6's Undo. **The line and the drafts
   list are new UI: mockups first, reviewed against the UX guide, before they are built.**
5. Expiry at start-up (R8).
6. Data View's draft page reads the tab's draft instead of `"character"`: on the rescue branch, or by
   whichever branch merges second.
7. One verification pass: lint, `lein test`, cljs, the browser suite.

## Open, for the owner

- Where to build, and who: it needs `stored-shape` from #64, so either a branch stacked on
  `feature/portrait-builder` (as #65 is) or one cut from `integration` after #64 merges. The rescue
  session owns Data View and item 9.
- ~~R8's amendment: never-saved drafts do not expire.~~ Built as proposed so the owner can see it in
  use (2026-10-16). **Re-review on or after 2026-11-16:** is 30 days right for saved characters'
  leftovers, and should never-saved drafts stay forever?
- ~~The drafts list: where it lives.~~ Both above the builder's options and on the character list (owner, 2026-10-16).
