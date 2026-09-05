# Homebrew volume and the core builder loop

**Branch:** `perf/homebrew-builder-loop`, cut from `origin/integration` at `0b4d499a`.
**Status: measured, and one fix ported in.**

Three answers, in order of how much they matter:

0. **Loaded-but-unlooked-at homebrew costs real, retained memory, and opening the builder
   is a multi-second freeze.** The real uploaded pack more than doubles the heap (30.9 ->
   67.6 MB) and turns a 217 ms block into 731 ms. Add spellcasters and it keeps going, to
   122.7 MB and a **2.15-second unbroken main-thread block**. See *Memory and freezes*.
1. **There is a hard ceiling on how much homebrew can exist at all.** Homebrew is persisted
   to `localStorage`, which browsers cap at ~5 MB per origin. A 2.9 MB library saves and
   reloads fine; a 5.9 MB one fails the write, and the content is gone on the next page
   load. The app handles this correctly and says so — this is a capacity limit, not a bug.
2. **The core loop was dominated by a quadratic topological sort, not by reading content.**
   `kahn-sort` was **76–86% of `entity/build` at every homebrew size**. Porting the
   `perf/entity-build` fix takes the rebuild from 27–32 ms to 5–7 ms and a real race click
   from 157–221 ms to 87–112 ms of CPU.
3. **Most of the spell work is detail panels nobody has opened.** 78% of building a spell
   option is `spell-help` — a hiccup tree of school/casting time/range/duration/components
   plus the whole description split into paragraphs — built eagerly for every spell. And
   `memoized-spell-option` is keyed on the **class name**, so each spell gets its own copy
   per class whose list contains it: at 130 classes that is 41,470 option objects holding
   2.39 million hiccup nodes. See *The detail panels* below.
4. **Spells ARE built into the builder when you are nowhere near the Spells tab, and that
   is the seconds-scale cost.** On the Race tab, before Spells has ever been opened, a
   library of 128 homebrew spellcasting classes constructs **4064 spell selections**, and
   ~1.5 s of the ~3.0 s of busy JS before the Race page is usable is spell machinery. It is
   linear in caster count (~13 ms per homebrew spellcaster) and is re-paid on **every** load
   of the builder. See *Spells on the Race tab* below.

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

Superseded by the measured tables above. The short version: homebrew volume moves
`entity/build` by ~20% across a 4x content increase, the dependency graph stays the same
size, and the rebuild was dominated by `kahn-sort` at every size. That fix has now been
ported here.

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

## The detail panels: state nobody asked for

`spell-option` (`options.cljc:439`) calls `(spell-help spell)` unconditionally. `spell-help`
(`options.cljc:406`) builds a hiccup tree — school, casting time, range, duration, a joined
components string — and then splits the spell's whole description on newlines into a `[:p]`
per paragraph, inside a `doall`. None of that is read until someone opens the spell.

Measured over the SRD list (319 spells), timed the way the code actually runs — a whole
list at once, warmed, min of 5:

```
spell-option  x319    6.14 ms     19.3 us per option
  spell-help  x319    4.82 ms     15.1 us of it        -> 78% is the detail panel
```

### The per-class multiplier

`memoized-spell-option` is memoized on `(spells-map, ability, class-name, key, ...)`. The
**class name is part of the cache key**, so the same spell is rebuilt — with its own full
detail panel — once for every class whose spell list contains it.

| classes | option objects | hiccup nodes in `:help` | build time |
|---|---|---|---|
| 1 | 319 | 18,404 | 6.4 ms |
| 8 | 2,552 | 147,232 | 50.8 ms |
| 32 | 10,208 | 588,928 | 208 ms |
| 64 | 20,416 | 1,177,856 | 451 ms |
| 130 | 41,470 | **2,392,520** | 860 ms |

130 classes is the size of the caster-duplicated variant of the uploaded pack. Clean linear
growth in both time and structure.

The description text is duplicated too, not shared: `spell-help` uses `s/split`, which
allocates fresh substrings per paragraph per class.

**What these numbers are and are not.** The node counts are real allocated structure. A
serialized `pr-str` size was also computed (50.6 MB at 130 classes) but is **not** a heap
measurement and is not quoted as one — it overstates what the JVM holds, and CLJS constants
differ again. The node count is the honest figure. Nothing here has been measured in the
browser yet; the JVM shows the shape, not the runtime cost in the app.

### Why this is the promising cut

The other candidates (lazy class levels, gating on character level) run into the fact that
the template is built once and shared across edits, so making it depend on the character
trades a one-time cost for a per-change one. This one has no such tension: `:help` is
display-only, read exclusively when a user opens a spell, and could be a thunk or derived at
render time without changing which selections exist or what any modifier computes. It is the
cheapest thing here to make lazy and the largest single share of the spell work.

Not attempted yet. It needs a characterization test first — `:help` is part of the option
map that the template walker traverses, so deferring it must not change option identity,
ordering, or anything `entity/build` reads.

## Memory and freezes, measured

Heap is `usedSize` after a forced GC via CDP, so it is retained, not transient. The freeze
column is the **longest single unbroken main-thread task** from `PerformanceObserver`
longtask entries — a total spread over many tasks is invisible to a user; one long task is
the thing that reads as a hang. Everything below happens without ever opening the Spells
tab.

| pack | caster classes | heap | vs clean | builder open | race | subrace | class | level |
|---|---|---|---|---|---|---|---|---|
| clean library | 1 | 30.9 MB | 1.00x | 217 ms | 54 | 67 | 87 | 68 |
| the real uploaded pack | 2 | **67.6 MB** | 2.19x | **731 ms** | 79 | 102 | 138 | 110 |
| + 8x casters | 18 | 74.5 MB | 2.41x | 901 ms | 101 | 74 | 145 | 112 |
| + 32x casters | 66 | 95.2 MB | 3.08x | 1374 ms | 84 | 83 | 262 | 120 |
| + 64x casters | 130 | **122.7 MB** | **3.97x** | **2150 ms** | 100 | 74 | **433** | 111 |

Marginal cost of one more homebrew spellcasting class, over 18 -> 130:

```
heap          0.43 MB      retained, after GC
builder open  11.15 ms     added to a single blocking task
class select   2.57 ms
```

### What this says

**Memory is real and retained.** Loading the pack alone costs +36.7 MB, and it is held for
the life of the page — `memoized-spell-option` is a `memoize` with no eviction, keyed on
class name, so every option object it ever builds stays live. At 130 caster classes the tab
holds ~4x what an empty library needs, for content the user has not looked at.

**Opening the builder is the freeze.** 217 ms clean is already a stutter; 731 ms with the
real pack and 2.15 s at 130 casters are hangs, in one unbroken task, with no yield for the
browser to paint. This is the template build described above — every class's every level,
every spell option, every detail panel — on the critical path before the Race tab can draw.

**Race and subrace are NOT where the freeze is.** They roughly double (54-67 -> 74-102 ms)
and then stay flat as casters are added: noticeable, not a hang. **Class selection is the
one that scales** — 87 -> 138 -> 262 -> 433 ms, a 5x growth driven purely by caster count.
So of "race / subrace / class", the reported symptom is concentrated in opening the builder
and in the class picker, not in race selection.

**Dev-build caveat, and why it does not rescue the numbers.** These come from the
`:optimizations :none` build, which inflates CPU roughly uniformly (it is a magnifier for
execution shape, and pure noise for the load waterfall, which is why the cold-load figure
was discarded). Even allowing a generous 3-5x production speedup, a 2.15 s block lands at
400-700 ms and a 433 ms class selection at 90-150 ms — still a hang and still a stutter.
Heap is not CPU and does not scale down that way: the retained structure is the same.

