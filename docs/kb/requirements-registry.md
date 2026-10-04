# Requirements — the facts content asks about

`src/cljc/orcpub/dnd/e5/requirements.cljc`

A named fact about a character that an effect can gate on: *while wielding two weapons*, *while
wearing no armor*. One entry per fact, in one place, so a feat, a fighting style and a class feature
all ask the same question the same way instead of each re-deriving it.

```clojure
(mod5e/ac-bonus {:dual-wielding? true} 1)     ; the Dual Wielder feat
(mod5e/ac-bonus {:armored? false}     5)      ; Robe of the Archmagi
```

Authors reach it as data — `{:props {:ac-bonus {:bonus 1 :dual-wielding? true}}}` in an `.orcbrew`,
or the "Weapon requirement" dropdown in the builder.

## An entry

```clojure
:dual-wielding? {:gate :build
                 :text "while wielding two weapons"
                 :pred (fn [{:keys [main-hand off-hand]}]
                         (and (some? main-hand) (some? off-hand)))}
```

| key | |
|---|---|
| `:gate` | `:build` — derivable from the sheet; the engine runs `:pred`.<br>`:toggle` — the player asserts it (the `equipped?`/deferred pattern); real math while on.<br>`:text` — a **trigger**: a moment in play. Carries **no** `:pred`. |
| `:text` | the phrasing that reaches the sheet and the PDF. Every entry has one — `:props` compiles to mechanics only, so without this a mechanic prints nowhere. |
| `:pred` | a function of a context map. `:build` and `:toggle` only. |

A trigger having no `:pred` is structural, not a convention: there is nothing to call, so nothing can
accidentally claim to compute a moment. (`builder-form-schemas.md` §4 on why triggers are not
computable here.)

## Three-state, and unknown keys are ignored

| authored | means |
|---|---|
| `true` | only while it holds |
| `false` | only while it does **not** |
| absent | either way |

`meets-all?` iterates **the registry**, not the author's map. Two things follow: a key this build
does not know is ignored rather than failing — so a pack authored against a newer build still applies
what this one understands — and non-requirement keys like `:bonus` need no special case.

`false` is a real value here, so absent and false must stay distinguishable. Test with `contains?`,
never truthiness. (A first cut resolved a legacy alias with `some`, which skips falsey values;
`{:shield? false}` read as absent and a Monk kept Unarmored Defense while holding a shield.)

## Why entries hold predicates, not condition forms

The modifier system has its own condition slot, and a hand-written modifier uses it:

```clojure
(mods/vec-mod ?ac-bonus-fns (fn [_ _] 1) nil nil
              [(and ?main-hand-weapon ?off-hand-weapon)])    ; ← a condition FORM
```

That looks like the natural thing for a registry to hold. It cannot be, because `es/conditions` is a
macro that captures those forms at compile time:

```clojure
(defmacro conditions [conds] (mapv (fn [cond] `(condition ~cond)) conds))
```

A form captured at compile time cannot be selected from a map at runtime. So a registry of condition
forms is unbuildable without changing the macro layer.

What works instead: **the macro assembles a plain-data context, and the registry holds ordinary
functions over it.** `mod5e/ac-bonus` splices the `?`-refs into the body it generates —

```clojure
{:armor armor :shield shield
 :main-hand ?…/main-hand-weapon
 :off-hand  ?…/off-hand-weapon}
