# Documentation Conventions

> How documentation is structured in this project. All agents and contributors
> should follow these conventions.

---

## Three-Tier Structure

| File | Role | Scope | Changes when? |
|------|------|-------|---------------|
| `CLAUDE.md` | Bootstrapper | Claude Code auto-loads; imports AGENTS.md + BRANCH.md | Rarely |
| `AGENTS.md` | Universal onboarding | Project arch, rules, commands, "where to find things" | Major project changes |
| `BRANCH.md` | Branch context | Purpose, current state, workflow, handoff notes | Every session / milestone |

### Rules

- **AGENTS.md** is the **single source of truth** for project-wide instructions
- **AGENTS.md** must be **model-agnostic** — no Claude-specific, Copilot-specific, or Cursor-specific content
- **BRANCH.md** is **expected to differ** per branch — treat merge conflicts as trivial to resolve
- **CLAUDE.md** is **thin** — only `@imports` and Claude-specific settings (attribution, etc.)
- Other agents (Copilot, Cursor) point to AGENTS.md via their own config files

### How Claude Code loads these files

Claude Code auto-loads `CLAUDE.md` at session start. The `@path/to/file` syntax
in CLAUDE.md triggers recursive imports (max 5 hops). So a thin CLAUDE.md with
`@AGENTS.md` and `@BRANCH.md` gives Claude all three tiers automatically.

### For non-Claude agents

Each agent platform has its own config file. Point them at AGENTS.md:

- `.github/copilot-instructions.md` → "Read AGENTS.md"
- `.cursorrules` → "Read AGENTS.md"
- Any new agent config → "Read AGENTS.md"

---

## Knowledge Base (`docs/`)

### Principle: Capture branch insights as KB docs

When working on a branch, discoveries about architecture, gotchas, patterns, or
domain knowledge should be written to `docs/*.md` files — **NOT** left in session
transcripts or commit messages where they'll be lost.

**Examples:**
- Discovered how the entity/build pipeline works → `docs/ENTITY-BUILD.md`
- Found test suite gotchas and patterns → `docs/TESTING.md`
- Learned theming system internals → `docs/THEMING.md`
- Figured out Datomic setup steps → `docs/DATOMIC_SETUP.md`

### KB doc guidelines

1. **One topic per file**, descriptive filename
2. **Start with a purpose statement** — what is this doc about and why does it exist
3. **Include code references** (`file:line`) where relevant
4. **Universal docs** go on `agents/develop`; **branch-specific docs** stay on their branch
5. **Keep docs current** — update in the same commit as related code changes

### Current KB inventory

| Doc | Content | Location |
|-----|---------|----------|
| `docs/DOC-CONVENTIONS.md` | This file — documentation structure | agents/develop |
| `docs/DATOMIC_SETUP.md` | Datomic Pro installation, transactor, peer JAR | agents/develop |
| `docs/THEMING.md` | Theme system, SVG icons, Garden CSS | agents/develop |
| `docs/TESTING.md` | Test suite, patterns, E2E testing | agents/develop |
| `docs/GIT_WORKFLOW.md` | Multi-branch workflow lessons | agents/develop |
| `docs/ERROR_HANDLING.md` | Error handling approach | agents/develop |
| `docs/CODEBASE.md` | Codebase overview (living doc) | agents/develop |
| `docs/ORCBREW_*.md` | Orcbrew import/validation | agents/develop |
| `docs/ENTITY-BUILD.md` | Entity/build pipeline architecture | upgrade/datomic-pro |
| `docs/SESSION-SUMMARY.md` | Session continuity notes | upgrade/datomic-pro |
| `docs/ENVIRONMENT.md` | Environment variable details | upgrade/datomic-pro |

---

## BRANCH.md Template

Every branch should have a `BRANCH.md` at root. Use this template:

```markdown
# Branch Context: <branch-name>

## Purpose
<What this branch is for — one or two sentences>

## Current State
<What's done, what's in progress, what's blocked>

## Workflow
<Special rules, hooks, commit routing patterns for this branch>

## Handoff Notes
<What the next agent or session needs to know to continue>

## Related Docs
<Pointers to relevant docs/ files for this branch's work>
```

### Tips for BRANCH.md

- Update it at the **end of every session** or when hitting a milestone
- Keep it **short** (15-40 lines) — it's a quick-reference, not a journal
- If handoff notes grow large, split detail into a `docs/` file and link to it
- Merge conflicts in BRANCH.md are expected and trivial — just take the current branch's version
