# Datomic Data Migration — Free to Pro

Existing self-hosted deployments have a Datomic Free database in `./data` that
must be migrated before upgrading to the new Java 21 / Datomic Pro Docker stack.
The storage protocols (`datomic:free://` vs `datomic:dev://`) are incompatible at
the file level — the Pro transactor cannot read Free storage, and vice versa.

## Why Migration Is Needed

Datomic Free and Datomic Pro use different storage protocols:

| | Datomic Free | Datomic Pro (dev) |
|---|---|---|
| URI scheme | `datomic:free://` | `datomic:dev://` |
| Storage engine | H2 (different on-disk schema) | H2 (different on-disk schema) |
| Transport | Custom (pre-1.0) | ActiveMQ Artemis |
| Authentication | None | Password-based |
| Java support | Java 8 only | Java 11, 17, 21 |

Despite both using H2, the Pro transactor cannot open Free's H2 files — same
data model, incompatible storage.

## How It Works

The migration uses Datomic's `bin/datomic` CLI — **not** the Peer API
(`datomic.api`). `backup-db` creates a portable, protocol-independent backup
from the running Free transactor. `restore-db` writes it into a new Pro database.

```
  Free transactor (running)         Pro transactor (running)
  ┌──────────────────┐              ┌──────────────────┐
  │  port 4334       │              │  port 4334       │
  └────────┬─────────┘              └────────┬─────────┘
           │                                 │
  bin/datomic backup-db             bin/datomic restore-db
  (Free distribution)               (Pro distribution)
           │                                 │
           ▼                                 ▼
      file:./backup/orcpub  ────────>  file:./backup/orcpub
```

**Peer library matching is critical**: the Free CLI can only connect to Free
transactors, the Pro CLI can only connect to Pro transactors. Backup must use the
Free distribution's `bin/datomic`; restore must use Pro's. The migration scripts
handle this automatically.

The backup format is storage-protocol-independent and the backup/restore cycle
is **lossless** — all datoms, full transaction history, timestamps, entity IDs,
and schema are preserved exactly. Only the storage layer changes.

**Commands reference:**

```bash
bin/datomic backup-db  <from-db-uri> <to-backup-uri>    # backup (Free or Pro)
bin/datomic restore-db <from-backup-uri> <to-db-uri>     # restore (Free or Pro)
bin/datomic list-backups <backup-uri>                     # list backup points
bin/datomic verify-backup <backup-uri> true <t>           # verify (Pro only)
```

## Quick Start (Bare Metal)

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

## Step-by-Step (Bare Metal)

### Prerequisites

