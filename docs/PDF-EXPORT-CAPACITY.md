# PDF export capacity

How much a character sheet costs to generate, what that means for a server, and
which settings to reach for. Written for whoever runs an instance and has to
decide how much machine to give it.

Every number here was measured on the code as it ships, on a four-core host with
Datomic in memory. The last section says how to reproduce them.

## The short version

- A sheet costs about **11 MB of heap while it is being made**, and nothing once
  it is sent.
- The server holds **35 MB** regardless of traffic, for the runtime and the
  templates.
- **A thousand people exporting at once is a queue, not a memory problem.** It
  drains in under a minute and needs well under a gigabyte.
- If you have cores and RAM to spare, raise `ORCPUB_PDF_CONCURRENCY`. That is
  the setting that matters.

## What one sheet costs

Generating a sheet means opening a template, adding a page per spellcasting
class, writing the character's values into the form fields, and saving. Style 1,
per export:

| character | heap while generating | time | file |
|---|---|---|---|
| no spellcasting | ~8 MB | 90 ms | 245 KB |
| one casting class *(most characters)* | ~11 MB | 218 ms | 285 KB |
| two casting classes | ~11 MB | 229 ms | 309 KB |
| six casting classes *(the worst the app allows)* | ~11 MB | 388 ms | 409 KB |

The heap figure is flat because the cost is dominated by having a document open
at all, not by how many pages it has.

## Why you may see a much larger number

Profiling this code reports **allocation churn** — every byte the export ever
asked for — and for a six-class sheet that is 162 MB. It is a real number and it
is not memory consumption. Nearly all of it is dead the instant it is created,
and the JVM reclaims it without effort.

The distinction is worth internalising because the two numbers differ by a factor
of fifteen:

- **Churn** is throughput. It costs CPU, because the garbage collector has to
  sweep it. It does not accumulate.
- **Footprint** is what is alive at once. It is what your heap has to hold, and
  it is the small number.

The proof is direct: **two hundred** six-class exports back to back complete in a
**48 MB** heap and fail at 40 MB. If churn were footprint, that could not happen.

The cost of churn shows up as time, not capacity. The same two hundred exports:

| heap | time per export |
|---|---|
| 64 MB | 362 ms |
| 48 MB | 543 ms |

Give the collector room and the garbage is nearly free. Starve it and the export
slows by half.

## What a thousand people at once means

It does not mean a thousand exports at once. Two limits stand in the way, and
both are deliberate.

Jetty's worker pool caps how many requests of any kind are in flight — Pedestal
sizes it at `(max 50 ...)`, so 50 until roughly sixteen cores. Inside that,
`ORCPUB_PDF_CONCURRENCY` caps how many of those may be generating a sheet, so a
rush of exports cannot take the workers that logins and saves need.

Everyone else waits, which costs a socket and nothing else. Heap needed against
exports actually in flight:

| concurrent exports | heap floor | per export |
|---|---|---|
| 8 | 96 MB | 7.6 MB |
| 32 | 384 MB | 10.9 MB |
| 50 | 448 MB | 8.3 MB |

And the drain, measured by running exactly one thousand exports on four cores
with a 1 GB heap:

| character | 1,000 sheets | rate |
|---|---|---|
| one casting class | 28.9 s | 34.6/sec |
| two | 46.7 s | 21.4/sec |
| six | 73.3 s | 13.6/sec |

So in the realistic case the thousandth person waits under thirty seconds. Even
if every one of them were a six-class multiclass it is about seventy, and it
falls proportionally as you add cores.

Budget for the request bodies as well: the handler caps a body at 2 MB, so 50 in
flight can hold 100 MB before any of it is parsed. **1 GB is comfortable for the
whole path.**

## Settings

All optional. A value that is present but not a positive integer is reported at
boot and the default is used, so a typo cannot take the server down or silently
mean zero.