## Spells on the Race tab

**The report was right.** Every homebrew spellcasting class has its full spell machinery
constructed when the builder's template is built — which happens before the Race page can
render, on every page load, whether or not Spells is ever opened.

Fixture: `dev/spellcaster_pack.clj` clones the *real* spellcasting class out of
`test-pak.orcbrew` — a full `:spellcasting` map with `:level-factor`, `:known-mode`,
`:spells-known`, `:cantrips-known` and a 285-entry `:spell-list` across levels 0–9 — so the
shape is real homebrew, not something invented. Each clone's spell list points at that
pack's own custom spells. Packs are kept under 2.8 MB so they clear the storage ceiling.

Measured on the **first render of the Race page**, with the Spells tab never opened.
Instrumentation is installed before the page renders, because the template is built once and
cached — measure it late and you measure nothing.

| homebrew casters | busy JS | `class-option` | `spell-selection` | `spells-known-selections` | `make-levels` | `spellcaster-subclass-levels` |
|---|---|---|---|---|---|---|
| 1 | 1166 ms | 13x 49 ms | **127x** 10 ms | 13x 42 ms | 2x 4 ms | 2x 4 ms |
| 8 | 1436 ms | 20x 104 ms | **344x** 28 ms | 20x 94 ms | 16x 22 ms | 16x 19 ms |
| 32 | 1832 ms | 44x 183 ms | **1088x** 79 ms | 44x 166 ms | 64x 70 ms | 64x 64 ms |
| 64 | 2225 ms | 76x 291 ms | **2080x** 145 ms | 76x 261 ms | 128x 136 ms | 128x 128 ms |
| 128 | 2972 ms | 140x 540 ms | **4064x** 249 ms | 140x 495 ms | 256x 237 ms | 256x 218 ms |

`class-option` call counts are exactly 12 SRD + N homebrew, confirming every homebrew caster
is built. Growth is linear: **~13 ms of busy JS per homebrew spellcaster**, of which the
spell-shaped work (`class-option` + `spells-known-selections` + `make-levels` +
`spellcaster-subclass-levels` + `spell-selection`) is roughly half. Extrapolating the
measured slope, 500 casters is ~6 s and 1000 is ~13 s before the Race page is usable.

### It is all built up front, for levels the character will never reach

The character in these runs is **level 1, single class**. Selecting a class and then setting
level 5 — real dropdowns — produced **zero** further `spell-selection` or `class-option`
calls:

```
spell-128:  race Half-Orc     wall 38ms   classOpt -   spellSel -   tmplSel -   build 2x32ms
            tab Class/Level   wall 59ms   classOpt -   spellSel -   tmplSel -   build -
            class -> Wizard   wall 12ms   classOpt -   spellSel -   tmplSel -   build 2x75ms
            level -> 5        wall 12ms   classOpt -   spellSel -   tmplSel -   build 2x39ms
```

Zero calls when the level changes means the level-5 spell selections already existed while
the character was level 1. Every level of every homebrew caster is constructed eagerly, up
front, before the first tab is drawn.

### Where it happens

`::char5e/template-selections` (`equipment_subs.cljs:309`) takes `spells-map` and
`spell-lists` and builds the whole builder template. Its `::classes5e/classes` input comes
from `::classes5e/plugin-classes` (`spell_subs.cljs:619`), which calls `make-levels`
(`spell_subs.cljs:534`) for **every** plugin class, and `::classes5e/plugin-subclasses`
(`spell_subs.cljs:587`) does the same for every plugin subclass. `make-levels` reaches
`spellcaster-subclass-levels` (`spell_subs.cljs:445`) and `opt5e/class-option`
(`options.cljc:3013`) builds `spells-known-selections` (`options.cljc:681`) and
`spell-selection` (`options.cljc:513`) across the whole level range.

Nothing in that chain is gated on the character's level, on the class being selected, or on
the Spells tab being open.

### Correction: an earlier conclusion in this document was wrong

An earlier section here reported that "template construction does NOT blow up with
class/subclass count", from a JVM sweep that stayed sub-millisecond and flat out to 64
homebrew classes. That sweep's synthetic classes carried **no `:spellcasting`**, so none of
the machinery above ever ran. The caveat was recorded at the time, and it turned out to be
the whole finding: the non-spellcasting path really is flat, and the spellcasting path is
where the seconds are. The JVM numbers stand for what they measured and are kept below; they
do not describe a library with spellcasters in it.

This is the `verification-discipline.md` fixture lesson again — a green number proves nothing
if the fixture does not match real content. `test-pak.orcbrew` does contain one real
spellcasting class (`:sorcerer-divine-soul-`, 285 spell refs) and two spellcasting
subclasses; checking that first would have pointed here sooner.

### What would fix it

Not measured, so stated as candidates, not conclusions:

- **Build a class's levels lazily.** `make-levels` runs for every plugin class at template
  time. Only the classes the character has actually taken need levels, and only up to the
  level it has reached.
- **Gate on level.** Even for a selected class, spell selections above the character's
  current level are constructed and never read.
- **Do not re-pay it per page load.** The template is cached within a session but rebuilt
  from scratch on every navigation into the builder.

## The hard ceiling: homebrew that cannot be saved

Plugins are written to `localStorage` as EDN text (`db.cljs:265`). Browsers cap that at
roughly 5 MB per origin. Imported through the real file input, then navigated away from
and back:

| pack | after import (app-db) | after navigating to the builder | localStorage |
|---|---|---|---|
| 0.7 MB | 6 sources, 166 spells | 166 spells | 750 KB |
| 1.5 MB | 11 sources, 332 spells | 332 spells | 1501 KB |
| 2.9 MB | 21 sources, 664 spells | 664 spells | 3001 KB |
| 5.9 MB | 41 sources, 1328 spells | **0 spells, 0 sources** | **0 KB** |

At 5.9 MB the write throws, and everything the user just imported is gone on the next page
load. **This is handled, not silent** — an earlier draft of this document said "silently
lost" before checking, which was wrong. `set-item` returns false (`db.cljs:176`),
`plugins->local-store` dispatches `::e5/plugins-save-failed`, and the user sees:

> Couldn't save to browser storage — it may be full. Your latest change is in memory but
> will be lost on refresh. Download a full backup now

The import log still reads "Import completed cleanly" alongside it, because that log is
about parse problems, not storage.

The cliff is somewhere between 2.9 MB and 5.9 MB and was not bisected. What matters is that
it exists and is a browser limit, so no amount of optimisation moves it — only storing the
library somewhere other than `localStorage` would.

**This voids the 5.9 MB rows in the tables below.** Those runs measured a builder with no
homebrew loaded at all, which is why their numbers dropped back to the clean-library values
and their option-map entry counts returned to exactly the clean figure. Reading that as
"flat at 5.9 MB" would have been a false result; the tell was a count that went back to its
baseline instead of continuing to grow.

## Where a character change actually spends its time

Warmed microbenchmarks (min of 5 reps x 20 iterations) on the **live** character and
template of a real app that really imported the pack, after really clicking a race card.
Click-triggered timings were tried first and discarded: they came out non-monotonic across
pack sizes (29.8 / 43.0 / 35.6 / 38.7 ms), because the debounce, rendering and GC swamp the
signal.

**Before the port — integration as it stands:**

| pack | `entity/build` | collect-mods | get-all-sels | option-map | **kahn-sort** | apply-mods | option-map entries |
|---|---|---|---|---|---|---|---|
| clean | 27.11 | 3.63 | 1.61 | 1.78 | **20.72** | 0.09 | 1110 |
| 0.7 MB | 29.90 | 4.28 | 1.81 | 2.23 | **23.19** | 0.10 | 1216 |
| 1.5 MB | 31.60 | 4.60 | 1.82 | 2.44 | **27.76** | 0.11 | 1292 |
| 2.9 MB | 32.41 | 5.13 | 2.27 | 2.66 | **25.04** | 0.09 | 1444 |

