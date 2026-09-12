# `:level-modifiers` and `:props` — the value vocabulary a homebrew item can use

What a homebrew class, subclass, race or feat may put in its grant keys, and what the app
does with each. Written because a class whose every `:level-modifiers` entry is misspelled
imports clean, saves clean, loads clean, builds a character, and grants nothing — there is
no error anywhere in that sequence.

Line references are against `integration` at `36766010`. Live/discarded status was checked
with `scripts/clj-grep.py`.

Spellcasting configuration is **not** covered here — see `homebrew-class-spellcasting.md`
for `:level-factor`, `:known-mode`, slot schedules and pact magic.

## Nothing validates the shape of a brew

Three layers look like validation and none of them checks a value:

| Layer | Where | What it actually does |
|---|---|---|
| Import | `orcbrew_validation.cljs` `required-fields` | Fills a missing `:name` with a placeholder. Spells also need a numeric `:level` and a `:school`; subclasses and subraces need their parent ref. That is the entire list. |
| Save | `content_specs.cljc` `save-specs` | Presence of `:name`, `:key`, `:option-pack`. Presence only. |
| Load | `content_specs.cljc` `load-item-spec` | An `:option-pack` string on the item. The letter-leading-key rule is on the map key the item is filed under (`::e5/homebrew-items`), not on its own `:key`. |

The looseness is deliberate and load-bearing: `content_specs.cljc` keeps save strict and load
loose, with a generative test proving `save ⊆ load`, so tightening a builder's save spec can
never quarantine content a user already has. Read that namespace's docstring before proposing
to change it.

The consequence for tooling is that the spec registry is a *presence* contract, not a shape
contract, and there are no value specs registered for `:level-modifiers`, `:profs`,
`:spellcasting`, `:traits` or `:equipment-selections`. A linter built on the specs would
accept almost anything.

## Two grant vocabularies, with different shapes

| | `:level-modifiers` | `:props` |
|---|---|---|
| Shape | `[{:type <kw> :level <int> :value <v>}]` | `{<prop-kw> <value>}`, usually `{item true}` |
| Level-gated | yes, per entry | no — applies from level 1 |
| Read by | `spell_subs.cljs:326` `level-modifier` | `options.cljc:3445` `make-feat-modifiers` |
| Editor exists for | classes, subclasses | feats, races |
| Actually consumed on | classes, subclasses | races, subraces, **classes, subclasses** |

`:props` on a class or subclass is consumed at `spell_subs.cljs:610,646` with no editor
there. Verified in the running app: a class carrying `:props {:initiative 2}` produced
Initiative +4 on a character whose Dexterity alone gives +2.

Two names appear in both vocabularies with different shapes — `:damage-resistance` and
`:saving-throw-advantage`. Copying a line from one to the other silently does nothing.

## The twelve `:level-modifiers` types

The entry has exactly three keys. There is no `:num`, `:skill`, `:speed` or `:damage-type`:
everything the modifier needs goes in `:value`. An unrecognised `:type` is logged to the
console and the entry dropped (`spell_subs.cljs:346`), so a misspelling costs the feature
with no import error.

| `:type` | `:value` | Domain |
|---|---|---|
| `:weapon-prof` | keyword | `:simple`, `:martial`, or a weapon key (40 in `weapons.cljc`) |
| `:num-attacks` | integer | 2–4 |
| `:damage-resistance` | keyword | `opt/damage-types` (12, `options.cljc:124`) |
| `:damage-immunity` | keyword | same |
| `:saving-throw-advantage` | keyword | a *condition* — `opt/conditions` (15, `options.cljc:77`) |
| `:skill-prof` | keyword | `skills/skills` (19) |
| `:armor-prof` | keyword | `:light` `:medium` `:heavy` `:shields` |
| `:tool-prof` | keyword | `equip/tools` — a concat of instruments, artisan's tools, misc, gaming sets, vehicles |
| `:flying-speed` | integer | 10–120, step 10 |
| `:swimming-speed` | integer | 10–120, step 10 |
| `:flying-speed-equals-walking-speed` | — | takes no value |
| `:spell` | map | `{:level <int> :key <spell-kw> :ability :orcpub.dnd.e5.character/<abbr>}` |

Domains from `modifier-values` (`views.cljs:6160`), which also drives the builder's own
dropdowns. `:spell` grants a known spell, not spellcasting.

### `:saving-throw-advantage` computes and is then read by nothing

The modifier writes `?saving-throw-advantage` (`modifiers.cljc:211`), the template base
declares it (`template_base.cljc:327`), and no view, subscription or PDF path reads it — the
accessor is commented out as dead with zero callers (`character.cljc:620-622`). This holds
for every producer: the level-modifier type, the `:props` key, SRD race traits, magic items.
Authoring it is not an error; it has no visible effect, and the advantage has to go in a
trait description to reach the reader.

