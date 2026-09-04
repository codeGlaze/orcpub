# Homebrew volume and the core builder loop

**Branch:** `perf/homebrew-builder-loop`, cut from `origin/integration` at `0b4d499a`.
**Status: measurement in progress. Nothing changed in `src/` yet.**

The report: with a lot of homebrew loaded, selecting race / subrace / class / subclass
slows down and can freeze the browser, and the suspicion is the core loop reading far too
much — especially spells.

## Method

Real stack only: `lein e2e-server` (Pedestal + in-memory Datomic), `lein fig:build`, and a
headless Chromium driving the **real UI** — the `.orcbrew` goes in through the My Content
page's own `input[type=file]` (`views.cljs:8933`), races are picked by clicking the real
cards, class by the real `<select>`. No re-frame dispatching.

Because the dev build is `:optimizations :none`, **load-time numbers from it are not
usable** (see dead ends). Runtime numbers are usable but run slower than production would.

### The fixtures

`test/fixtures/test-pak.orcbrew` (750 KB) is the repo's thick laundered QA pack. Its real
content, read on the JVM:

| | races | subraces | classes | subclasses | spells | feats | backgrounds | invocations | selections |
|---|---|---|---|---|---|---|---|---|---|
| test-pak | 3 | 25 | 1 | 101 | 166 | 72 | 30 | 46 | 20 |

To reach "a lot of homebrew" it is cloned into larger packs by `dev-scratch/mkpaks.clj`:
each clone gets a distinct source name and distinct entry keys, so N clones really are N
times the content rather than N overwrites of the same keys. Every generated pack is read
back with `clojure.edn/read-string` and its content counted before use, and the counts are
re-counted **out of the running app's `app-db`** after the real import — a pack is not
trusted until the app says it loaded it.

| pack | size | subclasses | spells | subraces |
|---|---|---|---|---|
| pak-c1 | 750 KB | 101 | 166 | 25 |
| pak-c2 | 1.5 MB | 202 | 332 | 50 |
| pak-c4 | 3.0 MB | 404 | 664 | 100 |
| pak-c8 | 6.0 MB | 808 | 1328 | 200 |
| pak-c16 | 12 MB | 1616 | 2656 | 400 |

All import with the log reading "No issues found / Import completed cleanly" — so these
measure the loaded path, not a quarantine path.

## What is established so far

### Import time scales with pack size

Real upload through the file input, timed to the point the sources appear in the library:

| pack | import |
|---|---|
| 750 KB | 1303 ms |
| 1.5 MB | 2135 ms |
| 3.0 MB | 2844 ms |

Roughly linear, and it is a foreground freeze. Related: plugins are stored in
localStorage as EDN text and `reader/read-string`-parsed on load (`db.cljs:319`), which
showed 229 ms of `cljs.tools.reader.edn` work for the 750 KB pack. That parse runs once
per page load, from the `::e5/plugins` cofx on `:initialize-db` (`events.cljs:230`) — not
per click.

### The per-click character rebuild barely notices homebrew

`entity/build` runs **twice** per race click (a leading-edge build plus a trailing one
500 ms later — the debounce at `subs.cljs:325` watches both `char-sub` and `tmpl-sub`).
Click-triggered medians across packs came out flat and non-monotonic — 29.8 / 43.0 / 35.6 /
38.7 ms for clean / 750 KB / 1.5 MB / 3.0 MB — i.e. noise, not signal, so they are not a
result. What *is* solid from the same runs: the dependency graph `kahn-sort` receives stays
at **115 nodes at every pack size**. Homebrew does not grow it.

That matters, because in the CPU profile the rebuild is still dominated by `kahn-sort` /
`no-incoming` — the same O(V·(V+E)) topological sort found on `perf/entity-build`
(`docs/kb/perf-entity-build.md`). On integration, 6 real race clicks spent 531 ms in
`kahn_sort` of 681 ms in `entity.build`. **That fix is not on this branch** and is the
first thing to port; it is worth ~5x on the rebuild and is independent of homebrew.

### Template construction does NOT blow up with class/subclass count (JVM)

Building the builder template through the real fns the subs call — `opt5e/class-option`
then `t5e/template-selections` then `t5e/template`:

```
SRD only (12 classes):  class-option x12  9.51 ms   template-selections+template  0.24 ms
+ 4 homebrew classes:   options 0.22 ms   template 0.24 ms
+ 8                     options 1.27      template 0.73
+16                     options 1.61      template 0.76
+32                     options 3.18      template 0.72
+64                     options 6.89      template 0.72
8 classes x 0/4/8/16/32 subclasses:  template 0.70 / 0.71 / 0.69 / 0.20 / 0.23 ms
```

`template-selections` + `template` is sub-millisecond and **flat** in both class count and
subclass count. Only `class-option` construction grows, linearly, at roughly 0.1 ms per
homebrew class — a one-time cost per content change, not per click.

**Caveat, and it is a real one:** these synthetic homebrew classes carry no
`:spellcasting`, so `spellcaster-subclass-levels` (`spell_subs.cljs:445`) never fires. That
function eagerly constructs ~20 levels of spell selections per spellcasting subclass, and
it is the strongest structural candidate for "reads too much, especially spells". This JVM
sweep does not exercise it. Treat the flat curve as covering the non-spellcasting case
only, until the browser numbers on the real fixture come in.

### A structural candidate, not yet measured

`collect-modifiers-2` (`entity.cljc:541`) builds `make-template-option-map` over **every
option of every active selection**, then reads it at only `(count flat-options)` paths — on
a small character that was 360 map entries built to serve ~10 lookups. Option paths are
`(conj (vec (or ref path)) key)`, so a flat option's path already decomposes into
selection-path + key; indexing selections by their ref-or-path would make those lookups
direct instead of materialising the whole map. Whether this actually matters depends on
how much of the option map is homebrew, which the pending microbenchmark measures.

## Dead ends (recorded so they are not repeated)

**The 12.8-second cold builder load is a dev-build artifact, not a homebrew problem.**
It measured the same 12.8 s on a clean library, and the CPU profile showed 11.5 s of the
12.8 s *idle* — the `:optimizations :none` build fetching hundreds of separate files. No
load-time measurement from `lein fig:build` output means anything; a real answer needs
`lein fig:prod`.

**`*print-length*` is 50 under this project's lein profiles.** The first generation of
scaled packs was written with `pr-str` and came out silently truncated with `...` — invalid
EDN that hung the browser import for the full 10-minute timeout and cost a whole probe run.
Any EDN written from a `lein run` here must bind `*print-length*` and `*print-level*` to
`nil` first. This also broke the first attempt at validating the fixture round-trip, which
looked like corrupt content in the fixture and was not.

**String-slicing the fixture to clone it was wrong twice over:** a regex count of
`:orcpub.dnd.e5/<type>` found 2 content-bearing sources when there are 5, and the
hand-rolled brace matcher mis-split the top level. Reading the EDN properly on the JVM
found the real shape immediately. Do not parse this format with regexes.

## Still open

- The warmed microbenchmark sweep of the rebuild internals across pack sizes
  (`build`, `collect-modifiers-2`, `get-all-selections-aux-2`,
  `make-template-option-map`, `kahn-sort`) — click-triggered timing was too noisy.
- The render/rebuild split per click as homebrew grows. In an early profile
  `reagent` render (926 ms over 6 clicks) was *larger* than `entity.build` (681 ms), which
  points at the option cards being rendered for every homebrew race/subrace, not at the
  rebuild.
- Whether the freeze is reproducible at 6 MB / 12 MB, and where.
- The spells path specifically, with real spellcasting homebrew.
