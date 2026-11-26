# test/browser-toolbox

**Purpose:** one place map of the app for browser tests, plus a census check, recipes, fixtures and one helper library, so a UI change costs one map edit instead of trial runs per suite. **Base:** `integration` at `b14a515e4` (2026-11-26).
**PR into:** `integration`, when the owner says. **KB:** [plan-browser-toolbox.md](../../docs/kb/plan-browser-toolbox.md).

## State (updated 2026-11-26)

- Done (pushed, `c7de2f67a`): first PR's work.
  - `tab-attrs` names the sheet's section tabs and the builder's tabs, and makes them work from the keyboard.
  - `test/browser/lib/toolbox.js` with its table test, run in the browser workflow.
  - `test/browser/places/` (builder, sheet, settings, weapons).
  - `weapons_table_e2e.js` moved onto them: 75/75 before and after, and its swallowed clicks are now checks.
- Payoff, shown with scratch edits:
  - the Combat icon swapped: the old suite FAILS after 75 s with "no .weapons-section", and the new one PASSES;
  - the label renamed: the new suite fails naming the tab, about 20 s into the run (the click timeout).
- Next: the PR into `integration` when the owner says. After it, the later steps in the plan (recipes, fixtures, census, more places).

## Owner decisions

- 2026-11-26: build it on this branch ("exactly what I've been pushing for").
- 2026-11-26: accessible names first; the few leftover hooks ship under a neutral attribute (proposed `data-part`), not `data-test*`. Plan, "Owner decisions".

## Open questions for the owner

- None.

## Gotchas for the next agent

- Playwright lives in `scripts/e2e/node_modules` (gitignored): run `npm install` there first in a new worktree. The cljs harness needs `NODE_PATH=$PWD/scripts/e2e/node_modules`.

- Read `test/browser/README.md` and the "Traps" list in the plan before touching the harness.
- Never two lein processes in one worktree: the census needs a server, so stop any REPL first.
