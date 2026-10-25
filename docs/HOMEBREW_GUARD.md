# Homebrew guarded by default

Status: **design, for review (2026-10-21).** Branch `feature/guarded-homebrew`, off integration
at `d51938c1`.

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

### What it promises, and what proves it

1. **Bad homebrew cannot crash the app at startup or take down a page.** At worst the one bad
   item does not show, and the user is told which one. *Proof:* the junk audit (every field,
   every junk value, by storage, import and share link) goes from 659 failures to 0, and its
   canaries show it can still fail.
2. **A fix is made once: written, used and exported.** `"5"` fixed to `5` is stored as `5`, read
   as `5`, exported as `5`. *Proof:* `fixes_persist_e2e`, and a second load that finds nothing to
   fix and writes nothing.
3. **Nothing disappears without a word.** *Proof:* the audit's "neither offered nor set aside"
   check stays at zero.

**What it cannot promise:** that code not yet written has no bugs. A bug in our own code is
contained to one item and reported (5), and the audit, rerun in minutes, finds the gap.

### 1. One cleaning step, from a table of shapes (built)

- **`homebrew_shapes.cljc`** says, for each of the 13 content types, every field the app reads and
  its shape. **`clean-entry` / `clean-library`** return the entry made to fit, and what they found:
  - **fixed** when the meaning is obvious (`"5"` → `5`; one card → a list of one; a set of keys →
    a key-to-true map). `mend-cards`' rules move into the table: one repair mechanism, not two;
  - **unreadable** when it is not (a number where a map belongs).
- A sound entry comes back as the identical object. Loading is never stricter than saving: every
  real fixture comes through with nothing unreadable (a test). Fixes produce only shapes the
  builders write, so exported files still import into older builds.
- The table decides how good a fix or a message is. It is not what keeps the app safe: a field it
  does not list is still covered by the conversion check (2) and by containment (5).

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

### 2. Where homebrew comes in: fix once, check it works, store it (built)

**One global interceptor** (re-frame 1.4.4's `reg-global-interceptor`, `::homebrew-shapes` in
events.cljs, `homebrew-shapes-checked`) runs after every event. If the event left the library as the identical object, which is almost every
event, it does nothing. If the event changed it, however it changed it:

- the changed entries are cleaned, and the fixes go into the new state and are **written to
  storage** with it (`"5"` stored as `5`, read as `5`, exported as `5`);
- an entry with a field that cannot be read is reported through the guard's reporter (the same
  path as an entry that fails to convert) and moves to the **quarantine panel** with its reason,
  as it was written, to fix or restore;
- the changed entries are then **converted by the app's own code** (the existing deep check,
  `check-and-set-aside!`, after the new state is in place): one that does not convert moves to
  the quarantine panel too. Only the entries that changed: the check costs about 20 ms an entry,
  so a whole 464-entry source took 9 s and a save now takes about 35 ms. Import and restore no
  longer call it themselves. An entry is checked when it changes, so a change to a race that
  breaks its subraces is left to containment (5).

The active library is then clean, so nothing is found again on the next load. Because it runs on
every event, it does not depend on code calling the right function, or on `library_gate_test`
recognising how a write was made (it scans for `assoc`, `update` and `dissoc` on `:plugins`, not
`merge` or a helper). That test stays, as the check that every write also reaches storage.

