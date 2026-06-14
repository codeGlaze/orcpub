# Test Suite State & Debt (verified)

**Purpose:** Durable, verified record of what the test suites actually run and gate, the
pre-existing failures and their diagnosis, and the open decisions — so this isn't
re-derived from scratch each session. Findings are verified from code / CI config /
git history (unshallowed) and one live cljs run (PR #28 e2e). Items that still need a
cljs runtime to settle are marked **UNRESOLVED**.

**Scope note:** Everything here is **pre-existing `develop` debt**, separate from the
content-extensibility work. It surfaced while verifying that work; it is not caused by it.

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

- **The dead `character_test.cljc`:** retire it with an explainer comment (it validates a
  representation that no longer exists; note the sub-vs-spec name collision), OR modernize
  it. Either way, **fix the duplicate namespace**.
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
