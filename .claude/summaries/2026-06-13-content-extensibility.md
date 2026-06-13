# Session Summary: Content Extensibility Analysis

**Date**: 2026-06-13
**Branch**: claude/zen-wright-04xhdz (docs prepared for split-commit to `agents/develop`)
**Status**: Design + docs complete. No production code changed. Awaiting go-ahead on a spike.

## The question

Why does adding a minor content type / builder to the 5e app touch ~8 files
(`routes.clj`, `route_map.cljc`, `db.cljs`, `events.cljs`, `spell_subs.cljs`,
`views.cljs`, `core.cljs`, plus a spec home)? Is there a less error-prone way that
keeps the standardization the codebase has?

## What we concluded

The cost is two separate problems, not one:

1. **Registration** — scattered, parallel boilerplate keyed by the same entity
   (route, db default, localStorage, events, subs, page-map). Mechanical.
2. **Injection** — wiring new options into a *parent* entity (e.g. boons into the
   warlock) via positional function arguments. This is the fragile, bug-prone half.

Proposed direction — two composable layers:

- **Layer 1: content-type registry.** One descriptor list feeding loops that call the
  *existing* `reg-*-homebrew` factories. Kills the registration boilerplate.
- **Layer 2: type-addressed catalogs + grants.** Generalize the subrace
  "bucket-by-key" pattern from `:race` to option *type*. Producers declare a type;
  consumers `grant-choice` from a catalog with an optional filter. Keep `mod5e/*` for
  fixed grants. Kills the positional injection and makes cross-aspect grants
  (feat→spell, background→feat, feat→boon) uniform — homebrew flows in for free.

## Why it matters

Verified from code: subraces and subclasses already use the clean bucket-by-key
pattern; boons and invocations use fragile positional threading; draconic ancestries
are a static list with no plugin path. The proposal is to make the rest work like
subraces already do. It also answers a cluster of open issues in
`docs/issues/homebrew-builders.md` (#58, #57/#209, #172/#170, #210/#107, #280, #173,
#128).

## Files created (this branch)

| File | Purpose |
|------|---------|
| `docs/kb/content-extensibility.md` | Problem, verified cross-link map, proposed two-layer direction |
| `docs/kb/content-extensibility-decisions.md` | Decision audit (how the thinking evolved) + crisp decisions D1–D8 |
| `BRANCH.md` | Branch purpose + handoff + split-commit notes |
| `.claude/summaries/2026-06-13-content-extensibility.md` | This summary |

## How to resume

1. Read `docs/kb/content-extensibility.md` (design) and `-decisions.md` (the why).
2. First concrete step: a **behavior-preserving spike** — add a generic catalog
   injector and migrate **subraces** onto it (they already work this way), review the
   diff, then migrate boons/invocations, then add lineages as new capability.
3. When split-committing these docs to `agents/develop`, add index rows for the two
   KB docs to `docs/kb/README.md` there (this branch's index differs).

## Caveats for the next agent

- KB rule is verified-only content. The cross-link map is verified from code; the
  design is labeled a proposal. Keep that boundary.
- File:line references were read on the monolithic frontend layout of this branch; on
  `agents/develop` views are split (`views-builders-split.md`), so resolve view
  references by symbol, not line.
