# When an icon font fails, the UI loses controls silently

**A support session, September 2026.** A self-hoster reported that the left/right arrows for
reordering ability scores had vanished from the character builder. Finding out why took hours,
because *nothing anywhere reported a problem* — not the server, not the console, not the page.

The root cause turned out to be a server bug, documented in full in
[locale-safety.md](locale-safety.md). This document is the other half of the lesson: **why the
symptom was so hard to read**, which detection methods work (several widely-recommended ones do
not), and what makes icon-only controls a poor place for any failure to land.

> **Correction, and why it is left visible.** An earlier revision of this document named Dark
> Reader as the cause, on the strength of a computed colour that matched nothing in our source.
> That was wrong. The extension was not even running on the affected page — a later probe returned
> zero injected elements. The colour reading was real and remains unexplained; it was not the
> cause. It is recorded here because the wrong answer was *plausible, evidence-backed and cost
> hours*, and because anyone re-reading the old §3 needs to know not to trust it.

---

## 1. The failure mode

The arrows are rendered as Font Awesome icons:

```clojure
[:i.fa.fa-chevron-circle-left.orange  {:on-click (swap-abilities i (dec i) k v)}]
[:i.fa.fa-chevron-circle-right.orange {:on-click (swap-abilities i (inc i) k v)}]
```

`character_builder.cljs` and `template.cljc`, in `abilities-standard` — shared by the Standard
Scores *and* Dice Roll variants.

An icon font draws nothing itself. The glyph comes from a CSS rule
(`.fa-chevron-circle-left:before{content:"\f137"}`) using a font supplied by another
(`.fa{font-family:"Font Awesome 5 Free"}`). **If either rule fails to apply, the `<i>` is an empty
inline element**, which means:

- nothing is drawn;
- it collapses to **zero width**, so there is no hit target — the control cannot even be clicked
  blindly (the reporter tried, sensibly, and it proved nothing was there);
- it carries no text, no `title`, no `aria-label`, so it is absent from the accessibility tree too.

The control does not degrade. It ceases to exist, with no error anywhere. **That is the durable
lesson, and it is independent of what broke the stylesheet this time.**

## 2. Diagnosing it

Run these in the console on the affected page, in this order.

```js
document.querySelectorAll('.fa-chevron-circle-left').length
```
Non-zero means the elements exist and this is **not** a code, branch or build problem. Stop looking
at Clojure.

```js
[...document.styleSheets].map(s=>{try{return (s.href||'inline')+' :: '+s.cssRules.length}catch(e){return (s.href||'')+' :: UNREADABLE ('+e.name+')'}})
```
A stylesheet present at **0 rules** is the tell. (`UNREADABLE (SecurityError)` on a cross-origin
sheet such as Google Fonts is normal and means nothing.)

```js
fetch('/assets/font-awesome/5.13.1/css/all.min.css',{cache:'reload'})
  .then(r=>r.text()).then(t=>console.log('len',t.length,'head',JSON.stringify(t.slice(0,80))))
```
**Ask for the bytes.** This is the probe that cracked the case and the one that was run last
instead of first. `len 0` means the server sent nothing and every styling theory above it is
moot — see §3.

```js
getComputedStyle(document.querySelector('.fa-chevron-circle-left'),'::before').content
```
`none` means the Font Awesome CSS rule never applied — useful, but it cannot tell you *why*, and
an empty response and a blocked response look identical here.

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

## 3. What actually broke it

The server returned **HTTP 200 with a zero-byte body** for every `/assets/*` webjar request. Font
Awesome's stylesheet arrived empty, so it parsed to zero rules and drew nothing.

Full mechanism, the locale bug behind it, and the fix: **[locale-safety.md](locale-safety.md)**.

The trap for anyone debugging the next one:

> **A 200 with an empty body is indistinguishable, from the CSSOM's point of view, from a
> stylesheet that was fetched and refused.** Both give you a real `<link>`, `disabled=false`, and
> `cssRules.length === 0`. Nothing in `document.styleSheets` tells you which happened.

That ambiguity is what sustained a long chain of wrong answers — a browser extension, strict MIME
checking, a poisoned HTTP cache, CSP — each consistent with everything observed, and each wrong. A
missing `Content-Type` header was read as the cause when it was a *consequence*: no response was
ever built, so nothing set one.

**Ask what arrived before asking why it did not apply.** One `r.text()` would have skipped all of it.

## 4. What would have caught it in seconds

Nothing in the stack noticed. A ~3-line startup check turns a silent deletion into a stated fact:

