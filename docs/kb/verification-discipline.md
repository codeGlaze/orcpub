# Verification Discipline — lessons (assumptions & thoroughness)

**Purpose:** Hard-won lessons from a long session where confident claims were repeatedly
wrong until verified. For agents and humans working this codebase. The throughline:
**verify against the real callers / intent / runtime before asserting — and mark what
you haven't verified as unverified.**

## Lessons (each with the concrete miss that taught it)

1. **A red test ≠ "the code is broken."** It means the test and the code *disagree*. The
   test runner surfaces the disagreement; it does not tell you which side is wrong.
   Adjudicating that requires reading the code **and** checking real callers / intended
   behavior. *Miss:* I called import failures "code bugs," then "stale tests," before
   adjudicating either.

2. **Verify against callers/intent, not the function in isolation.** Reading a function
   alone tells you what it does, not whether it's correct. *Miss:* I labeled
   `apply-key-renames` a "stale test" from the function's `:from`/`:to` destructuring;
   only checking the real caller (`events.cljs:4042` builds `:from`/`:to`) actually
   confirmed it — the test passes the old `:old-key`/`:new-key` shape.

3. **A shallow-clone blame boundary (`^sha`) means "older than the history I have," NOT
   "never existed."** Unshallow before claiming something was never there. *Miss:* I said
   the `::character` spec "never existed," over-correcting; unshallowing showed it was
   added by Larry in 2016 and removed later. The `^` marker was the tell I ignored.

4. **Same name, different registries — don't assume what a symbol *is*.** `::char5e/character`
   is a re-frame **subscription** (and was once a spec); `built-character` is a fn + sub,
   **not** a spec; "entity spec" / `entity-spec` is the **build engine**, not `clojure.spec`.
   Check the kind/registry before reasoning about it.

5. **JVM-isms in `.cljs` only surface in an actual cljs run.** `(int char)` returns a code
   point on the JVM but `0` in ClojureScript (no Character type). Source review and
   `lein test` (JVM) cannot catch these; a headless cljs run can. *Example:*
   `import-validation/count-non-ascii` uses `(int %)` and silently no-ops in the browser.

6. **Know what a tool's output actually represents.** The figwheel auto-test **DOM lists
   every test (passing included)**; I conflated that list with the *failure set* and
   mis-stated which tests failed. Use the clean per-test reporter for authoritative
   pass/fail + expected/actual.

7. **State uncertainty; don't pre-exonerate or pre-condemn.** When a claim is reasoned
   from the surface rather than verified, say "unverified" instead of "the code is fine"
   / "the code is broken."

8. **Reading the leaf is not reading the feature — walk UP to the caller and DOWN to the
   primitive before claiming behavior.** *Misses (this session, both stamped "VERIFIED"
   from a single-function read, both wrong, both caught by the user forcing the trace):*
   (a) `compile-feature` — I read the cfg and missed that `?class-level` is resolved at
   build time by the entity-spec (`template_base.cljc:125`), not a plain value. (b) Grant
   vocabulary B — I read `level-modifier` (a plain `case`, no level logic) and concluded
   "B does no level-gating"; the gating lives in the *caller* `make-levels`
   (`spell_subs.cljs:392`, `(group-by :level …)`), so B *is* level-gated. Note lesson 2
   already said this in prose — and I still failed it. The durable fix is not "try harder";
   it is to back behavioral claims with a **falsifiable test** that builds the real thing,
   so a leaf-misread fails the test instead of shipping as a confident doc claim.

## A check that has never failed has never been tested

**The shape:** a validator, guard, probe or test whose *failure* is indistinguishable from its
*success*. It reports "clean", everyone relaxes, and nobody can tell whether that means the tree is
healthy or the check is blind.

It bit repeatedly across September 2026, in five different disguises:

| the thing | how it lied |
|---|---|
| `document.fonts.check()` | returns `true` for fonts that do not exist ([icon-font-failure.md](icon-font-failure.md)) |
| glyph-width comparison | measured 60.22px with and without the font — no discrimination |
| the ETag interceptor's `catch` | logged the exception, discarded the response, returned `200` ([locale-safety.md](locale-safety.md)) |
| `topic-index.md`'s generator | crashed for a week; needed Leiningen, so nobody could discover that |
| `.githooks/` | committed, reviewed, and never armed — `core.hooksPath` does not survive a clone |
| the first `kb lint` reachability check | resolved paths against the cwd and flagged all 135 documents |

