# Authoring vocabulary vs storage — why these little tables exist

A recurring shape in this codebase: a small map whose keys are what a homebrew author *writes* and
whose values are how the engine *checks or stores* it.

```clojure
(def tag->flag                                     ; weapons.cljc
  {:melee? ::melee?  :ranged? ::ranged?  :thrown? ::thrown  …})

(def ac-conditions                                 ; options.cljc
  {:armor?  (fn [{:keys [armor]}]  (some? armor))
   :shield? (fn [{:keys [shield]}] (some? shield))})
```

They look like ceremony. They are not, and this page is why — written because a dev asked "what is
going on here" and the answer turned out to be load-bearing.

## The syntax first

`::melee?` is Clojure's **auto-resolved keyword**. Inside `weapons.cljc` it expands to
`:orcpub.dnd.e5.weapons/melee?`. Plain `:melee?` has no namespace. So each pair is:

```
:melee?    →    ::melee?
   ↑                ↑
what an          how a weapon
author writes    actually stores it
```

A stored weapon really does look like this — every field namespaced, `:key` and `:name` not:

```clojure
{:name "Crossbow, light"  :key :crossbow-light
 ::ranged? true  ::two-handed? true  ::loading? true  ::ammunition? true
 ::damage-die 8  ::damage-type :piercing  ::range {::min 80 ::max 320}}
```

An author writing a feat writes the short form: `{:attack-bonus {:bonus 2 :ranged? true}}`.

## Why the map can't be derived

The obvious shortcut is `(keyword "orcpub.dnd.e5.weapons" (name tag))`. It works for ten of the
thirteen. Three are spelled differently:

```clojure
:thrown?     → ::thrown        ; no ?
:versatile?  → ::versatile     ; no ?
:reach?      → ::reach         ; no ?
```

Those names were set years ago and are in every weapon and every saved character, so they cannot be
tidied. A derived lookup would silently return nil for those three — the bonus just would not apply,
with no error and nothing in a test unless someone thought to check those exact tags.

That is what the docstring means:

> The tags are the authoring vocabulary and the fields are storage; keeping the map explicit means
> renaming one never silently breaks the other.

**The table absorbs a real inconsistency and gives authors a clean vocabulary over messy storage.**
It is the same job D10 does for content keys — identity from a stable key, never a display name.

## The shared semantics: three-state, unknown-ignored

Every one of these vocabularies is three-state:

| authored | meaning |
|---|---|
| `true` | only when the condition holds |
| `false` | only when it does NOT |
| absent | either way |

And unknown tags are **ignored, not failed**. `weapons/matches?` says why:

> Unknown tags are ignored rather than silently failing the match, so old content with a tag this
> build does not know still applies its bonus.

That is forward compatibility for `.orcbrew` packs: a pack authored on a newer build, opened on an
older one, still applies everything the older build understands. Iterating the *table* rather than
the author's map gets this for free — and skips non-condition keys like `:bonus` with no special
case. Iterating the author's map would need an explicit escape for both.

## What the values are, and why they differ

| table | value | because |
|---|---|---|
| `weapons/tag->flag` | a **field key** to read off the weapon | every weapon tag is "does this record have this flag" |
| `opt5e/ac-conditions` | a **predicate** over the situation | "is armor equipped" is not a field on a record |

Same shape, same purpose — one place that says what the authoring vocabulary is — different because
the subject differs.

## When to build one of these (and when not)

The count is not the argument. `ac-conditions` was worth building at **two** entries, because the
tag list was living in three places that had to agree:

1. the predicate's destructure (`options.cljc`)
2. the form fields (`builder_fields.cljc`)
3. the drift test's hardcoded set (`builder_fields_test.cljc`)

Adding a condition meant three coordinated edits, and forgetting #1 meant the form wrote data the
compiler ignored — silently, which is exactly what that drift test exists to catch. The table makes
the engine's vocabulary one thing and lets the test *ask* it.

So the test is **"how many places must agree?"**, not "how many entries are there." A table that
replaces a clear two-branch `and` and nothing else is the `by-parent` mistake (D12) — thinner than
what it hides. A table that collapses three copies into one is not.

**The form stays a curated subset, deliberately.** `weapons.cljc` says so in its own comment — *"The
predicate supports every weapon flag; the form exposes the ones…"*. Not every tag deserves a
dropdown. Registering a condition in the engine and exposing it in a builder are two separate,
optional steps.

## Growing one

Adding a tag is one entry — *if* the predicate can already see what it needs.

`:dual-wield?` cannot be added yet, and the reason is worth understanding: `armor-class/reconcile`
invokes every AC contributor as `(f armor shield)`, so a predicate can only ever see the equipped
armor and shield. The wielded weapons never reach it. `:two-weapon-ac-1` is hardcoded as a
hand-written modifier (`options.cljc`, `dual-wield-ac-mod`) precisely because of this — the
hand-written path reads `?main-hand-weapon` directly off the built character, which the declarative
path cannot.

So `:dual-wield?` needs the **context to widen first** — one change to `reconcile`'s contract and the
~7 contributors — and then it is one entry in `ac-conditions` plus one optional field. The table is
not what is blocking it; the contract is.

## Where these live

- `weapons/tag->flag` — 13 weapon tags. Serves `:attack-bonus` and `:damage-bonus`.
- `opt5e/ac-conditions` — 2 AC conditions. Serves `:ac` and `:ac-bonus`.
- `content_types.cljc`, `grant_pools.cljc` — the same principle at content scale: one entry, many
  layers generated from it.
