# Git Workflow Scripts

This directory contains scripts for managing a multi-branch workflow that prevents cross-contamination between feature work, testing infrastructure, and documentation.

## Quick Start

```bash
# 1. Set up worktrees (one-time)
./scripts/git/setup-worktrees.sh

# 2. Install git hooks (one-time)
./scripts/git/install-hooks.sh

# 3. Work normally - hooks protect you automatically
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

## For Agents (AI Assistants)

Agents work normally but are protected by hooks. If blocked:

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

### Agent Workflow Recommendation

1. **Start sessions in the right worktree** when possible
2. **Let hooks catch mistakes** - they're automatic
3. **Use `route-commit.sh`** to fix blocked commits
4. **Ask the user** if unsure which branch is correct

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

Cherry-picks commits to the appropriate worktree.

```bash
./scripts/git/route-commit.sh <commit-or-range> <target>

# Targets: develop, testing, agents
```

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
