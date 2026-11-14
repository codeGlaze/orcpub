#!/usr/bin/env bash
# Reads a .env file the way Docker Compose does, without running it as shell code.
#
#   . scripts/load-env.sh            # defines load_env
#   load_env /path/to/.env           # exports every KEY=value line in it
#
# Each line is NAME=value. Blank lines and lines starting with # are skipped, and a leading
# "export " is allowed. The value is everything after the first "=", taken as written: spaces,
# apostrophes and backticks are just characters, and nothing is run. One pair of matching
# surrounding quotes is removed. An unquoted value ends at " #" (an inline comment) and loses
# trailing spaces. Windows line endings are removed. A value in the file replaces one already in
# the environment.
#
# As in Compose, unquoted and double-quoted values expand $NAME, ${NAME}, ${NAME:-default} and
# ${NAME-default} from the environment (earlier lines included), and $$ is a literal $.
# Single-quoted values are left exactly as written.

# _env_expand TEXT: TEXT with $$, $NAME, ${NAME}, ${NAME:-default} and ${NAME-default} expanded as
# described above; prints the result. A $ that starts none of these is kept.
_env_expand() {
  local s="$1" out="" name def body
  while [ -n "$s" ]; do
    out+="${s%%\$*}"
    s="${s#"${s%%\$*}"}"
    [ -z "$s" ] && break
    if [ "${s:0:2}" = '$$' ]; then
      out+='$'; s="${s:2}"
    elif [ "${s:0:2}" = '${' ] && [[ "${s:2}" == *"}"* ]]; then
      body="${s:2}"; body="${body%%\}*}"; s="${s:$((${#body}+3))}"
      name="$body"; def=""
      case "$body" in
        *:-*) name="${body%%:-*}"; def="${body#*:-}" ;;
        *-*)  name="${body%%-*}";  def="${body#*-}" ;;
      esac
      if [[ "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
        case "$body" in
          *:-*) out+="${!name:-$def}" ;;
          *-*)  if [ -n "${!name+set}" ]; then out+="${!name}"; else out+="$def"; fi ;;
          *)    out+="${!name-}" ;;
        esac
      else
        out+="\${${body}}"
      fi
    elif [[ "$s" =~ ^\$([A-Za-z_][A-Za-z0-9_]*) ]]; then
      name="${BASH_REMATCH[1]}"; out+="${!name-}"; s="${s:$((${#name}+1))}"
    else
      out+='$'; s="${s:1}"
    fi
  done
  printf '%s' "$out"
}

# load_env FILE: exports each NAME=value line of FILE as described above; nothing in the file
# is executed. Lines that are not NAME=value are skipped. Returns 0, also when FILE is missing.
load_env() {
  local file="$1" line key val first rest
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    line="${line#"${line%%[![:space:]]*}"}"
    case "$line" in ''|'#'*) continue ;; esac
    line="${line#export }"
    case "$line" in *=*) ;; *) continue ;; esac
    key="${line%%=*}"
    key="${key%"${key##*[![:space:]]}"}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    val="${line#*=}"
    val="${val#"${val%%[![:space:]]*}"}"
    first="${val:0:1}"
    rest="${val:1}"
    if [ "$first" = "'" ] && [[ "$rest" == *"'"* ]]; then
      val="${rest%%"'"*}"
    elif [ "$first" = '"' ] && [[ "$rest" == *'"'* ]]; then
      val="$(_env_expand "${rest%%'"'*}")"
    else
      val="${val%%[[:space:]]#*}"
      val="${val%"${val##*[![:space:]]}"}"
      val="$(_env_expand "$val")"
    fi
    export "$key=$val"
  done < "$file"
  return 0
}
