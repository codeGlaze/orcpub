#!/usr/bin/env bash
# Fold docs/branch-changelog.md into a release section of the root CHANGELOG.md,
# then remove it. Run this at merge-to-integration time (see docs/CONTRIBUTING.md).
#
# Usage:
#   scripts/fold-branch-changelog.sh "<release-section>"
#
#   <release-section>  the release the entries belong to, e.g. "Summer Patch"
#                      (matched against a "## [<release-section>] ..." header).
#
# Moves each line under the branch's Added / Changed / Fixed / Security into the same heading of
# the release, then git-rms the branch changelog. Review CHANGELOG.md after: a line that repeats
# one already there should be merged by hand.
set -euo pipefail

RELEASE="${1:-}"
if [ -z "$RELEASE" ]; then
  echo "Usage: scripts/fold-branch-changelog.sh \"<release-section>\" [\"<block-title>\"]" >&2
  exit 2
fi

BC="docs/branch-changelog.md"
CL="CHANGELOG.md"

[ -f "$CL" ] || { echo "No $CL found." >&2; exit 1; }
if [ ! -f "$BC" ]; then
  echo "No $BC to fold — nothing to do."
  exit 0
fi

if ! grep -q "^## \[${RELEASE}\]" "$CL"; then
  echo "Release section '## [${RELEASE}]' not found in $CL." >&2
  echo "Add the section header first, or pass the exact release name." >&2
  exit 1
fi

# Fold by change type: each bullet under the branch changelog's "## Added", "## Changed", "## Fixed" or
# "## Security" goes to the end of the same "### " heading inside "## [RELEASE]", which is created in
# that order when missing. "## Why this branch exists" and "## Highlights" are dropped (the release's
# Highlights are written by hand). Any other section stops the fold. A bullet without a leading
# "**Area:**" label is folded with a warning.
python3 - "$BC" "$CL" "$RELEASE" <<'PY' || exit 1
import re, sys
bc_path, cl_path, release = sys.argv[1:4]
TYPES = ["Added", "Changed", "Fixed", "Security"]
DROPPED = ("Why this branch exists", "Highlights")

text = re.sub(r"<!--.*?-->", "", open(bc_path).read(), flags=re.S)
found, section, current = {t: [] for t in TYPES}, None, None
for line in text.splitlines():
    if line.startswith("# "):
        continue
    if line.startswith("## "):
        section = line[3:].strip()
        if section not in TYPES and section not in DROPPED:
            sys.exit(f"fold: unknown section '## {section}' in {bc_path}; use one of: {', '.join(TYPES)}")
        current = None
        continue
    if section in TYPES:
        if line.startswith("- "):
            current = [line]
            found[section].append(current)
        elif current is not None and line.startswith(("  ", "\t")) and line.strip():
            current.append(line)
        elif not line.strip():
            current = None
for t in TYPES:
    for b in found[t]:
        if not re.match(r"- \*\*[^*]+:\*\*", b[0]):
            print(f"fold: warning: no **Area:** label: {b[0][:70]}", file=sys.stderr)
if "Highlights" in text and re.search(r"^## Highlights", text, re.M):
    print("fold: the branch's Highlights were dropped; edit the release's Highlights by hand if it earns a line.", file=sys.stderr)

lines = open(cl_path).read().split("\n")
start = next((i for i, l in enumerate(lines) if l.startswith(f"## [{release}]")), None)
if start is None:
    sys.exit(f"fold: '## [{release}]' not found in {cl_path}")
end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")), len(lines))
for t in TYPES:
    if not found[t]:
        continue
    heads = [i for i in range(start, end) if lines[i].strip() == f"### {t}"]
    if heads:
        h = heads[0]
        nxt = next((i for i in range(h + 1, end) if lines[i].startswith("### ")), end)
        ins = nxt
        while ins - 1 > h and not lines[ins - 1].strip():
            ins -= 1
        add = [l for b in found[t] for l in b]
    else:
        later = [i for i in range(start, end) for u in TYPES[TYPES.index(t) + 1:] if lines[i].strip() == f"### {u}"]
        ins = min(later) if later else end
        while ins - 1 > start and not lines[ins - 1].strip():
            ins -= 1
        add = ["", f"### {t}", ""] + [l for b in found[t] for l in b]
    lines[ins:ins] = add
    end += len(add)
open(cl_path, "w").write("\n".join(lines))
print(f"fold: {sum(len(v) for v in found.values())} line(s) folded into '## [{release}]'")
PY

git rm -q "$BC"

echo "Folded $BC into '## [${RELEASE}]' by change type and removed the branch changelog."
echo "Review $CL, then commit."
