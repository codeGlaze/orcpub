# Branch Context: claude/zen-wright-04xhdz

## Purpose
Capture the content-extensibility analysis and plan (reducing the multi-file cost of
adding a content type/builder to the 5e app). Docs only — no production code changed.

## Current State
Done: two KB docs written, structured for `agents/develop`:
- `docs/kb/content-extensibility.md` — the problem, verified cross-link map, and the
  proposed two-layer direction (registry + type-addressed catalogs/grants).
- `docs/kb/content-extensibility-decisions.md` — decisions and rejected options.

Not started: any code. The recommended first step is a behavior-preserving spike that
migrates subraces onto a generic catalog injector (see the docs).

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
  `docs/kb/content-extensibility-compatibility.md`
- Cross-references: `docs/kb/spa-routing-architecture.md`,
  `entity-options-architecture.md`, `srd-vs-plugin-content.md`,
  `views-builders-split.md`, `docs/issues/homebrew-builders.md` (all on `agents/develop`)
