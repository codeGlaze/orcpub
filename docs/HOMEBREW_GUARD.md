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

`lein fig:junk && node test/e2e/junk-pack-audit.js` runs it all on four pages at once in about
8 minutes on integration; the everyday suite runs only the clean base pack. Each pack holds one
type's entries with every junk value, a failure is traced to its entry within the run, and an
entry that arrives the same by all three ways is built and drawn once.

On integration `d51938c1`: **659 failures over 51 fields**, listed in full in
`HOMEBREW_GUARD-baseline.txt`. Every one is a field nothing checks the shape of. The first audit
(498) never tested a junk `:name`, because it renamed every entry after putting the junk in; the
161 more are those, and the faster audit reproduces the other 498 exactly.

| Where | What breaks it | Through |
|---|---|---|
| **Startup** | a whole `classes` or `subclasses` section that is `true`: the boot rescue sets the whole library aside | stored, imported |
| **Every character page** (the option list the builder and sheets share) | races and subraces `:abilities`, races `:languages`, backgrounds `:equipment-choices` and its `:options`, backgrounds `:profs :skill`, feats `:prereqs`, classes `:ability-increase-levels` | all three |
| **The race or subrace, once used** (thrown after the guard let it through) | races `:spells`, `:props :damage-resistance`; subraces `:spells`, `:props :weapon-prof` | all three |
| **My Content** | any of the 13 sections being `true` | stored, imported |
| **The monster list** | monsters `:size`, `:type`, `:subtypes`, `:name` | all three |
| **Not offered, not reported** | a junk `:name` on a subrace, subclass, invocation or boon (boons and invocations key their option by name; why subraces and subclasses drop out is still to trace) | all three |
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

The app trusts the shape of everything that passes a thin load check. The design stops trusting
it at two kinds of seam, so nothing downstream has to change, and contains and reports whatever
still gets through.

### 1. One cleaning step, from a table of shapes

- **`homebrew_shapes.cljc`** (cljc, a leaf: requires nothing from the app) says, for each of the
  13 content types, every field the app reads and its shape, nested as deep as the readers go:
  `{:abilities [:map-of :ability :number], :spells [:vector-of {...}], ...}`. Fields nobody reads
  are not listed and pass untouched. The builder field schemas (`builder_fields.cljc`, on
  grant-rows) only cover two grant-rows types, so the 13 core types' shapes are written here;
  where a builder schema exists later, it should produce its type's entry in this table.
- **`clean`** takes an entry and returns it with each listed field the right shape, plus what it
  found, each `{:path :problem :action}`:
  - **fixed** when the meaning is obvious: a string where a list of strings belongs, one card
    where a list of cards belongs (what `mend-cards` does today, which becomes table entries);
  - **unreadable** when it is not (`:spells 42`, a number where a map belongs). Which of these
    sets the entry aside and which reads past the field is the owner's ruling below.
  - A whole content section that is not a map is unreadable as a section.
- **Loading is never stricter than saving.** The table accepts everything any builder writes,
  old forms included (a feat's released set and its 2024 list; race `:languages` as a set of
  names). A generated test checks the save specs against it, as `content_specs_test` does today.

#### The shape forms

Each form, and in brackets what `clean` does when a value has the wrong shape but its meaning is
obvious:

```
  :any :string :number :int :keyword :boolean :map
  :num                  a number (a numeric string is read as its number)
  :key                  a keyword (text is read as its keyword: "Medium" -> :medium)
  [:enum #{...}]        one of these values
  [:or s ...]           any one of them
  [:list s]             a vector or list of s (a lone s becomes a list of one)
  [:coll s]             a vector, list or set of s
  [:set s]              a set of s (a list of s becomes a set; a lone s a set of one)
  [:map-of k v]         a map whose keys are k and values v
  [:flags k]            a map of k to true or false (a set or list of k marks each true)
  [:counts k]           a map of k to a number (a set or list of k counts each once)
  [:indexed s]          a map of whole numbers to s (a list of s is numbered from 0)
  [:map {field s ...}]  a map; the listed fields are checked, the rest pass untouched
```

