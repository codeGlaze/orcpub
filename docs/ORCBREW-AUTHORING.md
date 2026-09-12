# Writing an .orcbrew by hand

The homebrew builders in OrcPub write `.orcbrew` files, and for most content that is the
only tool you need. But the builders expose a *subset* of what the app can read. Starting
equipment is the standing example: classes granted it for years through keys that only ever
existed in hand-edited files, long before a builder learned to write them. That gap is not
unusual, and it is not documented anywhere the builders can show you.

This document is for the developer who has a `.orcbrew` open in an editor and wants to know
what the app will actually do with what they type. It describes the format as the code reads
it today, not as it might be reorganised later.

## 1. The container

An `.orcbrew` file is EDN: a single map from source name to that source's content.

```clojure
{"Example Source"
 {:orcpub.dnd.e5/classes {:warden {:key :warden :name "Warden" …}}
  :orcpub.dnd.e5/spells  {:wardens-mark {…}}}}
```

The silo keys — `:orcpub.dnd.e5/classes`, `/subclasses`, `/races`, `/subraces`,
`/backgrounds`, `/feats`, `/spells`, `/languages`, `/monsters`, `/encounters`,
`/selections`, `/invocations`, `/boons` — are namespaced. Inside an item, every key is bare
unless section 4 says otherwise.

Each item is keyed by its own `:key`, repeats that `:key` inside itself, and names its source
in `:option-pack`. Those three must agree; import repairs some disagreements and quarantines
the rest.

**Exports of a single source are not wrapped.** The per-source EXPORT button writes the inner
plugin map alone, with no source-name key around it. On import the app reconstructs the name
from the items' `:option-pack` — and when those disagree, from the *filename*. Renaming such
a file can therefore rename the source it creates. A whole-library export is wrapped and has
no such dependency.

## 2. How the app reads one

Import parses the EDN, validates each item, and merges the survivors into the library that
lives in browser local storage. Nothing is compiled at this point; a bad item is quarantined
here, with a reason, rather than breaking the file.

Compilation happens when a character is built. Homebrew classes go through
`opt5e/class-option` — the *same* function the SRD classes go through
(`spell_subs.cljs` `plugin-class`). This is the single most useful fact about the format:
there is no separate homebrew engine to satisfy. If the SRD can express something, the
question is only whether there is a data key that reaches the same code.

Before `class-option` sees a homebrew class, two things are compiled onto it:
`:level-modifiers` and `:traits` become the `:levels` map that SRD classes write by hand, and
`:props` becomes a modifier list. So `:levels` is the *compiled*
form and `:level-modifiers` the *authoring* form. A `:levels` key written into a file by hand
is not merged — it is overwritten by the compiled result and has no effect.

## 3. Two ways to grant something

A class or subclass can grant abilities through either of two keys, and they do not share a
shape.

`:level-modifiers` is a vector of level-gated entries with exactly three keys:

```clojure
:level-modifiers [{:level 6 :type :skill-prof :value :survival}]
```

Everything the modifier needs goes in `:value`. There is no `:skill`, `:num`, `:speed` or
`:damage-type` key — inventing one produces an entry whose `:value` is `nil`, and the app
applies it as such. An unrecognised `:type` is worse: it is logged to the browser console and
dropped, so the file imports cleanly and the feature simply never appears.

The twelve types and their value domains are tabulated in
the value-vocabulary reference on the knowledge-base branch
(`git show agents/develop:docs/kb/orcbrew-value-vocabulary.md`). The short version is that proficiencies and
resistances take a single keyword, speeds and attack counts take an integer, and `:spell`
takes a map of `{:level :key :ability}`.

`:props` is the other vocabulary — a flat map, not level-gated, written as `{prop {value
true}}` for the set-like properties and as a scalar for the rest:

```clojure
:props {:skill-prof {:perception true} :passive-perception-5 true}
```

Two names appear in both vocabularies with different shapes: `:damage-resistance` and
`:saving-throw-advantage`. Under `:level-modifiers` they are a `:type`/`:value` pair; under
`:props` they are a map of flags. Copying a line from one to the other silently does nothing.

## 4. Ability scores are namespaced; nothing else is

The single most expensive typo in the format is a bare ability keyword. Ability scores are
always `:orcpub.dnd.e5.character/str` through `/cha`:

```clojure
:profs {:save   {:orcpub.dnd.e5.character/con true    ; proficient
                 :orcpub.dnd.e5.character/wis true}
        :armor  {:light true :medium true}            ; bare
        :weapon {:simple true :martial true}}         ; bare
```

Write `:con` instead and the file imports without complaint, the class builds, and the
character never gains the save. The same rule governs `:spellcasting`'s `:ability` and the
`:ability` inside a `:spell` level-modifier.

## 5. What the builders will not write for you

These are read by the app and preserved through export, but have no editor:

- **`:props` on a class or subclass.** Consumed at `spell_subs.cljs:610,646`. The feat and
  race builders write `:props`; the class builders do not, though the class consumes it.
