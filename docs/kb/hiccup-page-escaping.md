# `hiccup.page/html5` does not escape

**The rule:** a page that shows anything a user typed is rendered with `hiccup2.core/html`, which
escapes every string, and each inline script or style the page itself writes is wrapped in
`hiccup2.core/raw` on purpose, with a comment saying why. Never put user text inside `raw`.

Verified against hiccup 2.0.0 (the version in `project.clj`), 2026-10-14, on
`claude/character-portrait-generator-hOutO` (commit 3935fd01).

## Why

hiccup 2 escapes by default, but only through `hiccup2.core/html`. The 1.x-compatible namespaces
turn it off:

- `hiccup.core/html` expands to `(hiccup2/html {:escape-strings? false} ...)`.
- `hiccup.page/html5` requires `hiccup.core`'s `html`, so it renders with escaping off too.

So `(page/html5 [:title name])` writes `name` into the page verbatim, and nothing about the call
site says so. `orcpub.index/index-page` rendered every page through `html5`, including the public
character page, whose title, `og:title` and description carry the character's name. A character
named `<b>MARK</b>` came out as live markup. Artist names on `/artists/<slug>` go through the same
template.

## What changed

`index-page` now returns `(str "<!DOCTYPE html>\n" (h/html {:mode :html} [:html ...]))`. Every
piece that must stay raw is wrapped by name: the inline boot styles, the spinner CSS and markup,
the `--innerHeight` script, the branding JSON (already escaped for a script context by
`script-json`), the boot-rescue script, the cookie init and the homebrew script.

Checked before and after: an ordinary page is byte-identical; the splash page changes only
`D&D 5e` to `D&amp;D 5e`, which the browser shows the same. Test: `orcpub.page-escape-test`
(3 failures on the code before the change).

## Still on `html5`

`orcpub.privacy/terms-page`: fork-written policy text and the integrations head tags, no user
input. If it ever shows user input, move it to `hiccup2.core/html` the same way.

## How to check a page

```bash
grep -rn "hiccup.page\|hiccup.core" src --include=*.clj
```

Any page built with these that shows user input is a cross-site-scripting hole. Test it with a
value containing `<b>MARK</b>`: the response must contain `&lt;b&gt;MARK` and not `<b>MARK</b>`.
