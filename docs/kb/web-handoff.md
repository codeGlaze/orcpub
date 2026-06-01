# Web handoff: homebrew half-casters with prepared spells

Branch: `claude/half-caster-prepared-spells-Jo0ai`
Status: **nothing shipped yet.** Plan approved (in session-scratch at
`/root/.claude/plans/half-casters-with-prepared-stateful-flute.md` — NOT in
the repo), no code edits made.
Audience: agent picking up the work in a fresh web session, including the
reconciler agent who will land this alongside
`claude/fix-cantrips-selection-bug-CSwVv`.

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
generic (`template_base.cljc:274-284`, `ability-mod + (level / slot-factor)`).
The gap is purely the builder form.

## The plan (three changes, blast radius minimal)

Full detail in the plan file. Summary:

1. **Builder UI dropdown** — `views.cljs` (~5738-5799). Add "How does this
   class get its spells?" with three items mapping to `:schedule`, `:all`,
   `:acquire`. Dispatches existing `::classes/set-class-prop :spellcasting
   <new-map>` (events.cljs:3057, reused — no new events). Each mode computes
   the right `:spellcasting` map shape (e.g. `:all` adds `:prepares-spells?
   true` and drops `:spells-known`).

2. **Mode-aware caster-level dropdown** — `views.cljs:5793-5799`. The existing
   level-factor `:on-change` always writes `:spells-known`; that's wrong for
   `:all`. Recompute based on current `:known-mode`.

3. **One-line `:all` grant fix** — `options.cljc:2953`. Current
   `(spell-lists kw)` looks up under the class's *own* key; ignores
   `:spell-list-kw`. A homebrew `:all` class that *borrows* another class's
   list silently renders an empty spell section. Change to
   `(spell-lists (or (:spell-list-kw spellcasting) kw))` — same idiom already
   used by the Learns path at `options.cljc:657`. For every existing `:all`
   class (Cleric/Druid/Paladin) `:spell-list-kw` is `nil`, so `(or nil kw)` =
   `kw` and behavior is byte-identical for current content.

## Branch state — important

- `git merge-base HEAD origin/claude/fix-cantrips-selection-bug-CSwVv` = HEAD.
  **This branch has not diverged from its starting point.** No commits, no
  edits. The sibling has work; we don't.
- The plan lives at `/root/.claude/plans/half-casters-with-prepared-stateful-flute.md`
  in session-scratch — invisible to other agents and sessions. Either treat
  this handoff as authoritative or open that file in the same session it was
  written in.

## Reconciliation with `claude/fix-cantrips-selection-bug-CSwVv`

This is the doc's main purpose. The sibling branch has shipped real work that
intersects ours conceptually. Code-level overlap turns out to be narrow.

### What the sibling has shipped (verified by reading
`origin/claude/fix-cantrips-selection-bug-CSwVv` directly)

- **Phase 1 (`0a4f262`):** revert `:name` mutation in `spell_subs.cljs`,
  plumb `:plugin-source` as a distinct slot through `option-cfg`, add the
  `::show-class-source-suffix` user pref, add a load-time
  `reconcile-spell-selection-keys` in `content_reconciliation.cljs`.
- **Phase 2 (`251e1a7`):** switch kw derivation from name-based to
  key-based. New `spell-selection-key` helper at `options.cljc:469-477`
  (key+level → keyword). `spell-selection` at `options.cljc:478-488` uses it
  instead of `(common/name-to-kw title)`. The old `class-key-name` /
  `spell-selection-key` fallback at the former `options.cljc:629-640` is
  **deleted.** `spells-known-selections` simplified accordingly.
- **`?prepare-spell-count` explicitly deferred (`251e1a7`).** Sibling
  added an 8-line code comment at `template_base.cljc:272-280` explaining
  that propagating `:class-key` through `spell-data` + `mod5e/spells-known`'s
  ~100 callsites is its own PR. Active risk bounded under phase 1's revert.
- **`base-class-keys` dedupe (`1c24a8e`).** Canonical home moved to
  `classes.cljc`. Shadow set in `events.cljs:4691` removed; shadow in
  `content_reconciliation.cljs:163` folded to reference the canonical.
- Docs: `HANDOFF.md` and `web-handoff.md` at repo root (sibling's intra-branch
  handoffs), plus `docs/kb/key-vs-name-separation.md` (design rule + case
  study — durable, read it).

