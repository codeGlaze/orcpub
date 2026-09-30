---
name: git-branch
description: Create a new git branch, only after the owner has approved it by name. Use when a task seems to need a branch other than the one the session was given.
---
# Git Branch Creator

## Workflow

A web agent can push a branch but cannot delete one, so a branch is the owner's to clean up.
Nothing here runs until the owner has approved the branch by name (`AGENTS.md`, Agent Workflow
Rules 8).

1. **Ask, as its own question, whether to create it, naming it.** Propose the name and base:
   `Create fix/<topic> from integration?`. Wait for a yes to that question. A yes to a plan that
   mentions a separate PR or branch is not a yes to the branch.

2. **Name it with a typed prefix** (`AGENTS.md`, Branch Protection): `feature/`, `fix/`,
   `hotfix/`, `perf/`, `refactor/` or `docs/`, then the topic in lowercase with hyphens. Never
   `claude/`: harness branches do not merge.

3. **Pick the base.** Code that must ship branches from `integration`. Docs and agent tooling
   branch from `agents/develop`.

4. **Check for uncommitted changes** with `git status`. If there are any, ask whether to commit
   them first or leave them where they are. Do not stash: the stash is shared by every worktree of
   the repository.

5. **Fetch, then create it.**
   ```bash
   git fetch origin <base>
   git checkout -b <name> origin/<base>
   ```
   If the fetch fails on the network, retry up to 4 times with backoff (2s, 4s, 8s, 16s).

6. **Record it.** Add the branch and its role to the working branch's `BRANCH.md` Current State in
   the same commit that first pushes it, so the owner can see every branch that exists.

## Error Handling

- The branch already exists: ask whether to switch to it or use another name. Never delete one.
- The fetch fails after 4 retries: report it and ask how to proceed.
