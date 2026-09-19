#!/usr/bin/env bash
# Regenerate docs/kb/topic-index.md with whatever runtime this machine has.
#
#   docs/kb/tools/topic-index.sh            regenerate
#   docs/kb/tools/topic-index.sh --check    exit 1 if the index is out of date
#   TOPIC_INDEX_RUNTIME=node docs/kb/tools/topic-index.sh    force one runtime
#
# Why this exists: the original generator was invoked through Leiningen, which is
# not installed in the containers agents run in, so the index silently went stale.
# Three implementations produce byte-identical output; this picks one that works.
# Preference order is by how reliably the runtime is present, not by quality.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../.." || exit 2
[ -d docs/kb ] || { echo "error: run from inside the repository" >&2; exit 2; }

TOOLS=docs/kb/tools
want="${TOPIC_INDEX_RUNTIME:-}"

have() { command -v "$1" >/dev/null 2>&1; }

run_python()  { python3 "$TOOLS/topic_index.py" "$@"; }
run_node()    { node "$TOOLS/topic_index.js" "$@"; }
# :paths [] keeps dev/ off the classpath -- Clojure auto-loads dev/user.clj from it,
# which pulls in the whole dev system and fails outside a full checkout.
run_clojure() { clojure -Sdeps '{:paths []}' -M \
                  -e '(load-file "dev/orcpub/topic_index.clj") (orcpub.topic-index/-main)' "$@"; }
run_lein()    { lein with-profile +tools run -m orcpub.topic-index "$@"; }

pick() {
  case "$want" in
    python|python3) have python3 && { echo python; return; } ;;
    node)           have node    && { echo node;   return; } ;;
    clojure)        have clojure && { echo clojure; return; } ;;
    lein)           have lein    && { echo lein;   return; } ;;
    "")             ;;
    *) echo "error: unknown TOPIC_INDEX_RUNTIME '$want'" >&2; exit 2 ;;
  esac
  [ -n "$want" ] && { echo "error: requested runtime '$want' is not installed" >&2; exit 2; }

  if   have python3; then echo python
  elif have node;    then echo node
  elif have clojure; then echo clojure
  elif have lein;    then echo lein
  else
    echo "error: none of python3, node, clojure or lein found" >&2
    exit 2
  fi
}

runtime="$(pick)"

# --check is implemented in the python and node ports only; the Clojure original
# always writes. Say so rather than silently regenerating when asked to verify.
if [ "${1:-}" = "--check" ] && { [ "$runtime" = clojure ] || [ "$runtime" = lein ]; }; then
  echo "error: --check needs python3 or node ($runtime cannot verify without writing)" >&2
  exit 2
fi

echo "topic-index: using $runtime" >&2
"run_$runtime" "$@"
