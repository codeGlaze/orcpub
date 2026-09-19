#!/usr/bin/env bash
# Regenerate docs/kb/topic-index.md.
#
#   docs/kb/tools/topic-index.sh            regenerate
#   docs/kb/tools/topic-index.sh --check    exit 1 if the index is out of date
#   docs/kb/tools/topic-index.sh --parity   prove every available runtime agrees
#   TOPIC_INDEX_RUNTIME=clojure ...         force one runtime
#
# Why this exists: the generator used to be invoked as
# `lein with-profile +tools run -m orcpub.topic-index`, and Leiningen is not
# installed in the containers agents run in, so the index silently went stale for
# a week. The Leiningen requirement was never real — the generator depends on
# nothing but its own file.
#
# topic_index.py is canonical: standard library only, present everywhere, and the
# fastest to run. dev/orcpub/topic_index.clj predates it and is kept because this
# is a Clojure repo and test/clj/orcpub/topic_index_coverage_test.clj cites it;
# it produces identical output, which --parity checks.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../.." || exit 2
[ -d docs/kb ] || { echo "error: run from inside the repository" >&2; exit 2; }

TOOLS=docs/kb/tools
OUT=docs/kb/topic-index.md
want="${TOPIC_INDEX_RUNTIME:-}"

have() { command -v "$1" >/dev/null 2>&1; }

run_python()  { python3 "$TOOLS/topic_index.py" "$@"; }
# :paths [] keeps dev/ off the classpath — Clojure auto-loads dev/user.clj from
# it, which pulls in the whole dev system and fails outside a full checkout.
run_clojure() { clojure -Sdeps '{:paths []}' -M \
                  -e '(load-file "dev/orcpub/topic_index.clj") (orcpub.topic-index/-main)' "$@"; }
run_lein()    { lein with-profile +tools run -m orcpub.topic-index "$@"; }

available() {
  have python3 && echo python
  have clojure && echo clojure
  have lein    && echo lein
  # Explicit success: the last `have` fails whenever that runtime is absent, and
  # under `set -e` a function returning non-zero takes the whole script with it.
  return 0
}

pick() {
  if [ -n "$want" ]; then
    case "$want" in
      python|python3) have python3 && { echo python; return; } ;;
      clojure)        have clojure && { echo clojure; return; } ;;
      lein)           have lein    && { echo lein;    return; } ;;
      *) echo "error: unknown TOPIC_INDEX_RUNTIME '$want' (python, clojure, lein)" >&2; exit 2 ;;
    esac
    echo "error: requested runtime '$want' is not installed" >&2; exit 2
  fi
  if   have python3; then echo python
  elif have clojure; then echo clojure
  elif have lein;    then echo lein
  else
    echo "error: need python3 (preferred), or clojure/lein as a fallback" >&2
    exit 2
  fi
}

# --parity: regenerate with every runtime present and diff the results. Two
# implementations that are never compared are two implementations that drift.
if [ "${1:-}" = "--parity" ]; then
  runtimes=$(available)
  [ -n "$runtimes" ] || { echo "error: no runtime available" >&2; exit 2; }
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
  saved="$tmp/saved.md"; [ -f "$OUT" ] && cp "$OUT" "$saved"
  ref=""; rc=0
  for rt in $runtimes; do
    "run_$rt" >/dev/null 2>&1 || { echo "  $rt: FAILED TO RUN"; rc=1; continue; }
    cp "$OUT" "$tmp/$rt.md"
    printf '  %-8s %8s bytes  %s\n' "$rt" "$(wc -c < "$tmp/$rt.md")" \
           "$(sha256sum "$tmp/$rt.md" | cut -c1-16)"
    if [ -z "$ref" ]; then ref="$rt"
    elif ! cmp -s "$tmp/$ref.md" "$tmp/$rt.md"; then
      echo "  MISMATCH: $rt differs from $ref"
      diff "$tmp/$ref.md" "$tmp/$rt.md" | head -20
      rc=1
    fi
  done
  [ -f "$saved" ] && cp "$saved" "$OUT"
  [ $rc -eq 0 ] && echo "  all runtimes agree"
  exit $rc
fi

runtime="$(pick)"

# --check is implemented in the python port only; the Clojure original always
# writes. Say so rather than silently regenerating when asked to verify.
if [ "${1:-}" = "--check" ] && [ "$runtime" != python ]; then
  echo "error: --check needs python3 ($runtime cannot verify without writing)" >&2
  exit 2
fi

echo "topic-index: using $runtime" >&2
"run_$runtime" "$@"
