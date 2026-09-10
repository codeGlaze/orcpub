# Branch Context: agents/develop

Agent config, onboarding docs and the knowledge base. `.claude/` is tracked here because the
code branches gitignore it — this is the only place the hooks and skills can be versioned.

Carries integration's code, merged in periodically. Deliberate: KB docs cite `file:line`, and
a stale branch cites lines that have moved.

## Current state

*Updated 2026-09-10. Update at every milestone — nothing else here carries live state.*

- **Fall Update = the `refactor/` line and its leaves.** `refactor/content-extensibility` is
  the live one and carries the orcbrew v1/v2 format versioning.
- **One leaf planned:** companions, summons and Wild Shape —
  [`docs/kb/plan-companions-and-wild-shape.md`](docs/kb/plan-companions-and-wild-shape.md).
  Researched, unbuilt. Branches off `refactor/content-extensibility`, which rewrites the same
  files. Blocked on: CR is a string on the character and a number on the monster; and nothing
  lets content declare that a feature grants a creature.
- Summer Patch is merged to `integration` and out of scope here.

## The loop

Stops research being lost. **Run `scripts/setup-hooks.sh` first — `core.hooksPath` is unset on
a fresh clone, and none of this fires without it.**

| Piece | What it does |
| --- | --- |
| `scripts/check-docs.sh` | Dangling links, orphaned KB docs, the develop superset. Runs from `pre-commit` |
| `.githooks/pre-push` | Blocks an un-folded branch changelog onto integration |
| `.claude/hooks/kb-doc-reminder.sh` | On push: code changed, no docs? Nudge. Register in `.claude/settings.json` |
| `.claude/hooks/kb-audit-reminder.sh` | Same nudge at Stop. Register under `Stop` |
| `docs/kb/README.md` | The index. An unlinked KB doc is invisible; `check-docs` fails the commit |

## Rules

- Findings go in `docs/kb/`, linked from its README.
- Keep the `.gitignore` exception for `.claude/` when merging integration in.
- Doc-touching PRs target this branch.

## Related

- [`docs/kb/documentation-discipline.md`](docs/kb/documentation-discipline.md) — what earns a doc, reversals, verification
- [`docs/DOC-CONVENTIONS.md`](docs/DOC-CONVENTIONS.md) — three-tier structure, KB conventions
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) · [`SETUP.md`](SETUP.md) · [`CODEBASE.md`](CODEBASE.md)
