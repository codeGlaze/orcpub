---
name: orcpub-primer
description: Primer for Claude sessions working on codeGlaze/orcpub from a `develop`-based branch (feature/*, fix/*, bugfix/*, claude/*, integrate/*, etc.). Use at session start, or whenever the agent needs to know "where are the agent instructions / KB / conventions for this repo?" — the answer is almost always `agents/develop`, a sister branch that this skill explains how to read without switching branches. Do NOT use for `agents/develop` sessions themselves — those already auto-load the canonical docs via CLAUDE.md.
---

# OrcPub / Dungeon Master's Vault — Code-Branch Primer

**If you are reading this, you are almost certainly on a `develop`-based branch** (feature/*, fix/*, bugfix/*, claude/*, integrate/*, or `develop` itself). The canonical agent instructions, knowledge base, and workflow docs for this repo do NOT live here. They live on a sister branch called **`agents/develop`**, and this skill exists so you can find and read them without repeating yourself or drifting out of sync.

## The one rule

**Do not re-derive what is already written on `agents/develop`.** If you feel the urge to explain the stack, branch protection rules, dev setup, or a KB topic — stop, fetch `agents/develop`, and read the authoritative file. This skill deliberately does not restate that content, so updates on `agents/develop` are the single source of truth.

## What lives on `agents/develop`

A docs-only branch. Git hooks enforce that only `*.md`, `.claude/*`, `agents/*`, `docs/*`, `scripts/git/*`, and `.githooks/*` can be committed to it. The canonical files are:

| File on `agents/develop` | What it is |
|---|---|
| `CLAUDE.md` | Thin bootstrapper. Auto-loads `AGENTS.md` and `BRANCH.md` via `@imports` when a session is on `agents/develop`. Also holds Claude-specific settings, E2E testing notes, and branch-protection + workflow tables. |
| `AGENTS.md` | Universal, model-agnostic agent instructions: critical rules, required reading, stack overview, key files, dev commands, tooling philosophy, env vars. The single source of truth for project-wide rules. |
| `BRANCH.md` | Branch-specific context for `agents/develop` (purpose, state, workflow, handoff notes). |
| `CODEBASE.md` | Living codebase overview. |
| `SETUP.md` | Devcontainer, MCP integration, attribution hooks. |
| `docs/DOC-CONVENTIONS.md` | **Read this before writing any doc.** Three-tier structure (`CLAUDE.md` / `AGENTS.md` / `BRANCH.md`), KB doc guidelines, `BRANCH.md` template, rules for where docs go. |
| `docs/GIT_WORKFLOW.md` | The multi-branch workflow (feature/integrate dual branches, hook routing, worktrees). |
| `docs/*.md` | Living KB — `DATOMIC_SETUP.md`, `THEMING.md`, `TESTING.md`, `ENVIRONMENT.md`, `ERROR_HANDLING.md`, `JAVA-COMPATIBILITY.md`, `MIGRATION-INDEX.md`, `ORCBREW_*.md`, `CODEBASE.md`, etc. |
| `docs/kb/` | Deep-dive reference KB — 30+ entries covering Pedestal CSP history, Datomic+Java21 test results, dependency validation, re-frame subscribe refactor, namespace architecture, PDF generation, Docker flow, homebrew spellcasting, etc. **Start with `docs/kb/README.md`** — the index. `docs/kb/namespace-architecture.md` in particular is flagged "START HERE" for codebase orientation. |
| `.claude/skills/git-branch/SKILL.md` | Companion skill: creates new `claude/<topic>-<session-id>` branches from `agents/develop`. |
| `.claude/config.json`, `.claude/branch-config` | Claude Code config and branch-routing hints (not tracked on code branches). |

## How to read those files from a `develop`-based branch

You do **not** need to switch branches or blow away your work. Pick whichever of these fits your situation.

### 1. Read a single file from `agents/develop` without touching your worktree

```bash
git fetch origin agents/develop   # once per session
git show origin/agents/develop:AGENTS.md
git show origin/agents/develop:CLAUDE.md
git show origin/agents/develop:BRANCH.md
git show origin/agents/develop:docs/DOC-CONVENTIONS.md
git show origin/agents/develop:docs/kb/README.md
git show origin/agents/develop:docs/kb/namespace-architecture.md
```

This is the default move. It is read-only, zero side effects, and the file content is exactly what a session that auto-loaded `CLAUDE.md` on `agents/develop` would see.

### 2. List everything on `agents/develop`

```bash
git ls-tree -r origin/agents/develop --name-only | grep -E '^(docs/|\.claude/|AGENTS|CLAUDE|BRANCH|CODEBASE|SETUP)'
```

Use this when you don't know which file has the answer — grep the tree, then `git show` the hit.

### 3. Keep a side-by-side worktree

If the repo already uses the multi-worktree layout (`/workspaces/orcpub-agents/` etc.), you can `cd` over and read files directly there. If no worktree exists yet, you can create one:

```bash
git worktree add ../orcpub-agents origin/agents/develop
# read docs in ../orcpub-agents/ — your current worktree is untouched
```

Remove it with `git worktree remove ../orcpub-agents` when you're done.

### 4. Merge agent docs into an integration branch

Only relevant if you're in an `integrate/*` branch that's meant to carry agent tooling alongside code:

```bash
./pull.sh    # interactively merges testing/develop + agents/develop into your branch
```

Do NOT run this on `develop` itself or on a clean `feature/*` branch — it will pollute the branch with docs and the hook will block the push.

## Branch protection — the short version

| Branch | Allowed files | Blocked |
|---|---|---|
| `develop` | (PR only, no direct push) | — |
| `testing/develop` | `e2e/*`, `.devcontainer/*`, `test/*`, `.github/*`, `scripts/*`, `.githooks/*`, `.gitignore`, `Dockerfile*`, `docker-compose*`, `*.sh` | Source code |
| `agents/develop` | `*.md`, `.claude/*`, `agents/*`, `docs/*`, `scripts/git/*`, `.githooks/*` | Source code, tests |
| `feature/*`, `fix/*`, `bugfix/*`, `hotfix/*`, `patch/*`, `enhancement/*` | Everything | Nothing |
| `integrate/*` | Everything (working branch for agents) | Nothing |

**For the full workflow, read `git show origin/agents/develop:CLAUDE.md` — the "Branch Strategy" section is authoritative and this table is a summary only.**

Consequences for code-branch work:

- **If you're on `develop` or `feature/*`**: never commit anything under `.claude/`, `docs/kb/`, `AGENTS.md`, `BRANCH.md`, `CLAUDE.md`, `CODEBASE.md`, or `SETUP.md`. Those belong on `agents/develop`. The only exception in this repo is `.claude/skills/*` (intentionally allowed so this primer skill itself can ship on code branches).
- **If you discover something worth documenting**, do not cram it into a code-branch README or commit message. Put it on `agents/develop` — either as a new `docs/kb/*.md` entry or an update to an existing doc. See the `docs/DOC-CONVENTIONS.md` rules on `agents/develop`.
- **If a hook blocks your commit**, the error message is the instructions. Follow it — usually one of: unstage the wrong file, route the commit to a sister branch with `./scripts/git/route-commit.sh`, or switch worktrees.

## Knowledge base — consult before you investigate

Before deep-diving into any of the following topics, grep `docs/kb/` on `agents/develop` first. Many investigations have already been done and written up:

- Pedestal CSP / 0.5.1→0.7.0 upgrade history → `docs/kb/pedestal-csp-history.md`
- Datomic + Java 21 compatibility → `docs/kb/DATOMIC_JAVA21_TEST_RESULTS.md`, `docs/DATOMIC_SETUP.md`, `docs/JAVA-COMPATIBILITY.md`
- `lein uberjar` hang → `docs/kb/lein-uberjar-hang.md`
- re-frame subscribe-outside-reactive-context → `docs/kb/re-frame-subscribe-refactor.md`, `docs/kb/subscribe-diagnosis-techniques.md`, `docs/kb/reframe-subscription-patterns.md`, `docs/kb/subscribe-refactor-phase2.md`
- Namespace / module layout → `docs/kb/namespace-architecture.md` (flagged "START HERE" for codebase orientation)
- PDF generation end-to-end → `docs/kb/pdf-generation-architecture.md`
- Entity / template / options system → `docs/kb/entity-options-architecture.md`
- Docker self-hosting, Swarm, security → `docs/kb/docker-setup-flow.md`, `docs/kb/docker-swarm-compat.md`, `docs/kb/docker-security-decisions.md`, `docs/kb/docker-testing-guide.md`, `docs/DOCKER.md`, `docs/DOCKER-SECURITY.md`
- Orcbrew import / validation → `docs/ORCBREW_FILE_VALIDATION.md`, `docs/ORCBREW_IMPORT_DEEP_DIVE.md`, `docs/kb/error-handling-import-validation.md`
- Homebrew class spellcasting → `docs/kb/homebrew-class-spellcasting.md`
- Fork customization (6-file pattern) → `docs/kb/fork-customization.md`
- Env vars + auth / `SIGNATURE` → `docs/kb/env-and-auth.md`, `docs/ENVIRONMENT.md`
- Testing patterns + gotchas → `docs/TESTING.md`, `docs/kb/testing-infrastructure.md`
- Theming / Garden / SVG icons → `docs/THEMING.md`

The full index lives at `docs/kb/README.md` on `agents/develop` and is the ground truth when this list drifts. Always `git show origin/agents/develop:docs/kb/README.md` to see the current inventory.

## Contributing back to the KB

If a non-trivial investigation produces a conclusion another agent would otherwise re-derive, write it down — but **on `agents/develop`**, not here.

1. Read `docs/DOC-CONVENTIONS.md` on `agents/develop` first. It is the authoritative guide for the three-tier doc structure (`CLAUDE.md` / `AGENTS.md` / `BRANCH.md`), where KB docs go (`docs/*.md` vs `docs/kb/*.md`), naming, and the `BRANCH.md` template.
2. Switch to an agent-side worktree or branch (`git worktree add ../orcpub-agents origin/agents/develop`, or use `./scripts/git/start-feature.sh <topic>` from `agents/develop` for a dual-branch setup).
3. Write the doc there — prefer appending to an existing file if the topic already exists.
4. Update `docs/kb/README.md` and/or `docs/DOC-CONVENTIONS.md` so the new entry is indexed.
5. Open a PR against `agents/develop` (not `develop`).

Do **not** try to push `AGENTS.md`, `CLAUDE.md`, or `docs/kb/*` from a code branch. The hook will block it, and even if it didn't, the file would be invisible to sessions that auto-load from `agents/develop`.

## Minimal onboarding sequence for a new session

Run this at the top of a session on a `develop`-based branch, once, to avoid having me repeat myself:

```bash
git fetch origin agents/develop
git show origin/agents/develop:AGENTS.md       | less      # project rules + stack + commands
git show origin/agents/develop:CLAUDE.md       | less      # branch table, dev workflow, theming
git show origin/agents/develop:BRANCH.md       | less      # agents/develop's own state
git show origin/agents/develop:docs/DOC-CONVENTIONS.md | less
git show origin/agents/develop:docs/kb/README.md | less    # KB index
```

After that, consult individual KB files on demand using `git show origin/agents/develop:docs/kb/<name>.md`. Don't paraphrase them from memory — the KB is the point.

## What this skill does NOT do

- It does not restate the stack, branch protection details, dev setup commands, theming internals, or KB contents. Those live on `agents/develop` so updates land in one place.
- It does not replace `CLAUDE.md`. When you are actually on `agents/develop`, Claude Code auto-loads `CLAUDE.md` (which imports `AGENTS.md` + `BRANCH.md`) — this skill is only the bridge from a code branch to that content.
- It does not create branches. Use `./scripts/git/start-feature.sh <topic>` (or the `git-branch` skill on `agents/develop`) for that.
