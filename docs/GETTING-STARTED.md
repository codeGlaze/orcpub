# Getting Started

This guide takes you from a fresh clone to a running dev server. Two paths are covered:

1. **Devcontainer** (recommended) - everything installs automatically
2. **Local machine** - you install Java, Leiningen, and Datomic yourself

Both paths end at the same place: Datomic running, backend serving on port 8890, and Figwheel hot-reloading the frontend.

---

## Path 1: Devcontainer

Requires [VS Code](https://code.visualstudio.com/) with the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers), or [GitHub Codespaces](https://github.com/features/codespaces).

### 1. Open in container

```bash
git clone https://github.com/orcpub/orcpub.git
cd orcpub
code .
```

When VS Code prompts **"Reopen in Container"**, click it. The container installs Java 21, Leiningen, and Datomic Pro automatically.

### 2. Run first-time setup

Once the container is built, open a terminal inside VS Code:

```bash
./scripts/dev-setup.sh
```

This will:
- Download project dependencies (`lein deps`)
- Start the Datomic transactor
- Initialize the database schema
- Create a test user: **test** / **test@test.com** / **testpass**

### 3. Start services

```bash
./menu
```

The interactive menu shows service status and lets you start/stop with single keystrokes. Or start individually:

```bash
./menu start server      # Backend on port 8890
./menu start figwheel    # Frontend hot-reload on port 3449
```

### 4. Open the app

Navigate to `http://localhost:8890` (or the Codespaces forwarded URL). Log in with **test@test.com** / **testpass**.

---

## Path 2: Local Machine

### 1. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 21+ (OpenJDK) | [Adoptium](https://adoptium.net/) |
| Leiningen | 2.9+ | [leiningen.org](https://leiningen.org/#install) |
| Datomic Pro | 1.0.7482 | See below |

**Datomic Pro** is free (Apache 2.0 license). The devcontainer installer handles this automatically, but on a local machine you need to install it manually:

```bash
# The install script downloads and extracts Datomic Pro
./.devcontainer/post-create.sh
```

Or follow the manual steps in [docs/migration/datomic-pro.md](migration/datomic-pro.md). Datomic should end up at `lib/com/datomic/datomic-pro/1.0.7482/`.

### 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env` if needed. The defaults work for local development. The most important variable is `SIGNATURE` (JWT secret) -a dev default is provided automatically via `.lein-env`, so this step is optional for local dev.

See [ENVIRONMENT.md](ENVIRONMENT.md) for the full variable list.

### 3. Download dependencies

```bash
lein deps
```

### 4. Start Datomic

```bash
./scripts/start.sh datomic
```

Wait for "Datomic is ready" before continuing. The transactor listens on port 4334.

### 5. Initialize the database

First time only -creates the schema and applies seed data:

```bash
./scripts/start.sh init-db
```

### 6. Create a test user

```bash
./menu add test testpass
```

This creates **test@test.com** with password **testpass**, already verified.

### 7. Start the backend

```bash
./scripts/start.sh server
```

The server starts on port 8890 with an nREPL port for editor connections.

### 8. Start Figwheel (frontend hot-reload)

In a separate terminal:

```bash
./scripts/start.sh figwheel
```

Figwheel compiles ClojureScript and serves it on port 3449. Changes to `.cljs` files are pushed to the browser automatically.

### 9. Open the app

Navigate to `http://localhost:8890`. Log in with **test@test.com** / **testpass**.

---

## Using the REPL directly

If you prefer `lein repl` over the shell scripts:

```bash
lein repl
```

The `user` namespace loads automatically with helpers:

```clojure
(start-server)     ; Start web server on port 8890
(stop-server)      ; Stop web server
(init-database)    ; Initialize DB schema (first time)
(fig-start)        ; Start Figwheel from the REPL
(cljs-repl)        ; Connect to ClojureScript REPL (after fig-start)
```

The `:dev` profile provides a dev `SIGNATURE` via `.lein-env`, so auth works without extra setup. If you need to override with your `.env` values:

```bash
source .env && lein repl
```

---

## Verification

After starting services, confirm everything is working:

| Check | Expected |
|-------|----------|
| `http://localhost:8890` loads | Splash page appears |
| Log in with test@test.com / testpass | Character list page |
| Create a new character | Builder loads with race/class options |
| Edit and wait 7.5 seconds | Autosave (check Network tab for POST) |
| Upload a `.orcbrew` file (My Content page) | Homebrew options appear in builder |

---

## Troubleshooting

**Server returns 500 on login or API calls**

The `SIGNATURE` env var is missing. If using `./menu` or `./scripts/start.sh`, check that `.env` exists and has `SIGNATURE` set. If using `lein repl`, the dev profile provides a default -but if you've overridden it with an empty value, auth will fail.

**Datomic won't start**

- Check that port 4334 isn't already in use: `lsof -i :4334`
- Verify Datomic is installed: `ls lib/com/datomic/datomic-pro/1.0.7482/bin/transactor`
- Check logs: `cat logs/datomic.log`

**Figwheel compilation errors**

- Run `lein fig:build` for a one-shot compile to see all errors
- First compilation can be slow (1-2 minutes) as it downloads ClojureScript dependencies

**Port already in use**

```bash
./scripts/stop.sh          # Stop all services
./scripts/stop.sh server   # Stop just the server
```

---

## What's next

- [Architecture overview](../README.md#architecture) -how entities, templates, and modifiers work
- [Dev tooling guide](migration/dev-tooling.md) -Leiningen profiles, REPL helpers, build commands
- [Environment variables](ENVIRONMENT.md) -full config reference
- [Stack migration context](MIGRATION-INDEX.md) -why Java 21, Datomic Pro, React 18, etc.