- **`:equipment-selections`** — the rich "(a) chain mail, or (b) leather and a longbow and 20
  arrows" form, with bundled `:grants` and nested `:choose`. Consumed by
  `class-equipment-selections` (`options.cljc:2583`); the shape is commented in full just
  above it.
- **`:starting-equipment {:base <srd-class> …}`** — the compact delta form written by export
  when a class was filled from an SRD base, expanded again on import.
- **A custom `:spell-list`** on a class that also wants cantrips: the list is a set per level,
  `{1 #{:wardens-mark}}`.

Hand-authored keys survive export because an item's map round-trips verbatim. Whatever you
add that the app does not read is carried along untouched — which is a convenience, and also
why a misspelled key never announces itself.

## 6. A complete example

Everything below was imported into the app and driven through the builder to a level-9
character with the subclass chosen. The pack runs from the simplest thing a source can
contain — a language, which is a name and a description — up to a class that grants
proficiencies, level-gated modifiers, starting equipment with a choice, half-caster
spellcasting off its own spell list, and a subclass of its own.

```clojure
{"Example Source"

 ;; The floor. A language is a name, a key, and its source.
 {:orcpub.dnd.e5/languages
  {:old-marches
   {:key :old-marches :name "Old Marches" :option-pack "Example Source"
    :description "The clipped trade-tongue of the border holds."}}

  ;; A spell. :spell-lists puts it on SRD class lists; the class below also
  ;; names it directly, which is how a class reaches a spell on no SRD list.
  :orcpub.dnd.e5/spells
  {:wardens-mark
   {:key :wardens-mark :name "Warden's Mark" :option-pack "Example Source"
    :level 1 :school "abjuration"
    :casting-time "1 bonus action" :range "60 feet"
    :components {:verbal true :somatic true}
    :duration "Concentration, up to 1 minute"
    :description "You mark a creature you can see. Until the spell ends, you know its direction and distance."
    :spell-lists {:druid true :ranger true}}}

  :orcpub.dnd.e5/classes
  {:warden
   {:key :warden :name "Warden" :option-pack "Example Source"
    :help "A guardian sworn to a place rather than a lord."
    :hit-die 10
    :ability-increase-levels [4 8 12 16 19]
    :subclass-level 3
    :subclass-title "Warden's Oath"

    ;; Saves take NAMESPACED ability keys. Everything else here is bare.
    :profs {:save {:orcpub.dnd.e5.character/con true
                   :orcpub.dnd.e5.character/wis true}
            :armor {:light true :medium true :shields true}
            :weapon {:simple true :martial true}
            :skill-options {:choose 2 :options {:athletics true :insight true
                                                :nature true :perception true
                                                :survival true}}
            :tool-options {:choose 1 :options {:herbalism-kit true}}}

    ;; Starting equipment: fixed grants, then one choice group. :martial in a
    ;; choice means "any martial weapon" and opens a nested pick.
    :weapons {:javelin 4}
    :armor {:leather 1}
    :equipment {:explorers-pack 1}
    :weapon-choices [{:name "Primary Weapon" :options {:martial 1 :greataxe 1}}]

    ;; The feat/race vocabulary. Classes consume it; no class builder writes it.
    :props {:initiative 2 :skill-prof {:stealth true}}

    :traits [{:name "Rooted Stance" :level 1
              :description "While you remain within 5 feet of where you began your turn, you have advantage on saves against forced movement."}]

    ;; What SRD classes express as code, homebrew expresses here as data.
    ;; Three keys per entry, always: :level, :type, :value.
    :level-modifiers [{:level 2 :type :num-attacks :value 2}
                      {:level 2 :type :damage-resistance :value :poison}
                      ;; Computed, but nothing in the app displays it — put the
                      ;; advantage in a trait description if a reader must see it.
                      {:level 5 :type :saving-throw-advantage :value :poisoned}
                      {:level 6 :type :skill-prof :value :survival}
                      {:level 7 :type :swimming-speed :value 30}
                      {:level 9 :type :spell
                       :value {:level 1 :key :wardens-mark
                               :ability :orcpub.dnd.e5.character/wis}}]

    ;; Half caster (:level-factor 2) off a list of its own. The list is a SET
    ;; of spell keys per spell level.
    :spellcasting {:level-factor 2
                   :known-mode :all
                   :prepares-spells? true
                   :ability :orcpub.dnd.e5.character/wis
                   :spell-list {1 #{:wardens-mark}}}}}

  ;; A subclass names its parent in :class. Its :level-modifiers run on the
  ;; same dispatch as the class's, with no gate.
  :orcpub.dnd.e5/subclasses
  {:oath-of-the-standing-stone
   {:key :oath-of-the-standing-stone :name "Oath of the Standing Stone"
    :option-pack "Example Source" :class :warden
    :help "Wardens bound to a single monument."
    :traits [{:name "Stonebound" :level 3
              :description "You always know the direction and distance to your bound site."}]
    :level-modifiers [{:level 3 :type :armor-prof :value :heavy}
                      {:level 7 :type :damage-immunity :value :cold}]}}

  :orcpub.dnd.e5/backgrounds
  {:hedge-warden
   {:key :hedge-warden :name "Hedge Warden" :option-pack "Example Source"
    :help "You kept a boundary that mattered to someone."
    :profs {:skill {:nature true :survival true}}
    :equipment {:explorers-pack 1 :rope-hempen 1}
    :equipment-choices [{:name "Keepsake" :options {:signet-ring 1 :pouch 1}}]
    :treasure {:gp 10}}}

  ;; A feat is the one silo whose whole mechanical payload is :props.
  :orcpub.dnd.e5/feats
  {:boundary-sense
   {:key :boundary-sense :name "Boundary Sense" :option-pack "Example Source"
    :description "Your attunement to thresholds sharpens your senses."
    :props {:skill-prof {:perception true} :passive-perception-5 true}}}}}
```

