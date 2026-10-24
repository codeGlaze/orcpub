#!/usr/bin/env bash
# Tests the transactor addresses deploy/start.sh writes, with name lookups faked: Compose (the
# service name is this container), Swarm (it is a service address), set values, and no lookup.
# Runs without Docker. Usage: bash test/docker/test-transactor-start.sh
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
TESTS=0
FAILURES=0

# A copy of start.sh that reads and writes in $WORK and stops before the transactor starts.
sed -e "s|/datomic/transactor.properties.template|$WORK/template|" \
    -e "s|/datomic/transactor.properties\"|$WORK/out\"|" \
    -e 's|^chown -R .*|true|' \
    -e 's|^exec su-exec .*|exit 0|' \
    "$PROJECT_ROOT/deploy/start.sh" > "$WORK/start.sh"
cp "$PROJECT_ROOT/docker/transactor.properties.template" "$WORK/template"

# Fake lookups: FAKE_SERVICE is what "datomic" resolves to, FAKE_SELF this container's address.
mkdir -p "$WORK/bin"
cat > "$WORK/bin/getent" <<'FAKE'
#!/usr/bin/env bash
case "$2" in
  datomic) [ -n "${FAKE_SERVICE:-}" ] && echo "$FAKE_SERVICE  datomic" ;;
  *) [ -n "${FAKE_SELF:-}" ] && echo "$FAKE_SELF  $2" ;;
esac
FAKE
printf '#!/usr/bin/env bash\necho "${FAKE_SELF:-}"\n' > "$WORK/bin/hostname"
chmod +x "$WORK/bin/getent" "$WORK/bin/hostname"

# check NAME WANT_HOST WANT_ALT WANT_TEXT [VAR=value ...]: runs start.sh with the settings given.
check() {
  local name="$1" want_host="$2" want_alt="$3" want_text="$4"; shift 4
  local out
  out=$(env -i PATH="$WORK/bin:/usr/bin:/bin" HOSTNAME=task1 ADMIN_PASSWORD=a DATOMIC_PASSWORD=b "$@" \
        bash "$WORK/start.sh" 2>&1)
  local host alt
  host=$(sed -n 's/^host=//p' "$WORK/out"); alt=$(sed -n 's/^alt-host=//p' "$WORK/out")
  TESTS=$((TESTS + 1))
  if [ "$host" = "$want_host" ] && [ "$alt" = "$want_alt" ] && { [ -z "$want_text" ] || grep -qF "$want_text" <<<"$out"; }; then
    echo "PASS  $name (host=$host alt-host=$alt)"
  else
    echo "FAIL  $name: got host=$host alt-host=$alt, wanted $want_host / $want_alt${want_text:+ and '$want_text'}"
    echo "$out" | sed 's/^/      /'
    FAILURES=$((FAILURES + 1))
  fi
}

check "Compose: the service name is this container" datomic 127.0.0.1 "transactor: host=datomic" \
  FAKE_SERVICE=172.18.0.5 FAKE_SELF=172.18.0.5
check "Swarm: the service name is a service address" 0.0.0.0 datomic "a Swarm service address" \
  FAKE_SERVICE=10.0.1.2 FAKE_SELF=10.0.1.7
check "Swarm, TRANSACTOR_HOST set: it wins" datomic 127.0.0.1 "" \
  FAKE_SERVICE=10.0.1.2 FAKE_SELF=10.0.1.7 TRANSACTOR_HOST=datomic
check "Swarm, ALT_HOST=127.0.0.1 set: kept, with a warning" 0.0.0.0 127.0.0.1 "WARNING ALT_HOST is 127.0.0.1" \
  FAKE_SERVICE=10.0.1.2 FAKE_SELF=10.0.1.7 ALT_HOST=127.0.0.1
check "No name lookup (plain docker run): unchanged" datomic 127.0.0.1 "" \
  FAKE_SELF=172.17.0.2
check "Both set for Swarm: used as set" 0.0.0.0 datomic "" \
  FAKE_SERVICE=10.0.1.2 FAKE_SELF=10.0.1.7 TRANSACTOR_HOST=0.0.0.0 ALT_HOST=datomic

echo ""
echo "=== Results: ${TESTS} tests, ${FAILURES} failures ==="
[ "$FAILURES" -eq 0 ]
