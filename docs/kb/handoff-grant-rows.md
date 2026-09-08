# Handoff — grant-rows (E4) and what follows

For the agent taking over `feature/grant-rows`. Read `CLAUDE.md`'s three pages first; this page is
the ordered work with an acceptance test per step, and it points at the docs rather than restating
them. Written 2026-09-08 at the end of the session that built the node.

## Where you are

Branch `feature/grant-rows`, cut from `feature/fighting-style-authoring` at `24ad64f0`. Two commits:

| commit | what |
|---|---|
| `9dfb6299` | `compile-grants` (fixed → modifiers, D4); `grant-selection` choice-only (D29); seven pools registered; one `damage-types` def |
| `6df5076c` | `bf/grant-rows` + `vector-rows-node`, first consumer on the feat builder; feat's `set-feat-prop` made path-accepting; `test/e2e/feat-grants.js` 27/27 |

Gates: JVM 495 tests / 2994 assertions; cljs dev build compiles; e2e through `lein e2e-server`.

**Proven:** an author adds grants from an add-bar that IS the pool registry (names no pool), toggles
fixed/choice, saves `:grants [{:pool :languages :count 2} {:pool :skills :key :athletics}]`, removes
a row. Use on a character is proven at the JVM level (`fighting_style_grant_matrix_test` builds
characters through `feat-option-from-cfg` with `:grants`).

**Not done, deliberately:** nothing struck. The bespoke feat widgets sit above the Grants card and
still write their legacy keys. They come out only behind the shim registry (step 2).

## The work, in order

Each step is its own commit with its acceptance test green before the next. Docs sync per commit
(`documentation-discipline.md`); reversals go in a Corrections section, never overwritten.

### 1. Re-record the gallery baseline — 5 minutes
`LABEL=after node test/e2e/builder-gallery.js` will report feat drifted (a new "Grants" label and
its chips). That is deliberate. `cp target/e2e-shots/gallery-after/index.json test/e2e/builder-baseline.json`.

### 2. `legacy_shims.cljc` — fixed-class keys normalize at import
Spec: `builder-disposition-audit.md` §"The 35 deletions", the model paragraph. Registry
`{legacy-path {:since :remove-after :class :normalize}}`; applied once at `::e5/plugin-vals`
(`spell_subs.cljs:243` — the slot the direction doc reserved for `resolve-variants`). **Fixed-class
only** (rows 1, 3, 5, 7–11, 14–18, 23–24, 29–32): `:props :skill-prof {k true}` → `:grants
[{:pool :skills :key k}]` and dissoc. `:languages {"Elvish" true}` goes through `name-to-kw` (D10).
Choice-class entries are registered with `:status :blocked-on-ref` and NOT applied.

**Acceptance:** (a) JVM — a pack fixture carrying every fixed-class legacy key normalizes to the
expected `:grants` and the key is gone; (b) JVM — a character built on the fixture BEFORE
normalization and AFTER has identical modifier sets (`grant_vocabulary_characterization_test` shows
the pattern); (c) e2e — import the pack, open the race in the builder, **the form shows the grants
as grant rows** (the five-readers rule, `before-you-start.md`; no conversion pin has asserted this
yet).

Then strike, per D34 (`#_` + date + ledger row in `backfill-ledger.md`), the fixed-class feat widgets:
`feat-damage-resistance`, `option-skill-proficiency-or-expertise`, `option-tool-proficiency-or-expertise`,
and the armor half of `feat-armor-proficiency`. ⚠️ `feat-armor-proficiency` also carries
`:medium-armor-stealth` and `:medium-armor-max-dex-3` (effects) — move those to `effect-rows` kinds
in the SAME commit or they vanish from the form. `feat-weapon-proficiency` carries the dead
`:improvised-weapons-prof` — wire it (`modifiers/weapon-proficiency :improvised` exists) or drop it;
do not carry it forward silently.

### 3. The `:ref` decision — unblocks the choice class
The question: legacy `language-selection` stores a character's pick at `:ref [:languages]`;
`grant-selection` stores it nested (no `:ref` — the prototype found a `:ref` on a *nested* grant
broke addressing). **Write the test first:** build a character on a legacy race with `:profs
:language-options`, pick Elvish, save; normalize the race to `:grants`; reload; assert the pick
survives. If it does not, two candidates — `grant-selection` honours a pool-declared `:ref` (re-test
whether the nested break applies to a `:profs`-level grant, which is not nested), or a
`content_reconciliation` pass rewrites picks at load (`reconcile-spell-selection-keys` is the
pattern). Pick by the test. Then lift `:blocked-on-ref` and normalize the choice class.

