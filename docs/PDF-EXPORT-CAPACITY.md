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
`503` with a `Retry-After` header, rather than held open until the browser gives
up with nothing to show for it.

The `Retry-After` is measured rather than guessed: the server keeps a weighted
mean of how long recent exports actually took, divides it into the queue ahead of
the caller, and floors the result at one second and caps it at thirty. So it
tracks your host and the sheets people are really asking for.

With the limit set to 1 and a 250 ms wait, twenty simultaneous exports produced
two sheets and eighteen refusals carrying `Retry-After` values of 1 to 5 seconds.
At the defaults, the same twenty all returned sheets.

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

Deeper notes on where the cost sits inside the PDF code — and what was removed to
get here — are in `docs/kb/pdf-form-techniques.md`.
