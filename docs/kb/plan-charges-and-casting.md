# Charges, rests and casting from items: one plan

How the Rest box ([handoff-rest-controls.md](handoff-rest-controls.md)), item charges and casting a
spell from an item fit together, what the app already has for each, and the build order. Written
2026-10-23 on `feature/weapons-table` at the owner's request to stop and plan before building.
Item scans below come from description searches and locate candidates only; each item is checked
by hand against its SRD text when its data is filled.

## What exists (verified)

| Piece | Where | State |
|---|---|---|
| Casting a spell from a slot, with an upcast level picker | `views.cljs` `cast-spell-component`: a "Cast at level" dropdown of usable slot levels, then "cast spell" dispatches `::char5e/use-spell-slot` | works; spends a slot, rolls nothing |
| Spell data, srd-2014 | `spells.cljc`: name, school, level, range, duration, components, casting time, description; 5 spells have `:attack-roll?` | damage, saves and upcasting exist only as description text (Fireball's 8d6 and "+1d6 per slot level above 3rd") |
| Spell damage and upcasting as data | planned in [srd-improvements.md](srd-improvements.md): damage **parts** (dice, type, label, each scaling on its own) and casting options per slot, generated on `srd52/develop` | not built |
| Weapon damage parts and a roll engine | this branch: `::mi/extra-damage` entries (dice, type, conditions), `weapon_rolls.cljc`, the result sheet | built |
| Uses of item powers | `::char5e/features-used` checkboxes per reset period | works; no counts |
| Items granting spells | none: no magic item adds a spell through `mod5e/spells-known` | 30 items cast spells in their text only |
| Item spell DC and attack | `mod5e/spell-save-dc-bonus` / `spell-attack-modifier-bonus` on staffs and robes | raises the wielder's own numbers |

30 SRD items cast spells (wands, staffs, rings, helms, Luck Blade, Wind Fan). About 12 name their
own DC ("save DC 15"); about 8 use the wielder's ("your spell save DC"); the rest need no save.

## The connecting idea

**A cast is one thing with a different payment.** Casting Fireball from a slot and from a Wand of
Fireballs are the same act: a spell, a level, a DC and attack bonus, and something spent. Today's
`cast-spell-component` already does spell + level picker + spend (a slot). Item casting reuses that
shape with charges as the payment and the item's DC where it has one. The result is the same result
card for both.

**Damage parts are one vocabulary.** The spell plan in srd-improvements.md (dice, type, label, own
scaling) is the shape of this branch's `::mi/extra-damage`. When spells gain damage parts, a cast
rolls through `weapon_rolls.cljc` and lands in the same result sheet as a weapon attack, with no
second engine.

## Data

On the item (code data, and the item builder; builder items need schema, see below):

| Field | Exists? | Values |
|---|---|---|
| `::mi/charges` | specced, unused | maximum (a number); or a rolled start ("1d8+1") set per carried item |
| `::mi/charges-recharge` | specced, unused | `::units5e/short-rest`, `long-rest`, `dawn`, `dusk`, none |
| `::mi/charges-regain` | new | number, dice string, or `:all` |
| `::mi/casts` | new | `[{::spell :fireball ::base-level 3 ::cost 1 ::per-level 1 ::save-dc 15}]`; `::per-level` nil for a fixed cost (staffs); `::save-dc` omitted means the wielder's |
| `::mi/charge-cost` | exists | a weapon power's cost, drawn from the same pool |

On the character, per carried item (saved, so schema): charges used per item in a stack, and the
rolled maximum where the item starts with a roll. A once-per-dawn power is a pool of 1.

## What the player sees

- **On the item** (Other Magic Items, magic weapon rows): pips or "21/36" with − and +; for each
  spell it casts, a Cast button opening the level picker priced in charges ("3rd · 1", "4th · 2"),
  unaffordable levels greyed.
- **The result card:** "Fireball, 5th level, DC 15" with the spell text in the rule card
  (rule-preview-cards.md), Undo. When spells have damage parts: a Roll button that rolls them.
- **The Rest box** (handoff): short, long, dawn, dusk; regains rolled and named, type your own; Undo
  until the line is closed.
- **The Spells tab** (open): list item spells under the item ("Wand of Fireballs"), so a caster sees
  every way to cast in one place, or leave them on the item only.

## Build order

On `feature/weapons-table` (owner: "just finish building it here"):

1. **Reset periods:** add dusk (spec, schema, clear); Long Rest stops clearing dawn (flip the test);
   Dawn also clears per-day uses (`::units5e/day`, never cleared today: a bug). **Ships with the Rest
   box (step 5),** so Long Rest never stops clearing dawn before a Dawn control exists.
2. **Charges model: done `807b7d60`.** In the item domain (`magic_items.cljc`): `::charges`,
   `::charges-start` (dice), `::charges-regain` (a dice or number string; absent means all), `::casts`
   (`::cast-spell ::cast-level ::cast-cost`, optional `::cast-per-level ::save-dc`), and `charge-max`,
   `charges-left`, `spend-charges`, `regain-charges`, `roll-regain`, `roll-charge-max`, `cast-levels`.
   Per carried item (`character/equipment.cljc`): `::charges-used`, `::charges-max`, vectors by
   position in the stack. Dice: `dice/parse-dice`, `dice/roll-dice` (injectable die). Schema for the
   character fields comes with the first step that saves them. `item_charges_test.cljc`, every check
   seen failing. One value type per field: a regain is always a string.
3. **SRD data fill: done `e4dc229e`.** 46 charged items and 63 casts, by hand from each item's text;
   `::cast-max-level` added (Staff of Healing: cure wounds up to 4th). `magic_items_test.clj` checks
   the data against the text (count, dawn regain), that every cast names a real spell, the prices,
   and that no item stating charges is left without them; every check seen failing.
   **Left out on purpose:** the Ivory Goats figurine (one goat of three has charges; named in the
   test), Ring of Elemental Command's spells (they depend on the ring's element; charges are in),
   free casts (Staff of the Magi, Staff of the Woodlands: no charge), and charged effects that are not
   spells (Ring of the Ram, Staff of Striking: spend counts, no cast). Once-per-dawn weapon powers
   stay on `::mi/recharge` until step 4, where the + menu's "used" moves to the pool.
