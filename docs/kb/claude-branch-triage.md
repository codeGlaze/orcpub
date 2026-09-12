# Triage of the 37 `claude/*` auto-branches

`claude/` is the harness auto-branch prefix. 37 such branches exist on origin, from sessions
between 2026-01-11 and 2026-09-06. This is the evidence for which of them hold work that exists
nowhere else, so the owner can prune the rest.

**Nothing here was deleted.** Deleting is the owner's call; `git push --delete` is refused in the
sandbox this was produced in anyway.

**References are pinned.** Branch tips as fetched 2026-09-12: `integration` at `36766010`,
`develop` at `15e1fe04`, `agents/develop` at `ac0fe313`, `refactor/content-extensibility` at
`f97567fb`, `perf/entity-build` at `999df1c6`, `feature/fighting-style-authoring` at `d778d1e5`,
`feature/demo-content-tier` at `33b6f10e`. Every `claude/*` tip is in the verdict table.

## How this was established, and why reachability was not enough

`git merge-base --is-ancestor` reports all 37 as unmerged into `integration`, `develop` and
`master`. **That answer is wrong for four of them and misleading for five more.** Squash- and
rebase-merges rewrite SHAs, so reachability cannot see work that landed under a different commit.

Each branch was therefore judged by content:

1. Diff it against its merge-base with `integration`; keep only `src/`, `test/`, `scripts/` paths.
2. Extract the `defn`/`def`/`defmacro`/`deftest` names it *introduces* — names absent from the
   file as it stood at that merge-base. Filtering against the base is load-bearing: without it the
   test reports 100% everywhere, because a diff's `+` lines are full of pre-existing namespaced
   keywords and re-indented code. An unfiltered run scored
   `claude/character-black-screen-feature-i8lvk3` at 17/17 on every branch in the repo, which says
   nothing.
3. Look for each of those names in the same files on all 99 other branches.

A branch whose new definitions are all present elsewhere has landed, whatever the SHAs say. A
branch scoring 0 everywhere holds work that exists in exactly one place.

Whole-file blob comparison (`git ls-tree -r` across every branch, then compare object IDs) was used
alongside it — byte-identical files are the strongest evidence of a cherry-pick, and they caught
the `zen-wright` and `cross-platform-scripts` cases before the symbol test ran.

Clojure searches used `scripts/clj-grep.py`, not `grep`: `#_` discards the next form, so switched-off
code reads as live.

## Corrections to the starting premise

Three assumptions this triage began from turned out to be false, and each changed a verdict.

**"None are merged" is false.** Four branches are fully present on `integration` under rewritten
SHAs — see the *Already merged* section. They are the cheapest deletions in the set.

**The AC engine is not on either AC-adjacent `claude/*` branch.**
`src/cljc/orcpub/dnd/e5/armor_class.cljc` exists on exactly three branches —
`perf/entity-build` (blob `5f7d4117`), `feature/fighting-style-authoring` and `feature/grant-rows`
(both `21383e23`) — and on none of the 37. Checking
`claude/fix-ac-calculation-bugs-AHrs2` and `claude/explore-fighting-styles-K56lQ` "against those
blobs specifically" has no answer: neither branch has the file, or any file like it. What those two
branches hold is described below, and one of them still matters.

**A `claude/*` branch's headline work is not always its own.**
`claude/cross-platform-scripts-x3efN` merged `origin/refactor/views-extraction` into itself. The
5,956-line `views.cljs` split that dominates its diffstat is inherited, byte-identical, and was
never that branch's contribution. Commit count and diffstat both mislead here; three of its commits
appear twice under different SHAs (`03f88dbc`/`388da619`, `a6990423`/`c6978ae4`,
`6056d137`/`62fdfc17`), a cherry-pick duplication.

## The one finding worth acting on

**Robe of the Archmagi computes AC as an additive +5 and should be a competing calculation.**
`magic_items.cljc:2261-2265` on `integration` registers it with `mod5e/ac-bonus-fn`:

