#!/usr/bin/env bash
# =============================================================================
# backup-lib.sh - Datomic backup and restore functions shared by Docker scripts
# =============================================================================
# Sourced by `run` (--backup, --restore, --verify-backup) and docker-migrate.sh.
# Wraps `bin/datomic backup-db | restore-db | verify-backup | list-backups`, run in a
# throwaway container of the transactor image on the Compose network.
#
# The sourcing script must define: info, warn, error (each prints one line), and
# must run from the repo root (where docker-compose.yaml is).
#
# Layout: a backup folder DIR holds the Datomic backup in DIR/orcpub/ (roots/, ...).
# Repeating a backup into the same DIR adds only what is new.
# =============================================================================

[[ -n "${_ORCPUB_BACKUP_LIB_LOADED:-}" ]] && return
_ORCPUB_BACKUP_LIB_LOADED=1

# Name of the backup inside a backup folder, and of the database it holds.
BK_NAME="orcpub"

# bk_latest_t <backup-name-dir>
#   Prints the highest restore point (t) found in <backup-name-dir>/roots (root file names
#   are the t values). A filesystem scan, because list-backups fails on some platforms
#   (docs/migration/datomic-data-migration.md); passing t explicitly avoids that path.
#   Exit: 0 and prints t; 1 when there is none.
bk_latest_t() {
  local roots_dir="$1/roots" latest=""
  if [[ -d "$roots_dir" ]]; then
    # shellcheck disable=SC2010  # names are checked to be digits only
    latest=$(ls -1 "$roots_dir" 2>/dev/null | grep -E '^[0-9]+$' | sort -n | tail -1 || true)
  fi
  [[ -n "$latest" ]] || return 1
  echo "$latest"
}

# bk_datomic_cid
#   Prints the container id of the running Compose "datomic" service. Exit: 1, silent, if none.
bk_datomic_cid() {
  local cid
  cid=$(docker compose ps -q datomic 2>/dev/null | head -1 || true)
  [[ -n "$cid" ]] || return 1
  echo "$cid"
}

# bk_network
#   Prints the Compose network of the running datomic container.
#   Exit: 1 with an error message when the service is not running.
bk_network() {
  local cid
  if ! cid=$(bk_datomic_cid); then
    error "The datomic service is not running. Start it with: docker compose up -d datomic"
    return 1
  fi
  docker inspect "$cid" \
    -f '{{range $k,$v := .NetworkSettings.Networks}}{{println $k}}{{end}}' | head -1
}

# bk_image
#   Prints the transactor image: the running datomic container's, else DATOMIC_IMAGE
#   (default orcpub-datomic).
bk_image() {
  local cid
  if cid=$(bk_datomic_cid); then
    docker inspect "$cid" -f '{{.Config.Image}}'
  else
    echo "${DATOMIC_IMAGE:-orcpub-datomic}"
  fi
}

# bk_db_uri [db-name]
#   Prints the datomic:dev URI of database [db-name] (default orcpub; "*" lists all), with the password the datomic container holds
#   (read inside the container, so it is never on a host command line).
#   Exit: 1 with an error message when the service is not running.
bk_db_uri() {
  local cid pw
  if ! cid=$(bk_datomic_cid); then
    error "The datomic service is not running. Start it with: docker compose up -d datomic"
    return 1
  fi
  pw=$(docker exec "$cid" sh -c 'printf %s "$DATOMIC_PASSWORD"' 2>/dev/null || true)
  if [[ -z "$pw" ]]; then
    error "Could not read DATOMIC_PASSWORD from the datomic container."
    return 1
  fi
  printf 'datomic:dev://datomic:4334/%s?password=%s' "${1:-$BK_NAME}" "$pw"
}

# bk_run_cli <image> <backup-folder> <bin/datomic args...>
#   Runs `bin/datomic <args>` in a temporary container of <image> on the Compose network,
#   with <backup-folder> mounted at /backup (so use file:/backup/orcpub for the backup).
#   Any argument starting with "datomic:" (a database URI, which carries the password) is
#   handed over as an environment variable, so it is not in the host's process list.
#   Runs as the owner of <backup-folder> (the transactor container hands ./backups to its own
#   user, which the host user may not be able to write).
#   BACKUP_JAVA_OPTS (JVM flags, default "-Xms256m -Xmx1g") sizes the JVM.
#   Exit: that of bin/datomic; 1 if the network is missing.
bk_run_cli() {
  local image="$1" dir="$2"; shift 2
  local network a n=0 snippet=""
  local -a envs=()
  network=$(bk_network) || return 1
  for a in "$@"; do
    case "$a" in
      datomic:*)
        n=$((n + 1))
        export "BK_URI_$n=$a"
        envs+=(-e "BK_URI_$n")
        snippet+=" \"\$BK_URI_$n\"" ;;
      *) snippet+=" $(printf '%q' "$a")" ;;
    esac
  done
  export DATOMIC_JAVA_OPTS="${BACKUP_JAVA_OPTS:--Xms256m -Xmx1g}"
  # The logging set-up lines Datomic prints at start are dropped; its own messages stay. The
  # subshell keeps docker's exit status (not grep's) whatever `set -e` is in force.
  local rc=0
  (
    set +e
    docker run --rm --network="$network" --user "$(stat -c %u:%g "$dir")" \
      --tmpfs /datomic/log:rw,mode=1777 -e HOME=/tmp -e DATOMIC_JAVA_OPTS "${envs[@]+"${envs[@]}"}" \
      -v "${dir}:/backup" "$image" sh -c "exec bin/datomic${snippet}" 2>&1 \
      | grep --line-buffered -Ev '^[0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]+ \|-|^$'
    exit "${PIPESTATUS[0]}"
  ) || rc=$?
  return "$rc"
}

