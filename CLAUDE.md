# orcpub — notes for agents

## Before you theorise, go and look

This repository documents itself more than it looks like it does, and the cost of not
checking is high: a whole session was spent re-deriving things already written down here.
Read the relevant one **before** forming a theory about behaviour — and verify a document
exists before citing it, rather than trusting a remembered filename.

- `docs/README.md` — the index of everything in `docs/`. Start here.
- `test/browser/README.md` — how to write a browser probe, and the two ways a probe lies.
  Read it before writing one. In particular: a missing control is a failure, not a reason to
  swallow the error and carry on, and a probe that silently stops asserting reports a broken
  feature as working.
- `docs/HOMEBREW_REQUIRED_FIELDS.md` — which fields each content type needs, and which ones
  break a feature silently when missing.
- `docs/ORCBREW_FILE_VALIDATION.md` — what import rejects, what it repairs, and what it lets
  through untouched.
- `docs/ORCBREW-AUTHORING.md` and `docs/kb/orcbrew-value-vocabulary.md` — what may go in an
  `.orcbrew` and what the app does with it. Nothing validates the *shape* of a brew; these say
  what the consuming code actually reads.
- `docs/CONTRIBUTING.md` — branching, the changelog fold, the roadmap rules, authorship.
- `docs/TODO.md` — the shared roadmap. Every section names its owning branch in its Status.

A design decision that looks wrong is often load-bearing and explained in a docstring one file
away — `content_specs.cljc` is the standing example. Read the namespace before proposing to
change it.

The wider knowledge base lives in `docs/kb/`, which on this line holds only the orcbrew value
vocabulary; the rest of it is on `refactor/content-extensibility` until the lines meet.

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

Three separate steps — skipping the second is why new CSS classes render unstyled:

- `lein fig:build`   — compile the dev CLJS build (needed before browser e2e).
- `lein garden once` — compile CSS to `resources/public/css/compiled/` (needed for screenshots).
- `lein test`        — JVM test suite.
- Browser e2e: `test/browser/*.js` (see `test/browser/README.md`) — run against `lein e2e-server`.

## Datomic

The project is on **Datomic Pro 1.0.7482**, which is Java-21-compatible. `datomic:mem://`
needs no transactor.
