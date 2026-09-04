# What a fighting-style builder must express — measured against real content

Gap analysis against the published fighting styles (24 printings, **14 distinct styles**, PHB /
XPHB / TCE / AU, via the 5etools data mirror). The question: what does an author need to say, and
what can our `:props` vocabulary say today?

**3 of 14 are expressible now** (Defense, Archery, Thrown Weapon Fighting); it was 1 when this was
written. But the *engine* already has hooks for 8 of them — the gap is
the authored vocabulary, not the machinery. That is exactly the position AC was in: `?ac-fns`
existed with no constructor and no writers.

| style | mechanical shape | engine hook | authorable |
|---|---|---|---|
| **Defense** | +N AC while wearing armor | `?ac-bonus-fns` | ✅ `{:ac-bonus {:ac-bonus 1 :armor? true}}` |
| Archery | +N attack, ranged weapons | `?attack-modifier-fns` ✅ | ✅ `{:attack-bonus {:bonus 2 :melee? false}}` |
| Dueling | +N damage, melee, one-handed, no other weapon | ⚠️ needs wielding context in the fn signature | ❌ |
| Thrown Weapon Fighting | +N damage with the Thrown property | `?damage-bonus-fns` ✅ | ✅ `{:damage-bonus {:bonus 2 :thrown? true}}` |
| Two-Weapon Fighting | add ability mod to the offhand attack's damage | `?dual-wield-weapon?` partial | ❌ |
| Protection | Reaction; requires a shield | `mod5e/reaction` ✅ | ❌ |
| Interception | Reaction; reduce damage 1d10 + prof | `mod5e/reaction` ✅ | ❌ |
| Blind Fighting | grants Blindsight 10 ft | `mod5e/darkvision` precedent | ❌ |
| Superior Technique | choose 1 from the maneuver pool | `:grant {:from … :choose N}` ✅ | ⚠️ needs a maneuver pool |
| Blessed Warrior | 2 cleric cantrips; Cha casts them | partial | ❌ |
| Druidic Warrior | 2 druid cantrips; Wis casts them | partial | ❌ |
| Arcane Warrior (AU) | 2 wizard cantrips; *choose* the casting ability | partial | ❌ |
| Unarmed Fighting | unarmed strike damage becomes 1d6+Str (d8 if empty-handed) | `?martial-arts-die` precedent | ❌ |
| Great Weapon Fighting | treat 1s and 2s on damage dice as 3 | **none** | ❌ |

## The shapes, grouped

1. **Conditional attack / damage bonuses** (Archery, Dueling, Thrown Weapon) — the engine channels
   exist and only the compiler is missing. The condition is the interesting half: *ranged*, *melee
   one-handed with no other weapon*, *has the Thrown property*.
2. **Reactions** (Protection, Interception) — `mod5e/reaction` already renders these on the sheet;
   Protection additionally gates on holding a shield.
3. **Grants from a pool** (Superior Technique, and the three cantrip styles) — `:grant` exists as a
   generic cross-bucket grant (`options.cljc:3883`). The cantrip styles need more: a spell-list
   pool AND setting the spellcasting ability for what they grant.
4. **Senses** (Blind Fighting) — a `darkvision` constructor exists; blindsight is the same shape.
5. **Replacing a damage expression** (Unarmed Fighting) — `?martial-arts-die` is the precedent.
6. **Damage-die manipulation** (Great Weapon Fighting) — the genuinely hard one. Nothing hooks the
   individual damage dice, so this needs new engine work, not just vocabulary.

## The recurring need: a WIELDING predicate

Half the corpus conditions on equipment state — *while wearing armor*, *while holding a shield*,
*one-handed with no other weapon*, *two-handed*, *a weapon with property X*, *empty-handed*.

AC already solved its slice of this with three-state tags (`:armor?` / `:shield?`: `false` = only
when not equipped, `true` = only when equipped, absent = either way). Generalising that same tag
idea to weapons — property, handedness, offhand — covers conditions 1, 2 and 5 above with one
vocabulary rather than a key per style.

