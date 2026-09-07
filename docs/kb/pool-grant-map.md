# Pool + grant — the whole web, on one page

The map of what pool/grant actually is, what is REAL, what is still AIR, and how the open pieces
constrain each other. Written because the work had spread across six documents and three months and
nobody could hold it in mind at once — including the agent, which re-derived several already-decided
pieces from scratch. If you read one page before touching this area, read this one.

`content-extensibility-direction.md` remains the canonical decision record; this is the index to it.

## In four sentences

A **pool** is a named, open collection of grantable things — languages, fighting styles, skills,
spells. A **grant** is what any content item declares to tap one: `{:pool :languages :count 2}`.
Register a pool once and every silo can grant from it; declare eligibility on the *entry* and grants
filter on it. That is the whole idea; everything below is which parts of it exist.

## The three layers

```
  AUTHOR    builder form                          ← the grant-authoring UI  ✗ AIR
              ↓ writes
  DATA      {:grant {:pool :languages :count 2}}  ← the vocabulary          ✓ REAL
              ↓ compiled by
  ENGINE    grant-selection → selection-cfg       ← the thin compiler       ✓ REAL
              ↑ reads
            grant_pools registry → pools          ← pool + registry         ✓ REAL
```

The engine and the data path are built and tested. **Nothing writes the data** — no builder emits
`:grant`. That single gap is why the capability is invisible in the app.

## REAL — built, tested, on this branch

| thing | what it does | since |
|---|---|---|
| `content_pools/pool` | built-in ++ homebrew, over the `plugin-vals` seam | 2026-06-14 |
| `grant-selection` | the thin compiler: `{:pool :count :filter :key}` → `selection-cfg` | 2026-06-17 |
| `grant_pools.cljc` | THE registry. One entry per pool; each entry owns its own shape | 2026-09-07 |
| `::e5/grantable-pools` | assembles the registry from `plugin-vals` | 2026-09-07 |
| silos compiling `:grant` | feat, race | 06-17 / 09-07 |
| pools registered | `:languages`, `:fighting-styles`, `:skills` | 2026-09-07 |
| entry eligibility | fighting style `:classes` (absent = all) | 2026-09-02 |
| `effect-rows` | the repeatable-row builder node, for *effects* (not grants) | 2026-09-05 |

**The maintainability gate is met and measured**: registering `:skills` — the third pool — cost one
entry in `grant_pools.cljc` and nothing else.

## AIR — decided in a doc, not built

| thing | what it unblocks | decided in |
|---|---|---|
| **filter as a metadata predicate** | today `:filter` is a key set (`#{:elvish}`) and can only enumerate. As a predicate (`{:level 0 :lists :bard}`) it expresses "2 bard cantrips" and "fighter-eligible styles" with one machinery | direction doc, "Filtering is optional and graceful" |
| **`:grants` plural** | a bundle. "2 skills AND a language" is one feat; singular `:grant` cannot say it | direction doc, "Compound grants" |
| **entries carrying their own `:grants`** | Magic Initiate ("pick a class, then spells from *that* list"), and a subclass entry declaring what it grants | — (see Correction below) |
| **`:gate`** | prereqs, as part of a grant rather than a feat-only side vocabulary | direction doc, discipline 1 |
| **`:offerable-by` consumed** | declared on every pool today, read by nothing. Stops a feat offering "choose a subrace" | direction doc, discipline 2 |
| **the grant-authoring UI** | the whole point: a control that iterates *registered* pools, so registration is the only edit | direction doc, "the next lever" |
| **spells as a registered pool** | every spell-granting case | this doc, below |
| **variants** (`resolve-variants`) | `_copy`/`_mod`; identity today, deliberately | direction doc, "Variants" |

## How the open pieces constrain each other

This is the part that does not fit in one's head, so here it is as a graph rather than prose:

```
filter-as-predicate ──┬─→ spells as a pool ──→ every spell grant
                      └─→ fighting styles filterable by :classes

:grants plural ───────┬─→ grant-authoring UI
dynamic field options ┘        (options from a sub, and depending on a sibling field)

entries carry :grants ──→ Magic Initiate, subclass grants

:gate ──→ prereqs
```

Nothing here is blocked on anything unbuilt except the two arrows shown. **`filter-as-predicate` and
`:grants` plural are both leaves** — neither depends on anything, and between them they unblock
everything else.

## Spells: closer than it looks

Spell granting reads like a special case. It is not.

- ✓ homebrew spells already flow through `plugin-vals` (`::spells5e/plugin-spells`)
- ✓ a homebrew spell is **required** to carry its list membership on the entry —
  `spells.cljc:43-47`, `::homebrew-spell` = `::spell` + `::homebrew` + `::has-spell-lists`,
  shaped `{:bard true :wizard true}`. That is exactly `:classes` on a fighting style, and the
  spell builder already authors it.
- ✗ **built-ins do not** — `:acid-arrow` has no `:spell-lists`; membership lives in
  `spell_lists.cljc` as `{class {level [keys]}}`. Invert that side table once inside the pool's own
  `:options-fn` and every entry looks identical from outside. Same class of irregularity as
  fighting styles' pre-compiled built-ins, and absorbed the same way.
- ✗ no spell → `option-cfg` constructor: spells compile via `spell-selection`/`spells-known`.

So: **one `:spells` pool, list membership on the entry, grants filter on it.** Not six
list-shaped pools.

## Where the confusion came from (so it does not recur)

Three claims were made in this area and then withdrawn. All three came from designing before
grepping.

1. *"A race cannot offer a language choice."* False — `:profs :language-options` always could. The
   real defect was five storage shapes for one question, not a missing capability.
2. *"Fighting styles are shaped differently, so the registry needs a `:built-in-compiled?` key."*
   That is the D14 god-function trap hoisted one level. A pool's entry is a function; it absorbs its
   own irregularity and nothing outside can tell.
3. *"Magic Initiate is a dependent two-level choice that breaks the model."* It is a nested grant.
   Pool entries already carry their own mechanics (fighting styles carry `:props`); letting an entry
   carry `:grants` is one shared extension, not an exception.

The pattern in all three: the answer was in `content-extensibility-direction.md` or in the code, and
was re-derived worse. **Grep the KB before designing.**

## Suggested order (not a commitment)

1. **filter as a predicate** — a leaf, no UI, unblocks spells and makes `:classes` filterable
2. **`:grants` plural** — a leaf; a mechanical rename while nothing writes it yet
3. **spells as a pool** — needs 1
4. **entries carry `:grants`** — Magic Initiate stops being special
5. **the grant-authoring UI** — needs 2 + dynamic field options; built against feat, because feat is
   the only silo needing the full set, so building it correctly *is* building the shared node
6. **`:gate`** — prereqs, last

Steps 1 and 2 are independent of each other and of everything else.
