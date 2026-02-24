# Cross-Platform Scripts — Implementation Plan

## Current State: Script Inventory

### Category 1: Dev Tooling (core — high complexity)

| Script | Lines | Purpose | Platform-Specific Features |
|--------|-------|---------|---------------------------|
| `scripts/common.sh` | 441 | Shared utilities: env, logging, port/process mgmt | `lsof`/`ss`/`netstat`, `/dev/tcp`, `kill -0`, `pgrep`, `nohup`, `ps -o`, `[[ ]]`, `BASH_SOURCE` |
| `scripts/start.sh` | 773 | Service launcher (datomic, server, figwheel, garden) | `nohup`, `tmux`, `kill` signals, `read -t`, PID files, process lifecycle |
| `scripts/stop.sh` | 367 | Process stop/kill/status | `kill -TERM`/`-KILL`, `pgrep`, PID files, signal handling |
| `scripts/dev-setup.sh` | 103 | First-time dev environment orchestrator | `/dev/tcp` for port check, sources common.sh |
| `scripts/create_dummy_user.sh` | 46 | User creation wrapper | Minimal — just calls `lein` |
| `scripts/migrate-db.sh` | 510 | Datomic Free→Pro data migration | `is_interactive`, `chmod`, sources common.sh |

### Category 2: Docker Scripts (medium complexity)

| Script | Lines | Purpose | Platform-Specific Features |
|--------|-------|---------|---------------------------|
| `docker-setup.sh` | ~200 | Docker env setup: .env gen, SSL, dirs | `openssl`, `chmod 600`, `/dev/urandom`, `read -rp` |
| `docker-user.sh` | ~220 | Docker user CRUD | `docker exec`/`cp` (cross-platform via Docker Desktop) |
| `docker-migrate.sh` | 458 | Docker-based Datomic migration | `docker compose` (cross-platform via Docker Desktop) |

### Category 3: Deploy/Container-Internal (no changes needed)

| Script | Lines | Purpose | Notes |
|--------|-------|---------|-------|
| `deploy/start.sh` | 87 | Docker transactor entrypoint | Runs inside Linux container |
| `deploy/snakeoil.sh` | 8 | SSL cert generation | `openssl req` |
| `deploy/snakeoil.bat` | 1 | Windows SSL cert generation | Already exists (minimal) |
| `.devcontainer/post-create.sh` | 115 | Datomic Pro install | Runs inside Linux container |

### Category 4: Agent/Utility (already cross-platform or irrelevant)

| Script | Lines | Purpose | Notes |
|--------|-------|---------|-------|
| `scripts/fix-missing-else.py` | ~70 | Linter auto-fix | Python — already cross-platform |
| `.agent-workarounds/maven-proxy/setup-maven-proxy.sh` | ~60 | Proxy workaround | Agent-only, Linux containers |

---

## Platform-Specific Dependencies in common.sh

These are the hard blockers for Windows compatibility:

| Feature | Linux/macOS | Windows Equivalent |
|---------|-------------|-------------------|
| Port check | `lsof -i :PORT` / `ss -tln` / `/dev/tcp` | `netstat -an` / PowerShell `Test-NetConnection` / Python `socket` |
| Find PID by port | `lsof -t -i :PORT` / `ss -tlnp` | `netstat -ano` + parse / PowerShell `Get-NetTCPConnection` |
| Find PID by name | `pgrep -f PATTERN` | `tasklist /FI` / `wmic` / PowerShell `Get-Process` |
| Kill process | `kill -TERM/-KILL PID` | `taskkill /PID` / PowerShell `Stop-Process` |
| Process alive check | `kill -0 PID` | PowerShell `Get-Process -Id` |
| Background execution | `nohup CMD &` | `Start-Process` / `start /B` |
| PID files | `echo $! > file.pid` | PowerShell `$proc.Id > file.pid` |
| Terminal detection | `[[ -t 0 && -t 1 ]]` | PowerShell: `[Environment]::UserInteractive` |
| Signal trapping | `trap '...' INT TERM` | No equivalent (use try/finally) |
| tmux | `tmux new-session` | Windows Terminal tabs / `wt` |
| ANSI colors | `\033[0;31m` | Works in modern Windows Terminal, not in legacy cmd |
| Source .env | `set -a; . .env; set +a` | PowerShell: `Get-Content .env \| ForEach-Object { ... }` |

