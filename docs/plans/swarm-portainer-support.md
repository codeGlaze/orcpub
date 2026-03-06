# Plan: Swarm / Portainer Support

## Problem

The `run` script's Swarm mode (`--swarm --up`) tries to deploy via
`docker stack deploy` directly. This creates two problems:

1. **Portainer catch-22**: Stacks deployed outside Portainer can't be managed
   by Portainer. The admin uses Portainer for all stack management.

2. **Compose assumptions break in Swarm**: bind mounts (host paths), implicit
   networks, `depends_on`, container naming (`orcpub-datomic-1` vs
   `<stack>_<service>.<replica>.<id>`), and `build:` sections all behave
   differently or don't work in Swarm.

## What the Admin Actually Wants

> "All I would really love to have is a dump of the docker-compose.yaml
> (normal one) and the .env file. I can then import the .env into Portainer
> and the docker-compose and done."

Translation: `./run --swarm` should be a **generator**, not a deployer.

## Research Findings

### Portainer (verified)

- **No `.env` file upload** — Portainer has its own env var editor UI. The
  admin must copy values from `.env` into Portainer's "Environment variables"
  tab, or use Portainer's GitOps mode (pull compose from a git repo).
- **Compose Specification format** — Portainer supports versionless compose.
  Drop `version:` field entirely.
- **Single-file import** — Portainer takes one compose file per stack. No
  multi-file merge (`-f base.yaml -f override.yaml`). This is why our Swarm
  compose must be **standalone**, not an override file.
- **Env vars persist across updates** — once entered in Portainer, env vars
  survive stack updates. The admin doesn't need to re-enter them every time.

### Traefik labels (verified — v2 and v3 compatible)

- **Label format is identical across Traefik v2 and v3.** The admin's labels
  work on both versions. No version-specific templating needed.
- **Labels MUST go under `deploy.labels`** in Swarm mode. Traefik's Swarm
  provider reads service-spec labels, not container labels. Service-level
  `labels:` (outside `deploy:`) are invisible to Traefik in Swarm.
- **`traefik.docker.network`** — should be included when a service is on
  multiple networks (e.g., backend + traefik-public). Tells Traefik which
  network to use for routing. The admin's config omits this (works by
  coincidence if only one shared network exists with Traefik).
- **`loadbalancer.server.port` is mandatory in Swarm** — Traefik can't
  auto-detect exposed ports from Swarm services the way it does in standalone.
- **Global HTTP→HTTPS redirect** is preferred over per-service middleware.
  Configure at the Traefik entrypoint level, not in our labels.
- **Traefik v3 provider name change**: `providers.docker.swarmMode=true` →
  `providers.swarm=true`. This is Traefik static config, not our concern —
  but worth documenting for users setting up Traefik for the first time.

### Swarm compose patterns (verified)

- **Standalone file is the right pattern for Portainer.** The dominant
  open-source pattern is base + override files, but Portainer doesn't support
  multi-file merge. Our standalone approach is correct for the primary use
  case. Users doing `docker stack deploy` directly can still use it.
- **Named volumes with `external: true`** — the standard for Swarm. Forces
  explicit pre-creation, preventing accidental data volume changes. For
  multi-node Swarm, users configure NFS volume drivers themselves.
- **External overlay networks** — standard for cross-stack communication
  (Traefik, Postfix, etc.). Internal service-to-service networking uses a
  stack-scoped network (not external).
- **`depends_on` is Compose-only.** Swarm ignores it. Healthchecks +
  application-level retry are the universal solution. Our app already retries
  Datomic connections, and both services have healthchecks.
- **`restart:` vs `deploy.restart_policy`** — `restart:` is Compose-only.
  `deploy.restart_policy` is Swarm-only. Both can coexist in the same file;
  each runtime reads its own.
- **`deploy.update_config`** with `order: start-first` enables zero-downtime
  deploys. Standard for production Swarm services.

### JVM memory in containers (verified)

- **Modern approach**: `-XX:MaxRAMPercentage=75.0` (JDK 11+). JVM
  auto-detects container memory limit and sets heap as percentage. No need to
  coordinate `-Xmx` with `deploy.resources.limits.memory` manually.
- **Datomic transactor caveat**: `bin/transactor` reads `XMX`/`XMS` explicitly
  and passes them to `java`. `-XX:MaxRAMPercentage` would need to go in
  `JAVA_OPTS` and would conflict with explicit `-Xmx`. Stick with explicit
  `XMX`/`XMS` for the transactor.
- **App service**: Can use either `JAVA_OPTS=-XX:MaxRAMPercentage=75.0` (modern)
  or explicit `-Xms/-Xmx` (traditional). Document both.
