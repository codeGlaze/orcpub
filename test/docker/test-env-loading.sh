#!/usr/bin/env bash
#
# Test scripts/load-env.sh, the .env reader every setup script uses.
#
#   1. Tricky values (apostrophes, spaces, quotes, $, #, backticks, Windows line endings) load to
#      the values Docker Compose gives a container. With COMPARE_WITH_DOCKER=1 the same file is
#      also run through a real container and compared.
#   2. Every fixture in test/docker/fixtures loads exactly as the old `. .env` did, so existing
#      files are unaffected.
#   3. .env.example with every example line uncommented loads, and no value keeps its quotes.
#
# Usage: ./test/docker/test-env-loading.sh        No Docker needed unless COMPARE_WITH_DOCKER=1.

set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

TESTS=0
FAILURES=0
pass() { TESTS=$((TESTS + 1)); printf 'PASS  %s\n' "$*"; }
fail() { TESTS=$((TESTS + 1)); FAILURES=$((FAILURES + 1)); printf 'FAIL  %s\n' "$*"; }

# load_into FILE: prints NAME=value for each name FILE sets, as load_env reads it, in a clean shell.
load_into() {
  env -i PATH="$PATH" bash -c '. "$1"; load_env "$2"; env' _ "$PROJECT_ROOT/scripts/load-env.sh" "$1" | sort
}

# source_into FILE: the same, as the old `set -a; . <(tr -d "\r" < FILE)` read it.
source_into() {
  env -i PATH="$PATH" bash -c 'set -a; . <(tr -d "\r" < "$1"); set +a; env' _ "$1" | sort
}

# ── 1. Tricky values ──────────────────────────────────────────────────────────
TRICKY="$TMP/tricky.env"
{
  echo "APOS=Dungeon Master's Vault"
  echo 'DQ="CI Vault"'
  echo "SQ='a b'"
  echo 'URL=datomic:dev://db:4334/orcpub?password=abc'
  echo 'HASH=abc#def'
  echo 'INLINE=value # comment'
  echo 'DQ_COMMENT="a b" # note'
  echo 'export EXP=bar'
  echo 'TRAIL=x   '
  echo 'EMPTY='
  echo 'DOLLAR=pa$$word'
  echo 'DQ_DOLLAR="x$$y"'
  echo "SQ_DOLLAR='x\$b'"
  echo 'REF=${URL}'
  echo 'DEF=${NOPE:-fallback}'
  echo 'BACKTICK=`date`'
  echo '# COMMENTED=no'
  echo 'not a setting line'
  printf 'CRLF=yes\r\n'
} > "$TRICKY"

declare -A EXPECT=(
  [APOS]="Dungeon Master's Vault" [DQ]="CI Vault" [SQ]="a b"
  [URL]="datomic:dev://db:4334/orcpub?password=abc" [HASH]="abc#def" [INLINE]="value"
  [DQ_COMMENT]="a b" [EXP]="bar" [TRAIL]="x" [EMPTY]="" [DOLLAR]='pa$word' [DQ_DOLLAR]='x$y'
  [SQ_DOLLAR]='x$b' [REF]="datomic:dev://db:4334/orcpub?password=abc" [DEF]="fallback"
  [BACKTICK]='`date`' [CRLF]="yes"
)

LOADED="$(load_into "$TRICKY")"
for k in "${!EXPECT[@]}"; do
  got="$(grep -m1 "^${k}=" <<<"$LOADED" | cut -d= -f2-)"
  if grep -q "^${k}=" <<<"$LOADED" && [ "$got" = "${EXPECT[$k]}" ]; then
    pass "tricky: $k"
  else
    fail "tricky: $k expected [${EXPECT[$k]}] got [$got]"
  fi
done
if grep -q '^COMMENTED=' <<<"$LOADED"; then fail "tricky: a commented line was loaded"; else pass "tricky: comments skipped"; fi

if [ "${COMPARE_WITH_DOCKER:-0}" = "1" ]; then
  # Compose refuses a .env with a malformed line, which load_env skips; leave that line out.
  D="$TMP/compose"; mkdir -p "$D"; grep -v '^not a setting line$' "$TRICKY" > "$D/.env"
  {
    echo "services:"; echo "  t:"; echo "    image: alpine:3.20"; echo "    environment:"
    for k in "${!EXPECT[@]}"; do echo "      $k: \"\${$k}\""; done
  } > "$D/docker-compose.yaml"
  ACTUAL="$(cd "$D" && docker compose run --rm -q t env 2>/dev/null | tr -d '\r' | sort)"
  for k in "${!EXPECT[@]}"; do
    a="$(grep -m1 "^${k}=" <<<"$ACTUAL" | cut -d= -f2-)"
    l="$(grep -m1 "^${k}=" <<<"$LOADED" | cut -d= -f2-)"
    if [ "$a" = "$l" ]; then pass "docker agrees: $k"; else fail "docker: $k container [$a] load_env [$l]"; fi
  done
