#!/usr/bin/env bash
# Lay the licensed portrait art over the silhouettes, on the PRIVATE deployment branch only.
#
#   scripts/private-art.sh update portrait-pack-v1.775.zip
#
# Copies each PNG in the pack over the silhouette with the same layer and file name (case
# aside), writes the private-art marker, and commits. It does not push: push to the private
# (Gitea) remote yourself. Strand files are not needed; the image build makes them.
# Refuses to run when the branch tracks a GitHub remote, because the art must never go there.
set -euo pipefail

cmd="${1:-}"; pack="${2:-}"
if [ "$cmd" != "update" ] || [ -z "$pack" ]; then
  echo "usage: scripts/private-art.sh update <portrait-pack.zip>" >&2; exit 2
fi
[ -f "$pack" ] || { echo "no such pack: $pack" >&2; exit 2; }

root="$(git rev-parse --show-toplevel)"; cd "$root"
art="resources/public/image/portraits"

# Never on a branch that pushes to GitHub. Read from the branch's settings, which do not
# depend on what has been fetched.
branch="$(git symbolic-ref --short HEAD 2>/dev/null || true)"
[ -n "$branch" ] || { echo "Not on a branch (detached HEAD); check out the private branch first." >&2; exit 1; }
remote="$(git config "branch.$branch.remote" || true)"
if [ -z "$remote" ]; then
  echo "Branch $branch tracks no remote. Set it to track the private (Gitea) remote first:" >&2
  echo "  git branch --set-upstream-to=<gitea-remote>/$branch" >&2; exit 1
fi
urls="$(git remote get-url "$remote" 2>/dev/null || true) $(git remote get-url --push "$remote" 2>/dev/null || true)"
case "$urls" in
  *github.com*) echo "REFUSED: branch $branch tracks remote '$remote' ($urls), which is GitHub." >&2
                echo "The licensed art must only ever be committed on the private deployment branch." >&2; exit 1;;
esac
upstream="$remote/$(git config "branch.$branch.merge" | sed 's#^refs/heads/##')"
if [ -n "$(git status --porcelain)" ]; then
  echo "The working tree has uncommitted changes; commit or stash them first." >&2; exit 1
fi

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
unzip -q "$pack" -d "$tmp"
version="$(sed -n 's/.*"packVersion"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$tmp/manifest.json" 2>/dev/null | head -1)"
version="${version:-$(basename "$pack" .zip)}"

replaced=0; unmatched=()
while IFS= read -r -d '' f; do
  rel="${f#"$tmp"/}"; layer="$(dirname "$rel")"; name="$(basename "$rel")"
  case "$name" in *.strands.png|*.STRANDS.PNG) continue;; esac
  dest="$(find "$art/$layer" -maxdepth 1 -iname "$name" -print -quit 2>/dev/null || true)"
  if [ -n "$dest" ]; then cp "$f" "$dest"; replaced=$((replaced + 1)); else unmatched+=("$rel"); fi
done < <(find "$tmp" -type f -iname '*.png' -print0)

if [ "${#unmatched[@]}" -gt 0 ]; then
  echo "Not laid in (no silhouette with that layer and name; add it to the registry first):"
  printf '  %s\n' "${unmatched[@]}"
fi
[ "$replaced" -gt 0 ] || { echo "Nothing replaced: is this a portrait pack?" >&2; git checkout -- "$art"; exit 1; }

printf 'Licensed portrait art, pack %s, laid in %s.\nPrivate deployment branch only: never push this to GitHub.\n' \
  "$version" "$(date -u +%Y-%m-%d)" > "$art/PRIVATE-ART"
git add "$art"
git commit -q -m "Portrait art: pack $version (private)"
echo "Laid in $replaced pieces from pack $version and committed. Push to $upstream when ready."
