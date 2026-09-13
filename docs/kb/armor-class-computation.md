# Armor Class computation

> **⚠️ HISTORICAL (2026-06-19).** This describes the engine **before** the 2026-09 refactor — the seven
> scalar channels, the natural-vs-unarmored tie-break, the shield inside the base. All of that is gone.
> **The current model is `armor-class-refactor.md`** ("Current state — read this first"). Keep this
> file for the characterization numbers it records and the reasoning that led to the refactor.

How AC is calculated, the channels a feature can plug into, and what that means for
custom AC (natural armor, unarmored defense). Markers: **VERIFIED** = read from code,
file:line cited. **DESIGN** = proposal, not built.

## Verified behavior — TEST-BACKED (`ac_characterization_test.clj`, JVM)
The model below is pinned by characterization tests (built characters + direct `?armor-class-with-armor`
invocation), so the numbers are the real ones, not prose:
- **Unarmored:** fighter 10+Dex(2)=12; monk 10+Dex+Wis=15; barbarian 10+Dex+Con=15.
- **Armored (fighter, Dex 14):** leather (light) 13 = 11+full Dex; scale mail (medium) 16 = 14+min(2,Dex);
  chain mail (heavy) 16 = 16+0 Dex; chain mail + shield 18; no-armor + shield 14.
- **Two Unarmored Defenses (monk + barbarian):** the `(first ?unarmored-defense)` gate applies **one**
  ability adder (AC 15), it does **not** stack both (which would be 18).
- **Natural-AC `:props` duplication:** `:lizardfolk-ac` and `:tortle-ac` emit the *same shape* — a
  `?natural-ac-bonus` + a bespoke `?armor-class-with-armor` override (one parameterized `:natural-ac`
  handler should replace both); they feed the same `?natural-ac-bonus` channel the sorcerer Draconic
  Bloodline uses (`classes.cljc:2270`).

**Two findings surfaced by the test (verified):**
1. **Dex cap is by armor TYPE, not the armor's `:max-dex-mod` field.** `?armor-dex-bonus` keys off
   `(:type armor)` (light/medium/heavy → full/min-2/0); the `:max-dex-mod` value present on each armor in
   `armor.cljc` is **ignored** by the AC fn. Consistent for SRD armor (medium=2, heavy=0), but a homebrew
   armor with a non-standard `:max-dex-mod` would not be honored.
2. **The armored branch relies on cljs nil-arithmetic.** It does `(+ … (::mi5e/magical-ac-bonus armor) …)`;
   non-magical armor lacks that key → `nil`. cljs treats `nil` as 0 in `+` (so production is fine); under
   the JVM that NPEs, so the test supplies an explicit `magical-ac-bonus 0`. A JVM-ism to be aware of
   (verification-discipline lesson 5).

**The "up" trace (how the displayed AC picks armor) — VERIFIED:** `?armor-class-with-armor` is the logic;
the cljs sub `::current-armor-class` (`subs.cljs:769`) calls it with the **worn** armor/shield, else falls
back to `::best-armor-combo` (`:760`) which takes `(max-key :ac …)` over every owned armor×shield combo
(incl. nil). So the cljc fn is the whole computation; the cljs layer only chooses which armor to feed it.

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
