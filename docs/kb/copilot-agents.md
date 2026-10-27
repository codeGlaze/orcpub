# Running Claude through VS Code Copilot

How to run a Claude agent in VS Code's Copilot Chat (agent mode) on this repo so it follows the
same rules as a Claude Code session. Written 2026-10-27 for finishing merges from VS Code.

## What carries over, and what does not

- **Carries over:** git itself. The agent works in the same clones, so the repo's git config
  (codeGlaze identity, GPG signing) and the hooks in `.git/agent-hooks` (`core.hooksPath`) apply to
  its commits and pushes, including the one that strips Claude attribution trailers.
- **Does not:** Claude Code's SessionStart hook, which runs `scripts/agent-setup.sh` and writes
  `CLAUDE.md`; its memory; and its stop hooks. Copilot reads only what is in the workspace or the
  prompt.
- **Copilot's own trailer** (`Co-authored-by: Copilot`) is not one the commit-msg hook was written
  for. The prompt below forbids it; check the agent's first commit.

## One-time setup in VS Code

1. Install **GitHub Copilot Chat**. In the chat view, pick **Agent** mode and a **Claude** model.
2. In Settings, turn on **`chat.useAgentsMdFile`** (Chat: Use AGENTS.md File). Copilot then reads a
   root `AGENTS.md` into every request.
3. Open the **worktree folder** of the branch the agent works on (File > Open Folder, e.g.
   `\\wsl$\...\orcpub-lookup`), not the main checkout, so its `AGENTS.md` is the one it reads.

## Every new worktree

Run, in the worktree:

```bash
git fetch --prune origin && git show origin/agents/develop:scripts/agent-setup.sh | bash
```

This copies `AGENTS.md` and the generated `CLAUDE.md` (AGENTS.md, BRANCH.md and the branch handoff
in one file) into the worktree, excluded from git, and arms the hooks. A worktree made with
`git worktree add` has neither file until this runs.

## The prompt

Fill in TASK and BRANCH. It works for any agent, Copilot or not:

```text
You are working on the codeGlaze/orcpub repo. First run
`git fetch --prune origin && git show origin/agents/develop:scripts/agent-setup.sh | bash`,
then read CLAUDE.md in the workspace root (it holds AGENTS.md, BRANCH.md and this branch's
handoff) and follow it. Its rules override your defaults.

Task: TASK
Branch: BRANCH. Work only in its worktree; run `git branch --show-current` before anything else.

- Start every reply that does work with stop rules (AGENTS.md, "Stop rules and budget").
- Commit as the repo is configured (codeGlaze, GPG-signed). Add no Co-authored-by or other
  attribution trailer. Never use --no-verify.
- Never push to develop or integration, and never merge a PR unless I say so in this chat.
- Every 12 to 24 hours, re-run the setup command and read what changed in AGENTS.md.
- When you finish or get stuck: update the branch handoff (branches/<branch>.md on agents/develop),
  then report in 3 to 6 lines with "Needs you" questions at the end.
```

For a step already written up, TASK can be one line: `do step 6 of branches/test-page-load-budget.md`.
