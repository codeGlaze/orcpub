# Handoff: cantrip/spell selection regression fix

Branch: `claude/fix-cantrips-selection-bug-CSwVv`
Status: phase 1 shipped; phase 2 + phase 3 + relink UI planned, not yet implemented
Last updated by previous agent at end of design conversation. Read this fully before touching code.

## The bug

Homebrew Cleric/Druid replacements lost cantrip/spell selections after a recent UX change. Saved entity still had `:cleric-cantrips-known`; UI showed nothing selected. Setting orcbrew source name to blank temporarily "fixed" it.

Root cause: `spell_subs.cljs:475-478` (the `::classes5e/plugin-classes` sub) mutated class `:name` to `"Cleric (Source)"` for display. Downstream consumers re-derived identity via `common/name-to-kw` from the mutated `:name`. Saved characters using the canonical `:cleric-cantrips-known` key were orphaned because the new template produced `:cleric-source-cantrips-known`.

Full case study in `docs/kb/key-vs-name-separation.md` including the four leak sites.

## What's shipped (phase 1)

Commits on the branch:

- `0a4f262` — Fix cantrip/spell selection regression + add source-suffix toggle
  - Reverted the `:name` mutation in `spell_subs.cljs`
  - Plumbed `:plugin-source` through `option-cfg` as a distinct `::plugin-source` slot
  - Added user-pref toggle `::show-class-source-suffix` (default off, pure display)
  - Added `class-option-display-name` helper in `character_builder.cljs`
  - Added `reconcile-spell-selection-keys` in `content_reconciliation.cljs` (load-time auto-rebind for unambiguous orphans)
  - Wired into `:set-character` in `events.cljs`
  - Tests in `content_reconciliation_test.cljs`
- `a77d0a1` — kb doc capturing the design rule + four leak sites + plugin-load race verification
- `46310f3` — three source comments pointing at the kb doc at speculation-prone sites
- `af5e6fe` — `console.info` on `:rewrote` for field debugging

## The architectural pivot mid-thread

Phase 1 only stopped the *active* leak. It defended the design rule by convention, not by architecture. The deeper question — should identity ever derive from `:name` at all outside the editor? — surfaced during review and led to the current plan.

The user's frame: there are two valid architectures, and the codebase has to commit to one.

1. **`:name` is mutable display.** Identity must not depend on it. Any code, anywhere, can mutate `:name` freely without risk.
2. **`:name` is locked. Display goes in a separate field.** Identity *can* depend on `:name` because `:name` is structurally protected from mutation.

Phase 1 left us at neither — `:name` is unmutated by convention but identity still derives from it via `(name-to-kw title)` at `options.cljc:469`. Anyone who mutates `:name` again replays the bug. The user (correctly) does not want a convention-only defense.

**Direction chosen: architecture #1.** Identity flows from `class-key`, not `class-name`/`:name`. `:name` is allowed to mutate freely; nothing identity-bearing depends on it outside the editor.

## What's planned next

### Phase 2 — switch kw derivation to class-key

**The surgical rule:** wherever a value sourced from `:name` ends up at `name-to-kw` and produces a key, replace the input with `class-key` at that terminus. Display chains (`class-name → title → user-facing label`) stay untouched — those legitimately use `:name`.

Three sites to change:

1. **`options.cljc:469`** in `spell-selection`: `kw (common/name-to-kw title)` → derive `kw` from `class-key` (already in the kwarg destructure). `title` stays for display.
2. **`template_base.cljc:275`** in `?prepare-spell-count`: takes `class-name` string today. Change to take `class-key` directly. Callers adjust.
3. **`options.cljc:635`** `class-key-name` fallback: latent today (callers pass `class-key`), but the fallback derivation is wrong. Switch to class-key.

**Sites that stay alone:**
- `events.cljs:544` `reg-save-homebrew`: `key (common/name-to-kw name)` — this is the class editor's save path. Editor-mediated rename mechanism. Editor owns key changes. **Do not touch.**
- `template.cljc:option-cfg`/`selection-cfg` constructors: operating on destructured kwargs at template-build time. They mint keys; they're not downstream consumers of mutable name fields.

