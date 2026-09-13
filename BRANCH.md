# Branch Context: agents/develop

Agent config, onboarding docs and the knowledge base. `.claude/` is tracked here because the
code branches gitignore it — this is the only place the hooks and skills can be versioned.

Carries integration's code, merged in periodically. Deliberate: KB docs cite `file:line`, and
a stale branch cites lines that have moved.

## Current state

*Updated 2026-09-13. Update at every milestone — nothing else here carries live state.*

### The tree

```
integration          hotfixes; mints the Summer Patch tag, then quiet
   └── refactor/      working parent for the big refactor. Pulls integration in,
        └── leaves    holds merges from its leaves: content-extensibility,
                      data-extraction, garden-inline-styles, views-extraction,
                      banner-parts-and-design
             └── feature/grant-rows   the live tip of content-extensibility.
                      Verified 2026-09-13: content-extensibility is an ancestor of it,
                      so is integration. The most active Fall Update branch

agents/develop       a silo, off to the side. An ancestor of develop, not a leaf of
                     anything. Pulls stable changes in; ships nothing back. Its job is
                     to hold the agentic tooling and docs that carry flow across
                     conversations.
```

Releases are tags, not branches, so no branch name says which release it serves.
**Summer Patch** ships from `integration` once the last hotfixes land — nothing tagged yet.
**After that tag, everything is Fall Update: the refactor tree.**

`refactor/` is not a release family — it predates the Summer Patch. Don't rename it after a
release; the next one inherits the branches.

### Fall Update leaves

**`feature/grant-rows` is the live one** and everything below is downstream of it. Merged into
this branch on 2026-09-13 (371 commits). Its KB arrived with it — 51 docs that did not exist
here, against 73 here that did not exist there, with only 7 filenames in common. Neither side
was a superset; both had been written without the other. Start at
[`docs/kb/roadmap.md`](docs/kb/roadmap.md), then
[`docs/kb/handoff-grant-rows.md`](docs/kb/handoff-grant-rows.md).

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

### Deferred to the refactor — known, not yet owned

- **Multi-tab character contamination** —
  [`docs/kb/multi-tab-character-contamination.md`](docs/kb/multi-tab-character-contamination.md).
  The character draft is cached under one localStorage key with no id (`db.cljs:34`), so two builder
  tabs share a slot and a reload rehydrates whichever saved last. `::char5e/notes` carries
  `:db/noHistory`, so what it overwrites is unrecoverable. **Deliberately parked** during the Summer
  Patch bugfix run: the fix is a storage-shape change (key drafts by character id, tie the restore
  to the id in the URL) and there was no good answer for it under time pressure. It wants doing when
  the storage shape is already being moved, i.e. in `refactor/`, not as a hotfix. Investigation and
  five pinning tests sit on `claude/fix-character-notes-merge-4YNzf` (`702fffc7`) — 16 commits ahead
  of a 2026-04-09 base.

## Active sequence — do these in order

*Set 2026-09-13. This exists because the custom-item work went off course with no map back; the
point of the ordering is that each step names its own blocker.*

### 1. Land the #669 stale-filtered-list fix

Verified ready: merge is 3 conflict hunks, the cljs suite is green at 354/1699, and the tests are
proven to fail when the defect is reinstated. See
[`docs/kb/plan-669-merge-verification.md`](docs/kb/plan-669-merge-verification.md) and
[`docs/kb/filtered-list-staleness.md`](docs/kb/filtered-list-staleness.md).

**Blocker: the work is on `claude/fix-custom-items-disappearing-DW8rb`, and `claude/*` branches do
not merge.** They are harness auto-branches (the `git-branch` skill mints them). Re-home the commits
onto a typed branch first — `fix/…`, matching `fix/ac-unarmored-natural-stacking`,
`fix/item-stable-identity`. The branch also carries more than the fix (a `get-auth-token` move, a 401
breadcrumb, five subs migrated to a `reg-api-sub` HOF); the plan doc separates them if only the fix
should travel.

### 2. Vet the roadmap that lives here

**`docs/kb/roadmap.md` and `docs/kb/plan-next.md` are scoped to `feature/fighting-style-authoring`,
not to this branch.** They arrived with the `feature/grant-rows` merge. `roadmap.md` opens
*"One reconciled plan for branch `feature/fighting-style-authoring`"* and `plan-next.md` is that
branch's ordered worklist, including items about its own merge hygiene.

