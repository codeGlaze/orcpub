# Granting something the character already has

*What the app does when a grant duplicates something already on the sheet. Four paths, three
behaviours, one of them correct. Measured 2026-09-14. **Line references are against `feature/grant-rows` at `8bd2ce5d`** —
`views.cljs` moved ~90 lines during this work, so re-check before trusting them;
assertions in `test/cljc/orcpub/dnd/e5/already_held_grant_test.clj`.*

Markers: **VERIFIED** = built a character and read the sheet, or read from code with file:line.
**OPEN** = not established.

## The rule being modelled

SRD backgrounds say: if you already have a proficiency the background grants, choose a different
one. So "already held" is a real rule with a defined answer, not an edge case.

**VERIFIED** — `background-skills-cfg` (`options.cljc:2845`) is the only place that implements it:

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

**DESIGN.** The rule in `background-skills-cfg` is pool-independent — "if held from another
source, don't grant, offer a pick from the same pool" is equally true of tools, languages and
weapon proficiencies. Lifted into the grant compiler it would:

- replace N bespoke implementations with one (background's becomes a deletion, not a port);
- give the behaviour to silos that never had it — today a race or feat granting a held skill just
  evaporates;
- serve skill expertise with the same machinery, since both ask *who else already gave me this?*

It needs the grant's owner threaded into `compile-grants`, because the test is "not me". Both
call sites already bind `key` and don't pass it.

## Expertise-instead-of-proficiency is not an SRD pattern

**VERIFIED** (`scripts/clj-grep.py`). Every content use of `skill-prof-or-expertise` /
`tool-prof-or-expertise` is in `ua_feats.cljc` and `ua_race_feats.cljc`, and **all 12 are `#_`
discarded**. The only live use is `grant_pools.cljc:71`, this branch's own `:skill-expertise`
pool.

So the app grants a replacement *choice* often (backgrounds, SRD) and *expertise* never — the
vocabulary exists for UA content that is switched off. Anything built on it is serving homebrew,
not SRD.

**OPEN** — which published books define expertise-instead (Tasha's *Skill Expert*, XGtE
*Prodigy*) is asserted from rules knowledge, not checked against a text.

## Found while building the fixture: a race's `:props` are read by nothing

**VERIFIED — live bug, independent of the grant work.**

`::race5e/toggle-race-map-prop` writes `[:props <k> <v>]` (`events.cljs:3903`), and six race
builder widgets route through it: skill, weapon and armor proficiency, damage resistance, damage
immunity, languages (`views.cljs:7518-7528`; subrace is the same, `:7257-7270`).

`race-option` destructures `[name icon key help abilities size speed darkvision subraces modifiers
selections traits source languages language-options armor-proficiencies weapon-proficiencies profs
plugin? grants edit-event]` — **no `:props`** — and nothing else compiles a race's props:
`plugin-modifiers` has exactly two callers, `feat-modifiers` and the fighting-style option.
`subrace-option` destructures no `:props` either.

Measured: a race with `:props {:skill-prof {:athletics true}}` builds a character with
`?skill-profs` **nil**. The same key on a feat grants it. The checkboxes save, reload into the
form and export to `.orcbrew` — and do nothing to a character.

This matters for the shim: rows 3, 5, 7, 9, 10 of the 35-table are race `:props` rows, and
normalizing a key that currently does nothing into a `:grants` that works is a **behaviour
change**, not a behaviour-preserving migration. It may be the fix people want, but it is not
silent and should not ride in on a shim commit.

**OPEN** — how long this has been broken, and whether any shipped homebrew race relies on it.

## Related

- [pool-grant-map.md](pool-grant-map.md) · [builder-disposition-audit.md](builder-disposition-audit.md)
- [content-extensibility-framework.md](content-extensibility-framework.md) §3b — the engine already
  supports filter/gate/prereq; the framework's job is exposing it as data (D18)