### Phase 3 — import-rename hardening

Today (`import_validation.cljs:1388-1394`), import-conflict-rename appends the full slugified source name to `:key` only. `:name` is untouched. Combined with `events.cljs:544` regenerating `:key` from `:name` on every editor save, **the import-disambiguation lasts only until the user opens the class in the editor**. First save obliterates it.

Fix:

- On import-conflict, compute a source abbreviation:
  - 1–3 words: first+last letter of each word (e.g., `"Kibbles' Tasty"` → `KsTy`, `"Kindly Townsfolk"` → `KyTk`)
  - 4+ words: first letter of each (e.g., `"Tasha's Cauldron of Everything"` → `TCoE`, `"Mythic Odysseys of Theros"` → `MOoT`)
  - Numeric tie-breaker only on detected collision (`KsTy` already taken → `KsTy2`)
- Append the abbreviation to both `:key` AND `:name` (e.g., `:key :cleric-kt`, `:name "Cleric (KT)"`).
- Editor save now regenerates `:key` from `:name`; because `:name` includes `(KT)`, the regenerated key remains `:cleric-kt`. Disambiguation survives edits.
- Override case: a user wanting to intentionally override a built-in opens the class in the editor and deletes `(KT)` from `:name`. Save regenerates as `:cleric`. Override active. The reconciler then handles the orphan rebind on next character load (auto if unambiguous, surface to UI otherwise).

**Stored abbreviation field:** undecided. The user is on the fence. Pros (deterministic source-of-truth, enables collision-resolution UI, decouples abbr from source rename) vs. cons (schema bloat, two-place state, recompute is deterministic anyway). Default plan: do not store unless the collision-resolution UI gets built.

### Reconciler — updates and additions

The shipped reconciler at `content_reconciliation.cljs` stays as infrastructure but needs three updates:

- `class->expected-spell-keys` computes from `class-key` (not `:name`) to match the new derivation. Same suffix-match logic, different input.
- **Drop `:parked`** accumulator from `reconcile-class-entry-options` and `reconcile-spell-selection-keys`. It was scaffolding for a UI that consumes empty data — `:parked` cannot fire today (class expected-keys always has 2 keys with distinct suffixes, so candidates is always 0 or 1; single-survivor always rewrites). Pure vapor surface area.
- **Add `:unbound-classes`** accumulator. For each character class entry whose `::entity/key` doesn't match any currently-loaded class (built-ins + plugins), emit `{:class-key K :inferred-name N :candidates [...]}`. Feeds the relink UI.

New reconciler return shape: `{:character ... :rewrote [...] :unbound-classes [...]}`.

**Add subclass-mismatch detection.** For each subclass entry on the character, check its `:class` reference. If it doesn't match any class entry on the same character, flag it. Auto-rebind if unambiguous (one candidate matches); surface in the relink UI otherwise.

### Relink UI

Inline on the character builder, modeled on the existing missing-content banner at `character_builder.cljs:1940-1972`. Same surface, same expand/collapse pattern, adds an action.

Sections:
- **Unbound classes:** "Class `:cleric-kt` from this character isn't currently loaded. Pick one: [list of loaded candidates ranked by name match / prefix share]. [Rebind] [Leave as-is]"
- **Subclass mismatch:** similar shape, but the candidate list is the character's own class entries.

Picking rebinds via an event handler that mutates `::entity/key` on the entry (class) or `:class` field (subclass), then re-dispatches `:set-character` to refresh. Dismiss leaves the orphan; banner reappears next load.

**Modal vs. inline:** inline. Matches existing missing-content banner precedent; lets user read both without losing context.

### Reconciler input — use existing aggregation

The reconciler today receives `loaded-plugin-classes` (plugin classes only — built-ins are not in the list). For `:unbound-classes` to be accurate, it needs to know about built-ins too.

**Do not build a new helper.** The app already aggregates via `::classes5e/classes` at `spell_subs.cljs:937-960` — that's what the dropdown consumes. The reconciler should consume the same source. For event-handler context, either replicate the aggregation logic directly from `db` (built-in keys are source-code constants in `class5e`; plugin keys come from `(:plugins db)`) or call the same helper functions the sub uses.

