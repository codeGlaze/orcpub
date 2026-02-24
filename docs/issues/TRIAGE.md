# Triage — Investigated Issues

> Codebase investigation performed 2026-02-24 against `breaking/2026-stack-modernization`
> [Back to Index](INDEX.md)

## Ready to Close (already implemented)

These issues have been fully addressed on the `breaking/` branch and can be closed with
a comment pointing to the relevant code.

### [#549](https://github.com/Orcpub/orcpub/issues/549) — Cognitect no longer providing Datomic free versions

**Status: FULLY RESOLVED**

- Datomic Pro 1.0.7482 bundled in Docker build (`Dockerfile:8-18`), installed to local Maven via `bin/maven-install`
- `project.clj:81` updated from `datomic-free` to `[com.datomic/peer "1.0.7482"]`
- Connection URI uses `datomic:dev://` protocol (`docker-compose.yaml:18`)
- Full migration docs: `docs/migration/datomic-pro.md`
- No dependency on external Cognitect downloads remains

### [#524](https://github.com/Orcpub/orcpub/issues/524) — Add Warning message: missing content

**Status: FULLY IMPLEMENTED**

- Detection: `content_reconciliation.cljs` — `extract-content-keys`, `check-content-availability`, `generate-missing-content-report`
- Built-in SRD exclusion (lines 163-194) prevents false warnings for hardcoded content
- Subscriptions: `subs.cljs` — `::char5e/missing-content-report`, `::char5e/has-missing-content?`
- UI: `character_builder.cljs:1913-1968` — expandable orange warning banner with per-item details, fuzzy matching suggestions, mobile-responsive
- Tests: `content_reconciliation_test.cljs` — content extraction, detection, exclusion, fuzzy matching, report generation
- Docs: `docs/CONTENT_RECONCILIATION.md`

---

## Quick Wins — Confirmed S-Complexity

These are one-liner or minimal-change fixes with clear file:line targets. Each is independently
deployable and user-visible.

### [#548](https://github.com/Orcpub/orcpub/issues/548) — Dropdown doesn't recognize '1'

**Status: STILL PRESENT** | Complexity: **S**

- **Root cause**: `views/common.cljs:340` — `{:value (or value "")}`. HTML select elements compare values as strings, but options may have numeric values. String `"1"` doesn't match numeric `1`.
- **Fix**: Ensure values are consistently stringified in the dropdown component. Change option value generation to `(str value)`.

### [#84](https://github.com/Orcpub/orcpub/issues/84) — Submit login via Enter key

**Status: NOT FIXED** | Complexity: **S**

- **Root cause**: `views/auth.cljs:363-367` — password input has `on-change` but no `on-key-down` handler. Login button (line 374-380) only has `on-click`.
- **Fix**: Add `:on-key-down` to the password field that dispatches login on keyCode 13 (Enter). Or wrap the form in `<form>` with `:on-submit`.

### [#547](https://github.com/Orcpub/orcpub/issues/547) — Remove Non-SRD human subraces

**Status: STILL PRESENT** | Complexity: **S**

- **Root cause**: `spell_subs.cljs:141-150` — Calishite, Chondathan, Damaran, Illuskan, Mulan, Rashemi, Shou, Tethyrian, Turami are hardcoded. These are Forgotten Realms setting content, not SRD.
- **Fix**: Remove the non-SRD subraces array. Keep only Standard Human and Variant Human (lines ~157-167).

### [#520](https://github.com/Orcpub/orcpub/issues/520) — Order Spell Cards by Level Then Alphabet

**Status: PARTIALLY FIXED** | Complexity: **S**

- **Root cause**: `pdf_spec.cljc:260` — `(sort-by (comp :name spells-map :key) a-s)` sorts by name only. Spells are grouped by level in the data structure but within-level ordering is alphabetical only.
- **Fix**: Change to `(sort-by (juxt :level (comp :name spells-map :key)) a-s)` for level-first, then alphabetical.

### [#304](https://github.com/Orcpub/orcpub/issues/304) — Sort by Level in Class/Level Tab

**Status: AMBIGUOUS** | Complexity: **S→M**

- `builders.cljs:1756` — `map-indexed` over `level-selections` directly. Order depends on input data structure. If `level-selections` comes from a map/set, order is not guaranteed.
- **Fix**: Add `(sort-by :level)` before `map-indexed`. Needs testing to confirm the actual sort issue.
- **Action**: Manually reproduce before attempting fix.

---

## Quick Wins — Need More Investigation

### [#614](https://github.com/Orcpub/orcpub/issues/614) — Features named 'Null' break things

**Status: LIKELY PRESENT** | Complexity: **S→M**

- `common.cljc:19` — `name-to-kw` converts "Null" to keyword `:null`. This is semantically distinct from `nil`, but downstream code may use `:null` in conditional checks that behave unexpectedly.
- `views/builders.cljs:2538` — `find-duplicate-option-names` guards blank strings but not "Null" specifically.
- **Action**: Need to trace how `:null` keyword flows through character building. May be a JS interop issue where CLJS keyword `:null` serializes to something that JavaScript treats as `null`.

### [#574](https://github.com/Orcpub/orcpub/issues/574) — Subclass cantrips in separate category

**Status: PARTIAL FIX COMMENTED OUT** | Complexity: **M**

- `options.cljc:1934` — `subclass-cantrip-selection` function exists but is commented out with `#_`. Comment: "dead — only called from deprecated ua_sorcerer.cljc".
- The fix was written and then disabled when UA content was deprecated. Re-enabling requires deciding whether UA content is coming back.
- **Action**: Separate from the commented function — the underlying issue of subclass cantrips rendering in a separate category needs investigation in current active code paths.

