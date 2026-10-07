# Branch inventory

Every branch on the fork (`origin`, codeGlaze/orcpub) and every local-only branch, with what it is and whether it can go. Current state first; the deletion log is at the bottom.

**How a branch was judged.** Commit reachability is not enough here: `claude/*` branches are source material and are never merged, and squash or rebuilt merges change commit ids. A branch counts as contained when `git cherry` finds every commit's change on `integration`; otherwise it was checked by content (see [claude-branch-triage.md](claude-branch-triage.md)). Nothing is deleted without the owner's OK.

Last updated 2026-10-06. Re-run before trusting a row: branches move.

## Trunk (9)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `agents/develop` | remote | 2026-10-06 | `a2f3d3c62b` | worktree | agent KB and tooling |
| `develop` | remote | 2026-09-07 | `15e1fe0489` | worktree | fork trunk; integration lands here via #32 |
| `integration` | remote | 2026-10-05 | `63d63add36` | open PR | pending-release trunk |
| `integration-local` | local-only | 2026-10-02 | `a078c78855` | worktree | local integration staging (worktree orcpub-integration) |
| `integration/colon-keyword` | local-only | 2026-08-11 | `4812ae61bf` | - | local integration branch for the colon-keyword fix |
| `master` | remote | 2019-01-08 | `aedcc74088` | - | legacy default (2019) |
| `mirror/upstream-develop` | remote | 2026-04-09 | `d42e05d1a0` | - | mirror of upstream develop |
| `srd52/develop` | local-only | 2026-10-05 | `1048e28d89` | worktree | SRD 5.2 line |
| `testing/develop` | remote | 2026-07-05 | `81d5f408a2` | worktree | testing-tools trunk |

## Release (1)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `contrib/summer-fixes` | remote | 2026-08-12 | `4171aa5032` | - | head of upstream PR #674 (stale; refresh before release) |

## Active (3)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `claude/artist-profile-pages` | remote | 2026-10-04 | `4e21787c2c` | - | Artist profile pages; commits 2026-10-04 |
| `claude/character-portrait-generator-hOutO` | remote | 2026-10-06 | `a84a731b73` | open PR | Paper-doll portrait compositor; PR #36, not done |
| `claude/fix-brave-export-bug-2Tt7j` | remote | 2026-09-13 | `f1bda173a3` | - | Rescue-script work added 2026-09-13; export handoff ("stringification") not yet in the KB |

## Unique work (11)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `claude/app-speed-fix-K8HwV` | remote | 2026-01-17 | `e8613ad66c` | - | Search index over homebrew content for Orcacle |
| `claude/check-character-field-zGYRJ` | remote | 2026-04-20 | `0b87487780` | - | default-plugin-source and small builder changes |
| `claude/cloud-drive-integration-SC31k` | remote | 2026-01-15 | `07f086c63d` | - | Google Drive sync, OAuth PKCE; no tests |
| `claude/docker-setup-errors-0ntths` | remote | 2026-08-06 | `ba54c17bc9` | - | Site-injected homebrew with content-hash caching |
| `claude/fix-ac-calculation-bugs-AHrs2` | remote | 2026-02-02 | `b42acbb5e9` | - | Robe of the Archmagi AC fix (3 lines, bug still live); rest of the branch is old docs and tests, do not merge |
| `claude/fix-character-notes-merge-4YNzf` | remote | 2026-04-26 | `702fffc710` | - | Tests pinning a level-up staleness bug |
| `claude/fix-pdf-export-GDM5n` | remote | 2026-04-10 | `7735fbdbc1` | - | Export-all validation |
| `claude/fix-pdf-widget-warnings-hUt9i` | remote | 2026-04-20 | `24ff01d6ab` | - | Fix landed; its tests and interactive-PDF coverage did not |
| `claude/improve-test-coverage-hlyhp` | remote | 2026-02-02 | `2579372abd` | - | Partner of the AC branch; test coverage and the ?ac-fns argument |
| `claude/loading-spinner-stuck-cXA6l` | remote | 2026-05-25 | `427777acde` | - | One test and about 46 lines in common, entity and subs |
| `claude/upgrade-class-creation-zYV46` | remote | 2026-01-11 | `ce4e7297b7` | - | Declarative class resources (rage, resource pools) |

