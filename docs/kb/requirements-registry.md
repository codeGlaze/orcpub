# The requirements registry — DESIGN, not built

**Status: BUILT 2026-09-08** — registry, `meets-all?`, `mod5e/ac-bonus-meeting`, and
`:dual-wielding?` / `:one-handed?` authorable. `:toggle` and `:text` gates are declared and
enforced by tests but have no entries yet. One place naming the facts about a character that content
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
- Legacy `:armor?` / `:shield?` read forever (D9); the form writes `:armored?` / `:shielded?`.
- `:dual-wielding?` and `:one-handed?` are authorable — the first weapon-aware requirements.

**A regression the characterization sweep caught, worth keeping:** the first legacy-alias lookup used
`some`, which skips falsey values, so `{:shield? false}` read as *absent* rather than *only-when-not*
and a Monk kept Unarmored Defense while holding a shield. `false` is a meaningful value in a
three-state vocabulary; absent and false must stay distinguishable, hence `contains?`.

## The registry now REPLACES something (2026-09-08)

`:two-weapon-ac-1` compiles to `{:ac-bonus {:bonus 1 :dual-wielding? true}}`; `dual-wield-ac-mod` is
`#_`-struck per D34, pinned by `ac_reconciliation_test` SECTION 4 — the slot that file had reserved
for exactly this. Three magic-item bonuses (two ioun stones, Robe of the Archmagi's `(if (nil? armor)
5 0)` → `{:armored? false}`) are declarative now too.

**One thing audited and found NOT to be duplication.** `ac-bonus-fn` and `ac-bonus-meeting` look like
two mechanisms on one channel (D29), and were reported as such. They are not: a bonus whose VALUE is
computed from the character — `(if (and (nil? shield) (nil? armor)) (?ability-bonuses char5e/con) 0)`
— cannot be written as `(spec, n)`. `ac-bonus-fn` is the primitive; `ac-bonus-meeting` is the
declarative constructor for the common fixed-number case. Both kept, and the docstrings say which to
reach for. *The count of callers in that audit was also wrong — the glob missed `templates/`.*

## Still to do

1. The damage/attack channels need the same macro treatment to reach the registry — today only AC
   bonuses do. That is the second consumer that proves the registry rather than assuming it.
2. Expose the requirements in `ac-bonus-fields` so an author can pick them (curation, not automatic).
   Until then `:dual-wielding?` is reachable from authored data but not from the form.
3. `:toggle` and `:text` entries when a real case wants them. The gates are declared in the
   vocabulary and have no entries; a test asserts that plainly rather than propping up empty
   structure.