That last one was caught only because it failed *loudly*. The same bug in the other direction —
always returning "reachable" — would have passed silently forever.

### The rule

> **Before trusting a check, show it a case it should catch and confirm it complains.**

Running it against a healthy tree proves it does not crash. It proves nothing about whether it
works. Those are different claims and the green tick looks identical for both.

### Making it mechanical

Discipline that depends on remembering is the same failure one level up — the `.githooks/` row
above is exactly that. So the rule is executable:

```
python3 docs/kb/tools/kb.py lint --self-test
```

It builds a fixture per check, breaks it in the specific way that check exists to detect, and
requires the check to complain. A clean fixture must pass. `.githooks/pre-commit` runs it *before*
`kb lint`, so the guard is verified before its verdict is believed.

Verified by sabotage: stubbing the reachability check to always pass, and the H1 check to never
fire, each makes `--self-test` exit 1 naming the blind check.

**When you add a check anywhere in this repo, add the case that makes it fail.** If you cannot
write that case, you do not yet know what the check detects.

## Comparing the existing codebase to a proposed upgrade (the method)

The question "is the upgrade equivalent / better / preserving the good?" is answered by a
**characterization test**, not by prose — and it does double duty:
- **It checks my understanding of the OLD code.** You cannot assert the real output without
  running the real code path, so writing it forces the up/down trace; if I mis-read the
  existing code, the characterization fails against the *current* code *now* — not three
  turns later. (This is the antidote to "your source understanding is suspect.")
- **It is the BASELINE the upgrade must reproduce.** Comparison becomes mechanical:
  - **Preserve** → the old characterization stays green against the new code (the good kept).
  - **Change on purpose** → it goes red; the diff shows *exactly* what changed, and you
    update the baseline deliberately (intended improvement vs regression is a decision, made
    visible).
  - **New capability** (old code couldn't do it) → no old behavior to match; the test asserts
    the new thing works AND every old characterization stays green (added without breaking).
- **Compare to literally pre-branch** by running the characterization at the merge-base commit
  (`980cc790`): green there = a true baseline; any divergence on the branch is then attributable.
- **What a behavior test can't compare** (design/maintainability — "one vocabulary vs two,"
  or **fewer files *touched per change*** — NOT fewer files in the tree; monoliths are not the
  goal) is compared by (a) the shown chain (so the structural claim is checkable) and
  (b) a falsifiable *effort* test — e.g. "expose a second pool in ~1 line, shown in a commit"
  (D21). Even "easier to extend" becomes falsifiable, not prose.
- **Honest limit:** a characterization only protects what it covers. Narrow coverage → silent
  regressions outside it. So coverage must reach the load-bearing behavior *before* the refactor
  it guards — which is precisely why "the foundation net gates refactors."

## Search the dead/old code too, not just the live surface

"Is X extensible?" / "How was X done before?" is not answered by the live `*-options` def or
`develop` alone. Old and dead worked examples live as `#_`-commented forms — notably in
`src/cljc/orcpub/dnd/e5/templates/ua_*.cljc` (the pre-refactor UA content). Twice this session a
fighting-style claim was made without checking there: first "fighting styles aren't extensible"
(the concat pattern was just at a different assembly point), then "no old fighting-style pipeline
exists" (Mariner is in `ua_base.cljc:690`, `#_`-commented). Before concluding a mechanism is
absent, grep the whole tree including `templates/` and `#_`-struck forms — the answer is often in
the dead code.

## A green (or red) number proves nothing if the FIXTURE doesn't match real content