### Where the branches touch the same files

`git diff HEAD origin/claude/fix-cantrips-selection-bug-CSwVv --stat` for the
files this branch plans to modify:

| File | Sibling delta | This branch's planned touch | Same-hunk risk |
|------|---------------|------------------------------|----------------|
| `src/cljc/orcpub/dnd/e5/options.cljc` | -47/+47 around lines 461-693 and one `:plugin-source` arg at 2870-2912 | `class-option` body, ~2953 (one-line fix) | **None.** Sibling's hunks are at the spell-selection helpers and the `class-option` arg destructure. Ours is in the `:all` grant body deep inside `class-option`. Different hunks. After rebase our line shifts; content survives. |
| `src/cljc/orcpub/dnd/e5/template_base.cljc` | +8 comment at 272-280 | not modified | None. |
| `src/cljc/orcpub/dnd/e5/classes.cljc` | +7 (`base-class-keys` canonical home) | read-only reference (Paladin config) | None. |
| `src/cljs/orcpub/dnd/e5/events.cljs` | +30/-6 (reconciler wiring, suffix-toggle handler, base-class-keys dedupe) | not modified (we reuse `::classes/set-class-prop` at 3057) | None. |
| `src/cljs/orcpub/dnd/e5/views.cljs` | 0 | builder spellcasting UI ~5738-5799 (new dropdown + on-change rewrite) | None — sibling does not touch this file. |

Net: no expected merge conflicts in the planned hunks. The rebase is
mechanical.

### Logical overlap (no code conflict, but worth knowing)

- **`?prepare-spell-count` name-to-kw at `template_base.cljc:275`.** Both
  branches *defer* this. Sibling has the explicit code comment; this branch
  documents the same decision in its plan. Reconciler: leave the deferral
  intact, do not let either side silently take it on.
- **`::prepared-spells-by-class` storage keyed by class display name.**
  Discussed in this branch's planning conversation. Real fragility for
  homebrew classes that get renamed (orphans prepared-spell selections under
  the dead name). Not addressed by either branch. Filing as TODO is on the
  agenda; not in scope here.
- **Sibling's class-key-based identity is *compatible with* and *supports*
  what this branch enables.** A homebrew `:all` half-caster created via the
  new builder dropdown will derive spell-selection keys from `:class-key`
  (Phase 2 behavior) — meaning rename-orphaning at the spell-selection layer
  is structurally fixed for our new content. Good.

### Recommended merge order

1. **Sibling lands first, this branch rebases.** Recommended.
   - Sibling is further along (multiple commits, tests, docs); this branch
     hasn't started.
   - Rebase is mechanical (table above). Our `:all` grant change at line 2953
     shifts to a new line number; content is unchanged.
   - This branch's new builder-UI flow inherits Phase 2's key-based
     spell-selection identity for free.
2. *Alternative if blocked:* this branch lands first (smaller PR). Sibling
   then rebases. Their Phase 2 edits to `spell-selection` /
   `spell-selection-key` / `spells-known-selections` don't physically touch
   our `:all` grant area. Conflict resolution still mechanical.
3. **Independent simultaneous merge to master is fine** — git will resolve
   without intervention given the table above. Reviewer can pick either
   ordering at merge.

### File path collision: two `web-handoff.md` files

The sibling's handoff is at repo root `web-handoff.md`. This one is at
`docs/kb/web-handoff.md` precisely to avoid the merge collision. On post-merge
cleanup, both could be archived together under `docs/kb/` with date-stamped
names (`web-handoff-cantrip-fix-2026-05.md`,
`web-handoff-half-caster-2026-06.md`) or simply deleted. Not a blocker.

## Verified findings

- The character engine already supports prepared half-casters generically.
  Cleric/Druid/Paladin are the only `:known-mode :all` classes today
  (`classes.cljc:399`, `760`, `1430`); none has a `:spell-list-kw`. None has
  a `:spells-known` table. The `?prepare-spell-count` formula at
  `template_base.cljc:274-284` produces the exact Paladin per-day count for
  any `slot-factor 2` class.
