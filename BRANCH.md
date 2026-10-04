# Branch Context: feature/grant-rows

## Purpose

The live tip of the content-extensibility refactor, the Fall Update line. Homebrew content that
carries real mechanics instead of inert text: a content type declares its fields once, and grants,
pools and ability-increase spreads work across silos the way official content does. Continues the
work that earlier ran as `feature/fighting-style-authoring` and, before that, the harness branch
`claude/zen-wright-04xhdz`.

**Founded on stability and flexibility as the same abstraction.** Every cross-type link used to
be bespoke positional wiring, and that bespoke-ness was both the multi-file cost and the fragility.
One open pool + grant layer, built from parts the engine already has, collapses N×M wirings to N+M
declarations down one tested path. Readability is a constraint on that, not a ceiling.

## Core facts — know these before touching homebrew

- **The homebrew LIBRARY never touches the server.** The library (`:plugins`) — everything made in the
  homebrew builders or imported from an `.orcbrew` — lives in the browser's localStorage and nowhere
  else. No server route accepts or stores it, and no request carries it.
  It moves between browsers only two ways: an **`.orcbrew` export/import**, or a **share link**,
  which carries the character's homebrew in the URL *fragment* (after `#`, never sent to a server)
  and lands in a view-only overlay (`:shared-plugins`) until the recipient chooses Keep. Clearing
  browser data loses the library unless it was exported.
  *Consequence:* every rule about the library is enforced in the client or not at all. There is no
  server-side validation, migration, backup or conflict resolution to fall back on.
  **Not all custom content is the library**, though: custom content entered inline on a character
  travels with the character, and **custom magic items are server-backed** (`::mi/custom-items`,
  keyed by `:db/id`). Three stores, one of them browser-only — `docs/kb/custom-content-lifecycle.md`.
  *(Corrected 2026-09-27: this line first said "homebrew never touches the server", which is true of
  the library only.)*
- **Characters DO live on the server** (`routes.clj` `save-character`), and they point at homebrew
  **only by key**. A character on the server can name content that exists in one browser only.
- **Key = address, name = display (D10).** A key is minted once and fixed; a rename is a name edit.
  `:former-keys` heals characters across a deliberate key change.
- **Two tabs, library:** on `port/save-gate` (`bf1ca6a5`) a tab reloads the library when another
  tab writes it, and a write made on a stale copy is merged onto the other tab's rather than
  overwriting it (`homebrew-keys-design.md`, step 6). On this branch and on `integration`, nothing
  coordinates tabs yet. **Characters** have the same class of problem, documented on
  `agents/develop` (`multi-tab-character-contamination.md`), and it is not addressed.

## Read this first — three pages, in this order

1. **`docs/kb/roadmap.md`** — what this branch is. The arc, a BUILT / DECIDED / OPEN ledger anchored
   to commits, the doc map, the critical path. If you are about to design, plan, or propose
   anything, check the ledger first: this branch has re-derived already-built and already-decided
   pieces from scratch more than once, and every time the answer was already on that page.
2. **`docs/kb/pool-grant-map.md`** — the pool + grant web on one page (REAL vs AIR, with dates and
   provenance). Read it before touching anything that grants, offers a choice, or registers a pool.
3. **`docs/kb/before-you-start.md`** — review lessons indexed by what you are about to do (design a
   control, add a CSS class, convert a builder, trust a CSS change). Short by design.

Then **`docs/kb/plan-next.md`** for what is being worked on now.

## Working rules on this branch (decided; don't re-litigate — cite the D-number if you must)

- **One mechanism per job (D29).** Before adding anything, ask: does a working path already do this?
  If yes, extend or replace it — never add a parallel one.
- **Deprecate, don't delete (D34).** Released shapes get a characterization test and a read-shim.
- **No shims for branch-local shapes.** If a spelling or key was introduced on this branch and has
  never been released, rename it everywhere. A compatibility alias for it is tech debt from birth.
- **Registration is one entry.** Adding a content type is one entry in `content_types.cljc`; adding a
  grantable pool is one entry in `grant_pools.cljc`. If your change needs edits in several files to
  wire one thing in, stop — that is the pattern this branch exists to remove.
- **Audit what a new piece REPLACES before building it (D17).** Find the existing path that does
  the job; if the new thing is not thicker than it, or drops what it carries (`:ref`, `:tags`,
  modifiers), extend the existing path instead.
- **A grant compiles to the same `selection-cfg` the bespoke path made (D30).** Same tags, same
  placement, same mechanics. If a granted choice renders somewhere the hand-wired one did not, that
  is a regression, not a feature.