---

## Strategy Options

### Option A: PowerShell Mirror Scripts

Create `.ps1` equivalents for each `.sh` script.

- **Pro**: Native on Windows, no extra dependencies, full OS integration
- **Pro**: PowerShell has excellent process/network management (`Get-Process`, `Test-NetConnection`)
- **Con**: Doubles maintenance — two implementations of every feature
- **Con**: PowerShell syntax is very different from bash — hard to keep in sync
- **Con**: Changes must be made in two places

### Option B: Python Cross-Platform CLI

Rewrite core logic in Python (single `orcdev` CLI tool or package).

- **Pro**: Single codebase for all platforms (Windows, macOS, Linux)
- **Pro**: Python already used in project (`fix-missing-else.py`)
- **Pro**: Rich stdlib: `subprocess`, `socket`, `os`, `pathlib`, `shutil`, `signal`
- **Pro**: `psutil` library gives cross-platform process management
- **Con**: Requires Python 3 installed (usually present but not guaranteed)
- **Con**: Third-party dep (`psutil`) for some features, or more verbose native code
- **Con**: Significant rewrite effort

### Option C: Babashka (Clojure scripting)

Use Babashka — fast-starting Clojure interpreter — for cross-platform scripts.

- **Pro**: Natural fit for a Clojure project
- **Pro**: Single binary, no JVM startup, works on all platforms
- **Pro**: Can share code/patterns with the main Clojure codebase
- **Con**: New dependency to install
- **Con**: Team needs Babashka knowledge
- **Con**: Smaller ecosystem for system admin tasks vs Python/PowerShell

### Option D: Keep Bash + PowerShell Wrappers (WSL-aware)

Keep bash scripts as primary. Create thin PowerShell wrappers that:
1. Detect WSL and delegate to bash scripts via `wsl.exe`
2. Fall back to native PowerShell for Windows without WSL

- **Pro**: Minimal changes to working bash scripts
- **Pro**: WSL2 is standard for most Windows developers doing Clojure/Java work
- **Pro**: PowerShell fallback for environments without WSL
- **Con**: WSL dependency for full functionality
- **Con**: Still need some PowerShell for the fallback path

---

## Recommended Approach: Option D (Pragmatic) + Hardening

### Rationale

1. **The scripts are well-built and battle-tested** — rewriting them risks introducing bugs
2. **This is a Clojure/JVM project** — most Windows devs already use WSL2 for JVM tooling
3. **Docker scripts already work** — Docker Desktop is cross-platform; `docker compose` commands work the same
4. **Deploy/container scripts don't need changes** — they run inside Linux containers
5. **Hardening the bash scripts benefits everyone** — shellcheck compliance, better error handling, POSIX where possible

### Implementation Plan

#### Phase 1: Bash Hardening (existing scripts)

1. **shellcheck compliance** — Run shellcheck on all `.sh` files, fix warnings
2. **Consistent shebang** — Ensure all scripts use `#!/usr/bin/env bash`
3. **Consistent error handling** — All scripts use `set -euo pipefail` and the exit code convention from `common.sh`
4. **Portable alternatives** — Where easy, prefer POSIX-compatible constructs:
   - `command -v` instead of `which`
   - Cross-platform `ss`/`lsof`/`netstat` fallback chain (already in `common.sh` — verify completeness)
5. **macOS compatibility** — Test/fix `sed -i` differences, `readlink` vs `realpath`, `date` flag differences
6. **NO_COLOR support** — Already partially implemented, ensure consistency
7. **Add `--help` to all scripts** — Some have it, ensure all do

#### Phase 2: Cross-Platform Entry Points

Create PowerShell wrapper scripts (`.ps1`) in a `scripts/windows/` directory:

```
scripts/
├── common.sh              # Existing (hardened)
├── start.sh               # Existing (hardened)
├── stop.sh                # Existing (hardened)
├── dev-setup.sh           # Existing (hardened)
├── create_dummy_user.sh   # Existing (hardened)
├── migrate-db.sh          # Existing (hardened)
└── windows/
    ├── README.md           # Windows setup guide (WSL or native)
    ├── orcdev.ps1          # Main entry point — delegates to bash via WSL or runs native
    ├── common.ps1          # Shared PowerShell utilities (env loading, port checks)
    ├── start.ps1           # Service launcher (WSL-first, native fallback)
    ├── stop.ps1            # Service stopper
    └── dev-setup.ps1       # First-time setup
```

