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

1. **Storage, part A: gear.** Item totals per container (backpack only), replacing the keyed-map
   selections; picking an item you already have adds to it. Converts saved characters once, on every
   load path (server, browser draft, shared link, import), marked done so it never runs twice. Fixes the
   overwrite and #138 (pack rations plus added rations show 10, not 11). Biggest risk; tested against
   real saved characters.
   **Part B: weapons and armor**, on the same structure, only after part A is proven in use (owner,
   2026-10-22). Fixes #340 (two handaxes shown as one; the reporter asks for a count).
   **Then #229** (edit quantities on the character sheet, not only in the builder): wanted, design to
   be worked out when it is reached.
2. **Packs as kits.** Starting-equipment pack choices and the picker create kits; counters, refill,
   restock, use order, delete, choices. Existing characters' chosen packs become kits in the phase 1
   conversion.
3. **Containers.** Don/doff; Not Carried places (camp, mount, wagon, ship); moving kits; grouped and
   alphabetical views; the PDF lists each place under its own header.
4. **Homebrew kits.** A content type: name, cost, container, items with targets, choices from a pool
   (the pool + grant layer, [content-extensibility-framework.md](content-extensibility-framework.md)),
   able to list custom items.

Later, separate items: carrying capacity and encumbrance (the mule's `:carrying-capacity` exists);
paying for restocks from coins; ammunition starting at its bundle from the picker.

## Patterns to reuse

- Stable identity from existing keys, never generated ids
  ([growable-option-menus.md](growable-option-menus.md), `menu-id`).
- Starting equipment's grant-and-choose shape for a pack's choices
  ([starting-equipment.md](starting-equipment.md)).

## Open (owner)

1. Using an item when every kit is empty: take from leftovers automatically, or ask first?
2. Inventory order: grouped by container by default, alphabetical as an option?
3. PDF: leave doffed and stored items off, or list them under each place's header?
