# Datomic Data Migration — Free to Pro

Existing self-hosted deployments have a Datomic Free database in `./data` that
must be migrated before upgrading to the new Java 21 / Datomic Pro Docker stack.
The storage protocols (`datomic:free://` vs `datomic:dev://`) are incompatible at
the file level — the Pro transactor cannot read Free storage, and vice versa.

## Why Migration Is Needed

Datomic Free and Datomic Pro use different **storage protocols** — the layer that
determines how datoms are physically written to disk:

| | Datomic Free | Datomic Pro (dev) |
|---|---|---|
| URI scheme | `datomic:free://` | `datomic:dev://` |
| Storage engine | H2 embedded database | H2 embedded database (different schema) |
| Transport | Custom (pre-1.0 Datomic) | ActiveMQ Artemis |
| Authentication | None | Password-based |
| Java support | Java 8 only | Java 11, 17, 21 |

Despite both using H2 internally, the on-disk format is different. The Pro dev
transactor cannot open a Free database's H2 files. This is analogous to PostgreSQL
major versions — same data model, incompatible storage.

## How It Works

The migration uses Datomic's `bin/datomic` CLI tools — **not** the Peer API
(`datomic.api`). These commands live in the `datomic.backup-cli` namespace inside
the transactor jar and are invoked via the command line:

```bash
bin/datomic backup-db  <from-db-uri> <to-backup-uri>
bin/datomic restore-db <from-backup-uri> <to-db-uri>
```

### backup-db

```bash
bin/datomic backup-db "datomic:free://localhost:4334/orcpub" "file:./backup/orcpub"
```

1. **Connects** to the running transactor via the peer library
2. **Reads the full database** — every datom, every transaction, the complete
   history — through the peer connection (not by reading storage files directly)
3. **Writes a portable backup** to the specified `file:` URI

The backup format is Datomic's internal serialization of the index segments and
log. It is **storage-protocol-independent** — the backup from a Free database is
byte-identical to what a Pro database with the same data would produce. This is
what makes cross-protocol migration possible.

The backup is **incremental-capable**: if you run backup-db twice to the same
directory, the second run only writes segments that changed since the first. For
the initial migration this doesn't matter, but it's useful for ongoing backups.

### restore-db

```bash
bin/datomic restore-db "file:./backup/orcpub" "datomic:dev://localhost:4334/orcpub?password=..."
```

1. **Creates a new database** at the target URI (the database must not already
   exist — restore-db handles the `create-database` call internally)
2. **Reads the portable backup** segments
3. **Writes them into the new storage protocol**, rebuilding indexes as needed
4. The restored database is immediately usable — no additional indexing or
   compaction step

**Important**: restore-db writes to the transactor, not directly to storage.
The target transactor must be running and reachable at the URI.

### verify-backup (Pro only)

```bash
bin/datomic verify-backup "file:./backup/orcpub" true <t>
```

Reads every segment in the backup and confirms it's intact. Use `list-backups`
to find available `t` values:

```bash
bin/datomic list-backups "file:./backup/orcpub"
```

**Note**: `verify-backup` is only available in the Pro distribution. The Free
distribution has `backup-db`, `restore-db`, and `list-backups` but not `verify-backup`.

### Peer library matching

**Critical**: The `bin/datomic` CLI uses the peer library from its own
distribution. The Free distribution's CLI can only connect to Free transactors
(`datomic:free://`), and the Pro distribution's CLI can only connect to Pro
transactors (`datomic:dev://`, etc.).

This means:
- **Backup** must use the **Free** distribution's `bin/datomic`
- **Restore** must use the **Pro** distribution's `bin/datomic`

The migration scripts handle this automatically.

## What's Preserved

Everything. The backup/restore cycle is lossless:

