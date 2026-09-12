# Knowledge that only exists on branches slated for deletion

Companion to [claude-branch-triage.md](claude-branch-triage.md). That doc asked which branches hold
unsaved *code*. This one asks which hold unsaved *knowledge* — and corrects two of its conclusions.

Scope: the 37 `claude/*` branches, plus the 31 non-`claude` branches already merged into a trunk
(deletable today), plus the unmerged branches the triage identified as duplicates. 217 doc files
were enumerated by diffing each branch against its merge-base with `integration` and comparing every
blob against all 99 other branches.

**References pinned**, fetched 2026-09-12: `agents/develop` at `6e7ff10b`, `integration` at
`36766010`, `develop` at `15e1fe04`, `feature/grant-rows` at `a24030b3`,
`feature/fighting-style-authoring` at `d778d1e5`, `perf/entity-build` at `999df1c6`. Branch tips for
each `claude/*` doc are given in the tables.

## The headline: agents/develop holds 70 of the repo's 128 KB docs

`docs/kb/*.md` exists on 49 branches. Counting distinct paths:

| | |
|---|---|
| KB docs anywhere in the repo | **128** |
| on `agents/develop` | 70 |
| **missing from `agents/develop`** | **58** |

Of those 58, five exist *only* on `claude/*` branches and die with them. The other 53 sit on
non-`claude` branches — a larger, quieter drift that deleting branches will eventually turn into the
same problem.

**`feature/grant-rows` (77 docs) is a bigger KB than `agents/develop` (70).** So is
`feature/fighting-style-authoring` (71). The silo that exists to hold the knowledge base is the
third-largest copy of it.

### Why the guard did not catch this

`scripts/check-docs.sh` §3 compares `docs/` on `origin/develop` against the working branch, and
nothing else:

```bash
comm -23 <(git ls-tree -r --name-only origin/develop -- docs/ | sort) ...
```

