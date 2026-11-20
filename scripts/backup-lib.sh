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
# Layout: live backups go to DIR/<YYYY-MM>/orcpub/ (roots/, ...), one folder per month. Repeating a
# backup in the same month adds only what is new; the first backup of a month starts a full one,
# and once it verifies, month folders beyond BACKUP_KEEP_MONTHS (default 3) are deleted. Offline
# copies are DIR/offline-<UTC stamp>/, the newest BACKUP_KEEP_OFFLINE (default 3) kept.
# Storage: DATOMIC_URL decides. A datomic:sql:// address (Postgres) is backed up and restored
# through that address; anything else is the Compose stack's dev storage. BACKUP_NETWORK names the
# Docker network the backup container joins, when the database is not on the datomic service's.
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

# bk_owner <path>
#   Prints <path>'s owner as uid:gid, with GNU stat (Linux) or BSD stat (macOS).
#   Exit: 1 with an error message when it cannot be read.
bk_owner() {
  local o
  o=$(stat -c %u:%g "$1" 2>/dev/null || stat -f %u:%g "$1" 2>/dev/null || true)
  if [[ ! "$o" =~ ^[0-9]+:[0-9]+$ ]]; then
    error "Could not read the owner of $1"; return 1
  fi
  echo "$o"
}

# bk_physical <path>
#   Prints <path> as an absolute path with symlinks resolved; a part that does not exist yet is
#   appended as written.
bk_physical() {
  local p="$1" rest=""
  while [[ ! -d "$p" ]]; do
    rest="/$(basename "$p")$rest"; p=$(dirname "$p")
  done
  printf '%s%s' "$(cd "$p" && pwd -P)" "$rest"
}

