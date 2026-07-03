#!/usr/bin/env bash
#
# Swarm compose generator — produces a standalone Swarm-ready compose file
# for import into Portainer or use with `docker stack deploy`.
#
# Sourced by the `run` script. Depends on:
#   - SCRIPT_DIR, SCRIPT_NAME, ENV_FILE (set by run)
#   - color_* variables (set by run)
#   - info(), warn(), change(), error() helpers (set by run)
#   - read_env_val() (set by run)

# shellcheck disable=SC2154  # color_*, SCRIPT_DIR etc. defined by sourcing script (run)

SWARM_COMPOSE="${SCRIPT_DIR}/docker-compose.swarm.yaml"
SWARM_PORTAINER_ENV="${SCRIPT_DIR}/.env.portainer"

# Additional color codes (extends run's palette)
color_bold=$'\033[1m'
color_dim=$'\033[2m'

# ---------------------------------------------------------------------------
# extract_swarm_config — parse existing Swarm compose into shell variables
# ---------------------------------------------------------------------------
# Sets _swarm_* variables for use by generate_swarm_compose and
# print_swarm_summary. Returns 1 if no existing file found.

extract_swarm_config() {
  local existing="$1"
  [ -f "$existing" ] || return 1

  # Stack name
  _swarm_stack_name=$(grep -E '^name:' "$existing" 2>/dev/null | head -1 | sed 's/^name:[[:space:]]*//' | tr -d '\r' || true)

  # Service names — top-level keys under services:
  _swarm_datomic_svc=$(sed -n '/^services:/,/^[a-z]/{ /^  [a-z].*:$/p }' "$existing" | grep -i 'datomic' | head -1 | sed 's/://;s/^[[:space:]]*//' || true)
  _swarm_app_svc=$(sed -n '/^services:/,/^[a-z]/{ /^  [a-z].*:$/p }' "$existing" | grep -iv 'datomic' | head -1 | sed 's/://;s/^[[:space:]]*//' || true)

  local d_svc="${_swarm_datomic_svc:-datomic}"
  local a_svc="${_swarm_app_svc:-orcpub}"

  # Images
  _swarm_datomic_image=$(sed -n "/^  ${d_svc}:/,/^  [a-z]/{ /image:/p }" "$existing" | head -1 | sed 's/.*image:[[:space:]]*//' | tr -d '\r' || true)
  _swarm_app_image=$(sed -n "/^  ${a_svc}:/,/^  [a-z]/{ /image:/p }" "$existing" | head -1 | sed 's/.*image:[[:space:]]*//' | tr -d '\r' || true)

  # Traefik labels
  _swarm_traefik_labels=()
  while IFS= read -r line; do
    [ -n "$line" ] && _swarm_traefik_labels+=("$line")
  done < <(grep -E '^\s*-?\s*traefik\.' "$existing" 2>/dev/null | sed 's/^[[:space:]]*-[[:space:]]*//' || true)

  # Networks — top-level block
  _swarm_networks=()
  _swarm_networks_external=()
  while IFS= read -r line; do
    local net_name
    net_name=$(echo "$line" | sed 's/://;s/^[[:space:]]*//')
    [ -z "$net_name" ] && continue
    _swarm_networks+=("$net_name")
  done < <(sed -n '/^networks:/,/^[a-z]/{/^  [a-z]/p}' "$existing" 2>/dev/null || true)

  for net in "${_swarm_networks[@]}"; do
    if sed -n "/^  ${net}:/,/^  [a-z]/p" "$existing" 2>/dev/null | grep -q 'external.*true'; then
      _swarm_networks_external+=("$net")
    fi
  done

  # Volumes — top-level block
  _swarm_volumes=()
  _swarm_volumes_external=()
  while IFS= read -r line; do
    local vol_name
    vol_name=$(echo "$line" | sed 's/://;s/^[[:space:]]*//')
    [ -z "$vol_name" ] && continue
    _swarm_volumes+=("$vol_name")
  done < <(sed -n '/^volumes:/,/^[a-z]/{/^  [a-z]/p}' "$existing" 2>/dev/null || true)

  for vol in "${_swarm_volumes[@]}"; do
    if sed -n "/^  ${vol}:/,/^  [a-z]/p" "$existing" 2>/dev/null | grep -q 'external.*true'; then
      _swarm_volumes_external+=("$vol")
    fi
  done

  # Bind mounts (host path lines starting with /)
  _swarm_bind_mounts_datomic=()
  while IFS= read -r line; do
    [ -n "$line" ] && _swarm_bind_mounts_datomic+=("$line")
  done < <(sed -n "/^  ${d_svc}:/,/^  [a-z]/{/volumes:/,/^[[:space:]]*[a-z]/{/^\s*-\s*\//p}}" "$existing" 2>/dev/null | sed 's/^[[:space:]]*-[[:space:]]*//' || true)

  _swarm_bind_mounts_app=()
  while IFS= read -r line; do
    [ -n "$line" ] && _swarm_bind_mounts_app+=("$line")
  done < <(sed -n "/^  ${a_svc}:/,/^  [a-z]/{/volumes:/,/^[[:space:]]*[a-z]/{/^\s*-\s*\//p}}" "$existing" 2>/dev/null | sed 's/^[[:space:]]*-[[:space:]]*//' || true)

  # Resource limits
  _swarm_datomic_mem_limit=$(sed -n "/^  ${d_svc}:/,/^  [a-z]/{/limits:/,/reservations:/{/memory:/p}}" "$existing" | head -1 | sed 's/.*memory:[[:space:]]*//' | tr -d '\r' || true)
  _swarm_datomic_mem_reservation=$(sed -n "/^  ${d_svc}:/,/^  [a-z]/{/reservations:/,/[a-z].*:/{/memory:/p}}" "$existing" | tail -1 | sed 's/.*memory:[[:space:]]*//' | tr -d '\r' || true)
  _swarm_app_mem_limit=$(sed -n "/^  ${a_svc}:/,/^  [a-z]/{/limits:/,/reservations:/{/memory:/p}}" "$existing" | head -1 | sed 's/.*memory:[[:space:]]*//' | tr -d '\r' || true)
  _swarm_app_mem_reservation=$(sed -n "/^  ${a_svc}:/,/^  [a-z]/{/reservations:/,/[a-z].*:/{/memory:/p}}" "$existing" | tail -1 | sed 's/.*memory:[[:space:]]*//' | tr -d '\r' || true)

  # JVM settings
  _swarm_xms=$(sed -n "/^  ${d_svc}:/,/^  [a-z]/{/XMS:/p}" "$existing" | head -1 | sed 's/.*XMS:[[:space:]]*//' | tr -d '\r' || true)
  _swarm_xmx=$(sed -n "/^  ${d_svc}:/,/^  [a-z]/{/XMX:/p}" "$existing" | head -1 | sed 's/.*XMX:[[:space:]]*//' | tr -d '\r' || true)
  _swarm_java_opts=$(sed -n "/^  ${a_svc}:/,/^  [a-z]/{/JAVA_OPTS:/p}" "$existing" | head -1 | sed 's/.*JAVA_OPTS:[[:space:]]*//' | tr -d '\r' || true)

  # Per-service env vars
  _swarm_datomic_env=()
  while IFS= read -r line; do
    [ -n "$line" ] && _swarm_datomic_env+=("$line")
  done < <(sed -n "/^  ${d_svc}:/,/^  [a-z]/{/environment:/,/^[[:space:]]*[a-z]/{/^[[:space:]]*[A-Z]/p}}" "$existing" 2>/dev/null | sed 's/^[[:space:]]*//' || true)

  _swarm_app_env=()
  while IFS= read -r line; do
    [ -n "$line" ] && _swarm_app_env+=("$line")
  done < <(sed -n "/^  ${a_svc}:/,/^  [a-z]/{/environment:/,/^[[:space:]]*[a-z]/{/^[[:space:]]*[A-Z]/p}}" "$existing" 2>/dev/null | sed 's/^[[:space:]]*//' || true)

  # Detect transactor.properties bind mount
  _swarm_has_transactor_props=false
  for m in "${_swarm_bind_mounts_datomic[@]}"; do
    [[ "$m" == *transactor.properties* ]] && _swarm_has_transactor_props=true && break
  done

  # Healthcheck timeout (app — for change detection)
  _swarm_app_hc_timeout=$(sed -n "/^  ${a_svc}:/,/^  [a-z]/{/timeout:/p}" "$existing" | head -1 | sed 's/.*timeout:[[:space:]]*//' | tr -d '\r' || true)

  return 0
}