```js
document.fonts.load('900 16px "Font Awesome 5 Free"')
  .then(f => f.length || document.body.classList.add('no-icon-font'))
```

With a matching rule that gives `.no-icon-font .fa` a visible outline and a minimum size, every icon
button becomes an obvious empty box instead of nothing — ugly on purpose, still clickable, and
self-evidently broken rather than mysteriously absent.

Note what this does and does not buy: it reports the **symptom** immediately, which is worth a great
deal, but it would not have diagnosed the cause. The server was the thing to interrogate.

## 5. The durable fix is not a fallback

Icon fonts are a workaround for HTTP/1.1 connection limits that no longer exist. Current guidance is
consistent: **do not start new work with them.** They ship every glyph, they are render-blocking,
screen readers sometimes announce the raw codepoint, and — as here — a single failure removes the
control entirely. Inline SVG has none of those properties: the markup carries the shape, so there is
nothing to fail to load, and `<title>`/`aria-label` make it accessible by default.

This case is not the strongest argument for that, since the cause was a server bug that would have
blanked inline SVG's stylesheet too — though not the SVG itself, which is the point. The stronger
argument is independent and already in the tree: **five Font Awesome 4 names survive in source and
draw nothing today**, on every browser, with the font loading perfectly.

| in source | FA5 name | where |
|---|---|---|
| `fa-pencil` | `fa-pencil-alt` | `import_log.cljs:63,192` |
| `fa-exchange` | `fa-exchange-alt` | `import_log.cljs:39` |
| `fa-circle-o` | `far fa-circle` | `conflict_resolution.cljs:17` |
| `fa-dot-circle-o` | `far fa-dot-circle` | `conflict_resolution.cljs:17` |
| `.fa-caret-square-o-down` | `far fa-caret-square-down` | `styles/core.clj:1127` (dead selector) |

FA5 replaced the `-o` suffix with style prefixes (`far` = the old "outline"), renaming 463 icons.
The webjar ships `css/v4-shims.css` and `metadata/shims.json` for exactly this, which we do not use.
Five is the complete set — every one of the 44 `fa-*` names in source was checked against the 1,605
SVGs in the webjar — so renaming is cheaper than carrying the shim.

The `conflict_resolution` pair is the radio indicator in the homebrew import conflict modal.
`.radio-icon` has `width:16px`, so it reserves the space and draws nothing; selection stays legible
from the border colour, which is why nobody has reported it. Same silent-failure class as the
arrows, no server bug required.

A wholesale migration of every `[:i.fa…]` in `views.cljs` is a large change and not obviously worth
it. A proportionate rule:

> **Interactive controls get SVG. Decoration can stay an icon font.**

Sizing, for whoever takes it: **44 distinct icon names, 128 occurrences, 7 of which carry their own
`:on-click`** (`chevron-circle-left`/`-right`, `minus-circle`, `plus-circle`, `times`,
`times-circle`, `pencil-alt` — all in `svgs/solid/`). `fa-caret-down`/`fa-caret-up` alone are 47 of
the 128 and are pure decoration. Precedent already exists: `bluesky-icon` (`views.cljs:359`) is a
hand-written inline SVG, added because FA 5.13.1 has no Bluesky glyph.

Whatever the icon, if it is the only label on a control it needs `title` and `aria-label` regardless:
those two arrows currently have neither, so even with the font working they are unlabelled for
screen readers and give no hover hint.

## 6. Order of questions, next time

1. Do the elements exist? (`querySelectorAll(...).length`) — separates code from rendering
2. **Did any bytes arrive?** (`fetch(url,{cache:'reload'}).then(r=>r.text())`) — separates
   *delivery* from *styling*, and is the step that was missed
3. Does the sheet have rules? (`document.styleSheets` → `cssRules.length`)
4. Does `::before` have content? — separates CSS from font
5. Only then: extensions, cache, CSP, MIME, branch, build

Steps 1–2 take a minute. Note the order: **confirm delivery before diagnosing presentation.** The
first revision of this document said "suspect the browser before the build", which is exactly the
advice that sent this investigation wrong — the browser was innocent and the server was not.

## Provenance

Diagnosed with a self-hoster on Spanish-locale Windows, September 2026. The detection comparison in
§2 was run in Chromium against two local fixtures (Font Awesome present / absent) rather than
reasoned about; the width technique was expected to work and did not. The root cause in §3 was
reproduced locally under `es_ES` and confirmed fixed by the reporter on their own machine. The FA4
inventory in §5 comes from cross-referencing every `fa-*` name in source against the webjar's SVG
listing.
