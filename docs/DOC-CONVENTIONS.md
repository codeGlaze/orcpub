# Documentation Conventions

> How documentation is structured in this project. All agents and contributors
> should follow these conventions.

---

## Three-Tier Structure

| File | Role | Scope | Changes when? |
|------|------|-------|---------------|
| `CLAUDE.md` | Bootstrapper | Claude Code auto-loads; on agents/develop imports AGENTS.md and BRANCH.md; on a code branch, generated with those and the branch handoff inlined | Rarely |
| `AGENTS.md` | Universal onboarding | Rules every agent follows on every branch, and where to find things | When the owner changes a rule |
| `BRANCH.md` | Project state | Release state, the branch tree, the active plan, cross-branch rules. On agents/develop, loaded on every branch | Each milestone |
| Branch handoff | Branch context | One branch's purpose, state, owner decisions, gotchas. `branches/<branch>.md` on agents/develop | Each milestone |

### Rules

- **AGENTS.md** is the **single source of truth** for project-wide instructions
- **AGENTS.md** must be **model-agnostic** — no Claude-specific, Copilot-specific, or Cursor-specific content
- **Branch handoffs live on agents/develop** (`branches/<branch>.md`), never on the code branch, so they never reach a PR (`branches/README.md` has the rules, and what to do on a branch that still tracks its own)
- **CLAUDE.md** is **thin** — only `@imports` and Claude-specific settings (attribution, etc.)
- Other agents (Copilot, Cursor) point to AGENTS.md via their own config files

### How Claude Code loads these files

Claude Code auto-loads `CLAUDE.md` at session start. The `@path/to/file` syntax
in CLAUDE.md triggers recursive imports (max 5 hops). On agents/develop, `@AGENTS.md` and
`@BRANCH.md` load directly. A code branch carries neither, so `agent-setup.sh` generates its
`CLAUDE.md` with `AGENTS.md`, `BRANCH.md` and that branch's handoff (`branches/<branch>.md`) inlined.

### For non-Claude agents

Each agent platform has its own config file. Point them at AGENTS.md:

- `.github/copilot-instructions.md` → "Read AGENTS.md"
- Any new agent config → "Read AGENTS.md"

---

## Knowledge Base (`docs/`)

### Principle: Capture branch insights as KB docs

When working on a branch, discoveries about architecture, gotchas, patterns, or
domain knowledge should be written to `docs/*.md` files — **NOT** left in session
transcripts or commit messages where they'll be lost.

**Examples:**
- Discovered how the entity/build pipeline works → a page in `docs/kb/`
- Found test suite gotchas and patterns → `docs/TESTING.md`
- Learned theming system internals → `docs/THEMING.md`
- Figured out Datomic setup steps → `docs/DATOMIC_SETUP.md`

### KB doc guidelines

1. **One topic per file**, descriptive filename
2. **Start with a purpose statement** — what is this doc about and why does it exist
3. **Include code references** (`file:line`) where relevant
4. **Every KB page ends up on `agents/develop`.** A working branch may hold pages in progress in its `docs/kb/`; they move to agents/develop at a milestone and before the branch merges into `integration` (`AGENTS.md`, top). Branch handoffs are only ever on agents/develop
5. **Keep docs current** — in the same PR as the code change, and on agents/develop at the same time (`AGENTS.md`, "Docs")

### Where docs live

- **`docs/kb/`:** agents/develop only; `docs/kb/README.md` is the index.
- **`CODEBASE.md`, `SETUP.md`, `branches/`:** agents/develop only.
- **Other `docs/` pages:** on agents/develop, and many also on the code branches (`git ls-tree
  origin/integration docs/` lists them). Where both have one, `AGENTS.md`, "Docs", says what to change.

---

## Branch handoffs

One file per code branch that has state the next agent needs, at `branches/<branch>.md` on
agents/develop, started from `branches/TEMPLATE.md`. A one-session fix needs none. Rules (keep it
under about 80 lines, update it at each milestone, move finished history to the KB, delete it when
the branch merges): `branches/README.md`.
