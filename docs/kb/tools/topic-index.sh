#!/usr/bin/env bash
# Regenerate docs/kb/topic-index.md.
#
#   docs/kb/tools/topic-index.sh            regenerate
#   docs/kb/tools/topic-index.sh --check    exit 1 if the index is out of date
#
# Why this exists: the generator used to be invoked as
# `lein with-profile +tools run -m orcpub.topic-index`, and Leiningen is not
# installed in the containers agents run in, so the index silently went stale for
# a week. The Leiningen requirement was never real — the generator depends on
# nothing but its own file.
#
# This is a thin wrapper kept because it is the entry point the KB documents cite.
# The generator is topic_index.py: standard library only, present everywhere.
#
# There used to be a Clojure implementation at dev/orcpub/topic_index.clj, kept in
# parallel and cross-checked with a --parity mode. It was removed once the only
# thing citing it — test/clj/orcpub/topic_index_coverage_test.clj — was itself
# superseded by `kb lint`, which makes the same guarantees without a JVM. A second
# implementation nothing depends on is a second thing to keep in step.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../.." || exit 2
[ -d docs/kb ] || { echo "error: run from inside the repository" >&2; exit 2; }

command -v python3 >/dev/null 2>&1 || {
  echo "error: python3 is required (standard library only, no packages needed)" >&2
  exit 2
}

exec python3 docs/kb/tools/topic_index.py "$@"
