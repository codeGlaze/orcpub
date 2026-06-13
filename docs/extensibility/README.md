# Content Extensibility Initiative

This folder collects the analysis, decisions, and forward plan for reducing the
multi-file effort required to add a new piece of content (a builder, a homebrew
option type, a cross-aspect grant) to the OrcPub 5e app — without sacrificing
the standardization the codebase has already earned.

It exists so that the reasoning behind this initiative survives context loss: if
a session crashes, a summary over-trims, or a different agent/developer picks it
up cold, everything needed to continue is here rather than in chat history.

## Start here

| Document | Purpose |
|----------|---------|
| [HANDOFF.md](HANDOFF.md) | What we discussed, where we landed, why, and the current plan + next step. Read this first. |
| [TARGET_ARCHITECTURE.md](TARGET_ARCHITECTURE.md) | The proposed design: two layers (registration registry + type-addressed option catalogs/grants), with pseudocode. |
| [Cross-link map](../kb/content-extensibility-cross-links.md) | The *current* cross-links between content aspects, mapped into the proposed catalog/grant shape. Citation-backed; lives in the agent KB (`docs/kb/`). |
| [DECISIONS.md](DECISIONS.md) | ADR-style log of the decisions made and why, including the ideas we rejected. |

## One-paragraph summary

Adding a content type today touches ~8 files (e.g. the Pact Boon builder, commit
`6029fd0`, touched 10). That cost is really **two** separate problems:
(1) **registration** boilerplate scattered across route/db/events/subs/view files,
and (2) **injection** — wiring a new option set into a parent entity (e.g. boons
into the warlock) via fragile positional arguments. We plan to attack them with
two composable layers: a **data-driven content-type registry** (kills problem 1
by reusing the existing `reg-save-homebrew` / `reg-new-homebrew` / `reg-edit-homebrew`
factories) and **type-addressed option catalogs + grants** (kills problem 2 by
generalizing the one extension point already done right — subraces). No code has
been written yet; this is a design phase.

## Status

**Design only. No production code changed.** The recommended first concrete step
is a behavior-preserving spike that migrates *subraces* onto the generic catalog
injector to prove it reads cleanly. See [HANDOFF.md](HANDOFF.md#next-step).

## Suggested future documents

These were identified as worth adding as the initiative progresses (tracked here
so the suggestion isn't lost):

- **`GLOSSARY.md`** — pin down overloaded terms: "option", "selection", "modifier",
  "plugin", "option-pack", "builder-item", "catalog", "grant", "slot", and the two
  distinct `key` concepts (data `:key` vs `::entity/key`) already flagged in
  `docs/README.md`.
- **`MIGRATION_PLAN.md`** — once the spike validates the approach, a step-by-step,
  per-phase migration checklist (which extension point moves when, and the
  behavior-preserving verification for each).
- **`EXTENSION_POINTS_INVENTORY.md`** — a living catalog of every parent→child
  injection site in the app (this doc set seeds it via CROSS_LINK_MAP.md, but a
  standalone inventory would be the source of truth for "what still needs migrating").
- **`SPEC_HOMES.md`** — a map of where each content type's `homebrew-*` spec and
  domain model lives (the dragonborn-lineage analysis showed some content has no
  natural home; this avoids re-deriving that each time).
