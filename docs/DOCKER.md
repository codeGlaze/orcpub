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
| `ALT_HOST` | `datomic` | Peer fallback hostname (Docker service name, resolves via DNS) |
| `ENCRYPT_CHANNEL` | `true` | Peer-transactor encryption |

`deploy/start.sh` handles startup:
1. Substitutes `${VAR}` placeholders using pure bash `sed`
2. Validates that both passwords are set (exits 1 if missing)
3. Supports password rotation via `ADMIN_PASSWORD_OLD` / `DATOMIC_PASSWORD_OLD`
4. Uses `exec` so the transactor becomes PID 1 (receives Docker signals directly)

## host=0.0.0.0 Rationale

The `host=` property controls the Artemis acceptor bind address AND is
advertised to peers. Both roles matter for choosing the right value.

**Why `host=0.0.0.0` is correct:**
- The embedded Artemis broker binds its acceptor to whatever `host=` resolves
  to. In Docker Swarm, a service name like `datomic` resolves to the Swarm
  VIP (Virtual IP), which is NOT a local interface on the container. Artemis
  cannot bind to it, causing:
  `ActiveMQNotConnectedException — Cannot connect to server(s).`
- `host=0.0.0.0` binds to all interfaces, working in both Compose and Swarm.
- With the `dev` protocol, peers connect using the URI hostname (e.g.,
  `datomic` from `datomic:dev://datomic:4334/...`), not the advertised host.
  So `0.0.0.0` is never sent to peers for initial connections.

**Why `host=datomic` is wrong for Swarm:**
- Works in Compose (service name resolves to container's own bridge IP).
- Fails in Swarm (service name resolves to VIP → bind fails → transactor
  crashes on startup).

**`alt-host` for peer fallback:** `alt-host=datomic` (the default) provides a
resolvable hostname for peer reconnection/fallback. Docker DNS resolves the
service name correctly in both Compose bridge and Swarm overlay networks.

**If `datomic` fails to resolve:** The containers are not on a shared Docker
network. `docker compose` creates one automatically. Standalone `docker run`
requires `--network <name>`. Host networking bypasses Docker DNS entirely.

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
  test: ["CMD-SHELL", "echo > /dev/tcp/127.0.0.1/4334"]
```

Uses bash's built-in `/dev/tcp` to perform an actual TCP connection to port
4334. This works in both Compose (bridge network) and Swarm (overlay network).

The previous approach (`grep -q ':10EE ' /proc/net/tcp`) inspected the
kernel's TCP socket table, but broke in Swarm where the overlay network
namespace differs from the container's `/proc/net/tcp` view. No `curl` or
`wget` is available in the transactor image, but bash is (required by
`start.sh`).

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

## Bare-Metal Proxy (Non-Docker)

When running the app directly on the host (e.g., `java -jar` or `lein run`)
with nginx installed natively (not in Docker), the Docker service name `orcpub`
won't resolve. Set `ORCPUB_HOST` to point nginx at the app:

```bash
# In .env
ORCPUB_HOST=127.0.0.1
```

Then use `envsubst` to render the template before loading it into nginx:

```bash
export ORCPUB_HOST=127.0.0.1
export ORCPUB_PORT=8890
envsubst '${ORCPUB_HOST} ${ORCPUB_PORT}' < deploy/nginx.conf.template > /etc/nginx/conf.d/orcpub.conf
nginx -s reload
```

**Important:** The app must bind to `0.0.0.0` (production mode) for the proxy
to reach it. Dev mode binds to `localhost` only. Run with `PORT=8890` set in the
environment to use production mode.

## Swarm Migration Notes

The current configuration is Swarm-ready:

- `host=0.0.0.0` binds Artemis to all interfaces (works with overlay networks)
- `alt-host=datomic` (default) resolves via overlay DNS for peer reconnection
- Healthchecks use bash `/dev/tcp` (works in overlay network namespaces)
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
| `deploy/nginx.conf.template` | Nginx reverse proxy template (`envsubst` resolves `${ORCPUB_HOST}` and `${ORCPUB_PORT}`) |
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