| Data | Preserved? | Notes |
|------|-----------|-------|
| All datoms (current values) | Yes | Every entity attribute value |
| Full transaction history | Yes | Every past assertion and retraction |
| Transaction timestamps (`db/txInstant`) | Yes | Exact timestamps preserved |
| Transaction IDs (t values) | Yes | `basis-t` matches after restore |
| Entity IDs | Yes | All `:db/id` values are identical |
| Schema (attributes, partitions) | Yes | Schema is just datoms — comes along |
| Indexes | Rebuilt | Pro rebuilds its own indexes from the data |

After restore, `(d/basis-t (d/db conn))` returns the same value as before
migration. Entity IDs are stable — anything stored externally that references
a Datomic entity ID will still work.

## What Changes

Only the storage layer changes. At the application level:

| Aspect | Before | After |
|--------|--------|-------|
| Connection URI | `datomic:free://host:4334/orcpub` | `datomic:dev://host:4334/orcpub?password=...` |
| Peer library | `com.datomic/datomic-free` | `com.datomic/peer` |
| Transactor process | `datomic-free` transactor | `datomic-pro` transactor |
| Storage files (`./data/`) | Free H2 format | Pro dev H2 format |
| API surface | `datomic.api/*` | `datomic.api/*` (identical) |
| Query language | Datalog | Datalog (identical) |

The Clojure Peer API (`datomic.api`) is the same across Free and Pro. Application
code does not change — only the connection URI and the dependency coordinate.

## Quick Start (Bare Metal)

The script auto-selects the correct Datomic distribution for each phase:
backup uses the Free distribution (searched in `lib/`, or extracted from the
bundled tarball), restore and verify use the installed Pro distribution.

```bash
# 1. With the old (Free) transactor running:
./scripts/migrate-db.sh backup

# 2. Stop the Free transactor, move data aside, start Pro transactor
# (see "Step-by-Step" below for details)

# 3. Restore into the new Pro database:
./scripts/migrate-db.sh restore "datomic:dev://localhost:4334/orcpub?password=..."

# 4. Verify backup integrity:
./scripts/migrate-db.sh verify
```

## Quick Start (Docker)

```bash
# 1. With the OLD Docker stack still running:
./docker-migrate.sh backup

# 2. Stop old stack, build and start new:
docker compose down
docker compose -f docker-compose-build.yaml build
docker compose -f docker-compose-build.yaml up -d

# 3. After services are healthy, restore:
./docker-migrate.sh restore

# 4. Verify backup integrity:
./docker-migrate.sh verify
```

Or run everything in one command: `./docker-migrate.sh full`

## Step-by-Step Guide (Bare Metal)

### Prerequisites

- Datomic Free transactor running with your existing data
- `.env` file with `DATOMIC_URL` (pointing to the Free transactor) and `DATOMIC_PASSWORD`
- Datomic Pro installed (`lib/com/datomic/datomic-pro/...`)
- Enough disk space for the backup (roughly equal to database size)

### Phase 1: Backup

With the Free transactor running:

```bash
./scripts/migrate-db.sh backup
```

The script detects `datomic:free://` in your `DATOMIC_URL` and looks for the
Free distribution in `lib/` (it should already be extracted alongside your
existing database). If it can't find one and you're in an interactive
terminal, it will prompt you for the path — or press Enter to extract the
bundled tarball (`lib/datomic-free-0.9.5703.tar.gz`) as a last resort.

Once the Free distribution is located, the script runs `bin/datomic backup-db`:

1. Connects to the running transactor via the Free peer library
2. Reads the full database (every datom, every transaction, full history)
3. Writes a portable backup to `./backup/orcpub`

If you want to skip the prompt and point to a specific distribution:

```bash
./scripts/migrate-db.sh --datomic-dir /path/to/datomic-free backup
```

**Duration**: Depends on database size. Roughly 1-3 GB/minute depending on
hardware. A 20GB database may take 15-60 minutes.

### Phase 2: Swap Transactors

```bash
# Stop the Free transactor
./scripts/stop.sh datomic

# Move old data aside (incompatible with Pro)
mv ./data ./data.free-backup
mkdir -p ./data

# Start the Pro transactor
./scripts/start.sh datomic
```

