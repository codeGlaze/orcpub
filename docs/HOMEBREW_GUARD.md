# Homebrew guarded by default

Status: **design, for review; read against our documents 2026-10-21.** Branch `feature/guarded-homebrew`, off integration at `d51938c1`.

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
| Load floor | `content_specs/valid-item-for-load?`, `::e5/plugin` | Keeps an entry if its key is a keyword and it has an `:option-pack` string. A whole section may be `true` (the spec allows `boolean?`). Nothing else is checked: a presence contract, not a shape contract (orcbrew-level-modifiers.md). |
| Repairs | `e5/mend-library`, `mend-cards` | On load and import: `:key`, `:traits`, a selection's `:options`, a spell's `:spell-lists`. Not run on share-link content. |
| Guard | `guard-entry` at 7 sites | Catches a throw while one entry converts, sets the entry aside into `plugins:rejected`, says so for 60 s. |
| Deep check | `homebrew_check/deep-check` | Converts and fully realizes 5 of 13 content types; after import, Restore, or a crash that reaches the app root. |
| Quarantine panel | top of My Content | Per source and per item: rename, Restore, Auto-name & Restore, Export raw, Discard. A damaged section is listed whole. |
| Link marks and repairs | `library/dangling`, `suggested-repairs` | A link to nothing is marked on the item in My Content and in its builder, once the template is built; a fix is offered, never applied. |
| Health card | My Content | One line per problem type with a count: conflicts, missing fields, export blockers. |
| Commit gate | `library/commit`, `library_gate_test` | The one way to write the library; a backup in `plugins:pre-fix` before a stored fix. |
| Startup rescue | `boot-step`, `set-aside-unloadable-library!` | If startup throws, the whole library goes to `plugins:corrupt`, the app starts without it and offers a download. |
| Error boundaries | item, tab, app root | Catch what throws while a page draws; only the app root runs the deep check. |

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
offered nor set aside. (That last check does not yet read My Content's link marks.)

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
| **Not offered** | a subrace whose `:race`, or a subclass whose `:class`, is not a real one: marked in My Content as a link to nothing once the template is built, but not offered in the character builder; a junk parent that is not a keyword is probably not marked | all three |

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

## What our documents already say

Read against the baseline (all on agents/develop unless marked):

**Already known.** A subrace or subclass whose parent does not exist is marked in My Content and
in its builder ("links to nothing", homebrew-keys-design.md I11), not in the character builder,
where it simply is not offered; a junk parent that is not a keyword (`42`) is probably not marked
at all. Throws after the guard come from lazy option builders (homebrew-safety-net.md).
HOMEBREW_REQUIRED_FIELDS.md (integration) lists classes `:ability-increase-levels` and `:traits`.

**Contradicted by the baseline, to correct once the fix lands:**
- homebrew-safety-net.md: bad homebrew "cannot keep a page down". My Content, the monster list and
  the builders go down; startup comes up only by setting the whole library aside.
- homebrew-fixes-persist.md: "whatever none of these can read is set aside". A `true` section is
  never set aside: the load spec accepts it.
- HOMEBREW_REQUIRED_FIELDS.md (integration): race `:abilities` and `:languages` are not critical.
  Junk in either takes down every character page.
- fail-soft-rendering.md and clojurescript-type-tolerance.md (integration): the crash surface is
  narrow (string operations). Most of the 498 are `seq` on a number, a keyword or vector called
  as a function, and missing protocols; both documents cover nil, not wrong types.

**Not in any document:** the monster list, the builder crashes on level modifiers and selections,
share-link content skipping the repairs, and a `true` section crashing My Content.

## Why per-site fixes keep failing

Every crash above has the same cause: **code reads a field and assumes its shape.** Guarding each
read site is what we have done, and it misses the next one. Realizing everything inside the guard
costs about 50 times more and still cannot reach modifier functions. Two things fix the class of
bug instead of the instance:

1. Make every field the right shape **before** any code reads it.
2. Give code exactly one way to read homebrew, and fail the build when it reads it another way.

## The design

### 1. A shape gate in front of everything

- **The shapes come from what already exists**, not a new table: the field schemas
  (`field_schemas.cljc`, `builder_fields.cljc`, `content_types.cljc`, built June 2026) describe
  each builder's fields and generate its save spec. They live on grant-rows, not integration (see
  open questions). Fields the schemas do not cover yet are added to them.
