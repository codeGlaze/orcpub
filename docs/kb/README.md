# OrcPub Agent Knowledge Base

Verified, research-backed findings from in-depth investigations. Each document is sourced from
direct inspection of code, logs, or authoritative references. Speculation is marked
**⚠️ UNVALIDATED SPECULATION** and must not be treated as fact without further verification.

## Index

> **START HERE:** [roadmap.md](roadmap.md) — the single reconciled branch plan (status ledger, doc map,
> critical path). Branch history/handoff lives in `BRANCH.md` (repo root).

### Plan & status
| Document | Topic |
|----------|-------|
| [roadmap.md](roadmap.md) | The single branch plan: both phases (content/pool+grant, largely built; mechanization/class-feature/spell-slot expansion), a BUILT/DECIDED/OPEN ledger anchored to commits, flagged conflicts, the full doc map, and the critical path. |

### Content-extensibility track (the pool/grant initiative)
| Document | Topic | Source quality |
|----------|-------|---------------|
| [content-extensibility-direction.md](content-extensibility-direction.md) | **Canonical detail + direction for the content track** (v2 spine): the pool+grant model, the one principle (an abstraction must be thicker than what it hides), the variant forward-compat seam, and the next levers/pins. | Mixed — verified + DESIGN flagged |
| [content-extensibility-decisions.md](content-extensibility-decisions.md) | The numbered decision log (**D1–D31**) — how each decision was reached, incl. the prototype-then-converge governance (D23), the grant conflict (D29/D30), and the vocabulary/AC duplication findings (D31). | Decision record |
| [content-extensibility-framework.md](content-extensibility-framework.md) | How-to reference for the registry-driven content system (mental model + schema + add-a-type + invariants). | High — code + design |
| [content-extensibility-compatibility.md](content-extensibility-compatibility.md) | Inventory of the persisted data formats (saved characters, `.orcbrew`) the refactor must not break — the backward-compat invariants. | High — format inventory |
| [content-extensibility-e2e.md](content-extensibility-e2e.md) | Live end-to-end verification checklist (what the JVM gate can't cover; for a browser/figwheel run). | Checklist |
| [registry-before-after.md](registry-before-after.md) | Representative before/after of adding a content type (Pact Boon) — scattered wiring vs the registry-driven path. | High — code |
| [content-extensibility.md](content-extensibility.md) · [content-extensibility-plan.md](content-extensibility-plan.md) | ⚠️ **HISTORY (superseded)** by the direction doc — read as "what was tried," not the live plan. | Historical |

### Verified topic / reference
| Document | Topic | Source quality |
|----------|-------|---------------|
| [decision-vocabulary.md](decision-vocabulary.md) | **Map of the homebrew wiring**: which decision keys each silo emits and which assembly fn compiles them. Includes the verified A/B grant-vocabulary comparison (shared primitive, B is level-gated, cljc/cljs layer split). | Medium-High — call-graph verified; key claims now test-backed |
| [homebrew-content-merge.md](homebrew-content-merge.md) | The `feat-options` trap: why "X isn't homebrew-extensible" conclusions are usually wrong (merge happens at the concat point, not the static `*-options` def). | High — code |
| [key-collision-behavior.md](key-collision-behavior.md) | What happens when content keys collide, **per layer**: classes/races/spells → homebrew OVERRIDES built-in (predictable, plugin-wins); subraces/pools/lists → coexist; import → conflict modal. The "duplicate keys" map. | High — traced + test-backed (`key_collision_test`) |
| [spell-granting-across-silos.md](spell-granting-across-silos.md) | Why feats/races/classes grant spells differently when they bottom out at the same primitives (`spells-known`, `spell-selection`); the per-silo wrappers, the gaps, the route-one-key fix. | High — chain traced; some paths flagged NOT-TESTED |
| [spell-slot-progression.md](spell-slot-progression.md) | How spell *slots* are computed: the overloaded `:level-factor` (table + multiclass + prepared count; why Artificer can't be expressed), warlock pact vs normal multiclassing, and the bucket-of-tables design. Test-backed (`spell_slot_characterization_test`). | High — traced + tested |
| [declarative-grant-vocabulary.md](declarative-grant-vocabulary.md) | DESIGN: a builder-UI vocabulary (`<grant spell>` / `<select spell>` + filters) compiling to existing primitives; the Magic-Initiate special case; open scope flags. | Mixed — verified + DESIGN |
| [class-features-and-mechanization.md](class-features-and-mechanization.md) | How class features are structured (inline, class-coupled, captured code), the rolling layer + mechanization ceiling, the registry/`compile-feature` direction (data spec → real fighter/rogue output, overridable fields). | Mixed — VERIFIED/USER-REPORTED/SPECULATION flagged |
| [class-feature-catalogue.md](class-feature-catalogue.md) | Per-class inventory of all 12 base classes (C1): distinct auto-features, sizing, and the odd cases the registry must handle (multi-source counts, resource pools, build-context interpolation, multi-part features, attribute interdependence). | High — all 12 read |
| [building-a-class-from-builders.md](building-a-class-from-builders.md) | What a homebrew class can be assembled from today: the `homebrew-class` spec, what `subclass-option`/`spellcasting-template` accept, the invocation/boon pool pattern, the `ua_artificer` witness, and the real gaps. | High — code |
| [armor-class-computation.md](armor-class-computation.md) | How AC is computed (max-of-alternatives + sum-of-bonuses), the channels, custom-AC friction. Test-backed (`ac_characterization_test`: armored dex-cap, unarmored tie-break, natural-AC duplication; the `:max-dex-mod`-ignored + cljs-nil-add findings). | High — traced + tested |
| [runtime-toggles-and-conditional-modifiers.md](runtime-toggles-and-conditional-modifiers.md) | How a player toggle (equipped armor/magic items) changes computed sheet stats — the `equipped?`-flag + deferred-modifier mechanism; basis for "while active" features. | High — code |
| [built-character-representation.md](built-character-representation.md) | **Load-bearing gotcha:** the built/computed character is a map of deferred `:entity-fn?` values (read via `entity-val`), NOT a flat map — don't `spec/keys` it. | High — code |

### Process & infrastructure
| Document | Topic |
|----------|-------|
| [verification-discipline.md](verification-discipline.md) | Lessons on assumptions/thoroughness + the **standing rule**: don't call it verified without walking it up and down and backing it with a falsifiable test (or the full chain); and how a characterization test doubles as the old-vs-new comparison instrument. |
| [cljs-headless-harness.md](cljs-headless-harness.md) | How to run the cljs test suite headless in a container (compile `fig:test` → serve `target/test` → Playwright Chromium) — the gate for cljs-only code; plus the full-app click-through E2E (`test/e2e/`). |
| [dropdown-value-coercion.md](dropdown-value-coercion.md) | The `<select>`-always-yields-a-string footgun — bit this branch twice (breath weapon, then floating ASI) because the first fix lived only in a code comment — and the `:typed?` template that round-trips the value's type. Decision **D32**. | High — git-verified provenance + E2E-backed |
| [ability-increase-spreads.md](ability-increase-spreads.md) | `:ability-increases` as terse `[amount pool]` pairs — fixed/floating/Tasha's "+2/+1"/arbitrary custom spreads, the "different abilities" rule, the opt-in `:save` rider, the standalone `:save-proficiencies` tool, multi-silo containment, and the feat dual-format reader. Compile + assign-from-bag widget + authoring. Decisions **D33** (terse export data). | High — JVM + cljs + E2E-backed |
| [backfill-ledger.md](backfill-ledger.md) | Living list for converging bespoke paths onto the pool/grant standard (D29) + deprecating code (D34): migration recipe, the ledger table, and the watch-list. | Process doc |
| [test-suite-state.md](test-suite-state.md) | Verified record of what the test suites run and gate, the pre-existing cljs failures (classified), and open decisions. |
| [character-validation.md](character-validation.md) | Preserves the intent of *validating a character* + a falsifiable replacement charter (own-branch). |
| [datomic-crash-analysis.md](datomic-crash-analysis.md) | Datomic transactor crashes — root cause, frequency, fix options (from direct log analysis). |

## Contribution rules

- Only add findings you can cite directly (log lines, code lines, benchmark results, official docs).
- If you are reasoning from circumstantial evidence, mark the entire paragraph with **⚠️ UNVALIDATED SPECULATION — [brief rationale]**.
- Include the date the analysis was done and the artifact(s) it was based on.
- Do not remove speculation flags — if something is later verified, replace the flag with a **✅ VERIFIED — [how]** marker and update the text.
