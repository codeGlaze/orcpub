# The weapon data model

Every field on a weapon in `weapons.cljc`, derived from the loaded data (40 weapons), not from
reading the file. Written because the model was undocumented: a `tag->flag` mapping was built from a
partial grep and silently missed more than half the flags.

## Fields

| field | on | values | notes |
|---|---|---|---|
| `:name` `:key` | 40 | — | `:key` is the stable id; never derive identity from `:name` (D10) |
| `::type` | 40 | `:simple` `:martial` | proficiency category |
| `::subtype` | 7 | `:axe` `:staff` `:sword` | sparse; only where something keys off it |
| `::damage-die` `::damage-die-count` | 39 | — | **Net has neither** — a weapon that deals no damage |
| `::damage-type` | 39 | `:bludgeoning` `:piercing` `:slashing` | |
| `::melee?` | 28 | `true` | |
| `::ranged?` | 12 | `true` | |
| `::two-handed?` | 14 | `true`, and `false` on one | |
| `::heavy?` | 11 | `true` | |
| `::light?` | 8 | `true` | |
| `::thrown` | 8 | `true` | **no `?`** |
| `::finesse?` | 7 | `true`, and `false` on one | |
| `::ammunition?` | 6 | `true` | |
| `::loading?` | 6 | `true` | |
| `::versatile` | 6 | **a map**, not a flag | `{::damage-die 8 ::damage-die-count 1}` — the two-handed damage. **No `?`** |
| `::range` | 18 | **a map** | `{::min 20 ::max 60}` |
| `::reach` | 5 | `true` | **no `?`** |
| `::special?` | 2 | `true` | |
| `::link` | 30 | url | cosmetic |

## Traps

**Flags are present-or-absent, not true-or-false.** Almost every boolean flag appears only as `true`
on weapons that have it and is absent otherwise — so `(::light? w)` is `nil`, not `false`, for most
weapons. Two weapons break the pattern and declare an explicit `false`: **Longsword**
(`::finesse? false`) and **Firearm, Hand** (`::two-handed? false`). Any predicate must treat absent
and `false` alike; `weapons/matches?` coerces both ends with `boolean`.

**Three fields lack the `?` suffix** that the rest use: `::thrown`, `::versatile`, `::reach`. Easy
to mistype as `::thrown?` and get `nil` for every weapon — a silently empty predicate rather than an
error.

**`::versatile` and `::range` are maps, not flags.** Truthiness works for "is it versatile", which
is what `matches?` relies on, but reading either as a boolean value is wrong.

## Invariants, verified against the data

These are pinned by tests (`weapon_bonus_test`) so they fail loudly if the data drifts:

- **Every weapon declares exactly one of `::melee?` / `::ranged?`** — never both, never neither.
  The engine depends on it: `?weapon-attack-modifier` treats not-melee as ranged.
- All 6 `::ammunition?` weapons are `::ranged?`.
- **No `::versatile` weapon is `::two-handed?`** — versatile means one-handed *with* a two-handed
  option, so the two are mutually exclusive by definition.

**Homebrew is NOT bound by any of these.** A homebrew weapon can declare neither `::melee?` nor
`::ranged?`. That is why authored conditions should use the positive tag: `{:ranged? true}` fails
such a weapon correctly, while `{:melee? false}` passes it and would grant a ranged-only bonus.

**Thrown is not a synonym for melee.** 6 of the 8 thrown weapons are melee (handaxe, javelin…), but
**Dart and Net are thrown and ranged**. A "thrown weapons" condition must test `::thrown` itself,
never infer it.

## Authoring against these

`weapons/matches?` is the three-state predicate over these flags — `true` requires it, `false`
forbids it, absent means either way — and `weapons/tag->flag` is the authored-tag → field mapping.
Used by the `:attack-bonus` and `:damage-bonus` props. See `fighting-style-vocabulary-gap.md`.
