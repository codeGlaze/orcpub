# Starting equipment — data shape, consumption, and the homebrew-builder path

**Purpose.** How a class grants starting equipment in orcpub, and what a homebrew
class-builder UI has to write for custom classes to grant equipment the way SRD
classes do. Findings are verified by `test/clj/orcpub/starting_equipment_test.clj`
(runs on the JVM against the real `class-option`). Written for the
`feat/starting-equipment` work; consolidate to `agents/develop`.

## The one thing that makes this cheap

Homebrew classes compile to real character options through the **same**
`opt5e/class-option` that SRD classes use (`src/cljs/orcpub/dnd/e5/spell_subs.cljs:1111`,
`plugin-class` → `class-option`). `class-option` already destructures and consumes the
starting-equipment keys (`src/cljc/orcpub/dnd/e5/options.cljc:2915`, keys listed at the
top of the arglist). **So the builder only has to write those keys onto the class map —
there is no new runtime wiring, and the whole builder-item persists verbatim, so it
round-trips through save/import/export untouched.** Verified: feeding a class map with
`:weapons`/`:equipment`/`:weapon-choices` through `class-option` produces the flagged
equipment grants and the choice selection (see the test).

## Two ways to express equipment on a class map

### 1. Shorthand keys — plain data, serializable, the UI target
Consumed directly by `class-option`. This is what SRD classes and manual `.orcbrew`
edits use for the common cases.

- **Fixed grants** — `item-key → quantity` maps:
  - `:weapons {:javelin 4}` (Barbarian, `classes.cljc:68`)
  - `:armor {:leather 1}` (Druid, `classes.cljc:779`)
  - `:equipment {:explorers-pack 1}` (Barbarian, `classes.cljc:69`; Wizard `{:spellbook 1}`)
- **Choice groups** — vector of `{:name .. :options {item-key qty ...}}`:
  - `:weapon-choices [{:name "Martial Weapon" :options {:greataxe 1 :martial 1}}]` (Barbarian `classes.cljc:62`)
  - `:armor-choices`, `:equipment-choices` (Fighter `classes.cljc:1072`, Druid `classes.cljc:780`)
- **Weapon pseudo-keys** `:simple` / `:martial` mean "any simple/martial weapon" and
  expand to a nested sub-selection (`options.cljc:2423`).

### 2. Hand-built `:selections` — NOT a UI target
The fully-expressive "(a) chain mail or (b) leather + longbow + 20 arrows" form
(Fighter, `classes.cljc:1106-1151`) uses `opt5e/new-starting-equipment-selection` with
**function-valued** `:modifiers` (`mod5e/armor`, `mod5e/weapon`, …). These do not
serialize to `.orcbrew`, so they cannot come from a builder UI safely. Even SRD
hand-codes them. **Out of scope for the builder** — note it in the UI as "advanced,
edit the file directly."

## How consumption works (what to expect on the character)

`class-option` emits two things (`options.cljc:2977-3004`):

- **Fixed grants → `::t/associated-options`.** `class-starting-equipment-entity-options`
  (`options.cljc:2351`) → `eh/starting-equipment-entity-options`
  (`event_handlers.cljc:37`) produces entity entries:
  ```clojure
  #:orcpub.entity{:key :javelin
                  :value #:orcpub.dnd.e5.character.equipment{:quantity 4
                                                             :equipped? true
                                                             :class-starting-equipment? true}}
  ```
  The `::char-equip/class-starting-equipment?` flag (spec `character/equipment.cljc:9`)
  lets `char5e/remove-starting-equipment` (`character.cljc:835`) strip and re-grant on a
  class change. (Backgrounds have the parallel `::background-starting-equipment?`.)
- **Choice groups → `::t/selections`** tagged `#{:equipment :starting-equipment}`, with a
  `:prereq-fn (first-class? …)` so equipment is only offered to the first class. The
  selection name is `"Starting Equipment: <group name>"`.

Note: the entity keyword namespace is **`orcpub.entity`** (`:orcpub.entity/key`), not
`orcpub.dnd.e5.entity` — an easy trap when walking the built option in a test.

## Vocabulary a builder UI picks from

- **Weapons** — `weapons.cljc`: `weapons-map` (key→map, `:422`); helpers `simple-weapons`
  / `martial-weapons` (`:431`/`:428`); plus the `:simple`/`:martial` pseudo-keys.
- **Armor** — `armor.cljc`: `armor-map` (`:97`); `shields` / `non-shields` (`:100`).
- **Equipment / packs / gear / tools** — `equipment.cljc`: `equipment-map` (`:338`).
  Packs carry a nested `:items {k n}` map (`:242`) that `equipment-option`
  (`options.cljc:2477`) fans out into individual grants. Grouped "chooser" entries
  (`:holy-symbol`, `:druidic-focus`, `:arcane-focus`, `:pack`, `:musical-instrument`)
  carry `:values` and expand to a sub-selection.

## Save / validation

- `reg-save-homebrew "Class"` (`events.cljs:962`) persists the **entire** builder-item map
  to `plugins[option-pack][:orcpub.dnd.e5/classes][class-key]`. New equipment keys survive
  automatically.
- The class spec `::classes/homebrew-class` (`classes.cljc:21`) only *requires*
  `:name :key :option-pack` and permits arbitrary extra keys — so the equipment keys need
  **no spec change** to save/load. Validation metadata (`orcbrew_validation.cljs:121`)
  only knows `:name`. Optional hardening: validate that referenced item keys exist in
  `weapons-map`/`armor-map`/`equipment-map` (catch a `:longsord` typo), surfaced through
  the existing import/export validation.

## Builder UI (where it slots in)

`class-builder` is hand-written hiccup (`views.cljs:6289-6554`), reading
`::classes/builder-item` and dispatching per-field events (`set-class-prop` /
`set-class-path-prop`, `events.cljs:3387`/`3262`). It has **no** equipment (or
weapon/armor-proficiency) section today. A "Starting Equipment" section slots after
skills (~`views.cljs:6521`), writing the shorthand keys above. `default-class`
(`db.cljs:130`) gains the (empty) equipment keys.
