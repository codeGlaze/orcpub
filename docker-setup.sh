#!/usr/bin/env bash
#
# OrcPub / Dungeon Master's Vault — Docker Setup Script
#
# Prepares everything needed to run the application via Docker Compose:
#   1. Generates secure random passwords and a signing secret
#   2. Creates a .env file (or uses an existing one)
#   3. Generates self-signed SSL certificates (if missing)
#   4. Creates required directories (data, logs, deploy/homebrew)
#
# Usage:
#   ./docker-setup.sh            # Interactive mode — prompts for optional values
#   ./docker-setup.sh --auto     # Non-interactive — accepts all defaults
#   ./docker-setup.sh --help     # Show usage
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

color_green='\033[0;32m'
color_yellow='\033[1;33m'
color_red='\033[0;31m'
color_cyan='\033[0;36m'
color_reset='\033[0m'

info()  { printf '%s[INFO]%s  %s\n' "$color_green" "$color_reset" "$*"; }
warn()  { printf '%s[WARN]%s  %s\n' "$color_yellow" "$color_reset" "$*"; }
error() { printf '%s[ERROR]%s %s\n' "$color_red" "$color_reset" "$*" >&2; }
header() { printf '\n%s=== %s ===%s\n\n' "$color_cyan" "$*" "$color_reset"; }

generate_password() {
  # Generate a URL-safe random password (no special chars that break URLs/YAML)
  local length="${1:-24}"
  if command -v openssl &>/dev/null; then
    openssl rand -base64 "$((length * 2))" | tr -d '/+=' | head -c "$length"
  elif [ -r /dev/urandom ]; then
    tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$length"
  else
    error "Cannot generate random password: no openssl or /dev/urandom available"
    exit 1
  fi
}

prompt_value() {
  local prompt_text="$1"
  local default_value="$2"
  local result

  if [ "${AUTO_MODE:-false}" = "true" ]; then
    echo "$default_value"
    return
  fi

  if [ -n "$default_value" ]; then
    read -rp "${prompt_text} [${default_value}]: " result
    echo "${result:-$default_value}"
  else
    read -rp "${prompt_text}: " result
    echo "$result"
  fi
}

usage() {
  cat <<'USAGE'
Usage: ./docker-setup.sh [OPTIONS]

Options:
  --auto        Non-interactive mode; accept all defaults
  --force       Overwrite existing .env file
  --help        Show this help message

Examples:
  ./docker-setup.sh              # Interactive setup
  ./docker-setup.sh --auto       # Quick setup with generated defaults
  ./docker-setup.sh --auto --force  # Regenerate everything from scratch
USAGE
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------

AUTO_MODE=false
FORCE_MODE=false

for arg in "$@"; do
  case "$arg" in
    --auto)  AUTO_MODE=true ;;
    --force) FORCE_MODE=true ;;
    --help)  usage; exit 0 ;;
    *)
      error "Unknown option: $arg"
      usage
      exit 1
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

header "Dungeon Master's Vault — Docker Setup"

# ---- Step 1: .env file ---------------------------------------------------

if [ -f "$ENV_FILE" ] && [ "$FORCE_MODE" = "false" ]; then
  info "Existing .env file found. Skipping generation (use --force to overwrite)."
