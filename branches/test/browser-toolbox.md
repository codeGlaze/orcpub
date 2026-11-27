# test/browser-toolbox

**Purpose:** one place map of the app for browser tests, plus a census check, recipes, fixtures and one helper library, so a UI change costs one map edit instead of trial runs per suite. Also, since 2026-11-27, the shared-controls merge (step 5c), because the map locates those controls. **Base:** `integration` at `b14a515e4` (2026-11-26), `integration` merged in at `519d1f23f` (#99).
**PR into:** `integration`, when the owner says. **Worktree:** `/home/codeglaze/projects/orcpub-toolbox`.
**KB:** [plan-browser-toolbox.md](../../docs/kb/plan-browser-toolbox.md) (the steps); the controls audit is in [monolith-decomposition-plan.md](../../docs/kb/monolith-decomposition-plan.md), "Shared controls and duplicates".

## State (updated 2026-11-27)

One branch, one PR; each plan step is commits here (owner, 2026-11-27). Steps are numbered as in the plan. All below is pushed unless it says otherwise.

- Steps 1-3 (`c7de2f67a`): `toolbox.js` and its table test; `test/browser/places/` (builder, sheet, settings, weapons); `weapons_table_e2e.js` moved onto them.
- Step 4 (`b937cb159`) and 4b (`e2da3723f`): `escapeRegExp`; `checker()` gives `check`, `attempt`, `must`, `watch`, `finish`; `must` throws `SetupFailed` and the suite's top-level catch ends through `finish()`; `attempt`/`must` take a function (`() => locator.click()`); `watch(page)` makes console errors, page errors and same-origin failed requests failed checks; `run.sh` builds `ledger.js` for suites with `// Needs: ledger`.
- Step 5 (`039e26720`): full tabs pattern (roving tabindex, arrows/Home/End, tabpanel ids scoped by character id); `tabs_keyboard_e2e.js`.
- Step 5b (`eb9746f4b`): `switch-attrs`, `tab-attrs`, `tab-key-target`, `step-tab!`, `tab-panel-attrs` live in `orcpub.components` (`src/cljc/orcpub/components.cljc`); `tab-attrs` is `[selected? label]` / `[selected? label {:id :panel-id}]` and uses `press-to-click`.
- Step 5c item 1 (one checkbox): done (`592468dab`). `comps/labeled-checkbox` takes an optional 5th
  arg, a row class; the 16 former builder_fields callers pass `"m-b-10 f-s-14"` and lost their
  wrapper divs; the builder_fields copy is gone; `option-checkbox` is rebuilt on it. Measured
  before/after on the item builder and the PDF options panel at 1920 and phone: 172 rows, 0 px
  difference (script in the old chat's scratchpad, not kept: re-measure with a bounding-box loop
  over `.checkbox` rows). The phone PDF options panel was not reached (Export is hidden at phone
  width). New: `builder_checkboxes_e2e.js`, `places/item_builder.js`.
- **Open, fix first (a failing suite is never "pre-existing"):** `test/browser/spell_layout_pdf_e2e.js`
  fails 2 of 24 the same way before and after item 1 (twice, so not flaky): "an emblem picker for
  each casting class: 0 found", then a 30 s timeout waiting for a "Change" span after clicking
  "Print Spellbook". Cause unknown. That suite reads `ORCPUB_E2E_URL`, not `E2E_PORT` (set both, or
  move it onto `toolbox.js`'s `BASE`). Check whether it passes on `origin/integration` before
  blaming this branch.
- **Next:** that fix, then 5c items 2-5 (plan), then 6 (place descriptors and the census), 7-9.

## Owner decisions

- 2026-11-26: build it on this branch; accessible names first; leftover hooks ship as `data-part` via one helper, never `data-test*`.
- 2026-11-27: one branch, one PR; steps are commits, never a series of PRs ("commits are where stepped progress goes... not PRs").
- 2026-11-27: one branch per chat. The shared-controls merge goes on this branch, not a `refactor/*` one, because it touches what the place map locates.
- 2026-11-27: merge duplicate controls and reuse as much as possible as part of the split.
- 2026-11-27: token burn is a problem; small items must stay cheap (next section).

## How to work this branch cheaply (measured 2026-11-27)

- **Do small, mechanical items in the chat**, not in a subagent: every subagent read the generated `CLAUDE.md` (67 KB, about 17k tokens) before doing anything, eight times in one day, for jobs of a few dozen lines.
- **If you brief a subagent, do not tell it to read `CLAUDE.md`.** Paste the rules it needs (no `git add -A`/stash/`checkout .`, commit with `git commit -- <paths>`, one lein process at a time, never filter security warnings, see a test fail before committing it) and the exact commands.
- **Checks per item: only the narrow one.** `node test/browser/lib/toolbox_test.js` (seconds); the one browser suite that touches the control (`./scripts/e2e/run.sh <suite>`, `E2E_PORT=8893`, one suite per call); a cljs change needs `lein fig:build` plus the harness (`lein fig:test`, then `NODE_PATH=$PWD/scripts/e2e/node_modules node test/e2e/cljs-harness.js`). The full set (`lein test`, full `lein lint` + `python3 scripts/lint-ratchet.py <lint-output>`, cljs suite) runs **once per batch**, before the push, not once per item.
- **Fail-first can share a build:** batch several breaks into one `fig:build` and one run, one break per claim.
- **Line counts:** `git show origin/agents/develop:scripts/line-counts.sh | bash -s -- <base> <head>`, never by hand.

## Gotchas for the next agent

- Playwright lives in `scripts/e2e/node_modules` (gitignored): `npm install` there first in a new worktree.
- `run.sh` reads `// Needs:` headers: `dev bundle` (the suite reads app internals via `dbAt`), `ledger` (Data View's `ledger.js`), `busy server`, `pack argument`, `fresh server`. Trust only its `E2E RESULT` line.
- A tab's selection lands a frame after the key press (re-frame): wait for `aria-selected` before reading it, and make that wait a recorded check.
- The two checkbox controls differed visibly: the builder one had `m-b-10` and a 14px label; `comps/labeled-checkbox` has neither. Measure bounding boxes before and after any control merge, at phone (`phoneContext`) and 1920 px.
- A mechanical regex rewrite of calls (`() => ` insertion) doubled existing arrows through `\s*` backtracking past a lookahead: grep for the doubled form after any such rewrite.
- `components.cljc` is shared with the JVM: DOM calls go under `#?(:cljs ...)`.
- Second opinions (Codex) read cached raw GitHub files and cannot run git: push first, and check each claim against the tree before acting (one review called `must` unexported from a stale copy).
- Never two lein processes in one worktree; `run.sh` boots one.
