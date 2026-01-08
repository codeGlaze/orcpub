#!/usr/bin/env bash
set -euo pipefail

echo "Searching jars for datomic/api and jars containing 'datomic'..."
for f in .datomic/datomic-pro-*/lib/*.jar; do
  echo " checking: $f"
  if jar tf "$f" 2>/dev/null | grep -q 'datomic'; then
    echo "FOUND datomic entries in $f"
    jar tf "$f" | grep 'datomic' -m 20 || true
  fi
done
