#!/usr/bin/env bash
# Stop hook: at most once an hour per session, asks the agent to record what it learned.
# A session an expertise file covers (the same matching as expertise.sh, which it runs) is asked to
# add to it; a session no file covers is asked to start one. See docs/kb/expertise-chats.md.
input=$(cat)
read -r id active cwd < <(printf '%s' "$input" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print(d.get("session_id", ""), d.get("stop_hook_active", False), d.get("cwd", "") or ".")')
[ "$active" = "False" ] && [ -n "$id" ] || exit 0
stamp=~/.claude/expertise/.nudged-$id
[ -n "$(find "$stamp" -mmin -60 2>/dev/null)" ] && exit 0
touch "$stamp"
covered=$(printf '%s' "$input" | bash ~/.claude/hooks/expertise.sh 2>/dev/null)
if [ -n "$covered" ]; then
  echo "Anything from the last hour worth keeping? Add owner corrections, failed approaches and traps to the expertise file that covers this area (add your own session: line), shared findings to the KB. If nothing, carry on." >&2
else
  branch=$(git -C "$cwd" branch --show-current 2>/dev/null)
  folder=$(basename "$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null || echo "$cwd")")
  echo "No expertise file covers this area (folder $folder, branch ${branch:-none}). If this chat learned anything another agent would need (owner corrections, traps, how an area works), start ~/.claude/expertise/<area>.md with session:, branches: or dirs: lines (docs/kb/expertise-chats.md). If nothing, carry on." >&2
fi
exit 2
