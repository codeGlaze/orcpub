# Branch Context: claude/zen-wright-04xhdz

## Purpose
Capture the content-extensibility analysis and plan, and implement it in gated phases
(reducing the multi-file cost of adding a content type/builder to the 5e app).

## Current State
Docs (structured for `agents/develop`): `content-extensibility.md` (design + cross-link
map), `-decisions.md` (audit + D1–D9), `-compatibility.md` (backward-compat audit),
`-plan.md` (phased implementation playbook).

Implementation progress (against `-plan.md`):
- **Phase 0 (safety net): DONE.** `test/cljc/orcpub/dnd/e5/extensibility_golden_test.cljc`
  locks the compat invariants (name-to-kw key derivation; saved-character round-trip
  idempotence + key preservation). Pure JVM `.cljc`, runs under `lein test`. Full suite
  green: 212 tests / 979 assertions / 0 failures.
- **Phase 1 (generic injector, subraces): DONE.** New leaf ns
  `src/cljc/orcpub/dnd/e5/option_catalog.cljc` (`by-parent`), unit-tested identical to
  `group-by`; `::races5e/plugin-subraces-map` re-pointed to it. Gate green: 213 tests,
  lint 0 errors.
- Next: **Phase 2** — re-point `::classes5e/plugin-subclasses-map` (`group-by :class`)
  to `catalog/by-parent` (same mechanism).

Note: code is currently landing on this branch (the only authorized push target). The
docs were written to split-commit to `agents/develop`; production code should land on a
code branch off the code line (`develop`) — confirm the target before merging.

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