`kahn-sort` is 76–86% of the rebuild at every size. Homebrew volume moves `entity/build` by
only ~20% across a 4x content increase — and the dependency graph it sorts stays at **114
nodes at every pack size**. Homebrew does not grow the graph; it is simply that the graph
was always expensive to sort.

**After porting the `kahn-sort` fix (`docs/kb/perf-entity-build.md`):**

| pack | `entity/build` | collect-mods | get-all-sels | option-map | kahn-sort | option-map entries |
|---|---|---|---|---|---|---|
| clean | **5.07** | 3.24 | 1.29 | 1.74 | 0.93 | 1110 |
| 0.7 MB | **6.44** | 4.28 | 1.64 | 2.34 | 1.13 | 1216 |
| 1.5 MB | **6.87** | 5.03 | 1.94 | 2.49 | 1.04 | 1292 |
| 2.9 MB | **6.95** | 5.10 | 2.26 | 2.73 | 1.01 | 1444 |

4.6–5.3x on the rebuild, at every homebrew size. And the picture inverts: `collect-modifiers-2`
is now 63–73% of the rebuild, and it is the piece that grows with content — 3.24 to 5.10 ms
from clean to 2.9 MB. **The quadratic sort was masking the over-reading.**

### Per real race click (CPU profile, 6 clicks)

| pack | busy/click | | `entity/build` | kahn | collect | React reconcile+commit | cards rendered |
|---|---|---|---|---|---|---|---|
| clean | 157 -> **87** | ms | 90 -> 25 | 72 -> 5 | 15 -> 16 | 25 -> 24 | 24 |
| 0.7 MB | 191 -> **105** | ms | 105 -> 30 | 80 -> 6 | 21 -> 20 | 29 -> 27 | 27 |
| 2.9 MB | 221 -> **112** | ms | 121 -> 36 | 90 -> 7 | 27 -> 26 | 41 -> 33 | 36 |

Each click still costs two builds — the debounce at `subs.cljs:325` watches both `char-sub`
and `tmpl-sub`, so one watch fires the leading edge and the other schedules a trailing build
500 ms later.

**A trap in reading these:** `entity/build` runs *inside* a reagent reaction, so its samples
sit under `reagent$` frames. The "render" bucket therefore **contains** the build bucket and
the two must not be added or compared as rivals. An earlier note here claimed render (926 ms)
was larger than build (681 ms) and therefore the bigger problem; that comparison was
meaningless. The independent render cost is the React reconcile/commit column, which is
24–33 ms per click and grows with the number of option cards.

## Under real use: clicking around quickly

Every measurement above waited ~1.5s between clicks, which lets the 500ms build debounce
settle — the friendliest possible pacing, and not how anyone builds a character.
`builder_churn_e2e.js` drives race / subrace / class / level changes 120-260ms apart for
~125 interactions and reports what a user would feel. All with `:help` already deferred.

| library | long tasks | worst | total blocked | blocked share | click->paint p90 | heap over the session |
|---|---|---|---|---|---|---|
| clean | 8 | 86 ms | 0.5 s | 2% | 43 ms | 30.9 -> 37.5 MB |
| the real pack | 17 | 103 ms | 1.0 s | 3% | 40 ms | 67.5 -> 70.5 MB |
| + 64x casters | **50** | **300 ms** | **3.4 s** | **11%** | 58 ms | **122.7 -> 161.9 MB** |

At 130 caster classes, **11% of a 32-second session is spent unable to paint**, across 50
separate long tasks — that is the "chug": not one hang, a constant stutter.

### On a machine that resembles a user's

The container is an idle 4-core 2.1 GHz Xeon with 15 GB free — nobody builds a character on
one. **Free RAM is not the lever**: the chug is CPU-bound (long tasks), so more memory does
not worsen it. CPU throttling does, and it is what a real device looks like. Chrome DevTools
calls 4x "mid-tier mobile"; 2x is roughly a modest laptop with other tabs open. Same churn,
same pack, `Emulation.setCPUThrottlingRate`:

| | long tasks | worst | total blocked | blocked share | click->paint median / p90 |
|---|---|---|---|---|---|
| 130 casters, 1x | 50 | 300 ms | 3.4 s | 11% | 24 / 58 ms |
| 130 casters, 2x | 130 | 599 ms | 13.1 s | 34% | 90 / 132 ms |
| **130 casters, 4x** | **138** | **1301 ms** | **31.9 s** | **54%** | **138 / 285 ms** |
| clean library, 4x (control) | 136 | 331 ms | 15.9 s | 35% | 59 / 166 ms |

At 4x, **54% of a 59-second session is spent unable to paint**, 135 of the 138 long tasks
are over 100 ms, 78 are over 200 ms, and the worst single block is **1.3 seconds**. Median
click-to-paint is 138 ms — every interaction visibly lags.

The control matters: a clean library at the same throttle also degrades (35% blocked), so
some of this is the app's baseline cost on a slow device. But content **doubles the blocked
time** (15.9 s -> 31.9 s), **quadruples the worst block** (331 ms -> 1301 ms), and adds 40 MB
of retained growth against the clean run's 12 MB. The content-driven share is the part this
investigation can fix.

Throttling amplifies CPU-bound work only. Heap growth is unchanged across 1x/2x/4x
(+39.2 / +39.0 / +40.0 MB) — the accumulation is speed-independent, exactly as expected for a
cache that never evicts.

### It is class browsing that accumulates, and it is unbounded

Splitting the churn by interaction kind, same pack, same pacing:

| churn | worst task | heap over the session |
|---|---|---|
| races/subraces only | 115 ms | 122.7 -> 121.3 MB (**flat**) |
| classes/levels only | **528 ms** | 122.6 -> **156.8 MB (+34.2 MB)** |

Race browsing costs nothing that is kept. **Browsing classes adds ~34 MB in half a minute
and never gives it back** — both measurements are taken after a forced GC, so this is
retained, not garbage waiting to be collected. Selecting a class realises that class's spell
options, and `memoized-spell-option` (`options.cljc:469`) is a `memoize` with no eviction, so
every class you look at is retained for the life of the page. Keep browsing and it keeps
growing.

A single class change also produces the worst block measured anywhere in this
investigation: **528 ms**.

This is the sharpest statement of the reported problem: it is not opening the builder once,
it is that **using** the builder gets progressively heavier the more you look at.

## What a bounded cache would cost, and why it is the wrong shape

`memoized-spell-option` (`options.cljc:469`) is `(memoize spell-option)` — a plain map that
never evicts. The obvious fix is a size cap, so the first question is what a cache MISS
costs. Visiting 8 classes cold, then revisiting the same 8 warm, 130 casters, 4x throttle:

```
class        cold (miss)   warm (hit)   miss cost
Wizard          1589ms        202ms      1387ms   <- first-ever switch, one-time work
Cleric           372ms        245ms       127ms
Druid            272ms        175ms        97ms
Bard             228ms        190ms        38ms
Sorcerer         256ms        176ms        80ms
Warlock          226ms        203ms        23ms
Paladin          173ms        183ms       -10ms
Ranger           272ms        204ms        68ms

excluding Wizard:  cold ~257ms   warm ~197ms   MISS COST ~60ms
heap 123.5 -> 166.3 MB after visiting 8 classes twice (~2.7 MB per class visited)
```

