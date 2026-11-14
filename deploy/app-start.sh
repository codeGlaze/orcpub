#!/bin/sh
# Starts the app. Run as root, it first prepares the app's state folder (crash files, unsent admin
# alerts), then runs Java as the `app` user, so the app itself never runs as root. Arguments are
# passed to `java -jar`.
#
# When the host's /var/log is mounted at /host-log (docker-compose.yaml, Swarm stacks), the state
# lives in /var/log/$APP_LOGS_NAME on the host (default orcpub), where admins look for logs: this
# creates that one subfolder and its alerts/ and crash/, gives only those to `app` (never
# recursively, never /var/log itself) and makes them private, then points /var/lib/orcpub/state at
# it. Without that mount the state stays in the container's /var/lib/orcpub/state.
set -e
STATE=/var/lib/orcpub/state
if [ "$(id -u)" = "0" ]; then
  if [ -d /host-log ]; then
    NAME="${APP_LOGS_NAME:-orcpub}"
    case "$NAME" in
      ""|.|..|*/*|*[!A-Za-z0-9._-]*)
        echo "app-start: APP_LOGS_NAME='$NAME' is not a plain folder name; using orcpub" >&2
        NAME=orcpub ;;
    esac
    HOST_STATE="/host-log/$NAME"
    for dir in "$HOST_STATE" "$HOST_STATE/alerts" "$HOST_STATE/crash"; do
      mkdir -p "$dir"
      chown app:app "$dir"
      chmod 750 "$dir"
    done
    # rmdir removes only empty folders, so nothing already in the image's state folder is lost.
    if ! su-exec app test -w "$HOST_STATE/crash"; then
      echo "app-start: the app cannot reach /var/log/$NAME (is the host's /var/log closed to other users?); keeping its files inside the container" >&2
    elif [ -L "$STATE" ] || rmdir "$STATE/alerts" "$STATE/crash" "$STATE" 2>/dev/null; then
      ln -sfn "$HOST_STATE" "$STATE"
    else
      echo "app-start: $STATE is not empty; keeping it there instead of /var/log/$NAME" >&2
    fi
  else
    echo "app-start: crash files and unsent alerts are kept inside the container ($STATE) and go when it is replaced; to keep them in /var/log/orcpub on the host, mount - /var/log:/host-log on this service" >&2
    for dir in "$STATE" "$STATE/alerts" "$STATE/crash"; do
      mkdir -p "$dir"
      chown app:app "$dir"
      chmod 750 "$dir"
    done
  fi
  exec su-exec app java -jar "$@"
fi
exec java -jar "$@"
