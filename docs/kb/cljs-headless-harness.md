# Headless ClojureScript test harness (how to run cljs tests in a container)

**Why:** the cljs suite (subs, events, orcbrew-validation, content-reconciliation, …) only
runs under a JS runtime. This runs it **headless** so cljs changes are verifiable without the
full webapp (no backend, no Datomic — those aren't needed; HTTP calls just
`ERR_CONNECTION_REFUSED` harmlessly).

> **Updated 2026-09-16.** The runner is committed now — `scripts/test/run-cljs-tests.js` — and
> most of what follows describes how it was built rather than how to use it. **To run the suite:**
>
> ```bash
> lein fig:test && node scripts/test/run-cljs-tests.js
> ```
>
> That is what `docs/CONTRIBUTING.md` means by "the node runner". It serves its own HTML, runs
> **both** passes (see below), and exits non-zero on any failure. Prefer it over
> `test/e2e/cljs-harness.js`, which predates it, needs a hand-made `target/test/runner-all.html`,
> and always exits 0.

### Why it is not in CI — measured 2026-09-16, because "deferred" was all this page said

Two separate items had been bundled into one deferral:

- **The cljs unit suite is cheap.** `lein fig:test` 44s warm + the node runner 13s = **~1 minute**
  for 426 tests. Its only blocker is that the workflow has **no JS runtime at all** — no
  `setup-node`, no npm, no Playwright, no browser. Four missing lines, not a cost problem.
- **The full-app E2E is the expensive one** — 29 scripts in `test/e2e/`, needing a built app, a
  running `lein e2e-server` and a database. That is a different order of setup.

CI's "ClojureScript" step is `lein fig:build` — a **compile**, not a run. Worth knowing why that
is weak cover: a missing var in CLJS is a *warning*, not an error, so that step goes green on code
that breaks at runtime.

**The harness is built in `/tmp` + the gitignored `target/` — both ephemeral, so rebuild
from this recipe.**

## Build it

```bash
# 1. Leiningen (no lein/.m2 in a fresh container)
mkdir -p ~/bin && curl -sS -o ~/bin/lein https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
chmod +x ~/bin/lein && export PATH="$HOME/bin:$PATH" && lein version   # self-installs
lein deps                                                              # fetch project deps

# 2. Headless browser (a real browser is needed — app uses js/window, localStorage, DOM)
mkdir -p /tmp/pw && cd /tmp/pw && npm init -y && npm install playwright && npx playwright install chromium

# 3. Compile the cljs test build (→ target/test/js/)
cd <repo> && lein fig:test
```

## Two ways to run (they differ — pick deliberately)

**A) Clean per-test reporter (use for triage — gives `expected:`/`actual:`):**
Runs `orcpub.test-runner/-main` (cljs.test → console). It runs only the namespaces listed
in `test/cljs/orcpub/test_runner.cljs`. To check a specific failing ns (e.g.
`import-validation-test`), **temporarily** add it to that `-main`, recompile, run, then revert.
- HTML (`target/test/runner.html`): `<body><script src="js/test.js"></script></body>`

**B) Full suite (all test nss, for totals/regression):**
Loads figwheel's auto-test runner. Reports to the DOM (the body lists ALL tests incl. passing
— do NOT mistake that list for failures; trust the totals + the clean run for per-test).
- HTML (`target/test/runner-all.html`):
  `<body><div id="app-auto-testing"></div><script src="js/test-auto-testing.js"></script></body>`
  (the `app-auto-testing` div is required or it throws.)

**Use `scripts/test/run-cljs-tests.js`.** It serves its own HTML containing *both* mains, so it
runs A and B in one pass, keeps every summary, requires each to be clean, and **exits non-zero**.
Nothing to hand-create.

Consequence worth knowing, because it is easy to get wrong: pass B loads **every test namespace the
build loaded**, so adding a `:require` to `test_runner.cljs` is enough for a new test to run and to
fail the build. The `-main` list only decides what pass A reports per-test. (This page previously
implied a namespace missing from `-main` is skipped entirely. It is not.)

**Older driver — `test/e2e/cljs-harness.js`** (`node test/e2e/cljs-harness.js` after
`lein fig:test`; picks Chromium out of `PLAYWRIGHT_BROWSERS_PATH`, serves `target/test/`, runs
mode **B** only, needs `target/test/runner-all.html` to exist, and **always exits 0**). Kept
because it is described here; reach for the committed runner instead. Described for when it needs
changing: a ~15-line `http` static server rooted at
`target/test/`, then Playwright Chromium navigates to the HTML, captures `console` +
`pageerror`, waits for `/Ran \d+ tests/`, and prints the console + body. Grep the output for
`Ran .* tests`, `FAIL in`, `ERROR in`.

