# Plan: chunked per-source library storage

Status: **proposed, not started.** Supersedes the Track 1 sketch in
`perf-homebrew-builder-loop.md`, which established the *why*; this is the *how*.

## The problem, in one line

The homebrew library is 13 sources in memory and **one localStorage value** on disk
(measured: 2,166,081 chars for MegaPak — `test/browser/storage_shape_e2e.js`). So every
save rewrites all 2.07 MB, and every load parses it back through a single blocking
`read-string` (~750 ms, the largest single piece of the builder-open freeze).

## The governing constraint

**The in-memory shape does not change.** `app-db :plugins` stays `{source-name -> plugin}`.

This is what makes the change safe. Import, export, quarantine, reconciliation, validation,
cross-source key-conflict detection and character building never touch localStorage — they
operate on that map and hand it to `::e5/set-plugins`. Everything above the storage layer is
untouched by design, not by luck. Any step that would change the in-memory shape is out of
scope for this plan.

Concretely, the blast radius is two functions in `db.cljs`:
`plugins->local-store` (write, line 265) and the `::e5/plugins` cofx (read, line 460).

## Storage schema v2

```
plugins:v2:index                        {:v 2 :chunks [...] :sizes {chunk-key chars}}
plugins:v2:src:<source>                 a whole source (the common case)
plugins:v2:src:<source>#<content-type>  one content group, when a source must be split
plugins:v2:src:<source>#meta            that source's non-content scalars (:disabled? etc.)
plugins:rejected                        unchanged (name-keyed quarantine, usually small)
```

Most sources are one chunk. The split forms exist so migration and load are never blocked by
a single oversized source (see the correction below). Reassembly per source is
`merge-plugins`, which already exists.

Two decisions worth recording:

- **Keys carry the source name verbatim, not a slot number.** localStorage keys are
  arbitrary strings and source names are already unique (they are map keys). Slot numbers
  would avoid escaping questions entirely but make devtools unreadable, and this subsystem
  is built around a human recovering broken content by hand (corrupt slots, emergency
  export). Debuggability wins.
- **The index is written LAST and is authoritative.** It is how a half-finished write
  becomes detectable instead of silent: `:sizes` lets load verify each slot arrived whole.
  Sources present on disk but absent from the index are orphans, cleaned on next write.

## Why the write gets cheaper: dirty tracking

Writing all N sources on every `set-plugins` is not a win — it is the same bytes in more
calls. The win needs a dirty check.

`plugins-interceptors` is `[(path :plugins) plugins->local-store-interceptor]`, so the
writer receives the plugins map after every edit. ClojureScript maps are persistent: an
`assoc-in` into one source leaves the other twelve **`identical?`** to their previous
values. Hold the last-written map in a module-level atom and write only sources that fail
`identical?`.

Result: toggling one book off rewrites that one book, not 2.07 MB.

## Why the read gets faster: it must yield, not just split

Stated plainly because it is the easiest thing to get wrong: **splitting the parse into N
smaller parses run back-to-back does not help.** Same total work, same one long task, same
frozen tab. The spike's 2.3x–5.9x longest-task reduction came from *yielding between
chunks*, which means startup hydration becomes incremental and asynchronous.

That is the real cost of this plan, and the reason it is phased. Two traps it creates:

1. **Hydration must not write back what it just read.** Each source arriving would otherwise
   fire the write interceptor. Seeding the dirty-tracking atom from the load makes every
   hydration step a no-op write for free — the mechanism that makes writes cheap also
   solves this.
2. **The app must tolerate a partially-loaded library for a few frames.** It already does in
   principle: before any homebrew loads, the app runs on built-in content, and sources
   arriving one at a time is the same shape as a user importing them one at a time. To be
   proven, not assumed (Phase 3).

## Auditing, quarantine and repair: what changes

Nothing in their logic. `salvage-library-items` is a `reduce-kv` over sources and
`reconcile-rejected-items` is name-keyed; both already work per source, and both keep
running on the assembled map exactly as now.

What improves is the failure granularity above them:

| Failure | Now | After |
| --- | --- | --- |
| One source unparseable | whole library -> `plugins:corrupt`, nothing loads | that source -> `plugins:v2:src:<name>:corrupt`, the rest loads |
| Invalid item in a source | already per-item (`salvage-library-items`) | unchanged |
| Half-finished write | silent truncation, looks like corruption | index/`:sizes` disagree, detected on load |
| Quota exceeded | atomic: all or nothing | **per-key: can half-succeed — see below** |

