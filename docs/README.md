# Documentation

This folder contains detailed documentation for the OrcPub/Dungeon Master's Vault project.

## Documentation Structure

### Root-Level Documents (High Visibility)

| File | Purpose |
|------|---------|
| [`README.md`](../README.md) | Project overview, getting started, Docker setup |
| [`AGENTS.md`](../AGENTS.md) | AI agent instructions and workflow rules |
| [`UPGRADE_PLAN.md`](../UPGRADE_PLAN.md) | Dependency upgrade roadmap and progress tracking |
| [`LICENSE`](../LICENSE) | Project license (EPL-2.0) |

### This Folder (`docs/`)

| File | Purpose |
|------|---------|
| [`DEPENDENCY_VALIDATION.md`](DEPENDENCY_VALIDATION.md) | Detailed validation report for dependency upgrades |
| [`DATOMIC_JAVA21_TEST_RESULTS.md`](DATOMIC_JAVA21_TEST_RESULTS.md) | Compatibility test results: Datomic Free on Java 21 |

### Other Documentation Locations

| Location | Purpose |
|----------|---------|
| [`.github/copilot-instructions.md`](../.github/copilot-instructions.md) | GitHub Copilot quick reference (points to AGENTS.md) |
| [`.cursor/worktrees.json`](../.cursor/worktrees.json) | Worktree configuration and branch protection rules |
| [`test/README.md`](../test/README.md) | Testing documentation |
| [`scripts/experimental/README.md`](../scripts/experimental/README.md) | Experimental scripts documentation |
| [`docker/*/README.md`](../docker/) | Docker container documentation |

## Contributing Documentation

When adding documentation:

1. **High-level, frequently referenced** → Root level (`README.md`, `AGENTS.md`, etc.)
2. **Detailed technical docs** → `docs/` folder
3. **Component-specific docs** → Alongside the component (e.g., `docker/datomic/README.md`)
4. **AI/Agent instructions** → Update `AGENTS.md` (primary) and `.github/copilot-instructions.md` (summary)

## For AI Agents

If you're an AI coding assistant, start with [`AGENTS.md`](../AGENTS.md) — it contains all the rules and patterns you need to know.
