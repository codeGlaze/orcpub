# A hidden selection's pick still applies

*Live defect class. Measured 2026-09-14, `feature/grant-rows` at `4b7dc0e6`; assertions in
`test/cljc/orcpub/dnd/e5/conditional_selection_ref_test.clj`.*

## Trapped picks: homebrew that is not loaded (e2e, 2026-10-04)

`test/e2e/orphan-clear.js` (`ab89f88e`). A level-4 wizard with a homebrew race, feat and spell,
then the pack disabled or deleted (both behave the same; neither warns that a character uses it).
"Missing Content (3)" lists all three and offers **no action**; none renders as a card.

| pick | stored at | clearable? |
|---|---|---|
| race | the race slot | yes: picking another race replaces it |
| feat | `[:feats]` (top level, not under the level-4 slot) | **no route.** It fills the one Feats place, so other feats are disabled; it survives switching the slot to ASI, lowering the level and changing the class |
| spell | `:wizard-spells-known` | only by changing the class, which discards every other spell pick. Until then it silently takes one of the 12 places |

Owner's rule (2026-10-03): no choice on a character may be trapped with no way to clear it.
Clearing a missing pick is an option the player takes, never automatic: the homebrew may load in
another browser.

## Two slot facts the design depends on (e2e, 2026-10-03)

`test/e2e/slot-order-and-click.js` (`e7239168`). **A shared slot stores picks in click order**:
`update-multi-select` (`event_handlers.cljc`) appends with `conj`; deselect then re-select moves a
pick to the end; nothing records which level granted it; the UI lists options alphabetically.
**The two one-pick kinds click differently:** Fighting Style (multi-pick, max 1) blocks a click on
another option until the current one is cleared; Defensive Tactics (single pick) swaps on click and
cannot be cleared. While overfull, clicking a selected pick removes it; unselected ones are inert.

## Reachable through the UI (e2e, 2026-10-01)

`test/e2e/hidden-pick-flows.js` on `fix/hidden-multiclass-skill-pick` (`babf234f`): five flows,
clicks only, the built character and the saved copy read back.

| flow | reachable | what remains |
|---|---|---|
| level drop below a subclass pick (Hunter, Steel Will at 7, lowered to 6) | yes | **Steel Will still applies; its section is gone; no notice.** The live hidden pick |
| level drop under the shared fighting-style pool (Champion 10 → 9) | yes | both styles apply (Defense still +1 AC); the builder shows "select 1 · remove 1" |
| delete the first class | yes | nothing stale: the class entry and its picks go; default gear swapped |
| delete the first class when the second has picks | yes | **the new first class is reset** to `{:levels [level-1]}`: its own picks are destroyed |
| change or reorder the first class | no reorder control; changing slot 0's class resets that slot | nothing stale; the old class's picks are gone |

So the first-class family (skills, tools, starting gear after the order changes) is **not reachable
in the UI today**: every route to a new first class resets the slot. The JVM verdicts below still
hold for stored data that arrives another way. The reset itself is the opposite failure, data loss.
**Not yet measured:** whether the reset also drops levels above 1 (flow 5 used level-1 classes).

## Verified 2026-10-01: claims tested, verdicts

