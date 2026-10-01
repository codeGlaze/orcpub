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
# --check       verify only, change nothing.
# --hooks-only  arm the git hooks from the local copy of agents/develop and stop: no fetch,
#               no tooling, no output beyond one line. What the SessionStart hook and
#               setup-hooks.sh call, so there is one implementation.

set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

CHECK_ONLY=0
HOOKS_ONLY=0
case "${1:-}" in
  --check) CHECK_ONLY=1 ;;
  --hooks-only) HOOKS_ONLY=1 ;;
esac

KB=agents/develop
branch="$(git branch --show-current)"
say() { printf '%s\n' "$*"; }

[ $HOOKS_ONLY -eq 1 ] && say() { :; }
say ""
say "  agent setup -- branch: ${branch:-(detached)}"
say ""

# --- the KB branch, fetched but never checked out -----------------------------------
if git rev-parse --verify -q "origin/$KB" >/dev/null 2>&1; then
  [ $CHECK_ONLY -eq 0 ] && [ $HOOKS_ONLY -eq 0 ] && git fetch -q origin "$KB" 2>/dev/null
  KB_REF="origin/$KB"
elif git rev-parse --verify -q "$KB" >/dev/null 2>&1; then
  KB_REF="$KB"
else
  say "  FAILED: no $KB branch, locally or on origin. Nothing to set up from."
  exit 1
fi
say "  knowledge base:  $KB_REF ($(git log -1 --format=%h "$KB_REF"))"

if [ $HOOKS_ONLY -eq 0 ]; then
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

# --- the agent entry point ------------------------------------------------------------
# .gitignore calls CLAUDE.md "copied in per environment", but nothing copied it: a session
# on a code branch loaded no project instructions at all, and one spent a while writing a
# thinner replacement from scratch before finding the real one.
#
# Only copied where the branch IGNORES it. agents/develop ignores /CLAUDE.md; integration
# does not, so dropping it there leaves an untracked file that `git add -A` will commit --
# which is how it drifted between branches, and how integration came to delete it.
if [ "$branch" = "$KB" ]; then
  say "  entry point:     already on $KB, CLAUDE.md is tracked here"
elif [ $CHECK_ONLY -eq 1 ]; then
  say "  entry point:     $([ -f CLAUDE.md ] && echo 'present' || echo 'MISSING -- run without --check')"
elif ! git check-ignore -q CLAUDE.md 2>/dev/null; then
  say "  entry point:     SKIPPED -- this branch does not gitignore /CLAUDE.md."
  say "                   Copying it here would leave a file `git add -A` can commit."
  say "                   Add '/CLAUDE.md' to .gitignore on this branch, then re-run."
elif [ -f CLAUDE.md ]; then
  say "  entry point:     CLAUDE.md already here, left alone (delete it to refresh)"
elif git show "$KB_REF:CLAUDE.md" > CLAUDE.md 2>/dev/null; then
  say "  entry point:     CLAUDE.md from $KB_REF ($(wc -l < CLAUDE.md | tr -d ' ') lines, gitignored)"
else
  rm -f CLAUDE.md
  say "  entry point:     FAILED to read CLAUDE.md from $KB_REF"
fi

fi

# --- git hooks ------------------------------------------------------------------------
# One versioned set (.githooks on agents/develop), installed inside .git where no branch can
# track or commit it. core.hooksPath is in the clone's shared config, so this arms every
# worktree at once, and it is the only thing that sets it: anything else pointing
# core.hooksPath elsewhere is replaced here (agent-hooks.md).
HOOKS="commit-msg pre-commit pre-push"
hooks_dir="$(cd "$(git rev-parse --git-common-dir)" && pwd)/agent-hooks"
hook_src() {
  if [ "$branch" = "$KB" ]; then cat ".githooks/$1"; else git show "$KB_REF:.githooks/$1"; fi
}
hooks_current() {
  [ "$(git config core.hooksPath 2>/dev/null)" = "$hooks_dir" ] || return 1
  for h in $HOOKS; do
    [ -x "$hooks_dir/$h" ] && hook_src "$h" 2>/dev/null | cmp -s - "$hooks_dir/$h" || return 1
  done
}
if hooks_current; then
  say "  git hooks:       armed ($HOOKS)"
elif [ $CHECK_ONLY -eq 1 ]; then
  say "  git hooks:       NOT ARMED or older than $KB_REF -- run without --check"
  say "                   core.hooksPath is '$(git config core.hooksPath 2>/dev/null || echo unset)'"
else
  mkdir -p "$hooks_dir"
  ok=1
  for h in $HOOKS; do
    if hook_src "$h" >"$hooks_dir/$h.new" 2>/dev/null && [ -s "$hooks_dir/$h.new" ]; then
      mv "$hooks_dir/$h.new" "$hooks_dir/$h" && chmod +x "$hooks_dir/$h"
    else
      rm -f "$hooks_dir/$h.new"; ok=0
      say "  git hooks:       FAILED to read .githooks/$h from $KB_REF"
    fi
  done
  was="$(git config core.hooksPath 2>/dev/null || true)"
  if [ $ok -eq 1 ] && git config core.hooksPath "$hooks_dir"; then
    say "  git hooks:       armed ($HOOKS)$([ -n "$was" ] && [ "$was" != "$hooks_dir" ] && echo ", replacing $was")"
  fi
fi
[ $HOOKS_ONLY -eq 1 ] && exit 0

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
