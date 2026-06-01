# Web handoff: homebrew half-casters with prepared spells

**Branch:** `claude/half-caster-prepared-spells-Jo0ai`
**Status:** **nothing shipped yet.** Plan written and approved in session;
this handoff is the only durable record. The original plan file lives at
`/root/.claude/plans/half-casters-with-prepared-stateful-flute.md` in
session-scratch and will not survive a fresh agent session.
**Audience:** agent picking up the work in a fresh web session, including the
reconciler agent who will land this alongside
`claude/fix-cantrips-selection-bug-CSwVv`.
**Date:** 2026-06-01
**Sources:** direct read of `options.cljc`, `template_base.cljc`,
`character.cljc`, `views.cljs`, `events.cljs`, `classes.cljc` on this branch's
HEAD (`d42e05d`); direct read of every diff and doc on
`origin/claude/fix-cantrips-selection-bug-CSwVv` HEAD (`edf9655`).

Before making any claims about codebase state: `git fetch origin
claude/half-caster-prepared-spells-Jo0ai && git log -20`. Trust the remote
over a stale local clone.

## The user's ask

A user trying to build a homebrew half-caster class (Paladin-style — *prepares*
spells from its entire class list) reported that the builder "automatically
assumes I want to learn spells." Accurate: the homebrew class builder UI
exposes no control for `:known-mode`, so enabling spellcasting silently
hardwires `:known-mode :schedule` (Learns, Ranger-style).

The character **engine** already supports all three D&D spellcasting styles
(`:schedule` / `:all` / `:acquire`) and the half-caster prepare-count math is
generic. The gap is purely the builder form.

## Branch state — important

- `git merge-base HEAD origin/claude/fix-cantrips-selection-bug-CSwVv` = HEAD
  (`d42e05d`). **This branch has not diverged from its starting point.** No
  commits beyond this handoff doc, no code edits.
- The sibling branch (`origin/claude/fix-cantrips-selection-bug-CSwVv`) is at
  `edf9655` — 7 commits ahead of our merge-base, with Phase 1 + Phase 2
  shipped, plus tests + docs.

## Why this work is small — verified blast-radius traces

The character engine and views layer are already generic across the three
spellcasting styles. Each line below was read on-branch; the claim summarizes
what the code actually does.

- **`options.cljc:2950-2969` (`class-option` body, `:all` grant path).** Emits
  `(mods/map-mod ?prepares-spells name true)` when `:prepares-spells?` is
  set in the `:spellcasting` map. When `:known-mode` is `:all`, mapcat's
  `spells-known-cfg` over `(spell-lists kw)`'s per-level spell keys to grant
  the full list. No `:level-factor` assumption — the grant happens regardless
  of caster progression. The current bug here is that this is
  `(spell-lists kw)` instead of `(spell-lists (or (:spell-list-kw spellcasting)
  kw))`; see change 3 below.

- **`template_base.cljc:274-284` (`?prepare-spell-count`).** Computes
  `(+ ability-mod (max 1 (int (/ class-level slot-factor))))` where
  `slot-factor = (get ?spell-slot-factors class-kw)`. For a half-caster
  (`slot-factor 2`), this is the exact Paladin formula:
  `ability-mod + floor(level / 2)`. Fully generic — no per-class hardcoding.
  Caveat: the function still derives `class-kw` from a name string via
  `(common/name-to-kw class-name)`. See "Pre-existing fragility" below.

- **`character.cljc:861-872` (`spell-prepared?`).** Reads
  `::prepared-spells-by-class` keyed on the class entry's display name and
  checks membership of the spell key. Generic across classes — no per-class
  branches.

- **`views.cljs:2047-2247` (character builder spellcasting panel).** Reads
  `prepares-spells`, `prepared-spells-by-class`, `prepare-spell-count-fn`
  subscriptions; renders the "prepare" checkboxes and per-day count entirely
  off those three pieces of data. No per-class code paths.