- Datomic Free transactor running with your existing data
- `.env` file with `DATOMIC_URL` (pointing to the Free transactor) and `DATOMIC_PASSWORD`
- Datomic Pro installed (`lib/com/datomic/datomic-pro/...`)
- Enough disk space for the backup (see [Performance](#performance))

### Phase 1: Backup

With the Free transactor running:

```bash
./scripts/migrate-db.sh backup
```

The script detects `datomic:free://` in your `DATOMIC_URL` and looks for the
Free distribution in `lib/`. If it can't find one interactively, it will prompt
for the path — or press Enter to extract the bundled tarball
(`lib/datomic-free-0.9.5703.tar.gz`). To skip the prompt:

```bash
./scripts/migrate-db.sh --datomic-dir /path/to/datomic-free backup
```

### Phase 2: Swap Transactors

```bash
./scripts/stop.sh datomic
mv ./data ./data.free-backup
mkdir -p ./data
./scripts/start.sh datomic
```

Wait for the transactor to become healthy (port 4334).

### Phase 3: Restore

```bash
./scripts/migrate-db.sh restore "datomic:dev://localhost:4334/orcpub?password=${DATOMIC_PASSWORD}"
```

**The target database must not already exist.** If the application already created
it on first connect, delete `./data/*` and restart the transactor before restoring.

### Phase 4: Verify

```bash
./scripts/migrate-db.sh verify

# Optionally check DB-level stats (user/entity counts):
# java -cp target/orcpub.jar clojure.main docker/scripts/migrate-db.clj verify [db-uri]
```

Then log in and verify your data — characters load, homebrew content is
accessible, user accounts work.

## Step-by-Step (Docker)

### Prerequisites

- Old Docker stack running (`docker compose ps` shows healthy datomic + orcpub)
- `.env` file with correct `DATOMIC_PASSWORD`
- Enough disk space (see [Performance](#performance))
- New source code checked out (has `docker-compose-build.yaml` and migration scripts)

Each phase launches a **temporary container** (`docker run --rm`) on the Compose
network, bind-mounting `./backup/` for I/O. No running containers are modified.

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

### Phase 1: Backup

```bash
./docker-migrate.sh backup
```

### Phase 2: Swap Stacks

```bash
docker compose down
mv ./data ./data.free-backup
mkdir -p ./data
docker compose -f docker-compose-build.yaml build
docker compose -f docker-compose-build.yaml up -d
```

Wait for healthy: `docker compose ps`

### Phase 3: Restore

```bash
./docker-migrate.sh restore
```

URI is auto-detected from the running container, or constructed from
`DATOMIC_PASSWORD` in `.env`.

### Phase 4: Verify

```bash
./docker-migrate.sh verify
```

Test a login:

```bash
curl -sk -X POST https://localhost/login \
  -H "Content-Type: application/json" \
  -d '{"username":"youruser","password":"yourpass"}'
```

## Rollback

You can roll back at any point before deleting `./data.free-backup`:

**Bare metal:**

```bash
./scripts/stop.sh datomic
rm -rf ./data
mv ./data.free-backup ./data
./scripts/start.sh datomic
```

**Docker:**

```bash
docker compose down
rm -rf ./data
mv ./data.free-backup ./data
docker compose up -d      # OLD compose file, not docker-compose-build.yaml
```

The backup directory is never modified — you can re-attempt the restore as many
times as needed. Just clear `./data/*` and restart the transactor before each attempt.

## Cleanup

Once verified:

```bash
rm -rf ./data.free-backup   # old Free storage
rm -rf ./backup             # portable backup (or keep as insurance)
```

## Performance

Rough estimates — actual performance depends on disk speed, CPU, and database
complexity.

| Database Size | Backup/Restore Time | Peak Disk Space |
|--------------|---------------------|-----------------|
| < 1 GB | 1-5 min | ~3 GB |
| 1-10 GB | 5-30 min | ~30 GB |
| 10-25 GB | 30-90 min | ~75 GB |
| 25-50 GB | 1-3 hours | ~150 GB |

**Peak disk** = old `./data` + `./backup` + new `./data` (all coexist during
migration). Monitor progress with `du -sh ./backup/orcpub`.

**Memory**: The default 1GB JVM heap (`bin/run`) suffices for most databases.
For 50GB+, increase with `DATOMIC_JAVA_OPTS="-Xmx4g"` before running.

## Troubleshooting

### "Cannot connect to Datomic"

**Bare metal**: Ensure the transactor is running and `DATOMIC_URL` in `.env` is
correct.

**Docker**: The temp container must be on the same Compose network. The script
auto-detects this. For unusual setups, use `--old-uri` or `--new-uri` (**before
the command word**):

```bash
./docker-migrate.sh --old-uri "datomic:free://datomic:4334/orcpub" backup
./docker-migrate.sh --new-uri "datomic:dev://datomic:4334/orcpub?password=mypass" restore
```

### "Target database already exists"

`restore-db` requires the target database to not exist. If the app auto-created
it on first boot:

1. `docker compose down` (or stop the transactor)
2. `rm -rf ./data/*`
3. Restart, then retry restore

### `:restore/no-roots` on Windows (and `.DS_Store` on macOS)

Datomic's `restore-db` and `list-backups` discover restore points by reading the
`roots/` directory inside the backup. The underlying code (`backup.clj`) calls
`Long.parseLong()` on every filename in the directory without filtering non-numeric
entries or handling platform directory enumeration quirks. On Windows, the JVM's
directory listing can silently fail, causing `:restore/no-roots` even though the
backup is intact. On macOS, the same code crashes with `NumberFormatException` on
`.DS_Store` files ([Datomic/mbrainz-sample#10](https://github.com/Datomic/mbrainz-sample/issues/10)).

**Diagnosis**: Check that `backup/orcpub/roots/` contains a numeric file
(e.g., `168103969`). If it does, the backup is valid — the error is a platform
bug, not a data problem.

**Fix**: Pass the `t` value (the filename from `roots/`) explicitly as the third
argument to `restore-db`:

```bash
# Find the t value
dir backup\orcpub\roots\
# e.g., shows file "168103969"

# Pass it explicitly
bin\datomic restore-db "file:///C:/path/to/backup/orcpub" "datomic:dev://localhost:4334/orcpub?password=..." 168103969
```

The migration scripts (`scripts/migrate-db.sh` and `docker-migrate.sh`) auto-
discover the `t` value from the filesystem and pass it explicitly, so this is
handled automatically. If running `bin/datomic` directly on Windows, always pass
the explicit `t` value.

### Interrupted backup

Delete the incomplete backup directory and re-run. Backups are not resumable
mid-segment.

### Data discrepancy after restore

Run `./scripts/migrate-db.sh verify`, then log in and check: characters load,
homebrew content accessible, users can log in. If wrong, delete `./data`,
re-restore from the same backup.

## FAQ

### Can I use the Pro transactor with the Free peer library?

No. The peer library and transactor must match — Free peer speaks
`datomic:free://` only, Pro peer speaks `datomic:dev://` only. They use
different transport protocols and are not interchangeable.

### What happens after migration?

The migrated database works identically to one that was always on Pro. No
ongoing migration state or compatibility mode. `backup-db` and `restore-db`
continue to work for regular backups going forward.

## Scripts

| Script | Environment | Purpose |
|--------|-------------|---------|
| `scripts/migrate-db.sh` | Bare metal | Primary migration tool, wraps `bin/datomic` CLI |
| `docker-migrate.sh` | Docker | Containerized wrapper, auto-detects images/networks |
| `docker/scripts/migrate-db.clj` | Either (optional) | DB-level stats verification (user/entity counts) |

## Related

- [datomic-pro.md](datomic-pro.md) — Code-level changes (dependency, URI, API)
- [../ENVIRONMENT.md](../ENVIRONMENT.md) — Environment variable reference
- [../../docker-setup.sh](../../docker-setup.sh) — Initial Docker setup
- [../../docker-user.sh](../../docker-user.sh) — User management after migration
