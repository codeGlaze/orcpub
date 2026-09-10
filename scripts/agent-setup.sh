#!/usr/bin/env bash
# Grab-and-go setup for an agent starting on a code branch.
#
# Code branches carry no CLAUDE.md and gitignore `.claude/`, so a session that starts
# on one loads nothing. This pulls the agent tooling out of agents/develop into the
# working tree, arms the git hooks, and prints where the knowledge base is.
#
# The tooling lands in gitignored paths, so it cannot reach a PR. That is the point:
# the ignore rule that keeps agentics off code branches is what makes copying them in
# safe.
#
# Run from anywhere in the repo, on any branch, as often as you like:
#   git show origin/agents/develop:scripts/agent-setup.sh | bash
#
# --check  verify only, change nothing.

set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

CHECK_ONLY=0
[ "${1:-}" = "--check" ] && CHECK_ONLY=1

KB=agents/develop
branch="$(git branch --show-current)"
say() { printf '%s\n' "$*"; }

say ""
say "  agent setup -- branch: ${branch:-(detached)}"
say ""

# --- the KB branch, fetched but never checked out -----------------------------------
if git rev-parse --verify -q "origin/$KB" >/dev/null 2>&1; then
  [ $CHECK_ONLY -eq 0 ] && git fetch -q origin "$KB" 2>/dev/null
  KB_REF="origin/$KB"
elif git rev-parse --verify -q "$KB" >/dev/null 2>&1; then
  KB_REF="$KB"
else
  say "  FAILED: no $KB branch, locally or on origin. Nothing to set up from."
  exit 1
fi
say "  knowledge base:  $KB_REF ($(git log -1 --format=%h "$KB_REF"))"

# --- agent tooling into the working tree ---------------------------------------------
# Skipped on the KB branch itself, where these files are tracked and extracting would
# overwrite live edits with committed ones.
if [ "$branch" = "$KB" ]; then
  say "  tooling:         already on $KB, nothing to copy"
elif [ $CHECK_ONLY -eq 1 ]; then
  say "  tooling:         $([ -d .claude ] && echo 'present' || echo 'MISSING -- run without --check')"
else
  if git archive "$KB_REF" .claude 2>/dev/null | tar -x -C . 2>/dev/null; then
    n=$(find .claude -type f 2>/dev/null | wc -l | tr -d ' ')
    say "  tooling:         $n files into .claude/ (gitignored here -- cannot reach a PR)"
  else
    say "  tooling:         FAILED to extract .claude from $KB_REF"
  fi
fi

# --- git hooks ------------------------------------------------------------------------
# core.hooksPath is per-clone and unset by default, so check-docs.sh and the changelog
# guard silently do not run. This was true for a whole session once; two plan docs went
# orphaned unnoticed.
hp="$(git config core.hooksPath || true)"
if [ "$hp" = ".githooks" ]; then
  say "  git hooks:       armed ($hp)"
elif [ $CHECK_ONLY -eq 1 ]; then
  say "  git hooks:       NOT ARMED -- run without --check"
elif [ -d .githooks ]; then
  git config core.hooksPath .githooks && say "  git hooks:       armed (.githooks)"
else
  say "  git hooks:       no .githooks on this branch, skipped"
fi

# --- verify rather than assume ---------------------------------------------------------
if [ -x scripts/check-docs.sh ] && git ls-files --error-unmatch docs >/dev/null 2>&1; then
  if scripts/check-docs.sh >/dev/null 2>&1; then
    say "  docs check:      CLEAN"
  else
    say "  docs check:      ISSUES -- run scripts/check-docs.sh"
  fi
fi

# --- orientation -----------------------------------------------------------------------
# Pointers only. Everything factual lives in the KB; restating it here would create a
# second copy that drifts.
say ""
say "  Read these before working (no checkout needed):"
say ""
say "    git show $KB_REF:BRANCH.md                          # live state, active plan"
say "    git show $KB_REF:docs/kb/README.md                  # the KB index"
say "    git show $KB_REF:docs/kb/documentation-discipline.md # how findings get recorded"
say "    git ls-tree -r $KB_REF --name-only docs/kb/         # everything available"
say ""
say "  Write findings to $KB (docs/kb/), never to a code branch. Index them in"
say "  docs/kb/README.md or check-docs.sh fails the commit."
say ""
if [ -f scripts/clj-grep.py ]; then
  say "  Grepping Clojure: use scripts/clj-grep.py, not grep. 27 subclasses in"
  say "  classes.cljc are #_ discarded and a plain grep reports them as live."
  say ""
fi
