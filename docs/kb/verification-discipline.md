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

## The rule

Before asserting a load-bearing claim — especially **"X is broken"** or **"X is fine"** —
ask: *have I verified this against the real callers, the intended behavior, and (for cljs)
the actual runtime — or am I reasoning from the surface?* If the latter, mark it
**unverified** and go check before stating it as fact. The cost of the extra check is far
lower than the cost of a confident wrong claim about someone's code.
