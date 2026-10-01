# OrcPub style guide

What the site's styles are, which ones are canonical, and the rules for adding more.
Written 2026-09-30 from the Garden stylesheet, `src/clj/orcpub/styles/core.clj`.

**Which branch this describes.** Written against `claude/artist-profile-pages`, whose `core.clj`
differed from `integration` (2026-09-30) by 9 lines, all in `.builder-tabs`. Line numbers are from
that snapshot and drift as the file changes; the named `def`s and class names don't. The portrait
drawer (`portrait.cljs`) exists only on the portrait branch.

**Captures.** The before/after screenshots and comparison pages are on `claude/artist-profile-pages`
under `docs/design/style-guide/` (about 8 MB of PNGs, kept off the trunk). The capture scripts are
here in `docs/design/style-guide/` and re-shoot them against a running `lein e2e-server`.

**How it was made.** A cheap agent inventoried every colour, size and spacing value with
file:line references (`style-inventory.md`, alongside this file). Its counts are approximate
and it misread one deliberate choice as a contradiction (the two reds, see below). Everything
this guide states as fact was re-read from the source.

Decisions marked **Proposed** are the owner's to make. Everything else describes what exists.

---

## 1. Rules for new work

1. **Look before you add.** Search this guide and `core.clj` for a class that already does the
   job. If one is close, extend it with a modifier (`.btn.quiet`) rather than copying it with
   small changes. Section 5 lists what near-copies have already cost.
2. **No inline styles.** Styles go in Garden. No `:style` maps in views, no CSS strings in
   `[:style]` tags. Values that vary at runtime come from a fixed list, so each value gets a
   class (see the artist site-mark colours, section 6).
3. **Name colours before using them.** A new colour becomes a `def` at the top of `core.clj`
   with a comment saying what it's for, like `warning-yellow`. No new hex literals inside rules.
4. **Every colour works in both themes.** If one value can't, define a pair and let
   `.app.light-theme` swap it, the way the two reds already do (section 2).
5. **Keyboard focus stays visible.** Anything clickable gets a `:focus-visible` style. See
   section 4 for why this currently needs doing by hand.

---

## 2. Colour

### Named colours (`core.clj:8-21`)

These are the palette. Use them by name.

| Name | Value | Role |
|---|---|---|
| `orange` / `button-color` | `#f0a100` | The accent: links, buttons, highlights, the dark theme's signature |
| `red` | `#9a031e` | Errors in the **light** theme |
| `red-on-dark` | `#ff6b6b` | Errors in the **dark** theme |
| `amber-on-dark` | `#f5b942` | Field warnings, dark theme |
| `muted-on-dark` | `#9fb0c3` | Secondary text, dark theme |
| `warning-yellow` | `#ffd21a` | "Needs attention": unresolved conflicts, missing fields |
| `broken-red` | `#e5637a` | "Broken": invalid or unexportable data |
| `green` | `#70a800` | Conflict resolution: keep existing |
| `cyan` | `#47eaf8` | Import log; conflict rename option |
| `purple` | `#8b7ec8` | Conflict skip option |

**The two reds are the model for theme pairs.** The comment at `core.clj:11` explains it: `#9a031e`
reads about 9:1 on white and about 2:1 on the dark ground. So the dark theme uses
`red-on-dark`, and `.app.light-theme` swaps `red` back in. Any colour that can't serve both
themes should follow this pattern.

### Surfaces (unnamed today)

These recur as literals. **Proposed:** give each a name.

| Value | Where | Role |
|---|---|---|
| `#313A4D` → `#080A0D` | `index.clj:174` (`#app` gradient); `#080A0D` alone at `index.clj:172` (body) | Page ground |
| `#313A4D` | `core.clj:1215`, `1710`; top of the page gradient | Header bar, stuck sticky header |
| `#1a1e28` | `core.clj:1199`, `1753`, `1938`, `2278` | Panels and modals (dark in **both** themes, per `core.clj:1960`) |
| `#2c3445` | `core.clj:665`, `1950`, `2169`, `2291` | Raised surfaces inside panels: dropdowns, modal headers |

### Light theme (`.app.light-theme`, from `core.clj:1778`)