## Decide (4)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `claude/add-color-themes-gyRhI` | remote | 2026-01-22 | `9e13293b5e` | - | Nord theme system, unique, never landed |
| `claude/custom-race-builder-3NxN5` | remote | 2026-02-02 | `b8116e518d` | - | Custom-race ASI distribution; probably superseded by the grant vocabulary |
| `claude/dazzling-dirac-byig77` | remote | 2026-06-10 | `9641bc3c96` | - | Readable import-error messages; partly landed |
| `claude/image-poisoning-spa-1dkpva` | remote | 2026-06-10 | `7584db7629` | - | Standalone image-cloaking app, unrelated to OrcPub |

## Landed, held by a worktree (1)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `claude/character-black-screen-feature-i8lvk3` | remote | 2026-06-10 | `9869d07a4d` | worktree | Fully on integration; delete after removing worktree orcpub-blackscreen |

## Contained, kept for a worktree or PR (8)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `docs/whats-new-summer-patch` | remote | 2026-10-05 | `1cdf10c363` | fully contained in integration; kept only for its worktree or PR |
| `feature/auth-redesign` | local-only | 2026-09-20 | `0b6b5c32fe` | fully contained in integration; kept only for its worktree or PR |
| `feature/homebrew-data-preservation` | remote | 2026-07-05 | `b7b6deec02` | fully contained in integration; kept only for its worktree or PR |
| `feature/mobile-layout` | local-only | 2026-09-15 | `93100ba6c4` | fully contained in integration; kept only for its worktree or PR |
| `feature/name-keyword-fix` | remote | 2026-06-09 | `06379312e6` | fully contained in integration; kept only for its worktree or PR |
| `fix/pdf-render-and-efficiency` | local-only | 2026-08-08 | `1120c44d48` | fully contained in integration; kept only for its worktree or PR |
| `hotfix/locale-safety` | remote | 2026-10-04 | `80892599d3` | fully contained in integration; kept only for its worktree or PR |
| `refactor/garden-harvest` | local-only | 2026-09-16 | `2c9c553d76` | fully contained in integration; kept only for its worktree or PR |

## Lives in another trunk (10)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `claude/zen-wright-04xhdz` | local-only | 2026-06-29 | `ba79bc5c7e` | - | contained in agents/develop srd52/develop feature/grant-rows |
| `dmv/hotfix-integrations` | remote | 2026-03-01 | `ff02c02f01` | - | contained in gitea/dmv |
| `feature/demo-content-tier` | remote | 2026-09-02 | `33b6f10eca` | - | contained in agents/develop srd52/develop feature/grant-rows |
| `feature/fighting-style-authoring` | remote | 2026-09-07 | `d778d1e55a` | - | contained in agents/develop srd52/develop feature/grant-rows |
| `feature/grant-rows` | remote | 2026-10-06 | `643b2de7d4` | - | contained in feature/grant-rows |
| `name-keyword-fix-on-dmv` | local-only | 2026-06-09 | `85c4b8d944` | - | contained in gitea/dmv |
| `refactor/content-extensibility` | remote | 2026-09-30 | `789aaa24a7` | - | contained in srd52/develop feature/grant-rows |
| `share/embedded-content-dmv` | local-only | 2026-08-10 | `e2ad5be4a8` | - | contained in gitea/dmv |
| `staging/june-bug-patches-01` | local-only | 2026-07-08 | `c679a3fa80` | worktree | contained in gitea/dmv |
| `summer-update` | local-only | 2026-08-08 | `073e3802f5` | - | contained in gitea/dmv |

## Unmerged, not triaged (28)

