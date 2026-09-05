# Builder form schemas — the reference

**Most of this already exists.** The schema system was built in June 2026:

| | |
|---|---|
| `f2454a08` 2026-06-14 | collapse per-type builder forms into `simple-content-builder` |
| `8058b55f` 2026-06-15 | declarative builder field-schema |
| `8a07531e` 2026-06-15 | generate the save spec from the field schema |
| `0fe18de4` 2026-06-15 | sync import/export verification with the field schema |

So §1 and §2a describe **shipped** machinery, not a proposal. What is genuinely new here: the shared
`:props` field fragments (`ac-bonus-fields`, `attack-bonus-fields`, `damage-bonus-fields`),
`flatten-fields`, the corrected survey in §5, and the `:group` node in §2b — **§2b alone is
unbuilt.**

## 0. Three tiers of content type

The single most useful thing to know before converting anything — a type's tier tells you whether
the work is already done, is a fragment away, or is unsolved.

| tier | needs | examples | status |
|---|---|---|---|
| **1** | name + source + description | boon, invocation, language | **solved.** `simple-content-builder`, one line per type (June 2026) |
| **2** | + mechanics that *do* something | **fighting style**, feat, a homebrew race wanting +1 AC | **solved.** A shared `:props` fragment plus a compiler arm |
| **3** | collections, conditionals, bespoke widgets | encounter, monster, class | **UNSOLVED.** No collection node; conditionals and domain selectors stay hand-written |

The two efforts sit on different axes and are easy to confuse:

- **June 2026** made *forms* data, **per type**. Before it, `builder_fields.cljc` held only helpers
  and `field_schemas.cljc` registered exactly one schema. A schema described one type and nothing
  else could use it.
- **This branch** made *mechanics* shared data, **across types**. `ac-bonus-fields` is not a
  fighting-style field; it drops into any of the seven `:props` silos unchanged, and the prop it
  writes compiles to real AC in all of them.

Tier 2 is what the June work did not reach, because no type then had enough mechanical fields to
need it — which is also why tier 2 exposed a problem tier 1 never could: three bonus fragments
expand to 19 flat fields with seven duplicated labels. See §2b.

This is the document to read before adding anything to a builder form. If you cannot find your case
under **HOW TO** below, that is a bug in this document — say so rather than inventing a mechanism.

---

## 1. The model, in one page

Three layers. Keep them straight and everything else follows.

| layer | what it is | where it lives |
|---|---|---|
| **schema** | data describing a type's fields | `builder_fields.cljc`, per-type nss |
| **widgets** | the components that render a field | `views.cljs` (`render-builder-field`) |
| **props** | the mechanical vocabulary a field writes into | `options.cljc` (`make-feat-modifiers`) |

A field says *what to collect*. A widget says *how it looks*. A prop says *what it does to a
character*. Changing one should not require changing the others — when it does, that is the bug.

The same schema drives **three** consumers, which is the whole reason it is data:

- the builder form (`simple-content-builder`)
- the save spec (`bf/fields->spec` — optional-by-default, `:required?` opt-in)
- import/export verification (`field_schemas.cljc` → `import_validation`)

Write a field once; the form, the spec and the import check all follow.

---

## 2. The node vocabulary

A schema is a vector of nodes. Today there is one node kind; the proposal adds a second.

### 2a. Field node — REAL, in use

```clojure
{:key       :description            ; or a PATH: [:props :ac-bonus :bonus]
 :type      :text                   ; :text | :number | :enum
 :label     "Description"
 :required? false                   ; default false. Optional-by-default is deliberate (D9)
 :when      (fn [item] …)           ; optional — render only when true
 :options   [{:value nil :title "Both"} …]}   ; :enum only
```

**Three-state enums.** A tag with `true` / `false` / absent needs an explicit `nil` option, and it
must be **first**. A `<select>` with no matching value renders its first option, so without it the
form displays a restriction the item does not have. See `weapon-data-model.md`.

### 2b. Group node — PROPOSED, not built

```clojure
{:group :attack-bonus                          ; id AND the props key: fields live under
 :title "Attack Bonus"                         ;   [:props :attack-bonus …]
 :hint  "to attack rolls with matching weapons"
 :tags  [:melee? :ranged? :thrown? :heavy?]}   ; just the flags — labels and options generated
```

A group is **a titled set of fields**, nothing more. `weapon-bonus-group` is a *constructor* that
expands the shorthand above into a generic group; the weapon flavour stays in `builder_fields` and
the framework only learns "titled set of fields."

