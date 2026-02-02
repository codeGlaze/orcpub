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

**Why a simple `max()` in `?base-armor-class` does NOT work:**

The obvious fix — `(max ?unarmored-ac-bonus ?natural-ac-bonus)` in
`?base-armor-class` — breaks the shield path. `?base-armor-class` feeds
both `?unarmored-armor-class` and `?unarmored-with-shield-armor-class`.
Monk sets `?unarmored-ac-bonus` = WIS but `?unarmored-with-shield-ac-bonus`
= 0 (PHB p.78: no shield). Baking `max(WIS, natural)` into the shared base
leaks monk WIS into the shield formula. A monk with DEX +2, WIS +3 would
get shield AC = 10+2+3+2 = **17** instead of the correct 10+2+0+2 = **14**.

**Fix strategy:** See "Recommended Fix Architecture" below — both bugs share
the same root cause and the same solution.

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

---

## Recommended Fix Architecture

Bugs 1 and 2 share the same root cause: the pipeline tries to combine
AC formulas into a single chain of intermediate values (`?base-armor-class`
→ `?unarmored-armor-class`), when D&D 5e says you pick the best formula.

The pipeline already has the right mechanism — `?ac-fns` at
`template_base.cljc:87` — but nothing populates it. There is no `ac-fn`
helper macro; only `ac-bonus-fn` exists (`modifiers.cljc:567-568`), which
pushes to the **additive** `?ac-bonus-fns`.

**The fix: make each AC source a self-contained formula in `?ac-fns`.**

Each formula receives `(armor, shield)` and returns its AC independently.
The pipeline picks the highest via `max`. No shared intermediate, no
stacking, no ordering issues.

```clojure
;; 1. Create ac-fn helper (doesn't exist yet)
(defmacro ac-fn [f]
  `(mod5e/vec-mod ?ac-fns ~f))

;; 2. Barbarian unarmored defense → register formula
(ac-fn (fn [armor shield]
         (when (nil? armor)
           (+ 10 DEX CON (if shield 2 0)))))

;; 3. Natural armor (generic, takes base-ac param)
(defn natural-armor-modifiers [base-ac]
  [(ac-fn (fn [armor shield]
            (when (nil? armor)
              (+ base-ac DEX (if shield 2 0)))))])
;; lizardfolk: (natural-armor-modifiers 13)
;; tortle:     flat 17 + shield, no DEX

;; 4. Robe of Archmagi → move from ac-bonus-fn to ac-fn
(ac-fn (fn [armor shield]
         (when (nil? armor)
           (+ 15 DEX (if shield 2 0)))))
```

**Why this works:**
- `max` is commutative — modifier ordering is irrelevant
- Each formula handles its own shield logic — no shared base leaking WIS
- Aligns with D&D 5e RAW: "choose which one to use"
- Eliminates the `?base-armor-class` double-duty problem
- Makes homebrew natural armor trivial: `(natural-armor-modifiers N)`

**What `?ac-bonus-fns` is still for:** True additive bonuses that stack on
top of ANY formula — Shield of Faith (+2), Ring of Protection (+1), etc.
These are correct as additive. The bug is items that provide an
**alternative formula** being registered as additive bonuses.

### Same-key modifier ordering (architectural note)

When two modifiers target the same `?`-key (e.g., both override
`?armor-class-with-armor`), the winner depends on:

1. `flatten-options` iterates the entity's `::options` map (`entity.cljc:297`)
2. `collect-modifiers-2` preserves that order (`entity.cljc:574`)
3. `order-modifiers` stable-sorts by topological position (`entity.cljc:417`)
4. Same-key modifiers keep their collection order (stable sort)
5. `apply-modifiers` reduce makes last-wins (`modifiers.cljc:101`)

For small option maps (≤8 keys), Clojure uses array-maps (insertion order).
Larger maps use hash-maps (hash-dependent order). Typical characters stay
in array-map territory, so the order is deterministic but implicit.

The `?ac-fns` approach eliminates this concern entirely — formulas are
collected into a vector and compared via `max`, so order doesn't matter.

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
| `natural-armor-barbarian-high-con-override-closure` | Non-bug: closure is not stale | Pass |

---

*Last updated: February 2026*