Wait for the transactor to become healthy (port 4334).

### Phase 3: Restore

```bash
./scripts/migrate-db.sh restore "datomic:dev://localhost:4334/orcpub?password=${DATOMIC_PASSWORD}"
```

This runs `bin/datomic restore-db` from the **Pro** distribution, which:
1. Creates a new database at the target URI
2. Reads the portable backup from `./backup/orcpub`
3. Writes it into the new Pro storage, rebuilding indexes

**The target database must not already exist.** If the application already created
it on first connect, delete `./data/*` and restart the transactor before restoring.

**Duration**: Similar to backup. Large databases take proportionally longer.

### Phase 4: Verify

```bash
# Verify the backup's integrity (reads every segment)
./scripts/migrate-db.sh verify

# Optionally, check DB-level stats (user/entity counts):
# java -cp target/orcpub.jar clojure.main docker/scripts/migrate-db.clj verify [db-uri]

# Then log in and check your data
```

## Step-by-Step Guide (Docker)

### Prerequisites

- Old Docker stack is running (`docker compose ps` shows healthy datomic + orcpub)
- `.env` file exists with correct `DATOMIC_PASSWORD`
- Enough disk space for the backup (roughly equal to database size)
- New source code checked out (has `docker-compose-build.yaml` and migration scripts)

### Phase 1: Backup

With the old stack running:

```bash
./docker-migrate.sh backup
```

This:
1. Detects the running datomic image and Docker network
2. Launches a temporary container on the same network
3. Runs `bin/datomic backup-db` to create a portable backup
4. Writes the backup to `./backup/orcpub` via a bind-mounted volume

The backup is written directly to the host filesystem through a Docker bind mount,
so large databases won't fill the container's overlay filesystem.

### Phase 2: Swap Stacks

Stop the old stack and start the new one:

```bash
docker compose down

# Move old data directory aside (incompatible with Pro)
mv ./data ./data.free-backup
mkdir -p ./data

# Build and start the new stack
docker compose -f docker-compose-build.yaml build
docker compose -f docker-compose-build.yaml up -d
```

Wait for services to become healthy:

```bash
docker compose ps
```

The `full` command automates this, including renaming `./data` → `./data.free-backup`.

### Phase 3: Restore

With the new stack running:

```bash
./docker-migrate.sh restore
```

This:
1. Detects the running datomic image (now the Pro build)
2. Launches a temp container that runs `bin/datomic restore-db`
3. Reads the backup from `./backup/orcpub` (bind-mounted)
4. Restores into the Pro transactor (URI auto-detected from the running
   container, or constructed from `DATOMIC_PASSWORD` in `.env`)

### Phase 4: Verify

```bash
./docker-migrate.sh verify
```

This runs `bin/datomic verify-backup` to confirm every segment is readable.

For DB-level stats (user count, entity count), you can use the optional Clojure
verification tool (`docker/scripts/migrate-db.clj`). It requires a JVM with the
uberjar on the classpath and network access to the transactor — see the script's
header for invocation details.

Also test a login:

```bash
curl -sk -X POST https://localhost/login \
  -H "Content-Type: application/json" \
  -d '{"username":"youruser","password":"yourpass"}'
```

## Rollback

If something goes wrong after the stack swap, you can roll back to the Free
transactor at any point before deleting `./data.free-backup`:

**Bare metal:**

```bash
# Stop the Pro transactor
./scripts/stop.sh datomic

# Restore the original Free data
rm -rf ./data
mv ./data.free-backup ./data

# Restart the Free transactor
./scripts/start.sh datomic
```

**Docker:**

```bash
docker compose down

# Restore the original Free data
rm -rf ./data
mv ./data.free-backup ./data

# Restart with the OLD compose file (not docker-compose-build.yaml)
docker compose up -d
```

