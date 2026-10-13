# Lifting mechanics out of prose

*A direction, not a plan. Written 2026-10-13 on `feature/extras-companions` because the same
problem turned up four separate times in one week of Extras work.*

## The pattern

A rule the app needs to *evaluate* is stored as a sentence the app can only *print*. The sentence
is correct and worth keeping — it is what the player reads. What is missing is the machine-readable
half beside it.

**The worked example is one modifier block.** `classes.cljc:802-814`, after the CR normalization:

```clojure
(mod/modifier ?wild-shape-cr
  (mod5e/level-val (?class-level :druid) {1 0.25  4 0.5  8 1}))        ; lifted — queryable

(mod/modifier ?wild-shape-limitation
  (mod5e/level-val (?class-level :druid)
    {1 "no flying or swimming speed"                                   ; still prose
     4 "no flying speed"
     8 nil}))
```

Same feature, four lines apart. The creature query needs **both** halves — a CR bound and a speed
exclusion — and only one of them can be asked a question.

## Instances found so far

| where | the mechanic, as prose |
| --- | --- |
| `classes.cljc:810-814` | `?wild-shape-limitation` — a speed exclusion as a sentence fragment |
| `spells.cljc:200-206` | Animate Objects: five size tiers of HP/AC/attack/Str/Dex as an unaligned text table |
| `spells.cljc` | **10** occurrences of "challenge rating N or lower" — the bound of every conjure/summon spell |
| `template.cljc:706` | Retainers: `:summary "You have 3 commoner retainers"` — a count and a creature type in one sentence (`#_` discarded, but it is how the author reached for it) |
| `spells.cljc` | Reincarnate's race table and Confusion's d10 table, as text rows |
| feat widgets | "Your HP increases by [1\|2] per level" — a template with a frozen parameter, per `builder-disposition-audit.md` §REFRAMING |

21 table-like lines sit inside `spells.cljc` description strings in total.

## The rule this should follow

**Lift the mechanic, keep the prose — and derive the prose from the mechanic where possible.**

CR is the precedent and it is worth copying exactly. The number became the stored form, `cr->label`
renders "1/4" at display, and every printed sentence stayed byte-identical. Nothing was lost and
the value became comparable. See [decision-cr-representation.md](decision-cr-representation.md).

The failure mode to avoid is the opposite one: storing the display form because it is what the page
needs, which is how `?wild-shape-cr` became a string in 2017 and stayed one for nine years.

## Open questions

- **What is the authored shape?** A sibling key (`:limitation` beside `:limitation-text`), a
  structured value a formatter renders, or a registry of named rules like `requirements.cljc`.
  `requirements.cljc` already pairs a `:pred` with a `:text` phrasing — that may be the answer
  already, for gating rules at least.
- **Who derives the sentence?** If prose is generated, a wording change becomes a code change.
  If it is authored alongside, the two drift. CR dodged this because "1/4" is mechanical; "no
  flying or swimming speed" is editorial.
- **How does homebrew express it?** An author writing a subclass needs to state a rule, not just
  describe it, or plugin content stays second-class.
- **Where does it stop?** Not every sentence is a mechanic. A spell's flavour text should stay
  text. The test is whether anything in the app would ever need to ask it a question.

## Why it matters beyond tidiness

Every one of these blocks something concrete: the creature query needs the speed exclusion, the
conjure spells need their CR bounds, animated objects need their size table, and a homebrew
subclass cannot declare a rule it can only describe.
