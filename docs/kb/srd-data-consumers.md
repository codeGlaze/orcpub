# Who reads the app's SRD data

*Measured 2026-10-11 on `fix/srd-2014-data-defects` (current with `integration` at `b8035afe`).
Facts only; the plan that uses them is in [srd-2024-roadmap.md](srd-2024-roadmap.md), "One
pipeline". Line numbers drift: re-check before editing.*

The SRD content the builder uses is hand-kept Clojure in `src/cljc/orcpub/dnd/e5/`: `spells.cljc`,
`monsters.cljc`, `weapons.cljc`, `armor.cljc`, `magic_items.cljc`. Rules and conditions are already
generated files fetched at runtime (`srd_rules.cljc`, `srd_conditions.cljc`).

## The two facts that shape any move

- **The builder does not read the data namespaces directly.** It gets the spell map through the
  `::spells5e/spells-map` sub (`spell_subs.cljs:1309-1345`), which is also where homebrew spells
  merge in. Monsters merge the same way (`spell_subs.cljs:1230-1266`). Class spell lists are a
  separate static map of keys (`spell_lists.cljc:299`), not derived from the spell data.
- **Few consumers read at load time.** Everything else reads inside a function, sub or delay.
  The load-time readers are the work list for fetching data at runtime:

| load-time reader | reads |
|---|---|
| `library.cljc:175,177` `built-in-keys` | spell and monster key sets |
| `srd_starting_equipment.cljc:16` `class-option-thunks` | `weapons-map` |
| `equipment.cljc:233` `adventuring-gear` | `weapons/ammunition` |
| `magic_items.cljc:3080` `magic-items` and every map derived from it (3083-3150) | `weapons`, `ammunition`, `armor` (expands each magic weapon/armor per base item) |

## Per type

| type | builder | browse / search | JVM (PDF, routes) | homebrew merge |
|---|---|---|---|---|
| spells | via sub; `classes.cljc:2345,2366,2756`, `options.cljc:710,2873` at call time | `compute.cljc:60`, `events.cljs:3523` (Orcacle), `views.cljs:1777` | `routes.clj:1012,1151` spell cards and spellbook, `routes.clj:1415` | `spell_subs.cljs:1168-1180,1309-1380` |
| monsters | none | `subs.cljs:1086-1096`, `events.cljs:3525`, combat tracker via subs | none | `spell_subs.cljs:1230-1266` |
| weapons | `template.cljc:1562`, `options.cljc:2428,2443` | `equipment_subs.cljs:111-249`, `views.cljs:7103,7181` | `pdf_spec.cljc:673` | through magic items |
| armor | `template.cljc:1564`, `options.cljc:2467` | `views.cljs` (component fns) | `pdf_spec.cljc:674,805` | through magic items |
| magic items | options passed in from `equipment_subs` | `compute.cljc:65`, `equipment_subs.cljs:28-258`, `events.cljs:3527` | `pdf_spec.cljc:171,247,345`, `routes.clj:2003` | `::mi/custom-items` through `expand-magic-items` |

## Magic items carry code

About 286 raw entries (`magic_items.cljc:461-2950`) before expansion. About 63 carry mechanics as
code: `::modifiers` built with `mod5e/...` macros (actions, resistances, AC, saves, speeds,
proficiencies), a few `fn` literals (`ac-bonus-fn`, `ac-formula`, an `::item-subtype` predicate),
and the belts of giant strength through `belt-of-giant-strength-mod`. The other ~220 are pure data.
Code cannot live in an EDN file, so magic items split: data generated, mechanics kept in code and
joined by key.

## Keys

Saved characters, homebrew and class spell lists refer to these keys, so a generated file must
reproduce every one.

- **Spells:** `(or (:key s) (name-to-kw (:name s)))` (`spells.cljc:4194`); ~16 literal keys;
  `spell-key-aliases` and `resolve-spell-key` (`spells.cljc:4231,4255`) map old keys.
- **Monsters:** `name-to-kw` of the name (`monsters.cljc:9264`).
- **Weapons, armor:** literal `:key` on every entry; ammunition by `name-to-kw`.
- **Magic items:** `name-to-kw` of the name, expanded items of the generated name with `:base-key`.
- `name-to-kw` lower-cases, so Title Case renames do not change a key.
