# Homebrew / orcbrew testing notes

Local notes for test conventions and decisions touched by the homebrew
data-preservation work. A holding place so the decisions aren't lost; fold into a
canonical test guide if one is established.

## Decisions

### Text normalization does NOT strip accents (decided against)
`orcbrew-validation/normalize-text` normalizes *typographic punctuation* (smart
quotes/apostrophes, dashes, non-breaking spaces, ellipsis, ™/®/© …) to ASCII via
the curated `unicode-to-ascii` map. It deliberately does **not** transliterate
accented letters (`"Café"` stays `"Café"`): accents carry meaning and silently
stripping them is data loss. Accented/other non-ASCII characters are *surfaced*
(warn, don't strip) via `count-non-ascii`, not removed.
- Tests must assert accents are **preserved**. An old
  `"Café" -> "Cafe"` expectation was stale and was corrected.

### `name-to-kw` stays pure; the spec boundary rejects bad names
Names that derive an invalid keyword (leading non-letter, e.g. `"9 Lives"` →
`:9-lives`) are **rejected with a clear message**, not silently sanitized —
silent stripping causes key collisions = data loss. See
`test/clj/orcpub/dnd/e5/keyword_audit_test.clj`.

## Gotchas that bite cljs tests

### ClojureScript has no char type
Seq'ing a string yields **1-char strings**, and `(int "é")` is `("é" | 0)` =
**0**, not the code point. Code like `(> (int %) 127)` silently never detects
non-ASCII in cljs — use `(.charCodeAt % 0)`. (This was a real bug in
`count-non-ascii`, caught by its test.)

### Expectation vs. regression guard — don't "update to match current behavior"
When a test fails, classify before editing:
- **Behavior changed on purpose** → it was an *expectation* → update the assertion.
- **Behavior did not intentionally change** → the test is a *regression guard
  doing its job* → fix the **code**, not the test.
- **Scaffolding drifted** (wrong API keys, wrong lookup path) → repair the
  harness *without changing what it asserts*, then re-run and believe the result.

Rewriting a failing guard to make it pass launders a real regression into green.
In the B5 triage, 3 of 6 "stale-looking" failures were guards catching **real
bugs** (`count-non-ascii`, dedup of top-level selection options, autosave crash
on an empty template); only 1 was a genuinely stale expectation.

## Where the suites run
- **CI** gates JVM `lein test` + lint + cljs **compile** (`fig:build`).
- The **cljs unit suite** (`lein fig:test` + `node e2e/run-cljs-tests.js`) and the
  **Playwright e2e** (`e2e/scenarios/`) are the **local/pre-merge** gate —
  browser-based, not run in CI by decision.
- The cljs runner now surfaces `ERROR in` lines (an uncaught-exception test once
  hid behind a bare error count).
