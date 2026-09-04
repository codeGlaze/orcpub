# Armor Class refactor — status, design, and corrections

Companion to `armor-class-computation.md` (which documents the AC model the app runs). This doc
covers the refactor: what has landed, what the design is, and what we got wrong on the way.

**Status words, used strictly:**
- **IN THE APP** — live code the running application executes.
- **NOT WIRED** — exists in the repo with passing tests, but nothing in `src/` calls it.

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
- **`:shield?`** — `false` = **disqualified** while a shield is held. Not "omit the shield bonus":
  the characterization shows Monk with a shield is 14, and the omit reading would give 15.
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

## What the migration must not drop

Two behaviours of the existing props are easy to lose, both found by testing rather than reading:

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

- **FIRST: move the shield's +2 and `?magical-ac-bonus` out of the base and into `?ac-bonus-fns`**,
  so they land on whichever calculation wins. Until this happens, authored calculations silently
  lose both (pinned at 15 in each case). This also removes the reason
  `?unarmored-with-shield-ac-bonus` exists, and it is a prerequisite for the shims below.
- Make `:shield?` tri-state, matching `:armor?`.
- Move unarmored defense, Monk, and natural armor onto `ac-formula`; delete the pairwise `if`.
- Effective-cap combination, with a Medium Armor Master + custom-cap test.
- Shims for `:lizardfolk-ac`, `:tortle-ac`, `?natural-ac-bonus`, `?unarmored-ac-bonus`,
  `?unarmored-with-shield-ac-bonus` (D9 zero-migration; `#_`-strike + date + ledger row per D34).
- **Delete `orcpub.dnd.e5.armor-class`.** It duplicates a live mechanism and nothing calls it; by
  D34 that is never-released scaffolding, so it goes rather than being deprecated. Its tests move to
  the real engine — they encode good cases (bonuses reaching the winner, floors as constant
  calculations, shield permission as opt-out).

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
- **A reachability claim that was wrong:** Lizardfolk was described as a built-in playable race. The
  race definition at `template.cljc:274` is `#_`-commented along with every other non-SRD race. The
  reachable path is the `:lizardfolk-ac` homebrew prop, which is what the characterization uses.
