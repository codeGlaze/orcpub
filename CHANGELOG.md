# Changelog

## [fix/custom-item-classification] — Custom items know whether they're magical

### Fixed
- **An item saved without touching a menu is no longer saved blank** — the Type
  and Rarity dropdowns show a value even when the item has none (a controlled
  `<select>` whose value matches no option still renders one), so items saved
  without opening those menus stored nothing and came back rendering as
  ", very rare" with a missing type. Saving now records what the builder was
  showing. Rarity is only filled for magic items, since it isn't shown for
  mundane ones.
- **Items no longer need a page refresh after signing in** — the subscription
  that loads them only fetches on a cache miss, so if anything read the chain
  while signed out (the equipment pickers do), it cached an empty list and no
  fetch fired again for the whole session. Signing in now asks for them
  directly.
- **Custom mundane items are no longer filed as magic items** — every user-built
  item went into the same list and through the same magic-item pipeline, so a
  homemade length of rope sat in "Other Magic Items" next to the vorpal swords.
  Items now record whether they are magical, and mundane ones are offered under
  Weapons / Armor / Equipment and listed with ordinary gear on the sheet.

### Added
- **`::magical?` on items, with a Magic Item? switch in the item builder** — a
  new attribute, so no existing item is rewritten to introduce it.
- **Self-healing classification for existing items** —
  `magic-items/classify` works the answer out from what an item already
  contains (attunement, modifiers, bonuses, type, rarity), and an idempotent,
  additive Datomic backfill records it on startup for the items it can be sure
  about. Nothing is retracted and no existing attribute is touched. Saving an
  item from the builder also records its answer.
- **A rarity of `common` decides nothing** — mundane gear has no rarity at all
  in 5e, and `common` is both a genuine magic rarity (a Moon-Touched Sword is a
  common magic weapon whose whole effect is that it glows, with no attunement
  and no bonus) and the item builder's old default. It is noise in both
  directions, so an evidence-free item carrying it goes to its owner rather
  than being inferred either way. Fewer items heal on their own as a result,
  deliberately: being asked is recoverable, being told your magic sword is
  ordinary gear — in your own voice — is not.
- **An explicit "we don't know" state** — an item with nothing to go on either
  way is left alone rather than guessed at. It keeps behaving exactly as it
  always has (as a magic item) and its owner is asked in the item builder,
  which is the only thing that retires the guess.

### Changed
- **Magic Item? is a dropdown, alongside Type and Rarity** — it was a checkbox
  labelled "Magic Item?" whose caption read "Mundane item", so the question and
  the answer contradicted each other. Now it answers its own label: Yes /
  No — mundane, plus a "Not set" option that only appears while an item is
  still unclassified and disappears once someone chooses.
- **The item builder only offers magical fields to magic items** — rarity,
  attunement, magical attack/damage/AC bonuses and the ability, save, speed,
  resistance and immunity grids appear only when Magic Item? is ticked. Name,
  type, description and the base weapon / base armor stats stay for both, since
  that is what an ordinary sword or breastplate is made of.
- **Unticking Magic Item hides and suspends, it does not delete** — an item's
  magical properties stop applying but stay on the item, and a notice names
  what is being held ("attunement, modifiers, an attack bonus"). Ticking the
  box again brings them all back; removing them for good takes a deliberate
  second click. That notice, and the "not recorded yet" one, use the
  `health-card` primitives from `feature/content-library-management` at the
  warning tier, so an "attention, not an error" signal looks the same wherever
  it appears — replacing near-invisible `opacity-5` italic hints. Nothing on the automatic path can reach this state: an item
  classification calls mundane on its own has no magical properties by
  definition.
- **The character sheet marks an item holding magic in reserve** — a "magic
  set aside" line in the Details column of the Weapons and Other Equipment
  tables, answering "why is my +1 not applying?" where the question is
  actually asked. The sheet resolves items through the effective (stripped)
  map, so a keys-only `::mi/items-holding-magic` subscription tells it THAT an
  item holds magic without giving the suspended mechanics a route back in.
