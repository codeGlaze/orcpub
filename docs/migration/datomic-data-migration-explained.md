# How Datomic Data Migration Works

This document explains **what happens to your data** during a Datomic Free to Pro
migration — not just the commands to run (see
[datomic-data-migration.md](datomic-data-migration.md) for that), but the
underlying mechanics, what's preserved, what changes, and what to expect.

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

## What backup-db and restore-db Actually Do

These are **CLI commands** provided by the Datomic distribution at `bin/datomic`.
They are not part of the Peer API (`datomic.api`) — they live in the
`datomic.backup-cli` namespace and are invoked via the command line:

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

**For a 20GB database**: `backup-db` reads every segment through the peer
connection. This is CPU and I/O bound on the transactor side. Expect roughly
1-3 GB/minute depending on hardware and database complexity (heavily indexed
databases are slower because there are more segments to serialize).

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

### verify-backup

```bash
bin/datomic verify-backup "file:./backup/orcpub" true <t>
```

Reads every segment in the backup and confirms it's intact. Use `list-backups`
to find available `t` values:

```bash
bin/datomic list-backups "file:./backup/orcpub"
```

### Peer library matching

**Critical**: The `bin/datomic` CLI uses the peer library from its own
distribution. The Free distribution's CLI can only connect to Free transactors
(`datomic:free://`), and the Pro distribution's CLI can only connect to Pro
transactors (`datomic:dev://`, etc.).

This means:
- **Backup** must use the **Free** distribution's `bin/datomic`
- **Restore** must use the **Pro** distribution's `bin/datomic`

The migration scripts handle this automatically — see "How the Migration Tools
Work" below.

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

## How the Migration Tools Work

There are two migration scripts — bare-metal and Docker. Both use the same
underlying `bin/datomic` CLI commands.

### Bare metal (`scripts/migrate-db.sh`)

Wraps `bin/datomic` directly. Auto-selects the correct distribution based on
the URI protocol: Free for `datomic:free://` (backup), Pro for `datomic:dev://`
(restore/verify). For backup, searches `lib/` for an extracted Free
distribution, prompts interactively if not found, and falls back to
extracting the bundled tarball (`lib/datomic-free-0.9.5703.tar.gz`).

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

## What Can Go Wrong

### "Target database already exists"

`restore-db` calls `create-database` internally. If the new stack auto-created
the database on first boot (because the application connected and triggered
schema installation), the restore will fail.

**Fix**: Stop the new stack, delete `./data/*`, restart, then retry restore
before the application connects. The `full` command handles this by clearing
`./data` during the stack swap.

### Backup succeeds but restore fails with connection error

The restore must reach the **new** transactor. For Docker, the temp container
must be on the same Compose network. The script auto-detects the network from
running containers. For unusual setups, use `--new-uri` to specify the
connection URI explicitly.

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

## The Free Peer vs Pro Peer Question

A common question: "Can I use the Pro transactor with the Free peer library
to avoid migration?"

No. The peer library and transactor must match:

- `com.datomic/datomic-free` (Free peer) speaks `datomic:free://` only
- `com.datomic/peer` (Pro peer) speaks `datomic:dev://`, `datomic:sql://`, etc.

They use different transport protocols. A Free peer cannot connect to a Pro
transactor, and vice versa. The peer library choice follows from the transactor
choice — it's not independently configurable.

## After Migration

Once verified, the migrated database works identically to a database that was
always on Pro. There is no ongoing migration state, no compatibility mode, no
legacy flags. The Pro transactor reads and writes the data using its own storage
engine, and the Pro peer library talks to it using the Pro transport protocol.

Going forward, `bin/datomic backup-db` and `restore-db` continue to work for
regular backups — the same mechanism used for migration is the recommended
backup strategy for production deployments.

## Related

- [datomic-data-migration.md](datomic-data-migration.md) — Step-by-step runbook
- [datomic-pro.md](datomic-pro.md) — Code-level changes (dependency, URI, API)
- [../../scripts/migrate-db.sh](../../scripts/migrate-db.sh) — Bare-metal migration script
- [../../docker-migrate.sh](../../docker-migrate.sh) — Docker migration wrapper