- **The rules index is the status table at the top of `content-extensibility-decisions.md`** — one
  line per D-number. Check a design against it before proposing; do not re-derive from the history
  below it.
- **Comments and docstrings: `AGENTS.md`, "Comments and Docstrings".** Spec only, history in
  `docs/kb/`, `FIELD NOTE`s recorded by hash, every hit of `comment_discipline_test` fixed or reviewed.
  Measurements and examples: `documentation-discipline.md`.
- **Record decisions where they are made.** The ledger in `roadmap.md`, the D-log in
  `content-extensibility-decisions.md`. Current truth at the top, history at the tail, reversals in a
  Corrections section — never overwrite.
- **Identity comes from a stored `:key`, never re-derived from a display `:name` (D10).** Pass
  each item's stored `:key` to `option-cfg`. Written down in June; the save rework broke it anyway
  by probing for items by name (`homebrew-save-rework.md`), so it is worth reading twice.
- **Catalogs are layered, memoized `reg-sub`s that grants reference (D11)** — never recomputed
  inside hot subs.
- **Tests must be falsifiable.** Every test goes red if the code it covers breaks. Gut check: if I
  break the code, does this fail? Verify by removing the fix and watching the right test fail.
- **Fix bugs on sight**, unless one is deep enough to deserve its own branch — then file and scope it.
- **An enumeration is part of the fix.** When a fix is correct only if "every caller does X", the
  search for those callers is part of the fix and gets the same scrutiny. Search by at least two
  independent methods; a single grep that assumes the call syntax missed ten call sites once
  (`homebrew-save-rework.md`).

## The knowledge base is the group memory — search it before you research anything

```
grep -ril "<term>" docs/kb/          # has anyone been here before?
git ls-tree -r origin/agents/develop --name-only docs/kb/   # ...including what only agents/develop has
git for-each-ref --sort=-committerdate refs/remotes/origin | head -25   # is it on a branch?
```

**Grep is the search.** Measured on fourteen realistic queries, grepping the corpus answered all
fourteen; the curated index answered nine and `docs/kb/README.md` answered six. Use
`docs/kb/topic-index.md` (generated) to find *which* document owns a topic, and `README.md` for what
each one is; use grep to find out *whether* a thing has been looked at.

This has been got wrong repeatedly and expensively: a builder schema system was designed twice, a
fighting-style fix was re-planned three days after it had been decided in a document named after the
branch, and a whole front-end design system sat on `port/redesign-on-refactor` for two months while
this work invented its own colours and spacing. All three were one grep away. The two KBs are not
the same set: `agents/develop` holds pages this branch does not, so check both.

## Running the real app (do this for browser e2e — don't fake it)

The **full stack runs locally in-memory** — no transactor, no external database:

```
lein e2e-server      # Pedestal + in-memory Datomic, serving the app on http://localhost:8890
```

