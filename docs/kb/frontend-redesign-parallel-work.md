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

### Is OMV's markup better or worse for a generated form?

**Better, and less fiddly — because the caller supplies data, not chrome.** That is the same contract
the field schema already has, which is not a coincidence: `checkbox-options`' own docstring says
*"This is the glue nearly every homebrew-builder menu uses; the callers in views.cljs all follow the
same shape."* OMV was written for these builders.

Its minimum per-option contract:

```clojure
{:key … :label … :selected? bool :selectable? true :on-toggle fn}   ; :card/:display optional
```

A field spec already carries everything that needs. The adapter is mechanical, written **once** in
the renderer, and **no schema changes at all**:

```clojure
(defn- omv-options [item set-prop {:keys [key options]}]
  (let [path   (if (sequential? key) key [key])
        chosen (set (get-in item path))]
    (mapv (fn [{:keys [value title]}]
            {:key value :label title :selectable? true
             :selected? (contains? chosen value)
             :on-toggle #(dispatch [set-prop path (toggle chosen value)])})
          options)))
```

So the trade is: **`option-menu`'s HTML is far more structured than anything here — banner, search
box, chips tray, N-of-M count, three layout bodies — and none of it is written by the caller.** It
replaces hand-rolled chrome rather than adding markup to write. The `.chip` / `.chip-row` / add-bar
CSS in `styles/core.clj` becomes partly redundant for the multi-select case, not extended.

Where the schema *would* grow is only optional: `:title` maps to the field's existing `:label`, and
`:wildcards` / `:slot-label` / `:collapsible?` / `:cell-fn` would be new keys that all have defaults.
Nothing becomes required.

**One real obligation:** `:menu-id` must be stable and unique — it keys that menu's search text and
active A–Z letter. Derivable from the field's key path with no author involvement, but the renderer
has to guarantee it rather than leaving it to a schema author.

**Where OMV does not reach**, and the current controls stay: `:text`, `:number`, and `:combo`'s
arbitrary free text have no OMV equivalent, and running a single `:boolean` through a full menu
panel would be absurd — the chip is right for that.

| field type | after OMV |
|---|---|
| `:enum` | `select-menu` — **simpler**, deletes the index-coercion workaround |
| `:multi-enum` | `option-menu` — same schema, better at scale, chrome for free |
| options from a subscription | `option-menu` — the blocking primitive, already built |
| `:combo` | unchanged — free text is not a menu |
| `:text` / `:number` / `:boolean` | unchanged |

### `:enum` now uses `select-menu` — done 2026-09-06

`select-menu` and its CSS are **ported verbatim** from `option_menu_views.cljs` (commit `3384d4c5`)
into `views.cljs` / `styles/core.clj`, byte-identical apart from the accent becoming
`var(--accent, …)`. Kept verbatim on purpose: **when that branch merges this is a delete, not a
reconciliation** — the OMV namespace becomes the one true copy.

**The index-coercion workaround is gone.** `:enum` was:

```clojure
{:value (str i)}  …  (:value (nth options (js/parseInt %)))   ; index in, index out
```

because a `<select>`'s value is always a string, so a keyword or int could not round-trip — the
workaround behind D32 / `dropdown-value-coercion.md`, which exists because the un-worked-around
version shipped a broken breath weapon. `select-menu` takes `[[value label] …]` and hands
`on-change` the real value:

```clojure
[:div.bf-enum {:class (when (and (some? v) (some #(nil? (:value %)) options)) "set")}
 [select-menu {:value v
               :options (mapv (fn [o] [(:value o) (opt-title o)]) options)
               :on-change #(dispatch [set-prop path %])}]]
```

The pin proves the values survive: the spell still saves `:level 3` as an integer and
`:school "abjuration"`, 39/39.

**The `set` highlight is keyed on *can be unset and is not*, not merely on having a value.** It
exists so that one weapon tag carrying a restriction stands out from six reading "Both". Keyed on
`(some? v)` it also lit Level and School — which always have a value — so two controls were
permanently orange and stole the emphasis from the tag that meant something. It now requires the
field to offer a `nil` option, which is exactly the three-state tags.

**What had to move with it**, all of it representation-following rather than behaviour:

- `controlFor` matches `.select-menu-btn` as well as `input/select/textarea`, so "field present"
  keeps meaning the same thing.
- New `lib.js` helpers `pickOption` (open the popover, click the option) and `optionsOf` (what the
  button shows, and what the menu offers). Four scripts drove enums by setting a `<select>`'s value.
- **The three-state invariant is now stated better.** It used to be `selectedIndex === 0`, guarding
  the fact that a select with no matching value silently shows its first option. It is now read off
  what the button *displays* and what the menu *offers* — closer to what a user sees, and the
  original failure mode cannot occur in a popover at all.
- `mockup-parity.js` compares the mockup's `.tag select` against `.tag .select-menu-btn` on shape
  (type size, padding) and no longer on `minWidth`: a content-sized button and a fixed-width select
  were never going to share that, and pretending otherwise would be a false red.
- Four separate control-count metrics had to learn `.select-menu-btn`. **That is the fourth time a
  metric here missed a representation change**; the pattern is now explicit enough to expect.

### Section cards — `option_menu_views/card`, done 2026-09-06

A suggested spell-builder page (an OMV-centric mock) builds the whole form out of **section cards**:
a flat elevated panel per section with an amber accent tab beside the title. OMV already ships that
as `card` → `.opt-section` / `.opt-section-head` / `.opt-section-accent` / `.opt-section-title`, so
the CSS is **ported verbatim** (accent as a var, as before) and a titled `:section` renders through
it. No schema change: `:section "Components"` already existed.

The untitled lead group stays plain. In the mock the identity and stat fields sit above the cards,
and wrapping those too would make the form a stack of boxes.

**The chrome was deliberately not copied** — sticky toolbar, header band with the name as a display
input, summary line, "Save target" badge, info popovers, the grid/pills layout switch. Those are
page-level design decisions that belong with the redesign branch, not smuggled in through a form
framework.

It costs height: the spell form went 1242px → **1405px** desktop, 1742px → **1899px** at 390px.
That is the cards' padding and margins, and it is the trade the mock proposes on purpose.

**A verbatim port was the wrong instinct for the card's own colour and spacing.** Measured after
landing it:

| | the mock | this page |
|---|---|---|
| page background | `#161d27` (22,29,39) | **`rgb(8,10,13)`** — near-black |
| card `#1b232f` (27,35,47) against it | +6 — a whisper of lift | **+19 — three times as strong** |

The literal was chosen against a page fourteen points lighter, so importing it produced chunky pale
blocks rather than a grouping. The card is now `rgba(255,255,255,0.035)` — a **translucent lift**,
which lands the same on either page and survives the redesign's light/dark work instead of needing a
second literal. That is a deliberate deviation from verbatim, for a reason the measurement gives.

Two spacing faults, both found by measuring rather than looking:

- **The first card butted straight into the fields above it — a gap of 0px.** The card carried
  `margin-bottom`, so nothing supplied space *before* the first one. It carries `margin-top` now, so
  every card gets the same 18px run-in including the first.
- **A wrapping title stranded its accent bar on a line of its own** at 390px, on "Add This Spell to
  Which Class Spell Lists?". `.opt-section-head` is `flex-wrap` (the mock puts a count pill beside
  the title), so the title broke below the accent instead of wrapping within its own box. The title
  is `flex: 1 1 auto; min-width: 0` now.

Mobile after the pass: **no horizontal overflow**, cards inset properly, Level|School still paired
and the flags still side by side.

Two process notes from doing it:

- **`lein garden` failed and I read past it.** The command was piped through `tail -1`, so
  "Subprocess failed (exit code: 1)" scrolled by while `fig:build` and the whole e2e suite ran green
  against **stale CSS** — the cards simply did not render and every test still passed. Garden's exit
  code needs checking, or a CSS change can appear to work while never having compiled.
- The failure itself: `[:.bf-section:not(.opt-section)]` is not valid Clojure — the reader takes
  `.opt-section` inside parens for a member expression. A `:not()` selector needs
  `garden.selectors`, or restructuring so it is not needed (which is what happened).

**Not adopted yet:** `option-menu` for `:multi-enum` and for options-from-a-subscription. Chips are
adequate for three classes, and the encounter builder needs the vector-rows work first.

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
