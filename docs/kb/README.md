# OrcPub Agent Knowledge Base

Verified, research-backed findings from in-depth investigations. Each document is sourced from
direct inspection of code, logs, or authoritative references. Speculation is marked
**⚠️ UNVALIDATED SPECULATION** and must not be treated as fact without further verification.

## Index

| Document | Topic | Source quality |
|----------|-------|---------------|
| [datomic-crash-analysis.md](datomic-crash-analysis.md) | Datomic transactor crashes — root cause, frequency, fix options | High — direct log analysis from `logs/datomic.{1,2,3}.log` |
| [decision-vocabulary.md](decision-vocabulary.md) | **Map of the homebrew wiring**: which decision keys each silo's builder emits and which assembly fn compiles them (feat/race/subrace/class/subclass/background). ⚠️ Maps *where data plugs in*, NOT observed runtime behavior or true limits — except the subclass-spellcasting gate, which is confirmed by code **and** a real `.orcbrew`. | Medium — call-graph + symbols verified; behavior/limits mostly NOT exercised |
| [homebrew-content-merge.md](homebrew-content-merge.md) | The `feat-options` trap: why "X isn't homebrew-extensible" conclusions are usually wrong (merge happens at the concat point, not the static `*-options` def) | High — direct code inspection |
| [content-extensibility-framework.md](content-extensibility-framework.md) | Canonical reference for the registry-driven content system being built (one entry vs ~9-file edits) | High — code + design |
| [runtime-toggles-and-conditional-modifiers.md](runtime-toggles-and-conditional-modifiers.md) | How a player toggle (equipped armor / magic items) changes computed sheet stats — the `equipped?`-flag + deferred-modifier mechanism. Feasibility basis for "while active" (rage-style) conditional features. | High — code read, file:line cited |

> Design, handoff, and decision records for the Content Extensibility initiative
> (the forward-looking, non-verified half) live in [`docs/extensibility/`](../extensibility/README.md).

## Contribution rules

- Only add findings you can cite directly (log lines, code lines, benchmark results, official docs).
- If you are reasoning from circumstantial evidence, mark the entire paragraph with **⚠️ UNVALIDATED SPECULATION — [brief rationale]**.
- Include the date the analysis was done and the artifact(s) it was based on.
- Do not remove speculation flags — if something is later verified, replace the flag with a **✅ VERIFIED — [how]** marker and update the text.
