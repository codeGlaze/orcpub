#!/usr/bin/env bash
# Scans every library the app ships for known security flaws and fails on a critical or high one.
#
#   scripts/security-scan.sh          # from the repo root; needs lein, python3 and Docker
#
# Leiningen resolves the production classpath (the uberjar profile); scripts/flat-pom.py lists each
# library in a plain pom; OSV-Scanner checks that list against the OSV database (the advisories
# GitHub uses) without resolving anything itself, since its resolver cannot read Clojars.
# osv-scanner.toml holds accepted alerts, each with a reason and an expiry date.
# Exit: 0 nothing critical or high; 1 at least one (each is listed); 2 the scan could not run.
set -uo pipefail

OSV_IMAGE="ghcr.io/google/osv-scanner:v2.2.4"
THRESHOLD="${SECURITY_SCAN_MIN_CVSS:-7.0}"
work=$(mktemp -d)
trap 'rm -r "$work"' EXIT

if ! lein with-profile uberjar classpath 2>/dev/null | python3 scripts/flat-pom.py > "$work/pom.xml"; then
  echo "security-scan: could not list the app's libraries (lein classpath failed)" >&2; exit 2
fi
count=$(grep -c "<dependency>" "$work/pom.xml")
[ "$count" -gt 0 ] || { echo "security-scan: the library list is empty" >&2; exit 2; }
cp osv-scanner.toml "$work/osv-scanner.toml"

docker run --rm -v "$work:/src" "$OSV_IMAGE" scan source --no-resolve \
  --config /src/osv-scanner.toml -L /src/pom.xml --format json > "$work/out.json" 2> "$work/err.txt"
rc=$?
if [ "$rc" -ne 0 ] && [ "$rc" -ne 1 ]; then
  echo "security-scan: OSV-Scanner failed (exit $rc):" >&2; tail -5 "$work/err.txt" >&2; exit 2
fi

python3 - "$work/out.json" "$THRESHOLD" "$count" <<'PY'
import json, sys
out, threshold, count = json.load(open(sys.argv[1])), float(sys.argv[2]), sys.argv[3]
bad, lower = [], 0
for result in out.get("results", []):
    for pkg in result.get("packages", []):
        name = "%s %s" % (pkg["package"]["name"], pkg["package"]["version"])
        for group in pkg.get("groups", []):
            sev = group.get("max_severity") or ""
            score = float(sev) if sev else None
            # An alert with no score counts as high: unknown is not safe.
            if score is None or score >= threshold:
                bad.append("%s  %s  CVSS %s" % (name, ", ".join(group.get("ids", [])), sev or "unscored"))
            else:
                lower += 1
print("security-scan: %s libraries checked; %d critical or high, %d lower (not failing)" % (count, len(bad), lower))
for line in bad:
    print("  " + line)
if bad:
    print("Fix by raising the version (project.clj :managed-dependencies for a library another one brings in),")
    print("or accept it in osv-scanner.toml with a reason and an expiry date.")
sys.exit(1 if bad else 0)
PY