`orcbrew-val/correct-library` stays whole-library and should: its cross-source conflict
detection genuinely needs every source at once, and it still gets one, since all sources are
assembled in memory as before.

## The migration hazard, measured — and why copy-then-delete is dead

The first draft of this plan proposed writing v2 alongside the legacy blob and deleting
legacy once v2 read back clean. **That is wrong for real libraries and is withdrawn.** The
reversal is recorded rather than overwritten because the reasoning matters.

Measured ceiling in Chromium (probe, not folklore):

```
localStorage ceiling:   5,177,344 chars   (~4.94 M chars)
                        identical for ASCII and CJK fills
                        -> Chrome counts UTF-16 code units, not UTF-8 bytes,
                           so the limit is a CHARACTER count and library size
                           can be compared to it directly

navigator.storage.estimate().quota:  916,414,672   <- IndexedDB/CacheStorage, NOT localStorage
```

Against that ceiling:

| Library | Doubled | Fits under 5.18 M? |
| --- | --- | --- |
| MegaPak, 2,166,081 chars (measured) | 4.33 M | yes, ~0.84 M spare |
| a 3 MB `.orcbrew` (~2.7 M chars) | 5.4 M | **no** |

Copy-then-delete therefore works only below ~2.58 M chars, and users are already past that.
The strategy has to change, not the safety margin.

### Incremental migration with a shrinking legacy blob

Move one source at a time and shrink the legacy blob as you go, so both copies never exist
in full:

```
parse legacy once (already happens on load) -> full map held in JS heap

for each source, SMALLEST FIRST:
    write  plugins:v2:src:<name>
    rewrite legacy blob without that source
```

Peak storage is `legacy-remaining + the one source being moved`, never `2 x library`.
Smallest-first keeps the early steps (when legacy is still large) cheap, and by the largest
source the legacy blob has nearly drained:

```
step 1     : L + s_min        e.g. 2.7 M + 0.1 M = 2.8 M
final step : 2 x s_max        e.g. 0.6 M + 0.6 M = 1.2 M
```

A 2.7 M-char library peaks around 2.8 M chars. Comfortably clear.

### Every intermediate state is valid, so interruption is not a failure

The loader reads **the union of the v2 slots and whatever remains in the legacy blob**. A
half-migrated library is a legitimate state, not damage to recover from. Migration is
finished when the legacy blob is empty and its key is dropped.

This removes the "failed migration must be a strict no-op" requirement entirely — there is
no partial state to roll back, and a tab closed mid-migration simply resumes next load.

On a `set-item` failure the migration stops where it is, leaves the union valid, and warns.
It is best-effort and partial by design.

### CORRECTION: there is no un-migratable case — the chunk is not the source

An earlier draft of this section claimed a source larger than ~2.58 M chars could not be
migrated, and the spike analysis in `perf-homebrew-builder-loop.md` separately claimed
chunking's benefit was permanently capped by the largest single source. **Both claims were
wrong, and both were the same mistake**: fixing the chunk at one source and then treating the
consequences as fundamental. Recorded rather than overwritten, because the error is the
instructive part.

Granularity recurses: **source -> content group -> item.** A source is
`{qualified-keyword content-type {item-key item}}` plus non-content scalars, and
`merge-plugins` (`merge-with merge`) is already exactly the reassembly operation, already
in use and tested.

Measured group sizes in MegaPak (`_gran` probe over the real imported library):

```
source                              chars      largest content group
Mordenkainen's Tome of Foes         383,817    :monsters    366,488
Monster Manual                      347,500    :monsters    346,461
Eberron - Rising from the Last War  271,165    :monsters    150,039
Xanathar's Guide to Everything      270,333    :spells      146,108
Player's Handbook                   203,108    :subclasses   67,335
Default Option Source                     2
```

Moving a chunk of size `c` while the legacy blob holds `L` peaks at `L + c`, so the only
requirement is `c <= ceiling - L`. For a 3 M-char library that allows `c` up to ~2.18 M;
real content groups top out at 366 K and items are kilobytes. The constraint stops binding
as soon as the chunk can be smaller than a source.

### Batch the compaction, not the chunk

Rewriting the legacy blob once per chunk would be far too slow at item granularity. Batch
instead:

```
loop:
  fill the available headroom with chunks (source, else content group, else item)
  write each as its own v2 key
  compact the legacy blob ONCE, minus all of them
```

