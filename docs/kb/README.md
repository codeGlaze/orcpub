# Knowledge base

Findings that were expensive to get and should not be re-derived. Each doc records what was
measured, what turned out to be wrong, and why.

## Performance

- **[perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)** -- the builder freeze
  with large homebrew libraries. Root cause: `cljs.core/memoize` cache lookups that
  deep-compare every class in the library (1125 ms -> 100 ms). Also covers the storage
  layer, heap behaviour under class browsing, and several withdrawn conclusions.
- **[perf-entity-build.md](perf-entity-build.md)** -- `entity/build` cost, and the
  `kahn-sort` rewrite (23.0 -> 3.0 ms JVM, 25.2 -> 4.9 ms browser) with the CLJS
  set-ordering trap that made a JVM-green rewrite diverge in the browser.
- **[plan-chunked-library-storage.md](plan-chunked-library-storage.md)** -- *parked.*
  Per-source localStorage keys. Measured ceiling 5,177,344 chars; why copy-then-delete
  migration is dead; why this does not fix the reported freeze.

## Practice

- **[memoize-antipattern-scan.md](memoize-antipattern-scan.md)** -- every `memoize` site
  scanned and traced. Four are dead code; `memoized-spell-option` is measurably 10x slower
  than no cache. Planned, not executed.
- **[verification-discipline.md](verification-discipline.md)** -- how this repo has been
  wrong, and the probe defects that produced confident wrong answers. Read before writing a
  performance probe.
- **[reagent-architecture-tenets.md](reagent-architecture-tenets.md)** -- subscriptions,
  lifetime, and what to use instead of another cache.
- **[documentation-tenets.md](documentation-tenets.md)** -- record reversals; never silently
  overwrite superseded reasoning.

## Domain

- [custom-content-lifecycle.md](custom-content-lifecycle.md)
- [library-management-and-conflicts.md](library-management-and-conflicts.md)
- [keyword-trap-name-repair.md](keyword-trap-name-repair.md)
- [starting-equipment.md](starting-equipment.md)

## Browser probes

All under `test/browser/`, run against `lein e2e-server` (see `test/browser/README.md`).

| Probe | Answers |
| --- | --- |
| `tab_switch_freeze_e2e.js` | the freeze: longest task per Race<->Class switch, heap, counters, stacks |
| `class_body_cost_e2e.js` | class-body cost at open and per switch, retained heap |
| `builds_per_interaction_e2e.js` | `entity/build` calls per click |
| `freeze_cpu_profile_e2e.js` | CPU profile ranked by inclusive time |
| `storage_shape_e2e.js` | what is actually in localStorage after a real import |
| `localstorage_ceiling_e2e.js` | the real quota, and whether it counts chars or bytes |
| `library_chunk_granularity_e2e.js` | how finely a library can be split |
| `scripts/test/run-cljs-tests.js` | runs the ClojureScript suite headlessly (repo's canonical runner, not under test/browser) |
