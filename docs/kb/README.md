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

## UI

- **[equipment-option-picker.md](equipment-option-picker.md)** -- the Equipment tab's 1037
  `<option>` elements, five controls measured against each other, and why the winner is a
  filtering combobox on the native Popover API (1541 nodes vs 2558 native). Records two
  withdrawn claims -- "datalists cannot be styled", and a 12-row cap that made the list
  searchable but not browsable -- plus six build defects with their fixes.

## Practice

- **[memoize-antipattern-scan.md](memoize-antipattern-scan.md)** -- every `memoize` site
  scanned and traced. Four are dead code; `memoized-spell-option` is measurably 10x slower
  than no cache. Planned, not executed.
- **[fast-browser-probes.md](fast-browser-probes.md)** -- why a measurement loop takes 40
  minutes and how to make it 4. Where the time really goes, batching variants into one run,
  and the `.lein-env` race that makes the e2e server boot against the wrong database.
- **[verification-discipline.md](verification-discipline.md)** -- how this repo has been
  wrong, and the probe defects that produced confident wrong answers. Read before writing a
  performance probe.
- **[reagent-architecture-tenets.md](reagent-architecture-tenets.md)** -- subscriptions,
  lifetime, and what to use instead of another cache.
- **[documentation-tenets.md](documentation-tenets.md)** -- record reversals; never silently
  overwrite superseded reasoning.

## Domain

- **[character-image-routes.md](character-image-routes.md)** -- how a portrait reaches the
  sheet, measured against real hosts. CORS and hotlink blocking are separate rules catching
  different hosts, which is why the two-tier design works. Carries two withdrawn conclusions
  (an invented URL proves nothing against an S3-backed host; the ceiling that refused
  pictures was ours), what the browser cannot be made to do however the attempt is dressed
  up, and how to reach the real internet from a browser test here.
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
| `combobox_shots_e2e.js` | Equipment combobox: screenshots plus anchored-geometry, light-dismiss and pick assertions |
| `combobox_scroll_e2e.js` | Equipment combobox: is the list browsable, keyboard nav, and open cost under throttle |
| `select_option_census_e2e.js` | how many `<option>`s every `<select>` in the app carries -- which pickers are actually big |
| `character_image_capture_e2e.js` | both routes a portrait can take into a PDF, and the shape of the field's notices |
| `scripts/test/run-cljs-tests.js` | runs the ClojureScript suite headlessly (repo's canonical runner, not under test/browser) |
