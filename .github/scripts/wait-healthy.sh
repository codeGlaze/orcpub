#!/usr/bin/env bash
# Waits up to 10 minutes for the app to answer /health, under Docker Compose ("compose") or a Swarm
# stack named orcpub ("swarm"). Exits 0 when it answers, 1 when it never does.
set -uo pipefail
MODE="${1:-compose}"
for i in $(seq 1 120); do
  if [ "$MODE" = swarm ]; then
    C=$(docker ps -q -f label=com.docker.swarm.service.name=orcpub_orcpub | head -1)
  else
    C=$(docker compose ps -q orcpub 2>/dev/null)
  fi
  if [ -n "$C" ] && docker exec "$C" wget -q --spider "http://127.0.0.1:${PORT:-8890}/health" 2>/dev/null; then
    echo "app healthy after ~$((i * 5))s"
    exit 0
  fi
  sleep 5
done
echo "FAIL: the app never answered /health"
exit 1
