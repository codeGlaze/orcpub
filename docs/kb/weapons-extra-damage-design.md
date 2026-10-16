# Design: weapons with extra damage, switches, and homebrew effects

The design agent's answer (2026-10-16) to [weapons-extra-damage-handoff.md](weapons-extra-damage-handoff.md),
kept verbatim below, followed by the review. Its visual reference (`Weapon Extra Damage.dc.html`) was
delivered as a zip and is not in the repo.

For the implementing agent. This extends the Weapons table. Read `weapons-table-spec.md` first; its Agreed rules still apply.
Visual reference: `Weapon Extra Damage.dc.html`, where each case is drawn in dark and light, with dots and with bars. Section numbers below refer to that file.
If anything here is ambiguous, ask the user before guessing.

## Decisions

| Topic | Decision | Reference |
|---|---|---|
| Damage tile | 98×54 with a 17px strip. It **never changes size**, however many parts the weapon has | 8b, 9 |
| One extra part | Chip inside the amber body under the base roll, e.g. `+1d6 cold` | 1 |
| Two or more extra parts | The tile shows the base roll plus a `+N more` chip with one dot per extra type. The parts are listed on an **Every hit** line under the row, which wraps | 8b (default) |
| Extra-damage chip colour | Filled with the type's **bar** colour, white text, type word always written. No dot | 1, 8b |
| Extra damage of the weapon's own type (Vicious, Giant Slayer) | Neutral chip (strip colour). No new type colour | 4 |
| Switched-on damage (Flame Tongue) | A switch in the grey line, same component as 1H/2H. The chip it adds gets a 1.5px inset ring in the switch's "on" colour | 2, 9 |
| Conditional damage (vs undead, vs giant) | Goes in the **+ menu**. Not added to the normal roll | 3c |
| + menu | A 15×15 "+" tab in the tile's top-right corner marks a tile that has a menu. Hold (long-press) or right-click opens it. Rows are 44px minimum | 3c |
| Crit | A "Critical hit" option in the + menu. Crit-only damage (Vicious) appears only there | 4 |
| Crit range | Goes in the to-hit strip: `hit · 19+` | 9 |
| Charges | A pill in the grey line (dots + `2/3`). Spending one is a + menu option, with "N of M left" under it | 9 |
| Once per rest | A + menu option. When used, it greys out and says `used`, with the rest type underneath | 9 |
| On-hit effects (saves, healing) | An **On hit** line under the row, using outline pills. They are not damage and never get a type colour | 9 |
| Grey line overflow | Switches and the charge counter wrap onto a second line. Rows with many properties may be taller than normal rows | 9 |
| Roll result | One line per part (dice, type, subtotal), then the total | 6 |
| Button text | White on amber by default. Dark text is a player preference | 7 |
| Strip style | Dark theme defaults to dots, light theme to bars. Either is a player preference | all |

## Damage-type palette

| Type | Dot | Bar / chip fill |
|---|---|---|
| acid | `#e0d84a` | `#6b6a12` |
| bludgeoning | `#a99bf0` | `#4a3f6b` |
| cold | `#7fd3f0` | `#1d5a73` |
| fire | `#ff8a4a` | `#9a3a10` |
| force | `#c792f0` | `#5a2f7a` |
| lightning | `#8fb0ff` | `#2f4fa0` |
| necrotic | `#a3b07a` | `#3b3f2a` |
| piercing | `#7fc48a` | `#23633a` |
| poison | `#9fd36a` | `#3f6b1f` |
| psychic | `#f08ad8` | `#7a2f6b` |
| radiant | `#ffd95a` | `#7a6212` |
| slashing | `#e8705a` | `#8a2f1f` |
| thunder | `#b8c4d6` | `#3f4a5c` |

**Not yet verified:** the contrast of white text on every bar colour, and how each dot reads on both strip colours. Check these before shipping. If `weapons-table-spec.md` has its own palette, that one takes priority.

