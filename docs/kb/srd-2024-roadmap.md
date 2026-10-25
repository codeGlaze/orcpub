# SRD 2024 (5.5e) support — roadmap and decisions

**Start here for the 2024 work.** This page holds the direction: where the work is on the
ladder, what has been decided and by whom, what is still open, and what blocks what. Detail
lives elsewhere and is linked, not repeated:

- [srd-2024-coverage.md](srd-2024-coverage.md) — what has and has not been analysed, per content type
- [srd-2024-implementation-notes.md](srd-2024-implementation-notes.md) — running list of what we cannot yet express, with ideas
- [srd-pdf-as-source-of-record.md](srd-pdf-as-source-of-record.md) — sources, extraction, and what the old import got wrong
- [open5e-upstream-notes.md](open5e-upstream-notes.md) — problems found in open5e's API and data, kept to PR back

Trunk: `srd52/develop`, watching `refactor/content-extensibility` and through it
`integration`. Leaves go under `srd52/*`.

## Long-term goal: a pure SPA

**Read this before choosing how any SRD content is stored or served.**

The long-term goal (owner, 2026-10-06) is for the app to run as a **pure single-page app, like
5etools**: static hosting, all data as files the browser fetches and caches, no backend needed to
browse the SRD or build a character. Not scheduled, and a long way off, but it constrains choices
being made now.

**The decision it drives: SRD content is served as static data files, never through a server
endpoint.** Full verbatim text for both editions is built into static, cacheable files and fetched
by the browser on demand; the builder ships summaries and structured mechanical fields. A server
endpoint for SRD content would be a step away from this goal and is ruled out.

**What ties the app to a server today is not the SRD**, which is already compiled into the browser
app. It is: PDF generation (server-side PDFBox), accounts and saved characters (Datomic), and short
share links (tokens stored server-side). Those are what a pure-SPA effort would one day have to
replace. Nothing in the SRD work should add to that list.

**Cheap to keep the door open:** make the static files cache-friendly from the start, which is
also what lets an SPA work offline.

**Standing principle (owner, 2026-10-06): prefer SPA groundwork that also helps the app now.**
When a choice can serve the long-term goal *and* fix something today, take that route.

### How SRD data is loaded

Measured on the 2014 content, whole entries, compressed for the wire:

| content | raw | compressed |
|---|---|---|
| spells | 343 KB | 87 KB |
| monsters | 463 KB | 73 KB |
| magic items | 808 KB | 77 KB |

An edition's whole core is under 250 KB compressed. At that size, clever per-entry loading costs
more engineering than it saves. So:

- **One file per content type per edition** (`spells-2014`, `spells-2024`, …). Never one request per
  entry: an entry is about 1 KB, and each request is a round trip.
- **Fetched the first time it is needed, then cached.** A version stamp in the filename picks up
  changes immediately; repeat visits cost nothing until then.
- **Two priorities, not a system.** Whatever the user opens loads first. Everything else loads in
  the background while the app is idle, ordered for the current page (spells first in the
  builder, monsters first on monster pages). The text is usually there before anyone clicks.
- **Revisit only if one file grows past a few hundred KB compressed.**

### One pipeline: the app's SRD data is generated, not hand-kept (planned 2026-10-11)

Rules and conditions already come from the generators (`agents/develop` `scripts/srd/`). Spells,
monsters, weapons, armor and magic items are still hand-kept Clojure, so a correction made in
open5e reaches the app only if someone repeats it by hand. The goal: every SRD type generated from
the corrected open5e data, so one fix lands in open5e and in the app together. Who reads the data
today, with file:line: [srd-data-consumers.md](srd-data-consumers.md).

**Two stages, so the move does not wait on the loader below.**

1. **Generate the data; compile it in.** Per type, the generator writes the app's own shape to
   `resources/public/srd/2014/<type>.edn`, and the namespace's data def becomes a read of that file
   at compile time (a macro: the JVM reads the file, ClojureScript gets the literal). No consumer
   changes, the builder still has the data synchronously, and the JVM side (PDF, spell cards,
   tests) reads the same file. This matches "an edition is compiled in, like `e5`". A test holds
   every existing key, so no saved character or homebrew reference breaks.