```clojure
(mod5e/ac-bonus-fn (fn [armor shield] (if (nil? armor) 5 0)))
```

The item's own description, three lines below at `:2268`, says *"If you aren't wearing armor, your
base Armor Class is 15 + your Dexterity modifier."* That is a whole AC formula, not a bonus. The
two forms agree at `10 + Dex + 5 = 15 + Dex` for a character with no other AC calculation, and
diverge the moment one competes: a Draconic Sorcerer (`13 + Dex`) or a Barbarian using Unarmored
Defense gets the +5 stacked onto the winner instead of the Robe competing with it.

This is live in three places and fixed in none:

| branch | Robe registration | has `ac-formula`? |
|---|---|---|
| `integration` `36766010` | `ac-bonus-fn`, returns 5 | no — only `ac-bonus-fn`, `modifiers.cljc:576` |
| `perf/entity-build` `999df1c6` | `ac-bonus-fn`, returns 5 | yes, `modifiers.cljc:584` |
| `feature/fighting-style-authoring` `d778d1e5` | `ac-bonus-fn`, returns 5 | yes, `modifiers.cljc:610` |

The two engine branches have the mechanism to express it correctly and do not use it for this item.
No test asserts the Robe's AC anywhere: the four "robe" hits in
`perf/entity-build:test/cljc/orcpub/dnd/e5/ac_reconciliation_test.clj` are the word *wardrobe* in a
benchmark fixture.

`docs/kb/armor-class-refactor.md` on `perf/entity-build` tabulates the Robe at `:483` as an example
of the predicate-gated bonus form, presenting the current mechanism as correct. It does not record
the RAW mismatch. **That is the gap this triage found: the bug is undocumented, untested, and live
on every branch that ships.**

`claude/fix-ac-calculation-bugs-AHrs2` carries a working fix (`magic_items.cljc`, +3/-2), moving it
to a `mod5e/ac-fn` that returns `(+ 15 Dex (shield))`. Salvage the three lines, not the branch.

### What is already fixed, so don't re-derive it

The *other* AC bug those branches found — natural armor and Unarmored Defense stacking — was fixed
independently on `fix/ac-unarmored-natural-stacking` (`f9fb327f`), which **is** an ancestor of
`integration`. The fix is the symmetric pair of `if`s at `template_base.cljc:65-67` and `:68-72`,
not the `?ac-fns` architecture the branch recommended, and `test/cljc/orcpub/dnd/e5/bracers_ac_test.clj`
covers the Bracers-of-Defense regression that the simpler fix introduced. The branch's AC test
scenarios — tortle, lizardfolk, monk, barbarian, draconic, shield interactions — are all covered by
`ac_characterization_test.clj` and `ac_reconciliation_test.clj` on `perf/entity-build`.

The architecture those branches recommended did land, under a different name. Commit `d8561267`
("Retract max() fix suggestion, recommend ?ac-fns approach", 2026-02-02) on
`claude/improve-test-coverage-hlyhp` argued for reviving `?ac-fns` so each AC source registers a
self-contained formula compared via `max`. `armor_class.cljc` on `perf/entity-build` is exactly
that, registered through `mod5e/ac-formula`. The design is superseded by its own implementation.

## Verdicts

`SUPERSEDED` = the content was verified present elsewhere. `MERGED` = present on `integration`
despite the ancestor check. `SALVAGE` = new definitions found on no other branch.
"new defs" is the step-2 count: definitions the branch introduces / how many were found elsewhere.

### Already merged into `integration` — reachability says otherwise (4)