**WSL-first approach in each wrapper:**
```powershell
# scripts/windows/start.ps1
param([string]$Target = "all", [switch]$Help)

# Check for WSL
if (Get-Command wsl -ErrorAction SilentlyContinue) {
    # Translate Windows path to WSL path and delegate
    $wslPath = wsl wslpath -u (Resolve-Path "$PSScriptRoot\..").Path
    wsl bash "$wslPath/start.sh" $Target @args
    exit $LASTEXITCODE
}

# Native fallback (for environments without WSL)
Write-Host "[WARN] WSL not found. Running native PowerShell implementation." -ForegroundColor Yellow
# ... native PowerShell implementation ...
```

#### Phase 3: Native PowerShell Fallback (for non-WSL Windows)

Implement core functionality natively in PowerShell for Windows users without WSL. Priority order:

1. **`common.ps1`** — .env loading, port check (`Test-NetConnection`), process lookup, logging
2. **`start.ps1`** — Start services (lein, java/datomic) with `Start-Process`
3. **`stop.ps1`** — Stop services via `Stop-Process`
4. **`dev-setup.ps1`** — First-time setup orchestration

Lower priority (Docker scripts already work cross-platform):
5. **`docker-setup.ps1`** — Only if `openssl` path differs on Windows

#### Phase 4: Shared Configuration

Ensure `.env` file loading works identically:
- Bash: `set -a; . .env; set +a` (already in `common.sh`)
- PowerShell: Parse `.env` with `Get-Content` + `Set-Variable`
- Same port defaults, same paths (adjusted for OS), same exit codes

#### Phase 5: Documentation

1. Update `SETUP.md` with Windows-specific instructions
2. Update `AGENTS.md` development commands section
3. Add `scripts/windows/README.md` with:
   - Prerequisites (WSL recommended, or PowerShell 7+)
   - How to use the wrappers
   - Differences from bash scripts

---

## What NOT to Change

- **Container-internal scripts** (`deploy/start.sh`, `.devcontainer/post-create.sh`) — these run inside Linux
- **Python scripts** (`fix-missing-else.py`) — already cross-platform
- **Agent workarounds** — environment-specific by design
- **`deploy/snakeoil.bat`** — already exists for Windows

---

## Hardening Checklist (Phase 1 Detail)

### common.sh
- [ ] Run `shellcheck` and fix all warnings
- [ ] Verify `find_pids_by_port` fallback chain works on macOS (no `ss`)
- [ ] Verify `find_pids_by_name` works without `pgrep` (fallback to `ps aux | grep`)
- [ ] Add macOS-compatible `sed -i ''` handling where applicable
- [ ] Ensure `get_uptime` works on macOS (`ps -o etime=` format may differ)
- [ ] Add `SHELL` detection for edge cases

### start.sh
- [ ] shellcheck compliance
- [ ] Verify `read -t 30 -p` works in all target shells
- [ ] Ensure tmux detection/usage is clean
- [ ] Verify PID file cleanup on all exit paths

### stop.sh
- [ ] shellcheck compliance
- [ ] Verify process kill chain works on macOS
- [ ] Ensure `--quiet` mode produces machine-parseable output

### docker-setup.sh
- [ ] shellcheck compliance
- [ ] Verify `openssl` command works on macOS Homebrew openssl
- [ ] Handle `date -u` differences (GNU vs BSD date)

### docker-user.sh
- [ ] shellcheck compliance
- [ ] Verify `docker compose` vs `docker-compose` detection

### docker-migrate.sh
- [ ] shellcheck compliance
- [ ] Verify all `docker compose` commands work with Docker Desktop on Windows

---

## File Changes Summary

### New files:
- `scripts/windows/README.md`
- `scripts/windows/common.ps1`
- `scripts/windows/orcdev.ps1`
- `scripts/windows/start.ps1`
- `scripts/windows/stop.ps1`
- `scripts/windows/dev-setup.ps1`

### Modified files (hardening):
- `scripts/common.sh`
- `scripts/start.sh`
- `scripts/stop.sh`
- `scripts/dev-setup.sh`
- `scripts/create_dummy_user.sh`
- `scripts/migrate-db.sh`
- `docker-setup.sh`
- `docker-user.sh`
- `docker-migrate.sh`
- `SETUP.md` (add Windows section)
