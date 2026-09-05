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
