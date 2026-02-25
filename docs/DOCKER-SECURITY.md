# Docker Security Hardening

Reasoning behind each security and correctness decision in the Docker
infrastructure. For architecture and configuration reference, see `DOCKER.md`.

## Non-Root Containers

Both containers create non-root users at build time. The transactor uses an
entrypoint-chown-drop pattern; the app uses a static `USER` directive.

### Transactor (`datomic` user) — Entrypoint-Chown-Drop

The transactor writes to four locations at runtime:

| Path | Purpose |
|------|---------|
| `/data` | Datomic dev storage files |
| `/log` | Transactor log output |
| `/backups` | Datomic backup destination |
| `/datomic/transactor.properties` | Generated config (secrets) |

**Why not `USER datomic` in the Dockerfile?** Build-time `chown` is overridden
by Docker bind mounts (`./data:/data`, `./logs:/log`, `./backups:/backups`).
The host directory's ownership takes over, making the container's `/log` etc.
unwritable by the `datomic` user. This caused "Permission denied" on
`/log/2026-02-24.log` in CI.

**Solution:** The entrypoint-chown-drop pattern (used by official postgres,
redis, and other images):

1. Container starts as root (no `USER` directive)
2. `start.sh` fixes ownership of mounted volumes
3. `su-exec` drops to `datomic` user before exec-ing the transactor

```bash
# In deploy/start.sh:
chown -R datomic:datomic /data /log /backups /datomic
exec su-exec datomic /datomic/bin/transactor "$OUTPUT"
```

