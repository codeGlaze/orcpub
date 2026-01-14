---
name: git-branch
description: Create a new git branch from agents/develop with proper naming conventions. Use when Claude needs to create a new feature branch, start working on a new task, or branch from agents/develop.
---
# Git Branch Creator

## Workflow

When this skill is invoked, follow these steps:

1. **Prompt for Branch Purpose**
   Ask the user: "What is the purpose or topic of this branch? (I'll format it as `claude/<your-input>-<session-id>`)"

2. **Get Current Session ID**
   Extract the session ID from the current branch name or context. The session ID is the suffix after the last hyphen in branches that follow the pattern `claude/*-<SESSION_ID>`.

3. **Create Branch Name**
   Format: `claude/<user-input-slugified>-<session-id>`
   - Convert the user's input to lowercase
   - Replace spaces with hyphens
   - Remove special characters except hyphens
   - Ensure it follows git branch naming conventions

4. **Fetch Latest Changes**
   ```bash
   git fetch origin agents/develop
   ```

   If network failures occur, retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s).

5. **Create and Switch to New Branch**
   ```bash
   git checkout -b <new-branch-name> origin/agents/develop
   ```

6. **Confirm Success**
   Display to the user:
   - The new branch name
   - The base branch (agents/develop)
   - Current branch status

   Example output:
   ```
   ✓ Created new branch: claude/cloud-storage-integration-SC31k
   ✓ Branched from: agents/develop
   ✓ Current branch: claude/cloud-storage-integration-SC31k
   ```

## Important Notes

- **Branch Naming**: All branches must start with `claude/` and end with the session ID
- **Base Branch**: Always branch from `agents/develop` unless explicitly instructed otherwise
- **Network Retries**: Use exponential backoff for git fetch operations (2s, 4s, 8s, 16s)
- **Clean State**: Check for uncommitted changes before switching branches and warn the user if any exist

## Error Handling

- If the branch already exists, ask the user if they want to:
  - Switch to the existing branch
  - Delete and recreate it
  - Choose a different name

- If there are uncommitted changes, ask the user if they want to:
  - Stash the changes
  - Commit the changes first
  - Continue anyway (may fail)
