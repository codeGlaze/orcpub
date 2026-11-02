#!/usr/bin/env bash
# SessionStart -- set this worktree up for an agent: tooling, CLAUDE.md and the git hooks.
#
# ---------------------------------------------------------------------------
# IF YOU ARE AN AGENT SEEING THIS FOR THE FIRST TIME: READ THIS PARAGRAPH.
#
# This runs agent-setup.sh --session, read from the local agents/develop ref,
# fetched first when that copy is over a day old. It copies .claude/ and a generated CLAUDE.md into this worktree
# (both kept out of commits), copies the three git hooks into .git/agent-hooks/
# and points core.hooksPath there. It changes no tracked file and always exits 0.
# When it had to write CLAUDE.md it prints a line asking the agent to read it,
# since CLAUDE.md is loaded before this hook runs.
#
# Undo with:  git config --unset core.hooksPath
# ---------------------------------------------------------------------------
#
# The hooks: commit-msg strips agent attribution trailers, pre-commit runs the
# docs check where it exists, pre-push refuses an un-folded branch changelog onto
# integration/develop/main. A guard nobody remembers to arm is not a guard, so a
# session arms it. Everything that sets core.hooksPath goes through this one
# script (docs/kb/agent-hooks.md).

set -uo pipefail

root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "session-start: not a git checkout -- nothing to arm"
  exit 0
}
cd "$root" || exit 0

# A session loaded after a gap gets the current rules and hooks; offline it carries on with the local copy.
stamp="$(git rev-parse --git-common-dir)/agents-develop-fetched"
if [ -z "$(find "$stamp" -mmin -1440 2>/dev/null)" ]; then
  timeout 8 git fetch -q origin agents/develop 2>/dev/null && touch "$stamp"
fi

if [ "$(git branch --show-current)" = "agents/develop" ] && [ -f scripts/agent-setup.sh ]; then
  bash scripts/agent-setup.sh --session
else
  ref=""
  for r in origin/agents/develop agents/develop; do
    git rev-parse --verify -q "$r" >/dev/null 2>&1 && { ref="$r"; break; }
  done
  [ -n "$ref" ] || { echo "session-start: no agents/develop ref -- hooks not armed"; exit 0; }
  git show "$ref:scripts/agent-setup.sh" | bash -s -- --session
fi

echo "session-start: core.hooksPath -> $(git config core.hooksPath 2>/dev/null || echo unset)"
exit 0
