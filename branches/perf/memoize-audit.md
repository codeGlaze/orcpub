# perf/memoize-audit

This branch tracked its own `CLAUDE.md` until 2026-10-24 (4f5025b20). The full file is
`git show 4f5025b20~1:CLAUDE.md`. What it said beyond the general instructions, verbatim:

**Do not run another `lein` command while `lein e2e-server` is starting.** lein-environ
rewrites a single `.lein-env` per invocation, so a concurrent `fig:build`/`garden`/`test`
clobbers the `:e2e` profile's `:datomic-url` and the server silently boots against
`datomic:dev://localhost:4334` instead of `datomic:mem://`. The symptom is a probe failing
with ERR_CONNECTION_REFUSED, not a config error. If it happens, start it explicitly:
`DATOMIC_URL="datomic:mem://orcpub" lein with-profile +e2e run`.

## Knowledge base

`docs/kb/README.md` indexes findings that were expensive to get: the builder-freeze root
cause, the storage-layer measurements, the browser probes and what each answers. Read
`docs/kb/verification-discipline.md` before writing a performance probe -- it lists the
probe defects that have produced confident wrong answers here.

Its Datomic note, in full: on Datomic Pro 1.0.7482 `datomic:mem://` needs no transactor; a doc
describing the old Datomic Free failure on Java 8/21 (`DATOMIC_JAVA21_TEST_RESULTS.md`, not on this
branch) is a resolved issue, history, not the current state.
