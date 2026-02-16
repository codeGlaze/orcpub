# OrcPub Codebase Knowledge

> Living document. Update as understanding evolves.

---

## Magic Item Builder — Serialization Pipeline

The custom item builder uses a multi-layer pipeline. Understanding this prevents a class of bugs where values appear in the UI but silently disappear on save.

### Data Flow

```
reset-item (sets defaults in app-db)
    |
    v
UI dropdowns/checkboxes (dispatch events that update app-db)
    |
    v
from-internal-item (serializes app-db → transit payload)
    |-- select-keys (whitelist — unlisted keys are dropped)
    |-- entity/remove-empty-fields (nil/empty values stripped)
    |
    v
HTTP POST to server → Datomic transact
    |
    v
item-save-success (must update BOTH builder-item AND custom-items list)
```

### Critical Gotcha: Namespace-Qualified Keys

Clojure's `::keyword` syntax expands to the *current namespace*. A key like `::damage-die` in `magic_items.cljc` becomes `:orcpub.dnd.e5.magic-items/damage-die`, which is **different** from `::weapons5e/damage-die` (= `:orcpub.dnd.e5.weapons/damage-die`).

The subscriptions, event handlers, and `from-internal-item` all use `::weapons5e/*` keys. If a function in `magic_items.cljc` writes to `::damage-die` instead of `::weapons5e/damage-die`, the value goes to the wrong key and gets silently dropped by `select-keys`.

**Rule**: Always use the fully-qualified alias (`::weapons5e/foo`) for weapon properties, never the bare `::foo` form, in any namespace other than `weapons.cljc` itself.

### Boolean Weapon Toggle Pattern (6 layers)

Every boolean weapon property (finesse?, heavy?, light?, special?, loading?, etc.) follows the same pattern across 6 files:

| Layer | File | Example |
|-------|------|---------|
| **Subscription** | `subs.cljs` | `(reg-sub ::mi5e/item-ammunition? :<- [::mi5e/builder-item] (fn [item _] (get item ::weapon5e/ammunition?)))` |
| **Event** | `events.cljs` | `(reg-event-db ::mi/toggle-item-ammunition? item-interceptors (fn [item _] (update item ::weapons/ammunition? not)))` |
| **UI** | `views.cljs` | `[:div {:on-click (make-event-handler ::mi/toggle-item-ammunition?)} [labeled-checkbox "Ammunition?" @(subscribe [::mi/item-ammunition?])]]` |
| **Strip on reset** | `magic_items.cljc` | Add to `remove-custom-weapon-fields` dissoc list |
| **Serialize** | `magic_items.cljc` | Add to `from-internal-item` select-keys whitelist |
| **DB Schema** | `schema.clj` | Add to `bool-prop-no-history` vector |

To add a new boolean weapon property, replicate all 6 layers. Missing any one causes silent data loss.

---

## Learnings

- [2026-02-16] `apply-subtype-toggle` must set defaults using `::weapons5e/*` keys, not `::mi/*` keys. The original implementation wrote to the wrong namespace, causing all custom weapon defaults to be dropped on save. Round-trip tests (`apply-subtype-toggle` -> `from-internal-item` -> assert) catch this class of bug.

- [2026-02-16] `item-save-success` must update both `::mi/builder-item` (editor state) and `::mi/custom-items` (list display state). Without updating custom-items, saved items only appear after a full page reload.

- [2026-02-16] The `dropdown` component renders `(or value "")` — so the UI shows the first option visually even when the underlying value is `nil`. This means `reset-item` defaults are essential; relying on the UI display to imply a value is a bug.