---

## High-Leverage Clusters — Root Cause Confirmed

### Multiclass Spellcasting (#435, #437, #47, #272)

**Root cause confirmed**: `template_base.cljc:268` — `int` always rounds down.

```clojure
;; Current (broken):
(int (/ class-level factor))

;; Fix:
(if round-up? (Math/ceil (/ class-level factor)) (int (/ class-level factor)))
```

| Issue | Status | Notes |
|---|---|---|
| [#435](https://github.com/Orcpub/orcpub/issues/435) | **STILL PRESENT** | 2-line fix: add `:round-up?` flag, use `Math/ceil` for Artificer. Lines 268 + 283. |
| [#437](https://github.com/Orcpub/orcpub/issues/437) | **PARTIALLY PRESENT** | Multiclass slot merge logic is correct (factor 1 baseline). Cascades from #435's rounding. |
| [#47](https://github.com/Orcpub/orcpub/issues/47) | **UNCLEAR** | `spell-prepared?` at `character.cljc:861` looks architecturally sound (segregates by class). Issue may be in views layer — per-class prep limits not enforced in UI. Needs manual testing. |
| [#272](https://github.com/Orcpub/orcpub/issues/272) | **IMPLEMENTED BUT DISABLED** | `ua_artificer.cljc:181` has full `spells-known` schedule with `:known-mode :schedule`, but whole definition is `#_` commented out. No non-UA Artificer exists. |

**Recommended approach**: Fix #435 first (2 lines), then test #437 and #47 to see if they cascade-fix. #272 depends on whether Artificer content is being restored.

### Equipment Identity (#340, #138, #229)

**Root cause confirmed**: `template.cljc:1268` — `sequential? false` makes equipment a map keyed by item type.

| Issue | Status | Notes |
|---|---|---|
| [#340](https://github.com/Orcpub/orcpub/issues/340) | **STILL PRESENT** | Two longswords → second overwrites first. `modifiers.cljc:464` uses `map-mod`. |
| [#138](https://github.com/Orcpub/orcpub/issues/138) | **STILL PRESENT** | Pack items with same key overwrite. Burglar's Pack (5 rations) + Dungeoneer's Pack (10 rations) → only 10. |
| [#229](https://github.com/Orcpub/orcpub/issues/229) | **PARTIALLY ADDRESSED** | `::quantity` field exists in spec. Commented-out display code has `"x " item-qty`. Weapons view has no quantity column. |

**Fix scope**: Change `sequential? false` → `true`, switch `map-mod` → `vec-mod`, update subs + views to iterate vectors. **L complexity** — entity storage model change ripples across template, modifiers, subs, views.

### Selection Nesting (#260, #174, #39, #486)

**Root cause confirmed**: Template system supports nesting (built-in Half-Elf uses 3+ levels deep), but homebrew builder UI doesn't expose it.

| Issue | Status | Notes |
|---|---|---|
| [#260](https://github.com/Orcpub/orcpub/issues/260) | **STILL PRESENT** | `builders.cljs:2545-2629` — option UI only has name + description. No `:selections` field. |
| [#174](https://github.com/Orcpub/orcpub/issues/174) | **LIKELY PRESENT** | Related to same builder limitations. |
| [#39](https://github.com/Orcpub/orcpub/issues/39) | **UNCLEAR** | May be edge cases in option selector rendering. |
| [#486](https://github.com/Orcpub/orcpub/issues/486) | **PARTIALLY IMPLEMENTED** | Built-in content works perfectly. Homebrew can't create nested selections. |

**Fix scope**: Extend `selections.cljc` spec, add recursive UI component, update events. **L complexity** — but architecturally clean since the template layer already supports it.

---

## Partially Fixed

### [#621](https://github.com/Orcpub/orcpub/issues/621) — Extreme UI Freezing With Excessive Custom Content

**Status: PARTIALLY FIXED**

**What's been done** (commit `56ad86fb`):
- Memoized `build-aux` → `memoized-build-aux` in `entity.cljc`
- Added `available-selections` subscription caching in `subs.cljs:277-292`
- Memoized event handler functions in `views.cljs` (route-handler, confirm-handler, etc.)
- Spell and equipment optimizations (commits `bac6010f`, `63ed22f1`)

**What's still missing**:
- No virtual scrolling for long lists
- No lazy loading of tab content
- Some subscriptions may still compute expensive results that could be split
- No `reagent.core/track` or cursor patterns found

**Recommendation**: Test with large homebrew sets (100+ items) to confirm freezing is resolved. If it recurs, virtual scrolling in `views/builders.cljs` and `views/lists.cljs` is the next target. Views extraction refactor sets up the architecture for this.

---

## Recommended Sprint Order

### Batch 1: Close + Quick Wins (1-2 days)
1. Close #549 and #524 (already done)
2. Fix #548 (dropdown stringify)
3. Fix #84 (Enter key login)
4. Fix #547 (remove non-SRD subraces)
5. Fix #520 (spell card sort)

### Batch 2: Spellcasting Domino (2-3 days)
1. Fix #435 (2-line `int` → `ceil`)
2. Test #437, #47 for cascade resolution
3. Decide on Artificer content (#272)

### Batch 3: Investigation + Testing (1-2 days)
1. Reproduce #304 (level sort)
2. Trace #614 (Null feature name)
3. Test #621 with large content sets
4. Reproduce #574 in active code paths

### Batch 4: Architecture (future sprint)
1. Equipment identity refactor (#340, #138, #229) — L
2. Selection nesting in homebrew builder (#260, #486) — L
