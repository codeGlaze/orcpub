# fix/release-cleanup

**Purpose:** clear what stands between `integration` and #32 (`integration` into `develop`).
**Base:** `integration`. **PR into:** `integration`. Worktree: `/home/codeglaze/projects/orcpub-cleanup`.

## State (2026-11-01)

Pushed, no PR yet (the owner opens that call). It merges `develop` in, moves stray files to
`agents/develop`, makes `./menu start` build what is out of date (`docs/kb/start-build.md`), and adds
the Branch shape PR check. `lein test`, the cljs suite, lint and `test-start-build.sh` passed once.

## Next

1. On the owner's yes, open the PR. It runs heavy Docker checks, then one `@coderabbitai full review`.
2. Once it merges, edit #32's description: its conflict note and "Commits with no PR" section go.
3. Add the fork-only `branch-shape.yml` to the list to remove before the upstream PR.

## Notes

- `dev/fix_spell_row_fields.clj`, `dev/style4_one_spell_page.clj` and `scripts/clj-grep.py` stayed:
  code or a shipped doc cites them.
- A new worktree has no `node_modules`: run the cljs suite with `NODE_PATH` at another worktree's.
