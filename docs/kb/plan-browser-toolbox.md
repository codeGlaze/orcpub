# Plan: a browser-test toolbox

**Status: plan 1.3, revised 2026-11-27 after a third second opinion (below). Planned 2026-11-26 (owner: "exactly what I've been pushing for"). Branch
`test/browser-toolbox`, cut from `integration` at `b14a515e4`. Steps 1-5 and 5c items 1-5 built and pushed (to
`46e96032f`, 2026-11-27); step 6 next, then 7-9 or a follow-on branch. The whole toolbox is one branch and one PR, each step a commit (owner, 2026-11-27).**

## The problem, measured

- About 120 browser suites in three folders (folder entries, helpers included: `test/browser/` 67, `test/e2e/` 38,
  `scripts/e2e/` 23).
- Three helper libraries that grew apart:
  - `test/e2e/lib.js`: 26 exports, builder-heavy (`pick`, `setClass`, `clickTab`, `dbAt`,
    `optionsOf`).
  - `scripts/e2e/lib.js`: 11 exports, sharing and login (`login`, `watchShares`, `copyLink`).
  - `test/browser/lib/`: Chrome lookup, the orcbrew import, the overlay preload.
  - `login`, `BASE` and `checker` each exist twice.
- No test hooks in the app: no `data-test` attribute anywhere in `src/` or `web/`.
- Each suite types its own selectors, so the same ones repeat across files. Counted on 2026-11-26:

  | Selector | Times typed |
  |---|---|
  | `.builder-tab` | 25 |
  | `button.form-button` | 18 |
  | `.whats-new-panel` | 16 |
  | `#app select.builder-option-dropdown.flex-grow-1` | 12 |
  | `.inv-combo-row` | 10 |
  | `#cookie-btn` | 9 |

- **The cost the owner sees:** after a visual change, an agent finds the new control by trial runs:
  edit a selector, run the suite (about 40 s of JVM start plus the boot), read a timeout, guess
  again. Nothing tells it where the control went.
