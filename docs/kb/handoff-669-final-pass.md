# Handoff: final pass on the #669 merge, before dev-server integration

For the agent doing the last testing pass. **Read this before running anything** — roughly half of
what a thorough pass would cover has already been run, and four of the traps below each cost a cycle
to find.

Everything here is from real runs on 2026-09-13 against `integration` `36766010` merged with
`claude/fix-custom-items-disappearing-DW8rb` `509431f2`. Full detail:
[plan-669-merge-verification.md](plan-669-merge-verification.md).

## What the change is

Five separable units. **Only P1 fixes something broken** — the rest are a refactor, so they must be
proven to change *nothing*, which is a different kind of testing.

| | unit | what to prove |
|---|---|---|
| **P1** | `filtered-items`/`filtered-spells` become reactive | that it FIXES — the list must update |
| **P2** | `get-auth-token` moves to `event_utils.cljc` | that nothing changed |
| **P3** | `console.warn` on the custom-items 401 | that nothing changed |
| **P4** | three unreachable subs `#_`-discarded | that nothing changed |
| **P5** | five API subs onto one `reg-api-sub` HOF | that nothing changed ← **the risk** |

## Already verified — do not spend time re-running

| | result |
|---|---|
| Merge | 3 conflict hunks, 2 files. `integration` has never touched the defect region |
| cljs suite, merged tree | **373 tests / 1741 assertions, 0 failures, 0 errors** |
| Tests actually bite | defect reinstated → **6 targeted failures**, including the share-overlay one. Fix restored → green |
| P5 request path | **4 runs** (2 accounts × 2 trees) — byte-identical traces |
| P5 response path | seeded items returned by the API *and* rendered, both accounts |
| Isolation | neither account sees the other's items, both directions |
| P4 reachability | `clj-grep --live`: **zero** live subscribers to the discarded keys, on any branch |

## What is NOT covered — this is your job

1. **The M1 hand check.** Open My Items (`/pages/dnd/5e/magic-items`), type 3+ characters into the
   filter, delete them, save a new item, confirm it appears **without a reload**. This is the only
   proof the user-visible bug is gone, and no automated test replaces it.
2. **401 / expired-token paths.** `user-sub-on-401`'s compound behaviour (clear login state, and
   bounce only when `required?`) is covered by a unit test only. Nothing exercises it end to end.
   **Watch specifically:** `::mi5e/custom-items` must stay SILENT on a 401 — it deliberately does not
   `:route-to-login`, because that races `:verify-user-session` and causes a login loop.
3. **The share-link flow end to end.** The cljs tests cover the overlay; no browser test creates a
   share link and opens it. `integration` gained link-embedded sharing while this branch sat, and
   shared items now flow into the same filtered list P1 fixes.
4. **`test/browser/*_e2e.js`.** Neither suite runs them. `node scripts/test/run-browser-probes.js`.
5. **The JVM suite.** `lein test` was not run here at all.

## Traps — each of these cost a cycle

**The `subs.cljs` conflict is not a line union.** It looks like one. Resolving it line-wise yields
two `(:require` forms and a broken `ns`. Union the *refer vector*: keep the branch's `[re-frame.db]`
and add integration's `reg-event-db` to the existing `:refer` list.

**Check `test_runner.cljs` after resolving it.** Its two conflicts are the namespace list and the
`run-tests` list. Resolve them wrong and tests silently drop out of the run — **a green result then
means nothing**. Count the namespaces against both parents before believing a pass.

**`#_` is not a comment.** P4 discards three subs with it, so they grep as live code. A plain `grep`
reported five live references that do not exist. Use `scripts/clj-grep.py`.

**Custom items are not on `/my-content`.** That is the homebrew-sources page. They render on
**`/pages/dnd/5e/magic-items`**. Asserting against the wrong one reports "not rendered" for items
that are in the API response the whole time.

**`SUMMARY: (none)` from the cljs harness is a harness fault, not a pass.** Mode B
(`runner-all.html`) never emits the totals line — it renders to the DOM and times out after 240s.
Use mode A (`runner.html` → `js/test.js`). And `lein fig:test` does **not** emit the runner HTML;
create it by hand. Recipe: [cljs-headless-harness.md](cljs-headless-harness.md).

**`cljs.test` prints nothing for a passing test.** Zero grep hits for your new test's name means it
passed, not that it failed to run. To confirm a test is wired in, break it or reinstate the defect.

**`scripts/e2e/run.sh` resolves `require('playwright')` from the repo ROOT**, not from
`scripts/e2e/` where its `package.json` lives.

## Commands that work

```bash
# toolchain (lein is not preinstalled; this works through the proxy)
mkdir -p ~/bin && curl -sS -o ~/bin/lein \
  https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
chmod +x ~/bin/lein && export PATH="$HOME/bin:$PATH" && lein deps

# cljs suite  (~30s compile)
lein fig:test
printf '<!doctype html><meta charset="utf-8"><body><script src="js/test.js"></script></body>\n' \
  > target/test/runner.html
git show origin/agents/develop:test/e2e/cljs-harness.js > test/e2e/cljs-harness.js   # not on integration
# then drive runner.html with Playwright — see cljs-headless-harness.md

# real app + real DB + a logged-in session  (~10 min first build)
npm install playwright --no-save          # from the repo ROOT
./scripts/e2e/run.sh api-sub-loaders.js                       # as kaylee
E2E_USER=zoe E2E_PASS=washburne7 ./scripts/e2e/run.sh api-sub-loaders.js
SELFTEST=1 ./scripts/e2e/run.sh api-sub-loaders.js            # must FAIL to be trusted
```

**Seeded accounts** (`dev/e2e_boot.clj`, one JVM — a `mem://` db only exists in the process that
created it, so `scripts/create_dummy_user.sh` cannot help):
`kaylee`/`serenity99` owns three items, `zoe`/`washburne7` owns one.
Login recipe and its four gotchas: [e2e-logged-in-sessions.md](e2e-logged-in-sessions.md).

## Pass criteria

- cljs suite green **and** the namespace count in `test_runner.cljs` matches both parents;
- `api-sub-loaders.js` passes as both accounts, and `SELFTEST=1` **fails**;
- M1 by hand;
- no login loop when a session goes stale on the My Items page.

## If something fails

**Bisect by unit, not by commit** — the table at the top maps units to what they touch. Two specific
first suspects:

- list empty / wrong → check `::party5e/parties`, whose `:db-key` is deliberately `::char5e/parties`
  (historical naming, flagged in a comment at the call site). "Tidying" that line is the most likely
  self-inflicted break in the merge.
- login loop → `::mi5e/custom-items` lost its `:on-401`; omitting it inherits the `:route-to-login`
  default.

**If M1 fails**, stop rather than assuming the fix is wrong. The defect was verified by reading and
by unit test, not by watching the UI — a second staleness layer between the sub and the view would
look identical. Capture `db` and the sub's value at that moment first.

## Two things that are not this merge's job

- **`GET /dnd/5e/items/:id` is public** — `routes.clj:1941-1944` registers it with no `check-auth`
  while DELETE on the same path has one. It returns any user's custom item by db-id and has no
  client consumer. P4 removes the dead client chain and leaves the endpoint untouched. Do not report
  it as fixed, and do not treat it as a merge blocker; it is a product decision.
- **P4's 77-line comment block** belongs in the KB rather than in `equipment_subs.cljs`, and its
  framing ("groundwork for cross-user item viewing") was overtaken by link-embedded sharing. Cosmetic,
  zero runtime effect, fine as a follow-up.