# ---------------------------------------------------------------------------
# generate_env_portainer — stripped .env for Portainer advanced mode paste
# ---------------------------------------------------------------------------

generate_env_portainer() {
  local src="$1" dst="$2"
  local _seen_keys=""
  : > "$dst"
  while IFS= read -r line; do
    # Strip comments, blanks, export prefix, quotes
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line// /}" ]] && continue
    line="${line#export }"
    # Skip Compose-only vars (irrelevant for Swarm stack deploy)
    local _key="${line%%=*}"
    [[ "$_key" == "COMPOSE_FILE" ]] && continue
    [[ "$_key" == "COMPOSE_PROJECT_NAME" ]] && continue
    # Deduplicate (keep first occurrence)
    if [[ "$_seen_keys" == *"|${_key}|"* ]]; then continue; fi
    _seen_keys="${_seen_keys}|${_key}|"
    # Strip surrounding quotes from value
    local _val="${line#*=}"
    _val="${_val#\'}" ; _val="${_val%\'}"
    _val="${_val#\"}" ; _val="${_val%\"}"
    printf '%s=%s\n' "$_key" "$_val" >> "$dst"
  done < "$src"
  chmod 600 "$dst"
}

# ---------------------------------------------------------------------------
# generate_transactor_reference — resolve template with .env values
# ---------------------------------------------------------------------------
# When the admin bind-mounts their own transactor.properties, template changes
# are invisible. This generates a .reference file showing what the current
# template would produce, so they can diff against their custom file.

