# perf/srd-spells-static

**Purpose:** take the SRD spell text out of the app bundle into a static file fetched when a page
needs it, with no content change; plus two cleanups riding along as separate commits.
**Base:** `integration` at `b14a515e4` (#97). **PR into:** `integration`.
**KB:** [`srd-2024-roadmap.md`](../../docs/kb/srd-2024-roadmap.md) ("One pipeline", stage 2),
[`srd-data-consumers.md`](../../docs/kb/srd-data-consumers.md) (every reader, with file:line),
[`page-load-budget.md`](../../docs/kb/page-load-budget.md).

## State (updated 2026-11-25)

- Done: the branch, its worktree (`/home/codeglaze/projects/orcpub-srd-spells`), the branch changelog.
- Next, in order:
  1. Measure first: which parts of `spells.cljc` (4,265 lines) carry the compressed weight. Use a
     source map from a `fig:prod` build. The bundle is 956 KB against the 960 KB ceiling
     (`page_load_budget_e2e.js`).
  2. Write the text fields to `resources/public/srd/2014/spells.edn`, keyed by each spell's key
     (`(or (:key s) (name-to-kw (:name s)))`, `spells.cljc:4194`). The builder keeps the fields its
     rules use. Generate the file from today's data with a dev script and check the output is identical
     to what the app shows now: no wording changes (AGENTS.md, "SRD text is verbatim").
  3. One loader for the file, built on the existing fetch-on-use pattern (`:spell-rolls` at
     `events.cljs` ~4378, `srd_rules.cljc`). It's fetched the first time a page shows spell text,
     then cached, with a version stamp in the address. Every reader in `srd-data-consumers.md`'s
     spells row must handle "not loaded yet". The JVM side (`routes.clj` spell cards, spellbook,
     PDF) reads the same file from resources.
  4. Browser check: the builder's start-up makes no request for the file, and a page that shows
     spell text makes one. The page-load test's allowance gets the new file.
  5. Separate commits, no behaviour change: rename `[charred.api :as cheshire]` to
     `[charred.api :as json]` in all five namespaces; fix the 48 shadowed-name warnings, deleting
     each line from `test/lint-baseline.txt` as it goes.

## Owner decisions

- 2026-11-25: the shortcut. Spell text leaves the hand-kept `spells.cljc` as it is. The generated
  pipeline (`feature/srd-pipeline`) overwrites the same file later, so the file uses the planned
  shape from day one. The alias rename and the shadowed-name fixes ride on this branch.

## Open questions for the owner

- None yet.

## Gotchas for the next agent

- Homebrew spells merge into the same map through `::spells5e/spells-map` (`spell_subs.cljs`
  ~1309). Their text stays in the homebrew. Only the SRD entries' text moves.
- Keys are user-facing: saved characters and class spell lists refer to them, and
  `spell-key-aliases` / `resolve-spell-key` (`spells.cljc` ~4231) map old keys. Don't change a key.
- The heavy-Docker paths: none expected. Check before pushing if a deploy file gets touched.