(That's `lein with-profile +e2e run`; the `:e2e` profile sets `datomic:mem://orcpub`.)
Then drive Playwright/curl against `http://localhost:8890` through the **real UI**.

Do **not** serve the compiled JS off a bare static file server and drive the app by
`dispatch_sync`-ing re-frame events / poking `app-db`. That skips the UI flows where real
behaviour lives — the import-conflict modal, for one, never surfaces — and produces misleading
results. Homebrew itself never reaches the backend (see Core facts); what the real server adds is
the real SPA, real routing, and the character endpoints.

## Build / test commands

- `lein fig:build`   — compile the dev CLJS build (needed before browser e2e).
- `lein garden once` — compile CSS to `resources/public/css/compiled/` (needed for screenshots).
- `lein test`        — JVM test suite.
- cljs suite: `lein fig:test`, then `node test/e2e/cljs-harness.js`. The harness must serve JS as
  `charset=utf-8`; ~270 undefined-namespace errors at once means it did not, not that code broke.
- Browser e2e: **`test/e2e/*.js`** — run against `lein e2e-server`. Start with
  `test/e2e/README.md`; `lib.js` holds the shared helpers (finding a control by its label, driving
  chips and the select-menu popover, the app-db reader). `test/browser/*.js` is an older parallel
  directory that has not been folded in yet.
- `lein garden once` **can fail while `lein fig:build` and the whole e2e suite then pass against
  stale CSS.** Check its exit code before believing a CSS change.

## Datomic

The project is on **Datomic Pro 1.0.7482**, which is Java-21-compatible. `datomic:mem://`
needs no transactor. The `docs/kb/DATOMIC_JAVA21_TEST_RESULTS.md` doc describes a **resolved**
issue with the *old* Datomic Free on Java 8/21 — it is history, not the current state.

## Current State

**Live status is the open checklist at the top of `docs/kb/plan-next.md`.** This section holds only
what changes rarely: what each branch is for, and which PRs carry the work. When a PR opens, merges
or closes, or a branch changes role, update this section in the same commit.

- **This branch's purpose is grants** (`plan-next.md` items 1–6).
- **It hosts the planning and the KB for the homebrew-keys fix** (owner's decision, 2026-09-27); the
  code for that fix lands on `port/save-gate`. Map: `homebrew-key-map.md`; design and as-built log:
  `homebrew-keys-design.md`; the rework's history: `homebrew-save-rework.md`; the mechanism:
  `key-collision-behavior.md`.

| branch | role |
|---|---|
| `feature/grant-rows` | this branch: grants, and the KB for everything above |
| `refactor/content-extensibility` | the refactor trunk |
| `port/save-gate` | the homebrew-keys fix; merged into `integration` as `614c17ff` (#37, 2026-09-30) |
| `fix/comment-check` | folded into #37 (2026-09-30); PR #38 closed. Owner to delete the branch |
| `integration` | the active trunk; carries the homebrew-keys fix and #39; comment baseline empty |
| `fix/comment-debt` | merged into `integration` as `1aa80ec3` (#39, 2026-09-30): comment debt to zero, party-route fix. Owner to delete the branch |
| `fix/hidden-multiclass-skill-pick` | the hidden-pick fix (Part A), from `integration`: skills done, equipment and tools next; no PR yet |
| `feature/character-rescue` | from `integration`: fixing a character without the app (`character-rescue.md`); its own session |

| PR | from → to | note |
|---|---|---|
| #32 | `integration` → `develop` | the route by which the homebrew-keys fix reaches `develop` |
| #34 | `f1852203-accounts` → `develop` | paused; its homebrew commits were inputs to #37 |
| #35 | `hotfix/locale-safety` → `mirror/upstream-develop` | the clean hotfix |

## Deferred follow-ups — HIGHLIGHT AT BRANCH CLOSE

Deliberately **not** done here, and they **must be surfaced when this branch is finalized** — repeat
them in the PR description and the handoff, so they do not vanish into the diff:

1. **Character-validation contract** (own branch). The computed character is the one user-facing
   representation with no validation. Intent and a falsifiable charter: `docs/kb/character-validation.md`.
2. **Get the ClojureScript tests into CI** (own branch). CI runs only the JVM gate, so the cljs
   suite is gated by hand in a container (`docs/kb/cljs-headless-harness.md`). Pairs with 1.

## Workflow

- **Agent docs follow `agents/develop`'s three tiers** (`docs/DOC-CONVENTIONS.md` there).
  `CLAUDE.md` is the thin bootstrapper and `AGENTS.md` the universal rules — both copied in from
  `agents/develop` per environment and gitignored here. This `BRANCH.md` is tracked here.
  `agents/develop`'s `scripts/agent-setup.sh` copies `CLAUDE.md` and `.claude/` but not
  `AGENTS.md`, so copy it by hand until that is fixed:
  `git show origin/agents/develop:AGENTS.md > AGENTS.md`.
- **Stop rules and budgets (`AGENTS.md`) apply to every turn that does work.**
- **`docs/kb/` and this file are committed here during development, and move to `agents/develop`
  before this branch merges outward.** `integration` ships to the public repo. Edit KB pages here
  only: the copy on `agents/develop` (2026-09-29, `0006659e`) is a snapshot, and it takes the final
  form when this branch merges (owner's decision, 2026-09-29). `AGENTS.md` is edited on
  `agents/develop`, where it lives.
- Commits are authored and committed as `codeGlaze <github@codeglaze.com>`, with no AI attribution
  in any pushed artifact.
- Documentation changes with every commit: current truth at the top, history at the tail.

## Related Docs

On this branch: `docs/kb/roadmap.md`, `plan-next.md`, `homebrew-save-rework.md`,
`key-collision-behavior.md`, `source-tagged-keys.md`, `handoff-integration-branches.md`.
On `agents/develop` only: `docs/DOC-CONVENTIONS.md`, `docs/kb/multi-tab-character-contamination.md`.
This file's previous version (June, with the phase history and the import-validation triage), kept
verbatim: `docs/kb/branch-context-history.md`.
