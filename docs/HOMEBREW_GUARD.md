# Homebrew guarded by default

Status: **design, for review.** Branch `feature/guarded-homebrew`, off integration at `d51938c1`.

## The problem in one paragraph

Homebrew is untrusted: an imported or hand-edited pack can hold any shape in any field. The app
protects itself with a guard (`homebrew_guard.cljc`), but the guard is a wrapper that code has to
choose to use. Code that reads homebrew without it is unprotected, and nothing says so: no rule,
no lint, no test. That is how grant-rows ended up with hooks that throw on a malformed pack, and
how integration still has crashes nobody had measured. This branch makes the safe path the only
path, so new code is protected without anyone remembering to protect it.

## How we got here

- **2026-09-12, PR #34 (`986c25a4`).** The guard landed. It wrapped the seven places integration
  turned homebrew into options. From integration's side that was everything.
- **The KB note** (`homebrew-safety-net.md`, on agents/develop) lists what is guarded and why
  nothing is fully realized on the everyday path (about 2 s against 40 ms for 12 sources). It has
  no rule that new code must use the guard. It is cited by code docstrings but is not on
  integration.
- **The wrong-type sweep** that checked every field was a scratch script, never committed; the
  note says its rerun "was cut short". Nothing has checked since.
- **grant-rows** wrote its pools and fighting-style hooks between June and 2026-09-08, before the
  guard, on another branch. The merge only flagged conflicting lines, so the hooks stayed
  unwrapped.

## What exists today

| Layer | Where | What it does |
|---|---|---|
| Load floor | `content_specs/valid-item-for-load?` | Keeps an entry if its key is a keyword and it has an `:option-pack` string. Nothing else is checked. |
| Repairs | `e5/mend-library` | Fixes a few known shapes: `:key`, `:traits`, a selection's `:options`, a spell's `:spell-lists`. |
| Guard | `guard-entry` at 7 sites | Catches a throw while one entry converts, sets the entry aside, says so. |
| Deep check | `homebrew_check/deep-check` | Converts and fully realizes 5 of 13 content types; after import, Restore, or a page crash. |
| Error boundaries | views, app root | Catch what throws while a page draws; only the app root runs the deep check. |

Option builders return lazy sequences. A throw inside one surfaces later (when a page draws it or
a character is built), outside the guard. Modifier functions run only when a character is built,
where nothing can guard them.

## Baseline: what crashes today

`test/cljs/orcpub/dnd/e5/junk_pack_test.cljs` takes one realistic entry of each of the 13 content
types (`test/fixtures/junk-bases.orcbrew`), puts each of 12 junk values in each field, and runs
the pack through startup, every homebrew subscription, the deep check, and a character that picks
each entry. `node test/e2e/junk-pack-audit.js` runs all of it (the everyday suite runs only the
base pack, which passes).

On integration `d51938c1`: **58 crashes**, every one a wrong shape in a field nothing checks.

| Content | Field | Junk values that throw | Where it throws |
|---|---|---|---|
| any | the whole `classes` or `subclasses` section is `true` | 1 each | **startup**: the boot rescue sets the whole library aside |
| races | `:abilities` | 6 | building the race option list |
| races | `:languages` | 3 | building the race option list |
| races | `:spells` | 3 | after the guard, when the race is used |
| races | `:props :damage-resistance` | 5 | after the guard, when the race is used |
| subraces | `:abilities` | 6 | building the race option list |
| subraces | `:spells` | 3 | after the guard |
| subraces | `:props :weapon-prof` | 5 | after the guard |
| backgrounds | `:equipment-choices`, and its `:options` | 9 | building the background option list |
| backgrounds | `:profs :skill` | 6 | building the background option list |
| feats | `:prereqs` | 7 | building the feat option list |
| classes | `:ability-increase-levels` | 3 | the class list (all classes) |

"Building the option list" is the template every character page uses, so each of those takes down
the character builder and sheets, not one entry.

Found by reading the code, **not yet proven** by the audit (it does not reach them yet):

- **Invocations and boons.** They are converted inside the built-in Warlock. `base-class-options`
  catches any throw there and returns no classes at all, with no report: one bad invocation makes
  all 12 classes vanish.
