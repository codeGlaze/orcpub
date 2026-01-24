
# AI Agent Instructions — OrcPub/Dungeon Master's Vault

> **Purpose**: Help AI coding agents get immediately productive in this repository while respecting project governance and workflow rules.

---

## 🚨 Critical Rules (Read First)

### Branch Protection — MUST FOLLOW

| Branch | Protection Level | Rule |
|--------|-----------------|------|
| `develop` | **🔴 OWNER ONLY** | NEVER merge or push. Only the repo owner touches this branch. |
| `modernize-stack` | **🟡 PR REQUIRED** | All changes require a Pull Request with owner approval. No direct pushes. No overrides. |
| `upgrade/*` | **🟢 OPEN** | Agents may work freely in these branches. |

### Agent Workflow Rules

1. ❌ **NEVER** merge or push directly to `develop`
2. ❌ **NEVER** merge or push directly to `modernize-stack`
3. ✅ Work in `upgrade/*` branches
4. ✅ Create Pull Requests for review — do not merge them yourself
5. ✅ Branch new features from `upgrade/security-jackson-guava`

---

> **TOP PRIORITY:** Agents MUST maintain up-to-date documentation—including agent instructions, workflow notes, and onboarding details—whenever features, commands, or workflows are added or changed. Update AGENTS.md, README.md, and related docs in the same PR as code changes. Documentation is a first-class deliverable.

---

## 📖 Required Reading

Before working on this project, read these documents:

| Document | Purpose | When to Read |
|----------|---------|--------------|
| [`UPGRADE_PLAN.md`](UPGRADE_PLAN.md) | Current upgrade roadmap, progress, and next steps | Before any upgrade work |
| [`README.md`](README.md) | Project overview, getting started, Docker setup | First time setup |
| [`.cursor/worktrees.json`](.cursor/worktrees.json) | Worktree configuration and branch rules | When using worktrees |

---


## 🗂️ Environment Variable Pattern (Canonical)

**All configuration for Datomic, secrets, and app settings is managed via a single `.env` file at the repo root.**

- All shell scripts and the canonical installer source `.env` if present.
- Docker Compose and devcontainer use `.env` via `env_file` or `containerEnv` (as fallback).
- Clojure code uses `environ` or `dotenv` to read `.env`/ENV.
- `.env.example` provides safe defaults; `.env` is git-ignored.

**Precedence:**
1. `.env` in repo root (authoritative, always sourced if present)
2. Docker Compose/devcontainer: use `env_file: .env` or `containerEnv` as fallback
3. Shell scripts: always source `.env` if present
4. Clojure: use `environ` or `dotenv` to read `.env`/ENV

See [`docs/ENVIRONMENT.md`](docs/ENVIRONMENT.md) for full details and variable documentation.

---
## 📁 Project Overview

**Stack**: Full-stack Clojure/ClojureScript application
- **Backend**: Pedestal + Datomic Free + Buddy auth
- **Frontend**: Reagent + re-frame + Figwheel
- **Build**: Leiningen + cljsbuild

## 🟢 Datomic Pro Installation & Usage (Agent Guidance)

**Tip:** Set `DATOMIC_VERSION` in the environment to control which Datomic Pro distribution is installed by `.devcontainer/post-create.sh` (e.g., `export DATOMIC_VERSION=1.0.7482`). This keeps the version authoritative and configurable by CI or Codespaces.
### Local Datomic Transactor Script

- The canonical script for starting a local Datomic Pro transactor is `scripts/start.sh` (supports both interactive and automation workflows).
- This script is invoked by the dev menu (`./menu`) and `scripts/dev-setup.sh` for local development workflows.
- Its responsibilities are:
   - Preparing a transactor properties file for local dev from the provided template and ensuring expected vendor layout is present (installation is handled by `.devcontainer/post-create.sh`).
   - Starting the transactor process in the background, writing a PID file to `logs/`, and waiting for port readiness.
   - Managing logs and PID files for troubleshooting.
   - Supporting automation via flags: `--quiet` (minimal output), `--check` (pre-flight validation), `--idempotent` (succeed if already running).
- **Installation of Datomic is handled by `.devcontainer/post-create.sh` (canonical installer).** The installer will ensure the full Datomic distribution is unzipped into `lib/com/datomic/datomic-pro/<version>/`, flatten nested directories if present, and run the vendor `bin/maven-install` to populate the Maven/local layout so Leiningen can resolve dependencies. If an automated install is explicitly requested, `scripts/start.sh --install` will invoke the canonical `.devcontainer/post-create.sh` rather than duplicating install logic, ensuring a single source of truth and consistent resolution.
- The entirety of datomic's zip contents need to be copied to `lib/com/datomic/datomic-pro/<version>/` so they can be used by datomic when it launches.
- The script is idempotent (with `--idempotent` flag) and does not tamper with vendor JARs or Datomic internals.
- This separation ensures there is a single source of truth for peer JAR installation and avoids duplication or accidental tampering.