| branch | tip | date | evidence |
|---|---|---|---|
| `claude/character-black-screen-feature-i8lvk3` | `9869d07a` | 2026-06-10 | 22/22 new defs live on `integration` via `feature/fix-black-screen-of-death` (merged). `render-guard` `views.cljs:1872`, `blank-feature-name?` `:3847`, `suspected-broken-features` `:3855`, `error-boundary` `:3948`, `character-health-warning` `:4034` |
| `claude/fix-cantrips-selection-bug-CSwVv` | `edf9655b` | 2026-06-01 | 15/15 live on `integration` via `feature/name-keyword-fix` (merged). `class->expected-spell-keys` `content_reconciliation.cljs:295`, `reconcile-spell-selection-keys` `:334` |
| `claude/fix-single-colon-keyword` | `bab40288` | 2026-07-15 | 18/18 live on `integration`. `sanitize-edn-colons` `common.cljc:16`, `safe-edn-decode` `http_safe.cljs:38`, `wrap-safe-edn-response` `:53` |
| `claude/fix-orcbrew-errors-bLkFJ` | `45072688` | 2026-06-10 | 171/171 on `develop`; 169/171 on `integration`. The two not on `integration` are `format-key-conflict-section` and the test `test-generate-new-key` |

### Superseded — content verified elsewhere (5)

| branch | tip | date | evidence |
|---|---|---|---|
| `claude/zen-wright-04xhdz` | `c1c9b339` | 2026-08-24 | 183 commits, 60 code files. 99% of added lines on `refactor/content-extensibility`, `perf/entity-build` and `feature/demo-content-tier`; 2% on `integration`. 49/60 files byte-identical to `refactor/content-extensibility`, including all of `content_types.cljc`, `builder_fields.cljc`, `content_pools.cljc`, `field_schemas.cljc` and the whole `test/e2e/` set. The only lines not present are comment rewordings in `events.cljs`; the substance (`:strict-unfilled`) is there, twice |
| `claude/cross-platform-scripts-x3efN` | `5691ef12` | 2026-02-24 | 100% of added lines on `feature/cross-platform-scripts`; 32/34 files byte-identical, including every `scripts/windows/*.ps1`. The views split is inherited from `refactor/views-extraction` — all 19 `views/` modules byte-identical there too |
| `claude/custom-items-magical-fix-9kvywv` | `60283985` | 2026-08-25 | 105/105 new defs on `fix/custom-item-classification`; 0/105 on `integration` or `develop` |
| `claude/add-kill-stop-subcommands-LU4GK` | `83e6c0af` | 2026-01-22 | 100% of added lines on `testing/develop` and `testing/dev-tooling-enhancements`; 6/7 files byte-identical (all of `scripts/git/`). 0% on `integration` |
| `claude/audit-name-to-kw-yVcJn` | `56cf559c` | 2026-05-19 | Its only file, `docs/kb/name-to-kw-audit.md`, is byte-identical on `agents/develop` (blob `fe43b26c`) |

Note that `feature/cross-platform-scripts`, `fix/custom-item-classification`, `testing/develop` and
`testing/dev-tooling-enhancements` are themselves unmerged. Deleting the `claude/*` copies loses
nothing; the work still needs a decision on its own branch.

### Salvage — 0 matches anywhere (11)

