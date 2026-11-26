# test/browser-toolbox

**Purpose:** one place map of the app for browser tests, plus a census check, recipes, fixtures and one helper library, so a UI change costs one map edit instead of trial runs per suite. **Base:** `integration` at `b14a515e4` (2026-11-26).
**PR into:** `integration`, when the owner says. **KB:** [plan-browser-toolbox.md](../../docs/kb/plan-browser-toolbox.md).

## State (updated 2026-11-26)

- Done: branch created; plan written.
- In progress: nothing.
- Next: first PR (plan 1.2): `toolbox.js`, accessible names, `weapons_table_e2e.js` moved onto the map.

## Owner decisions

- 2026-11-26: build it on this branch ("exactly what I've been pushing for").
- 2026-11-26: accessible names first; the few leftover hooks ship under a neutral attribute (proposed `data-part`), not `data-test*`. Plan, "Owner decisions".

## Open questions for the owner

- None.

## Gotchas for the next agent

- Read `test/browser/README.md` and the "Traps" list in the plan before touching the harness.
- Never two lein processes in one worktree: the census needs a server, so stop any REPL first.
