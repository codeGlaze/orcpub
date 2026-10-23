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
| Sell or lose | kits top-down, then leftovers (layout review round 1) |
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

## Phase 2 design (2026-10-22, before code)

**Storage (decided 2026-10-22: no schema change).** Kits are picks in the generic structure every
choice already uses, so they need no new Datomic attributes and a rollback loses nothing:

```clojure
[::entity/options :kits]
[{::entity/key :explorers-pack                                   ; one kit per pack, in kit order
  ::entity/options {:held [{::entity/key :torch ::entity/value 7}   ; stored as int-value
                           {::entity/key :rations-1-day ::entity/value 4}]}}]
```

Targets are read from the pack definition (`equipment5e/packs` `:items`), never stored. `:kits` joins
`picks/structural` so the Choices list does not report a kit as a missing choice (it lists every stored
pick and flags any the template does not offer). The held-pick hold (`disqualified`, `overflow`) only
looks at selections the template offers, so it never holds a kit.

**Choosing a pack makes it real.** The pack option stops emitting its contents as build-time
modifiers. Choosing it (builder event, and the picker) adds each item to the stored Equipment picks,
**adding to an existing pick's quantity** (not `add-associated-options`, which drops a key already
held: the pack's torches would vanish for a character who owns torches), and records the kit with
`held` = `target`. Pack contents then show in the builder as ordinary picks: editable, removable.

**Existing characters: repair on read**, inside `reconciled-character` (it runs on every load path:
`set-character`, `character-updated`, the by-id sub). If a pack choice is selected and no kit has that
`source`, materialize it as above. Idempotent: the kit's presence is the marker. Kept by the user's
next save, like the other heals. Packs stored as a fixed class item (`:equipment {:explorers-pack 1}`,
`classes.cljc:69`, `:785`) and packs granted through `:equipment-selections` appear, by reading the code (not yet seen on screen), as one
"Explorer's Pack" line, unexpanded: the same repair covers them.

**Slices.**
- **2a, no new UI:** schema, kit record, choose-to-materialize, repair on read. Visible result: a pack's
  contents become ordinary editable picks (the original complaint). Tests: a fresh choice, an existing
  character repaired once and only once, a character who already owns torches keeps both counts.
- **2b, the kit panel:** counters, Refill (free), Restock (paid, asks), delete, change a choice. Built
  only after a phone-width mockup is reviewed (ux-guide.md).
- **2c, using items:** top-down use order, and #229 (quantities edited on the sheet).

**Decided before 2a (owner, 2026-10-22):**
1. **Class change:** when the pack choice disappears, ask the player "Remove what the Explorer's Pack
   added?", removing up to what the kit still holds; nothing goes silently.
2. **A pack's own choice** (the Priest's and Monster Hunter's "holy symbol"): on repair, scan the
   character's items. Exactly one holy symbol (amulet, emblem or reliquary) owned: that is the choice.
   Several: ask. None: add the generic "Holy symbol" to pick later.

## Built: 2a wiring and 2b kit panel (2026-10-22, `feature/inventory-kits`)

**Wiring.** A pack option and a pack's Equipment pick build no modifiers (`options.cljc`
`equipment-option`, `template.cljc` `inventory-selection`); its items exist only as the picks the kit
made. `kits/repair` runs in `reconciled-character` on every load, and after any edit that moves the
character's Equipment picks or classes, as the follow-up event `::kits/repair` queued by the
`queue-kit-repair` interceptor (never inside the edit). A decrease typed into a gear row is taken
from the kits top first (`change-inventory-item-quantity-with-kits`); removing a row clears it from
every kit.

**Where a kit comes from (changed from 2a).** A pack stored as an Equipment pick is no longer
replaced by its items: the pick stays, hidden from the builder's list, as the kit's source. So Add a
kit, the item picker and a class's fixed pack all work one way, and a kit whose pack is no longer
chosen (class change, the pick removed) is detectable: it asks "Remove what the Explorer's Pack
added?" (Remove takes off what it still holds; Keep re-adds the pick as its source). Remove kit on a
pack chosen in starting equipment leaves a marker (`:removed`) so repair does not make it again; the
marker is dropped once the pack is no longer chosen.

**Holy symbol (changed from the decided rule).** The generic "Holy Symbol" is a group, not an
Equipment item: a pick of it builds nothing and the Choices list flags it. So when none is owned
nothing is added and the kit asks "Which holy symbol for the ...?" with Amulet, Emblem and Reliquary;
several owned asks among those. Exactly one owned is claimed, as decided. Confirmed by the owner 2026-10-22, as is the orphan prompt's wording (Remove / Keep).

