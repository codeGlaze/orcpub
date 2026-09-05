# Verification discipline

How this repo has actually been wrong, and what caught it. Written from the homebrew
builder-freeze investigation ([perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)),
where six diagnoses inferred from reading code were wrong and every correction came from a
measurement.

## The rule

**Reading the code tells you what could happen. Only running it tells you what does.**

Six diagnoses of one freeze, each confidently argued from source, each killed by a number:

| Diagnosis | Killed by |
| --- | --- |
| Unbounded `memoized-spell-option` | A/B: heap grew *more* without memoization (41.4 vs 38.5 MB) |
| Class bodies rebuilt per switch | counters: zero `class-option` / `make-levels` on any switch |
| Major GC pause | the long task ALLOCATES 45 MB; real collections landed on *fast* switches |
| Spell options / spell data | `memoized-spell-option` armed and never called during the block |
| `range` chunking | the one-line "fix" changed nothing; the consumer iterates fully |
| One memoize site (`set-class`) | removing it alone left the freeze unchanged; three sites shared the defect |

The actual cause was found by capturing a stack during the failing interaction.

## Probe defects that produced confident wrong answers

Each of these cost at least one run, and several nearly produced a wrong conclusion.

- **A control that suppresses what it measures.** A "positive control" ran before the
  measured window and realised the expensive content up front, so the freeze never occurred.
  Comparing that run against uncontrolled ones would have "proved" the bug was a dev-build
  artifact. Hence `SKIP_CONTROL`; runs compared to each other must agree on it.
- **Instrumentation that cannot intercept.** Wrapping a function that a caller captured at
  definition time (e.g. the target of a `memoize`) intercepts nothing and reports zeroes.
  Wrap the var the caller actually reaches.
- **A dead probe reporting silence.** Counters that never armed look exactly like "nothing
  ran". Always print what armed; `spy armed for: NOTHING` turned an invisible failure into
  an obvious one.
- **`with-redefs` around asynchronous work.** It unwinds when its body exits, so a callback
  firing later runs the real function and goes uncounted. Use `set!` and restore explicitly.
- **Escaping through two layers.** Instrumentation injected as a JS template literal turned
  `\n` into a real newline and broke the script at parse time. Use `String.fromCharCode(10)`.
- **Truncated stacks.** V8 defaults to `Error.stackTraceLimit = 10`, which hid the consumer
  inside library internals. Raise it before capturing.
- **A once-per-session capture describing the wrong event.** A "first call" stack recorded
  page load, not the failure. Reset per measured interaction.
- **Self time on allocation-heavy code.** It parks in `(program)` and GC, so every app frame
  looks small. Rank by *inclusive* time to find the frame that contains the work.
- **A model that is not the app.** A synthetic harness reproduced a double build and went
  green when "fixed", while the app kept doing it for an unrelated reason. When harness and
  app disagree, the app is right.

## Measure the right thing

- **A freeze is one long task.** Totals and averages erase it. Report the longest single
  task (`PerformanceObserver`, `entryTypes: ['longtask']`).
- **Reproduce the user's conditions.** This freeze needed both homebrew volume *and* CPU
  contention; it is invisible unthrottled. `Emulation.setCPUThrottlingRate` 4x models a
  laptop also running the server.
- **Check dev vs prod before optimising.** `:optimizations :none` inflates costs and a
  `(program)`-dominated profile is its signature. One cold-load finding was discarded for
  this; the freeze survived prod at 654 ms and was real.
- **Change one variable.** Same pack, same throttle, same probe shape -- otherwise the
  comparison proves nothing.

## Related

- [perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md) -- the investigation
- [reagent-architecture-tenets.md](reagent-architecture-tenets.md) -- why `memoize` is the
  wrong tool here
- [documentation-tenets.md](documentation-tenets.md) -- recording reversals rather than
  overwriting them
