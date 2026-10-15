# Equipment parity: the app's weapons and armor against the SRD

Before the generator replaces the hand-kept `weapons.cljc` and `armor.cljc` (stage 1 of the
pipeline in [srd-2024-roadmap.md](srd-2024-roadmap.md)), every difference between the two is known
and has a reason. Nothing changes in front of a player by surprise.

```bash
scripts/srd/equipment-parity.sh APP_WORKTREE               # report; exit 1 on an unreviewed difference
scripts/srd/equipment-parity.sh APP_WORKTREE CLONE SRD52 --self-test
```

It regenerates the 2014 SRD equipment from the open5e clone into a scratch directory (so a fix in
`srd-corrections` shows up at once), loads the app's real `weapons` and `armor` namespaces on the
app's classpath, and compares them field by field, matched by `:key`.

## How a difference is classified

- **Mapped fields** are compared after translating shapes: SRD `{:dice "1d8"}` against the app's
  die and count, SRD properties against the app's flags, `3` against `"3 lb."`. Format differences
  are normalised, not reported: a flat `"1"` is the app's `1d1`, the SRD's dash is no weight, heavy
  armor has no Dexterity cap on either side.
- **Every app field** is either mapped or listed as app-only with a reason. A new app field fails.
- **Every remaining difference** is listed in `equipment-parity-reviewed.edn` with a reason, or it
  fails. An entry pins the values it saw, so a value that changes later is a new finding.
  `:status :pending` marks a known difference waiting on a decision.

## Findings (2026-10-15, against `integration` `b8035afe4`)

| finding | state |
|---|---|
| Longbow lacks `::weapons/ammunition?`; the other six ammunition weapons have it, and magic items and the item builder read it | fixed on `fix/srd-2014-data-defects` (#62), test `ammunition-weapons-are-flagged`; still open against `integration` until #62 merges |
| Sling range: open5e's ammunition property said 80/320, its own record and the SRD 30/120 | fixed in `srd-corrections`; check rule `weapon-range` added |
| 30 weapons carry a Wikipedia `::link`, added 2017-04-27 (`1cd9abaf8`); `weapon-details` takes it and never renders it | pending: drop, or point at the Rules tab |
| 14 display names differ: 9 Title Case renames ([content-naming-style.md](content-naming-style.md)), 5 armors the SRD names "... Armor" | pending; player-visible, keys unchanged |
| SRD Studded Leather Armor is `:studded` in the app | settled: `:studded` stays, the generator maps it |
| Spiked Armor and DMV's three firearms are not SRD | settled: live options, kept |
| `::weapons/subtype` (sword, axe, bow) has no SRD field | settled: kept as a tag for feat and mastery hooks |

**The Net deals no damage, by design** (2026-10-15). SRD 5.1 lists its damage as "—" with
"Special, thrown (range 5/15)": a Large or smaller creature it hits is restrained (DC 10 Strength
check or 5 slashing damage to the net to free it), and attacking with it allows one attack per
action. No die means no ability modifier and no damage riders: Jeremy Crawford, 2015-04-10, on
Sharpshooter and Hunter's Mark with a net: "The intent is that a net doesn't deal extra damage
because it's not dealing damage in the first place." In SRD 5.2.1 the Net is gear, not a weapon:
it replaces one attack, the target makes a Dexterity save, and nothing deals damage. No spiked or
barbed net exists in either SRD or in open5e.

The app rendered the missing die as `d+2` (sheet roll button, Attacks text, PDF). Fixed on
`fix/srd-2014-data-defects` (#62), keyed on "no damage die" rather than the name, so homebrew
weapons get it too: the Attacks text says "no damage", the PDF and the weapons table say "None"
with no roll button, the details read Damage Type "None", and the Net carries a `:summary` of what
it does. Test: `a-weapon-without-damage-shows-none`. The same test found the Attacks text dropping
every weapon's damage type ("1d8+2  damage"): `attack-description` read only plain keys, so a
weapon's `::weapons/damage-type` never showed. Fixed with it.

Not compared yet: ammunition (SRD gear, not in the equipment file), 2024 equipment (the app has
none), and the SRD's rules text for special weapons (Net, Lance), which the app holds only as a flag.
open5e gives the Net `"0"` bludgeoning damage where the SRD prints a dash; the comparison treats it
as no damage, which matches the app.
