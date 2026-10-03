# The browser test runner: how a run proves it tested something

`scripts/e2e/run.sh` runs one browser suite against a real server; `scripts/e2e/run-all.sh` runs
them all and prints a table. Read this before writing a browser suite, before trusting a green
run, and before changing either script.

## The rule

**A run that cannot show it exercised the app has failed.** Every way a run can test nothing
stops it with a named reason and a non-zero exit:

| Silent failure seen before | What the runner does now |
|---|---|
| No Chromium where a suite looked (`/opt/pw-browsers` exists only in the cloud sandbox) | finds one browser once, exports it under all four variables suites read (`E2E_CHROMIUM`, `CHROME`, `CHROME_PATH`, `PLAYWRIGHT_CHROMIUM`), or stops naming where it looked |
| A stale bundle: the server compiles from source, the bundle is an artifact nothing rebuilds | rebuilds when any source under the build's own `:watch-dirs` is newer; the folder list is read from `dev.cljs.edn`/`prod.cljs.edn`, never kept in the script |
| The wrong kind of bundle for the suite | rebuilds to the kind the suite declares, or stops if `E2E_SKIP_BUILD` forbids it |
| An app that never mounted (a dev bundle under the strict CSP) read as a 3-minute "app bug" | `boot-check.js` loads a page first and stops naming the cause: blocked scripts, a 404ing bundle, an error thrown at startup |
| "pass: 0, fail: 0" read as a result | a suite that prints no `PASS`/`ok` line fails ("reported no checks") |
| A `FAIL` line in a suite that still exited 0 | fails |
| A PDF from an earlier run inspected after an unrelated suite | inspects only PDFs written during this run |
| A branch changelog that only went red after the merge | the Changelog guard also runs on pull requests into integration/develop/main |

## Production is the default

A suite runs on a **production** bundle unless its header says otherwise. That is the build the
public site serves, the only one where advanced-compilation bugs (renamed names breaking interop)
and the strict Content Security Policy are real. A pass anywhere else says nothing about either.

Two header lines change how a suite is treated:

- `// Needs: dev bundle` — it reads the app's internals (`cljs.core`, `re_frame.db`,
  `window.orcpub`, or `test/browser/lib/orcbrew-import.js`'s `importPack`, which does). A
  production bundle compiles those names away, so these run on a development bundle with the
  CSP off, and only they do. They are inside-the-app checks, not proof the site works.
- `// Kind: probe` — it measures and prints. The runner judges it by exit code only and reports
  `PROBE`, never `PASS`.

As of 2026-10-03: 39 suites, 27 need a dev bundle, 18 are probes, **12 run on production**.

## Reading a result

Every run ends with one line:

    E2E RESULT <suite> <PASS|FAIL|PROBE> checks=<n> failed=<n> bundle=<prod|dev> [reason]

`run-all.sh` keeps the two bundles apart in its summary ("production: 12 passed…", "development
only: …"), so an inside-the-app pass cannot be mistaken for the site working. A suite with no
result line counts as `FAIL`.

## Still open (step 2)

- Rewrite the suites that read internals to check what a visitor sees, wherever that is possible,
  so more of them prove the production build. Counting calls (`spell_help_laziness`) cannot move
  and stays dev-only. `importPack` is the biggest lever: 20 suites use it.
- One server for a batch of suites (each boots its own today, 1-2 minutes apiece), with a fresh
  one only for suites that create accounts or need a clean database
  (`registrations-per-host-hourly` accumulates).

Related: [account-flows.md](account-flows.md) (the stale-artifact asymmetry),
[e2e-logged-in-sessions.md](e2e-logged-in-sessions.md), [agent-hooks.md](agent-hooks.md).
