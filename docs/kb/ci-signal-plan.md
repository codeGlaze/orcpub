# CI that is worth listening to

*Started 2026-11-24 on `fix/class-picker-from-picks` (#97), after a day of failure emails.*

## The owner's rule

A red check must mean something needs fixing. Known issues are fixed or tracked, never left red
or quietly skipped: noise that people learn to ignore hides the alert that matters.

## What set it off

On 2026-11-24 every check on #97 failed in about three seconds. None ran: GitHub's annotation was
"The job was not started because your account is locked due to a billing issue." Each workflow
sent its own failure email. The owner cleared the lock the same day and the runs were restarted.

## The work, by state

| # | Item | State |
|---|------|-------|
| 1 | Billing lock cleared, #97's runs restarted | done |
| 2 | One clear signal when CI did not start (billing, outage) instead of a red test per workflow | not built: see below |
| 3 | The cheap guards run in the local pre-push hook, so their failures stop the push | done: pre-push already ran all four; the trailer pattern is now one script, `.githooks/agent-trailers`, run by the hook and by the PR check |
| 4a | Portrait art guard: ran on DMV's Gitea, which carries the art on purpose, and failed every push | fixed on #97: runs only on `codeGlaze/orcpub` and `Orcpub/orcpub` |
| 4b | Portrait art guard ran twice per PR push (push and pull_request events) | fixed on #97: a same-repo PR is covered by its push run; fork PRs still run |
| 4c | `page_load_budget_e2e.js` excused "KNOWN BUG, fix on #65" (artist credits fetched but not shown). #65 merged 2026-10-28 | fixed on #97: still there (5 pages failed without the allowance); credits now load only where a composed portrait is drawn, allowance removed |
| 6 | Lint warnings never failed anything (`--fail-level error`), so 83 piled up | done on #97: `scripts/lint-ratchet.py` fails CI on any warning not in `test/lint-baseline.txt`, and on a fixed one still listed, so the list only shrinks. 35 cleared the same day (all 27 real ones; dev scripts given `ns` forms); 48 shadowed names left |
| 5 | Every intentional hold is named, says why, and is switched off by a flag, not left failing | inventory below |

## 2. A clear signal when CI did not start

**Not built: it cannot work for the case that set it off.** A billing lock stops every workflow,
this one included, so it would stay silent exactly when it is needed. What does work: when a check
fails in seconds with no steps, read `check-runs/<id>/annotations` for the reason before anything
else. The original idea, kept for an outage that leaves Actions running:

A small workflow that runs when another workflow finishes (`workflow_run`). When the run failed
with no steps started, it posts one comment on the PR naming the cause from GitHub's annotation
("CI did not run: account billing lock"), and updates that comment rather than adding more.
It cannot stop GitHub's own per-workflow emails: those follow the account's notification settings
(Settings, Notifications, Actions). It makes the cause visible in one place on the PR.

## 3. Guards in the pre-push hook

The guards that read only the commits and file names (agent trailers, branch shape, personal data,
changelog house style) take seconds, need no build, and can run in `.githooks/pre-push`. A failure
then stops the push on the agent's side, before CI and before any email. Tests and builds stay in
CI. Each guard runs one script, called by both the hook and its workflow, so the two cannot drift.

## 5. Intentional holds

Something held back on purpose skips with a reason that names where it is tracked, so it shows as
"skipped: <reason>", never as a failure. Known so far:

- `Branch changelog folded`: skipped until the PR has the `ready to merge` label. Already right.
- DMV's page-load test fails by design: every page there loads AdSense and Matomo. DMV needs its
  own allowance listing those hosts (open question in DMV's `docs/ops/dmv-premerge.md`).
- `Submit Clojure dependencies`: skipped on PRs by design (it runs on pushes to the trunks).

Add each new hold here, with what lifts it.

## 6. The lint ratchet

`lein lint` fails only on errors. The CI step after it runs `scripts/lint-ratchet.py lint-output.txt
test/lint-baseline.txt`: a warning not in the baseline fails by name; a baseline line no longer
warned fails too, asking for its removal. After fixing warnings, `scripts/lint-ratchet.py --write
<lint output>` rewrites the baseline. Never add a line to hide a new warning.

The dev scripts without an `ns` form were all linted as one `user` namespace, so each one's
requires and defs counted as duplicates of the others'. Giving each script its own `ns` is the fix.
What is left (48, 2026-11-24): shadowed names, mostly a local called `name`, `key` or `val`. They are
being fixed on `perf/srd-spells-static` (owner, 2026-11-25).

**DMV has no CI, so run the ratchet by hand in its pre-merge.** Its fork files differ, so the shared
baseline is wrong there in both directions. The first run (2026-11-25, Gitea PR #10) found two new
warnings in DMV-only code and one baseline line DMV never had. All three were fixed on the pre-merge
branch, whose baseline is now DMV's own.

## 7. No job hangs (2026-11-26)

The owner's rule: a step that waits more than 5 to 10 minutes is a defect, never a flake to rerun. On PR #99 the Library security scan sat 2 h 48 min. `install-lein`'s `lein version` ran inside the project folder, so it resolved the project's plugins from Maven Central before the Maven cache was restored, with no timeout anywhere, and the job had none either (GitHub's default is 6 hours). The log ended with "Failed to read artifact descriptor" and the scan never ran; a rerun passed in 60 s. The fix (feature/character-rescue, `123d70bb9`):
- every job has `timeout-minutes` (10 for checks, 15 for the scan and dependency graph, 30 for Test & Lint, 20 for the release build, which takes about 3.5 minutes);
- `lein version` runs from `$RUNNER_TEMP` under `timeout 5m`;
- `lein deps` stops at 15 minutes and is tried once more.

A new job or waiting step gets its bound in the same commit.
