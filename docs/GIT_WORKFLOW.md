# Git workflow

How branches, PRs and the agent tooling fit together. The rules themselves are in `AGENTS.md`,
"Branches"; this page explains the moving parts.

## Branches

```
develop            upstream trunk; takes integration through the release PR (owner only)
  integration      active trunk; every feature, fix and hotfix PR lands here
    feature/* fix/* hotfix/* perf/* docs/* test/* upgrade/*   one branch per piece of work, cut from integration
    refactor/main  trunk of the refactor tree; pulls integration in
      refactor/*   its leaves: cut from refactor/main, PR back into it
agents/develop     agent home, off to the side: KB, docs, agent config, hooks, branch
                   handoffs, agent-facing research and measurement tooling. No app tests. Merges integration in for file:line accuracy; ships nothing
claude/*           harness auto-branches; never merged, re-homed onto a typed branch first
```

Fork-only files may live on our `develop` and are removed before any PR to `Orcpub/orcpub`
(list in `BRANCH.md` on agents/develop). `testing/develop` is retired; its agent-facing parts moved here.

## A piece of work, start to finish

1. Cut a typed branch from `integration`, with the owner's approval of its name.
2. Commit code, and every test that checks the app (unit, cljs, browser probes and hand-run
   regression scripts), there. KB findings, the branch handoff and research or measurement tooling
   go to `agents/develop` (`AGENTS.md`, branch table).
3. Before a merge or a PR: merge `integration` in, run the suites for what the branch changed and the
   full `lein lint` (`docs/TESTING.md`, "Before a PR"). Leave
   `docs/branch-changelog.md` unfolded while the PR is reviewed (its CI check stays red until the
   fold); fold it when the owner says the PR is about to merge (`AGENTS.md`, "Changelogs").
4. Open the PR into `integration` when the owner says. The merge gate is in `AGENTS.md`, "PRs and
   releases". Agents never merge unless the owner says to merge that PR.

## Recipes

The commands behind rules in `AGENTS.md`. The rules stay there; these are the how.

### Writing to agents/develop

From a code branch: one docs worktree per code branch, outside the code worktree, never by switching
the code branch's worktree. Name it after the branch (`/` as `-`) and reuse it in every session on
that branch. `--detach` works even when `agents/develop` is checked out elsewhere. A push that changes
`AGENTS.md`, `CLAUDE.md`, `.githooks/`, `scripts/agent-setup.sh` or `.claude/` needs the owner's yes
first (`AGENTS.md`).

```bash
D=../orcpub-agents-for-<your-branch>
git fetch -q --prune origin
git worktree add --detach "$D" origin/agents/develop       # first time on this branch
git -C "$D" status --porcelain                             # each later task: must print nothing;
                                                           # if not, an earlier task left edits: report them
git -C "$D" switch -q --detach origin/agents/develop       # then start from the fetched tip
# edit, then commit and push straight to it, no PR:
git -C "$D" add <files>
git -C "$D" commit -m "kb: ..."
git -C "$D" push origin HEAD:agents/develop
# refused because another agent pushed first?
#   git -C "$D" pull --no-rebase origin agents/develop
#   resolve any conflict and commit, then push again.
git worktree remove "$D"                                   # when the branch is merged or dropped
```

### Moving off a claude/* branch

- **Check what it was cut from:** `git fetch --prune origin`, then `git log --oneline origin/integration..HEAD` should list only
  commits made on this `claude/*` branch. If it lists others, it was not cut from `integration`:
  say so in Needs you.
