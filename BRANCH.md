# Branch Context: agents/develop

Agent config, onboarding docs and the knowledge base. `.claude/` is tracked here because the
code branches gitignore it — this is the only place the hooks and skills can be versioned.

Carries integration's code, merged in periodically. Deliberate: KB docs cite `file:line`, and
a stale branch cites lines that have moved.

## Current state

*Updated 2026-09-10. Update at every milestone — nothing else here carries live state.*

### The tree

```
integration          hotfixes; mints the Summer Patch tag, then quiet
   └── refactor/      working parent for the big refactor. Pulls integration in,
        └── leaves    holds merges from its leaves: content-extensibility,
                      data-extraction, garden-inline-styles, views-extraction,
                      banner-parts-and-design

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

- **Companions, summons and Wild Shape** —
  [`docs/kb/plan-companions-and-wild-shape.md`](docs/kb/plan-companions-and-wild-shape.md).
  Researched, unbuilt. Belongs under `refactor/content-extensibility`, which is the live leaf,
  carries the orcbrew v1/v2 format versioning, and rewrites the same files. Blocked on: CR is
  a string on the character and a number on the monster; and nothing lets content declare that
  a feature grants a creature. **Edition is not a blocker** — both rulesets ship and mix, so
  both get built; only the default is open.
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
- Keep the `.gitignore` exception for `.claude/` when merging integration in.
- Doc-touching PRs target this branch.

## Related

- [`docs/kb/documentation-discipline.md`](docs/kb/documentation-discipline.md) — what earns a doc, reversals, verification
- [`docs/DOC-CONVENTIONS.md`](docs/DOC-CONVENTIONS.md) — three-tier structure, KB conventions
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) · [`SETUP.md`](SETUP.md) · [`CODEBASE.md`](CODEBASE.md)
