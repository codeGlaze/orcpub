# Docker Infrastructure

## Key Decisions

### host=datomic (not 0.0.0.0)
- `host=` is what the transactor ADVERTISES to peers, not what it binds to
- `host=0.0.0.0` works on single-host Docker Compose by accident — peers reuse the URI hostname in dev:// protocol
- In Swarm with overlay, 0.0.0.0 is non-routable and peers would connect to their own loopback
- `host=datomic` works in both single-host and Swarm because Docker DNS resolves service names across overlay networks
- Commit: 81af0405

### Jetty binds 0.0.0.0 in prod
- Pedestal defaults `::http/host` to `"localhost"` when unset — silently blocks inter-container traffic
- `system.clj` sets `::http/host "0.0.0.0"` in prod, `"localhost"` in dev
- Commit: e1a36cf1

### Option C hybrid template for transactor.properties
- Previous: fragile sed-chain (5 build-time + 6 runtime) broke silently on Datomic upgrades
- Current: complete template with structural config hardcoded, only secrets use ${VAR} placeholders
- `deploy/start.sh` substitutes at runtime using pure bash sed (Alpine has no envsubst)
- Commit: fec2e562

### Healthcheck: /health + 127.0.0.1
- BusyBox wget (Alpine): only `-q` and `--spider` supported (not GNU flags)
- Use `127.0.0.1` not `localhost` to avoid IPv4/IPv6 ambiguity
- `/health` returns 200 OK — lighter than `/` which renders the full SPA
- App boot takes ~110s — healthcheck: 10s interval, 30 retries, 60s start_period
- Commit: 92de6f6f

### 3-step uberjar build
- lein compile subprocess hangs in Docker (non-daemon threads prevent JVM exit)
- Step 1: CLJS via figwheel-main (exits cleanly)
- Step 2: AOT compile with timeout (compilation finishes, subprocess hangs, timeout kills)
- Step 3: jar packaging only (no compile — uses .class files from step 2)
- Commit: a90884dc

## DO NOT (verified by failure)

- Do NOT use `host=0.0.0.0` for the transactor — works by accident, fails in Swarm
- Do NOT omit `::http/host` from Pedestal prod config — defaults to localhost, blocks Docker traffic
- Do NOT use `localhost` in healthchecks — IPv4/IPv6 ambiguity causes failures
- Do NOT use GNU wget flags in Alpine containers — BusyBox wget is limited
- Do NOT use envsubst in Alpine — requires gettext package; use sed instead
- Do NOT run lein uberjar as a single step in Docker — subprocess hangs permanently
- Do NOT run the transactor without `exec` — shell stays as PID 1, swallows Docker signals
- Do NOT use raw password values in sed replacements — escape `\`, `&`, `|` first (silent corruption)
- Do NOT mount `./logs:/logs` — transactor uses `log-dir=/log` (no trailing s), mount to `/log`
- Do NOT create `.env` or `transactor.properties` without `chmod 600` — contains plaintext secrets
- Do NOT run containers as root — both stages have non-root users (datomic, app)
- Do NOT trust ENVIRONMENT.md email vars blindly — cross-check against `email.clj` (was wrong for months)

## File Inventory

| File | Purpose |
|------|---------|
| `docker/Dockerfile` | Multi-target: datomic-dist, transactor, app-builder, app |
| `docker/transactor.properties.template` | Complete transactor config (Option C) |
| `deploy/start.sh` | Transactor startup: secret substitution + exec |
| `deploy/nginx.conf.template` | Nginx reverse proxy template (envsubst resolves `${ORCPUB_PORT}`) |
| `docker-compose-build.yaml` | Build-from-source compose |
| `docker-compose.yaml` | Pre-built images compose |
| `docker-setup.sh` | Interactive setup: .env, dirs, SSL |
| `.env.example` | Env var reference |

## Verified Facts

- Datomic Pro dev:// protocol works with Java 21 (Free/free:// does not)
- Datomic transactor port 4334 = 0x10EE in hex (used in /proc/net/tcp healthcheck)
- App boot takes ~110 seconds (Datomic peer connection + schema setup)
- PDFBox requires fontconfig, ttf-dejavu, freetype, lcms2 on Alpine for PDF generation
- Production memory tuning for 4GB heap: 32m/512m/512m (threshold/index-max/object-cache)

## See Also

- `docker-security-decisions.md` — detailed reasoning for each security fix (agent KB)
- `docs/DOCKER.md` — human-facing Docker reference
- `docs/DOCKER-SECURITY.md` — human-facing security hardening with code examples
- `docs/LEIN-UBERJAR-HANG.md` — detailed uberjar hang investigation
- `docs/ENVIRONMENT.md` — all environment variables