`develop` carries no `docs/kb/` at all — commit `042fd072` (2026-07-04, *"docs: keep docs/
human-facing, move the KB off the main line"*) removed it. So the superset check compares against an
empty set and passes unconditionally. Feature branches are never examined. The check has been
structurally incapable of detecting KB drift since the day the KB moved.

## A doc was deleted by the commit that was supposed to relocate it

`042fd072` removed two files: `docs/kb/README.md` and **`docs/kb/datomic-crash-analysis.md` (204
lines)**. Its message says the KB "belongs on the `agents/develop` silo". The README's content was
folded in. The crash analysis was not — `git log origin/agents/develop -- docs/kb/datomic-crash-analysis.md`
shows it created at `856947a7` and deleted at `042fd072`, never re-added.

It is absent from `integration`, `develop`, `master` and `agents/develop`. It survives on **30
branches** cut before 2026-07-04 — 17 of them `claude/*`, 6 already merged and prunable. Once the
prunable ones go it is down to seven stragglers (`dmv/hotfix-integrations`, `docs/browser-testing-kb`,
`docs/comment-style-tense`, `port/redesign-on-refactor`, `redesign/growable-option-menus`,
`refactor/garden-inline-styles`, `testing/develop`), none of which exists to hold documentation.

**Restore it to `agents/develop` before pruning anything.**

## Lift these before deleting

Nine documents. Each was checked against all 167 distinct KB blobs on non-`claude` branches; the
"absent" claims below are from that search, not from memory.

| doc | lines | on branch | why it survives the discipline test |
|---|---|---|---|
| `docs/kb/datomic-crash-analysis.md` | 204 | 30 branches | See above. Deleted in transit |
| `docs/CUSTOM_ITEMS_INVESTIGATION.md` | 1074 | `claude/fix-custom-items-disappearing-DW8rb` `509431f2` | Root cause for Orcpub#669: `filtered-items`/`filtered-spells` were non-reactive. **"filtered-items" and "reactive filter" are absent from every KB doc in the repo.** Five ranked patches with commit-level provenance |
| `docs/kb/web-handoff.md` | 519 | `claude/half-caster-prepared-spells-Jo0ai` `ed5e13da` | The plan for homebrew half-casters with prepared spells. `homebrew-class-spellcasting.md:29` documents the *constraint* (`:prepares-spells?` is hardcoded per `known-mode`, not user-selectable); nothing documents the plan to lift it. This repo keeps plan docs in the KB (`plan-companions-and-wild-shape.md`, `plan-npc-statblock-customizer.md`), so it qualifies. Its own header says the source plan lived in session-scratch and is already gone |
| `docs/kb/feature-tab-black-screen.md` §6 | 286 (§6 ≈ 60) | `claude/character-black-screen-feature-i8lvk3` `9869d07a` | §1–§5 duplicate `fail-soft-rendering.md`. **§6 does not**: a reusable diagnostic playbook — monkeypatching `Array.prototype.sort` to capture the throwing comparator's two operands, decoding cljs maps from JS (`.arr`/`.cnt`, keyword `.fqn`), and pickaxing a shallow clone (`-S` across `--all`, sort by date) when blame dead-ends at a re-import. `Array.prototype.sort` and `bookmarklet` are absent from every KB |
| `docs/issues/character-notes-merge-investigation.md` | 230 | `claude/fix-character-notes-merge-4YNzf` `702fffc7` | Multi-tab contamination of the floating `:character` slot: a tab reload restores `:character` from `localStorage["character"]`, which holds whichever character was saved last *across tabs*, so keystrokes land on the wrong entity. Cites `schema.clj:182-185`, `:234-237`. **"multi-tab" and "local-store-character" are absent from every KB.** Explains a user-visible data-loss report |
| `docs/recovery/single-colon-keyword.md` | 196 | `claude/fix-single-colon-keyword` `bab40288` | `name-to-kw-aux` returns the empty keyword for `""` or apostrophe-only names; one bare `:` makes the whole EDN read throw and the page die. REPL-verified, with the affected character identified. **The fix shipped — `sanitize-edn-colons` is live at `common.cljc:16` on `integration` — but no KB doc mentions "single colon", "empty keyword", or `sanitize-edn-colons`.** Code without its reasoning. Distinct from `keyword-trap-name-repair.md`, which covers number/symbol-leading names |
| `docs/kb/key-vs-name-separation.md` | 87 | `claude/fix-cantrips-selection-bug-CSwVv` `edf9655b` | Overlaps `spell-selection-source-fix.md` and `name-to-kw-audit.md` on the case study and the benign-vs-leak heuristic. **Unique:** the stated rule (`:key` is identity, `:name` is display), the four-site leak table, and the design call that parked orphans are re-derived each load rather than persisted. Lift those three; do not import whole |
| `docs/EXPORT_UNIFICATION_HANDOFF.md` | 142 | `claude/fix-brave-export-bug-2Tt7j` `3a1f5bfb` | The "Brave export bug" was browser-agnostic: a stringification regression at `events.cljs:560`/`:675`. **"stringification" is absent from every KB.** Records a wrong turn (browser-specific) that someone will otherwise re-take |
| `docs/tools/unblock-features-tab.html` | 45 | `claude/character-black-screen-feature-i8lvk3` `9869d07a` | Not prose — a drag-to-install bookmarklet that lets a stuck user open their Features tab before a fix deploys. Exists on no other branch. Keep it or decide deliberately to drop it |

Two more are judgement calls rather than findings:

- **`docs/CONFIGURATION_PATTERN.md`** (153L, `claude/cloud-drive-integration-SC31k` `07f086c6`) — says
  `profiles.clj` is the primary local-dev mechanism for env vars and warns against wrapper
  namespaces. `profiles.clj` appears in no KB doc, though `env-and-auth.md` and
  `dev-tooling-decisions.md` cover the surrounding ground. Low value, small.
- **`docs/kb/ui-ux-evaluation.md` + `ui-ux-plan.md`** (178L + 273L,
  `claude/ui-ux-evaluation-Kngfm` `36354fcc`) — a 2026-04 survey describing `styles/core.clj` at
  ~1600 lines. `refactor/garden-inline-styles` and `redesign/growable-option-menus` have since
  reworked that surface, so the measurements are stale. Read once, then drop unless the plan is
  still wanted.

## Drop these — superseded by better docs that already exist

| corpus | lines | superseded by |
|---|---|---|
| `claude/explore-fighting-styles-K56lQ` `docs/analysis/` + 2 root docs | ~6,944 across 12 files | **`docs/kb/fighting-style-vocabulary-gap.md`** (191L, on `feature/grant-rows` and `feature/fighting-style-authoring`) — measured against the published styles via the 5etools mirror, tabulating 14 distinct styles against the engine hook each needs and whether `:props` can express it today (3 of 14). Shorter, verified, and actually decision-bearing |
| `claude/upgrade-class-creation-zYV46` `docs/` | 6,115 across 8 files | `class-feature-catalogue.md`, `class-features-and-mechanization.md`, `decision-vocabulary.md`, `building-a-class-from-builders.md` (on the refactor tree). The KB catalogue reads all 12 option fns with `file:line` citations and states its exclusions; the branch's 1,516-line version leads with an "Agent ID" header and derived percentage tables |
| `PERFORMANCE_LESSONS.md`, `README_PERFORMANCE.md`, `FUTURE_ENHANCEMENTS.md` | 1,247 | `memoize-antipattern-scan.md`, `perf-entity-build.md` — both cover `memoized-build-aux` |
| everything else | — | 180 of the 217 doc files are byte-identical to a copy on another branch, mostly onboarding scaffolding (`AGENTS.md`, `SETUP.md`, `docs/ORCBREW_*`) that `agents/develop` already holds |

The Google Drive docs (`CLOUD_DRIVE_INTEGRATION_FEASIBILITY.md` 666L,
`GOOGLE_DRIVE_INTEGRATION.md` 304L) are unique but describe an unbuilt feature whose code is equally
unique. They stand or fall with the branch decision in the triage doc, not separately.

## Recommendations

1. **Restore `datomic-crash-analysis.md`** to `agents/develop` from any of the 30 branches. It is the
   only item here that was already decided — the relocation commit intended it.
2. **Fix `check-docs.sh` §3 before the next prune.** Comparing against `origin/develop` is a no-op
   for `docs/kb/`. Compare against the union of KB-carrying branches, or at minimum against
   `feature/grant-rows`, which currently holds the most. Without this, the next branch to accumulate
   docs repeats the problem silently.
3. **Lift the nine, then delete.** Ordering matters: four of them
   (`key-vs-name-separation.md`, `feature-tab-black-screen.md`, `unblock-features-tab.html`,
   `single-colon-keyword.md`) sit on branches the triage called the *cheapest* deletions, because
   their code had already landed. Their prose had not.
4. **Decide what `agents/develop` is for.** The 53 docs on non-`claude` branches are not at risk
   today, but `BRANCH.md` describes this branch as the place the knowledge base lives, and it holds
   55% of it. Either fold the leaves' KB in periodically the way integration's code is folded in, or
   change the description — the current gap between the two is what let items 1 and 3 happen.

## What is not known

- **Whether the 53 non-`claude` KB docs should all move.** Several belong to live leaves
  (`content-extensibility-*`, `builder-*`, `fighting-style-*`) and may be deliberately branch-local
  until the leaf lands. This was not adjudicated per doc; only the count is established.
- **Whether `CUSTOM_ITEMS_INVESTIGATION.md`'s five patches are still correct.** Nothing here was
  compiled or run. The uniqueness of the finding is established; its current accuracy is not.
- **Why `042fd072` dropped the Datomic doc.** The commit message gives no reason, and the two files
  it removed were treated differently with no note. Deliberate omission and oversight look identical
  in the diff.

## Revisions

- **2026-09-12 — `claude/character-portrait-generator-hOutO` is not cold.** The triage doc recorded
  its tip as `63eddd2f` (2026-09-06) and listed it for salvage. It has since moved to `54507402`
  (2026-09-12, +4 commits: robots.txt AI opt-out, desktop asset pipeline, artist attribution). It is
  an active branch, not a stale one, and should not be in a pruning conversation at all. Re-checked
  every other `claude/*` tip at the same time; that one is the only one that moved.
- **2026-09-12 — the fighting-style catalogue was not unique.** The triage doc said "no KB doc on any
  branch mentions Blind Fighting, Superior Technique or Unarmed Fighting" and recommended lifting
  ~8,000 lines of `docs/analysis/`. That search covered only `agents/develop`,
  `refactor/content-extensibility` and `perf/entity-build`. `fighting-style-vocabulary-gap.md` on
  `feature/grant-rows` and `feature/fighting-style-authoring` names all three and is the better doc.
  The corpus is superseded, not salvage. **The error was searching three branches and reporting as
  if it were all of them** — the same class of mistake the triage doc warned about for code.