else
  # Source existing .env (if any) so current values become defaults for prompts
  if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    . "$ENV_FILE"
  fi

  header "Database Passwords"

  # Generate defaults but let user override
  DEFAULT_ADMIN_PW="$(generate_password 24)"
  DEFAULT_DATOMIC_PW="$(generate_password 24)"
  DEFAULT_SIGNATURE="$(generate_password 32)"

  ADMIN_PASSWORD=$(prompt_value "Datomic admin password" "$DEFAULT_ADMIN_PW")
  DATOMIC_PASSWORD=$(prompt_value "Datomic application password" "$DEFAULT_DATOMIC_PW")
  SIGNATURE=$(prompt_value "JWT signing secret (20+ chars)" "$DEFAULT_SIGNATURE")

  header "Application"

  PORT=$(prompt_value "Application port" "8890")
  EMAIL_SERVER_URL=$(prompt_value "SMTP server URL (leave empty to skip email)" "")
  EMAIL_ACCESS_KEY=""
  EMAIL_SECRET_KEY=""
  EMAIL_SERVER_PORT="587"
  EMAIL_FROM_ADDRESS=""
  EMAIL_ERRORS_TO=""
  EMAIL_SSL="FALSE"
  EMAIL_TLS="FALSE"

  if [ -n "$EMAIL_SERVER_URL" ]; then
    EMAIL_ACCESS_KEY=$(prompt_value "SMTP username" "")
    EMAIL_SECRET_KEY=$(prompt_value "SMTP password" "")
    EMAIL_SERVER_PORT=$(prompt_value "SMTP port" "587")
    EMAIL_FROM_ADDRESS=$(prompt_value "From email address" "no-reply@orcpub.com")
    EMAIL_ERRORS_TO=$(prompt_value "Error notification email" "")
    EMAIL_SSL=$(prompt_value "Use SSL? (TRUE/FALSE)" "FALSE")
    EMAIL_TLS=$(prompt_value "Use TLS? (TRUE/FALSE)" "FALSE")
  fi

  header "Initial Admin User"

  # Check environment / existing .env for pre-set values
  INIT_ADMIN_USER="${INIT_ADMIN_USER:-}"
  INIT_ADMIN_EMAIL="${INIT_ADMIN_EMAIL:-}"
  INIT_ADMIN_PASSWORD="${INIT_ADMIN_PASSWORD:-}"

  if [ -n "$INIT_ADMIN_USER" ] && [ -n "$INIT_ADMIN_EMAIL" ] && [ -n "$INIT_ADMIN_PASSWORD" ]; then
    info "Using admin user from environment: ${INIT_ADMIN_USER} <${INIT_ADMIN_EMAIL}>"
  elif [ "${AUTO_MODE}" = "true" ]; then
    info "No INIT_ADMIN_* variables set. Skipping admin user setup."
    info "Create users later with: ./docker-user.sh create ..."
  else
    info "Optionally create an initial admin account."
    info "You can skip this and create users later with ./docker-user.sh"
    echo ""
    INIT_ADMIN_USER=$(prompt_value "Admin username (leave empty to skip)" "")
    if [ -n "$INIT_ADMIN_USER" ]; then
      INIT_ADMIN_EMAIL=$(prompt_value "Admin email" "")
      INIT_ADMIN_PASSWORD=$(prompt_value "Admin password" "")
      if [ -z "$INIT_ADMIN_EMAIL" ] || [ -z "$INIT_ADMIN_PASSWORD" ]; then
        warn "Email and password are required. Skipping admin user setup."
        INIT_ADMIN_USER=""
        INIT_ADMIN_EMAIL=""
        INIT_ADMIN_PASSWORD=""
      fi
    fi
  fi

  info "Writing .env file..."

  cat > "$ENV_FILE" <<EOF
# ============================================================================
# Dungeon Master's Vault — Docker Environment Configuration
# Generated by docker-setup.sh on $(date -u +"%Y-%m-%d %H:%M:%S UTC")
# ============================================================================

# --- Application ---
PORT=${PORT}

# --- Datomic Database ---
# ADMIN_PASSWORD secures the Datomic admin interface
# DATOMIC_PASSWORD is used by the application to connect to Datomic
# The password in DATOMIC_URL must match DATOMIC_PASSWORD
ADMIN_PASSWORD=${ADMIN_PASSWORD}
DATOMIC_PASSWORD=${DATOMIC_PASSWORD}
DATOMIC_URL=datomic:dev://datomic:4334/orcpub?password=${DATOMIC_PASSWORD}

# --- Transactor Tuning ---
# These rarely need changing. See docker/transactor.properties.template.
ALT_HOST=127.0.0.1
ENCRYPT_CHANNEL=true
# ADMIN_PASSWORD_OLD=
# DATOMIC_PASSWORD_OLD=

# --- Security ---
# Secret used to sign JWT tokens (20+ characters recommended)
SIGNATURE=${SIGNATURE}

# --- Email (SMTP) ---
EMAIL_SERVER_URL=${EMAIL_SERVER_URL}
EMAIL_ACCESS_KEY=${EMAIL_ACCESS_KEY}
EMAIL_SECRET_KEY=${EMAIL_SECRET_KEY}
EMAIL_SERVER_PORT=${EMAIL_SERVER_PORT}
EMAIL_FROM_ADDRESS=${EMAIL_FROM_ADDRESS}
EMAIL_ERRORS_TO=${EMAIL_ERRORS_TO}
EMAIL_SSL=${EMAIL_SSL}
EMAIL_TLS=${EMAIL_TLS}

# --- SSL (Nginx) ---
# Set to 'true' after running snakeoil.sh or providing your own certs
# SSL_CERT_PATH=./deploy/snakeoil.crt
# SSL_KEY_PATH=./deploy/snakeoil.key

# --- Initial Admin User (optional) ---
# Set these to create an admin account on first run:
#   ./docker-user.sh init
# Safe to run multiple times — duplicates are skipped.
INIT_ADMIN_USER=${INIT_ADMIN_USER}
INIT_ADMIN_EMAIL=${INIT_ADMIN_EMAIL}
INIT_ADMIN_PASSWORD=${INIT_ADMIN_PASSWORD}
EOF

  chmod 600 "$ENV_FILE"
  info ".env file created at ${ENV_FILE} (permissions: 600)"
