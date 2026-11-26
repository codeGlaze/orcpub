# feature/character-rescue

**Purpose:** nothing stored on a character is ever trapped. Data View (internally "the ledger"):
a standalone page, its own script (`ledger.js`), that lists a saved character's or this tab's
draft's stored choices for its owner, with Remove, Undo, Save and Copy for support. Plus the server
version check that refuses a save made from an older copy. **Base:** `integration`.
**PR into:** `integration`, when the owner says. **KB:** `docs/kb/character-rescue.md`, on this
branch by the owner's exception; it moves to agents/develop before the PR.

## State (updated 2026-11-25)

- Done: Data View and its entry points, the support email's repair link, the version check (409 on
  a stale save), unreadable drafts kept, the redesign. `integration` merged in on 2026-11-25
  (`d6596d1ce`, pushed): the Data View script now builds with cljs.main (`lein cljs:ledger`), like
  the app.
- Not on the branch: the Data View drafts picker (handoff of 2026-10-25 said "not pushed"; the code
  is lost). Owner, 2026-11-25: ship without it, add it later.
- Next, in order (owner go, 2026-11-25), each its own commit with tests seen failing first:
  1. version check: an unversioned save is accepted only when the stored character is unversioned
     too (today a stale unversioned save is retried and can overwrite newer data); a refused save
     applies nothing; `::se/version` gets `:db/noHistory true`.
  2. the save removes what a save left out (#295): for each entity the incoming tree keeps, retract
     the attributes and cardinality-many values it lacks; skip refs and the server-set owner and
     tags. Tested scope: `docs/kb/tracked-issues.md`, #295.
  3. the HP box left blank goes back to its previous number; 0 must be typed (owner, 2026-11-25).
  4. tests: two tabs (second refused), an old unversioned copy refused, HP/XP/one slot removals,
     the already-working removals, an unrelated save keeps equipment, charges, portrait and notes,
     a rest then a level up shows the new max.
  5. then the PR: move the KB page, full suites, `heavy-docker: later` label (the Dockerfile is in
     the diff), CodeRabbit.

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