fi

# ── 2. Existing fixtures load as before ──────────────────────────────────────
for f in "$SCRIPT_DIR"/fixtures/*.env; do
  name="$(basename "$f")"
  if [ "$(load_into "$f")" = "$(source_into "$f")" ]; then
    pass "fixture unchanged: $name"
  else
    fail "fixture differs: $name"
    diff <(source_into "$f") <(load_into "$f") | head -10
  fi
done

# ── 3. .env.example with every example uncommented ───────────────────────────
EXAMPLE="$TMP/example.env"
sed -E 's/^# ?([A-Z][A-Z0-9_]*=)/\1/' "$PROJECT_ROOT/.env.example" > "$EXAMPLE"
EXPECTED_NAMES="$(grep -oE '^[A-Z][A-Z0-9_]*=' "$EXAMPLE" | tr -d '=' | sort -u)"
LOADED_EX="$(load_into "$EXAMPLE")"
missing=""
for k in $EXPECTED_NAMES; do grep -q "^${k}=" <<<"$LOADED_EX" || missing="$missing $k"; done
if [ -z "$missing" ]; then pass ".env.example: every example line loads"; else fail ".env.example: not loaded:$missing"; fi
if grep -qE "^[A-Z0-9_]+=[\"'].*[\"']$" <<<"$LOADED_EX"; then
  fail ".env.example: a value kept its quotes"
else
  pass ".env.example: no value keeps its quotes"
fi

# ── 4. The scripts load through it ───────────────────────────────────────────
# A copy of the repo's scripts beside a .env whose value has an apostrophe and spaces: each loader
# must give the value as written. The old `. .env` stopped with a shell error on this line.
REPO="$TMP/repo"; mkdir -p "$REPO/scripts"
cp "$PROJECT_ROOT/scripts/load-env.sh" "$PROJECT_ROOT/scripts/common.sh" "$REPO/scripts/"
cp "$PROJECT_ROOT/run" "$REPO/"
printf "APP_NAME=Dungeon Master's Vault\r\nPORT=8890\n" > "$REPO/.env"
want="Dungeon Master's Vault"
got="$(cd "$REPO" && env -i PATH="$PATH" HOME="$TMP" bash -c '. scripts/common.sh >/dev/null 2>&1; printf %s "$APP_NAME"')"
if [ "$got" = "$want" ]; then pass "scripts/common.sh loads it"; else fail "scripts/common.sh got [$got]"; fi
got="$(cd "$REPO" && env -i PATH="$PATH" bash -c 'SCRIPT_DIR="$PWD"; source <(sed -n "/^if \[ ! -f \"\${SCRIPT_DIR}\/scripts\/load-env.sh\" \]/,/^}/p" run); source_env .env; printf %s "$APP_NAME"')"
if [ "$got" = "$want" ]; then pass "run's source_env loads it"; else fail "run's source_env got [$got]"; fi

# run without scripts/ beside it says so, instead of carrying on without its settings.
mkdir -p "$TMP/alone" && cp "$PROJECT_ROOT/run" "$TMP/alone/"
msg="$(cd "$TMP/alone" && bash ./run --check 2>&1)"; code=$?
if [ "$code" -ne 0 ] && grep -q "missing a file it needs: scripts/load-env.sh" <<<"$msg" && grep -q "git clone" <<<"$msg"; then
  pass "run alone stops and says it needs scripts/"
else
  fail "run alone: exit $code, said: $(head -2 <<<"$msg")"
fi

# No tracked script runs a .env file any more; everything goes through load_env.
OLD="$(cd "$PROJECT_ROOT" && git ls-files | grep -vE '^(docs/|test/docker/test-env-loading\.sh)' \
       | xargs grep -nE '^[^#]*(\. |source )<\(tr -d|^[^#]*(\. |source )"?\$[A-Z_]*\}?/?\.env"?\s*$' 2>/dev/null)"
if [ -z "$OLD" ]; then pass "no script runs a .env directly"; else fail "a script still runs .env: $OLD"; fi

printf '\n=== Results: %s tests, %s failures ===\n' "$TESTS" "$FAILURES"
[ "$FAILURES" -eq 0 ]
