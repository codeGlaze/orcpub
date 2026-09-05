# Backfill ledger

Tracks bespoke paths being converged onto the systematic **pool/grant** standard (D29) and the code
deprecated along the way (D34). The rule is **one mechanism per job**; this ledger draining to zero is the
"uniform, no surprises" guarantee — every superseded path is visible and scheduled, never silently
duplicated or forgotten.

## When something lands here
A bespoke path becomes a ledger item the moment a pool/grant capability can do its job. Adding a pool/grant
capability **requires** logging the paths it now subsumes.

## Migration recipe (per item)
1. **Pin** the bespoke path's current behavior in a characterization test (if one doesn't exist).
2. **Re-point** the job at the pool/grant.
3. The **same test stays green** (uniformity without blind regression — D29 gate).
4. **Deprecate, don't delete** (D34): `#_`-discard the bespoke fn under the note —
   ```clojure
   ;; DEPRECATED <date> — superseded by <X>; behavior pinned by <test>. Remove after <date + ~3 months>. See backfill-ledger.md.
   #_(defn the-superseded-thing [...] ...)
   ```
5. **Add a row** below.
6. **Removal sweep:** once past `remove-after`, grep `DEPRECATED`, delete the struck form, mark the row Removed.

## Ledger
| Function (file) | Superseded by | Deprecated on | Remove after | Pinning test | Status |
|---|---|---|---|---|---|
| `?natural-ac-bonus` (template_base) | `mod5e/ac-formula` calculations; adapted by a seeded `?ac-fns` entry | 2026-09-04 | after `integration`'s `bracers_ac_test` stops writing it | `ac_reconciliation_test` parity sweep | **Shimmed** (kept declared; nothing in-repo writes it) |
| `:lizardfolk-ac` / `:tortle-ac` props (options) | the universal `{:ac …}` + `{:armor-gives-no-ac}` shape | 2026-09-04 | never — kept as prop keys (D9) | parity sweep 0; `tortle-decomposes-…` | **Re-pointed**, keys retained |
| `?unarmored-ac-bonus`, `?unarmored-with-shield-ac-bonus`, `?armored-ac-bonus`, `?magical-ac-bonus`, `?ac-bonus`, `?unarmored-defense` | `?ac-fns` / `?ac-bonus-fns` | 2026-09-04 | — | parity sweep 0; full suite | **Deleted outright — a D34 EXCEPTION**, see note |
| `mod5e/natural-ac-bonus`, `mod5e/unarmored-defense` (constructors) | `mod5e/ac-formula` | 2026-09-04 | — | — | **Deleted outright** — zero callers, no `:props` key reached them |
| `armor-class/best-ac`, `reconcile-ac`, `ac_experiments_test` | naive outer loop (measured faster below ~8 armors) | 2026-09-05 | — | `ac_outer_loop_analysis_test` | Deleted — never-released scaffolding (D34 allows) |

**Note on the exception (recorded, not hidden).** The six scalar channels were *released* code and D34
says `#_`-strike + schedule, not delete. They were deleted because (a) every writer in the repo had already
been moved and verified by the parity sweep, (b) no `:props` key could reach them from saved content, and
(c) leaving six dead `?`-attributes declared would have defeated the trim's purpose. If a downstream branch
turns out to write one, the seeded-adapter shim used for `?natural-ac-bonus` is the recovery pattern.

## Watch-list (candidates — pool/grant doesn't fully subsume them yet)
- ✅ **Fighting-style grant registry** — DONE (`fighting-style-authoring.md`): `template.cljc` now
  feeds `grant-selection` from `::classes5e/fighting-style-pool` (built-in ++ homebrew). The *class*
  path (Fighter/Paladin/Ranger picking a homebrew style) is the remaining half — decided, pinned,
  not threaded. It becomes a ledger row when `fighting-style-selection`'s static list is re-pointed.
- **Feat ASI is dual-format, not fully converged.** `feat-option-from-cfg` reads BOTH the legacy feat
  set (`#{:str :con}` + `:saves?`) and the cross-silo spread (`[[amount pool]]`), dispatching on shape
  (`options.cljc`; tests `ability_increase_grant_test/feat-*`). The legacy set path is **kept, not
  deprecated**. Converging it (route the set through `compile-ability-increases`) is now **unblocked on
  two of three fronts** — the `:save` rider models `:saves?`, and the per-silo `:attribution :general`
  fix means a feat's fixed +N no longer mis-attributes as racial. The **remaining** blocker is
  save-compat: the legacy feat choose-selection keys options by the ability (`::char/str`) while the
  spread keys them `asi-<idx>-<ability>`, so a naive migration drops saved feat picks on reload — it
  needs a load-time key reconciliation (same pattern as spell-selection reconciliation) + a D34
  characterization test. Deliberate two-readers state until then; **do this post-merge**, not before.

See: D29 (one mechanism per job), D30 (grant = thin compiler), D34 (deprecation mechanics).