generate_transactor_reference() {
  local template="${SCRIPT_DIR}/docker/transactor.properties.template"
  local output="${SCRIPT_DIR}/transactor.properties.reference"
  [ -f "$template" ] || return 1

  # Resolve ${VAR} and ${VAR:-default} from environment (sourced from .env)
  local line
  while IFS= read -r line; do
    # Skip comments and empty lines — pass through
    if [[ "$line" == \#* ]] || [ -z "$line" ]; then
      printf '%s\n' "$line"
      continue
    fi
    # Resolve ${VAR:-default} patterns
    while [[ "$line" =~ \$\{([A-Za-z_][A-Za-z0-9_]*)(:-)([^}]*)\} ]]; do
      local var="${BASH_REMATCH[1]}" def="${BASH_REMATCH[3]}"
      local val="${!var:-$def}"
      line="${line/\$\{${var}:-${def}\}/$val}"
    done
    # Resolve ${VAR} patterns (no default)
    while [[ "$line" =~ \$\{([A-Za-z_][A-Za-z0-9_]*)\} ]]; do
      local var="${BASH_REMATCH[1]}"
      local val="${!var:-}"
      line="${line/\$\{${var}\}/$val}"
    done
    printf '%s\n' "$line"
  done < "$template" > "$output"

  chmod 644 "$output"
}

# ---------------------------------------------------------------------------
# activate_swarm_secrets — uncomment secrets blocks in generated compose
# ---------------------------------------------------------------------------
# Called after `docker secret create` succeeds. Uncomments:
#   - Per-service secrets: references under each service
#   - Top-level secrets: declaration block

activate_swarm_secrets() {
  local compose_file="$1"
  [ -f "$compose_file" ] || return 1

  # Uncomment service-level secrets (indented "# secrets:" and "# - name")
  sed -i \
    -e 's/^    # secrets:$/    secrets:/' \
    -e 's/^    #   - \(.*_password\)$/      - \1/' \
    -e 's/^    #   - \(signature\)$/      - \1/' \
    "$compose_file"

  # Uncomment top-level secrets block
  sed -i \
    -e 's/^# secrets:$/secrets:/' \
    -e 's/^#   \(.*_password:\)$/  \1/' \
    -e 's/^#   \(signature:\)$/  \1/' \
    -e 's/^#     external: true$/    external: true/' \
    "$compose_file"
}

# ---------------------------------------------------------------------------
# generate_swarm_compose — write the Swarm-ready YAML file
# ---------------------------------------------------------------------------
# Call after extract_swarm_config() (upgrade) or with defaults (fresh).

generate_swarm_compose() {
  local output="$1"
  local is_upgrade="${2:-false}"

  # Initialize arrays if not set (fresh generation path)
  _swarm_networks=("${_swarm_networks[@]+"${_swarm_networks[@]}"}")
  _swarm_networks_external=("${_swarm_networks_external[@]+"${_swarm_networks_external[@]}"}")
  _swarm_volumes=("${_swarm_volumes[@]+"${_swarm_volumes[@]}"}")
  _swarm_volumes_external=("${_swarm_volumes_external[@]+"${_swarm_volumes_external[@]}"}")
  _swarm_bind_mounts_datomic=("${_swarm_bind_mounts_datomic[@]+"${_swarm_bind_mounts_datomic[@]}"}")
  _swarm_bind_mounts_app=("${_swarm_bind_mounts_app[@]+"${_swarm_bind_mounts_app[@]}"}")
  _swarm_traefik_labels=("${_swarm_traefik_labels[@]+"${_swarm_traefik_labels[@]}"}")
  _swarm_datomic_env=("${_swarm_datomic_env[@]+"${_swarm_datomic_env[@]}"}")
  _swarm_app_env=("${_swarm_app_env[@]+"${_swarm_app_env[@]}"}")

  # Stack name — use if/else to avoid nested ${} expansion bugs
  local stack_name datomic_svc app_svc datomic_image app_image
  [ -n "${_swarm_stack_name:-}" ]    && stack_name="$_swarm_stack_name"    || stack_name='${COMPOSE_PROJECT_NAME:-orcpub}'
  [ -n "${_swarm_datomic_svc:-}" ]   && datomic_svc="$_swarm_datomic_svc"  || datomic_svc="datomic"
  [ -n "${_swarm_app_svc:-}" ]       && app_svc="$_swarm_app_svc"          || app_svc="orcpub"
  [ -n "${_swarm_datomic_image:-}" ] && datomic_image="$_swarm_datomic_image" || datomic_image='${DATOMIC_IMAGE:-orcpub-datomic}'
  [ -n "${_swarm_app_image:-}" ]     && app_image="$_swarm_app_image"      || app_image='${ORCPUB_IMAGE:-orcpub-app}'

  # Resource limits
  local d_mem_limit d_mem_res a_mem_limit a_mem_res
  [ -n "${_swarm_datomic_mem_limit:-}" ]       && d_mem_limit="$_swarm_datomic_mem_limit"       || d_mem_limit='${DATOMIC_MEMORY_LIMIT:-2G}'
  [ -n "${_swarm_datomic_mem_reservation:-}" ]  && d_mem_res="$_swarm_datomic_mem_reservation"   || d_mem_res='${DATOMIC_MEMORY_RESERVATION:-1G}'
  [ -n "${_swarm_app_mem_limit:-}" ]           && a_mem_limit="$_swarm_app_mem_limit"           || a_mem_limit='${APP_MEMORY_LIMIT:-2G}'
  [ -n "${_swarm_app_mem_reservation:-}" ]      && a_mem_res="$_swarm_app_mem_reservation"       || a_mem_res='${APP_MEMORY_RESERVATION:-1G}'

  # JVM
  local xms xmx java_opts
  [ -n "${_swarm_xms:-}" ]       && xms="$_swarm_xms"       || xms='${XMS:--Xms1g}'
  [ -n "${_swarm_xmx:-}" ]       && xmx="$_swarm_xmx"       || xmx='${XMX:--Xmx1g}'
  [ -n "${_swarm_java_opts:-}" ] && java_opts="$_swarm_java_opts" || java_opts='${JAVA_OPTS:-}'

  # --- Build network blocks ---
  local app_networks="" datomic_networks="" net_block=""
  if [ "${#_swarm_networks[@]}" -gt 0 ] 2>/dev/null; then
    for net in "${_swarm_networks[@]}"; do
      app_networks="${app_networks}      - ${net}\n"
      # Datomic skips proxy/mail networks
      case "$net" in
        traefik*|postfix*|mail*) ;;
        *) datomic_networks="${datomic_networks}      - ${net}\n" ;;
      esac
    done
    net_block="networks:"
    for net in "${_swarm_networks[@]}"; do
      local is_ext=false
      for ext in "${_swarm_networks_external[@]}"; do
        [ "$ext" = "$net" ] && is_ext=true && break
      done
      if [ "$is_ext" = "true" ]; then
        net_block="${net_block}\n  ${net}:\n    external: true"
      else
        net_block="${net_block}\n  ${net}:\n    driver: overlay"
      fi
    done
  else
    app_networks="      - backend\n      # - traefik-public           # Uncomment if using Traefik"
    datomic_networks="      - backend"
    net_block="networks:\n  backend:\n    driver: overlay\n  # traefik-public:\n  #   external: true"
  fi

  # --- Build volume blocks ---
  local d_vol_mounts="" vol_block=""
  if [ "${#_swarm_volumes[@]}" -gt 0 ] 2>/dev/null; then
    # Re-read actual volume mount lines from existing file for datomic
    while IFS= read -r line; do
      [ -n "$line" ] && d_vol_mounts="${d_vol_mounts}      - ${line}\n"
    done < <(sed -n "/^  ${datomic_svc}:/,/^  [a-z]/{/volumes:/,/^[[:space:]]*[a-z]/{/^\s*-\s*[a-z]/p}}" "$SWARM_COMPOSE" 2>/dev/null | sed 's/^[[:space:]]*-[[:space:]]*//' || true)
    # Fallback if nothing found
    [ -z "$d_vol_mounts" ] && d_vol_mounts="      - ${_swarm_volumes[0]}:/data\n"

    vol_block="volumes:"
    for v in "${_swarm_volumes[@]}"; do
      local is_ext=false
      for ext in "${_swarm_volumes_external[@]}"; do
        [ "$ext" = "$v" ] && is_ext=true && break
      done
      if [ "$is_ext" = "true" ]; then
        vol_block="${vol_block}\n  ${v}:\n    external: true"
      else
        vol_block="${vol_block}\n  ${v}:"
      fi
    done
  else
    d_vol_mounts="      - orcpub_data:/data\n      - orcpub_logs:/log\n      - orcpub_backups:/backups"
    vol_block="volumes:\n  orcpub_data:\n    external: true\n  orcpub_logs:\n    external: true\n  orcpub_backups:\n    external: true"
  fi

  # Bind mounts
  local d_bind_mounts="" a_bind_mounts=""
  for m in "${_swarm_bind_mounts_datomic[@]+"${_swarm_bind_mounts_datomic[@]}"}"; do
    [ -n "$m" ] && d_bind_mounts="${d_bind_mounts}      - ${m}\n"
  done
  for m in "${_swarm_bind_mounts_app[@]+"${_swarm_bind_mounts_app[@]}"}"; do
    [ -n "$m" ] && a_bind_mounts="${a_bind_mounts}      - ${m}\n"
  done

  # --- Build env blocks ---
  # Define canonical env vars for each service (name:default pairs)
  local -a _d_env_defaults=(
    "ADMIN_PASSWORD:\${ADMIN_PASSWORD}"
    "DATOMIC_PASSWORD:\${DATOMIC_PASSWORD}"
    "ALT_HOST:\${ALT_HOST:-datomic}"
    "ENCRYPT_CHANNEL:\${ENCRYPT_CHANNEL:-true}"
    "XMS:${xms}"
    "XMX:${xmx}"
    "TZ:\${TZ:-America/Chicago}"
  )
  local -a _a_env_defaults=(
    "PORT:\${PORT:-8890}"
    "EMAIL_SERVER_URL:\${EMAIL_SERVER_URL:-}"
    "EMAIL_ACCESS_KEY:\${EMAIL_ACCESS_KEY:-}"
    "EMAIL_SECRET_KEY:\${EMAIL_SECRET_KEY:-}"
    "EMAIL_SERVER_PORT:\${EMAIL_SERVER_PORT:-587}"
    "EMAIL_FROM_ADDRESS:\${EMAIL_FROM_ADDRESS:-}"
    "EMAIL_ERRORS_TO:\${EMAIL_ERRORS_TO:-}"
    "EMAIL_SSL:\${EMAIL_SSL:-FALSE}"
    "EMAIL_TLS:\${EMAIL_TLS:-FALSE}"
    "SIGNATURE:\${SIGNATURE}"
    "TZ:\${TZ:-America/Chicago}"
    "DATOMIC_URL:\${DATOMIC_URL:-datomic:dev://datomic:4334/orcpub}"
    "DATOMIC_PASSWORD:\${DATOMIC_PASSWORD}"
    "JAVA_OPTS:${java_opts}"
    "CSP_POLICY:\${CSP_POLICY:-strict}"
  )

  # Build env blocks: carry forward old vars, append any new canonical vars missing from old
  local d_env_block="" a_env_block=""

  _build_env_block() {
    local block="" var_name default_val
    local -n _old_env=$1; shift
    local -n _defaults=$1; shift

    # Start with old vars if upgrading
    if [ "$is_upgrade" = "true" ] && [ "${#_old_env[@]}" -gt 0 ] 2>/dev/null; then
      for e in "${_old_env[@]}"; do
        block="${block}      ${e}\n"
      done
      # Append new vars not in old
      for pair in "${_defaults[@]}"; do
        var_name="${pair%%:*}"
        default_val="${pair#*:}"
        local found=false
        for e in "${_old_env[@]}"; do
          [[ "$e" == "${var_name}:"* ]] && found=true && break
        done
        [ "$found" = "false" ] && block="${block}      ${var_name}: ${default_val}\n"
      done
    else
      for pair in "${_defaults[@]}"; do
        var_name="${pair%%:*}"
        default_val="${pair#*:}"
        block="${block}      ${var_name}: ${default_val}\n"
      done
    fi
    printf '%s' "$block"
  }

  d_env_block=$(_build_env_block _swarm_datomic_env _d_env_defaults)
  a_env_block=$(_build_env_block _swarm_app_env _a_env_defaults)

  # Traefik labels
  local traefik_block=""
  if [ "${#_swarm_traefik_labels[@]}" -gt 0 ] 2>/dev/null; then
    traefik_block="      labels:"
    for label in "${_swarm_traefik_labels[@]}"; do
      traefik_block="${traefik_block}\n        - ${label}"
    done
  else
    traefik_block='      # --- Traefik labels (uncomment and customize) ---
      # labels:
      #   - traefik.enable=true
      #   - traefik.docker.network=traefik-public
      #   - traefik.http.routers.orcpub.rule=Host(`your.domain.com`)
      #   - traefik.http.routers.orcpub.entrypoints=websecure
      #   - traefik.http.routers.orcpub.tls=true
      #   - traefik.http.routers.orcpub.tls.certresolver=letsencrypt
      #   - traefik.http.services.orcpub.loadbalancer.server.port=${PORT:-8890}'
  fi

  # --- Write the file ---
  cat > "$output" <<SWARM_YAML