**Transactor vs Peer JAR:**
- The Datomic transactor is NOT a JAR. It is a process started using a configuration file (e.g., `.datomic/datomic-pro-<version>/config/dev-transactor-template.properties`).
- The transactor is started by running the `bin/transactor` script with the appropriate properties file, NOT by running a JAR directly.
- The Datomic peer library (for use in Clojure code, i.e., `datomic.api`) is distributed as a JAR (e.g., `peer.<version>.jar`) in the Datomic Pro zip under `lib/`.
- **Do NOT rename or move the peer JAR after unzipping.** The peer JAR must retain its original name (e.g., `peer.1.0.7482.jar`) and location for compatibility and reproducibility.
- The correct way to "install" the peer JAR for Leiningen is to copy it (without renaming) into the vendor layout: `lib/com/datomic/datomic-pro/<version>/peer.<version>.jar`.
- The project should reference the peer JAR in `project.clj` using the `file:lib` repository pattern. Do not use the transactor JAR for the peer API.

**Unzipping Datomic Pro:**
- Always unzip the Datomic Pro distribution as-is into `.datomic/datomic-pro-<version>/`.
- Do not rename, move, or tamper with any files inside the unzipped directory, especially the peer JAR.
- The transactor configuration file is found at `.datomic/datomic-pro-<version>/config/dev-transactor-template.properties` (or similar).
- The transactor is started using the `bin/transactor` script and the config file above.

**Development verbosity:**
- For developer convenience, the devcontainer enables `POST_CREATE_VERBOSE=1` by default so the post-create script emits timestamped, verbose logs during container creation. This makes long-running steps (download, mvn install, `lein deps`) visible in real time. In production images or CI builds, this should be disabled to reduce log noise.

**Summary for agents:**
- Never treat the transactor as a JAR or attempt to run it as one.
- Never rename the peer JAR; always use the original name and location.
- Installation of the peer JAR for Clojure/Leiningen is handled by copying it to the vendor path, not by the transactor or its config.

**⚠️ Important:** Datomic Free does NOT work on Java 21 (SSL/TLS incompatibility). See [`docs/DATOMIC_JAVA21_TEST_RESULTS.md`](docs/DATOMIC_JAVA21_TEST_RESULTS.md) for test results. Migration to Datomic Pro required for JDK 21 support.

### Key Files & Entry Points

| Purpose | Location |
|---------|----------|
| Server entry | `src/clj/orcpub/server.clj`, `src/clj/orcpub/system.clj` |
| Frontend entry | `web/cljs/orcpub/core.cljs` |
| Routes & auth | `src/clj/orcpub/routes.clj` |
| DB schema | `src/clj/orcpub/db/schema.clj` |
| Shared domain logic | `src/cljc/orcpub/entity.cljc`, `src/cljc/orcpub/template.cljc` |
| REPL/dev helpers | `dev/user.clj` |
| PDF generation | `src/clj/orcpub/pdf.clj` |
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

## 🛠️ Development Commands

### Validation (Run Before Committing)

```bash
# Server-side tests (Clojure JVM only)
lein test

# ClojureScript compilation — REQUIRED after frontend changes
lein cljsbuild once dev

# Linter
lein lint

# Full frontend with hot reload (figwheel-main)
lein fig:dev
```

### What Each Command Validates

| Command | Scope | Catches |
|---------|-------|---------|
| `lein test` | Server-side only | Backend logic, routes, DB, PDF |
| `lein lint` | CLJ + CLJS syntax | Typos, unused vars, style |
| `lein cljsbuild once dev` | ClojureScript | Reagent/re-frame API changes, CLJS errors |
| `lein fig:dev` | Full frontend runtime | Runtime errors, React rendering |


### Starting Development Environment

```bash
# Using the menu (recommended)
./menu

# Or using scripts directly:

# 1. Start Datomic transactor
./scripts/start.sh datomic

# 2. Initialize DB (first time only) - uses fast :init-db profile
./scripts/start.sh init-db
# Or with test user:
lein with-profile init-db run -m orcpub.dev-init --add-test-user

# 3. Start backend REPL (foreground, interactive)
./scripts/start.sh server
# Or: lein with-profile +start-server repl

# 4. Optional: Start Figwheel for frontend hot-reload
./scripts/start.sh figwheel

# 5. Optional: Start Garden CSS watcher
./scripts/start.sh garden
```

