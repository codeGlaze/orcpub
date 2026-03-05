# Docker Testing Guide

How to test run and the Docker deployment. Covers the automated
test suite, manual smoke testing, and every gotcha we've hit.

## Automated Tests (No Docker Daemon)

```bash
bash test/docker/test-upgrade.sh          # All 46 tests
bash test/docker/test-upgrade.sh v1-free  # One fixture
```

Copies each fixture to a tmpdir, runs `--upgrade --auto`, validates output.
No Docker daemon needed — tests .env transformation logic only.

### Fixtures (test/docker/fixtures/)

| Fixture | Simulates |
|---------|-----------|
| `env-v1-free-localhost.env` | Ancient install: `datomic:free://localhost`, no DATOMIC_PASSWORD |
| `env-v2-missing-vars.env` | Partial .env missing SIGNATURE/ADMIN_PASSWORD |
| `env-v2-password-in-url.env` | Password embedded in DATOMIC_URL (old pattern) |
| `env-v2-password-mismatch.env` | DATOMIC_PASSWORD differs from URL-embedded password |
| `env-v2-windows-crlf.env` | Windows line endings (\r\n) |
| `env-v3-current.env` | Modern format — should report "no changes needed" |
| `env-production-like.env` | Realistic production .env with all vars |
| `compose-hardcoded.yaml` | Admin who edited docker-compose.yaml directly with passwords |

### Adding a test
Add assertions in the scenario's test block in test-upgrade.sh. Pattern:
```
assert_env "VARNAME" "expected_value" "$scenario_name"
assert_output "expected substring" "$scenario_name"
```

## Reset Script

```bash
bash test/docker/reset-test.sh fresh      # No .env, clean compose (default)
bash test/docker/reset-test.sh conflict   # No .env, hardcoded compose values
bash test/docker/reset-test.sh upgrade    # Old-format .env
bash test/docker/reset-test.sh secrets    # Modern .env, ready for --secrets
```

Cleans: .env, .env.backup.*, .env.secrets.backup, secrets/, docker-compose.secrets.yaml,
Docker secrets (if daemon available), restores compose and transactor template from git.