- **Confirmed `:known-mode :all` classes today: Cleric
  (`classes.cljc:399`), Druid (`760`), Paladin (`1430`). Three classes
  total.** None defines a `:spell-list-kw` (verified). None defines a
  `:spells-known` table (verified — the spell-table key is absent from each
  class's `:spellcasting` map). This is why change 3's `(or :spell-list-kw kw)`
  is byte-identical for current content.

## The plan — detailed

### Change 1: builder UI spell-acquisition-mode dropdown

**File:** `src/cljs/orcpub/dnd/e5/views.cljs` (homebrew class builder
spellcasting section, ~lines 5738-5799)

Add a new `labeled-dropdown` (rendered `when spellcaster?`, alongside the
existing four — ability, level-factor, ritual, cantrips) titled
"How does this class get its spells?":

| Option label | Value |
|---|---|
| "Learns a set number of spells" | `:schedule` |
| "Prepares spells from its full list" | `:all` |
| "Uses a spellbook" | `:acquire` |

`:value` reads from `(get-in class [:spellcasting :known-mode])`.

`:on-change` dispatches `::classes/set-class-prop :spellcasting <new-map>`
(reuses the existing event at `events.cljs:3057`; `set-class-prop` assocs the
whole `:spellcasting` map, needed because we must add *and* remove keys —
e.g. drop `:spells-known` when switching into `:all`).

Per-mode rebuild logic. Let `factor = (get spellcasting :level-factor 1)` and
`sched = (case factor 1 classes/full-caster-spells-known-schedule 2
classes/half-caster-spells-known-schedule 3
classes/third-caster-spells-known-schedule)`:

- **→ `:schedule`:**
  `(-> spellcasting (assoc :known-mode :schedule :spells-known sched) (dissoc :prepares-spells?))`
- **→ `:all`:**
  `(-> spellcasting (assoc :known-mode :all :prepares-spells? true) (dissoc :spells-known))`
  Prepared casters carry no `:spells-known` table (verified above against
  Cleric/Druid/Paladin).
- **→ `:acquire`:**
  `(-> spellcasting (assoc :known-mode :acquire :spells-known sched :prepares-spells? true))`
  `:acquire` mode reuses the caster schedule as the *per-level minimum*
  (`spells-known-selections` sets `:min num, :max nil` for acquire). Forcing
  `:prepares-spells? true` matches the real Wizard config at
  `classes.cljc:2381-2386` and avoids the never-used "acquire without
  prepare" combination.

### Change 2: mode-aware caster-level dropdown

**File:** `src/cljs/orcpub/dnd/e5/views.cljs:5793-5799`

Today's level-factor dropdown `:on-change` *always* overwrites
`[:spellcasting :spells-known]` with a caster schedule. Correct for
`:schedule` and `:acquire` (both use a `:spells-known` table); wrong for
`:all` (must have none). Switch from the 2-path `set-class-path-prop` to a
`set-class-prop` with a computed `:spellcasting` map, mirroring change 1:
set `:spells-known` for `:schedule`/`:acquire`; omit it for `:all`.

### Change 3: one-line `:all` grant fix — honor `:spell-list-kw`

**File:** `src/cljc/orcpub/dnd/e5/options.cljc:2953`

Current:

```clojure
(let [spell-list (spell-lists kw)]
  (mapcat ...))
```

The lookup uses the class's *own* key, so a prepared class that *borrows*
another class's list (`:spell-list-kw`) resolves to `nil` and grants zero
spells.

Change to:

```clojure
(let [spell-list (spell-lists (or (:spell-list-kw spellcasting) kw))]
  (mapcat ...))
```

**Safety analysis:** identical idiom is already in use by the Learns path at
`spells-known-selections` (`options.cljc:657`). For every existing `:all`
class (Cleric/Druid/Paladin) `:spell-list-kw` is `nil`, so `(or nil kw)` =
`kw` — behavior is byte-identical for all current content. The line is only
reachable for `:known-mode :all` classes (`when (= :all (:known-mode
spellcasting))` guard at line 2952).

## Forward-looking: what an orcbrew `:all` class does today

Verified by tracing the existing code (before any changes). A class with
`:known-mode :all` + `:prepares-spells? true` is structurally identical to
the built-in Paladin (`classes.cljc:1430` — no `:spells-known` key), so it
does **not** crash:

- `spells-known-selections` is still called (`options.cljc:707`) but the
  outer reduce iterates over nil `:spells-known` → produces `{}` → no
  spell-picker selection emitted. Correct behavior for a prepared caster.
- The `?prepares-spells` modifier is registered (`options.cljc:2951`).
- The `:all` grant (`options.cljc:2952-2969`) mapcats `spells-known-cfg`
  over the spell list.
- Prepare checkboxes, prepare-count, and slots all render correctly via the
  generic subs at `views.cljs:2047-2247`.

**The one real UI bug today:** the `:all` grant hardcodes `(spell-lists kw)`
and ignores `:spell-list-kw`. A homebrew `:all` class that *borrows* a list
resolves to `nil` → `mapcat` over nil → zero spell modifiers → spell section
renders empty with no error or warning. **Change 3 is exactly this fix.** A
homebrew `:all` class whose spells are registered under its own key works
fine even with no change.

## Pre-existing fragility (out of scope, but documented)

Both branches deliberately leave these for follow-up PRs.

### `:level-factor` omission breaks the count

If an orcbrew omits `:level-factor` from `:spellcasting` entirely,
`?prepare-spell-count` at `template_base.cljc:283` reaches `(/ lvl nil)` and
throws. The homebrew class **builder always writes `:level-factor`**, so this
is a hand-edited-orcbrew-only failure mode. Not in scope.

### `?prepare-spell-count` name-to-kw at `template_base.cljc:275`

The function derives `class-kw` from a name string via
`(common/name-to-kw class-name)`. Slot factors are keyed by the canonical
class key — so a name-to-kw drift produces a lookup miss and a wrong
per-day count.

**Sibling branch's stance:** explicitly defers. As of commit `251e1a7`
they've added an 8-line code comment at `template_base.cljc:272-280`
explaining that propagating `:class-key` through `spell-data` and the ~100
`mod5e/spells-known` call sites is its own PR. Active risk is bounded
under Phase 1's revert.

**This branch's stance:** also defers. Adding it here would (a) duplicate
the work the sibling has already analyzed, (b) explode our scope from
UI-only + a one-line bug fix to a cross-cutting engine change, (c) re-open
a settled discussion. The reconciler should leave both branches' deferral
intact.

### `::prepared-spells-by-class` storage keyed by class display name

`character.cljc` and `events.cljs` (`toggle-character-spell-prepared`)
both key the persisted prepared-spells map on the class entry's display
*name*, not its key. Discussed in this branch's planning conversation
(see "Reasoning trail" below). Real fragility for homebrew classes that
get renamed (orphans prepared-spell selections under the dead name);
zero fragility for built-in classes (names never change). Not addressed
by either branch.

## Reasoning trail — decisions made and why

Captured here because the design conversation that produced these
decisions was multi-hour and the synthesis is not obvious from the code
alone. A reconciler agent picking up this branch should not reopen
these without new information.

### Why the plan is UI-only + one-line bug fix, not an engine refactor

The character engine ALREADY supports prepared half-casters generically
(see the blast-radius traces above). Built-in Paladin proves it. The
ONLY gaps are:

1. The builder UI doesn't expose `:known-mode`.
2. The `:all` grant has a latent bug (ignored `:spell-list-kw`).

Anything bigger — re-keying the persisted prepared-spells map, threading
`:class-key` through `?prepare-spell-count`, refactoring the subclass
builder to emit `:all` grants — is *separate* engineering with its own
risk surface. The user's reported problem is solved by closing the two
gaps above, not by the larger refactors.

### Why we considered re-keying `::prepared-spells-by-class` and decided against it

The user probed: should we re-key the persisted prepared-spells map from
class display name to class key, given the broader "key is identity,
name is display" architectural direction the sibling branch is pursuing?

**Trace of the storage's risk profile:**

- The cfg field used as the key is the class display name. For a built-in
  class, the name is a source-code constant — stable forever. For a
  homebrew class, it's whatever the author typed at builder time, and the
  homebrew class editor's save path regenerates the class `:key` from
  `:name` on every save (`events.cljs:534-563`, `reg-save-homebrew` — see
  `docs/kb/key-vs-name-separation.md` for why this is the legitimate
  editor-side rename mechanism).
- So: built-in classes ARE stable in the persisted map. Homebrew classes
  store their *current name at the time of preparing*; a subsequent
  rename orphans the entry under the dead name.

**Decision:** defer. Reasons:

1. The bug surface is homebrew-rename-only. The user's reported problem
   is not this; closing it would expand scope without addressing the
   filed complaint.
2. A real fix requires a load-time migration that resolves
   `name → key` from the character entity's class entries. The migration
   itself has a partial-recovery caveat — entries already orphaned (named
   under a renamed-then-disappeared name) cannot be auto-recovered;
   only going forward would be key-stable.
3. The sibling branch has shipped exactly this kind of load-time
   reconciler (`reconcile-spell-selection-keys`) for the
   spell-selection layer. The `::prepared-spells-by-class` fix should
   follow the same pattern, ideally in a PR that also handles other
   name-keyed persisted state. That PR is not this PR and it is not the
   sibling's PR. **It is a third future PR.**

### Why `:acquire` mode forces `:prepares-spells? true`

The real built-in Wizard (`classes.cljc:2381-2386`) is `:known-mode :acquire`
+ `:prepares-spells? true`. The "acquire without prepare" combination is
never used in built-in content and would produce a UI where the user
collects spells but cannot prepare them — nonsensical for D&D 5e. Forcing
`:prepares-spells? true` when the user picks "Uses a spellbook" matches
the only sensible configuration.

### Why builder reuses the caster schedule as the `:acquire` minimum, not a Wizard-accurate table

The real Wizard learns 6 spells at L1 and +2 per level
(`classes.cljc:2384`). Matching that exactly would require its own
table/input in the builder. The reused full/half/third-caster schedules
function as a per-level minimum just fine — `spells-known-selections`
sets `:min num, :max nil` for `:acquire`, so the user can always add more
spells beyond the minimum. The Wizard-accurate variant is cosmetic, not
correctness — deferred.

### Why the subclass builder is out of scope

The subclass option builder at `options.cljc:2542-2633` does not emit
the `:all` grant or `:prepares-spells?` modifier. A homebrew subclass
cannot today be configured as a prepared-spells subclass via the builder.
The user is building a *base class*, not a subclass — so this is not in
the filed complaint. Worth a follow-up issue, but it would require its
own builder-UI surface and its own grant-path edit; not bundled here.

## Reconciliation with `claude/fix-cantrips-selection-bug-CSwVv`

### What the sibling has shipped (verified by reading the branch directly)

- **Phase 1 (`0a4f262`):** revert `:name` mutation in `spell_subs.cljs`,
  plumb `:plugin-source` as a distinct slot through `option-cfg`, add the
  `::show-class-source-suffix` user pref, add a load-time
  `reconcile-spell-selection-keys` in `content_reconciliation.cljs`.
- **Phase 2 (`251e1a7`):** switch kw derivation from name-based to
  key-based. New `spell-selection-key` helper at `options.cljc:469-477`
  (key+level → keyword). `spell-selection` at `options.cljc:478-488` uses
  `(spell-selection-key class-key level)` instead of
  `(common/name-to-kw title)`. The old `class-key-name` /
  `spell-selection-key` fallback at the former `options.cljc:629-640` is
  **deleted.** `spells-known-selections` simplified accordingly.
- **`?prepare-spell-count` explicitly deferred (`251e1a7`).** 8-line code
  comment at `template_base.cljc:272-280`.
- **`base-class-keys` dedupe (`1c24a8e`).** Canonical home moved to
  `classes.cljc`. Shadow set in `events.cljs:4691` removed; shadow in
  `content_reconciliation.cljs:163` folded to reference the canonical.
- Docs: `HANDOFF.md` and `web-handoff.md` at repo root (sibling's
  intra-branch handoffs), plus `docs/kb/key-vs-name-separation.md`
  (design rule + case study — **read this first**).

### Where the branches touch the same files

| File | Sibling delta | This branch's planned touch | Same-hunk risk |
|------|---------------|------------------------------|----------------|
| `src/cljc/orcpub/dnd/e5/options.cljc` | -47/+47 around lines 461-693 (spell-selection helpers); +1 `:plugin-source` arg at 2870-2912 (`class-option` destructure) | one-line fix at `class-option` body ~2953 | **None.** Different hunks. After rebase our line shifts; content survives. |
| `src/cljc/orcpub/dnd/e5/template_base.cljc` | +8 (deferring comment at 272-280) | not modified | None. |
| `src/cljc/orcpub/dnd/e5/classes.cljc` | +7 (`base-class-keys` canonical home) | read-only reference (Paladin config) | None. |
| `src/cljs/orcpub/dnd/e5/events.cljs` | +30/-6 (reconciler wiring, suffix-toggle handler, base-class-keys dedupe) | not modified (we reuse `::classes/set-class-prop` at 3057) | None. |
| `src/cljs/orcpub/dnd/e5/views.cljs` | 0 | builder spellcasting UI ~5738-5799 | None — sibling does not touch this file. |

Net: **no expected merge conflicts** in the planned hunks. The rebase is
mechanical.

### Logical (non-code) overlap

- **`?prepare-spell-count` name-to-kw at `template_base.cljc:275`.** Both
  branches defer. Reconciler: leave the deferral intact, do not let either
  side silently take it on.
- **`::prepared-spells-by-class` storage keyed by class display name.**
  Discussed in this branch's planning conversation (see "Reasoning trail"
  above). Not addressed by either branch. Third-PR territory.
