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

**The Net has no damage dice, and the app shows that badly** (found 2026-10-15, computed on the JVM
with the app's own display functions). The character sheet's damage roll button reads `d+2` and
tries to roll it, and the PDF prints `d+2` (`pdf_spec.cljc` `damage-str`); a Longsword gives
`1d8+2` and `1d8+2 slashing`. Proposed, not built: the damage column says "None", no roll button,
and the details line gives the SRD's effect (a Large or smaller creature hit is restrained).

Not compared yet: ammunition (SRD gear, not in the equipment file), 2024 equipment (the app has
none), and the SRD's rules text for special weapons (Net, Lance), which the app holds only as a flag.
open5e gives the Net `"0"` bludgeoning damage where the SRD prints a dash; the comparison treats it
as no damage, which matches the app.
