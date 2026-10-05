#!/usr/bin/env bash
# PreToolUse(Bash) — advisory only, never blocks.
#
# Fires on `git push` and checks what is ABOUT to be pushed. If those commits changed src/ or
# test/ but touched no documentation, it reminds the agent to write the knowledge down before it
# is lost. Non-obvious findings, reversals and traced behaviour are the whole value of this repo's
# KB; a push with none of that recorded is usually an oversight, not a decision.
#
# Deliberately a reminder and not a gate: plenty of pushes legitimately need no docs (a typo, a
# test-only fix), and a hook that blocks those would just train people to work around it.

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // ""' 2>/dev/null)

printf '%s' "$cmd" | grep -qE 'git( +-C +[^ ]+)? +push' || exit 0
# Run where the push runs: `git -C DIR push`, or `cd DIR && ... git push`, else the session's cwd.
# GOTCHA: a session works in several worktrees; the session's own checkout is often not the one pushed.
dir=$(printf '%s' "$input" | jq -r '.cwd // ""' 2>/dev/null)
target=$(printf '%s' "$cmd" | sed -nE 's/.*git +-C +"?([^" ]+)"? +push.*/\1/p' | tail -1)
[ -n "$target" ] || target=$(printf '%s' "$cmd" | sed -nE 's/(^|.*[;&|] *)cd +"?([^"; &|]+)"? *(&&|;).*git push.*/\2/p' | tail -1)
if [ -n "$target" ]; then case "$target" in /*) dir="$target" ;; *) dir="$dir/$target" ;; esac; fi
[ -n "$dir" ] && cd "$dir" 2>/dev/null
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

# The commits this push would send. With no upstream yet, fall back to the default branch.
if git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
  files=$(git diff --name-only '@{u}..HEAD' 2>/dev/null)
else
  base=$(git rev-parse --verify -q origin/HEAD 2>/dev/null || git rev-parse --verify -q origin/integration 2>/dev/null)
  [ -n "$base" ] && files=$(git diff --name-only "$base..HEAD" 2>/dev/null) || exit 0
fi
[ -n "$files" ] || exit 0

code=$(printf '%s\n' "$files" | grep -cE '^(src|test)/' || true)
docs=$(printf '%s\n' "$files" | grep -cE '^(docs/|CLAUDE\.md$)' || true)

if [ "${code:-0}" -gt 0 ] && [ "${docs:-0}" -eq 0 ]; then
  n=$(printf '%s\n' "$files" | grep -cE '^(src|test)/')
  jq -n --arg n "$n" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      additionalContext: ("KB REMINDER: this push changes \($n) file(s) under src/ or test/ and touches no documentation. Before pushing, ask whether it produced knowledge worth keeping: a traced behaviour, a measured number, a bug and its cause, a decision, or a REVERSAL of something previously believed. If so, add or update the relevant doc in docs/kb/ on agents/develop (and its README index) and amend or add a commit. Update an existing doc in place rather than appending a second account of the same thing, and when you contradict an earlier conclusion record the reversal instead of silently overwriting it. If the change genuinely needs no docs — a typo, a rename, a test-only tweak — push and say so in one line.")
    }
  }'
fi
exit 0
