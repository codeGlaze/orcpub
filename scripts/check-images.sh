#!/usr/bin/env bash
# Every image git tracks must be one a person has reviewed: a recorded portrait silhouette
# (test/portrait-silhouettes.edn) or an entry in test/reviewed-images.edn, matched by SHA-256.
# The licensed portrait art must never be in this repository, docs/ included; no measure tells it
# from a logo reliably, so a new or changed image fails here until someone has looked at it.
# Run by .github/workflows/portrait-art-guard.yml; orcpub.portrait-art-guard-test is the same
# rule in the test suite. On the private deployment branch (PRIVATE-ART marker) the art directory
# is skipped, as it holds the real art there.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
art=resources/public/image/portraits
bad=0
while IFS= read -r f; do
  case "$f" in *.strands.png) continue ;; esac
  sha=$(sha256sum "$f" | cut -d' ' -f1)
  if [[ "$f" == "$art/"* ]]; then
    [ -e "$art/PRIVATE-ART" ] && continue
    grep -q "\"$sha\"" test/portrait-silhouettes.edn && continue
    echo "::error file=$f::$f is not a recorded portrait silhouette. The licensed art belongs only on the private deployment branch."
  else
    grep -q "\"$sha\"" test/reviewed-images.edn && continue
    echo "::error file=$f::$f is not a reviewed image. If it contains the licensed portrait art it must not be here; otherwise record it (REVIEWED_IMAGES=record lein test :only orcpub.portrait-art-guard-test) and say what it is."
  fi
  bad=1
done < <(git ls-files | grep -iE '\.(png|jpe?g|webp|gif)$')
[ $bad -eq 0 ] && echo "OK - every image is a recorded silhouette or reviewed."
exit $bad
