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

- **The transactor's password can come from a Docker secret** (`/run/secrets/datomic_password`, which
  `deploy/start.sh` prefers), and `docker exec` does not see start.sh's export: read the secret
  first, the environment second (`bk_db_uri`).
- **Datomic needs its transactor restarted after a restore** before peers connect; `bk_restore` does it.
  A transactor that was just started may not answer yet: the restore waits up to two minutes.
- **Backups from older `docker-migrate.sh` runs are root-owned** (its CLI ran as root): `bk_prepare_dir`
  hands `<folder>/orcpub` to the folder's owner once, so the CLI can run as that owner and new files stay theirs.
- **`.env` values must be decoded** (quotes, `$NAME`) the way Compose does: `read_env_val` in `run`
  uses `load_env`; a raw `grep | cut` kept the quotes on paths and passwords.

## What was tested (isolated stack, 5k entities)

Live backup and restore into a fresh stack (count and checksum equal); backup during continuous
writes (restores to a whole-batch t); incremental second run (5 new segments, 57 skipped); offline
copy and restore; failure cases (transactor down or paused, missing, corrupt or truncated backup,
unwritable folder) all exit 1 with a message; restore over an existing database refused.
Not tested: killing the transactor mid-backup, Swarm, large databases.

**#89 review fixes, live (2026-11-18):** a wrong secret fails the backup and the right one passes; a
root-owned backup takes a new restore point; offline backup of a stopped stack leaves it stopped; a
failed move of `./data` exits 1 with `./data` unchanged; a quoted `BACKUP_DIR` with a space lands in
the right folder; restore right after starting the transactor restores and restarts it, same
fingerprint. Follow-up the same day: the wait, with the transactor paused 25 s mid-restore (one
"Waiting" line, same fingerprint), and the hand-back of a root-owned backup (nothing left foreign-owned).
