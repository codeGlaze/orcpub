# What to build next — ordered

A working list, roughly in order. Only one hard dependency (item 4 after item 1); everything else
can move if something makes a better case.

Status legend: ☐ not started · ◐ partly done · ☑ done

---

## 0. Standing, do these first

☑ **Re-run the browser tests.** Done 2026-09-18: `homebrew-grand-tour` 69/69, `spell-builder`
40/40, `move-between-sources` 10/10, `change-item-key` 11/11, `source-key-tag` 11/11,
`replace-or-refuse` 20/20, `builder-gallery` no drift.

☐ **Move `docs/kb` to `agents/develop`** before this branch merges anywhere. Integration carries no
`docs/kb`; the KB lives on `agents/develop`. This is branch hygiene, not a code change. Does NOT
block the branch reconciliation below — those merges are downward, and the port up to integration
deliberately leaves docs behind.

---

## 0a. Map the plugin system before changing it further (decided 2026-09-25) — CURRENT

☐ **Phase 1: the map. Nothing else until the owner has read it.**

**Why.** The homebrew save rework went through six adversarial review rounds (6, 5, 4, 6, 6, 3
findings) and every round found a part of the system nobody had mapped: ten edit doors that pass an
item without its address, a subscription reading through a different helper, one origin slot shared
by thirteen builders, a third way an entry changes under an open builder. The reviewers were building
the map one expensive, partial piece at a time. Patching symptom by symptom will keep producing
that. The account: `homebrew-save-rework.md`.

**The standard asked for.** Stable and hardened — not breakable by a later patch or by an ordinary
interaction in the interface. "Unbreakable" is not literal; what is achievable is making the safe
path the only natural one and making a bypass loud: the rules that must always hold, stated once,
enforced in one place, and tests that fail the moment any writer breaks one.

**Decided (owner, 2026-09-25): changing an item's source in its own edit view MOVES it.** From the
user's side the Option Source Name is a property of the item, and editing a property should not
duplicate the item. The design has to make that robust, not route around it.

**Scope of the map:**

1. **Start from the KB** — this branch's *and* `agents/develop`'s, which hold different pages
   (`git ls-tree -r origin/agents/develop --name-only docs/kb/`). Verify what is there; do not
   re-derive it.
2. **Data model and identity.** What `:plugins` stores and what is derived; what identifies an item
   across rename, move, key change, import, export and share. The branch has no real identity model
   — the round-six name check is a proxy, and names change.
3. **Every writer and every reader, as a table**, with what each assumes. Measured 2026-09-25: 26
   write sites across 17 events, 4 of them through the save gate; 27 subscriptions reading.
4. **Characters.** They live on the server and point at browser-only homebrew by key. What happens to
   a character when the item it names moves, is renamed, re-keyed or deleted.
