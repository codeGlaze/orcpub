# Branch Context: claude/fix-ac-calculation-bugs-AHrs2

## Purpose

Fix two confirmed AC (armor class) calculation bugs in the D&D 5e character
builder. **Both bugs are fixed.** All 47 assertions across 11 tests pass.
No regressions in warlock, template, character, or dice tests (31 tests, 164
assertions total).

## Parent Branch

`claude/improve-test-coverage-hlyhp` — provides the test suite in
`test/cljc/orcpub/dnd/e5/ac_test.clj`. See that branch's BRANCH.md for full
test-authoring context.

## Bugs to Fix

Full analysis with code snippets, examples, and fix strategies is in
[`docs/ac_calculation_gotchas.md`](docs/ac_calculation_gotchas.md).

### Bug 1: Natural + Unarmored AC Stacking (8 failing assertions)

- **Files:** `src/cljc/orcpub/dnd/e5/template_base.cljc:38-41,60`
- **Rule violated:** PHB p.14 — multiple AC formulas don't stack; you choose one
- **Symptom:** Barbarian 1 / Sorcerer(Draconic) 1 with DEX +2, CON +2 gets AC 17
  instead of the correct AC 15
- **Root cause:** `?base-armor-class` conditionally adds `?natural-ac-bonus`,
  then `?unarmored-armor-class` unconditionally adds `?unarmored-ac-bonus` on
  top. When natural >= unarmored, both bonuses are included.
- **Fix:** Replace the conditional-add in `?base-armor-class` with
  `(max ?unarmored-ac-bonus ?natural-ac-bonus)` and remove the redundant
  `?unarmored-ac-bonus` addition from `?unarmored-armor-class`
- **Affected tests:** `multiclass-ac-natural-and-unarmored-should-not-stack`,
  `natural-armor-barbarian-stacking`, `shell-armor-monk-stacking`,
  `full-stack-race-multiclass-shield`

### Bug 2: Robe of Archmagi Additive Stacking (2 failing assertions)

- **Files:** `src/cljc/orcpub/dnd/e5/magic_items.cljc:2260-2264`,
  `src/cljc/orcpub/dnd/e5/modifiers.cljc:567-568`,
  `src/cljc/orcpub/dnd/e5/template_base.cljc:78-85`
- **Symptom:** Robe of Archmagi + Draconic Resilience with DEX +2 gives AC 20
  instead of the correct AC 17
- **Root cause:** The Robe registers via `ac-bonus-fn` (pushes to
  `?ac-bonus-fns`), which are summed additively on top of the max AC. It should
  instead register as an alternative formula via `?ac-fns` and participate in
  `max()`.
- **Fix:** Create an `ac-fn` macro in `modifiers.cljc` (analogous to
  `ac-bonus-fn` but targeting `?ac-fns`), then change the Robe's registration
  from `ac-bonus-fn` to `ac-fn`
- **Affected tests:** `robe-archmagi-draconic-stacking`

## What Was Fixed

### Bug 1 fix (template_base.cljc)

The original plan put `max()` in `?base-armor-class`. This was wrong — it
bled monk WIS into the shield path (monk WIS shouldn't apply with shield).

**Actual fix:** Keep `?base-armor-class` clean (`10 + DEX + magical`), move
the `max()` into the downstream formulas where each path can select the
correct bonus independently:

```clojure
?base-armor-class (+ 10 (?ability-bonuses ::char5e/dex) ?magical-ac-bonus)

?unarmored-armor-class (+ ?base-armor-class
                          (max ?unarmored-ac-bonus ?natural-ac-bonus)
                          ?ac-bonus)

?unarmored-with-shield-armor-class (fn [shield]
                                     (+ ?base-armor-class
                                        (max ?unarmored-with-shield-ac-bonus ?natural-ac-bonus)
                                        ?ac-bonus
                                        (?shield-ac-bonus shield)))
```

**Why this works:** Each path picks the best bonus independently. The shield
path uses `?unarmored-with-shield-ac-bonus` (set by barbarian only, 0 for
monk), so monk WIS correctly drops out when a shield is equipped.

### Bug 1 supplemental fix (options.cljc)

Tortle's `:tortle-ac` override only replaced `?armor-class-with-armor` (flat
17), but the test reads `?unarmored-armor-class` directly. Added
`(mods/modifier ?unarmored-armor-class 17)` to the tortle override so both
properties agree.