| Way in | What the interceptor sees |
|---|---|
| **Import** | the import event storing the file; the import report lists fixes and quarantined entries |
| **Builder saves, rename, move, restore, conflict resolution** | each of those events; a builder save changes one entry, so one entry is checked |
| **Load** (startup, another tab's write, what `LOAD_HOMEBREW_URL` stored) | not the interceptor: the loader (`::e5/plugins` cofx) reads storage before any event can, and a section stored as `true` stopped startup there. So the loader applies the table straight after its mend: fixes are written back with its repairs, after the `plugins:pre-fix` backup; an unreadable entry joins what it sets aside in `plugins:rejected`, as written. The interceptor then finds nothing |
| **Share link** | `apply-shared-content` puts shared content in `:shared-plugins`, which the interceptor checks the same way; it is never stored, so a fix is used and an entry that cannot be read is left out and reported (to the console; a line in the share banner is still to do) |

Builder drafts are restored by `reg-local-store-cofx`, which TODO.md pins as hands-off; they are
covered by (5).

### 3. Inside the app: every read is checked (built)

Data can change after it came in without an event: a script writing to the app's memory, the
console, a bug of ours. So the shape check also runs where the app reads homebrew, in one
function, `homebrew-check/checked`, called from two places:

- **every view**: the `::e5/plugins` and `::e5/shared-plugins` subscriptions, which every other
  homebrew subscription (so every builder, page, option and character) is built on;
- **every event handler**: the global interceptor's `:before` hands each handler the library as
  checked, so the 57 handlers that read `(:plugins db)` directly need no change.

Whatever is in memory is checked there, so the functions after it only ever receive the right
shapes, and none of them changes. Anything unclean found there is read with its fixes, an entry
that cannot be read is left out and reported (to the quarantine panel, by the guard's reporter),
and the whole is logged as a bug: nothing should reach it that way. It writes nothing.

It remembers the last library it read and what it gave for it, so a library already checked costs
one comparison (0.002 ms), and a new one is walked only where it differs from the last (one
entry changed: 0.2 ms).

### 4. Never silent, with what exists

Fixes show where load repairs show today; quarantined entries in the quarantine panel and its
notice; the health card counts them. A crash caught by (5) reports through the guard's reporter,
as the page-level fallback does today. No new log.

### 5. Containment for bugs in our own code

The checks above protect the app from bad data. A function passing the wrong thing to another is
a bug in our code; these catch it, using what exists:

- **The page fallback** (app root, `core.cljs`) stays the last resort: a crashed page shows
  "Something went wrong on this page", runs the deep check, sets aside the cause, and retries.
- **Per-item boundaries** (the existing `render-guard`) on My Content's rows, the monster list,
  each builder form and the sheet's lists, so a bad item costs that item, not the page.
- **One `reg-event-error-handler`** (re-frame 1.4.4 has it): an event that throws (a click, a save,
  a load) is reported, and the app's state stays as it was before that event. Error boundaries
  only catch drawing; this catches events. The existing `check-spec-interceptor` on character
  events then reports through it instead of throwing with nothing listening.
- **The guard on all 13 types** (through `register-conversions!`; today 5), a subrace or subclass
  blamed on itself, and `base-class-options` reporting what it catches instead of silently
  returning no classes.
- **Hardened core helpers.** The few functions of ours every option, choice and modifier passes
  through (`t/option-cfg`, `t/selection-cfg`, the modifier constructors, `safe-capitalize-kw`, the
  trait builder) check what they are given, skip what they cannot use, and report it, the way
  `safe-compare` already made every sort safe. Which ones: the audit records where in the code
  each crash happened; the helpers most crashes pass through go first, and each must show a drop
  in the audit count.

Replacing Clojure's own functions (`seq`, `keys`, `map`, …) with safe versions is not part of
this: inside Clojure they call each other, and `for` and destructuring expand to them, so our
versions would only apply where our code calls them by name; a namespace that forgot them would be
unprotected; and returning nothing instead of throwing hides bad data rather than reporting it.
Clean data at the seams makes all of them safe at once.

### 6. Enforcement, and tests that cannot pass by accident

- **Writes:** the global interceptor checks every change, and `library_gate_test` still requires
  every write to reach storage through the gate.
- **The junk audit** (`lein fig:junk && node test/e2e/junk-pack-audit.js`, about 8 minutes), in CI
  once it reads zero. Each stage has a **canary**, a planted bad entry it must catch; **coverage is
  counted**: fewer entries picked, drawn or converted than expected fails.
- **AGENTS.md:** write the library through the gate; a safety claim cites a committed test that
  runs; a report says what it did not cover.

## Efficiency

Nothing here may make the app slower to use.

| Cost | Kept small by | Budget |
|---|---|---|
| Cleaning | Only on a write: an import, a builder save (one entry), the first load after this lands. Reading costs nothing. | 12-source test library: under 30 ms |
| The global interceptor | One identity comparison per event; work only when the library changed, and only on the entries that changed. | no measurable change |
| Later loads | The library is already clean: a read-only walk that finds nothing and writes nothing. | under 10 ms |
| The in-app read check | One comparison per read; on a library change, only the entries that changed. | 0.002 ms; 0.2 ms after a change |
| The conversion check at a way in | Only the entries that came in or changed; `guard/realize` now walks each shared part once. | a builder save: one entry |
| Downstream | A clean entry is the identical object, so the per-type `=` firewall still holds: editing a spell does not rebuild races. | no extra recomputation |
| The deep check | `guard/realize` walks each shared part once (done): one homebrew class went from seconds to milliseconds. | done |
| Audit | Build and CI time only. | under 10 min (now about 2) |

**Measured, not assumed:** before and after each step, with the 12-source test library: startup,
`process-plugin-vals`, the template build, one keystroke in a builder, and an import.

After step 3, test-pak stored (464 entries, 764 KB), dev build: an event that leaves the library
alone 0.001 ms; an event that changes one entry 0.05 ms, then its deep check 35 ms (of which
25 ms is setting up the conversions); the loader 170-190 ms, of which the shape walk is 6-7 ms.

## Prep work already done, to reuse

- **testing/develop**: a Playwright suite of 60 scenarios from July 2026, never merged and run by
  nothing (`bad-data`, `broken-content-survives-use`, `boot-resilience`, `quarantine-repair`, …).
  Go through it once and port what the junk audit does not cover, rather than keep two suites.
- **Fixtures on integration**: `broken-content`, `cruft-shapes`, `keyword-trap`, `test-pak`.
- **On integration**: the write gate and `library_gate_test`; `persist-set-aside!` and
  `plugins:pre-fix`; `mend-*` (folded into the table); the quarantine panel and
  `repair-quarantined-source`; `library/dangling`; `render-guard`; `register-conversions!` and
  `deep-check`; HOMEBREW_REQUIRED_FIELDS.md; the browser probes.
- **On grant-rows**: `builder_fields.cljc` and `content_types.cljc`; their schemas feed this table
  when grant-rows merges, so there is one description of a field, not two.
- **Fixed here** (owner, 2026-10-25): homebrew boons and invocations keyed their option by name,
  not their stored `:key` (`pact-boon-options`, `classes.cljc`; content-extensibility-compatibility.md
  called it a latent footgun, as a rule for later grant work that never came). They are offered
  under the stored key; a pick of the name's key heals to it, as a background's does.

## Order of work, each step gated

1. **Done:** the baseline (659), the shape table and `clean`, the audit in about 8 minutes,
   `guard/realize` walking each part once.
2. **Done:** audit canaries and counted coverage.
3. **Done:** the global interceptor, and the table in the loader. The audit went from 659 to 37
   in 132 s. The everyday suites, `homebrew_safety_net_e2e`, `fixes_persist_e2e`,
   `quarantine-restore` and `library-repairs` pass; a second load writes nothing (a test).
4. **Done:** the in-app read check, changed-only from the start (the full walk is 7 ms on
   test-pak, on every read it would not be). A feat's `:ability-increases` is now read as a set,
   the only form this build's builder reads (27 failures: a list reached the builder). The audit
   went from 37 to 10 in 135 s. Left: a background skill that names no skill (6; a lookup in
   our code, step 5) and a subrace or subclass from a share link whose parent does not exist (4;
   not shown in My Content, so silent). `boot_rescue_e2e` now passes 17/17: run by hand, it had
   no overlay suppression, and the What's New panel covered the link it clicks.
   **When PR #69 merges**, its list of `[amount pool]` spreads goes into the feats row with the
   builder that reads it, in the merge itself: until then this build would turn a spread into a
   set on load and set it aside, as it cannot read it.
5. **Hardened core helpers**, ranked by the audit.
6. **Containment:** per-item boundaries, the event error handler, the guard on 13 types.
7. **Enforcement:** the audit in CI at zero, the AGENTS.md rules, the documents corrected, the
   testing/develop scenarios gone through.
8. **grant-rows** merges this, adds its fields to the table, and runs the audit.

Shared hardening is built on integration; future work goes there unless it touches only the
refactor branch (owner, 2026-10-21).

## For the owner

- **An unreadable field**: the whole entry moves to the quarantine panel with the reason
  (owner, 2026-10-25).
- **Boons and invocations keyed by name**: fixed here (owner, 2026-10-25), above.