fi

# ---- Step 2: Directories -------------------------------------------------

header "Directories"

for dir in "${SCRIPT_DIR}/data" "${SCRIPT_DIR}/logs" "${SCRIPT_DIR}/backups" "${SCRIPT_DIR}/deploy/homebrew"; do
  if [ ! -d "$dir" ]; then
    mkdir -p "$dir"
    info "Created directory: ${dir#"${SCRIPT_DIR}"/}"
  else
    info "Directory exists:  ${dir#"${SCRIPT_DIR}"/}"
  fi
done

# ---- Step 3: SSL certificates --------------------------------------------

header "SSL Certificates"

CERT_FILE="${SCRIPT_DIR}/deploy/snakeoil.crt"
KEY_FILE="${SCRIPT_DIR}/deploy/snakeoil.key"

if [ -f "$CERT_FILE" ] && [ -f "$KEY_FILE" ]; then
  info "SSL certificates already exist. Skipping generation."
else
  if command -v openssl &>/dev/null; then
    info "Generating self-signed SSL certificate..."
    openssl req \
      -subj "/C=US/ST=State/L=City/O=OrcPub/OU=Dev/CN=localhost" \
      -x509 \
      -nodes \
      -days 365 \
      -newkey rsa:2048 \
      -keyout "$KEY_FILE" \
      -out "$CERT_FILE" \
      2>/dev/null
    info "SSL certificate generated (valid for 365 days)."
  else
    warn "openssl not found — cannot generate SSL certificates."
    warn "Install openssl and run: ./deploy/snakeoil.sh"
  fi
fi

# ---- Step 4: Validation --------------------------------------------------

header "Validation"

ERRORS=0

check_file() {
  local label="$1" path="$2"
  if [ -f "$path" ]; then
    info "  ${label}: OK"
  else
    warn "  ${label}: MISSING (${path})"
    ERRORS=$((ERRORS + 1))
  fi
}

check_dir() {
  local label="$1" path="$2"
  if [ -d "$path" ]; then
    info "  ${label}: OK"
  else
    warn "  ${label}: MISSING (${path})"
    ERRORS=$((ERRORS + 1))
  fi
}

# Validate DATOMIC_PASSWORD matches the password in DATOMIC_URL
if [ -f "$ENV_FILE" ]; then
  # Read specific values without polluting current shell namespace
  _env_datomic_pw=$(grep -m1 '^DATOMIC_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
  _env_datomic_url=$(grep -m1 '^DATOMIC_URL=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
  if [ -n "$_env_datomic_pw" ] && [ -n "$_env_datomic_url" ]; then
    if [[ "$_env_datomic_url" != *"password=${_env_datomic_pw}"* ]]; then
      warn "  DATOMIC_PASSWORD does not match the password in DATOMIC_URL"
      ERRORS=$((ERRORS + 1))
    else
      info "  DATOMIC_URL password: OK"
    fi
  fi
  unset _env_datomic_pw _env_datomic_url
fi

check_file ".env"                "$ENV_FILE"
check_file "docker-compose.yaml" "${SCRIPT_DIR}/docker-compose.yaml"
check_file "nginx.conf"          "${SCRIPT_DIR}/deploy/nginx.conf"
check_file "SSL certificate"     "$CERT_FILE"
check_file "SSL key"             "$KEY_FILE"
check_dir  "data/"               "${SCRIPT_DIR}/data"
check_dir  "logs/"               "${SCRIPT_DIR}/logs"
check_dir  "backups/"            "${SCRIPT_DIR}/backups"
check_dir  "deploy/homebrew/"    "${SCRIPT_DIR}/deploy/homebrew"

echo ""

if [ "$ERRORS" -gt 0 ]; then
  warn "Setup completed with ${ERRORS} warning(s). Review the items above."
else
  info "All checks passed!"
fi

# ---- Step 5: Next steps ---------------------------------------------------

header "Next Steps"

cat <<'NEXT'
1. Review your .env file and adjust values if needed.

2. Launch the application:
     docker compose up -d

3. Create your first user (once containers are running):
     ./docker-user.sh init                                 # uses admin from .env
     ./docker-user.sh create <username> <email> <password>  # or specify directly

4. Access the site at:
     https://localhost

5. Manage users later with:
     ./docker-user.sh list              # List all users
     ./docker-user.sh check <user>      # Check a user's status
     ./docker-user.sh verify <user>     # Verify an unverified user

6. To import homebrew content, place your .orcbrew file at:
     deploy/homebrew/homebrew.orcbrew

7. To build from source instead of pulling images:
     docker compose -f docker-compose-build.yaml build
     docker compose -f docker-compose-build.yaml up -d

For more details, see README.md.
NEXT
