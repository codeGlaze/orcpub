# test/page-load-budget

**Purpose:** pages load only what they show: self-hosted Open Sans, cache-stamped assets that
survive deploys, and `test/browser/page_load_budget_e2e.js`, which fails on any request a page's
allowance does not list. **Base:** `integration`. **PR into:** `integration` (#66).
**Then:** #70 (`feature/registration-in-boot-report`), below.

## State (updated 2026-10-27)

- Done: CodeRabbit reviewed the PR's head (`660261aa0`, 2026-10-17), and every thread is resolved.
- Done, **committed locally, not pushed:** `integration` merged in as `a841b4d3c`, in the worktree
  `/home/codeglaze/projects/orcpub-loadbudget` (branch `test/page-load-budget`, ahead of origin).
  Conflicts were resolved like this:
  - `system.clj`: all three interceptors kept (static caching, portrait guard, client address);
    client address stays first.
  - `index.clj`: integration's hiccup 2 `h/raw`, with this branch's stamped asset links and no
    Google Fonts link. The self-hosted faces cover weights 400 to 700.
  - `CHANGELOG.md`: both sides kept.
- Checks on the merge: `lein test` 1264 passed, cljs suite 828 passed, full `lein lint` 0 errors.
- **`page_load_budget_e2e.js` fails 10 of 106**, the same on two runs (not flaky), on what
  integration added after 2026-10-17. Logs: run
  `./scripts/e2e/run.sh test/browser/page_load_budget_e2e.js > /tmp/pl.log 2>&1; grep FAIL /tmp/pl.log`.

## Next, in order

1. **Bundle ceiling.** Replace the 842 KB budget (`BUNDLE_GZIP_BUDGET`) with a hard ceiling of
   **900 KB** compressed (owner, below). Print the size and the headroom on every run, pass or fail.
   The bundle is 870 KB today.
2. **Double logo.** The builder requests `GET /image/orcpub-logo.svg` twice, signed in and out
   (2 failures). Find why the two uses are not shared (probably two different URLs, or one fetched
   by script), fix it on this branch, and see the check pass.
3. **Allowances** (legitimate requests; add each with a one-line reason):
   - my account: `GET /user` twice. The start-up check (`:verify-user-session`) and the page's own
     `:load-account`, which wants fresh account details.
   - my content: `xhr GET /dnd/5e/items`. The page shows the player's homebrew items.
4. **Known bug, not fixed here:** `xhr GET /dnd/5e/portrait-artists` on the builder, character list
   and sheet (5 failures). They fetch the credits without showing them. The fix is on
   `feature/artist-pages` (#65, its own agent is on it). Allow it as a known bug that names #65,
   per AGENTS.md rule 6, so that agent removes the allowance when the fix lands.
5. Run the budget test (expect all pass), `lein test`, the cljs suite and lint once. Then push,
   add the `ready to merge` label (REST:
   `gh api -X POST repos/codeGlaze/orcpub/issues/66/labels -f 'labels[]=ready to merge'`),
   wait for CI, and merge with `--match-head-commit`. No new CodeRabbit review is needed (owner,
   below). The branch has no `docs/branch-changelog.md`: it writes `CHANGELOG.md` directly.
6. **Then #70** (`feature/registration-in-boot-report`). Merge `integration` in. It conflicts in
   `config.clj`, `config_report_test.clj` and `CHANGELOG.md` with #75's startup report.
   - #70 makes the `ALLOW_UNVERIFIED_REGISTRATION` row's value the outcome (verified by email /
     REFUSED / OPEN / inert flag) and removes the startup printlns in `routes.clj`
     (`report-registration-mode!`).
   - Since #75, a server without `EMAIL_SERVER_URL` refuses to start unless `EMAIL_REQUIRED=false`
     (`config/start-refusal`). So "no mail server" signup states occur only with that opt-out:
     word the row's note and `docs/email-system.md` to match.
   - Tests fail first. Push, then request **one** `@coderabbitai full review` (the owner is
     saving the credit for this). Fix its findings, then merge as above.

## Owner decisions

- 2026-10-27: the bundle stays under **900 KB compressed** (100 KB under a megabyte). A hard
  ceiling, not a per-branch growth rule. Trimming the bundle is a "someday" item in BRANCH.md.
- 2026-10-27: fonts stay self-hosted. The browser downloads only the character-set files a page's
  text needs (`unicode-range`), about 48 KB for an English page. Google links would send visitors'
  IPs to Google and make tests differ from production.
- 2026-10-27: no new CodeRabbit review for #66; one for #70 after its integration merge.

## Gotchas for the next agent

- `/home/codeglaze/projects/orcpub-pdf` is a **different branch** (`fix/pdf-render-and-efficiency`).
  Check `git branch --show-current` before running anything in a worktree.
- CI does not run `page_load_budget_e2e.js`; only a hand run shows its failures.
- `gh pr edit --add-label` fails on a Projects (classic) deprecation error: label through the
  REST call above.
