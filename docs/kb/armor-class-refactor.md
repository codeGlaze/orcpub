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

That last case also settled a question: the shipped engine already sums bonuses onto whichever
calculation wins. The behaviour was simply unobservable, because nothing could add a calculation.

## The authored shape

Homebrew declares AC through `:props`, the vocabulary that already reaches races, subraces, classes,
subclasses, feats and ancestries (`plugin-modifiers` → `make-feat-modifiers`). One shape, not a key
per case:

```clojure
{:ac 10 :abilities [:dex :con] :armor? false}                 ; Barbarian unarmored defense
{:ac 10 :abilities [:dex :wis] :armor? false :shield? false}  ; Monk
{:ac 13 :abilities [:dex]      :armor? false}                 ; natural armor / Mage Armor
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

The shield's own +2 needs no tag — it is a bonus, so it lands on whatever calculation won. Monk with
a shield therefore disqualifies, the plain `10 + Dex` wins, and the shield adds: 14.

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

## Remaining

- `:ac` / `:ac-bonus` props compiler, so authors write the shape above instead of a function.
- Move unarmored defense, Monk, and natural armor onto `ac-formula`; delete the pairwise `if`.
- Move `?magical-ac-bonus` (currently written in two places inside the base) to a bonus, so it lands
  on the winner.
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
- **A reachability claim that was wrong:** Lizardfolk was described as a built-in playable race. The
  race definition at `template.cljc:274` is `#_`-commented along with every other non-SRD race. The
  reachable path is the `:lizardfolk-ac` homebrew prop, which is what the characterization uses.