Pinned on `fix/hidden-multiclass-skill-pick` (`65bb097a`), each deftest named for its claim, each
asserting today's behaviour: `modifier_condition_research_test` (M), `hidden_pick_tools_equipment_
research_test` (D), `subclass_gate_research_test` (S). All through a real `entity/build`.

| claim | verdict | evidence |
|---|---|---|
| M1 a predicate conj'd onto a built modifier's `::mods/conditions` is enforced | CONFIRMED | `mods/apply-modifiers` checks `(every? #(% e) conditions)` |
| M2 such a condition reads the right `:classes` without declaring the dep | **REFUTED** | without `:classes` in `::mods/deps` the modifier can run before classes are set (`apply-options` graphs only non-empty deps; ungraphed keys sort first): saw `[]` for a rogue. Result depends on order |
| M3 one fn works as `prereq-fn` (built char) and as a condition (mid-build map) | CONFIRMED with the dep, REFUTED without | `char5e/classes` is `es/entity-val`, so it reads both shapes |
| M4 a condition on a deferred modifier's record is lost | CONFIRMED | `collect-modifiers-2` replaces the record with the `deferred-fn` result |
| M5 `add-mod-total-levels-prereq` throws on a list of modifiers | CONFIRMED, unreachable | `(map f lvl cls modifier)`; no built-in or homebrew path gives it a list |
| D1 stored class tool picks outlive the class being first, both arms | CONFIRMED | bare `modifiers/tool-proficiency` at `tool-prof-selection` and `-aux` |
| D2 the bard multiclass tool gate `(:classes c)` disagrees with `character/classes` | REFUTED | same value after build; the arm shows only when bard is second |
| D3 every chosen starting-equipment shape outlives the class being first | CONFIRMED | weapon, any-simple, armor, pack, plain, nested group, holy symbol, hand-built bundle. `weapon`/`armor`/`equipment` are `map-mod`: no conditions |
| D4 fixed starting gear follows the first class | REFUTED as stated | not template data: `char5e/set-class` writes it, only at class index 0; reordering classes leaves it. A stale-data path, not a gate |
| S1 subclass LEVEL picks outlive their level | CONFIRMED | Hunter's Defensive Tactics (7) applies at ranger 6; Champion's level-10 fighting style shares `:ref [:class :fighter :fighting-style]` with level 1, so picks lose their source level |
| S2 the subclass spell-selection gate `(>= lvl total-levels)` is inverted | CONFIRMED, latent | no live subclass reaches it (built-in EK/AT commented out; homebrew uses `:levels`) |
| S3 `total-levels-prereq` and `-2` agree | on real input; `-prereq` throws where `-2` returns false/nil (absent class key, nil level, nil character) | |

**Sites found since the scoping below:** hand-built starting-equipment `:selections` in
`classes.cljc` (cleric, druid, fighter, paladin, ranger, rogue, sorcerer, warlock) and
`starting-equipment-option` (holy symbols), all with bare modifiers; and subclass level selections
(S1). Any fix that conditions modifier constructors one by one misses the hand-built ones; a
condition applied over a whole selection does not.

**Not tested, found by reading:** `delete-class` (`events.cljs`) re-runs `set-class` on the new first
class, which resets that class's options to `{:levels [level-1]}`.

## The mechanism

A selection's `::t/prereq-fn` controls **whether the builder offers it**, not whether a pick
stored in it is applied.

- `remove-disqualified-selections` (`entity.cljc:536`) drops selections whose `prereq-fn` fails.
  It is called only from `get-all-selections-2` — the **builder UI** path.
- `entity/build` never consults a selection's `prereq-fn`. (`meets-prereqs?` at `:815` is a
  different thing: `::t/prereqs` on an *option*, not `::t/prereq-fn` on a *selection*.)

So a selection that is still in the template but hidden by a failing prereq keeps compiling
whatever the character saved in it. The player cannot see the control, cannot change it, and
still has the thing.

## What is NOT broken

**VERIFIED.** A pick dies correctly when the selection leaves the template entirely — the ordinary
swap case:

```
a language chosen as Chattyfolk (race offers the choice), then the race is swapped
  as Chattyfolk:       #{:elvish}
  as Quietfolk:        nil          ← dropped, correctly
  Quietfolk, no pick:  nil
```

This holds even though `language-selection-aux` stores the pick at a **top-level** `:ref
[:languages]` (`options.cljc:1069`), so the pick is not nested under the race at all. `build`
walks the template and applies only what it can reach; an unreachable option is ignored.

That is worth stating plainly because the obvious worry — *swap a race and its choices linger* —
is unfounded. The leak needs the selection to **stay** in the template.

## What is broken

**VERIFIED**, through `background-skills-cfg`, the shipping example. A background grants
Athletics; a race also grants it, so the background offers a replacement pick. Choose Insight,
then remove the duplicate:

```
with duplicate:    {:athletics {nil true},         :insight {nil true}}
duplicate removed: {:athletics {"Testbound" true}, :insight {nil true}}
```

The background's own skill comes back **and** the replacement stays. A free extra skill, with no
control on screen to remove it.

## Exposed sites — by what is gated, not by where the code lives

**VERIFIED.** Nine `prereq-fn` sites in `options.cljc`, but the useful grouping is what they gate.
Five of them are **starting equipment**, which an earlier version of this doc missed by counting
definitions instead of callers.

| what is gated | sites | gate | status |
| --- | --- | --- | --- |
| class skills, first-class arm | `class-skill-selection … :skill-proficiency` (`:3347`) | `first-class?` | **reproduced** |
| class skills, multiclass arm | `… :multiclass-skill-proficiency` (`:3351`) | `(complement first-class?)` | **reproduced** |
| class starting equipment | `:2672`, `:2682`, `:2735`, `:2798`, `:2811` | `first-class?` | **reproduced** |
| class tools | `tool-prof-selection … :tool-selection` (`:3339`), `:multiclass-tool-selection` (`:3341`) | both arms | untested |
| background replacement skill | `background-skills-cfg` (`:2845`) | held by another source | **reproduced** |