# Generated by ./${SCRIPT_NAME} --swarm on $(date -u +"%Y-%m-%d %H:%M:%S UTC")
# Import into Portainer or deploy with: docker stack deploy -c $(basename "$output") <stack>
name: ${stack_name}

services:
  ${datomic_svc}:
    image: ${datomic_image}
    environment:
$(printf '%b' "$d_env_block")
    volumes:
$(printf '%b' "${d_bind_mounts}${d_vol_mounts}")
    healthcheck:
      test: ["CMD-SHELL", "grep -q ':10EE ' /proc/net/tcp || grep -q ':10EE ' /proc/net/tcp6"]
      interval: 5s
      timeout: 3s
      retries: 30
      start_period: 40s
    networks:
$(printf '%b' "$datomic_networks")
    deploy:
      resources:
        limits:
          memory: ${d_mem_limit}
        reservations:
          memory: ${d_mem_res}
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 5
        window: 120s
      update_config:
        parallelism: 1
        delay: 10s
        order: stop-first
        failure_action: rollback
      rollback_config:
        parallelism: 1
        order: stop-first

  ${app_svc}:
    image: ${app_image}
    environment:
$(printf '%b' "$a_env_block")
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://127.0.0.1:\${PORT:-8890}/health"]
      interval: 30s
      timeout: 5s
      retries: 30
      start_period: 60s$(
  # Only emit volumes: block if there are bind mounts
  if [ -n "$a_bind_mounts" ]; then
    printf '\n    volumes:\n%b' "$a_bind_mounts"
  fi
)
    networks:
