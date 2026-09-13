# Declarative grant/select vocabulary (builder UI) — design + analysis

A proposed authoring vocabulary for the BUILDER (how a creator expresses "this content grants
a spell / a choice / a conditional bonus"), and a real analysis of whether it works for spells.

Markers: **VERIFIED** = read from code, file:line. **DESIGN** = proposed, not built.
**OPEN** = a point not yet agreed; confirm before building.

## Two layers, kept separate (agreed)
- **Layer A — builder UI.** The form elements a creator uses to author a grant. This is what the
  "generate UI from a grant declaration" idea is about.
- **Layer B — mechanical implementation.** How the authored data computes on the character
  (modifiers, selections, `?`-attributes). Separate logic, even if related.

A Layer-A form-generation concern (what controls to show) is distinct from a Layer-B concern (how
the data evaluates). An earlier note conflated them (worrying about boolean/AND-OR condition logic
as if it were a UI scope limit). It isn't: how conditions *combine mechanically* is Layer B; the
builder form is Layer A. The only Layer-A residue is "how does the form let a creator add more than
one condition" — see OPEN below.

## The vocabulary (DESIGN)
Two verbs, distinguished by who makes the choice, plus filters:
- `<grant spell (filters)>` — the **creator** picks the spell(s); fixed on every character. Compiles
  to `mod5e/spells-known`.
- `<select spell (filters)>` — the **user** picks at build time, from the filtered pool. Compiles to
  `opt5e/spell-selection`.
- **Filters** (spell level, school, class list, ritual-only, attack-only, count, casting ability) are
  not new machinery — they are the parameters `spell-selection`/`spells-known` already take. The
  filter set drives which form fields the generator renders.

Same two verbs generalize to other grant kinds, e.g. `<grant magic-AC when [conditions]>` → a number
picker + condition picker(s), compiling to the AC contribution + condition model
(see armor-class-computation.md, runtime-toggles-and-conditional-modifiers.md).

Scope note (agreed): this is about easily expressing the **ways rules grant spells** to players
(feat gives a fixed spell, race gives a choice, etc.) across silos — NOT about rebuilding the base
class spell pickers.

## Analysis — does it work for the real spell patterns?
Tested against the actual ways 5e/the app grant spells:

| Pattern | Vocabulary | Compiles to | Works? |
|---|---|---|---|
| Fixed innate spell (always / at a level / with a set ability) | `<grant spell>` + level + ability | `spells-known` | ✅ — `:spells` shape already carries level+ability |
| Choose N from ONE list, filtered (level/school/ritual/attack) | `<select spell (filters)>` | `spell-selection` | ✅ — filters = its existing list/level/restriction params |
| Cantrips | same, level-0 filter | `spell-selection`/`spells-known` | ✅ |
| Magic-Initiate style: pick a CLASS/list, then spells from it | nested/dependent select | `spell-selection` per chosen list | ⚠️ SPECIAL CASE — a two-level dependent choice, not a flat select |

**SPECIAL CASE — dependent two-level choice (VERIFIED; corrects an earlier overstatement).**
I previously claimed the feat spell "templates" were ~18 redundant constructions that a single
`<select spell>` would subsume. That was wrong. `magic-initiate-selection` (`options.cljc:3244`),
`ritual-caster-selection`, `spell-sniper-selection` are each ONE selection ("Spell Class") whose
options are the six caster classes; the user **picks a class**, and that class option then has
sub-selections to pick cantrips + a level-1 spell from *that class's* list. The six per template are
the user's class choice — Magic Initiate's actual rule (choose a class, then learn from it), not
copy-paste. So a flat `<select spell>` does NOT subsume them: this is a **dependent two-level
choice** (pick a list, then spells from it), which the vocabulary must support as a nested select.

The only genuine duplication is smaller: the six-class caster table (bard→Cha, cleric→Wis,
druid→Wis, sorcerer→Cha, warlock→Cha, wizard→Int) is written out in ~4 places
(`options.cljc:1390/1717/3249` + the ritual/sniper selections) and could be one shared table mapped
over. That's minor tidiness, not a structural collapse.

**It does unify the two divergent fixed-spell data shapes (VERIFIED):** races use `:spells`,
classes/subclasses use `:level-modifiers {:type :spell}` — both reach `spells-known`. One `<grant
spell>` collapses those. (This claim stands; the "subsumes 18" claim above did not.)

## Compound grants — "pick 2 cantrips and a 1st-level spell" (the canonical case)
A single feat often grants several picks of different kinds. The form is a **repeatable list of
grant/select rows**, each with its own filters + count. "2 cantrips + 1 first-level spell" =
`<select spell ×2, level 0>` + `<select spell ×1, level 1>` — which is exactly what
`magic-initiate-option` builds (two sub-selections). So the builder UI is "add a grant row" N times;
a compound grant is just multiple rows. The Magic-Initiate "pick a class first" case adds the
dependent/nested wrinkle on top (pick a list, then the rows draw from it).
(Note: this is separate from the *condition* combination flag below — compound spell grants are a
list of select rows; multiple conditions are an AND-list. Both are "a repeatable list" in the form.)

## Scope decisions
- **Spellcasting progression is OUT of scope** — but note: progression was never requested in this
  thread (it was my mis-attribution). The vocabulary is for granting *individual* spells as a
  bonus/reward. Full caster progression stays the existing `:spellcasting` machinery.
- **Usage qualifiers ("once per long rest") need real plumbing, separately.** VERIFIED: there is no
  creator-declarable use-count mechanism — `used-resource` is only a label, and magic-item/racial
  limited-use spells are descriptive text (see spell-granting-across-silos.md). So a qualifier is
  *descriptive only today*. Doing it properly is a **new tracked-resource layer** (max + current +
  reset-on-rest), a separate project from the grant vocabulary, not a flag to hand-wave.
- **Magic items are a real spell source the grant vocabulary should cover.** Today they show spells
  as `mod5e/action` text, not in `spells-known`. A `<grant spell>` routing to `spells-known` would
  make them properly visible/castable instead of loose text.

## Backward compatibility (hard requirement)
Any change to data shapes (e.g. unifying fixed-spell `:spells` vs `:level-modifiers :spell`) MUST
keep existing homebrew libraries and saved characters working — via a read-shim for the old shape if
the canonical shape changes. Prefer not churning saved data; if churn is unavoidable, migrate with a
shim, never break. This applies to every step of the uniform-data-path work.

## Sequencing (agreed)
This is a step *out from* organizing the framework, not instead of it. Make the data path uniform
first — one `:spells` (fixed) and one `:spell-choice` (select) key routed to the existing primitives
across every silo's assembly fn — THEN build reusable controls (spell-picker, condition-picker),
THEN let the registry generate the builder form from the declaration. Generating UI over the current
inconsistent data path would hide the inconsistency, not fix it.

## Status of the earlier flags (updated)
1. Spellcasting progression — RESOLVED: out of scope (and was my mis-attribution; never requested).
2. Usage qualifiers — RESOLVED as: descriptive today, and a proper fix is a **separate** tracked-
   resource project (not part of this vocabulary). VERIFIED there's no creator-declarable use-count.
3. Compound grants ("2 cantrips + 1 spell") — RESOLVED: a repeatable list of grant/select rows.
4. Multiple **conditions** — still OPEN: default to an AND-list, defer OR/nested (Layer-A choice).
5. `:spells` data shape — RESOLVED to a principle: backward compatibility is mandatory; keep the old
   shape readable (shim) if the canonical shape changes; prefer not churning.
6. Magic items as a spell source — NEW: covered by `<grant spell>` (today they're `mod5e/action`
   text, not in `spells-known`).

## Idiomatic check (Clojure/Reagent)
Data-driven UI (a render fn over a grant spec) is idiomatic — same pattern as the existing
`render-builder-field`, and more idiomatic than the current hand-written builder forms. Guardrails:
keep it data interpreted by small functions (not a macro-DSL), keep the vocabulary small/composable,
and compile DOWN to the existing primitives (`spells-known`, `spell-selection`, the modifier system)
rather than building a parallel engine.
