# OrcPub Service Management Scripts

This directory contains the unified service management suite for OrcPub development.

## Quick Start

```bash
# Interactive menu (recommended)
./menu

# Start individual services
./scripts/start.sh datomic
./scripts/start.sh server
./scripts/start.sh figwheel
./scripts/start.sh garden    # CSS auto-watcher (optional)

# Initialize database (fast - skips ClojureScript compile)
./scripts/start.sh init-db

# Create test user (test@example.com / testpass, pre-verified)
./scripts/create_dummy_user.sh test test@example.com testpass verify

# Start all (Datomic backgrounded, server in foreground)
./scripts/start.sh

# Stop services
./scripts/stop.sh              # Interactive - asks for confirmation
./scripts/stop.sh --yes        # Non-interactive - stops all
./scripts/stop.sh datomic      # Stop specific service
```

## Directory Structure

```
./menu                    # Interactive hub (at repo root for easy access)
scripts/
├── common.sh             # Shared utilities (colors, logging, port checks, exit codes)
├── start.sh              # Service launcher (datomic, server, figwheel, garden)
├── stop.sh               # Service stopper with graceful shutdown
├── dev-setup.sh          # Initial development environment setup
├── create_dummy_user.sh  # Utility: create test users
├── inspect-datomic-jars.sh   # Utility: inspect Datomic jars
├── run-dependency-audit.sh   # Utility: run dependency audit
└── legacy/               # Archived scripts (reference only)
```

## Script Behavior

### Background vs Foreground

| Service | Default Behavior | Logs |
|---------|------------------|------|
| datomic | Background, returns after ready | `logs/datomic.log` |
| figwheel | Background, returns after ready | `logs/figwheel.log` |
| garden | Background, returns after ready | `logs/garden.log` |
| server | **Foreground** (interactive REPL) | stdout |

### Chaining Commands

Since datomic/figwheel/garden run in background, you can chain:

```bash
./scripts/start.sh datomic && ./scripts/start.sh figwheel && ./scripts/start.sh garden
```

### Automation Flags

```bash
--quiet          # Suppress non-error output
--idempotent     # Exit successfully if service already running
--background     # Run in background (for server REPL)
--check          # Pre-flight validation only, don't start
--tmux           # Run services in tmux windows
```

## Error Handling

The scripts include robust error handling:

1. **Early verification** - After starting a background process, scripts verify it's still alive (kill -0 check)
2. **Port readiness** - Scripts wait for the service port to be available, with process liveness monitoring
3. **Timeout protection** - Interactive prompts timeout after 30 seconds
4. **Trap cleanup** - `start_all` cleans up Datomic if server is interrupted (Ctrl+C)
5. **Config protection** - Datomic config files are created with restricted permissions (chmod 600)

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success (or already running in idempotent mode) |
| 1 | Usage error / invalid arguments |
| 2 | Prerequisite failure (missing Java, lein, Datomic) |
| 3 | Runtime failure (service failed to start) |

## Environment Variables

Key variables (set in `.env` or environment):

| Variable | Default | Description |
|----------|---------|-------------|
| `DATOMIC_PORT` | 4334 | Datomic transactor port |
| `SERVER_PORT` | 8890 | Backend server port |
| `FIGWHEEL_PORT` | 9500 | Figwheel dev server port |
| `DATOMIC_TYPE` | pro | Datomic edition (pro) |
| `DATOMIC_VERSION` | 1.0.7482 | Datomic version |
| `LOG_DIR` | ./logs | Log file directory |
| `NO_COLOR` | (unset) | Set to disable colored output |

## Troubleshooting

### Service won't start
```bash
./scripts/start.sh --check    # Run pre-flight checks
tail -f logs/datomic.log      # Check logs
```

### Port already in use
```bash
./scripts/stop.sh datomic     # Stop existing service
# Or in non-interactive mode:
./scripts/start.sh datomic --idempotent  # Succeed if already running
```

### Process died immediately
The scripts will show the last 30 lines of the log file automatically. Common causes:
- Missing dependencies (`lein deps`)
- Invalid configuration
- Port conflict

## Leiningen Profiles

The project uses specialized Leiningen profiles for faster startup:

| Profile | Purpose | Usage |
|---------|---------|-------|
| `:init-db` | Fast database init (no ClojureScript) | `lein with-profile init-db run -m orcpub.dev-init` |
| `:dev` | Full development (default) | `lein repl` |
| `:uberjar` | Production build (includes Garden) | `lein uberjar` |

**Note:** Garden CSS compilation was removed from global prep-tasks for faster REPL startup.
CSS is compiled via:
- `./menu` → Start → Garden Auto (runs `lein garden auto` in background)
- `lein garden once` (one-shot compile)
- Automatically during `lein uberjar`

The compiled CSS is committed to `resources/public/css/compiled/styles.css`.

## Development

### Adding a New Service

1. Add port configuration to `common.sh`
2. Create `start_<service>()` function in `start.sh`
3. Create `stop_<service>()` function in `stop.sh`
4. Add to menu options in `menu`

### Testing Scripts

```bash
# Syntax check
bash -n ./scripts/start.sh
bash -n ./scripts/common.sh

# Pre-flight validation
./scripts/start.sh --check
```
