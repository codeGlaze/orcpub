# Subscribe Refactor Phase 2: Modifier Conditions, PDF, Equipment

## Context

Phase 1 (documented in `re-frame-subscribe-refactor.md`) fixed 12 subscribe-outside-reactive-context warnings in `events.cljs` and `core.cljs`. Phase 2 fixed 13 more across `options.cljc`, `pdf_spec.cljc`, `equipment_subs.cljs`, and `views.cljs`.

These 13 were in different contexts than Phase 1: modifier conditions, prereq functions, PDF generation, and UI event closures — not event handlers.

## Fix Patterns (New in Phase 2)

### Pattern 5: SSOT pure function with `@re-frame.db/app-db`

**When**: Code runs outside reactive context (modifier conditions, prereq fns) but needs data from app-db that includes user-imported content.

```clojure
;; Pure function as SSOT — used by both subscriptions and conditions
(defn compute-all-weapons-map [custom-items]
  (let [expanded (expand-magic-items custom-items)
        all-items (concat expanded magic-items)
        all-magic-weapons (sequence magic-weapon-xform all-items)]
    (merge (common/map-by-key all-magic-weapons) weapons5e/weapons-map)))

;; In modifier condition (options.cljc):
(let [all-weapons-map (mi/compute-all-weapons-map
                       (get @re-frame.db/app-db ::mi/custom-items))]
  ...)

;; In subscription (subs.cljs):
(mi/compute-all-weapons-map custom-items)
```

**Key lesson**: DO NOT replace a subscription with just a static def when custom/homebrew content is involved. `mi/all-weapons-map` (static) does NOT include user-imported weapons. Always check if the subscription chain includes dynamic data.

**CRITICAL**: Custom/homebrew content is the PRIMARY use case. Users import 2-5MB plugin files daily. Never treat `custom-items = []` as the normal case.

### Pattern 6: Thread parameter from caller

**When**: The needed data is already available in the calling chain — just not passed through.

```clojure
;; BEFORE — subscribes to ::races/race-map inside feat-prereqs
(defn feat-prereqs [prereqs path-prereqs]
  (let [race-map @(subscribe [::races/race-map])]
    ...))

;; AFTER — race-map threaded from template-selections → feat-option-from-cfg → feat-prereqs
(defn feat-prereqs [prereqs path-prereqs race-map]
  ...)

;; Caller computes from already-available data:
(let [race-map (common/map-by-key races)]
  (opt5e/feat-option-from-cfg ... race-map ...))
```

**Key**: Check what data the caller chain already has before reaching for app-db.

### Pattern 7: Plugin-data map parameter

**When**: Multiple subscribes in a pure namespace that shouldn't depend on re-frame at all.

```clojure
;; BEFORE — pdf_spec.cljc required re-frame and had 7 subscribes
(ns orcpub.pdf-spec
  (:require [re-frame.core :refer [subscribe]] ...))

(defn make-spec [built-char id options]
  (let [spells-map @(subscribe [::spells/spells-map])
        ...]))

;; AFTER — callers subscribe in render context, pass plugin-data map
(defn make-spec [built-char id options
                 {:keys [spells-map plugin-spells-map language-map
                         all-weapons-map current-armor-class]}]
  ...)

;; In views.cljs (render context — subscribes are safe):
(let [plugin-data {:spells-map @(subscribe [::spells/spells-map])
                   :plugin-spells-map @(subscribe [::spells/plugin-spells-map])
                   ...}]
  (export-pdf built-char id plugin-data options))
```

**Key**: This made pdf_spec.cljc fully pure — `[re-frame.core :refer [subscribe]]` removed entirely. Now testable on JVM.

### Pattern 8: reg-sub-raw for conditional subscription

**When**: A `reg-sub` computation function subscribes internally based on a parameter.

```clojure
;; BEFORE — subscribe inside reg-sub computation
(reg-sub ::mi5e/item
  (fn [_ [_ key]]
    (if (int? key)
      @(subscribe [::mi5e/remote-item key])  ;; WARNING
      (get mi5e/all-equipment-map key))))

;; AFTER — reg-sub-raw with proper signal routing
(reg-sub-raw ::mi5e/item
  (fn [_app-db [_ key]]
    (if (int? key)
      (subscribe [::mi5e/remote-item key])   ;; returns reaction directly
      (ra/make-reaction (fn [] (get mi5e/all-equipment-map key))))))
```

