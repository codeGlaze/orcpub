# Session Summary: Git Workflow Setup

**Date**: 2026-01-22
**Branch**: integrate/themes-nordic
**Status**: Complete - all workflow tools installed and tested

## What Was Done

1. Designed multi-branch workflow to prevent cross-contamination
2. Created git hooks for branch protection (pre-commit, pre-push)
3. Created worktree setup script for parallel branch development
4. Created dual-branch feature workflow (clean + integration branches)
5. Created route-commit script for cherry-picking between branches
6. Updated devcontainer to auto-install hooks and worktrees
7. Documented lessons learned in docs/GIT_WORKFLOW.md

## Key Design Decisions

### Three-Layer Protection
1. **Worktrees** - Physical isolation (separate directories per branch)
2. **Git Hooks** - Automatic safety (blocks wrong files per branch)
3. **Dual-Branch Workflow** - Clean PRs (feature/x + integrate/x)

### Absolute Paths for Hooks
Critical discovery: Relative `.githooks` path doesn't work in worktrees.
```bash
# Wrong - breaks in worktrees
git config core.hooksPath .githooks

# Correct - works everywhere
git config core.hooksPath "/workspaces/orcpub/.githooks"
```

### Branch Protection Rules

| Branch | Allowed Files | Blocked |
|--------|---------------|---------|
| `develop` | N/A | Direct pushes (use PR) |
| `testing/develop` | `e2e/*`, `.devcontainer/*`, `.github/*`, `scripts/git/*` | Source code |
| `agents/develop` | `*.md`, `.claude/*`, `docs/*` | Source code, tests |
| `feature/*`, `integrate/*` | Everything | Nothing |

## Files Created

| File | Purpose |
|------|---------|
| `scripts/git/setup-worktrees.sh` | Creates worktrees for develop, testing, agents |
| `scripts/git/install-hooks.sh` | Configures git to use .githooks/ |
| `scripts/git/route-commit.sh` | Cherry-picks commits to correct branch |
| `scripts/git/start-feature.sh` | Creates paired feature + integrate branches |
| `scripts/git/prepare-pr.sh` | Cleans agent files from branch |
| `scripts/git/README.md` | Full documentation |
| `.githooks/pre-commit` | Branch-aware file blocking |
| `.githooks/pre-push` | Final safety + blocks direct develop push |
| `docs/GIT_WORKFLOW.md` | Lessons learned documentation |

## Dual-Branch Workflow

```bash
# Start a feature
./scripts/git/start-feature.sh my-feature
# Creates: feature/my-feature (clean, from develop)
# Creates: integrate/my-feature (work branch, from agents/develop)

# Work in integrate/my-feature, route code commits
./scripts/git/route-commit.sh HEAD my-feature

# Create PR from clean branch
git checkout feature/my-feature
git push -u origin feature/my-feature
gh pr create --base develop
```

## Branch Type Prefixes Supported

`feature/`, `fix/`, `bugfix/`, `bug-fix/`, `hotfix/`, `hot-fix/`, `patch/`, `hotpatch/`, `hot-patch/`, `enhancement/`

## Worktree Layout

```
/workspaces/orcpub/          # Your working branch
/workspaces/orcpub-develop/  # develop
/workspaces/orcpub-testing/  # testing/develop
/workspaces/orcpub-agents/   # agents/develop
```

## Error Fixed

Reverted bad commit (580ab206) that deleted infrastructure files (.devcontainer, e2e, .github/workflows). Used `git revert 580ab206 --no-edit` after removing blocking state file.

## Quick Commands

```bash
# Start new feature with paired branches
./scripts/git/start-feature.sh add-feature

# Route commit to clean feature branch
./scripts/git/route-commit.sh HEAD add-feature

# Route commit to worktree
./scripts/git/route-commit.sh HEAD develop
./scripts/git/route-commit.sh HEAD testing
./scripts/git/route-commit.sh HEAD agents

# Check worktree status
./scripts/git/setup-worktrees.sh --status
```

## Alternative Approaches Discussed

### Symlink Approach (Not Adopted)
- Keep agent files in `agents/develop` worktree only
- Symlink CLAUDE.md and .claude/ into feature branches (gitignored)
- **Rejected because**: Risk of agents not finding symlinked files, IDE/platform issues

### Separate Repository (Not Adopted)
- Keep agent instructions in completely separate git repo
- **Rejected because**: Too much overhead for single project

### Key Insight: testing/develop vs agents/develop
- `testing/develop` also "pollutes" feature branches when merged
- But testing infrastructure is *part of the project* (legitimate)
- Agent instructions are *meta* (about how to work, not the work itself)
- This justifies treating them differently

### Final Decision
Stick with dual-branch workflow + `prepare-pr.sh` cleanup script for edge cases.

## prepare-pr.sh Capabilities

The existing cleanup script handles:
1. Cherry-picks non-agent commits from source branch
2. Skips agent-only commits automatically
3. Removes agent files that slipped through (CLAUDE.md, .claude/, flow*.md)
4. Creates cleanup commit if needed

Agent files removed:
- `CLAUDE.md`, `AGENTS.md`, `agents.md`
- `.claude/` directory
- `flow*.md` (workflow notes)

## Future Improvements

- [ ] Add `--strip-only` flag to prepare-pr.sh for quick cleanup without cherry-pick workflow
- [ ] GitHub Actions to validate branch contents on PR
- [ ] Script to sync worktrees after pulling
- [ ] Interactive mode for route-commit.sh to select commits
- [ ] VSCode tasks for common operations

## Session Continuation Notes

This summary was continued across multiple sessions due to context compaction.

**Key discussion points preserved:**
1. User explored keeping agent files outside git entirely
2. Symlink approach was considered but rejected (agent discovery risk, Windows issues)
3. Separate repo was considered but rejected (too much overhead)
4. Final decision: Keep dual-branch workflow, rely on prepare-pr.sh for cleanup
5. Critical insight: Testing infra is *part of project*, agent docs are *meta* - this justifies different treatment

## Remaining Work

Theme changes on `integrate/themes-nordic` (unrelated to workflow):
- Modified: `src/clj/orcpub/styles/core.clj`
- Deleted: `agents.md`
