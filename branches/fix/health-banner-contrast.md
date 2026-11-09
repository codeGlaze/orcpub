# fix/health-banner-contrast

**Holds:** two CSS fixes (library-health banner text, weapon caret) and the first version of the homebrew
Markdown export. **Base:** `integration`. **PR:** #82 into `integration` (https://github.com/codeGlaze/orcpub/pull/82). Worktree
`/home/codeglaze/projects/orcpub-health`. The name no longer says what it holds, so the PR title must.

## State (2026-11-09)

#82 open, pushed through `bde30372a`. CodeRabbit's one review (2026-11-09) had 10 actionable
comments; all fixed in `e94d4fce6` and `bd08631dc` (tests `fc5c51036`, `ebe3990a4`), each thread
answered with its commit. That push only fixes the review, so it needs no new review. Checks on the
final change: `lein test` 1539, cljs 1007, lint 0 errors (comment test and lint re-run after the
docstring trim in `1b21a7ba3`). Changelog unfolded: one Added line (Markdown export), one Fixed line
(class proficiencies survive the `.orcbrew` export; `docs/kb/class-proficiency-flags.md`).
Plan and decisions: `docs/kb/plan-homebrew-markdown-export.md`.

## Next, in order

1. **Wait for CI and the owner.** Fold the changelog only when the owner says #82 is about to merge.
2. **Piece 2b waits until after the release** (owner, 2026-11-08). **The simple types** (spells, feats, backgrounds, races, subraces, languages, invocations,
   boons): add rows to `formatters` in `homebrew_markdown.cljc`. Budget it before starting: the button
   pass took about 30 minutes and 158k helper tokens and used most of the owner's extra allowance.
   Batch every break-and-prove into one build, run only `lein test orcpub.homebrew-markdown-test` while
   working, and run the full suites once at the end.
3. Later: monsters, items, encounters, and a whole-library file.

## Open

- The button check's "button exists" claim was never seen failing against a bundle with no button.
- The notice now joins "Exported with warnings" (in its details) rather than replacing it; seen only
  in code, not in a browser.
- The cljs harness needs `playwright`, which this worktree lacks: run it with
  `NODE_PATH=/home/codeglaze/projects/orcpub-pwd/node_modules`.
