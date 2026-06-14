# Branch Context: claude/zen-wright-04xhdz

> **READ FIRST (current direction, v2):** `docs/kb/content-extensibility-direction.md` — now
> **re-centered**. A readability review correctly killed one unreadable wrapper (`by-parent`),
> but that local lesson was briefly over-applied to deflate the whole *capability*. v2 restores
> the spine: an open **pool + grant** composition layer (any (sub)race/(sub)class/feat/background
> can grant filtered, gated choices from any other silo), with a variant forward-compat seam.
> Principle (a *constraint*, not a ceiling): *an abstraction earns its keep only when it's
> thicker than what it hides and reveals intent.* `content-extensibility.md` / `-plan.md` are
> history.
>
> Verifying cljs in this container: `docs/kb/cljs-headless-harness.md` (rebuild recipe;
> the harness lives in ephemeral `/tmp`+`target`).
>
> **Immediate next steps:** (1) ✅ DONE (`9777ce88`) — reverted `by-parent`/`plugin-options`,
> deleted `option_catalog`. (2) ✅ DONE (`3980ea1b`) — `register-homebrew-content!` (the
> **wiring** sub-layer) + boon swapped through it (7 sites → 1); harness-verified. (3) **NEXT:**
> prove the **pool + grant** spine on one slice end-to-end — `resolved-content` indirection +
> a pool sub + the grant primitive; route one existing closed cross-link through it
> behavior-identically (golden/fixture-gated), then add one new open capability
> (e.g. `:draconic-ancestry` pack-extensible pool dragonborn grants from). See direction doc
> v2 §"The spine" + the PINS.
> Goal: **stabilize while adding features — stability and flexibility are the SAME abstraction.**

## Purpose
Capture the content-extensibility analysis and plan, and implement it in gated phases
(reducing the multi-file cost of adding a content type/builder to the 5e app).

## ⚓ Re-anchor — what this branch is *founded on* (don't lose the plot)
**Founding purpose = content extensibility, for TWO equal reasons: stability AND flexibility.**
The insight: they're the **same abstraction**. Today every cross-type link is bespoke
positional wiring (boons→warlock by arg; custom-race menu a hardcoded vector) — that bespoke-ness
*is* the ~8-file cost and the fragility. An open **pool + grant** layer collapses N×M bespoke
wirings to N+M declarations down one tested path: stability win = flexibility win. The
engine *already* supports filter/gate/prereq (`selection-cfg`/`prereq-fn`/`option-prereq`/
`ability-increase-selection-2`); the gap is the **authoring** layer (content can't *declare*
open cross-silo grants). Readability stays a *constraint*: two words (pool/grant), built from
existing thick parts, no cryptic DSL. What stands: `content_types` registry (data + audit
test), the Phase-4b subs loop, `register-homebrew-content!` (wiring sub-layer) + boon.
The `by-parent`/`plugin-options`/`option_catalog` wrappers were reverted (`9777ce88`).

**Current state / next core step:** ✅ `register-homebrew-content!` built; boon swapped
(`3980ea1b`). **Next core step:** prove the **pool + grant** spine on one slice end-to-end
(direction doc v2 §"The spine" + PINS — incl. the variant `resolved-content` forward-compat
seam). The test-suite triage (rotted cljs suite, dead `character_test.cljc`, import fixes) was
a tangent that produced the **headless cljs harness** — our gate for cljs work. Don't let the
tangent become the branch.

Verification discipline lessons from this session: `docs/kb/verification-discipline.md`.

## Roadmap / TODO (live checklist — updated as work proceeds)

> ⚠️ **The phase numbering below is from the OLD (superseded) plan** and refers to code that
> no longer exists (`option_catalog`, `by-parent`, `plugin-options` — all reverted in
> `9777ce88`). Read it as *history of what was tried*, not the live plan. The live plan is the
> re-centered **pool + grant** spine in `content-extensibility-direction.md` (v2) and Part 4
> of the decisions doc. The `[x]` items below (name-keyword-fix merge, harness, golden/fixture
> tests, the two ✅ steps) still stand; the `[ ]`/`[~]` catalog phases are reframed by v2.

