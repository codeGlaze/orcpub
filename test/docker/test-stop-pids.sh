#!/usr/bin/env bash
# find_service_pids (scripts/common.sh): a service's PIDs are its PID file's process AND whatever holds
# its port, so `stop` ends the child JVM that lein forks and not just the wrapper. Uses a parent
# process (the PID file's) whose child holds a port, and a bystander on another port.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
t="$(mktemp -d)"
pids_to_kill=""
cleanup() { for p in $pids_to_kill; do kill "$p" 2>/dev/null; done; rm -rf "$t"; }
trap cleanup EXIT
export LOG_DIR="$t/logs"; mkdir -p "$LOG_DIR"
# A stand-in lsof that behaves like the real one for `-iTCP:PORT [-sTCP:LISTEN]` (the Docker image may
# lack lsof): with -sTCP:LISTEN only listeners match, without it clients connected to the port match too.
mkdir -p "$t/bin"
cat >"$t/bin/lsof" <<'SH'
#!/usr/bin/env bash
port=""; listen=no
for a in "$@"; do case "$a" in -iTCP:*) port="${a#-iTCP:}";; -i) ;; :*) port="${a#:}";; -sTCP:LISTEN) listen=yes;; esac; done
[ "$listen" = yes ] && flags=-tlnp || flags=-tanp
ss $flags 2>/dev/null | grep -E "[:.]${port}[[:space:]]" | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u
SH
chmod +x "$t/bin/lsof"
export PATH="$t/bin:$PATH"
source "$root/scripts/common.sh" >/dev/null 2>&1
set +e +o pipefail

free_port() { python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1])'; }
export HOLD='import socket,sys,time; s=socket.socket(); s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1); s.bind(("127.0.0.1",int(sys.argv[1]))); s.listen(1); time.sleep(300)'

port="$(free_port)"; other="$(free_port)"
while [ "$other" = "$port" ]; do other="$(free_port)"; done
# The parent stands in for the lein wrapper; its child holds the port.
bash -c 'python3 -c "$HOLD" "$1" & wait' _ "$port" >/dev/null 2>&1 </dev/null & parent=$!
bash -c 'python3 -c "$HOLD" "$1" & wait' _ "$other" >/dev/null 2>&1 </dev/null & bystander=$!
pids_to_kill="$parent $bystander"
for _ in $(seq 1 40); do (echo >"/dev/tcp/127.0.0.1/$port") 2>/dev/null && break; sleep 0.25; done
child="$(pgrep -P "$parent" | head -1)"
bystander_child="$(pgrep -P "$bystander" | head -1)"
pids_to_kill="$pids_to_kill $child $bystander_child"
echo "$parent" >"$LOG_DIR/svc.pid"

pass=0; bad=0
check() { if [ "$2" = "yes" ]; then pass=$((pass+1)); else bad=$((bad+1)); echo "FAIL $1"; fi; }
has() { case " $1 " in *" $2 "*) echo yes;; *) echo no;; esac; }

got="$(find_service_pids svc "$port" 'no-such-process-pattern-xyz')"
check "the PID file's process is found" "$(has "$got" "$parent")"
check "the child holding the port is found" "$(has "$got" "$child")"
check "a process on another port is not found" "$([ "$(has "$got" "$bystander_child")" = no ] && echo yes || echo no)"

# A client connected to the target port is not the service and must not be listed.
python3 -c 'import socket,sys,time; s=socket.create_connection(("127.0.0.1",int(sys.argv[1]))); time.sleep(300)' "$port" >/dev/null 2>&1 </dev/null & client=$!
pids_to_kill="$pids_to_kill $client"
sleep 1
got="$(find_service_pids svc "$port" 'no-such-process-pattern-xyz')"
check "a client connected to the port is not found" "$([ "$(has "$got" "$client")" = no ] && echo yes || echo no)"

rm -f "$LOG_DIR/svc.pid"
got="$(find_service_pids svc "$port" 'no-such-process-pattern-xyz')"
check "without a PID file the port holder is found" "$(has "$got" "$child")"

echo "$parent" >"$LOG_DIR/svc.pid"
for p in $(find_service_pids svc "$port" 'no-such-process-pattern-xyz'); do signal_pid "$p" TERM; done
sleep 1
check "stopping ends the child, so the port is free" "$(kill -0 "$child" 2>/dev/null && echo no || echo yes)"

echo "stop pids: $pass passed, $bad failed"
[ "$bad" -eq 0 ]
