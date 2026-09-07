# Contributing — branching, changelog & commit conventions

The workflow for this fork. Read before committing or opening a PR.

## Branch roles

| Branch | Role | Commit here? | Promotes to |
|--------|------|--------------|-------------|
| `main` / `master` | Vestigial — tracks the abandoned upstream author. **Ignore.** | no | — |
| `develop` | **Production**: deployed, stable, the rollback baseline. Only *released* code. | no WIP | — (deploy target) |
| `integration` | Developer mainline. Features merge here to bake, then get promoted. | via merges/PRs | `develop`, per release |
| `refactor/content-extensibility` | The long-running content-extensibility initiative. | via sub-feature merges | `integration`, in slices |
| `feat/*`, `fix/*` | Small, focused changes. | yes | their epic or `integration` |

`develop` is a *release* branch despite the name — do **not** pollute it with
unreleased code; it must stay something you can pull to recover the live site.

## The flow

- **Feature / fix:** cut `feat/<name>` or `fix/<name>` **off `integration`**, do the
  work, PR it **back into `integration`**.
- **Release** (e.g. "Summer Patch", "Fall Update"): when `integration` is stable,
  merge it to `develop`, tag, deploy. A release is a *promotion event*, not a branch.
- **Hotfix:** branch off `develop`, fix, merge to `develop` **and** back-merge to
  `integration` (and the epic) so it isn't lost.
- **Big multi-part effort:** an epic branch (`refactor/content-extensibility`) that
  receives small feature branches and graduates stable slices to `integration`.

### Changes that must reach BOTH `integration` and the refactor line

Cut them off the **common base** (`integration` / `develop`), not off the refactor
branch — so they merge cleanly into both. (This is why the content-library and
import fixes live on a branch based near `develop`.)

## Releases are tags, not branches

A named release ("Summer Patch", "Fall Feature Update") is **not a branch** — it's
the set of changes sitting on `integration` that haven't been promoted to `develop`
yet. Its identity is two things:

- a **`[Name]` section in `CHANGELOG.md`** (accumulated as work lands), and
- a **git tag** cut when it deploys.

To ship a release: merge `integration → develop`, `git tag <release>` (e.g.
`summer-patch`), deploy. The tag is the permanent, immutable marker; `integration`
then keeps collecting the next release. So "the Summer Patch" is simply **whatever is
on `integration` but not yet on `develop`** — no separate branch to manage.

**Only** cut a `release/<name>` branch when you need **two trains at once** — a
release frozen for final QA while new work continues — so the frozen one takes only
bug fixes on its own branch while `integration` keeps moving. Reach for it then, not
as a standing structure.

## Changelogs — fold at merge-to-integration

Every feature/fix branch keeps a `docs/branch-changelog.md` while in flight. Start it
by copying **[`docs/branch-changelog.template.md`](branch-changelog.template.md)** —
that file is the single source of truth for the house style (bullet rules + when a
`## Highlights` is earned); it travels with every branch so no one has to hunt for the
rules here. **When the branch merges into `integration`, fold its entries into the
current in-progress release section of root `CHANGELOG.md` (e.g. `[Summer Patch]`) and
delete the branch changelog — it's consumed.** That keeps root `CHANGELOG.md` a live
reflection of the pending-release diff on `integration`. Folding is a **deliberate
step**, not automatic — don't skip it.

Two sections get special fold treatment:
- **`## Why this branch exists`** is reviewer context — **stripped at fold**, never
  reaches `CHANGELOG.md`.
- **`## Highlights`** (≤3 sentences, only when the branch is an impactful new capability
  or behavioral shift — not a bugfix bundle) is **kept**. Decide whether the branch
  earns one *before* opening the PR / claiming it done; the PR checklist asks for it.

Automation:
- **`scripts/fold-branch-changelog.sh "<release>"`** does the mechanical fold — strips
  the guidance + Why, keeps Highlights, demotes `## Section` → `**Section**`, inserts a
  labeled block into `## [<release>]`, and `git rm`s the branch changelog. Review +
  commit after; editorial curation stays yours.
- The **`changelog-guard` CI workflow** fails a push to `integration`/`develop` that
  still contains `docs/branch-changelog.md` — the unskippable backstop if the fold gets
  skipped.
- **`scripts/lint-changelog.py`** checks CHANGELOG.md against the mechanical half of the
  house style: bullets under 80 words, one change per bullet, no jargon, and prose only
  inside a Highlights block of at most three sentences. It runs in `changelog-guard` on
  pull requests as well as pushes, so the feedback arrives while the branch can still be
  edited. Run it yourself with `scripts/lint-changelog.py --stats`.
- **`scripts/setup-hooks.sh`** (run once per clone) enables a local **`pre-push`** guard
  (`.githooks/pre-push`) that mirrors both CI checks — a fail-fast reminder before the push
  leaves your machine. It only reminds; the fold stays a deliberate manual step.

## Authorship — required, no exceptions

Every commit must be authored **and** committed as `codeGlaze <github@codeglaze.com>`.
The repo git config is set to this; be explicit anyway:

```
git -c user.name="codeGlaze" -c user.email="github@codeglaze.com" commit -m "..."
```

- **Never** author or commit under another identity, and never add a trailer crediting
  one — no `Co-Authored-By:`, no session links, no tool banners, in commit messages, PR
  titles or bodies, code comments, docstrings or filenames.
- If tooling appends a "generated by" line to a message or PR body, **strip it**.

## Commit hygiene

- Confirm the branch first: `git branch --show-current`. **Never push to a different
  branch than assigned without explicit permission.**
- Tests green before committing (`lein fig:test && node scripts/test/run-cljs-tests.js` for CLJS; `lein test` for JVM).
- Descriptive messages — the diff is the record; say *why*, not just *what*.
- Compiled artifacts (`resources/public/css/compiled/styles.css` from `lein garden once`,
  `resources/public/js/compiled/orcpub.js` from `lein fig:build`) are **gitignored**.
  Recompile after a branch switch if you screenshot; never commit them.
- Branch names are descriptive (`feat/<what>` / `fix/<what>`) — no tool-generated
  prefixes, no auto-generated gibberish.

## Push

```
for i in 1 2 3 4; do git push -u origin <branch> && break || sleep $((2**i)); done
```

Retry with backoff on network errors. Use `--force-with-lease` **only** for a
deliberate history rewrite you were explicitly asked to do (e.g. an author scrub).

## Pull requests

- Don't open one unless explicitly asked.
- Target **`integration`**, not `develop` (production stays release-only).
- Mirror the repo's PR template (`.github/PULL_REQUEST_TEMPLATE.md`); fill its sections
  from the diff. Keep the body to what the diff does.

## Where the docs live

This guide, the changelog files, and the tooling that enforces them — `CHANGELOG.md`,
`branch-changelog.template.md`, `scripts/fold-branch-changelog.sh`, the `pre-push` hook,
and the `changelog-guard` workflow — live on the **main flow** (`integration` → `develop`).
They're what a contributor follows and what CI runs, so they stay here.

Deeper reference knowledge — how a subsystem works, what a past investigation measured —
is kept off the main flow rather than landing in its history piecemeal.