**Panel** (layout agent's 5a with the round 1 rulings): Kits header, "Spending uses the top kit
first" at two kits or more, Add a kit (pack picker, "Every pack is already a kit."), the dashed empty
slot, kit rows (low targets orange `Torch 7/10`, full ones muted), Restock only when low, asking in a box under the kit
"You have 3 spare torches. Use them first?" (Yes / No / Cancel; owner, 2026-10-22; Reorganize
hides while it is open) when spare stock could fill it, tap a kit for Use first and Remove kit
(keep items), the Reorganize line, and one Undo for Restock, Reorganize and Add a kit (cleared by any
other edit). Gear rows a kit tracks get the share line (`Explorer's 7/10 · 3 spare`). Restock costs
nothing yet: paying from coins is later. Styles: `.kit-*` in `styles/core.clj`, coloured from
`currentColor` so every theme draws them.

## Reuse audit (2026-10-23, before more kit work)

Read against `feature/inventory-kits` `f2e7f7d2`. What the kit code built that the app already has:

| Kit code | Existing code that does the job | Verdict |
|---|---|---|
| `.kit-well` boxes: Reorganize, Restock's question, the orphan and holy-symbol questions, the Undo line | `notifications/callout` (text, link actions, `:compact?` with the invisible 44px tap layer, `:tone :note`) | Replace; the spacing fix under review is what `callout-compact` already does |
| `kit-undo` r/atom | `::e5/restore-notice` (db state, Undo/Redo in a compact callout, `picks-notice`) | Follow that pattern: db, not a view atom |
| "Add a kit" pack picker, `.kit-pack-option` | The Equipment "Filter or browse" combobox already lists every pack, and picking one makes the kit | Drop the second picker; the empty slot can open the combobox |
| `choice-groups` | `equipment5e/equipment-map` `:holy-symbol {:values ...}`; `options.cljc` `equipment-group-choosers` | Read the existing group data |
| `kits/pluralize`, `count-label` | `display.cljc` `unit-amount-description` + `plural-map` | Reuse for the plurals; containers ("flasks of oil") are new |
| `kits/add-item`, `remove-items`, `pick-qty` | `event-handlers/add-inventory-item` (no summing), `change-inventory-item-quantity` | One inventory API in `event_handlers.cljc` that sums, used by the picker and kits |
| `queue-kit-repair` interceptor | `queue-settle` -> `::e5/settle-picks`, the existing after-the-edit step | Run the kit repair as a step of settle, not a second follow-up |
| Orphan question, `:removed` marker | Starting items carry `::class-starting-equipment?`; `remove-starting-equipment` drops them on a class change, and an edited quantity drops the flag so it survives | Flag a pack's items the same way; the owner's "ask first" becomes a choice about that one existing path |

New and kept: the kit record (`held`, targets from the pack), and the rules on it (refill, restock,
top-kit-first, the share line).

## Reuse refactor done (2026-10-23, `83ac1bb3`)

One inventory home: rules in `orcpub.dnd.e5.inventory` (was `kits.cljc`), the builder's inventory
lists and the Kits block in `orcpub.dnd.e5.views.inventory` (moved out of `character_builder.cljs`,
3,072 to 2,501 lines). Containers and other inventory code go in these two, never new files (owner,
2026-10-23). Kit notices are `notifications/callout`; Undo is `::inventory/undo` in the db; the kit
repair is a step of `settle-picks-fx`; the equipment groups are `equipment5e/groups`, read by options
and inventory. `expand-button` and `show-info-button` moved to `orcpub.components`. "Add a kit" and the
empty slot now open the inventory search box with a preset (`inventory-combobox` `preset`: a label
and the item keys it lists, cleared by × or a pick), showing the packs not already kits; the separate
pack list is gone (owner, 2026-10-23). The preset is a general option of the search box, for any
inventory list.

**Found while checking it:** a new character starts as a Barbarian, whose fixed starting equipment
includes an Explorer's Pack. Settle runs on opening, so that pack becomes a kit at once; switching
class then asks "Remove what the Explorer's Pack added?" on a character the player has not touched.
Owner's rule (2026-10-23): don't bother players with questions they did not earn. So a class change
asks only about a kit the player has used (`inventory/untouched?` is false) whose pack the new class
neither grants nor offers through a pack choice still open (`offered-packs`, `open-offer`). An
untouched kit whose pack is gone takes its items off quietly in settle; a kit the new class offers
stays. A new Barbarian switched to Fighter asks nothing.
The shared callout puts its actions beside the text, not under it.

## Layout review, round 1 (2026-10-22, owner)

The layout agent's "5a Kits, quieter" (tweaking_Kits_v1) is the base, with these rulings:

- **Decreases take from the top kit first; spares last.** Kits then show what is low, so Restock stays a
  useful shortcut. (Supersedes "sell or lose: leftovers first" in "The actions" above, which got the
  owner's rule backwards.)
- **Both buttons:** each kit has its own refill button (fills that kit), and one Reorganize button for
  the whole backpack inventory (fills every kit in order).
- **The empty-slot look stays:** a dashed tile with a faint icon when no pack is chosen, not only an
  "Add a kit" link.
- **What a kit shows:** every item whose target is more than 1, plus any item below its target (a lost
  bedroll). No "used up" flag on items.
- **Live buttons unchanged:** the light theme keeps its blue `.form-button`; amber in the drawing was a
  shortcut.
- **The drawing's inventory rows are not the app's:** the real rows (checkbox box, "Filter or browse"
  search) stay unchanged, as the agent's own handoff says.

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
