# Silhouette Loom

The art-prep tool for the portrait pack: a single HTML page, no server. Drop the
artist's pack zip (and optionally a manifest) on it to place irises, pupils, lids
and whites, review see-through fills, and download a calibrated pack, a manifest,
or a commission kit for other artists.

It lives here, beside the agent tooling, rather than on the code branches: it is a
tool for preparing art, not part of the app.

**No art in this folder, ever.** The real portrait art is not committed anywhere
public. The tests take the pack from `PACKZIP`, a path outside the repo.

## Files

| File | What it is |
|---|---|
| `silhouette-loom-v2.html` | The source. Edit this one. |
| `release-loom.py` | Packages a release. Run it before every delivery. |
| `releases.json` | The ledger: each released version and its build hash. |
| `tests/` | Playwright checks, one script per feature. |

## Releasing

```bash
LOOM_EDN=/path/to/code-branch/resources/portrait-pack/loom.edn python3 agents/loom/release-loom.py
# -> agents/loom/release/silhouette-loom-v2.html (gitignored)
```

The script:
- refuses when `LOOM_VERSION` and the newest About entry disagree, or when the
  page changed but the version did not;
- bundles the working placements from `loom.edn` (on the code branches, so point
  `LOOM_EDN` at one) together with the pack version it records;
- stamps the build hash into the badge, title and About.

The file name stays `silhouette-loom-v2.html` on purpose, so a new copy replaces
the old one. The version shows inside the page.

To change it: edit the source, bump `LOOM_VERSION`, add an About entry, release,
run the tests against the release, commit the source and `releases.json` together.

## Tests

Each script launches Chromium against a released file and prints PASS/FAIL lines:

```bash
LOOM=agents/loom/release/silhouette-loom-v2.html PACKZIP=/path/to/portrait-pack.zip \
  OUT=/tmp/loom-out NODE_PATH=node_modules node agents/loom/tests/review.js
```

| Script | Covers | Extra env |
|---|---|---|
| `ver.js` | version badge and title | |
| `bundled.js` | bundled placements and build in About | `OUT` is a file path (manifest) |
| `mobile.js` | phone layout, view chips, pack version | `OUT` dir |
| `locks.js` | dragging, undo/redo, locks | `OUTDIR` |
| `lids.js` | lower lids, locking the upper lid | |
| `seethru.js` | see-through flags and the fill option | `OUT` dir |
| `review.js` | fill review: brushes, undo, edge margin | `OUT` dir |
| `review2.js` | See-through filter, zoom, brush starts Off | `OUT` dir |
| `persist.js` | reviews survive reload and travel in the manifest | `OUT` dir |
| `lines.js` | lines are left out of the automatic fill, and the option to include them | `OUT` dir |
| `leakview.js` | Show leaks: bright green, see-through amplified | `OUT` dir |
| `patch.js` | Patch gaps: paint behind the art, undo, reload, export, manifest | `OUT` dir |
| `stroke.js` | a quick stroke is a solid line (Paint, Add fill; zoom 1 and 2.5) | `OUT` dir |
| `session.js` | a refresh keeps the pack, placements, reviews, patches and pack version; Clear forgets it | `OUT` dir |

## The see-through fill: scope

The fill exists for one job: the speckled insides that show once a piece is
coloured or tinted (fills drawn at 80-99% opacity, which show the skin or
background through). It fills the solid inside of each piece, staying 3px
back from the outline, and leaves lines alone. Anything else -- a real hole
the fill does not reach -- is fixed by hand with Add fill, and only if it
shows in a portrait.

**Judge it on portraits**, not on single pieces: export a pack from the Loom,
render whole portraits through the app's renderer on light to dark skin, at
the size the builder shows them. Show leaks (green, see-through x4) finds
spots; it is not a pass mark -- nearly all hand-drawn art fails it.

### Patch gaps

For a gap *between* pieces -- bare skin where two pieces do not meet --
which no fill can close, because neither piece has pixels there. Paint it in
the Loom on the piece that should own it, with the neighbouring pieces shown
under and over it. The patch sits behind the art, so drawn pixels never
change. First use: hair front 02 with bangs 03, to retire the placeholder
scalp piece.

### Parked: further healing

Worth revisiting later, with a lot of scrutiny. What was learned (October 2026):

- Smoothing (closing every opacity dip up to ~4px, v2.35) cleared grain on
  single pieces but in portraits changed 30-100x more pixels than the fill:
  it closed the gaps between bang strands (filling them brown from the
  neighbouring lines) and smeared eye whites. Removed in v2.36.
- The artist draws some marks with transparency rather than tone: a part in
  hair front 02, a dash on shirt 03, strand shading on bangs 02/03. Any rule
  that closes holes automatically must not touch these; neither size nor
  shape separated them from real holes.
- Leaving lines alone (v2.33) is right but invisible in portraits.
- The hair back 02 gap is covered by the face in every portrait.
- Any candidate goes on the portrait grid beside the approved fill before it
  ships.