- **Sibling's class-key-based identity is *compatible with* and *supports*
  what this branch enables.** A homebrew `:all` half-caster created via
  the new builder dropdown will derive spell-selection keys from
  `:class-key` (Phase 2 behavior) — meaning rename-orphaning at the
  spell-selection layer is structurally fixed for our new content. The
  `::prepared-spells-by-class` layer remains name-keyed; only the
  spell-selection-key layer is hardened. (Two different layers, only
  the latter shipped.)

### Recommended merge order

1. **Sibling lands first, this branch rebases.** Recommended.
   - Sibling is further along (7 commits, tests, docs); this branch has
     only this handoff.
   - Rebase is mechanical (table above). Our `:all` grant change at line
     2953 shifts to a new line number; content unchanged.
   - This branch's new builder-UI flow inherits Phase 2's key-based
     spell-selection identity for free.
2. *Alternative if blocked:* this branch lands first (smaller PR). Sibling
   then rebases. Their Phase 2 edits don't physically touch our `:all`
   grant area. Conflict resolution still mechanical.
3. **Independent simultaneous merge to master is fine** — git will resolve
   without intervention given the table above. Reviewer can pick either
   ordering at merge.

### File path collision: two `web-handoff.md` files

The sibling's handoff is at repo root `web-handoff.md`. This one is at
`docs/kb/web-handoff.md` precisely to avoid the merge collision. On
post-merge cleanup, both could be archived together under `docs/kb/` with
date-stamped names (`web-handoff-cantrip-fix-2026-05.md`,
`web-handoff-half-caster-2026-06.md`) or simply deleted. Not a blocker.

