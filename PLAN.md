# Cross-Platform Scripts — Architecture & Implementation

## Decision: Full PowerShell Mirrors (Option A)

After evaluating WSL reliability (not installed by default, requires admin + reboot,
may be blocked by IT), we chose **independent PowerShell scripts** that replicate
bash functionality natively. The bash scripts stay pure — no Windows concerns mixed in.

### Design Principles

1. **Bash scripts are the gold standard** — hardened, not hybridized
2. **PowerShell scripts are independent mirrors** — same contract, different implementation
3. **Shared service manifest** (`services.json`) — single source of truth for both platforms
4. **No auto-translation** — bash and PowerShell are too different for transpilation; shared specification instead

---

## Architecture

```
orcpub/
├── menu                            # Existing bash interactive menu
├── menu.ps1                        # NEW: Windows interactive menu entry point
├── scripts/
│   ├── services.json               # NEW: Shared service manifest (ports, patterns, exit codes)
│   ├── common.sh                   # HARDENED: manifest reading, timeouts, first-run detection
│   ├── start.sh                    # HARDENED: shellcheck fixes, lein hang docs, first-run warnings
│   ├── stop.sh                     # HARDENED: shellcheck fixes (quoted exit codes)
│   ├── dev-setup.sh                # HARDENED: uses common.sh logging, port_in_use()
│   ├── create_dummy_user.sh        # HARDENED: sources common.sh for logging and exit codes
│   ├── migrate-db.sh               # HARDENED: SC2005 fix
│   └── windows/
│       ├── common.ps1              # NEW: Shared PS utilities (~400 lines)
│       ├── start.ps1               # NEW: Service launcher (~430 lines)
│       ├── stop.ps1                # NEW: Process management (~255 lines)
│       ├── dev-setup.ps1           # NEW: First-time setup (~100 lines)
│       └── create_dummy_user.ps1   # NEW: User creation wrapper (~50 lines)
```

---

## Shared Service Manifest (`scripts/services.json`)

Single source of truth for both bash and PowerShell:

- **Defaults**: datomic_version, datomic_type, java_min_version, log_dir, kill_wait, port_wait
- **Services**: datomic, server, nrepl, figwheel, garden — each with port_env, port_default,
  process_pattern, start_command (per platform), description, dependencies
- **Targets**: all, init-db
- **Exit codes**: success=0, usage=1, prereq=2, runtime=3

**Why JSON (not YAML)?** Native parsing in both shells — PowerShell has `ConvertFrom-Json`,
bash uses `jq` with hardcoded fallbacks when jq isn't available. No YAML parser available
natively. No type coercion ambiguity.

---

## Bash Hardening Summary

### common.sh
- Added `_manifest_val()` — reads services.json via jq with hardcoded fallbacks
- Ports and exit codes now sourced from manifest (env vars still take priority)
- Added `SERVER_BOOT_WAIT=180` and `FIRST_RUN_WAIT=600` configurable timeouts
- Added `check_lein_deps_present()` — first-run detection via ~/.m2/repository heuristic
- Added Datomic Free + Java 21 incompatibility warning in `check_java()`

### start.sh
- All `$EXIT_*` variables quoted (~37 instances) — SC2086 fix
- Fixed `A && B || C` pattern — SC2015 fix in cleanup_on_exit
- Added first-run detection warning before `lein repl` (deps download can look like a hang)
- Added comments documenting lein subprocess hang issue (non-daemon JVM threads)
- Figwheel: already had first-run CLJS compilation handling (extended timeout + warning)
- Garden: already had multiple startup survival checks

### stop.sh
- All `$EXIT_*` variables quoted (~10 instances) — SC2086 fix

### dev-setup.sh
- Now sources `common.sh` for shared config and logging
- Uses `port_in_use "$DATOMIC_PORT"` instead of hardcoded `bash -c '</dev/tcp/localhost/4334'`
- Uses `$REPO_ROOT` and `$LOG_DIR` from common.sh instead of manual path derivation
- Replaced raw `echo` calls with `log_info`/`log_warn` for consistent formatting

### create_dummy_user.sh
- Now sources `common.sh` for shared config, logging, and exit codes
- Uses `$REPO_ROOT` from common.sh instead of manual derivation
- Uses `$EXIT_USAGE` for consistent exit codes

### migrate-db.sh
- Removed useless `echo` wrapping `dirname` (SC2005 fix)

### shellcheck Status
All scripts pass shellcheck. Remaining warnings are known false positives:
- **SC2034** (unused variables in common.sh) — used by scripts that `source` it
- **SC1091** (can't follow source paths) — solved with `shellcheck -x`
- **SC2009** (grep ps output) — fallback branch when pgrep isn't available

---

## PowerShell Implementation

### common.ps1
- Reads `services.json` via `ConvertFrom-Json`
- Imports `.env` via custom `Import-DotEnv` function
- Port checking via `Get-NetTCPConnection`
- Process lookup via `Get-CimInstance Win32_Process` (for CommandLine matching)
- PID file management with port/pattern fallback
- Functions: `Get-ServiceManifest`, `Get-ServiceDef`, `Get-ServicePort`, `Test-PortInUse`,
  `Wait-ForPort`, `Wait-ForPortOrDie`, `Find-PidsByPort`, `Find-PidsByName`,
  `Find-ServicePids`, `Test-JavaInstalled`, `Test-LeinInstalled`, `Test-DatomicInstalled`,
  `Stop-ProcessGracefully`, `Remove-StalePidFile`, `Show-StartupFailure`,
  `Get-TransactorCommand`, `Initialize-DatomicConfig`

### start.ps1
- CmdletBinding with: Target, Install, Background, Quiet, Check, Idempotent
- Shared `Start-BackgroundService` helper (Start-Process + PID files + early verification)
- `Test-PortAvailable` with interactive/idempotent/non-interactive modes
- Full `Invoke-Checks` pre-flight validation
- Start targets: Datomic, Server, Figwheel, Garden, init-db, all (with Datomic cleanup on exit)

### stop.ps1
- Targets: all, repl, server, datomic, figwheel, garden, port, name
- Reads service list from manifest for status display
- `Show-Status` for dry-run mode
- `Confirm-Kill` with interactive prompt support
- `Stop-Pids` with graceful SIGTERM equivalent + Force mode

### dev-setup.ps1
- Params: NoStart, SkipDatomic, NoTestUser, Start
- Orchestrates: Datomic start, lein deps, DB init, test user creation, optional service start

### create_dummy_user.ps1
- Params: Username, Email, Password, Verify
- Logs credentials to .test-users, calls lein with init-db profile

### menu.ps1
- 12-option numbered menu (mirrors the bash `./menu` script)
- Routes to `scripts/windows/*.ps1` for all operations

---

## What Was NOT Changed

- **Container-internal scripts** (`deploy/start.sh`, `.devcontainer/post-create.sh`) — run inside Linux
- **Docker scripts** (`docker-setup.sh`, `docker-user.sh`, `docker-migrate.sh`) — Docker Desktop is cross-platform
- **Python scripts** (`fix-missing-else.py`) — already cross-platform
- **Agent workarounds** — environment-specific by design
- **`deploy/snakeoil.bat`** — already exists for Windows
- **`migrate-db.sh`** — migration tool is a one-time operation, not daily-driver dev tooling;
  PowerShell mirror deferred until someone actually needs it on Windows