```

— and every `:pred` is a plain fn of that map. Nothing in `requirements.cljc` is macro-aware, which
is also why it can be a dependency leaf.

The general rule behind this is in `built-character-representation.md` ("the other half — writing"):
`?attr` is rewritten at compile time, so only code a macro *expands* can read one.

## Adding a requirement

1. **One entry** in `requirements.cljc`.
2. **If the predicate needs a fact the context does not carry**, add it to the map in the macro that
   builds the contributor — `mod5e/ac-bonus` today. Two edits, deliberately: the context and the
   registry are the two halves of one change.
3. **Exposing it in a builder is separate and optional.** The engine supports every entry; a form
   offers a curated subset — the rule `weapons.cljc` documents for weapon tags. `ac-bonus-fields`
   carries the dropdown for `:dual-wielding?` because published content wants it.

A form implying arithmetic offers `:build`/`:toggle` entries; a form printing a trigger offers
`:text`. Same registry, different filter.

## Scope today

Four `:build` entries — `:armored?`, `:shielded?`, `:dual-wielding?`, `:one-handed?` — read by AC
bonuses and AC calculations. `:toggle` and `:text` are declared in the vocabulary and have no
entries yet; the damage and attack channels do not reach the registry yet.

The registry uses the spellings `:ac-bonus` has shipped with — `:armor?`, `:shield?`. Renaming
them would have read marginally better beside `:dual-wielding?` and cost a permanent alias on
released data for nothing, so they keep their names (D9). There is no alias layer.

## Not to be confused with prereqs

A **prereq** gates acquisition — may you take this option at all, checked when choosing, with an
explanation for why it is greyed out (`option-prereq`). A **requirement** gates application — does
the effect apply right now. A feat can have either, both, or neither.

---

# Design record

Everything below is how the above was arrived at. Read it before proposing a change to the shape.

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

## The naming, and what lost

`requirement` reads correctly from the effect's side — *"this bonus requires you to be dual
wielding"* — and `meets?` is the accessor that made it land.

| rejected | why |
|---|---|
| `conditions` | **the best English word, and taken** — 5e's game term, already in the code as `:condition-immunity` (`modifiers.cljc:85`, registered `:695`). Two unrelated things behind one word is the `:from`/`:choose` mistake |
| `requires` | weak objection, recorded honestly: `:orcbrew/requires` is a NAMESPACED key in pack-envelope metadata, a different layer entirely — not a real collision. `requirement` won on its own merits, not because this ruled `requires` out |
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

## The blocker that was not one

An earlier version of this page said the channel contract had to widen first: `armor-class/reconcile`
invokes contributors as `(f armor shield)`, so a predicate could never see the wielded weapons, and
~13 contributors would need a new arity.

**Wrong — and the escape was where `dual-wield-ac-mod` already stood.** `reconcile` is called from
`template_base.cljc`, inside the entity-spec macro context, so `?main-hand-weapon` resolves *there*.
A macro can splice those refs into the contributor's own body and assemble the context inside it:

```clojure
(defmacro ac-bonus-meeting [spec n]                    ; modifiers.cljc
  `(mods/vec-mod ~'?ac-bonus-fns
                 (fn [armor# shield#]
                   (if (reqs/meets-all? ~spec {:armor armor# :shield shield#
                                               :main-hand ~'?…/main-hand-weapon
                                               :off-hand  ~'?…/off-hand-weapon})
                     ~n 0))))
```

Predicates stay ordinary runtime fns, the channel contract is untouched, and the other contributors
never changed. **Adding a fact to the context is one line in that map plus one registry entry.**

## What landed

- `requirements.cljc` — pure leaf, four `:build` entries, `meets-all?`, `offerable`.
- `mod5e/ac-bonus-meeting` — the context-assembling macro; `ac-bonus-modifiers` uses it.
- `opt5e/ac-applies?` delegates to `meets-all?`; the AC-only condition table is gone.
- The registry uses the shipped spellings `:armor?` / `:shield?` — no rename, so no alias layer.
- `:dual-wielding?` and `:one-handed?` are authorable — the first weapon-aware requirements — and
  `:dual-wielding?` is offered by the form.
- **Proven through a built character**, not just at the predicate: `bracers_ac_test` asserts the
  authored prop, the deprecated `:two-weapon-ac-1` key, and the converted Robe of the Archmagi each
  move the AC on the sheet (13/12/12 wielding, 17/13 robed). Compiling was never the claim.

**A regression the characterization sweep caught, worth keeping:** the first legacy-alias lookup used
`some`, which skips falsey values, so `{:shield? false}` read as *absent* rather than *only-when-not*
and a Monk kept Unarmored Defense while holding a shield. `false` is a meaningful value in a
three-state vocabulary; absent and false must stay distinguishable, hence `contains?`.

## The registry now REPLACES something (2026-09-08)

`:two-weapon-ac-1` compiles to `{:ac-bonus {:bonus 1 :dual-wielding? true}}`; `dual-wield-ac-mod` is
`#_`-struck per D34, pinned by `ac_reconciliation_test` SECTION 4 — the slot that file had reserved
for exactly this. Three magic-item bonuses (two ioun stones, Robe of the Archmagi's `(if (nil? armor)
5 0)` → `{:armored? false}`) are declarative now too.

**One mechanism, after two wrong calls.** `ac-bonus-fn` and the new macro were first reported as
duplication (D29), then defended as primitive-plus-constructor on the strength of a computed-value
case — `(if (and (nil? shield) (nil? armor)) (?ability-bonuses char5e/con) 0)`. That case is in
`#_`-commented UA code. **`ac-bonus-fn` has zero live callers**: the three magic-item bonuses were
converted, and all five remaining sites are inside `#_` blocks that never compile. So the first call
was right, the defense was built on dead code, and there is now one macro — `mod5e/ac-bonus [spec n]`,
struck predecessor in the ledger. The caller count in the original audit was also wrong: the glob
missed `templates/`.

**`ac-bonus-meeting` was a bad name** and is gone; `mod5e/ac-bonus` matches the `:ac-bonus` prop it
compiles.

## Still to do

1. The damage/attack channels need the same macro treatment to reach the registry — today only AC
   bonuses do. That is the second consumer that proves the registry rather than assuming it.
2. ✅ **Exposed in the form** — `ac-bonus-fields` carries a "Weapon requirement" dropdown, so an
   author picks `:dual-wielding?` in the builder rather than only in hand-written EDN. Registering
   and exposing stay separate steps; this one is exposed because published content wants it.
3. `:toggle` and `:text` entries when a real case wants them. The gates are declared in the
   vocabulary and have no entries; a test asserts that plainly rather than propping up empty
   structure.