| branch | where | last commit | tip | attached | note |
|---|---|---|---|---|---|
| `ci/fix-workflow` | remote | 2026-02-17 | `b3af50edcf` | - | 18% of its added lines are on integration |
| `claude-source-backup/custom-class-source-error` | local-only | 2026-07-02 | `bfa1fb35e0` | - | 29% of its added lines are on integration |
| `debug/develop-01` | remote | 2026-02-02 | `3741349d3f` | - | 39% of its added lines are on integration |
| `dev-codespace` | remote | 2026-01-01 | `398404f036` | - | 12% of its added lines are on integration |
| `docs/browser-testing-kb` | remote | 2026-09-01 | `aba2cdff2d` | - | 1% of its added lines are on integration |
| `docs/comment-style-tense` | remote | 2026-08-31 | `39546b4610` | - | 1% of its added lines are on integration |
| `docs/error-handling-learnings` | remote | 2026-02-04 | `1464f55d03` | - | 30% of its added lines are on integration |
| `docs/orcbrew-format` | remote | 2026-09-20 | `c848cef22f` | - | 1% of its added lines are on integration |
| `feature/character-rescue` | remote | 2026-10-06 | `269f989482` | - | 1% of its added lines are on integration |
| `feature/cross-platform-scripts` | remote | 2026-02-24 | `7378745a57` | - | 0% of its added lines are on integration |
| `feature/extras-companions` | remote | 2026-10-01 | `c655fa7e93` | - | 2% of its added lines are on integration |
| `fix/custom-item-classification` | remote | 2026-08-30 | `b234db2bb1` | - | 6% of its added lines are on integration |
| `fix/fa4-icon-names` | remote | 2026-09-20 | `71ba828431` | - | 27% of its added lines are on integration |
| `fix/hidden-multiclass-skill-pick` | remote | 2026-10-06 | `74fcb11a75` | - | 1% of its added lines are on integration |
| `fix/item-stable-identity` | remote | 2026-08-26 | `b00945684b` | - | 0% of its added lines are on integration |
| `fix/windows-port-detection` | remote | 2026-09-18 | `8fbf98f510` | - | 92% of its added lines are on integration |
| `name-generators` | remote | 2025-12-10 | `7be6733320` | - | 0% of its added lines are on integration |
| `patch/decoded-link-render-and-qol` | remote | 2026-08-16 | `f57f0296f4` | - | 75% of its added lines are on integration |
| `perf/entity-build` | remote | 2026-09-04 | `999df1c6ec` | - | 4% of its added lines are on integration |
| `perf/memoize-audit` | remote | 2026-09-06 | `cde510c263` | - | 36% of its added lines are on integration |
| `port/redesign-on-refactor` | remote | 2026-07-15 | `29f3809394` | worktree | 26% of its added lines are on integration |
| `premerge/integration-to-dmv` | local-only | 2026-09-07 | `199cab0535` | - | 3% of its added lines are on integration |
| `redesign/growable-option-menus` | remote | 2026-06-24 | `c1f4fcf465` | - | 22% of its added lines are on integration |
| `refactor/data-extraction` | remote | 2026-02-25 | `4da1d84704` | - | 0% of its added lines are on integration |
| `refactor/garden-inline-styles` | remote | 2026-07-03 | `59c2290298` | worktree | 56% of its added lines are on integration |
| `refactor/views-extraction` | remote | 2026-02-24 | `c2af99bb02` | - | 0% of its added lines are on integration |
| `testing/dev-tooling-enhancements` | remote | 2026-02-04 | `266362f0f6` | - | 1% of its added lines are on integration |
| `upgrade/datomic-pro` | remote | 2026-01-23 | `4ac0d1378c` | - | 18% of its added lines are on integration |

## Left alone locally

- `claude/zen-wright-04xhdz` (local copy): its fork branch was deleted, but the local copy holds 160 commits not on `integration`. The content is on the refactor line per the triage; check before deleting.
- `claude-source-backup/custom-class-source-error`: its fork branch is gone and it was on no approved list.

## Deleted

Restore any of these with `git push origin <tip>:refs/heads/<branch>` (or `git branch <branch> <tip>` for a local-only one), as long as the commit still exists in a clone.

### 2026-10-06 (65 branches)

Two lists approved by the owner: 18 `claude/*` branches whose work is on `integration`, on another branch or in the KB, and 48 branches fully contained in `integration` with no worktree or open PR (one branch was on both). Each contained branch was re-checked with `git cherry` immediately before deletion. Six local copies of deleted branches, also contained, went too.

