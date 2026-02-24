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
all subscription handlers are registered before autosave-fx is initialized.

**Don't add equipment-subs as a require in autosave-fx** — it pulls the entire
subscription dependency tree into the events.cljs load chain, changing namespace
initialization order and causing content blowout on page refresh.

After all requires complete, `core.cljs` calls `(autosave-fx/init-template-cache!)`
explicitly - no `defonce` or `setTimeout` needed.

## autosave-fx Template Cache

The template cache is initialized by an explicit function call from core.cljs,
after all subscriptions are registered:

```clojure
;; autosave_fx.cljs
(defn init-template-cache! []
  (r/track!
    (fn []
      (when-let [sub (subscribe [::char5e/template])]
        (when-let [template @sub]
          (dispatch [::cache-template template]))))))

;; core.cljs (after dispatch-sync [:initialize-db])
(autosave-fx/init-template-cache!)
```

Key design decisions:
1. No `defonce` or `setTimeout 0` - explicit call after all requires have loaded
2. Guards the subscribe call itself: `(when-let [sub (subscribe [...])]` prevents
   `@nil` crash (subscribe returns nil if handler isn't registered yet)
3. `r/track!` creates reactive context, re-fires when subscription value changes
4. **`when-let` does NOT guard `@nil`** - `@(subscribe [...])` evaluates deref
   BEFORE `when-let` checks. Always guard subscribe itself, not the deref.

## Content Source: SRD vs Plugins

Only SRD content is hardcoded. Most PHB content comes from orcbrew plugins.

| Hardcoded (SRD) | From plugins |
|---|---|
| 12 base classes | Plugin classes (e.g., homebrew Artificer) |
| 9 races + subraces | Plugin races |
| 1 subclass per class (Champion, Berserker, Lore, Life, Land, Open Hand, Devotion, Hunter, Thief, Draconic, Fiend, Evocation) | All other subclasses (Battle Master, Totem Warrior, etc.) |
| Acolyte background | All other backgrounds (Folk Hero, Sage, etc.) |
| Grappler (only SRD feat) | All other feats |

Non-SRD subclasses in `classes.cljc` are `#_` reader-discarded — they come from plugins.

**Subscription patterns:**
- `plugin-*` subs = only plugin content
- Full subs (e.g., `::classes5e/classes`) = hardcoded + plugin
- `available-content` subscription uses `plugin-*` subs, paired with builtin exclusion sets for SRD content

## Feat Storage — Two Locations

Feats appear in TWO places in the entity tree:

### 1. Top-level `:feats` selection (multi-select vector)
Standalone feats chosen outside of class level-ups:
```clojure
{::entity/options
 {:feats [{::entity/key :blade-mastery}
          {::entity/key :brawny}
          {::entity/key :metabolic-control
           ::entity/options
           {:asi [{::entity/key :orcpub.dnd.e5.character/con}]}}]}}
```
Path to feat key: `[:feats 0]` → kw-path `[:feats]`
Path to ability score UNDER feat: `[:feats 2 :asi 0]` → kw-path `[:feats :asi]`

### 2. Class-level `:asi-or-feat` (single-select under level)
At ASI levels (4, 8, 12, 16, 19), the character chooses between ability score
improvement and a feat. Choosing feat stores `:feat` as the option key:
```clojure
{::entity/options
 {:class [{::entity/key :dragon-knight
           ::entity/options
           {:levels [{::entity/key :level-4
                      ::entity/options
                      {:asi-or-feat {::entity/key :feat}}}]}}]}}
```
Path: `[:class 0 :levels 3 :asi-or-feat]` → kw-path `[:class :levels :asi-or-feat]`
The actual feat selected is stored in the top-level `:feats` vector (ref pattern).

### Content reconciliation detection

Feats are identified by `:feats` being the LAST keyword in kw-path (direct child).
Sub-selections under feats (ability scores, language choices, etc.) have kw-paths
like `[:feats :asi]` where last = `:asi` → NOT classified as feat.

**Bug found and fixed:** `(last (butlast kw-path))` matched grandchildren (ability
scores under feats) instead of the feats themselves. Correct check: `(last kw-path)`.

## Test Character (Datomic entity 17592186045779)

Dragon Knight character using `exported.orcbrew` test data:
- **Race:** `:tegokka` (from Rain-Junkie plugin)
- **Class:** `:dragon-knight` (from Rain-Junkie_DragonKnight plugin)
- **Background:** `:folk-hero` (from Player's Handbook plugin)
- **Feats:** `:blade-mastery`, `:brawny`, `:metabolic-control-...`
  - Metabolic Control has nested `:asi` sub-selection with `:orcpub.dnd.e5.character/con`
- **ASI-or-feat at levels 4, 8, 12:** all chose `:feat`

Datomic URI: `datomic:dev://localhost:4334/orcpub`
Test orcbrew: `logs/exported.orcbrew` (69 plugins, 84 feats, BOM-prefixed)

## Equipment Storage: Map vs Vector (`sequential?`)

The `sequential?` flag on a `selection-cfg` determines storage type:
- `sequential? false` → **map** keyed by option keyword (uses `map-mod`)
- `sequential? true` → **vector** of items (uses `vec-mod`)

Verified at `template.cljc:1268`: weapons/equipment use `sequential? false`.

### Impact: duplicate items overwrite

Map storage means two items with the same key silently overwrite:

```clojure
;; Two longswords — second overwrites first:
{:longsword {:equipped? true}}

;; Two packs with rations — Dungeoneer's (10) overwrites Burglar's (5):
{:rations-1-day- {:quantity 10}}
```

Confirmed root cause of #340 (duplicate weapons), #138 (pack item overlap),
#229 (quantity display).

### Modifier types (`modifiers.cljc`)

- `map-mod` (line 464) — stores `{item-kw → config}`, current for weapons
- `vec-mod` — stores `[{:key item-kw ...} ...]`, needed for per-instance identity

### Quantity field exists but is underused

`character/equipment.cljc:5-25` — `::quantity nat-int?` is in the spec.
`views.cljs:2111-2119` has commented-out display code with `"x " item-qty`.
Weapons view (`views.cljs:2834-2878`) has no quantity column.

### Subscription merge assumes maps

`subs.cljs:849-855` — `(merge magic-weapons weapons)` uses standard map
merge. If both maps contain `:longsword`, right side wins.

## Selection Nesting: Template vs Builder Gap

### Verified: template layer supports arbitrary nesting

`template.cljc` — `option-cfg` accepts `:selections` parameter. Built-in
content uses this heavily:

- Sailor background (`template.cljc:665`) — option contains nested selection
- Half-Elf Sword Coast (`template.cljc:1074-1105`) — 3+ levels deep
- Tiefling Sword Coast (`template.cljc:1128-1159`) — nested ability + feature selections

### Verified: homebrew builder doesn't expose nesting

`views/builders.cljs:2545-2629` — selection builder option UI only has:
- `:name` (text input)
- `:description` (textarea)

The homebrew option spec (`selections.cljc:24-25`):
```clojure
::option (spec/keys :req-un [::name]
                    :opt-un [::description])
```

No `:selections` key. Users cannot create nested selections via homebrew
builder despite the template system fully supporting them.

Confirmed root cause of #260 (nesting in class builder), #486 (nested
classes/items), #174 (selection builder problems).

---

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