## Verification plan

1. Start the dev environment per `docs/TESTING.md` (or standard `lein`
   figwheel dev build).
2. In the homebrew class builder:
   - Create a class.
   - Enable spellcasting.
   - Set caster level to "2nd level (Half Caster)".
   - Set the new mode dropdown to "Prepares spells from its full list".
   - Give it a spell list. **Test both:**
     - a Custom list (registered under the class's own key — change 3 is
       not exercised),
     - a Borrowed list, e.g. Wizard via `:spell-list-kw :wizard` (change 3
       IS exercised — confirm spells appear).
3. Build a character of that class. Confirm:
   - Spells are not "learned" via a fixed picker (the Learns UI does not
     render).
   - The spell list shows "prepare" checkboxes.
   - The per-day prepared count equals
     `ability-mod + floor(level / 2)` — matches an equivalent-level
     Paladin.
   - The borrowed-list variant actually grants spells (validates change 3).
4. Switch the same class to "Uses a spellbook"; confirm:
   - The spell picker is unbounded (can add beyond the minimum).
   - Prepare-checkboxes still appear.
5. Toggle the mode dropdown back and forth AND change the caster-level
   dropdown in each mode; confirm:
   - `:spells-known` is present for Learns/Spellbook.
   - `:spells-known` is absent for Prepares (no stale leftover from
     toggling).
