# Branch Context: agents/develop — and the project's live state

Agent config, onboarding docs and the knowledge base. `.claude/` is tracked here because the
code branches gitignore it — this is the only place the hooks and skills can be versioned.
**This file is also the project-wide state and plan:** `agent-setup.sh` inlines it into every
code branch's `CLAUDE.md`, ahead of that branch's own handoff (`branches/<branch>.md`).

Carries integration's code, merged in periodically. Deliberate: KB docs cite `file:line`, and
a stale branch cites lines that have moved.

Earlier detail (the full Summer Patch merge list, the #669 merge plan, the picks-strand analysis,
the custom-item-classification table) is word for word in
[`docs/kb/branch-md-2026-10-23.md`](docs/kb/branch-md-2026-10-23.md).

## Current state

*Updated 2026-10-23, checked against `git` that day. Update at every milestone — nothing else here
carries live state.*

### Summer Patch: where it stands

- **Not released yet:** no tag exists. Everything for it is on `integration`, including the Rules
  tab and SRD fixes (#58, #60, #61), the hidden-picks hold (#67, 2026-10-16) and portraits with
  per-character builder drafts (#64, 2026-10-18). The Summer Patch Highlights paragraph does not
  mention the Rules tab yet (owner's call). The SRD data is generated on `srd52/develop` from the
  open5e clone's `srd-corrections` branch: [srd-improvements.md](docs/kb/srd-improvements.md);
  open5e corrections are filed upstream as PRs #995-#997 and #999, issue #998.
- **#32 is parked; nothing goes to `develop` until the owner says so.** When it is picked up: re-measure
  what `develop` has that `integration` lacks. On 2026-10-06 it was two commits (`15e1fe04` the
  design-handoff gitignore, `93c15c03`), which conflict in `.gitignore`; on 2026-10-23
  `git log --cherry-pick --right-only integration...develop` listed 44. And CodeQL fails on a regex built from a CLI argument in
  `scripts/test/trace-probe.js` (cited 2026-10-06 as line 52, which no longer matches: re-read the
  CodeQL alert), with two docker workflows missing a `permissions` block.
- **Release, when the owner says so:** #32 takes `integration` into `develop`; then refresh or replace
  upstream #674 with "Fixes #N" for the 35 issues the release answers.
- **Upstream issues:** #704 (two-tab character contamination, below) and #705-#712, umbrella issues
  for outcomes no issue covered. The changelog cites them.
- **Fork-only files** (standing rule, owner, 2026-10-16: may live on our `develop`, removed before any
  PR to Orcpub/orcpub; the rule is in AGENTS.md, "PRs and releases"). Fork-only so far:
  `.coderabbit.yaml` (added on #64, so already on `integration`: reviews PRs into `develop` and
  `integration` once each, not on later pushes, since #71). Add each new one here.
- **Merge gate and CodeRabbit limits:** the rules are in `AGENTS.md`, "PRs and releases", and the
  mechanics in `docs/GIT_WORKFLOW.md`, "CodeRabbit" (moved 2026-10-23, so agents on code branches see
  them). Measured detail: the Advanced plan refills 10 reviews an hour
  below 50 in its rolling window and 1 an hour at 80+ (1 an hour on 2026-10-21); Greptile's trial is
  out of credits; plain `@coderabbitai review` works only while automatic reviews are paused
  (checked 2026-10-16 on #64); `.coderabbit.yaml` auto-reviews PRs into `develop` and `integration`. Source: docs.coderabbit.ai/management/rate-limits.
- **In flight** (checked 2026-10-23; this list goes stale, so before starting anything also check
  `git branch -r` and the open PRs):
  - Open PRs into `integration`: #63 `refactor/pdf-canvas`, #66 `test/page-load-budget`,
    #69 `feature/asi-spreads`, #70 `feature/registration-in-boot-report`. #65
    `feature/artist-pages` targets `feature/portrait-builder`, which has already merged (#64).
    #35 targets `mirror/upstream-develop`.
  - Active, no PR yet: `feature/character-rescue` (Data View and the server's version check;
    handoff in `branches/`), `feature/email-and-alerts` (brings the heavy Docker checks; nearly
    ready, owner 2026-10-23), `feature/inventory-kits` (the `inventory` namespace and the Kits panel,
    [`plan-inventory-kits.md`](docs/kb/plan-inventory-kits.md)), `feature/weapons-table` (item
    charges; overlaps inventory-kits), `feature/guarded-homebrew`, `feature/srd-pipeline`.

### The tree

```
integration          hotfixes; mints the Summer Patch tag, then quiet
   └── refactor/main  trunk of the refactor tree (renamed from refactor/content-extensibility,
        │             2026-10-24). Pulls integration in; leaves are cut from it and merge back
        └── feature/grant-rows   built on it; the most active Fall Update branch.
                      Also built on it (merged it in): feature/extras-companions,
                      srd52/develop, feature/fighting-style-authoring

agents/develop       a silo, off to the side, not a leaf of anything. Pulls stable changes
                     in; ships nothing back. Holds the agentic tooling and the docs that
                     carry flow across conversations.
```

**Checked 2026-10-24.** `feature/grant-rows` is 230 commits behind `integration`. Other refactor
branches went straight to `integration` instead: `refactor/garden-harvest` and
`refactor/picks-namespace` merged there; `refactor/pdf-canvas` (#63, with
`refactor/card-shape-layer`) and `refactor/garden-inline-styles` target it; whether they move to
`refactor/main` is the owner's call. `refactor/views-extraction` and `refactor/data-extraction`: below.

Releases are tags, not branches, so no branch name says which release it serves.
**Summer Patch** ships from `integration` once the last hotfixes land — nothing tagged yet.
**After that tag, everything is Fall Update: the refactor tree.**

`refactor/main` is not a release family — it predates the Summer Patch. Don't rename it after a
release; the next one inherits the branches. `refactor/views-extraction` is dead (it raced a
churning mainline); `refactor/data-extraction` is a reference for splitting the monoliths, not
revived (owner, 2026-10-19). Splitting the monoliths now goes one small extraction at a time
(AGENTS.md, "Shrinking the big files").

### Fall Update leaves

**`feature/grant-rows` is the live one** (unmerged; last code commit 2026-10-19) and everything below is
downstream of it. Its KB came over in the 2026-09-13 merge. Start at
[`docs/kb/roadmap.md`](docs/kb/roadmap.md) (labelled 2026-09-25 as `feature/grant-rows`' own
plan), then [`docs/kb/handoff-grant-rows.md`](docs/kb/handoff-grant-rows.md).

- **Companions, summons and Wild Shape** —
  [`docs/kb/plan-companions-and-wild-shape.md`](docs/kb/plan-companions-and-wild-shape.md) and
  [`docs/kb/extras-definitions.md`](docs/kb/extras-definitions.md).
  Researched, unbuilt. Belongs on `feature/grant-rows`, not beside it: the grant vocabulary a
  companion needs is the one being built there. Blocked on: CR is a string on the character and
  a number on the monster; and nothing lets content declare that a feature grants a creature.
  **Edition is not a blocker** — both rulesets ship and mix, so both get built; only the default
  is open.
  **Read before extending it** — written before the merge, so they do not cite:
  [`pool-grant-map.md`](docs/kb/pool-grant-map.md),
  [`authoring-vocabulary.md`](docs/kb/authoring-vocabulary.md),
  [`edition-drift.md`](docs/kb/edition-drift.md),
  [`requirements-registry.md`](docs/kb/requirements-registry.md),
  [`rules-override-layer.md`](docs/kb/rules-override-layer.md).
- **NPC statblock customizer** —
  [`docs/kb/plan-npc-statblock-customizer.md`](docs/kb/plan-npc-statblock-customizer.md).
  DM-facing, not character-facing. Researched, unbuilt. Independent of the Extras leaf but
  shares the copy-on-adopt statblock shape.
- No other leaf planned or started.

### Picks: the hold and the rescue

`src/cljc/orcpub/dnd/e5/picks.cljc` is the shared store-and-rewrite layer for a character's stored
picks. The hold (`fix/hidden-multiclass-skill-pick`: a pick whose gate has closed is held out of the
built character) **merged as #67** on 2026-10-16. The rescue (`feature/character-rescue`: Data View
and its Remove, Undo and Save) is in flight and carries #67's `picks.cljc`.

**Open for the owner:** do the hold and the rescue share one mechanism or two? The rescue agent's
view is two: the hold decides what the built character applies; Data View edits what is stored.

### Deferred to the refactor — known, not yet owned

- **Multi-tab character contamination** (#704) —
  [`docs/kb/multi-tab-character-contamination.md`](docs/kb/multi-tab-character-contamination.md).
  **The character builder's half landed in #64:** one draft per character
  (`src/cljs/orcpub/dnd/e5/character_drafts.cljs`, [`plan-character-drafts.md`](docs/kb/plan-character-drafts.md)).
  The other builders (homebrew forms, encounter, combat) still share one slot per kind.
  `::char5e/notes` carries `:db/noHistory`, so what an overwrite replaces is unrecoverable. The rest
  of the fix is a storage-shape change: do it in `refactor/` when the storage shape is already
  being moved, not as a hotfix. Pinning
  tests sit on `claude/fix-character-notes-merge-4YNzf` (`702fffc7`, unmerged).

## Active sequence — do these in order

*Set 2026-09-13; statuses checked 2026-10-23. Each step names its own blocker.*

1. **Land the #669 stale-filtered-list fix.** Done 2026-09-13 (`3bd77862`). Plan and proof:
   [`plan-669-merge-verification.md`](docs/kb/plan-669-merge-verification.md).
2. **Vet the roadmap that lives here.** Half done: on 2026-09-25 `roadmap.md` was labelled as
   `feature/grant-rows`' own plan. Still open: does this branch grow a roadmap that spans the tree?
   Its status claims are commit-anchored and therefore checkable, which is what makes vetting worth
   doing rather than guessing.
3. **Finish `fix/custom-item-classification`.** Not started: still unmerged, untouched since
   2026-08-30, and further behind `integration` every day. Its own last commit: *"Paused for a
   security branch, so the reasoning is written down rather than carried in someone's head"*. What is
   left: bulk review of unclassified items, designed, not built
   (`docs/issues/bulk-review-of-unclassified-items.md` on that branch); the feature is absent from
   `integration`. **Merge `integration` into it before anything else.** `integration` has since renamed its
   suite (`run.sh` is the entry point now), so after that merge rename the branch's own
   `scripts/e2e/run.js` to match. Then lift its `login()` helper into the shared
   `scripts/e2e/lib.js` ([`e2e-logged-in-sessions.md`](docs/kb/e2e-logged-in-sessions.md)). Full
   table: the 2026-10-23 snapshot.
4. **Then comb branches.** Resumed 2026-10-06: [branch-inventory.md](docs/kb/branch-inventory.md),
   with the deletion log. Re-run it before trusting a row. Earlier findings:
   [`claude-branch-triage.md`](docs/kb/claude-branch-triage.md),
   [`unsaved-knowledge-on-prunable-branches.md`](docs/kb/unsaved-knowledge-on-prunable-branches.md),
   [`rescued/`](docs/kb/rescued/README.md). Deletion is the owner's call, branch by branch.

## Agent tooling

Everything this branch exists to hold. **The hooks arm themselves at session start**
(the SessionStart hook, `.claude/hooks/session-start.sh`, runs `agent-setup.sh --session`); `agent-setup.sh --check` says whether they are armed and current.
One installer, one versioned set: [`docs/kb/agent-hooks.md`](docs/kb/agent-hooks.md).

| Piece | What it does |
| --- | --- |
| `START-HERE.md` | Entry point. Prompt a new session with *"read START-HERE.md on agents/develop and follow it"* |
| `scripts/agent-setup.sh` | Pulls this branch's tooling into any code branch's working tree (gitignored there, so it cannot reach a PR), generates its `CLAUDE.md` (AGENTS.md, this file and the branch handoff inlined), arms the hooks, prints where the KB is. `--check` verifies only |
| `branches/` | One handoff per code branch (`branches/<branch>.md`), from `TEMPLATE.md`; rules in `branches/README.md` |
| `scripts/clj-grep.py` | Greps Clojure with `#_` discards separated from live code. `classes.cljc` has 160 discarded `:name "` hits against 346 live — plain grep reports both as real. Also on `integration` |
| `scripts/check-docs.sh` | Dangling links, orphaned KB docs, the develop superset. Runs from `pre-commit` |
| `.githooks/commit-msg` | Strips agent attribution trailers (`Co-Authored-By: Claude`, `Claude-Session:`) |
| `.githooks/pre-commit` | Blocks `CLAUDE.md`, `AGENTS.md`, `BRANCH.md` or `.claude/` on a code branch; blocks a commit that adds a function without a docstring; warns on history wording; runs `check-docs.sh` here |
| `.githooks/pre-push` | Blocks an un-folded branch changelog onto integration, develop or main; prints the heavy-Docker heads-up when a push changes deployment files |
| `.claude/hooks/kb-doc-reminder.sh` | On push: code changed, no docs? Nudge. Register in `.claude/settings.json` |
| `.claude/hooks/kb-audit-reminder.sh` | Same nudge at Stop. Register under `Stop` |
| `docs/kb/README.md` | The index. An unlinked KB doc is invisible; `check-docs` fails the commit |

## Rules for work on agents/develop itself

These apply when working on this branch, not on a code branch.

- Findings go in `docs/kb/`, linked from its README.
- **Before planning Fall Update work here, bring `feature/grant-rows`' KB into agents/develop.** It is
  where the content track's decisions live, and it moves daily. Since 2026-10-24 that branch (and
  the others cut from it) no longer tracks the agent files, so a plain merge deletes them here.
  Merge with `git merge --no-commit --no-ff origin/feature/grant-rows`, then
  `git restore --source=HEAD --staged --worktree -- CLAUDE.md AGENTS.md BRANCH.md .claude .gitignore`,
  check `git status` shows none of them, and commit.
- When merging integration into agents/develop, keep the `.gitignore` exception for `.claude/`.

## Related

- [`docs/kb/documentation-discipline.md`](docs/kb/documentation-discipline.md) — what earns a doc, reversals, verification
- [`docs/DOC-CONVENTIONS.md`](docs/DOC-CONVENTIONS.md) — three-tier structure, KB conventions
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) · [`SETUP.md`](SETUP.md) · [`CODEBASE.md`](CODEBASE.md)
