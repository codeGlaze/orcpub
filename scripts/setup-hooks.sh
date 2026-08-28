#!/usr/bin/env bash
# Point git at the repo's tracked hooks (.githooks/). Run once per clone.
# Idempotent. Relative path resolves per-worktree, so each worktree runs the
# hooks of its own checked-out branch.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "core.hooksPath -> .githooks"
echo "active hooks: $(ls -1 .githooks 2>/dev/null | tr '\n' ' ')"
