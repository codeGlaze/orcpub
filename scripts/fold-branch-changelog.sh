#!/usr/bin/env bash
# Fold docs/branch-changelog.md into a release section of the root CHANGELOG.md,
# then remove it. Run this at merge-to-integration time (see docs/kb/branching-model.md).
#
# Usage:
#   scripts/fold-branch-changelog.sh "<release-section>" ["<block-title>"]
#
#   <release-section>  the release the entries belong to, e.g. "Summer Patch"
#                      (matched against a "## [<release-section>] ..." header).
#   <block-title>      optional heading for the folded block; defaults to the
#                      current git branch name.
#
# It automates the MECHANICAL fold (extract entries -> insert -> git rm). Editorial
# curation (release name, prose tidy-ups) is still yours — review CHANGELOG.md after.
set -euo pipefail

RELEASE="${1:-}"
if [ -z "$RELEASE" ]; then
  echo "Usage: scripts/fold-branch-changelog.sh \"<release-section>\" [\"<block-title>\"]" >&2
  exit 2
fi

BC="docs/branch-changelog.md"
CL="CHANGELOG.md"
TITLE="${2:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "branch")}"

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

# Build the folded block from the branch changelog:
#   - strip the HTML comment block and the top-level "# ..." title
#   - drop "## Why this branch exists" AND its body (reviewer context; never reaches
#     CHANGELOG.md). A "## Highlights" section, if the branch earned one, is a normal
#     "## Section" and survives as "**Highlights**".
#   - demote every other "## Section" to "**Section**"
#   - wrap it all under "### <block-title>"
BLOCK="$(mktemp)"
{
  echo "### ${TITLE}"
  echo
  awk '
    /<!--/ { inc=1 } inc { if (/-->/) inc=0; next }              # skip HTML comment
    /^## Why this branch exists[[:space:]]*$/ { skipwhy=1; next } # drop the Why header AND
    skipwhy && /^(## |### )/ { skipwhy=0 }                        #   its body, up to the next
    skipwhy { next }                                             #   section (any "---" too)
    /^## / { sub(/^## /, ""); print "**" $0 "**"; next }          # demote section headers
    /^# /  { next }                                               # drop the doc title
    { print }
  ' "$BC" | sed '/./,$!d'                                         # trim leading blank lines
} > "$BLOCK"

# Insert the block at the END of the "## [RELEASE]" section (just before the next
# top-level "## " header, or at EOF if it is the last section).
awk -v release="## [${RELEASE}]" -v blockfile="$BLOCK" '
  index($0, release) == 1 { insection = 1; print; next }
  insection == 1 && /^## / {
    while ((getline line < blockfile) > 0) print line
    close(blockfile)
    print ""
    insection = 0
  }
  { print }
  END {
    if (insection == 1) {
      while ((getline line < blockfile) > 0) print line
      close(blockfile)
    }
  }
' "$CL" > "${CL}.tmp" && mv "${CL}.tmp" "$CL"

rm -f "$BLOCK"
git rm -q "$BC"

echo "Folded $BC into '## [${RELEASE}]' as '### ${TITLE}' and removed the branch changelog."
echo "Review $CL, then commit."
