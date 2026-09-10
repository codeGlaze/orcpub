# Branch Context: agents/develop

## Purpose

Home for agent configuration, onboarding docs and the knowledge base. `.claude/` is tracked
here on purpose — the code branches ignore it as local tool state, so the hooks and skills
have nowhere else to be version-controlled.

Carries integration's code as well, merged in rather than forked from. That is deliberate:
KB docs cite `file:line`, and a branch five months behind cites lines that have moved.

## Current State

*Updated 2026-09-10. Update this section at every milestone — it is the only file that
carries live state.*

- **Merged `integration` 2026-09-10** after five months of drift (550 behind, 131 ahead).
  `src/`, `test/` and `project.clj` are byte-identical to integration; the 131 commits here
  touch no application code.
- **Active plan: the Fall Update** — companions, summons and Wild Shape. Research complete,
  nothing built. See [`docs/kb/plan-companions-and-wild-shape.md`](docs/kb/plan-companions-and-wild-shape.md).
  Two blockers, one per half: CR is stored as a string on the character and a number on the
  monster; and no hook exists for plugin content to declare that a subclass grants a companion.
- Summer Patch work is merged to `integration` and out of scope here.

## Reinforcement loop

The mechanism that stops research being lost. It only works when armed:

| Piece | What it does | Arm it |
| --- | --- | --- |
| `scripts/check-docs.sh` | Dangling links, orphaned KB docs, the develop superset | Runs from `pre-commit` |
| `.githooks/pre-commit` | Blocks a `docs/` commit that fails the above | **`scripts/setup-hooks.sh`, once per clone** |
| `.githooks/pre-push` | Blocks an un-folded branch changelog onto integration | same |
| `.claude/hooks/kb-doc-reminder.sh` | On `git push`: code changed, no docs? Nudge | `.claude/settings.json` |
| `.claude/hooks/kb-audit-reminder.sh` | Same nudge at Stop, while the work is still in hand | register under `Stop` |
| `docs/kb/README.md` | The index. A KB doc not linked from an index is invisible | check-docs enforces it |

**`core.hooksPath` is not set by cloning.** On 2026-09-10 it was unset here, so every commit
for the whole session bypassed `check-docs.sh`, and two plan docs had gone orphaned unnoticed.
Run `scripts/setup-hooks.sh` first, in every clone, or none of the rest of this fires.

## Workflow

- Branch-specific context belongs here, not in AGENTS.md.
- KB docs go in `docs/kb/` and **must** be linked from `docs/kb/README.md` or they orphan.
- Merge `integration` in periodically. Keep this branch's `.gitignore` exception for
  `.claude/`; taking integration's version wholesale would stop the hooks being tracked.
- Doc-touching PRs target this branch.

## Related Docs

- [`docs/kb/documentation-discipline.md`](docs/kb/documentation-discipline.md) — what earns a
  doc, updating in place, recording reversals
- [`docs/DOC-CONVENTIONS.md`](docs/DOC-CONVENTIONS.md) — the three-tier structure and KB conventions
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) — multi-branch workflow design
- [`SETUP.md`](SETUP.md) — devcontainer, MCP, attribution hooks
- [`CODEBASE.md`](CODEBASE.md) — codebase overview
