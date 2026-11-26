#!/usr/bin/env bash
# Prints the summary's line-count table: each file changed since <base>, its lines then and now.
#
# Usage, from any code worktree (no copy needed):
#   git show origin/agents/develop:scripts/line-counts.sh | bash -s -- <base> [<head>]
# <base> is the commit the work started from; <head> defaults to the working tree, uncommitted
# edits included. Marks new and deleted files, and files over 2,000 lines (AGENTS.md, "Reporting back").
set -euo pipefail

base=${1:?usage: line-counts.sh <base> [<head>]}
head=${2:-}

# Prints the line count of path $2 at commit $1, or 0 when it does not exist there.
lines_at() { if git cat-file -e "$1:$2" 2>/dev/null; then git show "$1:$2" | wc -l | tr -d ' '; else echo 0; fi; }

if [ -n "$head" ]; then files=$(git diff --name-only "$base" "$head"); else files=$(git diff --name-only "$base"); fi
[ -z "$files" ] && { echo "No files changed since $base."; exit 0; }

echo "| File | Before | After | |"
echo "|---|---|---|---|"
while IFS= read -r f; do
  before=$(lines_at "$base" "$f")
  if [ -n "$head" ]; then after=$(lines_at "$head" "$f")
  elif [ -f "$f" ]; then after=$(wc -l < "$f" | tr -d ' ')
  else after=0; fi
  note=""
  git cat-file -e "$base:$f" 2>/dev/null || note="new"
  { [ -n "$head" ] && ! git cat-file -e "$head:$f" 2>/dev/null; } && note="deleted"
  { [ -z "$head" ] && [ ! -e "$f" ]; } && note="deleted"
  [ "$after" -gt 2000 ] && note="${note:+$note, }over 2,000"
  echo "| $f | $before | $after | $note |"
done <<< "$files"
