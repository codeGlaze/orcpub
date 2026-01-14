---
name: git-branch
description: Create a new git branch from agents/develop with proper naming conventions. Use when Claude needs to create a new feature branch, start working on a new task, or branch from agents/develop.
---
# Git Branch Creator

## Workflow

When this skill is invoked, follow these steps:

1. **Prompt for Branch Name**
   Ask the user: "What is the purpose or topic of this branch? (I'll format it as `claude/<your-input>-<session-id>`)"

   Wait for the user's response before proceeding.

2. **Prompt for Branch Assignment** (if not already provided)
   If the user hasn't already described what they'll be working on, ask:
   "What's the assignment or goal for this branch? (e.g., 'Implement X feature', 'Fix Y bug', 'Explore feasibility of Z')"

   This helps document the branch purpose and can be used in commit messages or documentation.

3. **Get Current Session ID**
   Extract the session ID from the current branch name or context. The session ID is the suffix after the last hyphen in branches that follow the pattern `claude/*-<SESSION_ID>`.

4. **Create Branch Name**
   Format: `claude/<user-input-slugified>-<session-id>`
   - Convert the user's input to lowercase
   - Replace spaces with hyphens
   - Remove special characters except hyphens
   - Ensure it follows git branch naming conventions

5. **Check for Uncommitted Changes**
   Run `git status` to check for uncommitted changes.

   If there are uncommitted changes, ask the user if they want to:
   - Stash the changes
   - Commit the changes first
   - Continue anyway (may fail)

6. **Fetch Latest Changes**
   ```bash
   git fetch origin agents/develop
   ```

   If network failures occur, retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s).

7. **Create and Switch to New Branch**
   ```bash
   git checkout -b <new-branch-name> origin/agents/develop
   ```

8. **Confirm Success**
   Display to the user:
   - The new branch name
   - The base branch (agents/develop)
   - Current branch status
   - The assignment/purpose (for reference)

   Example output:
   ```
   ✓ Created new branch: claude/cloud-storage-integration-SC31k
   ✓ Branched from: agents/develop
   ✓ Current branch: claude/cloud-storage-integration-SC31k
   ✓ Assignment: Explore feasibility of browser-based cloud storage integration
   ```

## Important Notes

- **Branch Naming**: All branches must start with `claude/` and end with the session ID
- **Base Branch**: Always branch from `agents/develop` unless explicitly instructed otherwise
- **Network Retries**: Use exponential backoff for git fetch operations (2s, 4s, 8s, 16s)
- **Assignment Documentation**: The assignment/purpose helps track branch goals and can be referenced later

## Error Handling

- If the branch already exists, ask the user if they want to:
  - Switch to the existing branch
  - Delete and recreate it
  - Choose a different name

- If there are uncommitted changes, ask the user if they want to:
  - Stash the changes
  - Commit the changes first
  - Continue anyway (may fail)

- If git fetch fails after 4 retries, report the error and ask how to proceed
