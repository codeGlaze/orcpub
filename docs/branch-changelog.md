# Branch changelog — `docs/orcbrew-format`

## Why this branch exists

`HOMEBREW_REQUIRED_FIELDS.md` said which fields a homebrew item needs. Nothing said what
may go *inside* them, so hand-authoring meant guessing at key names — and a wrong guess is
silent: an unknown `:level-modifiers` type is logged to the console and dropped, and a bare
ability keyword where a namespaced one belongs imports cleanly and grants nothing.

## Added

- **`docs/ORCBREW-AUTHORING.md`** — what may go in an `.orcbrew` and what the app does with
  it: the container, the two grant vocabularies and why they are confused, the
  namespaced-keyword rule, what the builders will not write, and a complete example that was
  imported and driven to a level-9 character (`7b5f9169`).
- **A section for tool authors** — none of the three checking layers validates the shape of a
  brew, so an editor linter cannot be derived from the spec registry. Names the real contract,
  says why the looseness is deliberate, and lists the closed sets with counts (`15a73cee`).
- **`scripts/lint-todo.py`** — every roadmap section must open with a `**Status:**` line from
  a fixed vocabulary, and in `--strict` mode nothing marked shipped may survive. Wired into a
  new `roadmap-guard` workflow and the pre-push hook, at the fold, where pruning is due
  (`4f7dea2d`).
- **`scripts/lint-doc-links.py`** — fails a document that cites a file existing nowhere, on
  this tree or on the branches that hold documentation. Written after a document was cited
  repeatedly as evidence and turned out to be on a branch nobody had searched (`0022b480`,
  `985a1b8d`).
- **Roadmap rules in `docs/CONTRIBUTING.md`** — `docs/TODO.md` is shared and cross-branch
  because an item outlives the branch that noticed it; a Status line names the owning branch;
  items leave when a release folds (`073a50ef`).

## Changed

- **`docs/TODO.md` items now leave.** In 22 commits no section had ever been removed, so the
  file carried finished work presented as pending. The character-image section is the first
  through the rule: shipped narrative out, three open questions kept at Status Open
  (`073a50ef`).
- **`CLAUDE.md` and `docs/kb/` are gitignored here.** Both are dev-only on a code branch —
  tracked on `agents/develop`, useful locally, never merged. Tracked docs cite the knowledge
  base by branch rather than by a path that will not exist (`8e2c3584`).

## Fixed

- **Starting-equipment references pointed at another branch** as though the feature were
  unmerged. `starting_equipment_ledger.cljc` and its tests are on `integration`; the
  references now cite that code (`549ba9a4`).
- **The SRD class-map roadmap item asked for work already done.** All 12 base classes are
  catalogued on `feature/grant-rows` with their auto-features and odd cases, and the
  author-parity comparison it needs has an established method there too. The item now points
  at both and narrows itself to the one comparison left (`86d7b825`).
- **The SRD class-map roadmap item could be counted wrong.** `#_` discards the next form and
  `classes.cljc` carries 160 discarded `:name` hits against 346 live, so the item now names
  `scripts/clj-grep.py` and both numbers (`01c7a565`).