With key-based derivation, the reconciler's needs simplify further — for the spell-selection rewrite path it only needs "set of known class keys" (built-ins ∪ plugins). No `:name` lookups. Class names matter only for the relink UI's candidate ranking.

## Why these decisions

This was a multi-hour conversation that took several wrong turns. Documenting so the next agent doesn't repeat them.

### Why phase 1 wasn't enough

The shipped fix reverted the active leak but left identity still flowing through `:name`. The defense was "don't mutate `:name`" — a convention, enforced by docs and comments. The user pointed out this is exactly the kind of convention-only defense that fails the moment someone forgets, and identity hardening should be architectural, not procedural. Hence the pivot to class-key derivation.

### Why `:parked` is vapor and was removed from the plan

The reconciler returned `{:rewrote [...] :parked [...]}`. `:parked` was designed to feed a "manual rebind" UI for orphans the auto-rewrite couldn't resolve. Two failure modes were imagined:
- Multiple candidates within a class entry (ambiguous suffix match) — **unreachable** because `class->expected-spell-keys` emits exactly 2 keys with distinct suffixes ("cantrips-known", "spells-known"); per-orphan, candidates is always 0 or 1.
- Zero candidates — only fires if the orphan has a malformed suffix the regex would already filter out.

The "originating class not loaded" case the parking UI was meant to surface is a class-level orphan, not a spell-selection orphan. It's covered by the existing "Missing Content" banner at `character_builder.cljs:1940-1972` (which subscribes to `::char5e/missing-content-report`).

**Replacement:** `:unbound-classes` actually fires when a class isn't loaded — that's real data. The relink UI consumes that, not `:parked`.

### Why the plugin-load race is a non-issue (verified)

A reviewer worried that `:set-character` might fire before plugins hydrate, leaving the reconciler with empty expected-keys. **Not possible** — `::e5/plugins` is registered via `reg-local-store-cofx` at `db.cljs:302` (synchronous localStorage read). `:initialize-db` (`events.cljs:208`) injects both plugins and character cofx and writes them atomically into the same `db`. Subsequent `:set-character` dispatches see fully-hydrated plugins. The "user opened a character on a machine without the plugin" case is real but distinct — handled by the missing-content path / soon the relink UI, not by a race fix.

### Why import-rename is fragile (the editor save problem)

The user spotted this mid-conversation. `import_validation.cljs:1388` only renames `:key`. `events.cljs:544` always regenerates `:key` from `:name` on save. So conflict-renamed homebrew keys (`:artificer-kibbles-tasty`) are transient — first edit collapses them back to `:artificer`. This is what phase 3 fixes by getting the abbreviation into `:name`.

### Why we keep the suffix toggle

Phase 1 introduced `::show-class-source-suffix`. Originally framed as "decouple display from identity"; under the new architecture (key-based derivation) it's now purely a display preference. No architectural debt, low complexity. Keep.

### What we are deliberately not doing

- **Editor-side rebind trigger on rename.** Reconciler runs at next character load (lazy, per-character). Editor-mediated trigger is more immediate but adds plumbing. Defer.
- **Subclass-imported-in-separate-orcbrew-from-parent linking.** Cross-orcbrew-file subclass-to-class link breakage. Real but narrow blast radius (one subclass file referencing a renamed parent). Punt to `docs/TODO.md` or the agents-repo TODO.
- **Generalize the relink mechanism to all content types (race, feat, subrace).** Same pattern would apply but each needs its own audit. Spell-selection / class / subclass coverage is the scope of this PR.

## Files to know

### Modified or created in phase 1 (already shipped)
- `src/cljs/orcpub/dnd/e5/spell_subs.cljs` (`:467-485` — mutation revert; new comment above the sub)
- `src/cljc/orcpub/template.cljc` (`:74-88` — `::plugin-source` slot on `option-cfg`)
- `src/cljc/orcpub/dnd/e5/options.cljc` (`:2860-2887` — `class-option` pass-through; `:680-681` dead-binding cleanup)
- `src/cljs/orcpub/character_builder.cljs` (`:197-222` — toggle UI + display helper)
- `src/cljs/orcpub/dnd/e5/db.cljs` (`:283` — pref spec)
- `src/cljs/orcpub/dnd/e5/events.cljs` (`:1213-1230` — reconciler wiring + `console.info`; toggle handler elsewhere)
- `src/cljs/orcpub/dnd/e5/subs.cljs` — pref subscription
- `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs` — `reconcile-spell-selection-keys` + helpers
- `test/cljs/orcpub/dnd/e5/content_reconciliation_test.cljs` — reconciler tests
- `docs/kb/key-vs-name-separation.md` + `docs/kb/README.md`

