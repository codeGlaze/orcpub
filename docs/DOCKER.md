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

After starting, the app prints its **startup report**: every setting in use, where crash files
and alerts go, the admin email address, and this release's changes. `./run` waits for it and
prints it last, so it does not scroll away; it is also saved to
`/var/log/orcpub/startup-report.txt` on the host (Portainer: the service's Logs, search
"orcpub started").

The upgrade script:
- **Says what this release changes for your install.** It checks your `.env` and
  Swarm stack, prints only what you need to do, and writes the full notes to
  `logs/upgrade-report.txt`. `./run --check` prints the same.
- **Backs up** your `.env` before changing anything
- **Detects** old patterns (password in URL, missing variables)
- **Fixes** them automatically
- **Warns** about things that need your attention (like Free→Pro migration)
- **Does nothing** if your `.env` is already fine

Your `data/`, `logs/`, and SSL certs are never touched.

**Check your `.env` before upgrading to the Summer Patch.** Before this release, most settings
in `.env` never reached the app container: Docker only passes the settings named in the
compose file, and that list was short. It now names every setting the server reads. So a line
that has been sitting in your `.env` doing nothing, such as `APP_NAME=...` or an
`ORCPUB_PDF_...` value, takes effect after this upgrade. Look through `.env` for `APP_`,
`ORCPUB_` and `ALERT_` lines and remove any you do not mean. A branding value set here
replaces the one in your branding file. After the upgrade, the server's start-up output shows
each setting as `SET` or `DEFAULT`, so you can confirm what is in force.

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
| `TRANSACTOR_HOST` | No | Compose `datomic`, Swarm `0.0.0.0` | The address the transactor serves on and gives the app. Each setup has the right default; set it only to override. |
| `ALT_HOST` | No | Compose `127.0.0.1`, Swarm `datomic` | The second address the app tries. Same: leave it unset unless you mean to override it, since a Compose value in `.env` breaks Swarm. |
| `EMAIL_SERVER_URL` | No | *(empty)* | Your SMTP server. Leave it empty and nobody can sign up: the site can't send the confirmation email, so it turns registration off rather than letting people in unchecked. To run without email, see the next setting. |
| `ALLOW_UNVERIFIED_REGISTRATION` | No | `false` | Set this to `true`, and leave `EMAIL_SERVER_URL` empty, to let people sign up without confirming their email. Their accounts work right away. Only do this on a private site — anyone can sign up using an address that isn't theirs. It does nothing if you have SMTP set up. |
| `CSP_POLICY` | No | `strict` | Content Security Policy: `strict`, `permissive`, or `none`. |
| `DEV_MODE` | No | *(empty)* | Set to `true` to send no CSP header at all, which is what allows Figwheel hot-reload. Not a Report-Only mode — that does not exist. |
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
with all structural settings hardcoded. Only five values use `${VAR}`
placeholders for runtime substitution:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DATOMIC_PASSWORD` | *(required)* | Storage access password |
| `ADMIN_PASSWORD` | *(required)* | Admin/monitoring password |
| `TRANSACTOR_HOST` | `datomic` | Address the transactor serves on and advertises |
| `ALT_HOST` | `127.0.0.1` | Fallback host peers try when `TRANSACTOR_HOST` does not reach it |
| `ENCRYPT_CHANNEL` | `true` | Peer-transactor encryption |

`deploy/start.sh` handles startup:
1. Substitutes `${VAR}` placeholders using pure bash `sed`
2. Validates that both passwords are set (exits 1 if missing)
3. Supports password rotation via `ADMIN_PASSWORD_OLD` / `DATOMIC_PASSWORD_OLD`
4. Uses `exec` so the transactor becomes PID 1 (receives Docker signals directly)

## host=datomic Rationale

The transactor's `host=` setting is both the address it serves on and the
address it tells the app to connect to. Datomic has no separate setting for
each, so the right value depends on how Docker names the service.

**Compose: `datomic`.** The service name resolves to the container's own
address, so the transactor can serve there and the app can reach it by name.

**Swarm: `0.0.0.0`, with `ALT_HOST=datomic`.** On a Swarm overlay network the
service name resolves to a virtual address that load-balances to the task. The
transactor cannot serve on that address: it starts, fails to reach itself, and
exits with "Serve failed". `0.0.0.0` serves on every interface, and `alt-host`
gives the app the service name to connect to instead.

Both come from settings filled in when the transactor starts, so one image runs
either way. The stack file `./run --swarm` writes sets them for Swarm. When
`TRANSACTOR_HOST` is not set at all, the transactor works it out: if `datomic`
resolves to an address that is not its own container, or does not resolve at all
(Swarm only publishes the name once the service is healthy), it is on Swarm, so it
serves on `0.0.0.0` and gives the app `datomic` (unless `ALT_HOST` is set). It
prints which it chose, `transactor: host=... alt-host=...`, so a stack deployed
without `./run` (pasted into Portainer, say) still works and says what it did.

**If `datomic` does not resolve at all:** the containers are not on a shared
Docker network. `docker compose` creates one automatically. Standalone
`docker run` requires `--network <name>`.

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

## Logs, crashes and the app's memory

**Logs.** Docker keeps everything each container prints in a file on the host, outside the
container, so a crash or restart does not lose it; `docker compose logs orcpub` shows it. The file
is lost only when the container is replaced (a new image, or a changed compose file). Each
container keeps at most `LOG_MAX_FILES` files of `LOG_MAX_SIZE` each (default 5 of 10 MB), so logs
can no longer fill the disk. Set both in `.env`.

The app's log holds what the app reports: its start-up settings, warnings, errors, admin alerts
and a line for each security event (sign-ins and failed sign-ins, sign-ups, password resets,
email changes, deleted accounts, refused logins). It does not list every web request: the `web`
(nginx) container's log already has one line per request, with time, address and page. To see
request lines in the app's log too, add `-Dorg.slf4j.simpleLogger.log.io.pedestal=info` to
`JAVA_OPTS`.

**When the app runs out of memory** it writes a heap dump and exits straight away, and Docker
restarts it clean, rather than limping on half-broken. If Java itself crashes it writes a crash
log. Both go to `/var/log/orcpub/crash/` on the host, the usual place for logs on Linux, the same on
Docker Compose and on Swarm. To use another name, set `APP_LOGS_NAME` in `.env` (for example
`APP_LOGS_NAME=dmv` for `/var/log/dmv`); the folder is always inside `/var/log`. The files survive
the container being replaced, and the folder is private to the app's user, so read it with `sudo`.

The app mounts the host's `/var/log` because it exists on every machine: on Swarm, a container that
lands on any server starts without preparation. At start-up it creates its own folder there and
gives only that folder to the app's user; nothing else in `/var/log` is touched. If the host's
`/var/log` is closed to other users, the app says so at start-up and keeps the files inside the
container instead. On the next start the server emails an admin alert saying
what happened, when, and where the file is.

- The heap dump (`heap.hprof`) is as large as the app's memory and holds everything that was in
  it, player data included. Keep it private, and delete it once read: Java does not overwrite it,
  so the next crash cannot write a new one while it is there.
- Unsent admin alerts wait in `/var/log/orcpub/alerts/` in the same way.

**The app's memory.** `JAVA_OPTS` in `.env` sets Java's memory options for the app, for example
`JAVA_OPTS=-XX:MaxRAMPercentage=75.0` (use up to 75% of the container's memory). Left empty, Java
uses only 25% of the container's memory. Before the Summer Patch `JAVA_OPTS` was passed to the
container but never reached Java, so if you set it earlier, it starts taking effect now.

## Volumes

| Volume Mount | Service | Purpose |
|--------------|---------|---------|
| `/var/log` → `/host-log` | orcpub | The app keeps crash files and unsent admin alerts in `/var/log/$APP_LOGS_NAME` (default `orcpub`); survives the container being replaced. Compose and Swarm both |
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

**If your stack has things the generator does not write** (another service, a
top-level `configs:` section, a key such as `hostname:` on a service), `./run --swarm`
leaves your file as it is, writes the new one to `docker-compose.swarm.yaml.new`,
and lists what the new one would drop. Copy those parts across, or copy this
release's changes into your own file. Extra environment variables, volumes,
networks, Traefik labels and resource limits are carried over.

**Upgrading to the release with `TRANSACTOR_HOST`:** regenerate the stack with
`./run --swarm` (or add `TRANSACTOR_HOST: 0.0.0.0` to the datomic service by hand)
before deploying the new images. Without it the transactor serves on `datomic`,
which does not work on Swarm. `./run --upgrade` also removes `ALT_HOST=127.0.0.1`
from `.env`, the Compose default it used to write, which would otherwise override
the stack's `datomic`. `./run --check` tells you if either is still wrong. Deploying without `./run`?
The app's startup log has a DEPLOYMENT block that says where its crash files
and alerts are kept and lists this release's deployment changes, and Datomic's
log says which addresses it chose. If `git pull` refuses because
`docker/transactor.properties.template` has local changes, those were the old
way of switching to Swarm: discard them with
`git checkout -- docker/transactor.properties.template` and pull again.

### Upgrading a Portainer stack

Portainer keeps its own copy of the stack file and of your variables, so an upgrade has to
reach both:

1. On the server's checkout, pull the new code and run `./run --upgrade-swarm`. It reads the
   `docker-compose.swarm.yaml` on disk, keeps your changes, and adds any settings the new
   release reads (the Summer Patch adds many; without them Swarm drops those settings on the way
   into the container).
2. **If you have edited the stack in Portainer's own editor**, copy those edits into
   `docker-compose.swarm.yaml` on disk first. The upgrade reads the file on disk, not
   Portainer's copy, so edits made only in Portainer are lost when you paste the new file.
3. In Portainer, open the stack and replace the compose text with the new
   `docker-compose.swarm.yaml`.
4. Your variables (pasted earlier from `.env.portainer`) stay as they are. Add any new setting
   you want to use in **Advanced mode**, one `KEY=VALUE` per line. Before deploying, look for
   `APP_`, `ORCPUB_` and `ALERT_` lines you did not mean to set: they now take effect.
5. Update the stack. The app's log starts with the settings it is using, each marked `SET` or
   `DEFAULT`.

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
| Auto-percentage (recommended, app) | `JAVA_OPTS=-XX:MaxRAMPercentage=75.0` | Lets the app's Java scale with its container limit |
| Explicit heap (app) | `JAVA_OPTS=-Xms1g -Xmx1g` | When you need predictable fixed sizing |
| Explicit heap (transactor) | `XMS=-Xms1g` / `XMX=-Xmx1g` | Read by Datomic's transactor only, not the app |
| Default (no setting) | Leave `JAVA_OPTS`, `XMS`, `XMX` empty | The app's Java then uses 25% of its container's memory |

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
