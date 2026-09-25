
# AI Agent Instructions — OrcPub/Dungeon Master's Vault

> **Purpose**: Help AI coding agents get immediately productive in this repository while respecting project governance and workflow rules.

---

## Critical Rules (Read First)

### Branch Protection — MUST FOLLOW

*Verified against `git ls-remote` on 2026-09-24. If a branch named here does not exist,
correct this table rather than working around it — see `docs/kb/documentation-discipline.md`.*

| Branch | Protection | Rule |
|--------|-----------|------|
| `develop` | **OWNER ONLY** | Never merge or push. The upstream trunk; PRs target it. |
| `integration` | **PR REQUIRED** | The active trunk — hotfixes land here and it is ahead of `develop`. No direct pushes. |
| `agents/develop` | **DOCS + TOOLING ONLY** | `*.md`, `.claude/*`, `agents/*`, `docs/*`, `scripts/git/*`, `.githooks/*`. **No source, no tests.** It carries integration's code for `file:line` accuracy, but code changed here conflicts with the next merge down. |
| `refactor/*` | **OPEN** | Working parent for the refactor tree and its leaves. |
| `feature/*` `fix/*` `hotfix/*` `perf/*` `docs/*` | **OPEN** | Agents may work freely. Typed prefixes; these are what the tree actually uses. |
| `claude/*` | **DO NOT MERGE** | Harness auto-branches. Re-home commits onto a typed branch before merging anything. |

### Agent Workflow Rules

1. Never merge or push directly to `develop` or `integration`
2. Work in a typed branch — `feature/`, `fix/`, `hotfix/`, `perf/`, `refactor/`
3. Branch from `integration` for anything that must ship; it is ahead of `develop`
4. Create Pull Requests for review — do not merge them yourself
5. `claude/*` branches do not merge. Re-home the commits first
6. Source or test changes do not belong on `agents/develop`
7. **Declare stop rules before starting work** — see below

**Removed 2026-09-24, verified absent from the remote:** this section named
`modernize-stack` as PR-protected and instructed agents to "branch new features from
`upgrade/security-jackson-guava`". Neither branch exists — `git ls-remote` returns zero refs
for both — and they had not for roughly eight months, while sitting under a MUST FOLLOW
heading that is the first thing an agent reads. `upgrade/*` survives as a convention with one
live branch and is no longer the place to start work.


### Stop Rules and Budget — MUST FOLLOW

The person running a session often has a limited plan and **neither they nor any agent can
see how many tokens are left.** A run with no written stopping point can spend the rest of
the budget redoing finished work. This has happened here: an agent committed all its fixes,
never reported, and kept going — 137k to 203k tokens — re-rewriting a flaky test in the same
worktree another test suite was using. The prompt had told it to "fix anything that fails",
and a flaky test fails at random, so it never had a reason to stop.

**Every response that does work starts with its stop rules**, before any tool runs, so the
person can see them and interrupt if something looks wrong:

> **Stop rules for this turn**
> - what gets done, and what does not
> - the attempt budget for each risky item (usually one)
> - what makes it stop and report instead of retrying
> - anything it will NOT do (push, touch another branch, run a second suite)

Rules that apply every time:

- **Flaky means stop.** If a result changes between runs with nothing changed, stop and
  report it as flaky. Do not try to fix it by retrying, and do not change code to make it
  pass. Retrying a race looks like progress and never ends.
- **Attempt budgets, in the prompt.** Give every subagent a hard limit: one implementation
  attempt, a set number of verification runs, and a tool-call ceiling. When it hits the
  limit, it restores its changes and reports.
- **Verify once.** After committing, run one verification pass and report. Do not loop
  back into fixing.
- **Watch the only budget signal there is.** Task notifications show a subagent's
  `subagent_tokens` and `tool_uses`. If they keep climbing and nothing new is committed,
  check `git log` / `git status` yourself; if the work has landed, stop the agent.
