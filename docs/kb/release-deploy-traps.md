# Release and deployment traps

What looked right in the release, Docker and DMV work and was not. Written so the next agent skips
the same failures. Moved from the release chat's expertise file on 2026-11-01.


- **Swarm Datomic.** Editing the transactor template was baked into the image; plain `--swarm` never
  switched the host; `ALT_HOST=127.0.0.1` from `.env` broke it; `getent hosts datomic` is empty until
  the service is healthy, so an unresolvable name must count as Swarm. Host and alt-host are now
  start-time settings (`TRANSACTOR_HOST`, `ALT_HOST`).
- **Bash under `set -euo pipefail`:** `getent` and `read` exit non-zero inside `$(...)`; `producer |
  grep -q` hits SIGPIPE and reads as false. `! cmd` under `bash -e` does not fail a CI step.
- **Turkish locale, twice.** CI showed only the first failure; the rest hid behind it. Sweep every
  `str/lower-case` and `upper-case` in touched files in one pass, then run the full suite under
  `JAVA_TOOL_OPTIONS="-Duser.language=tr -Duser.country=TR"`.
- **A unit test that passes on old code.** A fetch inside a `go` block runs after a synchronous check,
  and `http-safe/get` is not `cljs-http.client/get`. Stub the right function, keep it across a timeout,
  and filter by URL. Break the code to prove it.
- **`REVIEWED_IMAGES=record lein test` truncates at 50 entries** and writes an invalid EDN file ending
  `...}`. Hand-insert entries, then parse with the EDN reader. Not yet fixed on `integration`.
- **My quick parsers lied three times** on that file. Use the real reader. For `~/.m2` jars, `sort -V`:
  plain `tail -1` picked ancient Clojure.
- **Worktree slips, twice.** `orcpub-pdf` holds a different branch, and I merged into it. Later a
  failed `git worktree add` left a `cd X && ...;` chain running in the main checkout, where the #70
  agent worked. Use `cd X || exit 1`, print the branch, and find a branch's folder with
  `git worktree list | grep -F '[branch]'`. Never guess a folder from its name.
- **Subagent conflict resolution needs reviewing.** One flipped DMV's "empty value disables Matomo"
  and took a robots.txt that would de-index the live site, while reporting success. Read what it
  changed against live behaviour, not just its test counts.
- **Hiccup 2 escapes raw strings**, so `h/raw` matters when merging `index.clj`.
- `gh pr edit --add-label` fails on a Projects (classic) error: use the REST labels call.
  `gh pr checks` has no `--json`. A PR's head can lag a push by seconds.

## Open and unchecked

- **Never confirmed built:** the on-demand logged-out e2e path (build a character and homebrew logged
  out, save, log in, repeat logged in with several tabs; asked 2026-10-16). Check `test/browser`.
- **Ideas to check against `docs/TODO.md`:** per-section on/off toggles on artist pages; Markdown
  support impact; a light in-browser EDN editor and linter, "only if light"; the Copilot review rule,
  patch every verified problem even outside the PR's own commits.