**Key**: `reg-sub-raw` handlers return reactions, not values. For the int path, return the subscription itself (it's a reaction). For the static path, wrap in `ra/make-reaction`.

### Pattern 9: Move subscribe from closure to render scope

**When**: `@(subscribe [...])` is inside an `:on-click` or other event closure.

```clojure
;; BEFORE — subscribe fires on click (not reactive context)
{:on-click #(dispatch [:edit-character @(subscribe [::char/character id])])}

;; AFTER — subscribe at render time, close over the value
(let [character @(subscribe [::char/character id])]
  {:on-click #(dispatch [:edit-character character])})
```

**Key**: The value is captured once at render time. If the character changes between render and click, you get the render-time value — which is actually correct (the user clicked on what they saw).

## All 13 Fixes

| Location | Subscribe | Pattern | Risk |
|----------|-----------|---------|------|
| options.cljc:1148 | `::mi/all-weapons-map` | SSOT pure fn (#5) | Low |
| options.cljc:1726 | `::mi/all-weapons-map` | SSOT pure fn (#5) | Low |
| options.cljc:2050 | `:homebrew? path` | Direct app-db read (#5) | Low |
| options.cljc:3177 | `::races/race-map` | Thread param (#6) | Low |
| pdf_spec.cljc:250 | `::spells/spells-map` | Plugin-data map (#7) | Low |
| pdf_spec.cljc:297 | `::spells/spells-map` | Plugin-data map (#7) | Low |
| pdf_spec.cljc:298 | `::spells/plugin-spells-map` | Plugin-data map (#7) | Low |
| pdf_spec.cljc:346 | `::spells/spells-map` | Plugin-data map (#7) | Low |
| pdf_spec.cljc:418 | `::langs5e/language-map` | Plugin-data map (#7) | Low |
| pdf_spec.cljc:446 | `::mi5e/all-weapons-map` | Plugin-data map (#7) | Low |
| pdf_spec.cljc:542 | `::char5e/current-armor-class` | Plugin-data map (#7) | Low |
| equipment_subs.cljs:264 | `::mi5e/remote-item` | reg-sub-raw (#8) | Medium |
| views.cljs:7920 | `::char/character` | Render scope (#9) | Low |

## Files Changed

| File | Changes |
|------|---------|
| `src/cljc/orcpub/dnd/e5/magic_items.cljc` | Added `compute-all-weapons-map` SSOT fn |
| `src/cljc/orcpub/dnd/e5/options.cljc` | Weapons-map, homebrew prereq, race-map fixes; added `[re-frame.db]` require |
| `src/cljc/orcpub/dnd/e5/template.cljc` | Compute race-map from `races`, pass to feat-option-from-cfg |
| `src/cljc/orcpub/pdf_spec.cljc` | All 7 subscribes removed; `subscribe` removed from ns |
| `src/cljs/orcpub/dnd/e5/equipment_subs.cljs` | `::mi5e/item` converted to reg-sub-raw |
| `src/cljs/orcpub/dnd/e5/views.cljs` | onClick fix; PDF callsite plugin-data assembly |

## Requires Added

Only ONE new require across all changes: `[re-frame.db]` in `options.cljc`. Everything else was already imported.

## Verification

- `lein test` — 123 tests, 332 assertions, 0 failures
- `lein fig:build` — 0 errors, 0 warnings
- Browser console: zero subscribe-outside-reactive-context warnings expected

### Pattern 10: Top-level `def` with `partial` → `defn`

**When**: A `def` uses `partial` with `@(subscribe ...)` as an eagerly-evaluated argument — runs at namespace load time, outside any reactive context.

```clojure
;; BEFORE — subscribes at load time (bad)
(def option-language-proficiency-choice
  (partial option-proficiency-choice
           "Language Proficiency Choice"
           :language-options
           @(subscribe [::langs/languages])))

;; AFTER — subscribes during render (good)
(defn option-language-proficiency-choice
  [option set-path-prop-event toggle-path-prop-event]
  (option-proficiency-choice
   "Language Proficiency Choice"
   :language-options
   @(subscribe [::langs/languages])
   option
   set-path-prop-event
   toggle-path-prop-event))
```

**Key**: `def` + `partial` is fine for static data (e.g. `skills/skills`), but NOT for subscriptions. The `partial` evaluates all args immediately. Converting to `defn` defers evaluation to call time (render).

**Diagnosis note**: This single call site produced 4 warnings because `::langs/languages` has a 3-deep `reg-sub` chain — each inner input function also calls `subscribe`, each triggering its own warning. One source, multiple warnings.

## Lessons

1. **Trace the full subscription chain before replacing.** `::mi/all-weapons-map` is NOT just `mi/all-weapons-map` — it includes custom items from user plugins.
2. **Custom content is never empty.** Treat `[]` as a test fixture, not a default.
3. **Check what the caller already has.** `feat-prereqs` didn't need to subscribe to race-map because `template-selections` (its grandparent caller) already had `races`.
4. **Pure .cljc namespaces shouldn't depend on re-frame.** The pdf_spec cleanup makes the module testable on JVM and removes a framework coupling that never belonged there.
5. **`def` + `partial` with dynamic data is a trap.** Looks clean, evaluates at load time. Any arg that needs reactive data must move to a `defn`.
