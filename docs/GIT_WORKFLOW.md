# Git Workflow: Lessons Learned & Implementation

This document captures the design decisions and lessons learned while implementing a multi-branch workflow for managing feature development, testing infrastructure, and documentation.

## The Problem

When working on feature branches, different types of changes get mixed together:
- **Code changes** → belong in `develop`
- **Test infrastructure** (E2E, devcontainer, CI) → belong in `testing/develop`
- **Documentation** (CLAUDE.md, agent config) → belong in `agents/develop`

Without guardrails:
- Hours of work can be corrupted by accidental commits
- Cherry-picking specific fixes becomes tedious
- PRs get polluted with unrelated files

### Additional Challenge: Agent Tooling

Feature branches created from `agents/develop` start "dirty" with agent files (CLAUDE.md, etc.). These need to be removed before PR, but agents need them during development.

## Solution Architecture

### Three-Layer Protection

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Worktrees (Physical Isolation)                    │
│  - Separate directories for each branch                     │
│  - No accidental branch switching                           │
│  - Can work on multiple branches simultaneously             │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: Git Hooks (Automatic Safety)                      │
│  - pre-commit: Blocks wrong file types per branch           │
│  - pre-push: Final check + blocks direct push to develop    │
│  - Agent-friendly error messages with fix instructions      │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: Dual-Branch Workflow (Clean PRs)                  │
│  - feature/x from develop (stays clean)                     │
│  - integrate/x from agents/develop (has tooling)            │
│  - route-commit.sh moves code between them                  │
└─────────────────────────────────────────────────────────────┘
```

### Branch Structure

```
develop                    # Main code branch (PRs only)
├── testing/develop        # E2E, devcontainer, CI, scripts/git
├── agents/develop         # CLAUDE.md, .claude/, docs/*.md
└── feature/* or fix/*     # Clean feature branches for PR
    └── integrate/*        # Work branches with agent tooling
```

## Key Design Decisions

### 1. Absolute Paths for Hooks

**Problem**: Relative `.githooks` path doesn't work in worktrees (they don't have the directory).

**Solution**: `install-hooks.sh` uses absolute path:
```bash
git config core.hooksPath "/workspaces/orcpub/.githooks"
```

### 2. Agent-Friendly Error Messages

**Problem**: Agents need clear, actionable guidance when blocked.

**Solution**: Error messages include:
- What file was blocked
- What branch you're on
- What files ARE allowed on this branch
- Multiple fix options with exact commands

```
✗ COMMIT BLOCKED

  File:   src/clj/orcpub/routes.clj
  Branch: agents/develop

  This branch only accepts:
    *.md, CLAUDE.md, .claude/*, agents/*, docs/*

  HOW TO FIX:
  Option 1: git reset HEAD src/clj/orcpub/routes.clj
  Option 2: ./scripts/git/route-commit.sh HEAD develop
  Option 3: cd ../orcpub-develop
```

### 3. Dual-Branch Feature Workflow

**Problem**: Agents need tooling during development, but PRs must be clean.

**Solution**: Two branches per feature:
- `feature/x` - from `develop`, stays clean, used for PR
- `integrate/x` - from `agents/develop`, has CLAUDE.md, used for work

Code commits are routed from integrate to feature using `route-commit.sh`.

### 4. Multiple Branch Type Prefixes

**Problem**: Not all work is "features" - fixes, patches, enhancements.

**Solution**: `start-feature.sh` and `route-commit.sh` support:
- `feature/`, `fix/`, `bugfix/`, `bug-fix/`
- `hotfix/`, `hot-fix/`, `patch/`, `hotpatch/`, `hot-patch/`
- `enhancement/`

### 5. Devcontainer Auto-Setup

**Problem**: Contributors shouldn't need to manually install hooks.

**Solution**: `.devcontainer/setup.sh` automatically:
1. Installs hooks (if scripts exist)
2. Sets up worktrees (if script exists)
3. Gracefully handles missing files (different branch)

### 6. Direct Push to Develop Blocked

**Problem**: Accidental pushes to develop bypass PR review.

**Solution**: `pre-push` hook blocks pushes to `develop` with guidance to create a feature branch and PR instead.

## Scripts Reference

| Script | Purpose |
|--------|---------|
| `setup-worktrees.sh` | Creates worktrees for develop, testing, agents |
| `install-hooks.sh` | Configures git to use .githooks/ |
| `route-commit.sh` | Cherry-picks commits to correct branch/worktree |
| `start-feature.sh` | Creates paired feature + integrate branches |
| `prepare-pr.sh` | Cleans agent files from a branch (alternative workflow) |

## Workflow Examples

### Starting a New Feature

```bash
# Create paired branches
./scripts/git/start-feature.sh add-dark-mode

# You're now on integrate/add-dark-mode
# Has CLAUDE.md, agent tooling available

# Work, commit code changes
git commit -m "Add dark mode toggle"

# Route to clean branch
./scripts/git/route-commit.sh HEAD add-dark-mode

# When ready, PR from clean branch
git checkout feature/add-dark-mode
git push -u origin feature/add-dark-mode
gh pr create --base develop
```

### Routing Infrastructure Changes

```bash
# Commit test changes on integration branch
git commit -m "Add E2E test for dark mode"

# Route to testing/develop
./scripts/git/route-commit.sh HEAD testing

# Push from worktree
cd ../orcpub-testing
git push origin testing/develop
```

### Fixing a Blocked Commit

When hook blocks you:
1. Read the error message
2. Choose a fix option:
   - Unstage the file: `git reset HEAD <file>`
   - Route the commit: `./scripts/git/route-commit.sh HEAD <target>`
   - Switch worktrees: `cd ../orcpub-<target>`

## Lessons Learned

### What Worked Well

1. **Worktrees >> Branch switching** - Physical isolation prevents most mistakes
2. **Hooks as safety net, not workflow** - They catch mistakes, don't enforce workflow
3. **Agent-readable errors** - Clear guidance reduces friction
4. **Devcontainer auto-setup** - Zero manual steps for contributors
5. **Flexible branch prefixes** - Works with existing naming conventions

### What Required Iteration

1. **Relative vs absolute hook paths** - Had to switch to absolute for worktree support
2. **Hook placement** - Hooks need to be in main repo, not each worktree
3. **Route script for local branches** - Initially only supported worktrees
4. **Error message formatting** - Took several iterations to get right for agents

### Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Worktrees | True isolation | More disk space |
| Dual-branch workflow | Always-clean PRs | Extra routing step |
| Blocking develop push | Forces PR review | Can't quick-fix develop |
| Auto-setup in devcontainer | Zero manual steps | Setup runs every rebuild |

## Files Created

```
.githooks/
├── pre-commit          # Branch-aware file blocking
└── pre-push            # Final safety + develop block

scripts/git/
├── README.md           # Full documentation
├── setup-worktrees.sh  # Create worktrees
├── install-hooks.sh    # Configure git hooks
├── route-commit.sh     # Move commits between branches
├── start-feature.sh    # Create paired branches
└── prepare-pr.sh       # Clean agent files (alternative)
```

## Alternative Approaches Considered

### Symlink Approach (Not Adopted)

**Idea**: Keep agent files only in `agents/develop` worktree, symlink them into feature branches (gitignored).

```
feature/my-feature/
├── CLAUDE.md -> ../orcpub-agents/CLAUDE.md  # symlink, gitignored
├── .claude/ -> ../orcpub-agents/.claude/     # symlink, gitignored
└── src/...                                   # actual code
```

**Pros**:
- Feature branches stay completely clean
- No stripping needed before PR
- Agent files versioned but never in feature branch history

**Cons**:
- Risk of agents not finding symlinked CLAUDE.md
- Symlinks may not work on Windows without admin privileges
- IDE indexing behavior varies with symlinks
- More complex setup

**Decision**: Stick with dual-branch workflow. The `prepare-pr.sh` cleanup script handles edge cases where agent files slip through.

### Separate Repository (Not Adopted)

**Idea**: Keep agent instructions in a completely separate git repository.

**Pros**:
- Complete isolation
- Can version independently
- Shareable across multiple projects

**Cons**:
- Two repos to manage
- Sync complexity
- Instructions can drift from code

**Decision**: Not worth the overhead for a single project.

### testing/develop vs agents/develop

**Observation**: `testing/develop` also "pollutes" feature branches when merged (for devcontainer, E2E access), but this is acceptable because:

1. Testing infrastructure is *part of the project* - legitimate to include
2. CI needs `.github/workflows/` to run
3. E2E tests validate the feature

Agent instructions are *meta* - about how to work on the project, not part of it. This distinction justifies treating them differently.

## Future Improvements

- [ ] GitHub Actions to validate branch contents on PR
- [ ] Script to sync worktrees after pulling
- [ ] Interactive mode for route-commit.sh to select commits
- [ ] VSCode tasks for common operations
- [ ] `--strip-only` flag for prepare-pr.sh (quick cleanup without cherry-pick)
