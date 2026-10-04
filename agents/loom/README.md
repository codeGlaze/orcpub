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
