# Docker Security Decisions

Decision log for Docker security hardening. Each entry explains what was done,
why, and what breaks if reverted. Cross-reference: `docs/DOCKER-SECURITY.md`
has the human-facing version with code examples.

## Non-Root Containers (Entrypoint-Chown-Drop)

**Decision:** Transactor runs as `datomic` user via `su-exec` privilege drop in
`start.sh`. App runs as `app` user via `USER` in Dockerfile.

**Why:** Root in container = root-level impact from any JVM/Datomic vulnerability.
Volume-mounted files owned by root on host. Security scanners flag it.

**What breaks if reverted:** Nothing functionally — it's defense-in-depth. But
security scanners will flag, and volume file ownership changes to root.

**Implementation detail:** Build-time `chown` + `USER datomic` does NOT work
with bind mounts — the host directory ownership overrides container ownership.
This caused "Permission denied" on `/log` in CI. Fix: entrypoint-chown-drop
pattern (same as official postgres, redis images):
1. Container starts as root (no `USER` directive)
2. `start.sh` runs `chown -R datomic:datomic /data /log /backups /datomic`
3. `exec su-exec datomic /datomic/bin/transactor` drops privileges

App doesn't need this — no bind mounts, only writes to `/tmp` (world-writable).

**DO NOT** add `USER datomic` to the transactor Dockerfile — bind mounts will
break with permission denied errors.

## sed Replacement Escaping

**Decision:** `escape_sed_replacement()` in `deploy/start.sh` escapes `\`, `&`,
`|` before passing values to sed.

**Why:** Without escaping:
- `\` in password → sed interprets as escape sequence (silent data corruption)
- `&` in password → sed replaces with entire match string (silent data corruption)
- `|` in password → breaks sed delimiter (container fails to start)

**What breaks if reverted:** Any password containing `\`, `&`, or `|` causes
either silent authentication failure or container crash. `run`
generates safe alphanumeric passwords, but manual `.env` edits are unprotected.

**Implementation detail:** Backslash escaped FIRST to avoid double-escaping.
Then `&` and `|`. Order matters.

## Log Directory: `/log` Not `/logs`

**Decision:** Compose mounts `./logs:/log` (host plural, container singular).

**Why:** Datomic's stock config uses `log-dir=log` (no s). Our template and
Dockerfile follow that convention. The compose files originally mounted to
`/logs` (with s) — transactor wrote to ephemeral `/log`, logs lost on restart.

**What breaks if changed to `/logs`:** Must also change template `log-dir=/logs`
AND Dockerfile `mkdir /logs`. All three must agree or logs are silently lost.

**Verification:** After container start, `docker exec <container> ls /log` should
show transactor log files. `ls ./logs/` on host should mirror.

## File Permissions (chmod 600)

**Decision:** `transactor.properties` and `.env` are chmod 600 after creation.

**Why:** Both contain plaintext passwords (ADMIN_PASSWORD, DATOMIC_PASSWORD,
SIGNATURE). Default umask creates world-readable files (0644).

**What breaks if reverted:** Information disclosure if container filesystem or
host is compromised. No functional impact.

## .dockerignore Secrets Exclusion

**Decision:** `.env` and `.lein-env` excluded from Docker build context.

**Why:** `ADD ./ /orcpub` in app-builder copies entire context. Without exclusion,
secrets end up in intermediate Docker layer cache — extractable via `docker
history` or shared CI daemons.

**What breaks if reverted:** Secrets in build cache. Final image unaffected
(only copies jar), but builder layer is compromised.

## Dynamic PORT Across All Services

**Decision:** PORT is dynamic in healthcheck (CMD-SHELL) and nginx (envsubst template).

**How it works:**
- Healthcheck: `CMD-SHELL` runs through `/bin/sh -c`, expanding `${PORT:-8890}`
- Nginx: `deploy/nginx.conf.template` uses `${ORCPUB_PORT}`. Official `nginx:alpine`
  runs `envsubst` on `/etc/nginx/templates/*.template` at startup. Only defined
  env vars are substituted — nginx's `$host`, `$scheme`, `$remote_addr` are safe.
- Compose passes `ORCPUB_PORT: ${PORT:-8890}` to web service.

**Why ORCPUB_PORT not PORT:** Avoids name collision with nginx internals and makes
the compose explicit about what the web service needs.

**What breaks if reverted to static 8890:** Changing PORT in .env breaks both
healthcheck (wrong port) and nginx proxy (wrong upstream). Web service never starts.

## DATOMIC_URL Password Sync Validation

**Decision:** `run` validates that DATOMIC_PASSWORD matches the
password embedded in DATOMIC_URL.

**Why:** DATOMIC_URL format is `datomic:dev://datomic:4334/orcpub?password=<PW>`.
If user changes DATOMIC_PASSWORD without updating DATOMIC_URL, app gets cryptic
Datomic auth failure. No helpful error message.

**Implementation:** grep-based extraction (not source, to avoid polluting shell):
```bash
_env_datomic_pw=$(grep -m1 '^DATOMIC_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
```

## CSP_POLICY / DEV_MODE Passthrough

**Decision:** Both compose files pass `CSP_POLICY` and `DEV_MODE` to orcpub
container's environment block.

**Why:** Docker compose `.env` variables are available for compose interpolation
(`${VAR}`) but are NOT automatically injected into containers. The app reads
env vars via `environ.core` inside the JVM. Without explicit passthrough,
setting CSP_POLICY in .env has zero effect — silent security misconfiguration.

**What breaks if removed:** CSP_POLICY and DEV_MODE silently ignored in Docker.
App uses defaults (strict CSP, dev mode off).

## VOLUME Declarations

**Decision:** Transactor Dockerfile declares `VOLUME ["/data", "/log", "/backups"]`.

**Why:** Standalone `docker run` (no compose) uses ephemeral storage by default.
VOLUME tells Docker to create anonymous volumes automatically, preserving data
across container restarts even without explicit `-v` flags.

**What breaks if removed:** Only affects standalone docker run users — compose
users have explicit bind mounts. Standalone users lose data on container removal.

## See Also

- `docker-infrastructure.md` — key architecture decisions, DO NOT list
- `docs/DOCKER-SECURITY.md` — human-facing version with code examples
- `docs/DOCKER.md` — architecture and operational reference
