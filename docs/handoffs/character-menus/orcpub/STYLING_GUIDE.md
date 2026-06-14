# OrcPub Styling Guide — how to navigate & extend the CSS

A practical map of how styling works in this app, written so you can find and
change the right thing fast instead of re-deriving it each time.

## TL;DR
- **All CSS is generated from Clojure (Garden), not hand-written.** The single
  source of truth is `src/clj/orcpub/styles/core.clj`. There is **no `.css` file
  you should edit** — `resources/public/css/compiled/styles.css` is a build output.
- The app uses a **utility-first / atomic class system** (like Tailwind/Tachyons):
  tiny single-purpose classes (`.p-10`, `.m-l-5`, `.flex`, `.f-s-18`, `.b-orange`)
  composed in Hiccup. Markup reads like `[:div.flex.align-items-c.p-10.b-1.b-rad-5]`.
- Larger, named **component classes** (`.builder-option`, `.checkbox`,
  `.form-button`, `.selection-stepper-main`) live in the same file under the `app`
  var.
- Recompile after editing (see "Build loop"). Figwheel watches the compiled CSS
  and hot-reloads it.

## Where everything lives
`src/clj/orcpub/styles/core.clj` is one namespace assembled at the bottom into the
`app` stylesheet:

```clojure
(def app
  (concat
   [ ...component & page rules... ]   ; named classes, :input, :select, theme block
   margin-lefts                       ; generated utility ranges ↓
   margin-tops
   widths
   font-sizes
   props                              ; the big atomic-class grab-bag
   media-queries))                    ; responsive + print
```

So when you need to change styling, decide **which bucket** your change belongs to:

| You want to… | Edit this | Notes |
|---|---|---|
| Add a one-off spacing/color/flex utility | `props` vector | Atomic class, reused everywhere |
| Add a spacing/size value in an existing scale | the relevant `px-prop` call | e.g. add `15` to the widths list |
| Style a named component | `app` vector (top section) | e.g. `.builder-option`, `.checkbox` |
| Change brand color | the top-of-file constants | `orange`, `red`, `green` |
| Change responsive behavior | `media-queries` vector | Bootstrap-style breakpoints |
| Adjust light theme | `.app.light-theme` block inside `app` | Mirrors dark defaults |

## The design tokens (top of file)
```clojure
(def orange "#f0a100")   ; primary brand / accent — used for borders, links, buttons
(def red    "#9a031e")   ; error / invalid
(def green  "#70a800")   ; success / valid
(def font-family "Open Sans, sans-serif")
```
`orange` (`#f0a100`, aliased `button-color`) is the single accent that drives
selected-borders, links, the checkbox shadow, and button gradients. Reuse it via
the `.orange` / `.b-orange` / `.bg-orange` classes rather than re-typing the hex.

## How the atomic classes are generated
Two mechanisms:

1. **Hand-written atomic classes** in the `props` vector — one entry per class:
   ```clojure
   [:.flex            {:display :flex}]
   [:.align-items-c   {:align-items :center}]
   [:.b-rad-5         {:border-radius "5px"}]
   [:.b-orange        {:border-color button-color}]
   ```

2. **Programmatically generated scales** via `px-prop`:
   ```clojure
   (defn px-prop [kw abbr values]            ; kw=CSS prop, abbr=class stem
     (map (fn [v] [(keyword (str "." (name abbr) "-" v)) {kw (str v "px !important")}])
          values))

   (def widths  (px-prop :width  :w   [12 14 15 18 20 24 32 ... 1440]))
   (def margin-lefts (px-prop :margin-left :m-l (concat (range -1 10) (range 10 55 5))))
   ```
   ⚠️ **These only exist for the listed values.** `.w-200` exists; `.w-205` does
   **not**. If you need a value that isn't in the scale, either add it to the
   `values` list or use an inline `:style` map (see below). Don't invent a class
   name and expect it to resolve.

### Naming conventions (so you can guess class names)
- Spacing: `m`=margin, `p`=padding + side `-t/-r/-b/-l` + size. `m-l-5`, `p-t-10`,
  `m-r--5` (double dash = negative), `m-5` (all sides), `p-5-10` (shorthand pair).
- Sizes: `w-`/`h-` + px from the scale; **`-p` suffix = percent** (`w-100-p`,
  `w-50-p`). Heights: `h-24`, `h-100-p`.
- Type: `f-s-N` font-size (most are `!important`), `f-w-b`/`f-w-n`/`f-w-600` weight,
  `i` italic, `uppercase`, `l-h-20` line-height, `t-a-c/-l/-r` text-align.
- Flex: `flex`, `flex-column`, `flex-grow-1`, `flex-wrap`, `align-items-c/-t/-end`,
  `justify-cont-s-b/-s-a/-c/-end`.
- Color: `.white`, `.black`, `.orange`, `.red`, `.green` (text); `.bg-light`,
  `.bg-lighter`, `.bg-orange`, `.bg-slight-white` (background); `.fill`/`.stroke`
  via `.main-text-color` / `.stroke-color` for SVG.
