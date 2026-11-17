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
- **Auth:** the worktree's git config has a credential helper for `code.dungeonmastersvault.com` that
  reads the token from `~/.config/dmv/gitea-token`, so a plain `git fetch gitea` / `git push gitea`
  works. Never put the token on a command line or print it.
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
- PR #10's description was stale until 2026-11-15, when it was rewritten for the Long Rest Update.

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

## Pushing to Gitea is blocked by Cloudflare (2026-11-04)

Gitea's SSH is off, so only HTTPS works, and Cloudflare now answers every Git request to
`code.dungeonmastersvault.com/DMV/DMV.git/info/refs` (read and push) with its "Sorry, you have been
blocked" page (403, e.g. Ray ID a559376d1b97c587). The website and Gitea's API still pass, and Gitea
reports the branch unprotected with push allowed for `tinkan`. Unblocking needs the Cloudflare admin.
The local branch now tracks its own Gitea branch, not `dmv`.

## Traps when updating PR #10 (2026-11-05)

- **Gitea reports "not mergeable" for a few seconds after a push** while it recalculates. Check
  again before calling it a conflict.
- **Public tests that count the home page's buttons break on DMV:** its splash page has 21, the
  public one 14. DMV's copy of `index_script_json_test` checks that the labels exist and are marked,
  not how many there are.
- **`art_mark_drawing_test` assumed silhouettes** and failed ten checks on DMV, where every piece carries
  the pack's mark. Fixed on the branch (`PRIVATE-ART` marker): never accept a failing test as "pre-existing".

## 2026-11-15 update (after #85, `feature/inventory-kits`)

- `git fetch gitea` works again over HTTPS: the 2026-11-04 Cloudflare block is gone (push not yet
  re-tested at the time of writing).
- The merge of `integration` (`fadd99fd4`) had one conflict, `test/reviewed-images.edn`: both sides
  appended entries after the same line. Resolve as the union (DMV's images plus integration's sigils
  and cup icon; mind the trailing comma that DMV's side adds on the shared line).
- `fork/branding.clj` auto-merged integration's new `:dev-tip` social link; DMV's values were untouched,
  and with `APP_SOCIAL_DEV_TIP` unset the footer pill stays hidden.
- **Matomo broke on the escaping page template** (`333625f89`, already on this branch, not yet on
  `gitea/dmv`): DMV's `fork/integrations.clj` passed its inline snippet as a plain string, so quotes
  became `&apos;` and every page threw "Unexpected token '&'". Fixed on the branch (`ee0716c20`):
  `script-tag` emits its body through `h/raw`; `test/clj/orcpub/fork/integrations_test.clj` covers it.
  The browser suites' "no page errors" check is what caught it: run them on every pre-merge.
- **State, 2026-11-15:** PR #10 (opened 2026-09-06) is still open and unmerged; pushed to `ee0716c20` and its title and description rewritten that day (795 files). The last merge into
  `dmv` was #8 on 2026-08-11; `gitea/dmv` ends at `31cd8be24` (2026-09-07). Local branch 176 ahead
  of its Gitea copy, not pushed.

## 2026-11-16 update (after #87, kits round two)

- The merge of `integration` (`85e964522`) was clean: no conflicts and no `fork/` file touched. Merge
  `0c1ade6a7`, pushed to Gitea with the fix below (`e65306b3e`); Gitea push over HTTPS works again.
- **`auth-flows.js` followed emailed links to the live site:** the mail names `APP_URL`, which DMV sets
  to `https://www.dungeonmastersvault.com`, so the suite left the local server and timed out after 12
  checks. Fixed on the pre-merge branch in `linkIn` (keep the link's path and key, move it onto
  `BASE`). The same fix is right on `integration` (a no-op there); bring it over with the next
  integration branch so the two copies do not drift.
- Checks on the merge: `lein test` 1646 tests, 0 failures; `inventory_kits_builder_e2e` 50/50,
  `inventory_kits_sheet_e2e` 26/26, `auth-flows` 44/44.
- The branch-shape hook warned: 97 files changed against `dmv`; a push over 100 is refused. The next
  update may need PR #10 merged first, or a split.