- **Rule of thumb**: JVM heap should be ~50-75% of container memory limit.
  The rest covers off-heap memory, native threads, and class metadata.
- **Admin's config**: transactor at 8g heap / 10g container (80%) and app at
  4g heap / 4g container (100%). The app is over-committed — OOM kill risk
  from off-heap memory. Should be ~75% max.

### DB_CLOSE_ON_EXIT (verified — dead config)

H2's `DB_CLOSE_ON_EXIT` is a **JDBC URL parameter**, not an environment
variable. The recommendation to set it `FALSE` comes from Spring Boot and
OSGi contexts where the framework manages database lifecycle. Datomic
constructs its H2 URL as `jdbc:h2:<data-dir>/datomic` with no extra
parameters (verified by decompiling `datomic.h2$sql_url`). No external tool
(Portainer, Docker, Traefik) interprets application-level env vars — they
just pass them through. Setting `DB_CLOSE_ON_EXIT` as a Docker env var is
cargo-culted config. Harmless but inert in our images. The admin's custom
image could have an entrypoint that reads it — ask them if curious.

## Admin Config Analysis

Comparing `data/swarm.example` against research findings:

| Aspect | Admin's Config | Assessment |
|--------|---------------|------------|
| Compose format | No `version:` field | Correct (Compose Specification) |
| Volumes | `dev_data` for both `/data` and `/log` | Same volume for data + logs risks I/O contention. Separate recommended. |
| Volume type | Named, NOT external | Works but risky — `docker stack rm` could delete non-external volumes. Should be `external: true`. |
| Networks | All `external: true` | Correct for Swarm |
| Traefik labels | Under `deploy.labels` | Correct placement |
| Traefik labels | Missing `traefik.docker.network` | Should add — works by coincidence with single proxy network |
| Traefik labels | Comprehensive health check | Good — Traefik-level health check complements Docker healthcheck |
| JVM (transactor) | `XMS: -Xms8g, XMX: -Xmx8g`, container: 10G | Good ratio (80%). Slightly above 75% rule but acceptable. |
| JVM (app) | `JAVA_OPTS: "-Xms4g -Xmx4g"`, container: 4G | Over-committed (100%). OOM risk from off-heap. Should be ~3g heap / 4g container. |
| `DB_CLOSE_ON_EXIT` | Set as env var | Dead config in our images (cargo-culted) |
| `restart:` | `restart: always` (no `deploy.restart_policy`) | Works in Compose but Swarm ignores `restart:`. Should add `deploy.restart_policy`. |
| `ALT_HOST` | Commented out | Should be set to `datomic` for Swarm (peer reconnection). Admin uses `host=0.0.0.0` + `alt-host=dev_dmv_datomic_dev` in transactor.properties directly. |
| Timezone | Bind-mount `/etc/timezone` + `/etc/localtime` + env `TZ` | Belt-and-suspenders. `TZ` env var alone is sufficient (POSIX standard, works on both musl and glibc). Alpine uses musl, not glibc. Bind mounts work on any Linux but are redundant when `TZ` is set. Admin's approach is harmless. |
| `depends_on` | Not present | Correct for Swarm |
| `update_config` | Not present | Should add `order: start-first` for zero-downtime updates |
| `rollback_config` | Not present | Should add for production safety |
| Service naming | `datomic_dev`, `orcpub_dev` | Custom names with `_dev` suffix for multi-environment. Our template uses generic names. |

## Design: Dual-Path Architecture

### Path A: Compose (single-host, default)
No changes needed. Current behavior works:
- `./run` or `./run --auto` = setup + build + up
- Bind mounts for data (`./data:/data`)
- Implicit networks (compose-managed)
- nginx reverse proxy (self-managed)
- `depends_on` with health conditions

### Path B: Swarm (Portainer / stack deploy)
`./run --swarm [--auto]` generates files and **stops**. No deploy.

Output:
- `.env` — same as compose, with Swarm-specific additions
- `docker-compose.swarm.yaml` — Swarm-ready compose (the deliverable)
- Instructions to import into Portainer or `docker stack deploy`

The Swarm compose differs from the base compose:
- Named volumes (external) instead of bind mounts
- External networks instead of implicit
- No `build:` sections (pre-built images required)
- No `depends_on` (not supported in Swarm)
- No nginx service (Traefik/external proxy assumed)
- `deploy:` section with resource limits, restart policy, update config
- Optional Traefik labels (commented, parameterized)
- JVM memory tunables (`XMS`, `XMX`, `JAVA_OPTS`)

## Detailed Changes

### 1. New file: `docker-compose.swarm.yaml` (generated)

