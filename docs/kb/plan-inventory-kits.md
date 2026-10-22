# Plan: inventory, kits and containers

Agreed with the owner 2026-10-20/22, in conversation. Nothing is built yet. Branch
`feature/inventory-kits`, cut from `integration` at `d51938c1` (2026-10-22), worktree `orcpub-inventory`.
Evidence for the problems: [equipment-parity.md](equipment-parity.md), "Gear, packs and quantities:
seen in the app"; [entity-options-architecture.md](entity-options-architecture.md), "Equipment
Storage: Map vs Vector".

## The problems today

- **Packs cannot be managed.** Choosing Explorer's Pack stores only the choice; its contents are
  recomputed on every build (`equipment-option`, `options.cljc`; `modifiers/equipment`), so the sheet
  lists them but nothing can edit, count down or remove them.
- **Same-key items overwrite.** Equipment is stored as a map keyed by item (`sequential? false`,
  `template.cljc`, `inventory-selection`), so a hand-picked Arrow (1) replaces the starting 20.
  Upstream #138 (pack items duplicating other items vanish), #340 (identical weapons shown as one),
  #229 (quantity on the view screen).
- **Pack rations have no name.** Packs ask for `:rations-1-day-`; the item's key is `:rations-1-day`
  since `0790b3cd`. Five packs. A contained fix, separate from this plan.
- **The picker starts every item at 1** and nothing reads `:sell-qty` / `:sell-container`.

## The model

```clojure
{:containers
 [{:id :backpack :place :carried :on? true                ; don/doff
   :items {:torch 13 :bedroll 2 :amulet 1 :lock-pick 5}     ; what you own: one total per item
   :kits  [{:id :kit-1 :name "Explorer's Pack"
            :items {:torch   {:held 3  :target 10}
                    :bedroll {:held 1  :target 1}
                    :amulet  {:held 1  :target 1 :choice-of :holy-symbol}}}
           {:id :kit-2 :name "Burglar's Pack"
            :items {:torch     {:held 10 :target 10}
                    :bedroll   {:held 1  :target 1}
                    :lock-pick {:held 1  :target 1}}}]}
  {:id :camp :place :not-carried :items {} :kits []}
  {:id :mule :place :not-carried :items {} :kits []}]}
```

- **Stored:** each container's item totals, and each kit's `:held` / `:target` per item. Named fields,
  one shape for every entry (no short form for full items: the display hides "1/1", the data does not).
