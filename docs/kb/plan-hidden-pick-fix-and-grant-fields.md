# Plan: fix the hidden-pick bug, then complete the grant compiler

*Two pieces of work that are often confused for one. Written 2026-09-14 after a long
investigation that oscillated between them; the separation is the point of this doc.*

## Position, stated once

**The bespoke skill/tool code should become pool + grant.** Roughly a dozen near-duplicate
selection builders, a per-silo compiler, and six sets of `:profs` readers all do the same thing:
*N things from a set, filtered, gated*. That is what a pool and a grant are.

**The gap is four fields and one argument, not a redesign.** An earlier claim in this
investigation — that skills and tools "break the pool system over its knee" — was overstated. What
was actually found is narrower: today's pool entry is context-free and proficiency is relational.
That is a gap in the implementation, not a mismatch in the concept.

**But the bug fix does not wait for it.** The hidden-pick defect
([hidden-selection-picks.md](hidden-selection-picks.md)) is shipped and live. Fixing it through the
migration means wiring the class silo for grants, adding fields, converting both arms and
characterizing — four pieces before one bug is fixed. Fixing it directly is five functions and no
new concepts. **Ship the fix; do the unification as its own work.**

## Part A — the bug fix

### Why it cuts from `integration`

**VERIFIED 2026-09-14.** The code it touches is byte-identical on `integration` and
`feature/grant-rows`: `skill-selection-2`, `class-skill-selection` and `skill-selection` all match,
as do all 15 `class-skill-selection`/`tool-prof-selection` call sites and all 5 equipment
`first-class?` gates.

So it applies cleanly as a neutral patch on `integration` and flows down —
`integration → refactor/content-extensibility → feature/grant-rows` — rather than being trapped
behind a refactor branch.

### The change

Attach the condition the **fixed** path already uses to the **chosen** path's modifiers. The two
arms take opposite conditions:

```clojure
;; today — the condition exists only as a display filter on the selection
(defn class-skill-selection [{…} key prereq-fn]
  (skill-selection skill-kws skill-num skill-select-order key prereq-fn))
;;   options' modifiers: (modifiers/skill-proficiency skill-kw source)   ← nothing

;; fixed — cls-kw threaded, condition matches the arm
(modifiers/skill-proficiency skill-kw source [(= cls-kw (first ?classes))])      ; first-class arm
(modifiers/skill-proficiency skill-kw source [(not= cls-kw (first ?classes))])   ; multiclass arm
```

`skill-proficiency` already takes a `conditions` argument
(`(defmacro skill-proficiency [skill-kw & [source conditions]])`) — it is simply unused on this
path.

### Blast radius

| | |
| --- | --- |
| code path touched | every character with a class — the most travelled path in the app |
| behaviour changes for | only characters carrying a stale pick |
| worst-case bug | `cls-kw` arrives `nil` → `(= nil (first ?classes))` is false → **every** chosen class skill vanishes, silently |
| second hazard | the two arms need opposite conditions; swapped, every multiclassed character loses a skill |

That asymmetry — universal path, narrow intended change — is why characterization comes first.

### Order

1. **Characterize** current skills across single-class, multiclass, and reordered configurations.
   `multiclass_hidden_pick_test.clj` has the reorder case; extend it to the matrix.
2. **Thread `cls-kw`** through `class-skill-selection` → `skill-selection` → `skill-selection-2`.
3. **Attach the conditions**, per arm.
4. **Diff** the characterization. Only stale picks should move.
5. **Repeat for equipment and tools**, same shape, one commit each.

**Characterizing class tools is not a prerequisite.** It is the same shape as the skills case and
would confirm rather than discover. Do it as part of step 5, not before step 1.

### Step 1 of the decided design: the build plan (2026-10-06)