- **My Items marks an item that is holding magic in reserve** — a small
  "magic set aside" line, with the held properties named in a native hover
  title. No new CSS, no absolutely-positioned tooltip and no click handler of
  its own: the row already expands to the item's details and edit button.
- **A mundane item no longer claims "(requires attunement)"** in its subtitle —
  its attunement is suspended along with the rest of its magic.
- **Reclassified items stay resolvable where characters already stored them** —
  a custom item that becomes mundane remains a valid option in the magic-item
  selections, hidden from the picker but still building. Dropping the option
  would have orphaned every character that picked it back when all custom items
  were magical, silently removing their gear from their sheet.
- **`::magical?` is `:db/noHistory`**, like every other item attribute. Keeping
  history for it was considered and rejected — item history is storage nobody
  asked for. The backfill's safety comes from refusing to guess, not from being
  able to undo a guess.

## [staging/june-bug-patches-01] — June bug-patch bundle (2026-07-05)

### Fixed
- **Homebrew class source no longer poisons spell-selection keys** — the source label was folded into the class `:name`, so the name-derived selection keys broke whenever it changed and saved spells/cantrips vanished; keys now come from a stable class identity, and orphaned saves repair on load (`9a709c0d`, `fe549631`, `a3e26155`).
- **Hunter's Evasion no longer blanks the Features tab** — the Superior Hunter's Defense → Evasion trait shipped with no name, and a nil name crashed the feature-name sort; it's now named "Evasion" (`dd65d66a`).
- **Character sheets no longer go blank** — an unrenderable section shows a recovery message; the rest of the sheet stays usable (`565c33c0`).
- **The Features tab loads for every character** — fixed a rendering bug that blanked it for all (`2a6fde93`).
- **A nameless trait no longer crashes the Features tab** — shown as "[Unnamed feature]" instead of throwing on the name sort (`5c3b073f`).
- **Boolean toggles no longer corrupt data** — they can't wipe an underlying map, and self-heal damage from the old bug (`1e9f27ec`).
- **Keyword-trap imports no longer silently vanish** — caught on import and routed to repair instead of a class that never appears (`d9b23021`).
- **Unreadable storage is preserved** for recovery instead of deleted (`eedffc08`).
- **localStorage quota failures warn and offer a backup** instead of silently dropping the save.
- **Readable import/export errors** — plain-English console messages instead of garbled output; import dedup is shown as a log line, not raw EDN (`eba28a9c`, `e512dc45`).
- **Post-save export fixed** — the "export here" link passed a stringified plugin instead of the map (`e3c9a9ee`).
- **Autosave no longer crashes on a not-ready template** — an empty template reached the builder and threw; now guarded (`e3c9a9ee`).
- **Import dedup no longer skips a top-level Selection** — de-duplication now covers a Selection at the top level, not just nested options (`d9b23021`).
- **Non-ASCII name detection works in the browser** — `count-non-ascii` was miscounting under ClojureScript (`d9b23021`).

### Added
- **Resilient homebrew loading** — a bad source is quarantined for repair (self-clearing once fixed) instead of dropping the whole library, with a My Content panel to rename, re-key, and restore it (`eedffc08`).
- **Builder escape hatches** — Export draft, refresh-safe WIP restore, "Save anyway" with placeholders, emergency raw export, and Export & Auto-Fix, so imperfect work is never trapped or lost (`eac350d0`, `e3c9a9ee`).
- **"Show homebrew source on class names" toggle** — without affecting saved spell selections (`8f94a94c`).
- **Fill-in dialog on export** — supply or auto-fill missing required fields instead of writing a broken file, with live field-level guidance in the builders (`1547cd69`, `22172adb`).

