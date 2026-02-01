# Branch Context: debug/develop-01

## Purpose

Debug and lint cleanup branch. Pre-commit hook on `upgrade/datomic-pro`
routes code commits here to keep the upgrade branch clean.

## Current State

- clj-kondo and clojure-lsp configs fixed (exclude-files regex, source-paths)
- Lint warnings resolved across styles, common, magic_items, template_base
- `strict-round-trip-2` test fixed — two bugs in entity.cljc:
  1. `from-strict-selections`: `reduce/assoc` promoted to PersistentHashMap beyond 8 keys (fix: `array-map`)
  2. `to-strict`: `::owner` field dropped during round-trip (fix: added to destructuring + cond->)
- `warlock_test.clj` rewritten: 7 entity/build integration tests, 20 assertions
- Full suite: 74 tests, 237 assertions, 0 failures, 0 errors
- Lint: 0 errors, 455 warnings (all pre-existing from third-party libs)

## Workflow

- Pre-commit hook on `upgrade/datomic-pro` routes commits here
- Run `lein test` and `lein lint` before committing
- Changes get merged back into `upgrade/datomic-pro` via merge commits

## Handoff Notes

- All code changes are committed and clean (`git status` shows no modifications)
- The entity.cljc fixes have inline comments explaining both bugs
- warlock_test.clj uses inline configs for data trapped in .cljs files — see pattern for future class tests
- A `refactor/cljs-data-to-cljc` branch exists at `/workspaces/orcpub-refactor` for extracting .cljs data to .cljc (separate effort)

## Related Docs

- `docs/TESTING.md` — test suite inventory and patterns
- `docs/ENTITY-BUILD.md` — entity/build pipeline architecture (on upgrade/datomic-pro)
- `docs/SESSION-SUMMARY.md` — session history (on upgrade/datomic-pro)
