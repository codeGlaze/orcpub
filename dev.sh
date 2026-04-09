#!/usr/bin/env bash
#
# dev.sh — Run lein commands inside a Docker container.
#
# Uses the same base image as the Dockerfile build stage, with your source
# bind-mounted so changes are visible immediately.
#
# Usage:
#   ./dev.sh test                    # lein test
#   ./dev.sh compile                 # lein compile
#   ./dev.sh cljsbuild once dev      # lein cljsbuild once dev
#   ./dev.sh repl                    # lein repl
#   ./dev.sh --raw bash              # drop into shell (no lein prefix)
#
# First run pulls the base image and installs deps (~2-3 min).
# Subsequent runs reuse the cached container volume.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE="clojure:temurin-21-lein-alpine"
CONTAINER_NAME="orcpub-dev"
DEPS_VOLUME="orcpub-m2-cache"

# Parse --raw flag: skip "lein" prefix, run command directly
RAW=false
if [[ "${1:-}" == "--raw" ]]; then
    RAW=true
    shift
fi

if [[ $# -eq 0 ]]; then
    echo "Usage: ./dev.sh <lein-subcommand> [args...]"
    echo "       ./dev.sh --raw <command> [args...]"
    echo ""
    echo "Examples:"
    echo "  ./dev.sh test              # lein test"
    echo "  ./dev.sh compile           # lein compile"
    echo "  ./dev.sh --raw bash        # interactive shell"
    exit 1
fi

# Build the command
if [[ "$RAW" == "true" ]]; then
    CMD=("$@")
else
    CMD=("lein" "$@")
fi

# One-time setup: install Datomic peer jar into the cached Maven volume.
# The vendor jars in ./lib/ need to be available, and the Datomic Pro peer
# library needs maven-install from the downloaded distribution.
setup_deps() {
    echo "==> First-run setup: installing vendor dependencies..."

    # Copy vendor jars (pdfbox snapshots etc.) into the Maven cache
    docker run --rm \
        -v "${SCRIPT_DIR}:/orcpub:ro" \
        -v "${DEPS_VOLUME}:/root/.m2" \
        -w /orcpub \
        "${IMAGE}" \
        bash -c 'cp -rn /orcpub/lib/* /root/.m2/repository/ 2>/dev/null || true'

    # Download lein deps (cached in volume for future runs)
    docker run --rm \
        -v "${SCRIPT_DIR}:/orcpub" \
        -v "${DEPS_VOLUME}:/root/.m2" \
        -w /orcpub \
        "${IMAGE}" \
        lein deps

    # Mark setup complete
    docker volume inspect "${DEPS_VOLUME}" > /dev/null 2>&1 && \
        docker run --rm -v "${DEPS_VOLUME}:/root/.m2" "${IMAGE}" \
            touch /root/.m2/.orcpub-deps-ready
}

# Check if deps are already set up
deps_ready() {
    docker run --rm -v "${DEPS_VOLUME}:/root/.m2" "${IMAGE}" \
        test -f /root/.m2/.orcpub-deps-ready 2>/dev/null
}

# Ensure the Maven cache volume exists
docker volume create "${DEPS_VOLUME}" > /dev/null 2>&1 || true

if ! deps_ready; then
    setup_deps
fi

# Run the command with source mounted read-write, Maven cache from volume
exec docker run --rm \
    -v "${SCRIPT_DIR}:/orcpub" \
    -v "${DEPS_VOLUME}:/root/.m2" \
    -w /orcpub \
    -e LEIN_ROOT=1 \
    --name "${CONTAINER_NAME}-$$" \
    "${IMAGE}" \
    "${CMD[@]}"
