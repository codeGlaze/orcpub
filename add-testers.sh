#!/usr/bin/env bash
# Seed test accounts from dev/test-accounts.edn into Datomic.
# Checks for Datomic, tries existing nREPL, falls back to fresh REPL (no Garden).
# Logs output to /tmp/add-testers-YYYYMMDD-HHMMSS.log
#
# Exit codes:
#   0 - Success
#   1 - Error (Datomic not running, Clojure exception, etc.)
set -e
set -o pipefail  # Catch errors in pipes
cd "$(dirname "$0")" || exit 1

DATOMIC_PORT=4334
NREPL_PORT=7888
LOG_FILE="/tmp/add-testers-$(date +%Y%m%d-%H%M%S).log"

# Spinner for visual feedback during slow operations (only in TTY)
SPINNER_PID=""
IS_TTY=false
[[ -t 1 ]] && IS_TTY=true

spin() {
  local spinchars='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
  local i=0
  while true; do
    printf "\r  %s %s" "${spinchars:i++%${#spinchars}:1}" "$1"
    sleep 0.1
  done
}

start_spinner() {
  if $IS_TTY; then
    spin "$1" &
    SPINNER_PID=$!
    # Hide cursor
    printf "\033[?25l"
  else
    # Non-TTY: just print a static message
    echo "  $1"
  fi
}

stop_spinner() {
  if [[ -n "$SPINNER_PID" ]]; then
    kill "$SPINNER_PID" 2>/dev/null || true
    wait "$SPINNER_PID" 2>/dev/null || true
    SPINNER_PID=""
    # Clear spinner line and show cursor
    printf "\r\033[K\033[?25h"
  fi
}

# Ensure spinner stops and cursor restored on exit
trap stop_spinner EXIT

# The Clojure command to run (uses updated user.clj functions that read EDN by default)
# Wrapped in try-catch to ensure errors are visible
# Shell script watches for SUCCESS/WARNING/FATAL ERROR and sends SIGINT to terminate
read -r -d '' CLOJURE_CMD <<'CLOJURE' || true
(try
  (println "\n---------------------------")
  (println "Checking test accounts...")
  (doseq [{:keys [username email exists?]} (list-test-accounts)]
    (println (format "  %-15s %-30s %s" username email (if exists? "EXISTS" "MISSING"))))
  (println "\nEnsuring accounts exist and are verified...")
  (ensure-test-accounts!)
  (println "\nFinal status:")
  (let [results (list-test-accounts)
        all-exist? (every? :exists? results)]
    (doseq [{:keys [username email exists?]} results]
      (println (format "  %-15s %-30s %s" username email (if exists? "✓ EXISTS" "✗ ERROR"))))
    (println "---------------------------")
    (if all-exist?
      (println "\n✓ SUCCESS: All test accounts are ready!")
      (println "\n✗ WARNING: Some accounts could not be created")))
  (catch Exception e
    (println "\n✗ FATAL ERROR:")
    (println (.getMessage e))
    (.printStackTrace e)))
CLOJURE

# Check if a port is open (works without nc)
port_open() {
  (echo > /dev/tcp/localhost/"$1") 2>/dev/null
}

# Check if Datomic is running
check_datomic() {
  if port_open "$DATOMIC_PORT"; then
    return 0
  else
    echo "ERROR: Datomic transactor not running on port $DATOMIC_PORT"
    echo "Start it with: cd lib/datomic-free-0.9.5703 && ./bin/transactor config/working-transactor.properties"
    return 1
  fi
}

# Check if nREPL is available
check_nrepl() {
  port_open "$NREPL_PORT"
}

# Run via existing nREPL connection
run_via_nrepl() {
  echo "Connecting to existing nREPL on port $NREPL_PORT..."
  echo "Log file: $LOG_FILE"
  lein repl :connect localhost:"$NREPL_PORT" <<< "$CLOJURE_CMD" 2>&1 | tee "$LOG_FILE"
}

# Run via fresh REPL (no-prep profile skips Garden for faster startup)
# Filters JVM shutdown noise from display but keeps it in log
run_via_fresh_repl() {
  echo "Log file: $LOG_FILE"

  # Start spinner while REPL initializes
  start_spinner "Starting REPL (this takes 15-30 seconds)..."

  # Run REPL, capture all output to log file only (not to terminal)
  {
    echo "$CLOJURE_CMD"
    echo "(System/exit 0)"
  } | lein with-profile +no-prep repl > "$LOG_FILE" 2>&1 &
  local repl_pid=$!

  # Wait for REPL to be ready (look for our output starting)
  while ! grep -q "Checking test accounts" "$LOG_FILE" 2>/dev/null; do
    if ! kill -0 $repl_pid 2>/dev/null; then
      # Process died before we saw expected output
      stop_spinner
      echo "REPL process exited unexpectedly"
      cat "$LOG_FILE"
      return 1
    fi
    sleep 0.5
  done

  # REPL is running, stop spinner
  stop_spinner
  echo ""

  # Wait for process to finish
  wait $repl_pid 2>/dev/null || true

  # Display filtered output from log
  sed 's/\x1b\[[0-9;]*[a-zA-Z]//g' "$LOG_FILE" | grep -v \
    -e "SocketException" \
    -e "nrepl.transport" \
    -e "java.util.concurrent" \
    -e "java.lang.Thread.run" \
    -e "Bye for now" \
    -e "^\[" \
    -e "^user=>" \
    -e "^  #_=>" \
    -e "WARNING:.*namespace" \
    -e "SLF4J:" \
    -e "nREPL server started" \
    -e "REPL-y" \
    -e "Clojure 1\." \
    -e "OpenJDK" \
    -e "Docs:" \
    -e "find-doc" \
    -e "Source:" \
    -e "Javadoc:" \
    -e "Exit:" \
    -e "Results:" \
    -e "^nil$" \
    -e "^)[^-]" \
    -e "^)$" \
    -e "^$" \
    || true
}

# Check log file for real errors (not the expected exit noise or code snippets)
check_for_errors() {
  local errors
  # Look for actual errors, excluding:
  # - Expected SocketException from clean JVM exit
  # - Our own "✗ ERROR" status markers
  # - nREPL transport messages
  # - The Clojure source code we piped in (catch Exception, FATAL ERROR in println)
  # - REPL prompt lines
  # - "exception in *e" from REPL help text
  errors=$(grep -iE "Exception|Error|FATAL|failed|refused" "$LOG_FILE" 2>/dev/null \
    | grep -v "SocketException.*lost its connection" \
    | grep -v "✗ ERROR" \
    | grep -v "✗ FATAL ERROR" \
    | grep -v "nrepl.transport" \
    | grep -v "catch Exception" \
    | grep -v "println.*FATAL" \
    | grep -v "exception in \*e" \
    | grep -v "^  #_=>" \
    | grep -v "^user=>" \
    || true)

  if [[ -n "$errors" ]]; then
    echo ""
    echo "⚠️  ERRORS DETECTED:"
    echo "----------------------------------------"
    echo "$errors"
    echo "----------------------------------------"
    echo "See full log: $LOG_FILE"
    return 1
  fi
  return 0
}

# Main
echo "=========================================="
echo "  OrcPub Test Account Seeder"
echo "=========================================="

check_datomic || exit 1

if check_nrepl; then
  run_via_nrepl
else
  echo "No nREPL found on port $NREPL_PORT, starting fresh REPL..."
  run_via_fresh_repl
fi

echo ""
echo "Log saved to: $LOG_FILE"

# Check for unexpected errors
check_for_errors || exit 1
