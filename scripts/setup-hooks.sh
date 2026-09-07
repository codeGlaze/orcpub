#!/usr/bin/env bash
# Point git at the repo's tracked hooks (.githooks/). Run once per clone.
# Idempotent — safe to re-run.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "core.hooksPath -> .githooks"
echo "active hooks: $(ls -1 .githooks 2>/dev/null | tr '\n' ' ')"