| branch | tip | date | what is unique | new defs |
|---|---|---|---|---|
| `claude/character-portrait-generator-hOutO` | `63eddd2f` | 2026-09-06 | Layered SVG portrait compositor: `portrait.cljs` (888), `portrait_assets.cljc` (343), `portrait_render.clj` (157), 6 test files. Distinct from `feature/browser-side-character-images`, which fetches and captures an existing image rather than generating one | 108/0 |
| `claude/cloud-drive-integration-SC31k` | `07f086c6` | 2026-01-15 | Google Drive sync: OAuth PKCE (`generate-code-challenge`, `exchange-code-for-token`), file browser, 4 files under `src/cljs/orcpub/cloud/`. No tests | 41/0 |
| `claude/docker-setup-errors-0ntths` | `ba54c17b` | 2026-08-06 | Site-injected homebrew: `read-site-homebrew`, `load-site-plugins`, content-hash cache versioning (`sha1-hex`, `version-changes-with-content`), plus `config_test.clj` | 23/0 |
| `claude/fix-custom-items-disappearing-DW8rb` | `509431f2` | 2026-04-14 | `api_subs.cljs`: `reg-api-sub`/`reg-filtered-sub` abstraction plus 401/500 handling, and `equipment_subs_test.cljs`. Both paths unique | 20/0 |
| `claude/fix-brave-export-bug-2Tt7j` | `3a1f5bfb` | 2026-06-10 | Pre-export validation gate: `validate-all-plugins-before-export`, `bulk-export-fx`, 8 tests | 15/0 |
| `claude/fix-pdf-export-GDM5n` | `7735fbdb` | 2026-04-10 | `fill-missing-for-export-all`, `valid-item-key?`, export-all validation | 10/0 |
| `claude/custom-race-builder-3NxN5` | `b8116e51` | 2026-02-02 | Custom-race ASI distribution in `options.cljc` (+202/-14, one file). **Design is probably superseded** — the refactor tree does the same job through the declarative grant vocabulary (`ability_increase_grant_test.clj`, `test/e2e/race-builder-asi.js`). The named functions exist nowhere, but check the grant vocabulary before salvaging | 5/0 |
| `claude/upgrade-class-creation-zYV46` | `ce4e7297` | 2026-01-11 | `rage-modifiers`, `class-resource-pools`, `expand-props-with-levels` — declarative class resources | 5/0 |
| `claude/fix-character-notes-merge-4YNzf` | `702fffc7` | 2026-04-26 | Tests only. Pins a level-up staleness bug: `level-up-staleness-character-slot-lags-character-map` | 5/0 |
| `claude/check-character-field-zGYRJ` | `0b874877` | 2026-04-20 | `default-plugin-source` plus small `character_builder.cljs` changes (+50/-48) | 1/0 |
| `claude/loading-spinner-stuck-cXA6l` | `427777ac` | 2026-05-25 | One test, `test-name-to-kw`, plus 46 substantive lines across `common.cljc` / `entity.cljc` / `subs.cljs`. Smallest salvage in the set | 1/0 |

### Salvage — partial matches, unique core (5 branches, 4 rows)

| branch | tip | date | what is unique |
|---|---|---|---|
| `claude/app-speed-fix-K8HwV` | `e8613ad6` | 2026-01-17 | Plugin-content search index for Orcacle: `::e5/plugin-content-index`, `::e5/plugin-content-by-type`, `::e5/search-plugin-content` in `spell_subs.cljs` (+234). The index subscriptions appear on **no other branch in the repo**, `perf/*` included. `::e5/plugin-vals` does exist on `integration`; the index built on it does not. Also `lazy-spell-help?` |
| `claude/dazzling-dirac-byig77` | `9641bc3c` | 2026-06-10 | Spec-error humanization for import validation: `humanize-pred`, `describe-location`, `format-missing-fields-issues`, `leaf-spec-name`, 6 tests. 2/18 elsewhere, both trivial helpers |
| `claude/fix-pdf-widget-warnings-hUt9i` | `24ff01d6` | 2026-04-20 | AcroForm widget page-reference repair: `fix-widget-page-refs!` and 4 tests for it, plus interactive-vs-flattened PDF coverage and `routes_pdf_test.clj`. 3/31 elsewhere |
| `claude/fix-ac-calculation-bugs-AHrs2` + `claude/improve-test-coverage-hlyhp` | `b42acbb5`, `2579372a` | 2026-02-02 | See *The one finding worth acting on*. These two forked from a shared base at `0a675645`; the AC branch is 6 commits ahead, the coverage branch 3, and they are **not** ancestors of each other. 60/62 and 68/130 of their new defs match only the other one. Two things exist nowhere else: the Robe fix in `magic_items.cljc`, and `test/cljc/orcpub/dnd/e5/magic_items_integration_test.clj` (867 lines, on the coverage branch only). **Delete neither until the Robe fix is lifted** |

### Discard (12)

Agent tooling and CI config, superseded by `agents/develop`, or off-topic. None touches `src/`.

