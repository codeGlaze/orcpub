# Claude files in branch history

**Rule (owner, 2026-10-23):** `.claude/` and `CLAUDE.md` are tracked only on `agents/` branches.
Every other branch gitignores them, so they cannot reach `integration`'s history and from there
an upstream PR. `agent-setup.sh` copies them into each worktree untracked. Squash-merging is not
the fix: it throws away the history the owner wants kept. The fix is to rewrite the branch.

## What leaked

Two kinds of file, both agent notes. Measured 2026-10-23: no secrets, credentials, session links
or attribution lines in either.

- `.claude/summaries/2026-06-13-content-extensibility.md` (8.4 KB): one session summary, added
  in `385c07da` (2026-07-01) and inherited by every branch below that descends from
  `refactor/content-extensibility`. Names the session branch `claude/zen-wright-04xhdz`.
- `CLAUDE.md`: agent instructions, a different version on each branch that has one.

## Done

| Branch | When | Tip before | Tip after |
|---|---|---|---|
| `perf/entity-build` | 2026-10-23 | `999df1c6` (plus two of mine, dropped) | `678ea776` |

110 commits rewritten. Checked before pushing: final files equal the old tip's minus the two files,
none of the 110 commits contains either file, all are signed, messages, dates and authors
unchanged. KB citations of the old tip updated. A local backup stays in that session only.

## Still to do — decide branch by branch

These carry the files and would bring them into `integration` on merge. Leave out `claude/*` and
`ccr-*` (harness branches, remade anyway).

| Branch | Carries | Own commits | Last push (2026-10-23) |
|---|---|---|---|
| `refactor/content-extensibility` | summary | 294 | 3 weeks |
| `feature/grant-rows` | summary | 394 | 4 days |
| `feature/extras-companions` | summary | 311 | 10 days |
| `srd52/develop` | summary | 313 | 10 days |
| `feature/fighting-style-authoring` | summary, `CLAUDE.md` | 182 | 7 weeks |
| `feature/demo-content-tier` | summary | 58 | 7 weeks |
| `perf/memoize-audit` | `CLAUDE.md` | 14 | 7 weeks |

Rewriting one alone diverges its shared commits from its relatives: they then carry the same
changes under different SHAs. Before rewriting a branch, check that no session has unpushed work
on it, since its checkout breaks.

**Not to rewrite.** The `docs/*` branches belong on `agents/develop`, where the files are allowed.
`debug/develop-01`, `upgrade/datomic-pro`, `dmv/hotfix-integrations`, `feature/cross-platform-scripts`,
`refactor/data-extraction` and `refactor/views-extraction` are stale (last pushed February–April;
three share no history with `integration`), so they are deletion candidates, not rewrite ones.

## How (the perf/entity-build run)

```bash
git branch -f rewrite/X origin/X
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f \
  --index-filter 'git rm -r -q --cached --ignore-unmatch .claude CLAUDE.md' \
  --commit-filter 'git commit-tree -S "$@"' -- rewrite/X ^origin/integration
# verify: git diff --name-only origin/X rewrite/X  -> only the Claude files
#         no commit in rewrite/X ^origin/integration lists them; all signed
git push --force-with-lease=X:<old tip> origin rewrite/X:X
```

`^origin/integration` keeps `integration`'s own commits and signatures untouched. Then update KB
citations of the old SHAs (`git grep` the KB for each 8-character prefix).

## Not yet built

A pre-commit check on `agents/develop`'s hooks that refuses `.claude/` or `CLAUDE.md` on any
branch outside `agents/`. `git add -f` gets past `.gitignore`, which is how the settings file
reached `perf/entity-build` (2026-10-21). Owner to decide.
