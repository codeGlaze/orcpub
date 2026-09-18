# How authored content becomes a built character

*The middle of the stack, which no existing doc covers.
[built-character-representation.md](built-character-representation.md) explains reading the result;
`namespace-architecture.md` and `entity-options-architecture.md` (both **on `agents/develop`
only** — not on this branch) inventory the namespaces and describe the saved options tree. This is
the assembly between them. Measured 2026-09-15 on `feature/grant-rows`.*

## The five stages

```
1  AUTHORED CONTENT   built-in defs (classes.cljc, template.cljc) ++ homebrew from .orcbrew
                      plain maps: {:name "Rogue" :profs {…} :levels {…}}
        │
        │  the silo assembly fns — options.cljc
        ▼
2  TEMPLATE PIECES    option-cfg / selection-cfg
                      "a choosable thing" / "a pick among them"
        │
        │  template-selections, then template — dnd/e5/template.cljc
        ▼
3  THE TEMPLATE       {::t/base <blank attribute map>  ::t/selections <the tree>}
        │
        │  entity/build — entity.cljc
        ▼
4  BUILT CHARACTER    attributes as cells; derived values are deferred fns
        │
        │  entity-val — entity_spec.cljc
        ▼
5  A VALUE            char5e/skill-proficiencies, etc.
```

## What each file actually is

**VERIFIED** — sizes and counts read, not recalled:

| file | size | shape | role |
| --- | --- | --- | --- |
| `orcpub/template.cljc` | 157 lines, 8 fns | `selection-cfg`, `option-cfg` | **the vocabulary.** Two constructors everything else speaks |
| `dnd/e5/options.cljc` | 4143 lines, **168 fns** | machinery | **the compiler.** Authored maps → template pieces |
| `dnd/e5/template.cljc` | 1588 lines, **9 fns** | **data** | the built-in content, assembled. `template-selections` builds the tree; `template` wraps it |
| `dnd/e5/template_base.cljc` | 331 lines, **0 fns** | data | `template-base` — the blank character every build starts from |
| `orcpub/entity.cljc` | — | machinery | `build` = raw options + template → built character |
| `orcpub/entity_spec.cljc` | 197 lines | machinery | the cell engine; `entity-val` reads one |

The count that surprises people: **`dnd/e5/template.cljc` is 1588 lines and 9 functions.** It is
content, not logic. Someone looking for assembly code there will not find it — that is
`options.cljc`.

And a template is only ever two things:

```clojure
(defn template [selections]
  {::t/base t-base/template-base      ; the blank attribute map
   ::t/selections selections})        ; what may be chosen
```

## Where to make a change

| you want to | go to |
| --- | --- |
| change what a race/class/background/feat *grants* | its silo's assembly fn in `options.cljc` |
| add a kind of choice | a new `*-selection` in `options.cljc`, offered from a silo's assembly fn |
| change a built-in's data | `classes.cljc`, `races.cljc`, `dnd/e5/template.cljc` — data, no logic |
| change a character's starting attributes | `template_base.cljc` |
| change how a value is computed from modifiers | `modifiers.cljc`, or `entity_spec.cljc` for the engine |
| change what homebrew may declare | the silo's spec, plus the assembly fn that reads the key |

## Two things that bite across stages

**Homebrew and built-in take the same path.** A plugin race is compiled by `race-option`, exactly
like Elf. That is the property the whole extensibility effort rests on — but the cljs subscription
layer compiles a plugin's `:props` *before* the map reaches `race-option`
(`spell_subs.cljs:362`, and the same for subrace, subclass, class). A JVM test that hands
`template-selections` a raw plugin map skips that step and the content grants nothing. That
mistake was made and retracted in this repo; see `already_held_grant_test.clj`'s `compiled-race`.

**A selection's `prereq-fn` gates stage 3, not stage 4.** It decides whether the builder OFFERS a
pick. `entity/build` never consults it, so a pick saved while the gate passed keeps applying after
it stops. See [hidden-selection-picks.md](hidden-selection-picks.md).

## A note on the namespace inventory

`namespace-architecture.md` (on `agents/develop`) listed `options.cljc` at 3,483 lines and
attributed `option-cfg` to it. Both were wrong — it is 4,143 here, 4,091 on `agents/develop`, and
`option-cfg` lives in `orcpub/template.cljc`. That doc was re-measured on 2026-09-15; 69 of its 89
counts were wrong and two sections described unmerged refactors as done. It is corrected now, but
the habit it came from is worth naming: **a line count in a doc is a measurement with a date on
it, not a fact.** Re-run `wc -l` before you rely on one.

## Related

- [decision-vocabulary.md](decision-vocabulary.md) — which authored keys each silo reads
- [content-extensibility-framework.md](content-extensibility-framework.md) — the pool/grant layer
  that sits on stage 1→2
- `perf-entity-build.md` *(on `agents/develop`)* — stage 3→4 is the hot path
