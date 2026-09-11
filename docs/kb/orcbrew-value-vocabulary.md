# orcbrew value vocabulary — what a field may contain

**Purpose.** `HOMEBREW_REQUIRED_FIELDS.md` says which fields must be present. This document
says what may go *inside* them: the exact shape and keyword domain each field accepts, and
which of those the builder UI has no editor for. Every row is read from the consuming code
(file:line) and, where marked VERIFIED IN APP, driven through the running builder.

The starting-equipment keys are not duplicated here. The code that reads them is on this
branch — `starting_equipment_ledger.cljc` for the compact delta form,
`options.cljc:2583` for `:equipment-selections`, with round-trip coverage in
`starting_equipment_ledger_test.clj` and `starting_equipment_test.clj`. The prose walkthrough
of that feature is `docs/kb/starting-equipment.md`, which lives on
`refactor/content-extensibility` along with the rest of the knowledge base — `docs/kb/` does
not exist on integration or develop, so that link and `declarative-grant-vocabulary.md`
(a *proposed* future vocabulary — design, not format) resolve only there.

In this repository on any branch: `docs/ORCBREW_FILE_VALIDATION.md` for what import rejects
and how it repairs, and `docs/ORCBREW-AUTHORING.md` for the same ground written for a human
reader rather than for lookup.

## The container

A `.orcbrew` file is EDN, one map, source name to plugin:

```clojure
{"Example Source" {:orcpub.dnd.e5/classes   {:warden {…}}
                   :orcpub.dnd.e5/spells    {:wardens-mark {…}}}}
```

The silo keys are namespaced `:orcpub.dnd.e5/…`; every other key inside an item is bare
unless this document says otherwise. Each item repeats its own `:key` and carries
`:option-pack` naming its source.

A per-source export writes the **bare plugin** — the inner map only, no source-name wrapper.
Import reconstructs the source name from the items' `:option-pack`, and falls back to the
FILENAME when they disagree. Renaming such a file therefore renames the source.

## Two grant vocabularies, easily confused

A class or subclass can grant things two ways, with different shapes:

| | `:level-modifiers` | `:props` |
|---|---|---|
| Shape | `[{:type <kw> :level <int> :value <v>}]` | `{<prop-kw> <value>}`, usually `{item true}` |
| Level-gated | yes, per entry | no — applies from level 1 |
| Read by | `spell_subs.cljs:326` `level-modifier` | `options.cljc:3445` `make-feat-modifiers` |
| Editor | class/subclass builders | feat and race builders only |
| Also read on | classes, subclasses | races, subraces, **classes, subclasses** |

`:props` on a class or subclass is consumed (`spell_subs.cljs:610,646`) but has no editor
there — it is hand-authoring only, and survives export because the item map round-trips
verbatim. VERIFIED IN APP: a class carrying `:props {:initiative 2}` produced Initiative +4 on
a character whose Dexterity alone gives +2.

### `:level-modifiers` — the 12 types

The entry has exactly three keys. There is no `:num`, `:skill`, `:speed`, `:damage-type` or
`:spellcasting-ability`: everything the modifier needs goes in `:value`. An unrecognised
`:type` is logged to the console and the entry is dropped silently (`spell_subs.cljs:346`),
so a misspelling costs you the feature with no import error.

| `:type` | `:value` | Domain |
|---|---|---|
| `:weapon-prof` | keyword | `:simple`, `:martial`, or a weapon key |
| `:num-attacks` | integer | 2–4 |
| `:damage-resistance` | keyword | `opt/damage-types` (`options.cljc:124`) |
| `:damage-immunity` | keyword | same |
| `:saving-throw-advantage` | keyword | a *condition* — `opt/conditions` (`options.cljc:77`) |
| `:skill-prof` | keyword | `skills/skills` |
| `:armor-prof` | keyword | `:light` `:medium` `:heavy` `:shields` |
| `:tool-prof` | keyword | `equip/tools` |
| `:flying-speed` | integer | 10–120, step 10 |
| `:swimming-speed` | integer | 10–120, step 10 |
| `:flying-speed-equals-walking-speed` | — | takes no value |
| `:spell` | map | `{:level <int> :key <spell-kw> :ability :orcpub.dnd.e5.character/<abbr>}` |

Domains from `modifier-values` (`views.cljs:6160`), the table that also drives the builder's
own dropdowns. `:spell` grants a *known spell*, not spellcasting.

**`:saving-throw-advantage` computes but is never shown.** The modifier writes
`?saving-throw-advantage` (`modifiers.cljc:211`), which the template base declares
(`template_base.cljc:327`) and *nothing* reads: the accessor is commented out as dead with
zero callers (`character.cljc:620-622`), and no view, subscription or PDF path touches it. This
holds for every producer — the level-modifier type, the `:props` key, SRD race traits, magic
items. Authoring it is not an error; it simply has no visible effect, and the advantage has to
be written into a trait's description to reach the reader.