- **Computed, never stored:** leftovers (total minus every kit's `:held`), the global alphabetical
  list (every container summed, each line tagged with where it is), kit counters ("Torches 3/10").
- **The rule:** kits' `:held` for an item never exceed the container's total for it.
- **A kit is a shorthand, not a container.** It owns no items; deleting every kit leaves a normal
  inventory. A pack is a kit created by choosing the pack: its recipe fills the targets and adds the
  items, and the pack itself is not stored as an item.

## The actions

| Action | Effect |
|---|---|
| Buy | container total up; it lands in leftovers |
| Choose a pack | adds its items to the total, creates the kit with `:held` = `:target`; asks any choice (which holy symbol) |
| Refill (free, "reorganize") | the kit clicked claims from leftovers up to its targets |
| Restock (paid) | if still short, asks "buy 7 torches? (7 cp)"; raises total and `:held` |
| Use | kits give up top-down, in kit order |
| Sell or lose | leftovers first, then kits top-down |
| Delete a kit | `:held` gone, totals unchanged: its items become leftovers |
| Move a kit to camp | its `:held` items move with it to that container |
| Doff the backpack | a flag: items greyed under "not carried", still counted |
| Change a choice | reopen that kit's picker; swaps that one item |

Order only matters for using items. Refill goes to whichever kit is clicked, which is why `:held` is
stored: the same 13 torches split 10/3 or 3/10 depending on refill history.

## Phases

1. **Storage, part A: gear. Sum at build; no data conversion** (revised 2026-10-22 from the map
   below). Stored picks are already one entry per item; the overwrite happens when the sheet is built,
   because every source writes its quantity with `map-mod`, a plain `assoc`. Building the equipment
   maps by adding quantities instead fixes the overwrite (20 starting arrows + 1 picked = 21) and #138,
   and leaves saved characters untouched. Check first that no item legitimately arrives twice for the
   same reason (a stored class starting item and its own modifier), or summing would double it.
   **Built 2026-10-22** on `feature/inventory-kits` (`4c549456`, `ab76e660`, `ce16a329`): packs' rations
   use `:rations-1-day`; `modifiers/equipment` and `deferred-equipment` add with `add-item-cfg`
   (summed quantity, equipped if either source is, today's behaviour when a quantity is missing);
   `equipment_totals_test` (rations 11, arrows 21, torches stay 10, every pack item real) seen failing
   first. No class or background emits gear from its own modifiers, so no source is counted twice.
   In the app: Arrow 21 and named rations seen; adding a ration by hand was not driven in the browser.
   Also fixed: the sheet's magic items list now includes magic armor (it read magic items twice).
   Treasure (coins) still replaces rather than adds; not in part A.
   **Part B: weapons and armor**, on the same structure, only after part A is proven in use (owner,
   2026-10-22). Fixes #340 (two handaxes shown as one; the reporter asks for a count).
   **Part B built 2026-10-22** (owner moved it ahead of "proven in use"): `weapon`, `magic-weapon`,
   `armor` and their deferred forms add with `add-item-cfg` (`ff6500da9`). Two Handaxes plus one picked
   = 3, Chain Mail plus one picked = 2, seen failing first. 186 tests, the ring-AC and SRD-equipment
   browser tests pass. The sheet's weapons table shows no count yet: that display is
   `feature/weapons-table`'s (#340 asks to see the count).
   **Then #229** (edit quantities on the character sheet, not only in the builder): wanted, design to
   be worked out when it is reached.
2. **Packs as kits.** Starting-equipment pack choices and the picker create kits; counters, refill,
   restock, use order, delete, choices. This is where stored data changes: existing characters' chosen
   packs become kits by a repair on read inside `char5e/from-strict`, idempotent like
   `vectorize-equipment` (the precedent), never a migration pass and never a stored "done" marker
   ([data-safety-layers.md](data-safety-layers.md): heal lazily; save is a subset of load).
3. **Containers.** Don/doff; Not Carried places (camp, mount, wagon, ship); moving kits; grouped and
   alphabetical views; the PDF lists each place under its own header.
4. **Homebrew kits.** A content type: name, cost, container, items with targets, choices from a pool
   (the pool + grant layer, [content-extensibility-framework.md](content-extensibility-framework.md)),
   able to list custom items.

Later, separate items: carrying capacity and encumbrance (the mule's `:carrying-capacity` exists);
paying for restocks from coins; ammunition starting at its bundle from the picker.

## The map: where equipment is written, read and saved (2026-10-22, `integration` `d51938c1`)

Three read-only searches; the load-bearing claims re-checked by hand.

**Written.** Picks are a vector at `[::entity/options <sel>]`, one entry per item key, for `:weapons
:armor :equipment :treasure :magic-weapons :magic-armor :other-magic-items` (`equipment-keys`,
`character.cljc:69`): `{::entity/key k ::entity/value {::quantity n ::equipped? b}}`, with
`::class-starting-equipment?` / `::background-starting-equipment?` on starting items. Writers:
`add-inventory-item` (`event_handlers.cljc:69`, no duplicate check); `:remove-inventory-item`,
`:toggle-inventory-item-equipped`, `:change-inventory-item-quantity` (events.cljs ~3171-3231; changing a
quantity deliberately drops the starting flags, so an edited item survives a class change);
`use-class-starting-equipment` (`character.cljc:912`); `add-background-starting-equipment`
(`event_handlers.cljc:46`). Custom gear is separate: vectors of `{::name ::quantity ::equipped?}` at
`[::entity/values ::char5e/custom-equipment]` and `custom-treasure`. Computed every build, never
stored: starting-equipment option modifiers (`equipment-option` and `equipment-grant->modifier`,
options.cljc), packs' `:items` included.

**Built and read.** All sources meet in `?equipment ?weapons ?armor ?magic-weapons ?magic-armor
?magic-items ?treasure` via `map-mod` (`entity_spec.cljc:110`, `assoc`: last write wins). Read by the
accessors in `character.cljc:480-513`, the subs in `subs.cljs:998-1175` (armor combo, carried
filters on `::equipped?`), the sheet (`other-equipment-section-2`, `treasure-section` show quantity;
armor and weapons tables do not), and the PDF (`equipment-fields`, `pdf_spec.cljc:168`, prints
"Name xN" and unequipped items as treasure). AC and wield picks do not read quantities. The builder's
own list reads the raw picks, not the built maps.

**Saved and moved.** Server save stores the strict form (`to-strict`, `::se/entity` checked,
`routes.clj:1971`); reads return the raw pull and never rewrite. Browser drafts store strict data
(`db.cljs:215`), restored with a `::se/entity` check. Shared links carry homebrew only; the character
loads by id. No character file import or export exists. Every load runs `char5e/from-strict`
(`vectorize-equipment` converts the old map shape, idempotently) and `reconciled-character`
(heals renamed keys). New stored fields need Datomic schema attributes and must fit `::se/entity`.

**Found in passing (on the sheet, `views.cljs`):** the weapons table destructures `equipped?`
unqualified (~3902), so it never matches; `magic-items-section-2` subscribes `::char/magic-items`
where `magic-armor` is meant (~3983).

## Patterns to reuse

- Stable identity from existing keys, never generated ids
  ([growable-option-menus.md](growable-option-menus.md), `menu-id`).
- Starting equipment's grant-and-choose shape for a pack's choices
  ([starting-equipment.md](starting-equipment.md)).

## Open (owner)

1. Using an item when every kit is empty: take from leftovers automatically, or ask first?
2. Inventory order: grouped by container by default, alphabetical as an option?
3. PDF: leave doffed and stored items off, or list them under each place's header?
