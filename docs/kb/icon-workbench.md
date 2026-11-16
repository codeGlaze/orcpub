# Icon workbench: let the owner compose icons himself

A one-page tool, published as an Artifact, where the owner picks and combines icons instead of
reviewing a set we chose. Built 2026-11-15 for the inventory kits ("a spritesheet or a tool to do it
myself"). The owner called it excellent and asked that it be kept as a template for other agents.
Live copy: https://claude.ai/artifact/2q42mLhgGFCeB5A2sS53ex.

## What it does

- **A row preview first:** every item composed, side by side, in the dark and the light theme, at the
  size the app draws it (32px in a 48px tile). That row is the real question ("do they blur
  together?"), so it comes before the controls.
- **A composer:** pick an item; choose a base icon and a small corner badge; set corner, size,
  backing (cut-out ring, accent disc, none) and badge colour. Large and true-size previews in both themes.
- **The icon sheet:** every icon the app ships, searchable; a click sets the badge (or the base). Icons
  downloaded for the session are tagged "not in the app yet".
- **A result to paste back:** an EDN map keyed as the app keys the items, with a copy button. Picks
  persist in the viewer's browser only.

## Files (`scripts/design/icon-workbench/`)

| File | Role |
|---|---|
| `build.py` | Inlines the SVGs into the template. `python3 -I build.py template.html out.html <checkout>/resources/public/image [extra-dir ...]`. Reads the image root, `emblems/` and `black/`; strips fills, styles and the full-size backdrop; refuses an SVG with scripts, handlers, links or embedded images |
| `template.html` | The page. The kit-specific parts are three constants at the top of its script: `PACKS` (the items), `BASES` (base icons offered first), `SUGGESTED` (starting picks) |
| `extra-icons/` | 13 game-icons.net SVGs fetched for the kits, not in the app: knapsack, swap-bag, mining (Lorc); hand-bag, briefcase, disc-golf-bag, chest, companion-cube, lockpicks, torch, rope-coil, light-backpack (Delapouite); test-tube-rack (Caro Asercion). CC BY 3.0: an icon that reaches the app gets its author in `resources/public/image/ATTRIBUTION.md` |

## Reusing it for something else

Copy the template, change the three constants and the output keys in `renderOut`, build, and
publish with the Artifact tool. Fetch extra icons from
`https://game-icons.net/icons/ffffff/000000/1x1/<author>/<name>.svg` into their own folder and pass
it as an extra dir. Keep the row preview: the owner judges sets side by side, at true size.
Design at every width ([ux-guide.md](ux-guide.md), "Phone to desktop"); the page already stacks at phone width.
