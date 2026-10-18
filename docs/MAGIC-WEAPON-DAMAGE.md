# Extra damage on magic weapons

Many magic weapons deal more than their base dice. A Flame Tongue adds 2d6 fire while it is ablaze;
a Holy Avenger adds 2d10 radiant against fiends and undead; a Vicious Weapon adds 2d6 when you roll
a 20. Until now the app only knew these from the item's description text, so nothing could add them
to a roll or print them on a sheet. This page describes how a magic weapon now states its extra
damage as data, so the character sheet, the dice rolls and the PDF can all read the same facts.

The description text stays exactly as it was. The data sits beside it and never replaces it.

## What a magic weapon can say

A weapon's damage is its own dice (from the base weapon, plus any magic bonus such as +1) followed
by a list of **extra damage** entries. Each entry gives its dice, and optionally a damage type and
the conditions under which it applies.

| Field | What it means | Example |
|---|---|---|
| dice | how many dice and what size | 2 and 6 for 2d6 |
| damage type | the type of the extra; leave it out for "damage of the weapon's type" | fire |
| `while` | only while a toggle on the item is switched on | Flame Tongue's "Ablaze" |
| `against` | only against these creature types | fiend, undead |
| `not-against` | never against these creature types | construct, undead |
| `attack-type` | only on a melee, or only on a ranged, attack | ranged (a thrown Dwarven Thrower) |
| `on` | when it applies: a hit (the default), a miss, or a critical hit | crit |
| `min-roll` | only when the d20 shows this number or higher | 20 |
| `charge-cost` | spends this many of the item's charges; used from its own button | 1 |
| `recharge` | can be used once, then not again until this reset | dawn, long rest |
| `damage-bonus` | a flat amount instead of dice | +2 (Mace of Smiting against a construct) |

Every condition an entry names must hold for it to apply, and an entry with none of them applies
on every hit. When the target's creature type is not known, an entry limited to certain types does
not apply, and an entry that excludes certain types does. A thrown weapon counts as a ranged attack.
An entry with a charge cost or a recharge is never added to an ordinary roll: you use it on purpose,
from its own button.

A weapon can also have **effects** (a saving throw such as Giant Slayer's "DC 15 Strength or fall
prone", healing, or a condition), using the same conditions, and **toggles** (a named on/off state
such as "Ablaze").

## Critical hits and "when you roll a 20"

These look alike but are different rules, and the data keeps them apart.

- **On a critical hit** (`on` set to crit) follows whatever counts as a critical hit for that
  attack. For most characters that is a 20; a Champion fighter crits on 19 or 20. A weapon can widen
  its own range with `crit-from` (a homebrew blade that crits on 18-20 says 18).
- **When you roll a 20** (`min-roll` 20) needs the die itself to show 20. A Champion's 19 is a
  critical hit, but it does not trigger a Vicious Weapon, because the SRD says "roll a 20".

How critical damage is counted (doubled dice or otherwise) is not part of this data; it will be a
setting.

## Resets: rests and dawn

Once-per-rest and charged features use the reset buttons the character sheet already has: finish
long rest, finish short rest, new round and new turn. Many SRD items say "until the next dawn", so
there is now a **dawn** reset as well. Finishing a long rest also clears anything that resets at
dawn, because most tables treat the two as the same moment; the data still says "dawn", so a table
that tracks days separately could reset them apart later.

Charges use the same tick boxes the sheet already shows for limited-use features.

## The SRD weapons that have it

Every SRD weapon with extra damage carries it, read from its SRD text: Dagger of Venom, Dragon
Slayer, Dwarven Thrower, Flame Tongue, Frost Brand, Giant Slayer, Holy Avenger, Javelin of Lightning,
Mace of Disruption, Mace of Smiting, Oathbow, Sun Blade, Sword of Life Stealing, Sword of Sharpness,
Vicious Weapon and Vorpal Sword. Their saving throws, healing and other effects carry the SRD's own
sentence.

Two are approximations, because the rule depends on a judgement the app cannot make:

- **Vorpal Sword** lists its 6d8 on every rolled 20. The SRD gives the 6d8 only to a creature that
  cannot lose its head; one that can simply loses it, so the extra damage no longer matters.
- **Mace of Smiting** raises its damage bonus against constructs, but its attack bonus against them
  is not modelled yet.

## Your characters are not affected

A saved character stores only which weapons it has and whether each is equipped. Damage is worked
out fresh from the item data every time, so adding this data changes no saved character and needs
no migration.

## Not yet

- The weapons table, rolls and PDF do not show the extra damage yet; that is the work this data is
  for.
- Custom items cannot set extra damage yet. The item builder needs new fields, and the server needs
  to accept them, before it can.
