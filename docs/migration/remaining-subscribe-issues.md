# Subscribe-Outside-Reactive-Context Issues — ALL FIXED

All 14 subscribe-outside-reactive-context warnings have been resolved
on `breaking/2026-stack-modernization`.

## Fixed Issues

### options.cljc — Modifier Conditions (2) ✓

**Lines 1148, 1726** — `@(subscribe [::mi/all-weapons-map])`

Replaced with `(mi/compute-all-weapons-map (get @re-frame.db/app-db ::mi/custom-items))`.
New pure function `compute-all-weapons-map` in magic_items.cljc serves as
SSOT for both subscriptions and condition code. Includes custom/homebrew items.

### options.cljc — Homebrew Prereq (1) ✓

**Line 2050** — `@(subscribe [:homebrew? path])`

Replaced with direct app-db read:
`(get-in (:character @re-frame.db/app-db) [::entity/homebrew-paths path])`.
Same data path the `:homebrew?` subscription used internally.

### options.cljc — Race Map Lookup (1) ✓

**Line 3177** — `@(subscribe [::races/race-map])`

Threaded `race-map` parameter through `template-selections` →
`feat-option-from-cfg` → `feat-prereqs`. Caller in template.cljc computes
`(common/map-by-key races)` from already-available `races` collection.

### pdf_spec.cljc — PDF Generation (7) ✓

All 7 subscribes removed. `make-spec` now accepts a `plugin-data` map
with keys `:spells-map`, `:plugin-spells-map`, `:language-map`,
`:all-weapons-map`, `:current-armor-class`. Callers in views.cljs subscribe
in render context and pass the map into onClick closures.

`[re-frame.core :refer [subscribe]]` removed from pdf_spec.cljc entirely.

### equipment_subs.cljs — Subscribe Inside reg-sub (1) ✓

**Line 264** — `@(subscribe [::mi5e/remote-item key])`

Converted `::mi5e/item` from `reg-sub` to `reg-sub-raw`. For int keys,
returns `(subscribe [::mi5e/remote-item key])` directly (reaction).
For keyword keys, wraps static lookup in `reagent.ratom/make-reaction`.

### views.cljs — onClick Closure (1) ✓

**Line 7920** — `@(subscribe [::char/character character-id])`

Moved subscribe to render-time `let` binding. The `character` value is
now captured at render and closed over in the onClick handler.

### views.cljs — Top-Level def with subscribe (1) ✓

**Line 4336** — `@(subscribe [::langs/languages])`

`option-language-proficiency-choice` was a `def` using `partial` that
evaluated `@(subscribe ...)` at namespace load time. Converted to `defn`
so the subscribe runs during render (inside reactive context). This single
call site produced 4 cascading warnings because `::langs/languages` has a
3-deep subscription chain — each inner `reg-sub` input function also calls
`subscribe`, each triggering its own warning.

---

## Dead Code (unchanged, not executing)

| File | Lines | Notes |
|------|-------|-------|
| options.cljc | 2139, 2719 | `#_` homebrew prereqs |
| templates/scag.cljc | 25, 452 | `#_` class defs |
| templates/ua_revised_ranger.cljc | 39 | `#_` multiclass prereq |
| templates/ua_warlock_and_wizard.cljc | 120 | `#_` patron prereq |
| templates/ua_base.cljc | 555 | `#_` entire ns require |

---

## Summary

All 14 active subscribe-outside-reactive-context instances are resolved.
Browser console is clean — zero subscribe warnings on fresh page load.
Dead code in `#_` blocks is inert and can be cleaned up separately.
