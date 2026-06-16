# Armor Class computation

How AC is calculated, the channels a feature can plug into, and what that means for
custom AC (natural armor, unarmored defense). Markers: **VERIFIED** = read from code,
file:line cited. **DESIGN** = proposal, not built.

## The model — VERIFIED (`template_base.cljc:35-88`)
AC is computed per equipped armor/shield combination, layered:

```
?armor-class            = 10 + Dex                         ; the simple displayed unarmored value
?base-armor-class       = 10 + Dex
                          + (if (> ?unarmored-ac-bonus ?natural-ac-bonus) 0 ?natural-ac-bonus)
                          + ?magical-ac-bonus
?armor-dex-bonus (fn)   = light: full Dex, medium: min(?max-medium-armor-bonus, Dex), heavy: 0
?shield-ac-bonus (fn)   = 2 + shield's magical bonus
?unarmored-armor-class  = ?base-armor-class + ?unarmored-ac-bonus + ?ac-bonus
?armor-class-with-armor-base (fn [armor shield]):
    no armor, no shield -> ?unarmored-armor-class
    no armor, shield    -> ?unarmored-with-shield-armor-class
    armor               -> shield + armor-dex-bonus + ?armored-ac-bonus + armor base-ac
                            + magical + ?ac-bonus + ?magical-ac-bonus
?armor-class-with-armor (fn [armor shield]):
    (apply max (?armor-class-with-armor-base armor shield)
               (map #(% armor shield) ?ac-fns))            ; MAX over base and alternatives
    + (sum of (map #(% armor shield) ?ac-bonus-fns))       ; then ADD bonuses
```

## The channels a feature can use — VERIFIED
- **`?ac-fns`** — alternative full AC formulas. The final AC takes the **max** of the base
  formula and every `?ac-fns` entry. This is where a from-scratch custom AC goes
  ("13 + Dex", "10 + Con").
- **`?ac-bonus-fns` / `?ac-bonus` / `?armored-ac-bonus`** — additive bonuses applied **on
  top** of the max (Defense's +1 while armored, Mariner's conditional +1).
- **scalar accumulators** `?unarmored-ac-bonus`, `?natural-ac-bonus`, `?magical-ac-bonus`,
  `?unarmored-with-shield-ac-bonus` — folded into the base formula. With-shield is a
  separate accumulator from without-shield.

Picking the wrong channel breaks the math: a replacement put in a bonus channel
double-counts; a bonus put in the max channel gets dropped.

## Where each kind of custom AC goes — VERIFIED mechanism
- **Natural armor** (Lizardfolk 13+Dex, maxes against worn armor): override
  `?armor-class-with-armor` to max its formula against the normal one. Pattern in
  `template.cljc:284`. Sets `?natural-ac-bonus`.
- **Unarmored defense** (10 + Dex + ability, no armor): add the second ability via
  `?unarmored-ac-bonus`, gated on being the active unarmored source. Barbarian
  (`classes.cljc:70`, adds Con), Monk (`:1262`, adds Wis).
- **Flat bonus** (+X while armored / conditionally): `?armored-ac-bonus` or `?ac-bonus-fns`.
- **Static AC** ("your AC is N"): an `?ac-fns` entry returning a constant, maxed in.

## Known friction — VERIFIED facts (the "issues" are analysis)
- **Pairwise tie-break is hardcoded.** `?base-armor-class` contains
  `(if (> ?unarmored-ac-bonus ?natural-ac-bonus) 0 ?natural-ac-bonus)` — it reconciles
  exactly two named sources. A third (custom) source isn't included automatically; that
  expression would need extending. The `?ac-fns` max channel generalizes; this scalar
  comparison does not.
- **Per-class ability is hardcoded, and a config for it is half-wired.** Monk declares
  `:unarmored-abilities [::char5e/wis]` (`classes.cljc:1241`) but the AC modifier hardcodes
  `(?ability-bonuses ::char5e/wis)` (`:1263`) — the config exists and isn't read.
- **Multiclass: first source wins.** The ability bonus is gated on
  `(= :class (first ?unarmored-defense))`, so Barbarian + Monk unarmored don't stack. A
  custom unarmored defense must participate in that ordering.
- **Reconciliation is max-based**, so a true override (force a specific AC even if lower)
  isn't expressible. Most 5e AC features are "use the better", which max handles.

## Design proposal — DESIGN, not built
A creator should not see the channels. Map a small set of **categories** to channels:
- Natural Armor (base number + optional ability, maxes against worn armor, shield allowed?)
- Unarmored Defense (10 + primary ability + optional second ability, no armor, shield allowed?)
- Flat AC bonus (value, optional condition)
- Set AC (constant)

Parameters that cover the space: base number (default 10), primary ability (default Dex —
changing it is "replace Dex"), optional second ability, works-in-armor?, shield allowed?,
alternative-vs-bonus. The compiler routes the parameterized result to the right channel.
Real 5e AC features cluster into these ~4 shapes, so bounded templates are likely
sufficient and avoid the edge cases a fully dynamic "selections reshuffle the stack"
builder would hit.

Structural improvement worth considering: replace the hardcoded pairwise tie-break with a
uniform list of AC contributions, each tagged alternative (max) or bonus (add), reconciled
generally. That makes a new AC source data-driven instead of an edit to `?base-armor-class`.
Low-risk first step: wire the AC modifier to read `:unarmored-abilities` instead of
hardcoding the ability — it serves Monk and custom unarmored defense at once. Caveat: AC is
load-bearing and many features touch it, so any reconciliation refactor needs regression
coverage first.