## This is SHARED vocabulary work, not fighting-style work

Every prop added here lands in **seven silos at once**, because they all compile the same `:props`
map through `plugin-modifiers` → `make-feat-modifiers`. Verified by tracing each call site:

| silo | entry point |
|---|---|
| homebrew races | `spell_subs.cljs` `::races5e/plugin-races` |
| homebrew subraces | `spell_subs.cljs` `::races5e/plugin-subraces` |
| homebrew classes | `spell_subs.cljs` `::classes5e/plugin-classes` |
| homebrew subclasses | `spell_subs.cljs` `::classes5e/plugin-subclasses` |
| draconic ancestries | `spell_subs.cljs` `draconic-ancestry-option` |
| feats | `options.cljc` `feat-option-from-cfg` |
| fighting styles | `options.cljc` `fighting-style-option` |

So `:attack-bonus` with a weapon predicate is not "an Archery feature". It is *"+N to attack under
condition X"* for a race, a subclass, a feat, or a fighting style — authored identically in all of
them. The same held for `:ac-bonus`: it was built for the AC refactor and Defense gets it free.

The planned rules-override layer (`rules-override-layer.md`) should ride this vocabulary too rather
than inventing its own — a DM granting "+1 AC to everyone at this table" wants the prop that
already exists, not a parallel one.

**Consequence for prioritising:** the value of each prop is roughly 7×, and the cost of getting the
*shape* wrong is also 7×. That argues for the generalised weapon predicate over a key per style,
and it argues for doing this before more builder pages — a new builder page serves one silo, a new
prop serves all of them.

## Order of work, cheapest first

1. ✅ **DONE — `:ac-bonus` exposed as a shared field fragment.** `bf/ac-bonus-fields` in
   `builder_fields.cljc`, not in the classes ns: it is shared `:props` vocabulary, so it drops into
   ANY silo's `extra-fields` unchanged. Defense is now authorable as `{:ac-bonus 1 :armor? true}`
   with no new code. The three-state tags are `:enum` fields with boolean values — `builder_fields`
   explicitly defers a `:boolean` type and says not to build a parallel mechanism.
2. ✅ **DONE — `:attack-bonus` / `:damage-bonus` with a weapon predicate.** `weapons/matches?` is
   the three-state tag predicate (`:melee? :thrown? :finesse? :light? :two-handed?`), same shape as
   AC's `:armor?`/`:shield?`. Both props ride the GENERAL channels (`?attack-modifier-fns`,
   `?damage-bonus-fns`), which retires the question the old comment on `damage-bonus-fn` left open:
   with a predicate, `?melee-damage-bonus-fns` and `?ranged-damage-bonus-fns` have nothing left to
   do, and both were already commented out of the engine.

   **Covers TWO styles, not three.** Archery `{:bonus 2 :melee? false}` and Thrown Weapon
   `{:bonus 2 :thrown? true}` — both verified end to end through the engine. **Dueling is NOT
   expressible** and neither is Two-Weapon Fighting: the engine hands these fns one argument, the
   weapon, so they cannot see whether it is the off-hand attack or what else is being wielded.
   "No other weapons" and "the extra attack" need that signature widened. An earlier note here said
   three styles; it is two until then.
3. **`:reaction` / `:trait` props.** Covers Protection and Interception, and every "it's just text
   on the sheet" homebrew style. Worth noting how low the real bar is: of the 6 PHB styles, three
   (Dueling, Great Weapon Fighting, Two-Weapon Fighting) ship as trait text with **no mechanical
   implementation at all**, so a description field already matches shipped behaviour for them.
4. **Blindsight prop.** One style, trivial next to darkvision.
5. **Cantrip grants with a casting ability.** Three styles, but needs pool work.
6. **Damage-die manipulation.** One style, and the only one needing real engine work. Defer.
