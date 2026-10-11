# Artist pages: tests seen failing

`AGENTS.md`, "Tests prove their fail state": every test added for the artist pages' examples
and the pieces-grid removal was run against a deliberate break of what it guards, seen to fail
on the expected checks only, and passed again once the source was restored with
`git checkout HEAD -- src`. This is the record for the PR description. Run 2026-10-11 on
`claude/artist-profile-pages` at `4db0f040`, plus the strengthened watermark assertion below.

One test was weak and was strengthened first: `the-examples-are-small-watermarked-jpegs`
never checked the watermark, so dropping it would have passed. It now compares each served
image with the same example rendered without one.

## JVM (`lein test`)

| Break | Test | Failed |
|---|---|---|
| Examples use each layer's first piece instead of rotating | `between-them-the-examples-use-every-piece` | 1, plus 9 in `examples-are-whole-drawable-and-different`, which guards the same rotation |
| `mulberry32` adds where the reference XORs (the old code) | `the-seeded-randomness-is-the-same-on-both-platforms` | 1 (the generator pin) |
| Watermark leads with the name instead of the link host | `the-examples-are-watermarked-with-her-site-and-ours` | 2 |
| `example-path` counts from 2 | `examples-have-their-own-addresses` | 1 |
| Route is `/example/` instead of `/examples/` | `the-route-map-round-trips`, `examples-have-their-own-addresses` | 2 |
| Examples rendered at 600×750 | `the-examples-are-small-watermarked-jpegs` | 3 (one per example) |
| Served as `image/png` | `the-examples-are-small-watermarked-jpegs` | 3 |
| Watermark not drawn | `the-examples-are-small-watermarked-jpegs` | 3 |
| Cache removed (`memoize` → `identity`) | `each-example-is-rendered-once` | 1 |
| `example-count` 4 | `there-are-only-three-examples` | 1 |
| Handler ignores the slug and the off switch | `a-page-the-artist-turned-off-is-a-404`, and `an-unknown-slug-is-a-404` | 2 |
| `og:image` points at example 2 | `the-profile-carries-its-own-share-tags` | 1 |

## ClojureScript (`node scripts/test/run-cljs-tests.js`)

| Break | Test | Failed |
|---|---|---|
| `seed->int` as it was: `(int c)` and a plain multiply | `the-seeded-randomness-is-the-same-on-both-platforms` | 2 (both hash pins). The JVM passes this break: the bug was browser-only. |

## Browser (`test/browser/artist_profile_e2e.js`)

| Break | Failed checks |
|---|---|
| Profile examples composed in the browser again | `[dark]`/`[light] every example is a small image the server made`; `[dark]`/`[light] no layer art is downloaded` |
| The artist list's card composed in the browser again | `the list downloads no layer art either` |
| Lede without the count | `[dark]`/`[light] the lede still says how many pieces she drew` |
| `og:image` points at example 2 | `og:image is the page's first example` |
| Served as `image/png` | `the examples are served as JPEGs` |

After every break: source restored, worktree clean, and the full run green (JVM 708 tests,
cljs 542, browser 46 checks).
