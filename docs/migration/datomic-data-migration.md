# Datomic Data Migration — Free to Pro

Existing self-hosted deployments have a Datomic Free database in `./data` that
must be migrated before upgrading to the new Java 21 / Datomic Pro Docker stack.
The storage protocols (`datomic:free://` vs `datomic:dev://`) are incompatible at
the file level — the Pro transactor cannot read Free storage, and vice versa.

## How It Works

The migration uses Datomic's `bin/datomic` CLI tools:

1. **`backup-db`** connects to the running transactor and writes a portable,
   protocol-independent backup directory
2. **`restore-db`** reads the backup and writes it into a fresh database using
   the target storage protocol

The backup format is the same regardless of storage protocol, so this works across
any Datomic storage transition (Free → Pro, Pro dev → Pro SQL, etc.).

For an in-depth explanation of what happens to your data, what's preserved, what
changes, performance expectations, and failure modes, see
[datomic-data-migration-explained.md](datomic-data-migration-explained.md).

## Quick Start (Bare Metal)

The script auto-selects the correct Datomic distribution for each phase:
backup uses the bundled Free distribution (`lib/datomic-free-0.9.5703.tar.gz`),
restore and verify use the installed Pro distribution.

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

### Out of disk space

The backup needs roughly as much free space as the database. Peak disk usage
during migration is: old `./data` + `./backup` + new `./data`.

```
./data              ~20GB  (old Free database)
./backup/orcpub     ~20GB  (portable backup)
./data.free-backup  ~20GB  (renamed after swap, can delete once verified)
```

Peak: up to ~60GB for a 20GB database.

## How the Migration Tools Work

### Bare metal (`scripts/migrate-db.sh`)

Wraps `bin/datomic` CLI commands directly. Auto-selects the correct Datomic
distribution based on the URI protocol — Free for backup, Pro for
restore/verify/list. For backup, it searches `lib/` for an extracted Free
distribution, prompts the user if not found (interactive mode), and falls
back to extracting the bundled tarball (`lib/datomic-free-0.9.5703.tar.gz`).
Supports `backup`, `restore`, `verify`, `list`, and `full` (guided) commands.

### Docker (`docker-migrate.sh`)

Runs `bin/datomic` inside temporary Docker containers:

1. **Detects** the running datomic container's image and Compose network
2. **Launches** a temporary `docker run --rm` container that:
   - Uses the detected datomic image (has `bin/datomic` in the distribution)
   - Joins the Compose network (can reach `datomic` by hostname)
   - Bind-mounts `./backup` for I/O (handles any database size)
3. **Runs** `bin/datomic backup-db` or `restore-db` inside the container
4. The temporary container exits and is removed

This design means:
- No modifications to running containers
- No `docker cp` bottleneck (backup goes directly to host via bind mount)
- Works with any database size
- Each phase uses the correct image's peer library

## Migration Scripts

| Script | Environment | Purpose |
|--------|-------------|---------|
| `scripts/migrate-db.sh` | Bare metal | Primary migration tool, wraps `bin/datomic` CLI |
| `docker-migrate.sh` | Docker | Containerized wrapper, auto-detects images/networks |
| `docker/scripts/migrate-db.clj` | Docker (optional) | DB-level stats verification (user/entity counts) |

## Related Documentation

- [datomic-data-migration-explained.md](datomic-data-migration-explained.md) — In-depth mechanics
- [datomic-pro.md](datomic-pro.md) — Code-level changes (dependency, URI, API)
- [../ENVIRONMENT.md](../ENVIRONMENT.md) — Environment variable reference
- [../../docker-setup.sh](../../docker-setup.sh) — Initial Docker setup
- [../../docker-user.sh](../../docker-user.sh) — User management after migration
