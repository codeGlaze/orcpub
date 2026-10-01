#!/usr/bin/env bash
# Arms the git hooks for every worktree of this clone. One implementation lives in
# agent-setup.sh; this is its --hooks-only mode under the name the older docs use
# (docs/kb/agent-hooks.md).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1
bash scripts/agent-setup.sh --hooks-only
echo "core.hooksPath -> $(git config core.hooksPath 2>/dev/null || echo unset)"
