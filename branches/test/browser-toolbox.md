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
- Step 5 done (pushed, `039e26720`): roving tabindex, arrow/Home/End switch tab, tabpanel ids;
  sheet ids carry the character id (`sheet-tab-<id|new>-<title>`), since the character list can
  show several sheets. `tabs_keyboard_e2e.js` checks both tab sets and that no id repeats.
- Step 4b done (pushed, `e2da3723f`): console errors, page errors and same-origin failed requests
  are failed checks; `attempt`/`must` take a function (every caller converted); setup steps use
  `must`; caught query errors keep their text; `run.sh` stops on a missing ledger extern.
- Next: step 6 (place descriptors and the census), then 7-9 as in the plan.

## Owner decisions

- 2026-11-26: build it on this branch ("exactly what I've been pushing for").
- 2026-11-26: accessible names first; the few leftover hooks ship under a neutral attribute (proposed `data-part`), not `data-test*`. Plan, "Owner decisions".

## Open questions for the owner

- None.

## Gotchas for the next agent

- Playwright lives in `scripts/e2e/node_modules` (gitignored): run `npm install` there first in a new worktree. The cljs harness needs `NODE_PATH=$PWD/scripts/e2e/node_modules`.

- Read `test/browser/README.md` and the "Traps" list in the plan before touching the harness.
- Never two lein processes in one worktree: the census needs a server, so stop any REPL first.
- A tab's selection lands a frame after the key press (re-frame): wait for `aria-selected` before reading it.
- Data View suites need `ledger.js`; `run.sh` builds it only for suites with `// Needs: ledger`.
