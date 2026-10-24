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

## Decided: no more rewrites (owner, 2026-10-24)

Old leaks stay in history: `integration` itself has 4 commits (2026-08-31 to 09-06) that touched
`CLAUDE.md` or `.claude/`, and these branches still carry the June summary and their own
`CLAUDE.md`. Rewriting them would cost far more than the harmless notes are worth. What matters
is that nothing NEW or RECENT leaks:

- Before one of these branches merges, delete the files in a normal commit, so `integration`'s
  tree stays clean. The history keeps them, as it already does.
- Branches still carrying them: `refactor/content-extensibility`, `feature/grant-rows`,
  `feature/extras-companions`, `srd52/develop`, `feature/fighting-style-authoring`,
  `feature/demo-content-tier`, `perf/memoize-audit`.
- `perf/entity-build` was rewritten before this decision (above). 20 of its 110 commits held
  no Claude files but got new SHAs from re-signing, so they no longer match the same commits on
  its relatives. Left as is.

## How (the perf/entity-build run, for reference)

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
