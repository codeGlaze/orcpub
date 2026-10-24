# Testing guide

> Which suite to run, where a new test goes, and the gotchas. Rules for tests (seen to fail first,
> assertions that can fail): `AGENTS.md`, "Tests"; probes: "Running the suites and probes", below.

---

## The layers

Pick the layer before writing the test: the wrong one wastes a build, or cannot reach the code.

| Layer | Where | Server | Run with | Use it for |
|---|---|---|---|---|
| JVM | `test/clj/`, `test/cljc/` | no | `lein test` (`lein test :only ns/test-name` for one) | pure functions, specs, entity and template building, routes, DB, PDF |
| cljs / re-frame | `test/cljs/` | no | `lein fig:test && node test/e2e/cljs-harness.js` | events, subs, db shape: anything in a `.cljs` file |
| real SPA, no backend | `test/e2e/*.js` | no | `lein fig:build`, then the script (`test/e2e/README.md`) | widget DOM to data, builder forms, localStorage |
| real app, DB and login | `scripts/e2e/*.js`, `test/browser/*_e2e.js` | yes | `./scripts/e2e/run.sh <suite>`: it builds the bundle, boots a server with seeded users (kaylee / serenity99, zoe / washburne7) and judges the run | anything that needs a session: saved characters, items, folders, parties, PDF export |

- **A regression test goes in the lowest layer that can reach the bug.** A bug in a `.cljs` event or
  sub needs the cljs layer: a `.clj` test cannot require `.cljs` code. A bug that only shows once a
  character is saved needs the real-app layer.
- **Every real-app suite:** `./scripts/e2e/run-all.sh` (`--probes` adds the measuring probes). A suite
  is killed after `E2E_SUITE_TIMEOUT` seconds (default 1200).
- **The probe runner** (`node scripts/test/run-browser-probes.js`) runs only the probes listed in its
  `PROBES` array, against a server you start. `lein e2e-server` seeds no user, so the signed-in
  lanes skip; for them build the bundle and the stylesheet (`lein fig:build && lein garden once`; `server.sh`
  builds nothing), start the seeded server with `CSP_POLICY=none ./scripts/e2e/server.sh start`
  (`stop` when done), and run the probes with `ORCPUB_TEST_USER=kaylee
  ORCPUB_TEST_PASSWORD=serenity99`. `CSP_POLICY=none` is needed
  for a dev bundle; `server.sh` sets the in-memory database and the rest of the environment.
- Seeded logins, and why seeding a session in localStorage cannot work:
  `docs/kb/e2e-logged-in-sessions.md`.

## Before a PR

`lein test`, the cljs suite, the full `lein lint`, and the real-app suites for what the branch
changed (`run-all.sh` when in doubt). A deployment change also needs a green heavy Docker run on
its PR before review is requested (which files count, and how they run: `docs/GIT_WORKFLOW.md`,
"Heavy Docker checks").

## Before committing

| Changed | Run |
|---|---|
| `src/clj/`, `src/cljc/`, `test/clj/`, `test/cljc/`, `project.clj` | `lein test` |
| `src/cljs/`, `src/cljc/`, `web/cljs/`, `test/cljs/`, `project.clj` | `lein fig:build`, then the cljs suite: `lein fig:test && node test/e2e/cljs-harness.js` |
| any Clojure file | lint it: `lein trampoline with-profile lint run -m clj-kondo.main --lint <files> --fail-level error` (the level `lein lint` uses) |

The full `lein lint` before committing a merge (merge with `--no-commit`: `docs/GIT_WORKFLOW.md`,
"Merging integration in"), before opening a PR, and before marking a draft ready.

## Seeing a test fail

The rule is in `AGENTS.md`, "Tests". The commands:

1. **Commit the code under test** (the fix or the feature), without the test. Never break a file
   that has uncommitted changes: the restore overwrites it for good.
2. **Break it, one claim at a time.** For each thing the test claims, restore only the files (or
   the hunk) that claim depends on to the source before the commit:
   `git restore --source=<that commit>~1 -- <those files>`
   (`git show --name-only --no-renames --format= <that commit>` lists the commit's files; restore
   also removes files the commit added). Run the uncommitted test and see the checks for that claim
   fail, and only those. Then restore (step 3) before the next claim.
   For a test of code committed earlier, with no commit to break back to, edit that code path by
   hand instead, one break per claim, and restore it the same way.
3. **Restore:** `git restore --source=HEAD -- <the same files>`, never a whole directory others may
   be editing, and confirm those files are clean.
