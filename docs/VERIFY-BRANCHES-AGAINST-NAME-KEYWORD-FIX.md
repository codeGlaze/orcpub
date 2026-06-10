# Campaign: verify related branches against `name-keyword-fix`

`feature/name-keyword-fix` is shipped and verified (spell-selection name-poisoning
fix — see `docs/kb/spell-selection-source-fix.md`). It's now the **integration
base**: several other semi-related branches need to land cleanly on top of it and
be e2e-verified before they ship.

## Process per branch (do NOT mint merge-commit artifacts)
1. **Test-merge** the branch against `feature/name-keyword-fix`. The goal is to
   **fix the *source* branch** until it merges clean — that's the point of a
   test-merge. Don't commit a merge result as the deliverable.
   - `git worktree add --detach /tmp/mt feature/name-keyword-fix` →
     `git merge --no-commit <branch>` → inspect `--diff-filter=U` → abort →
     adjust the source branch → repeat.
2. **e2e-verify live** on the running app (not just unit tests) — bring up the
   codespace, use the local Playwright → public-URL harness. See `E2E-NOTES.md`
   and memory `orcpub-e2e-verification-harness`.
3. **Land clean** — per-file commits authored `codeGlaze`, no agentic smell, no
   secrets. See memory `orcpub-clean-branch-discipline`.
4. For DMV public testing, the same branch must also merge onto the Gitea `dmv`
   fork (branding-siloed, usually trivial) — memory `orcpub-gitea-dmv-push`.

## Targets
| Branch | Files vs develop | Overlap w/ name-keyword-fix | Notes |
|---|---|---|---|
| `claude/character-black-screen-feature-i8lvk3` | error-boundary / render-guard (views.cljs, common.cljc, classes.cljc) + docs/tests | `classes.cljc`, `test_runner.cljs` | both overlaps are **additive** (a `def`, a test require) → expect an easy merge. Feature: per-item fail-soft render guard + recovery panel for the "feature tab black screen". |
| _(add others as identified)_ | | | |

## First-target head start (character-black-screen)
- Its `classes.cljc` change is Evasion/Hunter clarifications; ours is the
  `base-class-keys` dedupe — different regions, additive.
- Its `test_runner.cljs` change adds test requires; ours adds the
  content-reconciliation test require — same kind of edit, likely a clean
  3-way merge or a trivial both-added-a-line resolve on the source branch.
- Verify live: it's a render-resilience feature, so e2e should load a character
  / the feature tab and confirm no black screen + the recovery panel path.
