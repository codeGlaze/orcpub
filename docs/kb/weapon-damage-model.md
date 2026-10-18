# Weapon damage model: extra damage, effects, toggles, charges

How a magic weapon states damage beyond its base dice, for the Weapons table redesign
([handoff-weapons-table.md](handoff-weapons-table.md)). Agreed with the owner 2026-10-17. Base weapon
fields and their traps: [weapon-data-model.md](weapon-data-model.md). The human-facing version is
`docs/MAGIC-WEAPON-DAMAGE.md` on `feature/weapons-table`.

## Where it lives (feature/weapons-table)

| What | Where |
|---|---|
| Specs for every field below | `magic_items.cljc`, top of file, after `::attunement` |
| SRD data | the eight items in `magic_items.cljc` (`::extra-damage`, `::effects`, `::toggles`) |
| Functions | `weapon_damage.cljc`: `weapon-type`, `base-damage`, `resolve-extra`, `weapon-damage`, `crit-min`, `applies?`, `applying-damage` |
| Dawn | `units.cljc` `dawns`, `long-rest-clears`; used by `::char5e/finish-long-rest` (events.cljs) and the long-rest button condition (views.cljs, Details section) |
| Tests | `test/cljc/orcpub/dnd/e5/weapon_damage_test.cljc`; `events_test.cljs` `finish-long-rest-clears-dawn-and-keeps-shorter-periods` |

## The fields

All on the item, namespace `orcpub.dnd.e5.magic-items` (`::mi/`). Dice reuse the base weapon's own
keys, so one formatter reads a weapon, a feature attack and an extra alike.

```clojure
::mi/extra-damage [{::weapons/damage-die-count 2 ::weapons/damage-die 6
                    ::weapons/damage-type :fire      ;; omit = the weapon's type
                    ::mi/while :ablaze               ;; a ::mi/toggles id is on
                    ::mi/against #{:fiend :undead}   ;; target creature types (monster :type values)
                    ::mi/not-against #{:construct}   ;; never these types
                    ::mi/attack-type :ranged         ;; :melee | :ranged; thrown counts as ranged
                    ::mi/damage-bonus 2              ;; flat, in place of dice (Mace of Smiting)
                    ::mi/on :crit                    ;; :hit (default) | :miss | :crit
                    ::mi/min-roll 20                 ;; the d20 as rolled is at least this
                    ::mi/charge-cost 1               ;; spends item charges
                    ::mi/recharge ::units5e/dawn}]   ;; once, until this reset
::mi/effects  [{::mi/effect-type :save ::mi/save-dc 15 ::mi/save-ability ::char5e/str
                ::mi/save-fail :prone ::mi/text "<SRD sentence, verbatim>"
                ;; plus any condition field above, and optional dice
                }]
::mi/toggles  [{::mi/toggle-id :ablaze ::mi/toggle-name "Ablaze"}]
::mi/crit-from 18                                    ;; widens this weapon's crit range
::mi/charges 3  ::mi/charges-recharge ::units5e/dawn ;; the item's pool
```

`applies?` is an AND over every condition present. An unknown target (`:against` nil) passes
`not-against` and fails `against`. `applying-damage` defaults `:attack-type` from the weapon
(`::weapons/ranged?`), so a melee weapon thrown must be passed `:ranged` by the caller. Entries with `charge-cost` or `recharge` never
join an ordinary roll; they are their own menu item.

## Why it is shaped this way

- **One value type per field.** Custom items save to Datomic, which fixes each attribute's type. The
  first draft (`db9e6115`) had one `::when` that was sometimes a keyword and sometimes a map: storable
  in code-only SRD data, never in a custom item. Flat condition fields fix that.
- **`on :crit` and `min-roll` are different rules.** "On a critical hit" follows the attack's crit
  range (`char5e/critical-hit-values`, e.g. `#{19 20}` for a Champion, joined with `::mi/crit-from` by
  `crit-min`). "When you roll a 20" (Vicious, Vorpal, Sharpness, Life Stealing) needs the die to show
  20, so a Champion's 19 crits without it. Nothing in the data doubles dice: crit rules are an account
  setting (handoff).
- **Recharge uses the app's reset periods**, not new keywords. `::char5e/features-used` (stored per
  character) maps a period to the set of used names; the finish long rest / short rest / new round /
  new turn buttons clear periods with `clear-period`. **Dawn** is its own period so the data keeps the
  SRD's word; a long rest clears it (owner, 2026-10-17: both).
- **Charges reuse the existing counter.** `actions-indicators` (views.cljs) draws a checkbox per use
  (a dropdown at 5 or more) and stores `"<name>-<i>"` in `features-used` under the period. Not wired
  to items yet.

## Traps

- **`clear-period` takes periods as varargs.** Passing `long-rest-clears` as one argument dissocs a
  vector key and clears nothing; it is called with `apply`. The cljs test catches this.
- **Do not destructure `::mi/while` as `while`**: it shadows the core macro. `applies?` binds `toggle`.
- **Sun Blade expands to every sword** through `sword?` (`:sun-blade-longsword`, `-rapier`...), though the
  SRD makes it a longsword only. **Kept on purpose** (owner, 2026-10-17: the site is for homebrew, more
  options do no harm). Do not narrow it.
- **Approximations:** Vorpal Sword lists 6d8 on every rolled 20 (the SRD gives it only to creatures that
  keep their head); Mace of Smiting's +3 *attack* bonus against constructs is not modelled, only damage.
- **The PDF** prints weapons through `pdf-spec/weapon-entries` (fixed 2026-10-17: the two-handed line
  read `(:versatile weapon)` unqualified and never printed; Sun Blade printed slashing). It does not
  print extra damage yet.
- **A Vicious Net** resolves to a nil damage type (the Net has none).
- **The printed magic item cards** read charges off the description prose (`pdf.clj` `item-charges`),
  not `::mi/charges`. Switching them changes PDF output: ask.

## Not built yet

Datomic schema for these fields (needed before the custom item builder can save them); the builder's
"extra damage" rows; the shared formatter both the table and the PDF call; the table itself; charge
counts on items (the handoff lists recharge timing and zero charges as undecided). `.orcbrew` files
cannot carry items, so they are not affected.