- `options.cljc:2953` (the `:all` grant) currently hardcodes
  `(spell-lists kw)`. **Reproducible silent failure:** any homebrew `:all`
  class with `:spell-list-kw` set resolves the lookup to `nil` → `mapcat`
  over nil → zero spell modifiers → spell section renders empty with no
  error. The one-line fix uses the same `(or :spell-list-kw kw)` idiom
  already in use at `options.cljc:657` (the Learns path).
- An orcbrew `:all` class structurally identical to built-in Paladin
  (`:known-mode :all` + `:prepares-spells? true`, no `:spells-known`) does
  not crash today. `spells-known-selections` is called with nil
  `:spells-known` and reduces to `{}` (no picker — correct). This means a
  user who hand-edits the orcbrew today can already get prepared half-caster
  behavior; the only thing missing is the *builder UI surface* to set it.
- This branch's `views.cljs` change does NOT use `::classes/set-class-path-prop`
  (which the existing caster-level dropdown uses today) — it uses
  `::classes/set-class-prop` so the `:on-change` can both add `:known-mode` /
  `:prepares-spells?` and dissoc `:spells-known` in one assoc of the whole
  `:spellcasting` map. The plan calls for switching the existing caster-level
  dropdown to the same pattern for the same reason (must omit `:spells-known`
  on `:all`).

## Reasoning traps to avoid

- **Don't take on `?prepare-spell-count` here.** Sibling defers it with a
  code comment. This branch defers it in its plan. The fix is ~100 callsites
  across `classes.cljc` + a thread-through in `template_base.cljc`. Its own
  PR.
- **Don't conflate the two grant paths in `class-option`.**
  - `:schedule` / `:acquire`: `spells-known-selections` (line 707) →
    `spell-selection` (line 478, now key-based via Phase 2). **Sibling owns
    this site.**
  - `:all`: the `mapcat (fn [[lvl spell-keys]] (map ... spells-known-cfg ...))`
    block (lines 2952-2969, where this branch's one-line fix lives). Sibling
    does not touch this. **This branch owns this site.**
- **Don't add a `:spells-known` table to homebrew `:all` classes** in the new
  builder. Verified: Cleric/Druid/Paladin have none. `spells-known-selections`
  handles nil correctly. Adding one would be a behavior change.
- **Don't modify built-in class option-fns** to thread a new arg. The plan
  for this branch is UI-only plus the one-line `:all` grant fix. Don't expand
  scope into the engine.
- **`name-to-kw` at `events.cljs:534-563` (`reg-save-homebrew`) is the
  legitimate editor-side rename mechanism** — see
  `docs/kb/key-vs-name-separation.md`. Do not touch as part of this branch.

## Where to start

1. Verify branch state: `git fetch origin claude/half-caster-prepared-spells-Jo0ai
   && git log -20` (this branch) and same for sibling.
2. Read `docs/kb/key-vs-name-separation.md` (sibling-shipped, durable) to
   understand the identity model the engine now uses.
3. Implement the three changes in `views.cljs` (UI dropdown, mode-aware
   level-factor on-change) and `options.cljc:2953` (one-line `:all` grant
   fix). Existing event `::classes/set-class-prop` at `events.cljs:3057` is
   reused — no new events.
4. Verify per the plan's verification section: build a homebrew half-caster
   that prepares spells, check the per-day prepared count equals
   `ability-mod + floor(level / 2)`, regression-test built-in Paladin and
   Cleric characters.

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
  (the one the current caster-level dropdown uses; we move away from it)
- `src/cljc/orcpub/dnd/e5/options.cljc:2860+` — `class-option` (target for
  change 3 at line 2953)
- `src/cljc/orcpub/dnd/e5/options.cljc:657` — the `(or :spell-list-kw kw)`
  precedent for the Learns path (validates change 3's idiom)
- `src/cljc/orcpub/dnd/e5/classes.cljc:1430` — Paladin config (the structural
  reference for what a built-in prepared half-caster looks like)
- `src/cljc/orcpub/dnd/e5/template_base.cljc:274-284` — `?prepare-spell-count`
  (the generic engine math; do not modify on this branch)
- `docs/kb/key-vs-name-separation.md` — sibling-shipped design rule (read it)
- Sibling's `web-handoff.md` and `HANDOFF.md` at the sibling branch's repo
  root — sibling's intra-branch handoffs (read for full context)
- `/root/.claude/plans/half-casters-with-prepared-stateful-flute.md` — this
  branch's approved plan (session-scratch, may not survive context handoff)
