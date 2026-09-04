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
