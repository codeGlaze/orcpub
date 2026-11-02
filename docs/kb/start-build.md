# Why a server start builds the client

The server serves whatever `resources/public/js/compiled/orcpub.js` and
`css/compiled/styles.css` are on disk, and nothing used to rebuild them on a local start. On
2026-10-30 a main worktree served a bundle from 2026-09-16 with 230 newer commits, and the weapons table
and portraits "were missing". The `?v=` content stamp does not help: it hashes the file on disk, so an
unbuilt bundle keeps its stamp. A stamp makes a new build reach the browser; it never makes one.

**How it got that way.** In January 2026 Garden left the global `:prep-tasks` so a REPL started faster
(`SESSION-SUMMARY.md`). In February ClojureScript left the uberjar's, for the Docker hang
(`lein-uberjar-hang.md`). Each was right for its own reason. Nothing took their place for local dev.

**What happens now** (`fix/release-cleanup`). `ensure_fresh_build` in `scripts/bundle-check.sh` runs
before the server in `./menu start` and `./menu start server`. Unchanged sources cost a quick file-time
check and no compile. Changed ones build once: `lein fig:build` when `DEV_MODE` is on, `lein fig:prod`
otherwise, and `lein garden once` for styles. A failed build stops the start. It skips what this
folder's own Figwheel or Garden keeps current, so there is never a second `lein` in one folder, and
names the folder when another worktree holds Figwheel's port. All worktrees share that port.
`scripts/e2e/run.sh` reads the same helpers. Docker and production builds are untouched.

**The banner this replaced.** A dev-mode amber bar (`d7643882c`, `c06a3ebda`), pushed to `integration`
with no PR, warned that the build was old. It only reported the problem, stayed up because nothing
rebuilt, and its tip was wrong (owner, 2026-11-01). It was removed in the same branch.

Test: `test/docker/test-start-build.sh`.