- **Narrow agents.** One agent per risky item, not one agent for a batch, so a single bad
  item cannot spend the whole batch's budget.
- **Use the cheaper model for mechanical work.** A well-specified fix or search goes to a
  smaller model; keep the larger model for judgement.
- **Fetch before you read a remote branch.** `origin/*` is a snapshot from the last
  `git fetch`, not the remote. Run `git fetch --prune origin` before reading, searching or
  diffing any `origin/*` ref, every time -- sessions run for days and other agents push
  during them. An agent searched a stale `origin/agents/develop`, found no budget rule, and
  reported that none existed; it had been pushed hours earlier, and 9 of 112 refs were out
  of date.
- **Never share a worktree with a running `lein`.** Two `lein` processes in one worktree
  overwrite `.lein-env` and corrupt each other's results.

---

> **TOP PRIORITY:** Agents MUST maintain up-to-date documentation whenever features, commands, or workflows are added or changed. See `docs/DOC-CONVENTIONS.md` for the documentation structure.

---

## Required Reading

Before working on this project, read these documents:

| Document | Purpose | When to Read |
|----------|---------|--------------|
| [`BRANCH.md`](BRANCH.md) | Branch-specific context and handoff notes | Every session start |
| [`docs/kb/UPGRADE_PLAN.md`](docs/kb/UPGRADE_PLAN.md) | **Largely historical.** Records the Jan 2026 stack modernisation; its pins are landed. One live item: nothing tracks CVEs continuously. | Before dependency work |
| [`README.md`](README.md) | Project overview, getting started, Docker setup | First time setup |
| [`docs/DOC-CONVENTIONS.md`](docs/DOC-CONVENTIONS.md) | Documentation structure and KB conventions | When creating/updating docs |

---

## Environment Variable Pattern (Canonical)

**All configuration for Datomic, secrets, and app settings is managed via a single `.env` file at the repo root.**

- All shell scripts and the canonical installer source `.env` if present.
- Docker Compose and devcontainer use `.env` via `env_file` or `containerEnv` (as fallback).
- Clojure never reads `.env` itself. `scripts/common.sh` sources it with `set -a`, so the
  entries become ordinary environment variables and `environ` picks them up like any other.
  There is no `dotenv` dependency and there never has been — read values through
  `orcpub.env/value`, which treats a blank value as absent (see `docs/kb/blank-env-values.md`).
- `.env.example` provides safe defaults; `.env` is git-ignored.

**Precedence:**
1. `.env` in repo root (authoritative, always sourced if present)
2. Docker Compose/devcontainer: use `env_file: .env` or `containerEnv` as fallback
3. Shell scripts: always source `.env` if present
4. Clojure: `environ`, reading the environment variables the shell exported — not the file

See [`docs/ENVIRONMENT.md`](docs/ENVIRONMENT.md) for full details.

---

## Project Overview

**Stack**: Full-stack Clojure/ClojureScript application
- **Backend**: Pedestal + Datomic + Buddy auth
- **Frontend**: Reagent + re-frame + Figwheel
- **Build**: Leiningen + figwheel-main

For Datomic installation and transactor setup, see [`docs/DATOMIC_SETUP.md`](docs/DATOMIC_SETUP.md).

### Key Files & Entry Points

| Purpose | Location |
|---------|----------|
| Server entry | `src/clj/orcpub/server.clj`, `src/clj/orcpub/system.clj` |
| Frontend entry | `web/cljs/orcpub/core.cljs` |
| Re-frame events | `src/cljs/orcpub/dnd/e5/events.cljs` |
| Re-frame subs | `src/cljs/orcpub/dnd/e5/subs.cljs` |
| Routes & auth | `src/clj/orcpub/routes.clj` |
| DB schema | `src/clj/orcpub/db/schema.clj` |
| Shared domain logic | `src/cljc/orcpub/entity.cljc`, `src/cljc/orcpub/template.cljc` |
| D&D 5e rules | `src/cljc/orcpub/dnd/e5/` |
| REPL/dev helpers | `dev/user.clj` |
| PDF generation | `src/clj/orcpub/pdf.clj` |
| Styles (Garden) | `src/clj/orcpub/styles/` |
| Splash page (CLJC) | `src/cljc/orcpub/dnd/e5/views_2.cljc` |
| Project config | `project.clj` |