### To modify in phase 2 + 3
- `src/cljc/orcpub/dnd/e5/options.cljc` (`:469`, `:611-620`, `:680-687`, `:635`, `:1919-1928`) — kw derivation switch + caller adjustments
- `src/cljc/orcpub/dnd/e5/template_base.cljc` (`:275`) — `?prepare-spell-count` takes class-key
- `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs` — drop `:parked`, add `:unbound-classes`, add subclass-mismatch detection, update `class->expected-spell-keys`
- `src/cljs/orcpub/dnd/e5/import_validation.cljs` (`:1388-1394` and surrounding) — abbreviation rule, name-suffixing, numeric tie-breaker
- `src/cljs/orcpub/dnd/e5/events.cljs` — broaden `loaded-plugin-classes` to include built-ins, or call the same aggregation the sub uses; add relink event handlers
- `src/cljs/orcpub/dnd/e5/subs.cljs` — subscription for unbound-classes / subclass-mismatch from reconciler state
- `src/cljs/orcpub/character_builder.cljs` — relink UI section (model after existing missing-content banner at `:1940-1972`)
- `src/cljs/orcpub/dnd/e5/views.cljs` — relink UI shared components if any
- `test/cljs/orcpub/dnd/e5/content_reconciliation_test.cljs` — new tests for key-based expected, unbound-classes detection, subclass-mismatch
- `docs/kb/key-vs-name-separation.md` — rewrite the "design rule defended by convention" framing into "rule enforced by architecture"
- `docs/TODO.md` — add cross-file subclass-linking note

## Reasoning traps the previous agent fell into

If you find yourself doing any of these, **stop and re-read this doc.**

- Conflating `class-name` (a function parameter) with `:name` (a field on class config). They're synonymous *by convention* — every caller of `spell-selection` populates `:class-name` from `(:name cls-cfg)`. But the parameter slot itself is arbitrary.
- Suggesting we change `title` derivation. `title` is downstream of `class-name`. The fix targets the upstream input wherever `:name`-derived strings reach `name-to-kw`, not the downstream display string.
- Proposing UI for `:parked` orphans. They don't exist in real data. Don't build for vapor.
- Inventing a parallel helper for "loaded classes" when the dropdown already has access to the aggregated set via `::classes5e/classes`. Use what exists.
- Claiming the plugin-load race is real. It isn't. The cofx is synchronous; both plugins and character land in `db` in the same `:initialize-db` atomic write.
- Treating the half-fix (revert `:name` mutation, leave identity name-derived) as a complete solution. It addresses the active regression but leaves the architecture defended by convention only. The user explicitly rejected this as a stopping point.

## Open questions

- Should the stored abbreviation field be added? (Pros/cons in the conversation; user undecided.)
- Modal vs. inline for relink UI? (Tentatively inline, matching missing-content banner precedent. Confirm before building.)
- Candidate ranking for unbound-class relink: exact name match → prefix share → everything else. Sensible default, confirm.

## References

- Design rule and case study: `docs/kb/key-vs-name-separation.md`
- Existing missing-content banner (UI precedent for relink): `src/cljs/orcpub/character_builder.cljs:1940-1972`
- Existing aggregated classes subscription: `src/cljs/orcpub/dnd/e5/spell_subs.cljs:937-960` (`::classes5e/classes`)
- Existing import-rename logic: `src/cljs/orcpub/dnd/e5/import_validation.cljs:1388-1394` (`generate-new-key`)
- Existing class editor save (do not touch): `src/cljs/orcpub/dnd/e5/events.cljs:534-563` (`reg-save-homebrew`)
