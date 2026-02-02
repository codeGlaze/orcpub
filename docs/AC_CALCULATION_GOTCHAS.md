# AC Calculation Gotchas

Known bugs, fix strategies, and architectural notes for the armor class
pipeline in `template_base.cljc`, `options.cljc`, and `magic_items.cljc`.

Test suite: `test/cljc/orcpub/dnd/e5/ac_test.clj` (11 tests, 47 assertions).
Fix branch: `claude/fix-ac-calculation-bugs-AHrs2` (see `BRANCH.md` for step-by-step plan).

---

## D&D 5e AC Rule Reference

> **PHB p.14:** "Some spells and class features give you a different way to
> calculate your AC. If you have multiple features that give you different
> ways to calculate your AC, you choose which one to use."

The key principle: **AC formulas don't stack**. When a character has multiple
formulas (Barbarian Unarmored Defense, Draconic Resilience, natural armor,
Robe of the Archmagi), you pick the best one. Only flat bonuses (shields,
magic items with +N AC) add on top.

---

## AC Pipeline Overview

The AC pipeline is defined in `template_base.cljc:35-87` via `es/make-entity`.
Values flow through three stages:

```
?base-armor-class          10 + DEX + (natural-or-zero) + magical
        |
?unarmored-armor-class     ?base-armor-class + ?unarmored-ac-bonus + ?ac-bonus
        |
?armor-class-with-armor    max(?base, ...?ac-fns) + sum(?ac-bonus-fns)
```

Three modifier mechanisms feed into the final AC:

| Mechanism | Storage | Semantics | Used by |
|-----------|---------|-----------|---------|
| `?natural-ac-bonus` | single int | Conditional add in `?base-armor-class` | Draconic Resilience (+3), lizardfolk (+3), tortle (+7) |
| `?unarmored-ac-bonus` | single int | Added in `?unarmored-armor-class` | Barbarian CON, Monk WIS |
| `?ac-fns` | vec of fns | Alternative AC formulas, compared via `max()` | (currently unused — should be used by Robe) |
| `?ac-bonus-fns` | vec of fns | **Additive** on top of max | Robe of Archmagi, Ioun Stone, Staff of Power |

---

## Bug 1: Natural + Unarmored AC Stacking

**Status:** Confirmed. 8 failing assertions across 4 test functions.

**Location:** `template_base.cljc:38-41,60`

### What the code does wrong

```clojure
;; template_base.cljc:38-41
?base-armor-class (+ 10 (?ability-bonuses ::char5e/dex)
                     (if (> ?unarmored-ac-bonus ?natural-ac-bonus)
                       0
                       ?natural-ac-bonus)    ;; <-- adds natural when natural >= unarmored
                     ?magical-ac-bonus)

;; template_base.cljc:60
?unarmored-armor-class (+ ?base-armor-class
                          ?unarmored-ac-bonus  ;; <-- ALWAYS adds unarmored on top
                          ?ac-bonus)

;; template_base.cljc:61-65
?unarmored-with-shield-armor-class (fn [shield]
                                     (+ ?base-armor-class
                                        ?unarmored-with-shield-ac-bonus  ;; <-- same problem
                                        ?ac-bonus
                                        (?shield-ac-bonus shield)))
```

The conditional in `?base-armor-class` only suppresses natural when unarmored
is strictly greater. But it **never** suppresses unarmored — that bonus is
always added in `?unarmored-armor-class`. When `natural >= unarmored`, both
bonuses end up in the final AC:

```
actual = 10 + DEX + natural + unarmored + magical
```

### Worked examples

**Barbarian 1 / Sorcerer(Draconic) 1** — DEX 14 (+2), CON 14 (+2):

| Step | Value |
|------|-------|
| Draconic sets `?natural-ac-bonus` | 3 |
| Barbarian sets `?unarmored-ac-bonus` | 2 (CON mod) |
| `?base-armor-class` = 10 + 2 + 3 (natural wins) + 0 | **15** |
| `?unarmored-armor-class` = 15 + 2 (unarmored added!) + 0 | **17** |
| **RAW correct**: max(Barbarian: 10+2+2=14, Draconic: 13+2=15) | **15** |

