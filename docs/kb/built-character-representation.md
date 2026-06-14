# The Built Character is NOT a flat map (entity-spec / entity-val)

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

## Where it bit us (this session)

- The `save-character` null crash: `make-summary` realized fields on a character missing
  abilities and blew up (`entity-val → character.classes`). Fixed by gating on abilities
  before `make-summary`. See [test-suite-state.md](test-suite-state.md) §2/§4.

## Anchored in code

Short pointers to this doc live on `orcpub.entity-spec/entity-val`, `orcpub.entity/build`,
and the `built-character` subscription (`subs.cljs`), so this is findable from the code,
not only the KB.
