# Upstream issues: what gets one, and how to file it

The owner tracks the project's bugs, features and improvements in the upstream tracker,
[Orcpub/orcpub issues](https://github.com/Orcpub/orcpub/issues), even for work that so far lives
only on our fork. Changelog lines cite these issues (`AGENTS.md`, "Changelogs"). This page says
which changes get an issue, how to group them, how to check where a bug reached, and how to file.
The rules are the owner's (2026-11-25).

## Words used here

- **Upstream `develop`:** `develop` on `Orcpub/orcpub` (git remote `upstream`), the original project.
- **The dev site:** the owner's test deployment of Dungeon Master's Vault (DMV), which testers use.
  It runs the **DMV pre-merge** branch, `premerge/integration-to-dmv` on the `gitea` remote: our
  `integration` with DMV's own files on top (`docs/ops/dmv-premerge.md` on that branch). Every
  commit on that branch's first-parent line counts as a build testers had. Deploy dates are not
  recorded, so don't try to narrow it further.
- **Umbrella issue:** one issue per area per release that collects small changes with no issue of
  their own. The open ones are the current release's (the Long Rest Update): #724 Self-hosting,
  #725 Accounts, #726 Printing, #727 Sharing, #728 Characters, #729 Site. The closed #705-#712
  were the Summer Patch's; don't add to them.
- **The fold:** the step that moves a branch's `docs/branch-changelog.md` into `CHANGELOG.md`, done
  when the owner says its PR is about to merge (`AGENTS.md`, "Changelogs").
- **Needs you:** the end of an agent's reply where questions for the owner go (`AGENTS.md`,
  "Reporting back").

## Which changes get an issue

A change gets an issue only if what it fixes or adds **reached upstream `develop` or a dev-site
build**. A bug that a branch made and fixed before any dev-site build had it gets no issue, just
as it gets no Fixed line in the changelog.

Group by what a reader would look for, not by commit or by PR:

- **One problem, or one feature, is one issue.** Changes made together for the same thing share
  it. Example: #731, where the spell table offered a 1d20 roll on every spell, and the same work
  folded the roll into the Mod. column. That's one issue, not two.
- **Related but different is a new issue.** Making PDF export faster with a backend rewrite is one
  issue. Adding spell cards and a spellbook to the PDF is a different one, though both touch the PDF.
- **A bug gets its own issue; polish goes in the area's umbrella.** It's a bug if a player would
  call it broken: a wrong number or rule, an action that fails or is blocked, lost data, or
  content that is unreadable or distorted. Examples: the Class drop-down vanishing (#730), notice
  text hidden in Firefox (#735), a squashed portrait (#736). It's polish if everything works and
  only looks off: spacing, alignment, a heading running into its neighbour, a button overlapping
  the last lines of the footer. Those went into #728 as a comment.
- **A batch of polish is one entry.** If the area has an open umbrella, the batch is a comment on
  it ("Also covers a few layout fixes on the sheet: ..."). If not, the batch is one new issue,
  which then serves as that area's umbrella for the release.
- **No issue for work a player or self-hoster can't notice:** tests, CI, tooling, or a refactor
  that changes nothing outside the code. A rewrite that does change something they notice (faster
  exports, a security fix) gets one for that result. No issue either for changing where a
  third-party asset is served from (the Patreon and Buy Me a Coffee buttons).

## Checking where a bug reached

Take lines the fix commit removed or changed and look for them in each place:

```bash
git fetch upstream && git fetch gitea
# upstream develop
git show upstream/develop:<file> | grep -F '<a removed line>'
# the dev site before the fix: the pre-merge commit before integration brought the fix in
git log --first-parent --format='%h %ci %s' gitea/premerge/integration-to-dmv | head
git show <that commit>:<file> | grep -F '<a removed line>'
```

- If the file isn't upstream at all, the feature isn't either (portraits weren't, as of 2026-11-25).
- If the fix only added lines (a missing check), look for the function it changed and read
  whether that version lacks the fix. If you can't tell, say so in Needs you rather than guessing.
- If a remote is missing, `git remote -v` shows what exists. Upstream is
  `https://github.com/Orcpub/orcpub.git`. Gitea is the owner's DMV server, and its access is
  described in `docs/ops/` on the DMV branches.

## Writing and posting

- Title: area, colon, the problem as a player sees it ("Sheet: changing a character someone else
  owns tries to save it").
- First line, one of:
  - `**Affects:** upstream `develop` and the dev site`
  - `**Affects:** the dev site only (not in upstream `develop`)`
  - `**Affects:** upstream `develop` only`
- Then what goes wrong, in plain words, and what fixed it ("Fixed:", or "Fixed together:" with a
  list). Read #730-#736 for the voice.
- **Nothing is posted without the owner's yes for each item.** Draft the issues in a file and list
  them in Needs you.
- A local session posts as codeGlaze with `gh`, one issue per command
  (`gh issue create -R Orcpub/orcpub --title ... --body-file ...`), then checks that each posted
  body has no footer or attribution. A cloud session never posts upstream; it gives the owner the
  text to post (`AGENTS.md`, "PRs and releases").

## When to do it

At the fold: every line in the folded section cites its issue, or has been checked and needs none.
Draft any missing issues then.
