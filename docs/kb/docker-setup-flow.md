# docker-setup.sh — Mode Flow & Check Reference

1332 lines as of 2026-03-03. All modes are mutually exclusive early-exit blocks
(`if ... exit 0; fi`) except `--build --deploy` which composes.

## Flag Parsing (line ~308)

| Flag | Variable | Default | Notes |
|------|----------|---------|-------|
| `--auto` | `AUTO_MODE` | false | Skips all prompts, accepts defaults |
| `--build` | `BUILD_MODE` | false | Build images only |
| `--check` | `CHECK_MODE` | false | Read-only validation |
| `--deploy` | `DEPLOY_MODE` | false | Deploy Swarm stack |
| `--force` | `FORCE_MODE` | false | Overwrite existing files |
| `--secrets` | `SECRETS_MODE` | false | File-based secrets migration |
| `--swarm` | `SWARM_MODE` | false | Swarm external secrets |
| `--upgrade` | `UPGRADE_MODE` | false | Fix old .env format |
| `--upgrade-swarm` | sets both `UPGRADE_MODE` + `SWARM_MODE` | | |
| `--upgrade-secrets` | sets both `UPGRADE_MODE` + `SECRETS_MODE` | | |

## Mode Execution Order

The script runs the FIRST matching block, then exits. Order matters:

### 1. `--check` (line ~344)
Read-only. No changes.
- If no .env: offer interactive/auto/skip menu
- Check required vars: DATOMIC_URL, DATOMIC_PASSWORD, ADMIN_PASSWORD, SIGNATURE
  - Checks .env first, then `secrets/` files, then Swarm secrets (docker secret inspect)
- URL health: embedded password, free:// protocol, localhost
- Shell env conflicts (for each var)
- Required files: .env, docker-compose.yaml, nginx.conf.template
- Required dirs: data/, logs/
- If issues found: offer to run --upgrade

### 2. `--build` alone (line ~431)
Guard: `BUILD_MODE && !DEPLOY_MODE`
- Checks: docker compose available, .env exists
- Runs `docker compose build`

### 3. `--deploy` (line ~456)
May include `--build` as modifier.
- Checks: docker compose available, Swarm active, .env exists, jq available
- If `BUILD_MODE`: builds first
- Deploys via JSON+jq pipeline:
  - `docker compose config --format json`
  - jq: `del(.name)`, depends_on map→list, ports string→int
  - `docker stack deploy -c - orcpub`
- Shows useful commands after success

### 4. `--secrets` standalone (line ~508)
Guard: `SECRETS_MODE && !UPGRADE_MODE`
- Interactive: asks if user is on Swarm (redirects to --swarm)
- `read_passwords()` — reads from .env + shell env
- Creates `secrets/` dir with individual files (chmod 600)
- `write_compose_secrets("file")` — generates docker-compose.secrets.yaml
- Moves password vars from .env to .env.secrets.backup
- Next step: `docker compose down && docker compose up -d`

### 5. `--swarm` standalone (line ~578)
Guard: `SWARM_MODE && !UPGRADE_MODE`
- Checks Swarm active, offers to init if not
- `read_passwords()` — reads from .env + shell env
- Creates Docker secrets via `docker secret create`
- Handles existing secrets: skip or --force to replace
- `write_compose_secrets("external")` — generates docker-compose.secrets.yaml
- Next steps: --build then --deploy

### 6. `--upgrade` (line ~677)
This is the big one. Handles `--upgrade-swarm` and `--upgrade-secrets` via chaining.

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
7. Image tags (ORCPUB_IMAGE/DATOMIC_IMAGE):
   - Not set: prompt to add (interactive only)
   - Already set: prompt to change tag (interactive only)
   - Auto mode: report only, no changes

**Chaining:**
- `--upgrade-swarm`: `exec "$0" --swarm [--auto]`
- `--upgrade-secrets`: `exec "$0" --secrets [--auto]`
- Standalone `--upgrade`: offer secrets migration interactively

**Exit:**
- Detects shell env conflicts, builds compose command with `env -u` prefixes

### 7. Fresh Install / Main (line ~994)
Falls through if no mode flags set (or just `--auto`/`--force`).

**Step 1: .env generation**
- Skip if .env exists and no --force
- Prompts: passwords, port, image tag, SMTP, admin user
- Auto mode: generates all defaults
- Writes full .env template with comments

**Step 2: Directories**
- Creates: data/, logs/, backups/, deploy/homebrew/

**Step 3: SSL**
- Generates self-signed cert if missing (openssl)

**Step 4: Validation**
- DATOMIC_PASSWORD vs URL consistency
- Shell env conflicts
- Required files and dirs

**Step 5: Next Steps**
- Prints launch command (with `env -u` if conflicts)
- Warning banner if issues found

## Shared Helpers (line ~33-271)

| Helper | Purpose |
|--------|---------|
| `info/warn/change/error/success/header/next` | Colored output |
| `read_env_val(VAR, FILE)` | Read single var from .env |
| `source_env(FILE)` | Source .env safely (strips \\r) |
| `set_env_val(VAR, VAL, FILE)` | Write/update var in .env (awk-based) |
| `generate_password(len)` | URL-safe random password |
| `write_compose_secrets(mode)` | Generate docker-compose.secrets.yaml |
| `read_passwords(label)` | Read 3 passwords from .env + shell, exit if missing |
| `check_file(label, path)` | Validate file exists, increment ERRORS |
| `check_dir(label, path)` | Validate dir exists, increment ERRORS |
| `check_env_conflict(VAR)` | Detect shell vs .env value mismatch |
| `build_compose_cmd(base)` | Prefix with `env -u` for each conflict |
| `prompt_value(text, default)` | Interactive prompt, auto-mode returns default |

## Test Infrastructure

- `test/docker/test-upgrade.sh` — 46 automated tests
- `test/docker/reset-test.sh [scenario]` — Reset to: fresh, conflict, upgrade, secrets
- Scenarios tested manually: fresh+auto, upgrade+auto, conflict+auto,
  upgrade-secrets+auto, upgrade-swarm+auto, check-after-secrets,
  check-after-swarm, check-fresh-skip