## Known-good baselines

- **2026-09-16: 426 tests / 1905 assertions, 0 failures, 0 errors** (`scripts/test/run-cljs-tests.js`).
- 2026-09-12: 370 tests / 1742 assertions, 0 failures, 0 errors.

(The earlier baseline on this page, "≈150 tests, 10 failures, 2 errors", was measured while the
harness was mis-serving the build. See below — the failures were not real.)

### Two things had the suite reporting nonsense, and neither was a test

**The harness served JS without a charset.** A classic `<script>` with no `charset` on its
Content-Type is decoded as windows-1252, so `orcpub/common.js`'s `#"[^a-z0-9À-ɏ]+"` arrived as
`/[^a-z0-9Ã€-É]+/` — *"Invalid regular expression: Range out of order in character class"*. The
whole `orcpub.common` namespace then failed to parse and every test touching it threw
`Cannot read properties of undefined`: **271 distinct errors, no summary line, nothing wrong with
the code.** `cljs-harness.js` now sends `; charset=utf-8`. If this page ever reports a mass of
undefined-namespace errors again, check the Content-Type before believing any of them.

**A JVM-only test in `test/cljc` stopped the test build compiling at all.**
`builder_class_names_test.cljc` reads files with `clojure.java.io`; the cljs build compiles
everything under `test/cljc`, so it failed with *"No such namespace: clojure.java.io"* and produced
no JS. Moved to `test/clj`. A test that reaches for the filesystem belongs there, not in `cljc`.

## Gotchas worth remembering
- **JVM-isms bite only here.** `(int char)` = code point on JVM, but `(int "é")` = 0 in cljs
  (no Character type; strings seq into 1-char strings). Use `(.charCodeAt % 0)`. This class
  of bug is invisible to source review + `lein test`. (Was the real `count-non-ascii` bug.)
- The auto-test DOM body lists passing tests too — only the clean reporter (A) gives
  authoritative per-test pass/fail.
- No backend needed; connection-refused logs are expected and harmless.

## Full-app headless E2E — render and drive the REAL app UI (not the test build)

The recipe above runs the cljs **test** build (test namespaces). To verify the **actual app UI**
(e.g. a builder form renders and is interactable), drive the **app** build headlessly. **Verified
feasible in-container** — the SPA boots with no backend and no auth wall, and the real race-builder
page rendered (incl. a newly-added widget), zero page errors.

**Why it works (no auth gate — verified):** `[:verify-user-session]` is a **no-op when there is no
token** (`events.cljs:1633`), and the `:route` handler does not gate on auth (the `secure?`/https
redirect is skipped on `localhost`). Routing is **path-based** off `window.location.pathname`
(`core.cljs:80`). So an unauthenticated headless load reaches a builder route; backend calls just
`ERR_CONNECTION_REFUSED` harmlessly.

**Recipe (CLI-only; CI- and Codespace-portable):**
1. Compile the **app** build: `lein fig:build` (dev, `dev.cljs.edn`) → `resources/public/js/compiled/orcpub.js`.
2. Host page `/tmp/pw/host.html` (the server-rendered `index.clj` reduced to its essentials):
   ```html
   <!DOCTYPE html><html><head><meta charset="utf-8">
   <link rel="stylesheet" href="/css/compiled/styles.css">
   <link rel="stylesheet" href="/assets/font-awesome/5.13.1/css/all.min.css"></head>
   <body><div id="app"></div><script src="/js/compiled/orcpub.js"></script></body></html>
   ```
3. Driver `/tmp/pw/drive-app.js`: a node static server rooted at **`resources/public`** with an
   **SPA fallback** (return `host.html` for any path that isn't a real file, so a deep route URL
   loads the JS and client-routes); Playwright Chromium then `goto`s the route, waits for `#app` to
   have content, asserts/screenshots, and captures `pageerror`. Route path = `/pages/dnd/5e/<route-seg>`
   (e.g. `/pages/dnd/5e/race-builder`; `<route-seg>` is the content-types `:route-seg`).
4. Run: `cd /tmp/pw && node drive-app.js`. Observed: `RENDERED: true`, `PAGEERRORS: none`, the
   builder's text present (incl. the floating-ASI widget).

**This makes rendered-UI E2E a single CLI command** — so it runs in CI (results to your phone, no
desk) or a tunneled Codespace. It is the basis for the deferred "cljs/E2E into CI" item.

### Full content round-trip through the real UI (`test/e2e/export-import-use.js`)