# bk_db_exists
#   Asks the transactor whether the database "orcpub" exists (a short peer program in the
#   transactor image: bin/datomic has no list-databases).
#   Exit: 0 it exists; 1 it does not; 2 that could not be told (transactor unreachable).
bk_db_exists() {
  local image uri network out
  image=$(bk_image)
  uri=$(bk_db_uri '*') || return 2
  network=$(bk_network) || return 2
  export BK_URI_1="$uri"
  out=$(docker run --rm --network="$network" --user "$(id -u):$(id -g)" -e HOME=/tmp -e BK_URI_1 \
    "$image" sh -c 'exec java -Xms64m -Xmx256m -cp "$(bin/classpath)" clojure.main -e \
    "(require (quote [datomic.api :as d])) (println (if (some #{\"orcpub\"} (d/get-database-names (System/getenv \"BK_URI_1\"))) \"BK-EXISTS\" \"BK-ABSENT\")) (d/shutdown true)"' 2>/dev/null) || return 2
  case "$out" in
    *BK-EXISTS*) return 0 ;;
    *BK-ABSENT*) return 1 ;;
    *) return 2 ;;
  esac
}

# bk_prepare_dir <folder>
#   Creates <folder> if needed and checks a container running as its owner can write there
#   (the host user may not be able to: the transactor container owns ./backups).
#   Prints the absolute path. Exit: 0 writable; 1 with an error message.
bk_prepare_dir() {
  local dir="$1" image
  mkdir -p "$dir" 2>/dev/null || [[ -d "$dir" ]] || { error "Cannot create backup folder: $dir"; return 1; }
  dir=$(cd "$dir" && pwd)
  image=$(bk_image)
  if ! docker run --rm --user "$(stat -c %u:%g "$dir")" -v "$dir:/backup" --entrypoint sh "$image" \
      -c 'touch /backup/.write-test && rm -f /backup/.write-test' >/dev/null 2>&1; then
    error "Backup folder is not writable: $dir"; return 1
  fi
  echo "$dir"
}

# bk_backup <backup-folder>
#   Live backup: `backup-db` of the running database into <backup-folder>/orcpub. Repeating it
#   into the same folder is incremental. #   Exit: 0 done; 1 on any failure (an error message says why).
bk_backup() {
  local dir="$1" image uri
  dir=$(bk_prepare_dir "$dir") || return 1
  image=$(bk_image)
  uri=$(bk_db_uri) || return 1
  info "Backing up the database to $dir/$BK_NAME ($( [[ -d "$dir/$BK_NAME/roots" ]] && echo 'adding to the existing backup' || echo 'first backup'))"
  if ! bk_run_cli "$image" "$dir" backup-db "$uri" "file:/backup/$BK_NAME"; then
    error "backup-db failed: the backup in $dir is not complete. Is the transactor running and reachable?"
    return 1
  fi
  if ! bk_latest_t "$dir/$BK_NAME" >/dev/null; then
    error "backup-db reported success but $dir/$BK_NAME has no restore point."
    return 1
  fi
  info "Backup complete: $dir/$BK_NAME (restore point t=$(bk_latest_t "$dir/$BK_NAME"))"
}

# bk_verify <backup-folder>
#   Checks every segment of the latest restore point in <backup-folder>/orcpub (verify-backup).
#   Exit: 0 verified; 1 missing, empty, corrupt or unreadable.
bk_verify() {
  local dir="$1" image t out rc
  if [[ ! -d "$dir/$BK_NAME" ]]; then
    error "No backup found at $dir/$BK_NAME"; return 1
  fi
  dir=$(cd "$dir" && pwd)
  if ! t=$(bk_latest_t "$dir/$BK_NAME"); then
    error "$dir/$BK_NAME has no restore point (empty or incomplete backup)."; return 1
  fi
  image=$(bk_image)
  info "Verifying $dir/$BK_NAME at t=$t (reads every segment)"
  out=$(mktemp)
  bk_run_cli "$image" "$dir" verify-backup "file:/backup/$BK_NAME" true "$t" | tee "$out"
  rc=${PIPESTATUS[0]}
  # verify-backup exits 0 even when it errors, so the result line itself is checked.
  if [[ $rc -ne 0 ]] || ! grep -q ':event :verify-backup/complete' "$out" \
      || ! grep -q ':missing-segments #{}' "$out" || ! grep -q ':unreadable-segments ()' "$out"; then
    rm -f "$out"
    error "Backup verification failed: do not rely on $dir."; return 1
  fi
  rm -f "$out"
  info "Backup verified (t=$t)."
}

