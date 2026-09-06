# Branch changelog — `feat/whats-new-panel`

## Why this branch exists

A release currently lands with nothing to show for it: the changelog is a file in
the repo, and someone who used the site last month has no way to learn that their
portrait link works now or that My Content can move things between sources. This
adds the panel that tells them, once, and a way back to it afterwards.

Scope is deliberately small: one release entry, no route, no server work. The
highlights live in `src/cljc/orcpub/whats_new.cljc` — at release time that file is
the only edit, and a new `:id` is what makes the panel open again.

## Highlights

The site now says what changed. New release highlights open once per browser and
then stay one click away in the footer, so a release is something people notice
rather than something they'd have to go read the changelog to find.

## Added

- **What's New panel** — the current release's highlights open on the first visit
  after it ships, and the footer link and version line reopen them any time.
  Closing it stamps the release, so it stays shut until the next one (`ee3e4d8b`).
- **`orcpub.whats-new`** — the release entries and the id that gates the panel, in
  one cljc file the panel and the tests both read (`ee3e4d8b`).
- **Twelve Summer Patch highlights under three headings** — library, characters,
  printing — covering the builder freeze, the spell rows that never printed, the
  two styles that could not export a multiclass caster, and the packed multiclass
  layout, alongside the homebrew and portrait work.