### Code Organization

```
src/
├── clj/      # Server-only Clojure (JVM)
├── cljc/     # Shared code (runs on JVM and JS)
└── cljs/     # Client-only ClojureScript
web/
└── cljs/     # Frontend application code
```

---

## Development Commands

### Validation (Run Before Committing)

```bash
# Server-side tests (Clojure JVM only)
lein test

# ClojureScript compilation — REQUIRED after frontend changes
lein fig:build

# Linter
lein lint

# Full frontend with hot reload (figwheel-main)
lein fig:dev
```

### Starting Development Environment

```bash
# Using the menu (recommended)
./menu

# Or using scripts directly:
./scripts/start.sh datomic      # Start Datomic transactor
./scripts/start.sh init-db      # Initialize DB (first time only)
./scripts/start.sh server       # Start backend REPL
./scripts/start.sh figwheel     # Optional: Frontend hot-reload
./scripts/start.sh garden       # Optional: CSS watcher
```

### Calva (VSCode)

For interactive development, use Calva's "Jack-in" command. Select profiles:
- **start-server**: Auto-starts the web server on REPL launch
- **css-watch**: Auto-recompiles CSS (Garden) on file changes
- **dev**: Development mode with debugging tools

### Lein Profiles

```bash
lein with-profile +start-server repl              # REPL with auto-start server
lein with-profile +start-server,+css-watch repl   # REPL with server + CSS watch
lein garden once                                   # Compile CSS once
lein garden auto                                   # Watch CSS for changes
```

---

## Tooling Philosophy — Use Built-in Capabilities First

**CRITICAL PRINCIPLE**: Before writing custom scripts or tools, explore what Leiningen, Clojure, and Figwheel provide natively.

### Built-in Leiningen Capabilities

| Task | Built-in Solution | Custom Script |
|------|------------------|---------------|
| Install local JAR | Use `file:lib` repository (existing pattern) | Avoid |
| Run tests | `lein test` | Avoid |
| Lint code | `lein lint` (via plugin) | Avoid |
| Compile CSS | `lein garden` (via plugin) | Avoid |
| Start REPL | `lein repl` | Avoid |
| Build uberjar | `lein uberjar` | Avoid |
| Dependency management | `lein deps` | Avoid |

### When Custom Scripts Are Acceptable

Scripts that orchestrate multiple tools or handle environment-specific setup:
- `scripts/start.sh` — Unified service launcher
- `scripts/stop.sh` — Service stopper with graceful shutdown
- `scripts/dev-setup.sh` — Orchestrates initial dev environment setup
- `./menu` — Interactive development hub

### Look for Existing Functionality First

1. **Check existing test files** — Use established testing patterns
2. **Review existing scripts** — Extend rather than duplicate
3. **Examine existing code** — Look for reusable functions
4. **Follow established patterns** — Match naming conventions
5. **Add to existing files** — Extend integration tests, route handlers
6. **Create new files only as last resort**

---

## Environment Variables

Key environment variables (via `environ`):

| Variable | Purpose |
|----------|---------|
| `DATOMIC_URL` | Database connection string |
| `DATOMIC_PASSWORD` | Database password |
| `SIGNATURE` | JWT signing secret (authentication) |
| `PORT` | Web server port (production) |
| `EMAIL_*` | SMTP configuration for emails |

---

## Code Patterns & Conventions

### Must Preserve

1. **Shared logic** goes in `src/cljc/`, backend-only in `src/clj/`, client-only in `src/cljs/` or `web/cljs/`

