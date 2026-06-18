# OrcPub Agent Knowledge Base

Verified, research-backed findings from in-depth investigations. Each document is sourced from
direct inspection of code, logs, or authoritative references. Speculation is marked
**⚠️ UNVALIDATED SPECULATION** and must not be treated as fact without further verification.

## Index

| Document | Topic | Source quality |
|----------|-------|---------------|
| [roadmap.md](roadmap.md) | **START HERE for the content-extensibility/mechanization initiative** — the dependency-ordered layers (foundation net, cross-silo grants, mechanism layers, class-feature registry, AC, generated UI) + the recommended critical path. | Index/plan — links the verified docs |
| [datomic-crash-analysis.md](datomic-crash-analysis.md) | Datomic transactor crashes — root cause, frequency, fix options | High — direct log analysis from `logs/datomic.{1,2,3}.log` |
| [decision-vocabulary.md](decision-vocabulary.md) | **Map of the homebrew wiring**: which decision keys each silo's builder emits and which assembly fn compiles them (feat/race/subrace/class/subclass/background). ⚠️ Maps *where data plugs in*, NOT observed runtime behavior or true limits — except the subclass-spellcasting gate, which is confirmed by code **and** a real `.orcbrew`. | Medium — call-graph + symbols verified; behavior/limits mostly NOT exercised |
| [homebrew-content-merge.md](homebrew-content-merge.md) | The `feat-options` trap: why "X isn't homebrew-extensible" conclusions are usually wrong (merge happens at the concat point, not the static `*-options` def) | High — direct code inspection |
| [content-extensibility-framework.md](content-extensibility-framework.md) | Canonical reference for the registry-driven content system being built (one entry vs ~9-file edits) | High — code + design |
| [runtime-toggles-and-conditional-modifiers.md](runtime-toggles-and-conditional-modifiers.md) | How a player toggle (equipped armor / magic items) changes computed sheet stats — the `equipped?`-flag + deferred-modifier mechanism. Feasibility basis for "while active" (rage-style) conditional features. | High — code read, file:line cited |
| [armor-class-computation.md](armor-class-computation.md) | How AC is computed (the layered max-of-alternatives + sum-of-bonuses model in `template_base.cljc`), the channels a feature plugs into, and the friction for custom Natural AC / Unarmored Defense. | High — code read, file:line cited; design section flagged |
| [spell-granting-across-silos.md](spell-granting-across-silos.md) | Why feats/races/classes grant spells differently when they all bottom out at the same primitives (`spells-known`, `spell-selection`). The per-silo wrappers, the gaps (feat = no fixed spell; race = no choice), and the route-one-key-to-the-primitive fix vs the per-silo-wrapper bloat trap. | High — chain traced up+down, file:line cited; built-character paths flagged NOT-TESTED |
| [spell-slot-progression.md](spell-slot-progression.md) | How spell *slots* (not known spells) are computed: the `:level-factor` integer that's overloaded to drive the slot table **and** the multiclass contribution **and** the prepared count (why Artificer can't be expressed), warlock pact magic vs a normal caster when multiclassing, and the agreed design — a bucket of named/explicit slot tables (authored as an absolute grid) + a separately-declared multiclass rule. | High — slot chain traced, file:line cited; design section flagged DESIGN |
| [declarative-grant-vocabulary.md](declarative-grant-vocabulary.md) | DESIGN: a builder-UI vocabulary (`<grant spell>` creator-fixed / `<select spell>` user-choice + filters; `<grant X when [conditions]>`) compiling to the existing primitives. Works for flat spell patterns; Magic-Initiate-style is a dependent two-level choice (special case). Unifies the two fixed-spell data shapes. Scope flags (spellcasting progression, qualifiers) OPEN. | Mixed — verified findings + flagged DESIGN/OPEN |
| [class-features-and-mechanization.md](class-features-and-mechanization.md) | How class features are structured (inline, no key, class-coupled; partial shared-helper extraction), the verified rolling layer (`orcpub.dice` + roll-buttons + bonus-fn attachment) and the mechanization ceiling (rolling in, combat-state/turn resolution out), and the design direction: one keyed/filterable feature registry, pools as filtered views. Includes the `compile-feature` proof (data spec → real fighter/rogue output, with overridable fields). | Mixed — VERIFIED/USER-REPORTED/SPECULATION flagged inline |
| [class-feature-catalogue.md](class-feature-catalogue.md) | Per-class inventory of all 12 base classes (roadmap C1): distinct auto-features, sizing (~3–6 each; monk/paladin outliers, sorcerer/wizard lean), and the cross-cutting odd cases the registry must handle — multi-source use-counts, class-wide resource pools (ki/sorcery/Lay-on-Hands), build-context summary interpolation, multi-part features, attribute interdependence. | High — all 12 class option fns read, file:line cited |

> Design, handoff, and decision records for the Content Extensibility initiative
> (the forward-looking, non-verified half) live in [`docs/extensibility/`](../extensibility/README.md).

## Contribution rules

- Only add findings you can cite directly (log lines, code lines, benchmark results, official docs).
- If you are reasoning from circumstantial evidence, mark the entire paragraph with **⚠️ UNVALIDATED SPECULATION — [brief rationale]**.
- Include the date the analysis was done and the artifact(s) it was based on.
- Do not remove speculation flags — if something is later verified, replace the flag with a **✅ VERIFIED — [how]** marker and update the text.