### Changed
- **Save validation covers every required field** — dropdowns and multi-selects (spell class-lists, monster hit dice, parent class/race), not just text (`e512dc45`).
- **Save and load share one spec registry** — so they can't drift and wrongly quarantine already-saved content (`ca977e0a`).
- **Normal exports strip meaningless blank flags** (false/nil/empty); raw, draft, and emergency exports are untouched.
- **Invalid-key errors are element-specific** instead of a generic "Name" error.

## [breaking/2026-stack-modernization]

### Infrastructure

- **2026 full-stack modernization** (`22823da`)
  Java 8 → 21, Datomic Free → Pro, Pedestal 0.5 → 0.7.0, React 15 → 18,
  Reagent 0.6 → 2.0, re-frame 0.x → 1.4.4, PDFBox 2 → 3, clj-time → java-time,
  figwheel-main, lambdaisland/garden, Jackson/Guava pinning.

- **Consolidate dev tooling** (`6249565`)
  Unified `user.clj` with lazy figwheel, nREPL helpers, lein aliases
  (`fig:dev`, `fig:watch`, `fig:build`, `fig:test`), operational scripts
  (`start.sh`, `stop.sh`, `menu`), `:dev`/`:uberjar`/`:lint`/`:init-db` profiles.

- **Merge develop** (`1d50782`)
  Integrate character folders, weapon builder (special/loading properties),
  docker-compose updates from `origin/develop` (24 commits).

### Bug Fixes

- **`:class-name` → `:class`** (`263f290`)
  Reagent 2.x overwrites hiccup tag classes with `:class-name`. Converted all
  UI uses to `:class`; 18 remaining `:class-name` are D&D data keys (correct).

- **Subscribe-outside-reactive-context — phase 1** (`c2290ca`)
  42 fixes across events.cljs, options.cljc, classes.cljc, core.cljs.
  Patterns: direct db read, plugin-data map, track! template cache, SSOT pure fns.

- **Subscribe-outside-reactive-context — phase 2** (`09d7e4c`)
  14 fixes across options.cljc, pdf_spec.cljc, equipment_subs.cljs, views.cljs.
  Patterns: plugin-data threading, reg-sub-raw, move to render scope.

- **Prereq subscribes → pure character fns** (`9cbc25a`)
  22 prereq-fn lambdas in options.cljc converted from `@(subscribe)` to pure
  `(fn [character] ...)` functions.

- **Multiclass/wizard prereqs** (`3249f88`)
  7 multiclass and spell-mastery prereqs in classes.cljc converted to pure fns.

- **`def` + `partial` → `defn`** (`f578cdb`)
  `option-language-proficiency-choice` captured subscribe at load time via
  `partial`. Converted to `defn` for proper reactive context.

### Cleanup

- **Remove 11 orphaned subscriptions** (`bb2400d`)
  4 static map wrappers deleted (superseded by homebrew-aware versions).
  7 unused subs reader-discarded (`#_`) with comments: `all-melee-weapons`,
  `item`, `base-spells-map`, `spell-option`, `spell-options`,
  `filtered-monster-names`, `has-prof?`. Pre-existing tech debt, not caused
  by subscribe refactor.

- **Fix 591 missing-else-branch lint warnings** (`29c9f28`, via `fix/lint-missing-else`)
  Mechanical `if→when`, `if-let→when-let`, `if-not→when-not` across 33 files.
  Scripted fix (`scripts/fix-missing-else.py`) with column-precise substitution.
  Also fixed 2 pre-existing bugs: `when` used instead of `if` for two-branch
  conditionals in classes.cljc:1808 and options.cljc:463.

- **Fix forward-reference lint error** (`792fe3c`)
  `show-generic-error` used before its `def` alias in events.cljs. Changed to
  fully-qualified `event-utils/show-generic-error`.

