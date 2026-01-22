# Git Workflow Scripts

This directory contains scripts for managing a multi-branch workflow that prevents cross-contamination between feature work, testing infrastructure, and documentation.

## Quick Start

```bash
# 1. Set up worktrees (one-time, auto-runs in devcontainer)
./scripts/git/setup-worktrees.sh

# 2. Install git hooks (one-time, auto-runs in devcontainer)
./scripts/git/install-hooks.sh

# 3. Start a new feature (creates paired branches)
./scripts/git/start-feature.sh my-feature

# 4. Work in integrate/my-feature, route code to feature/my-feature
./scripts/git/route-commit.sh HEAD my-feature
```

## The Problem We're Solving

When working on an integration branch, you often produce different types of changes:

| Change Type | Should Go To |
|-------------|--------------|
| Bug fixes, features | `develop` |
| E2E tests, devcontainer, CI | `testing/develop` |
| Documentation, CLAUDE.md | `agents/develop` |

Without guardrails, these changes get mixed together, making it hard to:
- Keep branches clean
- Cherry-pick specific fixes
- Avoid corrupting hours of work

## How It Works

### Worktrees (Physical Isolation)

After running `setup-worktrees.sh`, you'll have:

```
/workspaces/
├── orcpub/                    # Your current branch (integration work)
├── orcpub-develop/            # develop branch
├── orcpub-testing/            # testing/develop branch
└── orcpub-agents/             # agents/develop branch
```

Each directory is a separate checkout. No branch switching needed.

### Git Hooks (Automatic Safety)

After running `install-hooks.sh`, the hooks automatically validate commits:

**On `testing/develop`:**
- ✅ Allows: `e2e/*`, `.devcontainer/*`, `test/*`, `.github/*`
- ❌ Blocks: Source code (`.clj`, `.cljs`, `.cljc`)

**On `agents/develop`:**
- ✅ Allows: `*.md`, `CLAUDE.md`, `.claude/*`, `agents/*`, `docs/*`
- ❌ Blocks: Test files, source code

**On `feature/*`, `integrate/*`:**
- ✅ Allows: Everything (this is where mixed work happens)

### Route Script (Move Commits)

When you need to move a commit to the right branch:

```bash
# Route last commit to develop
./scripts/git/route-commit.sh HEAD develop

# Route specific commit to testing
./scripts/git/route-commit.sh abc1234 testing

# Route last 3 commits to agents
./scripts/git/route-commit.sh HEAD~3..HEAD agents
```

## Dual-Branch Feature Workflow

The recommended workflow uses paired branches to keep PRs clean:

```
develop ──────────────────────> feature/my-feature (clean, for PR)
                                       ↑
                                       │ route-commit.sh
                                       │
agents/develop ──> integrate/my-feature (work here, has tooling)
```

### Starting a Feature

```bash
./scripts/git/start-feature.sh my-feature
# Creates: feature/my-feature (from develop, stays clean)
# Creates: integrate/my-feature (from agents/develop, for work)
```

You can specify a branch type: `feature`, `fix`, `bugfix`, `hotfix`, `patch`, `enhancement`

```bash
./scripts/git/start-feature.sh login-bug fix
# Creates: fix/login-bug, integrate/login-bug
```

### During Development

Work in `integrate/my-feature`. Route code commits to the clean branch:

```bash
# After committing code changes
./scripts/git/route-commit.sh HEAD my-feature
```

### Creating the PR

```bash
git checkout feature/my-feature
git push -u origin feature/my-feature
gh pr create --base develop
```

## For Agents (AI Assistants)

Agents work in `integrate/*` branches and are protected by hooks. If blocked:

```
✗ COMMIT BLOCKED

  File:   src/clj/orcpub/routes.clj
  Branch: agents/develop

  This branch only accepts:
    *.md, CLAUDE.md, .claude/*, agents/*, docs/*

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  HOW TO FIX
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Option 1: Unstage this file and commit the rest
    git reset HEAD src/clj/orcpub/routes.clj

  Option 2: Route this commit to the correct branch
    ./scripts/git/route-commit.sh HEAD develop

  Option 3: Switch to correct worktree
    cd ../orcpub-develop
```

### Agent Workflow Summary

1. **Work in `integrate/*` branches** - has CLAUDE.md and agent tooling
2. **Route code commits**: `./scripts/git/route-commit.sh HEAD <feature-name>`
3. **Let hooks catch mistakes** - they're automatic with clear guidance
4. **PR from clean branch** - `feature/*` is already clean, no prep needed

## Scripts Reference

### `setup-worktrees.sh`

Creates worktrees for all managed branches.

```bash
./scripts/git/setup-worktrees.sh           # Create worktrees
./scripts/git/setup-worktrees.sh --status  # Show status
./scripts/git/setup-worktrees.sh --remove  # Remove all worktrees
```

### `install-hooks.sh`

Configures git to use the hooks in `.githooks/`.

```bash
./scripts/git/install-hooks.sh

# To uninstall:
git config --unset core.hooksPath
```