$(printf '%b' "$app_networks")
    deploy:
      resources:
        limits:
          memory: ${a_mem_limit}
        reservations:
          memory: ${a_mem_res}
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 5
        window: 120s
      update_config:
        parallelism: 1
        delay: 10s
        order: start-first
        failure_action: rollback
      rollback_config:
        parallelism: 1
        order: stop-first
${traefik_block}
    # secrets:
    #   - datomic_password
    #   - signature

# --- Secrets (uncomment to use Swarm encrypted secrets) ---
# secrets:
#   datomic_password:
#     external: true
#   admin_password:
#     external: true
#   signature:
#     external: true

$(printf '%b' "$vol_block")

$(printf '%b' "$net_block")
SWARM_YAML

  chmod 644 "$output"
}

# ---------------------------------------------------------------------------
# print_swarm_summary — colorized full-file CLI output
# ---------------------------------------------------------------------------

print_swarm_summary() {
  local compose_file="$1"
  local is_upgrade="${2:-false}"
  local backup_file="${3:-}"

  # Safe defaults for unset variables
  : "${_swarm_has_transactor_props:=false}"
  _swarm_volumes=("${_swarm_volumes[@]+"${_swarm_volumes[@]}"}")

  printf '\n%s ╔══════════════════════════════════════════════════════════╗%s\n' "$color_bold" "$color_reset"
  printf '%s ║     Dungeon Master'\''s Vault — Swarm Compose Generator    ║%s\n' "$color_bold" "$color_reset"
  printf '%s ╚══════════════════════════════════════════════════════════╝%s\n\n' "$color_bold" "$color_reset"

  printf ' %s── Environment ──────────────────────────────────────────────%s\n' "$color_dim" "$color_reset"
  printf ' %s✓%s .env loaded\n' "$color_green" "$color_reset"
  if [ -n "$backup_file" ]; then
    printf ' %s✓%s .env backed up\n' "$color_green" "$color_reset"
  fi
  echo

  if [ "$is_upgrade" = "true" ] && [ -n "$backup_file" ]; then
    printf ' %s── Existing Swarm Compose Detected ──────────────────────────%s\n' "$color_dim" "$color_reset"
    printf ' %s✓%s Backed up: %s%s%s\n' "$color_green" "$color_reset" "$color_dim" "$backup_file" "$color_reset"
    echo

    printf ' %s── Legend ───────────────────────────────────────────────────%s\n' "$color_dim" "$color_reset"
    printf '   %s│%s white  %s│%s unchanged — your config, carried forward\n' "$color_dim" "$color_reset" "$color_dim" "$color_reset"
    printf '   %s│%s %scyan%s   %s│%s new line — upstream addition, default value\n' "$color_dim" "$color_reset" "$color_cyan" "$color_reset" "$color_dim" "$color_reset"
    printf '   %s│%s %sgreen%s  %s│%s new line — upstream addition, %syour%s value from .env\n' "$color_dim" "$color_reset" "$color_green" "$color_reset" "$color_dim" "$color_reset" "$color_bold" "$color_reset"
    printf '   %s│%s %syellow%s %s│%s changed — value differs from your previous config\n' "$color_dim" "$color_reset" "$color_yellow" "$color_reset" "$color_dim" "$color_reset"
    echo
  fi

  printf ' %s── %s ────────────────────────────────────────%s\n' "$color_dim" "$(basename "$compose_file")" "$color_reset"

  # Print file with color coding
  if [ "$is_upgrade" = "true" ] && [ -n "$backup_file" ] && [ -f "$backup_file" ]; then
    # Line-by-line color comparison against backup (old) file
    while IFS= read -r line; do
      local trimmed
      trimmed=$(echo "$line" | sed 's/^[[:space:]]*//')

      # Skip empty lines and comments — print dim
      if [ -z "$trimmed" ] || [[ "$trimmed" == \#* ]]; then
        printf '   %s%s%s\n' "$color_dim" "$line" "$color_reset"
        continue
      fi

      # Structural YAML keywords — print dim
      if [[ "$trimmed" =~ ^(name:|services:|volumes:|networks:|secrets:) ]] || \
         [[ "$trimmed" =~ ^[a-z_]+:$ ]]; then
        printf '   %s%s%s\n' "$color_dim" "$line" "$color_reset"
        continue
      fi

      # Check if line exists verbatim in old file → white (unchanged)
      if grep -qFx "$line" "$backup_file" 2>/dev/null; then
        printf '   %s\n' "$line"
        continue
      fi

      # Extract key and indentation for context-aware matching
      local key="" indent=""
      indent=$(echo "$line" | sed 's/[^ ].*//')
      if [[ "$trimmed" =~ ^([A-Za-z_][A-Za-z0-9_-]*): ]]; then
        key="${BASH_REMATCH[1]}"
      fi

      # If we have a key, check if same key at same indent existed with different value → yellow
      if [ -n "$key" ]; then
        local old_line
        old_line=$(grep -E "^${indent}${key}:" "$backup_file" 2>/dev/null | head -1 || true)
        if [ -n "$old_line" ]; then
          local old_val new_val
          old_val=$(echo "$old_line" | sed "s/^[[:space:]]*${key}:[[:space:]]*//" | tr -d '\r')
          new_val=$(echo "$trimmed" | sed "s/^${key}:[[:space:]]*//" | tr -d '\r')
          if [ "$old_val" != "$new_val" ]; then
            printf '   %s%s%s  %s# CHANGED (was: %s)%s\n' "$color_yellow" "$line" "$color_reset" "$color_yellow" "$old_val" "$color_reset"
            continue
          fi
        fi
      fi

      # New line — check if it references an env var the admin has set → green, else → cyan
      if [[ "$trimmed" =~ \$\{([A-Z_][A-Z0-9_]*)(:-)([^}]*)\} ]]; then
        local var_name="${BASH_REMATCH[1]}"
        local default_val="${BASH_REMATCH[3]}"
        local env_val
        env_val=$(read_env_val "$var_name" "$ENV_FILE" 2>/dev/null || true)
        if [ -n "$env_val" ] && [ "$env_val" != "$default_val" ]; then
          printf '   %s%s%s  %s# NEW — set to %s in .env%s\n' "$color_green" "$line" "$color_reset" "$color_green" "$env_val" "$color_reset"
        else
          local resolved="${default_val}"
          [ -n "$env_val" ] && resolved="$env_val"
          printf '   %s%s%s  %s# NEW — using default: %s%s\n' "$color_cyan" "$line" "$color_reset" "$color_cyan" "$resolved" "$color_reset"
        fi
        continue
      fi

      # New line without env var reference → cyan
      printf '   %s%s%s  %s# NEW%s\n' "$color_cyan" "$line" "$color_reset" "$color_cyan" "$color_reset"
    done < "$compose_file"
  else
    # Fresh generation — all lines are new (cyan)
    while IFS= read -r line; do
      printf '   %s%s%s\n' "$color_cyan" "$line" "$color_reset"
    done < "$compose_file"
  fi
  echo

  # Warnings
  if [ "$is_upgrade" = "true" ]; then
    local has_warnings=false

    [ "$_swarm_has_transactor_props" = "true" ] 2>/dev/null && has_warnings=true

    if [ "$has_warnings" = "true" ]; then
      printf ' %s── Warnings ────────────────────────────────────────────────%s\n' "$color_dim" "$color_reset"
      echo

      if [ "$_swarm_has_transactor_props" = "true" ] 2>/dev/null; then
        printf '   %s⚠%s %stransactor.properties%s is bind-mounted — template changes won'\''t apply.\n' "$color_yellow" "$color_reset" "$color_bold" "$color_reset"
        printf '     See: %stransactor.properties.reference%s\n\n' "$color_dim" "$color_reset"
      fi
    fi
  fi

  # Files
  printf ' %s── Files ───────────────────────────────────────────────────%s\n' "$color_dim" "$color_reset"
  printf '   %s%s%s    %s← ready to deploy%s\n' "$color_bold" "$compose_file" "$color_reset" "$color_green" "$color_reset"
  if [ -n "$backup_file" ]; then
    printf '   %s%s%s\n' "$color_dim" "$backup_file" "$color_reset"
  fi
  if [ "$_swarm_has_transactor_props" = "true" ] 2>/dev/null; then
    printf '   %s%s/transactor.properties.reference%s\n' "$color_dim" "$SCRIPT_DIR" "$color_reset"
  fi
  if [ -f "$SWARM_PORTAINER_ENV" ]; then
    printf '   %s%s%s    %s← paste into Portainer%s\n' "$color_bold" "$SWARM_PORTAINER_ENV" "$color_reset" "$color_green" "$color_reset"
  fi
  printf '   %s%s%s    %s← full .env%s\n' "$color_bold" "$ENV_FILE" "$color_reset" "$color_green" "$color_reset"
  echo

  # Deploy instructions (fresh only)
  if [ "$is_upgrade" != "true" ]; then
    printf ' %s── Deploy ───────────────────────────────────────────────────%s\n' "$color_dim" "$color_reset"
    echo
    printf '   %sPortainer:%s\n' "$color_bold" "$color_reset"
    printf '     1. Stacks → Add stack → Web editor\n'
    printf '     2. Paste contents of %s\n' "$(basename "$compose_file")"
    printf '     3. Environment variables → Advanced mode\n'
    printf '     4. Paste contents of %s\n' ".env.portainer"
    echo
    printf '   %sCLI:%s\n' "$color_bold" "$color_reset"
    printf '     set -a; source .env; set +a\n'
    printf '     docker stack deploy -c %s %s\n' "$(basename "$compose_file")" "${_swarm_stack_name:-orcpub}"
    echo

    # Volume pre-creation reminder
    if [ "${#_swarm_volumes[@]}" -eq 0 ] 2>/dev/null; then
      printf '   %sPre-create volumes:%s\n' "$color_bold" "$color_reset"
      printf '     docker volume create orcpub_data\n'
      printf '     docker volume create orcpub_logs\n'
      printf '     docker volume create orcpub_backups\n'
      echo
    fi
  fi
}
