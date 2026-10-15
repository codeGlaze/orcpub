# Serving portraits: work, caches and limits

How the server spends CPU and memory on images, and how the pieces fit. Read this before adding a
cache, a semaphore or another image route: there is one way to do each.

## The two shared tools

| Tool | What it is | Used by |
|---|---|---|
| `orcpub.work-pool` | A fair permit pool for one kind of heavy work, with a waiting count and a running mean of how long the work takes. `run` answers `{:result x}` or `{:busy retry-seconds}`; the Retry-After is the queue ahead over how fast permits drain, 1..30 s. | PDF export, the image probe, portrait draws |
| `orcpub.bounded-cache` | An LRU cache bounded by count and optionally age, checked on every read. `fetch!` is single-flight: concurrent misses for one key share one computation; a failure is not kept. | probed pictures, drawn portraits (in front of their files), strand fields (parsed and worked out) |

Each pool is separate from the HTTP worker pool (`ORCPUB_HTTP_MAX_THREADS`), so a rush of one kind
of work waits for its own permits while pages, logins and saves keep their workers.

## The pools

| Pool | Size | Wait | When full |
|---|---|---|---|
| export | `ORCPUB_PDF_CONCURRENCY` (`max(8, 2 x cores)`) | `ORCPUB_PDF_QUEUE_TIMEOUT_MS` (30 s) | 503 + the self-retrying busy page |
| probe | 8 | none | `rate-limited` (the endpoint needs no login, so it never queues) |
| portrait | `ORCPUB_PORTRAIT_CONCURRENCY` (`max(2, cores / 2)`) | `ORCPUB_PORTRAIT_QUEUE_TIMEOUT_MS` (5 s) | 503 + Retry-After |

The portrait pool is small because a draw is CPU, not I/O: about half a second of one core at
600 x 750 (measured on the silhouettes: ~20 ms reading and decoding ten layers, ~35 ms encoding,
the rest compositing, most of it the per-pixel ombre over the hair layers). The wait is short
because link-preview fetchers give up quickly.

## Stored drawings

The server draws each character's portrait itself and keeps the drawing as a file,
`<PORTRAIT_STORE_DIR>/<character id>-<fingerprint>.png` (`orcpub.portrait-store`). The default is
`orcpub-portraits` in the system temp directory, because the Docker image runs from `/` as a user
that can write only `/tmp`; for a shared volume, mount it at `/var/lib/orcpub/portraits` (created
for that user in the image) and set the variable to it. A write that fails is logged and the
drawing is served anyway.

- **Fingerprint.** The saved selection, the credit line, the site mark, and a digest of the
  registered art files and the drawing code (read from the classpath). It does not depend on the
  process, so a kept file is good across restarts, and any change to the art or the drawing code
  draws every portrait again.
- **On save.** The store compares the fingerprint with the kept file. Autosave fires every few
  seconds for edits like hit points; those find the file already there and do nothing. A changed
  portrait is drawn once, 3 s after the last save of a burst, with what was saved last. A removed
  portrait's files are deleted.
- **On request.** Memory (64 drawings), then the file, then a draw. Characters saved before this
  existed are drawn on their first request and kept.
- **Limits.** Every draw takes a portrait-pool permit; concurrent requests for one drawing share
  it. A save never waits for a draw.

**Decision: files, not the database.** Datomic keeps every past value forever and is not meant
for large values, and these are derived, so a lost file is only drawn again. A shared volume lets
several containers draw each portrait once; without one, each container draws on first request
and keeps its own copy. (Decided in an earlier session and stated only in chat then; recorded here.)

The browser's own picture is used only for the PDF the owner exports, which goes back to them
alone. The server never stores or serves a picture a browser made: it cannot tell one from any
image someone uploads, and the shared picture carries the artist's name.

## A shared link, end to end

1. The character page names the picture by version: `/pages/dnd/5e/characters/:id/portrait.png?v=<16 hex>`, the
   start of the portrait's fingerprint. It refreshes the artist credits first, so the version and
   the credit agree with what the image route will compute.
2. The image route pulls the stored portrait and computes the same fingerprint. No drawing yet:
   - `If-None-Match` covering it: 304.
   - `?v=` matching it: the response is `public, max-age=31536000, immutable`, because that address
     never changes content. Bare or stale: `public, max-age=300`.
3. Then the store, as above: memory, file, draw. The crawlers a new link sends at once (Discord,
   Slack, Mastodon instances...) share one draw. Nothing drawable is answered 404.

When a portrait changes, its fingerprint and so its address change: chat apps that cached the old
picture under the old address fetch the new one, and nothing has to be purged.

## Artist-page examples

`/artists/<slug>/examples/<n>` draws its small watermarked JPEG through the same portrait pool
(`portrait-store/draw-pool`), single-flight and kept in a bounded cache keyed by artist, example and
watermark text (the text comes from the artist's links, so an edit makes new keys and old ones age
out). A failed draw is remembered for 60 s, so a broken example is not redrawn on every request. A
busy pool answers 503 + Retry-After. The credits are refreshed first, so a fresh container
watermarks with the artist's current links.

## Strand fields

The build step writes `<piece>.strands.png` beside each hair piece. Without it (running from
source, or a deploy that skipped the step), `orcpub.portrait-pack.strands/worked-out-png` works
the same PNG out from the art once and keeps it. Both consumers read those bytes, the builder over
HTTP (`/image/portraits/...strands.png`) and the server renderer directly, so a deploy without the
files draws exactly what one with them draws (`portrait-render-test` checks it pixel for pixel).
The renderer keeps the parsed fields in their own cache.

## Seeded randomness

Everything that must come out the same in the browser and on the server goes through
`orcpub.dnd.e5.portrait-random`: Randomize's seed hash (FNV-1a) and generator (mulberry32), and
the grid hash behind hair streaks and freckles. Plain `*` and `(int c)` differ in ClojureScript,
which once made every browser Randomize return the same portrait. One-off UI randomness
(`rand-int`, `shuffle` in the character randomizer) and secrets (`SecureRandom`, UUIDs) are
different jobs and stay where they are.

## Artist credits in the browser

Artists can edit their credit, so it never goes into the page's HTML or a script. The browser
fetches `/dnd/5e/portrait-artists` (JSON, cached a minute) the first time a view that shows a
credit subscribes to `:portrait/artist-overrides`, once per page load, and keeps it in app-db
there; a page with no portrait never asks. Nothing is fetched at start-up: work a page does not
need stays off the load path. A credit drawn before the fetch updates when it lands. Until then the views, and a PDF exported in that
moment, show the registry's public default. Server-side pictures and share cards read the same
merge (`artist-credit/refresh!`) directly.

## What the browser does instead

The builder draws on a canvas; the PDF export posts the portrait the browser baked (it goes back
only to the person who asked, so the browser's pixels are trusted there and nowhere else). The
server never stores a browser-made image: the saved portrait is the selection, and every server
picture is drawn from registered art (see the roadmap for why).

## Open, measured on real art only

The real art is larger than the silhouettes, so decoding is a bigger share of a draw there. Two
changes wait on that measurement on the private deployment:

- decode each layer once per draw (today some layers are decoded by the drawing step and again for
  the crown, skin and light passes);
- keep decoded, placed layers across draws (bounded: one placed layer is 1.8 MB at 600 x 750).
