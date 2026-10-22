#!/bin/sh
# Starts the app. Run as root, it first hands the state folder (APP_LOGS_DIR on the host, mounted
# at /var/lib/orcpub/state) and its own subfolders to the app user, then runs Java as that user, so
# the app itself never runs as root. Ownership is changed on those folders only, never recursively,
# so a mount pointed at the wrong place cannot be taken over. Arguments are passed to `java -jar`.
set -e
STATE=/var/lib/orcpub/state
if [ "$(id -u)" = "0" ]; then
  for dir in "$STATE" "$STATE/alerts" "$STATE/crash"; do
    mkdir -p "$dir"
    chown app:app "$dir"
    chmod 750 "$dir"
  done
  exec su-exec app java -jar "$@"
fi
exec java -jar "$@"