- Border: `b-1`/`b-3` (width+solid, **color comes separately**), `b-orange`/
  `b-red`/`b-gray` (color), `b-w-5` (width override), `b-rad-5`/`b-rad-50-p`.
  Note borders are two classes: `.b-1.b-orange`.
- State/misc: `pointer`, `hover-shadow`, `opacity-5`, `hidden`, `posn-rel/-abs`.

## Using classes in Hiccup (Reagent)
Classes are written as keyword suffixes on the tag; multiple just chain:
```clojure
[:div.flex.align-items-c.p-10.b-1.b-rad-5.b-orange
 [:span.f-w-b.f-s-18.flex-grow-1 "Title"]]
```
For **dynamic** classes use `:class-name` (note: Reagent's `:class` also works, but
this codebase uses `:class-name` with `clojure.string/join`):
```clojure
[:div.p-10.b-1.b-rad-5.b-orange
 {:class-name (s/join " " (remove nil? [(when selected? "b-w-5")
                                        (when selectable? "pointer hover-shadow")]))}]
```

## Inline styles — when the scale doesn't have it
There is **no grid utility and no arbitrary spacing** in the system. For those,
use a Reagent `:style` map directly — this is the pragmatic escape hatch and is
fine for layout values that don't deserve a reusable class:
```clojure
[:div {:style {:display "grid"
               :grid-template-columns "repeat(auto-fill, minmax(180px, 1fr))"
               :gap "6px"}}]
```
Prefer a utility class when one exists; reach for `:style` for true one-offs
(CSS Grid templates, computed widths, a specific `minmax`).

## `!important` is everywhere — overriding gotcha
Many generated utilities (all `font-sizes`, all `widths`, the `px-prop` scales)
emit `!important`. Consequences:
- A later plain rule **won't** beat `.f-s-14`/`.w-200`. To override, either use
  another `!important` utility or an inline `:style` with `!important`, or don't
  apply the conflicting utility in the first place.
- This is why component classes and utilities mostly don't fight — but if a style
  "won't take", check whether an `!important` utility is on the element.

## Named component classes worth knowing
In the top section of `app`:
- `.builder-option` — the bordered option card (`1px` semi-white border, radius 5,
  padding 10). The selection list's per-option box. (Note: `option-selector-base`
  in `character_builder.cljs` actually composes utilities `.p-10.b-1.b-rad-5.b-orange`
  rather than this class — both patterns exist; prefer matching the local file.)
- `.selected-builder-option` / `.selectable-builder-option:hover` — selected =
  3px white border + bold; hover = orange border + shadow.
- `.checkbox` (+ `.checked` / `.disabled`) — 16×16 white box with an orange
  bottom-shadow and a `.fa-check`. Built by `comps/checkbox`.
- `.form-button` / `.link-button` — primary (orange gradient) and text buttons.
- `.input` — transparent field, white 1px border, radius 5, padding 10.
- `.selection-stepper-*` — the floating "remaining selections" stepper.
- `.builder-tabs` / `.builder-tab` / `.selected-builder-tab` — the section tabs.

## Theming
`.app.light-theme { … }` (inside `app`) **overrides** the dark defaults for light
mode: it re-defines `.orange`, `.b-orange`, `.input`, `.bg-light`, buttons, etc.
If you add a component that should respond to theme, add a matching override under
`.app.light-theme`. Most utility classes (spacing/flex/size) are theme-agnostic and
need no override; only **color/border/background** ones do.

## Responsive & print
`media-queries` holds Bootstrap-style breakpoints — `xs (<768)`, `sm (768–991)`,
`md (992–1199)`, `lg (≥1200)` — plus `.visible-*` / `.hidden-*` helpers and a print
block. The xs query also tweaks the character-builder header, summary, and content
width. Add responsive rules here, not inline.

## Build loop
- Garden build is configured in `project.clj` under `:garden` → stylesheet
  `orcpub.styles.core/app` → output `resources/public/css/compiled/styles.css`.
- Compile once: `lein garden once`. Auto-rebuild: `lein garden auto` (or the
  `lein-garden` plugin during dev). Figwheel watches `:css-dirs
  ["resources/public/css"]` and hot-swaps the compiled CSS in the browser.
- **Never edit the compiled `.css` directly** — it's overwritten on next build.

## Quick recipes
- *New accent-bordered card:* `[:div.p-10.b-1.b-rad-5.b-orange ...]`.
- *Right-aligned action row:* `[:div.flex.justify-cont-s-b.align-items-c ...]`.
- *New reusable spacing value 14px:* add `14` to the appropriate `px-prop` range
  in `margin-*`/`widths`, recompile.
- *One-off CSS Grid:* inline `:style {:display "grid" ...}` (no utility exists).
- *Make a style win over an `!important` utility:* drop the utility and use inline
  `:style`, or add your own `!important`.
