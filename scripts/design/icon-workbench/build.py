"""Builds an icon workbench page: game-icons.net SVGs inlined into a composer template.

Usage: python3 -I build.py TEMPLATE OUT REPO_IMAGE_DIR [EXTRA_DIR ...]
  REPO_IMAGE_DIR: resources/public/image of a code checkout (its root, emblems/ and black/ are read).
  EXTRA_DIR: folders of downloaded SVGs; tagged "extra" (not in the app yet) on the page.
The template holds the literal /*ICONS*/{} where the icon map goes.
"""
import json, os, re, sys

TEMPLATE, OUT, IMG = sys.argv[1:4]
EXTRA = sys.argv[4:]
UNSAFE = re.compile(r"<script|<foreignObject|\son\w+\s*=|href\s*=|<image|<iframe", re.I)


def clean(src):
    """Returns (viewBox, inner markup) of an SVG with its fills, styles and full-size backdrop removed."""
    m = re.search(r"<svg[^>]*>(.*)</svg>", src, re.S)
    if not m or UNSAFE.search(src):
        return None
    inner = m.group(1)
    inner = re.sub(r'<path[^>]*d="M0 0h512v512H0z"[^>]*>(</path>)?', "", inner)
    inner = re.sub(r'\s(fill|style|class|opacity|fill-opacity)="[^"]*"', "", inner)
    inner = re.sub(r"\s+", " ", inner).strip()
    vb = re.search(r'viewBox="([^"]+)"', src)
    return (vb.group(1) if vb else "0 0 512 512"), inner


icons = {}
dirs = [(os.path.join(IMG, s), "app") for s in ("", "emblems", "black")] + [(d, "extra") for d in EXTRA]
for d, origin in dirs:
    for f in sorted(os.listdir(d)):
        name = f[:-4]
        if not f.endswith(".svg") or name in icons or name in ("dmv-logo", "orcpub-logo"):
            continue
        got = clean(open(os.path.join(d, f), encoding="utf-8").read())
        if got is None:
            print("skipped (not a plain SVG):", f)
            continue
        icons[name] = {"vb": got[0], "body": got[1], "origin": origin}

html = open(TEMPLATE, encoding="utf-8").read().replace("/*ICONS*/{}", json.dumps(icons, separators=(",", ":")))
open(OUT, "w", encoding="utf-8").write(html)
print(len(icons), "icons (", sum(i["origin"] == "extra" for i in icons.values()), "extra ),", len(html) // 1024, "KB")
