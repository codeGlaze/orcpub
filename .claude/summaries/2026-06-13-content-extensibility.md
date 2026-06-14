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
| `docs/kb/content-extensibility-compatibility.md` | Backward-compat audit: persisted formats, invariants, proposal assessment |
| `docs/kb/content-extensibility-plan.md` | Phased implementation playbook for low-context agents (gates, stop conditions) |
| `BRANCH.md` | Branch purpose + handoff + split-commit notes |
| `.claude/summaries/2026-06-13-content-extensibility.md` | This summary |

## How to resume

1. Read `docs/kb/content-extensibility.md` (design) and `-decisions.md` (the why).
2. To implement, follow `content-extensibility-plan.md` literally: Phase 0 builds a
   golden test, then Phase 1 migrates **subraces** onto a generic injector
   (behavior-preserving), then subclasses, boons/invocations, the registry, and finally
   lineages. Each phase is gated and has stop conditions.
3. When split-committing these docs to `agents/develop`, add index rows for the two
   KB docs to `docs/kb/README.md` there (this branch's index differs).

## Caveats for the next agent

- KB rule is verified-only content. The cross-link map is verified from code; the
  design is labeled a proposal. Keep that boundary.
- File:line references were read on the monolithic frontend layout of this branch; on
  `agents/develop` views are split (`views-builders-split.md`), so resolve view
  references by symbol, not line.
- Backward compatibility is a hard constraint, audited in
  `content-extensibility-compatibility.md`. The target is zero-migration: derive
  catalogs over the existing plugin storage and preserve selection/option keys, then
  prove it with an orcbrew + saved-character fixture before/after each migration.
- **Coordinate with `feature/name-keyword-fix`** (same base `d42e05d`): identity keys
  derive from stable ids, not display names; `option-cfg` has a `::plugin-source` slot;
  a reconciler heals orphaned keys. Two standing rules for the catalog/grant phases
  (decisions D10/D11): pass each item's stored `:key` to `option-cfg` (never re-derive
  from a display `:name`), and make catalogs layered/memoized `reg-sub`s referenced by
  grants (never recompute a catalog in a hot sub). Guard both with comments.

## Implementation progress (code, this branch)
Phases 0, 1, 2 (subraces/subclasses via `option_catalog/by-parent`), 3a (key-lock
guard), 3b (`option_catalog/plugin-options`; boons/invocations), and 4a (the
`content_types.cljc` registry + audit test) are committed and gated green
(223 tests / 1106 assertions with the e2e fixture). Merged `feature/name-keyword-fix`
(`ec26955`); live-verified via PR #28 (all items PASS/covered, no regressions).
Remaining: 3c (positional-threading removal — risky, deferred), 4b–4f (wire the registry
into subs/db/events/routes/core — cljs, lint+review only here), Phase 5 (new builders).
See BRANCH.md for the live checklist.

## Test-suite debt found this session (separate from the extensibility work)
See `docs/kb/test-suite-state.md` (verified). Headline: **CI runs only the JVM gate
(`lein lint`/`lein test`); the cljs suite is never run and has rotted** — 10 failures /
3 errors pre-existing on `develop`, all real or removed-subject tests (not theater).
Notable: `character_test.cljc` references the `::character` spec Larry removed in the 2016
entity refactor (and duplicates the `.clj` test's namespace); the computed/built character
has no validation spec (the `save-character` null crash is a symptom). Working agreements
adopted: tests must be falsifiable (no theater); fix bugs on sight unless deep enough for
their own branch. Fixed the `save-character` null crash on sight (`42ceaaa8`).

**Load-bearing gotcha captured** (`docs/kb/built-character-representation.md`, anchored in
code): the built/computed character is a map whose derived values are deferred `:entity-fn?`
functions read via `entity-val` — NOT a flat map; don't `spec/keys` it. This is why the
computed character has no spec and why Larry's flat `::character` died.

**Deferred follow-ups — HIGHLIGHT AT BRANCH CLOSE** (also in BRANCH.md): (1) the
character-validation contract (own branch; charter in `character-validation.md`), and
(2) getting the cljs tests into CI (own branch; the cljs suite is unrun/rotted). Surface
both in the final PR/handoff so they aren't lost.

## Verification discipline + re-anchor (late session)
Several confident claims this session were wrong until verified (spec history, sub-vs-spec, which import tests failed). Lessons captured in `docs/kb/verification-discipline.md`: verify against real callers/intent/runtime before asserting; a red test means test+code DISAGREE, not that code is broken.

RE-ANCHOR: the branch's founding purpose is **content extensibility** (Phases 0–4b done; next core step = 4c, gated by the new headless cljs harness). The test-suite / import-validation triage is a semi-related tangent that produced the harness — which enables safely finishing 4c–4f. Don't let the tangent become the branch.
