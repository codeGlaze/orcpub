# Handoff: weapons with more than one kind of damage

For the design agent. Extends the Weapons table already designed: read
[weapons-table-spec.md](weapons-table-spec.md) first (its Agreed rules, and the numbered
picture of the parts), and the working prototype https://claude.ai/artifact/Kf86pSNkyFi2QJEuRMQsTd.

## The problem

Today a damage tile shows one roll and one type: `1d8+2` over `● slashing`. Magic weapons add
damage, and they do it in five different ways. The design has to show each one clearly on a 390px
phone, in dark and light, inside the existing tile.

## The five cases, from SRD 5.1 (text checked)

| Case | Example | SRD wording | What the player needs |
|---|---|---|---|
| 1. Always on | **Frost Brand** | "the target takes an extra 1d6 cold damage" | one roll of both parts, both types shown |
| 2. On while switched on | **Flame Tongue** | "While the sword is ablaze, it deals an extra 2d6 fire damage" | a way to say "ablaze" (like the 1H/2H switch), then case 1 |
| 3. Only against some targets | **Sun Blade** ("an undead": +1d8 radiant), **Holy Avenger** (fiend or undead: +2d10 radiant), **Mace of Disruption** (+2d6 radiant), **Giant Slayer** (a giant: +2d6 of the weapon's type), **Dragon Slayer** (+3d6) | "When you hit an undead with it, that target takes an extra 1d8 radiant damage" | NOT added to every roll; offered separately, named by the target |
| 4. Only on a 20 | **Vicious Weapon** | "your critical hit deals an extra 2d6 damage of the weapon's type" | shown with the critical roll, not the normal one |
| 5. Changes the base | **Sun Blade** "deals radiant damage instead of slashing"; **+1/+2/+3** weapons add to attack and damage | | the tile's own number and type change; no extra part |

Several are combinations: a Sun Blade is case 5 (radiant, +2) and case 3 (+1d8 vs undead).
"Extra damage of the weapon's type" adds dice without a new type, so no new colour.

## What is decided and must not change

- **Tile:** the app's amber gradient body with white text (dark text is a player preference), and
  a label strip under it (`hit`, or the damage type). Tiles 54×54 (to hit) and 98×54 (damage) at
  390px; the strip is 17px.
- **Damage-type colour:** each of the 13 types has a dot colour and a bar colour (the palette in
  the spec). Dark theme defaults to a neutral strip with a dot; light defaults to the coloured bar;
  either is a player preference. The type is always written, never colour alone.
- **Versatile:** the `1H | 2H` switch in the grey line; the changed dice get a tab in the switch's
  colour.
- **Shield rules, reason pill, opened row as an inset panel:** as in the spec.
- **Two good options become a preference:** if you have two strong answers, show both; one becomes
  the default and the other an account setting.

## The squeeze

Two types in a 98px strip at 10.5px: "● slashing ● fire" barely fits; "● bludgeoning ● thunder" does
not. Possible directions, open:

- dots for every type plus the main type's word, full list in the roll result and opened row;
- in bar mode, the bar split into each type's colour, proportional or equal;
- the number line itself carries the parts: `1d8+2 + 1d6`.

## Please deliver

1. Case 1 (Frost Brand longsword) and case 2 (Flame Tongue, ablaze off and on), dark and light.
2. Case 3 (Sun Blade): where the "+1d8 radiant vs undead" roll lives, and how it reads.
3. Case 4 (Vicious): how the extra shows on a critical.
4. What the roll result says for a multi-part roll (one line per part, and a total).
5. The longest real pairing at 390px (bludgeoning + thunder) not clipped or wrapped.
6. Both themes for every case, and how each degrades with the strip as dots vs bars.

Not in scope: spells and class features (Sneak Attack, Divine Smite, Hunter's Mark) will reuse
whatever this settles, but design the weapon cases first.
