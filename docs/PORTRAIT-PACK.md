# Portrait pack calibration

Each piece of portrait art can carry settings beyond its name: where its
irises are, whether it casts a shadow, whether it's lips. Those settings live
in two files, not in code, so tweaking a piece is a small edit to one file.

| File | Holds | Edited by |
|---|---|---|
| `resources/portrait-pack/loom.edn` | What the Loom places: iris regions, pupils, upper lids, lower lids and whites | The import command, never by hand |
| `resources/portrait-pack/pieces.edn` | Our choices: which mouth is lips, which bangs cast shadows, iris gamma | Hand, with comments saying why |

Both are keyed by layer, then by the art's file name in lower case. Where they
overlap, `pieces.edn` wins. They are read at build time and compiled into both
the server (share card) and the browser bundle (builder, PDF), so a change
needs a rebuild.

The list of pieces itself (ids and labels) stays in `portrait_assets.cljc`,
because saved portraits refer to those ids and they must never shift.

## After a Loom session

Export the manifest from the Loom, then:

```bash
lein run -m orcpub.portrait-pack.import manifest-v1.6.json --dry-run   # see what would change
lein run -m orcpub.portrait-pack.import manifest-v1.6.json             # write loom.edn
```

It prints exactly which pieces were added, changed or removed, with the old
and new values for each change. It **stops** if the manifest lists one piece
twice with different values. Decide which is right and re-run with
`--prefer first` or `--prefer last`. It also lists pieces the manifest has
and the registry doesn't; add those to `asset-inventory` before they can be
chosen.

## After new or changed hair art

Hair streaks follow the strands drawn in the art. Which way they run is
worked out from the linework once, ahead of time, and stored beside each
hair piece as `<name>.strands.png` (a small greyscale image, 5–15KB):

```bash
lein run -m orcpub.portrait-pack.strands                     # resources/public/image/portraits
lein run -m orcpub.portrait-pack.strands /path/to/portraits  # a pack kept elsewhere
```

The image build runs it (`docker/Dockerfile`, after the JavaScript build), so
a deploy needs only the art: it writes the files that are missing or older
than their art, and `--all` rewrites every one. They are derived from the art,
so they are gitignored and never committed. A piece without one (running from
source without the step) still streaks: the server works the field out the
first time it is asked for (about 0.1–0.5s), then keeps it.

## Tweaking by hand

Edit `pieces.edn`. The fields a piece can carry are listed at the top of the
file, and in `calibration-fields` in `portrait_assets.cljc`. A piece name or
field that doesn't exist fails `lein test` (`portrait-pack-test`), so a typo
can't quietly do nothing.

The art itself is never committed. These files are geometry and settings
only.

## The licensed art: private branch only

This repository carries only the anonymised silhouettes. The licensed art goes in one commit on
the private deployment branch (Gitea), which is built and deployed like any other branch; the
image build makes the strand files, so the art PNGs are all it needs.

**Laying in a pack** (first time, or a new Loom export), on the private branch, with a clean tree:

```bash
scripts/private-art.sh update portrait-pack-v1.775.zip
git push        # to the private remote
```

It copies each PNG over the silhouette of the same layer and name, writes
`resources/public/image/portraits/PRIVATE-ART`, and commits. It refuses on a branch whose remote
is GitHub (fetch or push address), or that tracks no remote.

**Taking in new code:** merge the GitHub branch into the private branch, as usual. The art commit
stays on top. Never merge or push the private branch the other way.

**The guards:**

- `orcpub.portrait-art-guard-test` checks every portrait PNG against the digest of its silhouette
  (`test/portrait-silhouettes.edn`), so art committed here fails the suite. The marker turns it off
  on the private branch. A new or redrawn silhouette is recorded with
  `PORTRAIT_SILHOUETTES=record lein test :only orcpub.portrait-art-guard-test`.
- `.github/workflows/portrait-art-guard.yml` fails any push or pull request on GitHub that carries
  the marker, which is the private branch arriving here.
- The server serves only PNGs from the art directory, so the marker and any manifest are never
  public, and each piece carries `X-Robots-Tag: noai, noimageai`.
