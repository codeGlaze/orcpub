# OrcPub Agent Knowledge Base

Verified, research-backed findings from in-depth investigations. Each document is sourced from
direct inspection of code, logs, or authoritative references. Speculation is marked
**⚠️ UNVALIDATED SPECULATION** and must not be treated as fact without further verification.

## Index

| Document | Topic | Source quality |
|----------|-------|---------------|
| [datomic-crash-analysis.md](datomic-crash-analysis.md) | Datomic transactor crashes — root cause, frequency, fix options | High — direct log analysis from `logs/datomic.{1,2,3}.log` |
| [content-extensibility-cross-links.md](content-extensibility-cross-links.md) | How content aspects inject into each other today (subraces, subclasses, boons, invocations, ancestries, spells) and the target catalog/grant shape | High — direct code inspection, symbols verified |
| [decision-vocabulary.md](decision-vocabulary.md) | **What homebrew decisions each silo can express + the load-time compile paths**, traced backward from every builder form to its assembly fn (feat/race/subrace/class/subclass/background). The map of cross-silo gaps. | High — backward builder→assembly trace, symbols + line refs verified |
| [homebrew-content-merge.md](homebrew-content-merge.md) | The `feat-options` trap: why "X isn't homebrew-extensible" conclusions are usually wrong (merge happens at the concat point, not the static `*-options` def) | High — direct code inspection |
| [content-extensibility-framework.md](content-extensibility-framework.md) | Canonical reference for the registry-driven content system being built (one entry vs ~9-file edits) | High — code + design |

> Design, handoff, and decision records for the Content Extensibility initiative
> (the forward-looking, non-verified half) live in [`docs/extensibility/`](../extensibility/README.md).

## Contribution rules

- Only add findings you can cite directly (log lines, code lines, benchmark results, official docs).
- If you are reasoning from circumstantial evidence, mark the entire paragraph with **⚠️ UNVALIDATED SPECULATION — [brief rationale]**.
- Include the date the analysis was done and the artifact(s) it was based on.
- Do not remove speculation flags — if something is later verified, replace the flag with a **✅ VERIFIED — [how]** marker and update the text.
