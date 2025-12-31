#!/usr/bin/env bash
set -euo pipefail

# scripts/dev-monitor.sh
# Open a tmux session with windows to start/monitor server, figwheel, repl, and logs.
# If tmux is not installed, this will print an instruction and exit with code 2.

SESSION="orcpub"
SERVER_LOG="/tmp/orcpub-server.log"
FIGWHEEL_LOG="/tmp/figwheel.log"

if ! command -v tmux >/dev/null 2>&1; then
  cat <<'EOF'
Error: tmux not found. Install it in the devcontainer or locally and re-run:
  apt-get update && apt-get install -y --no-install-recommends tmux
Or use the VS Code Tasks instead (see .vscode/tasks.json).
EOF
  exit 2
fi

# Helper to build command that either tails logs (if up) or starts the process
server_cmd="bash -lc 'if timeout 1 bash -c \"</dev/tcp/localhost/8890\" >/dev/null 2>&1; then tail -F $SERVER_LOG; else lein with-profile +start-server repl; fi'"
figwheel_cmd="bash -lc 'if timeout 1 bash -c \"</dev/tcp/localhost/3449\" >/dev/null 2>&1; then tail -F $FIGWHEEL_LOG; else lein figwheel; fi'"
repl_cmd="bash -lc 'lein repl'"
logs_cmd="bash -lc 'tail -F $SERVER_LOG $FIGWHEEL_LOG'"

# Create session if not present
if tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "Attaching to existing tmux session '$SESSION'..."
  tmux attach -t "$SESSION"
  exit 0
fi

echo "Creating tmux session '$SESSION'..."
# Create first window (server)
tmux new-session -d -s "$SESSION" -n server $server_cmd
# Create figwheel window
tmux new-window -t "$SESSION" -n figwheel $figwheel_cmd
# Create repl window
tmux new-window -t "$SESSION" -n repl $repl_cmd
# Create logs window
tmux new-window -t "$SESSION" -n logs $logs_cmd

# Select the logs window on attach so user sees progress
tmux select-window -t "$SESSION":logs

# Attach session
tmux attach -t "$SESSION"
