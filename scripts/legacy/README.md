# Legacy Scripts

This folder contains archived scripts that have been superseded by the modern unified service management suite.

## What replaced these scripts?

| Legacy Script | Replacement |
|---------------|-------------|
| `dev-menu.sh` | `./menu` (interactive hub) |
| `start-datomic.sh` | `./scripts/start.sh datomic` |
| `start-datomic-auto.sh` | `./scripts/start.sh datomic` |
| `stop-datomic-local.sh` | `./scripts/stop.sh datomic` |
| `dev-monitor.sh` | `./menu` (has tmux integration) |
| `dev-verify.sh` | Archived |

## Modern commands

```bash
# Interactive menu (recommended)
./menu

# Start services
./scripts/start.sh datomic
./scripts/start.sh server
./scripts/start.sh figwheel

# Stop services
./scripts/stop.sh datomic
./scripts/stop.sh --yes  # stop all

# Check status
./menu status
```

## Why keep these?

These scripts are preserved for reference in case legacy behavior is needed. They are not actively maintained.
