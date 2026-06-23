# Headless ClojureScript test harness (how to run cljs tests in a container)

**Why:** CI runs only `lein lint`/`lein test` (JVM). The cljs suite (subs, events,
import-validation, content-reconciliation, …) only runs under a JS runtime. This recipe
runs it **headless** so cljs changes are verifiable without the full webapp (no backend,
no Datomic — those aren't needed; HTTP calls just `ERR_CONNECTION_REFUSED` harmlessly).
**The harness is built in `/tmp` + the gitignored `target/` — both ephemeral, so rebuild
from this recipe.** It's also the prototype for the deferred "cljs tests in CI" item.

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

**Driver (node, `/tmp/pw/run.js`):** a ~15-line `http` static server rooted at
`target/test/`, then Playwright Chromium navigates to the HTML, captures `console` +
`pageerror`, waits for `/Ran \d+ tests/`, and prints the console + body. Grep the output for
`Ran .* tests`, `FAIL in`, `ERROR in`.

## Known-good baseline (as of this branch)
Full suite ≈ **150 tests, 10 failures, 2 errors**. The 2 errors are the dead
`character_test.cljc` (retire per `character-validation.md`). After the import fixes, the
import-validation failures are gone; remaining real failure is `user-stale-user` (subs auth
guard — separate, not triaged).

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