| branch | tip | date | holds |
|---|---|---|---|
| `claude/image-poisoning-spa-1dkpva` | `7584db76` | 2026-06-10 | `image-shield/` — a standalone Glaze/Nightshade-style image-cloaking SPA, 898 lines, unrelated to orcpub. Belongs in its own repo if it is wanted at all |
| `claude/review-testing-automation-aFeqE` | `fde25465` | 2026-01-17 | Playwright/devcontainer harness under `e2e/`, superseded by `test/browser/` and `docs/kb/fast-browser-probes.md` |
| `claude/faster-frontend-testing-j9G89` | `221fc8cf` | 2026-01-17 | `.claude/` test tooling, superseded by `agents/develop` |
| `claude/agent-branching-skill-SC31k` | `7f694add` | 2026-01-14 | An earlier `.claude/skills/git-branch/SKILL.md` — `agents/develop` carries a different, later blob (`081c2b87` vs `61b668f5`) |
| `claude/create-onboarding-skills-5Hz9K` | `c56f517b` | 2026-04-14 | `.claude/skills/orcpub-primer/SKILL.md`, superseded by `START-HERE.md` |
| `claude/update-error-handling-docs-IdYie` | `df297412` | 2026-01-16 | Seven `docs/*.md`, superseded by `docs/kb/error-handling-import-validation.md` |
| `claude/alternate-data-stream-fix-qsigug` | `170807db` | 2026-08-10 | `.gitignore`, 8 lines |
| `claude/investigate-ci-failure-6ouwW` | `2e4554f1` | 2026-03-01 | 10 lines of `docker-integration.yml` |
| `claude/add-color-themes-gyRhI` | `9e13293b` | 2026-01-22 | **Judgement call, not a content finding.** 16 new defs, 0 elsewhere: a Nord theme system (`themes.clj`, 739 lines; `colors.clj`). Genuinely unique and genuinely unlanded, at a base 8 months stale, against a `styles/core.clj` that has moved a long way. Discard unless the theme is still wanted, in which case treat it as a rewrite brief rather than a merge |
| `claude/half-caster-prepared-spells-Jo0ai` | `ed5e13da` | 2026-06-01 | `docs/kb/web-handoff.md` (519 lines), not on `agents/develop`. A session handoff, not a finding — see below |
| `claude/ui-ux-evaluation-Kngfm` | `36354fcc` | 2026-04-26 | `docs/kb/ui-ux-evaluation.md`, `ui-ux-plan.md`, `docs/plans/ui-ux-evaluation-plan.md`, none on `agents/develop`. Read before discarding |
| `claude/explore-fighting-styles-K56lQ` | `d53e36dd` | 2026-01-13 | Design superseded: its props-based `fighting_styles.cljc` (26 lines) is replaced by the content-type + grant-pool architecture on `feature/fighting-style-authoring` (`docs/kb/fighting-style-authoring.md`). **But** ~8,000 lines under `docs/analysis/` catalogue the official TCE and homebrew TGS2 fighting styles with mechanical analysis, and no KB doc on any branch mentions Blind Fighting, Superior Technique or Unarmed Fighting. Lift the catalogue if the class-selection half of that feature is still being built |

Three of these carry `docs/kb/` files that exist on no other branch
(`web-handoff.md`, `ui-ux-evaluation.md`, `ui-ux-plan.md`). They are listed as discard because a
handoff and an unexecuted UI plan are not findings under
[documentation-discipline.md](documentation-discipline.md), not because their content was checked
and found duplicated. If any of it should survive, it has to be moved to `agents/develop` before
the branch goes.

## What is not known

- **Whether the salvage branches still apply.** Nothing here was compiled or run; the sandbox has
  no Clojure toolchain. Bases range from 2026-01 to 2026-09 and several are far behind
  `integration`. "Unique" means the code exists in one place, not that it merges or works.
- **Whether `claude/custom-race-builder-3NxN5` is genuinely unique.** Its functions are absent
  everywhere, but the declarative grant vocabulary on the refactor tree covers the same ground by
  other means. Someone who knows that vocabulary should make the call.
- **Which branch each squash-merge actually landed through.** `feature/fix-black-screen-of-death`
  and `feature/name-keyword-fix` are named as the vehicles because they are merged and carry the
  same symbols. It was not traced commit by commit, and it does not change the verdict.
