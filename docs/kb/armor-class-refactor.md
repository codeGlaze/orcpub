# Armor Class refactor — design, decisions, and app-vs-design status

Companion to `armor-class-computation.md` (which documents the CURRENT shipped model). This
doc records the REFACTOR: the reconciler being proved out in the test sandbox, why, and —
critically — **what is shipped vs. what is only proven in tests**. Markers: **VERIFIED** =
backed by a running test (file cited); **DESIGN** = proposed, not built; **SHIPPED** = in the
live app today; **SANDBOX** = proven in `ac_experiments_test` / `ac_reconciliation_test` but
NOT yet wired into the app.

## The problem (VERIFIED)
For one operation ("what is my AC"), the shipped stack has THREE parallel mechanisms — 7 scalar
channels, a dead `?ac-fns` list (no constructor/writers), and two external `?armor-class-with-armor`
overrides (lizardfolk/tortle) — plus a pairwise `if` tie-break that reconciles only 2 sources.
Modeling base-setting AC as ADDITIVE scalars is a bug generator:
- **A2 (real, fixed):** natural-armor + unarmored-defense STACKED (Draconic Sorcerer / Barbarian
  = 18, RAW 15). Root: `?base-armor-class` zeroed natural when unarmored won but never the reverse.
  Fixed neutrally on integration (symmetric tie-break); `ac_reconciliation_test` A2 pins 15.
- **A3 (NOT a bug — a fixture artifact):** two natural sources appeared to stack to 18, but only
  because the test used the cum-sum constructor `mod5e/natural-ac-bonus`. ALL real content uses
  `mod/modifier` (a SET; `es/modifier` replaces). With real content, two sources last-win (15).
  No integration bug. Lesson recorded in `verification-discipline.md`: verify the MECHANISM, not
  just the output number.

## The model (SANDBOX — `orcpub.dnd.e5.armor-class/reconcile-ac`, VERIFIED by tests)
An AC is the **best applicable METHOD** ('your AC = ...') plus the **sum of applicable BONUSES** ('+N'):
- `method = (fn [armor shield] number)` — returns 0 when it does not apply (unarmored method while
  armored; shield-forbidding method while a shield is held). Competing methods reconcile by **MAX**
  (take the better, never stack). A floor (Barkskin) is a constant method — max gives 'at least N' free.
- `bonus = (fn [armor shield] number)` — shield, magic-item AC, Ring/Cloak, Defense style, Mariner.
  **Summed onto the winning method**, so bonuses reach whatever method wins (the shipped engine
  buries them in the base formula, so a method that beats the base drops them — `ac_reconciliation_test` B2).
`reconcile-ac` is two lines. It makes A2/A3 stacking structurally impossible (max, not sum).

## Method contract (SANDBOX, VERIFIED)
- A method opts OUT of a context by returning 0 (`reconcile-unarmored-method-excludes-when-armored`).
- Per-method shield permission IS self-exclusion: Monk returns 0 with a shield → base wins + shield
  = loses Wis per RAW; Barbarian keeps its value + shield (`reconcile-shield-permission-is-self-exclusion`).
- `:item?` flag: whether the method reads the equipped armor's FIELDS. Owned by the CONSTRUCTOR,
  never hand-set — homebrew methods are declarative (`{:base :abilities}`, no armor-field access),
  so they are always item-independent; only the engine's worn-armor method is item-dependent.

## Outer reconciler — bucketed vs naive (SANDBOX, VERIFIED — `ac_experiments_test`)
DECISION: **bucketed wins.**
- **Naive** — evaluate every method per (armor,shield) combo. Simple, can't be fooled. Cost ~ A·M.
- **Bucketed** — item-independent methods evaluated once per (armored?, shield) state; item-dependent
  per owned armor. Cost ~ M + A. Measured: 8 armors / 22 methods → naive **396** method-evals vs
  bucketed **102**; the gap widens with armor count.
- Tradeoff (measured): bucketed trusts `:item?` — a method that reads armor fields but is flagged
  item-independent crashes it (naive stays correct). **But that mis-flag is unreachable in the real
  design**: the declarative vocabulary gives authors no way to read armor fields, so their methods
  are always item-independent, flagged by the constructor. Guards: constructor owns the flag; a
  dev-time validation probes each item-independent method with two armors.

## Custom armor — a SEPARATE, ORTHOGONAL axis
Custom armor is DATA read by the ONE item-dependent method (the worn-armor formula); the reconciler
never sees armor internals (custom-armor cases pass through both candidates unchanged, VERIFIED).
Status, precisely:
- **SHIPPED:** plain custom armor (`:base-ac`, `:type`) and magical armor (`::magical-ac-bonus`) work
  today — armor is just a data entry.
- **NOT SHIPPED (known gap):** non-standard Dex behavior. The shipped worn-armor method hardcodes the
  Dex cap by `:type` and **ignores the armor's `:max-dex-mod` field** (`armor-class-computation.md`).
  So "heavy plate that still allows a Dex bonus" does NOT work in the app today.
- **SANDBOX (DESIGN, proven):** teaching the worn-armor method to honor the armor's own cap (default
  to type) makes it work — `{:type :heavy :max-dex 2}` → base + 2. Proven in `ac_experiments_test`,
  NOT yet in the app.
- **The ceiling (honest):** the worn-armor method reads an ENUMERATED set of armor→AC properties
  (base-ac, dex-cap, magic; add-abilities is a candidate). A novel property ("armor that adds Wisdom
  to AC") is expressible either as ONE more enumerated armor field the worn-armor method reads, or as
  a conditional bonus tied to the equipped armor — NOT automatic. Same growable-enumeration ceiling
  as the rest of the vocabulary: authors compose enumerated properties, they don't script.
- **Non-AC armor properties** (mithral's stealth/Str-requirement suppression) are orthogonal — handled
  by the stealth/prof logic, not the reconciler.

## Backward-compat gotcha for wiring (DESIGN)
Switching the worn-armor method from type-hardcoded caps to reading `:max-dex-mod` MUST reproduce SRD
armor AC. Verify SRD `:max-dex-mod` values match the type defaults (medium=2, heavy=0) in `armor.cljc`
before wiring; the characterization net (`ac_characterization_test`, `ac_reconciliation_test` A1) guards it.

## Status summary
- SHIPPED: the A2 fix (integration + propagated); plain/magical custom armor.
- SANDBOX (proven, not wired): `reconcile-ac`, the method contract, bucketed outer reconciler,
  custom-armor via `:max-dex`.
- NOT DONE: wiring `reconcile-ac` into `template_base`; migrating scalar channels → methods;
  the `ac-fn`/`ac-bonus-fn` constructors; ability-substitution; evicting lizardfolk/tortle to homebrew.