5. **Storage and transport.** localStorage (library, rejected, per-builder drafts, the origin
   record), quota failures, **two open tabs** (nothing coordinates them — the same class is
   documented for characters in `agents/develop`'s `multi-tab-character-contamination.md`),
   `.orcbrew` export/import, share links.
6. **The invariants**, each checked against every writer.
7. **A gap list, ranked.**

**Output:** one KB page — the map. Then design against it, build, and review against the
invariants rather than blind.

**Known inputs:** `homebrew-save-rework.md` and `key-collision-behavior.md` (the six rounds, and the
mechanism as built); rounds five and six enumerated the twelve edit doors and the `:plugins`
writers, recorded in the commit messages of `085c4d6c` and `fc6fb321`.

**Open, for the owner, alongside phase 1:** what happens to the save rework meanwhile — hold it on
this branch, or land it on `integration` as a stopgap. `integration` has confirmed silent data loss
today (retyping a source copies the item; `::selections5e/save-selection` has no collision check at
all), but landing an interim means `integration` changes twice. The trunk is also one round behind,
carrying two bugs round six fixed.

---

## 0b. Converge the save path across the four branches (2026-09-18) — PAUSED 2026-09-25 for 0a

☐ The homebrew save exists in **three variants**, and the newest is the one to converge on:

| branch | save path | missing |
|---|---|---|
| `feature/grant-rows` | `address-for` + `save-destination` + `replacing` + `collision-error-fx` | — |
| `integration` | `save-collision` + `legacy?` | the move fix, the replace offer, three guarded save paths |
| `refactor/content-extensibility` (trunk) | `save-collision`, tagged keys, **no** `legacy?` | all of the above |
| `feature/extras-companions` | as trunk | as trunk |

Agreed order: `integration` → trunk → `feature/grant-rows`; then a port branch built from the
POST-MERGE state (not the original commits, since the resolution may differ from what they carry)
gated and reviewed, merged to `integration`; then `integration` back down through the trunk into
both `feature/grant-rows` and `feature/extras-companions`. Convergence is asserted, not assumed:
the save region of `events.cljs` and `homebrew_save_lifecycle_test.cljs` identical across all four,
`save-collision` gone from every save handler.

☐ **Delete `fix/duplicate-key-traps-its-owner`** once this lands. It is marked do-not-merge in
`handoff-integration-branches.md`; the trap it was written for is now fixed properly, so the branch
is only a repro test with a broken fix attached.

**A fix made on a cut branch has to come back to the branch it was cut from.** The key-less-item
fix was made on `feat/source-tagged-keys` during review and never returned to `feature/grant-rows`,
which carried the bug for five days — and it then nearly got deleted from `integration` by a naive
port in the other direction. Check both directions.

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

**Natural-armor form, settled 2026-09-12** (in the mockup): riders are ONE control repeated —
`:abilities` is a vector that sums, so `base + Dex + Con` is "+ ability" pressed twice, not a new
shape; "whichever is better" is two rows, because calculations compete by max. Shields are never
authored in the row: the shield bonus is summed onto whichever calculation wins, so the only
shield control is the advanced tier's DISQUALIFIER (`:shield? false`, Unarmored Defense). Presets
are **picked templates, not values that fire when the AC number matches** — a form that rewrites
fields the author did not touch is untrustworthy, and one number does not identify one race. A
template is a parameter sheet over the same `:props` (the template tier,
`builder-disposition-audit.md` §REFRAMING).

☐ `plugin-datalist` (the Option Source Name field) keeps the source name in a component-local atom
that **NEW does not reset**, so after New the field still shows the previous source while the item
has none — and typing the same value back fires no change, so the save is refused for a field that
looks filled. Worked around in `test/e2e/source-key-tag.js` with a reload.

**Settled 2026-09-18:** this is not sticky-source working, it is the DISPLAY being sticky while the
data is not. Two paths, only one of which works — My Content's per-source *add* passes the source
into the new item and is fine; the builder's own *New* gives the item no source at all. The fix is
both halves: the component follows the item it is given, and New carries `:option-pack` forward as
real data, so sticky source becomes true rather than cosmetic. **No toggle** — the source is visible
in the field and one edit overrides it, so a hidden setting would govern something already on screen.

☐ **Other key-minting paths ignore a source's `:abbreviation`** — `relocate-content` and import
conflict resolution both mint through the derivation rather than the stored tag, so a source that
set one gets untagged keys from those paths.

☐ **`coerce-invalid-names` re-derives untagged keys for every item in a repaired source**, so
repairing one bad name can re-address its siblings.

☐ **An import-conflict "rename" can now be a no-op**, because the key it suggests is the one the
item was already minted with.

☐ **`:disable-overlay` sections are keyed by source name and are not updated by a move**, so an
item that changed source can leave a stale disable entry behind.

☐ **Should a move onto an occupied slot offer keep-both as well as replace?** Replace and cancel
ship (`key-collision-behavior.md`); keep-both was deliberately not offered, because two entries at
one address is the state that makes both uneditable. It arrives legitimately through IMPORT, where
the conflict modal asks. Revisit only if authors ask for it.
