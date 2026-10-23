# Rest controls: design handoff and how it meets the code

**Start here to build the Rest controls** (Short Rest, Long Rest, Dawn, Dusk, Undo). The design came
from a dedicated layout agent the owner briefed on 2026-10-22 with goals, not mockups; this page says
where the package is and what the code already has or lacks. The earlier rest mockups and rulings
are in [weapon-mastery-plan.md](weapon-mastery-plan.md) ("Undo for a rest", "Dawn: an option, never a
default"); where they differ, this handoff wins.

## Where it is

`/home/codeglaze/projects/rest-design-v1/handoff_rest_controls/` (local, beside `weapons-design-v5`).
`Rest Controls Final.dc.html` is the source of truth, `Rest Handoff.md` the spec, `Rest Decisions.dc.html`
the record of options D1-D7. Open the `.dc.html` files in a browser; they need their sibling files.

## The design in one paragraph

A **Rest box** is the first section of the Features tab: heading (`fa-bed` + "Rest"), then a
**time-of-day** radio group (None, Dawn, Dusk, Both; default None, back to None after any action), a
**Now** button that applies the chosen time without resting, and **Short / Long** rest buttons. A rest
with a time chosen applies both. A rest with None, for a character with a spent dawn or dusk item,
asks "Did dawn or dusk pass?" after **both** rests (Dawn, Dusk, Both, Neither, Undo rest). One result
line with one Undo. Characters with no dawn or dusk items see only the heading and the two rests.
Other tabs get a floating Rest button (56px, bottom right) opening the same box as a bottom sheet;
the Import log button moves above it and becomes dismissible when empty (its dismiss control is
not drawn yet). The Spells tab's old "finish long rest" stays until the new box has settled, then
moves behind a Dev mode setting. The item builder gains a Charges group.

## Against the code (checked 2026-10-23, `feature/weapons-table`)

| Handoff says | The code has | To do |
|---|---|---|
| `fa-bed`, `fa-sun`, `fa-moon`, `fa-code` | all in Font Awesome 5.13.1 (`index.clj`) | none |
| Light theme accent `#33658A` | `core.clj` `accent-on-light` | none |
| Long rest never implies dawn | this branch's `units5e/long-rest-clears` includes `::dawn`, and an events test checks it | remove `::dawn`; flip the test |
| Recharge values incl. dusk | `::units5e/dawn`, `short-rest`, `long-rest` (schema fixed `20b65614`); no dusk | add `::units5e/dusk` (spec, schema entry, clear) |
| Item fields `charges-max`, `charges-used`, `recharge`, `regains` | `::mi/charges` and `::mi/charges-recharge` are specced, used by no item; uses are tracked per period in `::char5e/features-used` | reuse `::mi/charges` and `::mi/charges-recharge` (renaming user-facing keys breaks homebrew); `regains` is new; where charges-used lives is a decision |
| Undo reverses the whole action | Undo agreed earlier: lasts until the character changes and survives logging off | the handoff does not say how long Undo lasts; ask |
| Import log button | `views/import_log.cljs` | move, plus dismissible when empty (needs a drawing first) |
| Settings Dev mode section | not checked yet | find the settings page before building |

## How charges work today (checked 2026-10-23)

- **No item data carries a charge count.** "charges" appears 96 times in `magic_items.cljc`, all in
  description prose. `::mi/charges` and `::mi/charges-recharge` are specced and unused.
- **The sheet has no charge counter.** An item's limited use is an action with a `:frequency` (Dragon
  Scale Mail, `units5e/days-1`) shown as a checkbox, recorded under its period in
  `::char5e/features-used` and cleared by the rest that period belongs to. Used or not, no counts.
  This branch's weapon powers (`::mi/recharge ::units5e/dawn`) use the same record.
- **The PDF guesses a count:** `pdf.clj` `item-charges` reads "<number or dice> charges" from the
  description to print tick boxes on magic item cards. Paper only.
- **Bug: per-day uses never clear.** Ten items use `units5e/days-1` (`::units5e/day`), and no rest or
  other event clears `::day` (`units5e/long-rest-clears` is long-rest, rest, dawn). Their text says
  "until the next dawn", so `::day` should be cleared by Dawn (or those items moved to `::dawn`).

## Decided (owner, 2026-10-23)

- **Built on `feature/weapons-table`** ("just finish building it here").
- **Undo lasts until the result line is closed.** Nothing saved; this replaces the earlier
  "until the character changes, across sessions" ruling.

## A real charge system: plan (2026-10-23, awaiting owner)

The owner: "figure out a real charge system, even if it rides the existing ones to a new location".

**What needs it.** 44 SRD 2014 items state a charge count (found by a description scan, to be checked
by hand when filling): 36 regain some at dawn (wands 1d6 + 1 of 7, staffs 1d6 + 4 of 10, Staff of the
Magi 4d6 + 2 of 50, rings 1d3 of 3), 8 never regain (Feather Token, Gem of Brightness, Nine Lives
Stealer "1d8 + 1", Scarab of Protection, the Talismans). Plus the once-per-dawn and once-per-day item
powers already tracked as checkboxes (10 items on `units5e/days-1`, this branch's weapon powers on
`::mi/recharge ::units5e/dawn`).

**One model: a pool of charges per carried item.**

| Where | Field | Values |
|---|---|---|
| Item (data, item builder) | `::mi/charges` (exists, unused) | maximum, a whole number |
| | `::mi/charges-recharge` (exists, unused) | `::units5e/short-rest`, `long-rest`, `dawn`, `dusk`, or none |
| | `::mi/charges-regain` (new) | a whole number, a dice string ("1d6+1"), or `:all` |
| Character (per carried item) | `::char-equip-5e/charges-used` (new, beside `quantity` and `equipped?`) | how many are spent; missing means none |

The character's field rides the item's existing entry on the character (the map that already holds
quantity and equipped), so it saves with the character and needs one additive schema attribute.

**Riding the existing uses.** A once-per-dawn power is a pool of 1 that regains all at dawn. New data
writes it that way (`::mi/charges 1`, recharge dawn, regain `:all`); the sheet shows it as today's
single checkbox. Old saved checkboxes in `::char5e/features-used` are still read and cleared by
rests, so no saved character changes meaning; they stop being written for items that move.

**What the player sees.** A charged item shows its pool (pips up to about 10, "37 / 50" above); a
power with a cost spends that many (`::mi/charge-cost`, exists). Rest and Dawn/Dusk regain by the
item's rule; a dice regain is rolled and named in the result line ("Wand of Fireballs regained 5")
with Undo. The PDF prints the pool from data and keeps its description guess only for homebrew
without the fields. The item builder gets the handoff's Charges group (§7).

**Data fill.** By hand per item from its SRD text, as the weapons were, with a test per field.

## Open, for the owner

- Where a used charge is stored.
