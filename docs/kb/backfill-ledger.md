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
| _(none yet — no bespoke path has been migrated)_ | | | | | |

## Watch-list (candidates — pool/grant doesn't fully subsume them yet)
- **Fighting-style grant registry is hardcoded.** `grant-selection` is fed a literal
  `{:fighting-styles {…opt5e/fighting-style-options}}` at `template.cljc:1549` (built-in styles only).
  Wiring it to `content-pools/pool` (so homebrew fighting-style packs are grantable) is the first real
  pool/grant expansion — and the point at which any bespoke fighting-style / feat-grant path it then
  subsumes becomes a ledger item.

See: D29 (one mechanism per job), D30 (grant = thin compiler), D34 (deprecation mechanics).
