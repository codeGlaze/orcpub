#!/bin/bash
# Cleanup duplicate VSCode/Clojure processes and show memory report

echo "=== Memory Report (Before) ==="
free -h
echo ""

echo "=== Heavy Processes ==="
ps aux --sort=-%mem | head -15 | awk '{printf "%-8s %-6s %5s%% %5sMB  %s\n", $1, $2, $4, int($6/1024), $11}'
echo ""

# Find and kill duplicate clojure-lsp processes (keep newest)
echo "=== Checking for duplicate clojure-lsp ==="
CLOJURE_LSP_PIDS=$(pgrep -f "clojure-lsp" | sort -n)
CLOJURE_LSP_COUNT=$(echo "$CLOJURE_LSP_PIDS" | wc -w)
if [ "$CLOJURE_LSP_COUNT" -gt 1 ]; then
    # Keep the last (newest) PID, kill the rest
    PIDS_TO_KILL=$(echo "$CLOJURE_LSP_PIDS" | head -n -1)
    echo "Found $CLOJURE_LSP_COUNT clojure-lsp processes, killing older ones: $PIDS_TO_KILL"
    echo "$PIDS_TO_KILL" | xargs -r kill 2>/dev/null
else
    echo "No duplicate clojure-lsp processes found"
fi

# Find and kill duplicate clj-kondo processes (keep newest)
echo ""
echo "=== Checking for duplicate clj-kondo ==="
CLJ_KONDO_PIDS=$(pgrep -f "clj-kondo" | sort -n)
CLJ_KONDO_COUNT=$(echo "$CLJ_KONDO_PIDS" | wc -w)
if [ "$CLJ_KONDO_COUNT" -gt 1 ]; then
    PIDS_TO_KILL=$(echo "$CLJ_KONDO_PIDS" | head -n -1)
    echo "Found $CLJ_KONDO_COUNT clj-kondo processes, killing older ones: $PIDS_TO_KILL"
    echo "$PIDS_TO_KILL" | xargs -r kill 2>/dev/null
else
    echo "No duplicate clj-kondo processes found"
fi

# Give processes time to die
sleep 1

echo ""
echo "=== Memory Report (After) ==="
free -h
echo ""

echo "=== Remaining Heavy Processes ==="
ps aux --sort=-%mem | head -10 | awk '{printf "%-8s %-6s %5s%% %5sMB  %s\n", $1, $2, $4, int($6/1024), $11}'
