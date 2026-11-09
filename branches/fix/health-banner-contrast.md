# fix/health-banner-contrast

**Holds:** two CSS fixes (library-health banner text, weapon caret) and the first version of the homebrew
Markdown export. **Base:** `integration`. **PR:** #82 into `integration` (https://github.com/codeGlaze/orcpub/pull/82). Worktree
`/home/codeglaze/projects/orcpub-health`. The name no longer says what it holds, so the PR title must.

## State (2026-11-06)

Pushed, 15 files against `integration`, no PR. All checks passed when the commits were made:
`lein test` 1531, cljs 998, lint 0 errors, the new browser checks. Commits:
`b9ea79a44` CSS fixes; `76399a1fb`, `80c083707`, `39e9ab9aa` the writer and tests;
`38be460a8`, `14a417674` the feature-names-only table and the "export as markdown" button in My Content.
Plan and decisions: `docs/kb/plan-homebrew-markdown-export.md`.

## Next, in order

1. **#82 is open (2026-11-08)**, as it is: classes and subclasses only. Branch changelog written, not
   folded; `heavy-docker: later` label on; one `@coderabbitai full review` requested. Next: answer the
   review per the merge gate. Fold the changelog only when the owner says it is about to merge.
2. **Piece 2b waits until after the release** (owner, 2026-11-08). **The simple types** (spells, feats, backgrounds, races, subraces, languages, invocations,
   boons): add rows to `formatters` in `homebrew_markdown.cljc`. Budget it before starting: the button
   pass took about 30 minutes and 158k helper tokens and used most of the owner's extra allowance.
   Batch every break-and-prove into one build, run only `lein test orcpub.homebrew-markdown-test` while
   working, and run the full suites once at the end.
3. Later: monsters, items, encounters, and a whole-library file.

## Open

- The button check's "button exists" claim was never seen failing against a bundle with no button.
- Whether the Markdown notice and "Exported with warnings" can both show was not checked.
- Equipment choices, multiclass profs and raw `:selections` are not written yet.