#### Menu Automation
The interactive menu (`./menu`) exposes options for:
- "Init DB" runs the database initialization only.
- "Add test user (dev only)" runs DB init and creates a test user in one step.

Agents and contributors should use the menu or the CLI flag for automated onboarding and test user creation.

---

## 🎯 Tooling Philosophy — Use Built-in Capabilities First

**CRITICAL PRINCIPLE**: Before writing custom scripts or tools, explore what Leiningen, Clojure, and Figwheel provide natively.

### Built-in Leiningen Capabilities

| Task | Built-in Solution | Custom Script ❌ |
|------|------------------|------------------|
| Install local JAR | Use `file:lib` repository (existing pattern) | ❌ Custom install script |
| Run tests | `lein test` | ❌ Custom test runner |
| Lint code | `lein lint` (via plugin) | ❌ Custom linter wrapper |
| Compile CSS | `lein garden` (via plugin) | ❌ Custom CSS build |
| Start REPL | `lein repl` | ❌ Custom REPL launcher |
| Build uberjar | `lein uberjar` | ❌ Custom build script |
| Dependency management | `lein deps` | ❌ Custom deps script |

### When Custom Scripts Are Acceptable

✅ **Acceptable**: Scripts that orchestrate multiple tools or handle environment-specific setup
- `scripts/start.sh` - Unified service launcher (Datomic, server, figwheel, garden)
- `scripts/stop.sh` - Service stopper with graceful shutdown
- `scripts/dev-setup.sh` - Orchestrates initial dev environment setup
- `./menu` - Interactive development hub

### Look for Existing Functionality First

**CRITICAL WORKFLOW**: Before creating new files, scripts, or tools, thoroughly explore existing repository functionality:

1. **Check existing test files** - Use established testing patterns and infrastructure
2. **Review existing scripts** - See if they can be extended or modified
3. **Examine existing code** - Look for reusable functions, macros, or utilities
4. **Follow established patterns** - Match existing naming conventions and structures
5. **Add to existing files** - Extend integration tests, route handlers, etc.
6. **Create new files only as last resort** - When no existing pattern fits

**Examples:**
- ✅ Add dependency tests to `test/clj/orcpub/dependencies/integration_test.clj`
- ✅ Extend existing route handlers instead of creating new ones
- ✅ Use existing macros like `with-conn` in routes tests
- ❌ Create separate test files when integration tests exist
- ❌ Write custom scripts when built-in Leiningen tasks exist

❌ **Avoid**: Scripts that duplicate Leiningen functionality
- Installing dependencies (`lein deps` exists)
- Running tests (`lein test` exists)
- Building projects (`lein build`/`lein uberjar` exists)

### Leiningen Hooks & Tasks

Use Leiningen's built-in mechanisms before creating custom tasks:

- **`:prep-tasks`** - Run before compilation (profile-specific, e.g., in `:uberjar`)
- **`:post-tasks`** - Run after compilation
- **`:hooks`** - Custom functions that run during build lifecycle
- **Plugins** - Extend Leiningen via plugins (already using: `lein-localrepo`, `lein-garden`, `lein-cljfmt`, `lein-kibit`)
- **`:repositories`** - Use `file:lib` for vendor dependencies (existing pattern)

### Example: Datomic Pro Installation

**✅ Correct approach** (using existing `file:lib` repository pattern):
```dockerfile
# Download and place JAR in lib/com/datomic/datomic-pro/VERSION/
# Uses existing file:lib repository - no lein localrepo install needed
```

**❌ Avoid** (custom script or unnecessary tooling):
```bash
# Don't create scripts/install-datomic-pro.sh
# Don't use lein localrepo install when file:lib repository exists
# Use the existing file:lib pattern (same as pdfbox)
```

### Vendor Dependencies Pattern

This project uses the `file:lib` repository pattern for vendor dependencies:

1. **Place JAR in Maven directory structure**: `lib/com/group/artifact/version/artifact-version.jar`
2. **Configure repository**: Already done in `project.clj` with `["local" {:url "file:lib"}]`
3. **CI copies to Maven repo**: CI workflow copies `lib/*` to `~/.m2/repository/` automatically

This pattern is used for:
- `org.apache.pdfbox/pdfbox` (in `lib/org/apache/pdfbox/...`)
- `com.datomic/datomic-pro` (in `lib/com/datomic/datomic-pro/...`)

### Documentation References

