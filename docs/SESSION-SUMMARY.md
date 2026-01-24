# External Scripts Consolidation - Session Summary

**Date**: January 2026
**Branch**: `upgrade/datomic-pro`
**Plan File**: `/root/.claude/plans/effervescent-baking-flask.md`

---

## Overview

This session focused on modernizing the `scripts/external/` suite for the OrcPub repository, creating a unified system that serves both interactive development and automation workflows via flags.

---

## Key Decisions Made

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **Port INTO external/, don't delegate OUT** | Features from legacy scripts get absorbed into external/, not the other way around. No wrapper/shim approach. |
| 2 | **Flag-based dual mode** | Same script serves both interactive dev and automation via flags (`--quiet`, `--check`, `--idempotent`), not separate scripts. |
| 3 | **Start Datomic is the primary action** | Most common dev workflow is starting Datomic then using IDE/Calva. Menu reflects this. |
| 4 | **Submenus for menu** | 16 flat options was cluttered. Use submenus with "Start Datomic" and "Stop all" as quick actions. |
| 5 | **dev-setup.sh deferred** | Update it AFTER external/ is stable, not during this work. |
| 6 | **No reason to keep start-datomic-auto.sh** | Once external/ has feature parity, deprecate it entirely. |
| 7 | **Exit codes 0/1/2/3** | More granular than 0/1/2 - separates usage errors from prereq failures from runtime failures. |
| 8 | **Quiet mode still emits errors** | `log_error` always outputs to stderr even with `--quiet`. |
| 9 | **tmux -c flag** | Use tmux's native working directory support instead of `bash -c` string building. |
| 10 | **PID-first, port-fallback** | Check PID files before port scanning for more reliable process finding. |

---

## What Was Learned From Legacy Scripts

### start-datomic-auto.sh (most robust)
- Idempotent start + PID/log management
- Port wait with timeout
- Failure tail diagnostics (shows last N lines of log on failure)
- Config templating / vendor layout checks
- AGENTS.md called it "canonical" but we're porting its features, not delegating to it

### dev-menu.sh
- VS Code integration (`code --goto` for logs)
- tmux session management
- Uses `/tmp/` for logs (inconsistent - we use `$LOG_DIR`)

### dev-setup.sh
- First-run devcontainer setup
- Calls start-datomic-auto.sh OR docker-compose
- Runs `lein deps` and DB init
- Has own port-wait loop (redundant with common.sh)
- Deferred for later update

### External scripts (current state)
- **common.sh**: Created with colors, logging, port utilities, prerequisite checks
- **start.sh**: Rewritten - sources common.sh, port conflict detection, Datomic readiness wait (polls instead of sleep), `--tmux`, `--background`, `--install`
- **stop.sh**: Rewritten - sources common.sh, macOS-compatible (no grep -oP), Figwheel/Garden in status
- **menu**: Updated - sources common.sh, status display at top, auto-refresh timeout

---

## Analysis Files

| File | Description |
|------|-------------|
| `scripts/analyze/cld-analyze.md` | Copy of consolidation plan |
| `scripts/analyze/gp52-analyze.md` | External review with suggestions (some adopted, some rejected) |
| `scripts/external/analysis.md` | Earlier efficiency/security analysis |

---

## Technical Concepts

- **Bash scripting**: `set -euo pipefail`, `.env` file sourcing
- **tmux**: Session management with `-c` flag for working directory
- **Port checking**: Cross-platform fallbacks (lsof/ss/netstat)
- **Process management**: SIGTERM/SIGKILL escalation, PID tracking
- **Non-interactive detection**: `[[ -t 0 && -t 1 ]]`
- **Exit codes**: 0=success, 1=usage error, 2=prereq failure, 3=runtime failure
- **PID-first strategy**: More reliable than port scanning when PID files exist
- **Idempotent startup**: Succeed if service already running

---

## Implementation Status

### Completed
- [x] Create common.sh with shared colors, logging, port config
- [x] Update stop.sh - source common.sh, fix macOS grep, add Figwheel/Garden status
- [x] Update start.sh - source common.sh, add readiness wait, port conflict check, --background flag
- [x] Update menu - source common.sh, add Init DB option, input timeout
- [x] `--quiet`, `--check`, `--idempotent` (and `--if-not-running` alias) flags in start.sh
- [x] Non-interactive detection (`is_interactive()` in common.sh)
- [x] Failure diagnostics (`show_startup_failure()` in common.sh)
- [x] Granular exit codes (0/1/2/3) - EXIT_SUCCESS, EXIT_USAGE, EXIT_PREREQ, EXIT_RUNTIME
- [x] Submenu redesign for menu (Quick Actions + Start/Stop/Utils submenus)
- [x] tmux `-c` fix (uses native working directory support + remain-on-exit)
- [x] PID-first lookup (`find_service_pids()` in common.sh)
- [x] Pattern validation warning in stop.sh (refuses broad patterns in non-interactive mode)
- [x] Graceful kill with escalation (`kill_gracefully()` in common.sh)
- [x] Stale PID cleanup (`cleanup_stale_pid()` in common.sh)
- [x] Configurable timeouts via env vars (KILL_WAIT, PORT_WAIT)
- [x] Non-interactive stop protection (fails fast without --yes in CI)
- [x] Port consistency check in --check mode (warns if config port differs from DATOMIC_PORT)

---

## Scripts to Deprecate After Migration

Once external/ is complete:
- `scripts/start-datomic.sh` → replaced by `./start.sh datomic`
- `scripts/start-datomic-auto.sh` → replaced by `./start.sh datomic --quiet --idempotent`
- `scripts/dev-menu.sh` → replaced by `./menu`

**Keep**:
- `scripts/dev-setup.sh` → still useful for devcontainer first-run setup
- `.devcontainer/post-create.sh` → canonical installer (called by `--install`)

---

## External Review (gp52-analyze.md) - Decisions

| Suggestion | Status | Notes |
|------------|--------|-------|
| Delegate to start-datomic-auto.sh | **REJECTED** | Port features into external/ instead |
| Exit codes 0/1/2/3 | **ADOPTED** | Separates usage/prereq/runtime failures |
| Quiet mode still emits errors | **ADOPTED** | `log_error` always outputs to stderr |
| Staged deprecation with wrappers | **REJECTED** | Direct replacement preferred |
| tmux `-c` flag | **ADOPTED** | Security + reliability improvement |
| PID-first, port-fallback | **ADOPTED** | More deterministic process finding |

---

## Verification Commands

```bash
# Interactive dev workflow
./menu                    # Should show status, allow start/stop
./start.sh               # Should start Datomic + server interactively

# Automation workflow
./start.sh --check && echo "Ready"           # Pre-flight
./start.sh datomic --quiet --idempotent      # Idempotent start
echo $?                                       # Should be 0
./start.sh datomic --quiet --idempotent      # Run again - still 0
./stop.sh datomic --yes --quiet              # Clean shutdown

# Failure diagnostics
./start.sh datomic &
sleep 5
./start.sh datomic        # Should show clear error + diagnostics

# Security test
REPO_ROOT="/tmp/test'quoted\"path" ./start.sh --check
# Should not break
```

---

## Documentation Updates

Completed:
- **README.md**: Updated Datomic terminal behavior note (line ~108) and "Datomic Transactor Management" section (line ~531) to reference `scripts/external/` suite
- **AGENTS.md**: Updated "Local Datomic Transactor Script" section (line ~73) and "Datomic Transactor Script" section (line ~411) to reference `scripts/external/` suite

Optional:
- **scripts/external/README.md**: Create if needed - document flags and usage (help is embedded in each script via `--help`)
