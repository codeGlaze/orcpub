# run — Mode Flow & Check Reference

~1460 lines as of 2026-03-04. Modes execute in source order as sequential
`if ... exit 0; fi` blocks. Config modes (SECRETS, SWARM) precede action modes
(BUILD, DEPLOY). SWARM falls through to BUILD/DEPLOY when both are set.

## Flag Parsing (line ~548)

| Flag | Variable | Default | Notes |
|------|----------|---------|-------|
| `--auto` | `AUTO_MODE` | false | Skips all prompts, accepts defaults |
| `--build` | `BUILD_MODE` | false | Build images (Swarm only — compose uses `docker compose up --build`) |
| `--check` | `CHECK_MODE` | false | Read-only validation |
| `--up` | `DEPLOY_MODE` | false | Deploy Swarm stack |
| `--force` | `FORCE_MODE` | false | Overwrite existing files/secrets |
| `--secrets` | `SECRETS_MODE` | false | File-based secrets migration |
| `--swarm` | `SWARM_MODE` | false | Swarm external secrets |
| `--upgrade` | `UPGRADE_MODE` | false | Fix old .env format |
| `--upgrade-swarm` | sets both `UPGRADE_MODE` + `SWARM_MODE` | | |
| `--upgrade-secrets` | sets both `UPGRADE_MODE` + `SECRETS_MODE` | | |

## Conflict Check (line ~583)

`--secrets` + `--swarm` without `--upgrade` → error with explanation.
They're mutually exclusive (file-based vs Swarm Raft log).
`--upgrade-secrets` works fine (UPGRADE_MODE exempts the check).

## Mode Execution Order (after refactor)

```
CONFLICT CHECK → CHECK → SECRETS → SWARM → BUILD → DEPLOY → UPGRADE → MAIN
```

Config modes (SECRETS, SWARM) run before action modes (BUILD, DEPLOY).
This eliminates the old `exec "$0"` re-entry pattern — SWARM falls through
to BUILD/DEPLOY naturally when those flags are also set.

### 1. `--check` (line ~600)
Read-only. No changes.
- If no .env: offer interactive/auto/skip menu
- Check required vars: DATOMIC_URL, DATOMIC_PASSWORD, ADMIN_PASSWORD, SIGNATURE
  - Checks .env first, then `secrets/` files, then Swarm secrets (docker secret inspect)
- URL health: embedded password, free:// protocol, localhost
- Shell env conflicts (for each var)
- Required files: .env, docker-compose.yaml, nginx.conf.template
- Required dirs: data/, logs/
- If issues found: offer to run --upgrade

### 2. `--secrets` standalone (line ~710)
Guard: `SECRETS_MODE && !UPGRADE_MODE`
- Interactive: asks if user is on Swarm (redirects to --swarm)
- No .env? calls `generate_env()` (works in both auto and interactive modes)
- `read_passwords()` — reads from .env + shell env
- Creates `secrets/` dir with individual files (chmod 600, error-checked)
- `write_compose_secrets("file")` — generates docker-compose.secrets.yaml
- `switch_transactor_host "compose"` — reverts to Compose binding if needed
- Moves password vars from .env to .env.secrets.backup
- Next step: `docker compose down && docker compose up -d`

### 3. `--swarm` standalone (line ~789)
Guard: `SWARM_MODE && !UPGRADE_MODE`
- Checks Swarm active, offers to init if not (auto: inits automatically)
- No .env? calls `generate_env()` — enables zero-to-swarm in one step
- `read_passwords()` — reads from .env + shell env
- Creates Docker secrets via `docker secret create` (stderr preserved for debugging)
- Handles existing secrets: skip or --force to replace
- `write_compose_secrets("external")` — generates docker-compose.secrets.yaml
- `switch_transactor_host "swarm"` — sets host=0.0.0.0 + ALT_HOST=datomic
- **Fall-through**: if BUILD or DEPLOY also set, does NOT exit — continues to those blocks
- If standalone: prints next steps (--build, --up) and exits

### 4. `--build` alone (line ~898)
Guard: `BUILD_MODE && !DEPLOY_MODE`
- Checks: docker compose available
- No .env? **Recovery**: auto-runs `generate_env()` or prompts user
- Runs `docker compose build`

### 5. `--up` (line ~935)
Guard: `DEPLOY_MODE` (no SWARM_MODE exclusion — SWARM runs first now)
- Checks: docker compose available, jq available
- Swarm not active? **Recovery**: shows init and compose alternatives
- No .env? Shows setup command
- If `BUILD_MODE`: builds first
- Deploys via JSON+jq pipeline:
  - `docker compose config --format json`
  - jq: `del(.name)`, depends_on map→list, ports string→int, strip nulls last
  - `docker stack deploy -c - orcpub`
- Shows useful commands after success

### 6. `--upgrade` (line ~1013)
Handles `--upgrade-swarm` and `--upgrade-secrets` via chaining.