On `fix/hidden-multiclass-skill-pick`, on top of the shared `picks` namespace (#47). One commit each,
its test green before the next. Items 7–8 wait for the header-layout design pass.

| # | commit | test |
|---|---|---|
| 1 | `picks/disqualified`, `picks/overflow`: addresses of stored picks whose selection is closed, and the newest picks over a selection's limit. The builder's own pipeline (`random-character` in `events.cljs`): `available-selections` → `combine-selections` → `count-remaining`, so one rule for showing and applying. A pick whose option the template does not offer (missing homebrew) is never reported; a closed selection sharing its storage slot with an open one is overflow, not closed; addresses under another reported address are dropped | JVM, real classes: Hunter 6 with Steel Will; Champion 9 with two styles; first-class skills on a second class; homebrew override; missing homebrew; a normal level-10 character reports nothing |
| 2 | `picks/to-planned` / `from-planned`: move reported picks out with `remove-at` (records kept in memory), put them back with `put-at` when no longer reported; nil from `put-at` retires the record. Repeat until nothing changes, with a pass limit that errors rather than guesses | JVM: 7→6→7 and 10→9→10 round-trip; re-picked meanwhile retires; convergence |
| 3 | Wire where the heals run (`set-character`, `cache-template`, the character sub); the hold in app-db, never saved; on open with ghosts, set aside and save | cljs + e2e: ranger 7→6→7, Champion 10→9→10, reload drops the hold |
| 4 | Remove the skills-only fix (per-arm conditions); its tests stay as characterization | the research tests give the same sheet |
| 5 | Stop the reset on deleting or changing the first class (flow 5 data loss) | e2e: the other class keeps its picks |
| 6 | Full checks; PR into `integration` only with items 7–8 | |

### Step 5, mapped (2026-10-01)

> **Superseded by the owner's decision of 2026-10-03** (`decision-gate-hidden-picks.md`, top): one
> set-aside path for every closed gate, held in memory, three steps. The map below stays as the
> list of sites that path has to reach.

> **Superseded in part by the tested verdicts** in `hidden-selection-picks.md`, "Verified
> 2026-10-01": D3 adds the hand-built `classes.cljc` selections and `starting-equipment-option`;
> D4 (fixed gear) is a stale-data path, not this fix; S1 adds subclass level picks. Threading each
> constructor (below) would miss the hand-built sites. The design question is now one condition
> applied over a whole selection, with its deps declared (M2); not yet decided.

**Status.** Steps 1–4 (skills) are done on `fix/hidden-multiclass-skill-pick` (`75fa9431`), which
has `integration` merged in (`5451b299`). Step 5 is the rest of the branch, then one PR into
`integration`, then down through the trunk to `feature/grant-rows` and `feature/extras-companions`
(which carries the skills fix as `5230db89`, docstrings already identical).

**5a. Class tools: a mirror of skills.** Two arms: `:tool-selection` (gate `first-class?`) and
`:multiclass-tool-selection` (bard's instrument). Today every chosen tool's modifier is the bare
`(modifiers/tool-proficiency (:key tool))`, unconditioned.
1. Characterize: extend `multiclass_hidden_pick_test` with a class whose tool pick is made first,
   then the class moved second; the bard instrument arm both ways; a single-class row that must
   not move.
2. Add `class-tool-proficiency [tool-kw cls-kw first-class?]` beside `class-skill-proficiency`,
   both arms with literal conditions. Do not reuse `tool-proficiency`: its false branch is
   unconditioned on purpose (fixed multiclass profs apply under either arm).
3. Thread `cls-kw` + arm through `tool-prof-selection` → `tool-prof-selection-aux` (the nested
   "Tool Proficiency: X" pick for grouped tools). Non-class callers keep the bare modifier.
   Enumerate the callers two ways (call syntax, and any `partial`/var reference).
4. Pin what the multiclass arm's gate reads: it is `(:classes c)` (`options.cljc`, multiclass tool
   selection) where every other gate uses `character/classes`. The test shows whether they agree.

**5b. Starting equipment: first-class arm only, one prerequisite.** `modifiers/weapon`, `armor` and
`equipment` are `mods/map-mod`, which takes no conditions, unlike `mods/modifier`. So:
1. Characterize: the Fighter/Rogue Explorer's Pack row exists; add a weapon pick, an armor pick, a
   nested group (pack, holy symbol), a class with rich `:equipment-selections`, a homebrew class,
   and a single-class row that must not move.
2. Give `map-mod` an optional conditions argument with `modifier`'s contract (literal at the call
   site, because `es/condition` rewrites `?classes` at macroexpansion), or class-only wrappers over
   it. Decide by which keeps non-class callers untouched.
3. Condition every place a chosen starting item is emitted, enumerated by what emits the item, not
   by the gate: `weapon-option-2`, the simple/martial picks through `weapon-options` (shared with
   non-class callers: needs a class variant), `armor-option` (has no class key: thread it from
   `class-armor-options`), `equipment-option` (recursive for groups), `equipment-grant->modifier`
   and the `:any-weapon` sub-choice (rich `:equipment-selections`).