- **Consolidate lint config** (`7476f10`)
  All linter settings moved from project.clj `:lint` profile to
  `.clj-kondo/config.edn` (single source of truth for IDE + CLI). Lint scope
  expanded to cover `native/`, `test/`, `web/`. clj-kondo bumped to 2026.01.19.
  LSP false-positive suppression via `:exclude-when-defined-by` for re-frame.

- **Dead code cleanup — ~92 vars** (`6bbcd9a`, `b68b917`)
  `#_` reader-discard on dead defs across 10 source files: deprecated ua/scag
  refs, superseded template UI (ability roller, amazon frames), 17 never-dispatched
  event handlers, dead style defs, duplicate constants. Includes cascade cleanup
  (helpers that lost all callers). Each `#_` has a comment explaining why.

- **Redundant expression fixes** (in `6bbcd9a`, `b68b917`, `429152e`)
  Remove nested `(str (str ...))`, flatten `(and (and ...))`, remove duplicate
  destructuring param, remove unused refers, narrow test `:refer` lists,
  fix unreachable code in registration.cljc.

### Enhancements

- **Input debounce** (`d108134`)
  Moved debounce from component-level `input-field` to `debounced-build-sub`
  in subs.cljs (leading+trailing edge, 500ms). Eliminates per-keystroke
  entity/build recomputation.

- **Folder hardening** (`f28f58f`)
  `on-folder-failure` event re-fetches server state on HTTP error. Client +
  server blank-name validation. `check-folder-owner` wrapped with
  `interceptor/interceptor`, returns 404 for missing folders. Named tempid
  `"new-folder"` + `d/resolve-tempid`. `case` default clause in folders sub.
  CSS class fix (`builder-dropdown` → `builder-option-dropdown`).

- **UI polish** (`d163ca9`)
  Zero-warning dev/prod builds, dev-mode CSP nonce, favicon, custom
  `externs.js` for React 18 advanced compilation.

### Tests

- **CLJS test infrastructure** (`b96b1b6`)
  figwheel-main test build, `test_runner.cljs`, pure function tests for
  compute, entity, character accessors.

- **JVM tests for new code** (`6124d9f`)
  `compute-all-weapons-map`, feat-prereqs, pdf_spec pure functions, folder
  routes (CRUD + blank rejection + trimming).

- **Folder validation tests** (in `f28f58f`)
  Blank name → 400, whitespace trimming, nil defaults to "New Folder",
  name unchanged after rejected renames.

### Documentation

- **Migration docs** (`026b031`)
  MIGRATION-INDEX.md, JAVA-COMPATIBILITY.md, datomic-pro.md, pedestal-0.7.md,
  frontend-stack.md, library-upgrades.md, dev-tooling.md, ENVIRONMENT.md,
  testing.md.

- **STACK.md** (in `f28f58f`)
  Library/dependency onboarding guide: architecture diagram, all frameworks,
  build system, profiles, dependency pinning rationale.

### Current Status

- **174 JVM tests**, 444 assertions, 0 failures
- **0 CLJS errors**, 0 warnings (dev + advanced)
- **0 subscribe warnings** in browser console
- **0 linter errors**, 0 warnings

---

## [feature/error-handling-import-validation] (merged)

### New Features

#### Import Validation (`import_validation.cljs` -- new file)
- **Unicode normalization**: Converts smart quotes, em-dashes, non-breaking spaces, and 40+ other problematic Unicode characters to ASCII equivalents on import and homebrew save. Prevents copy-paste corruption from Word/Google Docs.
- **Required field detection & auto-fill**: On import, missing required fields (`:name`, `:hit-die`, `:speed`, etc.) are auto-filled with placeholder values like `[Missing Name]`. Content types covered: classes, subclasses, races, subraces, backgrounds, feats, spells, monsters, invocations, languages, encounters.
- **Trait validation**: Nested `:traits` arrays are checked for missing `:name` fields and auto-filled.
- **Option validation**: Empty options (`{}`) created by the UI are detected and auto-filled with unique default names ("Option 1", "Option 2", etc.).
- **Multi-plugin format detection**: Distinguishes single-plugin from multi-source orcbrew files for correct processing.

