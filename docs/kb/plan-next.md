# What to build next — ordered

A working list, roughly in order. Only one hard dependency (item 4 after item 1); everything else
can move if something makes a better case.

Status legend: ☐ not started · ◐ partly done · ☑ done

---

## 0. Standing, do these first

☐ **Re-run the browser tests.** 401 commits of UI arrived with the integration merge and every
`test/e2e/*.js` was written before it. Until they run, none of them is evidence.

☐ **Move `docs/kb` to `agents/develop`** before this branch merges anywhere. Integration carries no
`docs/kb`; the KB lives on `agents/develop`. This is branch hygiene, not a code change.

---

## 1. Grants on the remaining four silos

☐ `subrace-option` · `background-option` · `level-option` (class and subclass)

Today only **race** and **feat** compile `:grants`. The others each have their own shape for the
same three capabilities:

| the creator wants | grant |
|---|---|
| to pick the specific things granted | `{:pool :skills :key :athletics}` |
| the player to choose, from everything | `{:pool :skills :count 2}` |
| the player to choose, from a shorter list the creator picked | `{:pool :skills :count 2 :filter #{…}}` |

These are three genuinely different capabilities, not one idea written three ways — and all three
take a variable count. The grant vocabulary has a mode for each; what's missing is that four silos
can't say any of them.

Roughly two lines per silo — destructure `grants`, merge `compile-grants`' output — plus the form
node and a test each.

**Unblocks item 4.** Converting a builder before it has grants means converting it twice.

---

## 2. The mechanics the feat builder still can't author

☐ Effect kinds, as **templates with tweakable numbers**, plus an advanced tier

The compiler accepts these already; the form doesn't offer them.

- **Natural Armor** — `[N] + [ability ▾]`. The template is the shape these share; the differences
  are knob values, not separate templates:
  - lizardfolk is `{:ac 13 :abilities [:dex]}` — one row, as-is
  - tortle is `{:ac 17 :abilities []}` **plus** `armor-gives-no-ac` — one row and a second control,
    because "your shell IS your armour" is a separate fact from the number
  - warforged's +1 is `:ac-bonus`, a different prop, and is not this template at all
- **Armor Dex Cap** — `[Heavy ▾] armor allows up to [2] Dexterity`
- **Speed / Initiative / Hit Points** — currently fixed checkbox ranges (5/10/15 ft, +1…+5); the
  compiler takes any number.

Then an **Advanced** section on the same props, using `optional-builder-section` (collapsed by
default, opens when data exists, exports nothing if untouched) exposing the rest of the formula —
the `:armor?` / `:shield?` conditions on `:ac`, and the other armor types on the cap.

The point is both tiers: a common case in one row with a number to change, and the whole formula
for someone who wants it.

---

## 3. Damage and attack bonuses reach the requirements registry

☐ The macro treatment `mod5e/ac-bonus` got, for `?damage-bonus-fns` and `?attack-bonus-fns`

`:dual-wielding?` and `:one-handed?` gate AC today and nothing else. Dueling Fighting Style is
*"+2 damage while wielding a weapon in one hand and no other"* — `:one-handed?` on a damage bonus —
and it is still hand-written because the damage channel can't reach the registry.

A damage contributor is `(fn [weapon])`, so it needs its own macro assembling a context, the same
way the AC one splices `?main-hand-weapon` into the contributor body.

**Does NOT wait on item 5.** It uses the existing mechanism exactly as `ac-bonus` does.

This is the item that *proves* the requirements registry rather than assuming it — one consumer is
not "shared".

---

## 4. Convert the remaining builder forms

☐ background · subrace · subclass · class · monster, cheapest first, class last

After item 1. Each conversion: pin the current form with a test, swap to the schema, assert the
numbers and the saved shape did not move. `builder-disposition-audit.md` has the per-widget table —
what becomes a grant row, an effect row, a plain field, or stays bespoke.

---

## 5. entity-spec / `?attr` — analysis, not a rewrite

☐ What it does, what modern Clojure would use, what breaks if it changes, what cheaper option buys
most of the value

`?foo` is rewritten to `(entity-val e :foo)` at compile time, so **only code a macro expands can
read a value off a character**. That constraint is why the dual-wield AC bonus was hand-written for
years and why two design calls in one session were wrong before it was understood.

Independent of everything above. Do not pre-judge the outcome: "it works and is understood" is not
sufficient on a branch whose premise is rewriting for maintenance.

---

## 6. Vector rows, generalised

☐ Encounter creatures, background traits

`vector-rows-node` exists but is grant-specific — its row body is mode / which / how many / from.
Creatures and traits are the other two ordered-list needs. Do this when one of them is actually
being built; if a fourth shape appears, stop and reassess.

---

## Also open, smaller

☐ Unrecognised-tag warning on **import** as well as export (`bf/unknown-tag-problems` already
exists; it is the other seam).
☐ `builder-notes` rendering through `notifications/callout` — shared box styling, and it fixes a
notice that currently renders as a 12px line.
☐ `feat-weapon-proficiency` still writes `:improvised-weapons-prof`, which nothing reads. Wire it
(`modifiers/weapon-proficiency :improvised` exists) or remove the control.
☐ `(mod5e/ac-bonus-fn (fn [] 1))` in the UA warforged block (`ua_base.cljc:832`) is a **0-arity**
fn in a channel that calls `(f armor shield)`. Inside `#_` so it never compiles, but it would throw
if that block were re-enabled. Also worth checking whether the early UA warforged was tortle-shaped
(base AC + Con, no worn armour) rather than the flat +1 the repo has — if so it is another data
point for the natural-armor template, see `assets/natural-armor-mockup.html`.
☐ `"Passive Investigation +5"` in the Custom Feat list grants passive **perception**. One word, but
it changes the sheet of any saved character that picked it — needs a decision, not a quiet fix.