Template generated by `./run --swarm`. Standalone, self-contained compose
file — Portainer can import it directly. NOT a compose override.

```yaml
# Generated by ./run --swarm — import into Portainer or use with docker stack deploy
# Standalone file (not a compose override). All values read from env vars or defaults.
name: ${COMPOSE_PROJECT_NAME:-orcpub}  # Controls stack name in Swarm / project name in Compose

services:
  # --- Web Application (Clojure/Ring) ---
  orcpub:
    image: ${ORCPUB_IMAGE:-orcpub-app}  # Use registry path for Swarm (e.g., registry.example.com/orcpub:latest)
    environment:
      PORT: ${PORT:-8890}                 # HTTP listen port inside the container
      EMAIL_SERVER_URL: ${EMAIL_SERVER_URL:-}       # SMTP host (e.g., postfix, smtp.gmail.com)
      EMAIL_ACCESS_KEY: ${EMAIL_ACCESS_KEY:-}       # SMTP username (if auth required)
      EMAIL_SECRET_KEY: ${EMAIL_SECRET_KEY:-}       # SMTP password (if auth required)
      EMAIL_SERVER_PORT: ${EMAIL_SERVER_PORT:-587}  # SMTP port (25=plain, 587=TLS, 465=SSL)
      EMAIL_FROM_ADDRESS: ${EMAIL_FROM_ADDRESS:-}   # Sender address for outgoing mail
      EMAIL_ERRORS_TO: ${EMAIL_ERRORS_TO:-}         # Error notification recipient
      EMAIL_SSL: ${EMAIL_SSL:-FALSE}                # Use SSL for SMTP (port 465)
      EMAIL_TLS: ${EMAIL_TLS:-FALSE}                # Use STARTTLS for SMTP (port 587)
      # Datomic connection URL. Hostname must match the datomic service name within this stack.
      # For cross-stack access, use the fully-qualified name: <stack>_datomic
      DATOMIC_URL: ${DATOMIC_URL:-datomic:dev://datomic:4334/orcpub}
      DATOMIC_PASSWORD: ${DATOMIC_PASSWORD}         # Peer connection password (must match transactor)
      SIGNATURE: ${SIGNATURE}                       # JWT signing key for auth tokens
      CSP_POLICY: ${CSP_POLICY:-strict}             # Content-Security-Policy mode (strict|relaxed|off)
      # JVM options for the app. Examples:
      #   -XX:MaxRAMPercentage=75.0    (auto-scale heap to 75% of container limit — recommended)
      #   -Xms1g -Xmx1g               (explicit heap — traditional)
      # Leave empty to use JVM defaults. Do NOT set heap = container limit (OOM risk from off-heap).
      JAVA_OPTS: ${JAVA_OPTS:-}
      TZ: ${TZ:-America/Chicago}                    # Timezone for log timestamps
    healthcheck:
      # BusyBox wget (Alpine). 127.0.0.1 avoids IPv4/IPv6 ambiguity.
      # /health is a lightweight 200 OK endpoint (no page render).
      test: ["CMD-SHELL", "wget -q --spider http://127.0.0.1:${PORT:-8890}/health"]
      interval: 30s
      timeout: 5s
      retries: 30
      start_period: 60s  # App needs time to compile routes and connect to Datomic
    networks:
      - backend                    # Internal: app ↔ datomic communication
      # - traefik-public           # External: uncomment if using Traefik reverse proxy
    deploy:
      resources:
        limits:
          memory: ${APP_MEMORY_LIMIT:-2G}          # Hard ceiling — container is OOM-killed above this
        reservations:
          memory: ${APP_MEMORY_RESERVATION:-1G}    # Scheduling minimum — Swarm reserves this on the node
      restart_policy:
        condition: on-failure      # Restart on crash, not on manual stop
        delay: 5s
        max_attempts: 5
        window: 120s               # Reset attempt counter after this window
      update_config:
        parallelism: 1             # Roll one container at a time
        delay: 10s                 # Wait between rolling updates
        order: start-first         # New container starts before old stops (zero-downtime)
        failure_action: rollback   # Auto-rollback if new container fails health check
      rollback_config:
        parallelism: 1
        order: stop-first          # During rollback, stop failed container first
      # --- Traefik labels (uncomment and customize) ---
      # Labels go under deploy (not service level) — Traefik's Swarm provider reads service-spec labels.
      # Works with both Traefik v2 and v3.
      # labels:
      #   - traefik.enable=true                                                    # Enable Traefik discovery
      #   - traefik.docker.network=traefik-public                                  # Which overlay network to route through (v3.2.2+: use traefik.swarm.network)
      #   - traefik.http.routers.orcpub.rule=Host(`your.domain.com`)               # Domain routing rule
      #   - traefik.http.routers.orcpub.entrypoints=websecure                      # HTTPS entrypoint
      #   - traefik.http.routers.orcpub.tls=true                                   # Enable TLS
      #   - traefik.http.routers.orcpub.tls.certresolver=letsencrypt               # Let's Encrypt resolver name
      #   - traefik.http.services.orcpub.loadbalancer.server.port=${PORT:-8890}     # Container port (required in Swarm — auto-detect doesn't work)
      #   - traefik.http.services.orcpub.loadbalancer.healthcheck.path=/health      # Traefik-level health check
      #   - traefik.http.services.orcpub.loadbalancer.healthcheck.interval=10s
      #   - traefik.http.services.orcpub.loadbalancer.healthcheck.timeout=3s
    # Uncomment to use Docker Swarm secrets instead of env vars for passwords.
    # secrets:
    #   - datomic_password
    #   - signature

  # --- Datomic Transactor (database) ---
  datomic:
    image: ${DATOMIC_IMAGE:-orcpub-datomic}  # Use registry path for Swarm
    environment:
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}       # H2 storage admin — locked on first boot, cannot change without wiping data
      DATOMIC_PASSWORD: ${DATOMIC_PASSWORD}   # Peer connection password — shared with app
      ALT_HOST: ${ALT_HOST:-datomic}          # Advertised hostname for peer reconnection (use service name for Swarm)
      ENCRYPT_CHANNEL: ${ENCRYPT_CHANNEL:-true}  # Encrypt peer ↔ transactor communication
      # JVM heap for the transactor. Read natively by Datomic's bin/transactor script.
      # Set to ~75% of DATOMIC_MEMORY_LIMIT. Example: 2G container → -Xms1536m / -Xmx1536m
      XMS: ${XMS:--Xms1g}                    # Min heap (pre-allocated on startup)
      XMX: ${XMX:--Xmx1g}                    # Max heap (hard ceiling)
      # DB_CLOSE_ON_EXIT is an H2 JDBC URL parameter, NOT a Docker env var.
      # Setting it here has no effect — Datomic constructs the H2 URL internally
      # as jdbc:h2:/data/datomic with no extra params. Kept commented for reference.
      # DB_CLOSE_ON_EXIT: 'FALSE'
    volumes:
      - orcpub_data:/data          # H2 database files — persistent, critical
      - orcpub_logs:/log           # Transactor logs — separate volume avoids I/O contention with data
      - orcpub_backups:/backups    # Backup scripts write here
      # Optional: bind-mount a custom transactor.properties to bypass start.sh template substitution.
      # This gives full control but disables password rotation and ALT_HOST switching via env vars.
      # - /path/to/transactor.properties:/datomic/transactor.properties:ro
      #
      # Optional: bind-mount backups to host filesystem for direct access (e.g., cron scripts).
      # Use INSTEAD of the orcpub_backups named volume above.
      # - /path/to/backups:/backups
    healthcheck:
      # Check if Datomic is listening on port 4334 (0x10EE in hex).
      # Reads /proc/net/tcp directly — no extra tools needed in minimal containers.
      test: ["CMD-SHELL", "grep -q ':10EE ' /proc/net/tcp || grep -q ':10EE ' /proc/net/tcp6"]
      interval: 5s
      timeout: 3s
      retries: 30
      start_period: 40s           # Transactor compiles schemas on first boot
    networks:
      - backend                    # Internal only — transactor should never be exposed externally
    deploy:
      resources:
        limits:
          memory: ${DATOMIC_MEMORY_LIMIT:-2G}
        reservations:
          memory: ${DATOMIC_MEMORY_RESERVATION:-1G}
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 5
        window: 120s
      update_config:
        parallelism: 1
        delay: 10s
        order: stop-first          # IMPORTANT: H2 embedded mode uses exclusive file lock on /data/datomic.lock.db.
        failure_action: rollback   # start-first would fail every time — new container can't acquire lock while old holds it.
    # Uncomment to use Docker Swarm secrets instead of env vars for passwords.
    # secrets:
    #   - datomic_password
    #   - admin_password

# --- Docker Swarm Secrets (optional, more secure than env vars) ---
# Secrets are encrypted in the Swarm cluster and delivered in-memory at /run/secrets/<name>.
# Both deploy/start.sh (transactor) and config.clj (app) check /run/secrets/ first,
# then fall back to environment variables. You can use secrets for some values and
# env vars for others — they coexist.
#
# Create secrets:
#   printf 'mypassword' | docker secret create datomic_password -
#   printf 'mypassword' | docker secret create admin_password -
#   printf 'mysecret'   | docker secret create signature -
#
# Then uncomment the secrets blocks on each service above and the top-level section below.
# You can then REMOVE the corresponding env vars (DATOMIC_PASSWORD, ADMIN_PASSWORD, SIGNATURE)
# from the environment: section — or leave them as fallbacks.
#
# secrets:
#   datomic_password:
#     external: true     # Created via `docker secret create`, not from a file
#   admin_password:
#     external: true
#   signature:
#     external: true

# --- Named Volumes ---
# Pre-create before first deploy:
#   docker volume create orcpub_data
#   docker volume create orcpub_logs
#   docker volume create orcpub_backups
# external: true prevents accidental deletion by `docker stack rm`
volumes:
  orcpub_data:
    external: true     # H2 database — losing this loses all data
  orcpub_logs:
    external: true     # Transactor logs
  orcpub_backups:
    external: true     # Backup storage

# --- Networks ---
networks:
  backend:
    driver: overlay    # Stack-scoped overlay for app ↔ datomic. Not external — created with the stack.
  # Uncomment if using Traefik (must be pre-created):
  #   docker network create --driver overlay --attachable traefik-public
  # traefik-public:
  #   external: true   # Shared across stacks — Traefik must also be on this network
```