`does-not-have-feat-prereq` (`:1319`) is a **different mechanism** — `::t/prereq-fn` inside an
option's `::t/prereqs`, read by `meets-prereqs?`, not a selection gate. Not in this class.

## Starting equipment is the most visible case

**VERIFIED** (`multiclass_hidden_pick_test.clj`). Fighter 1 / Rogue 1 with the fighter's
Explorer's Pack chosen:

```
fighter first
  equipment: (:backpack :bedroll :mess-kit :rations-1-day- :rope-hempen :tinderbox :torch :waterskin)
  offered:   [:class :fighter :starting-equipment-equipment-pack]  (and 3 more fighter selections)

rogue first, fighter 2nd
  equipment: (:backpack :bedroll :mess-kit :rations-1-day- :rope-hempen :tinderbox :torch :waterskin)
             ← identical, still there
  offered:   [:class :rogue :starting-equipment-equipment-pack]    (fighter's: GONE)
```

The character keeps the fighter's whole starting pack, has no control to remove it, **and** the
rogue's starting equipment opens on top — so they can hold two classes' starting gear. Eight items
in the inventory is considerably easier to notice than one extra skill, which may be why this gets
reported as "an option I can't uncheck" rather than as a rules error.

## The multiclass case — REPRODUCED

**VERIFIED 2026-09-14** (`multiclass_hidden_pick_test.clj`). This is the shape users report as
*"an option I can't uncheck or get to."*

`class-option` gives every class two mutually exclusive skill selections, both always present in
the template:

```
:skill-proficiency             gated  first-class?               rogue: choose 4
:multiclass-skill-proficiency  gated  (complement first-class?)  rogue: choose 1
```

Fighter 1 / Rogue 1, rogue multiclassed, Athletics picked from the rogue's multiclass option:

```
skills:  (:athletics :intimidation :survival)
offered: Skill Proficiency   at [:class :fighter :skill-proficiency]
         Expertise           at [:class :rogue :expertise]
         Skill Proficiency   at [:class :rogue :multiclass-skill-proficiency]   ← holds Athletics
```

Drop the fighter level. Rogue is now the first class:

```
skills:  (:athletics)                                              ← still there
offered: Expertise           at [:class :rogue :expertise]
         Skill Proficiency   at [:class :rogue :skill-proficiency] ← EMPTY, choose 4
                                                       (multiclass selection: GONE)
```

Three things at once, and together they are the reported symptom:

1. **The fighter's skills vanish correctly** — Intimidation and Survival go, because the whole
   fighter class left the template.
2. **Athletics stays**, because the rogue did *not* leave. Only its selection's gate flipped.
3. **Nothing on screen can remove it.** The control holding Athletics is filtered out by its own
   `(complement first-class?)` prereq, while the first-class "choose 4" selection opens beside it
   — so the character can reach **five** rogue skills and cannot get back to four.

That is the precise rule: **remove the piece and the pick dies; keep the piece and flip a gate
and the pick persists, uncontrollable.**

## Why it has been hard to find## Why it has been hard to find

The evidence is invisible everywhere a person would look. The control is gone from the builder, so
there is nothing to notice; the skill just sits on the sheet looking legitimate. And the *stored*
pick is the only trace, which means it shows up in neither the sheet nor a PDF — you would have to
read the saved character's option tree.

## Fixing it

Three shapes, and this is **OPEN**:

1. **Apply the same gate at build time** — have `build` skip options under a selection whose
   `prereq-fn` fails. Most correct, and the widest blast radius: it changes what every existing
   saved character computes, so it needs characterization across all nine sites first.
2. **Suppress at the modifier** — give the resolution's own modifiers the same
   `unless-held-by-other` condition the grant carries. Narrow, only fixes the cases written that
   way, leaves the general mechanism intact.
3. **Surface it** — treat an unreachable pick like a dangling content reference and report it
   through `content_reconciliation`. Does not change any computed value; makes the state visible
   and lets the player clear it.

(1) and (3) compose: report it, and stop applying it.

Whatever is chosen, it wants deciding **before** the resolution vocabulary in
[decision-already-held-resolution.md](decision-already-held-resolution.md) is built, because that
design multiplies the number of conditional selections from nine sites to every grant in every
silo.

## Related

- [decision-already-held-resolution.md](decision-already-held-resolution.md) — the design that
  makes this common rather than rare
- [already-held-grants.md](already-held-grants.md) — what a duplicate grant does today
- [multi-tab-character-contamination.md](multi-tab-character-contamination.md) — the other
  silent-character-corruption doc
