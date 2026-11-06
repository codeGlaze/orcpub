# Plan: export a homebrew source as Markdown

Owner, 2026-11-06. Branch: `fix/health-banner-contrast` (the owner asked for it all on one branch; its name
no longer says what it holds, so the PR title does).

**Why.** Homebrew is trapped in the builder and in `.orcbrew` files. Someone asked for feedback on a
subclass they are building: they need a readable file to share, print at a table or review without
loading it into the builder and making a character.

**MVP.** One source, one Markdown file, in 5e style, for the types people ask for feedback on first.
Classes and subclasses come first, then the simple types. One-way: nothing imports Markdown back.

## Decisions (owner)

- Per source first; a whole-library file later.
- It runs through the existing export checks (`validate-and-show-modal-or-export`, the correction and
  fill-in gate), so a damaged entry is reported, not printed as garbage.
- No Markdown library: writing Markdown is string building. Only reading and rendering it needs one,
  which is a different feature (`srd-2024-roadmap.md`, "Markdown in user notes and homebrew text").
- User text is escaped (`*`, `_`, `|`, `#`, `<`, backticks, a `|` inside a table cell).

## Shape

`src/cljc/orcpub/dnd/e5/homebrew_markdown.cljc`: `source->markdown` takes a stored source and returns a
string. A table maps each content type to its formatter, so the differences are data
(AGENTS.md, "Similar things share one function"). The shape table (`homebrew_shapes.cljc`) names the
fields. Level modifiers print as readable lines from a label table; an unknown type falls back to
`Type: value`, never an error.

5e style: `# Name`, an italic line (`*Ranger subclass*`), a `Level | Features` table for classes and
subclasses, then each feature as `***Feature Name.*** text` in level order, choices stated where they
occur, spellcasting and always-prepared spells as their own lines.

## Pieces

1. Core: the namespace, the escape helper, class and subclass formatters, tests (cljc, under `lein test`).
2. Wiring and the simple types: a per-source "Export as Markdown" beside Export, through the export gate,
   a text download; spells, feats, backgrounds, races, subraces, languages, invocations, boons; a browser check.
3. Later: monsters, items, encounters, and the whole-library file.

Cost estimate (not measured): about 150k helper tokens a piece, plus review here. The bundle grows about
5 KB compressed against the 900 KB ceiling.

## Piece 1 built (2026-11-06)

`homebrew_markdown.cljc` (650 lines) with tests (318), commits `76399a1fb`, `80c083707`, `39e9ab9aa` on
`fix/health-banner-contrast`. Public: `escape-md`, `escape-cell`, `class->markdown`, `subclass->markdown`,
`source->markdown`, `modifier->text`; `formatters` is the ordered table piece 2 adds rows to.

- The builder's modifier labels live inside `modifier-values` in `views.cljs` and need a subscription, so
  the cljc writer has its own `modifier-labels` table (12 types); an unknown type prints `Type: value`.
- Not covered yet: `multiclass-*` profs, `equipment-selections`, raw `:selections`. A modifier whose level
  is not 1 to 20 is listed under "Features without a valid level"; a feature with no level counts as 1.
- Open style call (owner): the level table shows feature names only; proficiencies, speeds and
  resistances stay in the "Gained at this level" lists.

