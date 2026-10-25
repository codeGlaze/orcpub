# feature/character-rescue

**Purpose:** nothing stored on a character is ever trapped. Data View (internally "the ledger"):
a standalone page, its own script (`ledger.js`), that lists a saved character's or this tab's
draft's stored choices for its owner, with Remove, Undo, Save and Copy for support. Plus the server
version check that refuses a save made from an older copy. **Base:** `integration`.
**PR into:** `integration`, when the owner says. **KB:** `docs/kb/character-rescue.md`, on this
branch by the owner's exception; it moves to agents/develop before the PR.

## State (updated 2026-10-25)

- Done: Data View and its entry points (gear, Missing Content warning, won't-load panel, rescue
  notice), the support email's repair link and stored choices, the version check (409 on a stale
  save), unreadable drafts kept, the redesign (cards, in-place removals, popups), integration
  merged in with per-character drafts.
- Done 2026-10-25: the drafts picker (a tab with no draft of its own lists the drafts; ux-guide.md
  log). Not pushed yet.
- Next: the PR: merge integration, full suites, move the KB page, CodeRabbit; fold the changelog
  when the owner says it is about to merge.
- Later, needs the owner: key editing behind dev mode (mockup first); Data View script size.

## Owner decisions

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