So "the roadmap" currently means one leaf's plan sitting in the shared KB. Decide the scope before
vetting the contents: either it stays that leaf's plan and is labelled as such, or this branch grows a
roadmap that spans the tree. Its status claims are commit-anchored and therefore checkable, which is
what makes vetting worth doing rather than guessing.

### 3. Add `fix/custom-item-classification` to it, then finish that branch

**This branch was never finished and never merged, and nothing said so until now.**

| | |
|---|---|
| tip | `b234db2b`, 2026-08-30 |
| base | `e632297d`, 2026-07-07 — `integration` has moved **539 commits** since |
| size | 36 commits |
| why it stopped | its own last commit: *"Paused for a security branch, so the reasoning is written down rather than carried in someone's head"* |
| what is left | bulk review of unclassified items — designed, not built. `docs/issues/bulk-review-of-unclassified-items.md` on that branch |
| state of the feature on `integration` | **absent.** `classification-tx`, `effective-item`, `backfill-report` all score zero there |
| what did leak across | only `dev/e2e_boot.clj`, carried over separately (byte-identical, different commit `23eb07cf`). The harness landed; the suite that used it did not |

**Merge `integration` into it before anything else.** As it stands the merge is 17 hunks across 12
files and grows with every integration commit. One of those hunks is not really a conflict:
`scripts/e2e/run.js` is **two unrelated suites contesting one filename** — 394 lines of PDF
scenarios on `integration`, 566 lines of custom-item flows on the branch. Give them distinct names
first and that hunk disappears. `integration`'s `run.sh` already takes a script argument
(`node "scripts/e2e/${1:-run.js}"`), so nothing else has to change. Naming is an open question —
`test/browser/` uses a `*_e2e.js` suffix, `test/e2e/` uses plain descriptive names, and
`scripts/e2e/` has no precedent yet.

Also worth lifting while in there: that branch's `login()` helper into a shared
`scripts/e2e/lib.js`, so the next session does not re-derive it.
[`docs/kb/e2e-logged-in-sessions.md`](docs/kb/e2e-logged-in-sessions.md) has the detail.

### 4. Only then, resume combing branches

The `claude/*` triage is paused at this point deliberately, with its findings recorded:
[`claude-branch-triage.md`](docs/kb/claude-branch-triage.md),
[`unsaved-knowledge-on-prunable-branches.md`](docs/kb/unsaved-knowledge-on-prunable-branches.md),
[`rescued/`](docs/kb/rescued/README.md). Nothing has been deleted; deletion is still the owner's call
and the report is the input to it.

## Agent tooling

Everything this branch exists to hold. **Run `scripts/setup-hooks.sh` first — `core.hooksPath`
is unset on a fresh clone, and nothing enforcing below fires without it.**

| Piece | What it does |
| --- | --- |
| `START-HERE.md` | Entry point. Prompt a new session with *"read START-HERE.md on agents/develop and follow it"* |
| `scripts/agent-setup.sh` | Pulls this branch's tooling into any code branch's working tree (gitignored there, so it cannot reach a PR), arms the hooks, prints where the KB is. `--check` verifies only |
| `scripts/clj-grep.py` | Greps Clojure with `#_` discards separated from live code. `classes.cljc` has 160 discarded `:name "` hits against 346 live — plain grep reports both as real. Also on `integration` |
| `scripts/check-docs.sh` | Dangling links, orphaned KB docs, the develop superset. Runs from `pre-commit` |
| `.githooks/pre-push` | Blocks an un-folded branch changelog onto integration |
| `.claude/hooks/kb-doc-reminder.sh` | On push: code changed, no docs? Nudge. Register in `.claude/settings.json` |
| `.claude/hooks/kb-audit-reminder.sh` | Same nudge at Stop. Register under `Stop` |
| `.claude/skills/git-branch/` | Branch-creation conventions |
| `docs/kb/README.md` | The index. An unlinked KB doc is invisible; `check-docs` fails the commit |

## Rules

- Findings go in `docs/kb/`, linked from its README.
- **Merge `feature/grant-rows` in before planning Fall Update work.** Its KB is where the
  content track's decisions live, and it moves daily.
- Keep the `.gitignore` exception for `.claude/` when merging integration in.
- Doc-touching PRs target this branch.

## Related

- [`docs/kb/documentation-discipline.md`](docs/kb/documentation-discipline.md) — what earns a doc, reversals, verification
- [`docs/DOC-CONVENTIONS.md`](docs/DOC-CONVENTIONS.md) — three-tier structure, KB conventions
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) · [`SETUP.md`](SETUP.md) · [`CODEBASE.md`](CODEBASE.md)