4. **Run the test green and commit it,** with the failing output in its message; put the failing
   output in the PR beside the pass too.

## Build tests to copy

`test/cljc/orcpub/dnd/e5/warlock_test.clj` is the pattern for entity/build tests: it builds a
character through the full `entity/build` pipeline with inline configs, and checks class features,
ability scores, proficiencies and spell slots.

## Gotchas

### clj vs cljs test boundary

- `.clj` tests run on the JVM only (`lein test`), and cannot require `.cljs` files.
- Data stuck in `.cljs` files must be duplicated inline in a `.clj` test, or moved to `.cljc`
  (`warlock_test.clj` shows the inline pattern).

### PersistentArrayMap threshold

Maps with ≤8 keys use `PersistentArrayMap` (preserves insertion order).
Adding a 9th+ NEW key promotes to `PersistentHashMap` (arbitrary order).
Use `(apply array-map ...)` to force ordered maps at any size.
This caused the `strict-round-trip-2` test failure — see `entity.cljc:from-strict-selections`.

## Running the suites and probes

```bash
lein test                                      # JVM suite
lein fig:test && node test/e2e/cljs-harness.js # cljs suite, headless (docs/kb/cljs-headless-harness.md)
./scripts/e2e/run.sh <suite>                   # builds, boots a seeded server (kaylee / serenity99), runs, judges
lein fig:build && CSP_POLICY=none lein e2e-server  # dev bundle, no seeded user; then, in another shell:
node scripts/test/run-browser-probes.js        # every probe listed in its PROBES array
```

Which layer a test belongs in: "The layers", above. There is no `e2e/` directory and no `npm test`.
Stop the e2e server before any other `lein` command in the same worktree (AGENTS.md). Neither `lein test` nor the cljs harness runs the
browser probes, so "both suites green" says nothing about them. `ORCBREW_PACK=<pack>.orcbrew` enables the probes that need
homebrew; one that cannot run prints `SKIP` with its reason. Test layers, seeded logins, and why
seeding a session in localStorage cannot work: `docs/kb/e2e-logged-in-sessions.md` (on a code branch,
`git show origin/agents/develop:docs/kb/e2e-logged-in-sessions.md`).

- **Before committing a UI change:** check the browser console for JS errors, and that the routes
  you touched still load.
- **Write assertions that can fail.** A missing control is a failure, not a reason to print SKIP;
  `after <= opened` passes when filtering does nothing.
- **Probes may not stop asserting:** per-probe assertion counts live in
  `scripts/test/probe-baseline.json`, and running fewer is a failure. A new probe runs only once it is listed in
  the `PROBES` array of `scripts/test/run-browser-probes.js`; then, only after it passes, record its count alone:
  `ONLY=<new-probe-file-name> UPDATE_BASELINE=1 node scripts/test/run-browser-probes.js`. `ONLY`
  matches substrings, and every probe that ran is rewritten, even one that failed, so check that
  the "baseline updated for:" line names only the new probe. Don't guard checks with
  `if (await x.count())`: a renamed control takes its checks with it and the probe still exits 0.
- **A stuck probe is caught by silence first:** the runner fails a probe silent for 180s
  (`PROBE_SILENCE_S`). A probe that keeps printing but never finishes hits a per-probe backstop of
  twice its measured runtime, at least 180s (`PROBE_BUDGET_S` overrides). A flat limit short enough
  to catch a hang would kill the slowest probe, which legitimately runs 393s. Changing these is the
  owner's call: ask, with the reason. Hung runs have sat 45 minutes to an hour before anyone looked,
  so when anything runs far past its expected time, check on it instead of waiting.
- **A slow probe is traced, not reasoned about:** `node scripts/test/trace-probe.js <probe>`
  reports the slowest calls. A round number repeated is a timeout, not work: several calls at
  exactly 30.0s mean a default is being waited out. (One probe's runtime was guessed wrong four
  times before a trace found nine swallowed 30s timeouts: 400s to 126s.) Never
  `await x.click().catch(() => {})` without a `timeout`; use `clickIfVisible()`.

## App facts that trip tests

- Routes use **`/dnd/5e/`**, not `/dnd/e5/`: `/pages/dnd/5e/character-builder`,
  `/pages/dnd/5e/spells`, `/dnd/5e/my-content` (no `/pages/`).
- `/` is the server-rendered splash page (`.splash-button`, no header); other pages render into
  `#app`.
- Under `lein run`, `WebSocket connection to 'ws://localhost:3449/figwheel-ws/dev' failed` is
  expected: the Figwheel client is compiled in, but Figwheel is not running.