Why it exists — measured, not assumed: three bonuses expand to **19 flat fields**, with
`Melee/Ranged/Heavy/Thrown/Finesse/Light/Handedness` appearing **twice under identical labels** and
nothing saying which bonus each belongs to. Grouping fixes that structurally: "Melee" under a group
titled *Attack Bonus* can only mean one thing.

A rendered comparison of the current flat form against the grouped proposal is checked in at
`docs/kb/assets/builder-form-mockup.html` — open it in a browser.

`flatten-fields` reduces a schema to its fields, so `fields->spec` and `validate-fields` are
unchanged and **the save spec and `.orcbrew` format do not change**. Grouping is presentational.

---

## 3. HOW TO

**The test of this whole design is whether these recipes stay short. If one starts growing, the
framework is leaking and should be fixed rather than the recipe extended.**

### Add a field to an existing type

Append a field node to that type's schema (e.g. `classes/fighting-style-fields`). Done — form, save
spec and import check all pick it up. **Do not touch `views.cljs`.**

### Add a field to EVERY type that has props

Add it to a shared fragment in `builder_fields.cljc` (`ac-bonus-fields`, `attack-bonus-fields`).
`:props` compiles into **seven silos** — races, subraces, classes, subclasses, draconic ancestries,
feats, fighting styles — so a fragment lands in all of them. See
`fighting-style-vocabulary-gap.md`.

### Add a new field TYPE (e.g. `:boolean`)

Two edits, and note the standing decision first: `builder_fields.cljc` carries an explicit note that
a boolean/toggle type must route through `common/toggle-in`, **not** a parallel fn — read it before
starting.

1. `builder_fields.cljc` — add the case to `field-value-pred` (the save-validation predicate)
2. `views.cljs` — add the case to `render-builder-field` (the widget)

If you find yourself editing a third place, stop: the layers are leaking.

### Add a domain widget (the escape hatch)

Some controls are genuinely bespoke — `creature-selector`, `cantrip-num-selector`,
`base-weapon-selector`, `option-level-selection`. Do **not** force these into the schema. Pass them
as raw hiccup in `extra-fields`; the renderer already passes non-map entries through untouched. The
schema is for fields, not for every control that can exist.

### Add a mechanical prop (make a field DO something)

The field only stores data. To give it effect, add a case arm to `make-feat-modifiers`
(`options.cljc`) that compiles the authored map into modifiers. Model it on `:ac-bonus` /
`:attack-bonus`. Then add a test that walks the field paths and asserts each one is a key the
compiler reads — a form field writing a path the compiler ignores looks right, saves fine, and does
nothing. See `ac-bonus-field-paths-match-what-the-props-compiler-reads`.

### Add a whole new content type

See `content-extensibility-framework.md` §2e. One registry entry generates subs, events, db slots,
routes, the SPA allowlist and the page-map binding.

---

## 4. Triggers are not conditions

The most important distinction in this document, and the answer to *"how do we express
`if <trigger> then <bonus>`?"* — it is **two different things**, and only one is computable.

### Conditions — the app evaluates these

State the engine can inspect when it computes a number: what you are wearing, what you are wielding,
the weapon being used.

```clojure
{:group :attack-bonus :tags [:ranged?]}    ; +N to attack with ranged weapons — a real number
```

These are real mechanics. `?ac-bonus-fns` receives `(armor, shield)`; weapon bonus fns receive the
weapon. The condition is checked at computation time.

### Triggers — the app CANNOT evaluate these

*"When a creature you can see attacks a target other than you…"* There is no combat loop. Nothing
observes an attack happening. **Do not build a trigger DSL** — making this computable means writing
a combat simulator.

This is not a gap to close; it is how the shipped content already works. Protection is
`modifiers/reaction` with a name, page and description. The engine's `reaction`, `action`,
`bonus-action` and `trait-cfg` are **sheet entries** — labelled slots holding prose — and that is
the correct model for a trigger.

So the proposed second node kind is an entry, not a condition:

```clojure
{:entry :reaction                     ; :reaction | :action | :bonus-action | :trait
 :title "Protection"
 :text  "When a creature you can see attacks a target other than you…"}
```

**The rule:** if the effect depends on something the character *has or wears*, it is a condition and
gets computed. If it depends on something that *happens during play*, it is an entry and gets
printed on the sheet. A bonus whose trigger is a play event is still an entry — the number is shown
to the player to apply, not applied by the app.

---

## 5. Reconstructing the existing builders

Measured, not estimated — control census across every builder in `views.cljs` (10,288 lines,
98 builder-ish `defn`s).

**A first census here listed only 8 builders and mis-measured them** — the pattern required
`builder [` on one line and sized each by distance to the next `defn`. Corrected: there are **16
real form builders totalling 1,283 lines**.

