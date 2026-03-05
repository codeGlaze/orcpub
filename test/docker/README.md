# Docker Setup Tests

Manual and automated tests for `run` and `docker-user.sh`.

## Scripts

| Script | Purpose |
|---|---|
| `reset-test.sh [scenario]` | Reset environment to a clean test state |
| `test-upgrade.sh` | Automated upgrade tests (46 assertions, no Docker daemon needed) |

## Reset Scenarios

```bash
./test/docker/reset-test.sh fresh       # No .env, templated compose (default)
./test/docker/reset-test.sh conflict    # No .env, hardcoded compose vs transactor
./test/docker/reset-test.sh upgrade     # Old v1 .env (free protocol, localhost)
./test/docker/reset-test.sh secrets     # Modern .env with passwords, ready for --secrets
```

## Fixtures

Test `.env` files representing real-world configurations:

| Fixture | Scenario |
|---|---|
| `env-v1-free-localhost.env` | Original orcpub: Free protocol, localhost, password in URL |
| `env-v2-missing-vars.env` | Hand-edited: has some vars, missing others |
| `env-v2-password-in-url.env` | Early DMV: password still embedded in URL |
| `env-v2-password-mismatch.env` | URL password differs from DATOMIC_PASSWORD |
| `env-v2-windows-crlf.env` | Windows-edited with CRLF line endings |
| `env-v3-current.env` | Current format, already up to date |
| `env-production-like.env` | Production-like with SMTP and admin configured |
| `compose-hardcoded.yaml` | docker-compose.yaml with hardcoded values (no templating) |

## Manual Test Flow

```bash
# New install
./test/docker/reset-test.sh fresh
./run --auto
docker compose up --build -d
./docker-user.sh init

# Upgrade + secrets
./test/docker/reset-test.sh upgrade
./run --upgrade-secrets --auto

# Upgrade + swarm (conflict detection)
./test/docker/reset-test.sh conflict
./run --upgrade-swarm --auto
```
