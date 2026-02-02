# Branch Context: claude/fix-ac-calculation-bugs-AHrs2

## Purpose

Fix two confirmed AC (armor class) calculation bugs in the D&D 5e character
builder. This branch was created from `claude/improve-test-coverage-hlyhp`,
which contains 11 tests (47 assertions) that already assert RAW-correct values.
10 assertions currently fail, documenting the bugs. The goal of this branch is
to fix the source code so all 47 assertions pass.

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

## Fix Strategy — Step by Step

### Step 1: Fix Bug 1 in template_base.cljc

```clojure
;; BEFORE (buggy) — lines 38-41:
?base-armor-class (+ 10 (?ability-bonuses ::char5e/dex)
                     (if (> ?unarmored-ac-bonus ?natural-ac-bonus) 0 ?natural-ac-bonus)
                     ?magical-ac-bonus)

;; AFTER (fixed):
?base-armor-class (+ 10 (?ability-bonuses ::char5e/dex)
                     (max ?unarmored-ac-bonus ?natural-ac-bonus)
                     ?magical-ac-bonus)
```

```clojure
;; BEFORE (buggy) — line 60:
?unarmored-armor-class (+ ?base-armor-class ?unarmored-ac-bonus ?ac-bonus)

;; AFTER (fixed) — remove redundant ?unarmored-ac-bonus:
?unarmored-armor-class (+ ?base-armor-class ?ac-bonus)
```

```clojure
;; BEFORE (buggy) — lines 61-65:
?unarmored-with-shield-armor-class (fn [shield]
                                     (+ ?base-armor-class
                                        ?unarmored-with-shield-ac-bonus
                                        ?ac-bonus
                                        (?shield-ac-bonus shield)))

;; AFTER (fixed) — remove redundant ?unarmored-with-shield-ac-bonus:
?unarmored-with-shield-armor-class (fn [shield]
                                     (+ ?base-armor-class
                                        ?ac-bonus
                                        (?shield-ac-bonus shield)))
```

**Side effects to check:**
- Barbarian's `?unarmored-ac-bonus` (CON) and `?unarmored-with-shield-ac-bonus`
  (CON) are set in `classes.cljc:65-72`. After the fix, `?base-armor-class`
  already includes the max of unarmored vs natural, so the separate additions
  in `?unarmored-armor-class` and `?unarmored-with-shield-armor-class` become
  redundant and must be removed.
- Monk's `?unarmored-ac-bonus` (WIS) in `classes.cljc:1255-1259` — same logic.
- Verify that `?unarmored-with-shield-ac-bonus` is ONLY set by barbarian
  (monks don't get shield bonuses per RAW). Grep the codebase to confirm.

### Step 2: Fix Bug 2 in modifiers.cljc and magic_items.cljc

```clojure
;; ADD to modifiers.cljc (near line 568, after ac-bonus-fn):
(defmacro ac-fn [ac-fn]
  `(mods/vec-mod ~'?ac-fns ~ac-fn))
```

Then in `magic_items.cljc:2260`, change:
```clojure
;; BEFORE:
(mod5e/ac-bonus-fn
  (fn [armor shield]
    (if (nil? armor) 5 0)))

;; AFTER:
(mod5e/ac-fn
  (fn [armor shield]
    (+ 15 (if (nil? armor)
             (?ability-bonuses ::char5e/dex)
             0))))
```

Note: The Robe's formula is "base AC 15 + DEX when unarmored". It should
return the full AC value (not a delta), because `?ac-fns` entries are compared
via `max()` against the base AC calculation.

**Audit other `ac-bonus-fn` usages** to determine if they are truly additive
bonuses or alternative formulas:
- `magic_items.cljc:1631` — Ioun Stone of Protection: `(fn [_ _] 1)` — flat +1
  to AC regardless of armor. This IS a true additive bonus. Leave as-is.
- `magic_items.cljc:2522` — Staff of Power: `(fn [_ _] 2)` — flat +2 to AC.
  This IS a true additive bonus. Leave as-is.

### Step 3: Verify with tests

```bash
/usr/bin/lein test :only orcpub.dnd.e5.ac-test
```

All 47 assertions across 11 tests should pass. If Claude Code Web lacks
Clojars access, run the workarounds first:
```bash
bash .agent-workarounds/maven-proxy/setup-maven-proxy.sh
bash .agent-workarounds/clojars-deps/install-test-deps.sh
```

### Step 4: Run full test suite for regressions

```bash
/usr/bin/lein test
```

Ensure no existing tests break. Pay special attention to:
- `warlock_test.clj` — exercises the full entity build pipeline
- `template_test.clj` — may reference AC properties
- `character_test.clj` — character accessors

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
