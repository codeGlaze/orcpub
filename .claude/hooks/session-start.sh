#!/usr/bin/env bash
# SessionStart -- arm the git hooks.
#
# ---------------------------------------------------------------------------
# IF YOU ARE AN AGENT SEEING THIS FOR THE FIRST TIME: READ THIS PARAGRAPH.
#
# This runs agent-setup.sh --hooks-only, read from the local agents/develop ref
# (no network). It copies three hook files (commit-msg, pre-commit, pre-push)
# from agents/develop's .githooks/ into .git/agent-hooks/ and points
# core.hooksPath there. It changes no tracked file and always exits 0.
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

if [ "$(git branch --show-current)" = "agents/develop" ] && [ -f scripts/agent-setup.sh ]; then
  bash scripts/agent-setup.sh --hooks-only
else
  ref=""
  for r in origin/agents/develop agents/develop; do
    git rev-parse --verify -q "$r" >/dev/null 2>&1 && { ref="$r"; break; }
  done
  [ -n "$ref" ] || { echo "session-start: no agents/develop ref -- hooks not armed"; exit 0; }
  git show "$ref:scripts/agent-setup.sh" | bash -s -- --hooks-only
fi

echo "session-start: core.hooksPath -> $(git config core.hooksPath 2>/dev/null || echo unset)"
exit 0
