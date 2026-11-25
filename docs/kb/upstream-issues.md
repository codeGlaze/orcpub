# Upstream issues: what gets one, and how to file it

The owner tracks the project's bugs, features and improvements in the upstream tracker,
[Orcpub/orcpub issues](https://github.com/Orcpub/orcpub/issues), even for work that so far lives
only on our fork. Changelog lines cite these issues (`AGENTS.md`, "Changelogs"). This page says
which changes get an issue, how to group them, and how to check where a bug reached.

## Words used here

- **Upstream `develop`:** `develop` on `Orcpub/orcpub` (remote `upstream`), the original project.
- **The dev site:** the owner's test deployment of Dungeon Master's Vault. It runs the DMV
  pre-merge branch, `premerge/integration-to-dmv` on the `gitea` remote (our `integration` plus
  DMV's own files, `docs/ops/dmv-premerge.md` on that branch). Testers use it, so a bug there has
  been seen by people. The owner treats a dev-site build like a release (owner, 2026-11-25).
- **Umbrella issue:** an issue per area that collects small changes with no issue of their own
  (`#705`-`#712`, `#724`-`#729`, titled like "Characters: builder navigation and sheet fixes").

## Which changes get an issue

A change gets an issue if what it fixes or adds **reached upstream `develop` or a dev-site
build**. A bug that a branch made and fixed before any dev-site build had it gets no issue, just
as it gets no Fixed line in the changelog.

Group by what a reader would look for, not by commit or by PR:

- **One problem, or one feature, is one issue.** Changes made together for the same thing share it:
  moving every sheet roll to the same result popup is one issue, and so is a batch of CSS fixes.
- **Related but different is a new issue.** Making PDF export faster with a backend rewrite is one
  issue. Adding spell cards and a spellbook to the PDF is a different one, though both touch the PDF.
- **A bug someone could hit, search for or report again gets its own issue**, not a line in an
  umbrella: "the Class drop-down disappears" (#730) is one; the Rest button covering the footer
  went under umbrella #728.
- **Small polish with no one to search for it goes in the area's umbrella**, as a comment
  ("Also covers a few layout fixes on the sheet: ...").
- **No issue** for internal work (tests, CI, refactors, tooling), or for swapping where a
  third-party asset is served from (the Patreon and Buy Me a Coffee buttons; owner, 2026-11-25).

## Checking where a bug reached

Take the fix commit's removed lines and look for them in each place:

```bash
git fetch upstream && git fetch gitea
# upstream develop
git show upstream/develop:<file> | grep -F '<a removed line>'
# the dev site before the fix: the pre-merge commit before integration brought the fix in
git log --first-parent --format='%h %ci %s' gitea/premerge/integration-to-dmv | head
git show <that commit>:<file> | grep -F '<a removed line>'
```

If the file isn't upstream at all, the feature isn't either (portraits weren't, as of 2026-11-25).

## Writing and posting

- Title: area, colon, the problem as a player sees it ("Sheet: changing a character someone else
  owns tries to save it").
- First line: `**Affects:** upstream `develop` and the dev site`, or `the dev site only`.
- Then what goes wrong, in plain words, and what fixed it ("Fixed:", or "Fixed together:" with a
  list). Match the house voice of #724-#736.
- **Nothing is posted without the owner's yes for each item.** Draft them in a file, list them in
  Needs you, then post one per command and check the posted body has no footer or attribution.

## When to do it

At the changelog fold (`AGENTS.md`, "Changelogs"): every line in the folded section cites its
issue, or has been checked and needs none. Draft the missing issues then.
