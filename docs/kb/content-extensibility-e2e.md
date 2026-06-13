# Content Extensibility — Live / E2E Verification Checklist

**Purpose:** The work on branch `claude/zen-wright-04xhdz` was verified only by the JVM
gate (`lein test` + `lein lint`). That gate does **not** execute the ClojureScript
re-frame subscriptions or the running app. This checklist is for an agent/dev in a full
environment (figwheel + browser + backend/Datomic) to verify the parts the JVM gate
skips, and report back.

**Branch:** `claude/zen-wright-04xhdz` (includes a merge of `feature/name-keyword-fix`).
**What changed and why these checks exist:**
- Phases 1–3b rerouted plugin subscriptions through a new `option_catalog` seam
  (`spell_subs.cljs`: subraces, subclasses, boons, invocations). Behavior should be
  identical — these checks confirm it in a live app.
- The merge brought in the name-keyword fix (stable keys, `::plugin-source`, a
  spell-selection reconciler).
- Phase 4a added a `content_types.cljc` registry (not yet wired to anything; no runtime
  effect expected).

## Setup (use the project's standard dev flow)

- **cljs tests:** `lein fig:test` — compiles the `test` build (`orcpub.test-runner`) and
  runs `subs_test`, `events_test`, `content_reconciliation_test`. Confirm **0 failures**.
- **App:** standard dev setup — backend (Datomic) + `lein fig:dev` for the hot-reload
  frontend. See `docs/GETTING-STARTED.md` / `docs/DATOMIC_SETUP.md` on `agents/develop`
  for environment details.

## Checks

Report PASS/FAIL + notes for each. Capture browser console (F12) errors and a screenshot
of the relevant builder/sheet where noted.

### A. ClojureScript test suite (the JVM gate skips this)
1. `lein fig:test` reports **0 failures / 0 errors**. (Paste the summary line.)

### B. Catalog read-seams — behavior must be UNCHANGED (Phases 1–3b)
Import an `.orcbrew` containing homebrew content, then in the character builder:
2. A homebrew **subrace** appears under its parent race.
3. A homebrew **subclass** appears under its parent class.
4. A homebrew **pact boon** appears in the Warlock level-3 "Pact Boon" selection.
5. A homebrew **eldritch invocation** appears in the Warlock invocation selection.
*(If any fail to appear, the `option_catalog` re-pointing regressed a subscription.)*

### C. name-keyword fix (merged in)
6. Enable the "show homebrew source on class names" preference → class names show the
   source suffix, AND selecting/using the class still works (key unchanged, no orphan).
7. Load a previously-saved character that uses a homebrew class → it still resolves;
   watch the console for spell-selection reconciliation logs and confirm **no errors**.

### D. Backward compatibility (non-negotiable — do not skip)
8. Import a real, pre-existing `.orcbrew` library → all content loads, **no validation
   errors** in the console.
9. Load a saved character that chose a homebrew **pact boon** and a homebrew **subrace**
   → both choices are intact (not "(not loaded)"/orphaned). Then **export** it back to
   `.orcbrew` and re-import → loads cleanly.
10. A character saved on `develop` (before this branch) loads here with identical
    selections.

## Feedback format

Reply with:
- The `lein fig:test` summary line (item 1).
- A PASS/FAIL line per item (2–10) with a one-line note on any failure.
- Console errors/warnings verbatim, and screenshots for B (builder lists) and C/D
  (the loaded character sheet).

Hand the results back (PR comment or message). I'll fix any regression before continuing
to Phase 3c (positional-threading removal) and 4b–4f (wiring the registry).

## What is NOT in scope here
- Phase 3c and 4b+ are not implemented yet, so there is no new registry-driven wiring to
  test. These checks confirm the *current* branch (catalog read-seams + the merged fix)
  behaves exactly like the app did before, plus the fix's intended behaviors.