2. **Fetch instead of compile in.** Move full text out of the bundle into the fetched files
   described above; the builder keeps summaries and mechanical fields. This needs the shared loader
   below, and the load-time readers in the consumer map are its work list.

**Order follows the plan's type order:** weapons and armor first (`equipment.edn` exists and is
checked; 55 entries), then spells (keys and aliases), then monsters (no builder use), then magic
items. Magic items split: their ~220 data-only entries are generated, and the ~63 with mechanics
keep the mechanics in code, joined by key.

**Each type is checked first** (comparison B above): where `e5` and the corrected SRD text differ,
the departure is reviewed, per the decision that `e5`'s departures are not dismissed. Stage 1
replaces the text the app shows, so this is a content change, not a refactor.

**Spells get a richer shape than open5e's, not a copy of it** (owner, 2026-10-12): typed upcast
fields (a flat amount, a level threshold, a summon count) and a list of damage parts in place of one
damage roll. Both are in [srd-improvements.md](srd-improvements.md), "What is prose that should be
data", and both serve the homebrew spell builder too.

**Decided:** `e5`'s rewritten spell summaries are kept, as a separate summary field beside the SRD
text (owner, October 2026). **On the full spell card the SRD text is shown and the summary is not,
except as an optional section at the bottom for comparing** (owner, 2026-10-25).

**When types join (owner, 2026-10-15):** a type joins the pipeline once its data is verified, not
before. Re-running a generator is cheap, but the departure review above needs final data, so a
half-verified type would be reviewed twice. Weapons and armor are verified, so they go first and
prove the machinery; the data work on the other types carries on meanwhile.

#### Stage 1, first type: weapons and armor

Read against `integration` `b8035afe4`. The SRD side is already generated:
`resources/public/srd/2014/equipment.edn` (`emit-equipment.py`), checked by
`srd_equipment_test.clj`. The app still reads the hand-kept `weapons.cljc` and `armor.cljc`.

The two shapes differ, so the generator emits the app's shape, not the served one:

| | served file | app entry |
|---|---|---|
| damage | `{:dice "1d8" :type ...}` | `::damage-die 8`, `::damage-die-count 1`, `::damage-type` |
| weight | `3` | `"3 lb."` |
| category | `:martial-melee` | `::type :martial`, `::melee? true` |
| app-only | none | `::subtype :sword`, `::link` (Wikipedia) |

App-only fields stay in code, joined by `:key`, the same split as magic items' mechanics.

1. A generator writes the app's shape for every SRD field. **Done 2026-10-19** on agents/develop:
   `emit-app-equipment.py`; every mechanical field matches the app ([equipment-parity.md](equipment-parity.md)).
2. `weapons` and `armor` become a compile-time read of that file, merged with the app-only fields by key.
3. A test holds every existing `:key` and every field value the builder reads, so no saved character
   or homebrew item changes. Load-time readers to keep working: `srd_starting_equipment.cljc:16`,
   `equipment.cljc:233`, `magic_items.cljc:3080` (expands magic weapons and armor per base item).
4. `srd_equipment_test` compares generated against served, which become one source.

**Steps 2-4 done 2026-10-19** on `feature/srd-pipeline` (`96925cee`, pushed, no PR yet): `weapons` and
`armor` are `srd_equipment.cljc` plus the app's own fields and non-SRD entries, merged by `:key` in a
hand-kept order list (lists show the vector unsorted, so the order is kept). Old against new, entry by
entry: only the 9 Title Case names and the 30 Wikipedia links differ. `srd_equipment_test` checks every
SRD weapon's and armor's mechanics against the served file, the key order, and the app's own fields;
each claim was seen to fail under a break. The served 2014 file was stale (Sling 80/320) and is
regenerated. The PR waits for `feature/weapons-table`, which changes the same two files the same way.

**Decided:**
- **Stage 1 ships with the Summer Patch** (owner, 2026-10-19: it serves 2014). Branch
  `feature/srd-pipeline`, cut from `integration` at `d51938c1`, PR into `integration`; worktree
  `orcpub-pipeline`. The generator scripts stay on agents/develop (`scripts/srd/`); the branch carries
  only the generated data, the app's switch to reading it, and the tests. App code waits for
  `feature/weapons-table` to merge: both change `weapons.cljc`.
