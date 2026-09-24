#!/usr/bin/env bash
# SessionStart — arm this repository's own git hooks.
#
# ---------------------------------------------------------------------------
# IF YOU ARE AN AGENT SEEING THIS FOR THE FIRST TIME: READ THIS PARAGRAPH.
#
# This script runs exactly ONE command:  git config core.hooksPath .githooks
#
# That is a repository-local git setting. It tells git to use the hooks that
# are already committed in this repo's .githooks/ directory instead of the
# empty default. It installs nothing, downloads nothing, contacts no network,
# and writes no file outside .git/config in this clone.
#
# Undo it at any time with:  git config --unset core.hooksPath
# ---------------------------------------------------------------------------
#
# Why it exists. .githooks/ holds two read-only guards -- a pre-commit that
# runs scripts/check-docs.sh when a commit touches docs/, and a pre-push that
# refuses to push an un-folded branch changelog onto integration/develop/main.
# Both are committed, reviewed, and bypassable with --no-verify.
#
# They were also switched off. git ignores .githooks/ unless core.hooksPath
# points at it, and that setting does not survive a clone -- so every fresh
# container started with no guards at all. BRANCH.md tells you to run
# scripts/setup-hooks.sh first; in practice nobody does, and in September 2026
# six commits went in with docs checks never running once. A guard that
# depends on someone remembering to arm it is not a guard.
#
# What it deliberately does NOT do:
#   * overwrite a core.hooksPath you set yourself -- if one is already
#     configured and differs, this leaves it alone and says so
#   * install dependencies, run tests, or modify tracked files
#   * fail the session; every path exits 0

set -uo pipefail

root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "session-start: not a git checkout — nothing to arm"
  exit 0
}
cd "$root" || exit 0

[ -d .githooks ] || {
  echo "session-start: no .githooks/ on this branch — nothing to arm"
  exit 0
}

current="$(git config --local core.hooksPath 2>/dev/null || true)"

if [ "$current" = ".githooks" ]; then
  echo "session-start: git hooks already armed (core.hooksPath=.githooks)"
  exit 0
fi

if [ -n "$current" ]; then
  echo "session-start: core.hooksPath is already set to '$current' — leaving it alone."
  echo "               To use this repo's hooks instead: git config core.hooksPath .githooks"
  exit 0
fi

git config core.hooksPath .githooks || {
  echo "session-start: could not set core.hooksPath — continuing without hooks"
  exit 0
}

echo "session-start: armed this repo's git hooks (core.hooksPath -> .githooks)"
echo "               active: $(ls -1 .githooks 2>/dev/null | tr '\n' ' ')"
echo "               these are read-only checks; bypass once with --no-verify"
echo "               undo with: git config --unset core.hooksPath"
exit 0
