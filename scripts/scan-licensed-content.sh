#!/usr/bin/env bash
# Scan tracked files for licensed D&D content names (WotC product titles and
# trademarked named-spell proper nouns).
#
# orcpub is a D&D tool, so its content-source files legitimately carry SRD/OGL
# game content. This scan therefore ALLOWLISTS those files (reported for
# reference only) and FAILS when licensed names appear anywhere else — tests,
# fixtures, docs, scripts, or other code — where they usually mean copyrighted
# data got copied in (e.g. a user's real .orcbrew export used as a fixture).
#
# Usage:  scripts/scan-licensed-content.sh            # report + exit 1 on stray hits
#         scripts/scan-licensed-content.sh --list     # just list every hit, exit 0
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

# WotC product titles (multi-word, distinctive) ...
TITLES="Player.?s Handbook|Sword Coast Adventurer|Dungeon Master.?s Guide|Monster Manual|Elemental Evil|Volo.?s Guide|Mordenkainen.?s|Xanathar.?s|Tasha.?s Cauldron|Mythic Odysseys|Van Richten|Fizban.?s|Strixhaven|Wild Beyond|Curse of Strahd|Acquisitions Inc|Ghosts of Saltmarsh|Storm King|Rise of Tiamat|Hoard of the Dragon|Out of the Abyss|Princes of the Apocalypse|Yawning Portal"
# ... plus trademarked named-spell wizards (single words → word-bounded to avoid
# matching "libRARY", "tempoRARY", etc.).
NAMES="\\b(Bigby|Drawmij|Evard|Leomund|Melf|Nystul|Otiluke|Rary|Tenser|Aganazzar|Snilloc|Abi-Dalzim|Maximilian|Xanathar|Mordenkainen)\\b"
PAT="$TITLES|$NAMES"

# Files that legitimately contain SRD/OGL game content (informational, not a failure).
# Keep this list tight and intentional — do not add tests/fixtures/docs here.
ALLOW='^src/cljc/orcpub/dnd/e5/(template\.cljc|spells\.cljc|monsters\.cljc|magic_items\.cljc|classes\.cljc|backgrounds\.cljc|feats\.cljc|races\.cljc|templates/)'
# Vendored/binary paths where matches are coincidental substrings, plus
# deprecated reference files that are intentionally left as-is (scag.cljc).
# NOTE: git grep lines are `path:line:content`, so anchor filenames with a
# trailing ':' (not '$').
IGNORE='(^lib/|\.pom:|\.svg:|\.min\.js:|^resources/public/js/compiled/|test-pak\.orcbrew:|templates/scag\.cljc:)'

hits=$(git grep -I -inE "$PAT" -- ':/' 2>/dev/null | grep -vE "$IGNORE" || true)

if [ "${1:-}" = "--list" ]; then
  echo "$hits"
  exit 0
fi

allowed=$(echo "$hits" | grep -E "$ALLOW" || true)
stray=$(echo "$hits"   | grep -vE "$ALLOW" || true)

echo "== Content-source files (SRD/OGL game content, informational) =="
echo "$allowed" | grep -oE '^[^:]+' | sort | uniq -c | sort -rn || true
echo
echo "== Stray licensed names OUTSIDE content-source files (should be empty) =="
if [ -z "$stray" ]; then
  echo "  none"
  exit 0
fi
echo "$stray" | sed 's/^/  /'
echo
echo "FAIL: licensed content names found outside the allowlisted content sources."
echo "Genericize the names, or (if this is a new legitimate content source) add the file to ALLOW."
exit 1
