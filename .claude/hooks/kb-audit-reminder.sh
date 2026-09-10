#!/usr/bin/env bash
# Stop hook: nudge when code changed but the KB did not.
#
# Fires only on an actual gap, so it stays quiet during ordinary work. It looks at ONE
# window: the uncommitted tree while it is dirty, otherwise the last commit. Unioning the
# two was tried first and is wrong — a docs-only commit at HEAD then silences every later
# code change, which is exactly the case the reminder exists for.
#
# Exits 0 always — this is a reminder, never a gate.
set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

changed=$(git status --porcelain 2>/dev/null | sed 's/^...//' | sed 's/^"//; s/"$//' | sort -u)
# Clean tree: judge the commit that was just made instead.
[ -n "$changed" ] || changed=$(git diff --name-only HEAD~1 HEAD 2>/dev/null | sort -u)
[ -n "$changed" ] || exit 0

code=$(printf '%s\n' "$changed" | grep -E '^(src|test|dev)/' | head -20)
docs=$(printf '%s\n' "$changed" | grep -E '^docs/')

[ -n "$code" ] || exit 0
[ -z "$docs" ] || exit 0

n=$(printf '%s\n' "$code" | wc -l | tr -d ' ')
printf '{"systemMessage":"KB gap: %s code/test file(s) changed with no docs/ update. If this changed a finding, a decision, a measurement, or a tenet, record it in docs/kb/ — an existing doc if one covers it, a new one if not, and note reversals rather than overwriting superseded reasoning. Keep code comments dense and high-signal. If the change genuinely needs no doc (lint, rename, mechanical refactor), ignore this."}\n' "$n"
exit 0
