#!/usr/bin/env bash
# Compare the app's weapons and armor with the SRD equipment, field by field.
# Usage: equipment-parity.sh APP_WORKTREE [OPEN5E_CLONE] [SRD52_WORKTREE] [--self-test]
# Regenerates the 2014 SRD equipment from the open5e clone into a scratch dir, then runs
# equipment-parity.clj on the app's classpath. Exit 1 on any difference not in
# equipment-parity-reviewed.edn. KB: docs/kb/equipment-parity.md
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
app=${1:?usage: equipment-parity.sh APP_WORKTREE [OPEN5E_CLONE] [SRD52_WORKTREE] [--self-test]}
clone=${2:-/home/codeglaze/projects/open5e-api}
srd52=${3:-/home/codeglaze/projects/orcpub-srd52}
flag=${4:-}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/resources/public/srd/2014" "$tmp/resources/public/srd/2024"
ln -s "$srd52/resources/srd" "$tmp/resources/srd"
uv run --quiet "$here/emit-equipment.py" "$clone" "$tmp" >/dev/null
cd "$app"
java -cp "$(lein classpath 2>/dev/null)" clojure.main "$here/equipment-parity.clj" \
  "$tmp/resources/public/srd/2014/equipment.edn" "$here/equipment-parity-reviewed.edn" $flag \
  2> >(grep -v -e 'registration is DISABLED' -e 'EMAIL_SERVER_URL' -e 'verification email' \
               -e 'ALLOW_UNVERIFIED' -e 'accounts without verifying' >&2)
