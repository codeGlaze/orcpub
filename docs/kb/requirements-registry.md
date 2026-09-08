# The requirements registry — DESIGN, not built

**Status: agreed shape, nothing written.** One place naming the facts about a character that content
asks about — *while wielding two weapons*, *while wearing no armor*, *when you hit with a melee
attack* — so any effect can reference one instead of each feature re-deriving it by hand.

Decided 2026-09-08 across a design conversation; this is the record so it is not re-derived.

## Why: the same fact is hand-written in three places today

`is a second weapon in hand` is spelled out independently in at least three features, in three
silos, feeding **two different effect channels**:

| feature | silo | reads | feeds |
|---|---|---|---|
| Dual Wielder (`dual-wield-ac-mod`, `options.cljc:1438`) | feat | `?main-hand-weapon` + `?off-hand-weapon` | `?ac-bonus-fns` |
| Dueling Fighting Style (`options.cljc:2010`) | fighting style | `?off-hand-weapon` — the **inverse**, "no other weapons" | `?damage-bonus-fns` |
| `dual-wield-weapon-mod` | feat | the same lookup again | weapon eligibility |

Plus `template_base.cljc` threads `off-hand?` through the damage pipeline as a parameter.

So this is not "a condition on an AC bonus." It is a **named fact about the character that unrelated
content asks about**, which is what a registry is for. `opt5e/ac-conditions` (2 entries, built
2026-09-08) is one *consumer* of that idea, not its home.

## The shape

```clojure
(def requirements
  {:dual-wielding? {:gate :build  :text "while wielding two weapons"
                    :pred (fn [c] (and (main-hand c) (off-hand c)))}
   :one-handed?    {:gate :build  :text "while wielding a weapon in one hand and no other"
                    :pred (fn [c] (and (main-hand c) (not (off-hand c))))}
   :unarmored?     {:gate :build  :text "while wearing no armor"
                    :pred (fn [c] (nil? (armor c)))}
   :raging?        {:gate :toggle :text "while raging"}
   :on-hit-melee   {:gate :text   :text "when you hit with a melee weapon attack"}})

(defn meets? [c k] …)          ; the accessor: (meets? character :dual-wielding?)
```

Read by AC bonuses, damage bonuses, attack bonuses, trait applicability, and the "scenario"
dropdown in a template.

### The three gates

| gate | what it is | who decides | computes? |
|---|---|---|---|
| `:build` | a fact derivable from the sheet | the engine | yes, always |
| `:toggle` | a fact the player asserts about now | the player, via the `equipped?`/deferred pattern | yes, while on |
| `:text` | a **moment in play** — a **trigger** | nobody | **no** |

This is `runtime-toggles-and-conditional-modifiers.md`'s own three-way split, made data. **The
absence of `:pred` is what makes a trigger a trigger** — structural, so nothing can accidentally
claim to compute a moment. `builder-form-schemas.md` §4 stands: no trigger DSL, no combat simulator.

### Why `:text` entries still earn their place

1. **They write the sentence.** `:props` emits mechanics only — the sheet and the **PDF** get the
   author's hand-typed prose, which can drift or be skipped. A registry entry supplies the phrasing.
2. **A picklist instead of free prose** — homebrew reads like the rulebook and like other homebrew.
3. **It keeps the door open.** An author who picks `:on-hit-melee` stored a KEY; one who typed
   "whenever you land a hit" stored a string. If the app ever tracks combat, keyed content becomes
   computable for free — the string never can. Same argument as D10.

A gate is also **upgradable per entry**: `:flanking?` is `:text` today because nothing tracks
positioning; if that changed, one entry flips to `:build` and every referencing item starts
computing, with no re-authoring.

### Curation, not exposure

`weapons.cljc` already documents the rule — *"The predicate supports every weapon flag; the form
exposes the ones…"*. Registering a requirement and offering it in a builder are separate, optional
steps. A form saying "+[N] AC while [X]" filters to `:build`/`:toggle`; one saying
"**Reaction:** [trigger] — [effect]" offers `:text`.

## The naming, and what lost

`requirement` reads correctly from the effect's side — *"this bonus requires you to be dual
wielding"* — and `meets?` is the accessor that made it land.

| rejected | why |
|---|---|
| `conditions` | **the best English word, and taken** — 5e's game term, already in the code as `:condition-immunity` (`modifiers.cljc:85`, registered `:695`). Two unrelated things behind one word is the `:from`/`:choose` mistake |
| `requires` | `:orcbrew/requires` is the format envelope's feature list (`orcbrew_format.cljc:155`), in every v2 pack header |
| `states` | genuinely close, and `has-state?` is a fine accessor. Lost on point of view: *state* describes the character, *requirement* describes what the effect demands — and it read wrong for the trigger subset |
| `constraint` | wrong sense — these qualify when something applies, they do not restrict |
| `criterion`/`criteria` | a standard for judging, which fits filtering; plus the singular/plural tax |
| `prereq` | taken, 119 uses, and a genuinely different gate — see below |

**Correction worth keeping:** `requirement` was first rejected by lumping it with `requires`. They
are different words and only `requires` collides. The distinction that survived is not that
"requirement" is wrong, but that *prerequisite* carries a **before** sense the plain noun does not.

## Kinship with prereqs — same shape, different gate

`prereq` gates **acquisition**: may you take this option at all, checked once when choosing, with a
user-facing explanation (`option-prereq [explanation func]`, "You already have this skill").
A requirement gates **application**: does the effect apply right now.

Orthogonal — a feat can have a prereq and an unconditional effect, or neither, or both. But the
direction doc's PIN plans a **declarative prereq vocabulary** (`has-class?`, `level>=`,
`has-feature?`, `ability>=`) which is the *same tag → predicate-over-the-character shape*, and they
genuinely overlap: `:medium-armor-max-dex-3` is "when wearing medium armor, +3 AC **if your Dex is
16+**" — an ability test used as a requirement, not a prereq.

**So whoever builds the prereq vocabulary should reuse this predicate layer, not write a second
incompatible one.** That is the whole reason this page exists.

## What blocks it

`armor-class/reconcile` invokes every AC contributor as `(f armor shield)`, so a predicate can only
see equipped armor and shield — the wielded weapons never reach it. That is why `:two-weapon-ac-1`
is a hand-written modifier: the hand-written path reads `?main-hand-weapon` straight off the built
character, which the declarative path cannot.

**The context has to widen before any weapon-aware requirement can exist declaratively** — one
change to `reconcile`'s contract and the ~7 contributors. The registry is not what blocks it.

## Sequence, when it is picked up

1. Widen the contributor context (the blocker above), pinned by the AC characterization tests.
2. Stand up `requirements` with the entries that already exist by hand, `:gate :build` only.
3. Point `opt5e/ac-conditions` at it — it becomes a filtered view, not a second table.
4. Express `:two-weapon-ac-1` and Dueling as requirements; characterization-test both against the
   hand-written versions; deprecate per D34.
5. `:toggle` and `:text` gates only when a real case wants them.
