#!/usr/bin/env python3
"""Turn an illustrator's layer-art folders into the portrait asset pack.

This is the desktop half of the Silhouette Loom. The Loom runs on a phone and
trades some quality for being able to run anywhere; this does the same job with
a proper resampler and real PNG optimisation, and is what should produce the
files that actually land in the repository.

Both paths emit the same manifest (version 3, mode "production"), so the asset
registry can be generated from either.

    scripts/build-portrait-assets.py ~/art --height 638

Input is a directory of layer folders, the illustrator's own convention:

    layer 0 - hair bits/L0_hair_bits_pony_long.png
    layer 2 - head/L2_head_01.png
    layer 2 - head/no L2_head_04.txt      <- "not drawn yet" marker

Output defaults to resources/public/image/portraits/<layer-key>/<file>.png.

Requires Pillow and numpy. Will also use `oxipng` and `pngquant` if they are on
PATH -- both are optional and the script says which it found.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

try:
    import numpy as np
    from PIL import Image
except ImportError:  # pragma: no cover - environment problem, not logic
    sys.exit("needs Pillow and numpy:  pip install Pillow numpy")

IMAGE_RE = re.compile(r"\.(png|jpe?g|gif|webp|bmp)$", re.I)
GAP_RE = re.compile(r"^no\s+(.+)\.txt$", re.I)
FOLDER_RE = re.compile(r"^layer\s+(\d+)\s*[-–—]\s*(.+)$", re.I)

# Mirrors portrait_assets.cljc/layer-colors and the Loom's palette. Only used
# for the manifest; the composited art takes its colour from the character.
PALETTE = {
    "hair bits": "#e0a24d", "hair back": "#c88a4a", "head": "#f2e6d0",
    "shirt": "#7a94b8", "hair front": "#e6a040", "ears": "#eab098",
    "eyes": "#78d0d4", "nose": "#d67c5c", "mouth": "#c85c5c",
    "bangs": "#f5c46b",
}


def layer_key(name):
    return re.sub(r"^-|-$", "", re.sub(r"[^a-z0-9]+", "-", name.strip().lower()))


def parse_folder(raw):
    m = FOLDER_RE.match(raw)
    if m:
        return int(m.group(1)), m.group(2).strip().lower()
    return 999, raw.strip().lower()


def category_color(name):
    if name in PALETTE:
        return PALETTE[name]
    # deterministic fallback, same djb2-ish hash the Loom uses
    h = 5381
    for c in name:
        h = ((h << 5) + h + ord(c)) & 0xFFFFFFFF
    hue = h % 360
    s, l = 0.45, 0.65
    a = s * min(l, 1 - l)

    def f(n):
        k = (n + hue / 30) % 12
        return l - a * max(-1, min(k - 3, 9 - k, 1))

    return "#" + "".join(f"{round(f(n) * 255):02x}" for n in (0, 8, 4))


def resize_premultiplied(img, size, linear=False):
    """Lanczos downscale that does not halo.

    Alpha has to be premultiplied before filtering. Pillow resizes each band
    independently, so without this the RGB sitting under fully transparent
    pixels -- usually black -- gets averaged into every edge pixel and the
    outline picks up a dark fringe.

    With `linear`, the resample happens in linear light, which is the
    physically correct thing to do and leaves antialiased edges very slightly
    brighter. It is off by default so output matches the Loom.
    """
    a = np.asarray(img.convert("RGBA"), dtype=np.float64) / 255.0
    rgb, alpha = a[..., :3], a[..., 3:]

    if linear:
        rgb = np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)

    pre = np.concatenate([rgb * alpha, alpha], axis=-1)

    # Filter each plane in float. Rounding the premultiplied image back to
    # 8-bit before the resample looks harmless and is not: a premultiplied
    # value at low alpha is tiny, so quantising it costs most of its
    # precision, and dividing the alpha back out then multiplies that error
    # by up to 255. Measured against a float reference it pushed edge pixels
    # from RGB 30 to RGB 152 -- a bright halo around every outline.
    planes = [
        np.asarray(
            Image.fromarray(pre[..., i].astype(np.float32), "F").resize(
                size, Image.LANCZOS
            ),
            dtype=np.float64,
        )
        for i in range(4)
    ]

    out_a = np.clip(planes[3], 0.0, 1.0)[..., None]
    # Where nothing is opaque there is no colour to recover; leave it black.
    out_rgb = np.stack(
        [
            np.divide(planes[i], out_a[..., 0],
                      out=np.zeros_like(out_a[..., 0]), where=out_a[..., 0] > 1e-6)
            for i in range(3)
        ],
        axis=-1,
    )

    if linear:
        out_rgb = np.clip(out_rgb, 0, 1)
        out_rgb = np.where(out_rgb <= 0.0031308, out_rgb * 12.92,
                           1.055 * out_rgb ** (1 / 2.4) - 0.055)

    merged = np.concatenate([np.clip(out_rgb, 0, 1), np.clip(out_a, 0, 1)], axis=-1)
    return Image.fromarray((merged * 255.0 + 0.5).astype(np.uint8), "RGBA")


def optimise(path, quantise):
    """Shrink the PNG in place with whatever is installed. Returns notes."""
    notes = []
    if quantise and shutil.which("pngquant"):
        r = subprocess.run(
            ["pngquant", "--force", "--skip-if-larger", "--quality", "70-95",
             "--output", str(path), str(path)],
            capture_output=True,
        )
        # 98 = "quality too low", 99 = "larger than original"; both mean "kept"
        if r.returncode not in (0, 98, 99):
            notes.append(f"pngquant failed ({r.returncode})")
    if shutil.which("oxipng"):
        r = subprocess.run(["oxipng", "-o", "4", "--strip", "safe", "-q", str(path)],
                           capture_output=True)
        if r.returncode != 0:
            notes.append(f"oxipng failed ({r.returncode})")
    return notes


def collect(src):
    """Group images and gap markers by their layer folder."""
    layers = {}
    for path in sorted(src.rglob("*")):
        if not path.is_file() or path.parent == src:
            continue
        folder = path.parent.name
        bucket = layers.setdefault(folder, {"images": [], "gaps": []})
        if IMAGE_RE.search(path.name):
            bucket["images"].append(path)
        elif (m := GAP_RE.match(path.name)):
            bucket["gaps"].append({"filename": m.group(1), "marker": path.name})
    return layers


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", type=Path, help="directory of 'layer N - name' folders")
    ap.add_argument("-o", "--out", type=Path,
                    default=Path("resources/public/image/portraits"))
    ap.add_argument("--height", type=int, default=638,
                    help="target height in px (638 = 200 dpi in the PDF box, "
                         "968 = 300 dpi; default: %(default)s)")
    ap.add_argument("--linear", action="store_true",
                    help="resample in linear light (slightly brighter edges)")
    ap.add_argument("--quantise", action="store_true",
                    help="also run pngquant -- big savings on flat art, lossy")
    ap.add_argument("--keep-case", action="store_true",
                    help="do not lower-case output filenames")
    ap.add_argument("--version", default="v1", help="pack version recorded in the manifest")
    args = ap.parse_args()

    if not args.source.is_dir():
        sys.exit(f"no such directory: {args.source}")

    layers = collect(args.source)
    if not layers:
        sys.exit(f"no layer subfolders found under {args.source}")

    # One canvas for every layer. They are stacked at inset 0 in the browser,
    # so an asset exported at a different size silently breaks registration.
    sizes = Counter()
    for bucket in layers.values():
        for p in bucket["images"]:
            with Image.open(p) as im:
                sizes[im.size] += 1
    (sw, sh), _ = sizes.most_common(1)[0]
    target = (max(1, round(args.height * sw / sh)), args.height)

    print(f"source   {sw}x{sh} ({sizes[(sw, sh)]}/{sum(sizes.values())} files)")
    print(f"target   {target[0]}x{target[1]}  "
          f"({round(args.height / 3.15)} dpi in the 3.15in PDF box)")
    have = [t for t in ("oxipng", "pngquant") if shutil.which(t)]
    print(f"optimise {', '.join(have) if have else 'none installed (Pillow output as-is)'}")
    if len(sizes) > 1:
        print("\n  !! mixed source sizes -- these will be stretched and will not line up:")
        for size, n in sizes.most_common()[1:]:
            print(f"     {size[0]}x{size[1]}  ({n} file{'s' if n > 1 else ''})")
    print()

    manifest_layers, total_bytes, warnings = [], 0, []
    for folder in sorted(layers, key=lambda f: parse_folder(f)[0]):
        z, name = parse_folder(folder)
        key = layer_key(name)
        bucket = layers[folder]
        dest = args.out / key
        dest.mkdir(parents=True, exist_ok=True)

        assets = []
        for path in sorted(bucket["images"]):
            with Image.open(path) as im:
                if im.size != (sw, sh):
                    warnings.append(f"{path.name}: {im.size[0]}x{im.size[1]}")
                out_name = path.name if args.keep_case else path.name.lower()
                out_name = Path(out_name).with_suffix(".png").name
                out_path = dest / out_name
                resize_premultiplied(im, target, linear=args.linear).save(
                    out_path, "PNG", optimize=True)
            for note in optimise(out_path, args.quantise):
                warnings.append(f"{out_name}: {note}")
            size = out_path.stat().st_size
            total_bytes += size
            assets.append({
                "filename": out_name,
                "sourceFilename": path.name,
                "path": f"portraits/{key}/{out_name}",
                "sourceWidth": sw, "sourceHeight": sh,
                "bytes": size,
            })

        manifest_layers.append({
            "z": z, "folder": folder, "key": key, "name": name,
            "color": category_color(name),
            "count": len(assets), "gapCount": len(bucket["gaps"]),
            "assets": assets, "gaps": bucket["gaps"],
        })
        gap = f"  +{len(bucket['gaps'])} wip" if bucket["gaps"] else ""
        print(f"  z{z:02d} {key:<12} {len(assets):>2} file"
              f"{' ' if len(assets) == 1 else 's'}"
              f"  {sum(a['bytes'] for a in assets) / 1024:7.1f} KB{gap}")

    manifest = {
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "version": 3,
        "mode": "production",
        "packVersion": args.version,
        "generator": "scripts/build-portrait-assets.py",
        "target": {"width": target[0], "height": target[1]},
        "source": {"width": sw, "height": sh},
        "totalBytes": total_bytes,
        "layers": manifest_layers,
    }
    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")

    n = sum(l["count"] for l in manifest_layers)
    print(f"\n{n} assets, {total_bytes / 1024:.0f} KB total -> {args.out}")
    if warnings:
        print("\nwarnings:")
        for w in warnings:
            print(f"  {w}")
    return 1 if warnings else 0


if __name__ == "__main__":
    sys.exit(main())