- **On the owner's yes, if it was cut from `integration`:** `git switch -c <branch> && git push -u
  origin <branch>` (the new branch carries the `claude/*` commits), and continue there.
- **If it was cut from anywhere else:** ask in Needs you how to move the commits before switching.

### CodeRabbit

- **It never reviews on its own** (`.coderabbit.yaml`: `auto_review.enabled: false`, #73). It reads
  that file from the PR's own branch, so a branch cut before #73 merged keeps the older setting and
  is reviewed when its PR opens. Pause such a PR with `@coderabbitai pause` (a command, not a
  review); #63, #66, #69 and #70 were paused on 2026-10-25.
- **Asking:** a `@coderabbitai full review` comment on the PR. Plain `@coderabbitai review` has
  answered "Review failed" on PRs into `integration` (it worked only while automatic reviews were
  paused; checked 2026-10-16 on #64).
- **The allowance:** every agent pushes as codeGlaze, so all agents and the owner share one,
  refilled hourly (measured figures: `BRANCH.md`, "Merge gate and CodeRabbit limits"); there is no
  paid overage. A
  review replaced by a newer push still counts. A rate-limited request does nothing: wait out the
  reset, then ask once more.

### Subagents in a shared worktree

```bash
git status --porcelain --untracked-files=all   # at the start: keep this list
# EITHER the work is finished and verified: commit only your own, clean-at-start files
git add -- <your new files>
git commit -m "..." -- <your files>
# OR you hit your limit with the work unfinished: restore only files that were clean at the start
git checkout HEAD -- <files you changed that were clean>
rm <files you created>
```

Never both. A file that already had changes at the start is reported, never restored or committed.
The start list cannot see an edit the parent makes to a clean file after you started: if a file
you are about to restore has changes you did not make, report it instead. Never run `git checkout .`,
`git stash`, `git clean`, `git commit -a` or `git add -A` in a shared worktree: they wipe or take the
parent's edits.

### A branch renamed on GitHub

GitHub retargets open PRs and redirects the old name, but clones keep it. In each one:

```bash
git fetch --prune origin
git branch -m <old> <new> && git branch -u origin/<new> <new>   # only where <old> was checked out
```

Then move the branch's handoff: `branches/<old>.md` to `branches/<new>.md` on agents/develop.

### Merging integration in

```bash
git fetch origin && git merge --no-commit --no-ff origin/integration
# then the full lein lint and the "Before committing" checks (docs/TESTING.md), then:
git commit
```

Without `--no-commit`, a merge with no conflicts commits itself, with no checks and no pre-commit
hook. `--no-ff` stops a fast-forward from skipping the merge commit.

### Heavy Docker checks

About 20 minutes of out-of-memory and Swarm tests, not part of the general suite. The files that
count are the `paths:` list of `.github/workflows/docker-heavy.yml`; read the current list there (on a
branch without the file: `git show origin/feature/email-and-alerts:.github/workflows/docker-heavy.yml`).

- **When they run:** on a PR into `develop` or `integration` whose diff against its base touches
  any listed path, on every push and every label change, docs-only pushes included: GitHub matches
  the filter against the whole PR diff. Not on a refactor leaf's PR into `refactor/main`. A PR
  run uses the workflow from the PR merged into its base, so it runs once either side carries
  `docker-heavy.yml`: today only `feature/email-and-alerts`; every PR into `integration` once that
  merges, older branches included.
- **Pushes to `feature/email-and-alerts` itself:** its workflow has at times carried a temporary
  `push:` trigger with no file filter, so every push runs the checks, PR or not. Read its `on:` block
  before pushing there.
- **Pausing them:** the PR label `heavy-docker: later`. Batch pushes to keep runs few.
- **By hand:** `gh workflow run docker-heavy.yml --ref <branch>` (the pre-push hook prints it) works
  only once the workflow is on `develop`, the default branch.
- **When to ask the owner:** `AGENTS.md`, "Tests".

## Agent tooling on a code branch

Code branches carry no `CLAUDE.md` and gitignore `.claude/`. `scripts/agent-setup.sh` (run by the
SessionStart hook, or `git show origin/agents/develop:scripts/agent-setup.sh | bash`) copies the
tooling from `agents/develop` into gitignored paths, so it can never reach a PR; generates
`CLAUDE.md` with `AGENTS.md`, `BRANCH.md` and the branch's handoff (`branches/<branch>.md`) inlined;
drops plain copies of `AGENTS.md` and `BRANCH.md` for agents that read those instead; and arms the
hooks. Integration's `.gitignore` covers all four, and other AI tools' agent files, since #72; on a branch
cut before that, the script ignores the ones missing in the clone's `.git/info/exclude`. `--check` verifies without changing
anything. The KB stays on `agents/develop`: read it with `git show origin/agents/develop:<path>`.

## Hooks

Versioned in `.githooks/` on `agents/develop`, armed through `core.hooksPath` (`docs/kb/agent-hooks.md`).

| Hook | Does |
|------|------|
| `commit-msg` | strips agent attribution: `Co-Authored-By: Claude` and `Claude-Session:` trailers, and "Generated with Claude Code" lines |
| `pre-commit` | blocks a commit that adds or changes `CLAUDE.md`, `AGENTS.md`, `BRANCH.md` or `.claude/` on a code branch (a forced `git add -f` gets past `.gitignore`, not this); blocks a commit that adds a function without a docstring; warns on history wording in comments; on a merge commit, checks only functions new to both sides; on `agents/develop`, when `docs/` changes, checks doc links, the develop superset and the KB lint (headings, every page reachable, `topic-index.md` current), and when an SRD check script changes, runs its self-test |
| `pre-push` | refuses to push an unfolded `docs/branch-changelog.md` onto `integration`, `develop` or `main`; when the branch changes deployment files and `docker-heavy.yml` exists, prints the heavy-Docker heads-up and the command to run them; on a push onto those branches, also refuses a `CHANGELOG.md` that fails `scripts/lint-changelog.py` |

On a merge commit the pre-commit docstring check counts a function as new only if it is new to both
sides, so the other side's functions pass and ones written while resolving are checked. Agents never
use `--no-verify` without the owner's yes for that commit or push: it skips every hook, including the
session-URL stripper in `commit-msg` and the agent-file guard.

## History

Until 2026-10 this page described a dual-branch system (paired `integrate/*` and `feature/*`
branches, `route-commit.sh`, `pull.sh`, `testing/develop`). Those scripts are gone and `testing/develop` is retired (it still exists, unused);
the old page is in git history (`git show 2e895a7f8:docs/GIT_WORKFLOW.md`).
