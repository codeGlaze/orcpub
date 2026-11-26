# Plan: a browser-test toolbox

**Status: plan 1.1, revised 2026-11-26 after a second opinion (below). Planned 2026-11-26 (owner: "exactly what I've been pushing for"). Branch
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
- **How locators are written:** by role and accessible name first (`getByRole('button',
  {name: 'Long Rest'})`). That form survives restyling and doubles as an accessibility check.
  A `data-test` attribute is never a way around a missing role or name. It is only for hooks that have
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
    by `addInitScript` before the app boots, which needs no server route; check this on the first
    fixture.
  - Either way, nothing is built through the UI first. A suite that tests the Rest
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

1. **Library merge, compatibility-safe.** Add `lib/index.js` and keep the old exports through shims
   that adapt the incompatible APIs:
   - `checker`'s `check` takes `(name, ok, detail)` in `test/e2e/lib.js` but `(ok, label, detail)`
     in `scripts/e2e/lib.js`;
   - `login(browser)` makes and returns a page in one; `login(page, user, pass)` fills a page in the
     other.
   The shared library gives them distinct names. Each shim is run against its existing callers
   before the commit (one run of `run-all.sh` before and after).
2. **Prove it on one real failure.** Map entries for the builder and the sheet only, with the
   accessible names they need. Migrate one suite that has needed trial runs after a UI change.
   Show the payoff: rename a label in a scratch edit and compare how fast each version points at it.
3. Recipes and two fixtures: a level-3 Fighter (API), and a homebrew pack (local storage).
4. The census for the mapped places, seen failing on the same scratch rename.
5. Expand only after step 2 shows the payoff: the header, Data View, My Content, auth, reference
   pages. Migrate the suites that type the most selectors, timing each before and after.
6. KB page, README and the `agent-setup.sh` pointer. The pointer change needs the owner's yes
   for that push.

After the branch, old suites move when they are next touched; there is no big-bang migration.

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
Corrected: the "hard-wired :8890" trap is in suites that build their own base, not in the helpers.

## Open for the owner

- Should `data-test` attributes ship in the production bundle (a few bytes each) or be stripped
  there? The proposal is to ship them: the production suites need them too.
