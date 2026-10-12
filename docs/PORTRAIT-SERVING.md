# Serving portraits: work, caches and limits

How the server spends CPU and memory on images, and how the pieces fit. Read this before adding a
cache, a semaphore or another image route: there is one way to do each.

## The two shared tools

| Tool | What it is | Used by |
|---|---|---|
| `orcpub.work-pool` | A fair permit pool for one kind of heavy work, with a waiting count and a running mean of how long the work takes. `run` answers `{:result x}` or `{:busy retry-seconds}`; the Retry-After is the queue ahead over how fast permits drain, 1..30 s. | PDF export, the image probe, portrait draws |
| `orcpub.bounded-cache` | An LRU cache bounded by count and optionally age, checked on every read. `fetch!` is single-flight: concurrent misses for one key share one computation; a failure is not kept. | probed pictures, drawn portraits, strand fields (parsed and worked out) |

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

## A shared link, end to end

1. The character page names the picture by version: `/character/:id/portrait.png?v=<12 hex>`, the
   start of the portrait's validator (stored EDN, credit line, site mark, artwork epoch). It
   refreshes the artist credits first, so the version and the credit agree with what the image
   route will compute.
2. The image route pulls the stored portrait and computes the same validator. No drawing yet:
   - `If-None-Match` covering it: 304.
   - `?v=` matching it: the response is `public, max-age=31536000, immutable`, because that address
     never changes content. Bare or stale: `public, max-age=300`.
3. A miss goes to `rendered-portraits` (64 entries, keyed by validator). The crawlers a new link
   sends at once (Discord, Slack, Mastodon instances...) share one draw.
4. The draw takes a portrait-pool permit. Nothing drawable is cached too (nil, answered 404).

When a portrait changes, its validator and so its address change: chat apps that cached the old
picture under the old address fetch the new one, and nothing has to be purged.

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