### Bug 2 fix (modifiers.cljc, magic_items.cljc, ac_test.clj)

Added `ac-fn` macro targeting `?ac-fns` (the max-comparison vector). Changed
the Robe of the Archmagi from `ac-bonus-fn` (additive) to `ac-fn` (formula).
The Robe's function now returns the full AC value including DEX and shield:

```clojure
(mod5e/ac-fn
  (fn [armor shield]
    (if (nil? armor)
      (+ 15 (?ability-bonuses ::char5e/dex)
         (if shield (?shield-ac-bonus shield) 0))
      0)))
```

The test's `robe-bearer-race-cfg` was also updated to use `ac-fn` (the test
defined its own Robe implementation independent of `magic_items.cljc`).

### Verification

```
11 AC tests, 47 assertions: 0 failures
31 total tests, 164 assertions: 0 failures (excludes routes_test which
  requires Datomic, unavailable in this environment)
```

## Architectural Context

### Entity build pipeline (modifier ordering)

The entity system does NOT apply modifiers in source order. Instead:
1. All modifiers from all options (race + classes) are collected (`entity.cljc:594`)
2. A dependency graph is built by scanning `?`-symbol references (`entity_spec.cljc:42`)
3. Kahn's topological sort orders them (`entity.cljc:252`)
4. Applied via `reduce` in dependency order (`modifiers.cljc:100`)

This means race modifiers and class modifiers interleave based on their
dependencies, not their source position. Closures capture the entity AFTER all
dependencies are satisfied.

### AC modifier properties reference

| Property | Default | Semantics |
|----------|---------|-----------|
| `?natural-ac-bonus` | 0 | Natural armor (Draconic +3, lizardfolk +3, tortle +7) |
| `?unarmored-ac-bonus` | 0 | Unarmored defense ability bonus (Barbarian CON, Monk WIS) |
| `?unarmored-with-shield-ac-bonus` | 0 | Same as above but with shield (Barbarian only) |
| `?ac-bonus` | 0 | General flat AC bonus |
| `?armored-ac-bonus` | 0 | Bonus when wearing armor (Defense fighting style) |
| `?magical-ac-bonus` | 0 | Magical bonus to all AC |
| `?ac-fns` | [] | Alternative AC formulas — compared via `max()` |
| `?ac-bonus-fns` | [] | Additive AC bonus functions — summed on top of max |
| `?max-medium-armor-bonus` | 2 | DEX cap for medium armor |

## Key Files

| File | Role |
|------|------|
| `src/cljc/orcpub/dnd/e5/template_base.cljc:35-87` | Core AC calculation (Bug 1 location) |
| `src/cljc/orcpub/dnd/e5/modifiers.cljc:567-568` | `ac-bonus-fn` macro (Bug 2 helper) |
| `src/cljc/orcpub/dnd/e5/magic_items.cljc:2260-2264` | Robe of Archmagi (Bug 2 location) |
| `src/cljc/orcpub/dnd/e5/classes.cljc:64-72` | Barbarian unarmored defense |
| `src/cljc/orcpub/dnd/e5/classes.cljc:1254-1259` | Monk unarmored defense |
| `src/cljc/orcpub/dnd/e5/classes.cljc:2258-2262` | Draconic Resilience (natural AC +3) |
| `src/cljc/orcpub/dnd/e5/options.cljc:3246-3258` | Lizardfolk and Tortle AC overrides |
| `src/cljc/orcpub/entity.cljc` | Entity build pipeline (topological sort) |
| `test/cljc/orcpub/dnd/e5/ac_test.clj` | AC test suite (11 tests, 47 assertions) |

## Related Docs

- [`docs/ac_calculation_gotchas.md`](docs/ac_calculation_gotchas.md) — full bug analysis with worked examples
- [`docs/TESTING.md`](docs/TESTING.md) — test suite patterns
- [`docs/CLAUDE.md`](docs/CLAUDE.md) — KB index for navigating docs/
- [`.agent-workarounds/CLAUDE.md`](.agent-workarounds/CLAUDE.md) — Claude Code Web dependency workarounds
