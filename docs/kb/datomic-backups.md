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

## CodeRabbit round three (2026-11-18, #89)

- Offline backup and restore hold `.orcpub-offline.lock` in the project folder, so two never overlap. A run killed with `kill -9` leaves it: remove the folder by hand.
- Offline paths inside `./data` are refused before the stack stops: a backup there copies itself, and a restore source there is moved aside with `./data`.
- `verify-backup` reads only the file URI, so it runs without the transactor or the Compose network.
- The folder owner is read with GNU `stat -c` or BSD `stat -f` (macOS).
- A `.env` value that refers to itself (`DATOMIC_PASSWORD=${DATOMIC_PASSWORD:-x}`) resolves against the shell, as in Compose; `run`'s reader checks whether the file sets the key separately from what it resolves to.

## Datomic 1.0.7482 to 1.0.7745 (verified 2026-11-18, #89)

- A `dev`-storage database written by 1.0.7482 opens under 1.0.7745 unchanged: the same row count, checksum and `t` (5000 rows, t=5510). It then took 500 new rows, and `./run --backup` and `--verify-backup` passed at t=6011. Upgrading needs no migration step.
- The transactor zip still comes from `datomic-pro-downloads.s3.amazonaws.com/<version>/datomic-pro-<version>.zip`, free with no login; the Dockerfile fetches it from `DATOMIC_VERSION`. The peer is on Maven Central (`com.datomic/peer`).
- The transactor zip bundles its own Netty (4.1.131 in 1.0.7745). `project.clj`'s `:managed-dependencies` pin Netty only for the app's peer.
- The live site stores Datomic in Postgres (`plan-lookup-indexes.md`); `./run --backup` assumes `dev` storage.
- Test trap: a check that compares two fingerprints must fail when either is empty. A script that sent the peer's errors to /dev/null reported two blank results as "same data".