Imported, this produces six items under "Example Source". Built to level 9 with the Oath
chosen, the character shows two attacks, resistance to poison, immunity to cold, heavy armour
proficiency, a 30-foot swim speed, Initiative +4, Warden's Mark known at DC 12, half-caster
slots of 4/3/2, and both traits under Features.

The one line that produces nothing visible is the `:saving-throw-advantage` modifier, and that
is a property of the app rather than of the file: the value is computed onto the character and
no view, subscription or PDF path reads it. The value-vocabulary reference on
`agents/develop` records the trace.

## 7. Writing tools against the format

Everything above is enough to write a brew by hand. Writing a *program* against the format —
an editor linter, an alternative builder, a converter — needs one more thing said plainly:

**Nothing in the app validates the shape of a brew.** There are three layers of checking and
none of them is a schema.

| Layer | Where | What it actually does |
|---|---|---|
| Import | `orcbrew_validation.cljs` `required-fields` | Fills a missing `:name` with a placeholder. Spells additionally need a numeric `:level` and a `:school`; subclasses and subraces need their parent ref. That is the whole list. |
| Save | `content_specs.cljc` `save-specs` | Presence of `:name`, `:key`, `:option-pack` — per type, and only presence. |
| Load | `content_specs.cljc` `load-item-spec` | An `:option-pack` string on the item. Separately, the key each item is filed *under* in its silo map must start with a letter (`::e5/homebrew-items`) — that is the map key, not the item's own `:key` field. |

So a class whose every `:level-modifiers` entry is misspelled imports clean, saves clean,
loads clean, and grants nothing. **That gap is the reason to write a linter**, and it is also
why you cannot derive one from the specs.

### Do not build on the spec registry

`spec/def ::homebrew-class` and its siblings look like the schema and are not. They are
`:req-un` *presence* contracts, and there are no value specs registered for `:level-modifiers`,
`:profs`, `:spellcasting`, `:traits` or `:equipment-selections` — so `spec/keys` has nothing to
check even when those keys are present.

The looseness is deliberate, not an oversight. `content_specs.cljc` keeps save strict and load
loose, with a generative test (`content_specs_test.clj`) proving `save ⊆ load`, so that
tightening a builder's save spec can never quarantine content a user already had. Read that
namespace's docstring before proposing any change to it: the permissiveness is load-bearing.

What this means for you is simply that the specs answer "is this item storable", not "is this
item correct". Build against the consuming code instead — the tables in
the value-vocabulary reference on `agents/develop` cites it line by line.

### The closed sets

A linter's real work is checking keywords against the enumerations the app resolves against.
These are the ones a brew can reference:

| Set | Count | Defined in |
|---|---|---|
| Damage types | 12 | `options.cljc` `damage-types` |
| Conditions | 15 | `options.cljc` `conditions` |
| Abilities | 6 | `options.cljc` `abilities` — namespaced keys |
| Skills | 19 | `skills.cljc` |
| Weapons | 40 | `weapons.cljc`, plus `:simple` / `:martial` as class pseudo-keys |
| Armor | 14 | `armor.cljc`, plus `:light` / `:medium` / `:heavy` / `:shields` as proficiency keys |
| Equipment | 50 | `equipment.cljc` |
| Tools | — | `equipment.cljc` `tools`, a concat of instruments, artisan's tools, misc, gaming sets and vehicles |
| Spell schools | 7 | `spells.cljc` `schools` — **strings**, not keywords |

Spell schools are the one place the format uses a string where everything around it uses a
keyword. `:school "abjuration"` is correct; `:school :abjuration` is not.

### What is not documented yet

This file covers six of the thirteen content types the app accepts. **Races, subraces,
monsters, encounters, selections, invocations and boons** have no equivalent write-up — their
keys have to be read out of `options.cljc` and the matching builder. Races are the significant
gap: they are commonly homebrewed and they carry their own grant vocabulary (`:props`,
`:spells`) rather than the class one.