**A miss costs ~60 ms at 4x throttle** (~15 ms unthrottled) — small next to the ~197 ms a
class switch already costs warm. So capping the cache is cheap in UX terms.

**But the cache is not the dominant cost.** Warm switches still block ~197 ms with everything
cached; the cache is ~60 of ~257 ms, about 23%. Bounding it fixes the *growth*, not the chug.
Those are separate problems and this measurement is what separated them.

### This is not Reagent failing to manage memory

Reagent and re-frame **do** manage lifetime — for things expressed in their terms. Verified
in `re-frame 1.4.4`: `subs.cljc` `cache-and-return` registers `add-on-dispose!` on every
cached subscription, and `clear-subscription-cache!` disposes each one. A subscription that
nothing subscribes to is reclaimed.

`clojure.core/memoize` opts out of all of that. It is a top-level `def` holding a map that
grows forever; no framework can see inside it or reclaim it, because the namespace holds a
strong reference. The leak is not a gap in Reagent — it is code stepping outside the thing
that does the managing.

### So do not hand-roll an LRU

`clojure.core.cache` ships **`.clj` only** (checked the 1.1.234 jar: `cache.clj` and
`wrapped.clj`, no `.cljc`), so it cannot be used from shared `.cljc` — which is what made a
hand-rolled LRU look necessary. It is not. The framework-native fix is to derive a class's
spell options in a **subscription keyed by class** and let re-frame dispose it when nothing
is subscribed. No eviction policy to invent, no cache size to tune, and it composes with the
granular-subscription tenet instead of fighting it.

That is more work than a size cap, and it belongs with step 3 rather than ahead of it — step
3 restructures the same code path.

## PHASE 0 RESULT: the builder-open block is EDN parsing, not template construction

Spike B — derive class and subclass bodies only for classes the character has taken — was
built as a throwaway and measured on `mega-64`. It moved two things and not the one it was
aimed at:

| | before | spike B |
|---|---|---|
| heap after load | 122.7 MB | **95.3 MB** (-27.4) |
| class switch | 391 ms | **237 ms** |
| **builder open** | 1730 ms | **1583 ms (-8%)** |

Gating the suspected cause **entirely off** — no `make-levels` and no `class-option` for 130
classes and 214 subclasses — removed 8% of the block. That is the cleanest possible
disproof: remove the cause, the symptom stays.

Profiling the cold load with the spike in place says where it actually goes (busy 3842 ms):

```
  1516 ms  edn reader          <- cljs.tools.reader parsing plugins out of localStorage
   670 ms  reagent render
   212 ms  entity.build
    98 ms  class_option
     4 ms  template_selections
```

Plus 464 ms of GC and ~336 ms of `cljs.core/_EQ_` / `IEquiv`. **The single biggest item is
re-parsing the whole homebrew library from localStorage on every page load.**

### The confound I did not control for

Builder-open looked like it scaled with caster count. It scales with **pack size**, and my
packs got bigger as I added casters:

```
pack        size(MB)  casters  builder-open
mega-raw       2.26        2       728ms
mega-8         2.30       18       869ms      size x1.02, casters x9.0  -> time x1.19
mega-32        3.00       66      1374ms      size x1.30, casters x3.7  -> time x1.58
mega-64        3.90      130      1730ms      size x1.30, casters x2.0  -> time x1.26
```

Nine times the casters at the same size costs 19% more. A 30% bigger pack costs 26-58% more.
**Time tracks bytes, not casters.** Every earlier statement in this document attributing
builder-open to caster count is wrong on that point; the numbers themselves stand, the
attribution does not. `db.cljs:319` `reader/read-string` on the `::e5/plugins` cofx was
identified in the first pass and dismissed as "once per page load, not per click" — true, and
it is 1.5 s of that load.

### What this does to the plan

- **Step 3 as written is not the fix for builder-open.** It is still worth doing for what it
  did deliver — 27 MB of heap and a halved class switch — but it is no longer the headline.
- **The new headline is the EDN re-parse.** Candidate directions, none measured yet: store
  the library as transit rather than EDN (materially faster to read), parse once into
  IndexedDB and hydrate from there, or parse lazily per source instead of the whole library
  at `:initialize-db`.
- **`reagent render` at 670 ms is now second**, which promotes virtualisation from "step 5,
  unmeasured" to a real candidate.

Phase 0 did its job: it was built to compare two designs and instead invalidated the premise
of both. Cheaper than shipping either.

## Decision point: what to do after Phase 0 (analysis, pending owner decision)

Reading the load path end to end (`db.cljs:460-506`) after the Phase 0 profile:

1. The library is parsed **once** — no double-parse to remove. The ~1.5 s is one
   `read-string` of the whole library.
2. Then **every item is spec-validated on every page load** via
   `salvage-library-items content-specs/valid-item-for-load?`. The code's own comment says
   `stored` "normally holds only valid items, so `rejected` is usually empty here — it's the
   defensive net if the floor tightens." So a full walk of ~1,350+ items runs each load to
   catch a case that essentially never occurs. Unmeasured, but the ~336 ms of `=`/`IEquiv`
   in the profile is the right shape for it.
3. Then `reagent render`, 670 ms, for the first paint.

**There are two problems, not one.** The owner's report was the *interaction* chug —
race/class selection. Phase 0 exposed a separate *load-time* block with a different
mechanism. They need different fixes and should not be conflated in one plan.

### Why not the transit spike next

- It addresses only the parse (~1.5 s) — not the per-load validation, not the render.
- It is a **localStorage format migration for every existing user**. Real risk, and it
  does not lift the ~5 MB ceiling already hit in this investigation.
- Transit-in-localStorage is a half-step: still synchronous, still on the critical path,
  still capped. If the storage format is going to migrate at all, it should migrate once, to
  the right tier — **IndexedDB** (async, no ceiling, lazy per-source loads) — after the
  cheaper things below have been measured, when it is known how much block remains.

### Recommended order

1. **Quantify the validation** (~15 min). Time `salvage-library-items` alone on the
   `mega-64` library in the browser. If it is hundreds of ms: validate on import, stamp the
   stored library with a schema version, and skip the walk on load unless the version
   changed. Zero migration, and it removes work that finds nothing.
2. **Chunk the parse** (spike, ~1-2 h). Parse per source with a yield between sources.
   Total is unchanged; the *longest task* — the thing users feel — drops from one 1.5 s block
   to many small ones, and the page paints between them. No format change.
3. **Finish the interaction-loop decision.** Spike B got class switch 391 -> 237 ms and heap
   -27 MB, but 237 misses the < 150 target; design A (no template rebuild on class change) is
   the candidate for the rest. This is the owner's original complaint and is independent of
   the load-time track — it should not wait behind it.
4. **Measure render.** 670 ms first paint is now the second-largest item on *both* tracks.
   Virtualisation moves from "unmeasured step 5" to something that needs a number.
5. **Park the storage migration as one decision**, IndexedDB not transit, scoped after 1-4.

**Measured (superseding item 1 above):** on the `mega-64` library in the browser, 3.87 MB,
14 sources, 1,576 items —

```
read-string        723 ms   parse
salvage/validate    10 ms   7 us per item
```

**Validation is 10 ms. Recommendation item 1 is withdrawn** — it would have removed nothing.

**Double-parse hypothesis: also dead.** Three cold-load profiles were taken; the middle one
(reader time 0, busy 1091 ms) was self-inflicted — its `addInitScript` wrapper replaced
`read_string` with an anonymous JS function, so the profiler re-attributed the parse to
`(anonymous)` and the run is not comparable. Discarded. The clean re-run on the reverted
build shows reader **812 ms**, within noise of the isolated 723 ms parse: **one parse per
load, not two**. The earlier 1516 ms attribution was profiler/GC inflation, not a second
parse.

