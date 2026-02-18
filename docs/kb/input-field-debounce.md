# Input Field Debounce Architecture

## Current Design (post-refactor)

Debounce responsibility lives in the `:built-character` subscription, not in `input-field`. The component dispatches on every keystroke; `entity/build` is debounced downstream with leading+trailing edge.

### Data flow

```
User types → on-change dispatches immediately → app-db updated
           → :built-character subscription sees change
           → leading edge: if ≥500ms since last build, compute now
           → trailing edge: if <500ms, schedule build after 500ms quiet
           → entity/build runs → derived subs (~60) recalculate
```

### input-field (components.cljc)

Form-2 component with minimal local atom. Dispatches `on-change` on every keystroke. Local `reagent.core/atom` buffers the typed value to prevent controlled-input flicker while re-frame propagates.

```clojure
;; State: two atoms (reagent, for reactive re-render)
local-val  ;; what user typed (cleared when subscription catches up)
prev       ;; last value prop from parent (change detection)
```

The `(when (not= value @prev) ...)` check runs during render and causes one extra render cycle when the subscription value changes. Not infinite — on the second render `value == @prev` so no further reset.

### debounced-build-sub (subs.cljs)

`reg-sub-raw` handler shared by `:built-character` and `::char5e/built-character`. Uses `add-watch` on the input subscription reactions.

**Leading+trailing edge**: dropdown changes compute immediately (>500ms since last build). Rapid keystrokes batch — only the last value triggers a build after 500ms quiet.

Cleanup via `:on-dispose` — removes watches, clears pending timeout.

## Why the Debounce Exists

Every character value change triggers `entity/build` which:
- Walks the entire template tree (nested options, modifiers, prereqs)
- Applies all modifiers (ability scores, proficiencies, HP, AC, etc.)
- Recalculates ~60 downstream subscriptions
- Costs ~50-200ms for a typical character

Without debounce: 10 keystrokes = 10 full builds = 500-2000ms of computation = visible lag.

## All input-field Callsites

| File | Element | on-change Event | Notes |
|------|---------|----------------|-------|
| character_builder.cljs | character-field wrapper | `:update-value-field` | Most character props |
| character_builder.cljs | equipment name | `::char5e/set-custom-item-name` | Homebrew item editing |
| character_builder.cljs | equipment qty (int-field) | `:change-inventory-item-quantity` | Numeric |
| views.cljs | XP field | `::char/set-current-xps` | Direct parseInt |
| views.cljs | Notes textarea | `::char/set-notes` | Memoized handler |
| views.cljs | Builder fields | Various | Spell/Monster/Feat builders |
| options.cljc | Custom option name | Variable name-event | Plugin option builder |

## Historical: The Flickering Bug

**Old code** (pre-refactor) debounced the dispatch in `input-field` and cleared `:temp-val` inside the setTimeout callback. This raced with Reagent 2.x's synchronous rendering — temp-val cleared before subscription caught up, causing a visible flash of the old value.

The intermediate fix (prev-value tracking) solved the flicker. The current refactor eliminated the problem entirely by removing setTimeout from the component.

## Design Decision: Why Leading+Trailing Edge

Plain trailing-edge debounce would delay ALL changes by 500ms, including dropdown selections (class, race, etc.) that should compute instantly. Leading edge fires the first change immediately; only rapid subsequent changes within 500ms are batched. Result: dropdowns feel instant, typing doesn't lag.
