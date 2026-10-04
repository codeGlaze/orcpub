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
| A stale bundle: the server compiles from source, the bundle is an artifact nothing rebuilds | rebuilds when any source under the build's own `:watch-dirs`, the build config, or an `:externs` file is newer; all read from `dev.cljs.edn`/`prod.cljs.edn`, never kept in the script |
| The wrong kind of bundle for the suite | rebuilds to the kind the suite declares, or stops if `E2E_SKIP_BUILD` forbids it |
| An app that never mounted (a dev bundle under the strict CSP) read as a 3-minute "app bug" | `boot-check.js` loads a page first and stops naming the cause: blocked scripts, a 404ing bundle, an error thrown at startup, or the app's own error page (which also fills `#app`) |
| "pass: 0, fail: 0" read as a result | a suite that prints no `PASS`/`ok` line fails ("reported no checks") |
| A `FAIL` line, or a printed `FAILED`, in a suite that still exited 0 (two probes caught their own errors this way) | fails; a probe that prints `FAILED` did not finish measuring |
| A PDF from an earlier run inspected after an unrelated suite | inspects only PDFs written during this run |
| A branch changelog that only went red after the merge | the Changelog guard also runs on pull requests into integration/develop/main |

## Production is the default

A suite runs on a **production** bundle unless its header says otherwise. That is the build the
public site serves, the only one where advanced-compilation bugs (renamed names breaking interop)
and the strict Content Security Policy are real. A pass anywhere else says nothing about either.

Three header lines change how a suite is treated:

- `// Needs: dev bundle` — it reads the app's internals (`cljs.core`, `re_frame.db`,
  `window.orcpub`, or `test/browser/lib/orcbrew-import.js`'s `importPack`, which does). A
  production bundle compiles those names away, so these run on a development bundle with the
  CSP off, and only they do. They are inside-the-app checks, not proof the site works.
- `// Needs: busy server` — it needs the export queue small enough to fill; the server starts
  with the `busy-export` profile (`export_busy_retry`).
- `// Kind: probe` — it measures and prints. The runner judges it by exit code only and reports
  `PROBE`, never `PASS`.
- `// Needs: pack argument` — it imports the `.orcbrew` named by its first argument. With none,
  `run.sh` passes `test/fixtures/test-pak.orcbrew` and the result line says `(fixture pack)`.
  Pass a real pack after the suite name: `run.sh test/browser/x_e2e.js path/to/pack.orcbrew`.
- `// Needs: fresh server` — `run-all.sh` gives it a server of its own instead of the shared one.
  Nothing uses it yet; it is for suites that create accounts or need a clean database
  (`registrations-per-host-hourly` accumulates on a shared server).
- `// Overlays: NOT suppressed` — it tests the cookie banner or What's New itself. Every other
  suite starts with both stamped as seen, through `test/browser/lib/suppress-overlays-preload.js`
  injected via `NODE_OPTIONS` (the release id is read from `src/cljc/orcpub/whats_new.cljc` into
  `PROBE_WHATS_NEW_RELEASE`). The What's New backdrop covering the Export button is what broke
  `export_busy_retry`. Never stamp `whats-new-seen` with a literal id in a suite: init scripts run
  in order, so a suite's old id overwrites the runner's and the panel returns with the next
  release. `orcbrew-import.js` reads the same variable.

`importPack` (`test/browser/lib/orcbrew-import.js`) drives the visible import flow and works on a
production bundle; using it is NOT a reason to tag a suite dev-only. Six suites were tagged for it;
PR #43 moved five to production. The sixth, `homebrew_render_split`, and `freeze_cpu_profile` stay
dev-only for a different reason: they split a CPU profile by function NAME, and a production bundle
renames functions, so the probe would exit 0 with every bucket empty. A probe passing on production
proves only that it ran; read what it measures before moving it.

As of 2026-10-03 (PR #43): 39 suites, 22 need a dev bundle, 18 are probes, **17 run on production**.

`run.sh --describe <suite>` prints `<bundle> <profiles> <own-server yes|no> <kind>`. It is the only
parser of these headers; `run-all.sh` asks it rather than grepping on its own.

## Reading a result

Every run ends with one line:

    E2E RESULT <suite> <PASS|FAIL|PROBE> checks=<n> failed=<n> bundle=<prod|dev> [reason]

`run-all.sh` keeps the two bundles apart in its summary ("production: 12 passed…", "development
only: …"), so an inside-the-app pass cannot be mistaken for the site working. A suite with no
result line counts as `FAIL`.

Landed on integration 2026-10-03 as PR #42 (`a3f23939`); shared servers and the header fixes in PR #43. The standing checkout for integration
runs is the `orcpub-int` worktree, detached at `origin/integration`; builds there are incremental.

## Servers: one per batch

`scripts/e2e/server.sh start [profiles] | stop` owns the server: in-memory Datomic, seeded user, a
pidfile per port, and the whole process group killed on stop (lein forks a JVM that otherwise keeps
the port). It refuses to start when something already answers on the port, so a stale server can
never be tested by mistake. `stop` signals the recorded group only while it still runs `e2e-boot`,
so a pidfile left by a killed run cannot hit a reused group id.

`run.sh` alone starts and stops its own. `run-all.sh` starts one for the production batch and one
for the development batch (`CSP_POLICY=none`), and runs those suites with `E2E_SHARED_SERVER=1`;
a suite on another profile (`busy-export`) or tagged `fresh server` gets its own. That is 3 boots
instead of 39: the full run took **20 minutes** on 2026-10-03. Each suite still gets a fresh
browser, so client state (localStorage) never leaks between suites; only the database is shared.

A suite that runs past `E2E_SUITE_TIMEOUT` (default 1200 s) is killed and reported `FAIL`.

## Known failures

- `chunked_parse_spike`: defaults to `dev-scratch/paks/mega-64.orcbrew`, a local scratch file
  that no longer exists. Pass a large pack as its argument, or regenerate the fixture.

## Two failures that were the tests, not the app (fixed 2026-10-03, PR #43)

- `export_busy_retry` failed about 4 runs in 10. The server's limit was fine: 8 simultaneous
  exports got 1 PDF and 7 busy pages. But the slot queue is a FAIR semaphore, and with the busy
  profile's 250ms wait an export arriving under load reached the front and got its PDF 5 times in
  12. A heavier load made it worse (fewer requests queued ahead). The `busy-export` profile now
  waits 1ms (12 of 12 busy), and the suite stops with one reason if it is not busy, rather than six
  failures and a vacuous "delivered" pass. The profile is test-only; production waits 30s.
- `starting_equipment_browser` read `:browser-test-class`, but save mints a source-tagged key
  (`:browser-test-class-brttse`, D10b, `address-for` in events.cljs) and writes it back onto the
  builder item. The test now reads that key. The class and its weapons had always saved.

Lesson: before calling a failure an app bug or a flake, measure it directly (the burst and
12-trial scripts took minutes) and dump the state the check reads.

## Still open

- Rewrite the suites that read internals to check what a visitor sees, wherever possible, so more
  of them prove the production build. Counting calls (`spell_help_laziness`) cannot move.

Related: [account-flows.md](account-flows.md) (the stale-artifact asymmetry),
[e2e-logged-in-sessions.md](e2e-logged-in-sessions.md), [agent-hooks.md](agent-hooks.md).