# bk_inside_data <path>
#   Exit: 0 when <path> is ./data or inside it (symlinks resolved); 1 otherwise.
bk_inside_data() {
  local p d
  [[ -d data ]] || return 1
  p=$(bk_physical "$1"); d=$(cd data && pwd -P)
  [[ "$p" == "$d" || "$p" == "$d"/* ]]
}

# bk_running_services
#   Prints the running Compose services, space-separated (empty when none run).
#   Exit: 1 with an error message when Docker cannot be asked.
bk_running_services() {
  local out
  if ! out=$(docker compose ps --services --status running); then
    error "Could not ask Docker which services are running; nothing was stopped."; return 1
  fi
  # shellcheck disable=SC2086  # word-splitting joins the names with spaces
  echo $out
}

# bk_start_services <services>
#   Starts the space-separated <services> again; says so and does nothing when the list is empty.
#   Exit: 0 started or nothing to start; 1 with an error message.
bk_start_services() {
  if [[ -z "${1// /}" ]]; then
    info "The stack was not running before, so it stays stopped."; return 0
  fi
  info "Starting the stack"
  # shellcheck disable=SC2086  # a list of service names
  docker compose up -d $1 || { error "The stack did not start again: run docker compose up -d."; return 1; }
}

# bk_locked <function> <args...>
#   Runs <function> holding .orcpub-offline.lock in the project folder, so two offline backups or
#   restores of one stack never overlap. Exit: that of <function>; 1 when the lock is held.
bk_locked() {
  local rc=0
  if ! mkdir .orcpub-offline.lock 2>/dev/null; then
    error "Another offline backup or restore is running here (.orcpub-offline.lock)."
    error "If none is, remove that folder and try again."
    return 1
  fi
  "$@" || rc=$?
  rmdir .orcpub-offline.lock 2>/dev/null || true
  return "$rc"
}

# bk_is_sql
#   Exit: 0 when DATOMIC_URL is a datomic:sql:// address (Postgres storage); 1 otherwise.
bk_is_sql() {
  [[ "${DATOMIC_URL:-}" == datomic:sql://* ]]
}

# bk_db_name
#   Prints the database name: the one in a datomic:sql:// DATOMIC_URL, else orcpub.
bk_db_name() {
  if bk_is_sql; then
    local rest="${DATOMIC_URL#datomic:sql://}"
    echo "${rest%%\?*}"
  else
    echo "$BK_NAME"
  fi
}

# bk_du_kb <path>
#   Prints the size of <path> in KB, read in a root container of the transactor image (backups
#   and ./data are owned by container users). Prints 0 when it cannot be read.
bk_du_kb() {
  local kb
  kb=$(docker run --rm -v "$1:/p:ro" --entrypoint du "$(bk_image)" -sk /p 2>/dev/null | cut -f1)
  [[ "$kb" =~ ^[0-9]+$ ]] && echo "$kb" || echo 0
}

# bk_check_space <folder> <needed-kb> <what>
#   Refuses, with what to do, when the disk holding <folder> has less free space than
#   <needed-kb> plus a tenth. <what> names the copy in the message. Exit: 0 fits; 1 does not.
bk_check_space() {
  local free need=$(( $2 + $2 / 10 ))
  free=$(df -Pk "$1" 2>/dev/null | awk 'NR==2 {print $4}')
  [[ "$free" =~ ^[0-9]+$ ]] || return 0
  if (( need > free )); then
    error "Not enough space for $3 in $1: about $(( need / 1024 )) MB needed, $(( free / 1024 )) MB free."
    error "Free some space, lower BACKUP_KEEP_MONTHS / BACKUP_KEEP_OFFLINE, or use --backup-dir on another disk."
    return 1
  fi
}

# bk_months <folder>
#   Prints the month folders (YYYY-MM) in <folder>, oldest first, one per line.
bk_months() {
  local d
  for d in "$1"/[0-9][0-9][0-9][0-9]-[0-9][0-9]; do
    [[ -d "$d/$BK_NAME" ]] && basename "$d"
  done | sort
}

# bk_resolve <folder>
#   Prints the backup folder a command should use: <folder>'s newest month folder when it has one,
#   else <folder> itself (a month folder named directly, or a backup from before month folders).
#   A backups/orcpub kept from before month folders never hides a newer month.
#   Prints <folder> unchanged when neither exists (the caller reports it).
bk_resolve() {
  local newest
  newest=$(bk_months "$1" | tail -1)
  if [[ -n "$newest" ]]; then echo "$1/$newest"; else echo "$1"; fi
}

# bk_prune <folder> <glob> <keep> <what>
#   Deletes all but the newest <keep> entries of <folder>/<glob> (names sort oldest first), in a
#   root container. <what> names them in the messages. Exit: 0; 1 when a delete failed.
bk_prune() {
  local dir="$1" glob="$2" keep="$3" what="$4" rc=0 name
  local -a all=()
  [[ "$keep" =~ ^[0-9]+$ ]] && (( keep >= 1 )) || { warn "Keeping every $what: the keep count '$keep' is not 1 or more."; return 0; }
  while IFS= read -r name; do all+=("$name"); done \
    < <(cd "$dir" && for d in $glob; do [[ -d "$d" ]] && echo "$d"; done | sort)  # no mapfile: Bash 3.2
  (( ${#all[@]} > keep )) || return 0
  for name in "${all[@]:0:${#all[@]}-keep}"; do
    info "Deleting the old $what $dir/$name (keeping the newest $keep)"
    docker run --rm --user 0:0 -v "$dir:/b" --entrypoint rm "$(bk_image)" -r "/b/$name" || rc=1
  done
  return "$rc"
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
#   Prints BACKUP_NETWORK when set, else the Compose network of the running datomic container.
#   Exit: 1 with an error message when the service is not running.
bk_network() {
  local cid
  if [[ -n "${BACKUP_NETWORK:-}" ]]; then echo "$BACKUP_NETWORK"; return; fi
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
#   Prints the URI of database [db-name] (default bk_db_name; "*" lists all). With a datomic:sql://
#   DATOMIC_URL, that address with the name swapped in (its JDBC part carries the Postgres login).
#   Otherwise the datomic:dev URI on the datomic service, with the password the transactor uses:
#   its Docker secret /run/secrets/datomic_password when mounted, else its DATOMIC_PASSWORD
#   (read inside the container, so it is never on a host command line).
#   Exit: 1 with an error message when the service is not running or the address has no JDBC part.
bk_db_uri() {
  local cid pw
  if bk_is_sql; then
    local rest="${DATOMIC_URL#datomic:sql://}"
    if [[ "$rest" != *\?* ]]; then
      error "DATOMIC_URL is a datomic:sql:// address without its ?jdbc:... part."; return 1
    fi
    printf 'datomic:sql://%s?%s' "${1:-$(bk_db_name)}" "${rest#*\?}"
    return
  fi
  if ! cid=$(bk_datomic_cid); then
    error "The datomic service is not running. Start it with: docker compose up -d datomic"
    return 1
  fi
  pw=$(docker exec "$cid" sh -c 'if [ -f /run/secrets/datomic_password ]; then cat /run/secrets/datomic_password; else printf %s "$DATOMIC_PASSWORD"; fi' 2>/dev/null || true)
  pw="${pw%$'\r'}"
  if [[ -z "$pw" ]]; then
    error "Could not read DATOMIC_PASSWORD from the datomic container."
    return 1
  fi
  printf 'datomic:dev://datomic:4334/%s?password=%s' "${1:-$(bk_db_name)}" "$pw"
}

# bk_run_cli <image> <backup-folder> <bin/datomic args...>
#   Runs `bin/datomic <args>` in a temporary container of <image>, with <backup-folder> mounted at
#   /backup (so use file:/backup/orcpub for the backup). It joins the Compose network only when an
#   argument is a database URI, so verify-backup works with the transactor stopped.
#   Any argument starting with "datomic:" (a database URI, which carries the password) is
#   handed over as an environment variable, so it is not in the host's process list.
#   Runs as the owner of <backup-folder> (the transactor container hands ./backups to its own
#   user, which the host user may not be able to write).
#   BACKUP_JAVA_OPTS (JVM flags, default "-Xms256m -Xmx1g") sizes the JVM.
#   Exit: that of bin/datomic; 1 if the network is needed and missing, or the owner unreadable.
bk_run_cli() {
  local image="$1" dir="$2"; shift 2
  local network owner a n=0 snippet=""
  local -a envs=() net=()
  owner=$(bk_owner "$dir") || return 1
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
  if [[ $n -gt 0 ]]; then
    network=$(bk_network) || return 1
    net=(--network="$network")
  fi
  export DATOMIC_JAVA_OPTS="${BACKUP_JAVA_OPTS:--Xms256m -Xmx1g}"
  # The logging set-up lines Datomic prints at start are dropped; its own messages stay. The
  # subshell keeps docker's exit status (not grep's) whatever `set -e` is in force.
  local rc=0
  (
    set +e
    docker run --rm "${net[@]+"${net[@]}"}" --user "$owner" \
      --tmpfs /datomic/log:rw,mode=1777 -e HOME=/tmp -e DATOMIC_JAVA_OPTS "${envs[@]+"${envs[@]}"}" \
      -v "${dir}:/backup" "$image" sh -c "exec bin/datomic${snippet}" 2>&1 \
      | grep --line-buffered -Ev '^[0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]+ \|-|^$'
    exit "${PIPESTATUS[0]}"
  ) || rc=$?
  return "$rc"
}

# bk_db_exists
#   Asks the transactor whether the database (bk_db_name) exists (a short peer program in the
#   transactor image: bin/datomic has no list-databases).
#   Exit: 0 it exists; 1 it does not; 2 that could not be told (transactor unreachable).
bk_db_exists() {
  local image uri network out
  image=$(bk_image)
  uri=$(bk_db_uri '*') || return 2
  network=$(bk_network) || return 2
  export BK_URI_1="$uri" BK_DB
  BK_DB=$(bk_db_name)
  out=$(docker run --rm --network="$network" --user "$(id -u):$(id -g)" -e HOME=/tmp -e BK_URI_1 -e BK_DB \
    "$image" sh -c 'exec java -Xms64m -Xmx256m -cp "$(bin/classpath)" clojure.main -e \
    "(require (quote [datomic.api :as d])) (println (if (some #{(System/getenv \"BK_DB\")} (d/get-database-names (System/getenv \"BK_URI_1\"))) \"BK-EXISTS\" \"BK-ABSENT\")) (d/shutdown true)"' 2>/dev/null) || return 2
  case "$out" in
    *BK-EXISTS*) return 0 ;;
    *BK-ABSENT*) return 1 ;;
    *) return 2 ;;
  esac
}

# bk_wait_transactor
#   Waits up to two minutes for the transactor to answer (a freshly started one takes a while).
#   Exit: 0 it answered, with bk_db_exists's verdict in BK_DB_STATE (0 exists, 1 absent); 2 it never did.
bk_wait_transactor() {
  local i rc
  for i in $(seq 1 12); do
    rc=0; bk_db_exists 2>/dev/null || rc=$?
    if [[ $rc -ne 2 ]]; then BK_DB_STATE=$rc; return 0; fi
    [[ $i -eq 1 ]] && info "Waiting for the transactor to start"
    sleep 10
  done
  return 2
}

# bk_prepare_dir <folder>
#   Creates <folder> if needed, through a container running as the parent's owner when the host
#   user cannot (the transactor container owns ./backups), and checks a container running as its
#   owner can write there. A backup in it
#   owned by someone else (older migrate runs wrote as root) is handed to the folder's owner.
#   Prints the absolute path. Exit: 0 writable; 1 with an error message.
bk_prepare_dir() {
  local dir="$1" image parent own
  if ! mkdir -p "$dir" 2>/dev/null && [[ ! -d "$dir" ]]; then
    parent=$(dirname "$dir")
    if [[ -d "$parent" ]] && own=$(bk_owner "$parent"); then
      docker run --rm --user "$own" -v "$(cd "$parent" && pwd):/b" --entrypoint mkdir "$(bk_image)" \
        "/b/$(basename "$dir")" >/dev/null 2>&1
    fi
    [[ -d "$dir" ]] || { error "Cannot create backup folder: $dir"; return 1; }
  fi
  dir=$(cd "$dir" && pwd)
  image=$(bk_image)
  own=$(bk_owner "$dir") || return 1
  if ! docker run --rm --user "$own" -v "$dir:/backup" --entrypoint sh "$image" \
      -c 'touch /backup/.write-test && rm -f /backup/.write-test' >/dev/null 2>&1; then
    error "Backup folder is not writable: $dir"; return 1
  fi
  if [[ -d "$dir/$BK_NAME" ]] && [[ -n "$(find "$dir/$BK_NAME" ! -user "${own%:*}" -print -quit 2>/dev/null)$(find "$dir/$BK_NAME" ! -readable -print -quit 2>/dev/null)" ]]; then
    info "Handing $dir/$BK_NAME to the folder's owner ($own), as older backups were written by root" >&2
    docker run --rm --user 0:0 -v "$dir:/backup" --entrypoint chown "$image" -R "$own" "/backup/$BK_NAME" >/dev/null 2>&1 \
      || { error "Could not hand $dir/$BK_NAME to the folder's owner."; return 1; }
  fi
  echo "$dir"
}

# bk_backup <backup-folder>
#   Live backup: `backup-db` of the running database (dev or Postgres storage) into
#   <backup-folder>/<YYYY-MM>/orcpub. Repeating it in the same month is incremental. The first
#   backup of a month is checked for space first (the size of the newest month), and once it
#   verifies, month folders beyond BACKUP_KEEP_MONTHS (default 3) are deleted.
#   Exit: 0 done; 1 on any failure (an error message says why).
bk_backup() {
  local base="$1" dir image uri month newest new=false
  base=$(bk_prepare_dir "$base") || return 1
  month=$(date -u +%Y-%m)
  if [[ ! -d "$base/$month/$BK_NAME/roots" ]]; then
    new=true
    newest=$(bk_months "$base" | tail -1)
    if [[ -n "$newest" ]]; then
      bk_check_space "$base" "$(bk_du_kb "$base/$newest")" "a new month's backup" || return 1
    fi
  fi
  [[ -d "$base/$BK_NAME" ]] && info "The backup in $base/$BK_NAME (before month folders) is left as it is."
  dir=$(bk_prepare_dir "$base/$month") || return 1
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
  info "Backup complete: $dir/$BK_NAME (restore point t=$(bk_latest_t "$dir/$BK_NAME"), $(( $(bk_du_kb "$dir") / 1024 )) MB)"
  if [[ "$new" == true ]] && (( $(bk_months "$base" | wc -l) > ${BACKUP_KEEP_MONTHS:-3} )); then
    if bk_verify "$dir"; then
      bk_prune "$base" '[0-9][0-9][0-9][0-9]-[0-9][0-9]' "${BACKUP_KEEP_MONTHS:-3}" "monthly backup" || return 1
    else
      warn "Older month folders are kept, since this month's backup did not verify."
      return 1
    fi
  fi
}

# bk_verify <backup-folder>
#   Checks every segment of the latest restore point in <backup-folder>/orcpub (verify-backup).
#   <backup-folder> may also be the folder of month folders: the newest month is checked.
#   Exit: 0 verified; 1 missing, empty, corrupt or unreadable.
bk_verify() {
  local dir image t out rc
  dir=$(bk_resolve "$1")
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
#   Restores <backup-folder>/orcpub into an empty database. <backup-folder> may be the folder of
#   month folders: the newest is used. Refuses, with what to do, when "orcpub" already exists (checked
#   on the running transactor). Dev storage restores through the running transactor, then restarts
#   it; Postgres has the app and transactor stopped for the restore, then starts the transactor.
#   Exit: 0 restored; 1 on any failure or refusal.
bk_restore() {
  local dir image uri t rc=0
  dir=$(bk_resolve "$1")
  if [[ ! -d "$dir/$BK_NAME" ]]; then
    error "No backup found at $dir/$BK_NAME"; return 1
  fi
  dir=$(cd "$dir" && pwd)
  if ! t=$(bk_latest_t "$dir/$BK_NAME"); then
    error "$dir/$BK_NAME has no restore point (empty or incomplete backup)."; return 1
  fi
  bk_wait_transactor || rc=$?
  [[ $rc -eq 0 ]] && rc=$BK_DB_STATE
  case "$rc" in
    0) error "The database '$BK_NAME' already exists on this transactor, so nothing was restored."
       error "A restore needs an empty database. To replace it: stop the app (docker compose stop orcpub),"
       error "back up what is there first (./run --backup), stop the stack, move ./data aside,"
       error "start only the transactor (docker compose up -d datomic), then run this restore again."
       bk_is_sql && error "On Postgres, ./data holds none of it: point DATOMIC_URL at an empty database instead (docs/DOCKER.md)."
       return 1 ;;
    1) ;;
    *) error "Could not reach the transactor to check for an existing database."; return 1 ;;
  esac
  image=$(bk_image)
  uri=$(bk_db_uri) || return 1
  if bk_is_sql; then
    bk_restore_sql "$image" "$dir" "$uri" "$t"; return
  fi
  info "Restoring $dir/$BK_NAME at t=$t"
  if ! bk_run_cli "$image" "$dir" restore-db "file:/backup/$BK_NAME" "$uri" "$t"; then
    error "restore-db failed: the database may be incomplete. Stop the stack, move ./data aside and try again."
    return 1
  fi
  # Datomic needs its transactor restarted after a restore before peers connect.
  info "Restarting the transactor"
  docker compose restart datomic || { error "Restore done, but the transactor did not restart: run docker compose restart datomic."; return 1; }
  info "Restore complete (t=$t). Start the app: docker compose up -d"
}

# bk_restore_sql <image> <backup-folder> <uri> <t>
#   The Postgres half of bk_restore: Datomic restores SQL storage only with no transactor or peer
#   connected, so the app and transactor are stopped first (the network is read while the transactor
#   still runs), restore-db writes straight to Postgres, and the transactor is started again.
#   Peers outside this Compose stack are not stopped. Exit: 0 restored; 1 on any failure.
bk_restore_sql() {
  local image="$1" dir="$2" uri="$3" t="$4" network
  network=$(bk_network) || return 1
  info "Stopping the app and the transactor: a Postgres restore runs with nothing connected"
  docker compose stop orcpub datomic || { error "Could not stop the stack, so nothing was restored."; return 1; }
  info "Restoring $dir/$BK_NAME at t=$t"
  if ! BACKUP_NETWORK="$network" bk_run_cli "$image" "$dir" restore-db "file:/backup/$BK_NAME" "$uri" "$t"; then
    error "restore-db failed: the database may be incomplete. The stack is left stopped."
    error "Point DATOMIC_URL at an empty database and run this restore again."
    return 1
  fi
  info "Starting the transactor"
  docker compose up -d datomic || { error "Restore done, but the transactor did not start: run docker compose up -d datomic."; return 1; }
  info "Restore complete (t=$t). Start the app: docker compose up -d"
}

# bk_offline_backup <backup-folder>
#   Offline fallback: stops the stack, copies ./data to <backup-folder>/offline-<UTC date-time>/data,
#   starts the services that were running again (also when the copy failed). Copies run in the transactor image as
#   root, so ownership of the files is kept.
#   Refuses a <backup-folder> inside ./data. Holds the offline lock throughout (bk_locked).
#   Exit: 0 copied and stack restarted; 1 otherwise.
bk_offline_backup() {
  bk_locked _bk_offline_backup "$@"
}

# _bk_offline_backup <backup-folder>
#   bk_offline_backup's work, run under its lock. Exit: as bk_offline_backup.
_bk_offline_backup() {
  local dir="$1" image stamp dest services rc=0
  if bk_is_sql; then
    error "DATOMIC_URL stores the database in Postgres, so ./data holds none of it: an offline copy would copy nothing."
    error "Use ./run --backup (works on Postgres too), and the Postgres server's own backups (pg_dump or archiving)."
    return 1
  fi
  if [[ ! -d data ]]; then
    error "No ./data folder here: nothing to copy (a Swarm stack keeps it in the orcpub_data volume)."
    return 1
  fi
  if bk_inside_data "$dir"; then
    error "The backup folder $dir is inside ./data, which is what gets copied: choose a folder outside it."
    return 1
  fi
  dir=$(bk_prepare_dir "$dir") || return 1
  bk_check_space "$dir" "$(bk_du_kb "$PWD/data")" "an offline copy of ./data" || return 1
  image=$(bk_image)
  stamp=$(date -u +%Y-%m-%dT%H%M%SZ)
  dest="$dir/offline-$stamp"
  services=$(bk_running_services) || return 1
  info "Stopping the stack"
  docker compose down || { error "Could not stop the stack; no copy made."; return 1; }
  docker run --rm --entrypoint sh -v "$PWD/data:/src:ro" -v "$dir:/dst" "$image" -c \
    'mkdir -p "/dst/$1" && cp -a /src/. "/dst/$1/" && touch "/dst/$1/.orcpub-offline-backup"' sh "offline-$stamp" || rc=1
  bk_start_services "$services" || rc=1
  if [[ $rc -ne 0 ]]; then
    error "Offline backup failed; $dest is not complete."; return 1
  fi
  info "Offline backup complete: $dest ($(( $(bk_du_kb "$dest") / 1024 )) MB)"
  bk_prune "$dir" 'offline-*' "${BACKUP_KEEP_OFFLINE:-3}" "offline copy" || return 1
}

# bk_offline_restore <offline-backup-folder>
#   Puts an offline backup back: stops the stack, moves ./data to ./data.before-restore-<UTC stamp>,
#   copies the backup in, starts the services that were running. Nothing is deleted.
#   Refuses a backup inside ./data. Holds the offline lock throughout (bk_locked).
#   Exit: 0 restored; 1 otherwise (if the copy failed, ./data is put back and the stack restarted).
bk_offline_restore() {
  bk_locked _bk_offline_restore "$@"
}

# _bk_offline_restore <offline-backup-folder>
#   bk_offline_restore's work, run under its lock. Exit: as bk_offline_restore.
_bk_offline_restore() {
  local src="$1" image stamp aside services back=0
  if bk_is_sql; then
    error "DATOMIC_URL stores the database in Postgres: an offline copy of ./data cannot restore it."
    error "Restore a live backup with ./run --restore <folder>."
    return 1
  fi
  if [[ ! -f "$src/.orcpub-offline-backup" ]]; then
    error "$src is not an offline backup (no .orcpub-offline-backup marker)."; return 1
  fi
  if bk_inside_data "$src"; then
    error "The backup $src is inside ./data, which a restore moves aside: copy it out of ./data first."
    return 1
  fi
  src=$(cd "$src" && pwd)
  image=$(bk_image)
  stamp=$(date -u +%Y%m%dT%H%M%SZ)
  aside="data.before-restore-$stamp"
  services=$(bk_running_services) || return 1
  info "Stopping the stack"
  docker compose down || { error "Could not stop the stack; nothing restored."; return 1; }
  if [[ -d data ]] && ! mv data "$aside"; then
    error "Could not move ./data aside; nothing restored. The stack is stopped: docker compose up -d"; return 1
  fi
  if ! mkdir data; then
    error "Could not create ./data; the previous data is in ./$aside."; return 1
  fi
  if ! docker run --rm --entrypoint sh -v "$src:/src:ro" -v "$PWD/data:/dst" "$image" \
      -c 'cp -a /src/. /dst/ && rm -f /dst/.orcpub-offline-backup'; then
    error "Copy failed: putting ./data back."
    docker run --rm --entrypoint sh -v "$PWD/data:/dst" "$image" -c 'rm -rf /dst/* /dst/.[!.]* /dst/..?*' || true
    rmdir data 2>/dev/null || back=1
    if [[ $back -eq 0 && -d "$aside" ]]; then mv "$aside" data || back=1; fi
    if [[ $back -ne 0 ]]; then
      error "Could not put ./data back: the previous data is in ./$aside. The stack stays stopped."
      return 1
    fi
    bk_start_services "$services" || true
    error "Restore failed; the previous data is back in ./data."
    return 1
  fi
  bk_start_services "$services" || return 1
  info "Offline restore complete. The previous data is in ./$aside (delete it when satisfied)."
}