4. Verify, don't assume, the fixed gear: `:associated-options`
   (`class-starting-equipment-entity-options`) is not among the nine sites. Pin that it already
   follows the first class; fix it only if the pin fails.

**Hazard, both parts:** a `nil` class key makes the condition false and every chosen tool or item
vanishes silently. The homebrew-class rows are there to catch it.

**Not on this branch:** the background replacement skill (gated by "held by another source"; it is
Part B's `:if-held`), and the entity-layer gate, repair log and load notice
([decision-gate-hidden-picks.md](decision-gate-hidden-picks.md), its steps 3–5).

## Part B — completing the grant compiler

### Are the new fields ornaments?

The honest test is whether each one **adds a concept** or **restores one that is being dropped**.

| addition | new concept? | verdict |
| --- | --- | --- |
| `:key` | no — `selection-cfg` already has it | **completing a lossy wrapper** |
| `:order` | no — same | same |
| `:prereq-fn` | no — same | same |
| `min` ≠ `max` | no — `selection-cfg` takes them separately; `grant-selection` forces `min=max=n` | same |
| the **owner** argument | not a field at all — an argument, universal, never per-pool | **context, not vocabulary** |
| `:if-held` | **yes** — genuinely new | **earns its place on evidence; land it last** |

**The first four are not ornaments, because they are not new.** `grant-selection` builds a
`selection-cfg` and drops four of the fields that shape it. It is a lossy wrapper, and completing
it is fidelity, not growth. That is also why they are cheap: no new ideas to justify, no tenet to
weigh.

**`:if-held` is the only real addition**, and it is the one to hold to the bar:

- *D21 — pool-kind logic lives in the pool definition, never a `cond` inside `grant`.* `:if-held`
  branches on what the **author declared**, not on which pool it is. That is the same class as the
  existing `:count` vs `:key` branch — the two verbs — which is accepted. It does not violate D21.
- *D12/D17 — thicker than what it hides.* It replaces `background-skills-cfg`, a per-silo compiler,
  and makes the behaviour available in six silos instead of one. Yes.
- *"wait for a case that demands it."* Four distinct resolutions across at least eight published
  books ([decision-already-held-resolution.md](decision-already-held-resolution.md)). The bar is
  met with evidence rather than anticipation.

### Derived helpers, or more fields?

The alternative — keep `grant-selection` minimal and build named wrappers over it
(`skill-grant`, `tool-grant`) — is exactly how `skill-selection` relates to `skill-selection-2`
today, and it is a real option.

**Use both, on this rule:** the four dropped fields belong in the **core**, because they are
`selection-cfg`'s own vocabulary and a wrapper that truncates its target is just broken.
Convenience belongs in **named helpers**, because that is where a caller-facing shorthand should
live. `:if-held` belongs in the core too — it must compose with both verbs, so a wrapper per
resolution would multiply rather than simplify.

The failure mode to watch for is a field that is neither: one that only one pool reads, or one
that makes `compile-grants` branch on which pool it is holding. Both proposals of that shape were
raised in this investigation (`:modifiers-fn`, `:replaceable?`) and both were rejected — the first
for putting per-pool behaviour in the registry, the second because the published data showed the
resolution belongs to the granting feature, not the pool. **That is the line: a grant field
describes the grant, never the pool.**

### Order

1. The four dropped fields on `grant-selection` — cheap, and `:prereq-fn` is wanted twice over.
2. The owner through `compile-grants`, which fixes the measured skill-expertise defect.
3. Wire the remaining silos (handoff step 4) so class/subrace/background can compile grants at all.
4. `:if-held`, with the resolution vocabulary.
5. Convert the bespoke skill/tool builders, one at a time, each replacing its bespoke path.

A grant compiled with its owner's condition cannot have the hidden-pick defect, so step 5 makes
Part A's fix redundant *for the sites it reaches* — which is a reason to do Part B, not a reason
to delay Part A.

## Related

- [hidden-selection-picks.md](hidden-selection-picks.md) — the defect, reproduced
- [decision-gate-hidden-picks.md](decision-gate-hidden-picks.md) — the fix analysis and the
  entity-layer backstop
- [decision-already-held-resolution.md](decision-already-held-resolution.md) — `:if-held` and the
  published evidence for it
- [already-held-grants.md](already-held-grants.md) — what a duplicate grant does today
