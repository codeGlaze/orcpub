#!/usr/bin/env bash
# Stop hook: reminds about the comment rule and the KB when code changed but no doc did.
#
# Checks every worktree this session has worked in, not only the one it started in: the
# session's own checkout, plus each worktree of this clone whose path appears in the session
# transcript (a `cd` or a file path). Per worktree it looks at ONE window: the uncommitted tree
# while it is dirty, otherwise a last commit made in the past 30 minutes. Silent when agents/develop
# (where code branches keep their docs) changed in that time. GOTCHA: do not union the two windows --
# a docs-only commit at HEAD would then silence every later code change, the case this exists for.
#
# Reads the hook's JSON on stdin for transcript_path. Always exits 0: a reminder, never a gate.
set -uo pipefail
input=$(cat)
transcript=$(printf '%s' "$input" | jq -r '.transcript_path // ""' 2>/dev/null)
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

dirs=$(git rev-parse --show-toplevel)
if [ -n "$transcript" ] && [ -f "$transcript" ]; then
  for w in $(git worktree list --porcelain 2>/dev/null | awk '/^worktree /{print $2}'); do
    [ "$w" = "$dirs" ] && continue
    grep -qE "$(printf '%s' "$w" | sed 's/[.[\*^$]/\\&/g')([/\" ';&|)]|$)" "$transcript" 2>/dev/null || continue
    dirs="$dirs"$'\n'"$w"
  done
fi

# Code branches keep their docs on agents/develop: a commit there in the last 30 minutes counts
# as the docs having been updated.
recent=$(( $(date +%s) - 1800 ))
for r in agents/develop origin/agents/develop; do
  [ "$(git log -1 --format=%ct "$r" 2>/dev/null || echo 0)" -ge "$recent" ] && exit 0
done

gaps=""
while IFS= read -r d; do
  [ -n "$d" ] || continue
  changed=$(git -C "$d" status --porcelain 2>/dev/null | sed 's/^...//; s/^"//; s/"$//' | sort -u)
  # Clean tree: judge the last commit, but only one made in the last 30 minutes.
  if [ -z "$changed" ] && [ "$(git -C "$d" log -1 --format=%ct 2>/dev/null || echo 0)" -ge "$recent" ]; then
    changed=$(git -C "$d" diff --name-only HEAD~1 HEAD 2>/dev/null | sort -u)
  fi
  code=$(printf '%s\n' "$changed" | grep -cE '^(src|test|dev|scripts)/' || true)
  docs=$(printf '%s\n' "$changed" | grep -cE '^docs/' || true)
  [ "${code:-0}" -gt 0 ] && [ "${docs:-0}" -eq 0 ] && gaps="$gaps ${d##*/} ($code files)"
done <<< "$dirs"

[ -n "$gaps" ] || exit 0
jq -n --arg g "$gaps" '{systemMessage: ("Code changed with no doc change in:" + $g + ". Before moving on: every function you wrote or touched has a SPEC docstring (what it does, its args, what it returns; a GOTCHA line only for code easy to break). No history, rationale or decision narrative in code comments: move it to docs/kb/ on agents/develop and name the page in the comment. If this changed a finding, decision, measurement or reversal, record it there too (index it in docs/kb/README.md). If none of that applies (a rename, a lint fix), ignore this.")}'
exit 0
