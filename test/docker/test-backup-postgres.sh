#!/usr/bin/env bash
# ./run --backup, --verify-backup and --restore against real Postgres storage: Postgres 16, a SQL
# transactor and a stand-in app (the Compose file and table script below). Seeds rows through a peer, backs up and
# verifies, checks a restore over the existing database is refused, then restores into an empty
# Postgres database and compares the data. Needs Docker and the transactor image (DATOMIC_IMAGE,
# default orcpub-datomic). Runs in a temp folder with its own .env; the repo's .env is not read.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
image="${DATOMIC_IMAGE:-orcpub-datomic}"
work="$(mktemp -d)"
export COMPOSE_PROJECT_NAME="orcpub-pgtest-$$"
net="${COMPOSE_PROJECT_NAME}_default"
fails=0
pass() { echo "  PASS: $1"; }
fail() { echo "  FAIL: $1"; fails=$((fails + 1)); }
cleanup() {
  (cd "$work" && docker compose down -v --remove-orphans >/dev/null 2>&1)
  docker run --rm -v "$work:/w" --entrypoint rm "$image" -rf /w/backups >/dev/null 2>&1
  rm -rf "$work"
}
trap cleanup EXIT

# Datomic's bin/sql/postgres-table.sql without WITH (OIDS=FALSE), which Postgres 12+ rejects. Two
# storage databases: "datomic" holds the original, "restored" starts empty for the restore.
cat >"$work/init.sql" <<'SQL'
CREATE TABLE datomic_kvs (id text NOT NULL, rev integer, map text, val bytea, CONSTRAINT pk_id PRIMARY KEY (id));
CREATE DATABASE restored OWNER datomic;
\connect restored
CREATE TABLE datomic_kvs (id text NOT NULL, rev integer, map text, val bytea, CONSTRAINT pk_id PRIMARY KEY (id));
SQL
# The transactor serves the Postgres database named by PG_DB.
cat >"$work/docker-compose.yaml" <<'YAML'
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: datomic
      POSTGRES_PASSWORD: datomic
      POSTGRES_DB: datomic
    volumes:
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U datomic -d datomic"]
      interval: 2s
      retries: 30
  datomic:
    image: ${DATOMIC_IMAGE:-orcpub-datomic}
    depends_on:
      db:
        condition: service_healthy
    environment:
      PG_DB: ${PG_DB:-datomic}
    entrypoint: ["sh", "-c"]
    command:
      - |
        cat > /tmp/sql.properties <<PROPS
        protocol=sql
        host=datomic
        alt-host=datomic
        port=4334
        sql-url=jdbc:postgresql://db:5432/$$PG_DB
        sql-user=datomic
        sql-password=datomic
        sql-driver-class=org.postgresql.Driver
        memory-index-threshold=32m
        memory-index-max=256m
        object-cache-max=128m
        PROPS
        exec bin/transactor /tmp/sql.properties
  orcpub:
    image: ${DATOMIC_IMAGE:-orcpub-datomic}
    entrypoint: ["sleep", "infinity"]
YAML
ln -s "$root/run" "$work/run"
ln -s "$root/scripts" "$work/scripts"
mkdir -p "$work/backups"
url() { echo "datomic:sql://orcpub?jdbc:postgresql://db:5432/$1?user=datomic&password=datomic"; }
write_env() {
  printf 'COMPOSE_PROJECT_NAME=%s\nDATOMIC_IMAGE=%s\nPG_DB=%s\nDATOMIC_URL=%s\n' \
    "$COMPOSE_PROJECT_NAME" "$image" "$1" "$(url "$1")" >"$work/.env"
}

peer() {
  # peer <storage-db> <clojure>: runs <clojure> in a peer with `uri` bound to orcpub on <storage-db>.
  docker run --rm --network "$net" -e URI="$(url "$1")" --entrypoint sh "$image" -c \
    'exec java -Xmx512m -cp "$(bin/classpath)" clojure.main -e "(require (quote [datomic.api :as d])) (def uri (System/getenv \"URI\")) $0 (shutdown-agents) (System/exit 0)"' "$2" 2>&1
}
wait_transactor() {
  # wait_transactor <storage-db> <since>: until the transactor logged "System started" after <since>
  # (a docker logs time) and, when orcpub exists on <storage-db>, a peer connects to it. 3 minutes.
  for _ in $(seq 1 36); do
    if (cd "$work" && docker compose logs --since "$2" datomic 2>/dev/null) | grep -q "System started" \
      && peer "$1" '(when (some #{"orcpub"} (d/get-database-names (clojure.string/replace uri "orcpub" "*"))) (d/db (d/connect uri)))' >/dev/null; then
      return 0
    fi
    sleep 5
  done
  return 1
}
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
fingerprint() {
  # Prints "rows sum" of :t/n over the orcpub database on <storage-db>, or nothing on any error.
  peer "$1" '(let [db (d/db (d/connect uri))] (println "FP" (ffirst (d/q (quote [:find (count ?e) :where [?e :t/n]]) db)) (ffirst (d/q (quote [:find (sum ?n) :with ?e :where [?e :t/n ?n]]) db))))' \
    | sed -n 's/^FP //p'
}
service_running() { [ -n "$(cd "$work" && docker compose ps -q --status running "$1")" ]; }

