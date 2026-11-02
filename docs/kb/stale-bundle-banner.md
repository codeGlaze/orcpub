# Stale-bundle banner (dev mode)

**What you see:** in dev mode (`DEV_MODE=true`), a fixed amber bar at the top of the page reading
"This page is running an old build: orcpub.js is older than web/cljs/...". It is gone in production
and when the build is current.

**Why it exists.** The server serves whatever `resources/public/js/compiled/orcpub.js` and
`css/compiled/styles.css` are on disk. Only Figwheel (`lein fig:watch`, `./menu start figwheel`) and
Garden compile them, so a server started on its own, or `./menu start all` skipping Figwheel because its
port was held, serves an old build with no sign of it. On 2026-10-30 a main worktree served a bundle
from 2026-09-16 with 230 newer commits, and the weapons table and portraits "were missing". The
`?v=` content stamp (`asset-url`, `pedestal/content-stamp`) does not help: it hashes the file on
disk, so an unbuilt bundle keeps its stamp. Stamping makes a new build reach the browser; it never
triggers one.

**How it decides.** `orcpub.stale-bundle/problems` compares modification times: the bundle against
the newest `.clj`/`.cljs`/`.cljc` under `web/cljs`, `src/cljc`, `src/cljs` and against
`dev.cljs.edn`; the stylesheet against `src/clj/orcpub/styles`. A missing file counts. These are the
same inputs `scripts/e2e/run.sh` checks (`:watch-dirs`, the build config), which still rebuilds on its
own; `menu` and `start.sh` do not check.

**Limits.** It runs from the working directory on every index-page request in dev mode, so it is
meant for a checkout, not a deployed jar. A source file touched without a content change (a branch
switch, a merge) makes it say stale until the next build; that is correct, the bundle was not built
from those files. It warns only; it never rebuilds or refuses to start. The e2e server does not set
`DEV_MODE`, so it shows no banner.

Code: `src/clj/orcpub/stale_bundle.clj`, wired in `index-page` (`src/clj/orcpub/index.clj`); test
`test/clj/orcpub/stale_bundle_test.clj`. On `integration` from `c06a3ebda` (2026-10-30).

## Why it keeps happening, and the fix still owed

`start_all` in `scripts/start.sh` (`./menu start`, the menu's "start all") starts Datomic and the
server REPL only. It never starts Figwheel or Garden and has no build step, so the earlier line above
about it skipping Figwheel because a port was held is wrong. After a pull or merge touches sources, the
bundle stays older than them until someone builds by hand, so the banner stays up. The owner reports
this (2026-11-01): the banner is constantly out of date, and its tip is wrong.

`start_figwheel` does exit quietly when port 3449 is held, and every worktree uses that port, so
`./menu start figwheel` from a second worktree can do nothing.

Planned fix: `start all` and `start server` run the same check as `scripts/e2e/run.sh`
(`newer_sources`, `newer_build_inputs`) and build once before the server starts; the banner and its
test go. `scripts/start.sh` is on the heavy Docker path list.
