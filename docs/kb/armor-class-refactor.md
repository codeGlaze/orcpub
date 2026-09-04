# Armor Class refactor — status, design, and corrections

Companion to `armor-class-computation.md` (which documents the AC model the app actually runs).
This doc covers the **replacement** reconciler: what exists, what doesn't, and what we got wrong
along the way.

**Only two status words are used, and they mean exactly this:**
- **IN THE APP** — live code the running application executes.
- **NOT WIRED** — code that exists in the repo and has passing tests, but which nothing in `src/`
  calls. It has no effect on the application.

Verified 2026-09-04: `orcpub.dnd.e5.armor-class` is required by `ac_reconciliation_test` and
`ac_experiments_test` only. No file in `src/` requires it. Everything below marked NOT WIRED is
therefore invisible to users.

## Why replace the current AC code

The live AC code (`template_base.cljc:35-96`) computes AC through several overlapping mechanisms:
seven scalar accumulators, a `?ac-fns` list, and two external overrides in `options.cljc`
(`:lizardfolk-ac`, `:tortle-ac`) that replace `?armor-class-with-armor` wholesale. Two of those
mechanisms interact through a pairwise `if` that reconciles only two sources.

That produced a real bug (below). It is the motivation for the replacement, which reconciles
*every* AC calculation the same way instead of layering special cases.

One caveat on an earlier draft of this doc: it claimed the live engine "drops bonuses when a
formula beats the base". That is true of the code as written, but **unreachable** — `?ac-fns` has
no writers anywhere in `src/`, so no formula can beat the base today. It is a latent hazard, not a
live defect.

## The replacement model (NOT WIRED)

`orcpub.dnd.e5.armor-class`. AC is built from two kinds of thing:

- **formula** — a whole "your AC = ..." calculation (worn armor, unarmored defense, natural armor,
  a Barkskin-style floor, homebrew). Mutually exclusive: the best one wins (`max`).
- **bonus** — a flat +N added on top of whichever formula won (shield, Ring of Protection, Defense
  fighting style). Summed.

`AC = best formula + sum of bonuses`. Both are `(fn [armor shield] -> number)`; one that doesn't
apply returns 0.

Formulas are passed in two groups because they behave differently as armor changes:
`:armor-formula` (AC from the worn armor — the only value that depends on *which* armor) and
`:other-formulas` (everything else, which must not read armor fields). An earlier design tagged
each formula with an `:item?` boolean instead; that flag no longer exists.

Behaviours covered by tests in `ac_reconciliation_test` SECTION 3: best formula wins; a bonus still
applies when a formula beats the base; a floor is just a constant formula; a formula opts out by
returning 0; per-formula shield permission is that same opt-out (Monk loses Wis with a shield,
Barbarian doesn't).

## Choosing the outer reconciler (NOT WIRED)

`best-ac` finds the highest AC across owned armor and shields. Two implementations were compared in
`ac_experiments_test` against one shared spec:

- **naive** — evaluate every formula for every (armor, shield) pair.
- **chosen** — evaluate `:other-formulas` once per (armor-worn?, shield) pair, and `:armor-formula`
  per owned armor.

Both produce identical results on every spec case. Measured on 8 armors and 22 formulas: naive
performs 396 formula evaluations, the chosen one 102. The gap grows with the number of owned armor.

The tradeoff, also measured: the chosen implementation evaluates `:other-formulas` with a
placeholder (`::worn`) standing in for "some armor". A formula that reads armor fields but is
placed in `:other-formulas` will therefore misbehave — in the test it throws. The naive one cannot
be fooled this way. Nothing currently enforces the rule; it is a documented contract on the
namespace, not a checked one.

## Custom armor — a separate axis

Armor is data read by the armor formula; the reconciler never inspects armor itself.

- **IN THE APP:** custom armor with `:base-ac` and `:type`, and magical armor via
  `::magical-ac-bonus`, already work — a homebrew armor is just a data entry.
- **IN THE APP, but limited:** the Dex cap is chosen by `:type` (`?armor-dex-bonus`,
  `template_base.cljc:52-57`). The `:max-dex-mod` field that every medium and heavy armor carries
  is **never read**. So homebrew armor with a non-standard cap — "heavy armor that still allows a
  Dex bonus" — does not work today.
- **NOT WIRED:** the sandbox armor formula honours an armor's own cap, which makes that work.

### Trap: reading `:max-dex-mod` naively breaks Medium Armor Master

Verified in `armor.cljc`: all 6 medium armors carry `:max-dex-mod 2`, all 4 heavy carry
`:max-dex-mod 0`, and light armor carries none. So the values do match the type defaults, and an
earlier "verify these before wiring" note can be closed.

But the cap is not just a property of the armor. Medium Armor Master raises it by setting
`?max-medium-armor-bonus` to 3 (`options.cljc:1461`, used at `:1684`, `:1933`, and via the
`:medium-armor-max-dex-3` homebrew key at `:3599`). The armor itself still says 2.

So an implementation that simply prefers the armor's `:max-dex-mod` over the type default would
read 2 and **silently disable the feat**. The sandbox's `armor-dex` does exactly that; it escapes
notice only because no test armor carries the field alongside the feat.

The effective cap has to combine both — the armor's own limit and any effect that raises it (e.g.
`max` of the two) — not simply pick one. This must be settled, and covered by a test involving
Medium Armor Master, before the armor formula is wired in.

### Where custom armor stops

The armor formula reads a fixed set of armor properties. A new kind of armor→AC interaction
("armor that adds your Wisdom modifier") is expressible either as one more property the armor
formula knows about, or as a bonus conditioned on the equipped armor — it is not automatic. Armor
properties that don't touch AC (mithral suppressing stealth disadvantage and Strength requirements)
are handled elsewhere entirely.

## Corrections

- **Real bug, fixed:** natural armor and unarmored defense stacked (a Draconic Sorcerer / Barbarian
  came out 18 where the rules give 15). `?base-armor-class` dropped natural armor when unarmored
  defense won the tie-break, but never the reverse. Fixed with a symmetric tie-break, on
  `integration` as `f9fb327f`. Pinned at 15 in `ac_reconciliation_test`.
- **Not a bug — a fixture artifact:** two natural-armor sources appeared to stack. The test's
  synthetic classes used the cum-sum constructor `mod5e/natural-ac-bonus`; all real content uses
  `mod/modifier`, which replaces rather than accumulates. With the fixture corrected the effect
  disappeared. Nothing to fix. Lesson recorded in `verification-discipline.md`.

## Not built

Wiring `armor-class` into `template_base`; migrating the scalar channels to formulas; constructors
for registering homebrew formulas and bonuses; letting a character use an ability other than Dex
for AC; moving `:lizardfolk-ac`/`:tortle-ac` out of the engine. No enforcement of the
`:other-formulas` contract, and no resolution of the Medium Armor Master interaction above.
