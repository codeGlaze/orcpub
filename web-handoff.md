# Web handoff: cantrip/spell-selection regression + identity architecture

Branch: `claude/fix-cantrips-selection-bug-CSwVv`
Audience: agent picking up the work in a fresh web session. Read this BEFORE making any claims about codebase state — local checkouts can be stale relative to the branch HEAD. Verify with `git log claude/fix-cantrips-selection-bug-CSwVv -20` and `git status` first.

## The bug

Homebrew Cleric/Druid replacements lose cantrip/spell selections after a recent UX change. Saved entity has `:cleric-cantrips-known`; UI shows nothing selected. Setting orcbrew source name to blank temporarily "fixes" it, proving the source label leaks into the selection key.

Root cause: `spell_subs.cljs:475-478` (the `::classes5e/plugin-classes` sub) mutated class `:name` to `"Cleric (Source)"` for display. Downstream consumers re-derived identity via `common/name-to-kw` from the mutated `:name`. Saved characters bound to the canonical key got orphaned because the new template produced a different shifted key.

## What's shipped on this branch (verify with git log)

- `0a4f262` Phase 1 — revert `:name` mutation in `spell_subs.cljs`, plumb `:plugin-source` as a distinct `::plugin-source` slot through `option-cfg`, add `::show-class-source-suffix` user-pref toggle, add load-time reconciler `reconcile-spell-selection-keys` in `content_reconciliation.cljs`, wire into `:set-character`. Tests included.
- `a77d0a1` Kb doc: `docs/kb/key-vs-name-separation.md` — design rule, four-leak-site case study, plugin-load race verification.
- `46310f3` Three source comments pointing to the kb doc at speculation-prone sites.
- `af5e6fe` `console.info` on `:rewrote` results for field debugging.
- `dd4144d` Dense `HANDOFF.md` at repo root (older, denser sibling of this doc).
- `251e1a7` **Phase 2 — switch kw derivation from `class-name`-based to `class-key`-based.** Adds `spell-selection-key` helper in `options.cljc` (the dead one was repurposed). Deletes the actually-dead `class-key-name` fallback. Updates the reconciler so `class->expected-spell-keys` computes from `class-key`, drops the `:parked` accumulator (which was unreachable scaffolding for a UI we're not building). Adds a comment at `template_base.cljc:275` (`?prepare-spell-count`) explicitly deferring the propagation fix to a follow-up. Reconciler input changes shape from "seq of class data" to "set of known class keys"; events.cljs adjusted accordingly. Test fixtures rewritten to model the post-phase-2 migration shape.
- `1c24a8e` Dedupe `base-class-keys` — canonical home is now `classes.cljc`; the duplicate in `events.cljs:4691` is removed, and `content_reconciliation.cljs`'s `builtin-classes` set is folded back to reference the canonical one.

## Architectural decision

The hardest decision of the conversation: phase 1's revert defended the design rule by convention only — anyone re-mutating `:name` would replay the bug. The user explicitly rejected convention-only as a stopping point. Phase 2 commits to:

> Outside the class editor, identity flows from `class-key`. `class-name` (i.e. `:name` on class config) is display only. Display chains stay; identity chains get rewired to use `class-key` directly.
> 
> Inside the class editor, `(name-to-kw name)` at `events.cljs:534-563` (`reg-save-homebrew`) IS the legitimate `:name → :key` rename mechanism. The editor owns key changes intentionally. This is the carve-out exception to the rule.

The bug class is now structurally impossible at the fixed site (`spell-selection`), not just convention-defended.

## Verified findings

- **Plugin-load race is not real.** Reviewer-raised concern that `:set-character` could fire before plugins hydrate, leaving the reconciler with empty expected-keys. **Verified false.** `::e5/plugins` is registered via `reg-local-store-cofx` at `db.cljs:302` — synchronous localStorage read. `:initialize-db` (`events.cljs:208`) injects both plugins and character cofx and writes them atomically into the same `db` value. Subsequent `:set-character` dispatches see fully-hydrated plugins. The "user opens character on a machine without the originating plugin" case is real but distinct — handled by the existing missing-content banner.

- **Editor save regenerates `:key` from `:name`** at `events.cljs:534-563` on every homebrew save. Combined with import-rename only changing `:key` (not `:name`), this means import disambiguation is destroyed by any subsequent edit. Phase 3 (planned, not yet shipped) addresses this.

- **Import-rename only changes `:key`** at `import_validation.cljs:1388-1394` (`generate-new-key`). `:name` is untouched. This is why disambiguation is fragile.

- **`class->expected-spell-keys`'s `:parked` accumulator was vapor.** Verified by tracing the candidate count — `class->expected-spell-keys` always emits 2 keys with distinct suffixes, so candidates is always 0 or 1; single-survivor always rewrites; the multi-candidate parking branch was unreachable. Dropped in phase 2.

- **`base-class-keys` was duplicated.** `events.cljs:4691` and `content_reconciliation.cljs:163` (`builtin-classes`) both defined the same 12-class SRD set. Canonical home is now `classes.cljc`; the duplicates were removed in `1c24a8e`.

- **There is no lightweight runtime registry of built-in content.** Built-in classes are 12 imperative source-code functions (`barbarian-option`, `cleric-option`, etc. in `classes.cljc`). Their KEYS only exist inside the option-cfgs those functions return when called with real args. No data-shaped enumeration exists; the `base-class-keys` set is a hand-maintained shadow. Same pattern repeats for `builtin-races`, `builtin-subraces`, `builtin-subclasses`, `builtin-backgrounds`, `builtin-feats` — all currently shimmed via hardcoded sets in `content_reconciliation.cljs`. This is real architectural debt; see "open architectural concern" below.

## What's NOT yet shipped (decisions made, code pending)

### Phase 3 — import-rename hardening
Decision: append a short source ABBREVIATION (not the full slug) to both `:key` AND `:name` on import-conflict.
- Abbreviation rule: 1–3 words → first+last letter of each word (`"Kibbles' Tasty"` → `KsTy`); 4+ words → first letter only (`"Tasha's Cauldron of Everything"` → `TCoE`). Matches community-standard D&D abbreviation conventions.
- Numeric tie-breaker on actual collision (`KsTy` already taken → `KsTy2`).
- Override case: user wanting to override a built-in opens the class in the editor and removes `(KT)` from `:name`; save regenerates `:key` to `:cleric` (clean), override becomes active. The reconciler then handles orphan rebind on next character load.
- **Stored abbreviation field:** user undecided. Not adding to schema unless a collision-resolution UI gets built.

### Relink UI
Decision: extend the existing missing-content banner at `character_builder.cljs:1940-1972`, do NOT build new detection.
- The existing `::char5e/missing-content-report` sub already detects unloaded classes/subclasses/races/etc.
- The existing `find-similar-content` at `content_reconciliation.cljs:132` already produces scored candidate suggestions.
- Extend the banner with rebind buttons that consume those existing `:suggestions`. Inline placement (matches existing precedent), not modal.
- New event handler `relink-class-entry` rewrites `::entity/key` on the chosen class entry and re-dispatches `:set-character`.

### Subclass-mismatch detection
Two flavors discussed:
- (a) Subclass not loaded — already covered by missing-content-report.
- (b) Subclass IS loaded but linked to wrong class on the character — **deferred to follow-up**. Detection requires new logic (cross-reference subclass `:class` field against character's class entries).

## Scope explicitly excluded from this PR

- **`?prepare-spell-count` propagation** at `template_base.cljc:275`. Clean fix requires threading `:class-key` through `spell-data`, `spells-known` modifier, and ~100 `mod5e/spells-known` callsites scattered across `classes.cljc`. Latent risk; active behavior is correct under phase 1's revert. Code comment + TODO added.
- **Refactoring how base class option-fns are written** (e.g. into multimethods, registries, or compile-time scanned macros). User explicitly rejected. Stays as imperative source-code functions.
- **Subclass-cross-file linking** (subclass imported in a separate orcbrew from its parent class). Real but narrow blast radius. Punt to `docs/TODO.md`.
- **Generalizing relink to feats / races / subraces / backgrounds.** Same pattern would apply; each needs its own audit.

## Open architectural concern

Every built-in content type has a hardcoded "shim shadow" enumeration in `content_reconciliation.cljs` (`builtin-races`, `builtin-subraces`, `builtin-subclasses`, `builtin-backgrounds`, `builtin-feats`) plus the `base-class-keys` in `classes.cljc`. These exist because built-in content is defined as imperative option-fns and plugin content is data; the two don't unify cheaply.

User is justifiably frustrated by this — the app obviously knows about its content (every dropdown lists them), but there's no clean way to query that knowledge without either calling the heavy option-fns or maintaining manual shadow sets.

Minimum-invasive fix proposed but not yet committed: an app-init event that runs the existing heavyweight subs once at startup, extracts keys for each content type, caches at db path `[:content/known-keys]`. All `builtin-*` sets read from the cache instead of being hand-maintained lists. Eliminates the shim pattern without refactoring how option-fns are written.

User asked whether Clojure/cljs offers something better. Surveyed: `ns-publics` (brittle under cljs `:advanced` compilation), macros (workable, add complexity), multimethods (idiomatic but requires changing how option-fns are written — rejected), var metadata (same compilation concern). The app-init cache pattern is the realistic fit.

**Open question:** fold the architectural cleanup into this PR, or file as a separate high-priority TODO?

## Reasoning traps from prior sessions (do not repeat)

- **Conflating `class-name` (a function parameter) with `:name` (a class config field).** They are synonymous *by convention only* — callers populate the parameter from `:name`. The parameter slot itself is arbitrary.
- **"Dead code" claims without thorough verification.** A grep that misses one usage in a related file leads to incorrect deletion plans. Use multiple search patterns (`\bfoo\b`, `foo/`, `qualified.ns/foo`) and check both src/ and test/ before assuming a symbol is unused.
- **Trusting local-checkout state when the branch has remote commits.** This handoff itself was originally written against a stale local clone that didn't have any branch commits visible. Always `git fetch origin <branch>` and `git log <branch>` before claiming anything about the branch state.
- **Inventing reconciler outputs for UIs that don't exist.** The `:parked` accumulator was a hook into vapor. Verify the producer fires in real data before consuming.
- **Proposing UI components without checking for existing precedent.** The missing-content banner already does most of what the relink UI needs.
- **Trusting reviewer claims without re-verifying.** Plugin-load race was claimed real; it isn't.
- **Treating phase 1's revert as a complete solution.** Phase 1 stops the active bleed but defends only by convention. The user explicitly rejected this as a stopping point. Phase 2's architectural switch is mandatory.

## Where to start

1. Verify branch state via `git fetch origin claude/fix-cantrips-selection-bug-CSwVv && git log -20`. Reconcile against any state the user mentions; trust the remote over any stale local checkout.
2. Pending decisions to confirm with the user before writing code:
   - Should the architectural cleanup (shim-shadow cache) fold into this PR or be a separate follow-up?
   - Confirm relink UI inline placement and event-handler shape.
   - Confirm stored-abbreviation field stays deferred.
3. The natural next implementation step is phase 3 (`import_validation.cljs` abbreviation logic + `:name` suffixing). Phase 2 is already shipped.

## Key files

- `src/cljs/orcpub/dnd/e5/spell_subs.cljs:454-483` — `::classes5e/plugin-classes` sub; historic mutation site, now reverted
- `src/cljc/orcpub/dnd/e5/options.cljc:469` — `spell-selection-key` helper (phase 2's class-key-based derivation)
- `src/cljc/orcpub/dnd/e5/options.cljc:478-488` — `spell-selection` using the helper for `kw`
- `src/cljc/orcpub/dnd/e5/template_base.cljc:275` — `?prepare-spell-count`; deferred site with code comment
- `src/cljs/orcpub/dnd/e5/events.cljs:534-563` — `reg-save-homebrew`; the editor save path (carve-out exception to the design rule)
- `src/cljc/orcpub/dnd/e5/classes.cljc` — `base-class-keys` canonical home (post-dedupe)
- `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs:132` — `find-similar-content`; reuse for relink UI candidate ranking
- `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs:163+` — remaining `builtin-*` shim sets (the architectural-debt site)
- `src/cljs/orcpub/character_builder.cljs:1940-1972` — existing missing-content banner; extend, don't duplicate
- `src/cljs/orcpub/dnd/e5/import_validation.cljs:1388-1394` — `generate-new-key`; phase 3 target
- `src/cljs/orcpub/dnd/e5/spell_subs.cljs:937-983` — `::classes5e/classes` + `::classes5e/class-map` (heavyweight unified sub)
- `src/cljs/orcpub/dnd/e5/subs.cljs:1435-1467` — `::char5e/available-content` + `::char5e/missing-content-report` (existing detection)
- `docs/kb/key-vs-name-separation.md` — durable case study + design rule
- `HANDOFF.md` — older sibling doc with more granular file:line references
