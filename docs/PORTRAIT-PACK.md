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

## Tweaking by hand

Edit `pieces.edn`. The fields a piece can carry are listed at the top of the
file, and in `calibration-fields` in `portrait_assets.cljc`. A piece name or
field that doesn't exist fails `lein test` (`portrait-pack-test`), so a typo
can't quietly do nothing.

The art itself is never committed. These files are geometry and settings
only.