- **`refactor/data-extraction` is a reference, not revived** (owner, 2026-10-19). That leaf (15
  commits, 2026-02-24/25, 1,390 behind `integration` on 2026-10-19) split `views.cljs` into 7 files and
  `builders.cljs` into 10, and moved the hand-kept SRD spells, monsters and magic items into
  `*_data.cljc` files. It is kept as a worked example of how the monoliths split, not to be merged.
  Stage 1 supersedes its data moves with generated reads; its bottom-of-form buttons shipped
  separately in #68.

### One loader for the SRD and for homebrew

**Why a shared loader, honestly stated.** It is *not* the fix for the freeze users reported.
That freeze happens while *using* the builder — switching race, subrace, class, subclass — and
the perf investigation traced it to retained memory on class browsing, blocking class switches and
a double build per change. No storage read happens on a class switch. Its ranking: double-build fix
and lazy class bodies first (the double build is now debounced on `integration`; lazy class bodies
were proven by a spike, not shipped); the ~750 ms one-time library parse at builder open last, and
the plan for it (`plan-chunked-library-storage.md`) is **deprioritized** for exactly that reason.

The real case for the loader is **capacity and the SPA goal.** localStorage holds about 5.18 M
characters; IndexedDB has about 916 MB of measured origin quota. The KB records IndexedDB as "the
eventual right tier", to be scheduled on capacity grounds regardless of the perf work — not
rejected. Moving to it is per-key and asynchronous by nature, which is also exactly what on-demand
SRD edition files need. So: one loader for homebrew sources and SRD editions, justified by
capacity and the SPA, scheduled as its own decision. Treat them as one design, not two.

**Full write-up, known costs, risks and how to trial it:** [proposal-indexeddb-storage.md](proposal-indexeddb-storage.md). Parked; the owner recalls past non-starters that are not yet recorded — recover those first.

## The ladder

| rung | what it is | state |
|---|---|---|
| 1. Research | what is in 2024, how it differs, what the app cannot express | **Mostly done, uneven.** Spells, magic items, classes, rules tier and conditions surveyed; species, backgrounds and feats mapped by shape. Monsters, equipment, weapons and armor not surveyed. |
| 2. Design | the decisions below | **Made, now written down here.** |
| 3. Plan | order, dependencies, what blocks what | **Breadth first** — see *The plan* below. Per-type order follows the breadth survey. |
| 4. Framework | the machinery that makes 2024 possible in the app | **Nothing built.** One characterization test exists. |
| 5. MVP | something a user can touch | **Not started.** |
| 6. Alpha | the whole project: all six rungs done — an MVP verified against the SRD and safe to hand to testers | **Not started.** |

Proposed meaning of done for each rung, to be confirmed:

- **Research** — every row of the coverage map is past NOT STARTED.
- **Plan** — every framework item below has an owner trunk and a known blocker list.
- **Framework** — a source-tagged `e55` entry can reach a character without being silently dropped, and can be filtered by edition.
- **MVP** — still to define. Alpha is not a choice of rung: it is the end of all six (decided).

## The plan: breadth first

