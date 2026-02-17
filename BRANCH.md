# Branch Context: agents/develop

## Purpose

Documentation-only branch. All AI agent configuration, onboarding docs,
and knowledge base files live here. Git hooks enforce: only `*.md`,
`.claude/*`, and `docs/*` files are allowed.

## Current State

- Documentation restructured (Feb 2026): CLAUDE.md / AGENTS.md / BRANCH.md pattern
- See `docs/DOC-CONVENTIONS.md` for the pattern specification
- GIT_WORKFLOW.md documents the multi-branch workflow
- SETUP.md covers devcontainer and MCP integration

## Workflow

- Git hooks enforce: only `*.md`, `.claude/*`, `docs/*` files allowed
- This branch is merged into integration branches for agent access
- Never contains source code, tests, or scripts
- PRs from agents that touch docs should target this branch

## Handoff Notes

- CLAUDE.md is a thin bootstrapper using `@AGENTS.md` and `@BRANCH.md` imports
- AGENTS.md is model-agnostic (works for Claude, Copilot, Cursor, etc.)
- Branch-specific context belongs in BRANCH.md, not AGENTS.md
- KB docs go in `docs/` — see DOC-CONVENTIONS.md for guidelines

## Related Docs

- `docs/DOC-CONVENTIONS.md` — documentation structure and KB conventions
- `docs/GIT_WORKFLOW.md` — multi-branch workflow design
- `SETUP.md` — devcontainer, MCP, attribution hooks
- `CODEBASE.md` — codebase overview