| builder | lines | status |
|---|---|---|
| `class-builder` | 268 | ⚠️ the hard one — 8 `when`s, 2 bespoke selectors, real conditionals |
| `monster-builder` | 233 | 8 controls; the rest is layout |
| `race-builder` | 152 | |
| `subrace-builder` | 129 | |
| `subclass-builder` | 105 | |
| `spell-builder` | 85 | |
| `selection-builder` | 80 | has duplicate-name detection of its own |
| `feat-builder` | 62 | |
| `item-builder` | 53 | |
| `background-builder` | 46 | ⚠️ prose lives in `:help`, not `:description` |
| `encounter-builder` | 25 | ⚠️ a repeating collection (creatures) |
| `language-builder` | 21 | ✅ **rebuilt** — see below |
| `fighting-style-builder` | 10 | already schema-driven |
| `draconic-ancestry-builder` | 8 | already schema-driven |
| `invocation-builder` | 3 | already `simple-content-builder` |
| `boon-builder` | 3 | already `simple-content-builder` |

The bottom four are the existing proof: a type that fits the generic form is **3–10 lines**.

### Two gaps the survey found before any code was written

1. **The description key is not universal.** `simple-content-builder` hardcodes `:description`;
   `background-builder` stores its prose in `:help` while labelling it "Description". Converting
   background needs a per-type key or a data migration. Recorded in
   `builders/description-key-exceptions`.
2. **There is no collection node.** `encounter-builder` renders a repeating `creature-selector`
   list plus a blank one at the end. The schema has no way to say "a list of these". Traits have
   the same shape. This is the next node kind to design, and it is a bigger deal than `:group`.

### Converting the rest

There is no new mechanism to build for the simple types — `simple-content-builder` is the mechanism,
and `boon-builder` / `invocation-builder` are each **one line** using it. A conversion is: replace
the hand-written body with that call, keep the numbers identical.

An attempt to stand up a parallel `orcpub.dnd.e5.builders` namespace for this was **reverted**: for
language it amounted to a new namespace holding `(def language-fields [])`, a `-v2` view passing
that empty vector, and a test asserting the empty vector was empty — ceremony around a one-line
change the June work already demonstrated twice. A parallel namespace only earns its place for a
type that has actual field data to hold (spell, monster, item).

The real remaining work is not a new system. It is:

1. **Convert the boilerplate builders** (language 21, background 46, item 53, feat 62, spell 85,
   subclass 105, subrace 129, race 152, monster 233) to the existing mechanism, one at a time,
   asserting the numbers do not move.
2. **Design the two missing node kinds** — a collection node, and a per-type description key —
   because those genuinely do not exist yet.
3. **`:group`** (§2b), which is layout, and the least urgent of the three.

**The headline finding:** `monster-builder` is 233 lines for **8 fields**. The bulk is layout, not
logic. And 8 of the ~22 distinct controls across all builders are the *same input field* wrapped
per type — `class-input-field`, `spell-input-field`, `monster-input-field`,
`subrace-input-field`, `subclass-input-field`, `feat-input-field`, `language-input-field`,
`selection-input-field` — the exact duplication `simple-content-builder` already collapsed once for
the small builders (D22).

`labeled-checkbox` appears **7 times**, which is the `:boolean` type the schema still lacks.

**Order to convert, cheapest first:** language → subclass → subrace → spell → monster → class. Class
last: it is the only one with conditional structure and bespoke selectors, so it is the case that
tells us what the escape hatch really needs.

---

## 6. What this does NOT solve

Stated plainly so nobody discovers it later:

- **Triggers.** See §4. Not a limitation to fix — a category error to avoid.
- **Cross-field required.** `:required-when` (a field required only given another field's value) is
  **not enforced**. Flagged HIGH in `content-extensibility-direction.md`.
- **Layout control.** The schema says what to collect, not where it sits. A type needing a specific
  visual arrangement uses the hiccup escape hatch.
- **Bespoke selectors.** ~5 domain widgets stay hand-written, on purpose.
- **Consumption.** A field can be authored, saved, exported and imported and still not be *usable* —
  homebrew fighting styles are currently not selectable by the Fighter class. Authoring and
  consumption are separate problems. See the roadmap.

---

## 7. Open questions

1. **Deleting a group with data** — does `✕` clear `[:props :attack-bonus]` immediately or confirm?
   Silent loss on a misclick is the risk.
2. **Does `:hint` earn its place?** It is schema surface every silo inherits, for one line of prose.
3. **Generic `:group` vs bonus-specific.** Leaning generic (a titled set of fields) with
   `weapon-bonus-group` as a constructor, so the framework learns one concept — but that is a
   prediction about a second use case that does not exist yet, which D22 warns about.
