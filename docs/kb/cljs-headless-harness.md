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
