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
types (`test/fixtures/junk-bases.orcbrew`) and puts each of 12 junk values in each field, one level
deep. Each pack arrives three ways (stored, imported through the file importer, and through a share
link) and goes through startup, every homebrew subscription, the deep check, a character that picks
each entry where the builder offers it, and the pages that draw it: My Content, the monster list
and each entry's builder. It fails when anything throws, when a built-in class, race or background
goes missing, when something other than the junk entry is set aside, and when an entry is neither
offered nor set aside (lost without a word).

`node test/e2e/junk-pack-audit.js` runs it all, one content type per page (about two and a half
hours on integration, most of it classes); the everyday suite runs only the clean base pack.

On integration `d51938c1`: **498 failures over 46 fields**, listed in full in
`HOMEBREW_GUARD-baseline.txt`. Every one is a field nothing checks the shape of.

| Where | What breaks it | Through |
|---|---|---|
| **Startup** | a whole `classes` or `subclasses` section that is `true`: the boot rescue sets the whole library aside | stored, imported |
| **Every character page** (the option list the builder and sheets share) | races and subraces `:abilities`, races `:languages`, backgrounds `:equipment-choices` and its `:options`, backgrounds `:profs :skill`, feats `:prereqs`, classes `:ability-increase-levels` | all three |
| **The race or subrace, once used** (thrown after the guard let it through) | races `:spells`, `:props :damage-resistance`; subraces `:spells`, `:props :weapon-prof` | all three |
| **My Content** | any of the 13 sections being `true` | stored, imported |
| **The monster list** | monsters `:size`, `:type`, `:subtypes` | all three |
| **Builders** | classes and subclasses `:level-modifiers` (and each one's `:type`), `:level-selections`; classes `:spellcasting :cantrips-known`; encounters `:creatures`; feats `:ability-increases` | all three |
| **Share links only** (shared content skips the load repairs) | `:traits` on races, subraces, backgrounds, classes, subclasses and monsters; selections `:options`; a `true` `classes` or `subclasses` section | share |
| **Lost without a word** | a subrace whose `:race`, or a subclass whose `:class`, is not a real one | all three |

Integration's feat builder bug (`:ability-increases`) is already fixed on PR #69.

Suspected from reading the code, **not reproduced**: a bad invocation or boon emptying the class
list (with the fields an invocation or boon has, nothing throws), and a subrace's failure being
blamed on its race (nothing was set aside but the junk entry). `LOAD_HOMEBREW_URL` writes into the
same storage slot, so the stored path covers it.

Not reached yet: junk two levels down (inside a trait card, a level selection's options), and the
pages that only draw a picked entry (the character sheet, the spell page).

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
- Each field that does not match goes one of two ways:
  - **Fixed, when the meaning is obvious.** `:languages "Elvish"` becomes `["Elvish"]`; one trait
    card where a list belongs is wrapped in a list. This is what `mend-library` already does for
    traits and selection options, extended to every declared field. Like those repairs, a fix is
    written back to storage once, so it is not made again on every load.
  - **Skipped and flagged, when it is not.** `:spells 42` or `:abilities true` cannot be guessed.
    The app reads the entry without that field, so nothing crashes, and the stored entry is left
    exactly as written. The entry's builder says which field cannot be read and why, the way the
    ability-increase forms do now, so the author can fix it there.
- Checking a shape is a read, not a write. `readable` (library in, readable library out) runs once
  per change to the library (memoized), and startup, the subscriptions, the deep check, shared
  content and events all read through it.
- Fields nobody reads pass through untouched.

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

## Proposed, for the owner to confirm

- **A field with the wrong shape** is fixed when the meaning is obvious, otherwise skipped and
  flagged; the entry stays, with its good fields. Setting the whole entry aside, as the guard does
  today, is kept only for an entry that still throws after the gate.
- **Nothing the author wrote is overwritten.** Only the obvious fixes are saved, once; a skipped
  field stays in storage as written.
