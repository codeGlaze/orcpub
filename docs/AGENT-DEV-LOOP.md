# Agent Dev Loop — running & verifying orcpub changes

How to make a code change and actually prove it works in the running app.
Companion: end-to-end test details live in [docs/E2E-NOTES.md](E2E-NOTES.md)
(kept in sync with the same file on `testing/develop`).

## Ground rule: bugs don't ship by comparison

A known-incorrect behavior is **never** acceptable to ship because a worse bug
exists, or because it "only" affects display. A change ships only when either:

- we have **proven** the bad behavior cannot occur within the scope being
  shipped, **or**
- the behavior is **patched and stabilized**.

"Doesn't corrupt data," "recomputed each load," "edge case" are not ship
justifications. If a change introduces a bug, either it can't ship or we fix it.

## Known design decisions & verified facts — do NOT rediscover these

Before calling any behavior a bug, check this list and the project's stated
design. Re-deriving intentional behavior from code and presenting it as a
"finding" wastes everyone's time — it's the single most common way agents go
sideways here.

**Intentional design (NOT bugs):**
- **Homebrew content is client-side only.** There is no server-side backup of
  homebrew/imported content — it lives in the browser (localStorage), and the
  site explicitly tells users to **export `.orcbrew` files for safety**. The
  absence of backend persistence is by design. Do not flag it, do not "discover"
  it.

**Verified facts (checked directly this session):**
- **A BOM in an orcbrew file is a non-issue.** Import reads the file with
  `FileReader.readAsText`, which strips a leading UTF-8 BOM before the EDN reader
  ever sees it (verified with a browser harness). No BOM-stripping code is
  needed; don't add it.
- **figwheel's file-watcher is INCONSISTENT in the codespace** — it recompiled
  some edits automatically and silently missed others (missed a `sed -i` edit,
  caught `perl`/base64-append edits). Don't assume "it always reloads" *or* "it
  never reloads." If a change isn't reflected in the running app, restart
  figwheel to force a clean recompile (~50s).
- **The `#_`-discarded `goliath-option-cfg` / `deep-gnome-option-cfg` /
  `svirfneblin-magic-feat` source-decoration defs are dead reference code**, not
  live name-poisoning bugs. `#_` is the reader-discard macro — the compiler never
  sees those forms. Agents keep "rediscovering" them via text grep. Stop.
  Exclude `#_` and `(comment …)` forms when auditing.

**Discipline:**
- Before treating behavior as a bug, check whether it's a stated/intentional
  design decision (the list above, the site copy, the KB). Don't reverse-engineer
  intended behavior from code and narrate it like a clue.
- **e2e import:** use the My Content page's file input via `setInputFiles` — that
  *is* the import button. Verify the import by inspecting **in-app UI state**
  (the class dropdown / content list), NOT by reloading the page and reading
  `localStorage` — homebrew lives in the re-frame db and a naive reload can make
  a successful import look like it did nothing.

## Two environments

Pick one — each has its own setup.

- **Codespace** — what we used last (already provisioned, convenient). Trade-offs:
  ports must be made public to reach them, files are edited over SSH (no direct
  filesystem access), and it bills while running.
- **Local WSL** — also fully capable of running the app; not yet validated as an
  agent workflow. See the stub at the bottom.

## Running the app (infra facts, verified 2026-06-01)

ClojureScript/Reagent front end + Datomic/Pedestal back end.

- Datomic transactor on port **4334**
- Back-end server on **8890** (this is the app URL)
- figwheel (front-end compiler + hot-reloader) on **3449**

Services are managed by the repo's `./menu` script (`./menu start <svc>`,
`./menu status`, `./menu stop <svc>`). Datomic must come up first; everything
depends on it. The first figwheel compile takes ~50s.

## Codespace process (verified 2026-06-01)

Codespace `<your-codespace-name>` (repo codeGlaze/orcpub),
reached with `gh codespace ssh -c <name>`.

1. **Start services**, Datomic first:
   - `./menu start datomic --background --idempotent`
   - `./menu start server --background --idempotent`
   - figwheel **from a login shell** — its start script needs the
     `CODESPACE_NAME` variable, which a bare `gh codespace ssh -- '…'` shell does
     not have:
     `gh codespace ssh -c <name> -- 'bash -lc "cd /workspaces/orcpub && ./menu start figwheel --background --idempotent"'`
2. **Make ports reachable from a browser:**
   `gh codespace ports visibility 8890:public 3449:public -c <name>`
3. **Create a login** (the database starts with no users):
   `./menu add test testpass` → log in as `test@test.com` / `testpass`.
4. **Node** is not provided by this container (the devcontainer declares only
   `sshd` and `docker-in-docker`): `apt-get install -y nodejs` (you are root).
   Needed only for the e2e tests; Playwright's browsers are already cached.
5. **Editing code:** the agent cannot edit Codespace files directly (no remote
   filesystem mount). Push changes over SSH (e.g. base64 a local file into place)
   or `gh codespace cp`.
6. **Getting a change to show up:** figwheel is supposed to recompile on save.
   - **Observed once, cause unconfirmed:** an SSH `sed -i` edit (and a follow-up
     `touch`) did **not** trigger a recompile; restarting figwheel did. `sed -i`
     swaps the file's inode, which may be the real cause rather than figwheel
     itself — this needs verifying with a normal in-place editor save.
   - **Fallback:** if your change isn't reflected in the app, restart figwheel
     (~50s rebuild).
7. **Known tooling bug:** `./menu stop figwheel` kills a stale PID and misses the
   real process, leaving port 3449 held so the next start fails with "port in
   use." Work around by killing whatever holds 3449 directly.
8. **Run an e2e test** (see [E2E-NOTES.md](E2E-NOTES.md) for the harness):
   `cd /workspaces/orcpub-testing/e2e && APP_URL=http://localhost:8890 ./node_modules/.bin/playwright test scenarios/<file> --project=chromium`

## WSL (local) process — STUB, not yet validated

Local WSL can run the full app, but we have not done it end-to-end, so this is
deliberately **not** written as a procedure yet. Start from the repo's existing
guide: `docs/GETTING-STARTED.md`, "Path 2: Local Machine" (Java 21, Leiningen,
Datomic Pro; plus Node for the e2e tests). Run it for real, then replace this
stub with what actually happened.