| Value | Role |
|---|---|
| `linear-gradient(182deg, #FFFFFF, #DDDDDD)` | Page ground |
| `#363636` | Main text, links |
| `#282828` | Input borders, option text |
| `#33658A` | The light theme's accent: `.form-button`, focus of attention. Stands in for amber |
| `#8a5a00` | Warning text (light counterpart of `amber-on-dark`) |
| `#55637a` | Note text |

`#33658A` does for the light theme what `orange` does for the dark one, but it has no name.
**Proposed:** `accent-light`.

### Near-duplicates to fold together

| Values | Where | Proposed |
|---|---|---|
| `#f0a100` and `#f1a20f` | `orange`; the `.form-button` gradient top (`core.clj:1428`) and builder tabs (`1330`, `1343`) | Use `orange`; the difference is invisible |
| `#dbab50` | `.form-button` gradient bottom | Name it as the gradient's second stop, or drop the gradient |

---

## 3. Type

| Face | Loaded | Used for |
|---|---|---|
| **Open Sans** | Google Fonts, `index.clj:265`, **regular weight only** | Everything in the app |
| **Vollkorn** italic | `@font-face` in the portrait drawer's CSS string, a Latin-1 subset | Artist names in credits; the baked portrait caption |
| `ui-monospace, Menlo, monospace` | system | Code and seed values in the portrait drawer |

**Bold is missing.** Only Open Sans 400 is loaded (`css?family=Open+Sans`), but tabs, titles
and labels ask for 600 and 700. In Chrome and Edge that text renders at **regular** weight: the
browser doesn't even fake the bold. Firefox and Safari may fake it by smearing the regular weight.
Loading `Open+Sans:wght@400;600;700` fixes both. For a modern browser that's one more file, about
48 KB for Latin text. Before/after, captured from the site: `style-guide/bold.html`.

**Decided (owner, 2026-09-30): header tabs 700, page title 600.** At 700 the tabs are far more
readable: they are small, all caps and sit on a busy banner, and capitals carry the extra weight
without thickening. The title is large and mixed case, and at 700 its round lowercase letters turn
plump, so it takes 600. Both share the generic `.f-w-b` bold class today, so each needs its own
weight in Garden: `.header-tab` 700, the page title (`h1.f-s-36` in `views/header`) 600. Chosen
from the pair captured together: `style-guide/weights.html`.

### Weights (decided 2026-09-30)

| Weight | For | How |
|---|---|---|
| **700** | Small capitals labels, 14px and under: header tabs, sheet tabs, `.form-button`, `.mc-btn` | `.f-w-b`; the button classes set 700 |
| **600** | Bold text 18px and up: page and panel titles, section headings, spell and monster names, big numbers | One rule: `.f-w-b` combined with `.f-s-18` or larger renders at 600 (155 call sites, no per-site edits) |
| **400** | Sentences, e.g. the builder's info boxes (`info-block`) | No weight class; links inside them keep `.f-w-b` |

Why: at display sizes 700 thickens mixed-case letters and reads plump, while small capitals carry
700 well and read more easily for it. Found by the owner on the builder's "Race" heading, its info
sentence and My Content's "Import Option Source"; a survey of every bold element on four pages
(`survey-weights.js` on `claude/artist-profile-pages`) showed the same split.

**Button contrast: opt-in dark text.** White on the amber button gradient is 2.1:1, under the
4.5:1 minimum for text; dark text (`#15202e`) is 7.8:1. Long-time dark-theme users know the
white, so it stays the default, and a "Dark Button Text" checkbox beside "Light Theme" (dark
theme only) switches `.form-button`, `.mc-btn` and `.roll-button` to dark text. The choice is
saved in the browser with the theme, and on the account when logged in
(`:orcpub.user/dark-button-text?`); at login a choice saved on the account wins.

