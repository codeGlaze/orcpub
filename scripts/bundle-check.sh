#!/usr/bin/env bash
# Whether the compiled client and stylesheet are out of date, and building them when they are.
# Sourced from the repository root by scripts/start.sh and scripts/e2e/run.sh: nothing else rebuilds
# them, and the server serves what is on disk.

BUNDLE="${BUNDLE:-resources/public/js/compiled/orcpub.js}"
SHEET="${SHEET:-resources/public/css/compiled/styles.css}"

bundle_kind() {
  [ -f "$BUNDLE" ] || { echo none; return; }
  grep -q CLOSURE_UNCOMPILED_DEFINES "$BUNDLE" 2>/dev/null && echo dev || echo prod
}
# The folders the bundle compiles from, read from its own build config, never kept here: a list
# kept here once missed web/cljs, and startup edits were tested against an old bundle unwarned.
cljs_dirs() {
  local cfg=prod.cljs.edn; [ "$1" = dev ] && cfg=dev.cljs.edn
  sed -n 's/.*:watch-dirs *\[\([^]]*\)\].*/\1/p' "$cfg" | tr -d '"'
}
newer_sources() { local a="$1"; shift; find "$@" -name '*.clj*' -newer "$a" 2>/dev/null | head -5; }
# The build config, the externs it names and project.clj are inputs too: each can alter the bundle
# without touching a source file.
newer_build_inputs() {
  local cfg=prod.cljs.edn; [ "$1" = dev ] && cfg=dev.cljs.edn
  for f in "$cfg" project.clj $(sed -n 's/.*:externs *\[\([^]]*\)\].*/\1/p' "$cfg" | tr -d '"'); do
    [ -f "$f" ] && [ "$f" -nt "$BUNDLE" ] && echo "$f"
  done
}

# Whether process $1 exists and its command line mentions $2. A JVM started by lein names its tools
# only in the classpath, which is what this finds.
process_is() { [ -n "$1" ] && tr '\0' ' ' 2>/dev/null <"/proc/$1/cmdline" | grep -q "$2"; }

# Prints why the $1 (dev or prod) bundle needs building; prints nothing when it does not.
bundle_out_of_date() {
  local have; have="$(bundle_kind)"
  if [ "$have" = none ]; then echo "there is no bundle"
  elif [ "$have" != "$1" ]; then echo "a $have bundle is on disk"
  else
    # shellcheck disable=SC2046
    { newer_sources "$BUNDLE" $(cljs_dirs "$1"); newer_build_inputs "$1"; } | head -1
  fi
}

# Builds what the server is about to serve, only if it is out of date. Skips a part this folder's own
# watcher keeps current, and says when another folder holds Figwheel's port. Returns non-zero when a
# build fails: an old client served in silence is the failure this exists to stop.
ensure_fresh_build() {
  local need=prod holder="" pid="" why
  # As env/flag? reads it: only "true" counts. Unset is dev, because start.sh runs the :dev profile.
  case "$(printf '%s' "${DEV_MODE-true}" | LC_ALL=C tr '[:upper:]' '[:lower:]')" in true) need=dev ;; esac

  if port_in_use "$FIGWHEEL_PORT"; then
    pid="$(find_pids_by_port "$FIGWHEEL_PORT" | awk '{print $1}')"
    holder="$(readlink "/proc/$pid/cwd" 2>/dev/null)"
  fi
  if [ "$holder" = "$(pwd)" ] && process_is "$pid" figwheel; then
    if [ "$need" != dev ]; then
      log_error "Figwheel is building a development client here, but DEV_MODE asks for production. Stop Figwheel or set DEV_MODE=true."
      return 1
    fi
    log_info "Figwheel is running here and keeps the client built."
  else
    [ -n "$holder" ] && log_warn "Figwheel's port $FIGWHEEL_PORT is held from $holder, so it is not building this folder."
    why="$(bundle_out_of_date "$need")"
    if [ -n "$why" ]; then
      log_info "Building the $need client first ($why). The first build takes a few minutes."
      if [ "$need" = dev ]; then lein fig:build; else lein fig:prod; fi || {
        log_error "The client did not build, so the server was not started."; return 1; }
    fi
  fi

  if process_is "$(cat "${LOG_DIR:-logs}/garden.pid" 2>/dev/null)" garden; then
    log_info "Garden is running and keeps the stylesheet built."
  elif [ ! -f "$SHEET" ] || [ -n "$(newer_sources "$SHEET" src/clj/orcpub/styles)" ]; then
    log_info "Building the stylesheet first."
    lein garden once || { log_error "The stylesheet did not build, so the server was not started."; return 1; }
  fi
}
