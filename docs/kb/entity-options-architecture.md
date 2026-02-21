# Entity Options Architecture

## Character Entity Structure

Characters store their choices in `::orcpub.entity/options`, a nested map.
The VALUE type depends on whether the template selection is single-select or multi-select.

### Single-select → MAP (no vector index in path)

Race, background, subrace, ability-scores, subclass archetypes:
```clojure
{::entity/options
 {:race {::entity/key :elf
         ::entity/options {:subrace {::entity/key :wood-elf}}}
  :background {::entity/key :acolyte}
  :ability-scores {::entity/key :standard-scores
                   ::entity/value {...}}}}
```

### Multi-select → VECTOR (indexed in path)

Class (multiclassing), levels, feats, skill proficiencies, weapons:
```clojure
{::entity/options
 {:class [{::entity/key :fighter
           ::entity/options
           {:levels [{::entity/key :level-1} ...]
            :martial-archetype {::entity/key :champion}  ;; single-select under class
            }}]
  :skill-profs [{::entity/key :arcana} {::entity/key :deception}]}}
```

### What determines single vs multi?

`template.cljc:selection-cfg` — if `::max` is 1 (or nil + not multiselect), single-select → map.
If `::max > 1` or `::multiselect? true` → vector.

## Path Generation via traverse-nested

`common/traverse-nested` walks the options tree. For vectors it adds the index:
- Map value: `(f v (conj path k))` → `[:race]`
- Vector value: `(f item (conj path k idx))` → `[:class 0]`

This means **paths for multi-select options always contain integer indices**.
Any code matching on paths must account for this (strip indices or match flexibly).

## Namespace Load Order (core.cljs)

```
core.cljs requires (in order):
  1. character-builder
  2. subs
  3. equipment-subs     ← registers ::char5e/template and equipment subs
  4. events             ← requires autosave-fx (which uses ::char5e/template)
  5. views, views-2, conflict-resolution
  6. route-map, cljs-http, etc.
```

**Critical invariant:** equipment-subs loads BEFORE events. This guarantees
all subscription handlers are registered before autosave-fx's `defonce` runs.

**Don't add equipment-subs as a require in autosave-fx** — it pulls the entire
subscription dependency tree into the events.cljs load chain, changing namespace
initialization order and causing content blowout on page refresh.

## autosave-fx Template Cache

```clojure
(defonce _init-template-cache
  (js/setTimeout
    (fn [] (r/track! (fn [] (when-let [template @(subscribe [...])] ...))))
    0))
```

Why `setTimeout 0` is safe:
1. core.cljs load order guarantees the sub is registered before this file loads
2. `setTimeout 0` defers to next event loop tick — after ALL synchronous loading
3. `when-let` nil-guards if timing is off; `r/track!` re-fires reactively
4. `defonce` prevents double-init on hot reload

## Content Source: SRD vs Plugins

Only SRD content is hardcoded. Most PHB content comes from orcbrew plugins.

| Hardcoded (SRD) | From plugins |
|---|---|
| 12 base classes | Plugin classes (e.g., homebrew Artificer) |
| 9 races + subraces | Plugin races |
| 1 subclass per class (Champion, Berserker, Lore, Life, Land, Open Hand, Devotion, Hunter, Thief, Draconic, Fiend, Evocation) | All other subclasses (Battle Master, Totem Warrior, etc.) |
| Acolyte background | All other backgrounds (Folk Hero, Sage, etc.) |
| No feats | All feats |

Non-SRD subclasses in `classes.cljc` are `#_` reader-discarded — they come from plugins.

**Subscription patterns:**
- `plugin-*` subs = only plugin content
- Full subs (e.g., `::classes5e/classes`) = hardcoded + plugin
- `available-content` subscription uses `plugin-*` subs, paired with builtin exclusion sets for SRD content

## Key Files

| File | Role |
|---|---|
| `template.cljc` | `selection-cfg`, `option-cfg` — defines single/multi-select |
| `entity.cljc` | `::entity/options`, `::entity/key` — entity structure |
| `common.cljc` | `traverse-nested` — walks option trees |
| `spell_subs.cljs` | All content subscriptions (classes, races, backgrounds, feats, etc.) |
| `equipment_subs.cljs` | Equipment subs + `::char5e/template` registration |
| `autosave_fx.cljs` | Throttled save + template cache |
| `content_reconciliation.cljs` | Missing content detection |
| `subs.cljs` | `available-content`, `missing-content-report` subs |
| `core.cljs` | App entry, namespace load order |
