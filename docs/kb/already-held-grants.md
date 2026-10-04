# Granting something the character already has

*What the app does when a grant duplicates something already on the sheet. Four paths, three
behaviours, one of them correct. Measured 2026-09-14. **Line references are against `feature/grant-rows` at `8bd2ce5d`** —
`views.cljs` moved ~90 lines during this work, so re-check before trusting them;
assertions in `test/cljc/orcpub/dnd/e5/already_held_grant_test.clj`.*

Markers: **VERIFIED** = built a character and read the sheet, or read from code with file:line.
**OPEN** = not established.

## The rule being modelled

**VERIFIED** against the SRD 5.1 text (`vitusventure/5thSRD` at `e1a7913`,
`docs/character/backgrounds.md:15`):

> "If a character would gain the same proficiency from two different sources, he or she can choose
> a different proficiency of the same kind (skill or tool) instead."

Three things that wording settles, and an earlier draft of this doc got the first one wrong:

- **It is a GENERAL rule, not a background rule.** "From two different sources" — any two. It sits
  in the backgrounds chapter because that is where duplicates first bite, not because it is scoped
  there. The app implements it in exactly one silo, which is the mismatch.
- **It covers skills AND tools**, named explicitly. The app implements the skill half only; a
  duplicate tool proficiency is silently wasted everywhere, including from a background.
- **"can choose"** — the player's option, not automatic. A design that silently substitutes is as
  wrong as one that silently wastes.

**Languages, weapons and armor are not covered** — checked exhaustively, not by one grep.
`backgrounds.md:15` is the *only* duplicate-proficiency rule in the corpus: searches for "already
have/proficient", "choose a different", "gain the same", "same proficiency", "duplicate" and
"twice" across every file turn up nothing else on the subject. `languages.md` has no duplicate
rule, and multiclassing's Proficiencies section is a *restriction* (you gain fewer), not a
duplicate rule. So a wasted duplicate language is unaddressed by the rules rather than incorrect.

`background-skills-cfg` (`options.cljc:2845`) is the only place in the app that implements any of
it, and only for skills:

```clojure
:modifiers  (modifiers/skill-proficiency skill-kw background-nm
                                         [(not (get ?skill-profs skill-kw))])
:selections (skill-selection (map :key skills/skills) 1 0 nil
              (fn [c] (let [srcs (get (character/skill-proficiencies c) skill-kw)]
                        (and srcs (not (srcs background-nm))))))
```

Grant it unless held; if a source **other than this background** already granted it, offer a free
pick instead. The `(not (srcs background-nm))` test is why `?skill-profs` is keyed
`[skill source]` — the rule is relational, and unaskable without the source.

## The four paths

| path | already-held handling | evidence |
| --- | --- | --- |
| `background-skills-cfg` | full — conditional modifier **and** a replacement pick | code, `options.cljc:2845` |
| pool/bespoke **choice** (`skill-option`, `language-option`) | option-prereq greys it out — *"You already have this skill"* | code, `options.cljc:144`, `:1170` |
| `:props :skill-prof` etc. (feat, race, subrace) | **none** — the duplicate is silently wasted | built character |
| `{:pool p :key k}` fixed grant | **none** — same | built character |

**VERIFIED, across pools.** A character who already has the thing, granted it a second time:

```
skill  — legacy :props     no change
skill  — pool :key         no change
lang   — legacy :props     no change
lang   — pool :key         no change
tool   — legacy :profs     no change
tool   — pool :key         no change
```

Same in every pool. A fixed grant of something held is a no-op, and nothing tells the player.

## Consequence for the pool work

A fixed grant compiles straight to modifiers in `compile-grants` — it never builds an option, so
it cannot reach the option-prereq the choice path gets for free, and it has no fallback.

So **porting `background-skills-cfg` to `{:pool :skills :key …}` would lose a feature.** The pool
is not yet a faithful replacement for the one path that is correct.

