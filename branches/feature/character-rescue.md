# feature/character-rescue

**Purpose:** nothing stored on a character is ever trapped. Data View (internally "the ledger"):
a standalone page, its own script (`ledger.js`), that lists a saved character's or this tab's
draft's stored choices for its owner, with Remove, Undo, Save and Copy for support. Plus the server
version check that refuses a save made from an older copy. **Base:** `integration`.
**PR into:** `integration`, when the owner says. **KB:** `docs/kb/character-rescue.md`, on agents/develop since 2026-11-26.

## State (updated 2026-11-25)

- Done: Data View and its entry points, the support email's repair link, the version check (409 on
  a stale save), unreadable drafts kept, the redesign. `integration` merged in on 2026-11-25
  (`d6596d1ce`, pushed): the Data View script now builds with cljs.main (`lein cljs:ledger`), like
  the app.
- Not on the branch: the Data View drafts picker (handoff of 2026-10-25 said "not pushed"; the code
  is lost). Owner, 2026-11-25: ship without it, add it later.
- Done 2026-11-25, pushed (`07dc14136`), each fix seen failing first: unversioned saves refused
  once a character has a version, `::se/version` with no history (`ce54ec065`); removals stored
  (#295, `ffab1a216`); a blank HP box reverts (`8b7b02ec7`); branch changelog lines.
- Second opinion (Codex) reviewed the merge `d6596d1ce`: resolutions sound, Docker builds
  ledger.js. Its "Data View not owner-gated" finding is by design (KB who-row: the client shows
  "Only the owner can open this"; character data is public anyway).
- 2026-11-26: owner said open the PR. KB page moved here; browser suites run; PR opened (the open
  is the heavy Docker landmark, since the Dockerfile is in the diff; label `heavy-docker: later`
  after it), then CodeRabbit once the heavy run is green.

## Owner decisions

- 2026-11-25: finish this branch rather than port its server half; the #295 fix and the version-check
  fixes go on it. Blank HP box reverts. Data View ships without the drafts picker.

- 2026-10-16: the gear sits on the character page's share line and in the opened list row.
- 2026-10-16: #67 may auto-save a character cleaned when it is opened.
- 2026-10-25: drafts picker: a list, newest edit first, drafts open elsewhere included; a dropdown
  at the top once one is shown.
- 2026-10-21: layout B1 with indents; removals marked in place; save notice bottom on a phone, top
  on desktop; popups for Save and for race, class and ability-score removals.

## Gotchas for the next agent

- Builds: `lein fig:build` (app) and `lein fig:ledger` (Data View, advanced); restyle with
  `lein garden once`. Stop the e2e server before any lein build or test.
- Data View e2e scripts are in `test/e2e/` and need the seeded server
  (`DATOMIC_URL=datomic:mem://orcpub-e2e ... lein with-profile init-db run -m e2e-boot`).
