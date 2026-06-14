# Character Validation — intent, history, and modernization charter

**Purpose:** Preserve the long-standing intent of *validating a character* so it isn't
lost when the broken `character_test.cljc` is retired, and define the modern, falsifiable
replacement. The intent matters: the computed character is the source of the character
sheet + PDF + the saved record, and right now nothing validates it.

**Status:** History and the gap are **verified** (code + unshallowed git). The
modernization (the "Charter" section) is a **PROPOSAL — not implemented**; it's the
spec for an own-branch effort. Don't treat it as built.

---

## The intent worth keeping (do not lose this)

"A valid character passes validation; a malformed one (e.g. missing an ability score)
fails — visibly and early." That guard has existed since the original codebase and is
genuinely important: a malformed computed character should not silently flow into the
sheet, the PDF, or the server save. This is the idea to carry forward, independent of
any particular implementation.

## History (verified)

- Larry's `test-character-spec` (`a7ee3d32`, 2016-12-23) validated the **flat computed
  character** against:
  `(spec/def ::character (spec/keys :req [::abilities ::savings-throws ::speed ::darkvision ::initiative]))`.
- That `::character` spec was removed in the early entity/`from-strict` refactor and the
  test was never updated → it has been dead for years, visible only under `fig:test`
  (which CI doesn't run). It also collides on namespace with `character_test.clj`.
- Today the **computed/built character has no clojure.spec validation**. Only the *raw*
  and *strict* entity forms are spec'd (`::raw-character`, `::strict-character`). Full
  detail: [test-suite-state.md](test-suite-state.md) §3–§4.

## Why it can't be revived verbatim (verified)

The built character is now a **lazy entity-val structure** (the `entity.spec` engine,
fields pulled via `es/entity-val`), not a flat map. A `spec/keys` over it doesn't fit,
and a naive whole-structure spec would be awkward and potentially expensive (it'd force
realization of every field). So the original flat spec can't simply come back — the
*intent* has to be re-expressed against the current representation.

## Charter — the modern replacement (PROPOSAL)

1. **One narrow contract** on the invariant computed fields the sheet/PDF/save actually
   depend on — start with "base abilities present," grow to speed/HP/etc. only as each
   earns its keep. Enforce it **at the chokepoint** (`make-summary` / save): the guard
   *is* the spec applied where it matters. (The `save-character` crash fix — gating
   `make-summary` behind the ability check — is the first installment of this guard.)
2. **The test must be real and falsifiable.** Drive `make-summary`/`save-character` with
   (a) a valid built character → it succeeds, and (b) a malformed one (missing ability) →
   it returns a graceful error, **not** a crash. It must go **red** if the guard is
   removed. No theater: do **not** just assert `(spec/valid? my-spec my-handcrafted-input)`
   — that tests the spec against examples, not the system.
3. **Stretch (optional):** an entity-val-aware validation of the broader computed
   character, only if it proves worth the cost.

This is an **own-branch** item (per the working agreements: deep enough to warrant its
own branch), best done alongside or after getting the cljs tests into CI so it can be
gated.

## Disposition of the broken test

With this charter captured, `character_test.cljc`'s `test-character-spec` can be safely
**retired** — it validates a representation that no longer exists — with a comment
pointing here. The **idea lives on in this doc + the save-path guard**, not in the dead
test. Do not retire it until this charter exists (it now does).