# bk_restore <backup-folder>
#   Restores <backup-folder>/orcpub into an empty database on the running transactor.
#   Refuses, with what to do, when the database "orcpub" already exists.
#   Exit: 0 restored; 1 on any failure or refusal.
bk_restore() {
  local dir="$1" image uri t rc=0
  if [[ ! -d "$dir/$BK_NAME" ]]; then
    error "No backup found at $dir/$BK_NAME"; return 1
  fi
  dir=$(cd "$dir" && pwd)
  if ! t=$(bk_latest_t "$dir/$BK_NAME"); then
    error "$dir/$BK_NAME has no restore point (empty or incomplete backup)."; return 1
  fi
  bk_db_exists || rc=$?
  case "$rc" in
    0) error "The database '$BK_NAME' already exists on this transactor, so nothing was restored."
       error "A restore needs an empty database. To replace it: stop the app (docker compose stop orcpub),"
       error "back up what is there first (./run --backup), stop the stack, move ./data aside,"
       error "start only the transactor (docker compose up -d datomic), then run this restore again."
       return 1 ;;
    1) ;;
    *) error "Could not reach the transactor to check for an existing database."; return 1 ;;
  esac
  image=$(bk_image)
  uri=$(bk_db_uri) || return 1
  info "Restoring $dir/$BK_NAME at t=$t"
  if ! bk_run_cli "$image" "$dir" restore-db "file:/backup/$BK_NAME" "$uri" "$t"; then
    error "restore-db failed: the database may be incomplete. Stop the stack, move ./data aside and try again."
    return 1
  fi
  info "Restore complete (t=$t). Start the app: docker compose up -d"
}

# bk_offline_backup <backup-folder>
#   Offline fallback: stops the stack, copies ./data to <backup-folder>/offline-<UTC date-time>/data,
#   starts the services that were running again (also when the copy failed). Copies run in the transactor image as
#   root, so ownership of the files is kept.
#   Exit: 0 copied and stack restarted; 1 otherwise.
bk_offline_backup() {
  local dir="$1" image stamp dest services rc=0
  if [[ ! -d data ]]; then
    error "No ./data folder here: nothing to copy (a Swarm stack keeps it in the orcpub_data volume)."
    return 1
  fi
  dir=$(bk_prepare_dir "$dir") || return 1
  image=$(bk_image)
  stamp=$(date -u +%Y-%m-%dT%H%M%SZ)
  dest="$dir/offline-$stamp"
  services=$(docker compose ps --services --status running 2>/dev/null | tr '\n' ' ')
  info "Stopping the stack"
  docker compose down || { error "Could not stop the stack; no copy made."; return 1; }
  docker run --rm --entrypoint sh -v "$PWD/data:/src:ro" -v "$dir:/dst" "$image" -c \
    'mkdir -p "/dst/$1" && cp -a /src/. "/dst/$1/" && touch "/dst/$1/.orcpub-offline-backup"' sh "offline-$stamp" || rc=1
  info "Starting the stack"
  # shellcheck disable=SC2086  # $services is a list of service names
  docker compose up -d $services || { error "The stack did not start again: run docker compose up -d."; rc=1; }
  if [[ $rc -ne 0 ]]; then
    error "Offline backup failed; $dest is not complete."; return 1
  fi
  info "Offline backup complete: $dest"
}

# bk_offline_restore <offline-backup-folder>
#   Puts an offline backup back: stops the stack, moves ./data to ./data.before-restore-<UTC stamp>,
#   copies the backup in, starts the services that were running. Nothing is deleted.
#   Exit: 0 restored; 1 otherwise (./data is put back if the copy failed).
bk_offline_restore() {
  local src="$1" image stamp aside services
  if [[ ! -f "$src/.orcpub-offline-backup" ]]; then
    error "$src is not an offline backup (no .orcpub-offline-backup marker)."; return 1
  fi
  src=$(cd "$src" && pwd)
  image=$(bk_image)
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  aside="data.before-restore-$stamp"
  services=$(docker compose ps --services --status running 2>/dev/null | tr '\n' ' ')
  info "Stopping the stack"
  docker compose down || { error "Could not stop the stack; nothing restored."; return 1; }
  [[ -d data ]] && mv data "$aside"
  mkdir -p data
  if ! docker run --rm --entrypoint sh -v "$src:/src:ro" -v "$PWD/data:/dst" "$image" \
      -c 'cp -a /src/. /dst/ && rm -f /dst/.orcpub-offline-backup'; then
    error "Copy failed: putting ./data back."
    docker run --rm --entrypoint sh -v "$PWD/data:/dst" "$image" -c 'rm -rf /dst/* /dst/.[!.]*' || true
    rmdir data 2>/dev/null; [[ -d "$aside" ]] && mv "$aside" data
    return 1
  fi
  info "Starting the stack"
  # shellcheck disable=SC2086  # $services is a list of service names
  docker compose up -d $services || { error "The stack did not start: run docker compose up -d."; return 1; }
  info "Offline restore complete. The previous data is in ./$aside (delete it when satisfied)."
}
