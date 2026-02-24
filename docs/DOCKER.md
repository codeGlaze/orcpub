# Docker Reference

Consolidated reference for OrcPub's Docker infrastructure: three services,
two compose files, and the configuration patterns that connect them.

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

## Compose Files

### `docker-compose-build.yaml` — Build from Source

Builds images locally using the multi-target `docker/Dockerfile`. Use for CI
pipelines and local development.

```sh
docker compose -f docker-compose-build.yaml up --build
```

### `docker-compose.yaml` — Pre-built Images

Pulls pre-built images from Docker Hub. Use for production and release
deployments.

```sh
docker compose up -d
```

Both files are kept in sync: same env vars, healthchecks, and volume mounts.

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

## Swarm Migration Notes

The current configuration is Swarm-ready with minimal changes:

- `host=datomic` already works with overlay network DNS
- Set `ALT_HOST=datomic` in `.env` (change from default `127.0.0.1`)
- Add a `deploy:` section to each service for replica count and placement
  constraints (~5-10 lines per service)
- Consider using Docker secrets instead of environment variables for
  `DATOMIC_PASSWORD` and `ADMIN_PASSWORD`
- No separate compose file needed — the same file works with added deploy
  config

## File Inventory

| File | Purpose |
|------|---------|
| `docker/Dockerfile` | Multi-target: `datomic-dist` (downloader), `transactor`, `app-builder`, `app` |
| `docker/transactor.properties.template` | Complete transactor config (Option C hybrid template) |
| `deploy/start.sh` | Transactor startup: secret substitution + exec |
| `deploy/nginx.conf.template` | Nginx reverse proxy template (`envsubst` resolves `${ORCPUB_PORT}`) |
| `deploy/snakeoil.sh` | Self-signed SSL certificate generator |
| `docker-compose-build.yaml` | Build-from-source compose |
| `docker-compose.yaml` | Pre-built images compose |
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