#### Export Validation
- **Pre-export warning modal**: Before exporting homebrew, all content is validated for missing required fields. If issues are found, a modal lists them with an "Export Anyway" option.
- **Specific save error messages**: `reg-save-homebrew` now extracts field names from spec failures and shows targeted messages instead of generic "You must specify a name" errors.

#### Content Reconciliation (`content_reconciliation.cljs` -- new file)
- **Missing content detection**: When a character references homebrew content that isn't loaded (e.g., deleted plugin), the system detects missing races, classes, and subclasses.
- **Fuzzy key matching**: Uses prefix matching and base-keyword similarity to suggest available content that resembles missing keys (top 5 matches with similarity scores).
- **Source inference**: Guesses which plugin pack a missing key likely came from based on key structure.

#### Missing Content Warning UI (`character_builder.cljs`)
- **Warning banner**: Orange expandable banner appears in character builder when content is missing, showing count and details.
- **Detail panel**: Lists each missing item with its content type, key, inferred source, and suggestions for similar available content.
- **DOM IDs for testability**: `#missing-content-warning`, `#missing-content-details`, `.missing-content-item` with `data-key` and `data-type` attributes.

#### Conflict Resolution Modal (`views/conflict_resolution.cljs`, `events.cljs`)
- **Duplicate key detection**: On import, detects keys that conflict with already-loaded homebrew (both internal duplicates within a file and external conflicts with existing content).
- **Resolution UI**: Modal presents each conflict with rename options. Key renaming updates internal references (subclass -> parent class mappings, etc.).
- **Color-coded radio options**: Rename (cyan), Keep (orange), Skip (purple) with left-border + tinted background. All styles in Garden CSS.

#### Import Log Panel (`views/import_log.cljs`)
- **Grouped collapsible sections**: Changes grouped into Key Renames, Field Fixes, Data Cleanup, and Advanced Details (collapsed by default). Empty sections hidden automatically.
- **Detailed field fix reporting**: Field Fixes section shows per-item breakdown — which item, content type, which fields were filled, how many traits/options were fixed.
- **Collapsible section component**: Reusable `collapsible-section` with configurable icon, colors, and default-expanded state.

#### OrcBrew CLI Debug Tool (`tools/orcbrew.clj` -- new file)
- `lein prettify-orcbrew <file>` -- Pretty-prints orcbrew EDN for readability.
- `lein prettify-orcbrew <file> --analyze` -- Reports potential issues: nil-nil patterns, problematic Unicode, disabled entries, missing trait names, file structure summary.

### Bug Fixes

#### nil nil Corruption (`events.cljs`)
- **Root cause fix**: `set-class-path-prop` was calling `assoc-in` with a nil path, producing `{nil nil}` entries in character data. Now guards against nil path before the second `assoc-in`.

#### Nil Character ID Crash (`views.cljs`)
- Character list page crashed with "Cannot form URI without a value given for :id parameter" when characters had nil `:db/id`. Added `(when id ...)` guard to skip rendering those entries.

#### Subclass Key Preservation (`options.cljc`, `spell_subs.cljs`)
- Subclass processing now uses explicit `:key` field if present (for renamed plugins), falling back to name-generated key. Prevents renamed keys from reverting.
- `plugin-subclasses` subscription preserves map keys and sets `:key` on subclass data correctly.

#### Plugin Data Robustness (`spell_subs.cljs`)
- `plugin-vals` subscription wrapped in try-catch to skip malformed plugin data instead of crashing.
- `level-modifier` handles unknown modifier types gracefully (logs warning, returns nil instead of throwing).
- `make-levels` filters out nil modifiers with `keep`.

