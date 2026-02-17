# Dev Tooling

## How Clojure Dev Tooling Works (Quick Primer)

Clojure projects use **Leiningen** (`lein`) as their build tool — similar to npm for JavaScript or Maven for Java. Leiningen reads `project.clj` for configuration.

**Profiles** are named configurations that control what code is available and how things compile. Think of them like npm scripts that also change the project's classpath:

- `:dev` — full development mode (REPL, Figwheel, ClojureScript, all dev helpers)
- `:init-db` — minimal mode for CLI tasks (no ClojureScript, no CSS, fast startup)
- `:uberjar` — production build (AOT-compiled, no dev code)

A **REPL** (Read-Eval-Print Loop) is an interactive Clojure session. You type expressions and get results immediately — it's how most Clojure development happens. The `user` namespace loads automatically when you start a REPL in dev mode.

## user.clj — The Dev Tooling Hub

**Location**: `dev/user.clj`

This single file is the hub for all dev tooling. It serves two purposes:

1. **REPL helpers** — functions you call interactively during development
2. **CLI entrypoint** — a `-main` function that shell scripts call for automation

### Why dev/ and not src/

`user.clj` lives in the `dev/` directory, which is **only** on the classpath in development profiles (`:dev` and `:init-db`). It is **never** included in the production jar. This means none of the dev tooling (user creation, database initialization, Figwheel helpers) ships to production — a deliberate security choice.

### REPL Functions

When you start a REPL (`lein repl`), the `user` namespace loads automatically. These functions are available:

| Function | What it does | Needs running server? |
|----------|-------------|----------------------|
| `(start-server)` | Start Pedestal web server + Datomic | No (starts it) |
| `(stop-server)` | Stop the server | Yes |
| `(init-database)` | Create database + apply schema | No |
| `(add-test-user)` | Create a pre-verified test user | No |
| `(conn)` | Get a raw Datomic connection | No |
| `(create-user! (conn) {...})` | Create a user with options | No |
| `(verify-user! (conn) "email")` | Mark a user as verified | No |
| `(delete-user! (conn) "email")` | Remove a user | No |
| `(verify-new-user "email")` | Verify via routes (legacy) | Yes |
| `(fig-start)` | Start Figwheel hot-reload | No |
| `(fig-stop)` | Stop Figwheel | — |
| `(cljs-repl)` | Connect to ClojureScript REPL | Figwheel running |
| `(with-db [conn db] ...)` | Ad-hoc DB access macro | Yes |

**"Needs running server?"** — Some functions (like `create-user!`) connect directly to Datomic, so they work without the web server. Others (like `verify-new-user`) go through the route handlers and need the full system running.

### CLI Commands

Shell scripts call user.clj via Leiningen's `run -m` flag, which invokes the `-main` function:

```bash
# Initialize database (create + apply schema)
lein with-profile init-db run -m user init-db

# Initialize database AND create a test user
lein with-profile init-db run -m user init-db --add-test-user

# Create an arbitrary user (with duplicate checking)
lein with-profile init-db run -m user create-user bob bob@example.com s3cret verify

# Mark a user as verified (useful when emails don't send in dev)
lein with-profile init-db run -m user verify-user bob@example.com

# Delete a user
lein with-profile init-db run -m user delete-user bob@example.com
```

**Why `with-profile init-db`?** Without it, Leiningen loads the full `:dev` profile which compiles ClojureScript and CSS — slow and unnecessary for database tasks. The `:init-db` profile skips all that for fast startup.

## Shell Scripts

You don't need to remember the `lein` commands above. Shell scripts wrap them:

### start.sh — Service Launcher

```bash
./scripts/start.sh datomic    # Start Datomic transactor
./scripts/start.sh init-db    # Initialize database (calls user.clj init-db)
./scripts/start.sh server     # Start backend REPL
./scripts/start.sh figwheel   # Start Figwheel hot-reload
./scripts/start.sh garden     # Start Garden CSS watcher
```

Flags: `--quiet` (less output), `--check` (pre-flight only), `--idempotent` (succeed if already running)

### stop.sh — Service Shutdown

```bash
./scripts/stop.sh datomic --yes --quiet
```

### menu — Interactive Hub

```bash
./menu    # Interactive terminal menu with status display
```

### create_dummy_user.sh — Create a User

```bash
./scripts/create_dummy_user.sh testuser test@example.com s3cret verify
```

### dev-setup.sh — First-Time Onboarding

Runs the full first-time setup: start Datomic, install dependencies, initialize database.

```bash
./scripts/dev-setup.sh
```

## config.clj — Configuration Hub

**Location**: `src/clj/orcpub/config.clj`

Single source of truth for all runtime configuration. Reads environment variables via the `environ` library. Unlike user.clj, this file IS in production — it's the config layer the app uses at runtime.

| Function | Returns |
|----------|---------|
| `(config/get-datomic-uri)` | `DATOMIC_URL` env or `"datomic:dev://localhost:4334/orcpub"` |
| `(config/get-csp-policy)` | `CSP_POLICY` env or `"strict"` |
| `(config/strict-csp?)` | `true` when CSP policy is strict |
| `(config/dev-mode?)` | `true` when `DEV_MODE` env is truthy |
| `(config/get-secure-headers-config)` | Pedestal secure-headers map based on CSP policy |

Used by: `system.clj`, `pedestal.clj`, `user.clj`

## Profile Security Model

| Profile | Source paths | Includes dev/? | In production jar? |
|---------|-------------|----------------|-------------------|
| `:dev` | src/clj, src/cljc, src/cljs, web/cljs, dev | Yes | No |
| `:init-db` | src/clj, src/cljc, dev | Yes | No |
| `:uberjar` | src/clj, src/cljc, src/cljs, web/cljs | No | Yes |
| (base) | src/clj, src/cljc, src/cljs | No | — |

The `dev/` directory (containing user.clj) is only available in development profiles. The production uberjar contains zero dev tooling.