**Lizardfolk Barbarian** — DEX 12 (+1), CON 16 (+3):

| Step | Value |
|------|-------|
| Lizardfolk sets `?natural-ac-bonus` | 3 |
| Barbarian sets `?unarmored-ac-bonus` | 3 (CON mod) |
| `?base-armor-class` = 10 + 1 + 0 (unarmored > natural? No, equal) + natural 3 | **14** |
| `?unarmored-armor-class` = 14 + 3 (unarmored added!) | **17** |
| **RAW correct**: max(Barbarian: 10+1+3=14, lizardfolk: 13+1=14) | **14** |

**Tortle Monk** — DEX 14 (+2), WIS 16 (+3):

| Step | Value |
|------|-------|
| Tortle sets `?natural-ac-bonus` | 7 |
| Monk sets `?unarmored-ac-bonus` | 3 (WIS mod) |
| Tortle override forces AC to 17 + shield | works correctly |
| BUT if the override ever falls through... | stacking would occur |
| **RAW correct**: Tortle shell = flat 17 (ignores DEX, WIS) | **17** |

### Fix

In `template_base.cljc`, change `?base-armor-class` to use `max()` instead
of the conditional-add, and remove the redundant additions downstream:

```clojure
;; FIXED ?base-armor-class (lines 38-41):
?base-armor-class (+ 10 (?ability-bonuses ::char5e/dex)
                     (max ?unarmored-ac-bonus ?natural-ac-bonus)
                     ?magical-ac-bonus)

;; FIXED ?unarmored-armor-class (line 60):
?unarmored-armor-class (+ ?base-armor-class ?ac-bonus)

;; FIXED ?unarmored-with-shield-armor-class (lines 61-65):
?unarmored-with-shield-armor-class (fn [shield]
                                     (+ ?base-armor-class
                                        ?ac-bonus
                                        (?shield-ac-bonus shield)))
```

**Why this works:** `?base-armor-class` now picks the single best bonus
(unarmored or natural) via `max()`. No downstream formula re-adds it.

**Side effects to verify:**
- Barbarian's `?unarmored-ac-bonus` (CON) and `?unarmored-with-shield-ac-bonus`
  (CON) are set in `classes.cljc:65-72` via `cum-sum-mod`. After the fix, the
  max is computed in `?base-armor-class`, so the separate additions in
  `?unarmored-armor-class` and `?unarmored-with-shield-armor-class` must be
  removed to avoid double-counting.
- Monk's `?unarmored-ac-bonus` (WIS) in `classes.cljc:1255-1259` — same.
- Confirm `?unarmored-with-shield-ac-bonus` is ONLY set by Barbarian (grep).

---

## Bug 2: Robe of Archmagi Additive Stacking

**Status:** Confirmed. 2 failing assertions in 1 test function.

**Location:** `magic_items.cljc:2260-2264` + `modifiers.cljc:567-568` +
`template_base.cljc:78-85`

### What the code does wrong

The Robe of Archmagi registers a function via the `ac-bonus-fn` macro:

```clojure
;; magic_items.cljc:2260-2264
(mod5e/ac-bonus-fn
  (fn [armor shield]
    (if (nil? armor) 5 0)))    ;; returns +5 when unarmored
```

This macro pushes the function onto `?ac-bonus-fns`:

```clojure
;; modifiers.cljc:567-568
(defmacro ac-bonus-fn [bonus-fn]
  `(mods/vec-mod ~'?ac-bonus-fns ~bonus-fn))
```

In the final AC computation, `?ac-bonus-fns` results are **summed additively**
on top of the max AC:

```clojure
;; template_base.cljc:78-85
?armor-class-with-armor (fn [armor & [shield]]
                          (let [max-ac (apply max
                                              (?armor-class-with-armor-base armor shield)
                                              (map #(% armor shield) ?ac-fns))
                                bonuses (map #(% armor shield) ?ac-bonus-fns)]
                            (apply + max-ac bonuses)))  ;; bonuses added, not max'd
```

The Robe provides an **alternative AC formula** ("base AC 15 + DEX when
unarmored"), not a flat additive bonus. It should participate in `max()`.

### Worked example

**Sorcerer(Draconic) 6 with Robe of the Archmagi** — DEX 14 (+2):

| Step | Value |
|------|-------|
| Draconic `?natural-ac-bonus` | 3 |
| `?base-armor-class` = 10 + 2 + 3 | 15 |
| `max-ac` = max(15) | 15 |
| Robe's `?ac-bonus-fns` returns | +5 |
| `?armor-class-with-armor` = 15 + 5 | **20** |
| **RAW correct**: max(Draconic: 13+2=15, Robe: 15+2=17) | **17** |

### Fix

**Step 1:** Add an `ac-fn` macro to `modifiers.cljc` (targets `?ac-fns`):

```clojure
;; Add after ac-bonus-fn (line 568):
(defmacro ac-fn [ac-fn]
  `(mods/vec-mod ~'?ac-fns ~ac-fn))
```

**Step 2:** Change the Robe in `magic_items.cljc:2260` from `ac-bonus-fn` to
`ac-fn`, returning the full AC value (not a delta):

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

**Why the return value changes:** `?ac-fns` entries are compared via `max()`
against the base AC. They must return absolute AC values, not deltas. The Robe
says "base AC 15 + DEX" when unarmored, so the function returns `(+ 15 dex-mod)`.
When armored, the Robe provides no benefit, so it returns 15 (which will lose
the `max()` against any real armor).

### Audit of other `ac-bonus-fn` usages

These items also use `ac-bonus-fn` — verify each one is genuinely additive:

| Item | Location | Returns | Additive? | Action |
|------|----------|---------|-----------|--------|
| Ioun Stone of Protection | `magic_items.cljc:1631` | `(fn [_ _] 1)` — flat +1 always | Yes | Leave as `ac-bonus-fn` |
| Staff of Power | `magic_items.cljc:2522` | `(fn [_ _] 2)` — flat +2 always | Yes | Leave as `ac-bonus-fn` |
| Robe of the Archmagi | `magic_items.cljc:2260` | `(fn [a s] (if (nil? a) 5 0))` | **No** — it's a formula | Change to `ac-fn` |

---

## Non-Bug: Lizardfolk Override Closure (Originally Theorized as Bug 3)

**Status:** Tests pass. No bug found. Original theory was incorrect.

**Location:** `options.cljc:3246-3252`

The `:lizardfolk-ac` modifier overrides `?armor-class-with-armor` with a
closure that references `?base-armor-class` and the previous
`?armor-class-with-armor`:

```clojure
;; options.cljc:3248-3252
(mods/modifier ?armor-class-with-armor
  (fn [armor & [shield]]
    (max (+ ?base-armor-class
            (if shield (?shield-ac-bonus shield) 0))
         (?armor-class-with-armor armor shield))))
```

**Original theory:** The closure captures a "stale" entity from before class
modifiers run, so barbarian's CON bonus would be invisible.

**Why the theory was wrong:** The entity build pipeline in `entity.cljc:592-608`
does NOT apply modifiers in source order (race-first, class-second). Instead:

1. **All modifiers** from all options (race + every class) are collected at once
   (`collect-modifiers-2`, line 594)
2. A **dependency graph** is built from each modifier's `?`-symbol references
   (`deps` at `entity_spec.cljc:42` uses `tree-seq` to find `?`-symbols even
   inside nested `fn` forms)
3. **Kahn's topological sort** orders modifiers by dependency
   (`kahn-sort`, `entity.cljc:252`)
4. Modifiers are applied via `reduce` in dependency order
   (`apply-modifiers`, `modifiers.cljc:100`)

By the time the `:armor-class-with-armor` modifier runs, ALL its dependencies
(`:base-armor-class`, `:natural-ac-bonus`, `:unarmored-ac-bonus`, etc.) are
already in the entity. The closure captures a fully-populated entity — not a
stale snapshot.

**Key insight:** Don't assume modifier application order matches
template/option source order. The build system uses topological dependency
sorting. Trace the actual order via `entity.cljc:apply-options`.

---

## Gotcha: Lizardfolk/Tortle AC Overrides Interact with Bug 1

The lizardfolk and tortle race AC overrides in `options.cljc:3246-3258` both:
1. Set `?natural-ac-bonus` (3 for lizardfolk, 7 for tortle)
2. Override `?armor-class-with-armor` with a custom closure

Because Bug 1 causes `?base-armor-class` to include natural AND unarmored
bonuses, the lizardfolk override's `?base-armor-class` reference sees an
inflated value. Fixing Bug 1 (using `max()`) will automatically fix the
lizardfolk case — the override's `?base-armor-class` will see the correct
max'd value.

The tortle override hardcodes AC 17 + shield, so it sidesteps Bug 1 entirely.
However, if a tortle also has Draconic Resilience, `?natural-ac-bonus` would
be `(max 7 3)` = 7 since `cum-sum-mod` accumulates. This is handled correctly
because the tortle override ignores `?base-armor-class` entirely.

---

## Gotcha: `cum-sum-mod` vs `modifier` for AC Properties

Properties like `?unarmored-ac-bonus` use `cum-sum-mod` (cumulative sum), meaning
multiple sources ADD together. Properties like `?natural-ac-bonus` also use
`cum-sum-mod`. This is fine for single-source scenarios but creates confusion
when multiple sources exist:

- Lizardfolk (+3 natural) + Draconic Resilience (+3 natural) = 6 natural
- This is wrong per RAW (you choose one), but it doesn't cause visible bugs
  because both happen to be +3 and the lizardfolk override handles it

After fixing Bug 1, the `max()` in `?base-armor-class` makes this less
dangerous since the formula picks the single best bonus. But if two features
set different `?natural-ac-bonus` values, they'd still accumulate via
`cum-sum-mod` — the `max()` would see the combined value, not individual ones.

This is an edge case unlikely to matter in practice (no RAW character gets two
different natural AC sources), but worth noting for homebrew content.

---

## Test Coverage Summary

| Test | What it verifies | Status | Bug |
|------|-----------------|--------|-----|
| `barbarian-unarmored-defense` | Single-class AC = 10+DEX+CON | Pass | - |
| `monk-unarmored-defense` | Single-class AC = 10+DEX+WIS | Pass | - |
| `draconic-resilience-natural-ac` | Single-class AC = 13+DEX | Pass | - |
| `multiclass-ac-natural-and-unarmored-should-not-stack` | Barb/Sorc stacking | **Fail** (17 vs 15) | Bug 1 |
| `barbarian-shield-ac` | Shield + barbarian CON | Pass | - |
| `monk-shield-ac` | Shield drops monk WIS | Pass | - |
| `natural-armor-barbarian-stacking` | Lizardfolk + Barbarian | **Fail** (3 assertions) | Bug 1 |
| `shell-armor-monk-stacking` | Tortle + Monk | **Fail** (21 vs 17) | Bug 1 |
| `full-stack-race-multiclass-shield` | Race + Barb/Sorc + shield | **Fail** (3 assertions) | Bug 1 |
| `robe-archmagi-draconic-stacking` | Robe additive stacking | **Fail** (20 vs 17) | Bug 2 |
| `natural-armor-barbarian-high-con-stale-closure` | Closure is NOT stale | Pass | Non-bug |

After fixing both bugs, all 11 tests (47 assertions) should pass with no test
changes required — the tests already assert RAW-correct values.

---

*Last updated: February 2026*