**DESIGN.** The SRD rule is general and the app's implementation is not, which is the argument
for lifting it into the grant compiler rather than porting it per silo. Scope it as the SRD scopes
it — **skills and tools, not languages or weapon/armor**. Lifted, it would:

- replace the one bespoke implementation with one general one (background's becomes a deletion);
- give the behaviour to every other silo — today a race or feat granting a held skill just
  evaporates, which the SRD says should offer a replacement;
- cover **tools**, which nothing covers today even though the rule names them;
- serve skill expertise with the same machinery, since both ask *who else already gave me this?*

It needs the grant's owner threaded into `compile-grants`, because the test is "not me". Both
call sites already bind `key` and don't pass it.

## Expertise-instead-of-proficiency is not an SRD pattern — confirmed in the text

**VERIFIED** (`scripts/clj-grep.py`). Every content use of `skill-prof-or-expertise` /
`tool-prof-or-expertise` is in `ua_feats.cljc` and `ua_race_feats.cljc`, and **all 12 are `#_`
discarded**. The only live use is `grant_pools.cljc:71`, this branch's own `:skill-expertise`
pool.

So the app grants a replacement *choice* often (backgrounds, SRD) and *expertise* never — the
vocabulary exists for UA content that is switched off. Anything built on it is serving homebrew,
not SRD.

**VERIFIED in the SRD.** There is no expertise-from-duplication rule. Expertise appears only as
the Rogue and Bard class feature, and it *selects among proficiencies you already have* rather
than converting a duplicate:

> Rogue: "choose two of your skill proficiencies, or one of your skill proficiencies and your
> proficiency with thieves' tools. Your proficiency bonus is doubled…"
> (`docs/character/classes/rogue.md:52`)

Every other SRD mention of "expertise" is prose (the feats intro, a monster-stat note), not a
mechanic. So the correct answer to a duplicate proficiency is **a replacement choice, never
expertise**.

**OPEN** — which published books define expertise-instead (Tasha's *Skill Expert*, XGtE
*Prodigy*) is still unchecked; both are outside the SRD.

## RETRACTED — the race `:props` "bug" was my broken fixture

*Written and committed 2026-09-14, retracted the same day. Left in full because the error is more
instructive than the claim was.*

**The claim:** a race's `:props` were read by nothing — `race-option` destructures no `:props`,
so the six race-builder checkboxes did nothing to a character.

**Why it was wrong:** the supporting grep was `grep -n 'plugin-modifiers' options.cljc` — one
file — and its result was reported as a fact about the codebase. There are **seven** callers, five
of them in `spell_subs.cljs`: race (`:362`), subrace (`:379`), subclass (`:685`), class (`:721`).

A homebrew race's props are compiled in the cljs **subscription** layer, before the race reaches
`race-option`:

```clojure
(assoc race :modifiers (concat (opt5e/plugin-modifiers (:props race) (:key race)) …))
```

The builder works. The JVM fixture handed `template-selections` a raw race map and skipped that
step, so the race granted nothing and I read the fixture's silence as the app's.

**The general trap, which this repo had already written down:**
`grant_vocabulary_characterization_test` says vocabulary B "lives in `spell_subs.cljs` (cljs), so
its level-gated assembly is NOT reachable from this JVM gate." Any JVM fixture standing in for
plugin content must apply the cljs compile step itself. `compiled-race` in the test does.

**What survives:** every duplicate-grant measurement. Re-run through the compiled path, all six
rows still report no change. The retraction touches only the race-props claim, and rows 3, 5, 7,
9, 10 of the 35-table are ordinary behaviour-preserving shim rows after all.

## Related

- [pool-grant-map.md](pool-grant-map.md) · [builder-disposition-audit.md](builder-disposition-audit.md)
- [content-extensibility-framework.md](content-extensibility-framework.md) §3b — the engine already
  supports filter/gate/prereq; the framework's job is exposing it as data (D18)