| Variable | Default | What it does |
|---|---|---|
| `ORCPUB_HTTP_MAX_THREADS` | Pedestal's, which is 50 until ~16 cores | Jetty's worker pool: requests of any kind in flight. |
| `ORCPUB_PDF_CONCURRENCY` | `max(8, 2 x cores)` | Sheets generated at once. |
| `ORCPUB_PDF_QUEUE_TIMEOUT_MS` | `30000` | How long an export waits for a slot before the server says it is busy. |
| `ORCPUB_PDF_MAX_RETRIES` | `3` | How many times the busy page retries itself before waiting for a click. |
| `ORCPUB_PDF_MAX_CASTER_SECTIONS` | `13` | Most spellcasting sections one sheet may be grown to. Thirteen is every class in the game. |
| `ORCPUB_PDF_MAX_CARDS` | `200` | Most cards of one kind a single export prints. A level 20 wizard's spellbook is about 44. |

### Sizing them

**`ORCPUB_PDF_CONCURRENCY` is the one to think about.** Two ceilings apply and
you want the lower:

- **Heap.** An export in flight holds ~11 MB, so the limit is about
  `(usable heap - 100 MB) / 11 MB`. A 4 GB heap allows roughly 350.
- **Cores.** Throughput is bounded by CPU. Raising the limit past what the cores
  can chew through lengthens the queue without shortening anyone's wait — it
  just means more people are half-served at once instead of quickly served in
  turn.

Two cores' worth of concurrency is a sound starting point, which is what the
default gives you. On a sixteen-core host with plenty of RAM, 32 to 64 is
reasonable; going to 300 because the heap allows it will make things worse, not
better.

Raise `ORCPUB_HTTP_MAX_THREADS` alongside it if you set the export limit above
the HTTP pool, or exports will simply queue a level higher up.

## What people see when it is saturated

An export that cannot get a slot within `ORCPUB_PDF_QUEUE_TIMEOUT_MS` is answered
`503` with a small page saying the server is busy — and that page **retries itself**.

The export is a plain form POST into a new tab, so the 503 response is the page
the person is already looking at. The retry lives there rather than in the app:
the page carries the original request body forward in a hidden field, counts down,
and resubmits. After `ORCPUB_PDF_MAX_RETRIES` it stops and waits to be clicked.
Nothing in the character builder changed, and how the finished PDF arrives is
untouched.

The countdown is measured rather than guessed. The server keeps a weighted mean of
how long recent exports actually took, divides it into the queue ahead of the
caller, floors the result at one second and caps it at thirty — so it tracks your
host and the sheets people are really asking for. The page then applies ±25%
jitter, so a crowd turned away together does not come back in the same instant.

The retry count is deliberately small. The queue drains in under thirty seconds in
the realistic case, so three attempts covers it; the point is to spare someone a
wait they would abandon anyway, not to retry forever. The counter is read back from
the page's own hidden field, and a hand-edited value counts as a first try rather
than buying extra attempts.

The page looks like the rest of the site: the app's fixed gradient ground, its
`#1a1e28` panel, white type, the header bar with the logo, and `.form-button` for
the button. Its rules live in `orcpub.styles.core` with the rest of the
stylesheet, so `lein garden once` has to have run for it to look right. The
builder's markup and scripts are absent in this tab, so the page restates the
ground and panel rather than reusing app layout classes.

### Seeing it on a dev machine

The default limits are too generous to reach by hand, so there is a profile that
shrinks them — one sheet at a time, a quarter-second wait for a slot, two
self-retries:

```
lein e2e-server-busy
```

Then click Export in the builder twice in quick succession, or drive the whole
thing:

```
node test/browser/export_busy_retry_e2e.js
```

That test goes through the real UI — Export, pick a sheet style, Create PDF —
while holding every export slot, and checks that the new tab lands on the busy
page, carries the site header, retries itself unattended, stops at the limit, and
delivers the sheet once the rush passes.

Measured with those limits: twenty simultaneous exports produced two sheets and
eighteen busy pages carrying `Retry-After` values of 1 to 5 seconds. At the
defaults, the same twenty all returned sheets.

One thing to expect: a browser logs `Failed to load resource: 503` in its console
for each turned-away attempt. That is the browser reporting an HTTP status, not a
script error, and it is the correct status to send — a `200` would lie to caches,
crawlers and any non-browser client.

