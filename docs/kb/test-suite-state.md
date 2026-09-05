# Test Suite State & Debt (verified)

**Purpose:** Durable, verified record of what the test suites actually run and gate, the
pre-existing failures and their diagnosis, and the open decisions — so this isn't
re-derived from scratch each session. Findings are verified from code / CI config /
git history (unshallowed) and one live cljs run (PR #28 e2e). Items that still need a
cljs runtime to settle are marked **UNRESOLVED**.

**Scope note:** Everything here is **pre-existing `develop` debt**, separate from the
content-extensibility work. It surfaced while verifying that work; it is not caused by it.

---

## 0. Current measured state — 2026-09-05, `feature/fighting-style-authoring`

Both suites run in this container. The numbers below are from one run each on that date; the
sections after this one are the older investigation and are kept for the diagnosis they carry.

| suite | how | result |
|---|---|---|
| JVM (`clj` + `cljc`) — the CI gate | `lein test` | **463 tests / 2631 assertions, 0 failures, 0 errors** |
| ClojureScript — not in CI | `lein fig:test` then `node test/e2e/cljs-harness.js` | **350 tests / 1726 assertions, 4 failures, 2 errors** |

**The 4 cljs failures are one cluster**, all in `format-import-result-*` (`import_validation_test`):
`re-find` of a leading **emoji** (⚠️ / ✅) against the formatted message. Same shape in each, none of
them touched by this branch. Suspect the harness's unicode handling rather than the assertion — the
captured log renders ⚠️ as mojibake — but that is untested; do not close it as "environment" without
checking the string in a browser REPL.

**The 2 errors** include `test-character-spec` (`character_test.cljc`), pre-existing and long-standing.

**Two errors were fixed here, and both were self-inflicted:** `bucketing-analysis` and
`bucketing-is-wrong-if-a-calculation-is-misgrouped` came from `ac_outer_loop_analysis_test`, a JVM
timing benchmark written as `.cljc` — `System/nanoTime` does not exist in cljs. Moved to
`test/clj/…/ac_outer_loop_analysis_test.clj`; still 3 tests / 12 assertions green on the JVM.
**The lesson generalizes: a benchmark or anything using JVM interop must be `.clj`, not `.cljc`.**
Nothing in the build catches it, because the cljs suite is not in CI — which is section 1's point.

**Harness caveat.** The mode-B DOM reporter prints `4 failures / 2 errors / 350 Tests /
1726 Assertions` as separate lines, not the `Ran N tests containing N assertions.` line the driver
waits for, so `cljs-harness.js` reports `SUMMARY: (none)` and falls through its 240s timeout before
dumping the body. The results are correct and the full body lands in `target/test/cljs-run.log`;
the driver's regex is simply written for mode A. Fix it when it next annoys someone.

---

## 1. What runs where (the gate reality)

- **CI** (`.github/workflows/continuous-integration.yml`) runs **only `lein lint` + `lein test`** — JVM, `clj` + `cljc`. ✅ verified (workflow has no cljs/figwheel step).
- **`lein test`** executes the JVM `clj`/`cljc` tests (~223 passing on the working branch).
- **The ClojureScript tests** (`subs_test`, `events_test`, `import_validation_test`,
  `content_reconciliation_test`, and the `.cljc` character/spec test) run **only** via
  `lein fig:test` (figwheel `test` build) in a browser/headless harness — **never in CI**.

**Consequence:** the cljs suite is **unrun and unmaintained** → it has rotted. This is the
root problem, not any single test. The container used for this work is **JVM-only** (no
browser/figwheel), so cljs fixes here can be linted + reasoned but not executed; they were
verified via the PR #28 live harness (figwheel `test-runner.html` + Playwright/Chromium).

## 2. Pre-existing cljs failures (10 failures / 3 errors)

Counts reported by the PR #28 live run: this branch **150 tests / 888 assertions**,
develop baseline **132 / 735** with the **identical 10/3**. This branch **adds 18 tests /
153 assertions, all passing — no new regressions.** Classification:

| Test | Kind | Verdict |
|------|------|---------|
| `character-test/test-character-spec` (3 errors) | references `::char5e/character` as a **spec**, but it's only a **subscription** | **Dead test** — see §3 |
| `import-validation-test/*` (≈8 failing assertions across `test-apply-key-renames-batch`, `test-count-non-ascii`, `test-normalize-text-in-data-recursive`, `test-dedup-options-in-import-full-pipeline`) | real tests of **present** functions (`apply-key-renames`, `count-non-ascii`, `normalize-text-in-data`, `validate-import` dedup) | **UNRESOLVED** — real-bug-vs-stale needs a cljs run. Guards the orcbrew import path, so worth triaging. |
| `subs-test/user-stale-user-no-token-still-guarded` (1) | real auth-guard behavior test (`:user` sub skips HTTP without a token) | **UNRESOLVED** — likely a small `[]`-vs-`nil` mismatch; needs runtime |
| `events-test/save-character-rejects-missing-abilities` (1 error) | crashes `Cannot read … null` in `make-summary → entity-val → character.classes` on degenerate input | **Real crash**; production-reachability **UNRESOLVED** (artificial empty-template trigger) — see §4 |

**These are not "test theater."** The failing tests assert real behavior. The disease is
the opposite: real tests left unrun (§1).

## 3. The `::character` spec / `character-test.cljc` saga (verified via unshallowed git)

- `::char5e/character` is a **re-frame subscription** (`subs.cljs:507`), **not** a
  clojure.spec spec. Two separate registries; `spec/explain-data` ignores subs → the test
  errors "Unable to resolve spec".
- A `::character` **spec did exist**: added by **Larry (original author) 2016-12-23**
  (`a7ee3d32`) in `character.cljc` as the **flat computed character**:
  `(spec/keys :req [::abilities ::savings-throws ::speed ::darkvision ::initiative])`.
  It is **gone by our base `d42e05d`** (dropped during the early entity/`from-strict`
  refactor). The test (also from 2016) was never updated → dead for years, invisible
  because the cljs suite isn't run.
- **Duplicate-namespace bug:** `character_test.clj` (real, passing tests) and
  `character_test.cljc` (this broken one) both declare ns `orcpub.dnd.e5.character-test`.
  JVM loads the `.clj` (shadows the `.cljc`); cljs loads the `.cljc`. That's why it only
  fails under `fig:test`.
- It was **not renamed**. Current character specs are all **entity-based**:
  `::raw-character` (=`::entity/raw-entity`), `::unnamespaced-character`,
  `::strict-character` (=`::se/entity`). None matches the old flat shape.

## 4. The built/computed character has no validation spec (verified)

- **No clojure.spec spec validates the computed character.** `built-character` is a
  **function** (`subs.cljs:307`, `entity/build`) and a **subscription**
  (`:built-character` / `::char/built-character`) — not a spec. Searched: no
  `spec/def ::*built*`.
- The build output is a **lazy entity-val structure** (the `orcpub.entity.spec` engine,
  aliased `es`; fields pulled via `es/entity-val`), not a flat map — which is *why* a
  `spec/keys` over it is awkward and why the 2016 flat spec had no successor.
- **Terminology overload:** "spec" in this repo means both clojure.spec validation AND the
  `entity.spec` *build engine*. The built character is the output of the latter; it has no
  validation from the former.
- The 2016 `::character` was the last validation of the computed character; it died with
  the flat representation. The `save-character` crash (§2, #4) is a **symptom**: with no
  contract on the build output, a malformed build crashes deep in summary/PDF instead of
  failing fast. This is **refactor debt**, not a fresh oversight.

## 5. Open decisions / recommendations (so we don't re-litigate)

- **The dead `character_test.cljc`:** its *intent* (validate a character) is preserved in
  [character-validation.md](character-validation.md), so it can now be safely retired with
  an explainer comment pointing there (it validates a representation that no longer exists;
  note the sub-vs-spec name collision). **Fix the duplicate namespace** when you do.
- **Built-character validation:** if pursued, use **one narrow contract** (e.g.
  abilities / `base-abilities` present) enforced at `make-summary`/save (fail-fast) — the
  guard *is* the spec applied at the chokepoint. **Not** a big speculative `spec/keys` over
  the lazy structure, and **not** two separate mechanisms. "Narrow" = narrow **scope**, not
  narrow **rigor**. The test must be a **real, falsifiable integration test**: drive
  `make-summary`/`save-character` with a built character missing abilities and assert it
  **fails gracefully (clear error) instead of crashing** (reproducing §2 #4), and with a
  valid one assert it succeeds. **Avoid the theater version** — a test that only asserts
  `(spec/valid? my-spec my-handcrafted-input)` tests the spec against examples you wrote,
  not the system. This would kill the §2 #4 crash class. Grow only if it earns its keep.

- **No-theater rule (applies to every test added here):** before keeping a test, ask
  *"if I break the production code this covers, does this test go red?"* If no, it's
  theater — fix it or drop it. (Corollary from §2/§3: the repo's actual problem is the
  opposite — real, failable tests that aren't **run**, plus a couple of real tests whose
  **subject was removed**. Neither is fixed by adding tests that can't fail.)
- **The root fix — get cljs tests into CI** (headless `fig:test`; harness proven by the
  PR #28 e2e run). Without it the cljs suite keeps rotting and cljs changes can't be gated.
- **Triage the import-validation failures** (§2) against a cljs runtime — they guard the
  orcbrew import path that the extensibility compatibility story depends on.
