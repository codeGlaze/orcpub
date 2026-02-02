# Branch Context: claude/improve-test-coverage-hlyhp

## Purpose

AC stacking bug test coverage. Tests expose two confirmed bugs in the
armor class pipeline and document the entity build system's modifier
ordering (topological sort, not source order).

## Current State

- **11 AC tests** written and verified (`test/cljc/orcpub/dnd/e5/ac_test.clj`)
  - 37/47 assertions pass (single-class formulas, shield interactions, closure correctness)
  - 10/47 assertions fail as expected (Bug 1 x8, Bug 2 x2)
- **Bug 1 confirmed:** natural + unarmored AC bonuses stack instead of using max
  (`template_base.cljc:38-41,60`)
- **Bug 2 confirmed:** Robe of Archmagi adds +5 on top of max AC instead of
  participating in max (`magic_items.cljc` + `template_base.cljc:82-85`)
- **Bug 3 retracted:** stale closure theory was wrong; the entity build
  pipeline topologically sorts modifiers (Kahn's algorithm, `entity.cljc:592-608`),
  so all dependencies are satisfied before the override closure runs
- **Claude Code Web workaround** for running `lein test` without Clojars access
  (`.agent-workarounds/clojars-deps/install-test-deps.sh`)

## Workflow

- This branch is test-only — source fixes go on a separate `upgrade/*` branch
- Tests assert RAW-correct values; failing tests document bugs, not regressions
- To run tests in Claude Code Web: `bash .agent-workarounds/maven-proxy/setup-maven-proxy.sh && bash .agent-workarounds/clojars-deps/install-test-deps.sh`
- Then: `/usr/bin/lein test :only orcpub.dnd.e5.ac-test`

## Handoff Notes

### For the fix agent (separate branch)

Branch from `upgrade/security-jackson-guava` (per AGENTS.md).

**DO NOT** put `max()` into `?base-armor-class`. That breaks the shield
path — monk WIS leaks into the shield formula because `?base-armor-class`
is shared between `?unarmored-armor-class` and
`?unarmored-with-shield-armor-class`.

**Recommended approach: revive `?ac-fns`.**

`?ac-fns` (`template_base.cljc:87`) is an empty vec designed for alternative
AC formulas compared via `max`. Nothing populates it — there is no `ac-fn`
helper (only `ac-bonus-fn` exists). The fix for both bugs:

1. Create an `ac-fn` helper macro that pushes to `?ac-fns`
2. Move each AC source (barbarian, monk, draconic, lizardfolk, tortle, Robe)
   from the `?base-armor-class`/`?unarmored-ac-bonus` chain into
   self-contained `?ac-fns` formulas
3. Each formula receives `(armor, shield)` and handles its own shield logic
4. `?ac-bonus-fns` stays for true additive bonuses (Shield of Faith, etc.)

See `docs/AC_CALCULATION_GOTCHAS.md` "Recommended Fix Architecture" for
details and pseudocode.

**After fixing**, the 10 currently-failing assertions in `ac_test.clj` should
pass. No test changes needed — the tests already assert RAW-correct values.

### Key architectural insight

The entity build pipeline (`entity.cljc:apply-options`) collects ALL modifiers
from all options, builds a dependency graph from `?`-symbol references, and
applies them in topological order. Modifier closures capture the entity AFTER
all their dependencies are satisfied, not at "source position" time. This is
documented in `docs/AC_CALCULATION_GOTCHAS.md`.

## Related Docs

- `docs/AC_CALCULATION_GOTCHAS.md` — AC pipeline bugs, fix strategies, test coverage
- `docs/CLAUDE.md` — KB index for navigating docs/
- `docs/TESTING.md` — test suite patterns (use `warlock_test.clj` as template)
- `.agent-workarounds/CLAUDE.md` — Claude Code Web dependency workarounds