### 4. `grant-rows` on the other silos — one line each
`race`, `subrace`, `background`, `class`, `subclass`: add `[vector-rows-node item <set-prop>
(first (bf/grant-rows :<silo>))]` — or, for the already-converted builders, `(bf/grant-rows :<silo>)`
in `extra-fields`. Check each silo's `set-<base>-prop` accepts a path (feat's did not; the generated
ones do). **Acceptance:** the D30 tag test — a granted language on a race lands on the Proficiencies
tab (`pools-carry-their-tags` pins the tags at JVM; add the e2e that clicks the tab). Never
`:monster` (`builder-disposition-audit.md`).

### 5. Effect kinds feat is missing
Add to `bf/effect-rows` `:kinds`: `:ac` (Natural Armor), `:armor-dex-cap`, and number kinds for
`:speed`, `:initiative`, `:max-hp-bonus`, plus the two medium-armor booleans from step 2. Fields are
the shared `:props` fragments (`builder-form-schemas.md` §3). Then strike `feat-speed-bonuses`,
`feat-initiative-bonuses`, `feat-hps`, `feat-misc-modifiers`. **Acceptance:** the drift test that
walks field paths against compiler keys (`ac-bonus-field-paths-match-what-the-props-compiler-reads`
is the model) covers every new kind.

### 6. E3 proper — creatures and traits
`vector-rows-node` is grant-specific (mode / which / how-many / from). Encounter's creatures and
background's traits are the other two vector-shaped needs (`builder-form-schemas.md` §5, §6).
Decide whether its add/remove skeleton generalizes with a per-kind body, or stays grant-only. If a
fourth shape is needed, stop and reassess (§6's own rule).

### 7. `content-builder :feat` — the one-liner
Only after steps 2 and 5 have emptied the bespoke feat body. `simple-content-builder` doing its own
lookups (`:builder-item` and `:plugin-key` from `content_types`, `set-<base>-prop` by convention,
schema from `field_schemas/by-plugin-key`). The two inline-fields call sites (spell, fighting style)
collapse the same day.

### 8. Spells as a pool — last
Needs a spell → `option-cfg` constructor and the built-in list membership inverted from
`spell_lists.cljc` inside the pool's own `:options-fn` (`pool-grant-map.md`, "Spells"). Then the
three feat spellcasting templates become nested grants and the passthrough goes. Not before 2–7.

## ⚠️ Read before step 5 — the template tier (added 2026-09-08)

Steps 2 and 5 say "strike `feat-hps` / `feat-speed-bonuses` / `feat-initiative-bonuses` /
`feat-misc-modifiers`". That is right about storage and wrong about the form: those widgets are
**templates with frozen parameters** — "Your HP increases by [1|2] per level" is the sentence with N
frozen to a menu of two. Deleting them for a generic number field loses the sentence, which is what
makes the form recognizable and fast.

See `builder-disposition-audit.md` §"REFRAMING" for the full argument and the three tiers. The
practical consequence for these steps: a widget's replacement may be a **parameterized template**
rather than a generic row, and a template emits the same `:grants` / `:props` either way — so
nothing about the storage work below changes, only which surface an author sees. The template
registry itself is unbuilt and unscoped; do not invent it mid-step. If a step's widget is clearly a
frozen template, parameterize it in place and note it, rather than striking it for a bare number
field.

## Rules that bit this session — do not re-learn them
All in `CLAUDE.md` ("Working rules") and `before-you-start.md`. The two that cost the most:
- **Walk the five readers** of a content item before declaring a storage change safe. The builder
  path (opening an existing item) was missed once already.
- **Grep the KB before designing.** Three settled things were re-derived from scratch in one
  session; the direction doc had all three.

## Don'ts
- No alias for a branch-local shape (`:grant`, `:from`, `:choose` are gone; do not read them).
- No `:mode` or any UI-only key in the data. The row toggle rewrites the row.
- `:monster` in no pool's `:offerable-by`.
- `grant-selection` never compiles `:key`. That is `compile-grants`' job (D29).
- Never migrate an item on save. Normalization happens at import, once, from the registry.
- No `:built-in-compiled?`-style descriptor keys. A pool's `:options-fn` absorbs its own shape.

## Commands
```
lein test                      # JVM gate
lein fig:build                 # cljs — a missing var is a WARNING; grep the output
lein garden once               # only if CSS changes; check its exit code
lein e2e-server                # then: node test/e2e/feat-grants.js
```
