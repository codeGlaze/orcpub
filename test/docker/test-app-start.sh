#!/usr/bin/env bash
#
# Test deploy/app-start.sh, the app image's entrypoint, in a small Alpine container with a stand-in
# for Java. With the host's /var/log mounted at /host-log (a temporary folder here): Java runs as
# `app`, never root; the state lands in /var/log/<APP_LOGS_NAME>, private (750) and owned by app;
# /var/log itself and anything else in it keep their owner; a name that would leave /var/log is
# refused. Without the mount, the state stays in the container.
#
# Usage: ./test/docker/test-app-start.sh        Needs Docker.

set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
HOSTLOG="$(mktemp -d)"
chmod 755 "$HOSTLOG"   # as /var/log is on a normal host
trap 'docker run --rm -v "$HOSTLOG":/s alpine:3.20 sh -c "rm -rf /s/* && chown $(id -u) /s" >/dev/null 2>&1; rm -rf "$HOSTLOG"' EXIT

TESTS=0
FAILURES=0
check() { TESTS=$((TESTS + 1)); if eval "$2"; then printf 'PASS  %s\n' "$1"; else printf 'FAIL  %s\n' "$1"; FAILURES=$((FAILURES + 1)); fi; }

# run_start [-v mount] [-e ENV=…]: runs app-start.sh as the image does and prints what happened.
run_start() {
  docker run --rm -v "$PROJECT_ROOT/deploy/app-start.sh:/app-start.sh:ro" "$@" alpine:3.20 sh -c '
    apk add --no-cache su-exec >/dev/null 2>&1
    addgroup -S app && adduser -S -G app app
    mkdir -p /var/lib/orcpub/state/alerts /var/lib/orcpub/state/crash && chown -R app:app /var/lib/orcpub
    printf "#!/bin/sh\necho user=\$(id -un)\necho args=\$*\ntouch /var/lib/orcpub/state/crash/probe && echo wrote=yes\n" > /usr/local/bin/java
    chmod +x /usr/local/bin/java
    sh /app-start.sh /orcpub.jar
    [ -d /host-log ] && for d in /host-log /host-log/*; do echo "dir $(basename $d)=$(stat -c "%U %a" $d)"; done
    [ -d /host-log ] || echo "inner=$(stat -c "%U %a" /var/lib/orcpub/state/crash)"' 2>&1
}

echo "already here" > "$HOSTLOG/syslog"
OUT="$(run_start -v "$HOSTLOG":/host-log)"
echo "$OUT" | sed 's/^/  | /'
check "Java runs as app, not root" '[[ "$OUT" == *"user=app"* ]]'
check "it is given -jar and the jar" '[[ "$OUT" == *"args=-jar /orcpub.jar"* ]]'
check "a crash file written by the app lands in /var/log/orcpub on the host" '[ -n "$(docker run --rm -v "$HOSTLOG":/s alpine:3.20 ls /s/orcpub/crash/probe 2>/dev/null)" ]'
check "/var/log/orcpub belongs to app and is private" '[[ "$OUT" == *"dir orcpub=app 750"* ]]'
check "/var/log itself keeps its owner" '[[ "$OUT" != *"dir host-log=app"* ]]'
check "another file in /var/log keeps its owner" '[ "$(stat -c %u "$HOSTLOG/syslog")" = "$(id -u)" ]'

OUT="$(run_start -v "$HOSTLOG":/host-log -e APP_LOGS_NAME=dmv)"
check "APP_LOGS_NAME=dmv uses /var/log/dmv" '[[ "$OUT" == *"dir dmv=app 750"* ]]'

OUT="$(run_start -v "$HOSTLOG":/host-log -e APP_LOGS_NAME=../etc)"
check "a name that leaves /var/log is refused" '[[ "$OUT" == *"is not a plain folder name"* && "$OUT" == *"wrote=yes"* ]]'

chmod 700 "$HOSTLOG"
OUT="$(run_start -v "$HOSTLOG":/host-log)"
check "a host /var/log closed to other users: files stay in the container, and it says so" '[[ "$OUT" == *"cannot reach /var/log/orcpub"* && "$OUT" == *"wrote=yes"* ]]'
chmod 755 "$HOSTLOG"

OUT="$(run_start)"
check "without the mount the state stays in the container, private" '[[ "$OUT" == *"inner=app 750"* && "$OUT" == *"user=app"* ]]'

printf '\n=== Results: %s tests, %s failures ===\n' "$TESTS" "$FAILURES"
[ "$FAILURES" -eq 0 ]
