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
| Archery | +N attack, ranged weapons | `?attack-modifier-fns` ✅ | ✅ `{:attack-bonus {:bonus 2 :ranged? true}}` |
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

### `:ranged?` is a real flag, not the negation of `:melee?`

An earlier version of this work used `{:melee? false}` for Archery, on the claim that the data
models only `::melee?`. Wrong: `::ranged?` is carried by 12 weapons and maintained consistently —
**every shipped weapon declares exactly one of the two, never both, never neither** (pinned by
`weapons-declare-exactly-one-of-melee-or-ranged`).

That makes the two tags interchangeable for SRD content but NOT for homebrew, which is not bound by
the invariant. A homebrew weapon declaring neither flag *passes* `{:melee? false}` and would collect
an Archery bonus it should not. Prefer the positive tag.

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
   the three-state tag predicate, same shape as AC's `:armor?`/`:shield?`, over every weapon flag
   the data carries: `:melee? :ranged? :thrown? :finesse? :light? :heavy? :two-handed? :versatile?
   :reach? :loading? :ammunition? :special?`. Both props ride the GENERAL channels (`?attack-modifier-fns`,
   `?damage-bonus-fns`), which retires the question the old comment on `damage-bonus-fn` left open:
   with a predicate, `?melee-damage-bonus-fns` and `?ranged-damage-bonus-fns` have nothing left to
   do, and both were already commented out of the engine.

   **Covers TWO styles, not three.** Archery `{:bonus 2 :ranged? true}` and Thrown Weapon
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

## What driving the real app caught

None of this was visible from the JVM suite or a clean cljs compile — all of it came from
`test/browser/fighting_style_builder_e2e.js` against `lein e2e-server`.

1. **Every `:number` field in every declarative builder was broken.** `number-field` already parses
   its input to an int, and `render-builder-field` then called `(when (seq %) (js/parseInt %))` on
   it — `(seq 1)` throws `1 is not ISeqable`. Typing a digit threw inside the handler so the value
   never reached app-db, while the input still *showed* it via `input-field`'s local buffer.
   Clearing worked, since `(seq nil)` is nil. **Pre-existing and shipped** — reproduced on the
   draconic ancestry builder, which is released content.

2. **A three-state value in a two-option dropdown lied.** A `<select>` with no matching value
   renders its first option, so an unset tag *displayed* "Only while wearing armor". Every tag field
   now offers an explicit `Either way` as the first option.

3. **`nil` would have inverted a condition.** Once that option exists, a stored blank reaches
   `weapons/matches?` as `nil`, which its `(boolean want)` coerced to `false` — silently turning
   "either way" into "must NOT have this property". The AC compiler's `ac-applies?` already handled
   nil correctly; `matches?` did not.

The third only existed because of the second, and neither would have appeared without looking at a
screenshot of the rendered form.

## Verified end to end in the real app

`homebrew_roundtrip_e2e.js` — 14/14 against `lein e2e-server`:

1. A fighting style authored **through the UI** (typed name, source, AC bonus, attack bonus, and the
   armor tag) produces `{:props {:ac-bonus {:ac-bonus 1 :armor? true} :attack-bonus {:bonus 2}}}`.
2. It **saves** into `:plugins` with those props intact.
3. A draconic ancestry with a **numeric** breath weapon saves as `:line-width 5` — the regression
   case for the `render-builder-field` double-parse, proven in the app rather than by reading.
4. **Export** yields a real `.orcbrew` download carrying both, props and numbers included.
5. **Re-import** into a clean browser context restores both, verified absent beforehand.

So the vocabulary is not just compiled — it survives the whole authoring lifecycle.

## GAP: an imported style cannot be picked by the class that has the feature

The round-trip above proves storage. It does **not** prove usability, and the last mile fails.

Measured (`imported_style_usable_e2e.js`): import a homebrew style, build a Fighter, open Class /
Level. The Fighting Style selection renders with all six SRD styles — Archery, Defense, Dueling,
Great Weapon Fighting, Protection, Two Weapon Fighting — and the imported style is **absent**.

Cause, traced:

| | |
|---|---|
| `classes.cljc:1119` | the Fighter's selection is `opt5e/fighting-style-selection` |
| `options.cljc:2072` | which reads the **static** `opt5e/fighting-style-options` — the six SRD styles |
| `spell_subs.cljs:1052` | a homebrew-inclusive pool `::classes5e/fighting-style-pool` **does** exist and concats `::e5/fighting-styles` plugin entries |
| `template.cljc:1560` | but it is threaded only into a feat's `:grant {:from :fighting-styles}` |

So homebrew styles reach a character **only through a feat grant**, never through the class feature
that actually grants fighting styles. Authoring works end to end; consumption does not.

The fix is to point the class selection at the pool instead of the static list. It is not a
one-line swap: the pool lives in a cljs subscription while `fighting-style-selection` is called from
`.cljc` template construction, so the pool has to be threaded in the way `template.cljc:1483`
already threads it for grants. Pinned by the e2e as characterization — flip that assertion when it
lands.