2. **Component lifecycle**: Use `com.stuartsierra/component` patterns. Restart cleanly via `dev/user.clj` helpers.

3. **Authentication**: Buddy JWS tokens. Keep token handling consistent with `orcpub.routes`.

4. **Homebrew compatibility**: Front-facing keys in exports are user-facing. Never rename without providing backwards compatibility mapping.

5. **Require aliases**: When swapping libraries, keep the same alias:
   ```clojure
   ;; Good: same alias
   [clj-time.core :as t]  →  [java-time.api :as t]

   ;; Bad: new alias causes churn
   [java-time.api :as jt]
   ```

### Re-frame Conventions

Follow existing event/subscription naming in `web/cljs/orcpub/*`.

---

## Documentation Standards

See [`docs/DOC-CONVENTIONS.md`](docs/DOC-CONVENTIONS.md) for the three-tier structure
(CLAUDE.md / AGENTS.md / BRANCH.md) and KB conventions.

### Documentation Locations

| Document | Purpose | Owner |
|----------|---------|-------|
| `CLAUDE.md` | Claude Code bootstrapper (auto-loads AGENTS.md + BRANCH.md) | Thin, rarely changes |
| `AGENTS.md` | Universal AI agent instructions (this file) | Repo owner |
| `BRANCH.md` | Branch-specific context and handoff notes | Current branch maintainer |
| `README.md` | Project overview, setup, usage | All contributors |
| `docs/` | Knowledge base — technical reference docs | As needed |
| `docs/DOC-CONVENTIONS.md` | Documentation structure and conventions | Repo owner |

### Documentation Principles

1. **Single source of truth** — Don't duplicate information; reference other documents
2. **Keep it current** — Update docs in the same PR as code changes
3. **Capture insights as KB docs** — Discoveries belong in `docs/*.md`, not session transcripts
4. **Proximity** — Put docs close to what they document
5. **Agent-friendly** — Use clear headings and tables for easy parsing

---

## Testing Guidelines

1. **Always run `lein test`** before committing server changes
2. **Always run `lein fig:build`** after frontend/CLJS changes
3. **Run `lein lint`** to catch syntax issues
4. Large schema changes require migration scripts and tests
5. **See `docs/TESTING.md`** for test suite inventory, gotchas, and patterns
6. **See `docs/ENTITY-BUILD.md`** for the character build pipeline architecture
7. **`warlock_test.clj`** is the entity/build integration test — use it as a pattern
   for adding new build tests (e.g. other classes, multiclassing)

---

## PR & Safety Guidelines

1. Run `lein lint` and `lein test` before proposing changes
2. Never commit secrets (`SIGNATURE`, DB credentials)
3. Use `.env` or CI secret stores for sensitive values
4. Keep PRs small and atomic — one upgrade per PR
5. Include migration notes for breaking changes

---

## Known Warnings (Cannot Fix)

These warnings come from third-party libraries and are unfixable from our code:

1. **`garden.color/abs`** — shadows `clojure.core/abs` (Garden library issue)
2. **`datomic.common/requiring-resolve`** — Datomic Free is unmaintained
3. **PDFBox font fallback** — Missing Helvetica in Docker/CI environments

## Compatibility Warning

**Datomic Free + Java 21:** Datomic Free 0.9.5703 does NOT work on Java 21.
See [`docs/DATOMIC_SETUP.md`](docs/DATOMIC_SETUP.md) for details and test results.

---

## Additional Resources

- [Clojure Documentation](https://clojure.org/reference)
- [ClojureScript Documentation](https://clojurescript.org/)
- [Reagent](https://reagent-project.github.io/)
- [re-frame](https://day8.github.io/re-frame/)
- [Pedestal](http://pedestal.io/pedestal/0.7/)
- [Datomic](https://docs.datomic.com/)

---

*Last updated: February 2026*
