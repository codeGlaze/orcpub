# Getting a logged-in browser against a real server

The thing that is easy to conclude is impossible, and is not. Verified 2026-09-13 against
`integration` `36766010`.

**There is a seeded, verified user, and a one-command script that boots a server holding it.**
Neither was mentioned in `START-HERE.md`, `BRANCH.md`, `CLAUDE.md` or any KB doc before this one,
which is why two sessions in a row concluded that logged-in browser testing needed credentials
nobody had.

## The credentials

`dev/e2e_boot.clj` seeds one verified user at boot:

| | |
|---|---|
| username | `kaylee` |
| email | `kaylee@example.com` |
| password | `serenity99` |
| verified? | **yes** — can log in immediately, no email step |

## The one command

```bash
./scripts/e2e/run.sh [script.js]        # defaults to scripts/e2e/run.js
```

It builds the bundle and stylesheet if missing, starts the server on `:8890` against
`datomic:mem://orcpub-e2e`, seeds the user, waits for the port, runs your script with `E2E_BASE`
set, then tears the whole process group down. `E2E_PORT`, `E2E_LOG` and `E2E_OUT` override the
defaults.

**Why a script and not two commands:** `datomic:mem://` exists only inside the JVM that created it.
Seeding from a separate `lein run -m user create-user` talks to a *different, empty* database. That
is the whole reason `e2e_boot.clj` starts the server and seeds the user in one process, and it is
why **`scripts/create_dummy_user.sh` cannot give you a user on the e2e server** — that script is for
a persistent dev database.

## Which harness for what

Four layers. Picking the wrong one wastes a build.

| layer | where | server needed | use it for |
|---|---|---|---|
| JVM | `test/clj/`, `test/cljc/` | no | pure fns, specs, entity/template building. `lein test` |
| cljs / re-frame | `test/cljs/` | no | subs, events, db shape. See [cljs-headless-harness.md](cljs-headless-harness.md) |
| real SPA, no backend | `test/e2e/*.js` | no | widget DOM→data coercion, builder forms, localStorage. See `test/e2e/README.md` |
| **real app + real DB + login** | `test/browser/*_e2e.js`, or a script run through `scripts/e2e/run.sh` | **yes** | anything that needs a session: saved characters, custom items, folders, parties, PDF export |

`test/browser/` probes expect a server you started yourself (`lein e2e-server`) and take
credentials from `ORCPUB_TEST_USER` / `ORCPUB_TEST_PASSWORD` — set those to `kaylee` /
`serenity99` and the signed-in lanes stop skipping. `scripts/e2e/run.sh` is the newer path that
owns the server lifecycle for you. See also [fast-browser-probes.md](fast-browser-probes.md) for
the timing traps and the `.lein-env` trap.

## Seeding a session in localStorage does not work

Do not try it. The app verifies a stored token against the server on boot and clears it if the
server does not accept it — which is the app being right. Log in through the real form.
(`test/browser/overlay_reachability_e2e.js:24-28` records this, having tried.)

## What CLAUDE.md says about this is wrong

`CLAUDE.md` on this branch still tells you to run `cd e2e && npm test`, and its file-map lists E2E
tests under `e2e/scenarios/`. **`e2e/` does not exist on `integration`.** That path belongs to an
abandoned Playwright/TypeScript harness that only ever existed on `testing/develop` and on
`claude/*` branches. The live paths are the four in the table above. Corrected in `CLAUDE.md` at the
same time as this doc was written; noted here because the wrong instruction has already cost time.

## Related

- [cljs-headless-harness.md](cljs-headless-harness.md) — the cljs runner, and the four things that
  make it report a false pass
- [fast-browser-probes.md](fast-browser-probes.md) — probe timing, keeping the server up
- [testing-infrastructure.md](testing-infrastructure.md) — runners, re-frame testing truths
- [env-and-auth.md](env-and-auth.md) — how `SIGNATURE` and the env chain feed auth