Earlier work went deep on spells while monsters, equipment, weapons and armor were not even
surveyed. The risk is not just neglect: a pipeline designed around spells may not fit the rest.
Monsters rename and split between editions (2014's Goblin became Goblin Minion, Warrior and Boss);
species and backgrounds changed *structure*, so they cannot be diffed; magic items are expanded per
base item. So: **one shallow, fixed-size pass over every content type before more depth anywhere.**

### Four sources, two comparisons

| source | role |
|---|---|
| SRD PDFs (`resources/srd/`) | **the authority**; disputes are settled on the rendered page |
| open5e repo data | structured transcription; the basis we generate from, after our corrections |
| 5etools (`5etools-mirror-3/5etools-src`) | **independent crosscheck only** — detector, not fixer (see *Decided*) |
| `e5` | the app's existing 2014 data, under review |

Two comparisons apply to every content type, and must not be confused:

- **A. Edition difference** — 2014 against 2024. Decides how a 2024 entry is *stored*: diff
  against its 2014 entry, in full, or neither where the structure changed.
- **B. Correctness check** — `e5`, open5e and 5etools against each other, then the PDF. Decides
  whether the data is *right*. **B comes before A**: A diffs against a 2014 base, which must be
  verified first. That base is the corrected open5e text, not `e5` directly.

Triage under B: open5e and 5etools agree and `e5` differs — `e5` is likely wrong. open5e differs
from both — a correction and an upstream fix. open5e and 5etools disagree — an error *or* a
deliberate SRD-versus-PHB difference, so check the PDF.

### Phase 1 — the breadth survey (done 2026-10-06; results in the coverage map)

For every content type, the same seven questions, results recorded in
[srd-2024-coverage.md](srd-2024-coverage.md):

1. **Counts** across `e5`, open5e 2014 and 2024, 5etools (`srd` / `srd52`) and the PDFs — measured by
   loading the data, never by grepping.
2. **Do 2014 and 2024 entries match one-to-one by name**, or rename, split, merge?
3. **What changed structurally** — fields added or removed, format changes.
4. **What mechanical fields** the sources offer.
5. **Which storage model fits** — diff, full, or neither.
6. **A small `e5` spot-check** — a handful of entries against the PDF, not the full check.
7. **What it needs from the framework** — grant vocabulary, the version filter, and so on.

### Phase 2 — order the content types (done 2026-10-07)

What phase 1 changed: the diff model fits spells and magic items, **not** monsters (rewritten:
49 of 285 pairs reach 60%) and not species, backgrounds or feats (structure changed). Monsters,
spells and gear all need a **cross-edition identity map** for renames and splits. A new 2024-only
subsystem turned up — **weapon mastery** — which belongs on the 5.5 trunk under the machinery
split.

Alpha is the end of all six rungs, so every content type is in scope and none is optional. The
order is set by dependency and by which types most threaten the pipeline design, not by
convenience.

**The order (owner, 2026-10-07).** Each type follows what it depends on.

1. **Rules and conditions.** Everything else refers to them, and they are the target of rule
   interlinking. Small; full storage (all 15 conditions were restyled).
2. **Weapons, armor, gear.** The base items that magic items and classes build on. Weapon
   mastery, the 2024-only subsystem, comes in here.
3. **Spells, then magic items.** The diff model fits both. Each needs its identity map first
   (Feeblemind became Befuddlement).
4. **Feats, then backgrounds, then species.** Backgrounds grant an origin feat, so feats come
   first. All three need grant vocabulary, which ties to `feature/grant-rows` on the refactor.
5. **Classes and subclasses.** Depend on feats, spells and weapon mastery.
6. **Monsters.** The largest job: full storage plus an identity map for renames and splits, and
   only companions on the character side depend on them.

### Phase 3 — per content type, in that order

Check B; corrections list (each fix with its PDF page, doubling as upstream PRs); canonical files
for both editions, normalising structure only; comparison A where a storage model fits, with the
60% threshold provisional and every diff round-trip checked; replace the `e55` file.

### Rules (2014), through phase 3

Check B done, corrections committed in the clone (upstream note 7), canonical file generated with
links and shipped in PR #58 with the Rules reference. 2024 rules: not started.

### Conditions, partly through phase 3

Check B done (open5e verbatim on all 30, one artifact) and comparison A done (full storage, both
editions). Canonical files are unblocked: EDN under `resources/public/srd/<edition>/` (decided).

### Spells, already partly through phase 3

Check B on description text is done: 311 of 319 `e5` spells are SRD text, 8 depart, open5e
departs in 4 others. The three upcasts open5e lacks (Heroism, Chain Lightning, Dissonant
Whispers) are present in 5etools in both editions. Remaining for spells: fields, the 12
departures on rendered pages, then the rest of phase 3. **Not the next thing** — phase 1 is.

## Decided

| decision | by |
|---|---|
| **Clear typos in the SRD itself are corrected, not preserved**, each noted in the correcting commit and the coverage map (first: SRD 5.1 Grappled "thunder-wave" for Thunderwave). Verbatim means the SRD's content, not its mistakes. | owner, 2026-10-07 |
| **Improve on the 2014 SRD, not reproduce it.** Missing features, defects and prose that should be data are analysed and fixes proposed, for both editions: [srd-improvements.md](srd-improvements.md). | owner, 2026-10-07 |
| **SRD static data files are EDN, at `resources/public/srd/<edition>/<type>.edn`**: one file per content type per edition. ClojureScript reads EDN natively, and the same file is a static asset for the SPA and readable on the JVM for tests. | owner, 2026-10-07 |
| **Fixes to open5e go upstream as codeGlaze**: commits, signature and the PR account all under one name. | owner, 2026-10-07 |
| **Alpha is the whole project**: all six rungs of the ladder done, not a subset such as browse-only or 2024-only characters. Every content type is in scope. | owner, 2026-10-06 |
| Pick the gitea `srd-2024-intergration` branch for parts; do not merge it. Its structural notes are worth keeping, its data is not. | owner |
| Model the work as two independent dials — **content** (which sources and items are on) and **rules** (what the engine computes) — not one edition switch. One switch can only express purity; users want to mix. | agreed in discussion |
| The content dial is a **version filter**: 2014, 2024, or both. **No version selected means no filter.** That decouples tagging content from filtering it, so tagging can ship without changing anything for anyone. | owner |
| Filtering **removes content from pools**, the way turning a source off in My Content already does — not a build-time condition that leaves dead entries visible. | owner |
| **A homebrew Weapon Mastery defaults to tagged 2024.** Its author can retag it for both editions (untagged) or for 2014. An exception to "tag the 2024 side": masteries are a 2024 concept, so a homebrew one starts there. [weapon-mastery-plan.md](weapon-mastery-plan.md) | owner, 2026-10-19 |
| Untagged content is shared by both editions. **Tag the 2024 side**, not 2014: 2024 is broadly a superset of 2014 content, and 2014 is what existing characters reference. | owner |
| Colliding names are distinguished by a **version appended at render time**, not stored in the name. The PDF renders names as bare strings, so a UI badge alone is not enough. | owner |
| Track the **edition, not the errata**: 5.2.1 only. (5.2.0 and 5.2.1 showed no spell *field* differences; their text has not been compared.) | owner |
| **How 2024 entries are stored.** Where an entry's 2024 text is largely the same as its 2014 text (above a word-similarity threshold), store the 2024 version as a **diff against the 2014 entry** and generate it when needed. Where it was substantially rewritten, store it in full. Any wording difference at all means a 2024 version exists: in a game built on wording, nothing counts as the same unless it is word-identical. | owner |
| 2024 data lives in `orcpub.dnd.e55.*` as **data only** — no templates, no views, no edition dispatch, and nothing that mints a namespaced keyword into a Datomic ident, so the name stays free to change. | owner (name), agreed (scope) |
| An edition is not packed into browser-loaded content: it is far too large for the plugin store. It is compiled in, like `e5`. | owner |
| Only SRD content ships. We keep **our own copies, in our own syntax, verbatim in content**. | owner |
| Sources: the **SRD PDF is the authority**; open5e's per-publisher repo data (`data/v2/wizards-of-the-coast/srd-2024`) supplies structure and is verified against it; a **rendered page** settles disputes. Text extracted from the PDF is never checked only against itself. | agreed in discussion |
| **5etools is an independent crosscheck, never a source.** It flags SRD membership on every entry (`srd` / `srd52`), with counts matching both SRDs, and maps renamed entries to their SRD names ("Bigby's Hand" carries `srd: "Arcane Hand"`). It detects errors; it does not fix them — its text is PHB text, so corrections come from the SRD PDF. Its data is fetched only to check against and is never committed. Mirrors get taken down; the live one as of 2026-10-06 is `5etools-mirror-3/5etools-src`. | owner |
| The 5.2.1 PDF and a text transcript are kept in the repository (`resources/srd/` on the trunk). | owner |
| Machinery **specific to 2024** belongs on the 5.5 trunk; machinery that is **edition-neutral or helps both** belongs to the refactor. Decide per piece. | owner |
| Species and backgrounds: **map the shapes now, mechanise later.** Keep a running list of what is needed. | owner |
| Every entry should carry as much mechanical data as can be derived, **on both editions** — prose that a machine could act on becomes data. Additive passes; a key never changes because a mechanical field arrives. | owner |
| The app already is a 2014 SRD. Anything built to present 2024 content should serve 2014 equally. | owner |
| We host our own copy of the **full** SRD in both editions — every entry, whether stored in full or as a diff. | owner |
| **Two kinds of normalisation, kept apart.** *Structural* — where data lives (all upcasting in one place, cantrip scaling included), encoding artifacts folded, gaps filled from the PDF and flagged as filled — happens at import and is stored. *Linguistic* — treating 2014 and 2024 phrasings of the same term as equal — is used only to compare and classify, and never stored, because stored text stays verbatim. | agreed in discussion |
| The app's existing 2014 data (`e5`, the original conversion) is **not assumed correct**. Verify it three ways: `e5` against open5e's `srd-2014`, then both against the SRD 5.1 PDF. | owner |
| Keep notes on problems in open5e's data and API, and PR useful fixes back upstream. | owner |
| **How our content is generated: open5e's data, plus our corrections, verified against the PDF.** Correct open5e problems as they are found, and generate our own content from the corrected data. Idea for the shape: a small corrections list (record, field, corrected value, PDF page as evidence) applied at generation time, so each correction is also a ready-made upstream PR and the upstream notes and the corrections are one list. | owner (decision), idea (shape) |
| **`e5`'s departures from the SRD are reviewed, not dismissed.** The original conversion has opinionated content, and some of it is good. Each departure is judged on its own: the hosted SRD text stays verbatim, but good editorial work is kept where it fits — some of `e5`'s rewritten spells read like summaries, which is exactly what the builder needs. | owner |
| **SRD content is served as static data files, not endpoints**, because of the pure-SPA goal above. | owner |

## Wanted, not yet scoped

- **A diff view** that shows exactly what changed in a rule or entry between editions. A separate feature on its **own branch**, not part of this work. (owner)
- **Rule interlinking and indexing** across the SRD — follow a spell or weapon property to the rule it depends on. (owner)
- **Edition subdomains**: `2014.` and `2024.` hosts preselect a version, while the bare domain uses the default or the user's saved choice. (owner)
- **Small visual tells** for 2014, 2024 and both — composed with the theme, not a theme of their own, and never the only indicator. (owner)
- **Markdown in user notes and homebrew text.** Its own branch, after the data work. Not for SRD data: the generator already turns open5e's markdown into structured data (`srd_format.py`), and the app renders no markdown anywhere today. Raw HTML stays off, since shared characters and homebrew render someone else's text. Existing notes must not change meaning (a saved `*` or `1.` at a line start), so either a per-field or per-user switch, or a small set: bold, italic, lists, line breaks. Needs a browser-side parser. (owner, 2026-10-15)

## Open decisions

- **The diff threshold is provisionally 60%** (owner, 2026-10-06), to run in development and be
  reviewed for being too lax. Two conditions make that review meaningful. Diffs are **regenerable
  build output**, produced from the 2014 base and the full 2024 text and checked by round-trip
  (applying the diff must reproduce the 2024 text exactly), so changing the threshold or correcting
  a 2014 entry means regenerating, never hand-editing. And every entry records several measures,
  not just the one the rule uses: word-sequence similarity, changed-word count, number of changed
  spans, similarity after terminology normalisation, and whether a number, dice expression or range
  changed. Whether a combined rule beats a single percentage is decided from that data, by reading
  entries that pass at 60% but score badly on the other measures. For reference, at 60% spells split
  221 diff / 96 full; at 80%, 88 / 229; at 90%, 43 / 274.
- **The `:source` value for 2024 content** (currently the placeholder `:srd-2024`). Small: the `e55` data is generated, so it is a one-line change until something outside the code stores it, such as a user's saved edition preference. Pick it when the version filter is built. The other names in play are separate contexts and need no decision: `srd52` is the branch, `e55` the namespace, and `srd-pdfs-2024` a local git tag that is redundant now the PDF is committed.
- **Subdomain precedence** — does a user's saved choice beat a `2024.` link?
- **Where the theming work lives.** It is on none of `integration`, the refactor trunk, the redesign branches or `develop`, so version tells cannot be scheduled.

## What blocks what

**The option-source gate blocks shipping any tagged content.** `using-source?` is live and its
input has had no writer since 2017, so anything carrying a `:source` other than `:phb` is
silently dropped from cleric, druid and paladin spell lists. Both committed `e55` files carry
`:source :srd-2024`. Pinned by `option_sources_characterization_test` on the trunk; the test is
expected to flip when this is fixed.

**The version filter blocks mixing editions.** Designed, not built.

**Nothing consumes `e55` yet.** The data exists and loads; no part of the app requires it. Wiring
it in is framework work, and it hits the gate above immediately.

**Grant vocabulary blocks species and backgrounds.** Level-gated grants (2024 lineages, but
also FTD dragonborn features) are edition-neutral and so refactor-side by the split above.
Background ability scores and origin feats are 2024-specific and so trunk-side.

**2024-only mode needs every replacing entry to point at the 2014 entry it replaces.** Untagged 2014 content counts as shared, so without that link a user who picks 2024-only sees both versions of every changed spell. Diff-stored entries carry the link by construction; entries stored in full need it added.

**Renamed concepts need a cross-edition identity map.** Four subclasses were renamed and their
keys changed with them (`way-of-the-open-hand` became `warrior-of-the-open-hand`); Arrow of
Slaying became Ammunition of Slaying. Nothing relates the two keys.

**Changing a filter on an existing character needs a migration test** before it ships. The
no-filter default means nothing breaks on deploy, but a user who switches to one edition can
lose content their character already holds.

## Known debt in what exists

- `e55/spells.cljc` (109 entries) discards every mechanical field the source offers —
  damage roll, save ability, area, upcast. Needs re-emitting.
- **Spell text has been compared word by word, not yet for meaning.** On open5e's text for
  both editions, 5 of the 317 shared spells are word-identical (Gentle Repose, Glibness,
  Protection from Energy, Remove Curse, Transport via Plants) and 312 differ in some wording;
  22 are new. That is a count of *wording* differences, not of rule changes. Read samples
  show five kinds mixed together: standardised terminology ("must make" -> "makes", "is
  frightened" -> "has the Frightened condition"), trimmed flavour text, clauses that may have
  moved into general rules (Mage Armor's dismissal, Heroism's lost temporary hit points), genuine
  rule changes (Control Weather now ends indoors; Banishment 60 -> 30 feet), and errors in the
  source data. No similarity score separates them; normalising terminology moves the median only
  from 71% to 72%. Telling them apart needs a classification pass, finished by reading.
- **open5e is missing upcast text for 3 spells** that the PDFs have: Heroism (2014), Chain
  Lightning and Dissonant Whispers (2024). Each would show a false difference. Everything else
  upcast-related reconciles once open5e's two ways of recording it are counted — the
  `higher_level` text and per-slot `SpellCastingOption` records — and cantrip scaling, which the
  PDFs write in the description but open5e files under `higher_level`.
- The earlier "230 identical" compared six fields and never the text, and was wrong.
  `e55/spells.cljc` (109 entries) and its header predate all of this.
- `e55/magic_items.cljc` (32 entries) has not been checked against a rendered page.
- One spell description (Guards and Wards) still ends early.
- `e5` ships 805 magic items against roughly 231 in SRD 5.1. Probably per-base-item expansion,
  as open5e does; not yet confirmed.
- **`e5` spell text, checked three ways** (description text only; fields not yet compared).
  311 of 319 `e5` spells are SRD text. **8 depart from it**, judged by how much of each text
  occurs word for word in the SRD 5.1 PDF: Branding Smite, Animal Friendship and Vicious Mockery
  (none of their wording is in the SRD), Guardian of Faith and Compulsion (little of it), and
  Counterspell, Goodberry and Hunter's Mark (partly rewritten). Their origin — paraphrase or
  another book — is unknown and matters, because only SRD content ships. Review each per the
  decision above rather than overwriting. Not yet confirmed on rendered pages.
- **The three-way check has not been run for any other content type.**
