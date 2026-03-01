# Docker Reference

Consolidated reference for OrcPub's Docker infrastructure: three services,
one compose file, and the configuration patterns that connect them.

**Contents**

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

---

## Quick Start

### 1. Run the setup script

```sh
./docker-setup.sh          # interactive — prompts for passwords, email, etc.
./docker-setup.sh --auto   # non-interactive — generates random passwords
```

This creates:
- `.env` with secure random passwords and a JWT signing secret
- `data/`, `logs/`, `backups/` directories
- Self-signed SSL certificates in `deploy/`

### 2. Build and launch

```sh
docker compose up --build -d
```

First build takes ~10 minutes (downloads Datomic Pro, compiles the Clojure
uberjar). Subsequent builds use Docker layer cache and finish in seconds.

### 3. Wait for healthy

```sh
docker compose ps     # all three services should show "healthy"
```

The transactor starts in ~30 seconds. The app takes ~2 minutes (JVM startup +
Datomic schema installation). Nginx waits for both before accepting traffic.

### 4. Create your first user

```sh
./docker-user.sh init                                # uses INIT_ADMIN_* from .env
./docker-user.sh create myuser me@example.com Pass1  # or specify directly
```

### 5. Open the app

Browse to **https://localhost** (self-signed cert — browser will warn).

### Upgrading an Existing Installation

If you already have a running instance with your own `.env` and data:

```sh
# 1. Pull latest source
git pull

# 2. Rebuild images (your .env is NOT overwritten)
docker compose up --build -d
```

That's it. Your `.env`, `data/`, `logs/`, and SSL certs are untouched.
Docker Compose only rebuilds the app and transactor images from the new
source — your database and configuration stay as-is.

**If the upgrade adds new `.env` variables:** Check `.env.example` for any
new entries and add them to your `.env`. New variables always have sensible
defaults, so the app will still start without them — but you may want to
configure them.

**Password rotation:** Set `ADMIN_PASSWORD_OLD` and/or
`DATOMIC_PASSWORD_OLD` in `.env` to the current password, then change
`ADMIN_PASSWORD` / `DATOMIC_PASSWORD` to the new value. Restart. Remove
the `_OLD` vars after all peers reconnect.

### Key Environment Variables

These are the variables you'll actually touch. Full reference in
[ENVIRONMENT.md](ENVIRONMENT.md).

| Variable | Required | Default | What it does |
|----------|----------|---------|--------------|
| `DATOMIC_PASSWORD` | Yes | — | App-to-transactor auth. Must match the `?password=` in `DATOMIC_URL`. |
| `ADMIN_PASSWORD` | Yes | — | Transactor admin/monitoring auth. |
| `SIGNATURE` | Yes | — | JWT signing secret. All login and API calls fail without it. |
| `DATOMIC_URL` | Yes | `datomic:dev://datomic:4334/orcpub?password=...` | Database connection URI. The hostname `datomic` is the compose service name. |
| `PORT` | No | `8890` | App server port. Nginx and healthcheck adapt automatically. |
| `ALT_HOST` | No | `127.0.0.1` | Transactor peer fallback host. Change to `datomic` for Swarm. |
| `EMAIL_SERVER_URL` | No | *(empty)* | SMTP server. Leave empty to disable email (registration still works, just no verification emails). |
| `CSP_POLICY` | No | `strict` | Content Security Policy: `strict`, `permissive`, or `none`. |
| `DEV_MODE` | No | *(empty)* | Set to `true` for CSP Report-Only mode (allows Figwheel hot-reload). |
| `LOAD_HOMEBREW_URL` | No | *(empty)* | URL to fetch `.orcbrew` plugins on first page load. |

`docker-setup.sh` generates `DATOMIC_PASSWORD`, `ADMIN_PASSWORD`, and
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

The same `docker-compose.yaml` works for Swarm with two `.env` changes and
an optional `deploy:` section.

### What changes from single-host

| Setting | Single-host (default) | Swarm |
|---------|-----------------------|-------|
| `ALT_HOST` | `127.0.0.1` | `datomic` |
| Network | bridge (auto) | overlay (auto with `docker stack deploy`) |
| Secrets | `.env` file | `.env` file or Docker secrets |

`host=datomic` in the transactor config already resolves via Docker DNS on
both bridge and overlay networks — no template change needed.

### Steps

```sh
# 1. Initialize Swarm (once per cluster)
docker swarm init

# 2. Edit .env — change ALT_HOST for multi-node overlay DNS
#    ALT_HOST=datomic
#    (everything else stays the same)

# 3. Build images (Swarm doesn't build — it needs pre-built images)
docker compose build

# 4. Deploy the stack
docker stack deploy -c docker-compose.yaml orcpub

# 5. Check service status
docker stack services orcpub
docker service logs orcpub_orcpub --follow
```

### Scaling notes

- **datomic**: Must be exactly 1 replica (Datomic transactor is a singleton).
- **orcpub**: Can scale to multiple replicas if they share the same transactor.
  Each replica connects to `datomic:4334`.
- **web**: Can scale freely. Each replica proxies to any `orcpub` replica via
  Swarm's built-in load balancing.

### Optional: Docker secrets

For production Swarm, consider replacing `.env` password vars with Docker
secrets:

```sh
echo "my-strong-password" | docker secret create datomic_password -
echo "my-admin-password"  | docker secret create admin_password -
echo "my-jwt-secret"      | docker secret create signature -
```

Then add a `secrets:` section to `docker-compose.yaml` and update
`deploy/start.sh` to read from `/run/secrets/` instead of environment
variables. The current setup works without this — secrets are an upgrade
for hardened production environments.

## File Inventory

| File | Purpose |
|------|---------|
| `docker/Dockerfile` | Multi-target: `datomic-dist` (downloader), `transactor`, `app-builder`, `app` |
| `docker/transactor.properties.template` | Complete transactor config (Option C hybrid template) |
| `deploy/start.sh` | Transactor startup: secret substitution + exec |
| `deploy/nginx.conf.template` | Nginx reverse proxy template (`envsubst` resolves `${ORCPUB_PORT}`) |
| `deploy/snakeoil.sh` | Self-signed SSL certificate generator |
| `docker-compose.yaml` | Compose file (pull or build-from-source) |
| `docker-setup.sh` | Interactive setup: generates `.env`, dirs, SSL certs |
| `.env.example` | Environment variable reference with defaults |

## Security

Both containers run as non-root users (`datomic` and `app`). Secrets are
handled with `chmod 600` file permissions, sed escaping for special characters
in passwords, and `.dockerignore` exclusion of `.env` from the build context.

For full reasoning behind each security decision, see `DOCKER-SECURITY.md`.

## See Also

- `DOCKER-SECURITY.md` — Security hardening decisions with reasoning
- `LEIN-UBERJAR-HANG.md` — Why the uberjar build uses a 3-step process
- `ENVIRONMENT.md` — All environment variables
- `docker-user-management.md` — User management in Docker deployments
