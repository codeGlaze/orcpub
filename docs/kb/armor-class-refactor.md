# Armor Class refactor — status, design, and corrections

Companion to `armor-class-computation.md` (which documents the AC model the app runs). This doc
covers the refactor: what has landed, what the design is, and what we got wrong on the way.

**Status words, used strictly:**
- **IN THE APP** — live code the running application executes.
- **NOT WIRED** — exists in the repo with passing tests, but nothing in `src/` calls it.

## Current state — read this first

**The model:** `AC = max(worn-armor value, every registered formula) + sum(every registered bonus)`.
Formulas compete, bonuses stack onto the winner. `?ac-fns` holds the formulas, `?ac-bonus-fns` the
bonuses. Both are upstream (PR #156); this refactor gave them constructors and migrated content onto
them.

**Authoring, one shape for everything** (reaches races, subraces, classes, subclasses, feats,
ancestries via `:props` → `plugin-modifiers` → `make-feat-modifiers`):

```clojure
{:ac       {:ac 13 :abilities [:dex] :armor? false :shield? false}}  ; a competing calculation
{:ac-bonus {:ac-bonus 1 :armor? true}}                               ; a flat bonus on the winner
{:armor-gives-no-ac true}                                            ; worn armor stops counting
```
`:armor?`/`:shield?` are three-state: `false` = only when NOT equipped, `true` = only when equipped,
absent = either way.

**Verification:** the migration parity sweep (`ac_reconciliation_test`) compares every old mechanism
against its authored replacement across 7 equipment states. **It is pinned at 0 and must stay there.**

**Overrides:** the mug icon in the character builder waives *selection* rules per selection, never
computed values — see `homebrew-override.md`. Restrictions built as selection constraints are
player-overridable for free; ones built as computations are not.

**Known limits:** `:armor-gives-no-ac` is not "you can't wear armor" — see the roadmap.
`?armor-ac-suppressed?` is debt, not design (below). `:tortle-ac`'s ceiling behaviour is preserved
but only as an AC consequence.

## The channel trim — DONE. 18 attributes → 10

The AC surface in `template_base.cljc` was 18 `?`-attributes. It is now **10**, and the arithmetic
has moved out to `orcpub.dnd.e5.armor-class`.

**What is left, and why each one earns its place:**

| kept | why |
|---|---|
| `?ac-fns`, `?ac-bonus-fns` | the two channels the design is built on |
| `?armor-dex-bonus`, `?shield-ac-bonus`, `?max-medium-armor-bonus` | parameters, not channels — inputs the engine reads |
| `?armor-class-with-armor`, `?armor-class` | the outputs; `?armor-class` is the bare unarmored display value read by the sheet and the PDF |
| `?armor-class-with-armor-base` | the worn-armor value, split out so `?armor-ac-suppressed?` can drop it |
| `?armor-ac-suppressed?` | the `:armor-gives-no-ac` flag |
| `?natural-ac-bonus` | **deprecated shim**, see below |

**Retired:** `?ac-bonus` (zero writers, ever) · `?unarmored-ac-bonus` ·
`?unarmored-with-shield-ac-bonus` · `?armored-ac-bonus` · `?magical-ac-bonus` ·
`?unarmored-defense` · `?base-armor-class` · `?unarmored-armor-class` ·
`?unarmored-with-shield-armor-class` — plus the symmetric natural-vs-unarmored tie-break, both
halves.

**A claim in this doc that was wrong:** that the target shape would delete `?armor-ac-suppressed?`.
It does not. Making worn armor an ordinary calculation still leaves the question of how to *not*
register it for a given character, and a flag is how the entity spec answers that. The flag stays;
the earlier note said otherwise.

**Target was 7, delivered 10.** The three over: the deprecated shim (deliberate), the flag (above),
and `?armor-class-with-armor-base` (the seam the flag acts on).

### How the built-ins moved

| feature | was | now |
|---|---|---|
| Barbarian | a `?unarmored-defense` tag + **two** gated cum-sum scalars | one `ac-formula`: `(if armor 0 (+ 10 dex con))` |
| Monk | the tag + one gated scalar, "no shield" implied by not writing the with-shield channel | one `ac-formula` stating `(or armor shield)` outright |
| Draconic Bloodline | `(mod/modifier ?natural-ac-bonus 3)` | one `ac-formula`: `(if armor 0 (+ 13 dex))` |
| Defense fighting style | owned `?armored-ac-bonus` | a predicated `?ac-bonus-fns` entry |
| Bracers of Defense | wrote the unarmored channel, "no shield" by omission | a predicated `?ac-bonus-fns` entry |
| Ring/Cloak of Protection | `?magical-ac-bonus` scalar inside the base | a `?ac-bonus-fns` entry |

The `?unarmored-defense` tag existed **only** to arbitrate between Barbarian and Monk on a
multiclass. `?ac-fns` already takes the best calculation by `max`, so there was nothing left to
arbitrate — and a monk/barbarian now gets the *better* of the two rather than whichever the vector
happened to list first.

### The one shim: `?natural-ac-bonus`

Kept declared and adapted into `?ac-fns` by a seeded entry. Nothing in this repo writes it any
more. It stays because `bracers_ac_test` on `integration` writes it, and that branch has no
`ac-formula` to write instead — deleting it here would break that test on every merge. The shim
costs one formula (D9).

## LANDED: the AC engine moved to `orcpub.dnd.e5.armor-class`

Reverses the earlier D34 call to delete that namespace. `?`-attributes are entity-spec macros valid
only inside `es/make-entity`, so the **declarations** stay in `template_base`; the **arithmetic**
moved. `template_base`'s AC block is now 38 lines of delegation.

The namespace holds `dex-cap`, `armor-dex-bonus`, `shield-bonus`, `worn-armor-ac` and `reconcile` —
all plain functions, which is the real payoff: `armor_class_test` checks the engine directly with
no entity and no character template (25 assertions), leaving `ac_reconciliation_test` to prove the
same rules survive content and the entity spec.

### DECIDED: bucketing rejected, `best-ac` deleted

The outer loop — `::char5e/best-armor-combo`, "best AC across every owned armor and shield" — stays
naive. Measured, not argued (`ac_outer_loop_analysis_test`):

| scenario | naive | bucketed | |
|---|---|---|---|
| typical (2 armor, 1 shield, 2 calcs) | 0.022 ms | 0.033 ms | **0.64x — slower** |
| kitted (5 armor, 2 shields, 6 calcs) | 0.067 ms | 0.099 ms | **0.68x — slower** |
| adversarial (8 armor, 2 shields, 21 calcs) | 0.125 ms | 0.105 ms | 1.19x |
| absurd (20 armor, 3 shields, 40 calcs) | 0.500 ms | 0.276 ms | 1.81x |
| absurd ×2 (40 armor, 4 shields, 80 calcs) | 1.901 ms | 0.632 ms | 3.01x |

(JVM; the app runs this in JS, so the ratio transfers and the absolute numbers do not. The last row
is recorded here rather than run every suite — it dominated the runtime.)

Bucketing scales genuinely well, but **the crossover is above any realistic character** and it is
slower below it: building the state map costs more than the evaluations it saves until roughly 8
armors and 20 calculations. And this runs inside a memoized `reg-sub` that only recomputes when
equipment or AC state changes, so even the adversarial case saves ~0.02 ms per recompute.

Three things settled it beyond the timings:

- **`best-ac` could not serve the call site anyway.** It returns a number; `views.cljs:3537,3550`
  read `(-> best-armor-combo :armor :key)` and `:shield :key` to preselect the equipment dropdowns.
  Adopting it meant rewriting it.
- **The footgun is real and silent-ish.** A calculation that reads the worn armor's fields, placed
  in the item-independent group, is evaluated against a `::worn` placeholder — a wrong number or a
  `NullPointerException`. Nothing in the shape prevents it; it is on the author. Pinned in the
  analysis test.
- **The original evidence measured the wrong thing.** The earlier "396 vs 102 formula evaluations"
  benchmark was accurate about counts, and counts turned out to be a poor proxy for cost.

`best-ac` and the old unwired `reconcile-ac` are deleted (D34), along with `ac_experiments_test`.
Coverage was carried over, not dropped: outer-loop correctness (best magic armor surfaces, item
magic never leaks to a combination not wearing that item, shields searched and reported) is now in
`ac_outer_loop_analysis_test`, and single-combo arithmetic is in `armor_class_test`.

### Two things the rejection does NOT mean

**It does not affect competing calculations.** A lizardfolk barbarian/monk carries three "your
AC = ..." calculations at once. Those resolve by `max` over `?ac-fns` inside
`?armor-class-with-armor` — the *inner* reconciler, which `best-ac` was never part of. Measured
with unequal ability mods so each calculation is identifiable (Dex +2, Con +4, Wis +1):

| | |
|---|---|
| unarmored | **16** — Barbarian's 10+2+4 beats lizardfolk's 15 and Monk's 13. Not their sum, 44 |
| + shield | **18** — Monk self-excludes, Barbarian does not, and the shield is a bonus on the winner |
| leather | **15** — lizardfolk substitutes for worse worn armor; both Unarmored Defenses stay out |
| plate | **18** — the worn armor wins outright |

**Toggling loadouts is not the bottleneck.** Equipping armor changes the character entity, so the
entity REBUILD runs before the AC search does. Measured on a 12-armor, 2-shield wardrobe — 39
combinations:

| | |
|---|---|
| character rebuild (what equipping triggers) | **16.6 ms** |
| AC search across all 39 combinations | **0.73 ms** |
| the search as a share of one toggle | **4.4%** |

Bucketing would, at best, take ~40% off that 0.73 ms — about **1.5% of a toggle**, on a wardrobe
larger than any real character's. Pinned in `loadout-toggling-is-dominated-by-the-character-rebuild`.

**A measurement mistake worth remembering:** the first run of this benchmark had no JIT warmup and
produced 0.82x / 1.96x / 1.10x — non-monotonic, which should have been an immediate tell rather
than something to report. Warmup made it monotonic.

### The Bracers fix is portable to `integration` on its own

The defect is shipped, so the fix is worth having there without waiting for the refactor. **Verified
against `origin/integration`'s own AC engine**, not reasoned about: swap in that branch's
`template_base.cljc`, apply only the constructor change, run the tests — natural armor + Bracers
goes 15 → 17, and all seven Bracers-clause assertions pass, including the no-shield delta.

It needs nothing this branch added. Integration already wires `?ac-bonus-fns` into
`?armor-class-with-armor` (upstream PR #156) and already defines the `ac-bonus-fn` macro over
`mods/vec-mod` (`modifiers.cljc:567`). The shield still living inside the base there does not
matter, because the predicate returns 0 whenever a shield is held.

The whole patch — `modifiers.cljc:561-562`, one function, no other callers, no engine change:

```clojure
;; before
(defn unarmored-ac-bonus [bonus]
  (mods/cum-sum-mod ?unarmored-ac-bonus bonus))

;; after
(defn unarmored-ac-bonus
  "A flat bonus while wearing no armor and using no shield — Bracers of Defense. A BONUS, so it
  stacks onto whichever AC calculation wins.

  It used to write ?unarmored-ac-bonus, expressing \"no shield\" by NOT also writing
  ?unarmored-with-shield-ac-bonus. That put a flat bonus in a channel that also carries Barbarian's
  and Monk's ability modifiers, which compete as calculations and are zeroed by the tie-break
  against ?natural-ac-bonus. A natural-armor character therefore lost the bonus entirely — AC 15
  instead of 17."
  [bonus]
  (mods/vec-mod ?ac-bonus-fns (fn [armor shield] (if (or armor shield) 0 bonus))))
```

Scope on integration: `mod5e/unarmored-ac-bonus` has exactly one caller there, Bracers of Defense
(`magic_items.cljc`). Barbarian and Monk write `?unarmored-ac-bonus` directly via
`mod/cum-sum-mod`, so they are untouched and the tie-break they depend on is unchanged.

### Hazard to characterize BEFORE trimming `?unarmored-ac-bonus`

That one scalar carries **two different meanings**. Barbarian and Monk write an *ability modifier*
that participates in the base as a competing calculation (`10 + Dex + Con`), subject to the
tie-break against `?natural-ac-bonus`. Bracers of Defense writes a *flat +2* that ought to be a
bonus stacking on whatever wins.

**CONFIRMED and FIXED. It is live on `origin/integration`** — a real shipped defect, not something
this branch introduced. A natural-armor(3) character is AC 15 unarmored and still 15 with Bracers of
Defense equipped: delta 0, the +2 silently dropped. RAW is 17.

Measured by running the same test against each branch's `template_base`:

| branch | natural armor 3 + Bracers | |
|---|---|---|
| **`origin/integration`** — the baseline | **15** | bug present |
| this branch, before the fix | 15 | inherited |
| this branch, after the fix | **17** | correct |
| `origin/agents/develop` | 17 | a divergent lineage without the tie-break — **not** the baseline |

Cause: the tie-break in `?unarmored-armor-class` zeroes the whole `?unarmored-ac-bonus` channel when
natural armor wins. Correct for its target case (Barbarian + Draconic stacked to 18 when RAW is 15),
wrong for flat bonuses that legitimately stack. The overloaded channel is what makes one correct fix
break the other case.

Fixed by moving `mod5e/unarmored-ac-bonus` to `?ac-bonus-fns` with both clauses stated
(`(if (or armor shield) 0 bonus)`). That constructor had exactly one caller — Bracers — because
Barbarian and Monk write the scalar directly, so the fix is narrow and is also the trim's first
retired channel. Guarded by `bracers-plus-natural-armor-the-overloaded-channel`.

## The approach, and why it changed

An earlier plan was to build a replacement reconciler (`orcpub.dnd.e5.armor-class`) and wire it in
place of `?armor-class-with-armor`. That plan was **dropped** after checking it against D17 ("find
the existing app code it would replace; if the new thing isn't thicker, extend the existing thing").

The live reconciler already is the model:

```clojure
?armor-class-with-armor  =  (max <worn-armor value> <each ?ac-fns entry>)  +  (sum <each ?ac-bonus-fns>)
```

That is exactly "best calculation wins, bonuses sum onto the winner." A second implementation of it
would be the parallel-engine mistake D30 already caught once.

**The real gap was narrower:** `?ac-fns` is read by that reconciler but had **no constructor and no
writers anywhere in `src/`**. No content could add an AC calculation at all. That is why unarmored
defense and natural armor were bolted on as scalars outside the max — reconciled by a pairwise `if`
that only handled two sources, which is where the stacking bug came from — and why `:lizardfolk-ac`
and `:tortle-ac` had to replace the whole function instead of adding a calculation.

So the refactor opens the existing channel rather than replacing the engine.

## Landed

1. **Stacking bug fixed** (IN THE APP). Natural armor and unarmored defense stacked: a character
   with both came out 18 where the rules give 15. `?base-armor-class` dropped natural armor when
   unarmored defense won the tie-break but never the reverse. Fixed with a symmetric tie-break on
   `integration` as `f9fb327f`. Reachable by an ordinary homebrew natural-armor race plus Barbarian.
2. **Characterization net** (`ac_reconciliation_test` SECTION 1/1b). Pins, from real character
   builds, the behaviours a rewrite is most likely to break:
   - Monk + shield = **14** (loses Unarmored Defense), Barbarian + shield = **17** (keeps Con)
   - scale mail at Dex 16 = **16**, and **17** with Medium Armor Master
   - custom heavy carrying `:max-dex-mod 2` = **16** — the field is ignored today
   - `:lizardfolk-ac` prop + Barbarian = **15**
3. **`mod5e/ac-formula`** (IN THE APP, `modifiers.cljc`). The missing twin of `ac-bonus-fn`; writes
   `?ac-fns`. Purely additive — nothing existing changed behaviour. Verified end-to-end through the
   live engine (`ac_reconciliation_test` SECTION 1c): a homebrew "AC = 19 unarmored" calculation
   wins the max (19), returns 0 and loses to plate when armored (18), and a flat bonus lands on it
   (20). **This is what makes homebrew AC possible at all.**

4. **`:ac` / `:ac-bonus` props compiler** (IN THE APP, `options.cljc`). Authors write the shape
   below as `:props` data and `make-feat-modifiers` compiles it to `ac-formula` / `ac-bonus-fn`
   entries, so it reaches every silo that carries `:props`. Verified through the real props path
   (`ac_reconciliation_test` SECTION 1d): a natural-armor-shaped calculation wins at 15 and yields
   to plate at 18; `:shield? false` disqualifies (14, not 15); `:abilities` sum; an absent `:armor?`
   gives a floor that lifts 13 to 16 without capping 18; bonuses apply and `:armor? true` gates them.

That third case also settled a question: the shipped engine already sums bonuses onto whichever
calculation wins. The behaviour was simply unobservable, because nothing could add a calculation.

## The authored shape

Homebrew declares AC through `:props`, the vocabulary that already reaches races, subraces, classes,
subclasses, feats and ancestries (`plugin-modifiers` → `make-feat-modifiers`). One shape, not a key
per case:

```clojure
{:ac 10 :abilities [:dex :con] :armor? false}                 ; Barbarian unarmored defense
{:ac 10 :abilities [:dex :wis] :armor? false :shield? false}  ; Monk
{:ac 13 :abilities [:dex]}                                    ; natural armor — NO :armor? tag (see below)
{:ac 17 :abilities []          :armor? false}                 ; Tortle
{:ac 16 :abilities []}                                        ; Barkskin floor — applies either way
{:ac-bonus 1 :armor? true}                                    ; Defense fighting style
{:ac-bonus 1}                                                 ; Ring of Protection
```

- `:ac` entries compete (`max`); `:ac-bonus` entries sum onto the winner.
- **`:armor?`** — `false` = only when armor is *not* worn; `true` = only when it *is*; **absent =
  either**. Three states via boolean-plus-absent.
- **`:shield?`** — the same three states as `:armor?`. `false` = **disqualified** while a shield is
  held (not "omit the shield bonus" — Monk with a shield is 14, and the omit reading gives 15);
  `true` = only *while* wielding one, which a construct-style homebrew wants; absent = either. No
  built-in content uses `true`; the vocabulary supports it because homebrew flexibility is the goal,
  and "no current content needs it" is not a reason to leave a shape inexpressible.
- **`:abilities`** sums. "Whichever is better" is two competing `:ac` entries, no extra syntax.

**The shield is not currently a bonus, and that matters.** Its +2 is added *inside*
`?armor-class-with-armor-base` (`template_base.cljc:73` and `:80`), not in `?ac-bonus-fns`. So a
calculation that beats the base **loses the shield**: a Barbarian-shaped authored calculation with a
shield gives 15 (its own 10+Dex+Con) rather than the rules' 17, because the with-shield base is only
14 and `max` picks 15. That is also why `?unarmored-with-shield-ac-bonus` exists — it is the only
way to get Con into the with-shield base branch.

Moving the shield into the bonus channel fixes this and leaves every pinned number unchanged: the
base drops to `10 + Dex` = 12, the shield adds 2 for 14 as before, and the authored calculation
becomes 15 + 2 = 17. Pinned at the current 15 in `ac_reconciliation_test` until that move lands.

Monk with a shield is already right either way: its calculation disqualifies, the plain base wins,
and the shield applies — 14.

## Parameters are not calculations

Two things modify *how the armor calculation runs* rather than offering an alternative to it: the
ability AC uses, and the Dex cap. They stay as named channels, which is what they already are.

**Trap — reading `:max-dex-mod` naively disables Medium Armor Master.** All 6 medium armors carry
`:max-dex-mod 2` and all 4 heavy carry `0`, matching the type defaults (verified in `armor.cljc`;
light carries none). But MAM raises the cap to 3 by setting `?max-medium-armor-bonus`
(`options.cljc:1461`) while the armor still says 2. An implementation preferring the armor's own
field would read 2 and silently break the feat. The effective cap must combine the type default, the
armor's own limit, and any raising effect. The characterization pins 16 → 17 with MAM so this fails
loudly if someone gets it wrong.

## Custom armor

Armor is data read by the armor calculation. Custom `:base-ac`/`:type` and magical armor work today.
Non-standard Dex behaviour does not — the cap is chosen by `:type` and the armor's `:max-dex-mod` is
never read (pinned at 16 for a custom heavy that declares 2; honouring the field makes it 18).

The armor calculation reads a fixed set of properties, so a novel armor→AC interaction ("armor that
adds your Wisdom modifier") is one more property it knows about, or a bonus conditioned on the
equipped armor — not automatic. Armor properties that don't touch AC (mithral suppressing stealth
disadvantage and Strength requirements) are handled elsewhere.

## LANDED: `:lizardfolk-ac` compiles to the universal shape — parity sweep at 0

`:lizardfolk-ac` used to write `?natural-ac-bonus 3` **and** replace `?armor-class-with-armor` with
a hand-written `max(?base-armor-class + shield, <the old fn>)`. It now emits the universal
`{:ac 13 :abilities [:dex]}` through `ac-calculation-modifiers` — the same compiler a homebrew
author's `:props` goes through. `?ac-fns` already *is* that max, and shield and character magic are
now summed onto the winner rather than baked into the replacement's hardcoded sum.

`?natural-ac-bonus 3` is still written, so the no-stacking tie-break against unarmored defense in
`?base-armor-class` (template_base.cljc:39) still sees it.

**Parity sweep: 7 → 2 → 0.** Every old mechanism now returns exactly what its authored replacement
returns, in all 4 pairs × 7 equipment states. Deprecating the old forms cannot change a saved
character's AC. That assertion is pinned at 0 and must stay there — a non-zero count is a
regression, not a number to update.

### `:tortle-ac` split into a calculation and an AC suppression

It was never a sibling of `:lizardfolk-ac`. It welded two separable things together:

- **a flat natural AC** — `{:ac 17 :abilities []}`, which any author might want
- **"worn armor gives no AC"** — the AC consequence of the tortle's shell

The old form expressed the second by replacing `?armor-class-with-armor` with `(+ 17 shield)`, so
worn armor could never beat 17. That is a **ceiling**, and `?ac-fns` is a `max` — it raises a floor,
it cannot impose one. The ceiling was a workaround for the app having no way to state the
restriction.

Expressed as what it does to AC, no ceiling is needed: **worn armor contributes nothing.** That
composes with `max` instead of fighting it, and needs no new AC concept — just
`?armor-ac-suppressed?`, which routes the armored branch of `?armor-class-with-armor-base` back to
the unarmored one. Authored as `{:armor-gives-no-ac true}`; constructor `mod5e/armor-gives-no-ac`.

**The name claims only the AC effect, deliberately.** This is not "you can't wear armor": nothing
prevents equipping armor, and everything else derived from worn armor still applies — notably
`?armor-stealth-disadvantage?` (template_base.cljc:49), so a flagged character in plate takes
plate's stealth disadvantage while getting none of its AC, and the equipment UI still shows the
plate worn. The old ceiling had exactly the same gap, so the split does not widen it; but an
earlier draft of this doc called the split "modelling the restriction honestly", which overstated
it. The real restriction is unbuilt and roadmapped.

So `:tortle-ac` is now `{:ac 17 :abilities []}` + `(armor-gives-no-ac)`, and the two halves are
independently authorable — which is the point. Someone who just wants a high flat natural AC takes
the calculation and skips the suppression; a DM who wants an armor-wearing tortle omits it. The
`?natural-ac-bonus 7` the old form wrote alongside was inert (the replacement never consulted
`?base-armor-class`), so it is gone rather than carried forward.

Verified by equivalence rather than argument — composed equals welded in all 7 equipment states,
and each half stands alone:

| | flat AC only | suppression only | composed | old `:tortle-ac` |
|---|---|---|---|---|
| unarmored | 17 | 12 | 17 | 17 |
| plate | **18** | **12** | 17 | 17 |
| plate + shield | 20 | 14 | 19 | 19 |

The `plate` row is the whole argument: with no ceiling baked in, good armor can win (18) — and the
suppression alone drops plate to 12 while leaving the shield counting, because a shield is not
armor.

**Still missing: the actual restriction, and a builder for it.** `:armor-gives-no-ac` is authorable
data reaching every silo that carries `:props`, but it is one consequence of a rule, not the rule.
Tracked in the roadmap.

## LANDED: shield and character magic moved into `?ac-bonus-fns`

`?armor-class-with-armor` is `max(base, ?ac-fns…) + sum(?ac-bonus-fns…)`. Two things that are
bonuses by nature were living inside `base` instead:

- the shield's `+2` — added in `?unarmored-with-shield-armor-class` and in the armored branch
- `?magical-ac-bonus` (character magic — Ring/Cloak of Protection) — added in `?base-armor-class`
  and again in the armored branch

Because they sat inside the term `max` chooses between, any calculation that *beat* the base
silently lost them. Both are now entries in `?ac-bonus-fns`, so they are summed onto whichever
calculation wins. Six lines added, nine removed.

Measured over the full suite (419 tests / 2224 assertions): nothing outside the AC characterization
net changed. Inside it, four pinned numbers flipped, all toward the rules:

| case | was | now | rules |
|---|---|---|---|
| authored Barbarian shape + shield | 15 | **17** | 17 |
| authored `:shield? true` construct + shield | 16 | **18** | 18 |
| authored natural armor + Ring of Protection | 15 | **16** | 16 |
| parity sweep divergences | 7 | **2** | 0 |

**What did NOT change, and why it was the real risk:** Monk + shield is still 14. Monk loses
Unarmored Defense with a shield by never writing `?unarmored-with-shield-ac-bonus` — the same
omission mechanism as Bracers of Defense. Pulling the shield out of the base leaves that untouched:
Monk's base is `10 + Dex = 12` and the shield adds 2. Bracers is likewise still 14 with a shield.
Both were checked, not assumed.

**The 2 remaining divergences are the reverse of the old problem.** `:lizardfolk-ac` overrides
`?armor-class-with-armor` with its own hardcoded sum built on `?base-armor-class`. That sum used to
carry character magic; now it does not, so the *prop* loses a Ring of Protection while the authored
form keeps it (old 15, authored 16, in leather and leather+shield). Rewriting the override to defer
to `?armor-class-with-armor` — the shim this migration needs regardless — closes both. This is the
next step, and the sweep drops to 0 when it lands.

## Two kinds of magic — name them differently

Agents keep conflating these, and the app invites it by giving them near-identical names:

| in-house term | app field | scope |
|---|---|---|
| **item magic** | `::mi5e/magical-ac-bonus`, a field ON an armor or shield | applies only while that item is used; a +2 plate's bonus is gone the moment you take it off |
| **character magic** | `?magical-ac-bonus`, a scalar on the character | applies to whatever calculation wins; Ring and Cloak of Protection |

Use those two terms. Unqualified "magic bonus" is what produced several wrong claims in this work.

**Magic gated on armor/shield state exists, and is currently implemented by channel omission.**
Bracers of Defense — "+2 to AC if you are wearing no armor and using no shield" — is shipped as
`(mod5e/unarmored-ac-bonus 2)`. It writes the no-shield channel and *not*
`?unarmored-with-shield-ac-bonus`, and that omission is the entire "no shield" clause. Verified:
14 unarmored, 14 with a shield (bracers excluded), 13 in leather.

So `?unarmored-with-shield-ac-bonus` is **load-bearing even though only Barbarian writes it** — an
earlier note in this doc called it redundant on that basis, which was wrong. Collapsing the two
channels without care would make Bracers apply with a shield. In the authored vocabulary Bracers is
`{:ac-bonus 2 :armor? false :shield? false}`, which the symmetric three-state tags express directly.

## Three mechanisms for one job (the D29 problem, concretely)

"A bonus that applies only in certain armor/shield states" is implemented three different ways in
shipped content:

| item / feature | gate | mechanism |
|---|---|---|
| Robe of the Archmagi | no armor (a shield is fine) | `(ac-bonus-fn (fn [armor shield] (if (nil? armor) 5 0)))` — a predicate inside the fn |
| Bracers of Defense | no armor **and** no shield | writes `?unarmored-ac-bonus` and not the with-shield channel — omission |
| Defense fighting style | only while wearing armor | `(armored-ac-bonus 1)` — a dedicated channel, summed only in the armor branch |
| Ioun Stone of Protection | none | `(ac-bonus-fn (fn [_ _] 1))` |

Nothing in the content overrides or replaces a shield's contribution.

The Robe's predicate form is the general one — the other two are special cases of it.
`{:ac-bonus N :armor? … :shield? …}` compiles to that predicate, so all three collapse to one
mechanism without any of them changing behavior. `?armored-ac-bonus` has exactly one writer
(Defense) and `?unarmored-ac-bonus` two (Barbarian, Monk) plus Bracers.

**Dead data found in passing:** Staff of Power sets `::magical-ac-bonus 2` on an item whose
`::type` is `:weapon`. AC reads that field only off `armor` (template_base.cljc:84) and `shield`
(:59) — never a weapon. The staff's real +2 comes from its own `ac-bonus-fn`. Harmless, but it is
the kind of thing that makes an unqualified "magic bonus" ambiguous.

## Natural armor: which `:armor?` tag, measured

The rules text has two sentences — "when you aren't wearing armor, your AC is 13 + Dex" *and* "you
can use your natural armor if the armor you wear would leave you with a lower AC." The app
implements both. Measured (Dex +2):

| context | `:armor? false` | no `:armor?` tag | shipped prop |
|---|---|---|---|
| unarmored | 15 | 15 | 15 |
| leather | **13** | **15** | **15** |
| plate | 18 | 18 | 18 |
| unarmored + shield | 17 | 17 | 17 |

`:armor? false` drops a Lizardfolk in leather from 15 to 13 — it implements only the first sentence.
**No `:armor?` tag** is the faithful migration. (The `+ shield` row read 15/15/17 before the shield
move below; that gap was the shield being trapped in the base, not a disagreement about the tag.)

## Traced: what `:lizardfolk-ac` actually computes

Recorded in full so this never has to be re-derived. The prop (`options.cljc`) emits two modifiers:

```clojure
(mods/modifier ?natural-ac-bonus 3)                       ; 1. set the scalar
(mods/modifier ?armor-class-with-armor                    ; 2. REPLACE the reconciler with:
  (fn [armor & [shield]]
    (max (+ ?base-armor-class (if shield (?shield-ac-bonus shield) 0))
         (?armor-class-with-armor armor shield))))        ;    ... vs whatever it was before
```

`?base-armor-class` is `10 + Dex + (natural, via the tie-break) + ?magical-ac-bonus`. With natural 3
and no unarmored defense present, the tie-break contributes the 3, so it is `13 + Dex + magical`.

**So the prop computes `max(13 + Dex + magical + shield, whatever you would otherwise have)`.** It is
a *complete* alternative AC — magic scalar and shield already folded in — compared against everything
else. That single expression accounts for every measured number (Dex +2):

| context | `13 + Dex + magic + shield` | otherwise | result |
|---|---|---|---|
| unarmored | 15 | 12 | **15** |
| unarmored + shield | 17 | 14 | **17** |
| leather | 15 | 13 | **15** |
| leather + shield | 17 | 15 | **17** |
| plate | 15 | 18 | **18** |
| + ring, unarmored | 16 | 13 | **16** |
| + ring, unarmored + shield | 18 | 15 | **18** |

Two things follow, and they are the whole of it:

1. **Natural armor applies whether or not armor is worn.** It competes with the worn value and the
   better wins — hence 15 in leather, 18 in plate. The authored form therefore carries **no
   `:armor?` tag**. Tagging it `:armor? false` would zero it while armored and regress leather to 13.
2. **The authored form `{:ac 13 :abilities [:dex]}` computes `13 + Dex` and nothing else** — 15. It
   omits the magic scalar and the shield *because in the new model those are bonuses applied to the
   winning calculation*, and they are not bonuses yet. That single difference explains all 7 sweep
   divergences; it is not a disagreement about what natural armor means.

Once the shield and `?magical-ac-bonus` move into `?ac-bonus-fns`, the authored calculation plus
those bonuses reconstructs the prop's number exactly (15 + 1 magic + 2 shield = 18, matching the old
18), and the sweep goes to zero.

## What the migration must not drop

`ac_reconciliation_test` SECTION 1e runs a **parity sweep**: every mechanism being replaced against
its authored replacement, across every equipment state, compared in one run. It currently reports
**7 divergences**, each one a case where deprecating the old form would change a real character's AC.
Finding these by hand turned up only some of them; the sweep is the thing that must be kept, and its
count is pinned so a new hazard fails a test instead of being noticed later.

The 7 have exactly two causes, and **one change fixes all of them** — move the shield's +2 and
`?magical-ac-bonus` out of `?armor-class-with-armor-base` into `?ac-bonus-fns`:

- **shield trapped in the base** (3 cases): natural armor with a shield, unarmored and in leather;
  Barbarian-shaped unarmored defense with a shield. Old gives 17, authored 15.
- **magical scalar trapped in the base** (4 cases): natural armor plus a +1 ring, in every state; two
  of them compound with the shield (old 18, authored 15).

Monk-shaped unarmored defense diverges in **no** context — its authored form already matches exactly.

The two behaviours behind those causes:

- **Natural armor applies while armored.** `:lizardfolk-ac` maxes against the worn value, so a
  character in leather shows **15** (natural 13+Dex), not leather's 13. Tagging it `:armor? false`
  would return 0 while armored and regress that to 13. The correct authored form carries **no
  `:armor?` tag**, and reproduces the prop exactly (15 with leather, 18 with plate — both pinned).
- **The magical scalar reaches the prop but not an authored calculation.** With a +1 Ring-style
  `?magical-ac-bonus`, the prop gives **16** and the authored form gives **15**, because the prop
  reads `?base-armor-class` (which includes the scalar) while an authored calculation is a bare
  value. Both pinned.

**So the order is fixed:** move the shield and `?magical-ac-bonus` into `?ac-bonus-fns` *before*
deprecating the props. Doing it the other way round silently drops shields and ring/cloak bonuses
from every natural-armor character.

### A gap in the tag set

`:armor?` has three states (must not wear / must wear / either) but `:shield?` has only two
(disqualified with a shield / usable with one). "Only while wielding a shield" is not expressible.
No current content needs it — the sole writer of `?unarmored-with-shield-ac-bonus` is Barbarian,
which also writes the no-shield channel — but a homebrew author would reach for it, so `:shield?`
should be made tri-state to match before the vocabulary is published.

## Remaining

All four planned items have landed: effective Dex cap · built-ins onto `ac-formula` · the channel
trim · the namespace extraction. What is left is not AC-engine work:

- **The pre-existing red test.** `audit-specs-match-the-registry` — `homebrew-fighting-style` is in
  `content-specs/save-specs` with no `content-types` entry. Predates this work; it is the
  fighting-style registry entry, tracked in the roadmap.
- **`best-ac` is still unwired**, and whether to adopt its bucketing is an open question, not a
  decision. See the namespace section above.
- **Backlog carried in the roadmap:** a real "can't wear armor" restriction as a selection
  constraint · per-item mug override · the rules-override layer · moving `map-plugin-classes` to
  CLJC.

### Channel count is going the wrong way

The design collapses toward two lists, `?ac-fns` and `?ac-bonus-fns`. `template_base.cljc` today
holds eleven AC attributes:

```
?ac-fns  ?ac-bonus-fns                                    the 2 the design wants
?ac-bonus  ?armored-ac-bonus  ?unarmored-ac-bonus         7 legacy scalars
?unarmored-with-shield-ac-bonus  ?natural-ac-bonus
?magical-ac-bonus  ?shield-ac-bonus
?armor-ac-suppressed?                                     added by this refactor
```

`?armor-ac-suppressed?` is not a duplicate — nothing else expresses it — but it is a new scalar in a
refactor whose point is removing scalars, and it is **avoidable**. In the target shape
(`:armor-formula` / `:other-formulas` / `:bonuses`) "worn armor gives no AC" is simply *omitting the
armor formula*; no flag exists. It should disappear when step 4 lands. Recorded so it is not
mistaken for a permanent part of the design.

### Attribution, since this refactor keeps circling the same model

Traced, because "whose design is this" kept being answered from memory:

- **`?ac-fns` and `?ac-bonus-fns` are upstream** — they entered `template_base` via PR #156
  (bewlay), not from any agent work on this branch. `max(base, formulas) + sum(bonuses)` was
  already the app's model. It was dead code: no constructor, no writers.
- **`orcpub.dnd.e5.armor-class` is the earlier agent's**, created 2026-09-03. It extracts that
  model into a namespace and adds `best-ac` bucketing.
- **This refactor** gave `?ac-fns` a constructor, moved shield and character magic into the bonus
  channel, migrated the bespoke props onto it, and characterized the lot.

So the convergence is on upstream's model, which both agents read the same way. What is genuinely
the earlier agent's and worth taking is one idea: **worn armor is itself a formula, not a
privileged base.** That is what removes `?armor-ac-suppressed?` — register no armor formula and
armor contributes nothing.

The criticisms of that work were about verification, not design, and they stand: docstrings made
present-tense claims about code nothing called, and the fixtures used the cum-sum constructor where
all real content uses `mod/modifier`, so a green suite proved nothing. D17 was about sequencing —
do not swap the live reconciler wholesale — not about the destination.

**Correction to a claim made in this doc's own reasoning:** that `?armor-ac-suppressed?` is deleted
by "the target shape (`:armor-formula` / `:other-formulas` / `:bonuses`)". The flag is deleted by
*armor being a formula*, which needs **one** list. The two-group split is a performance structure
for `best-ac` only, and it carries a footgun the earlier agent's own test documents
(`hardening-armor-reading-formula-in-wrong-group`: a formula placed in the wrong group returns a
wrong number or throws). Adopt the idea; do not inherit the grouping by default.

### REVISED: extract the AC namespace instead of deleting it

`orcpub.dnd.e5.armor-class` was slated for **deletion** under D34 — never-released scaffolding that
duplicates a live mechanism. That call assumed AC would stay in `template_base`. It should not:
breaking up the monoliths is a branch goal, and AC is the best-understood candidate now that it is
characterized and the sweep is at 0.

So the namespace is **wired rather than deleted**, and `template_base` keeps thin `?`-attribute
declarations that delegate into it. There is precedent in the same file —
`?dual-wield-weapon? weapon5e/light-melee-weapon?` already delegates to another namespace.

Constraint worth stating: `?`-attributes are entity-spec macros and only work inside
`es/make-entity`, so the *declarations* must stay in `template_base`. What moves is the arithmetic.
That is the D30 shape — a thin compiler over a real engine — not a parallel engine.

Size check, so the target is not overstated: `template_base.cljc` is 339 lines and AC is ~60 of
them. The extraction is worth doing for structure, not line count. The actual monolith on this
branch is `options.cljc` at 3938 lines.

---

# History

Everything below is the audit trail: what was believed and
when, and what reversed it. Current truth is at the top of this file.

## Ledger

Newest first. Each entry is one commit; sections below carry the detail. Reversals stay here even
when the section they came from has been rewritten — the section says what is true now, this says
what we believed and when.

| commit | what changed | reversed anything? |
|---|---|---|
| (this batch) | effective Dex cap; built-ins onto `ac-formula`; channel trim 18 → 10; AC engine extracted to its own namespace | yes — the D34 "delete `armor-class`" call, and the claim that the target shape deletes `?armor-ac-suppressed?` |
| `a950898c`+ | traced attribution for the AC model; corrected the claim that the two-group split is what deletes `?armor-ac-suppressed?` | yes — see Attribution |
| `8ab0a8f6` | renamed `:cant-wear-armor` → `:armor-gives-no-ac`; roadmapped the real restriction | yes — the name claimed a rule it does not implement |
| `ca0314b9` | split `:tortle-ac` into calculation + AC suppression; fixed the degenerate Bracers test | yes — see Corrections, "a limitation that wasn't" |
| `77acb74f` | characterized `:tortle-ac` (17/19/17-in-plate) | no. **This commit shipped with no doc entry — the one gap in the trail** |
| `7df2e618` | `:lizardfolk-ac` compiles to the universal shape; parity sweep 2 → 0 | no |
| `e1894a46` | shield + character magic moved into `?ac-bonus-fns`; sweep 7 → 2; 4 pins flipped | no |
| `a09ba395` | documented the three mechanisms for one job | no |
| `0abc1f53` | pinned the Bracers no-shield clause; named the two kinds of magic | yes — "the with-shield channel is redundant" |
| `73de3a03` | symmetric `:shield?`/`:armor?` tags | no |

## Corrections

- **A claim that was wrong:** an earlier draft said the live engine "drops bonuses when a calculation
  beats the base." True of the code as written, but unreachable — `?ac-fns` had no writers, so
  nothing could beat the base. A latent hazard, not a live defect.
- **A bug that wasn't:** two natural-armor sources appeared to stack. The test's synthetic classes
  used the cum-sum constructor `mod5e/natural-ac-bonus`; all real content uses `mod/modifier`, which
  replaces rather than accumulates. With the fixture corrected the effect vanished. Lesson in
  `verification-discipline.md`: verify the mechanism, not just the number.
- **A design claim that was wrong:** an earlier version of this doc said "the shield's own +2 needs
  no tag — it is a bonus." It is not a bonus in the current engine; it is computed inside the base,
  so a winning calculation loses it. Caught by the step-3 tests rather than by reading.
- **A prediction that was wrong:** this doc said the target shape would delete
  `?armor-ac-suppressed?` — "worn armor gives no AC is just omitting the armor formula, no flag
  needed". Making worn armor an ordinary calculation does not answer *how* it is left unregistered
  for one character; the entity spec's defaults are static, so a flag is still how that is
  expressed. The flag stays.
- **A correction that was itself wrong:** the Bracers/natural-armor defect was reported as shipped,
  then retracted as a regression this branch introduced, then confirmed shipped after all. The
  retraction compared against `agents/develop`, assumed to be the integration branch on the strength
  of its name; the baseline is `origin/integration`, which has the defect. Compounded by trusting
  `git merge-base --is-ancestor`, which answers "is this commit an ancestor" and not "does this
  branch have this change" — the tie-break had reached integration under a different SHA. Lesson in
  `verification-discipline.md`.
- **A limitation that wasn't:** this doc argued at length that `:tortle-ac` could not be moved onto
  the universal mechanism, because reproducing it needs a *ceiling* on AC and `?ac-fns` is a `max`,
  which raises floors only. The conclusion was "left as-is until equipment restrictions exist."
  Wrong framing, not wrong arithmetic. The ceiling was never the rule — it was a stand-in for "a
  tortle can't wear armor." Expressed as its AC consequence instead (*worn armor contributes
  nothing*) it composes with `max` and needs no ceiling. Prompted by the observation that
  `:tortle-ac` is a generic prop any homebrew author can take, so a species limitation should not
  ride along with it. **Process failure:** the superseded section was overwritten in place rather
  than corrected here, so the reasoning was briefly unrecoverable. That is what this section is for.
- **An overstatement:** the split above was described as "modelling the restriction honestly." It
  models the AC consequence. See `:armor-gives-no-ac` — it does not prevent equipping armor and
  does not touch `?armor-stealth-disadvantage?`.
- **A reachability claim that was wrong:** Lizardfolk was described as a built-in playable race. The
  race definition at `template.cljc:274` is `#_`-commented along with every other non-SRD race. The
  reachable path is the `:lizardfolk-ac` homebrew prop, which is what the characterization uses.
