#!/usr/bin/env bash
#
# Test deploy/app-start.sh, the app image's entrypoint, in a small Alpine container with a stand-in
# for Java: Java runs as `app` (never root), the app can write its state folders, the folders are
# private (750), and a file already in the mounted folder keeps its owner (no recursive chown).
#
# Usage: ./test/docker/test-app-start.sh        Needs Docker.

set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
STATE="$(mktemp -d)"
trap 'docker run --rm -v "$STATE":/s alpine:3.20 sh -c "rm -rf /s/* && chown $(id -u) /s" >/dev/null 2>&1; rm -rf "$STATE"' EXIT

TESTS=0
FAILURES=0
check() { TESTS=$((TESTS + 1)); if eval "$2"; then printf 'PASS  %s\n' "$1"; else printf 'FAIL  %s\n' "$1"; FAILURES=$((FAILURES + 1)); fi; }

echo "already here" > "$STATE/keep-me"
OUT="$(docker run --rm -v "$PROJECT_ROOT/deploy/app-start.sh:/app-start.sh:ro" -v "$STATE":/var/lib/orcpub/state \
  alpine:3.20 sh -c '
    apk add --no-cache su-exec >/dev/null 2>&1
    addgroup -S app && adduser -S -G app app
    printf "#!/bin/sh\necho user=\$(id -un)\necho args=\$*\ntouch /var/lib/orcpub/state/crash/probe && echo wrote=yes\n" > /usr/local/bin/java
    chmod +x /usr/local/bin/java
    sh /app-start.sh /orcpub.jar
    echo state=$(stat -c "%U %a" /var/lib/orcpub/state)
    echo crash=$(stat -c "%U %a" /var/lib/orcpub/state/crash)
    echo keep=$(stat -c "%u" /var/lib/orcpub/state/keep-me)' 2>&1)"
echo "$OUT" | sed 's/^/  | /'

check "Java runs as app, not root" '[[ "$OUT" == *"user=app"* ]]'
check "it is given -jar and the jar" '[[ "$OUT" == *"args=-jar /orcpub.jar"* ]]'
check "the app can write its crash folder" '[[ "$OUT" == *"wrote=yes"* ]]'
check "the state folder belongs to app and is private" '[[ "$OUT" == *"state=app 750"* ]]'
check "so does the crash folder" '[[ "$OUT" == *"crash=app 750"* ]]'
check "a file already there keeps its owner" '[[ "$OUT" == *"keep=$(id -u)"* ]]'

printf '\n=== Results: %s tests, %s failures ===\n' "$TESTS" "$FAILURES"
[ "$FAILURES" -eq 0 ]
