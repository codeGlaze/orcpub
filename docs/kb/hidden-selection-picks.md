# A hidden selection's pick still applies

*Live defect class. Measured 2026-09-14, `feature/grant-rows` at `4b7dc0e6`; assertions in
`test/cljc/orcpub/dnd/e5/conditional_selection_ref_test.clj`.*

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

## Exposed sites

Every selection carrying a `prereq-fn` is in this class — nine live sites in `options.cljc`:

| site | gate | the scenario |
| --- | --- | --- |
| `background-skills-cfg` replacement (`:2845`) | held by another source | change race after taking the background |
| `class-skill-selection` (`:2672`, `:2682`) | `first-class?` | **change class order** |
| the multiclass variants (`:2735`, `:2798`, `:2811`) | `(complement first-class?)` | **reproduced — see below** |
| `tool-prof-selection` / `-aux` (`:2630`, `:2655`) | caller's prereq | as above |
| `skill-selection-2` (`:1127`), `tool-proficiency-selection-2` (`:1183`) | caller's prereq | as above |

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
