# Cantrip / spell-selection source-poisoning fix — changes + verified research

> **Branch:** `claude/fix-cantrips-selection-bug-CSwVv`
> **Status:** Phases 1–2 shipped on-branch; two gaps found and fixed this
> session; all behavior below **verified end-to-end against the running app**
> (local Playwright → codespace public URL), not just reasoned from code.
> **Related:** [name-to-kw-audit.md](name-to-kw-audit.md),
> [homebrew-class-spellcasting.md](homebrew-class-spellcasting.md),
> [srd-vs-plugin-content.md](srd-vs-plugin-content.md),
> [error-handling-import-validation.md](error-handling-import-validation.md).

## The bug (one sentence)

The app derived **identity** (`:key` / spell-selection keys) from a **display
string** (`:name`): a UX change appended the homebrew source to a class's
`:name` (e.g. `"Cleric (Kibbles Tasty)"`), and downstream code re-derived
spell-selection keys from that mutated name via `common/name-to-kw`, so saved
characters keyed on the canonical `:cleric-cantrips-known` were orphaned and the
UI showed their cantrips/spells unselected — users had to re-pick spells.

## Feature changes

### Committed on-branch
| Commit | Change |
|---|---|
| `0a4f262` (Phase 1) | Revert the `:name` mutation in the `::classes5e/plugin-classes` sub (`spell_subs.cljs`); carry source in a separate **`:plugin-source`** field; add the **`::show-class-source-suffix` toggle** ("Show homebrew source on class names", control at `character_builder.cljs:293`, render helper `class-option-display-name` at `:197`); add the load-time reconciler **`reconcile-spell-selection-keys`** (`content_reconciliation.cljs`), wired into `:set-character` (`events.cljs:1223`). |
| `251e1a7` (Phase 2) | Derive spell-selection keys from **`:class-key`**, not the display name (`options.cljc` `spell-selection-key`); reconciler computes expected keys from `class-key`; input becomes a set of known class keys. |
| `1c24a8e` | Dedupe `base-class-keys` → canonical home `classes.cljc`. |

### Fixed this session (were uncommitted at time of writing)
1. **`character_builder.cljs:261` — the toggle was silently broken for custom
   classes.** Phase 1 plumbed `:plugin-source` through `option-cfg`, but the
   class dropdown renders options passed through `class-level-data`, whose
   `select-keys` whitelist **dropped `::t/plugin-source`** one hop before render.
   So `class-option-display-name` always received `plugin-source = nil` and the
   toggle did nothing. **Fix:** add `::t/plugin-source` to that whitelist.
   *General lesson: when you plumb a new display field through `option-cfg`,
   audit every downstream `select-keys` that rebuilds options.*
2. **`test_runner.cljs` — the reconciler tests existed but never ran.**
   `content_reconciliation_test.cljs` (all three states + spell-preservation)
   was not in the runner's require/`run-tests`. Wired it in.

## Verified research (this session, e2e)

Three Playwright specs, all green, run on this machine against the codespace's
public URL (the codespace's own headless Chromium OOMs on large content; see
"E2E method" below).

1. **Toggle fix — `source-suffix-toggle`.** Import a homebrew class, flip the
   toggle: **OFF → `"Artificer"`, ON → `"Artificer (sourced-classes)"`.** Before
   the `select-keys` fix, ON showed no source. *(The displayed source is the
   import's source key — for a flat single-source orcbrew that's the **file
   name**, not the `:option-pack` field inside.)*
2. **Pak import — `test-pak`.** Multi-source orcbrew imports via the conflict
   modal (**RENAME ALL → RESOLVE ALL**), and a homebrew base class appears:
   class dropdown = 13 (12 SRD + **"Sorcerer (Divine Soul)"**). No crash at this
   size (~0.7 MB).
3. **Remediation — `spell-remediation-homebrew`.** A homebrew (Divine Soul
   sorcerer) character with a **poisoned** key
   `:sorcerer-divine-soul-xanathars-cantrips-known` holding 2 cantrips; on
   `:set-character` the reconciler **heals it to `:sorcerer-divine-soul-cantrips-known`
   with both cantrips preserved** (`healedToCanonical:true, stillPoisoned:false,
   cantripCount:2`). This is the user-facing fix proven in-app: no re-picking
   spells.

### Verified facts / corrections to prior docs
- **The reconciler is plugin-scoped at Phase 1** — it heals **homebrew (plugin)
  classes, not built-ins**. Verified: an injected poisoned key on a *built-in*
  Cleric was **not** healed; the same on a *homebrew* sorcerer **was**. This is
  correct: built-ins have no source, so they can't be source-poisoned. (Phase 2
  widens the known-key set to built-ins ∪ plugins.)
- **A BOM in an orcbrew is a non-issue.** Import reads via
  `FileReader.readAsText`, which strips a leading UTF-8 BOM before the EDN reader
  sees it (verified with a browser harness). No BOM-stripping code is needed.
- **`?prepare-spell-count` (`template_base.cljc:275`) is a *transient* wrong-count,
  not persisted corruption.** Its `name-to-kw` output feeds a *number*
  (slot-factor → prepared count), never a stored key — a non-round-tripping name
  yields a wrong on-screen count, recomputed each load, no saved-data orphaning.
  Refines the framing in [name-to-kw-audit.md](name-to-kw-audit.md) §7.2.
- **Homebrew content is client-side only by design** (localStorage; export
  `.orcbrew` for safety) — not a bug, do not "rediscover" it.
- **Big-pack import halts on a conflict-resolution modal** ("N conflicts need
  resolution before import can continue") for multi-source packs with shared
  keys; the import does not complete until resolved (RENAME ALL / RESOLVE ALL).

## E2E method (why it's not run headless in the codespace)

The codespace's headless Chromium OOMs **materializing** large packs (full
MegaPak, ~2.4 MB), independent of `/dev/shm` or system RAM. Stable approach:
run **Playwright on a workstation** pointed at the codespace's **public** port
(`APP_URL=https://<cs>-8890.app.github.dev`), clicking through the GitHub
public-port **"Continue"** interstitial (`getByText('Continue',{exact:true})`).
Use a **smaller pak** (e.g. one source via `e2e/edn-split.js`) for content that
must render in the builder. re-frame is reachable from the page in the dev
build (`re_frame.db.app_db`, `re_frame.core.dispatch_sync`,
`cljs.reader.read_string`, `orcpub.common.name_to_kw`) — used to craft
poisoned saved-state for the remediation test. See `E2E-NOTES.md`.

## Still open / deferred (not part of this fix)
- `?prepare-spell-count` class-key threading — its own PR (audit §7.2). Per the
  "bugs don't ship by comparison" rule, this must be proven non-manifesting
  under the name-lock or patched before that work ships.
- Cross-file subclass→class relink; `::prepared-spells-by-class` keyed by display
  name (same disease, different storage layer). See `docs/TODO.md`.