A test builds synthetic inputs; if the synthetic path differs from how real content works, the
number characterizes the fixture, not the engine. *Miss (this session):* I reported an "A3 bug" —
two natural-armor sources stacking to 18 — from a test whose synthetic classes used the cum-sum
constructor `mod5e/natural-ac-bonus`. But ALL real content sets `?natural-ac-bonus` via
`mod/modifier` (a SET; `es/modifier` replaces, doesn't accumulate). With the fixture switched to the
real SET mechanism the "bug" vanished (15, no stacking) — there was nothing to fix. The user caught
it ("is that even in integration, or something you introduced?"). The tell I ignored: I checked the
output number without checking the MECHANISM that produced the input — which constructor, SET vs
cum-sum. Before calling a characterized number a bug, confirm the fixture uses the SAME modifier
primitives real content uses; grep who actually writes the channel in `src/`. (Companion to the
"same name, different registries" lesson — here it was "same channel, two accumulation semantics,
one live and one a trap.")

## The rule

Before asserting a load-bearing claim — especially **"X is broken"** or **"X is fine"** —
ask: *have I verified this against the real callers, the intended behavior, and (for cljs)
the actual runtime — or am I reasoning from the surface?* If the latter, mark it
**unverified** and go check before stating it as fact. The cost of the extra check is far
lower than the cost of a confident wrong claim about someone's code.

**Standing rule (always-on):** *Don't call it verified without walking it up and down and
backing it with a falsifiable test (or showing the full caller→fn→primitive chain, file:line
each). A single-function read is a hypothesis, not a finding.* Behavioral claims that the plan
rests on become characterization tests, not prose; that test is simultaneously the check on my
reading and the baseline an upgrade is compared against.

## A test whose contributors share a magnitude proves nothing

Bracers of Defense (+2, no armor and no shield) was "verified" against a plain shield (+2):

```
unarmored        14   ; 10 + Dex(2) + bracers(2)
unarmored+shield 14   ; 10 + Dex(2) + shield(2)
```

Two 14s, and the second was read as "the bracers were correctly excluded". It is equally
consistent with the bracers applying and the shield being dropped, or with both applying and
something else vanishing. The assertion cannot attribute the number to a cause.

Two fixes, use both: **vary the magnitudes** so each contributor is identifiable (a +1 shield
contributes 3, so the answer is 15 if excluded and 17 if not), and **assert the delta** against the
same character without the feature rather than the absolute total. The delta is what the feature
actually claims.

Applies past AC: whenever a test pins one number produced by summing several sources, check that no
two of them are equal before believing it.

## A comparison is only as good as its baseline — verify the baseline by CONTENT

The Bracers/natural-armor defect was called shipped, then retracted as "our own regression, siloed
to the refactor branch", then un-retracted. It is shipped. The retraction came from comparing
against `agents/develop` — assumed to be the integration branch because the name looked right. The
integration branch is `origin/integration`, and it has the defect.

Two failures, both cheap to avoid:

1. **The baseline was assumed, not checked.** One `git branch -r` would have shown
   `origin/integration` sitting there. Never name a comparison baseline from a branch's name.
2. **`git merge-base --is-ancestor <sha> <branch>` said "no" and that was believed.** The commit had
   reached integration under a different SHA (merge or cherry-pick). Ancestry answers "is this
   commit an ancestor", never "does this branch have this change". **Compare the content** — read
   the file on the target branch, or run the test against it.

The empirical method itself was right: swap in the other branch's file, run the one test, read the
number. It just ran against the wrong file. Running it against two branches would have shown the
disagreement immediately, since `agents/develop` and `origin/integration` do not agree here.

## Benchmark rules: warm up, and measure cost not proxies

Two failures in one afternoon, on the same benchmark:

1. **No JIT warmup.** The first run reported 0.82x / 1.96x / 1.10x across three scenarios of
   increasing size. A non-monotonic curve over a monotonic workload is a measurement artifact, not
   a finding — the first scenario measured was paying for compilation. With warmup it read
   0.64x / 0.68x / 1.19x, monotonic and believable, and the conclusion reversed.
2. **Counting operations instead of timing them.** An earlier benchmark counted formula
   evaluations, found 396 vs 102, and concluded the optimisation was worthwhile. The counts were
   right. But the optimisation allocates a state map, and that costs more than the evaluations it
   saves until the input is far larger than anything real — so the version doing 4x the work is
   *faster* at realistic sizes.

Also: always locate the crossover. "Faster at size N" is not a result; "slower below ~8 armors,
faster above, and the app never exceeds 3" is.

---

# Probes and benchmarks — the measurement half

How this repo has actually been wrong, and what caught it. Written from the homebrew
builder-freeze investigation ([perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)),
where six diagnoses inferred from reading code were wrong and every correction came from a
measurement.

## Reading the code tells you what could happen; only running it tells you what does



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
