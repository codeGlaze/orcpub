# Bringing `integration` to DMV: the pre-merged PR

How the Summer Patch work reaches the Dungeon Master's Vault fork, and what a merge there must
preserve. Learned on the 2026-10-29 update of PR #10.

## Where it happens

- **Repo:** `code.dungeonmastersvault.com/DMV/DMV` (remote `gitea`), base branch `dmv`.
- **PR:** #10, from `premerge/integration-to-dmv`. It carries the merge already resolved, so the
  admin only reviews and merges. Update that branch; do not cut a new one.
- **Worktree:** `/home/codeglaze/projects/orcpub-dmv`. Its pushes are pinned to `gitea`.
- **Auth:** `GITEA_TOKEN` in the main checkout's `.env`, sent as an `http.extraHeader`. Never print it.
- **Never push this branch to `origin` or `upstream`:** it holds DMV branding and private portrait art.

## How to resolve conflicts

DMV's `src/*/orcpub/fork/` files are real implementations; `integration` has generic versions plus
new functions that other code calls. Take integration's structure and every new definition, then
re-apply DMV's values. Branding, wording and defaults: DMV wins. Logic, validation, accessibility:
integration wins. Taking one whole side breaks either the build or the branding.

- `branding.clj`: DMV's values as both the `env/value` default and the `checked-*` fallback.
- `integrations.clj`: an empty value turns an integration off. `orcpub.env/value` would give the
  default instead ([blank-env-values.md](blank-env-values.md)), so this file reads the environment itself.
- `integrations.cljs`: the union of both sides' hooks, with DMV's body where both define one.
- `robots.txt`: DMV deleted it on purpose, so crawlers are allowed. `integration` disallows nearly all.

## Checks

- Read what a subagent changed against live behaviour, not only its test counts. One flipped the
  empty-value rule and took integration's `robots.txt` while reporting success.
- `test/reviewed-images.edn`: DMV's own images need entries. Look at each one first.
  `REVIEWED_IMAGES=record` truncates at 50 entries and writes invalid EDN, so insert by hand and
  parse the result with the EDN reader.
- Some integration tests assume the public defaults, such as an unset app URL.
