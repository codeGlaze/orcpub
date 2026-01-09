#!/usr/bin/env bash
# Wrapper for backward compatibility: delegate to start-datomic-auto.sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$SCRIPT_DIR/start-datomic-auto.sh" "$@"
