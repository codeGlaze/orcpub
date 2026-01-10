#!/usr/bin/env bash

# --- Configurable variables ---
DATOMIC_VERSION="1.0.7482"
DATOMIC_DIR="lib/com/datomic/datomic-pro/$DATOMIC_VERSION"
PROPERTIES_FILE="config/samples/dev-transactor-template.properties"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Allow overriding LOG_DIR via environment
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"
mkdir -p "$LOG_DIR"
TRANSACTOR_LOG="$LOG_DIR/datomic-transactor.log"
TRANSACTOR_PID="$LOG_DIR/datomic-transactor.pid"

SERVICE="${1:-datomic}"

# Helper to start datomic
start_datomic() {
    echo "Starting $SERVICE..."
    (cd "$DATOMIC_DIR" && nohup bin/transactor "$PROPERTIES_FILE" > "$TRANSACTOR_LOG" 2>&1 & echo $! > "$TRANSACTOR_PID")
    echo "$SERVICE started. PID: $(cat "$TRANSACTOR_PID" 2>/dev/null || echo '<unknown>')"
    echo "Transactor logs: $TRANSACTOR_LOG"
    sleep 2
}

# Helper to get transactor PIDs (robust)
get_transactor_pids() {
    ps -eo pid,comm,args --no-headers \
        | grep -iE 'transactor|datomic' \
        | grep -v grep \
        | awk '{print $1}'
}

# Main loop
echo "Polling for running $SERVICE processes..."

while true; do
    # Header
    printf "%-30s %-10s\n" "NAME" "PID"
    printf "%-30s %-10s\n" "------------------------------" "----------"

    # Process list
    PIDS=$(get_transactor_pids)
    ps -eo pid,comm,args --no-headers \
        | grep -iE 'transactor|datomic' \
        | grep -v grep \
        | awk '{ printf "%-30s %-10s\n", $2, $1 }'

    if [ -z "$PIDS" ]; then
        echo
        echo "No $SERVICE processes detected. Auto-starting..."
        start_datomic
        continue
    fi

    echo
    echo "Menu:"
    echo "  1) Kill all $SERVICE processes"
    echo "  2) Kill specific PID"
    echo "  3) Start $SERVICE manually"
    echo "  4) Repoll"
    echo "  5) Abort"
    echo "  6) Open main dev menu (./start.sh)"
    read -p "Choose an option [1-6]: " CHOICE

    case "$CHOICE" in
        1)
            # Kill all
            if [ -z "$PIDS" ]; then
                echo "No $SERVICE processes found."
            else
                echo "Killing PIDs: $PIDS"
                kill $PIDS
            fi
            ;;
        2)
            read -p "Enter PID to kill: " KPID
            if kill "$KPID" 2>/dev/null; then
                echo "Killed PID $KPID"
            else
                echo "Failed to kill PID $KPID"
            fi
            ;;
        3)
            start_datomic
            ;;
        4)
            echo "Repolling..."
            ;;
        5)
            echo "Abort selected. Exiting."
            exit 0
            ;;
        6)
            if [ -x "./start.sh" ]; then
                echo "Opening main dev menu (./start.sh)..."
                ./start.sh
            else
                echo "./start.sh not found or not executable."
            fi
            ;;
        *)
            echo "Invalid option."
            ;;
    esac
    echo
    sleep 1
    echo "Polling for running $SERVICE processes..."
done
