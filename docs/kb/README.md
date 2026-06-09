# OrcPub Agent Knowledge Base

Verified, research-backed findings from in-depth investigations. Each document is sourced from
direct inspection of code, logs, or authoritative references. Speculation is marked
**⚠️ UNVALIDATED SPECULATION** and must not be treated as fact without further verification.

## Index

| Document | Topic | Source quality |
|----------|-------|---------------|
| [datomic-crash-analysis.md](datomic-crash-analysis.md) | Datomic transactor crashes — root cause, frequency, fix options | High — direct log analysis from `logs/datomic.{1,2,3}.log` |
| [feature-tab-black-screen.md](feature-tab-black-screen.md) | "Black screen" rendering a character section — a nameless feature crashes the sort; built-in (Evasion) + custom-content cases; fixes + reusable diagnosis playbook | High — live headless-browser repro (exact stack + offending map), code lines, git pickaxe |

## Contribution rules

- Only add findings you can cite directly (log lines, code lines, benchmark results, official docs).
- If you are reasoning from circumstantial evidence, mark the entire paragraph with **⚠️ UNVALIDATED SPECULATION — [brief rationale]**.
- Include the date the analysis was done and the artifact(s) it was based on.
- Do not remove speculation flags — if something is later verified, replace the flag with a **✅ VERIFIED — [how]** marker and update the text.