6. Regression: open an existing Paladin character and an existing Cleric
   character; confirm prepared-spell behavior and counts unchanged.

## Reasoning traps to avoid

- **Don't take on `?prepare-spell-count` here.** Sibling defers it with a
  code comment. This branch defers it in its plan. The fix is ~100
  callsites across `classes.cljc` + a thread-through in
  `template_base.cljc`. Its own PR.
- **Don't conflate the two grant paths in `class-option`.**
  - `:schedule` / `:acquire`: `spells-known-selections`
    (`options.cljc:707`) → `spell-selection` (`options.cljc:478`, now
    key-based via Phase 2). **Sibling owns this site.**
  - `:all`: the `mapcat (fn [[lvl spell-keys]] (map ... spells-known-cfg
    ...))` block (`options.cljc:2952-2969`, where this branch's one-line
    fix lives). Sibling does not touch this. **This branch owns this
    site.**
- **Don't add a `:spells-known` table to homebrew `:all` classes** in the
  new builder. Verified: Cleric/Druid/Paladin have none.
  `spells-known-selections` handles nil correctly. Adding one would be a
  behavior change.
- **Don't modify built-in class option-fns** to thread a new arg. The
  plan for this branch is UI-only plus the one-line `:all` grant fix.
  Don't expand scope into the engine.
