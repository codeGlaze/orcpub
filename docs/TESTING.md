# Testing Knowledge Base

## Running Tests

```bash
lein test                              # all tests
lein test orcpub.dnd.e5.warlock-test   # single namespace
lein lint                              # clj-kondo (--fail-level error)
```

Test paths (from project.clj): `test/clj`, `test/cljc`, `test/cljs`

## Test Suite Overview

| File | What it tests | Key functions |
|------|--------------|---------------|
| `character_test.clj` | Serialization: namespacing, strict round-trips, equipment quantities | `equipment-to-namespaced`, `unnamespaced-character`, `strict-round-trip` |
| `character_test.cljc` | Spec validation, property-based speed tests | `test-character-spec`, `non-negative-speeds-are-valid` |
| `entity_test.clj` | Entity strict format: to-strict, from-strict, round-trips, path-maps | `test-to-strict`, `test-round-trip`, `get-entity-path` |
| `entity/strict_test.clj` | Strict entity spec validation, duplicate selection detection | `has-duplicate-selections?`, `valid-spec` |
| `event_handlers_test.clj` | Event handlers: set-class, set-level, add-inventory, parse queries | `set-class-level--*`, `test-set-class--round-trip` |
| `magic_items_test.clj` | Magic item serialization: to/from internal format, armor expansion | `test-to-internal-item`, `test-expand-armor` |
| `modifiers_test.clj` | (no deftest found — may need investigation) | |
| `options_test.clj` | Spell slot calculation | `test-total-slots` |
| `template_test.clj` | Modifier map construction | `make-modifer-map` |
| `warlock_test.clj` | entity/build integration: ability scores, race, skills, levels, spells | `build-smoke-test`, `warlock-ability-scores`, `warlock-spells` |
| `common_test.clj` | Namespace key utilities | `test-add-namespaces-to-keys` |
| `entity_spec_test.clj` | Entity spec macros, modifier construction | `test-defentity`, `test-modifier` |
| `routes_test.clj` | Route handlers: save character, DB IDs, orphan removal | `test-do-save-character`, `test-save-entity` |
| `security_test.clj` | Auth: date comparison, rate limiting, multi-account detection | `test-compare-dates`, `test-too-many-attempts-for-username?` |
| `csp_test.clj` | CSP headers: nonce generation, format, no unsafe-inline | `nonce-generation`, `csp-header-format` |
| `pdf_test.clj` | PDF font loading | `fonts-test` |
| `dependencies/integration_test.clj` | Jackson JSON, Guava, Datomic connectivity | `test-jackson-json-serialization`, `test-datomic-pro-basic-connectivity` |

## entity/build Coverage (warlock_test.clj)

`warlock_test.clj` exercises the full `entity/build` pipeline with a level-10 Drow Elf
Warlock (Archfey patron, Pact of the Tome, Book of Ancient Secrets, 5 invocations).