### FINAL SPLIT of the cold builder-open block (mega-64, 3.87 MB, dev build)

```
~700-800 ms   EDN parse of the library (isolated: 723 ms; profiled: 812 ms)
~700-1000 ms  first render (reagent 670-973 ms across runs)
 ~125-212 ms  entity.build
   ~10 ms     per-load validation
    tiny      template-selections / spell machinery
+ ~500 ms     GC interleaved through all of it
```

**Two co-equal heads, not one**: the parse and the first paint. Everything this
investigation started from (spell machinery, class levels) is the small tail of this block —
those costs live in the interaction loop instead (class switch, churn heap), where spike B
already proved the fix works.

### The go-forward plan, final

| track | change | evidence | expected |
|---|---|---|---|
| 1. load | parse per source with yields (no format change), or move the library to IndexedDB with lazy per-source hydration (one migration, kills the 5 MB ceiling too) | 723 ms measured | longest task -700 ms |
| 2. load | split/virtualise the first paint | 670-973 ms measured | up to -900 ms |
| 3. interaction | design A (lazy class bodies, no template rebuild) | spike B: -27 MB heap, class 391->237 ms | class switch <150 ms, churn heap ~flat |
| 4. interaction | debounce double-fire | 2 builds/change measured | halves per-change cost |

1+2 fix "opening the builder freezes"; 3+4 fix "using it chugs and bloats". Independent
tracks, either can ship first. Characterization gates as per Phase 1 above apply to each.


## The fix plan (revised — supersedes the original below)

Everything before this point is measurement. This is the delivery plan, rewritten after
step 1 and the churn/throttle runs changed what we know. Exit criteria first, so "done" is
not a matter of opinion.

### Done means

Against `mega-64` (130 caster classes) at 4x CPU throttle, using the committed probes:

| | now | target |
|---|---|---|
| builder open, longest single task | 1730 ms | **< 400 ms** |
| class switch, longest single task | 391 ms (cold 257 / warm 197 at 4x) | **< 150 ms** |
| blocked share over a 125-interaction churn | 54% | **< 25%** |
| heap growth over that churn | +40 MB | **< 5 MB** |
| `lein test` | 0 failures | 0 failures |

Race/subrace are already flat and are not targets.

### Phase 0 — pick the shape by spiking it, not by arguing about it

Two candidate designs for "only build what the character has taken", and they trade
differently:

- **A. Lazy option bodies.** Each class option keeps its name/key but its `:selections` and
  `:modifiers` become thunks, forced when the option is actually walked. `entity/build`
  already only walks *selected* options (`get-all-selections-aux-2` filters on
  `selected-option-paths`), so unselected classes would never force. Small blast radius;
  same trap as `:help` — something forcing during picker render silently undoes it.
- **B. Character-driven derivation.** `template-selections` takes the character's selected
  class keys and builds full options only for those. Structurally cleaner and gets re-frame
  disposal for free, but the template then rebuilds when class selection changes — trading a
  one-time cost for a per-change one.

**Do not pick on reasoning.** Spike both as throwaway branches, measure builder-open and
class-switch on `mega-64` at 4x, and keep the winner. B's per-change cost is the open
question and it is cheap to answer: building 1 class's levels versus 130.

Budget this at one measured comparison, not a debate.

### Phase 1 — the characterization net, before any structural change

This is a bigger blast radius than step 1, so the net has to be wider first:

- Pin the template's shape for a matrix of characters (SRD-only, homebrew race, homebrew
  caster, multiclass): option counts per selection, option keys **in order**, and the
  built-character values (`entity/build` output) for each.
- Pin that unselected classes contribute no modifiers — the invariant laziness relies on.
- Extend `spell_help_laziness_e2e.js`'s trick to the new thunks: count constructions during
  picker render, expect 0. **Run it against the pre-change build too**, so it is proven to
  catch the regression rather than merely passing.

Nothing lands until this is green on unchanged code.

### Phase 2 — implement the winner

Plus the two things it subsumes: `memoized-spell-option`'s unbounded growth (options for
unvisited classes stop existing, so there is nothing to evict), and most of the class-switch
cost.

### Phase 3 — measure, with the probes already committed

`builder_churn_e2e.js` (1x and 4x, `races`/`classes`/`both`), the memory/freeze probe, and
`lein test`. Compare against the table above. Record the result **including if it misses** —
step 1 is the precedent.

### Phase 4 — follow-ons, only if Phase 3 leaves them mattering

- **Debounce double-fire** (`subs.cljs:325`): every change costs two builds. Halves the
  per-change cost, but it is a semantics change to a load-bearing debounce — own commit, own
  test.
- **Virtualise the option lists**: 200 subraces is 200 cards. Unmeasured; get numbers before
  committing to it.

### Explicitly not doing

- Wiring the memoized `entity.cljc` wrappers — removed on purpose twice, once inside a bug
  fix, and the cost they hid is gone.
- Hand-rolling an LRU — see *What a bounded cache would cost*; Phase 2 removes the need.
- Removing the 500 ms debounce.
- Touching the ~45 handler memoizations. They are correct (`reagent-architecture-tenets.md`).

## What is left, in priority order

1. **`collect-modifiers-2` builds far more than it reads.** `make-template-option-map`
   (`entity.cljc:561`) materialises an entry for **every option of every active selection**
   — 1110 entries on a clean library, 1444 at 2.9 MB — and `collect-modifiers-2` then reads
   it at only `(count flat-options)` paths, which is ~32 here. Option paths are
   `(conj (vec (or ref path)) key)`, so a flat option's path already decomposes into
   selection-path + key: indexing selections by their ref-or-path would make those lookups
   direct and skip building the map. Worth ~2–3 ms of a 5–7 ms rebuild, doubled per click.
   This is the closest thing found to the original "reading way too much" suspicion.
2. **The double build per change.** Identified, not fixed — halving it is worth as much as
   any of the above, but it changes the subscription's semantics and needs its own test.
3. **Import cost**, which is linear and a foreground freeze: 1303 / 2135 / 2844 / 5276 ms
   for 0.75 / 1.5 / 3.0 / 5.9 MB.
4. ~~Spells specifically~~ — **now measured, and it is the big one.** See *Spells on the
   Race tab* above. This outranks items 1–3 by an order of magnitude: milliseconds of
   rebuild versus seconds of template construction.

## The builder ran TWO character builds per click, and the first three diagnoses were wrong

The reported symptom: every interaction ran `entity/build` twice. Confirmed in the real app
before any fix (`test/browser/builds_per_interaction_e2e.js`, MegaPak loaded, dev build):
**a single race click = 2 builds.**

Four diagnoses were attempted. The first three came from reading code; all three were wrong,
and each was killed by a measurement:

| Diagnosis | How it died |
| --- | --- |
| Watch fan-in: `debounced-build-sub` adds the same watch to both inputs, so one change fires it twice | The coalescing fix for it left the count at **2**. |
| The first build pairs a new character with a STALE template | True in the synthetic harness; impossible in the app, where `built-template` returns the same object every time (its body is commented out and it ignores `selected-plugin-options`). |
| The subscription is disposed and re-created, and its constructor builds outside the debounce | Constructions per click: **0**. (That test was also weak — instrumentation was installed after builder load, so it could not see instances that already existed.) |
| A direct `subs/built-character` call in `views/character-page` | Different route; not mounted in the builder. |

The stack at each build said both went through `settled` -> `do_build`, i.e. both were inside
a debounced sub. Two builds 13 ms apart cannot both take the debounce's leading edge unless
there are **two instances**, each with its own `last-run` starting at 0. Dumping re-frame's
subscription cache in the running app:

```
built-character cache entries:
   [{:re-frame/query-v [:built-character nil] ...}]
   [{:re-frame/query-v [:built-character]     ...}]

:character reaction watches: [build-3 ... build-2 ...]
```

Two cache entries differing only by a trailing `nil`, and two `build-*` gensym watches on the
`:character` reaction — one per instance.

**Cause:** `views/summary-details` subscribed `[:built-character id]`. The builder renders it
with `id` nil, so the query vector is `[:built-character nil]` — a different cache key from
the builder's own `[:built-character]`, hence a second `debounced-build-sub` over the same
character. `reg-sub-raw` ignores its query args here, so the two instances build *identical*
results.

**Fix:** route by id like every sibling in that `let`
(`(if id [::char/built-character id] [:built-character])`). Measured after:

```
race Half-Orc:   2 builds -> 1 build
```

Across every interaction type, after the fix (the probe previously drove classes by clicking
a card, which always timed out — class and level are `<select>`s, so it measured races only):

```
race Half-Orc  1     class Wizard  1     class Cleric  1     class Druid  1     level 5  1
```

**So class-switch cost is not redundant builds.** It is one build plus render being slow,
which matches the earlier warm/cold split (~197 ms warm with everything cached, the
unbounded cache accounting for only ~60 of ~257 ms). Anything aimed at class-switch time has
to attack that single build and its render, not duplication.

**Why there were two, and why one was nil:** `character_builder.cljs:1913` and `:1928` render
`[views5e/character-display nil true 1]` with an explicit nil, which reaches
`summary-details` and becomes `[:built-character nil]`. The builder's own code subscribes
`[:built-character]` with no argument. Same data, two query vectors. An argument-passing
inconsistency, nothing deeper.

### CORRECTION: the first version of this fix overreached

It also routed **non-nil** ids to `[::char/built-character id]`, described here as fixing a
latent bug — `[:built-character id]` ignores `id` and returns the builder's character, which
looks wrong on a character page. Two problems with that:

1. Nobody has reported seeing the wrong character, which is evidence the reading is wrong or
   the path rarely renders (`character-display` defaults to the "combat" tab in two-column
   layouts, so the summary tab may seldom mount).
2. `::char5e/character` is a `reg-sub-raw` that **fires an HTTP GET and dispatches
   `:set-loading`** for a non-nil id. `character-display` is rendered with real ids in the
   character page, a character list and two party views, so that change could add a fetch per
   rendered character — a path never measured.

The fix is now narrowed to the nil case only, which is the one that was measured. The non-nil
behaviour is left exactly as it shipped. Whether it is genuinely wrong is an **open
question**, and answering it means checking whether each call site already subscribes
`[::char/character id]` (re-frame would then reuse the cached reaction and add no fetch) —
not assuming it.

### The lesson, which cost most of the debugging time

A characterization test in a synthetic harness (two live reactions over a shared source)
reproduced a double build and passed once "fixed" — while the app kept building twice for an
entirely different reason. The harness modelled a mechanism the app does not have. Both were
needed: the CLJS test to pin the coalescing behaviour, and a probe against the **real app**
to find what was actually happening. When the two disagree, the app is right.

The coalescing fix and its identity guard were kept: they are correct, they are pinned by
`built_character_debounce_test.cljs`, and they prevent a redundant rebuild when a
notification carries no change. But **they were not the fix for the reported symptom**, and
the commit that introduced them claimed more than it delivered.

## CORRECTION: class browsing is not an unbounded leak, and class switches are not slow

Two claims in this document are withdrawn. Both were the basis for the "lazy class bodies"
work; measured with `test/browser/class_body_cost_e2e.js` on `mega-64` (130 casters) and on
the owner's real MegaPak, with the duplicate-subscription fix in place.

**Withdrawn 1: "class switch 391 ms / 528 ms worst".** Class switches cost ~10 ms wall and
run *zero* `class-option`, `make-levels` and `spell-selection` calls on both fixtures. Class
bodies are built once at builder open and never rebuilt on a switch. Making them lazy
therefore cannot speed up switching.

**Withdrawn 2: "adds ~34 MB in half a minute and never gives it back — keep browsing and it
keeps growing".** The growth converges:

```
PASS 1 (first view of each class)    +38.5 MB    memoized-spell-option: 75,12,22,40,61 calls
PASS 2 (same classes again)          + 0.5 MB    memoized-spell-option: 0 calls
```

It is per-class realisation of lazy spell-option seqs, retained by the long-lived template —
paid once per class looked at, bounded by library size, not by session length. The ceiling is
"the whole library realised", which is what an eager template would cost up front anyway. The
laziness is *saving* memory for anyone who does not browse everything.

**And it is not the memoize.** A/B in the same session, `memoized-spell-option` replaced with
a passthrough: heap grew **41.4 MB without memoization vs 38.5 MB with it**. So the
`clojure.core/memoize` analysis earlier in this document — the miss-cost table, the bounded-cache
discussion, the "derive it in a subscription keyed by class" recommendation — was aimed at
something that is not the cause. Those sections stand as reasoning about the memoize itself,
but they do not describe this symptom.

### What class-body work actually costs

Only builder open, and only on class-heavy libraries:

```
                    class-option    make-levels    spell-selection
mega-64 (130 casters)  142x397ms      340x158ms       4045x161ms     ~716 ms total
MegaPak (real)          14x 32ms       84x 36ms        333x 31ms      ~99 ms total
```

So lazy class bodies is a **builder-open** optimisation worth ~0.7 s on a synthetic
130-caster library and ~0.1 s on the owner's real one — against a ~12 s open dominated by the
EDN parse (~750 ms) and first render (~670 ms) plus dev-build overhead. That is the honest
value; it is not a fix for either the freeze or the memory.

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

**A real import parks on the conflict-resolution modal, and a probe that only polls app-db
reads that as failure.** A pack whose keys overlap existing content makes the app log
`Import "<name>": imported 1352, skipped 0 | changes: 11 | conflicts: 20` and then open a
modal — *"20 key conflicts, resolved safely... 13 duplicate keys renamed"* — with
`Cancel Import / Review / change / Import`. Nothing commits until a button is clicked, so
the plugin count in `app-db` never moves and a polling probe waits forever. **Three long
runs were lost to this**, and one of them produced a wrong claim to the repo owner that
their pack did not import; it imports fine, because a person clicks the button.

`CLAUDE.md` warns about precisely this — "a static-file server + `dispatch_sync` can't
surface an import-conflict modal, and it misled a previous pass into a false 'modal is
unmounted' conclusion". It had already burned someone once. What finally exposed it was
logging **all** console output instead of filtering to `pageerror`: the app had been
printing what it was doing the whole time, and the listener was narrowed to the wrong level.

Two follow-on traps in fixing it: a *fixed* sleep before clicking is not enough (a larger
pack parses slower, so the click finds no button and the run still fails), and the generated
`pak-c*` packs suffixed every entry key so they had zero conflicts and imported silently —
which is exactly why the problem looked specific to the owner's file rather than to the
probe. `test/browser/lib/orcbrew-import.js` races both outcomes until one lands.

That helper then failed *again* on packs a standalone diagnostic had imported minutes
earlier, reporting only `plugins=1, modal clicked=false`. Several more runs went into
reasoning about why the click had not landed — precedence, visibility checks, context setup
— each guess costing a full run and none of them right. The lesson is the one that cracked
the modal in the first place and was not carried forward: **when a harness fails, make it
report what the page actually showed, rather than theorising about the harness**. The helper
now dumps every button with its text and visibility, plus any conflict or error text, on
give-up.

