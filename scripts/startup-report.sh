#!/usr/bin/env bash
# Prints the app's startup report as the last thing on screen after ./run starts the server.
# Sourced by ./run. Needs run's info/warn helpers.

# _report_from_log
# Reads a log on stdin and prints the last startup report in it: from the rule above
# "orcpub started" through the rule below "This report is saved in" (or to the end when the
# report could not be saved). Prints the refusal instead when the app would not start.
_report_from_log() {
  awk '
    { line[NR] = $0 }
    /orcpub NOT STARTED/ { refused = NR }
    /orcpub started/     { start = NR; saved = 0 }
    /This report is saved in/ && start { saved = NR }
    END {
      if (refused > start) { for (i = refused; i <= refused + 4 && i <= NR; i++) print line[i]; exit }
      if (!start) exit
      last = saved ? saved + 1 : NR
      for (i = start - 1; i <= last && i <= NR; i++) if (i > 0) print line[i]
    }'
}

# show_startup_report MODE SINCE [TRIES]
# Waits for the app's report in its log (MODE compose or swarm; lines since SINCE, an RFC 3339
# time), checking every 5 seconds up to TRIES times (default 72, six minutes), then prints it.
# Returns 0 when a report or a refusal was shown, 1 when the app said nothing in time.
show_startup_report() {
  local mode="$1" since="$2" tries="${3:-72}" i out shown
  info "Waiting for the app to start (about 2 minutes); its startup report follows."
  for ((i = 0; i < tries; i++)); do
    if [ "$mode" = "swarm" ]; then
      out=$(docker service logs --raw --since "$since" orcpub_orcpub 2>&1 || true)
    else
      out=$(docker compose logs --no-log-prefix --since "$since" orcpub 2>&1 || true)
    fi
    if grep -q "orcpub NOT STARTED\|This report is saved in" <<<"$out"; then
      shown=$(_report_from_log <<<"$out")
      echo ""
      printf '%s\n' "$shown"
      return 0
    fi
    sleep 5
  done
  warn "The app has not printed its startup report yet. When it has, read it with:"
  if [ "$mode" = "swarm" ]; then
    warn "  docker service logs orcpub_orcpub | less"
  else
    warn "  docker compose logs orcpub | less"
  fi
  return 1
}
