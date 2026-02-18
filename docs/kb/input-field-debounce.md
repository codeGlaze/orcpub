# Input Field Debounce Architecture

## Current Design (components.cljc)

`input-field` is a Reagent Form-2 component (closure over local atom). It debounces re-frame dispatches to avoid triggering expensive `entity/build` on every keystroke.

### How it works

```
User types → local :temp-val updated immediately → UI shows typed text
           → 500ms timer starts
           → timer fires → on-change dispatched → re-frame event → db update
           → subscription recalculates → parent re-renders with new value prop
           → input-field sees value ≠ :prev-value → clears :temp-val
           → now showing subscription value (which matches what user typed)
```

### State atom structure

```clojure
{:timeout   nil      ;; js/setTimeout handle (for clearTimeout)
 :temp-val  nil      ;; what user has typed (not yet dispatched)
 :prev-value nil}    ;; last value prop from parent (for change detection)
```

Uses `clojure.core/atom` (NOT `reagent.core/atom`) because we `swap!` during render to sync `:prev-value`. A Reagent atom would cause infinite re-render loops.

### The flickering bug (fixed)

**Old code** cleared `:temp-val` inside the setTimeout callback after dispatching. This raced with Reagent 2.x's synchronous rendering:

1. Timer fires → dispatch + clear temp-val
2. Re-frame processes event → updates db
3. Reagent 2.x renders synchronously → component re-renders
4. But subscription hasn't propagated yet → value prop is still OLD
5. temp-val is nil (cleared in step 1) → shows old subscription value
6. Subscription catches up → another re-render with correct value
7. User sees: typed text → flash of old text → correct text

**Fix**: Track `:prev-value`. Only clear `:temp-val` when the parent value prop actually changes (subscription caught up). No clearing in setTimeout.

## Why the Debounce Exists

Every character value change triggers `entity/build` which:
- Walks the entire template tree (nested options, modifiers, prereqs)
- Applies all modifiers (ability scores, proficiencies, HP, AC, etc.)
- Recalculates ~60 downstream subscriptions
- Costs ~50-200ms for a typical character

Without debounce: 10 keystrokes = 10 full builds = 500-2000ms of computation = visible lag.

## All Callsites

| File | Element | on-change Event | Notes |
|------|---------|----------------|-------|
| character_builder.cljs | character-field wrapper | `:update-value-field` | Most character props |
| character_builder.cljs | equipment name | `::char5e/set-custom-item-name` | Homebrew item editing |
| character_builder.cljs | equipment qty (int-field) | `:change-inventory-item-quantity` | Numeric |
| views.cljs | XP field | `::char/set-current-xps` | Direct parseInt |
| views.cljs | Notes textarea | `::char/set-notes` | Memoized handler |
| views.cljs | Builder fields | Various | Spell/Monster/Feat builders |
| options.cljc | Custom option name | Variable name-event | Plugin option builder |

## Future: Dispatch-Immediate Architecture

The cleaner approach eliminates local state entirely:

1. **Dispatch immediately** on every keystroke → value goes into `app-db` instantly
2. **Debounce `entity/build`** downstream — the `:built-character` subscription waits 500ms after last db change before recomputing
3. **No local atom** needed — pure re-frame controlled input, no race conditions

### Scope of the refactor

~50 lines total:

1. **`input-field`** — delete local atom, just dispatch. Becomes a thin wrapper.
2. **`:built-character` subscription** in `subs.cljs` — wrap `entity/build` call in debounced `reg-sub-raw` that delays recomputation by 500ms, returning stale value in the interim.
3. **`->local-store` interceptor** — already fires on every character event (saves to localStorage). Unchanged.

### Trade-off

During the 500ms window, derived values (AC, ability scores, etc.) show stale. For most inputs this is invisible — nobody notices their AC lagging after typing a character name. For inputs that affect the build (class selection, race selection), those use dropdowns, not text fields, so debounce doesn't apply.

### Why not do it now

The current fix (prev-value tracking) works correctly. The refactor is a cleanup, not a bug fix. Worth doing when touching the subscription architecture for other reasons.