**7 tests, 20 assertions** covering:
- Smoke test (build doesn't throw)
- Ability scores with racial + feat bonuses (DEX+2 elf, CHA+1 drow, INT+1 keen mind)
- Race/subrace name resolution ("Elf" / "Dark Elf (Drow)")
- Skill proficiencies from 3 sources (elf, spy background, warlock class)
- Class levels (warlock level 10)
- Base land speed (elf 30)
- Spells from invocations/pacts (Book of Ancient Secrets rituals, Book of Shadows cantrips)

### How the test works
Uses real cljc data modules (`spell-lists`, `spell-map`, `weapons-map`) plus inline
configs for data stuck in `.cljs` (elf/drow race, spy background, keen mind feat,
language map). Calls `classes5e/warlock-option` directly — same code path as production.
See `docs/ENTITY-BUILD.md` for architecture details.

### Remaining coverage gaps
- No test for multiclassing (multiple classes in one entity)
- No test for magic item equipment slots
- HP calculation not asserted (too many interdependencies — CON mod, levels, class HD)
- Armor class not asserted (depends on armor, shield, DEX, bonuses)
- The 2 disabled entity_test.clj tests (`get-all-selections-aux-2`, `make-template-option-map`)
  could be revived using the same template construction pattern from warlock_test.clj

## Disabled Tests (with #_)

These tests are commented out with `#_` and have `TODO: Fix / remove test` comments:

| File | Test | Why disabled |
|------|------|-------------|
| `entity_test.clj` | `get-all-selections-aux-2` | Needs real template (same pattern as warlock_test) |
| `entity_test.clj` | `make-template-option-map` | Needs real template (same pattern as warlock_test) |
| `security_test.clj` | `test-multiple-ip-attempts-to-same-account?` | Unknown |
| `routes_test.clj` | `test-index` | Unknown |

## Test Patterns

### Entity construction
Raw entities use `::entity/options` with `::entity/key` for selections:
```clojure
{::entity/options
 {:race {::entity/key :elf
         ::entity/options {:subrace {::entity/key :drow}}}
  :ability-scores {::entity/key :standard-roll
                   ::entity/value (char5e/abilities 10 10 10 10 10 10)}
  :class [{::entity/key :warlock ...}]}}
```

### Template construction
Templates are `{::t/base template-base, ::t/selections [...]}`.
`template-selections` builds the selections vector from 12 params.
`t5e/template` wraps selections with template-base.

### Strict format round-trips
Most entity_test/character_test tests verify `(= entity (-> entity to-strict from-strict))`.

## Linter Configuration

### clj-kondo (`.clj-kondo/config.edn`)
- `unused-public-var` excludes: `user` namespace (REPL utils), `orcpub.styles.core/app` (garden entry point)
- `unresolved-symbol` excludes: macro-generated symbols from modifiers, entity-spec, test.check, core.match
- `unresolved-var` excludes: `garden.selectors` (macro-generated at compile time)
- `lint-as`: reagent `with-let` → `let`, `defspec` → `deftest`, `with-db` → `let`

### clojure-lsp (`.lsp/config.edn`)
- **Explicit `source-paths`** — required because `resources` is on the classpath and clojure-lsp
  would otherwise scan `resources/public/js/compiled/out/` (compiled CLJS output).
  `source-paths-ignore-regex` doesn't work because it filters directory-level source paths,
  not files within `resources/`.
- Source paths: `src/clj`, `src/cljc`, `src/cljs`, `web/cljs`, `test/clj`, `test/cljc`, `test/cljs`, `dev`

## Previously Failing Tests (Now Fixed)

`strict-round-trip-2` in `character_test.clj:108` had two bugs:
1. **Selection ordering**: `from-strict-selections` used `reduce`/`assoc` with `{}`, which
   promotes to `PersistentHashMap` beyond 8 keys, losing insertion order. Fixed by using
   `apply array-map` to preserve vector ordering regardless of size.
2. **Missing owner field**: `to-strict` didn't include `::owner` in its output, so the
   `::strict/owner` field was lost during the round-trip. Fixed by adding `::owner` to
   the destructuring and `cond->` in `to-strict`.

## Key Gotchas

1. **`mod5e/race-ability` returns a vector** of two modifiers (one for `?ability-increases`,
   one for `?race-ability-increases`). You don't need to flatten manually in race configs —
   `entity.cljc` calls `(flatten modifiers)` at lines 553 and 589 before applying them.
   But be aware when building modifiers outside of option builders.

2. **`mod5e/skill-proficiency` is a macro**, not a function. Cannot be passed to `map`.
   Call directly in vector literals: `[(mod5e/skill-proficiency :perception)]`.
   All other modifier constructors (`weapon-proficiency`, `saving-throw-advantage`,
   `immunity`, `darkvision`, `speed`, `race`, `subrace`, `trait-cfg`) are functions.

3. **`::char5e/str` shadows `clojure.core/str`** when destructured with `{:keys [::char5e/str]}`.
   Use explicit map access instead: `(:orcpub.dnd.e5.character/str abilities)`.

4. **template-base has interdependent properties** — don't assert specific HP values
   (they depend on CON mod × levels + class HD + hit-point-level-increases + bonuses).
   Test relative changes or use predicates like `pos?`.

5. **Always provide all 6 ability scores** — template-base `?abilities` calculation defaults
   missing keys to 0, which breaks ability bonus math.

6. **Warlock spells use schedule mode** — regular cantrips and spells (eldritch blast,
   charm person, etc.) don't appear in `char5e/spells-known`. They use
   `:spells-known-modes {"Warlock" :schedule}`. Only special spells from invocations
   and pacts (Book of Ancient Secrets rituals, Book of Shadows cantrips, at-will
   invocation spells like speak-with-animals) appear in `spells-known`.

7. **Subrace keys are auto-generated from names** — `common/name-to-kw` converts
   "Dark Elf (Drow)" → `:dark-elf-drow-` (lowercase, non-word chars to `-`, collapse
   consecutive `-`). Don't provide explicit `:key` in subrace configs unless you need
   to override this.

8. **Unmatched entity options are silently skipped** — if the entity selects an option
   (e.g. a feat or tool proficiency) that doesn't exist in the template, `collect-modifiers-2`
   just skips it with no error. This means you can have a simpler template than the
   entity expects without crashes, but missing options produce no modifiers.

9. **`feat-options` returns empty vector** — all built-in feats in `options.cljc:1181+` are
   `#_` commented. Feats only come from the `feats` parameter to `template-selections`
   via `feat-option-from-cfg`.
