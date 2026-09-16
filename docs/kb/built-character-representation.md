# The Built Character is NOT a flat map (entity-spec / entity-val)

> Two halves: **reading** a built character (`entity-val`, deferred fns) — most of this page — and
> **writing** against it (`?attr` refs, and why they need a macro) — the section near the end. The
> writing half decides what is expressible at all, and cost this branch a hand-written feat.

**Purpose:** A foundational gotcha that has caused real bugs and a lot of confusion in
this session. Read this before you `get`, `spec/keys`, iterate, or otherwise treat a
"built"/computed character as a plain map.

**Status:** Verified from code.

## One-liner

The **built character** (output of `entity/build`) is a **map whose *derived* values are
deferred functions**, not a flat map of realized values. Read derived fields with
`orcpub.entity-spec/entity-val` (or the `q` / `?ref` macros) — **not** plain `get`.

## How it actually works (verified)

- `entity/build` (`src/cljc/orcpub/entity.cljc:620`) applies modifiers
  (`orcpub.modifiers/apply-modifiers`) to a base entity, producing a map.
- Each value is **either a plain value or a deferred function** tagged with `:entity-fn?`
  metadata. Computed/derived fields (those that depend on other fields) are the deferred ones.
- The accessor is `entity-val` (`src/cljc/orcpub/entity_spec.cljc:5`):
  ```clojure
  (defn entity-val [entity k]
    (let [v (entity k)]                         ; entity is a map; (entity k) == (get entity k)
      (if (:entity-fn? (meta v)) (v entity) v))) ; deferred fn? realize by calling (v entity)
  ```
  So `entity-val` returns the *realized* value; a plain `get` on a deferred key returns the
  **function itself**, not the value.

## What this means for you

- **Some keys are plain** — e.g. `:base-abilities` is read with `get-in` in
  `events.cljs`. **Many derived keys are not** — `get`/`get-in` on those returns a
  function. Use `entity-val` / `q` / `?ref` for anything computed.
- **Do not `spec/keys` the built character as a flat map.** Its deferred values are
  functions, not their realized values; a whole-structure spec would have to realize every
  field via `entity-val`. This is *why* the computed character has **no clojure.spec spec**
  (see [character-validation.md](character-validation.md)), and why Larry's 2016 flat
  `::character` spec could not survive the move to this representation.
- **Terminology overload:** "entity spec" / `entity-spec` (`es`) here is this
  **build/compute engine**, NOT `clojure.spec` validation. Two unrelated things both called
  "spec."

## The other half — WRITING against it, and why it needs a MACRO

Everything above is about *reading* a built character. The writing side has a rule that is easy to
trip over and was not written down until 2026-09-08.

### `?attr` is not a variable — it is rewritten at compile time

A modifier body written like this:

```clojure
(mods/modifier ?armor-class (+ ?base-ac ?dex-bonus))
```

contains no variables named `?base-ac`. `orcpub.entity-spec/modifier` is a **macro** that walks the
body and rewrites every `?`-prefixed symbol before the code ever runs (`entity_spec.cljc:26`):

```clojure
(defn ref-to-kw [s entity]
  (if (and (symbol? s) (s/starts-with? (str s) "?"))
    `(entity-val ~entity ~(ref-sym-to-kw s))       ; ?base-ac  ->  (entity-val e :base-ac)
    s))
```

`replace-refs` applies that recursively through maps, vectors and seqs, against a gensym'd entity
argument the macro introduces. So `?base-ac` becomes `(entity-val e :base-ac)` — the same accessor
the reading half uses — and the whole body becomes a deferred fn of the entity.

### The consequence: a plain fn cannot read `?attrs`

Only code the macro *expands* gets the rewrite. A function you write and hand to something is an
ordinary closure — its body was compiled long before, with no entity in scope. **If a value has to
come off the built character, whatever builds that code has to be a macro that splices the `?`-ref
in.**

This is not a style preference; it decides what is expressible. The worked example is AC bonuses:

```clojure
;; you write the fn — it only ever sees what it is CALLED with
(mod5e/ac-bonus-fn (fn [armor shield] (if (nil? armor) 5 0)))     ; struck 2026-09-08

;; the MACRO writes the fn — so it can put ?-refs inside the body
(mod5e/ac-bonus {:armored? false} 5)
;; expands to roughly:
(mods/vec-mod ?ac-bonus-fns
  (fn [armor shield]
    (if (reqs/meets-all? {:armored? false}
                         {:armor armor :shield shield
                          :main-hand ?…/main-hand-weapon         ; ← spliced by the macro
                          :off-hand  ?…/off-hand-weapon})        ; ← unreachable from a plain fn
      5 0)))
```

An AC contributor is invoked as `(f armor shield)` and gets nothing else, so *"+1 AC while wielding
two weapons"* was **unwritable** in the first form. That is the whole reason the Dual Wielder feat
was hand-written for years rather than authored as data — and why the fix was a macro, not a wider
function signature (`armor-class/reconcile`'s contract never changed; see
`requirements-registry.md`).

### The rules that follow

- **Need a character value inside a generated body?** The generator must be a macro, and the `?`-ref
  must appear literally in its template (`~'?orcpub.dnd.e5.character/main-hand-weapon`).
- **You cannot select a `?`-ref at runtime.** `es/conditions` is a macro that captures condition
  *forms*; a runtime map of conditions cannot be spliced into one. A registry of predicates works
  only if the macro assembles a plain-data context and the predicates are ordinary fns over it —
  which is what `requirements.cljc` does.
- **`?`-refs are fully qualified inside a macro template.** `?ac-bonus-fns` written literally in a
  source file resolves; the same symbol produced by a macro needs `~'` so the expansion contains the
  symbol rather than its value.
- **Adding a fact to a generated context is two edits, deliberately** — the map in the macro, and the
  entry in the registry that reads it.

## Where it bit us (this session)

- The `save-character` null crash: `make-summary` realized fields on a character missing
  abilities and blew up (`entity-val → character.classes`). Fixed by gating on abilities
  before `make-summary`. See [test-suite-state.md](test-suite-state.md) §2/§4.

## Anchored in code

Short pointers to this doc live on `orcpub.entity-spec/entity-val`, `orcpub.entity/build`,
and the `built-character` subscription (`subs.cljs`), so this is findable from the code,
not only the KB.
