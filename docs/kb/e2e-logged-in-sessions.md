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

## Logging in from a probe — use the one that already exists

**`fix/custom-item-classification:scripts/e2e/run.js` has been running logged-in custom-item
scenarios since 2026-08. Start there; do not re-derive this.** Its `login()` is seven lines:

```js
const login = async p => {
  await p.goto(BASE + '/pages/login-page', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('input');
  await p.locator('input').nth(0).fill(USER);
  await p.locator('input').nth(1).fill(PASS);
  await p.locator('button.form-button').click();
  await p.waitForTimeout(3500);
};
```

The same file carries the helpers a custom-items probe needs — `newItem(page, name, type)`,
`kindSelect`, `clickButton`, `buttonLabels`, `shimFonts` — and nine scenario functions including
`customItemOverridesSrd`, `removeForGoodActuallyRemoves` and `itemTextReachesTheCharacterSheet`.

**It is not on `integration`.** That branch is unmerged, and integration's own
`scripts/e2e/run.js` is the PDF-export script with no login at all. So the helper is real, works,
and is invisible to anyone who looks only at `integration` or `agents/develop` — which is exactly
how it got missed here.

### Why that helper is invisible: two different suites share one filename

`scripts/e2e/run.js` is **two unrelated test suites contesting one path**:

| | lines | what it is |
|---|---|---|
| `integration` | 394 | PDF export scenarios. No login at all |
| `fix/custom-item-classification` | 566 | Custom-item flows, with `login()`, `newItem()`, and `kaylee`/`serenity99` as the defaults |

They are not versions of one file. The branch wrote its runner alongside `dev/e2e_boot.clj`
(`8775f9f4`, 2026-08-27); the harness itself then reached `integration` through a *different*
commit (`23eb07cf`, 2026-08-30 — byte-identical blob, so it was carried over separately), and
`integration` grew its own `run.js` for PDF. **The infrastructure landed; the suite that used it
did not.**

That is the whole reason a reader on `integration` sees a runner with no login and concludes
logged-in probing is unsupported.

`integration`'s `run.sh` already takes a script argument — `node "scripts/e2e/${1:-run.js}"` — so
the mechanism for having several suites exists. Only the contested default filename is missing a
decision. The branch's own `run.sh` still hardcodes `run.js`, which is one of the four hunks that
file conflicts on.

**Merging the branch as-is is 17 hunks across 12 files**, and `scripts/e2e/run.js` is one of them —
a 566-line file conflicting with a 394-line file that shares nothing but a name. Resolving that as a
merge is the wrong shape of work: give the two suites distinct names first and the hunk stops
existing.

### Naming convention for `scripts/e2e/`

Derived from what `test/e2e/` already does across 27 scripts, so this is the house style written
down rather than a new one invented.

| slot | name | rule |
|---|---|---|
| launcher | `run.sh` | keeps its name — it is the thing you invoke, and it already takes a script argument |
| shared helpers | `lib.js` | `login`, `clickButton`, `newItem`, overlay stamps. `test/e2e/lib.js` is the precedent |
| a suite | **named for the journey it drives** | `export-import-use.js`, `messy-pak-survives.js`, `multi-container-roundtrip.js`, `imported-style-usable.js` |

**The rule the existing names follow: a suite is named for the journey or the claim, never for the
domain object.** `export-import-use.js` is what it does; a file called `plugins.js` would not be.
That is exactly why `custom-items.js` is the wrong name — it reads as source, not as a suite — and
why `run.js` is wrong in the other direction: it names the mechanism instead of the journey, which
is how two unrelated suites ended up claiming it.

Applied to the collision:

| now | becomes |
|---|---|
| `integration:scripts/e2e/run.js` (394 lines, PDF) | `export-character-pdf.js` |
| `fix/custom-item-classification:scripts/e2e/run.js` (566 lines, items) | `classify-items-in-builder.js` |

**`run.sh`'s default gets dropped, not repointed** (decided 2026-09-13). The silent
`${1:-run.js}` fallback is part of how the collision went unnoticed — two suites could claim the
default and neither run announced which it had picked. With no `run.js` to fall back on, a bare
invocation should list what is available:

```bash
SUITE="${1:-}"
if [ -z "$SUITE" ] || [ ! -f "scripts/e2e/$SUITE" ]; then
  echo "usage: ./scripts/e2e/run.sh <suite.js>"
  echo "suites:"
  for f in scripts/e2e/*.js; do
    b=$(basename "$f"); [ "$b" = "lib.js" ] && continue
    echo "  $b"
  done
  exit 2
fi
```

Place the check **before** the server boots, so a typo costs nothing rather than a full build and
boot. **Done on `integration-local`, 2026-09-13:** `run.js` is now `export-character-pdf.js`, `run.sh`
lists the suites when given none or a missing one, honours `E2E_PORT`, and turns CSP off (and says so)
when the bundle on disk is a development build. `fix/custom-item-classification` still has to rename
its own `run.js` to `classify-items-in-builder.js`.

### The four facts behind those seven lines

Worth keeping because a changed selector sends you back to them:

| | |
|---|---|
| **URL** | `/pages/login-page`. `/login` 404s into the SPA |
| **Submit** | Click `button.form-button`. **Enter does nothing** — LOGIN is an `:on-click` dispatching `[:login params true]`, not a form submit |
| **Storage key** | `localStorage["user"]` (`db.cljs:35`). `user-data` is the key *inside* the stored map |
| **Overlays** | Set `orcpub:no-cookie-banner` = `'1'` and `whats-new-seen` = `JSON.stringify(current-release-id)` in an init script before load, or the What's New backdrop swallows the click — 58 retries then a timeout. `test/browser/lib/suppress-overlays-preload.js` does this for `test/browser/` probes; `scripts/e2e/` scripts must do it themselves |

`scripts/e2e/run.sh` resolves `require('playwright')` from the **repo root**, not from
`scripts/e2e/` where its `package.json` lives.

### An assertion trap, if you add checks

Two that look like session checks and are not — both pass with no session at all:
`/pages/dnd/5e/my-content` renders for anonymous visitors, so "did not bounce to login" proves
nothing; and the header LOGIN link (`views.cljs:782`) reports zero while logged out too. What
discriminates: the stored token, and leaving the login page. Verify any new check fails without a
session before trusting it.

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

## Revisions

- **2026-09-13 — this doc originally derived the login flow from scratch; that was wasted work.**
  `fix/custom-item-classification` has been running logged-in e2e scenarios for weeks, with a
  `login()` helper and an item-creation helper already written. The derivation cost a throwaway
  smoke test and five wrong guesses at things that file already encodes. **The search that missed it
  covered `integration` and `agents/develop` only** — the helper lives on an unmerged branch. Third
  time in this session that searching a subset and reporting as though it were the whole repo
  produced a wrong conclusion; the rule that actually works is `git grep` across
  `git branch -r`, not across the two branches that happen to be checked out.
