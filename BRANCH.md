# Branch Context: claude/zen-wright-04xhdz

## Purpose
Capture the content-extensibility analysis and plan, and implement it in gated phases
(reducing the multi-file cost of adding a content type/builder to the 5e app).

## Roadmap / TODO (live checklist — updated as work proceeds)

Each step is small, behavior-preserving, and must leave the gate green
(`lein test` + `lein lint`) before commit. Code lands on this branch.

- [x] **Setup** — toolchain (lein + deps), baseline gate green.
- [x] **Phase 0 — safety net.** `extensibility_golden_test.cljc` locks compat invariants
      (name-to-kw key derivation; saved-character round-trip). Pure JVM. (212→ tests green.)
- [x] **Phase 1 — generic injector.** New leaf ns `option_catalog.cljc` (`by-parent`),
      unit-tested = `group-by`; subraces re-pointed. (213 tests, lint 0 errors.)
- [x] **Phase 2 — subclasses** re-pointed to `by-parent`. (lint 0 errors.)
- [ ] **Phase 3 — boons + invocations onto a catalog read (the risky one).**
  - [ ] 3a. Extend the golden test to lock the "Pact Boon" selection key + boon option
        keys via the `.cljc` fns (`pact-boon-options`, `warlock-option`). Additive.
  - [ ] 3b. Add `plugin-options` to `option_catalog.cljc` (extract all items of a
        content-key from the plugins map — the catalog read primitive).
  - [ ] 3c. Warlock pulls boons from the catalog instead of the positional arg; keys
        IDENTICAL. Then drop `boons` from `warlock-option` / `base-class-options` /
        `::classes5e/classes`. STOP if any golden key changes.
  - [ ] 3d. Repeat 3b–3c for invocations.
- [ ] **Phase 4 — Layer 1 registration/indexing registry (the "8 files → 1 descriptor"
      win). Existing types only; one subsystem per commit.**
  - [ ] 4a. Create leaf `content-types` registry ns describing existing types.
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
  `docs/kb/content-extensibility-compatibility.md`, `docs/kb/content-extensibility-plan.md`
- Cross-references: `docs/kb/spa-routing-architecture.md`,
  `entity-options-architecture.md`, `srd-vs-plugin-content.md`,
  `views-builders-split.md`, `docs/issues/homebrew-builders.md` (all on `agents/develop`)
