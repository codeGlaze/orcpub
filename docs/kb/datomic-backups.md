# Datomic backups: how they work here and the traps

The admin commands are `./run --backup`, `--restore`, `--verify-backup` and `--backup --offline`
(instructions: `docs/DOCKER.md`, "Backups"). The logic is in `scripts/backup-lib.sh`, shared with
`docker-migrate.sh`. Built on `feature/inventory-kits`, 2026-11-17.

## Traps (verified)

- **`bin/datomic verify-backup` exits 0 even when it throws.** The old `docker-migrate.sh verify`
  passed it a malformed t value, got a NumberFormatException, and printed "verification passed".
  `bk_verify` requires the `:verify-backup/complete` line with no missing or unreadable segments.
- **Take the restore point from the backup's `roots/` folder**, not `list-backups`, which parses
  every filename as a number and can fail.
- **The transactor container chowns a bind-mounted `./backups` to its own uid**, so later host
  writes fail. The lib runs the Datomic CLI container as the folder's owner and does offline copies
  inside a container.
- **`bin/datomic` has no `list-databases`.** The "database already exists" check runs a short peer
  program in the transactor image.
- **A function that pipes docker output through grep returns grep's exit status**, which hid
  backup failures; wrap it so docker's status is returned.
- **`docker compose up -d` after an offline copy tries to build the app image** on a host that
  never built it: restart only the services that were running.
- Non-root Datomic logs a FileNotFound stack trace for `/datomic/log`: a tmpfs there silences it.

## What was tested (isolated stack, 5k entities)

Live backup and restore into a fresh stack (count and checksum equal); backup during continuous
writes (restores to a whole-batch t); incremental second run (5 new segments, 57 skipped); offline
copy and restore; failure cases (transactor down or paused, missing, corrupt or truncated backup,
unwritable folder) all exit 1 with a message; restore over an existing database refused.
Not tested: killing the transactor mid-backup, Swarm, large databases.