| branch | where | tip | last commit | why it could go | what was on it |
|---|---|---|---|---|---|
| `breaking/2026-stack-modernization` | remote | `ba79f0578d` | 2026-03-05 | contained in integration | fix: sudo rm for H2 data — datomic user owns the files;  |
| `bugfix/menu-flyout` | remote | `aad7116b79` | 2026-03-30 | contained in integration | fix: consolidate .env.* gitignore rules into single glob pattern;  |
| `claude/fix-custom-items-disappearing-DW8rb` | remote | `509431f2e0` | 2026-04-14 | contained in integration | docs: finalize investigation notes with execution outcome; test/cljs(4) src/cljs(4) test/cljc(1) |
| `consolidate/notifications-and-exports` | remote | `217c41625b` | 2026-09-10 | contained in integration | Merge remote-tracking branch 'origin/integration' into consolidate/notifications-and-expor;  |
| `copilot/dependencies-develop` | remote | `d42e05d1a0` | 2026-04-09 | contained in integration | Merge pull request #668 from datdamnzotz/fixmutliclass-humansubraces;  |
| `docs/spec-docstrings-spellbook-e2e` | remote | `36b65ddaa6` | 2026-10-05 | contained in integration | Browser suites: doc comments on the helpers, and an accurate account of the share wait;  |
| `docs/summer-patch-changelog` | remote | `7e6734c2f4` | 2026-10-05 | contained in integration | fold-branch-changelog: Security lines are plain, so no area-label warning for them;  |
| `feat/boot-shell-rescue` | remote | `7bd0d7bc2c` | 2026-09-08 | contained in integration | Read the store at the moment of rescue, not at page load;  |
| `feat/option-picker` | remote | `b2af3e453c` | 2026-09-07 | contained in integration | Exercise the busy-export profile, and stop the runner mixing the two;  |
| `feat/source-tagged-keys` | remote | `ed4f5a4c7d` | 2026-09-18 | contained in integration | Mint every key with its source's tag, and let a source choose that tag;  |
| `feat/starting-equipment` | remote | `a0c004d9b6` | 2026-08-31 | contained in integration | docs: bring branch changelog up to date;  |
| `feat/whats-new-panel` | remote | `9e3017d3d6` | 2026-09-06 | contained in integration | Cover the whole release in the panel, under three headings;  |
| `feature/browser-side-character-images` | remote | `a41421c08f` | 2026-09-06 | contained in integration | Fold the branch changelog into Summer Patch;  |
| `feature/content-library-management` | remote | `893e578afb` | 2026-08-27 | contained in integration | docs(kb): releases are tags on integration, not branches;  |
| `feature/fix-black-screen-of-death` | remote | `4f379aade3` | 2026-06-12 | contained in integration | Document ClojureScript type tolerance and the string-op crash pattern;  |
| `feature/one-template-per-style` | remote | `7536549db8` | 2026-09-05 | contained in integration | Fold the branch changelog into Summer Patch;  |
| `feature/spellbook-print` | remote | `5e57306386` | 2026-10-05 | contained in integration | Fold the spellbook branch changelog into Summer Patch;  |
| `feature/swarm-portainer-support` | remote | `8b85650855` | 2026-03-30 | contained in integration | merge develop — sync with PR #661 and fork develop sync;  |
| `fix/ac-unarmored-natural-stacking` | remote | `f9fb327ff3` | 2026-09-03 | contained in integration | fix(ac): stop unarmored-defense and natural-armor from stacking;  |
| `fix/banner-hiccup-passthrough` | remote | `5559e55ee2` | 2026-09-08 | contained in integration | Stop the banner printing its own markup;  |
| `fix/comment-check` | remote | `48f945b3ac` | 2026-09-29 | contained in integration | Comment check: a note exempts only itself; metadata docstrings are checked;  |
| `fix/comment-debt` | remote | `cbc2e745b5` | 2026-09-30 | contained in integration | Fold the fix/comment-debt changelog into Summer Patch;  |
| `fix/custom-content-false-missing` | remote | `3af551db2b` | 2026-08-25 | contained in integration | docs(kb): map the three custom-content mechanisms; clarify inline vs builder;  |
| `fix/duplicate-key-traps-its-owner` | remote | `e1fef76050` | 2026-09-14 | contained in integration | A standing duplicate key no longer traps the item that owns it; test/cljs(1) src/cljs(1) |
| `fix/e2e-fresh-server-parallel` | remote | `7f0531fc46` | 2026-10-05 | contained in integration | run-all: an interrupted run stops the suites and servers it started;  |
| `fix/filtered-list-staleness` | remote | `fe19d27fe5` | 2026-09-13 | contained in integration | docs: finalize investigation notes with execution outcome;  |
| `fix/get-auth-token-undeclared` | remote | `67a1e18f85` | 2026-09-18 | contained in integration | Qualify the get-auth-token call that no longer resolves;  |
| `fix/header-flyout-under-sticky-toolbar` | remote | `9eb3ac8152` | 2026-09-07 | contained in integration | Put the header dropdowns back above the button row;  |
| `fix/homebrew-save-keys` | remote | `a660345ce4` | 2026-09-13 | contained in integration | Pin the homebrew save lifecycle;  |
| `fix/import-probe-button-labels` | remote | `950ab526c1` | 2026-09-07 | contained in integration | Point the import helper at the buttons that exist now;  |
| `fix/item-builder-save-label` | remote | `024a1952d7` | 2026-09-08 | contained in integration | Stop the item builder promising browser storage it does not use;  |
| `fix/message-banner-spacing` | remote | `e13d2b2f97` | 2026-09-08 | contained in integration | Let the banner keep its line breaks, and give it room;  |
| `fix/modal-and-switch-pass` | remote | `fdeab36839` | 2026-09-29 | contained in integration | Import modals in the app's type and weight; switches for on/off; one icon per section; src/cljs(2) src/clj(1) |
| `fix/my-content-source-toolbar` | remote | `6b90a07cde` | 2026-09-08 | contained in integration | Put a source's search on the same row as its buttons;  |
| `fix/pdf-endpoint-hardening` | remote | `f2d5411d79` | 2026-09-02 | contained in integration | Prove the page-generation scheme on style 4, and correct where the saving comes from;  |
| `fix/tall-flyout-and-held-release` | remote | `2573f97601` | 2026-09-07 | contained in integration | Keep tall menus on screen, and stop holding the release panel too long;  |
| `hotfix/boot-config-report` | remote | `2c6416da73` | 2026-09-07 | contained in integration | Fold the hotfix changelog into Summer Patch;  |
| `hotfix/my-content-dropdown-and-probe-cost` | remote | `61ddd60388` | 2026-09-07 | contained in integration | Make the My Content dropdown readable, and cut the release probe to 30s;  |
| `perf/homebrew-builder-loop` | remote | `f5cf9620f0` | 2026-09-05 | contained in integration | Merge remote-tracking branch 'origin/integration' into perf/homebrew-builder-loop;  |
| `port/save-gate` | remote | `6fdccb6f1f` | 2026-09-30 | contained in integration | Changelog: say where the write gate refuses and where it reports;  |
| `refactor/banner-parts-and-design` | remote | `17658bb5ca` | 2026-09-08 | contained in integration | Keep the safety valve, and guard the export shape with a test;  |
| `refactor/picks-namespace` | remote | `453aff1d71` | 2026-10-04 | contained in integration | picks/put-at: never overwrite a one-pick selection either; nil when nothing is written;  |
| `test/overlay-probe-user-menu-and-modals` | remote | `b031ce527b` | 2026-09-08 | contained in integration | Walk the delete-all guard, and cancel at the last step;  |
| `tooling/changelog-lint` | remote | `cc0f58c2dc` | 2026-09-06 | contained in integration | Lint the changelog's house style, and bring CHANGELOG.md back to it;  |
| `feature/fix-orcbrew-errors` | local-only | `d17a6cbbc2` | 2026-06-13 | contained in integration | build: stop tracking generated styles.css (regenerated by garden);  |
| `feature/password-rules` | local-only | `934f5aa436` | 2026-09-19 | contained in integration | Record the .lein-env clobbering that breaks a parallel e2e boot; test/browser(38) src/cljs(16) src/cljc(12) |
| `locale-signed` | local-only | `89f8d1778e` | 2026-09-20 | contained in integration | Stop the env template from dictating the Datomic URL to both runtimes;  |
| `share/upstream-premerge` | local-only | `9f9edb55d8` | 2026-08-09 | contained in integration | Custom item save persists the shown type default (no more blank type); src/cljs(7) src/cljc(2) test/cljs(1) |
| `claude/fix-single-colon-keyword` | remote | `bab40288cd` | 2026-07-15 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Optional auto-send for the character-load report (gated, with copyable fallback); src/cljs(6) src/cljc(3) src/clj(3) |
| `claude/fix-orcbrew-errors-bLkFJ` | remote | `450726882a` | 2026-06-10 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | feat: require dropdown fields before export, sort to top, highlight unfilled; src/cljs(5) test/cljs(1) src/clj(1) |
| `claude/zen-wright-04xhdz` | remote | `c1c9b33977` | 2026-08-24 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | docs(roadmap): clear the resolved conflict-#1 gate; log the library work (PR #30); docs/kb(31) test/e2e(14) test/cljc(14) |
| `claude/cross-platform-scripts-x3efN` | remote | `5691ef12fc` | 2026-02-24 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Merge remote-tracking branch 'origin/feature/cross-platform-scripts' into claude/cross-pla; src/cljs(19) scripts(14) web(1) |
| `claude/custom-items-magical-fix-9kvywv` | remote | `60283985d4` | 2026-08-25 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Fix two real item-flow bugs found by end-to-end exercises; docs/screenshots(8) test/cljs(5) src/cljs(5) |
| `claude/add-kill-stop-subcommands-LU4GK` | remote | `83e6c0afbe` | 2026-01-22 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Refactor start.sh with subcommands, update menu as pure dispatcher; e2e(15) scripts(6) .vscode(2) |
| `claude/audit-name-to-kw-yVcJn` | remote | `56cf559cde` | 2026-05-19 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | docs: clarify migration cost in name-to-kw audit; docs/kb(1) |
| `claude/review-testing-automation-aFeqE` | remote | `fde25465e8` | 2026-01-17 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Add Codespace testing guide for continuing work from Codespace; e2e(15) .devcontainer(2) .gitignore(1) |
| `claude/faster-frontend-testing-j9G89` | remote | `221fc8cfe2` | 2026-01-17 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Add test output files to .gitignore; .claude(5) .gitignore(1) |
| `claude/agent-branching-skill-SC31k` | remote | `7f694addb6` | 2026-01-14 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Add git-branch skill for creating feature branches from agents/develop; .claude(1) |
| `claude/create-onboarding-skills-5Hz9K` | remote | `c56f517be7` | 2026-04-14 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | add orcpub-primer skill to bridge code branches to agents/develop; .gitignore(1) .claude(1) |
| `claude/update-error-handling-docs-IdYie` | remote | `df2974125f` | 2026-01-16 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | docs: Document conflict modal integration flow; docs/README.md(1) docs/progress.md(1) docs/ORCBREW_FILE_VALIDATION.md(1) |
| `claude/alternate-data-stream-fix-qsigug` | remote | `170807db63` | 2026-08-10 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Ignore Windows Alternate Data Stream (Zone.Identifier) artifacts; .gitignore(1) |
| `claude/investigate-ci-failure-6ouwW` | remote | `2e4554f13f` | 2026-03-01 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | fix: tag built images for compose to prevent BuildKit rebuild hang; .github(1) |
| `claude/half-caster-prepared-spells-Jo0ai` | remote | `ed5e13daa9` | 2026-06-01 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | docs: expand web-handoff with full plan, reasoning trail, verified traces; docs/kb(1) |
| `claude/ui-ux-evaluation-Kngfm` | remote | `36354fccb4` | 2026-04-26 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | docs: expand UI/UX evaluation with branch context, modern UX gap analysis, and implementat; docs/kb(3) docs/plans(1) |
| `claude/explore-fighting-styles-K56lQ` | remote | `d53e36dd1d` | 2026-01-13 | claude/*: landed, copied elsewhere, or superseded with its docs in the KB | Add plugin spec support for fighting styles; docs/analysis(11) src/cljc(3) test-fighting-styles.edn(1) |
| `docs/spec-docstrings-spellbook-e2e` | local copy | - | - | local copy of a deleted contained branch | same as above |
| `docs/summer-patch-changelog` | local copy | - | - | local copy of a deleted contained branch | same as above |
| `feature/fix-black-screen-of-death` | local copy | - | - | local copy of a deleted contained branch | same as above |
| `feature/spellbook-print` | local copy | - | - | local copy of a deleted contained branch | same as above |
| `fix/e2e-fresh-server-parallel` | local copy | - | - | local copy of a deleted contained branch | same as above |
| `tooling/changelog-lint` | local copy | - | - | local copy of a deleted contained branch | same as above |