- **A subrace on a built-in race** is blamed on the race, so the built-in race is what disappears.
- **Monsters:** the monster list and its filter sort and lower-case names unguarded.
- **My Content** sorts each section; a section that is not a map takes the page down.
- **Shared-link content** is converted the same way but never repaired, and cannot be set aside.
- **`LOAD_HOMEBREW_URL`** writes fetched text straight to storage, skipping import validation.
- `compute/compute-plugin-vals` is a second, drifted copy of the plugin read, with no callers.

On feature/grant-rows the same audit is expected to add the pool hooks (fighting styles, draconic
ancestry, grant pools), the AC and weapon `:props`, and grant `:order`; to be run there after
this lands.

## Why per-site fixes keep failing

Every crash above has the same cause: **code reads a field and assumes its shape.** Guarding each
read site is what we have done, and it misses the next one. Realizing everything inside the guard
costs about 50 times more and still cannot reach modifier functions. Two things fix the class of
bug instead of the instance:

1. Make every field the right shape **before** any code reads it.
2. Give code exactly one way to read homebrew, and fail the build when it reads it another way.

## The design

### 1. A shape gate in front of everything

- A new `homebrew_shapes.cljc` declares, per content type, every field the app reads and the
  shape it must have, as data: `{:abilities ability-map, :spells spell-list, :profs {...}}`.
  Nested shapes are declared the same way, down to the depth the builders read.
- `readable` takes a library and returns it with every field that does not match its shape
  removed, plus a list of what was removed. Fields nobody reads pass through untouched.
- It runs at **read**, not in storage: stored data is never changed, so an author can still open
  the entry in its builder and fix it. The builder says which fields cannot be read, the way the
  ability-increase forms do now.
- Startup, the subscriptions, the deep check, shared content and events all read through
  `readable`. Memoized on the library, so it costs one pass per change.

This alone removes every crash in the baseline: none of them is reachable once the field has the
right shape.

### 2. One door for conversion

- A registry: each content type registers its converter once (`register-content-type!`), in a leaf
  namespace with no subscription dependencies (the load-order trap in
  entity-options-architecture.md).
- The converted options come from one parameterized subscription per type,
  `[::homebrew/options content-type]`, fed only by that type's readable entries. That keeps the
  per-type firewall: editing a spell does not rebuild races.
- Every conversion runs inside the guard. The deep check runs the same registered converters, so it
  cannot drift from the live path (on grant-rows it already has: the races and feats checks pass an
  argument in the wrong place and check nothing).
- All 13 types are registered, not 5. A subrace or subclass failure is blamed on itself, not its
  parent. `base-class-options` reports what it catches instead of silently dropping every class.

### 3. Enforcement

- **A source-scan test**, modelled on `library_gate_test` and `export_paths_test`: every read of
  raw homebrew (`::e5/plugins`, `::e5/plugin-vals`, `::e5/plugins-with-sources`,
  `::e5/shared-plugins`, `(:plugins db)`) must be in the homebrew namespace or carry a marker
  comment saying why it needs the raw data (storage, export, rename, quarantine). A new read
  without one fails `lein test` with a message saying how to route it.
- **The junk audit in CI.** Once the baseline is zero, a sample of the grid joins the everyday
  suite (it takes about a second per pack when nothing throws), and the full audit runs before a
  merge to integration.
- **A shape for every field read.** The audit takes its fields from the base entries; a test also
  checks that every field the shape gate declares appears in a base entry, so the two lists stay
  together.
- **AGENTS.md** gets one rule: homebrew is read through `readable` and converted through the
  registry; anything else needs a marker and a reason.

## Order of work

1. This document, and the baseline test (done).
2. The shape gate, field by field, with the audit going to zero on integration's content.
3. The registry and the per-type subscriptions; move the 7 guarded sites and the unguarded ones
   onto it; the deep check reads the registry.
4. Startup, events, My Content, shared content and `LOAD_HOMEBREW_URL` read through `readable`.
5. The source-scan test and the CI audit; the AGENTS.md rule; the KB note brought to integration.
6. grant-rows merges this and registers its pools; its hooks get the same audit.

## Open questions for the owner

- **Remove, or set aside?** The gate removes an unreadable field and keeps the entry (a race with
  junk `:spells` keeps its name, size and traits). The alternative is setting the whole entry
  aside, which is what the guard does today. Recommended: remove the field, and say so.
- **Write the cleaned data back?** Recommended: no. Storage keeps what the author wrote; the
  builder shows what cannot be read.