Beyond authoring, the whole **export → import → use** loop runs headless with no backend (Blob/saveAs
and FileReader are client-side): the My Content **Export** button yields a real `.orcbrew` download
(Playwright `page.on('download')` captures it), the real `<input type=file>` imports it
(`setInputFiles`), and the imported homebrew race is then selectable in the character builder. Routes:
My Content is `/dnd/5e/my-content` (the `dnd/` branch — NOT `/pages/...`); the character builder is
`/pages/dnd/5e/character-builder`. This is the UI-level proof for floating-ASI layer 5, above the
function-level round-trip tests. **Gotcha it pins:** import names the pack from the *file name*
(`import-file`: `nm = split(filename, ".orcbrew")`), so preserve `download.suggestedFilename()`
(`<pack>.orcbrew`) — saving under a different name re-creates the pack under that name.

### Driving interactions (done — committed as `test/e2e/race-builder-asi.js`)

The render proof above was extended to a full **click-through**: author a floating-ASI homebrew
race through the real form, click the real Save button, and assert the persisted localStorage. It
runs with `REPO=<repo> node test/e2e/race-builder-asi.js` (see `test/e2e/README.md`).

**It caught a real bug source review missed:** the race-builder ASI widget stored raw `<select>`
strings (`"cha"`, `"martial"`, `"1"`) instead of the namespaced keyword / ints the data model
needs — which would make `compile-ability-increases` → `race-ability` choke on a string and
`(ability-groups "martial")` return nil (empty option list). The harness/JVM tests dispatch events
with already-correct values, so **only the browser-driven `<select>` exercised the coercion.** Fix:
the widget now coerces each dropdown's emitted string via lookup maps (`views.cljs`,
`race-ability-increase-choices`). This is the concrete payoff of the "90%-without-backend" E2E.

**Interaction gotchas (verified, the hard way — encoded in the script):**
- `dropdown`/`labeled-dropdown` on the **default** path yields the **rendered string** (`event-value`
  = `.target.value`); reagent renders a keyword value via `name`, so `::character/cha` → `"cha"`. Pass
  **`:typed? true`** and on-change instead receives the item's original `:value` (any type) — no
  coercion, nothing to forget. New non-string dropdowns should use it. Full story + the repo-wide
  census: `docs/kb/dropdown-value-coercion.md` (decision D32).
- Input index `[1]` on the race-builder page is the **Orcacle search box** (`placeholder="search"`),
  NOT a form field. Typing into it opens an autofill **suggestions overlay** that intercepts later
  clicks (this is the "div intercepts pointer events" symptom — not a modal/loading overlay).
  Target real fields by class/placeholder (`input.input.h-40`, `[placeholder="Default Option Source"]`).
- "Save to Browser Storage" is in the DOM **twice** (hidden mobile + visible desktop twin); a plain
  `.first()` grabs the hidden one ("element is not visible"). Use `button:visible`.
- reagent flushes re-renders **async**; back-to-back `selectOption`s can fire an on-change whose
  closure still holds the pre-dispatch state and clobbers an earlier pick. Pause (~150ms) between.
- When in doubt about what the page actually is, **take a screenshot** — it settles "which input/
  overlay is this" in one shot instead of repeated wrong hypotheses.

## Driving the character builder — three gotchas that each cost a debugging pass

From `test/e2e/homebrew-grand-tour.js`, which drives the builder end to end. Every one of these
looked like a bug in the app and was not.

**1. A selection's `:tags` decide which TAB it renders on, not the thing that granted it.** This is
the big one, and it caught me three times in one script:

| selection | granted by | renders on |
|---|---|---|
| Fighting Style | Fighter, level 1 | Class / Level |
| Pact Boon | Warlock, level 3 | Class / Level |
| **Eldritch Invocations** | Warlock, level 2 | **Spells** — `:tags #{:spells}` |
| **Languages** | the Acolyte background | **Proficiencies** — `:tags #{:profs :language-profs}` |

Looking for a warlock's invocations on its class panel finds nothing and looks exactly like
"homebrew invocations are not offered". Check the `selection-cfg`'s `:tags` in `options.cljc`
before concluding anything is missing.

**2. Tab clicks must be CASE-SENSITIVE.** The builder's tabs are capitalised (`Spells`), the
character sheet's own tabs on the right are lowercase (`spells`), and the site header nav is
lowercase too. A case-insensitive shortest-match click lands on the header link and navigates out
of the character builder entirely — after which every later check fails for the wrong reason, with
no error. `lib.js/clickTab` is case-sensitive for this reason.

**3. The builder mixes cards and dropdowns.** Race, background and fighting style are clickable
cards; **class and level are `<select>`s** and the builder seeds Barbarian by default. A
`:text("Fighter")` locator matches hidden nodes and times out. `lib.js` has `clickText` (visible
only) and `pickFromAnySelect` for the dropdowns; the distinction is real and not worth papering
over.

Also: the SRD background list the builder offers is short — Acolyte, a demo background, and Custom.
Sage is not there.
