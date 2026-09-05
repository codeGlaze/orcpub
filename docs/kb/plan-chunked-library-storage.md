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
plugins:v2:index              {:v 2 :sources ["Player's Handbook" "Volo's ..." ...]
                               :sizes {"Player's Handbook" 184203 ...}}
plugins:v2:src:<source name>  the EDN of that one source's plugin map
plugins:rejected              unchanged (name-keyed quarantine, usually small)
```

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

## The one genuine hazard: migration under a ~5 MB ceiling

During migration both copies exist. A 2.07 MB library peaks at ~4.14 MB against a ~5 MB
localStorage ceiling. This is the part of the plan most likely to hurt a real user, so it is
handled explicitly rather than hoped through:

- Migrate source-by-source, checking each `set-item` return (it already reports quota
  failure).
- On any failure: delete the partial v2 keys, keep the legacy blob untouched, stay on the v1
  path, and warn. A failed migration must be a no-op, never a lost library.
- Delete the legacy `plugins` key only after the v2 index and every slot have been read back
  successfully.
- A library too large to migrate is a library already at the ceiling. That is the existing
  hard limit documented in `perf-homebrew-builder-loop.md`, not a new one, and it is the
  case IndexedDB eventually solves.

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

**Phase 2 — v2 format + migration. Synchronous read still.**
Land the format, the dirty-tracked writer, and the migration. Deliberately keeps the read
synchronous so this phase is about correctness only, with startup timing unchanged. Verify
with `storage_shape_e2e.js` extended into a before/after assertion.

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

- IndexedDB. It is the eventual right tier (async, no ~5 MB ceiling, lazy per-source
  hydration) and this plan is a compatible stepping stone toward it, not a competitor.
- Any change to the `.orcbrew` file format. Files are already per-source.
- Lazy *class bodies* / the spell-detail work (Track 3) — separate, tracked separately.
