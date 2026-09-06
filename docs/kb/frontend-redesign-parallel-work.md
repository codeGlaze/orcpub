# The parallel front-end redesign — `port/redesign-on-refactor`

**Why this file exists.** Two generations of builder-form work styled forms from scratch — colours,
spacing, chips, a grid — without knowing that a **design-system branch already existed**. The KB
had no record of it. Nothing here is new work; it is a pointer so the next person checks before
inventing a palette.

## What is on the branch (last commit 2026-07-15)

`origin/port/redesign-on-refactor`, ~3,300 insertions across 9 files. A phased header/theme system:

| | |
|---|---|
| `src/cljs/orcpub/dnd/e5/themes.cljs` | **the theme registry — themes are DATA** |
| `src/cljs/orcpub/dnd/e5/option_menu_views.cljs` (610 lines) | searchable option picker / popover menus |
| `src/cljs/orcpub/dnd/e5/option_grouping.cljs` | grouping for those menus |
| `src/clj/orcpub/styles/core.clj` | +1,436 lines — header shapes, page-environment layers, theme switcher |

Its own docstring states the principle, which is the same one this branch keeps arriving at from
the other direction:

> Theme registry — themes are DATA, mechanics are central. […] Adding a theme is ONE entry here —
> no new CSS/mechanics until a theme needs a bespoke asset that doesn't fit the token model.

Three themes ship: `:classic`, `:dwarven`, `:arcane`, each a `{:tokens … :policy …}` bundle.
`:policy` decides which controls the switcher exposes per theme — `:locked | :default | :choosable`.

It is stacked on `refactor/garden-inline-styles` and `redesign/growable-option-menus`, and a
light/dark appearance row is folded into the palette switcher.

## The part that directly affects the builder forms

**`:accent` is a per-theme token, wired to a `--accent` CSS var:**

| theme | accent |
|---|---|
| classic | `#f0a100` |
| dwarven | `#f0a100` |
| arcane | **`#7c8cff`** |

The redesign's own CSS consumes it as `var(--accent, #f0a100)`.

**Every accent colour in the builder-form CSS now does the same** (`.chip`, `.chip-toggle.chip-on`,
`.effect-row-header span`, `.tag select.set`). Before that they were the literal `#f0a100`, which
would have rendered every builder chip and effect-row header the wrong colour under Arcane — and
`styles/core.clj` line 8 has had `(def orange "#f0a100")` the whole time, so even the literal was
avoidable. Rendering is unchanged today: the fallback is the same value.

## What is NOT aligned yet, and is a real follow-up

The builder CSS assumes a **dark surface**: `rgba(255,255,255,0.14)` hairlines, `rgba(255,255,255,0.55)`
muted labels, `rgba(240,161,0,0.10)` tints. The redesign folds light/dark into the palette but so far
only ships the *switcher UI* — there is no `--surface` / `--line` / `--muted` var to consume. When
that lands, those literals need the same treatment the accent just got. **Do not invent those tokens
here**; take them from the redesign branch when it defines them.

## How the OMV elements meet the generated builder — the actual question

`option_menu_views.cljs` ("OMV") is a **shared view layer for growable multi-select menus**. Two of
its components land directly on field types the builder framework already has.

### `select-menu` replaces `:enum`, and removes a documented bug class

```clojure
[omv/select-menu {:value v :options [[value label] …] :on-change f :placeholder "…"}]
```

A button + popover, alignment-controllable, dismissing on outside click — because a native
`<select>`'s popup is OS-positioned and cannot be styled.

**The important part is the data path.** `render-builder-field`'s `:enum` renders the app's
`dropdown` with **index-based option values** — `{:value (str i)}` — purely to survive the fact that
a `<select>`'s value is always a string, so a keyword or int cannot round-trip. That workaround is
`dropdown-value-coercion.md` / D32, and it exists because the un-worked-around version shipped a
broken breath weapon. `select-menu` hands `on-change` **the real value**. Adopting it deletes the
index dance and the class of bug with it.

### `option-menu` covers `:multi-enum` at scale, and one blocking primitive

`option-menu` is a full multi-select panel: search box, selected-chips tray, N-of-M count + Clear,
wildcards ("choose any N"), collapsible section header, and three layouts (`:grid` / `:pills` /
`:az`) chosen by a **global, persisted** `layout-toggle` — flip it once and every menu re-renders.

- `:multi-enum` (currently chips) is fine for three classes and wrong for eighty monsters.
  `option-menu` is the version that scales, and callers supply options already normalized plus an
  optional `cell-fn`, so the chrome is shared while per-option rendering stays family-specific.
- **"Options from a subscription" — listed in `builder-conversion-gallery.md` as the blocking
  primitive for the encounter builder — is what `option-menu` is for.** Encounter's creature rows
  need a live monster/character list; that is the same shape as the menus OMV already renders.

### `:combo` survives, narrowly

`:combo` (an `<input list>`: suggestions plus arbitrary free text) is not the same control. Casting
time has 13 canonical values *and real outliers* — an author must be able to type
"3 rounds and a wink". `option-menu` is a closed list with search. Keep `:combo` for
**short canonical list + free text**; use `option-menu` for **long closed list**.

### What would have to give

| | |
|---|---|
| **Layout is app-wide state** | The menu layout is a persisted global, not a per-field choice. A builder field that delegates to `option-menu` must not hard-code a layout — the user owns it. |
| **Two chip vocabularies** | OMV has `opt-menu-chip` for its selected tray; the builder has `.chip` / `.chip-toggle`. No CSS collision (checked), but two chip looks in one app is a design decision someone should make deliberately. |
| **`.chip` and `.tag` are unnamespaced** | They do not collide with the redesign today. They are exactly the kind of generic name that bit once already (`.field`), in a 3,000-line utility stylesheet. Worth prefixing when something forces the churn. |
| **Accent** | Already handled — the builder reads `var(--accent, #f0a100)`. Surfaces are not; see above. |

### The strategic point

The redesign rewrites **~2,400 lines of `views.cljs`**, which is where every builder lives — so the
two branches collide most in exactly the file this work is shrinking. A converted builder is ~10
lines where a bespoke one is 100–270. **Converting builders makes that merge smaller, not bigger**,
and the conversions that reuse existing vocabulary (per §5b of `builder-form-schemas.md`) are the
cheapest way to shrink it.

## Overlap worth reconciling before either branch merges

- **`option_menu_views.cljs` (610 lines) is a searchable option picker.** The builder framework has
  `:enum` (a `<select>`) and `:combo` (an `<input list>`). For a long option list — monsters,
  spells, creatures in an encounter — that picker is almost certainly the better control, and
  "options from a subscription" is listed in `builder-conversion-gallery.md` as a blocking primitive
  for the encounter builder. **Check that picker before building a third option control.**
- `feat/option-picker` (active, 2026-09-06) appears to be the continuation of the same idea.
- The redesign rewrites ~2,400 lines of `views.cljs`, which is where every builder lives. A merge
  will be substantial in both directions; the smaller the builder-side surface, the better.

## How this was missed

The KB audit (2026-09-05) added the rule *"before designing anything, `git log -S` the identifier and
grep the KB for the branch name"*. It was applied to code identifiers and not to design work: no one
listed branches before choosing colours and spacing. `git for-each-ref --sort=-committerdate
refs/remotes/origin` takes a second and would have surfaced this immediately.