Note also the arity mismatch: `mod5e/saving-throw-advantage` takes a *collection* of types and
every SRD caller passes one (`[:poisoned]`), while the level-modifier dispatch forwards the
dropdown's bare keyword. Since nothing consumes the result, neither shape has been exercised.

### `:props` — the feat vocabulary

Scalars: `:initiative`, `:speed`, `:flying-speed`, `:swimming-speed`, `:max-hp-bonus`.
Flags: `:two-weapon-ac-1`, `:two-weapon-any-one-handed`, `:passive-investigation-5`,
`:passive-perception-5`, `:medium-armor-max-dex-3`, `:medium-armor-stealth`,
`:flying-speed-equals-walking-speed`, `:saving-throw-advantage-traps`, `:lizardfolk-ac`,
`:tortle-ac`.
Sets, written `{key true}`: `:language`, `:saving-throw-advantage`, `:skill-prof`,
`:skill-prof-or-expertise`, `:tool-prof-or-expertise`, `:armor-prof`, `:weapon-prof`,
`:damage-resistance`, `:damage-immunity`.

Note the collision: `:saving-throw-advantage` and `:damage-resistance` exist in *both*
vocabularies with different shapes — `{:type :damage-resistance :value :poison}` under
`:level-modifiers`, `{:damage-resistance {:poison true}}` under `:props`.

## Namespaced keys

Almost every keyword in a `.orcbrew` is bare. The exceptions are ability scores, which are
always `:orcpub.dnd.e5.character/str` … `/cha`:

- `:profs {:save {:orcpub.dnd.e5.character/con true}}` — VERIFIED IN APP. A bare `:con`
  here is accepted by import and silently grants nothing.
- `:spellcasting {:ability :orcpub.dnd.e5.character/wis}`
- `:level-modifiers [{:type :spell :value {:ability :orcpub.dnd.e5.character/wis}}]`

In the same `:profs` map, `:armor`, `:weapon`, `:skill-options` and `:tool-options` all take
bare keys. The rule is per-key, not per-map.

## `:traits`

`[{:name … :description … :level <int> :type <kw> :summary … :page … :conditions …}]`
(`modifiers.cljc:283`). `:type` is `:other` (default), `:action`, `:b-action` or `:reaction`,
and routes the trait into Actions / Bonus Actions / Reactions on the sheet. On a class or
subclass `:level` counts CLASS levels; on a race or background it counts total levels.

## `:spellcasting` on a class

`:level-factor` 1 (full), 2 (half) or 3 (third); `:known-mode` `:all`, `:schedule` or
`:acquire`; `:ability` namespaced; `:prepares-spells?`; `:cantrips?` with
`:cantrips-known {<level> <n>}`; `:spells-known` for `:schedule` mode.

The spell list is either borrowed or custom:
- `:spell-list-kw :ranger` — use that SRD class's list.
- `:spell-list {<level> #{<spell-keys>}}` — a custom list, a SET per level
  (`events.cljs:3737` `toggle-class-spell-list`).

Subclass slot-based spellcasting is gated to `#{:fighter :rogue :warlock :cleric :paladin}`
(`views.cljs:7012`). The reasoning behind that gate is in `decision-vocabulary.md`, which
lives on `refactor/content-extensibility` with the rest of the knowledge base.


## Verified end to end

A pack exercising every row below was imported into the running app and driven through the
builder to a level-9 character with the subclass selected. What appeared on the sheet:

| Authored | Result |
|---|---|
| `:hit-die 10` | hit-dice table "Warden (D10)", 9d10 |
| `:profs {:save {…/con …/wis}}` | CON +5, WIS +4 |
| `:profs {:armor …}` + subclass `:armor-prof :heavy` | "light, medium, shields, heavy" |
| `:level-modifiers :num-attacks 2` | "Number of Attacks 2" |
| `:level-modifiers :damage-resistance :poison` | "Damage Resistances poison" |
| `:level-modifiers :skill-prof :survival` | Survival proficient |
| `:level-modifiers :swimming-speed 30` | "30 ft. (swim)" |
| `:level-modifiers :spell` (Warden's Mark, WIS) | spell known, DC 12, attack 1d20+4 |
| `:spellcasting` level-factor 2 + custom `:spell-list` | half-caster slots 4/3/2, prepares 4/day |
| `:props {:initiative 2}` on the class | Initiative +4 |
| `:weapons` / `:armor` / `:equipment` | carried on the Equipment tab |
| `:weapon-choices` | "Starting Equipment: Primary Weapon — select 1" |
| `:traits` on class and subclass | listed under Features, Traits and Feats |
| `:subclass-level` / `:subclass-title` | subclass offered under "Warden's Oath" at level 3 |
| `:level-modifiers :saving-throw-advantage` | **nothing** — see the dead-end note above |
