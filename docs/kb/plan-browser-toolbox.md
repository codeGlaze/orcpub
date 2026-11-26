# Plan: a browser-test toolbox

**Status: planned 2026-11-26 (owner: "exactly what I've been pushing for"). Branch
`test/browser-toolbox`, cut from `integration` at `b14a515e4`. Nothing built yet.**

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
  - Suites hard-wired to :8890 instead of `E2E_BASE`.
  - Overlays (cookie bar, What's New) cover controls.
  - The phone layout needs the user agent as well as the viewport.

## What it is

### 1. One place map, the evolving model of the app

`test/browser/places/` holds one module per screen: `builder.js`, `sheet.js`, `data-view.js`,
`my-content.js`, `auth.js`, `header.js`.

- **Each entry:** a name, the route that shows it, and a locator function. For example:
  `sheet.restButton`, `builder.tab('Class')`, `builder.option('Fighting Style')`,
  `dataView.row('Skills')`, `header.menu('Account')`.
- **How locators are written:** by role and accessible name first (`getByRole('button',
  {name: 'Long Rest'})`). That form survives restyling and doubles as an accessibility check.
  A `data-test` attribute goes on a control only where it has no accessible name. It is added in
  the same commit as the map entry, and a census row names why.
- **How suites use it:** suites call names, never selectors. When a screen changes, its map entry
  changes in the same PR, and every suite follows.

### 2. The census: the trigger that keeps the map true

- `place_census_e2e.js` visits every place and asserts each named locator finds exactly one
  visible element (or the count its entry declares).
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
- **Fixtures** in `test/browser/fixtures/`: named characters and homebrew packs, seeded through
  the API on the in-memory server, never built through the UI first. A suite that tests the Rest
  button should not first spend 20 s building a Fighter.

### 4. One library

- `test/browser/lib/index.js` re-exports the map, the recipes and the shared basics: `BASE` from
  `E2E_BASE`, `APP_BUNDLE`, `checker`, the overlay suppression and the Chrome lookup.
- `test/e2e/lib.js` and `scripts/e2e/lib.js` become thin shims over it, so old suites keep
  working while they move.

### 5. Bootstrap for agents

- A KB page, `browser-toolbox.md`, with the map's shape, the recipes, how to add a place, and
  the traps listed above.
- `agent-setup.sh` names that page in its KB pointer.
- `test/browser/README.md` points at it on the code branch.

## Order of work, each a commit seen working before the next

1. Library merge: `lib/index.js` plus the two shims, with no suite changed. All suites still pass
   (one run of `run-all.sh`, before and after).
2. Map and census for the three busiest screens: builder, sheet, header. The census is seen
   failing by renaming one control's label in a scratch edit.
3. Recipes and two fixtures: a level-3 Fighter, and a homebrew pack.
4. Migrate the five suites that type the most selectors. Measure each suite's wall time before
   and after.
5. Remaining screens' places: Data View, My Content, auth, reference pages.
6. KB page, README and the `agent-setup.sh` pointer. The pointer change needs the owner's yes
   for that push.

After the branch, old suites move when they are next touched; there is no big-bang migration.

## What it is not

- Not a new test runner: `run.sh`, `run-all.sh` and the `Needs:` headers stay.
- Not screenshots or visual diffing.
- Not a page-object class hierarchy: plain functions and data, one module per screen.

## Open for the owner

- Should `data-test` attributes ship in the production bundle (a few bytes each) or be stripped
  there? The proposal is to ship them: the production suites need them too.
