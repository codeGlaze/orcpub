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
   Dawn also clears per-day uses (`::units5e/day`, never cleared today: a bug).
2. **Charges model:** item fields, character fields with schema, pure functions (spend, regain with
   a die fn, roll a starting maximum), tests seen failing first.
3. **SRD data fill:** the 44 charged items and the once-per-dawn powers, by hand from the text.
4. **Charges on the sheet:** pips, counts, spending; magic weapon powers draw from the pool.
5. **Rest box** per the handoff: Features tab, time of day, Now, rests, the question, result line,
   Undo; regain rolls with type-your-own.
6. **Casting from items:** `::mi/casts` data for the 30 items, the Cast button and charge-priced
   level picker, the result card. No damage roll yet.
7. **Item builder:** Charges group and casts; schema for the new item fields.
8. **Later, its own step:** floating Rest button with the Import log move, Dev mode setting (handoff
   §9-10).

On `srd52/develop`, separately: spell damage parts and casting options as data (srd-improvements.md).
Then a cast rolls damage through the weapon roll engine.

## Open, for the owner

- Stacks: one pool per item, or shared.
- Item spells on the Spells tab, or on the item only.
- Size: steps 1-7 make the weapons PR much larger. One PR, or the weapons table first and charges
  and rests as a second PR from the same branch.
