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

### 2. The full "(a) or (b)+(c)" form — serializable, and IS a UI target
The SRD *source* hand-builds this with `opt5e/new-starting-equipment-selection` and
function calls (`mod5e/armor`, `mod5e/weapon`, …) — Fighter, `classes.cljc:1106-1151`.
That's the SRD's *code* representation; do not confuse it with "can't be data." The same
**semantics** are fully serializable via the `:equipment-selections` key (added on
`feat/starting-equipment`):
```clojure
:equipment-selections
[{:name "Armor"
  :options [{:name "Chain Mail" :grants [{:kind :armor :key :chain-mail :qty 1}]}
            {:name "Leather, Longbow, 20 Arrows"
             :grants [{:kind :armor :key :leather} {:kind :weapon :key :longbow}
                      {:kind :equipment :key :arrow :qty 20}]}]}
 {:name "Weapon"
  :options [{:name "A martial weapon and a shield"
             :grants [{:kind :armor :key :shield}]
             :choose [{:from :martial}]}]}]
```
`opt5e/class-equipment-selections` (`options.cljc`) compiles this to the exact
`new-starting-equipment-selection` structure the SRD uses — an option carries multiple
`:grants` (a bundle) and/or nested `:choose` sub-selections. So the complex form is
authorable in the UI and round-trips through `.orcbrew`; nothing is out of scope.

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
  **no spec change** to save/load. (Note: `:option-pack` really is required on import — a
  class without it is legitimately *skipped*; `reg-save-homebrew` supplies it from the
  Option Source field. Surfaced by the round-trip test below.) Validation metadata (`orcbrew_validation.cljs:121`)
  only knows `:name`. Optional hardening: validate that referenced item keys exist in
  `weapons-map`/`armor-map`/`equipment-map` (catch a `:longsord` typo), surfaced through
  the existing import/export validation.

## Builder UI (where it slots in)

`class-builder` is hand-written hiccup (`views.cljs`), reading `::classes/builder-item`
and dispatching per-field events (`set-class-prop` / `set-class-path-prop`,
`events.cljs:3387`/`3262`). It had **no** equipment (or weapon/armor-proficiency)
section.

**Implemented** (`feat/starting-equipment`): a `starting-equipment-section` in
`views.cljs` — fixed-grant blocks (writing `:weapons`/`:armor`/`:equipment`) plus a
**rich choices** builder (group → option → one-or-more grants + optional weapon
sub-choice) writing `:equipment-selections`. Both go through one setter,
`::class5e/set-equipment` (`events.cljs`), which drops a key when its map/vector empties
so exports stay clean; `::class5e/migrate-equipment-choices` one-click-converts legacy
shorthand `:*-choices` into the editable rich form. `default-class` is left untouched (absent
keys read as empty). Regression coverage: `test/clj/orcpub/starting_equipment_test.clj`
(consumption + EDN round-trip), the `set-equipment` cases in
`test/cljs/orcpub/dnd/e5/events_test.cljs`, and
`test/cljs/orcpub/dnd/e5/starting_equipment_roundtrip_test.cljs` — a full `.orcbrew`
export (`strip-export-blanks` + text) → real import (`validate-import`) → re-apply
(`class-option`) across fixed-only, choice + pseudo-key, and mixed multi-group configs;
and `test/browser/starting_equipment_browser_e2e.js` — a headless-chromium drive of the
real app: renders the section, clicks the equipment buttons, saves, captures a real
`.orcbrew` **download**, and re-imports that file into a fresh library (also exercises
the source-name-choice modal).
Still open: import/export validation that referenced item keys exist in the vocab
(catches hand-edited typos).

## Start from an SRD class + the override delta

"Start from an SRD class" fills the builder with an SRD class's equipment, and — if the
user only tweaks it — the export stores just the **delta from that base** instead of a full
copy.

- **Deriving the base.** `srd_starting_equipment.cljc` `builder-equipment` **decompiles** a
  live class into the editable full form by reading its built `class-option` output and
  recovering each choice grant by *applying the modifier fn* (no hand-transcribed table).
  Round-trip-verified against all 12 SRD classes in `starting_equipment_test.clj`.
- **The base marker.** Filling from a class stamps `:starting-equipment-base <class-kw>` on
  the class map (`::class5e/fill-starting-equipment`, `events.cljs`). It's a live-only
  breadcrumb; the builder keeps editing the plain full form.

### On-disk delta format (data integrity)

The delta is a **pure on-disk encoding — it exists ONLY in exported files.** Everything live
(app-db, localStorage, the builder) holds the full `:weapons`/`:armor`/`:equipment`/
`:equipment-selections` form the existing functions consume, so the consumption path is
untouched. A collapsed class carries:

```
:starting-equipment {:base <srd-class-kw>
                     :fixed  {:set {<bucket> {<item-key> qty}}   ; added / qty-changed items
                              :del {<bucket> #{<item-key>}}}      ; removed items
                     :groups {:set {<group-name> <whole-group>}  ; a TOUCHED choice group, stored whole
                              :del #{<group-name>}}}              ; removed group names
```

Addressing is by identifiers that **already exist** — item keys for fixed grants, `:name`
for choice groups — so there are **no minted keys**. A touched choice group is stored whole
(the nested OR-menus are rarely edited and not worth a sub-diff). See
`starting_equipment_ledger.cljc` (`derive-delta` / `resolve-delta`) and
`docs/kb/starting-equipment-override-ledger.md` for the design and rejected alternatives.

- **Collapse on export / expand on import.** `sel/collapse-class` runs at the single
  serialize funnel (`save-orcbrew-blob!`) plus the two direct-blob export bypasses;
  `sel/expand-class` runs at both ingestion points (`store-imported-sources`,
  `apply-shared-content`) **before** the load-floor spec gate, and defensively at the
  `::classes5e/plugin-classes` consumption sub — so the library only ever holds full form
  and a missed ingestion path can't silently drop equipment.
- **Fail-soft.** An unknown `:base`, or a delta entry that doesn't line up with the base,
  leaves the class untouched / appends rather than dropping equipment or crashing.
- **UI.** When a base is set, `starting-equipment-section` shows a `.bg-warning` callout
  ("Based on the &lt;Class&gt; class …") with a **Detach** button
  (`::class5e/detach-starting-equipment-base`) that drops the link so export writes the
  equipment in full; the equipment itself is untouched.
- **Coverage.** `test/clj/orcpub/starting_equipment_ledger_test.clj` (derive/resolve
  round-trip over every edit kind + whole-class collapse/expand + fail-soft) and
  `test/browser/starting_equipment_ledger_e2e.js` (live runtime: fill → collapse to a
  delta-only file → `validate-import` accepts → expand restores the full equipment → the
  banner + Detach behave).
