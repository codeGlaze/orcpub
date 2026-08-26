#!/usr/bin/env bash
# Compile the ClojureScript test suite for Node and RUN it.
#
# `lein fig:test` only compiles — nothing executes the bundle, so a broken
# assertion still exits 0 and CI reports success. This runs it for real.
#
#   ./scripts/test-cljs.sh
#
# Exits non-zero if any test fails, so it is usable as a gate.
set -euo pipefail
cd "$(dirname "$0")/.."

lein run -m figwheel.main -- --build-once test-node

node dev/cljs-test-node.js target/test-node/test.js
