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