## Suggested data model

Store a weapon's damage as a list of parts, not as one string:

```
part = { dice, bonus?, type | "weapon", when }
when = "always" | { switch: id } | { vs: "undead" | ... } | "crit" | { charge: cost } | { perRest: "short" | "long" }
```

The UI places each part by its `when` value:
- `always` and active switches go on the **Every hit** line, or in the tile chip if there's only one extra part.
- Every other `when` value goes in the **+ menu**.

On-hit effects (saves, healing, conditions) are a separate list from damage. The item creator writes to this same model. Spells and class features (Sneak Attack, Smite, Hunter's Mark) should reuse it later.

## Not designed yet; ask the user before building

1. **Roll result for effects other than damage**: how saves, healing, and a spent charge appear in the result.
2. **Resistance and vulnerability**: halving or doubling a single part, and where that choice lives.
3. **Charges**: how they recharge (dawn, or on a rest), the zero-charges state, and whether the count is saved per character.
4. **Accessibility for the + menu**: long-press isn't discoverable and doesn't work with a keyboard or screen reader. It needs a focusable button or a keyboard shortcut.
5. **Desktop width**: everything so far is designed at 390px.
6. **Crit dice**: under 5e rules, a crit doubles *all* damage dice, including extra parts. Confirm the menu label and the roll result show this.

## Trial checklist

- At 390px, nothing clips: the longest type pair, three switches plus charges, and a long weapon name.
- Every case looks right in all four modes (dark or light × dots or bars) and with both button text colours.
- The + menu stays on screen next to the bottom and right edges.
- Every tap target is at least 44px.

## Review (2026-10-16)

Rendered and checked against [weapons-table-spec.md](weapons-table-spec.md) and SRD 5.1. Its bundled spec
copy was byte-identical to ours.

- **Take:** the fixed-size damage tile with a chip or "+N more" and an Every hit line (8b); the
  switch reused for Flame Tongue; target-only damage kept out of the normal roll; one result line per
  part; a natural 20 arming crit damage; the `when` data model.
- **White text on every bar colour was already verified** (5.7 to 10.9:1), clearing its "not yet
  verified" note.
- **The hold-only + menu needs a visible way in:** the same named rolls as buttons in the opened row,
  with the hold as a shortcut. A 15px tab is below the 44px rule; a slow tap on a roll button could
  roll by accident.
- **Chip text inside the tile is about 9px:** check at 200% text before building.
- **Vicious crit:** the design shows the extra 2d6 not doubled. The general rule (a critical hit rolls
  all of the attack's damage dice twice; Sage Advice applies it to extra damage such as Sneak Attack)
  points to doubling. No ruling on Vicious itself was found; owner to decide.
- **Charges, once-per-rest and on-hit effects** are drawn but have open questions; not to build yet.

## Owner decisions on the review (2026-10-16)

- **Crit rules are a setting:** an account default, overridable per character (so a campaign's table
  rules apply), and always stated where crit damage is shown, so it is not forgotten. Vicious and every
  extra part follow whichever rule is set.
- **The scope is intentional:** charges, once-per-rest, on-hit effects and the stress test exist so new
  features do not force a redesign. The site supports more than SRD data; the stress test shows what
  official content and homebrew should support.
- **Small chip text is acceptable:** measured at 10px bold in the tile and 11px on the Every hit line;
  the opened row repeats every part at 13px.
- **Target rolls stay on the row, as a line of bubbles:** putting them behind the expand arrow was
  rejected (bad UX for rolls used mid-fight). Owner: in the design agent's mockups they are "just another
  line of bubbles", like Every hit and On hit, so a labelled line (e.g. `VS UNDEAD  +1d8 radiant`) whose
  bubble rolls the damage with that part is the direction. A tappable "+" on the tile corner was also
  drawn (22px, 44px tap area) and remains the place for crit and other named rolls; the crit row names
  the crit rule in use.
