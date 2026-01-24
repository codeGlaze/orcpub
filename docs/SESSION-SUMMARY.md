# Service Management Scripts - Session Summary

**Date**: January 2026
**Branch**: `upgrade/datomic-pro`

---

## Final State

The service management suite is now organized as follows:

```
./menu                  # Interactive hub + CLI passthrough (at repo root)
scripts/
├── common.sh           # Shared utilities (colors, logging, port config, exit codes)
├── start.sh            # Start services (datomic, server, figwheel, garden, init-db)
├── stop.sh             # Stop services with graceful shutdown
├── dev-setup.sh        # Initial dev environment setup
└── legacy/             # Archived scripts (for reference only)
    ├── dev-menu.sh
    ├── start-datomic.sh
    ├── start-datomic-auto.sh
    ├── stop-datomic-local.sh
    ├── dev-monitor.sh
    └── experimental/
```

**Primary interface**: `./menu`
- Interactive mode: `./menu` (numbered options, submenus)
- CLI passthrough: `./menu start datomic`, `./menu stop --yes`

---

## Key Decisions Made

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **Menu at repo root** | Easy access: just `./menu` from anywhere in the repo |
| 2 | **Scripts in scripts/** | Logical grouping, menu delegates to start.sh/stop.sh |
| 3 | **Legacy scripts preserved** | Moved to scripts/legacy/ for reference, not deleted |
| 4 | **Flag-based dual mode** | Same scripts for interactive and automation via `--quiet`, `--check`, `--idempotent` |
| 5 | **Logs in ./logs/** | Consolidated from /tmp/ to repo-local directory |
| 6 | **Exit codes 0/1/2/3** | 0=success, 1=usage, 2=prereq, 3=runtime |
| 7 | **PID-first, port-fallback** | More reliable process detection |

---

## Menu Structure

```
Quick Actions
  1) Start Datomic
  2) Init Database
  3) Stop all services
  q) Quit

  4) Start Services →
  5) Stop Services →
  6) Utilities →       (tail logs, open in VS Code, install, check prereqs)
  7) Tmux →            (start in tmux, attach, kill session)
  8) Help
```

---

## Quick Reference

```bash
# Interactive
./menu

# CLI (recommended for frequent use)
./menu start datomic
./menu stop datomic
./menu status

# Direct script usage
./scripts/start.sh datomic
./scripts/stop.sh --yes

# Automation / CI
./scripts/start.sh --check
./scripts/start.sh datomic --quiet --idempotent
./scripts/stop.sh --yes --quiet

# Setup (first run)
./scripts/dev-setup.sh --no-start
```

---

## Verification Commands

```bash
# Syntax check all scripts
bash -n ./menu && echo "menu: OK"
for f in scripts/*.sh; do bash -n "$f" && echo "OK: $f"; done

# Interactive workflow
./menu

# Automation workflow
./scripts/start.sh --check && echo "Ready"
./scripts/start.sh datomic --quiet --idempotent
echo "Exit code: $?"
./scripts/stop.sh datomic --yes --quiet

# Idempotent test (run twice, should succeed both times)
./scripts/start.sh datomic --idempotent
./scripts/start.sh datomic --idempotent
echo "Both succeeded: $?"
```

---

## Codespace Rebuild Checklist

After rebuild, verify:
```bash
# Check scripts are executable
ls -la ./menu scripts/*.sh

# Check menu loads
./menu --help

# Check start.sh prerequisites
./scripts/start.sh --check

# Full workflow test
./menu start datomic
./menu status
./menu stop datomic --yes
```

Potential issues:
- Scripts not executable → `chmod +x ./menu scripts/*.sh`
- logs/ directory missing → scripts create it automatically
- Java not available → `--check` will report prereq failure
- Old PID files from previous session → scripts handle stale cleanup

---

## Design Patterns Adopted

- `set -euo pipefail` - Fail fast
- `is_interactive()` - `[[ -t 0 && -t 1 ]]`
- `log_error` always outputs - Even in quiet mode
- SIGTERM → wait → SIGKILL escalation
- PID file in $LOG_DIR, not /tmp/

---

## Code Review Improvements (January 2026)

Based on external code review, the following robustness improvements were made:

### High Priority (Fixed)

| Issue | Fix |
|-------|-----|
| **tmux send-keys argument mangling** | Use `printf '%q'` for safe quoting |
| **Background start not verified** | Added `kill -0` check after 0.5s for all background starts |
| **No trap cleanup in start_all** | Added trap handler to stop Datomic on Ctrl+C |
| **Missing binary checks** | `check_datomic_installed` and `check_lein` verify binaries exist |
| **Config file permissions** | `chmod 600` on Datomic config after template copy |

### Medium Priority (Fixed)

| Issue | Fix |
|-------|-----|
| **Interactive read could hang** | Added 30-second timeout to `read -p` prompts |
| **Colors in non-interactive** | Already handled: colors disabled when `! -t 1` or `NO_COLOR` set |
| **lein exec failure handling** | Added `wait_for_port_or_die()` that checks process liveness during port wait |
| **Garden startup verification** | Extended to 5-second check loop instead of 1-second |

### New Functions Added

- `wait_for_port_or_die(port, pid, timeout)` - Waits for port, fails fast if process dies
- Trap handler in `start_all()` - Cleanup Datomic on interrupt

### Service Behavior (Final)

| Service | Execution | Returns Control |
|---------|-----------|-----------------|
| datomic | Background (nohup) | Yes, after port ready |
| figwheel | Background (nohup) | Yes, after port ready |
| garden | Background (nohup) | Yes, after 5s alive check |
| server | **Foreground** | No (interactive REPL) |

---

## Claude Code Data Persistence (January 2026)

To preserve Claude Code conversation history across codespace rebuilds:

### Setup
- Added Claude Code extension to `.devcontainer/devcontainer.json`
- Modified `.devcontainer/post-create.sh` to symlink `~/.claude` → `.claude-data/` in workspace
- Added `.claude-data/` to `.gitignore`

### How It Works
```bash
# post-create.sh creates this structure:
~/.claude → /workspaces/orcpub/.claude-data/  (symlink)

# .claude-data/ persists in the workspace but is gitignored
# Conversation history survives codespace rebuilds
```

### Manual Setup (if needed after rebuild)
```bash
# If symlink is missing:
mkdir -p /workspaces/orcpub/.claude-data
ln -sf /workspaces/orcpub/.claude-data ~/.claude
```

---

## Key Code Changes Made

### scripts/common.sh

Added `wait_for_port_or_die()` function for robust port waiting with process liveness checks:

```bash
wait_for_port_or_die() {
    local port="$1"
    local pid="$2"
    local timeout="${3:-60}"
    local elapsed=0
    while [[ $elapsed -lt $timeout ]]; do
        if ! kill -0 "$pid" 2>/dev/null; then
            log_error "Process $pid died while waiting for port $port"
            return 1
        fi
        if port_in_use "$port"; then
            return 0
        fi
        sleep 1
        ((elapsed++))
    done
    log_error "Timeout waiting for port $port (process $pid still running)"
    return 1
}
```

Fixed REPO_ROOT calculation after script reorganization:
```bash
# Before (when scripts were in scripts/external/):
REPO_ROOT="${REPO_ROOT:-$(cd "$COMMON_DIR/../.." && pwd)}"

# After (scripts now in scripts/):
REPO_ROOT="${REPO_ROOT:-$(cd "$COMMON_DIR/.." && pwd)}"
```

### scripts/start.sh

Background execution pattern for datomic/figwheel/garden:
```bash
# Example: start_datomic()
nohup "$DATOMIC_DIR/bin/transactor" "$DATOMIC_CONFIG" > "$LOG_DIR/datomic.log" 2>&1 &
local datomic_pid=$!
echo "$datomic_pid" > "$LOG_DIR/datomic.pid"

# Early verification (0.5s check)
sleep 0.5
if ! kill -0 "$datomic_pid" 2>/dev/null; then
    log_error "Datomic process died immediately after starting"
    show_startup_failure "datomic" "$LOG_DIR/datomic.log" "$DATOMIC_PORT"
    exit $EXIT_RUNTIME
fi
```

Trap cleanup in `start_all()`:
```bash
cleanup_on_exit() {
    local exit_code=$?
    if [[ "$started_datomic" == "true" && -n "$datomic_pid" ]]; then
        log_info "Stopping Datomic (PID $datomic_pid)..."
        kill "$datomic_pid" 2>/dev/null || true
        sleep 1
        kill -0 "$datomic_pid" 2>/dev/null && kill -9 "$datomic_pid" 2>/dev/null || true
        rm -f "$LOG_DIR/datomic.pid"
    fi
    exit "$exit_code"
}
trap cleanup_on_exit INT TERM
```

Safe tmux argument quoting:
```bash
# Before (could mangle arguments with spaces/special chars):
tmux send-keys -t "$TMUX_SESSION:$window_name" "${cmd_args[*]}" Enter

# After (properly quoted):
quoted_cmd=$(printf '%q ' "${cmd_args[@]}")
tmux send-keys -t "$TMUX_SESSION:$window_name" "$quoted_cmd" C-m
```

Interactive read timeout:
```bash
read -t 30 -p "Port $port in use. Stop existing service? [Y/n] " -n 1 -r
```

---

## Files Modified in This Session

| File | Change |
|------|--------|
| `scripts/common.sh` | Fixed REPO_ROOT, added `wait_for_port_or_die()` |
| `scripts/start.sh` | Background execution, trap cleanup, tmux quoting, read timeout, chmod 600 |
| `scripts/README.md` | Created comprehensive documentation |
| `menu` | Moved to repo root, updated paths |
| `.devcontainer/post-create.sh` | Claude data persistence symlink |
| `.devcontainer/devcontainer.json` | Added Claude Code extension |
| `.gitignore` | Added `.claude-data/` |
| `AGENTS.md` | Updated script paths |
| `README.md` | Fixed `././` paths |
| `UPGRADE_PLAN.md` | Fixed paths |
| `.vscode/tasks.json` | Updated script paths |

---

## Troubleshooting Notes

### "Datomic pro not found" error after script move
**Cause**: REPO_ROOT was calculated as `$COMMON_DIR/../..` (going up 2 directories) but scripts moved from `scripts/external/` to `scripts/`
**Fix**: Changed to `$COMMON_DIR/..` in common.sh

### Services don't return control after starting
**Cause**: Background services (datomic, figwheel, garden) were running in foreground
**Fix**: Added nohup, backgrounding (`&`), PID tracking, and port readiness waits

### Port wait hangs forever when process dies
**Cause**: `wait_for_port` didn't check if process was still alive
**Fix**: Created `wait_for_port_or_die()` that monitors process liveness during wait

---

## Menu Error Handling Improvements (January 2026)

Fixed issue where declining to restart a running service didn't return control to the menu.

### Root Cause
1. `set -euo pipefail` caused menu to exit when start.sh returned non-zero
2. No "Press Enter" prompts after script calls, so output scrolled away immediately

### Fixes Applied to `menu`

| Location | Issue | Fix |
|----------|-------|-----|
| Main menu "Start Datomic" | No error handling | `if ! start.sh; then` + prompt on failure |
| Main menu "Init DB", "Stop all" | No `\|\| true` | Added `\|\| true` to prevent exit |
| Start submenu (all options) | Same issues | Added error handling + prompts |
| Stop submenu (all options) | Missing `\|\| true` | Added |
| Tmux submenu - attach | No error handling if session dies | Added `\|\| { message }` |
| Tmux submenu - kill | Race condition | Wrapped in `if` |
| Tail submenu | No prompt after Ctrl+C | Added "Press Enter to continue..." |
| Utilities (install, check) | Missing `\|\| true` | Added |

### Fixes Applied to `stop.sh`

| Location | Issue | Fix |
|----------|-------|-----|
| `confirm_kill()` | No read timeout | Added `-t 30` with timeout error |
| `stop_name()` | No read timeout | Added `-t 30` with timeout error |
| `stop_*()` functions | Exit status bug - returned 0 even if `kill_pids` failed | Added `result` variable to capture and propagate exit status |

### Pattern for Menu Error Handling

```bash
# For operations that should show output on failure:
if ! "$SCRIPT_DIR/scripts/start.sh" datomic; then
    echo ""
    read -r -p "Press Enter to return to menu..."
fi

# For operations where we don't care about exit status:
"$SCRIPT_DIR/scripts/stop.sh" || true
echo ""
read -r -p "Press Enter to continue..."
```

---

## Port Forwarding Analysis (January 2026)

User observed 17-20 ports forwarded in VS Code despite only 3 configured in devcontainer.json.

### Explanation

VS Code's `remote.autoForwardPorts` (enabled by default) detects all listening ports:

| Source | Ports |
|--------|-------|
| Configured (`forwardPorts`) | 8890, 9500, 4334 |
| Datomic auto | 4335 (data port) |
| VS Code internals | ~5 node processes (extension hosts, language servers) |
| System | 2222 (SSH), 2000, 53 (DNS) |
| Ephemeral | ~4 IPC ports between VS Code processes |

### Not a Production Concern

- Devcontainers are development-only
- Codespaces port forwarding requires GitHub authentication
- Production deployments use separate Dockerfile/config
- Only port 8890 should be exposed in production (behind reverse proxy)

### To Disable Auto-Forwarding (Optional)

Add to devcontainer.json:
```json
"portsAttributes": {
  "*": { "onAutoForward": "ignore" }
},
"forwardPorts": [8890, 9500, 4334, 4335, 3449]
```

---

## Additional Files Modified

| File | Change |
|------|--------|
| `menu` | Error handling, "Press Enter" prompts, Garden label, test user option |
| `scripts/stop.sh` | Read timeouts (30s), exit status propagation in stop_* functions |
| `project.clj` | Removed Garden prep-tasks, added `:init-db` profile, cleaned up deps |
| `scripts/start.sh` | Uses `:init-db` profile for fast database init |
| `scripts/create_dummy_user.sh` | Uses `:init-db` profile for faster user creation |
| `scripts/README.md` | New: comprehensive script documentation |
| `AGENTS.md` | Updated development commands to use new scripts |

---

## External Code Review (January 2026)

An external code review was performed and documented in `scripts/analyze/rptr-analyze.md`. Key findings addressed:

### High Priority Issues (All Fixed)
- **Background-start verification**: PID written immediately after `nohup ... &` but process may exit right away
- **No cleanup trap**: `start_all` could leave orphaned Datomic if server crashes
- **Tmux argument quoting**: `send-keys` could mangle commands with spaces/special characters

### Medium Priority Issues (All Fixed)
- **Read timeout for prompts**: Added `-t 30` to prevent hanging in non-interactive mode
- **Exit status propagation**: `stop_*` functions now properly return `kill_pids` exit status
- **Binary checks**: Added explicit `-x` checks before invoking transactor

### Recommendations for Future Work
- Add shell tests (Bats or shunit) for `--check`, `--dry-run`, background starts
- Add CI job for shellcheck and integration tests
- Normalize `ss` grep for IPv6 robustness

---

## Session Continuity Notes

This project uses Claude Code with conversation persistence configured (see "Claude Code Data Persistence" section above). Long sessions may require compaction to manage context limits.

### Key Files for Context Recovery
When resuming after a compaction or new session:
1. **This document** (`docs/SESSION-SUMMARY.md`) - Comprehensive session history
2. **AGENTS.md** - Agent instructions and project context
3. **scripts/README.md** - Script documentation
4. **scripts/analyze/rptr-analyze.md** - External code review findings

### Session Compactions
- **January 2026 (1st)**: Initial script development, reorganization from `scripts/external/` to `scripts/`
- **January 2026 (2nd)**: Code review fixes, menu error handling, stop.sh improvements
- **January 2026 (3rd)**: Leiningen profile cleanup, faster REPL/init-db startup
- **January 2026 (4th)**: Documentation updates, verified all changes from 3rd session
- **January 2026 (5th)**: Figwheel/server startup fixes, devcontainer port labels

---

## Leiningen Profile Cleanup (January 2026)

Fixed performance issues and cleaned up project.clj configuration.

### Performance Fixes

| Issue | Fix |
|-------|-----|
| Garden compiled before every lein command | Removed global `:prep-tasks [["garden" "once"]]` |
| init-db triggered full ClojureScript compile | Added `:init-db` profile with minimal source-paths |
| Updated `start.sh` to use fast profile | `lein with-profile init-db run -m orcpub.dev-init` |

### Dependency Cleanup

| Issue | Fix |
|-------|-----|
| Duplicate `test.check` in main deps | Removed duplicate (line 37) |
| `test.check` redundant in `:dev` profile | Removed (base deps percolate) |
| `test.check` redundant in `:native-dev` | Removed |
| Outdated `piggieback` in `:native-dev` | Updated `com.cemerick/piggieback 0.2.1` → `cider/piggieback 0.5.3` |
| Redundant `datomic-pro` in `:prod` | Removed (peer already in base deps) |

### New Profiles

```clojure
;; Fast init-db - skips ClojureScript/Garden
:init-db {:source-paths ["src/clj" "src/cljc"]
          :prep-tasks ^:replace []}
```

### Menu Updates
- "Garden (CSS)" → "Garden Auto (CSS watcher)" for clarity
- Added "Create test user (verified)" to Utilities submenu

### User Creation
- `scripts/create_dummy_user.sh` now uses `:init-db` profile (faster)
- Menu option: Utilities → `u) Create test user` creates `test@example.com` / `testpass`
- Users created with `verify` flag can log in immediately

---

## History

This document was originally created during the January 2026 upgrade session when the `scripts/external/` suite was developed. The scripts have since been reorganized:

- `scripts/external/` → `scripts/` (modern scripts moved to main scripts dir)
- `./menu` moved to repo root for easy access
- Legacy scripts moved to `scripts/legacy/` for reference
- Code review improvements applied for robustness
- Claude Code data persistence configured for codespace rebuilds
- Menu error handling hardened (declining restart now returns to menu)
- stop.sh read timeouts and exit status fixes
- External code review documented and all high/medium priority issues addressed
- Session continuity documentation added for future context recovery
- Leiningen profile cleanup: removed redundant deps, fixed Garden prep-tasks
- Added `:init-db` profile for fast database initialization (no ClojureScript compile)
- Updated piggieback dependency in `:native-dev` profile
- Added fast test user creation to menu and updated create_dummy_user.sh
- Updated AGENTS.md with current development commands

---

## Figwheel & Server Startup Fixes (January 2026 - 5th Session)

Fixed multiple issues preventing the service scripts from correctly launching figwheel and the backend server.

### Figwheel Startup Fixes

| Issue | Fix |
|-------|-----|
| `'figwheel' is not a task` error | Changed `lein with-profile +dev figwheel` → `lein fig:dev` (uses alias) |
| Timeout waiting for wrong port | Reverted FIGWHEEL_PORT from 9500 back to 3449 (project's established port) |
| figwheel-main port not explicit | Added `:ring-server-options {:port 3449}` to `dev.cljs.edn` |

**Note**: The frontend code in `env/dev/env/main.cljs:14` has a hardcoded websocket URL expecting port 3449. This is why 3449 must remain the figwheel port.

### Server Background Mode Fix

| Issue | Fix |
|-------|-----|
| Server exits immediately with "Bye for now!" in background mode | REPL requires stdin; nohup closes it |
| Fix | Added TTY detection: uses `:headless` mode when not interactive |

**Pattern in start.sh**:
```bash
start_server() {
    if [[ -t 0 ]]; then
        # Interactive terminal - normal REPL
        lein with-profile +dev,+start-server repl
    else
        # Non-interactive (background/nohup) - headless mode
        lein with-profile +dev,+start-server repl :headless
    fi
}
```

### Devcontainer Port Labels

Added named port labels for VS Code/Codespaces port forwarding panel:

```json
"forwardPorts": [8890, 3449, 4334],
"portsAttributes": {
  "8890": { "label": "Backend Server", "onAutoForward": "notify" },
  "3449": { "label": "Figwheel", "onAutoForward": "silent" },
  "4334": { "label": "Datomic", "onAutoForward": "silent" }
}
```

Also fixed stale port 9500 → 3449 in `forwardPorts` array.

### Files Modified

| File | Change |
|------|--------|
| `scripts/start.sh` | Fixed figwheel command (`lein fig:dev`), added headless server mode |
| `scripts/common.sh` | FIGWHEEL_PORT reverted to 3449 |
| `dev.cljs.edn` | Added `:ring-server-options {:port 3449}` |
| `.devcontainer/devcontainer.json` | Added `portsAttributes` with labels, fixed port 9500→3449 |

### Service Launch Order

The correct order for launching the full stack:
1. **Datomic** - `./scripts/start.sh datomic` (background, wait for port 4334)
2. **Init DB** - `./scripts/start.sh init-db` (one-shot, applies schema)
3. **Server** - `./scripts/start.sh server --background` (headless mode for background)
4. **Figwheel** - `./scripts/start.sh figwheel` (background, port 3449)

Or use `./scripts/start.sh` (no args) for the integrated flow that handles all of this
