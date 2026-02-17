# Dev Tooling

## user.clj

`dev/user.clj` is the primary REPL helper file. It loads automatically in the `:dev` profile.

### Functions

| Function | Purpose |
|----------|---------|
| `(start-server)` | Start the full Pedestal + Datomic system |
| `(stop-server)` | Stop the system |
| `(init-database)` | Create DB and apply schema |
| `(add-test-user)` | Create a verified test user (dev only) |
| `(fig-start)` | Start figwheel-main hot-reload |
| `(fig-stop)` | Stop figwheel |
| `(cljs-repl)` | Connect to ClojureScript REPL |
| `(with-db [conn db] ...)` | Macro for ad-hoc DB access |
| `(verify-new-user email)` | Mark a user as verified |

### Figwheel Lazy Loading

Figwheel-main is lazy-loaded via `delay` so server-only REPL sessions don't pull in ClojureScript tooling:

```clojure
(def ^:private fig-api
  (delay
    (require 'figwheel.main.api)
    (find-ns 'figwheel.main.api)))
```

## CLI Entrypoints

Two CLI-callable namespaces exist for automation (used by `start.sh` and `menu`):

### dev_init.clj

**Purpose**: Database initialization from the command line.

```bash
lein with-profile init-db run -m orcpub.dev-init
lein with-profile init-db run -m orcpub.dev-init --add-test-user
```

Uses the `:init-db` Leiningen profile (no CLJS compilation, minimal deps). Calls `datomic.api/create-database` + schema transact. The `--add-test-user` flag delegates to `user/add-test-user`.

### dev_tools.clj

**Purpose**: User CRUD from the command line.

```bash
lein run -m orcpub.dev-tools testuser test@example.com s3cret verify
```

Provides `create-user!`, `verify-user!`, `delete-user!` functions.

### Consolidation Note

Both `dev_init.clj` and `dev_tools.clj` overlap with `user.clj` functions. A planned consolidation will merge their CLI functionality into `user.clj` with a `-main` dispatch, eliminating the separate files. This is tracked as a walk-through task.

## Scripts

### start.sh

Unified service launcher. Replaces ad-hoc scripts for starting individual services.

```bash
./scripts/start.sh datomic           # Start Datomic transactor
./scripts/start.sh init-db           # Initialize database
./scripts/start.sh server            # Start backend REPL
./scripts/start.sh figwheel          # Start figwheel hot-reload
./scripts/start.sh garden            # Start Garden CSS watcher
```

Flags: `--quiet`, `--check` (pre-flight), `--idempotent`

### stop.sh

Graceful service shutdown.

```bash
./scripts/stop.sh datomic --yes --quiet
```

### menu

Interactive development hub. Wraps start.sh/stop.sh with a terminal menu.

```bash
./menu
```

### dev-setup.sh

First-time onboarding script. Currently orchestrates: start Datomic, install deps, init DB. Planned update to include test user creation and full ready-to-go setup.

## config.clj

Single source of truth for runtime configuration. Centralizes settings that were previously scattered across multiple files.

```clojure
(ns orcpub.config
  (:require [environ.core :refer [env]]))

;; Datomic
(config/get-datomic-uri)    ;; DATOMIC_URL env or default
(config/datomic-env)        ;; raw env value or nil

;; CSP
(config/get-csp-policy)     ;; CSP_POLICY env or "strict"
(config/strict-csp?)        ;; true when policy is "strict"
(config/dev-mode?)          ;; true when DEV_MODE env is truthy
(config/get-secure-headers-config)  ;; Pedestal secure-headers map
```

Used by: `system.clj`, `pedestal.clj`, `dev_init.clj`, `dev_tools.clj`, `user.clj`
