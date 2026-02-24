# Docker Security Hardening

Reasoning behind each security and correctness decision in the Docker
infrastructure. For architecture and configuration reference, see `DOCKER.md`.

## Non-Root Containers

Both container stages run as non-root users created at build time.

### Transactor (`datomic` user)

The transactor writes to four locations at runtime:

| Path | Purpose | Owned by |
|------|---------|----------|
| `/data` | Datomic dev storage files | `datomic` (chown at build) |
| `/log` | Transactor log output | `datomic` (chown at build) |
| `/backups` | Datomic backup destination | `datomic` (chown at build) |
| `/datomic/transactor.properties` | Generated config (secrets) | `datomic` (chown at build) |

The Dockerfile creates the user and transfers ownership before switching:

```dockerfile
RUN addgroup -S datomic && adduser -S -G datomic datomic \
 && chown -R datomic:datomic /data /log /backups /datomic
USER datomic
```

### App (`app` user)

The app is stateless inside its container. The only runtime writes go to `/tmp`
for temporary PDF generation (PDFBox `createTempFile`). `/tmp` is
world-writable by default on Alpine, so no chown is needed:

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

## Healthcheck Port Binding

The app healthcheck uses `CMD-SHELL` to enable environment variable expansion:

```yaml
test: ["CMD-SHELL", "wget -q --spider http://127.0.0.1:${PORT:-8890}/health"]
```

`CMD` (array form) passes arguments directly to exec with no shell. `CMD-SHELL`
runs through `/bin/sh -c`, so `${PORT:-8890}` expands from the container's
environment at runtime.

**Caveat:** `deploy/nginx.conf` hardcodes `proxy_pass http://orcpub:8890`.
Changing `PORT` without updating nginx breaks the reverse proxy. The dynamic
healthcheck is defense-in-depth — it ensures the healthcheck matches the app's
actual port, but PORT is effectively locked to 8890 until nginx is also
templated. This is documented in `.env.example` and `ENVIRONMENT.md`.

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
