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

## History

This document was originally created during the January 2026 upgrade session when the `scripts/external/` suite was developed. The scripts have since been reorganized:

- `scripts/external/` → `scripts/` (modern scripts moved to main scripts dir)
- `./menu` moved to repo root for easy access
- Legacy scripts moved to `scripts/legacy/` for reference
