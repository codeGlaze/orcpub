# test/browser-toolbox

**Purpose:** one place map of the app for browser tests, plus a census check, recipes, fixtures and one helper library, so a UI change costs one map edit instead of trial runs per suite. **Base:** `integration` at `b14a515e4` (2026-11-26).
**PR into:** `integration`, when the owner says. **KB:** [plan-browser-toolbox.md](../../docs/kb/plan-browser-toolbox.md).

## State (updated 2026-11-27)

One branch, one PR; each plan step is commits here (owner, 2026-11-27). Steps are numbered as in
the plan.

- Steps 1-3 done (`c7de2f67a`): `tab-attrs` on the sheet's and builder's tabs; `toolbox.js` and its
  table test; `test/browser/places/`; `weapons_table_e2e.js` moved onto them. Payoff shown with
  scratch edits (icon swapped: old suite fails after 75 s, new passes; label renamed: new suite names the tab).
- `integration` merged in with #99 (`519d1f23f`); one conflict in `views_test.cljs`, both sides'
  tests kept.
- Step 4 done (pushed, `b937cb159`): `escapeRegExp`; `attempt` and `must` in the checker (`must`
  ends a journey whose setup failed); no silent waits in the Weapons suite; `quietOverlays` warns;
  `watch(page)` collects console errors, page errors and failed requests, printed when a check
  failed; `run.sh` builds `ledger.js` for suites marked `Needs: ledger` (the five Data View suites).
- Step 5 (full tabs pattern) half done, in the worktree `/home/codeglaze/projects/orcpub-toolbox`:
  - committed, NOT pushed: `800314c25` (roving tabindex, Left/Right/Home/End switch tab, tabpanel
    ids, in `views.cljs` and `character_builder.cljs`);
  - NOT committed: `test/cljs/orcpub/dnd/e5/views_test.cljs` (+25 lines of table tests) and the new
    `test/browser/tabs_keyboard_e2e.js` (79 lines). Nothing has been run or seen failing yet.
  - Next: read the diff of `800314c25` and the two test files; check the aria-controls choice
    against the APG tabs pattern; fail-first (break the arrow-key hunk and the roving-tabindex hunk
    of `800314c25` by hand, see the tests fail, restore); commit the tests; then once: `fig:build` +
    cljs suite, full lint + ratchet, run.sh on `tabs_keyboard_e2e.js`, `weapons_table_e2e.js`,
    `spell_table_e2e.js`, `rest_fab_e2e.js`; push. Then 6 (descriptors and census), 7 (recipes, fixtures),
  8 (more places), 9 (KB page, README, `agent-setup.sh` pointer: that push needs the owner's yes).

## Owner decisions

- 2026-11-26: build it on this branch ("exactly what I've been pushing for").
- 2026-11-26: accessible names first; the few leftover hooks ship under a neutral attribute (proposed `data-part`), not `data-test*`. Plan, "Owner decisions".

## Open questions for the owner

- None.

## Gotchas for the next agent

- Playwright lives in `scripts/e2e/node_modules` (gitignored): run `npm install` there first in a new worktree. The cljs harness needs `NODE_PATH=$PWD/scripts/e2e/node_modules`.

- Read `test/browser/README.md` and the "Traps" list in the plan before touching the harness.
- Never two lein processes in one worktree: the census needs a server, so stop any REPL first.
- Data View suites need `ledger.js`; `run.sh` builds it only for suites with `// Needs: ledger`.
