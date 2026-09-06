# orcpub — notes for agents

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

## Knowledge base

`docs/kb/README.md` indexes findings that were expensive to get: the builder-freeze root
cause, the storage-layer measurements, the browser probes and what each answers. Read
`docs/kb/verification-discipline.md` before writing a performance probe -- it lists the
probe defects that have produced confident wrong answers here.

## Build / test commands

- `lein fig:build`   — compile the dev CLJS build (needed before browser e2e).
- `lein garden once` — compile CSS to `resources/public/css/compiled/` (needed for screenshots).
- `lein test`        — JVM test suite.
- Browser e2e: `test/browser/*.js` (see `test/browser/README.md`) — run against `lein e2e-server`.
- `node scripts/test/run-browser-probes.js` — runs every asserting browser probe, exits
  non-zero on failure. Neither test suite touches `test/browser/`, so run this before
  claiming a branch is green.

## Datomic

The project is on **Datomic Pro 1.0.7482**, which is Java-21-compatible. `datomic:mem://`
needs no transactor. The `docs/kb/DATOMIC_JAVA21_TEST_RESULTS.md` doc describes a **resolved**
issue with the *old* Datomic Free on Java 8/21 — it is history, not the current state.
