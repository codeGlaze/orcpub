# Docker Reference

Consolidated reference for OrcPub's Docker infrastructure: three services,
one compose file, and the configuration patterns that connect them.

**Contents**

- [Platform Notes](#platform-notes) — Linux, macOS, Windows
- [Quick Start](#quick-start) — setup, build, launch, first user
  - [Upgrading an Existing Installation](#upgrading-an-existing-installation)
  - [Key Environment Variables](#key-environment-variables)
- [Architecture](#architecture) — services, networking, boot order
- [Compose File](#compose-file) — image naming, registry overrides
- [Transactor Configuration](#transactor-configuration-option-c-hybrid-template)
- [host=datomic Rationale](#hostdatomic-rationale)
- [Jetty Binding](#jetty-binding)
- [Healthcheck Strategy](#healthcheck-strategy) — app and transactor probes
- [Production Memory Tuning](#production-memory-tuning)
- [Volumes](#volumes)
- [Docker Swarm Deployment](#docker-swarm-deployment) — ALT_HOST, scaling, secrets
- [File Inventory](#file-inventory)
- [Security](#security)
- [Troubleshooting](#troubleshooting) — env var conflicts, protocol errors, restarts

---

### Platform Notes

The setup and management scripts (`run`, `docker-user.sh`,
`docker-migrate.sh`) are bash scripts. They work on:

- **Linux** — natively
- **macOS** — natively (Terminal.app, iTerm, etc.)
- **Windows** — run from **Git Bash** (ships with Git for Windows) or
  **WSL** (Windows Subsystem for Linux, required by Docker Desktop anyway)

All scripts handle Windows line endings (`\r\n`) in `.env` and config files
defensively. If you edit `.env` in Notepad or another Windows editor, it
will still work.

`docker compose` itself works on all three platforms without special steps.

---

## Quick Start — New Install

You need to run **3 commands**. You don't need to edit any files.

```sh
# 1. Setup — generates passwords, creates directories, makes SSL certs
./run --auto

# 2. Build and launch (first build takes ~10 minutes, then seconds)
docker compose up --build -d

# 3. Create a user (once all 3 services show "healthy" in docker compose ps)
./docker-user.sh create myuser me@example.com MyPassword1
```

Open **https://localhost** (self-signed cert — your browser will warn, that's
normal).

That's it. Everything else is optional.

<details>
<summary>Want to customize? (email, admin user, ports)</summary>

Run the interactive version instead:

```sh
./run          # prompts for each setting
```

Or edit `.env` after setup — it's a plain text file with comments explaining
every setting. See `.env.example` for the full list.

</details>

### What the setup script creates

| What | Where | Purpose |
|------|-------|---------|
| `.env` | project root | All your passwords and settings |
| `data/` | project root | Database files (persists between restarts) |
| `logs/` | project root | Transactor log files |
| `backups/` | project root | Database backup destination |
| `deploy/snakeoil.*` | deploy/ | Self-signed SSL certificate + key |

### What you actually run day-to-day

| Task | Command |
|------|---------|
| Start everything | `docker compose up -d` |
| Stop everything | `docker compose down` |
| Rebuild after code changes | `docker compose up --build -d` |
| Check status | `docker compose ps` |
| View logs | `docker compose logs orcpub --tail 50` |
| Create a user | `./docker-user.sh create <user> <email> <pass>` |
| List users | `./docker-user.sh list` |

---

## Upgrading an Existing Install

**You edit: nothing.** The upgrade script checks your `.env` and fixes
anything that's out of date.

```sh
# 1. Pull latest code
git pull

# 2. Let the upgrade script check and fix your .env
./run --upgrade

# 3. Rebuild and restart
docker compose up --build -d
```

The upgrade script:
- **Backs up** your `.env` before changing anything
- **Detects** old patterns (password in URL, missing variables)
- **Fixes** them automatically
- **Warns** about things that need your attention (like Free→Pro migration)
- **Does nothing** if your `.env` is already fine

Your `data/`, `logs/`, and SSL certs are never touched.

<details>
<summary>What does --upgrade actually check?</summary>

| Issue | What it does |
|-------|--------------|
| `?password=` in DATOMIC_URL | Extracts password to DATOMIC_PASSWORD, cleans URL |
| Missing DATOMIC_PASSWORD | Adds it (generates random if `--auto` also passed) |
| Missing SIGNATURE | Adds it (warns that sessions will be invalidated) |
| Missing ADMIN_PASSWORD | Adds it |
| `datomic:free://` in URL | Changes to `datomic:dev://` (Datomic Pro). Warns about data migration if needed |
| `localhost` in URL | Changes to `datomic` (Docker service name). Warns if you run outside Docker |

If you prefer to edit files by hand, just read the output — it tells you
exactly what to change and why.

</details>

### Don't use `.env`? (Export vars directly)

If you set passwords as shell environment variables instead of using `.env`,
the upgrade script can't check your setup. But nothing breaks — the app
reads env vars the same way it always did.

If you want to start using `.env`:

```sh
./run --auto    # creates .env with generated passwords
```

Then edit the generated `.env` to use your existing passwords instead of
the random ones.

### Optional: Docker secrets

Move passwords out of `.env` so they aren't all sitting in one file:

```sh
# Single server (creates secret files on disk)
./run --secrets

# Swarm cluster (stores secrets encrypted in the cluster)
./run --swarm
```

Both read your existing passwords from `.env` or shell env vars — you
don't re-enter anything. If you're not sure which to pick, run `--secrets`
and it will ask if you're using Swarm.

The script creates `docker-compose.secrets.yaml` and adds `COMPOSE_FILE`
to your `.env` so compose merges both files automatically. No manual
edits needed. Secret files take priority over `.env` — you can leave
`.env` passwords in place or remove them.

### Password rotation

```sh
# 1. In .env, add the OLD vars with your current password:
#    ADMIN_PASSWORD_OLD=current-password
#    DATOMIC_PASSWORD_OLD=current-password

# 2. Change the main vars to the new password:
#    ADMIN_PASSWORD=new-password
#    DATOMIC_PASSWORD=new-password

# 3. Restart
docker compose down && docker compose up -d

# 4. After everything is working, remove the _OLD vars from .env
```

### Key Environment Variables

These are the variables you'll actually touch. Full reference in
[ENVIRONMENT.md](ENVIRONMENT.md).

| Variable | Required | Default | What it does |
|----------|----------|---------|--------------|
| `DATOMIC_PASSWORD` | Yes | — | App-to-transactor auth. The app appends `?password=` to `DATOMIC_URL` at startup. |
| `ADMIN_PASSWORD` | Yes | — | Transactor admin/monitoring auth. |
| `SIGNATURE` | Yes | — | JWT signing secret. All login and API calls fail without it. |
| `DATOMIC_URL` | Yes | `datomic:dev://datomic:4334/orcpub` | Database connection URI. No `?password=` — the app adds it from `DATOMIC_PASSWORD`. |
| `PORT` | No | `8890` | App server port. Nginx and healthcheck adapt automatically. |
| `ALT_HOST` | No | `127.0.0.1` | Transactor peer fallback host. Change to `datomic` for Swarm. |
| `EMAIL_SERVER_URL` | No | *(empty)* | SMTP server. Leave empty to disable email (registration still works, just no verification emails). |
| `CSP_POLICY` | No | `strict` | Content Security Policy: `strict`, `permissive`, or `none`. |
| `DEV_MODE` | No | *(empty)* | Set to `true` for CSP Report-Only mode (allows Figwheel hot-reload). |
| `LOAD_HOMEBREW_URL` | No | *(empty)* | URL to fetch `.orcbrew` plugins on first page load. |

`run` generates `DATOMIC_PASSWORD`, `ADMIN_PASSWORD`, and
`SIGNATURE` automatically. You only need to edit `.env` if you want email or
custom branding.

## Architecture

Three services form the stack:

| Service | Role | Base |
|---------|------|------|
| `datomic` | Datomic Pro transactor (dev storage protocol) | Alpine + Datomic distribution |
| `orcpub` | Clojure uberjar served by Jetty (port 8890) | Alpine + JRE |
| `web` | Nginx reverse proxy + static homebrew content | Nginx Alpine |

Docker Compose creates a default bridge network. Services communicate by
service name via Docker DNS (e.g., `orcpub` reaches the transactor at
`datomic:4334`).

Boot order is enforced by healthcheck dependencies:

    datomic --> orcpub (waits for datomic healthy) --> web (waits for orcpub healthy)

## Compose File

A single `docker-compose.yaml`:

```sh
docker compose up --build -d  # Build from source using docker/Dockerfile
```

Image names default to local build tags (`orcpub-app`, `orcpub-datomic`).
Override with `ORCPUB_IMAGE` and `DATOMIC_IMAGE` env vars to point at a
registry (e.g., `ORCPUB_IMAGE=registry/orcpub:2.6.0.0 docker compose up -d`).

## Transactor Configuration (Option C Hybrid Template)

### Previous Approach

A fragile sed-chain — 5 build-time and 6 runtime substitutions — that mutated
a stock properties file. Broke silently on Datomic upgrades when line formats
changed.

### Current Approach

`docker/transactor.properties.template` is a **complete** transactor config
with all structural settings hardcoded. Only four values use `${VAR}`
placeholders for runtime substitution:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DATOMIC_PASSWORD` | *(required)* | Storage access password |
| `ADMIN_PASSWORD` | *(required)* | Admin/monitoring password |
| `ALT_HOST` | `127.0.0.1` | Advertised host for peer connections |
| `ENCRYPT_CHANNEL` | `true` | Peer-transactor encryption |

`deploy/start.sh` handles startup:
1. Substitutes `${VAR}` placeholders using pure bash `sed`
2. Validates that both passwords are set (exits 1 if missing)
3. Supports password rotation via `ADMIN_PASSWORD_OLD` / `DATOMIC_PASSWORD_OLD`
4. Uses `exec` so the transactor becomes PID 1 (receives Docker signals directly)

## host=datomic Rationale

The `host=` property in transactor.properties controls what the transactor
**advertises** to peers — it is not what it binds to.

Connection flow:
1. Peer connects to the transactor using the URI hostname (e.g., `datomic` in
   `datomic:dev://datomic:4334/orcpub`)
2. Transactor responds with its advertised `host=` value
3. Peer uses the advertised host for subsequent connections

**Why `host=0.0.0.0` is wrong:** It works on single-host Docker Compose by
accident because the `dev://` protocol reuses the URI hostname rather than the
advertised address. In Docker Swarm with a multi-node overlay network, a peer
on node A would try to connect to `0.0.0.0:4334` locally, hitting itself
instead of the transactor on node B.

**Why `host=datomic` is correct:** The Docker Compose service name resolves
via Docker DNS in both single-host bridge networks and multi-node overlay
networks.

**If `host=datomic` fails to resolve:** The containers are not on a shared
Docker network. `docker compose` creates one automatically. Standalone
`docker run` requires `--network <name>`. Host networking bypasses Docker DNS
entirely.

## Jetty Binding

| Mode | `::http/host` | Why |
|------|---------------|-----|
| Production | `"0.0.0.0"` | Reachable from nginx and healthcheck containers |
| Development | `"localhost"` | Loopback only, no external exposure |

Pedestal defaults to `"localhost"` when `::http/host` is unset. This silently
blocks all Docker inter-container traffic because the container's loopback
interface is isolated from the bridge network.

## Healthcheck Strategy

### App (orcpub)

```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -q --spider http://127.0.0.1:${PORT:-8890}/health"]
  interval: 10s
  timeout: 5s
  retries: 30
  start_period: 60s
```

`CMD-SHELL` enables `${PORT}` expansion from the container's environment.
`CMD` (array form) would pass it literally with no expansion.

- BusyBox `wget` (Alpine) only supports `-q` and `--spider` — not GNU flags
  like `-O` or `--timeout`
- Use `127.0.0.1` not `localhost` to avoid IPv4/IPv6 ambiguity
- `/health` returns 200 OK — lighter than `/` which renders the full SPA page
- The app boot takes ~110 seconds; 60s start_period + 30 retries at 10s
  interval provides sufficient tolerance

### Transactor (datomic)

```yaml
healthcheck:
  test: ["CMD-SHELL", "grep -q ':10EE ' /proc/net/tcp || grep -q ':10EE ' /proc/net/tcp6"]
```

Checks that port 4334 (hex `0x10EE`) is listening by inspecting the kernel's
TCP socket table. No `curl` or `wget` is available in the transactor image.

## Production Memory Tuning

The template defaults are safe for the stock 1 GB heap (`-Xmx1g` in
`bin/transactor`). Scale up when increasing the heap.

**Constraint:** `(object-cache-max + memory-index-max)` must be < 75% of `-Xmx`.

| Setting | 1 GB heap (`-Xmx1g`) | 4 GB heap (`-Xmx4g`) |
|---------|-----------------------|-----------------------|
| `memory-index-threshold` | `16m` (default) | `32m` |
| `memory-index-max` | `128m` (default) | `512m` |
| `object-cache-max` | `128m` (default) | `512m` |

Rules of thumb (from Datomic capacity planning docs):

- `memory-index-threshold`: ~1% of `-Xmx`
- `memory-index-max`: ~13% of `-Xmx` (minimum 2x threshold)
- `object-cache-max`: ~50% of `-Xmx` minus index memory

## Volumes

| Volume Mount | Service | Purpose |
|--------------|---------|---------|
| `./data` | datomic | Datomic dev storage data files |
| `./logs` → `/log` | datomic | Transactor log output (host `logs/`, container `/log`) |
| `./backups` | datomic | Datomic backup destination |
| `./deploy/homebrew/` | web | User homebrew `.orcbrew` files served by nginx |
| `./deploy/nginx.conf.template` | web | Nginx config template (`envsubst` at startup) |
| `./deploy/snakeoil.*` | web | Self-signed SSL certificates |

## Docker Swarm Deployment

`./run --swarm` generates a Swarm-compatible compose file and a Portainer-ready
env file. It produces **text files only** — no Docker daemon required.

### What changes from Compose

| Concern | Compose (`docker-compose.yaml`) | Swarm (`docker-compose.swarm.yaml`) |
|---------|---------------------------------|-------------------------------------|
| Volumes | Bind mounts (`./data:/data`) | Named volumes (`orcpub_data`) with `external: true` |
| Dependencies | `depends_on` + healthchecks | Removed (Swarm ignores `depends_on`) |
| Restart | `restart: always` | `deploy.restart_policy` |
| Build | `build:` supported | Removed (Swarm needs pre-built images) |
| Networks | Default bridge | Explicit overlay (`backend`) |
| `.env` file | Auto-read by `docker compose` | **Not read** by `docker stack deploy` |

### Quick start

```sh
# 1. Generate .env (if you haven't already)
./run

# 2. Generate Swarm compose + Portainer env file
./run --swarm

# 3. Pre-create named volumes (Swarm won't auto-create external volumes)
docker volume create orcpub_data
docker volume create orcpub_logs
docker volume create orcpub_backups

# 4. Deploy via CLI
set -a; source .env; set +a
docker stack deploy -c docker-compose.swarm.yaml orcpub

# 5. Check status
docker stack services orcpub
docker service logs orcpub_orcpub --follow
```

### Generated files

| File | Purpose |
|------|---------|
| `docker-compose.swarm.yaml` | Swarm-ready compose — named volumes, deploy sections, overlay networks |
| `.env.portainer` | Flat `KEY=VALUE` file — no comments, no blank lines, no quotes. Paste into Portainer's "Advanced mode" env editor |
| `transactor.properties.reference` | Generated when you bind-mount a custom `transactor.properties`. Shows current template values so you can diff against your file |

### Portainer import

Portainer has no `.env` file upload. Use its "Advanced mode" for bulk env vars:

1. **Stacks → Add stack** (or update existing)
2. Paste `docker-compose.swarm.yaml` into the compose editor
3. Click **Advanced mode** in the Environment variables section
4. Paste the contents of `.env.portainer` (one `KEY=VALUE` per line)
5. Deploy

### Upgrading an existing Swarm deployment

Running `./run --swarm` when `docker-compose.swarm.yaml` already exists:

1. Backs up the existing file (timestamped `.bak`)
2. Extracts your customizations (Traefik labels, resource limits, network names, JVM settings, env var values, stack name)
3. Regenerates the compose with the latest template, preserving your customizations
4. Shows a colorized diff:
   - **White** — unchanged lines
   - **Cyan** — new upstream lines (using defaults)
   - **Green** — new upstream lines where your `.env` value was applied
   - **Yellow** — lines where the value changed from the old file

New canonical env vars added upstream are appended to each service's
environment block — existing vars are never reordered or removed.

### Scaling notes

- **datomic**: Must be exactly 1 replica (Datomic transactor is a singleton).
- **orcpub**: Can scale to multiple replicas if they share the same transactor.
  Each replica connects to `datomic:4334`.
- **web**: Can scale freely. Each replica proxies to any `orcpub` replica via
  Swarm's built-in load balancing.

### JVM memory guidance

Do **not** set heap equal to the container memory limit — the JVM needs
headroom for off-heap memory (metaspace, thread stacks, NIO buffers).

| Approach | Example | When to use |
|----------|---------|-------------|
| Auto-percentage (recommended) | `JAVA_OPTS=-XX:MaxRAMPercentage=75.0` | JDK 11+, lets JVM scale with container limit |
| Explicit heap | `XMS=-Xms1g` / `XMX=-Xmx1g` | When you need predictable fixed sizing |
| Default (no setting) | Leave `JAVA_OPTS`, `XMS`, `XMX` empty | Small deployments, JVM picks conservative defaults |

The Swarm compose sets `deploy.resources.limits.memory` (hard ceiling) and
`deploy.resources.reservations.memory` (scheduling minimum). Configure these
in `.env` via `APP_MEMORY_LIMIT` and `APP_MEMORY_RESERVATION`.

### Docker Secrets

Docker secrets mount passwords as files at `/run/secrets/<name>` inside
the container instead of passing them as environment variables. Both the
transactor (`deploy/start.sh`) and the app (`config.clj`) already check
`/run/secrets/` first, then fall back to environment variables — no code
changes needed.

**Secret files always win over env vars.** If both exist, the file is used.

#### File-based secrets (single server, no Swarm)

```sh
./run --secrets
```

Creates a `secrets/` directory with one file per password (`chmod 600`),
generates `docker-compose.secrets.yaml`, and adds `COMPOSE_FILE` to `.env`
so compose merges both files automatically.

#### Swarm Raft secrets (cluster)

Passwords are stored encrypted in the Swarm Raft log. Containers receive
them via an in-memory tmpfs mount — never written to disk on worker nodes.

```sh
./run --swarm --secrets
```

This generates the Swarm compose (if not already present), then creates
Docker secrets via `docker secret create` and uncomments the `secrets:`
blocks in the generated compose file. Requires a running Swarm manager.

#### What changes when using secrets

| Without secrets | With secrets |
|----------------|--------------|
| Passwords in `.env` (plaintext on disk) | Passwords in `/run/secrets/` (tmpfs in Swarm, file in compose) |
| `DATOMIC_PASSWORD=xxx` in env | `DATOMIC_PASSWORD` env var optional (ignored when secret exists) |
| All config in one `.env` file | Secrets separated from non-sensitive config |

**Use `printf`, not `echo`** when creating secrets — `echo` appends a newline
that becomes part of the password. Both `start.sh` and `config.clj` strip
trailing newlines defensively, but `printf` avoids the issue entirely.

## File Inventory

| File | Purpose |
|------|---------|
| `docker/Dockerfile` | Multi-target: `datomic-dist` (downloader), `transactor`, `app-builder`, `app` |
| `docker/transactor.properties.template` | Complete transactor config (Option C hybrid template) |
| `deploy/start.sh` | Transactor startup: Docker secrets → env var fallback → template substitution → exec |
| `deploy/nginx.conf.template` | Nginx reverse proxy template (`envsubst` resolves `${ORCPUB_PORT}`) |
| `deploy/snakeoil.sh` | Self-signed SSL certificate generator |
| `docker-compose.yaml` | Compose file (pull or build-from-source) |
| `docker-compose.secrets.yaml` | Generated by `--secrets` — merges file-based secrets into compose |
| `docker-compose.swarm.yaml` | Generated by `--swarm` — Swarm-ready compose (named volumes, deploy sections) |
| `.env.portainer` | Generated by `--swarm` — flat KEY=VALUE for Portainer's Advanced mode env editor |
| `run` | Interactive setup: generates `.env`, dirs, SSL certs, secrets, Swarm compose |
| `scripts/swarm.sh` | Swarm compose generation functions (sourced by `run`) |
| `.env.example` | Environment variable reference with defaults |

## Security

Both containers run as non-root users (`datomic` and `app`). Passwords support
Docker secrets (`/run/secrets/` files) as an alternative to environment variables —
secret files take priority over env vars when both exist. Additional hardening
includes `chmod 600` file permissions, sed escaping for special characters in
passwords, and `.dockerignore` exclusion of `.env` from the build context.

For full reasoning behind each security decision, see `DOCKER-SECURITY.md`.

## Troubleshooting

### "Connection refused: localhost:4335"

The app is trying to connect to Datomic at `localhost` instead of the
`datomic` service. This happens when a shell environment variable
overrides the `.env` value.

**Check what compose sees:**

```sh
docker compose config | grep DATOMIC_URL
```

If it shows `localhost` instead of `datomic`, something in your shell is
setting `DATOMIC_URL`. Common sources:

- **Codespaces**: `containerEnv` in `devcontainer.json` sets it for local dev
- **`.bashrc` / `.profile`**: Previous `export DATOMIC_URL=...`
- **Other dotfiles**: Any shell init script that exports the variable

**Fix:**

```sh
# Check if it's set in your shell
echo $DATOMIC_URL

# Clear it for this session
unset DATOMIC_URL

# Or override inline (one-shot, doesn't persist):
source .env && docker compose up -d
```

**Why this happens:** Docker Compose resolves `${VAR:-default}` from
your shell environment first, then falls back to the `.env` file.
If your shell already has `DATOMIC_URL` set, compose uses that value
and ignores `.env` entirely. See the comment block at the top of
`docker-compose.yaml` for details.

### "Unsupported protocol :dev"

The app jar was built with Datomic Free (which only supports `datomic:free://`),
but the connection URL uses `datomic:dev://` (Datomic Pro). This means the
Docker image wasn't built from source — it was pulled from Docker Hub, where
only old Datomic Free images exist.

**Fix:** Rebuild from source:

```sh
docker compose up --build -d
```

The `--build` flag is important. Without it, compose reuses existing images
or pulls from a registry. Building from source installs the Datomic Pro peer
jar, which supports the `dev://` protocol.

### App container keeps restarting

The compose file has `restart: always`, so a crashed app retries indefinitely.
Check why it's failing:

```sh
docker compose logs orcpub --tail 50
```

Common causes:
- Wrong `DATOMIC_URL` (see above)
- Transactor not ready yet (wait for `datomic` to show "healthy")
- Missing `SIGNATURE` (set it in `.env` or `run` generates one)

### Build takes too long / hangs

First build downloads Datomic Pro (~400MB) and compiles a Clojure uberjar.
This normally takes ~10 minutes.

If it hangs during `lein uberjar`, the JVM may be running out of memory.
Check your Docker memory limit:

```sh
docker info --format '{{.MemTotal}}'
```

The build needs at least 4GB. Increase Docker's memory allocation in
Docker Desktop settings, or set `MAVEN_OPTS=-Xmx2g` in the Dockerfile's
build args.

### Healthcheck failing

```sh
docker compose ps         # shows health status
docker inspect --format='{{json .State.Health}}' orcpub-orcpub-1
```

- **datomic**: Checks if port 4334 is listening. If unhealthy, check
  `docker compose logs datomic` for password or storage errors.
- **orcpub**: Hits `http://127.0.0.1:8890/health`. Takes ~2 minutes after
  container start. If it never becomes healthy, check the app logs.
- **web**: Depends on orcpub being healthy first. Won't start until orcpub
  passes its healthcheck.

## See Also

- `DOCKER-SECURITY.md` — Security hardening decisions with reasoning
- `LEIN-UBERJAR-HANG.md` — Why the uberjar build uses a 3-step process
- `ENVIRONMENT.md` — All environment variables
- `docker-user-management.md` — User management in Docker deployments