### `route-commit.sh`

Cherry-picks commits to the appropriate branch (worktree or local).

```bash
./scripts/git/route-commit.sh <commit-or-range> <target>

# Worktree targets:
#   develop, testing, agents

# Feature targets (local branch):
#   <feature-name>  → routes to feature/<name>, fix/<name>, etc.
```

Examples:
```bash
./scripts/git/route-commit.sh HEAD develop        # → orcpub-develop worktree
./scripts/git/route-commit.sh HEAD testing        # → orcpub-testing worktree
./scripts/git/route-commit.sh HEAD my-feature     # → feature/my-feature branch
./scripts/git/route-commit.sh HEAD~3..HEAD agents # → orcpub-agents worktree
```

### `start-feature.sh`

Creates paired branches for clean PR workflow.

```bash
./scripts/git/start-feature.sh <name> [type]

# Types: feature (default), fix, bugfix, hotfix, patch, enhancement
```

Examples:
```bash
./scripts/git/start-feature.sh dark-mode           # feature/dark-mode + integrate/dark-mode
./scripts/git/start-feature.sh login-bug fix       # fix/login-bug + integrate/login-bug
./scripts/git/start-feature.sh perf enhancement   # enhancement/perf + integrate/perf
```

### `pull.sh` (root directory)

Interactive script to pull updates from multiple branches into your integration branch. Merges `testing/develop`, `agents/develop`, and a working branch of your choice.

```bash
./pull.sh                           # Interactive mode
./pull.sh testing/develop feature/x # With arguments
```

**Features:**
- Remembers your last selections (stored in `.integration-workflow-state`)
- Interactive branch selection with filtering and pagination
- Prefers local branches over remote (preserves unpushed commits)
- Explicit conflict detection with clear guidance
- Auto-resolves known conflicts (devcontainer.json, AGENTS.md)

**Typical usage:**
```bash
# On your integration branch
./pull.sh
# Select testing branch (default: testing/develop)
# Select working branch from menu
# Script merges all three sources
```

### `prepare-pr.sh`

Cleans agent files from a branch (alternative to dual-branch workflow).

```bash
# Full workflow: create clean branch from develop with cherry-picked commits
./scripts/git/prepare-pr.sh [source-branch] [target-branch]

# Quick strip: just remove agent files from current branch
./scripts/git/prepare-pr.sh --strip-only
```

The `--strip-only` flag is useful when you accidentally committed agent files and just want to remove them without creating a new branch.

## Branch Protection Rules

| Branch | Allowed Files | Purpose |
|--------|---------------|---------|
| `testing/develop` | `e2e/*`, `.devcontainer/*`, `test/*`, `.github/*`, `scripts/git/*` | Test infrastructure |
| `agents/develop` | `*.md`, `.claude/*`, `agents/*`, `docs/*` | Documentation, AI config |
| `develop` | Everything (with warning) | Main development |
| `feature/*`, `integrate/*` | Everything | Integration work |

## Troubleshooting

### "Worktree not found"

Run `./scripts/git/setup-worktrees.sh` to create missing worktrees.

### "Branch not found"

The target branch may not exist yet. Create it first:
```bash
git checkout -b testing/develop
git push -u origin testing/develop
```

### Hook not running

Ensure hooks are installed:
```bash
git config --get core.hooksPath  # Should show: .githooks
./scripts/git/install-hooks.sh   # Re-install if needed
```

### Conflict during cherry-pick

Resolve in the target worktree:
```bash
cd ../orcpub-develop
# Fix conflicts
git add .
git cherry-pick --continue
```

## Bash Script Best Practices

Lessons learned from developing these workflow scripts:

### State Persistence
Use `trap` to ensure state is saved on any exit (success, error, Ctrl+C):
```bash
trap save_state EXIT
```
Don't rely on end-of-script calls alone—they won't run on errors.

### Config File Security
Avoid `source` for user-writeable config files (security risk). Parse safely:
```bash
while IFS='=' read -r key val; do
  val="${val#\"}"  # Strip quotes
  val="${val%\"}"
  case "$key" in
    MY_VAR) MY_VAR="$val" ;;
  esac
done < "$CONFIG_FILE"
```

### Pre-Operation Checks
Before operations that could fail with dirty state (checkout, merge):
```bash
ensure_clean_worktree() {
  if [[ -n $(git status --porcelain) ]]; then
    echo "Working tree not clean. Commit or stash first."
    exit 1
  fi
}
```

### Local vs Remote Branches
When merging, prefer local branches (may have unpushed commits):
```bash
if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  git merge "$BRANCH"  # Local
else
  git fetch origin "$BRANCH"
  git merge origin/"$BRANCH"  # Remote fallback
fi
```

### Explicit Error Handling
Check return codes and give clear guidance:
```bash
if ! git merge origin/"$BRANCH"; then
  echo "Merge conflicts detected. Resolve and re-run."
fi
```
Avoid `|| true` which silently swallows errors.