### H2 Database Prompt
If `data/db/datomic.mv.db` exists, reset-test.sh asks what to do because
ADMIN_PASSWORD is locked into the H2 file at first startup. Options:
1. Back up to `data/db.bak/` and wipe (can restore later)
2. Wipe permanently
3. Keep (next test may fail if passwords don't match)

In scripted/piped contexts, defaults to option 1 (backup + wipe).

## Manual Smoke Test — Compose Path

```bash
# 1. Setup
bash test/docker/reset-test.sh fresh
./run --auto

# 2. Build + launch
env -u DATOMIC_URL docker compose up --build -d

# 3. Wait for healthy (~2-3 min for app, datomic is faster)
docker compose ps    # all 3 should show "healthy"

# 4. Create test user
./docker-user.sh init

# 5. Verify
curl -sk https://localhost/ -o /dev/null -w "%{http_code}\n"          # → 200
curl -sk https://localhost/login -X POST \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"PASSWORD_FROM_ENV"}' \
  -w "\n%{http_code}\n"                                               # → 200 + token

# 6. Cleanup
docker compose down
```

## Manual Smoke Test — Swarm Path

```bash
# 1. Setup (zero to swarm in one step)
bash test/docker/reset-test.sh fresh
./run --swarm --auto --build --up

# 2. Verify stack
docker stack services orcpub         # all replicas should be 1/1
docker service logs orcpub_orcpub    # app logs, check for errors

# 3. Cleanup
docker stack rm orcpub
docker swarm leave --force
```

## Gotchas & Workarounds

### `env -u DATOMIC_URL` is required in Codespaces
Codespaces exports `DATOMIC_URL=datomic:dev://localhost:4334/orcpub` via
`.lein-env` / `post-create.sh`. Docker Compose prefers shell env over .env,
so the container gets `localhost` instead of `datomic` (the service name).
The `env -u` prefix unsets the shell var so .env wins.

The script detects this and prints the correct command with `env -u` prefix.
Don't skip it or omit it — the containers will fail to connect.

### Port 443 not auto-forwarded in Codespaces
Docker-in-Docker ports aren't detected by the Codespaces port forwarder.
Manually add port 443 in the Ports tab, or `curl -sk https://localhost/`
from the terminal.

### Login is `/login`, not `/api/login`
POST to `/login` with JSON body `{"username":"...","password":"..."}`.
Returns EDN (not JSON) with `:user-data` map and `:token` string.
`/api/login` returns 404.

### Character save is transit, not JSON
The character save endpoint uses `application/transit+json` (`:transit-params`).
Login uses `:json-params`. Don't mix them up in API tests.

### H2 password lock-in
ADMIN_PASSWORD is baked into the H2 database file on first transactor startup.
Changing it in .env without wiping `data/db/` causes:
```
"Unable to connect to embedded storage"
```
The transactor crashes in a loop. The data is NOT lost — just set the password
back, or wipe `data/db/` to start fresh. `reset-test.sh` handles this with
a backup prompt.

### `docker compose up --build` vs `docker compose build` + `docker compose up`
For Compose deployments, `docker compose up --build -d` is the one-liner.
`./run --build` is Swarm-only (for use with `--up`).
Don't mix Compose and Swarm build commands.

### `--build --up` without `--swarm`
Works if Swarm is already initialized. The SWARM block only runs when
`--swarm` is passed. BUILD and DEPLOY just need an active Swarm (checked
by DEPLOY block with a recovery message if missing).

### Swarm jq null-stripping must be last
`docker compose config --format json` emits explicit `null` for unset fields.
`docker stack deploy` rejects them. The jq pipeline strips nulls, but this
must happen LAST because `|=` on missing keys re-creates null values.

### `read -rp` in piped/non-interactive mode
All `read -rp` calls have `|| true`. Without it, `read` returns non-zero on
EOF, and `set -e` kills the script. This was a pre-existing bug that caused
doubled output in `--check` mode (script crashed partway, got re-run).

When testing interactive mode with piped input (`printf '...' | ./run`),
provide enough newlines for every prompt. Missing newlines → read gets EOF
on the wrong prompt → confusing partial output.

### `docker secret create` output leaks
`docker secret create` prints the secret ID to stdout. The script redirects
stdout but preserves stderr for debugging. The secret ID in test output is
harmless (it's just an identifier, not the secret value).

### Transactor template has two host lines
`docker/transactor.properties.template` always has both:
```
host=datomic          # active for Compose
#host=0.0.0.0         # commented, for Swarm
```
`switch_transactor_host()` comments/uncomments. `reset-test.sh` restores
from git to ensure clean state. If the template is dirty, host switching
tests will give wrong results.

### Test output capture pattern
For assertions on script output, capture to a variable first:
```bash
out=$(./run --auto 2>&1)
echo "$out" | grep "expected"
```
Don't pipe directly (`./run | head -5`) — `head` closes the pipe
early, which can cause "broken pipe" errors or truncated output under `set -e`.

### `docker-user.sh init` needs all containers healthy
`docker-user.sh` runs `docker exec` into the app container. If the app is
still booting (~2 min), the exec will fail or the Datomic connection inside
will fail. Wait for `docker compose ps` to show all services as "healthy"
before running user commands.

### Swarm deploy needs `jq`
`./run --up` requires `jq` for the Compose→Swarm JSON
pipeline. The script checks and errors with install instructions. In
Codespaces, `jq` is pre-installed. In minimal Docker images, install it first.

## Test Loop Checklist

Full verification loop after run changes:

1. `bash test/docker/test-upgrade.sh` — 46/46 must pass
2. `bash test/docker/reset-test.sh fresh && ./run --auto` — compose setup
3. `./run --check` — validation
4. `./run --secrets --auto` — file secrets
5. `bash test/docker/reset-test.sh fresh && ./run --swarm --auto` — swarm setup
6. `./run --secrets --swarm` — conflict check (should error)
7. `./run --up` (no swarm active) — recovery message
8. Interactive: `printf '\n\n\n...' | ./run` — all defaults via Enter
9. `./run --help` — verify help text
10. Live: `env -u DATOMIC_URL docker compose up --build -d` → healthy → login