- **`name-to-kw` at `events.cljs:534-563` (`reg-save-homebrew`) is the
  legitimate editor-side rename mechanism** — see
  `docs/kb/key-vs-name-separation.md`. Do not touch as part of this
  branch.
- **Don't re-key `::prepared-spells-by-class` opportunistically.** See
  "Reasoning trail" above for why this is deferred to a future PR.
- **Don't reach for the subclass builder.** The user is building a base
  class. The subclass builder is a separate fix at a separate site.

## Out of scope (deliberately, with rationale captured above)

- Subclass builder edits (`options.cljc:2542-2633`).
- Wizard-accurate spellbook acquisition curve (real Wizard's `6, +2/level`).
- `?prepare-spell-count` name-to-kw fix.
- `::prepared-spells-by-class` storage re-keying.
- Hand-edited-orcbrew `:level-factor` omission crash.

## Where to start

1. Verify branch state on both branches: `git fetch origin
   claude/half-caster-prepared-spells-Jo0ai
   claude/fix-cantrips-selection-bug-CSwVv && git log -20`.
2. Read `docs/kb/key-vs-name-separation.md` (sibling-shipped, durable) to
   understand the identity model the engine now uses.
3. Implement the three changes:
   - §1 builder UI dropdown — `views.cljs:5738-5799` area
   - §2 mode-aware level-factor — `views.cljs:5793-5799`
   - §3 one-line `:all` grant fix — `options.cljc:2953`
   Existing event `::classes/set-class-prop` at `events.cljs:3057` is
   reused — no new events.
4. Run the verification plan above.

## Open questions for the reconciler agent

- Merge order: confirm sibling-first (recommended) vs. simultaneous.
- Post-merge: where do the two `web-handoff.md` files live, if anywhere?
  (Archive under `docs/kb/` with dated names, or delete.)
- Should the `::prepared-spells-by-class` rename-orphan TODO be filed as a
  GitHub issue now, or left for the next session that touches that area?

## Key files

- `src/cljs/orcpub/dnd/e5/views.cljs:5738-5799` — builder spellcasting UI
  section (target for changes 1 + 2)
- `src/cljs/orcpub/dnd/e5/events.cljs:3057` — `::classes/set-class-prop`
  (reused, not modified)
- `src/cljs/orcpub/dnd/e5/events.cljs:2932` — `::classes/set-class-path-prop`
  (the one the current caster-level dropdown uses today; we move away from
  it in change 2)
- `src/cljc/orcpub/dnd/e5/options.cljc:2860+` — `class-option` (target for
  change 3 at line 2953)
- `src/cljc/orcpub/dnd/e5/options.cljc:657` — the `(or :spell-list-kw kw)`
  precedent for the Learns path (validates change 3's idiom)
- `src/cljc/orcpub/dnd/e5/options.cljc:469-488` — sibling's new
  `spell-selection-key` + key-based `spell-selection` (post-Phase 2 state
  the reconciler will see)
- `src/cljc/orcpub/dnd/e5/classes.cljc:1430` — Paladin config (the
  structural reference for what a built-in prepared half-caster looks
  like)
- `src/cljc/orcpub/dnd/e5/classes.cljc:2381-2386` — Wizard `:spellcasting`
  config (validates change 1's `:acquire` shape)
- `src/cljc/orcpub/dnd/e5/template_base.cljc:272-284` — `?prepare-spell-count`
  + sibling's deferring comment (do not modify on this branch)
- `src/cljs/orcpub/dnd/e5/character.cljc:861-872` — `spell-prepared?` (the
  generic per-class check)
- `src/cljs/orcpub/dnd/e5/views.cljs:2047-2247` — character builder
  spellcasting display (renders generically off subs)
- `docs/kb/key-vs-name-separation.md` — sibling-shipped design rule (read
  it)
- Sibling's `web-handoff.md` and `HANDOFF.md` at the sibling branch's repo
  root — sibling's intra-branch handoffs (read for full context)
- `/root/.claude/plans/half-casters-with-prepared-stateful-flute.md` —
  this branch's approved plan (session-scratch; **do not rely on it
  existing** — this handoff is the durable record)