Note also an arity mismatch nothing has exercised: `mod5e/saving-throw-advantage` takes a
collection and every SRD caller passes one (`[:poisoned]`), while the level-modifier dispatch
forwards the dropdown's bare keyword.

## `:props` — the feat vocabulary

Scalars: `:initiative`, `:speed`, `:flying-speed`, `:swimming-speed`, `:max-hp-bonus`.

Flags: `:two-weapon-ac-1`, `:two-weapon-any-one-handed`, `:passive-investigation-5`,
`:passive-perception-5`, `:medium-armor-max-dex-3`, `:medium-armor-stealth`,
`:flying-speed-equals-walking-speed`, `:saving-throw-advantage-traps`, `:lizardfolk-ac`,
`:tortle-ac`.

Sets, written `{key true}`: `:language`, `:saving-throw-advantage`, `:skill-prof`,
`:skill-prof-or-expertise`, `:tool-prof-or-expertise`, `:armor-prof`, `:weapon-prof`,
`:damage-resistance`, `:damage-immunity`.

## Ability keys are namespaced; nothing else is

The most expensive typo in the format. Ability scores are always
`:orcpub.dnd.e5.character/str` … `/cha`:

```clojure
:profs {:save   {:orcpub.dnd.e5.character/con true}   ; proficient
        :armor  {:light true}                          ; bare
        :weapon {:simple true}}                        ; bare
```

Write `:con` and the file imports without complaint, the class builds, and the character
never gains the save. Same rule for `:spellcasting`'s `:ability` and the `:ability` inside a
`:spell` level-modifier. The rule is per-key, not per-map.

## `:traits`

`[{:name … :description … :level <int> :type <kw> :summary … :page … :conditions …}]`
(`modifiers.cljc:283`). `:type` is `:other` (default), `:action`, `:b-action` or `:reaction`,
routing the trait into Actions / Bonus Actions / Reactions on the sheet. On a class or
subclass `:level` counts CLASS levels; on a race or background, total levels.

A hand-written `:levels` key on a class is discarded — `plugin-class` overwrites it with the
result of compiling `:level-modifiers` and `:traits` (`spell_subs.cljs:646`). `:levels` is the
compiled form, `:level-modifiers` the authoring form.

## Verified in the running app

A pack exercising every row was imported and driven to a level-9 character with the subclass
selected:

| Authored | Result on the sheet |
|---|---|
| `:hit-die 10` | hit-dice table "Warden (D10)", 9d10 |
| `:profs {:save {…/con …/wis}}` | CON +5, WIS +4 |
| `:profs {:armor …}` + subclass `:armor-prof :heavy` | "light, medium, shields, heavy" |
| `:num-attacks 2` | "Number of Attacks 2" |
| `:damage-resistance :poison` | "Damage Resistances poison" |
| subclass `:damage-immunity :cold` | "Damage Immunities cold" |
| `:skill-prof :survival` | Survival proficient |
| `:swimming-speed 30` | "30 ft. (swim)" |
| `:spell` (Warden's Mark, WIS) | spell known, DC 12, attack 1d20+4 |
| `:props {:initiative 2}` on the class | Initiative +4 |
| `:weapons` / `:armor` / `:equipment` | carried on the Equipment tab |
| `:weapon-choices` | "Starting Equipment: Primary Weapon — select 1" |
| `:traits` on class and subclass | listed under Features, Traits and Feats |
| `:saving-throw-advantage :poisoned` | **nothing** — see the dead-end note above |

The example pack itself is the closing section of `docs/ORCBREW-AUTHORING.md` on the code
branch, written for a human audience.

## Races

Races use `:props`, not `:level-modifiers` — nothing gates a race grant by level. The widget
level detail is already recorded: `builder-disposition-audit.md` on `feature/grant-rows`
audits all 19 race widgets, what each writes and what reads it, measured 2026-09-07. Read
that before touching the race builder; it is not repeated here.

One thing it does not cover, because it audits what the builder writes rather than what the
app accepts. A race can grant armor and weapon proficiency **two different ways**:

```clojure
:props {:armor-prof {:light true}}   ; what the builder writes
:armor-proficiencies [:light]        ; top-level, consumed but never written by a widget
```

`race-option` destructures `:armor-proficiencies` and `:weapon-proficiencies` and passes them
straight to `armor-prof-modifiers` / `weapon-prof-modifiers` (`options.cljc:2268`, body around
`:2330`), reaching the same modifiers the `:props` route reaches through `plugin-modifiers`.
Verified by reading the consumer, not driven in the app. `:icon` is likewise consumed and
unwritten.

## What is not known

Six of the thirteen content types have no write-up: subraces, monsters, encounters,
selections, invocations, boons.
