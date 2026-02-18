# re-frame: Subscribe-Outside-Reactive-Context Refactor

## The Problem

re-frame 1.3.0+ warns when `@(subscribe [...])` is called outside a Reagent reactive context (i.e., outside a component render function). In OrcPub, 12 instances existed in event handlers and top-level code — a pattern that "worked" in re-frame 0.x but was never correct.

### What actually happens when you subscribe in a handler

1. A fresh Reagent `Reaction` is created
2. It's immediately deref'd — synchronous computation — returns the correct value
3. The Reaction is **orphaned** (never tracked by Reagent's dependency graph, never disposed)
4. Each invocation leaks one Reaction object
5. It's **never reactive** — one-shot synchronous read that just looks like a subscription

The value is correct, but the code leaks memory and will break if re-frame removes this accidental behavior.

## Key Insight: Subscription Layers Matter

Not all subscriptions are simple db reads. Before fixing, you **must** trace the subscription's computation chain:

| Layer | Description | Fix Approach |
|-------|------------|--------------|
| Layer 2 | Direct db lookup (`(fn [db _] (get db :key))`) | Read from `db` directly in handler |
| Layer 3 | Computed/derived from other subs | Extract pure functions or pass from component |
| reg-sub-raw | Side-effecting (HTTP calls, async) | Create equivalent event, or read cached db value |

**Critical mistake to avoid**: Assuming all subscriptions are "just db reads." In OrcPub, 11 of 12 problematic subscriptions were Layer 3 (computed). Naively replacing with `(get db ...)` would silently return `nil` or wrong values.

## Fix Patterns (Stable APIs Only)

Ranked by preference. All use stable re-frame APIs (no alpha namespaces).

### Pattern 1: Direct db read

**When**: The subscription is Layer 2 — just a `(get db :key)` or `(get-in db [...])`.

```clojure
;; BEFORE
(reg-event-fx
 ::export-all-plugins
 (fn [_ _]
   (let [plugins @(subscribe [::e5/plugins])]  ;; Layer 2: (get db :plugins)
     ...)))

;; AFTER
(reg-event-fx
 ::export-all-plugins
 (fn [{:keys [db]} _]
   (let [plugins (get db :plugins)]
     ...)))
```

**Key**: You must know the exact db path. Trace the subscription definition to verify.

### Pattern 2: Pass from component

**When**: The dispatching component already has (or can easily get) the value.

```clojure
;; BEFORE — component
[:button {:on-click #(dispatch [:save-character])}]
;; BEFORE — handler
(fn [{:keys [db]} _]
  (let [built-char @(subscribe [:built-character])] ...))

;; AFTER — component (already has built-char in scope)
[:button {:on-click #(dispatch [:save-character built-char])}]
;; AFTER — handler
(fn [{:keys [db]} [_ built-char]]
  (let [...] ...))
```

**Key**: The subscribe call moves from the handler (wrong context) to the component (correct reactive context). No computation changes.

### Pattern 3: Extract shared pure functions

**When**: The subscription computes a derived value through a chain of pure transformations. Extract the transformation logic into a pure function, call it from both the subscription and the handler.

```clojure
;; Pure helper (used by both sub and handler)
(defn compute-sorted-spells [db]
  (let [plugins (get db :plugins)
        plugin-vals (compute-plugin-vals plugins)
        ...]
    (common/aloof-sort-by :name all-spells)))

;; In subscription: (compute-sorted-spells @app-db)
;; In handler: (compute-sorted-spells db)
```

**Key**: This is the most work but safest for complex chains. The pure function is testable and guarantees identical results.

### Pattern 4: Replace side-effecting subscribe with dispatch

**When**: A `reg-sub-raw` subscription is used purely for its side effects (e.g., triggering an HTTP call).

```clojure
;; BEFORE — top-level in core.cljs
@(subscribe [:user false])  ;; triggers HTTP auth check as side effect

;; AFTER — explicit dispatch
(dispatch-sync [:verify-user-session])

;; New event handler replicates the side effect
(reg-event-fx
 :verify-user-session
 (fn [{:keys [db]} _]
   (when (and (:user db) (:token (:user db)))
     (go (let [response (<! (http/get ...))]
           (case (:status response)
             200 nil
             401 (dispatch [:clear-login])
             nil)))
     {})))
```

## What NOT To Do

### Don't use `re-frame.flow` or `re-frame.alpha`

These are unstable APIs. For a production app with 100k users, stick to stable patterns. `re-frame.flow` is the "modern answer" but lives in `re-frame.alpha` namespace — not production-ready.

### Don't use `inject-sub` cofx

Third-party library (`re-frame-utils`). Adds an external dependency for a problem solvable with stable patterns.

### Don't assume you can "just read from db"

Many subscriptions compute derived values through multi-step chains. Example: `::char5e/sorted-spells` goes through 5 subscription layers:
```
(get db :plugins)
  → filter disabled plugins/entries (::e5/plugin-vals)
  → extract spells + add :edit-event (::spells5e/plugin-spells)
  → merge with base spells into sorted-set (::spells5e/spells)
  → sort by name (::char5e/sorted-spells)
```

Replacing `@(subscribe [::char5e/sorted-spells])` with `(get db ::char5e/sorted-spells)` returns `nil` — that key doesn't exist in db. The value only exists in the subscription cache.

## OrcPub-Specific: Subscription Chain Reference

### Spells chain (compute-sorted-spells)
```
:plugins (db)
  → ::e5/plugin-vals — filter disabled plugins + disabled entries within
  → ::spells5e/plugin-spells — (mapcat (comp vals ::e5/spells) plugin-vals) + :edit-event
  → ::spells5e/spells — (into (sorted-set-by compare-keys) (concat (reverse plugin-spells) spells5e/spells))
  → ::char5e/sorted-spells — (common/aloof-sort-by :name spells)
```

### Items chain (compute-sorted-items)
```
::mi5e/custom-items (db, async fetch via reg-sub-raw)
  → ::mi5e/expanded-custom-items — mi5e/expand-magic-items
  → ::char5e/sorted-items — (concat expanded @sorted-static-items)
```

### Template chain (NOT replicated — too complex)
```
::char5e/template-selections — 12 input subscriptions (weapons, armor, spells, races, classes, etc.)
  → ::char5e/template — t5e/template(template-selections)
  → :built-template — no-op (plugin merging commented out, just returns template)
  → :built-character — entity/build(character, built-template)
```

This chain is why `::char5e/save-character` retains one subscribe call — replicating 12+ subscription inputs would be fragile and violate DRY.

### character-interceptors and (path :character)

Handlers using `character-interceptors` receive `character` (not `db`) because the interceptor chain includes `(path :character)`. This means you **cannot** do direct db reads in these handlers. Options:
- Pass needed values from the component via the dispatch vector
- Switch from `reg-event-db` with `character-interceptors` to `reg-event-fx` (loses the path convenience)

## Instances Fixed

| Handler | Technique | Risk |
|---------|-----------|------|
| `::e5/export-all-plugins` | Direct db read | Zero |
| `::e5/export-all-plugins-pretty-print` | Direct db read | Zero |
| `::char5e/level-up` | Direct db read | Zero |
| `:save-character` | Pass built-char from component | Low |
| `::char5e/set-random-name` | Pass built-char + pure fns (char5e/race, subrace, sex) | Low |
| `::char5e/filter-spells` | Extract compute-sorted-spells helper | Medium |
| `::char5e/filter-items` | Extract compute-sorted-items helper | Medium |
| `:set-custom-subclass` | Pass built-template from component | Low |
| `:set-custom-feat-name` | Pass built-template from component | Low |
| `core.cljs` top-level | Replace with dispatch-sync [:verify-user-session] | Low |
| `::char5e/save-character` (character) | Direct db read | Zero |

### Last fix: Template cache via `track!` (12 of 12)

| Handler | Technique |
|---------|-----------|
| `::char5e/save-character` (built-character) | Cache template in app-db via `reagent.core/track!`, compute `entity/build` in handler |

**How it works**: `autosave_fx.cljs` creates a `track!` watcher (proper reactive context) that observes the `::char5e/template` subscription and caches its value in app-db under `::autosave-fx/cached-template`. The save handler reads the cached template and calls `entity/build(character, cached-template)` directly.

**Why this works**: `built-template` is a no-op (plugin merging is commented out in `subs.cljs:254-270`). So `built-character = entity/build(character, template)` — no need for the full `built-template` computation.

**Safety**: Nil guard skips save if template not cached yet. Next autosave cycle (7.5s) retries.

### Circular Dependency (Root Cause)

```
equipment_subs.cljs → events.cljs (url-for-route, show-generic-error)
subs.cljs           → events.cljs (url-for-route, show-generic-error, mod-cfg, default-mod-set)
spell_subs.cljs     → events.cljs (dead import — not actually used)
```

Also: `auth-headers` duplicated 3x (events.cljs as `authorization-headers`, subs.cljs, equipment_subs.cljs).

**Fix**: Create `orcpub.dnd.e5.event-utils` with shared utility functions. Sub files import from event-utils instead of events. Events can then import from sub files, breaking the circle.

### Critical Bug Found and Fixed

`custom-option-builder` in options.cljc was initially modified to unconditionally inject `built-template` into ALL dispatch vectors. Only 2 of 5 handlers (`:set-custom-subclass`, `:set-custom-feat-name`) expected it. The other 3 (`:set-custom-race`, `:set-custom-subrace`, `:set-custom-background`) would receive the template object as the "name" argument — data corruption for homebrew content (core functionality, not niche). Fixed with multi-arity: `inject-template?` flag defaults to `false`.

## Files Changed

| File | Changes |
|------|---------|
| `src/cljs/orcpub/dnd/e5/events.cljs` | All handler fixes + compute helpers + verify-user-session event |
| `src/cljs/orcpub/character_builder.cljs` | save-character and set-random-name pass values via dispatch |
| `src/cljc/orcpub/dnd/e5/options.cljc` | custom-option-builder multi-arity (inject-template? flag) |
| `web/cljs/orcpub/core.cljs` | @(subscribe [:user false]) → (dispatch-sync [:verify-user-session]) |
| `src/cljs/orcpub/dnd/e5/event_utils.cljs` | NEW: shared utility functions extracted from events.cljs |
| `src/cljs/orcpub/dnd/e5/subs.cljs` | Import event-utils instead of events, remove local auth-headers |
| `src/cljs/orcpub/dnd/e5/equipment_subs.cljs` | Import event-utils instead of events, remove local auth-headers |
| `src/cljs/orcpub/dnd/e5/spell_subs.cljs` | Remove dead events import |

## Verification

**Automated**:
- [x] `lein test` — 74 tests, 238 assertions, 0 failures
- [x] `lein fig:build` — 0 errors, 0 warnings
- [x] `subscribe` removed from events.cljs requires — zero subscribe calls outside reactive context

**Manual testing needed** (no CLJS test infra exists):
- [ ] Save character (manual + autosave) — verify correct summary
- [ ] Random name — verify race/subrace/sex-appropriate
- [ ] Filter spells/items — type 3+ characters, verify filtering
- [ ] Level up — verify navigation to builder
- [ ] Export plugins — verify .orcbrew file downloads
- [ ] Custom subclass/feat name — verify homebrew name input
- [ ] Login/startup — verify auth check fires
- [ ] Homebrew import — import .orcbrew file, create character with custom content

## Risk: `reagent.core/track!` (new pattern)

**First use in this project.** `track!` is stable Reagent API (since ~0.6) but has never been used in OrcPub.

Risk factors:
- Creates a long-lived reactive watcher — won't be GC'd
- `js/setTimeout 0` relies on all `reg-sub` calls completing before the timeout fires
- If `::char5e/template` subscription is renamed/removed, the watcher silently fails
- If `built-template` plugin merging is re-enabled, the cached template would be incomplete

The nil guard (returns `{}` if template not cached) prevents data loss but means autosave silently skips one 7.5s cycle on cold start.
