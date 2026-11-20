#!/usr/bin/env python3
"""Build the kit tile icons: a bag with a badge in a corner, one SVG per pack and theme.

    scripts/build-kit-icons.py            write resources/public/image/kits/<pack>-{dark,light}.svg
    scripts/build-kit-icons.py --check    exit 1 if a file on disk differs from what the picks give,
                                          or if `kit-icon-packs` in views/inventory.cljs lacks a pick

Picks live in resources/kit-icons.edn (a flat map of pack key to settings). Each output is one
self-contained SVG, viewBox 0 0 512 512, drawn the way the icon workbench draws a "Badge with a
coloured ring": the bag, a transparent cut-out circle in it (a mask, so it shows whatever the tile
sits on), a ring in the pack's hue, and the badge inside the ring. Ink is white in the dark theme
and black at 70% in the light theme, as `themed-icon` draws the other icons.

Source SVGs are read from resources/public/image (then emblems/ and black/), stripped of fills,
styles and the full-size backdrop, and refused if they hold scripts, handlers or links.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IMG = ROOT / "resources/public/image"
OUT = IMG / "kits"
PICKS = ROOT / "resources/kit-icons.edn"
VIEW = ROOT / "src/cljs/orcpub/dnd/e5/views/inventory.cljs"
UNSAFE = re.compile(r"<script|<foreignObject|\son\w+\s*=|href\s*=|<image|<iframe", re.I)
THEMES = ("dark", "light")
CORNERS = {"br", "bl", "tr", "tl"}


def read_picks(text):
    """Parse the flat picks map in `text`: {pack: {setting: value}}, keywords as bare strings,
    numbers as int or float, strings unquoted. Raises ValueError on anything else."""
    text = re.sub(r"^\s*;.*$", "", text, flags=re.M)
    picks = {}
    for m in re.finditer(r":([\w-]+)\s+\{([^{}]*)\}", text):
        entry = {}
        for k, v in re.findall(r":([\w-]+)\s+(\"[^\"]*\"|[^\s}]+)", m.group(2)):
            if v.startswith('"'):
                entry[k] = v[1:-1]
            elif v.startswith(":"):
                entry[k] = v[1:]
            else:
                entry[k] = float(v) if "." in v else int(v)
        picks[m.group(1)] = entry
    if not picks:
        raise ValueError("no picks found in " + str(PICKS))
    return picks


def clean(src, name):
    """Return (viewBox, inner markup) of the SVG text `src` without fills, styles or the
    full-size backdrop. Raises ValueError if `name`'s file is not a plain SVG."""
    m = re.search(r"<svg[^>]*>(.*)</svg>", src, re.S)
    if not m or UNSAFE.search(src):
        raise ValueError(name + ": not a plain SVG")
    inner = m.group(1)
    inner = re.sub(r"<path[^>]*d=['\"]M0 0h512v512H0z['\"][^>]*?(/>|>\s*</path>)", "", inner)
    inner = re.sub(r"\s(fill|style|class|opacity|fill-opacity)=(\"[^\"]*\"|'[^']*')", "", inner)
    inner = re.sub(r"\s+", " ", inner).strip()
    vb = re.search(r"viewBox=[\"']([^\"']+)[\"']", src)
    return (vb.group(1) if vb else "0 0 512 512"), inner


def load_icon(name):
    """The cleaned (viewBox, markup) of icon `name`, from image/, emblems/ or black/."""
    for d in (IMG, IMG / "emblems", IMG / "black"):
        f = d / (name + ".svg")
        if f.exists():
            return clean(f.read_text(encoding="utf-8"), name)
    raise ValueError("no icon named " + name)


def hue_colour(h, theme):
    """The ring colour for hue `h` in `theme`: lighter on the dark theme, darker on the light."""
    return f"hsl({h},48%,68%)" if theme == "dark" else f"hsl({h},42%,38%)"


def num(x):
    """`x` as a short decimal string."""
    return f"{x:.2f}".rstrip("0").rstrip(".")


def kit_svg(pack, pick, theme):
    """The SVG text of pack `pack` (settings `pick`) in `theme`."""
    if (pick.get("colour"), pick.get("backing"), pick.get("tint")) != ("ring", "cutout", "ink"):
        raise ValueError(pack + ": only colour ring, backing cutout, tint ink is built")
    if pick["corner"] not in CORNERS:
        raise ValueError(pack + ": bad corner")
    ink = '#fff' if theme == "dark" else '#000" fill-opacity=".7'
    base_vb, base = load_icon(pick["base"])
    badge_vb, badge = load_icon(pick["badge"])
    s = 512 * pick["scale"]
    edge = s * 0.075  # the ring (r 0.54s, stroke 0.07s) reaches 0.575s from the centre; keep it in
    x = 512 - s - edge if pick["corner"].endswith("r") else edge
    y = 512 - s - edge if pick["corner"].startswith("b") else edge
    cx, cy, inset = x + s / 2, y + s / 2, s * 0.18
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">'
        f'<mask id="cut"><rect width="512" height="512" fill="#fff"/>'
        f'<circle cx="{num(cx)}" cy="{num(cy)}" r="{num(s * 0.6)}" fill="#000"/></mask>'
        f'<g mask="url(#cut)"><svg viewBox="{base_vb}" width="512" height="512" fill="{ink}">{base}</svg></g>'
        f'<circle cx="{num(cx)}" cy="{num(cy)}" r="{num(s * 0.54)}" fill="none" '
        f'stroke="{hue_colour(pick["hue"], theme)}" stroke-width="{num(s * 0.07)}"/>'
        f'<svg x="{num(x + inset)}" y="{num(y + inset)}" width="{num(s - 2 * inset)}" '
        f'height="{num(s - 2 * inset)}" viewBox="{badge_vb}" fill="{ink}">{badge}</svg></svg>\n'
    )


def outputs():
    """Every file the picks produce, as {path: text}."""
    picks = read_picks(PICKS.read_text(encoding="utf-8"))
    return {OUT / f"{p}-{t}.svg": kit_svg(p, pick, t) for p, pick in picks.items() for t in THEMES}


def view_packs():
    """The pack names in `kit-icon-packs` of views/inventory.cljs, as a set."""
    m = re.search(r"\(def \^:private kit-icon-packs.*?#\{([^}]*)\}", VIEW.read_text(encoding="utf-8"), re.S)
    return set(re.findall(r":([\w-]+)", m.group(1))) if m else set()


def main(argv):
    """Write the icons, or with --check compare them to disk. Returns the exit code."""
    want = outputs()
    if "--check" in argv:
        stale = [str(p.relative_to(ROOT)) for p, t in want.items()
                 if not p.exists() or p.read_text(encoding="utf-8") != t]
        extra = sorted(p.name for p in OUT.glob("*.svg") if p not in want) if OUT.exists() else []
        listed = view_packs()
        named = {p.name.rsplit("-", 1)[0] for p in want}
        for f in stale:
            print("out of date:", f)
        for f in extra:
            print("not in the picks:", f)
        if listed != named:
            print("kit-icon-packs differs from the picks:", sorted(listed ^ named))
        return 1 if stale or extra or listed != named else 0
    OUT.mkdir(parents=True, exist_ok=True)
    for p, t in want.items():
        p.write_text(t, encoding="utf-8")
    print(len(want), "icons written to", OUT.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