The backup directory (`./backup/orcpub`) is never modified — you can re-attempt
the restore as many times as needed. Just clear `./data/*` and restart the Pro
transactor before each attempt.

## Cleanup

Once you've confirmed the migration is successful:

```bash
# Remove the Free database files (no longer needed)
rm -rf ./data.free-backup

# Optionally remove the backup (or keep as insurance)
rm -rf ./backup
```

## How the Migration Tools Work

### Bare metal (`scripts/migrate-db.sh`)

Wraps `bin/datomic` CLI commands directly. Auto-selects the correct Datomic
distribution based on the URI protocol — Free for backup, Pro for
restore/verify/list. For backup, it searches `lib/` for an extracted Free
distribution, prompts the user if not found (interactive mode), and falls
back to extracting the bundled tarball (`lib/datomic-free-0.9.5703.tar.gz`).
Supports `backup`, `restore`, `verify`, `list`, and `full` (guided) commands.

```
  Free transactor (running)         Pro transactor (running)
  ┌──────────────────┐              ┌──────────────────┐
  │  port 4334       │              │  port 4334       │
  └────────┬─────────┘              └────────┬─────────┘
           │                                 │
  bin/datomic backup-db             bin/datomic restore-db
  datomic:free://...                datomic:dev://...
           │                                 │
           ▼                                 ▼
      file:./backup/orcpub  ────────>  file:./backup/orcpub
```

### Docker (`docker-migrate.sh`)

Runs `bin/datomic` inside temporary Docker containers without modifying any
running containers:

```
 Old Stack (running)                     New Stack (running)
┌─────────────┐  ┌────────────┐    ┌─────────────┐  ┌────────────┐
│  orcpub      │  │  datomic   │    │  orcpub      │  │  datomic   │
│  (Free peer) │  │  (Free tx) │    │  (Pro peer)  │  │  (Pro tx)  │
└──────────────┘  └──────┬─────┘    └──────────────┘  └──────┬─────┘
                         │                                    │
                  Compose Network                      Compose Network
                         │                                    │
                ┌────────┴─────────┐              ┌───────────┴────────┐
                │  Temp container   │              │  Temp container     │
                │  (old datomic img)│              │  (new datomic img)  │
                │  backup-db ──────►│ ./backup/   │◄─────── restore-db │
                └──────────────────┘  (bind mount) └────────────────────┘
```

Each phase launches a **temporary container** (`docker run --rm`) that:

- **Uses the correct datomic image** — the old image has the Free peer library
  for backup, the new image has the Pro peer library for restore
- **Joins the Compose network** — can reach the `datomic` container by hostname
- **Bind-mounts `./backup/`** — the backup directory is on the host filesystem,
  not inside the container overlay. 20GB+ databases write directly to the host
  disk without any `docker cp` bottleneck or container storage limits
- **Runs `bin/datomic`** from the distribution inside the image

The temporary container exits after the operation. No running containers are
modified, stopped, or restarted during backup or restore.

## Performance Expectations

All numbers are rough estimates. Actual performance depends on disk speed, CPU,
available memory, and database complexity (number of indexes, attribute count).

| Database Size | Backup Time | Restore Time | Disk Space Needed |
|--------------|-------------|--------------|-------------------|
| < 1 GB | 1-5 min | 1-5 min | ~3 GB peak |
| 1-10 GB | 5-30 min | 5-30 min | ~30 GB peak |
| 10-25 GB | 30-90 min | 30-90 min | ~75 GB peak |
| 25-50 GB | 1-3 hours | 1-3 hours | ~150 GB peak |

**Peak disk space** = old `./data` + `./backup` + new `./data`. All three
coexist during migration. The old data can be deleted after verification.

### Memory

The `bin/datomic` CLI loads index segments into memory during backup/restore.
The default JVM heap (1GB, set in `bin/run`) should be sufficient for most
databases. For very large databases (50GB+), increase the heap by setting
`DATOMIC_JAVA_OPTS` before running:

