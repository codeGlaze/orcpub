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

# First: anything carrying the licensed-art mark (the Loom writes it into the real art, and
# everything drawn from marked pieces carries it) is the art, whatever else it passes.
marked=$(git ls-files | grep -iE '\.(png|jpe?g|webp|gif|pdf)$' | python3 -I -c '
import sys, re, zlib, os, struct
key = b"orcpub-art"
private = os.path.exists("resources/public/image/portraits/PRIVATE-ART")
for path in sys.stdin.read().split("\n"):
    if not path or (private and path.startswith("resources/public/image/portraits/")): continue
    try: b = open(path, "rb").read()
    except OSError: continue
    hit = False
    if b[:8] == b"\x89PNG\r\n\x1a\n":
        i = 8
        while i + 8 <= len(b):
            n = struct.unpack(">I", b[i:i+4])[0]; t = b[i+4:i+8]
            if t == b"iTXt" and b[i+8:i+8+len(key)+1] == key + b"\0": hit = True; break
            if t in (b"IDAT", b"IEND"): break
            i += 12 + n
    elif key in b: hit = True
    elif path.lower().endswith(".pdf"):
        for m in re.finditer(rb"stream\r?\n(.*?)endstream", b, re.S):
            try:
                if key in zlib.decompress(m.group(1)): hit = True; break
            except zlib.error: pass
    if hit: print(path)
')
while IFS= read -r f; do
  [ -n "$f" ] || continue
  echo "::error file=$f::$f carries the licensed-art mark (orcpub-art). It must not be in this repository: it belongs only on the private deployment branch."
  bad=1
done <<< "$marked"
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
