# Bringing `integration` to DMV: the pre-merged PR

How the Summer Patch work reaches the Dungeon Master's Vault fork, and what a merge there must
preserve. Learned on the 2026-10-29 update of PR #10.

**"Pre-merge" means a test merge** (owner): "test merge and fix the flags before pushing a branch out
for PR." The merge and its fixes come first and are checked; pushing or opening the PR is a separate
step, not what the word means.

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

## Audit of the branch, 2026-11-01

Checked `aaa34c997` against `gitea/dmv` (`31cd8be24`) and `origin/integration`, by file and by commit.
No code was run.

- Every `gitea/dmv` commit is in the branch. It is 2 commits behind `integration`: the dev-mode
  stale-bundle banner (`d7643882c`, `c06a3ebda`).
- `robots.txt` is absent, as DMV wants.
- 18 of the 68 files DMV changed since the merge base differ from `dmv`. Eight are real mixes,
  including the `fork/` files. Ten match `integration`: seven are source files (share links, homebrew
  validation), where `integration` carries its own later version of those features. Their behaviour
  was not compared line by line.
- PR #10's description is stale: it says 188 files and the frozen Summer Patch tip; the PR shows 800.

## Launching the DMV side locally

The startup and config code match `integration`. `orcpub-dmv/.env` is the likely reason a local launch
errors (2026-11-02, not confirmed: no log survived): it has no `DATOMIC_URL`, and its Datomic and admin
passwords and `SIGNATURE` differ from the working `.env`. The licensed art is only on this branch, so a
public-branch launch shows silhouettes.

**Licensed art on any local launch** (working, 2026-11-02). The art is copied to `~/orcpub-private-art/public/image/portraits`,
without the `PRIVATE-ART` marker, and a git-ignored `profiles.clj` in the checkout prepends it:
`{:user {:resource-paths ^:prepend ["/home/codeglaze/orcpub-private-art"]}}`. Static files and strand
images read the classpath, so served files match the private branch byte for byte, and git sees nothing.
`lein test` in that checkout picks the folder up too. A new art pack means copying again.

