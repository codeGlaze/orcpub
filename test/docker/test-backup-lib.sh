#!/usr/bin/env bash
# scripts/backup-lib.sh without Docker: which backup folder a restore or verify picks (bk_resolve),
# pruning old month folders with mapfile unavailable as on macOS's Bash 3.2 (bk_prune), making a
# month folder the host user cannot create itself (bk_prepare_dir), and the order of steps in a
# restore (bk_restore). `docker` is a stand-in that runs `rm` and `mkdir` on the host path behind
# the mounted folder and records each call.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
tmp="$(mktemp -d)"
cleanup() { chmod -R u+w "$tmp" 2>/dev/null; rm -rf "$tmp"; }
trap cleanup EXIT
fails=0
pass() { echo "  PASS: $1"; }
fail() { echo "  FAIL: $1"; fails=$((fails + 1)); }
info() { :; }
warn() { :; }
error() { echo "  (error) $*" >&2; }

docker() {
  # docker run --rm [--user U] -v HOST:/b|/backup [--entrypoint CMD] IMAGE ARGS...: CMD runs on HOST.
  echo "docker $*" >>"$tmp/docker.log"
  local host="" mnt="" cmd="" a
  [ "$1" = compose ] && return 0
  shift  # run
  while [ $# -gt 0 ]; do
    case "$1" in
      --rm) shift ;;
      --user) shift 2 ;;
      -v) host="${2%%:*}"; mnt="${2#*:}"; shift 2 ;;
      --entrypoint) cmd="$2"; shift 2 ;;
      *) shift; break ;;  # the image
    esac
  done
  local -a args=()
  for a in "$@"; do args+=("${a/#$mnt/$host}"); done
  case "$cmd" in
    rm) chmod u+w "$host" && rm "${args[@]}" ;;
    mkdir) chmod u+w "$host" && mkdir "${args[@]}" && chmod u-w "$host" ;;
    sh) return 0 ;;  # the write test
    *) return 1 ;;
  esac
}
bk_image() { echo test-image; }

source "$root/scripts/backup-lib.sh"
set +e

echo "bk_resolve: which backup a restore or verify reads"
b="$tmp/backups"; mkdir -p "$b/orcpub" "$b/2026-09/orcpub" "$b/2026-11/orcpub"
[ "$(bk_resolve "$b")" = "$b/2026-11" ] && pass "the newest month wins over a kept backups/orcpub" \
  || fail "the newest month wins over a kept backups/orcpub (got $(bk_resolve "$b"))"
[ "$(bk_resolve "$b/2026-09")" = "$b/2026-09" ] && pass "a month folder named directly is used" \
  || fail "a month folder named directly is used (got $(bk_resolve "$b/2026-09"))"
l="$tmp/legacy"; mkdir -p "$l/orcpub"
[ "$(bk_resolve "$l")" = "$l" ] && pass "a folder with only a pre-month backup is used as it is" \
  || fail "a folder with only a pre-month backup is used as it is (got $(bk_resolve "$l"))"

echo "bk_prune: keeps the newest, with mapfile unavailable (Bash 3.2)"
p="$tmp/prune"; for m in 2026-07 2026-08 2026-09 2026-10 2026-11; do mkdir -p "$p/$m/orcpub"; done
( enable -n mapfile 2>/dev/null; bk_prune "$p" '[0-9][0-9][0-9][0-9]-[0-9][0-9]' 3 "monthly backup" )
left="$(cd "$p" && ls | tr '\n' ' ')"
[ "$left" = "2026-09 2026-10 2026-11 " ] && pass "the two oldest months are deleted, three kept" \
  || fail "the two oldest months are deleted, three kept (left: $left)"

echo "bk_prepare_dir: a month folder in a backups folder the host user cannot write"
if [ "$(id -u)" = 0 ]; then
  echo "  SKIP: running as root, so a read-only folder does not stop mkdir"
else
o="$tmp/owned"; mkdir -p "$o"; chmod u-w "$o"
out="$(bk_prepare_dir "$o/2026-11" 2>/dev/null)"; rc=$?
[ $rc -eq 0 ] && [ -d "$o/2026-11" ] && pass "the month folder is made through a container" \
  || fail "the month folder is made through a container (rc $rc, exists: $([ -d "$o/2026-11" ] && echo yes || echo no))"
grep -q -- '--entrypoint mkdir' "$tmp/docker.log" && pass "it was made as the parent's owner, in a container" \
  || fail "it was made as the parent's owner, in a container"
fi

echo "bk_restore: the order of steps on each storage"
r="$tmp/restore"; mkdir -p "$r/2026-11/orcpub/roots"; touch "$r/2026-11/orcpub/roots/1000"
bk_wait_transactor() { BK_DB_STATE=1; return 0; }  # the database is absent: a restore may go ahead
bk_db_uri() { echo "datomic:test://orcpub"; }
bk_network() { echo net1; }
bk_run_cli() { echo "docker cli $3 network=${BACKUP_NETWORK:-}" >>"$tmp/docker.log"; }
steps() { grep -E 'compose (stop|up|restart)|cli restore-db' "$tmp/docker.log" | sed 's/^docker //' | tr '\n' ';'; }
: >"$tmp/docker.log"; ( export DATOMIC_URL="datomic:sql://orcpub?jdbc:postgresql://db:5432/datomic"; bk_restore "$r" ) >/dev/null 2>&1
[ "$(steps)" = "compose stop orcpub datomic;cli restore-db network=net1;compose up -d datomic;" ] \
  && pass "Postgres: app and transactor stopped, restored on the stack's network, transactor started" \
  || fail "Postgres: app and transactor stopped, restored on the stack's network, transactor started (got: $(steps))"
: >"$tmp/docker.log"; ( export DATOMIC_URL="datomic:dev://datomic:4334/orcpub"; bk_restore "$r" ) >/dev/null 2>&1
[ "$(steps)" = "cli restore-db network=;compose restart datomic;" ] \
  && pass "dev storage: restored through the running transactor, which is then restarted" \
  || fail "dev storage: restored through the running transactor, which is then restarted (got: $(steps))"

echo
[ $fails -eq 0 ] && echo "backup-lib: all checks passed" || echo "backup-lib: $fails check(s) failed"
exit $((fails > 0))