**Text lift on amber (decided).** Text on the amber buttons gets a faint edge in the opposite
tone: `text-lift-dark` (a soft dark shadow) under the default white text, `text-lift-light` (a
soft light glow) under the opt-in dark text. It sharpens the letters without changing the colours
people know. Applied to `.form-button` (dark theme only, since it's slate in the light theme),
`.mc-btn` and `.roll-button` (amber in both themes), and to every header tab (the active one is
amber; the grey ones on the banner firm up too). The ability buttons (`.roll-button`, "+2",
"-1") are 700 like the other amber buttons. A one- or two-character label has too little stroke
to hold up with the standard lift, so with dark text they get a stronger glow (70% white) and a
yellow rim: `-webkit-text-stroke: 2px` in `warning-yellow` (`#ffd21a`) with `paint-order: stroke
fill`, which draws the rim behind the letters so about 1px shows outside them and the letters keep
their full weight. Chosen over white, pale yellow, amber and orange rims (captures `round9.html`,
`round10.html` on `claude/artist-profile-pages`). Orange and amber blend in because they're about as
light as the button; an outline only separates a letter when it's lighter or darker than the button.

*Fallback if the rim doesn't hold up in use*, in order:
1. Pale yellow `#ffe680` rim (option l): crisper, still warm; adds one colour to the palette.
2. Remove the two stroke properties and keep the stronger glow (option d, the state at `844c15ca`).
3. Drop the `.roll-button` override entirely: the standard `text-lift-light`, as on the other buttons.

**Light-theme inconsistency.** `.form-button` turns slate blue in the light theme, but its copies
`.mc-btn` and `.roll-button` stay amber, because the light-theme rule was only written for one
copy. The shared button base (section 5) fixes this properly.

**My Content tab wrap (decided).** At 700, "MY CONTENT" needed 92.5px of the tab's fixed 90px
and wrapped. The tab titles have −4% letter spacing (86.9px, 3px to spare; −2% left only 0.3px).

**Buttons aren't in Open Sans.** `.form-button` and the other `<button>` classes render in Arial,
because browsers give buttons their own default font and nothing here sets `button
{font-family: inherit}`. Confirmed with the browser's own font report: the builder's buttons use
Arial Bold (Liberation Sans on Linux), the header tabs Open Sans.

**Screenshot caveat.** In the Claude Code sandbox, the headless browser can't verify the network
proxy's certificate for Google Fonts, so pages silently fall back to another sans. Captures made
before 2026-09-30 (the artist-profile mockups included) show that fallback, not Open Sans.
`style-guide/capture-bold.js` shows the fix: fetch font requests on the Node side with
`NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt`, which keeps certificate checks on.

Sizes come from the `.f-s-*` utilities (`core.clj:66`): 10, 11, 12, 14, 16, 18, 20, 24, 28, 32,
36, 48 px. UI chrome is 12px/600 uppercase (buttons, tabs); body text is 14px.

---

## 4. Spacing, radius, focus

**Spacing** comes from generated utilities (`px-prop`, `core.clj:34`), not individual rules:
- `m-l-*`: −1 to 9, then 10 to 50 in 5s.
- `m-t-*`: 0 to 9, **21**, then 10 to 25 in 5s. 21 is a one-off for a single header.
- `w-*`: a fixed list (`core.clj:53`).

A class that isn't in these lists compiles without error and **produces no CSS**. The
garden-inline-styles harvest hit this with `.m-t-100` and `.min-w-53` (see
`docs/kb/garden-inline-styles-harvest.md` on `agents/develop`). Check the list before using one.

**Radius.** 5px for buttons, inputs and panels (`.b-rad-5`); 10px for large frames; 50% or 999px
for pills and dots. The inventory also found 3, 4, 6 and 8px one-offs. **Proposed:** 5 and 10 only,
plus pills.

**Focus.** `*:focus {outline: 0}` (`core.clj:1202`) removes the keyboard focus ring from
everything. Only the dev-mode toggle adds one back (`core.clj:2217`, a 2px orange outline, offset
3px). **Proposed:** replace the global rule with `:focus-visible` rings on interactive
elements (orange in dark, `#33658A` in light), using the dev-mode toggle's style as the
standard. Until then, new components must add their own.

**Phone width.** One breakpoint: 767px and under is a phone (`xs-query`). The stylesheet and the
page code use the same cutoff: at phone width the page draws the phone layout on any device,
and resizing across it re-lays the page (`user-agent/layout-type`, the `:device-type`
subscription). Wider than that, the device decides between desktop and tablet as before. What the
device *can do* (a keyboard for the roll buttons' ctrl/shift tip) reads `:ua-device-type` instead.
To capture the phone layout, a narrow window is enough now; a real phone's user agent still
matters for touch-only behaviour.

---

## 5. Buttons

### What exists

| Class | What it is | Where |
|---|---|---|
| `.form-button` | The primary button: amber gradient, white 12px/600 uppercase, 5px radius, shadow on hover. Flat `#33658A` in light theme | `core.clj:1419`; light at `1831` |
| `.link-button` | A text action: amber uppercase underlined text, no fill | `core.clj:1647`; light at `1807` |
| `.mc-btn` | **A copy of `.form-button`** plus icon spacing (`inline-flex`, `gap: 7px`) and no wrap | `core.clj:1500` |
| `.roll-button` | **A copy of `.form-button`** with 14px text, 2px radius and different padding | `core.clj:1462` |
| `.pl-btn` (portrait branch) | The drawer's own button set, "matched to `.form-button`" per its comment, in an inline CSS string | `portrait.cljs` |
| `.ap-link` (portrait branch) | The artist page's link rows, written fresh | `views/artist_page.cljs` |
| `.remove-item-button`, `.add-item-button`, `.expand-collapse-button`, `.close-button`, `.inv-picker-btn` | Single-purpose controls | `core.clj` |

The `port/redesign-on-refactor` branch adds six more (`theme-switch-btn`, `theme-mode-btn`,
`select-menu-btn`, `ability-stepper-btn`, `opt-info-btn`, `form-submit-btn`). No branch has a
shared base.

### What's wrong with reusing them as-is

- No focus style (section 4).
- `.form-button` has one weight only. There's no quiet or outlined version, so pages that need
  one invent it, which is how `.pl-btn`'s ghost button and `.ap-link` happened.
- Three of the classes are the same button with small differences, so a fix to one misses the
  others.

### Proposed: one base, a few roles

```
.btn            shared: Open Sans 12px/600 uppercase, 5px radius, padding 10px 15px,
                cursor, focus-visible ring, disabled state
.btn.primary    today's .form-button (amber; #33658A in light)
.btn.quiet      outlined, transparent fill: the drawer's ghost button, secondary actions
.btn.row        full-width row with an icon, a label and a trailing detail: link lists
.btn.icon       inline-flex with a gap, for icon + label (what .mc-btn adds)
.btn.small      the compact size .roll-button needs
```

`.form-button` stays as an alias of `.btn.primary` until its call sites move, so nothing breaks
at once. `.mc-btn`, `.roll-button` and `.pl-btn` then become the base plus a role instead of
copies.

**Where:** a small branch off `integration`. Not the portrait branch, which shares no history
with `integration`, and not `refactor/garden-inline-styles`, which is being harvested for parts
and never touched buttons.

---

## 6. Colours chosen by users

The artist's site-mark picker (`docs/design/artist-profiles/picker.html`) is the first place users
pick colours. It follows rule 2: every choice is a named preset with a dark and a light value, so
each one compiles to a class and nothing is set inline.

| Preset | Dark theme | Light: deeper shade | Light: paired colour |
|---|---|---|---|
| yellow | `#ffd21a` (`warning-yellow`) | `#9a7400` | `#33658A` |
| coral | `#ff6b57` | `#d9432f` | `#0f7c80` |
| teal | `#1fb5a3` | `#0f8577` | `#b4532a` |
| rose | `#f06aa6` | `#c2386f` | `#1f7a4a` |
| green | `#43b85c` | `#2f8a44` | `#8e3a78` |

Each value is at least 4.3:1 against its own theme's background. `port/redesign-on-refactor`
treats themes as data with an `--accent` CSS variable. If that model is adopted, these presets
belong in it as tokens.

---

## 7. Open decisions

1. ~~Load Open Sans 600 and 700, and make buttons use the site font~~ Done on `feature/style-guide`, with the header weights (section 3).
2. Replace the global `outline: 0` with focus-visible rings (section 4).
3. Name the surface colours and the light accent (section 2).
4. Build the shared button base on a branch off `integration` (section 5).
5. Radius: 5 and 10 only, plus pills (section 4).
6. Whether the redesign branch's token model is the direction for themes (section 6).
