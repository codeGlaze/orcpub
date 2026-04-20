# CLAUDE.md — Agent Onboarding

## Project

OrcPub2 — a D&D 5e character builder. Full-stack Clojure/ClojureScript.

- **Backend**: Pedestal + Datomic + Buddy auth
- **Frontend**: Reagent + re-frame + Figwheel
- **Build**: Leiningen

## Documentation

Extensive KB docs live on `origin/agents/develop` (not on this branch):

```bash
git fetch origin agents/develop
git show origin/agents/develop:docs/kb/namespace-architecture.md    # full codebase map
git show origin/agents/develop:docs/kb/entity-options-architecture.md  # entity/options tree
git show origin/agents/develop:docs/kb/fork-customization.md        # branding/integrations
git show origin/agents/develop:docs/kb/srd-vs-plugin-content.md     # what's hardcoded vs plugins
git show origin/agents/develop:docs/TESTING.md                      # test suite + E2E
git show origin/agents/develop:docs/kb/testing-infrastructure.md    # test patterns
```

Also read `AGENTS.md` and `BRANCH.md` on that branch for workflow rules.

## Key Files

| Purpose | Location |
|---------|----------|
| Frontend entry | `web/cljs/orcpub/core.cljs` |
| Character builder | `src/cljs/orcpub/character_builder.cljs` |
| Re-frame events | `src/cljs/orcpub/dnd/e5/events.cljs` |
| Re-frame subs | `src/cljs/orcpub/dnd/e5/subs.cljs` |
| Content subs (classes, races, spells) | `src/cljs/orcpub/dnd/e5/spell_subs.cljs` |
| Homebrew builder views | `src/cljs/orcpub/dnd/e5/views.cljs` |
| Content reconciliation | `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs` |
| Template DSL | `src/cljc/orcpub/template.cljc` |
| Entity model | `src/cljc/orcpub/entity.cljc` |
| D&D 5e options (class builders) | `src/cljc/orcpub/dnd/e5/options.cljc` |
| Branding (server) | `src/clj/orcpub/fork/branding.clj` |
| Branding (client) | `src/cljs/orcpub/fork/branding.cljs` |
| Backend routes | `src/clj/orcpub/routes.clj` |
| Tests (CLJ/CLJC) | `test/clj/`, `test/cljc/` |
| E2E tests (Playwright) | On `testing/develop` branch: `e2e/scenarios/` |

## Architecture Notes

### Entity Storage

Characters store choices in `::entity/options`, a nested map. All identity
uses `::entity/key` (keywords like `:fighter`, `:calishite`). Names (`:name`,
`::t/name`) are display-only and never used for persistence or lookups.

### Content: SRD vs Plugins

Only SRD content is hardcoded (12 base classes, 9 races, Acolyte background,
Grappler feat, 1 subclass per class). Everything else comes from `.orcbrew`
plugin files. The `plugin-*` subs return plugin-only content; full subs
(e.g. `::classes5e/classes`) merge SRD + plugin.

### Fork Override Pattern

6 files under `src/*/orcpub/fork/` differ between public and production.
Shared code calls the same API — it just gets different results. See
`fork-customization.md` on `agents/develop`.

### Branding Config Bridge

Server env vars -> `branding.clj` -> `window.__BRANDING__` JSON -> `branding.cljs`
reads at namespace load time. `branding/field-limits` provides `:text 255`,
`:notes 50000`, `:number 7` for input maxLength constraints.

## Build & Test

### Environment Limitation

`lein` is installed but **Clojars is blocked** by the network proxy (403
Forbidden). Maven Central works. This means `lein deps` fails on first run
because lein plugins and most Clojure libraries are hosted on Clojars.

**Workaround needed**: A pre-cached `.m2/repository` with all deps. See
"Open Items" below.

### Commands (when deps are available)

```bash
lein test                      # JVM tests (123 tests, 332 assertions)
lein cljsbuild once dev        # ClojureScript compilation check
lein lint                      # clj-kondo linter
```

### Docker

The project runs in Docker (`docker-compose.yaml`), but the Docker daemon
is not running in this environment either. The `dev.sh` script wraps lein
commands in a Docker container — it works when Docker is available.

## What This Branch Contains

Branch `claude/check-character-field-zGYRJ` has 4 commits on top of `develop`:

### 1. Consolidate character-field DRY violation
`character-field-255` and `character-field-50000` were two nearly-identical
functions differing only by maxLength. Merged into a single `character-field`
that takes a `max-len` parameter and uses `branding/field-limits`.

**File**: `character_builder.cljs` lines 128-147

### 2. Add class source toggle
Plugin classes had source names baked into `:name` at the subscription level
(`spell_subs.cljs:473`). Moved source display to render time with a toggle
checkbox ("Show Sources") in the class selector.

**Files**: `spell_subs.cljs` (stop suffixing `:name`), `character_builder.cljs`
(add toggle UI, pass `show-sources?` to `class-level-selector`)

### 3. Fix human subrace false positive
Content reconciliation flagged human ethnic subraces (Calishite, Chondathan,
etc.) as missing content immediately on selection. Added `builtin-subraces`
set to `content_reconciliation.cljs`.

**File**: `content_reconciliation.cljs` lines 167-195

### 4. Extract "Default Option Source" constant
`db/default-plugin-source` in `db.cljs` — the catch-all source name for
homebrew content when users don't specify a source pack name.

**File**: `db.cljs`

## Open Items / TODOs

### Content Reconciliation Rework (Important)
The `builtin-*` sets in `content_reconciliation.cljs` are a fragile approach —
hardcoded lists that must stay in sync with the template. The human subrace
bug proved this. A better approach: have `available-content` sub use the full
rendered content subs (SRD + plugins) instead of `plugin-*` subs, eliminating
the need for exclusion lists entirely. Requires normalizing key extraction
across different sub shapes (`::t/key` vs `:key`).

### Source Display HOF Extraction
TODO in `character_builder.cljs` line 200: the source-suffix formatting logic
is inlined in the class dropdown. Should be extracted into an HOF shared across
all option selectors that display plugin sources.

### "Default Option Source" Consolidation
The string `"Default Option Source"` is now a constant in `db.cljs` but is
still hardcoded in `events.cljs`, `views.cljs`, and other files. Those should
be migrated to use `db/default-plugin-source`.

### Deps Cache for CI/Agent Environments
`lein` can't download from Clojars in this environment. Need a pre-cached
`.m2/repository` — either vendored into a separate `orcpub-deps` repo
(cloned via session-start hook) or expanded in `lib/`. See conversation
history for details.

### Multiclass Prereq Fix (Applied Separately)
A patch fixing `prereq-failures` and `class-level-selector` to pass
`built-char` was shared by the user and is being applied by admin on
`develop`. Not on this branch.