Which then showed the button was visible, enabled and stable the whole time — so
"is it visible?" was the wrong question. The right one, **"can it receive a click?"**, was
already being answered in Playwright's own call log, which the helper was truncating to
three lines:

```
<div id="poper" class="window banner bottom"> from <div id="cookie-policy-popup">
  subtree intercepts pointer events
```

The **cookie-consent banner** is fixed to the bottom of the page and sits over the conflict
modal's buttons. A forced click does not help either — the banner still receives the event.

Fixed at the source rather than worked around: `resources/public/js/cookies.js` now takes an
explicit opt-out, `localStorage['orcpub:no-cookie-banner'] = '1'` (or `?no-cookie-banner=1`
when driving by hand), checked before the banner is built. Verified in a real browser —
control still shows the banner, both opt-out forms suppress it. Harnesses call
`suppressCookieBanner(context)` before the first navigation; `dismissCookieBanner(page)`
remains as a fallback for an already-loaded page.

Three separate obstacles stood between a headless harness and a real `.orcbrew` import — the
conflict modal, the actionability wait, and the banner occluding the button — and each was
found one at a time, each costing a run. **The generalisable lesson: when a harness stalls,
widen every diagnostic channel at once (all console levels, the full tool call log, the
page's own DOM state) before forming a single hypothesis.** Every round of "theorise, patch,
re-run" here cost more than one round of "capture everything" would have.

**String-slicing the fixture to clone it was wrong twice over:** a regex count of
`:orcpub.dnd.e5/<type>` found 2 content-bearing sources when there are 5, and the
hand-rolled brace matcher mis-split the top level. Reading the EDN properly on the JVM
found the real shape immediately. Do not parse this format with regexes.

## The port, and how it was checked here

`kahn-sort` and its two helpers were spliced in from `perf/entity-build` — the topo-sort
section only, not the whole file, so nothing unrelated to it came across. The order pin
(`test/cljc/orcpub/entity_build_perf_test.clj`) was rewritten to build its own fixture from
SRD classes so it does not depend on a test namespace that exists only on that branch.

| check | result |
|---|---|
| `lein test` on unmodified integration (with the pin present) | 304 tests, **0 failures** — so the pin's embedded reference *is* integration's implementation |
| `lein test` with the fix | 304 tests, **0 failures** |
| order pin: real build graph + 500 DAGs + 300 cyclic + degenerate, JVM | identical |
| the same 808 graphs re-run in the browser (cljs sets ≤8 iterate in insertion order; the JVM cannot see this class of bug) | **0 mismatches** |
| live build graph in the running app | identical |
| `kahn-sort` in the browser | 20.70 ms -> 0.843 ms (24.6x) |

## Track 1 spike: is chunked parsing worth a storage migration?

Spike: `test/browser/chunked_parse_spike_e2e.js` (dev copy: `dev-scratch/chunked_parse_spike.js`).
No code under `src/` touched; no migration implemented. Question: does parsing the
homebrew library **per source, yielding between sources**, actually shrink the longest
single blocking task — the thing a freeze is made of — enough to justify the localStorage
format change (one key -> N per-source keys, plus a migration for every existing user) that
per-source parsing requires.

**Modeling, not the migration.** The stored library is read once (unavoidable — something
has to see the source map), then each `[source-name value]` pair is `pr-str`-ed back into
standalone EDN text and `read-string`-ed one at a time, yielding
(`setTimeout(…, 0)`) between each. This stands in for what per-source *storage* would hand
the loader directly (N small strings instead of 1 big one) — it is legitimate for measuring
parse cost, but it is not itself the storage change.

Two fixtures, both real content, different source-size distributions:

- **mega-64** (3.87 MB, 14 sources) — the repo's primary stress fixture. **46.6% of its
  bytes sit in one synthetic source**, `"Duplicated Casters x64"` — `dev-scratch/megadup.clj`
  bundles all 64 cloned spellcasting classes into a single fabricated source for test
  convenience. This is a fixture artifact: a real user's homebrew grows source-by-source
  (one subscribed pack/book = one source), not by having one pack balloon to half the
  library.
- **mega-raw** (2.07 MB, 13 sources) — the same real content *before* caster duplication.
  Its largest source is 17.7% of total bytes (`Mordenkainen's Tome of Foes`); the rest are
  9.4%, 12.5%, 12.5%, 12.5%, 9.3%, 4.1%, 3.4%, 1.4%, 0.8%, 0.3%, ~0%. This is the organic,
  one-source-per-book distribution a real library actually has.

Warmed (2 discarded reads + a throwaway long task to prime the browser's longtask observer
— see note below), min-of-3-by-longest-task, `PerformanceObserver` `entryTypes:['longtask']`
(only reports tasks ≥50 ms, which is the right floor — nothing shorter reads as a freeze):

| fixture | cpu | baseline: total / **longest task** | chunked: total / **longest task** / chunks | longest-task reduction |
|---|---|---|---|---|
| mega-64 (46.6% in one source) | 1x | 612–654 ms / **612–654 ms** | 661–700 ms / **266–290 ms** / 14 | **~2.3x** (654 -> 270 typical) |
| mega-64 | 4x | 2935–3126 ms / **2935–3126 ms** | 2846–3038 ms / **1263–1366 ms** / 14 | **~2.3x** (3000 -> 1300 typical) |
| mega-raw (17.7% max in one source) | 1x | 325–368 ms / **325–368 ms** | 384–421 ms / **57–72 ms** / 13 | **~5.5x** (350 -> 62 typical) |
| mega-raw | 4x | 1584–1803 ms / **1584–1803 ms** | 1548–1812 ms / **267–334 ms** / 13 | **~5.9x** (1700 -> 290 typical) |

Ranges are 2 independent script runs (3 warmed reps each) per row; both fixtures repeated
consistently — no runs disagreed in direction, only within these small ranges. **Total time
is essentially unchanged or slightly higher** (splitting + yields add a little overhead) —
this was never a total-time play, only a hang-shape one, and the numbers confirm that: total
stays flat, longest task drops hard.

**Equivalence.** `cljs.core/=` between the chunked-and-merged map and the one-shot parse
initially reported **FAIL** — not a bug in chunking, but in the equivalence check: the real
uploaded content contains `##NaN` (a monster's malformed skill bonus in the Eberron source),
and `NaN = NaN` is false by IEEE-754 definition in both Clojure and JS, in *every* structure
that contains one, chunked or not. Re-checked with a NaN-tolerant deep-equal (two NaNs in
the same position count as equal, matching what `pr-str` would print), **every run above is
PASS**: chunked parsing reconstructs byte-for-byte the same data as the one-shot parse. (The
`##NaN` itself is a separate, pre-existing data-quality wrinkle in this library — noted here
because it nearly invalidated the check, not chased further; it is unrelated to chunking.)

**A tooling note for anyone re-running this**: Chrome's Long Tasks API silently drops the
*first* long task reported after a fresh page load/idle period — verified directly
(`dev-scratch/lt_debug.js`, `lt_debug2.js`, `lt_debug3.js`): five identical 700 ms busy-loops
back to back, first reads `longest=0`, next four read 700. A throwaway long task before the
timed runs (the reader warm-up doubles as this) fixes it. Missing this would have silently
zeroed out exactly the number this spike depends on.

### CORRECTION (later): the "capped by the largest source" limit was not real

Point 4 above, and point 3's reading of the mega-64 fixture, both said chunking's benefit is
permanently capped by the size of the single largest source — "the longest task can never
drop below roughly that source's own parse time" — and wrote off the user with one enormous
single-source pack as unfixable by this approach.

That is wrong, and the superseded reasoning is left above deliberately. It assumed the chunk
must be a whole source. It need not be: a source is `{content-type {item-key item}}` plus
non-content scalars, `merge-plugins` already reassembles it exactly, and measurement
(`test/browser/library_chunk_granularity_e2e.js`) shows the largest source in MegaPak is
383,817 chars whose largest content group is 366,488 — splittable, with items below that.

So the longest parse task is bounded by *chunk* size, not by source size, and mega-64's
46.6%-of-bytes single source is not a floor on what chunking can achieve. The same mistake
had also produced a claimed un-migratable library in the storage plan. See
`plan-chunked-library-storage.md` for both corrections and the batched migration algorithm.

### Recommendation: worth it

**Yes** — chunk the parse per source with a yield between sources. Reasoning:

1. **It works, and equivalence holds.** Same total work, same final data (NaN included),
   redistributed into tasks the browser can paint between.
2. **The realistic case is a big win.** On mega-raw's organic per-book distribution — the
   shape real homebrew actually takes — the longest task drops from a 325–368 ms stutter to
   a 57–72 ms blip at 1x, and from a 1.6–1.8 s hang to a 267–334 ms one at 4x. That is most
   of the way to this document's "< 400 ms" builder-open target, from this piece alone.
3. **Even the adversarial case improves substantially.** mega-64's single 46.6%-of-bytes
   source caps how much chunking alone can help — the longest task can never drop below
   roughly that source's own parse time — and it still only gets a ~2.3x reduction (3.0 s ->
   1.3 s at 4x), not enough by itself to clear the 400 ms target. But this cap is a fixture
   artifact, not a realistic shape: no ordinary usage path bundles 64 duplicated classes into
   one source the way `megadup.clj` does for test convenience. A real library that reached
   mega-64's byte volume would almost certainly be spread across many subscribed
   sources/books, closer to mega-raw's distribution — where chunking alone gets close to
   target.
4. **The residual risk is real but narrow and worth stating plainly**: the benefit is capped
   by the size of the single largest source in the library. A user with one enormous
   individual source (one giant homebrew pack authored as a single monolithic source, not
   many small ones) would still see a multi-hundred-ms-to-1s+ block post-migration. Chunking
   is not a complete fix for that case; it is a strong fix for the common one.
5. **Cost is contained.** No format upheaval — a per-source key/value split of the same EDN
   text, not a new serialization. It does not foreclose the larger IndexedDB migration this
   document already flags as the eventual right tier (async, no ~5 MB ceiling, lazy
   per-source hydration) — chunking under localStorage is a compatible stepping stone, not a
   competing design.

Net: pay for the storage-format migration. It is cheap relative to the win on the realistic
distribution, the win is not marginal (5-6x on mega-raw), and even the worst measured case
here is a clear improvement, not a wash.

### Why storage is one blob when import/export are already per-source

Asked while scoping the Track 1 migration: if a user can export and import one library at a
time, how is the library "one big string"? Checked against the code rather than assumed.

Because they are different layers. `app-db :plugins` is `{source-name -> plugin}`, and every
feature already works on that map per source:

| Path | Granularity | Code |
| --- | --- | --- |
| Export one source | per source | `::e5/export-plugin` takes `[name plugin]`; `select-emergency-export` does `(get plugins plugin-name)` |
| Export draft (WIP) | per source | `reg-export-draft` builds `{src {content-type {key item}}}` |
| Export everything | whole map | `::e5/export-all-plugins` |
| Import single-source | per source | `(assoc (:plugins db) plugin-name plugin)` |
| Import multi-source | per source, merged | `e5/merge-all-plugins` |
| Load-time salvage | per source, per entry | `salvage-library-items` is a `reduce-kv` over sources |
| Quarantine | per source, name-keyed | `reconcile-rejected`, in its own `plugins:rejected` key |

Only *persistence* is monolithic. `plugins->local-store` (`db.cljs:265`) is
`(set-item "plugins" (str plugins))` over the whole map, and the `::e5/plugins` cofx parses
that one string back. The `.orcbrew` file format is per-source; the localStorage
representation is not. Nothing about the file format forces the blob, and nothing about the
blob is visible to import or export.

**So the migration touches neither import nor export.** Both build or consume the in-memory
map and hand it to `::e5/set-plugins`; only the write sitting behind that event changes.
Same for the validation/fix functions: `salvage-library-items` and `reconcile-rejected` are
already keyed by source, so per-source keys fit them better than the blob does.

#### Quarantine already chunks, but not at the layer that matters

Raised on review of the above: isn't quarantine already pulling out only the troublesome
part? It is, per source and per item — but entirely *post-parse*. `get-local-storage-item`
runs `reader/read-string` over the whole blob before any per-source code can execute, and
`handle-unreadable` moves the entire blob to `plugins:corrupt` if that throws. Two different
kinds of chunking, and the app has only one:

| | Granularity | When it runs |
| --- | --- | --- |
| Quarantine (exists) | per source, per item | after the parse, in memory |
| Chunked storage (proposed) | per source | at the parse, in bytes |

Quarantine answers "which content is valid". Chunked storage answers "how much must be read
at once". The answer to the second is currently always *everything*, which is why the
builder-open freeze is one unbroken task regardless of how the library is organised.

```
NOW                                    AFTER
"plugins" = one string                 "plugins:v2:index"     [names]  (written LAST)
      |                                "plugins:v2:src:<name>" one string each
      v  read-string  ONE parse              |
   {src -> plugin}   ~750ms, blocking        v  read-string per source (N small parses)
      |                                 {src -> plugin}
      v  salvage-library-items               |
   kept -> app-db                            v  salvage-library-items  (unchanged)
                                        kept -> app-db

parse throws -> WHOLE blob quarantined  one source throws -> only that key quarantined
                                        half-written     -> index disagrees, detectable
```

#### Measured: 13 sources, 1 key, 2.07 MB

Asked twice, so it was settled by running it rather than by reading code
(`test/browser/storage_shape_e2e.js`, real e2e server, real import of MegaPak):

```
key                        chars
plugins                    2,166,081     <- 2.07 MB, ONE value
orcpub:no-cookie-banner            1

sources in the library: 13   (PHB, Xanathar's, Volo's, Mordenkainen's, Sword Coast,
                              Eberron, Wildemount, Ravnica, Theros, Acquisitions Inc,
                              Monster Manual, DMG, Default Option Source)
head: {"Xanathar's Guide to Everything" {:orcpub.dnd.e5/subclasses {:war-magic ...
tail: ... :pouch 1}, :key :athlete}}}}
```

One opening brace, 13 books, one closing brace. The intuition that storage is already
split comes from the UI, where everything genuinely is per-source — import, export, delete
and disable all act on one book. The flattening happens only at the save step, and it is
symmetric: toggling one source off re-serialises all 13 and rewrites all 2.07 MB; opening
the builder reads it all back through one `read-string`.

Two consequences worth carrying into the implementation:

1. **Corruption stops being all-or-nothing, which is a gain.** `preserve-on-unreadable-keys`
   currently moves the entire library to `plugins:corrupt` when one byte of the blob will not
   read. Per-source keys quarantine only the source that failed.
2. **Quota failure stops being atomic, which is a cost.** Today a full-library write either
   sticks or does not, and `::e5/plugins-save-failed` reports it. Split across keys, a write
   can half-succeed and leave storage internally inconsistent. The migration needs an index
   key written last, so a partial write is detectable on the next load instead of silently
   presenting a truncated library.

`orcbrew-val/correct-library` stays whole-library, and should: its cross-source key-conflict
detection genuinely needs to see every source at once. That is unaffected either way, since
all sources are still loaded into memory. Chunking is a parse/storage change, not a
semantic one.