- [Leiningen Plugins](https://github.com/technomancy/leiningen/blob/master/doc/PLUGINS.md)
- [Leiningen Hooks](https://github.com/technomancy/leiningen/blob/master/doc/HOOKS.md)
- [Leiningen Profiles](https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md)
- [Leiningen Repositories](https://github.com/technomancy/leiningen/blob/master/doc/DEPLOY.md#repositories)
- [Figwheel Configuration](https://figwheel.org/docs/configuration.html)
- [Datomic Pro Releases](https://docs.datomic.com/releases-pro.html) - Check for latest version

---

## 🔧 Environment Variables

Key environment variables (via `environ`):

| Variable | Purpose |
|----------|---------|
| `DATOMIC_URL` | Database connection string |
| `DATOMIC_PASSWORD` | Database password |
| `SIGNATURE` | JWT signing secret (authentication) |
| `PORT` | Web server port (production) |
| `EMAIL_*` | SMTP configuration for emails |

---

## 📋 Code Patterns & Conventions

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

## 📝 Documentation Standards

### How to Document as the Project Evolves

When making changes, update documentation as follows:

| Change Type | Update These Documents |
|-------------|----------------------|
| **Upgrade/dependency change** | Update `UPGRADE_PLAN.md` with status and notes |
| **New feature or API** | Update `README.md` if user-facing; add inline docstrings |
| **Agent workflow change** | Update this file (`AGENTS.md`) |
| **Breaking change** | Add migration notes to `UPGRADE_PLAN.md` and PR description |
| **New scripts/tooling** | Add to relevant `README.md` or create one in the script's folder |

### Documentation Locations

| Document | Purpose | Owner |
|----------|---------|-------|
| `README.md` | Project overview, setup, usage | All contributors |
| `AGENTS.md` | AI agent instructions and rules | Repo owner |
| `UPGRADE_PLAN.md` | Upgrade roadmap, progress tracking | Active upgraders |
| `docs/` | Detailed technical documentation | As needed |
| `.github/copilot-instructions.md` | Pointer to AGENTS.md only | Auto-maintained |
| `.cursor/worktrees.json` | Worktree and workflow config | Repo owner |

### Documentation Principles

1. **Single source of truth** — Don't duplicate information; reference other documents instead
2. **Keep it current** — Update docs in the same PR as code changes
3. **Proximity** — Put docs close to what they document (e.g., `docker/datomic/README.md`)
4. **Agent-friendly** — Use clear headings and tables for easy parsing

---

## 🧪 Testing Guidelines

1. **Always run `lein test`** before committing server changes
2. **Always run `lein cljsbuild once dev`** after frontend/CLJS changes
3. **Run `lein lint`** to catch syntax issues
4. Large schema changes require migration scripts and tests

---

## 🔒 PR & Safety Guidelines

1. Run `lein lint` and `lein test` before proposing changes
2. Never commit secrets (`SIGNATURE`, DB credentials)
3. Use `.env` or CI secret stores for sensitive values
4. Keep PRs small and atomic — one upgrade per PR
5. Include migration notes for breaking changes

---

## ⚠️ Known Warnings (Cannot Fix)

These warnings come from third-party libraries and are unfixable from our code:

1. **`garden.color/abs`** — shadows `clojure.core/abs` (Garden library issue)
2. **`datomic.common/requiring-resolve`** — Datomic Free is unmaintained
3. **PDFBox font fallback** — Missing Helvetica in Docker/CI environments

## ⚠️ Critical Compatibility Issue

**Datomic Free + Java 21:** Datomic Free 0.9.5703 does NOT work on Java 21. Peer-to-transactor connections fail with SSL handshake timeout. See [`docs/DATOMIC_JAVA21_TEST_RESULTS.md`](docs/DATOMIC_JAVA21_TEST_RESULTS.md) for complete test results.

**Impact:** Application cannot run on Java 21 with Datomic Free. Migration to Datomic Pro required.

---

## 📖 Additional Resources

- [Clojure Documentation](https://clojure.org/reference)
- [ClojureScript Documentation](https://clojurescript.org/)
- [Reagent](https://reagent-project.github.io/)
- [re-frame](https://day8.github.io/re-frame/)
- [Pedestal](http://pedestal.io/pedestal/0.7/)
- [Datomic](https://docs.datomic.com/)

---

*Last updated: January 2026*

## Datomic Transactor Script

The `scripts/` suite provides unified service management:

- **`scripts/start.sh`** - Start services (Datomic, server, Figwheel, Garden)
- **`scripts/stop.sh`** - Stop services with graceful shutdown
- **`./menu`** - Interactive development hub
- **`scripts/common.sh`** - Shared utilities (colors, logging, port config)

**Interactive usage:**
```bash
./menu              # Interactive menu with status
./scripts/start.sh datomic  # Start Datomic
```

**Automation usage:**
```bash
./scripts/start.sh --check                    # Pre-flight validation
./scripts/start.sh datomic --quiet --idempotent  # Idempotent start
./scripts/stop.sh datomic --yes --quiet       # Non-interactive stop
```

See the scripts and project README for full documentation.