4. **Charges on the sheet: first part done.** `19cdb7e9`: charges save with the character as one
   component record per item in a stack, `::char-equip5e/charges` [{`charge-index` `charges-used`
   `charges-max`}] (`schema.clj`). **Not tuples:** a Datomic tuple needs at least 2 values, so a single
   wand's `[6]` is rejected ("Invalid tuple value"); padding was rejected too (the save path strips
   empty values, and a padded 0 reads as a real 0). Necklace of Fireballs beads added. `a7e27468`:
   Other Magic Items shows pips per item (tap lit to spend, dim to get back), a count with − and + over
   10, and roll-or-type for a dice start (`views.cljs` `item-charges`; events
   `::char5e/change-item-charges`, `::char5e/set-item-charges-max`); `item_charges_e2e.js`, 11 checks,
   each seen failing. **Done `62783957`:** weapon powers that cost charges (`::mi/charge-cost`: Staff
   of Power, Striking, Withering) are + menu items with cost and charges left (`disp/weapon-menu-items`
   `:cost`); picking spends from the first item in the stack that can pay (`mi/able-to-pay`); one it
   cannot pay is greyed (`:spent?`). **Changed from the plan:** once-per-period powers
   (`::mi/recharge`, Javelin of Lightning) stay on `::char5e/features-used`; that record already models
   "once per dawn", saves and resets, so turning them into pools of 1 would add risk with no visible
   change. Withering's Con save is not tied to the spend yet (an effect with a cost would be a second
   paid item).
5. **Rest box: done.** `d40aa2de` reset times: a long rest clears `[::long-rest ::rest]` only;
   `::dusk` added (spec, schema, names); `units/dawn-clears` `[::dawn ::day]` (once-per-day uses come
   back at dawn) and `dusk-clears`; Robe of Stars (6 stars, 1d6 at dusk). `71b4f433` the engine,
   `orcpub.dnd.e5.rests`: `carried`, `apply-actions` (rest and times; charges rolled or typed),
   `ask-time-of-day?`, `would-recharge`, `has-time-of-day?`. `8b528b9e` the box (`views.cljs`
   `rest-box`, first on Features; events `::char5e/take-rest`, `answer-time-of-day`,
   `set-rest-amount`, `undo-rest`, `close-rest-result`; the last action is kept in
   `::char5e/rest-result` and cleared on any route change, so Undo never reaches another
   character). `rest_box_e2e.js`, 16 checks, every claim seen failing. The Spells tab's old long-rest
   button stays (handoff D4) and no longer means dawn. **Not yet:** the floating button and Import log
   move, Dev mode (step 8); light theme not shot yet.
6. **Casting from items: done `dd160709`.** `views.cljs` `cast-panel` (spell picks for an item with
   several, levels priced from `mi/most-charges-left`, the item's DC or the highest
   `::char/spell-modifiers` DC, `mi/higher-levels-text`, Cast then the spell at that level with Undo),
   `cast-sheet` (phones), inline `tr.cast-row` (desktop), `cast-button` on Other Magic Items, and the
   Spells tab's `items-spells-section` (From Items, shown for non-casters too). **Built beyond the
   mockup:** an item with several spells (staffs) opens one "Cast" with a spell chooser; reviewed in
   the shots, not mocked. `item_casting_e2e.js`, 16 checks, each seen failing (two were weak, reading
   a count before the change landed, and were strengthened). No damage roll yet (spell data).
   **Not yet:** the Dev mode switch for the inline panel on phones (step 8).
