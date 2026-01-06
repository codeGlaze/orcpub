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

## 📖 Required Reading

Before working on this project, read these documents:

| Document | Purpose | When to Read |
|----------|---------|--------------|
| [`UPGRADE_PLAN.md`](UPGRADE_PLAN.md) | Current upgrade roadmap, progress, and next steps | Before any upgrade work |
| [`README.md`](README.md) | Project overview, getting started, Docker setup | First time setup |
| [`.cursor/worktrees.json`](.cursor/worktrees.json) | Worktree configuration and branch rules | When using worktrees |

---

## 📁 Project Overview

**Stack**: Full-stack Clojure/ClojureScript application
- **Backend**: Pedestal + Datomic Free + Buddy auth
- **Frontend**: Reagent + re-frame + Figwheel
- **Build**: Leiningen + cljsbuild

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

# Full frontend with hot reload
lein figwheel
```

### What Each Command Validates

| Command | Scope | Catches |
|---------|-------|---------|
| `lein test` | Server-side only | Backend logic, routes, DB, PDF |
| `lein lint` | CLJ + CLJS syntax | Typos, unused vars, style |
| `lein cljsbuild once dev` | ClojureScript | Reagent/re-frame API changes, CLJS errors |
| `lein figwheel` | Full frontend runtime | Runtime errors, React rendering |

### Starting Development Environment

```bash
# 1. Start Datomic transactor (separate terminal)
bin/transactor config/samples/free-transactor-template.properties

# 2. Start backend REPL
lein with-profile +start-server repl

# 3. In REPL: initialize DB (first time only) and start server
(init-database)  ; Only needed once per fresh DB
(start-server)

# 4. Start frontend (separate terminal)
lein figwheel
```

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