echo "Postgres backups: start Postgres, a SQL transactor and a stand-in app"
write_env datomic
t0="$(now)"
(cd "$work" && docker compose up -d >/dev/null 2>&1) || { echo "  FAIL: the stack did not start"; exit 1; }
wait_transactor datomic "$t0" && pass "the SQL transactor serves the datomic storage database" \
  || { fail "the SQL transactor serves the datomic storage database"; (cd "$work" && docker compose logs datomic | tail -20); exit 1; }

seed='(d/create-database uri) (let [c (d/connect uri)] @(d/transact c [{:db/ident :t/n :db/valueType :db.type/long :db/cardinality :db.cardinality/one}]) (doseq [b (partition-all 500 (range 2000))] @(d/transact c (mapv (fn [i] {:t/n i}) b))) (println "SEEDED"))'
for _ in 1 2 3; do  # a freshly started SQL transactor can refuse its first large write
  peer datomic "$seed" | grep -q SEEDED && break
  sleep 10
done
before="$(fingerprint datomic)"
[ "$before" = "2000 1999000" ] && pass "2000 rows seeded ($before)" || fail "2000 rows seeded (got '$before')"

echo "Backup and verify"
(cd "$work" && ./run --backup --backup-dir ./backups) >"$work/backup.log" 2>&1 \
  && pass "./run --backup" || { fail "./run --backup"; tail -15 "$work/backup.log"; }
(cd "$work" && ./run --verify-backup ./backups) >"$work/verify.log" 2>&1 \
  && pass "./run --verify-backup" || { fail "./run --verify-backup"; tail -15 "$work/verify.log"; }

echo "A restore over the existing database is refused"
if (cd "$work" && ./run --restore ./backups) >"$work/refuse.log" 2>&1; then
  fail "the restore was refused"
else
  grep -q "already exists" "$work/refuse.log" && pass "refused: the database already exists" \
    || { fail "refused for the right reason"; tail -10 "$work/refuse.log"; }
fi
[ "$(fingerprint datomic)" = "$before" ] && pass "the existing data is untouched" || fail "the existing data is untouched"

echo "Restore into an empty Postgres database"
write_env restored
t0="$(now)"
(cd "$work" && docker compose up -d datomic orcpub >/dev/null 2>&1)
wait_transactor restored "$t0" || fail "the transactor serves the empty database"
service_running orcpub && pass "the app is running before the restore" || fail "the app is running before the restore"
t0="$(now)"
(cd "$work" && ./run --restore ./backups) >"$work/restore.log" 2>&1 \
  && pass "./run --restore" || { fail "./run --restore"; tail -20 "$work/restore.log"; }
grep -q "Stopping the app and the transactor" "$work/restore.log" && pass "the app and transactor were stopped for the restore" \
  || fail "the app and transactor were stopped for the restore"
service_running orcpub && fail "the app stays stopped until started by hand" || pass "the app stays stopped until started by hand"
service_running datomic && pass "the transactor was started again" || fail "the transactor was started again"
wait_transactor restored "$t0" || fail "the transactor serves the restored database"
after="$(fingerprint restored)"
[ -n "$after" ] && [ "$after" = "$before" ] && pass "the restored data matches ($after)" \
  || { fail "the restored data matches (before '$before', after '$after')"
         peer restored '(println (d/get-database-names (clojure.string/replace uri "orcpub" "*"))) (d/db (d/connect uri))' | tail -5
         grep -iE 'restor|error|t=' "$work/restore.log" | tail -8; }

echo
[ $fails -eq 0 ] && echo "backup-postgres: all checks passed" || echo "backup-postgres: $fails check(s) failed"
exit $((fails > 0))
