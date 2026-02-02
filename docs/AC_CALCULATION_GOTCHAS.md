# AC Calculation Gotchas

Known bugs and architectural notes for the armor class pipeline
in `template_base.cljc`, `options.cljc`, and `magic_items.cljc`.

Test suite: `test/cljc/orcpub/dnd/e5/ac_test.clj` (11 tests, 47 assertions).

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
| `?natural-ac-bonus` | single int | Conditional add in `?base-armor-class` | Draconic Resilience, lizardfolk-ac |
| `?unarmored-ac-bonus` | single int | Added in `?unarmored-armor-class` | Barbarian CON, Monk WIS |
| `?ac-bonus-fns` | vec of fns | **Additive** on top of max | Robe of Archmagi, Bracers of Defense |

---

## Bug 1: Natural + Unarmored Stacking

**Status:** Confirmed by tests. 10 failing assertions across 4 test functions.

**Location:** `template_base.cljc:38-41,60`

**D&D 5e rule (PHB p.14):** When you have multiple AC formulas, you choose
one. They don't stack.

**What the code does:**

```clojure
;; template_base.cljc:38-41
?base-armor-class (+ 10 (?ability-bonuses ::char5e/dex)
                     (if (> ?unarmored-ac-bonus ?natural-ac-bonus)
                       0
                       ?natural-ac-bonus)    ;; <-- adds natural
                     ?magical-ac-bonus)

;; template_base.cljc:60
?unarmored-armor-class (+ ?base-armor-class
                          ?unarmored-ac-bonus  ;; <-- adds unarmored ON TOP
                          ?ac-bonus)
```

When `?natural-ac-bonus >= ?unarmored-ac-bonus`, BOTH are added:
`10 + DEX + natural + unarmored`. The conditional only suppresses
natural when unarmored is strictly greater — it never suppresses
unarmored when natural wins.

**Example:** Barbarian 1 / Sorcerer(Draconic) 1, DEX 14(+2), CON 14(+2):
- Draconic sets `?natural-ac-bonus` = 3
- Barbarian sets `?unarmored-ac-bonus` = 2 (CON)
- Since 3 > 2, natural is included: base = 10 + 2 + 3 = 15
- Then unarmored adds CON: 15 + 2 = **17**
- RAW: max(Barbarian 10+2+2=14, Draconic 13+2=15) = **15**

**Fix strategy:** Replace the conditional-add with `max()`:

```clojure
?base-armor-class (+ 10 (?ability-bonuses ::char5e/dex)
                     (max ?unarmored-ac-bonus ?natural-ac-bonus)
                     ?magical-ac-bonus)
```

And remove the redundant `?unarmored-ac-bonus` add from
`?unarmored-armor-class`.

---

## Bug 2: Robe of Archmagi Additive Stacking

**Status:** Confirmed by tests. 2 failing assertions.

**Location:** `magic_items.cljc:2260-2264` + `template_base.cljc:82-85`

The Robe of Archmagi (and similar items) registers a +5 bonus via
`?ac-bonus-fns`. The final AC computation at `template_base.cljc:78-85`
**adds** all `?ac-bonus-fns` results on top of the max AC:

```clojure
;; template_base.cljc:78-85
?armor-class-with-armor (fn [armor & [shield]]
                          (let [max-ac (apply max
                                              (?armor-class-with-armor-base ...)
                                              (map #(% armor shield) ?ac-fns))
                                bonuses (map #(% armor shield) ?ac-bonus-fns)]
                            (apply + max-ac bonuses)))  ;; <-- additive
```

The Robe should be an **alternative AC formula** (via `?ac-fns`, compared
with `max`), not an additive bonus (via `?ac-bonus-fns`).

**Example:** Robe Bearer + Sorcerer(Draconic), DEX 14(+2):
- Robe's bonus-fn returns +5 when unarmored
- Draconic: base AC = 15 (13+DEX)
- Code: 15 + 5 = **20**
- RAW: max(Robe 15+2=17, Draconic 13+2=15) = **17**

**Fix strategy:** Move the Robe from `?ac-bonus-fns` to `?ac-fns` so it
participates in `max()` instead of being added.

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

**Key insight for future agents:** Don't assume modifier application order
matches template/option source order. The build system uses topological
dependency sorting. Trace the actual order via `entity.cljc:apply-options`.

---

## Test Coverage Summary

| Test | What it verifies | Expected result |
|------|-----------------|-----------------|
| `barbarian-unarmored-defense` | Single-class AC = 10+DEX+CON | Pass |
| `monk-unarmored-defense` | Single-class AC = 10+DEX+WIS | Pass |
| `draconic-resilience-natural-ac` | Single-class AC = 13+DEX | Pass |
| `multiclass-ac-natural-and-unarmored-should-not-stack` | Bug 1: barb/sorc stacking | Fail (17 vs 15) |
| `barbarian-shield-ac` | Shield + barbarian CON | Pass |
| `monk-shield-ac` | Shield drops monk WIS | Pass |
| `natural-armor-barbarian-stacking` | Bug 1: lizardfolk-ac + barb | Fail (3 assertions) |
| `shell-armor-monk-stacking` | Bug 1: tortle-ac + monk | Fail (21 vs 17) |
| `full-stack-race-multiclass-shield` | Bug 1: race + barb/sorc + shield | Fail (3 assertions) |
| `robe-archmagi-draconic-stacking` | Bug 2: Robe additive stacking | Fail (20 vs 17) |
| `natural-armor-barbarian-high-con-stale-closure` | Non-bug: closure is not stale | Pass |

---

*Last updated: February 2026*
