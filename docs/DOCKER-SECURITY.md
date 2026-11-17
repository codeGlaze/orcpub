# Docker Security Hardening

How the Docker containers are locked down, and why each choice matters to you as an admin. For
installing and running the stack, see [DOCKER.md](DOCKER.md).

## Non-Root Containers

Both containers create non-root users at build time, and both use an
entrypoint-chown-drop pattern: they start as root only long enough to give a
mounted folder to their user, then run as that user.

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
unwritable by the `datomic` user, and the transactor fails with "Permission denied".

**The pattern used instead:** entrypoint-chown-drop (used by official postgres,
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

The app keeps crash files and unsent admin alerts in `/var/log/$APP_LOGS_NAME`
on the host (default `orcpub`). The host's `/var/log` is mounted at
`/host-log`, because it exists on every machine and so never stops a Swarm task
starting. The app starts as root and `deploy/app-start.sh`:

1. checks `APP_LOGS_NAME` is a plain folder name (no `/`, no `..`), else uses
   `orcpub`, so the folder can never be outside `/var/log`;
2. creates that folder and its `alerts/` and `crash/`, and gives **those three
   only** to `app`, private (`750`). It never changes ownership recursively and
   never touches `/var/log` itself or anything else in it;
3. checks the app can reach the folder; if the host's `/var/log` is closed to
   other users it says so and keeps the files inside the container;
4. runs Java as `app` with `su-exec`, so the app itself never runs as root.

The cost: until step 4 the start script runs as root with the host's `/var/log`
mounted. It is the only code that runs then, and it only does the steps above.

`docker-user.sh` runs its commands in the container with `docker exec -u app`.

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

`run` generates alphanumeric-only passwords (no special chars), but
users who set passwords manually via `.env` can use any characters. The escaping
makes this safe.

### Generated File Permissions

The generated `transactor.properties` contains plaintext passwords. After sed
writes it, `chmod 600` restricts it to the `datomic` user:

```bash
sed ... "$TEMPLATE" > "$OUTPUT"
chmod 600 "$OUTPUT"
```

Similarly, `run` sets `chmod 600` on the generated `.env` file,
which contains `ADMIN_PASSWORD`, `DATOMIC_PASSWORD`, `SIGNATURE` (JWT secret),
and SMTP credentials.

### Build Context Exclusion

`.dockerignore` excludes `.env` and `.lein-env` so that `ADD ./ /orcpub` in the
app-builder stage doesn't copy secrets into the Docker layer cache. The final
`app` image only contains the jar, but intermediate builder layers are cached
and can be inspected with `docker history` or extracted from shared CI daemons.

## Credentials in Logs

A `datomic:sql` URI carries the database password in a query parameter:

```
datomic:sql://datomic?jdbc:postgresql://host:5432/datomic?user=datomic&password=hunter2
```

The server hides credentials before it logs them, so boot output is safe to keep and share:
terminal scrollback, container logs, a log aggregator, a screenshot pasted into chat.
`orcpub.config/redact-secrets` blanks them in both shapes they arrive in:

| Shape | Becomes |
|---|---|
| `?password=` / `passwd` / `pwd` / `secret` / `token` / `api-key` | `password=****` |
| `scheme://user:pass@host` | `scheme://user:****@host` |

Everything else is untouched, so host, port, database and user still show and the line stays
useful for diagnosing a connection.

**If you ever find a connection string with a password in a log, rotate that password.** Hiding
credentials protects new log lines; it does nothing about lines already written.

Developers: anything that might carry a credential goes through `redact-secrets` before it is
logged or put in an exception, and boot output should show a secret only when it must.

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

If the mount points at `/logs`, the transactor runs without errors but writes to a folder inside
the container, and its logs are lost on every restart.

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

## Database Password in DATOMIC_URL

Keep `?password=` out of `DATOMIC_URL`. The app reads `DATOMIC_PASSWORD` and adds it to the URL
itself, so changing the password in one place is enough. When `run` writes `.env`, it leaves the
URL without a password.

A URL that still carries `?password=` works, and its embedded password wins. If that password
differs from `DATOMIC_PASSWORD`, the app fails to connect with a Datomic authentication error that
does not mention a mismatch. `./run --check` warns about this, and `./run --upgrade` moves the
password out of the URL for you.

## Environment Variable Passthrough

Docker containers only see environment variables explicitly listed in the
compose `environment:` block. Variables in `.env` are available to compose for
interpolation (`${VAR}`), but are **not** automatically injected into
containers.

Settings such as `CSP_POLICY` and `DEV_MODE` reach the app only because the compose files list
them:

```yaml
CSP_POLICY: ${CSP_POLICY:-strict}
DEV_MODE: ${DEV_MODE:-}
```

A variable that is in `.env` but not in the compose `environment:` block does nothing in Docker.
All the settings in [ENVIRONMENT.md](ENVIRONMENT.md) are listed.

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

- [DOCKER.md](DOCKER.md): architecture, configuration, and operational reference
- [ENVIRONMENT.md](ENVIRONMENT.md): all environment variables
- [LEIN-UBERJAR-HANG.md](LEIN-UBERJAR-HANG.md): why the build uses a 3-step process