- **Loading is never stricter than saving.** The gate accepts everything any builder writes,
  old forms included (a feat's released set and its 2024 list; race `:languages` as a set of
  names), checked by a generated test from the save specs, as `content_specs_test` does today.
- **A field that does not fit is read past, never rewritten.** Where the meaning is obvious
  (`:languages "Elvish"`, one trait card where a list belongs) the gate reads it the way it was
  meant, as `mend-cards` does today. Where it is not (`:spells 42`) the gate reads the entry
  without that field. Either way the stored entry is untouched (data-safety-layers.md: heal on
  read, no load-time migration; homebrew-keys-design.md C1).
- **Every one is shown, item by item.** The entry is marked in My Content the way a link to
  nothing is, with the field and why, and its builder says the same. A fix is **offered**, through
  the commit gate with its backup, never applied silently (C6). Export writes what is stored.
- `readable` (library in, readable library plus a list of what it read past) runs once per change
  to the library; the subscriptions, the deep check, shared content and events read through it.

This alone removes every crash in the baseline: none of them is reachable once the field has the
right shape.

### 2. One door for conversion

- No new registry: `guard/register-conversions!` (integration) and `content_types.cljc`
  (grant-rows) already list the content types; the conversions join them.
- The converted options come from one parameterized subscription per type, fed only by that
  type's readable entries, so editing a spell does not rebuild races.
- Every conversion runs inside the guard. The deep check runs the same registered converters, so it
  cannot drift from the live path (on grant-rows it already has: the race check passes the entry
  where `grantable-pools` goes and checks nothing).
- All 13 types are registered, not 5. A subrace or subclass failure is blamed on itself.

### 3. Telling us what it did

- **One homebrew health log**: every repair read, field read past, entry set aside, link to
  nothing and crash a boundary caught, each with its entry, field and reason. It feeds My
  Content's item-by-item marks, and a test can read it.
- **Tests read it and check it.** The log names the field, so the audit needs no search; the audit
  still watches for throws independently, so a crash the log does not explain is a failure.

### 4. Enforcement, and tests that cannot pass by accident

- **A source-scan test**, modelled on `library_gate_test` and `export_paths_test`: every read of
  raw homebrew (`::e5/plugins`, `::e5/plugin-vals`, `::e5/plugins-with-sources`,
  `::e5/shared-plugins`, `(:plugins db)`) must be in the homebrew namespace or carry a marker
  comment saying why it needs the raw data (storage, export, rename, quarantine).
- **Every check proves it can fail**: each stage of the audit has a canary, a known-bad entry it
  must catch; a canary that passes fails the audit.
- **Coverage is counted**: the audit checks how many entries each stage actually picked, drew and
  converted; fewer than expected fails.
- **The junk audit runs in CI** once the baseline is zero, and the full audit before a merge to
  integration. A faster audit replaces the slow one only after it reproduces the same failures.
- **AGENTS.md**: homebrew is read through `readable` and converted through the registry; a safety
  claim cites a committed test that runs; a report says what it did not cover.

## Order of work

1. This document, the baseline test, and the documents read against it (done).
2. The audit: canaries, counted coverage, the health checks in My Content, and the waste removed
   (proven to give the same failures).
3. The shape gate, from the field schemas, with the audit going to zero.
4. The conversions on one registry; the deep check reads it.
5. The health log and the item-by-item marks in My Content.
6. The source-scan test, the CI audit, the AGENTS.md rules; the documents above corrected.
7. grant-rows merges this and registers its pools; its hooks get the same audit.

## Open questions for the owner

- **Where to build.** The field schemas the gate should use exist only on grant-rows. Bring them
  to integration on this branch first, or build the gate on grant-rows and ship it with grants?
- **Storage path.** A `true` section passes the load spec; closing that changes the load path,
  which TODO.md pins as hands-off until deliberately scheduled. Schedule it here?
- **Offered fixes.** Are the obvious fixes offered in My Content (recommended, per C6), or applied
  quietly at read with a mark (data-safety-layers.md allows healing on read)?