### 2. Entry seams: clean once, store the result

Where homebrew comes in, the cleaned entry is what is kept, so the same file is not fixed again
on every load and export writes clean data:

| Seam | Where | What happens |
|---|---|---|
| **Import** | `orcbrew-val/validate-import`, after its own repairs | Fixed entries are stored fixed; unreadable ones go to the quarantine panel with the field and reason. The import report lists both. |
| **Load** (startup, and another tab's write) | the `::e5/plugins` load step, after `mend-library` | The same; a fix is written back once through `library/commit`, after the `plugins:pre-fix` backup, as homebrew-keys-design §4a.2 does for the loader's repairs. Later loads find nothing to fix. |
| **Share link** | `apply-shared-content`, and the party page's share load | Cleaned before the load gate. Shared content is never stored, so an unreadable entry is left out of the view and the banner says so. |

`LOAD_HOMEBREW_URL` writes into storage, so the load seam covers it. Builder drafts are restored by
`reg-local-store-cofx`, which TODO.md pins as hands-off; drafts are not touched, and the builders
are covered by containment (4).

### 3. The read seam: nothing reads raw homebrew

Anything that changed after load (data written straight into the app's memory, an event that
stores raw data, a bug of ours) skips the entry seams. So everything that interprets homebrew
reads it through `readable` (a library in, the cleaned library and its findings out):

- `process-plugin-vals` and `process-plugins-with-sources`, which every homebrew subscription
  already goes through;
- the few event handlers and pages that read `(:plugins db)` or `::e5/plugins` to interpret it
  (the class keys at startup, key reconciliation, the former-key index, My Content's lists).

Storage, export, rename and the quarantine keep reading the raw library, each with a marker
comment saying why; the source-scan test (6) holds that line.

### 4. Containment for what still gets through

- **The guard on all 13 types**, through the existing `register-conversions!` list; the deep
  check runs the same conversions, so it cannot drift; a subrace or subclass is blamed on itself.
- **Per-item boundaries** (the existing `render-guard`) on My Content's rows, the monster list's
  items and each builder form, so one bad item takes down that item, not the page.
- `base-class-options` reports what it catches instead of silently returning no classes.

### 5. The health log: never silent

Every finding from `clean`, every entry set aside, every link to nothing and every crash a
boundary catches goes to one log in app-db, each with its entry, field and reason, deduplicated.
It feeds a per-item mark in My Content (the way a link to nothing is marked) and the builder's
note, and the audit reads it to name the failing field.

### 6. Enforcement, and tests that cannot pass by accident

- **Source scan** (like `library_gate_test`): a read of `::e5/plugins`, `::e5/plugin-vals`,
  `::e5/plugins-with-sources`, `::e5/shared-plugins` or `(:plugins db)` outside the homebrew
  namespace needs a marker comment and a reason, or `lein test` fails.
- **The junk audit** (`node test/e2e/junk-pack-audit.js`): every type, field, junk value and way
  in; target under 10 minutes; a faster version replaces a slower one only after it reproduces
  the same failures. Each stage gets a **canary**, a known-bad entry it must catch; a canary that
  passes fails the audit. **Coverage is counted**: fewer entries picked, drawn or converted than
  expected fails.
- **AGENTS.md**: homebrew is read through `readable`; a safety claim cites a committed test that
  runs; a report says what it did not cover.

## Efficiency

Nothing here may make the app slower to use. The costs, and how each is kept small:

| Cost | Kept small by | Budget |
|---|---|---|
| `clean` over a library | Only listed fields are walked; O(size of the entry). | 12-source test library: under 30 ms |
| The read seam on every library change | It runs inside `process-plugin-vals`, which already walks every entry on each change, not as a second pass. Cleaned entries are cached by entry identity, so editing one entry cleans one entry. | no measurable change |
| Subscriptions downstream | An unchanged entry comes back as the identical object, so the per-type `=` firewall still stops propagation: editing a spell does not rebuild races. | no extra recomputation |
| Events reading the library | `readable` is memoized on the library's identity: one clean per change, shared. | one pass per change |
| Startup | The load seam writes its fixes once; later boots clean and find nothing, and write nothing. | under 30 ms added |
| Import | One pass per file, beside parsing. | negligible |
| Laziness | Not realizing option lists stays as is (realizing everything costs ~50x); clean shapes make the lazy throws unreachable instead. | none |
| Health log | Built in the same pass as `clean`; deduplicated by entry and field; capped. | none |
| Source scan, audit | Build and CI time only. | audit under 10 min |

**Measured, not assumed:** before and after each step, with the 12-source test library: startup,
`process-plugin-vals`, the template build, one keystroke in a builder, and an import. A step that
adds more than its budget is not merged.

## Prep work already done, to reuse

- **testing/develop**: a Playwright suite of 60 scenarios from July 2026, never merged and run
  by nothing: `bad-data`, `broken-content-survives-use`, `boot-resilience`,
  `error-boundary-verify`, `quarantine-repair`, `resilient-loader` and others. Run against
  integration to see what still holds; port what the junk audit does not cover.
- **Fixtures on integration**: `broken-content.orcbrew`, `cruft-shapes.orcbrew`,
  `keyword-trap.orcbrew`, `test-pak.orcbrew`: audit inputs beside the junk grid.
- **On integration**: `mend-library`/`mend-cards` (their fixes become table entries),
  `library/commit` and `plugins:pre-fix` (the write-back), the quarantine panel and
  `repair-quarantined-source` (unreadable entries), `library/dangling` (extend to parents that
  are not keywords), `render-guard`/`isolate-culprit` (containment), `register-conversions!` and
  `deep-check`, `library_gate_test` (the source scan's model), HOMEBREW_REQUIRED_FIELDS.md (the
  nil analysis), the browser probes and their baseline.
- **On grant-rows**: `builder_fields.cljc` (field predicates and where-messages) and
  `content_types.cljc`; to converge with the shape table, not to duplicate it.
- **Known, not fixed**: homebrew boons and invocations drop their `:key` and key their option by
  name (`pact-boon-options`, `classes.cljc`), so two with one name collide and a renamed one is
  lost to the characters that picked it. content-extensibility-compatibility.md (agents/develop)
  calls it a latent footgun and says to pass the stored `:key`. Fixed in step 7 with the guard.

## Order of work, each step gated

1. **The audit** (in progress): under 10 minutes, the same failures as the baseline (differences
   explained), canaries, counted coverage. *Gate:* parity; time.
2. **The shape table and `clean`**, cljc, with a JVM test feeding every listed field every junk
   value. No app wiring. *Gate:* every baseline field is fixed or reported unreadable in the unit
   grid; save specs accepted.
3. **The read seam.** *Gate:* the audit drops by the fields covered; the everyday suites and the
   homebrew e2e scripts pass; the benchmark is in budget.
4. **The share seam**, then **the import seam**. *Gate:* the same, plus the import report test.
5. **The load seam** with the write-back once. *Gate:* `fixes_persist_e2e`, `boot_rescue_e2e`,
   `quarantine-restore`, `library-repairs` pass; a second boot writes nothing.
6. **The health log and the marks** in My Content and the builders.
7. **Containment**: all 13 types guarded, per-item boundaries, `base-class-options` reporting.
8. **Enforcement**: the source scan, the audit in CI, the AGENTS.md rules, the documents
   corrected, the testing/develop scenarios run and ported.
9. **grant-rows** merges this, registers its pools in the table and the guard, and runs the
   audit.

Shared hardening is built on integration; future work goes there unless it touches only the
refactor branch (owner, 2026-10-21).

## For the owner

- **An unreadable field**: set the whole entry aside in the quarantine panel with the reason
  (recommended: a number where a map belongs is an authoring mistake, not something to read
  past), or keep the entry and read past the field, with a mark?
- **Boons and invocations keyed by name** (known, above): fix in step 7 by passing the stored key?
  Characters that picked one hold the name-derived key; where the stored key differs (a source
  tag), they need healing through `:former-keys`, the way renamed keys heal today.