7. **Item builder: done.** `fe6db87a` moves the builder fields (`dropdown`, `base-builder-field`,
   `number-field` and the rest) to `views/builder_fields.cljs`, no behaviour change. `cbd64b84` the
   Charges group, its own namespace `views/charges_builder.cljs`: charges (a number, or dice kept as
   a rolled start), recharge time, regain, and the spells it casts (spell, level, charges, + per
   level, up to level, DC). Under each spell, what it rolls, from the SRD spell data
   (`spell-rolls.edn`, loaded through `:load-srd :spell-rolls` when the group opens; built on
   `feature/srd-pipeline`, cherry-picked). Custom items save the fields: `schema.clj`
   `magic-item-schema`, `::mi/magic-item`, `from-internal-item`. Tests: `item_charges_save_test.clj`,
   `charges_builder_test.cljs`, every claim seen failing. **Not built:** a charge cost for weapon
   powers (`::mi/charge-cost`) in the builder; custom-item weapon powers are not editable at all yet.
   **Trap:** a namespace `views.item-charges` clashed with the var `views/item-charges` in compiled
   JS (a compile warning), hence the name `charges-builder`.
8. **Floating Rest button: done.** `ce250ecc` moves the Rest box group to `views/rest_controls.cljs`
   (no behaviour change). `8bbac32a` `rest-fab`: 56px, bottom-right, on every sheet tab but
   Features; a bottom sheet on phones, a popover elsewhere (its width fits the box: at the drawn
   330px the Long button was cut off); while the box asks about dawn or dusk the dimmed page does
   not close it. The Import log button sits at 84px whenever a sheet is on the page
   (`body:has(.rest-fab-slot)`). Developer mode (the footer switch, per device) gains "Open item
   casting under the item on phones" (`cast-inline-on-phones`, `cast-as-sheet?` in `views.cljs`).
   `rest_fab_e2e.js`, 13 checks, every claim seen failing. **Not built, on purpose:** the Import log
   dismiss control (the button already hides when its log is empty, which is what dismissing was
   for); the "Finish long rest" Dev mode setting (handoff section 10: it ships with that button's
   removal, D4, after the new Rest UI settles). There is no Settings page; Developer mode lives in
   the page footer.
   The Charges group was brought to the handoff's section 7 in the same commit ("Recharges at",
   76px and 96px fields, "all", the helper line); step 7 had been built before reading it.

On `srd52/develop`, separately: spell damage parts and casting options as data (srd-improvements.md).
Then a cast rolls damage through the weapon roll engine.

## Against open5e's data (checked 2026-10-23, clone at `~/projects/open5e-api`, `3d623fe3`)

open5e has **no** charge, recharge, regain or casting fields. `MagicItem` (`api_v2/models/item.py`)
holds: name, desc, category, rarity, `requires_attunement` + `attunement_detail`, size, weight, cost,
links to a base `weapon` (214 of 499 srd-2014 rows) or `armor` (36), and the item's **object** stats:
`armor_class`, `hit_points`, `hit_dice`, `damage_immunities` / `resistances` / `vulnerabilities`,
`nonmagical_attack_*`. srd-2024 (760 rows) has the same fields. Charges live only in `desc`, the same
as in our `magic_items.cljc`, so our fields add what neither source has.

**Trap when the magic items are processed:** `damage_immunities` is set on all 499 rows (poison,
psychic): that is the item as an object (objects are immune to poison and psychic damage), not
something it grants its bearer. Mapping it to a modifier would make every magic item grant immunity.

**Useful when processing:** the base-weapon and base-armor links (a +1 Battleaxe row per
`srd_battleaxe-1/-2/-3`) match how this app keys variants (`:flame-tongue-longsword`); and
attunement text is a separate field.

**Decided (owner, 2026-10-23): our fields stay ours.** "It makes sense for our app to have more
detailed fields because we are planning on interactivity while open5e is only SRD info." No charge
fields are proposed upstream.

**A charged item our scan missed:** Necklace of Fireballs ("1d6 + 3 beads", a 3rd-level fireball,
DC 15, one level higher per extra bead) fits the model exactly as beads: `::charges-start "1d6+3"`, no
recharge, a fireball cast from 3rd with `::cast-per-level 1`. The coverage test looks only for the word
"charges"; beads (and the Necklace of Prayer Beads) need adding by hand.

## Decided (owner, 2026-10-23)

- **Stacks: one pool per item.**
- **One PR:** charges, rests and casting ride the weapons PR ("all under the same umbrella").
- **Two ways to cast from an item:** on the item (Equipment tab), and a "From Items" table above
  Spells By Level on the Spells tab. Both open the same cast panel: level buttons priced in charges
  (unaffordable ones greyed), the item's DC, and one Cast button. Until spells carry damage data, the
  panel shows the spell's own "At Higher Levels" sentence.
- **Where the cast panel opens:** a bottom sheet on phones, a full-width panel under the item row on
  desktop. A Dev mode setting (the handoff's Dev mode section in Settings) turns the phone sheet off
  so phones use the desktop panel: "Open item casting under the item on phones", off by default.

## Open, for the owner


