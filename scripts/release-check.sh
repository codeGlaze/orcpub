#!/usr/bin/env bash
# What this release changes for an existing install, checked against the install's own files.
# Sourced by ./run for --check and --upgrade. Prints only what needs doing to the console and
# writes every note, plain text, to logs/upgrade-report.txt. Needs run's warn/info helpers,
# read_env_val, and scripts/swarm.sh for the stack checks.

# _release_service_block FILE SERVICE
# Prints the lines of SERVICE's block in a compose or stack FILE, without comments.
_release_service_block() {
  sed -n "/^  ${2}:/,/^  [a-z]/p" "$1" 2>/dev/null | grep -v '^[[:space:]]*#'
}

# _release_stack_value FILE SERVICE NAME
# Prints NAME's value in SERVICE's environment in a stack FILE, as written (may be ${NAME:-x}).
_release_stack_value() {
  _release_service_block "$1" "$2" | sed -n "s/^[[:space:]]*${3}:[[:space:]]*//p" | head -1 | tr -d '"'
}

# _release_effective VALUE ENV_VALUE
# The value Swarm ends up with: ENV_VALUE when set, else the default inside VALUE's ${NAME:-x}.
_release_effective() {
  local written="$1" from_env="$2"
  [ -n "$from_env" ] && { printf '%s' "$from_env"; return; }
  case "$written" in
    '${'*':-'*'}') written="${written#*:-}"; printf '%s' "${written%\}}" ;;
    *) printf '%s' "$written" ;;
  esac
}

# release_check ENV_FILE STACK_FILE REPORT_FILE
# Checks this release's deployment changes against ENV_FILE and the Swarm STACK_FILE (either may
# be missing), prints the ones needing action, writes all notes to REPORT_FILE. Sets
# RELEASE_ACTIONS to the number needing action and appends each to WARNING_MSGS.
release_check() {
  local env_file="$1" stack="$2" report="$3"
  local -a todo=() notes=()
  # shellcheck disable=SC2034  # read by run after the call
  RELEASE_ACTIONS=0

  local alt_env th_env java_opts logs_name
  alt_env=$(read_env_val ALT_HOST "$env_file" 2>/dev/null)
  th_env=$(read_env_val TRANSACTOR_HOST "$env_file" 2>/dev/null)
  java_opts=$(read_env_val JAVA_OPTS "$env_file" 2>/dev/null)
  logs_name=$(read_env_val APP_LOGS_NAME "$env_file" 2>/dev/null); logs_name="${logs_name:-orcpub}"

  if [ -f "$stack" ]; then
    local th alt
    if declare -F swarm_stack_lacks_transactor_host >/dev/null && swarm_stack_lacks_transactor_host "$stack"; then
      todo+=("Swarm: $(basename "$stack") sets no TRANSACTOR_HOST, so Datomic will not start. Run ./run --swarm, or add TRANSACTOR_HOST: 0.0.0.0 to the datomic service.")
    else
      th=$(_release_effective "$(_release_stack_value "$stack" datomic TRANSACTOR_HOST)" "$th_env")
      case "$th" in
        0.0.0.0) notes+=("Swarm: Datomic serves on 0.0.0.0, as Swarm needs.") ;;
        *) todo+=("Swarm: Datomic would serve on '$th' (from .env TRANSACTOR_HOST); Swarm needs 0.0.0.0. Remove TRANSACTOR_HOST from .env.") ;;
      esac
    fi
    alt=$(_release_effective "$(_release_stack_value "$stack" datomic ALT_HOST)" "$alt_env")
    case "$alt" in
      datomic) notes+=("Swarm: the app reaches Datomic as 'datomic' (ALT_HOST), as Swarm needs.") ;;
      *) todo+=("Swarm: ALT_HOST is '$alt'; on Swarm the app cannot reach Datomic that way. Remove ALT_HOST from .env (the stack defaults it to datomic), or set it to datomic.") ;;
    esac
    if _release_service_block "$stack" orcpub | grep -q ':/host-log'; then
      notes+=("Logs: crash files and unsent alerts are kept in /var/log/${logs_name} on the host.")
    else
      todo+=("Logs: the orcpub service does not mount /var/log, so crash files stay inside the container. Run ./run --swarm, or add - /var/log:/host-log to its volumes.")
    fi
    if _release_service_block "$stack" orcpub | grep -q '^    user:'; then
      todo+=("Swarm: the orcpub service sets user:. Remove it: the container now starts as root, prepares /var/log/${logs_name}, then runs the app as 'app'.")
    fi
  fi

  local f
  for f in "${SCRIPT_DIR}"/docker-compose.override.yaml "${SCRIPT_DIR}"/docker-compose.override.yml; do
    [ -f "$f" ] || continue
    if _release_service_block "$f" orcpub | grep -q '^    user:'; then
      todo+=("Compose: $(basename "$f") sets user: on orcpub. Remove it: the container now starts as root, prepares its folders, then runs the app as 'app'.")
    fi
  done

  if [ -n "$java_opts" ]; then
    notes+=("JAVA_OPTS now reaches Java (it was ignored before): '$java_opts'. Check it is still what you want.")
  fi
  notes+=("Logs: Docker logs are capped (LOG_MAX_SIZE, LOG_MAX_FILES; 10m x 5 by default).")
  notes+=("Datomic prints 'transactor: host=... alt-host=...' when it starts, the addresses it gives the app.")
  notes+=("New app settings: the app's startup log lists them under NEW IN, with their values.")

  # shellcheck disable=SC2034  # read by run after the call
  RELEASE_ACTIONS=${#todo[@]}
  mkdir -p "$(dirname "$report")" 2>/dev/null
  {
    echo "Changed in this release: what it means for this install ($(date '+%Y-%m-%d %H:%M'))"
    echo ""
    if [ "${#todo[@]}" -gt 0 ]; then
      echo "To do:"; printf '  - %s\n' "${todo[@]}"; echo ""
    else
      echo "To do: nothing."; echo ""
    fi
    echo "Notes:"; printf '  - %s\n' "${notes[@]}"
    echo ""
    echo "Details: docs/DOCKER.md, and CHANGELOG.md for everything else in the release."
  } > "$report" 2>/dev/null

  echo ""
  info "Changed in this release:"
  local t
  for t in "${todo[@]+"${todo[@]}"}"; do
    warn "  $t"
    WARNING_MSGS+=("$t")
  done
  [ "${#todo[@]}" -eq 0 ] && info "  Nothing to do for this install."
  info "  Full notes: ${report#"${SCRIPT_DIR}"/}"
  return 0
}