`su-exec` (Alpine's lightweight `gosu` alternative) replaces itself with the
target command — no intermediate process, transactor becomes PID 1 as the
`datomic` user.

### App (`app` user)

The app is stateless inside its container. The only runtime writes go to `/tmp`
for temporary PDF generation (PDFBox `createTempFile`). `/tmp` is
world-writable by default on Alpine, so no chown is needed. No bind mounts
means the static `USER` directive works fine:

```dockerfile
RUN addgroup -S app && adduser -S -G app app
USER app
```

### Why This Matters

- A JVM vulnerability running as root grants root within the container
- Volume-mounted directories (`./data`, `./logs`) get files owned by root on
  the host, making cleanup and backup scripts harder
- Security scanners (Trivy, Snyk, Docker Scout) flag root containers
- Defense-in-depth: even if the container is compromised, the attacker has
  limited privileges

## Secret Handling in sed Substitution

`deploy/start.sh` substitutes `${VAR}` placeholders in the template using sed.
Three characters are special in sed replacement strings and must be escaped:

| Character | Sed meaning | Failure mode |
|-----------|-------------|--------------|
| `\` | Escape character | `pass\word` → `password` (silent, `\w` = literal `w`) |
| `&` | "Entire matched text" | `pass&word` → `pass${ADMIN_PASSWORD}word` (silent corruption) |
| `\|` | Our delimiter | `pass\|word` → sed syntax error (container won't start) |

The `escape_sed_replacement()` function handles all three:

```bash
escape_sed_replacement() {
  # Escape backslash first (avoid double-escaping), then & and |
  printf '%s' "$1" | sed -e 's/[\\]/\\&/g' -e 's/[&|]/\\&/g'
}
```

**Order matters:** backslash must be escaped first. If we escaped `&` first
(producing `\&`), then the backslash pass would double it to `\\&`.

`docker-setup.sh` generates alphanumeric-only passwords (no special chars), but
users who set passwords manually via `.env` can use any characters. The escaping
makes this safe.

### Generated File Permissions

The generated `transactor.properties` contains plaintext passwords. After sed
writes it, `chmod 600` restricts it to the `datomic` user:

```bash
sed ... "$TEMPLATE" > "$OUTPUT"
chmod 600 "$OUTPUT"
```

Similarly, `docker-setup.sh` sets `chmod 600` on the generated `.env` file,
which contains `ADMIN_PASSWORD`, `DATOMIC_PASSWORD`, `SIGNATURE` (JWT secret),
and SMTP credentials.

### Build Context Exclusion

`.dockerignore` excludes `.env` and `.lein-env` so that `ADD ./ /orcpub` in the
app-builder stage doesn't copy secrets into the Docker layer cache. The final
`app` image only contains the jar, but intermediate builder layers are cached
and can be inspected with `docker history` or extracted from shared CI daemons.

## Log Directory: `/log` Not `/logs`

Datomic's stock config sample uses `log-dir=log` (no trailing s). Our template
follows that convention: `log-dir=/log`. The Dockerfile creates `/log` to match.

The host-side directory is `./logs` (plural, common convention). The compose
mount must map host plural to container singular:

```yaml
volumes:
  - ./logs:/log     # correct: host logs/ → container /log
  # - ./logs:/logs  # WRONG: transactor writes to /log, /logs sits empty
```

This mismatch went undetected because the transactor ran without errors — it
just wrote to the ephemeral `/log` directory, and logs were silently lost on
every container restart.

## Dynamic PORT Across All Services

Three components must agree on the app's port: the Jetty server (`PORT` env
var), the healthcheck, and the nginx reverse proxy.

### Healthcheck

```yaml
test: ["CMD-SHELL", "wget -q --spider http://127.0.0.1:${PORT:-8890}/health"]
```

`CMD` (array form) passes arguments directly to exec with no shell. `CMD-SHELL`
runs through `/bin/sh -c`, so `${PORT:-8890}` expands from the container's
environment at runtime.

### Nginx Template

`deploy/nginx.conf.template` uses `${ORCPUB_PORT}` instead of a hardcoded port:

```nginx
proxy_pass http://orcpub:${ORCPUB_PORT};
```

The official `nginx:alpine` image runs `envsubst` on files in
`/etc/nginx/templates/*.template` at container startup. Only defined environment
variables are substituted — nginx's own `$host`, `$scheme`, `$remote_addr` are
left untouched because they don't exist as environment variables.

The compose files pass `ORCPUB_PORT: ${PORT:-8890}` to the web service so all
three components track the same value from a single `.env` setting.

## DATOMIC_URL Password Sync

`DATOMIC_URL` embeds the password inline:

```
datomic:dev://datomic:4334/orcpub?password=<DATOMIC_PASSWORD>
```

If a user edits `.env` and changes `DATOMIC_PASSWORD` without updating the
password in `DATOMIC_URL`, the app connects with the wrong credential. The
error is a cryptic Datomic authentication failure with no mention of password
mismatch.

`docker-setup.sh` validates this in its verification section:

```bash
_env_datomic_pw=$(grep -m1 '^DATOMIC_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
_env_datomic_url=$(grep -m1 '^DATOMIC_URL=' "$ENV_FILE" | cut -d= -f2-)
if [[ "$_env_datomic_url" != *"password=${_env_datomic_pw}"* ]]; then
  warn "DATOMIC_PASSWORD does not match the password in DATOMIC_URL"
fi
```

When `docker-setup.sh` generates the file, it constructs `DATOMIC_URL` using
`${DATOMIC_PASSWORD}` so they always match at creation time.

## Environment Variable Passthrough

Docker containers only see environment variables explicitly listed in the
compose `environment:` block. Variables in `.env` are available to compose for
interpolation (`${VAR}`), but are **not** automatically injected into
containers.

`CSP_POLICY` and `DEV_MODE` were in `.env.example` and documented in
`ENVIRONMENT.md`, but neither compose file passed them through. The app reads
them via `environ.core` (which checks system env vars inside the JVM), so
setting `CSP_POLICY=strict` in `.env` had zero effect in Docker deployments.
Users could believe they'd hardened CSP when they hadn't.

Both compose files now include:

```yaml
CSP_POLICY: ${CSP_POLICY:-strict}
DEV_MODE: ${DEV_MODE:-}
```

## VOLUME Declarations

The transactor Dockerfile declares persistent volumes:

```dockerfile
VOLUME ["/data", "/log", "/backups"]
```

This only matters for standalone `docker run` usage (no compose). Without
`VOLUME`, data is ephemeral — lost when the container is removed. With it,
Docker automatically creates anonymous volumes even without explicit `-v` flags.

Compose users are unaffected (they have explicit bind mounts), but standalone
users get data persistence by default.

## See Also

- `DOCKER.md` — architecture, configuration, and operational reference
- `ENVIRONMENT.md` — all environment variables
- `LEIN-UBERJAR-HANG.md` — why the build uses a 3-step process
