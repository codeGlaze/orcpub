# Branch Context: claude/zen-wright-04xhdz

## Purpose
Capture the content-extensibility analysis and plan, and implement it in gated phases
(reducing the multi-file cost of adding a content type/builder to the 5e app).

## Roadmap / TODO (live checklist — updated as work proceeds)

Each step is small, behavior-preserving, and must leave the gate green
(`lein test` + `lein lint`) before commit. Code lands on this branch.

- [x] **Merged `feature/name-keyword-fix`** (commit `ec26955`) — catalog work now sits on
      the stable-key fix. Clean auto-merge; gate green (220/1092/0, lint 0). **Live/E2E
      verification still needed** (JVM gate doesn't run cljs subs or the app):
      see `docs/kb/content-extensibility-e2e.md`.

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
  - [ ] 4b. subs: replace per-type `builder-item` passthrough subs with a loop.
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
- Cross-references: `docs/kb/spa-routing-architecture.md`,
  `entity-options-architecture.md`, `srd-vs-plugin-content.md`,
  `views-builders-split.md`, `docs/issues/homebrew-builders.md` (all on `agents/develop`)
