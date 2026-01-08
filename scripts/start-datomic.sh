#!/usr/bin/env bash

# --- Configurable variables ---
DATOMIC_VERSION="1.0.7482"
DATOMIC_DIR="lib/com/datomic/datomic-pro/$DATOMIC_VERSION"
PROPERTIES_FILE="config/samples/dev-transactor-template.properties"

SERVICE="${1:-datomic}"

# Helper to start datomic
start_datomic() {
    echo "Starting $SERVICE..."
    (cd "$DATOMIC_DIR" && bin/transactor "$PROPERTIES_FILE" &)
    echo "$SERVICE started. Waiting for process to appear..."
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
    read -p "Choose an option [1-5]: " CHOICE

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
        *)
            echo "Invalid option."
            ;;
    esac
    echo
    sleep 1
    echo "Polling for running $SERVICE processes..."
done
