# orcpub — notes for agents

## Read this first — three pages, in this order

1. **`docs/kb/roadmap.md`** — WHAT THIS BRANCH IS. The arc, a BUILT / DECIDED / OPEN ledger anchored
   to commits, the doc map, the critical path. If you are about to design, plan, or propose
   anything, check the ledger first: this branch has re-derived already-built and already-decided
   pieces from scratch more than once, and every time the answer was already on that page.
2. **`docs/kb/pool-grant-map.md`** — the pool + grant web on one page (REAL vs AIR, with dates and
   provenance). Read it before touching anything that grants, offers a choice, or registers a pool.
3. **`docs/kb/before-you-start.md`** — review lessons indexed by what you are about to do (design a
   control, add a CSS class, convert a builder, trust a CSS change). Short by design.

## Working rules on this branch (decided; don't re-litigate — cite the D-number if you must)

- **One mechanism per job (D29).** Before adding anything, ask: does a working path already do this?
  If yes, extend or replace it — never add a parallel one.
- **Deprecate, don't delete (D34).** Released shapes get a characterization test and a read-shim.
- **No shims for branch-local shapes.** If a spelling or key was introduced on this branch and has
  never been released, rename it everywhere. A compatibility alias for it is tech debt from birth.
- **Registration is one entry.** Adding a content type is one entry in `content_types.cljc`; adding a
  grantable pool is one entry in `grant_pools.cljc`. If your change needs edits in several files to
  wire one thing in, stop — that is the pattern this branch exists to remove.
- **Record decisions where they are made.** The ledger in `roadmap.md`, the D-log in
  `content-extensibility-decisions.md`. Current truth at the top, history at the tail, reversals in a
  Corrections section — never overwrite.

## The knowledge base is the group memory — search it before you research anything

```
grep -ril "<term>" docs/kb/          # has anyone been here before?
git for-each-ref --sort=-committerdate refs/remotes/origin | head -25   # is it on a branch?
```

**Grep is the search.** Measured on fourteen realistic queries, grepping the corpus answered all
fourteen; the curated index answered nine and `docs/kb/README.md` answered six. Use
`docs/kb/topic-index.md` (generated) to find *which* document owns a topic, and `README.md` for what
each one is; use grep to find out *whether* a thing has been looked at.

This has been got wrong repeatedly and expensively: a builder schema system was designed twice, a
fighting-style fix was re-planned three days after it had been decided in a document named after the
branch, and a whole front-end design system sat on `port/redesign-on-refactor` for two months while
this work invented its own colours and spacing. All three were one grep away.

## Running the real app (do this for browser e2e — don't fake it)

The **full stack runs locally in-memory** — no transactor, no external database:

```
lein e2e-server      # Pedestal + in-memory Datomic, serving the app on http://localhost:8890
```

(That's `lein with-profile +e2e run`; the `:e2e` profile sets `datomic:mem://orcpub`.)
Then drive Playwright/curl against `http://localhost:8890` through the **real UI** — real
imports, saves, and conflicts go through the live backend.

Do **not** serve the compiled JS off a bare static file server and drive the app by
`dispatch_sync`-ing re-frame events / poking `app-db`. That bypasses the backend, misses
real flows (e.g. import-conflict modals never surface), and produces misleading results.
Run the real server.

## Build / test commands

- `lein fig:build`   — compile the dev CLJS build (needed before browser e2e).
- `lein garden once` — compile CSS to `resources/public/css/compiled/` (needed for screenshots).
- `lein test`        — JVM test suite.
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