```bash
export DATOMIC_JAVA_OPTS="-Xmx4g"
./scripts/migrate-db.sh backup
```

For Docker, pass it as an environment variable to the container (edit
`docker-migrate.sh`'s `run_datomic_cli` function).

## Troubleshooting

### "Cannot connect to Datomic"

**Bare metal**: Ensure the transactor is running and the URI is correct.
Check `DATOMIC_URL` in `.env`.

**Docker**: The migration container must be on the same Docker Compose network as
the datomic container. The script auto-detects this by inspecting running containers.

For unusual network setups, use `--old-uri` or `--new-uri` (**options must come
before the command word**):

```bash
./docker-migrate.sh --old-uri "datomic:free://datomic:4334/orcpub" backup
./docker-migrate.sh --new-uri "datomic:dev://datomic:4334/orcpub?password=mypass" restore
```

### "Target database already exists"

`restore-db` requires the target database to not already exist. If the new stack
was started and the application already created the database:

1. Stop the new stack: `docker compose down`
2. Clear the Pro data: `rm -rf ./data/*`
3. Restart: `docker compose up -d`
4. Wait for healthy, then retry restore

### Backup takes too long

Monitor progress by checking the backup directory size:

```bash
du -sh ./backup/orcpub
```

The backup writes incrementally — partial progress is visible as files appear.

### Partial backup (interrupted)

If backup is interrupted (Ctrl+C, killed, disk full), the backup directory will
be incomplete. Delete it and re-run. Backups are not resumable mid-segment —
the incremental feature only helps when the previous backup completed successfully.

### Data discrepancy after restore

Use `bin/datomic verify-backup` to check integrity, then log in and verify
your data visually. The key things to check:

- Characters load correctly
- Homebrew content is accessible
- User accounts can log in

If something looks wrong, delete `./data`, re-run restore from the same backup
(backups are reusable).

### Out of disk space

The backup needs roughly as much free space as the database. Peak disk usage
during migration is: old `./data` + `./backup` + new `./data`.

```
./data              ~20GB  (old Free database)
./backup/orcpub     ~20GB  (portable backup)
./data.free-backup  ~20GB  (renamed after swap, can delete once verified)
```

Peak: up to ~60GB for a 20GB database.

## FAQ

### Can I use the Pro transactor with the Free peer library?

No. The peer library and transactor must match:

- `com.datomic/datomic-free` (Free peer) speaks `datomic:free://` only
- `com.datomic/peer` (Pro peer) speaks `datomic:dev://`, `datomic:sql://`, etc.

They use different transport protocols. A Free peer cannot connect to a Pro
transactor, and vice versa. The peer library choice follows from the transactor
choice — it's not independently configurable.

### What happens after migration?

Once verified, the migrated database works identically to a database that was
always on Pro. There is no ongoing migration state, no compatibility mode, no
legacy flags. The Pro transactor reads and writes the data using its own storage
engine, and the Pro peer library talks to it using the Pro transport protocol.

Going forward, `bin/datomic backup-db` and `restore-db` continue to work for
regular backups — the same mechanism used for migration is the recommended
backup strategy for production deployments.

## Migration Scripts

| Script | Environment | Purpose |
|--------|-------------|---------|
| `scripts/migrate-db.sh` | Bare metal | Primary migration tool, wraps `bin/datomic` CLI |
| `docker-migrate.sh` | Docker | Containerized wrapper, auto-detects images/networks |
| `docker/scripts/migrate-db.clj` | Either (optional) | DB-level stats verification (user/entity counts) |

## Related Documentation

- [datomic-pro.md](datomic-pro.md) — Code-level changes (dependency, URI, API)
- [../ENVIRONMENT.md](../ENVIRONMENT.md) — Environment variable reference
- [../../docker-setup.sh](../../docker-setup.sh) — Initial Docker setup
- [../../docker-user.sh](../../docker-user.sh) — User management after migration