**Changes from previous draft** (research-backed):
- Added `name:` field (Compose Specification standard)
- Added `CSP_POLICY`, `EMAIL_SSL`, `EMAIL_TLS` (parity with base compose)
- Added `deploy.restart_policy` (Swarm ignores `restart:`)
- Added `deploy.update_config` with `start-first` for app (zero-downtime),
  `stop-first` for datomic (exclusive H2 file lock — can't run two)
- Added `deploy.rollback_config`
- Expanded Traefik labels template: `traefik.docker.network`, entrypoints,
  TLS, certresolver, health check
- `backend` network is `driver: overlay` (not external) — stack-scoped for
  service-to-service. Only proxy networks need `external: true`.
- Lowered `reservations` default to 1G (reservations are scheduling minimums,
  not working memory — overcommitting reservations wastes cluster capacity)
- Added `DATOMIC_URL` default (was missing default, would fail if not in .env)
- `ALT_HOST` defaults to `datomic` (correct for Swarm, was `127.0.0.1` in
  base compose which doesn't work for Swarm peer reconnection)

### 2. Changes to `run` script

#### `--swarm` mode (reworked)
Current: creates Swarm secrets + compose override + falls through to `--up`.
New: generates `.env` + `docker-compose.swarm.yaml` + prints instructions.

Steps:
1. Generate `.env` (same as now)
2. Prompt for Swarm-specific values:
   - Volume names (default: `orcpub_data`, `orcpub_logs`, `orcpub_backups`)
   - Network names (default: `backend`)
   - Resource limits (default: 2G each)
   - JVM memory (`XMS`, `XMX`, `JAVA_OPTS`)
   - Image names (if using a registry)
3. Generate `docker-compose.swarm.yaml` from template
4. Print instructions:
   ```
   Generated files:
     .env                        — environment variables
     docker-compose.swarm.yaml   — Swarm-ready compose file

   Pre-create resources:
     docker volume create orcpub_data
     docker volume create orcpub_logs
     docker volume create orcpub_backups

   Portainer:
     1. Create a new stack in Portainer
     2. Paste the contents of docker-compose.swarm.yaml
     3. Add environment variables from .env in the "Environment variables" tab
        (Portainer does not support .env file upload)

   CLI deploy (docker stack deploy does NOT read .env — must export first):
     set -a; source .env; set +a
     docker stack deploy -c docker-compose.swarm.yaml orcpub
   ```

#### `--up` mode (simplified)
Current: does jq transform + `docker stack deploy`.
New: just does `docker compose up -d` for compose path.
Swarm deploys happen through Portainer or manual `docker stack deploy`.

#### `--swarm --secrets` (reworked)
Creates Docker Swarm secrets from `.env` values and generates the Swarm
compose with the `secrets:` sections **uncommented**. Steps:
1. Read `DATOMIC_PASSWORD`, `ADMIN_PASSWORD`, `SIGNATURE` from `.env`
2. Create each as a Docker Swarm secret (`docker secret create`)
3. Generate `docker-compose.swarm.yaml` with `secrets:` blocks active
4. Remove the secret env vars from the generated `.env` (they're now in
   Swarm's encrypted store, read from `/run/secrets/` at runtime)
5. Print instructions noting which values are in secrets vs env vars

Portainer also has a secrets management UI — the instructions should
note that users can create secrets there instead of via CLI.

Both `deploy/start.sh` and `config.clj` already check `/run/secrets/`
first, then fall back to env vars. No application code changes needed.

### 3. Changes to `docker-user.sh`

Current: uses `docker compose exec` which finds containers by compose
project name. Fails in Swarm (different naming).

Fix: detect deployment mode and use the right exec path:
```bash
if docker compose ps -q orcpub 2>/dev/null | head -1; then
  # Compose mode
  docker compose exec orcpub java -jar /orcpub.jar ...
else
  # Swarm mode — find container by service label
  CONTAINER=$(docker ps -q --filter "label=com.docker.swarm.service.name=${STACK}_orcpub" | head -1)
  docker exec "$CONTAINER" java -jar /orcpub.jar ...
fi
```

### 4. Changes to `deploy/start.sh`

**No changes needed.** Verified from source (`lib/.../bin/transactor`):

`bin/transactor` natively reads `XMX`, `XMS`, and `JAVA_OPTS` from the
environment. Our `start.sh` calls `exec su-exec datomic /datomic/bin/transactor`
which inherits the container's environment. These env vars just need to be
exposed in the compose file's `environment:` section.

```bash
# From bin/transactor (actual Datomic source):
if [ "$XMX" == "" ]; then XMX=-Xmx1g; fi
if [ "$XMS" == "" ]; then XMS=-Xms1g; fi
if [ "$JAVA_OPTS" == "" ]; then JAVA_OPTS='-XX:+UseG1GC -XX:MaxGCPauseMillis=50'; fi
exec java -server -cp ... $XMX $XMS $JAVA_OPTS clojure.main --main datomic.launcher "$@"
```

### 5. New env vars (added to `.env` in Swarm mode)

| Variable | Default | Purpose |
|----------|---------|---------|
| `ORCPUB_IMAGE` | `orcpub-app` | App image (registry path for Swarm) |
| `DATOMIC_IMAGE` | `orcpub-datomic` | Transactor image |
| `XMS` | `-Xms1g` | Transactor min heap |
| `XMX` | `-Xmx1g` | Transactor max heap |
| `JAVA_OPTS` | (empty) | App JVM options (e.g., `-XX:MaxRAMPercentage=75.0`) |
| `APP_MEMORY_LIMIT` | `2G` | Docker resource limit for app |
| `APP_MEMORY_RESERVATION` | `1G` | Docker scheduling reservation for app |
| `DATOMIC_MEMORY_LIMIT` | `2G` | Docker resource limit for transactor |
| `DATOMIC_MEMORY_RESERVATION` | `1G` | Docker scheduling reservation |
| `TZ` | `America/Chicago` | Timezone |
| `COMPOSE_PROJECT_NAME` | `orcpub` | Stack/project name |

### 6. DATOMIC_URL and service naming

In Swarm, the DATOMIC_URL hostname must match the Swarm service DNS name.
Within the same stack, bare service names resolve — `datomic:dev://datomic:4334/orcpub`
works because Docker Swarm resolves service names within the stack's network.

The admin's `datomic:dev://dev_dmv_datomic_dev:4334/orcpub` uses the
fully-qualified name (`<stack>_<service>`) visible from outside the stack
or from the host.

No change needed to the default URL. Document that if the app and
transactor are in different stacks, use the full `<stack>_<service>` form.

### 7. JVM memory guidance (new documentation)

The Swarm compose and instructions should document:

**Transactor** (`XMS`/`XMX`):
- Set to ~75% of `DATOMIC_MEMORY_LIMIT`
- Example: 2G container → `-Xms1536m -Xmx1536m`
- Also tune `memory-index-max` and `object-cache-max` in transactor.properties
  proportionally (see comments in template)

**App** (`JAVA_OPTS`):
- Modern: `JAVA_OPTS=-XX:MaxRAMPercentage=75.0` (auto-scales with container limit)
- Traditional: `JAVA_OPTS=-Xms1g -Xmx1g` (explicit)
- Don't set heap = container limit (100%). Off-heap memory causes OOM kills.

## What NOT to Change

- **Base `docker-compose.yaml`** — stays as-is for compose users
- **`deploy/start.sh` core logic** — secret substitution works for both paths
- **Transactor template** — works for both paths (already has `host=datomic`
  for compose, `ALT_HOST` for Swarm peer reconnection)
- **nginx service** — stays in base compose, absent from Swarm compose
  (Traefik or external proxy handles it)

## Upgrade Path for Existing Deployments

The admin's scenario: already running in Swarm with hand-crafted compose,
custom volumes, Traefik labels, tuned JVM, production data. Pulls new code.
Needs to absorb changes without losing configuration.

### The problem with "just generate a new compose"

A blind `./run --swarm` that overwrites `docker-compose.swarm.yaml` would:
- Lose custom Traefik labels and routing rules
- Reset resource limits to defaults
- Lose custom network names
- Lose JVM tuning (XMS/XMX/JAVA_OPTS)
- Potentially change volume names, orphaning production data

### `--swarm` is always safe (detection-first)

`--swarm` checks for existing `docker-compose.swarm.yaml` before doing
anything. Behavior depends on what it finds:

**No existing file** → fresh generation (prompts or `--auto` defaults).
Optional sections (Traefik, secrets, mail) stay commented with clear
instructions for the admin to uncomment what they need.

**Existing file found** → smart upgrade:

1. **Back up existing file**: `docker-compose.swarm.yaml.backup.<timestamp>`
2. **Parse the existing compose** and extract the admin's customizations:
   - Traefik labels (any `traefik.*` lines under deploy.labels)
   - Network names and `external:` flags
   - Volume names and `external:` flags
   - Resource limits and reservations
   - JVM settings (XMS, XMX, JAVA_OPTS values)
   - Bind mounts (host paths → container paths)
   - Custom env var values (non-default)
   - Stack name (`name:` field)
3. **Generate the new template with customizations already applied.**
   The admin's Traefik labels, network names, resource limits, JVM
   tuning, bind mounts — all carried forward into the new file
   automatically. Upstream structural changes (new env vars, updated
   healthchecks, new deploy options) are added around them.
4. **Print colorized full-file breakdown** — the entire generated YAML,
   color-coded so the admin can assess everything at a glance:

   **Color legend** (printed at top of output):
   ```
   │ white  │ unchanged — your config, carried forward
   │ cyan   │ new line — upstream addition, default value
   │ green  │ new line — upstream addition, your value from .env
   │ yellow │ changed — value differs from your previous config
   ```

   **How lines render:**
   - Existing config (white): literal values, no template syntax
     `XMS: -Xms8g`
   - New + default (cyan): template syntax + resolved value in comment
     `EMAIL_SSL: ${EMAIL_SSL:-FALSE}    # NEW — using default: FALSE`
   - New + admin's value (green): template syntax + their value in comment
     `ALT_HOST: ${ALT_HOST:-datomic}    # NEW — set to datomic in .env`
   - Changed (yellow): new value + old value in comment
     `timeout: 5s                       # CHANGED (was: 2s)`
   - Warnings inline (yellow on specific line):
     `priority=1                        # ⚠ lowest priority`
   - Structural YAML keywords dim (`services:`, `deploy:`, etc.)

   Followed by **Warnings** section (actionable issues only) and
   **Files** section with absolute paths.

   Demo script: `/tmp/swarm-upgrade-demo.sh`

The admin gets **one file, ready to deploy**. Not two files and a
homework assignment.

### How extraction works (bash-safe)

We don't need a YAML parser. The customizations we care about are
line-oriented and structurally predictable:

```bash
# Traefik labels: lines matching "traefik." under deploy.labels
grep -E '^\s*-?\s*traefik\.' existing.yaml

# Networks: top-level networks block (name + external flag)
sed -n '/^networks:/,/^[a-z]/p' existing.yaml

# Volumes: top-level volumes block
sed -n '/^volumes:/,/^[a-z]/p' existing.yaml

# Resource limits: deploy.resources section
# Captured per-service during template generation

# Env var values: environment block per service
# Compared against defaults — non-default values preserved

# Bind mounts: volume lines containing ':'  with host paths (start with /)
grep -E '^\s*-\s*/' existing.yaml

# Stack name: name: field at top level
grep -E '^name:' existing.yaml
```

This is robust because **our template controls the structure**. We know
exactly what sections exist and where. We're not parsing arbitrary YAML —
we're extracting known patterns from a file our script generated.

Edge case: if the admin hand-edited the structure beyond recognition
(reordered sections, added custom services, etc.), extraction may miss
things. The backup exists for this — and the summary shows exactly what
was preserved, so the admin can verify at a glance.

### Custom transactor.properties (bind-mount handling)

The admin bind-mounts their own `transactor.properties`, bypassing
`start.sh`'s template substitution entirely. This means:
- Password rotation via env vars doesn't apply (they edit the file directly)
- `ALT_HOST` changes don't propagate (hardcoded in their file)
- Template upgrades are invisible to them

On upgrade, if the existing compose has a bind-mounted transactor.properties
(detected during extraction — any volume line containing `/transactor.properties`):
1. **Preserve the bind mount** in the generated compose (it's their config)
2. **Note in the upgrade summary**: "You bind-mount transactor.properties —
   template changes won't apply automatically"
3. **Generate `transactor.properties.reference`** with the latest template
   values filled in from `.env`, so the admin can see what changed upstream
   and decide if they need to update their file

### What counts as "ours" vs "theirs"

**Ours (template-managed, may change between versions):**
- Service names, image references
- Healthcheck commands and parameters
- Default env var names and structure
- Default volume mount paths inside containers
- `deploy.update_config` and `deploy.rollback_config` defaults

**Theirs (user-managed, always preserved):**
- Traefik/proxy labels
- Resource limits and reservations
- Network names and external flags
- Volume names and external flags
- JVM tuning (XMS, XMX, JAVA_OPTS)
- Custom env var values
- Custom `deploy:` configuration beyond our defaults
- `name:` field (stack/project name)

### First-time Swarm setup

For users starting fresh (no existing Swarm compose):
1. Run `./run --swarm [--auto]`
2. Pre-create volumes: `docker volume create orcpub_data` etc.
3. Import `docker-compose.swarm.yaml` into Portainer + add env vars from
   `.env` in the Portainer UI (no `.env` file upload in Portainer)
4. Or: `docker stack deploy -c docker-compose.swarm.yaml orcpub`

### Migrating from bind mounts to named volumes

For users moving from compose (bind mounts) to Swarm (named volumes):
```bash
docker volume create orcpub_data
docker run --rm -v orcpub_data:/data -v $(pwd)/data:/src alpine cp -a /src/. /data/
# Repeat for orcpub_logs and orcpub_backups
```

## Verified Findings

| Finding | Status | Source |
|---------|--------|--------|
| `bin/transactor` reads `XMX`, `XMS`, `JAVA_OPTS` natively | Verified | Datomic source (`lib/.../bin/transactor`) |
| `DB_CLOSE_ON_EXIT` is JDBC URL param, not env var | Verified | H2 docs, decompiled `datomic.h2$sql_url` |
| Portainer has no `.env` file upload | Verified | Portainer docs |
| Traefik v2/v3 label format is identical for basic routing | Verified | Traefik migration docs |
| Swarm labels must be under `deploy.labels` | Verified | Traefik Swarm provider docs |
| `version:` field is obsolete (Compose Specification) | Verified | Docker docs |
| `restart:` ignored by Swarm, `deploy.restart_policy` ignored by Compose | Verified | Docker docs |
| `-XX:MaxRAMPercentage` works on JDK 11+ with container support | Verified | JDK docs |
| `start-first` update order enables zero-downtime deploys | Verified | Docker docs |
| Volumes without `external: true` may be deleted by `docker stack rm` | Verified | Docker docs |
| `docker stack deploy` does NOT read `.env` files | Verified | Docker docs, moby/moby#29133 |
| Traefik v3.2.2+ deprecates `traefik.docker.network` → `traefik.swarm.network` | Verified | traefik/traefik#11879 |
| Admin's `priority=1` is lowest possible (likely unintentional) | Noted | Traefik routing docs |
| Admin's app heap=container limit (4g/4g) risks OOM from off-heap | Noted | JVM container best practices |
| `TZ` env var works on both musl (Alpine) and glibc — POSIX standard | Verified | POSIX spec, Alpine docs |
| Timezone bind mounts (`/etc/localtime`, `/etc/timezone`) are redundant when `TZ` is set | Verified | Works on any Linux, but `TZ` alone is sufficient |

## Task Breakdown

1. Create Swarm compose template/generator function in `run`
2. Build extraction logic: parse existing compose for Traefik labels, networks, volumes, resource limits, JVM settings, bind mounts, env values, stack name
3. Rework `--swarm` mode: no file → fresh generate; existing file → extract customizations + generate with them applied
4. Build upgrade summary: show what was preserved vs what's new upstream
5. Handle transactor.properties bind-mount: preserve in generated compose, generate `.reference` file, warn in summary
6. Wire `--upgrade` to detect `docker-compose.swarm.yaml` and include in upgrade summary
7. Keep `--up` for compose path only (Swarm deploys via Portainer/CLI)
8. Fix `docker-user.sh` container discovery for Swarm
9. Add Swarm-specific env vars to `.env` generation
10. Wire `--swarm --secrets`: create Docker secrets, uncomment secrets blocks in generated compose
11. Update Portainer instructions (no `.env` upload — document env var tab workflow)
12. Update docs/DOCKER.md with Swarm/Portainer section + JVM memory guidance
13. Test: `test-upgrade.sh` still passes (no regression)
14. Test: `./run --swarm --auto` generates valid compose (fresh)
15. Test: `./run --swarm` with existing compose — extracts customizations, generates ready-to-deploy file