Headroom grows as the blob drains, so batches accelerate. A 3 M library: batch 1 moves
1.98 M, batch 2 moves the remaining 1.02 M — **two compactions**, ~4 M chars of
serialization total. A 4.5 M library sitting near the wall takes about four. Both are
one-time and fast.

Rule: **use the coarsest chunk that fits the current headroom.** Whole sources for almost
every library; content groups only when a source will not fit; items only for a pathological
single-group source.

### What this fixes beyond migration

The longest parse task is now bounded by *chunk* size rather than by the largest source. The
user with one enormous single-source homebrew pack — written off in both the earlier plan and
the spike analysis — gets the same benefit as everyone else.

### One shape detail the data surfaced

`Monster Manual` carries `:disabled? 5` alongside its content groups, and
`salvage-plugin-items` explicitly preserves such non-content entries. Splitting a source must
put those scalars in their own small meta chunk rather than assuming a source is nothing but
content groups.

### Stated plainly: this does not raise the ceiling

Chunking makes loads faster and failures narrower. It does **not** give anyone more room —
same bytes, plus a little per-key overhead, so total usage rises slightly. Users at 3 M
chars are near the wall before and after.

Capacity is a separate problem with a separate answer: the same probe measured ~916 MB of
origin quota available to IndexedDB against ~5.18 M chars for localStorage, roughly 180x.
That argues for promoting the IndexedDB migration on **capacity** grounds independently of
this perf work, and this plan is deliberately shaped as a stepping stone toward it.

## Phases

Each phase is independently revertible and leaves the app working.

**Phase 0 — characterization net. No behavior change.**
Pin what today does before touching it, so the change shows up as a visible diff in expected
values. JVM (`lein test`) for the pure functions in `e5.cljc`; the headless CLJS suite
(`lein fig:test`, real localStorage) for the storage layer, extending `db_test.cljs`, which
already pins corrupt-slot behavior. Cover: multi-source round-trip, quota failure, the
bare-colon self-heal, and quarantine reconciliation across a reload.

**Phase 1 — pure seam. No behavior change.**
Extract `split-library`, `assemble-library` and `plan-writes` into `.cljc` and re-express the
current writer/reader in terms of them. Prove output is byte-identical to today. This moves
the interesting logic somewhere the JVM suite can reach it.

**Phase 2 — v2 format + incremental migration. Synchronous read still.**
Land the format, the dirty-tracked writer, the union loader, and the shrinking-legacy
migration. Deliberately keeps the read synchronous so this phase is about correctness only,
with startup timing unchanged. Must be tested from an interrupted migration (v2 slots plus a
partial legacy blob), not only from a clean one. Measure the migration's own cost: it
rewrites the legacy blob once per source, roughly `N/2 x library` of serialization one time,
which may need batching (shrink every K sources) or a progress indicator. Verify with
`storage_shape_e2e.js` extended into a before/after assertion.

**Phase 3 — async chunked hydration. The actual win.**
Yield between sources. Measure with the probes already committed (builder-open split, churn
under 4x CPU throttle). Target: builder open under 400 ms.

**Phase 4 — narrow the failure granularity.**
Per-source corrupt slots, orphan cleanup, and surfacing a single failed source in the
library-health UI instead of an all-or-nothing corrupt slot.

## How each phase is proven

The CLJS harness was verified working before writing this plan, not assumed: `lein fig:test`
builds `target/test/js/test.js`, and loading it in headless chromium runs **236 tests / 722
assertions, 0 failures**, with `db-test` exercising a real `localStorage` (its corrupt-slot
and quota warnings show up in the console output). So the storage layer genuinely can be
tested where it lives.

No phase is done on inspection. Phases 0-2 must be green on `lein test` and `lein fig:test`;
Phase 2 additionally on the real-import browser probe; Phase 3 on measured longest-task
numbers against the same fixtures used for the baseline, not on a plausible-looking diff.

## Explicitly not in this plan

- IndexedDB. It is the eventual right tier and the only answer to **capacity** (~916 MB of
  measured origin quota vs ~5.18 M chars for localStorage). This plan is a compatible
  stepping stone toward it, not a competitor — but see the note above: it should be
  scheduled on capacity grounds regardless of how this perf work lands.
- Any change to the `.orcbrew` file format. Files are already per-source.
- Lazy *class bodies* / the spell-detail work (Track 3) — separate, tracked separately.
