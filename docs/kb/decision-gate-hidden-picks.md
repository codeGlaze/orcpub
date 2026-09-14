# Decision: gate hidden picks at build time, and tell the player

*DESIGN — nothing built. Fixes the defect in [hidden-selection-picks.md](hidden-selection-picks.md).
Evidence measured 2026-09-14, `feature/grant-rows` at `ec04da1b`.*

## Decision

**A pick stored under a selection whose `prereq-fn` fails must stop applying, and the character
must say so.** Chosen over grandfathering by save version, and over reporting without fixing.

Rationale: the rule is the rule, and a character carrying a skill with no control to remove it is
worse than one that loses a skill and is told why. Existing characters are corrected on their next
build. The notice is what turns "where did my skill go" into an answer.

## Where the gate goes

`apply-options` (`entity.cljc:661`) is the only place options become modifiers:

```clojure
(defn apply-options [raw-entity template]
  (let [options   (flatten-options (::options raw-entity))
        modifiers (sort-by ::mods/order (collect-modifiers-2 raw-entity options template))
        …]
    (mods/apply-modifiers base ordered-mods)))
```

The gate belongs between `flatten-options` and `collect-modifiers-2`: drop options whose owning
selection is disqualified, exactly as `remove-disqualified-selections` (`:536`) already does for
the builder.

## It is a filter for 7 of 9 sites — RETRACTED fixed-point claim

*An earlier version of this section said the gate was a two-pass fixed point that would double
`entity/build`. That was wrong, and the cost estimate with it.*

**The error:** `?classes` is written by a modifier —
`(mods/modifier ?classes (conj ?classes cls-key))` — so I concluded `first-class?` needs a built
character. The modifier *carries* the class order; it does not compute it.

**VERIFIED** (`multiclass_hidden_pick_test.clj`):

```
raw [fighter rogue]  ->  built ?classes [:fighter :rogue]
raw [rogue fighter]  ->  built ?classes [:rogue :fighter]
raw [rogue]          ->  built ?classes [:rogue]
```

Class order is exactly the raw `:class` vector order. So `first-class?` is answerable off the raw
entity with no build at all:

```clojure
(= class-kw (first (map ::entity/key (:class options))))
```

| gate | reads | needs a build? |
| --- | --- | --- |
| `first-class?` / `(complement …)` — both class skill arms, all five starting-equipment sites (**7**) | raw class order | **no** — a plain filter |
| `background-skills-cfg` replacement (**1**) | `?skill-profs`, real modifier output | **yes** |
| caller-supplied on `skill-selection-2`, `tool-*-selection-2` (**2**) | whatever the caller passes | unknown until each caller is checked |

**How it works today:** one build, then `get-all-selections-2` filters the selection list *for
display* against that built character. Nothing builds twice. That is precisely why the bug exists
— the filter is display-only and never feeds back into what gets applied.

**Consequence for the fix.** Seven sites need only a raw-data predicate applied before
`collect-modifiers-2`, which is cheap and has no ordering problem. The background site genuinely
needs the built character and is the only place where a second pass, or a narrower
`unless-held-by-other` condition on its own modifiers, is required. Scope it as *one filter plus
one special case*, not as a rebuild of the hot path.

## The mug does not cover this

**VERIFIED.** The homebrew override affects `count-remaining` (`entity.cljc:790`):

```clojure
homebrew? (get-in character [::homebrew-paths actual-path])
…
(cond homebrew? 0        ; waives min/max
```

It waives **how many** you may pick. `remove-disqualified-selections` takes only
`[selections built-char]` — no character, no homebrew paths — so the mug **cannot un-hide a
gated selection**, and after this fix a player who wants the fifth rogue skill has no route back.

**CORRECTED.** An earlier version said a player would have "no route back" after the fix. Wrong:
the *other* arm is still on screen. Hide the rogue's multiclass skill pick and the first-class
"choose 4" selection remains — mug that and take a fifth skill. Same for equipment: the surviving
class's starting-equipment selections are still there to over-pick.

What the fix removes is the specific stale pick, not the capability. So widening the mug to bypass
`prereq-fn` is **not** required to keep the pressure valve working, and the case for doing it is
much weaker than this doc first claimed. Left **OPEN** only as a question of whether re-picking
through the other arm is good enough UX.

## The paper trail: a new attribute, not an existing text field

The notice should be durable on the character, and **not** written into anything a player owns.

**VERIFIED from the schema** (`db/schema.clj`):

| field | history | verdict |
| --- | --- | --- |
| `::char5e/notes` | `:db/noHistory true` (`:234`) | **worst candidate** — an overwrite is unrecoverable, which is the same hazard [multi-tab-character-contamination.md](multi-tab-character-contamination.md) records |
| `::char5e/description` | declared **twice** — `fulltext-prop` (noHistory) at `:262` and `string-prop` (history kept) at `:278` | latent schema inconsistency; effective value needs checking against a live DB |
| `bonds`, `ideals`, `flaws`, `personality-trait-*` | plain `string-prop`, history kept | safe to write, wrong place — they are roleplay fields |

Appending to any of them means string concatenation into content someone is editing, which is
exactly the accident to avoid.

**DESIGN — use a new attribute instead:**

```clojure
{:db/ident ::char5e/repair-log
 :db/valueType :db.type/string
 :db/cardinality :db.cardinality/many}
```

`:db.cardinality/many` makes it **append-only by construction** — each entry is its own assertion,
so nothing can be overwritten and no concatenation logic has to be careful. History is kept
(no `:db/noHistory`), so the log is recoverable. One entry per repair:

```
"2026-09-14 — removed Athletics: granted by the rogue's multiclass skill choice, which no
 longer applies now that rogue is your first class."
```

**Surfacing — a transient notice is not enough.** An earlier draft said "wherever character
notices already appear", which means a toast the player can miss, and a paper trail nobody can
find is the same invisibility that made this defect hard to track down. The log needs a **durable,
readable place on the character** — its own section — with the load-time notice as a pointer to
it, not as the only sighting.

**OPEN:** whether the log is also shown on the PDF. Probably not by default — it is provenance,
not character content.

## Scope

Ordered, each step its own commit with a green gate.

1. **Characterize all nine sites.** Build a character exercising each `prereq-fn`, pin what
   applies today. The multiclass and background cases exist
   (`multiclass_hidden_pick_test.clj`, `conditional_selection_ref_test.clj`); the other seven do
   not. **Nothing changes until this is green** — it is what proves the fix does not take away
   something it should not.
2. **Decide the mug question.** It changes what the fix means for a player and should not be
   retrofitted.
3. **The gate itself**, in `apply-options`, two-pass, with the convergence check. Measure
   `entity/build` before and after against `entity_build_perf_test`.
4. **The repair log attribute** plus a migration, and the write from the gate.
5. **Surface it** — a notice on load for affected characters, reading from the log.

Steps 1 and 2 gate everything else. Step 3 without step 1 is a silent change to every saved
character in the app.

## Related

- [hidden-selection-picks.md](hidden-selection-picks.md) — the defect, measured, with the
  multiclass reproduction
- [decision-already-held-resolution.md](decision-already-held-resolution.md) — the design that
  would multiply conditional selections from nine sites to every grant, which is why this is
  urgent rather than tidy
