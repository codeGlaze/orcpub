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

## ⚠️ It is a fixed point, not a filter

**This is the finding that sets the cost.** `prereq-fn` takes the **built character**. You cannot
know whether a gate passes until you have built, and you cannot build correctly until you know the
gate. So the fix is two-pass:

```
pass 1   build permissively, as today          → a character to evaluate gates against
gates    evaluate every selection's prereq-fn against that character
pass 2   rebuild, dropping options under failing selections
```

Same shape as the CR → PB → CR loop in
[plan-npc-statblock-customizer.md](plan-npc-statblock-customizer.md), and it needs the same care.

**The invariant that makes two passes enough:** *no selection's gate may depend on what that
selection grants.* Checked against the nine live sites:

| gate | depends on | stable under the fix? |
| --- | --- | --- |
| `first-class?` / `(complement first-class?)` (6 sites) | class order | **yes** — skills do not change class order |
| `background-skills-cfg`'s replacement | is *this* skill held by another source | **yes in practice** — the replacement grants a *different* skill, so dropping it cannot change whether the original is held |
| caller-supplied on `skill-selection-2`, `tool-*-selection-2` | whatever the caller passes | **unknown** — depends on the caller |

So two passes converge today. That is a property of the current nine, **not a guarantee** — it
should be stated as an invariant and tested, not assumed. A third pass that changes nothing is the
cheap check.

**Cost consequence:** `build-aux` is memoized and `entity/build` is the hot path
(`perf-entity-build.md`: 25 ms → 4.9 ms in the browser, and the sort alone was 74% of it).
Two passes is a doubling of the most expensive operation in the app. Measure before and after;
gate evaluation may be cheap enough to fold into one pass for the six `first-class?` sites, which
depend only on data available before modifiers run.

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

**OPEN, and it wants deciding with the fix, not after:** widen the mug to bypass `prereq-fn` as
well as the count, or accept that a which-arm rule is not waivable per selection. Widening makes
the pressure valve cover the case; not widening means the only route is a custom feature. The
first matches what the override is *for*; the second keeps its definition narrow
([homebrew-override.md](homebrew-override.md): it waives selection rules, never computed values —
and a gate is a selection rule).

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

Surfaced wherever character notices already appear, not inside an editable field.

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