**If no .env exists:**
- Scans docker-compose.yaml and transactor.properties for hardcoded values
- Resolves conflicts (auto: use transactor; interactive: ask)
- Creates .env from found values

**Upgrade checks (on existing .env):**
1. Password embedded in DATOMIC_URL → extract to DATOMIC_PASSWORD
2. Missing DATOMIC_PASSWORD → generate (auto) or prompt (interactive)
3. Missing SIGNATURE → generate or prompt
4. Missing ADMIN_PASSWORD → generate or prompt
5. Old `datomic:free://` → upgrade to `datomic:dev://`
6. `localhost` in URL → change to `datomic`
7. Image tags (ORCPUB_IMAGE/DATOMIC_IMAGE)

**Chaining:**
- `--upgrade-swarm`: `exec "$0" --swarm [--auto]`
- `--upgrade-secrets`: `exec "$0" --secrets [--auto]`
- Standalone `--upgrade`: offer secrets migration interactively

### 7. Fresh Install / Main (line ~1338)
Falls through if no mode flags set (or just `--auto`/`--force`).
Calls `generate_env()` then runs validation.

## Composable Flag Combinations

| Command | What happens |
|---------|-------------|
| `--auto` | Fresh install, all defaults |
| `--swarm --auto` | Generate .env + init Swarm + create secrets |
| `--swarm --auto --build --up` | Zero to running Swarm stack |
| `--build --up` | Build images + deploy Swarm stack |
| `--secrets --auto` | Generate .env + create file-based secrets |
| `--upgrade-secrets --auto` | Upgrade .env + migrate to file secrets |
| `--upgrade-swarm --auto` | Upgrade .env + migrate to Swarm secrets |

## Shared Helpers (line ~33-502)

| Helper | Purpose |
|--------|---------|
| `info/warn/change/error/success/header/next` | Colored output |
| `read_env_val(VAR, FILE)` | Read single var from .env |
| `source_env(FILE)` | Source .env safely (strips \r) |
| `set_env_val(VAR, VAL, FILE)` | Write/update var in .env (awk-based) |
| `generate_password(len)` | URL-safe random password |
| `write_compose_secrets(mode)` | Generate docker-compose.secrets.yaml |
| `switch_transactor_host(mode)` | Toggle host=datomic ↔ host=0.0.0.0 in transactor template |
| `read_passwords(label)` | Read 3 passwords from .env + shell, exit if missing |
| `check_file(label, path)` | Validate file exists, increment ERRORS |
| `check_dir(label, path)` | Validate dir exists, increment ERRORS |
| `check_env_conflict(VAR)` | Detect shell vs .env value mismatch |
| `build_compose_cmd(base)` | Prefix with `env -u` for each conflict |
| `prompt_value(text, default)` | Interactive prompt, auto-mode returns default silently |
| `write_env_file()` | Write .env heredoc template from current vars |
| `setup_directories()` | Create data/, logs/, backups/, deploy/homebrew/ |
| `setup_ssl_certs()` | Generate snakeoil SSL certs if missing |
| `generate_env()` | Full env generation: prompts/auto + write + dirs + SSL |
| `usage()` | Print --help text |

### `generate_env()` — unified auto/interactive
Replaces the old separate auto/interactive code paths. Uses `prompt_value()`
which returns defaults silently in auto mode, or prompts interactively.
Called by: MAIN block, SECRETS block (no .env), SWARM block (no .env),
BUILD block (recovery when no .env).

### `switch_transactor_host(mode)`
Toggles `host=datomic` ↔ `host=0.0.0.0` in `docker/transactor.properties.template`.
Uses comment/uncomment pattern (both lines always present, one commented).
Called by SECRETS ("compose") and SWARM ("swarm") blocks.

### `read -rp` + `set -e`
All `read -rp` calls have `|| true` suffix. Without it, `read` returns
non-zero on EOF (non-interactive/piped input), triggering `set -e` and
crashing the script. This was a pre-existing bug that caused `--check`
output to double.

## Error Recovery

Errors offer solutions instead of dead-ends:
- `--build` without .env → auto-runs setup (auto) or prompts (interactive)
- `--up` without Swarm → shows init and compose alternatives
- `--secrets --swarm` → explains which to pick and why
- Secret file writes → fail with specific file path in error message
- Docker secret create → preserves stderr for debugging (was `&>/dev/null`)

## Test Infrastructure

- `test/docker/test-upgrade.sh` — 46 automated tests (no Docker daemon)
- `test/docker/reset-test.sh [scenario]` — Reset to: fresh, conflict, upgrade, secrets
- Scenarios tested manually: fresh+auto, fresh+interactive, upgrade+auto,
  secrets+auto, secrets+interactive, swarm+auto, swarm+interactive,
  swarm+build+deploy, upgrade-secrets, upgrade-swarm, check, build-recovery,
  deploy-recovery, conflict-check, help
