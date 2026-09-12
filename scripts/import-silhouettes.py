#!/usr/bin/env python3
"""Unpack a Silhouette Loom prototype manifest into real asset files.

The Loom's prototype mode returns 48x48 tinted alpha shapes -- the illustrator's
work reduced to silhouettes so it can be handled without the originals leaving
their device. They are the real inventory: real filenames, real per-layer
counts, real proportions. Only the detail is missing.

This writes them where the production art will eventually live, so the registry
can point at the real paths today and a later production run just overwrites
the same files.

    scripts/import-silhouettes.py manifest.json

Two corrections on the way through:

* The Loom squashes each source into a 48x48 square, so the shapes come back
  vertically compressed. The manifest records the source dimensions, so the
  aspect is restored here.
* Filenames are lower-cased, matching build-portrait-assets.py -- the source
  set mixes `L4_` and `l4_`, which works on a phone and breaks on a Linux
  server.
"""

import argparse
import base64
import io
import json
import re
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("needs Pillow:  pip install Pillow")


def layer_key(name):
    return re.sub(r"^-|-$", "", re.sub(r"[^a-z0-9]+", "-", name.strip().lower()))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest", type=Path)
    ap.add_argument("-o", "--out", type=Path,
                    default=Path("resources/public/image/portraits"))
    ap.add_argument("--scale", type=int, default=4,
                    help="integer upscale of the 48px shapes (default: %(default)s)")
    args = ap.parse_args()

    manifest = json.loads(args.manifest.read_text())
    if manifest.get("version") not in (1, 2, 3):
        sys.exit(f"unexpected manifest version {manifest.get('version')}")

    total = 0
    index = []
    for layer in sorted(manifest["layers"], key=lambda l: l["z"]):
        key = layer_key(layer["name"])
        dest = args.out / key
        dest.mkdir(parents=True, exist_ok=True)
        entries = []
        for asset in layer["assets"]:
            png = base64.b64decode(asset["silhouettePng48"])
            im = Image.open(io.BytesIO(png)).convert("RGBA")
            # undo the square squash: the manifest knows the real proportions
            sw, sh = asset["width"], asset["height"]
            w = im.width * args.scale
            h = max(1, round(w * sh / sw))
            im = im.resize((w, h), Image.LANCZOS)
            name = asset["filename"].lower()
            (dest / name).write_bytes(b"")
            im.save(dest / name, "PNG", optimize=True)
            entries.append({"file": name, "source": asset["filename"],
                            "w": w, "h": h})
            total += 1
        index.append({"z": layer["z"], "key": key, "name": layer["name"],
                      "folder": layer["folder"], "color": layer["color"],
                      "assets": entries,
                      "gaps": [g["filename"] for g in layer.get("gaps", [])]})
        print(f"  z{layer['z']:02d} {key:<12} {len(entries):>2} files  "
              f"{entries[0]['w']}x{entries[0]['h']}")

    (args.out / "silhouette-index.json").write_text(
        json.dumps({"source": "silhouette manifest (prototype mode)",
                    "generatedAt": manifest.get("generatedAt"),
                    "layers": index}, indent=2) + "\n")
    print(f"\n{total} silhouettes -> {args.out}")


if __name__ == "__main__":
    main()