#### Unhandled HTTP Status Crash (`subs.cljs`, `equipment_subs.cljs`)
- All 7 API-calling subscriptions used bare `case` on HTTP status with no default clause. Any unexpected status (e.g., 400) threw `No matching clause`. Replaced with `handle-api-response` HOF that logs unhandled statuses to console.

#### Import Log "Renamed key nil -> nil" (`events.cljs`, `import_validation.cljs`)
- Key rename change entries used `:old-key`/`:new-key` fields but display code expected `:from`/`:to`. Unified on `:from`/`:to` across creation, application, and display.

### Error Handling (Backend)

#### Database (`datomic.clj`)
- Startup wrapped in try-catch with structured errors: `:missing-db-uri`, `:db-connection-failed`, `:schema-initialization-failed`.

#### Email (`email.clj`)
- Email config parsing catches `NumberFormatException` for invalid port (`:invalid-port`).
- `send-verification-email` and `send-reset-email` check postal response and raise on failure (`:verification-email-failed`).

#### PDF Generation (`pdf.clj`, `pdf_spec.cljc`)
- Network timeouts (10s connect, 10s read) for image loading. Specific handling for `SocketTimeoutException` and `UnknownHostException`.
- Nil guards throughout `pdf_spec.cljc`: `total-length`, `trait-string`, `resistance-strings`, `profs-paragraph`, `keyword-vec-trait`, `damage-str`, spell name lookup. All use fallback strings like "(unknown)", "(Unknown Spell)", "(Unnamed Trait)".

#### Routes (`routes.clj`, `routes/party.clj`)
- All mutation endpoints wrapped with error handling: verification, password reset, entity CRUD, party operations. Each uses structured error codes (`:verification-failed`, `:entity-creation-failed`, `:party-creation-failed`, etc.).

#### System (`system.clj`)
- PORT environment variable parsing validates numeric input (`:invalid-port`).

#### Error Infrastructure (`errors.cljc` -- expanded)
- New error code constants for auth flows.
- `log-error`, `create-error` utility functions.
- `with-db-error-handling`, `with-email-error-handling`, `with-validation` macros for consistent patterns.

### Supporting Changes

#### Common Utilities (`common.cljc`)
- `kw-base`: Extracts keyword base before first dash (e.g., `:artificer-kibbles` -> `"artificer"`).
- `traverse-nested`: Higher-order function for recursively walking nested option structures.

#### Styles (`styles/core.clj`)
- `.bg-warning`, `.bg-warning-item` CSS classes for warning banner UI.
- `.conflict-*` Garden CSS classes for conflict resolution modal (backdrop, modal, header, footer, body, radio options with color-coded variants: cyan/rename, orange/keep, purple/skip).
- `.export-issue-*` Garden CSS classes for export warning modal.

#### App State (`db.cljs`)
- Added `import-log` and `conflict-resolution` state maps to re-frame db.

#### Subscriptions (`subs.cljs`, `equipment_subs.cljs`)
- Import log, conflict resolution, export warning, missing content report subscriptions.
- `handle-api-response` HOF (`event_utils.cljc`) — centralizes HTTP status dispatch with sensible defaults (401 → login, 500 → generic error) and catch-all logging for unhandled statuses. Replaces bare `case` statements across 7 API-calling subscriptions.

#### Entry Point (`core.cljs`)
- Dev version logging on startup.
- Import log overlay component mounted in main view wrapper.

#### Linter Configuration
- `.clj-kondo/config.edn`: Exclusions for `with-db` macro and user namespace functions.
- `.lsp/config.edn` (new): Explicit source-paths to prevent clojure-lsp from scanning compiled CLJS output in `resources/public/js/compiled/out/`.

### Design Principles

- **Import = permissive** (auto-fix and continue), **Export = strict** (warn user, let them decide)
- **Placeholder text convention**: `[Missing Name]` format (square brackets indicate auto-filled)
- **Modal pattern**: db state -> re-frame subscription -> event handlers -> component in `import-log-overlay`
