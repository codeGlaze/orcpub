#!/usr/bin/env bash
# Stop hook: asks the agent to document code it just changed when no doc changed with it.
#
# Checks every worktree this session has worked in: the session's own checkout, plus each worktree
# of this clone whose path appears in the session transcript. In each it counts only files changed
# in the window: the last 30 minutes, and not before the session began. Uncommitted files count by
# modification time; with a clean tree, a last commit made in the window counts. Silent when
# agents/develop (where code branches keep their docs) changed in the window. GOTCHA: do not union
# the dirty tree with the last commit: a docs-only commit at HEAD would then silence every later
# code change.
#
# Speaks to the agent, not the person: it answers {"decision":"block","reason":...}, which Claude
# Code hands to the agent as one more instruction before the turn ends. Silent when the stop was
# already caused by this hook (stop_hook_active), so it can never loop. See docs/kb/agent-hooks.md.
set -uo pipefail
input=$(cat)
field() { printf '%s' "$input" | jq -r "$1" 2>/dev/null; }
[ "$(field '.stop_hook_active // false')" = "true" ] && exit 0
transcript=$(field '.transcript_path // ""')
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

now=$(date +%s)
since=$(( now - 1800 ))
if [ -n "$transcript" ] && [ -f "$transcript" ]; then
  first=$(head -c 20000 "$transcript" | grep -m1 -oE '"timestamp":"[^"]+"' | cut -d'"' -f4)
  started=$(date -d "$first" +%s 2>/dev/null || echo 0)
  [ "$started" -gt "$since" ] && since=$started
fi

dirs=$(git rev-parse --show-toplevel)
if [ -n "$transcript" ] && [ -f "$transcript" ]; then
  for w in $(git worktree list --porcelain 2>/dev/null | awk '/^worktree /{print $2}'); do
    [ "$w" = "$dirs" ] && continue
    grep -qE "$(printf '%s' "$w" | sed 's/[.[\*^$]/\\&/g')([/\" ';&|)]|$)" "$transcript" 2>/dev/null || continue
    dirs="$dirs"$'\n'"$w"
  done
fi

# Code branches keep their docs on agents/develop: a commit there in the window counts as the
# docs having been updated.
for r in agents/develop origin/agents/develop; do
  [ "$(git log -1 --format=%ct "$r" 2>/dev/null || echo 0)" -ge "$since" ] && exit 0
done

gaps=""
while IFS= read -r d; do
  [ -n "$d" ] || continue
  changed=""
  dirty=$(git -C "$d" status --porcelain --untracked-files=all 2>/dev/null | sed 's/^...//; s/^"//; s/"$//; s/.* -> //')
  if [ -n "$dirty" ]; then
    while IFS= read -r f; do
      [ -n "$f" ] || continue
      m=$(stat -c %Y "$d/$f" 2>/dev/null || echo "$now")   # a deleted file counts as just changed
      [ "$m" -ge "$since" ] && changed="$changed$f"$'\n'
    done <<< "$dirty"
  elif [ "$(git -C "$d" log -1 --format=%ct 2>/dev/null || echo 0)" -ge "$since" ]; then
    changed=$(git -C "$d" diff --name-only HEAD~1 HEAD 2>/dev/null)
  fi
  code=$(printf '%s' "$changed" | grep -cE '^(src|test|dev|scripts)/' || true)
  docs=$(printf '%s' "$changed" | grep -cE '^docs/' || true)
  [ "${code:-0}" -gt 0 ] && [ "${docs:-0}" -eq 0 ] && gaps="$gaps ${d##*/} ($code files)"
done <<< "$dirs"

[ -n "$gaps" ] || exit 0
jq -n --arg g "$gaps" '{decision: "block", reason: ("Code changed with no doc change in:" + $g + ". Before ending the turn: every function you wrote or touched has a SPEC docstring (what it does, its args, what it returns; a GOTCHA line only for code easy to break). No history, rationale or decision narrative in code comments: move it to docs/kb/ on agents/develop and name the page in the comment. If this changed a finding, decision, measurement or reversal, record it there too (index it in docs/kb/README.md). If none of that applies (a rename, a lint fix), say so in one line and stop.")}'
exit 0
