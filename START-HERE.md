# START HERE

If you started without project instructions (no `CLAUDE.md` loaded: you are on a code branch,
or on `develop`, or in a worktree nobody set up), you have no MUST FOLLOW rules, no skills and no
armed git hooks. Fix that first. The SessionStart hook does it for you where `.claude/` exists,
and says so; read the `CLAUDE.md` it names before doing anything else.

## 0. Find out where you already are

```bash
git branch --show-current
git log --oneline -3
git ls-tree -r HEAD --name-only docs/kb/ | wc -l
```

**Some branches carry KB pages in progress** in their own `docs/kb/` (the Fall Update branches:
`feature/grant-rows` and those built on it). They are edited there and move to `agents/develop`
later (`AGENTS.md`, top); until they do, read both. A branch name can mislead:
`perf/entity-build` is mostly AC engine work, not a finished perf spike.

## 1. Run this

```bash
git show origin/agents/develop:scripts/agent-setup.sh | bash
```

Installs the agent tooling into the working tree, arms `core.hooksPath`, and prints
where the knowledge base is. Everything it writes is gitignored on code branches, so
none of it can reach a pull request. Safe to re-run; `--check` verifies without
changing anything.

## 2. Read what it points you at

`BRANCH.md` on `agents/develop` carries the project's live state and the active plan, and your
branch's handoff, `branches/<branch>.md`, carries its own. Read both before proposing work: the
plan may have moved since the last session. A branch with no handoff has nothing carried over.

## 2b. If you are going to run anything

Four test layers, and picking the wrong one wastes a build. There is a **seeded verified user**
(`kaylee` / `serenity99`) and a one-command script that boots a server holding it —
`./scripts/e2e/run.sh`. Two sessions concluded logged-in browser testing was impossible here before
anyone wrote that down:

```bash
git show origin/agents/develop:docs/kb/e2e-logged-in-sessions.md
```

## 3. Two rules that cause the most rework here

**Findings end up on `agents/develop`**, in `docs/kb/`, indexed in `docs/kb/README.md`. You may
write them on your working branch first and move them later (`AGENTS.md`, top). An unindexed doc is invisible and `check-docs.sh` will fail the
commit. Read the KB from a code branch without checking it out:

```bash
git show origin/agents/develop:docs/kb/<file>.md
git ls-tree -r origin/agents/develop --name-only docs/kb/
```

**Do not grep Clojure with `grep`.** `#_` discards the next form, so discarded code
reads as live — 27 subclasses in `classes.cljc` are switched off this way. Use
`scripts/clj-grep.py`, which separates live from discarded.

---

Full conventions: `git show origin/agents/develop:docs/kb/documentation-discipline.md`
