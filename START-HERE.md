# START HERE

You are on a code branch. It carries no `CLAUDE.md` and gitignores `.claude/`, so you
have loaded no project instructions, no skills and no armed git hooks. Fix that first.

## 1. Run this

```bash
git show origin/agents/develop:scripts/agent-setup.sh | bash
```

Installs the agent tooling into the working tree, arms `core.hooksPath`, and prints
where the knowledge base is. Everything it writes is gitignored on code branches, so
none of it can reach a pull request. Safe to re-run; `--check` verifies without
changing anything.

## 2. Read what it points you at

`BRANCH.md` on `agents/develop` carries the live state and the active plan. Read it
before proposing work — the plan may have moved since the last session.

## 3. Two rules that cause the most rework here

**Findings go to `agents/develop`, never to a code branch**, in `docs/kb/`, indexed in
`docs/kb/README.md`. An unindexed doc is invisible and `check-docs.sh` will fail the
commit. Read the KB from a code branch without checking it out:

```bash
git show agents/develop:docs/kb/<file>.md
git ls-tree -r agents/develop --name-only docs/kb/
```

**Do not grep Clojure with `grep`.** `#_` discards the next form, so discarded code
reads as live — 27 subclasses in `classes.cljc` are switched off this way. Use
`scripts/clj-grep.py`, which separates live from discarded.

---

Full conventions: `git show agents/develop:docs/kb/documentation-discipline.md`