## Reproducing the measurements

Heap floors come from running a fixed number of exports under a constrained
`-Xmx` and bisecting until it fails:

```
java -cp "$(lein classpath)" -Xmx48m clojure.main your-export-loop.clj
```

Note that `lein classpath` includes `dev/`, whose `user.clj` loads Datomic at
REPL init and will not fit in a small heap. Drop that entry before running.

Churn is measured with `ThreadMXBean.getThreadAllocatedBytes` around the call.
Do **not** use `totalMemory - freeMemory` for either question: it counts
allocation not yet collected and misses allocation already collected, so it
answers neither churn nor footprint.

## What bounds the work one request can buy

The queue limits how many exports run at once and how long a request waits for a
slot. Neither limits how long a slot is HELD, so the work inside one export has to
be bounded too. Two inputs decide it and both come from the caller:

- **The spellcasting section count** is the largest N in any `spellcasting-class-N`
  field NAME. Unbounded, a body of a few dozen bytes asked for thousands of cloned
  pages at about 14 MB each: measured, `spellcasting-class-9999` ran for 310
  seconds and died with an out of memory error. Clamped by
  `ORCPUB_PDF_MAX_CASTER_SECTIONS`, the same request answers 200 in 0.8 s.
- **The card count** is however many spells or items the caller lists, nine to a
  page. A 2 MB body holds about 60,000 spell entries -- some 13,000 pages, a
  quarter of an hour with a slot held throughout. Clamped by
  `ORCPUB_PDF_MAX_CARDS`; a request for 4,000 now answers in 2.2 s and logs that
  it printed the first 200.

Note that the section count is derived in TWO places -- the handler and
`add-missing-spell-pages!` -- and clamping only the handler left the endpoint
exactly as vulnerable. That was caught by firing the request at a running server
rather than reasoning about the code.

**So the ceiling is applied to the request, not to the generators.**
`routes/bound-request` runs once on the parsed body before any part of the export
sees it, and does two things: a `spellcasting-class-N` name past the section
ceiling is dropped outright, and every collection is truncated to the card
ceiling. Because the field never arrives, no downstream reader can derive a number
too large however many of them there are -- which is the failure the first fix
walked into.

The point is that it holds for code nobody has written yet. A feature added later
that reads a list out of the request, or counts `spellcasting-class-N` names, is
bounded without being wired up. `export_capacity_test` proves this the only way
worth proving it: it disables every per-site clamp and checks the request is still
bounded, so if the boundary ever regresses the test fails rather than the
generators quietly covering for it.

The per-site clamps stay as defence in depth. They are no longer what is doing
the work.

## Character images and your deployment

A character's portrait is a URL, and the **server** fetches it while building the
sheet. That is the only outbound request the app makes to an address a visitor
chose, so it is bounded on every axis: http and https only, private and reserved
addresses refused, redirects not followed, 128 KB, 2000x2000, and 20 seconds of
transfer. The host is resolved **once** and the connection is made to that answer,
so a name that resolves differently the second time cannot be used to slip past
the address check.

Three deployment questions that follow from it:

- **Behind nginx?** Yes, and nothing to configure. `deploy/nginx.conf.template`
  is an inbound reverse proxy; the image fetch is outbound and never passes
  through it.
- **In Docker?** The first resolution uses the container's own DNS, so Docker's
  embedded resolver works normally. Note that `10/8`, `172.16/12` and `192.168/16`
  are refused, so a character image URL cannot be pointed at another container on
  the compose network. That is deliberate, and it is the same rule that stops it
  reaching your database or the cloud metadata endpoint.
- **Behind an egress proxy?** Also nothing to configure. If the JVM is started
  with `https.proxyHost` (or the usual proxy properties), the app detects it and
  lets the proxy do the resolving, because it is then the egress control point.
  The shipped compose files set no proxy, so the resolve-once path is what runs by
  default.

If you deliberately want the server to reach an internal image host, there is no
allowlist for it today — the refusal is by address range, with no exceptions.
