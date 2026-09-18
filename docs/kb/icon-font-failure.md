# When an icon font fails, the UI loses controls silently

**A support session, September 2026.** A self-hoster reported that the left/right arrows for
reordering ability scores had vanished from the character builder. The cause was not in our code.
Finding that out took hours, because *nothing anywhere reported a problem* — not the server, not the
console, not the page.

This doc is the reusable part: how that failure presents, how to identify it in minutes rather than
hours, which detection methods actually work (several widely-recommended ones do not), and what the
durable fix is.

---

## 1. The failure mode

The arrows are rendered as Font Awesome icons:

```clojure
[:i.fa.fa-chevron-circle-left.orange  {:on-click (swap-abilities i (dec i) k v)}]
[:i.fa.fa-chevron-circle-right.orange {:on-click (swap-abilities i (inc i) k v)}]
```

`character_builder.cljs`, in `abilities-standard` — shared by the Standard Scores *and* Dice Roll
variants.

An icon font draws nothing itself. The glyph comes from a CSS rule
(`.fa-chevron-circle-left:before{content:"\f137"}`) using a font supplied by another
(`.fa{font-family:"Font Awesome 5 Free"}`). **If either rule fails to apply, the `<i>` is an empty
inline element**, which means:

- nothing is drawn;
- it collapses to **zero width**, so there is no hit target — the control cannot even be clicked
  blindly (the reporter tried, sensibly, and it proved nothing was there);
- it carries no text, no `title`, no `aria-label`, so it is absent from the accessibility tree too.

The control does not degrade. It ceases to exist, with no error anywhere.

## 2. Diagnosing it in three lines

Run these in the console on the affected page.

```js
document.querySelectorAll('.fa-chevron-circle-left').length
```
Non-zero means the elements exist and this is **not** a code, branch or build problem. Stop looking
at Clojure.

```js
getComputedStyle(document.querySelector('.fa-chevron-circle-left'),'::before').content
```
**`none` means the Font Awesome CSS rule never applied.** This is the single most useful probe: it
tests the thing that actually draws the icon.

```js
(await document.fonts.load('900 16px "Font Awesome 5 Free"')).length
```
`0` means the font family is unknown to the page.

### Detection methods that do NOT work

Verified in a browser against two fixtures — one with Font Awesome, one without:

| method | font present | font missing | verdict |
|---|---|---|---|
| `document.fonts.check('900 16px "Font Awesome 5 Free"')` | `true` | **`true`** | **useless** |
| `document.fonts.load(...)` → `.length` | `1` | `0` | works |
| comparing rendered glyph width vs a fallback font | 60.22px | 60.22px | **does not discriminate** |
| `getComputedStyle(el,'::before').content` | the glyph | `none` | works |

Two traps worth naming, because both cost time here:

- **`fonts.check()` returns `true` for a font family that does not exist.** Per MDN: *"If we specify
  a font that is not in the FontFaceSet and is not a system font, check() returns true, because in
  this situation we will not rely on any fonts from the set."* A vacuous true reads exactly like a
  pass, and it sent this investigation down the wrong path for a while.
- **The width-comparison trick — the one most articles recommend — did not discriminate.** Both the
  icon font and the fallback rendered U+F137 at identical width. Do not reach for it without
  testing it first.

## 3. What broke it here

The reporter had [Dark Reader](https://chromewebstore.google.com/detail/dark-reader) installed
(~7M users). It does not merely overlay a filter: it parses stylesheets, recolours them, and
re-emits them, and it is a known breaker of icon fonts.

The tell was a colour that matched nothing in the source. Both this fork and upstream define orange
as `#f0a100` = `rgb(240,161,0)`; the computed value on their page was `rgb(232,155,0)` = `#e89b00`,
a shade that appears nowhere in the tree or its history. **A computed colour that exists nowhere in
your source is proof something is rewriting CSS in the browser.** That is a fast, general diagnostic
— worth reaching for before suspecting the build.

### The mitigation

Dark Reader honours a lock tag; when present it bypasses the site entirely:

```html
<meta name="darkreader-lock">
```

This is the right call for OrcPub independent of any bug: **the app already ships its own dark
theme**, so Dark Reader re-darkening it is pure downside — shifted colours, wasted work, broken
icons. Users still get dark mode; they get ours. (Caveat: an open issue reports the tag broke in
v4.9.86, so treat it as a strong mitigation, not a guarantee.)

## 4. What would have caught it in seconds

Nothing in the stack noticed. A ~3-line startup check turns a silent deletion into a stated fact:

```js
document.fonts.load('900 16px "Font Awesome 5 Free"')
  .then(f => f.length || document.body.classList.add('no-icon-font'))
```

With a matching rule that gives `.no-icon-font .fa` a visible outline and a minimum size, every icon
button becomes an obvious empty box instead of nothing — ugly on purpose, still clickable, and
self-evidently broken rather than mysteriously absent.

## 5. The durable fix is not a fallback

Icon fonts are a workaround for HTTP/1.1 connection limits that no longer exist. Current guidance is
consistent: **do not start new work with them.** They ship every glyph, they are render-blocking,
screen readers sometimes announce the raw codepoint, and — as here — a single failure removes the
control entirely. Inline SVG has none of those properties: the markup carries the shape, so there is
nothing to fail to load, and `<title>`/`aria-label` make it accessible by default.

A wholesale migration of every `[:i.fa…]` in `views.cljs` is a large change and not obviously worth
it. A proportionate rule:

> **Interactive controls get SVG. Decoration can stay an icon font.**

An icon that *is* a button — these arrows, delete, save — must not depend on a webfont, because when
it fails the user loses a capability. A caret beside a section heading failing to draw is cosmetic.

Whatever the icon, if it is the only label on a control it needs `title` and `aria-label` regardless:
those two arrows currently have neither, so even with the font working they are unlabelled for
screen readers and give no hover hint.

## 6. Order of questions, next time

1. Do the elements exist? (`querySelectorAll(...).length`) — separates code from rendering
2. Does `::before` have content? — separates CSS from font
3. Does the computed colour match the source? — a mismatch means a browser extension
4. Only then: branch, build, server

Steps 1–3 take a minute and would have skipped most of this session. Note the order: **suspect the
browser before the build.** The server, the branch, the compile and the asset route were all checked
here and all were fine.

## Provenance

Diagnosed live with a self-hoster on Windows, September 2026. The detection comparison in §2 was run
in Chromium against two local fixtures (Font Awesome present / absent) rather than reasoned about;
the width technique was expected to work and did not. The `#e89b00` colour reading came from the
reporter's own `getComputedStyle` output.