Each step is small, behavior-preserving, and must leave the gate green
(`lein test` + `lein lint`) before commit. Code lands on this branch.

- [x] **Merged `feature/name-keyword-fix`** (commit `ec26955`) — catalog work now sits on
      the stable-key fix. Clean auto-merge; gate green (220/1092/0, lint 0). **Live/E2E
      verification still needed** (JVM gate doesn't run cljs subs or the app):
      see `docs/kb/content-extensibility-e2e.md`.
- [x] **Live E2E verification (PR #28): all PASS / covered, no regressions.** A
      full-environment run (figwheel + browser + Datomic) confirmed the catalog seams
      end-to-end: homebrew subrace under built-in Elf, subclass under built-in Sorcerer,
      boon + invocation in the Warlock builder, byte-identical character round-trip. Item 1
      "failures" are pre-existing on `develop`; this branch's 18 added tests all pass.
      Items 7/10 accepted as covered by `content-reconciliation-test/*` + the round-trip
      golden. Fixture for the gaps: `test/extensibility-fixtures.orcbrew` (commit `f977ba9`).
      **Merge is sequencing-blocked on #27** (name-keyword-fix) landing on `develop` first.

- [x] **Setup** — toolchain (lein + deps), baseline gate green.
- [x] **Phase 0 — safety net.** `extensibility_golden_test.cljc` locks compat invariants
      (name-to-kw key derivation; saved-character round-trip). Pure JVM. (212→ tests green.)
- [x] **Phase 1 — generic injector.** New leaf ns `option_catalog.cljc` (`by-parent`),
      unit-tested = `group-by`; subraces re-pointed. (213 tests, lint 0 errors.)
- [x] **Phase 2 — subclasses** re-pointed to `by-parent`. (lint 0 errors.)
- [~] **Phase 3 — boons + invocations onto a catalog read.**
  - [x] 3b. Added `plugin-options` to `option_catalog.cljc` (catalog read primitive),
        JVM-unit-tested identical to the legacy `(mapcat (comp vals key) plugins)`;
        `::classes5e/plugin-boons` and `::classes5e/plugin-invocations` routed through
        it. Behavior-preserving; no keys/signatures changed. (214 tests, lint 0 errors.)
  - [x] 3a. (guard) `extensibility_golden_test.cljc` now builds boon/invocation options
        via `pact-boon-options`/`eldritch-invocation-options` (real spell data) and locks
        the built-in + homebrew option keys and the `:pact-boon`/`:eldritch-invocations`
        selection keys. (217 tests green.)
  - [ ] 3c. (RISKY — deferred) Stop threading `boons`/`invocations` as positional args:
        inject them as a post-step (like subraces→races) or via an ambient ctx map.
        Keys MUST stay identical; 3a guards it. Approach carefully; cljs assembly is
        lint+review-only here.
- [ ] **Phase 4 — Layer 1 registration/indexing registry (the "8 files → 1 descriptor"
      win). Existing types only; one subsystem per commit.**
  - [x] 4a. Created leaf `content_types.cljc` registry (13 plugin-based types;
        magic-item + combat excluded as non-plugin). `content_types_test.cljc` audits it:
        every `:spec` resolves via `spec/get-spec`, every `:plugin-key` satisfies the
        orcbrew `::e5/content-keyword` contract, identity fields unique. (220 tests green.)
        Built from an agent inventory; the get-spec/contract checks auto-verified it.
  - [x] 4b. subs: the 13 `::<type>/builder-item` passthrough subs are now generated by a
        loop over the registry (`spell_subs.cljs`). JVM guard `content_types_test/
        builder-items-match-the-subs` locks the set against drift. Provably the same 13
        keys; lint clean, 224 tests green. cljs behavior (builder forms load) → e2e.
  - [ ] 4c. db: build `default-value` slots + `reg-local-store-cofx` from the registry.
  - [ ] 4c. db: build `default-value` slots + `reg-local-store-cofx` from the registry.
  - [ ] 4d. events: generate `set-`/`reset-` + `reg-*-homebrew` calls from the registry.
  - [ ] 4e. routes: derive bidi tree + route sets + `routes.clj` allowlist (keep the
        `(def …-route :kw)` lines).
  - [ ] 4f. core: build the `pages` map from the registry.
  - [ ] Gate each: app boots, NO route/event/sub/localStorage key renamed, golden green.
- [ ] **Phase 5 — prove it with a new builder.**
  - [ ] 5a. Fighting-style builder (easier): `fighting-style-options` → catalog;
        `fighting-style-selection` → grant-with-filter; descriptor + spec + form.
  - [ ] 5b. Lineage/ancestry builder (harder): convert `dragonborn-option-cfg` def→fn,
        catalog, plus breath-weapon/resistance modifiers (real domain work).
  - [ ] Gate: golden green (existing unaffected) + a test that an imported homebrew
        fighting style / lineage appears under its parent.

Honesty note: the JVM gate does not run the `.cljs` subscription code. For cljs-only
edits I rely on `lein lint` + the `.cljc` unit tests + manual review; the risky logic is
kept in `.cljc` (`option_catalog`, option fns) precisely so it IS JVM-tested.

Note: code is landing on this branch (the only authorized push target). Docs are meant
to split-commit to `agents/develop`; production code would normally go on a code branch
off `develop` — confirm the target before merging.

## Deferred follow-ups — HIGHLIGHT AT BRANCH CLOSE

These are intentionally **not** done on this branch and **must be surfaced when this
branch is finalized / PR'd** (don't let them vanish into the diff):

1. **Character-validation contract** (own branch). The computed character is the one
   user-facing representation with no validation; the *intent* and a falsifiable charter
   are preserved in `docs/kb/character-validation.md`. Implement on its own branch.
2. **Get the ClojureScript tests into CI** (own branch). CI runs only the JVM gate, so the
   cljs suite is unrun and has rotted (`docs/kb/test-suite-state.md`). This is the root
   fix; it also lets future cljs changes be gated instead of hand-verified. Pairs with #1.

When putting a bow on this branch, repeat these two items in the PR description / handoff.

## Workflow
This branch is based on the leaner fork line, not `agents/develop`, so file
references in the docs use the monolithic `views.cljs`/`events.cljs` layout. The docs
flag this. Intent is to **split-commit these docs onto `agents/develop`** later.

When split-committing to `agents/develop`, also add index rows for the two new docs
to `docs/kb/README.md` there (not done here — this branch's index differs from
`agents/develop`'s, so editing it here wouldn't carry over cleanly).

## Handoff Notes
- **Coordinate with `feature/name-keyword-fix`** (forks from the same base `d42e05d`).
  It establishes: identity keys derive from stable ids (`:class-key`, stored `:key`),
  NOT display names; `option-cfg` has a `::plugin-source` slot; a reconciler heals
  orphaned keys. Both branches touch classes.cljc / options.cljc / spell_subs.cljs /
  events.cljs / template.cljc — expect overlap and align on its stable-key approach.
- **Two standing rules for the catalog/grant phases (3c+):** (1) pass each item's stored
  `:key` to `option-cfg` — never re-derive identity from a display `:name`; (2) catalogs
  are layered, memoized `reg-sub`s referenced by grants — never recomputed in hot subs.
  Guard both with comments. (Decisions D10/D11; details in the design + compatibility docs.)
- **Working agreements (apply to all work here):**
  - *Tests must be falsifiable.* Every test must go red if the production code it covers
    breaks. No theater (a test that only asserts `(spec/valid? my-spec my-input)` tests the
    spec against examples, not the system). Gut check: "if I break the code, does this fail?"
  - *Fix bugs on sight.* Don't leave a bug lying around once found — fix it in-flight,
    UNLESS it's deep enough to warrant its own branch (then file it and scope it).
- **Test-suite debt found this session (`docs/kb/test-suite-state.md`):** CI runs only the
  JVM gate (`lein lint`/`lein test`); the cljs suite is never run and has rotted. A
  **headless cljs harness now exists in this container** (compile `fig:test` → serve
  `target/test/` → drive Chromium via Playwright → capture the clean reporter), so cljs is
  verifiable here. **`save-character` null crash: FIXED + verified** (errors 3→2).
- **Import-validation triage — TRIAGED + FIXED (`86eb5cc4`), harness-verified:**
  - `apply-key-renames` test → was STALE (`:old-key`/`:new-key`); **fixed test** to `:from`/`:to`.
  - `normalize-text café→cafe` → was STALE/WRONG (accents are preserved + flagged);
    **fixed test** to expect `"Café"`.
  - `count-non-ascii` → REAL cljs bug (`(int %)`=0 in cljs); **fixed code** → `(.charCodeAt % 0)`.
  - `dedup-options-in-import` → REAL bug (mechanism pinned): `dedup-options-in-item` only
    handled `:selections`-nested options, not a homebrew `:orcpub.dnd.e5/selections` item's
    own top-level `:options`; **fixed code** (additive). Full-pipeline dedup now works.
  - Verified: headless cljs run 133 tests / **1 failure / 0 errors** — only the unrelated
    `user-stale-user` (subs auth guard) remains. lint 0; JVM 224/1107/0.
  - **Still open (not import, out of this list's scope):** `user-stale-user` subs auth-guard
    test (1 failure) + the dead `character_test.cljc` (2 errors, retire per the charter).
- The KB requires verified-only content. The cross-link map is verified from code; the
  proposed design is clearly labeled as a proposal. Preserve that boundary.
- The design directly answers a cluster of open issues (#58, #57/#209, #172/#170,
  #210/#107, #280, #173, #128) listed in `docs/issues/homebrew-builders.md` on
  `agents/develop`.
- Conversation context that produced these docs is not preserved elsewhere; the two
  KB docs are the durable record.

## Related Docs
- `.claude/summaries/2026-06-13-content-extensibility.md` — session summary / handoff
- `docs/kb/content-extensibility.md`, `docs/kb/content-extensibility-decisions.md`,
  `docs/kb/content-extensibility-compatibility.md`, `docs/kb/content-extensibility-plan.md`,
  `docs/kb/content-extensibility-e2e.md` (live verification checklist for a VS Code agent)
- `docs/kb/test-suite-state.md` — verified state of the test suites, the pre-existing cljs
  failures (classified), the `::character`/built-character spec findings, open decisions
- `docs/kb/verification-discipline.md` — lessons on assumptions & thoroughness (verify
  against callers/intent/runtime before asserting; "red test = disagreement, not bug")
- `docs/kb/character-validation.md` — preserves the *intent* of validating a character
  (Larry's 2016 test) + the modern, falsifiable replacement charter (own-branch). Capture
  this before retiring the broken `character_test.cljc`.
- `docs/kb/built-character-representation.md` — **load-bearing gotcha:** the built/computed
  character is a map of deferred `:entity-fn?` values (read via `entity-val`), NOT a flat
  map; don't `spec/keys` it. Anchored in code on `entity-val`/`build`/`built-character`.
- Cross-references: `docs/kb/spa-routing-architecture.md`,
  `entity-options-architecture.md`, `srd-vs-plugin-content.md`,
  `views-builders-split.md`, `docs/issues/homebrew-builders.md` (all on `agents/develop`)