- **Traps the harness already holds:**
  - Production bundles compile away `dbAt` (it needs the `// Needs: dev bundle` header).
  - The cache-stamped bundle URL defeats globs (`APP_BUNDLE`, [page-load-budget.md](page-load-budget.md)).
  - Suites that build their own base address: both helpers honour `E2E_BASE`, but 69 suite files
    name `localhost:8890`, most as a fallback when they build their own.
  - Overlays (cookie bar, What's New) cover controls.
  - The phone layout needs the user agent as well as the viewport.

## What it is

### 1. One place map, the evolving model of the app

`test/browser/places/` holds one module per screen: `builder.js`, `sheet.js`, `data-view.js`,
`my-content.js`, `auth.js`, `header.js`.

- **Each entry:** a name, the route that shows it, and a locator function. For example:
  `sheet.restButton`, `builder.tab('Class')`, `builder.option('Fighting Style')`,
  `dataView.row('Skills')`, `header.menu('Account')`.
- **Accessible names are real work, done first.** Many controls have none today: `.builder-tab` is a
  clickable element with no role, the class dropdown has no label (`test/e2e/lib.js` `controlFor`
  walks ancestors to find a visual heading for that reason), and the header's `header-tab` is a
  clickable `div` whose title disappears on phones (`views.cljs` ~206-231). Each place gets a role, a
  name or a label association in the app, so players with screen readers gain too.
- **The smallest correct change per control:**
  - builder tabs and header tabs: real `<button>` elements, keeping their classes and click
    handlers, with the browser's default button style reset so nothing looks different;
  - header tabs on phones, where the title is hidden: an `aria-label`;
  - flyout menus: menu-button semantics (`aria-haspopup`, `aria-expanded`);
  - the class dropdown: stays a native `<select>`, tied to its visible heading by `id`/`for`.
  Keyboard use improves; nothing else changes for players.
- **How locators are written:** by role and accessible name first (`getByRole('button',
  {name: 'Long Rest'})`). That form survives restyling and doubles as an accessibility check.
  A `data-part` hook (Owner decisions, below) is never a way around a missing role or name. It is only for hooks that have
  no meaning to a person, such as a list's container. Each one names its reason beside the map entry.
- **How suites use it:** suites call names, never selectors. When a screen changes, its map entry
  changes in the same PR, and every suite follows.

### 2. The census: a quick check of the map, beside journey assertions

- Journey assertions stay the main guard. A suite's unconditional interaction already fails when a
  control moves, and `probe-baseline.json` catches checks that went quietly missing. The census only
  diagnoses the shared map, and only covers the entries that exist.
- `place_census_e2e.js` visits every place in the state its entry declares (signed in or out, which
  fixture, the viewport, which menu is open). It asserts the declared count, and that each control
  can be hit: its centre is the element `elementFromPoint` returns, not an overlay
  ([test/browser/README.md](../../test/browser/README.md) "Two ways a probe lies").
- It runs first in `run-all.sh` and in the PR browser workflow, and takes seconds.
- A redesign then fails one clear line, `sheet.restButton: 0 matches on /dnd/5e/characters/:id`,
  instead of a timeout deep in an unrelated suite. The agent fixes the map, not twenty suites.
- With `--describe`, the census prints the map: every place, its route and how it is located. An
  agent reads that instead of probing the DOM.

### 3. Recipes and seeded fixtures

- **Recipes** for the journeys suites repeat by hand:
  - `openCharacter(page, fixture)`
  - `goToBuilder(page, {class, level})`
  - `importPack(page, name)`
  - `signIn(page, user)`
  - `onPhone(context)`, which sets the user agent and the viewport together
- **Fixtures** in `test/browser/fixtures/`.
  - Characters are seeded through the routes that exist: `POST /login`, then an authenticated
    `POST /dnd/5e/characters` with no `:db/id`, which creates one. `run.sh`'s boot already seeds
    users.
  - Homebrew packs live in the browser's local storage, not on the server. They are written there
    by `addInitScript` before the app boots, which needs no server route. `scripts/e2e/lib.js`
    already does this: it writes the `plugins` key as EDN text, not JSON, and only when the key is
    absent. Check the app's first-load reader on the first fixture.
  - Either way, nothing is built through the UI first. A suite that tests the Rest
  button should not first spend 20 s building a Fighter.

### 4. One new library; the old two stay as they are

- `test/browser/lib/toolbox.js` holds the map, the recipes and the shared basics: `BASE` from
  `E2E_BASE`, `APP_BUNDLE`, one `checker`, overlay suppression and the Chrome lookup.
- `test/e2e/lib.js` and `scripts/e2e/lib.js` are not touched and get no shims. Their `login` and
  `checker` contracts differ (arguments and return values), and adapting both adds risk for no gain.
  New suites use the toolbox; an old suite moves to it when it is next edited.

### 5. Bootstrap for agents

- A KB page, `browser-toolbox.md`, with the map's shape, the recipes, how to add a place, and
  the traps listed above.
- `agent-setup.sh` names that page in its KB pointer.
- `test/browser/README.md` points at it on the code branch.

## Order of work

One branch, one PR; each step is one or more commits on `test/browser-toolbox`. The PR opens when
the owner says.

1. Done: `toolbox.js` with the basics, plus a small table test for its `checker`.
2. Done: accessible names for the builder tabs and the sheet's section tabs.
3. Done: map entries for those controls, and `weapons_table_e2e.js` moved onto them (it changed
   98 selector lines since 2026-08-01, the most of any sheet suite). Payoff shown: with the icon
   changed, the old suite timed out after 75 s and the new one passed; with the label renamed,
   the new one named the missing tab.
4. Third-review fixes: escape names before building a `RegExp`; a `must()` beside `attempt()`
   so failed setup ends the journey; no swallowed waits (`.catch(() => {})` becomes a check);
   `quietOverlays` reports its errors; the toolbox collects console errors and failed requests
   and prints them with a failure.
   `run.sh` also learns `Needs: ledger`: the Data View suites need `ledger.js` (`lein cljs:ledger`),
   which nothing built, so on a fresh checkout they timed out on an empty page (found on the DMV
   pre-merge, 2026-11-27). It builds the script when missing or stale, and fails with a reason when
   it cannot.
4b. Fourth review's fixes (checked against `b937cb159`, 2026-11-27):
   - diagnostics count: a console error, page error or same-origin failed request is a failed
     check, not only printed when another check failed; cross-origin transport failures are not
     recorded;
   - `attempt` and `must` take a function, not a promise, so an error thrown while building the
     action is recorded too;
   - steps later checks depend on use `must` (`eightRows`, the Javelin setup, the phone journey's
     first Weapons section);
   - queries that turn a failure into a value (`.catch(() => null)`) keep the error text in the
     check's detail;
   - `run.sh` fails when an extern listed in `ledger.cljs.edn` is missing.
   Not taken: its "toolbox.js does not export `must`" came from a cached older copy; `find -newer`
   missing an edit with an older timestamp is how every timestamp build behaves.
5. The full tabs pattern: roving `tabindex`, Left/Right/Home/End, `aria-controls` and
   `role=tabpanel`.
5b. The tab and switch helpers move to `orcpub.components` (`src/cljc/orcpub/components.cljc`),
   and `tab-attrs` reuses its `press-to-click` instead of a second Space/Enter handler.
5c. Shared controls, merged here because the place map locates these controls (owner, 2026-11-27:
   "on the toolbox branch bc it might have knock-on effects for the toolbox"). The audit and its
   numbers: [monolith-decomposition-plan.md](monolith-decomposition-plan.md), "Shared controls and
   duplicates". One commit per item, checked before the next; each item's browser check is the
   suites that touch that control.
   1. One checkbox: `components/labeled-checkbox` (keyboard use) replaces `builder_fields`'
      mouse-only copy at its 17 callers. Players gain keyboard use of those checkboxes.
   2. One `checkbox-grid` plus a data table replaces the 19 checkbox-grid functions in `views.cljs`
      (about 250 lines).
   3. One caret: `comps/caret` (or an `expand-button` option) for the about 30 hand-written caret
      icons; the chevron and angle variants become options.
   4. The 13 `*-input-field` wrappers become one function plus an event table, moved with
      `builder-input-field` and `builder-field-cue` to `views/builder_fields.cljs`.
   5. Small merges: `event-value` in components, used at about 50 sites; one Space/Enter helper
      and one `on-enter` helper for the inline copies; the two What's New pills become one;
      `svg-icon-2` uses `themed-icon`; the dead `default-input-style` and `validation-messages` go.
   6. Later, after a closer look: one dropdown (`builder_fields/dropdown` with options) and a shared
      modal.
6. Place descriptors: each entry carries its locator, route, preconditions and expected count.
   The census checks the mapped places and says which kind of failure it saw: missing, wrong count,
   hidden, covered, or setup failed. It is seen failing on a scratch rename.
7. Recipes and two fixtures: a level-3 Fighter (API), and a homebrew pack (local storage).
8. More places: header, Data View, My Content, auth, reference pages. Move the suites with the
   most selector churn, timing each before and after.
9. KB page, README and the `agent-setup.sh` pointer. The pointer change needs the owner's yes
   for that push.

## What it is not

- Not a new test runner: `run.sh`, `run-all.sh` and the `Needs:` headers stay.
- Not screenshots or visual diffing.
- Not a page-object class hierarchy: plain functions and data, one module per screen.

## Second opinion, 2026-11-26

Codex reviewed plan 1.0. Accepted:
- make step 1 compatibility-safe (checked: the `check` argument orders and `login` shapes do differ);
- accessible names as prerequisite work, with a remediation policy;
- the census as a supplement to journey assertions, with preconditions and a hit test;
- the routes that can seed fixtures, and local storage for homebrew packs;
- prove the map on builder and sheet before building six modules.
Codex reviewed plan 1.1. Accepted:
- no shims: a new library, with the old two left alone;
- the per-control accessible-name changes above;
- a smaller first PR.

Checked and not taken:
- its "stale `data-test` text" came from a cached older copy; the current plan has none;
- it suggested `data-orcpub-part` against collisions, but nothing in `src/` or `web/` uses
  `data-part`, so the owner's plain-looking name stays unless the owner prefers otherwise.

It could not read git history, so the churn counts above are ours.

Codex reviewed the built steps 1-3 (2026-11-27). Checked against the code and accepted:
- `weapons.row` builds a `RegExp` from a name without escaping it;
- `attempt()` lets a journey continue after its setup failed;
- four waits end in `.catch(() => {})`, and `quietOverlays`' init script has an empty catch;
- a failure prints no console errors or failed requests;
- `role=tab` without roving focus, arrow keys and `tabpanel` is part of the pattern only;
- places should be descriptors (route, preconditions, count) before more suites move onto them.
Not taken: its "smaller first PR". Steps go in commits, not in a series of PRs (owner, 2026-11-27).

Corrected in 1.0: the "hard-wired :8890" trap is in suites that build their own base, not in the helpers.

## Second opinion, 2026-11-27 (5c items 2-5 and the step 6 design)

Reviewed at `46e96032f` (source only). Checked against the code and taken (`5fe81f02a`, `d3b8597ba`):
- the kits suite's `topLevelId` missed EDN character literals (`\{`, `\}`, `\"`): fixed, 7/7 cases;
- the feat checks proved the cell builders, not the call sites: added map-shaped choices, a mixed
  grid (choices beside flags) and the race builder's nested `[:profs :tool]`, each seen failing.
  Our own trap on the way: a grep for `option-tool-proficiency` also matched `-or-expertise`; boxes are
  now found inside their titled section (`places` `sectionCheckbox`).
Its other Part A points found no regression (paths, event table, switch attributes, splash icons).

Decided (owner, 2026-11-27): no change. `press-to-click` (behind nearly every keyboard control) does not check
`e.repeat`, so a held Space or Enter clicks repeatedly. Native buttons repeat on a held Enter too,
and the refactor does not justify changing every such control.

**Step 6 design, adopted as the starting point:**
- A place is plain data in its `places/` module: `route` (with `:id`-style params a fixture fills),
  `locator` (role and name first; CSS or `data-part` only where the plan allows), `preconditions`
  (`auth`, `fixture`, `viewport`, `openMenu`), `expectedCount` (usually 1). No setup callbacks in a
  descriptor; setup lives in the toolbox recipes.
- One line per failing place, the kinds exclusive and checked in order: setup failed, missing, wrong
  count, hidden (zero size counts), covered (the centre's `elementFromPoint` is neither the element
  nor inside it; name the cover). For example
  `FAIL sheet.restButton: covered, centre hit by .cookie-banner on /pages/dnd/5e/characters/:id`.
- Fast: one browser; group places by route and preconditions; one context per auth and viewport;
  one navigation per route and state; an app-ready wait, not network idle or sleeps; immediate counts.
  Check phone only where the layout differs.
- Not: behaviour, persistence, every configuration, screenshots, discovering controls, page objects,
  a navigation per place. Journey assertions stay the main guard.

## Owner decisions

- 2026-11-27: `test/browser-toolbox` ends at step 6 (the census). Steps 7-9 (recipes and fixtures, more
  places, the KB page and pointer) go to a follow-on branch, named and approved when it starts, so the
  picture-link hotfix is not held behind more test tooling.
- 2026-11-27: a held Space or Enter keeps repeating, as native buttons do; `press-to-click` is unchanged.

- 2026-11-26: names first. Accessible names do most of the work and help players with screen
  readers, keyboard and voice control. The few remaining hooks are shipped in production under a
  neutral attribute, not `data-test*`, through one helper. The proposal is `data-part` (Ark UI and
  Zag use it) via `(part :kit-grid)`. `data-slot` (shadcn/ui) is the alternative; `data-role` is
  avoided because it reads like ARIA's `role`. The helper's docstring says tests and styles find
  the element by it, so nobody tidies it away.
