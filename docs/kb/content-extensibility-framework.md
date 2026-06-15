# The Content-Extensibility Framework

**What this is:** the canonical reference for how orcpub's homebrew/content system is being
rebuilt so (a) adding a content type is one registry entry instead of edits in ~9 files, and
(b) content can grant choices from *other* content (cross-silo customization). Written for
**both humans** (the mental model, the why) **and agents** (precise schema, conventions,
invariants, how-to). Read this to understand or extend the framework.

> ⚠️ **STATUS: partially built — this doc marks current vs planned explicitly.** Do not assume a
> layer is generative unless the status table below says ✅. Roadmap lives in
> `content-extensibility-direction.md`; the *why* behind every decision is in
> `content-extensibility-decisions.md` (referenced as D1–D22).

---

## 1. The mental model (start here)

A **content type** (spell, feat, boon, draconic ancestry, …) is described **once as data** — a
single entry in the `content_types` registry. The framework has two halves:

1. **The Builder Framework** — the per-type *wiring* (events, db draft state, subscriptions,
   routes, the builder page) is **generated from the registry entry**, not hand-written per type.
2. **The Composition layer (pool + grant)** — content is exposed in open, type-addressed
   **pools**; any other content can **grant** a (filtered, gated) choice from a pool. This is
   how a feat grants a fighting style, or dragonborn grants a homebrew draconic ancestry.

The payoff: *add a type* = one registry entry (+ its genuinely-custom form/spec/rules);
*let content tap other content* = a grant referencing a pool. Both are data, not boilerplate.

The deciding principle (D12): **an abstraction earns its keep only when it is thicker than what
it hides AND its interface reveals intent.** Collapse mechanical duplication; keep readable,
meaningful code explicit; never force a genuinely-different kind of thing through one pattern.

---

## 2. The Builder Framework (registry-driven wiring)

### 2a. The single source: `content_types`
`src/cljc/orcpub/dnd/e5/content_types.cljc` holds one descriptor per plugin-based homebrew type.
It is a **dependency-leaf** (currently requires only `route-map`; the routes pass will make it a
pure-data leaf — D7) so every other layer can read it without circular deps.

**Registry schema (per entry):**

| key | meaning | consumed by | required |
| --- | --- | --- | --- |
| `:id` | content-type id (keyword) | identity / `by-id` index | yes |
| `:type-name` | human label ("Pact Boon") | builder UI, error messages | yes |
| `:builder-item` | app-db key holding the in-progress draft | subs, db, events | yes |
| `:spec` | spec the saved item is validated against | save handler | yes |
| `:plugin-key` | `::e5/*` key the items live under in `:plugins` | save/delete, pools | yes |
| `:route-kw` | builder page route keyword | routes, core, events | yes |
| `:route-seg` | builder page URL segment | routes (bidi) | yes |
| `:local-storage-key` | localStorage draft key | events (interceptor) | yes |
| `:homebrew-builder?` | opt this type into the **events + db generative loops** | events, db | for loop-driven types |
| `:default` | the empty-draft value (usually `{}`) | db default-value, new-item event | with `:homebrew-builder?` |

### 2b. What's generated from the registry — CURRENT STATUS

| Layer | File | Status | Mechanism |
| --- | --- | --- | --- |
| **subscriptions** | `spell_subs.cljs` | ✅ generated | `doseq` over registry → `reg-sub` builder-item passthroughs |
| **events** | `events.cljs` | ✅ generated | `doseq` over `:homebrew-builder?` → `register-homebrew-content!` |
| **db draft slots** | `db.cljs` | ✅ generated | builder-item `default-value` slots from `:builder-item`+`:default` |
| **routes** | `route_map.cljc`, `routes.clj` | ✅ generated | bidi segs + `my-content` set + SPA allowlist from `:route-seg`/`:route-kw` (registry is now a pure-data leaf; guarded by `content_types_routes_test`). `route_map` keeps only the one route-keyword `def` per type (D6). |
| **core page-map** | `core.cljs` | ⚠️ won't generate | a view *fn* can't be derived from data in cljs — the route→view binding is irreducible (best co-located with the form) |
| **spec** | per-type ns | ✅ generated (draconic) | `bf/fields->spec` over the field schema — optional-by-default, required name/key/option-pack + `:required?` fields, enum values validated. 🔴 conditional-required (`:required-when`) NOT yet enforced — high-priority pin. |
| **builder form** | `views.cljs` | ✅ collapsed (not generated) | `simple-content-builder` makes it a one-liner; custom fields via `extra-fields` |

### 2c. The wiring HOFs (the trusted thick parts the loops compose)
- `register-homebrew-content!` (events.cljs) — from one descriptor registers
  save/delete/edit/new + set/set-prop/reset. The events loop builds this descriptor per
  registry entry.
- `reg-save-homebrew` / `reg-delete-homebrew` / `reg-edit-homebrew` / `reg-new-homebrew` — the
  existing per-concern factories `register-homebrew-content!` composes.
- `simple-content-builder [item-sub set-prop & [extra-fields]]` (views.cljs) — the generic
  builder form (Name + Option Source + Description + optional extra fields).

### 2d. Conventions (agents: follow these exactly)
- **Event keyword naming.** In the `:builder-item`'s namespace, the verbs `save-`/`delete-`/
  `edit-`/`new-`/`set-`/`reset-<base>` and `set-<base>-prop`, where `<base>` = the builder-item
  name minus `-builder-item` (e.g. `::class5e/boon-builder-item` → `::class5e/save-boon`). The
  events loop *derives* these; they are also **literal at every dispatch site** in views
  (`builder-page`, `simple-content-builder`, new/edit/delete buttons) so grep still finds them.
- **`builder-item` naming** must be `::<ns>/<base>-builder-item` for the convention to hold.
- **Keys come from stable ids, never display names** (D10). The save handler keys an item by
  `name-to-kw` at creation; never re-derive identity from a name display code may mutate.
- **The `content_types_test` registry guards** the entry count + the exact builder-item set —
  adding a type updates those two locked assertions (by design: drift fails loudly).

### 2e. HOW TO ADD A HOMEBREW CONTENT TYPE (current state)
1. **Registry entry** in `content_types.cljc` (all schema keys; `:homebrew-builder? true` +
   `:default {}` to opt into the events/db loops). ⇒ events + db + subs wiring is now automatic.
2. **Spec** — one `(spec/def ::homebrew-<type> (spec/keys :req-un [::name ::key ::option-pack]))`
   in the type's ns (mirror `::homebrew-boon`).
3. **Builder form** in `views.cljs` — `(defn <type>-builder [] (simple-content-builder <item-sub>
   <set-prop> [extra-fields…]))` + `(defn <type>-builder-page [] (builder-page "..." <reset>
   <save> <type>-builder))`; add the my-content menu entry.
4. **Routes** (until the routes pass lands): `route_map.cljc` (def + route-set + bidi seg),
   `routes.clj` (allowlist), `core.cljs` (route→page).
5. **Game-rule wiring** — if the type is *granted* by other content, register a **pool** and a
   **grant** (§3); if it stands alone in its own list, nothing more.
6. **Update `content_types_test`** count + builder-item set.
7. **Verify** with the gate (§5).

> The irreducible per-type work is the **field schema** (the form's custom fields), the **spec**,
> and **how it plugs into game rules**. Everything else is generated or a one-liner (D22).

---

## 3. The Composition layer (pool + grant)

The capability that makes this a *framework*, not just a builder generator: content tapping
content across silos, with filtering, gating, and prerequisites.

### 3a. Pool
A **pool** is an open, type-addressed collection of grantable things: built-in entries (in code)
`++` homebrew entries (from loaded `.orcbrew` packs). Defined by the pure leaf primitive
`src/cljc/orcpub/dnd/e5/content_pools.cljc`:
```clojure
(pool plugin-vals plugin-key built-in)   ; = built-in ++ (mapcat (comp vals plugin-key) plugin-vals)
```
Pools are **memoized subscriptions** that read through `::e5/plugin-vals` — the **single
resolved-content seam** every plugin pool already uses, and where variant (`_copy`/`_mod`)
resolution will slot in later **without changing pools or grants** (the variant pin, D20).

### 3b. Grant
A **grant** is what a content item declares to tap a pool. One primitive, three faces:
- fixed: "you gain this specific thing" (= the existing modifier system, keep — D4);
- choose-from-filtered: a `selection-cfg` whose options are pool entries matching a filter;
- choose-from-all: an unfiltered selection over the open pool.
The engine ALREADY supports filter/gate/prereq (`selection-cfg` carries `prereq-fn`/`tags`/
`ref`; `option-prereq` exists) — the framework exposes that as *declarable data* (D18). Filters
are predicates over *present* metadata: absent metadata → not offered → **never an error**.

### 3c. Mechanics as data
Content carries real mechanics declaratively via a `:props` map compiled by the EXISTING
`opt5e/plugin-modifiers` / `make-feat-modifiers` vocabulary (speed, flying-speed,
saving-throw-advantage, skill-prof, language, …). Built-ins with no `:props` are unchanged.

### 3d. Worked example — draconic ancestry (the proven slice)
- Pool: `::races5e/draconic-ancestry-pool` = built-in colours `++` `::e5/draconic-ancestries`
  homebrew (`content_pools/pool`). Dragonborn's "Draconic Ancestry" choice grants from it.
- Each entry compiles (in `draconic-ancestry-option`) to resistance + the breath-weapon the
  race's attack reads + any `:props` riders. A homebrew gem ancestry inherits full mechanics.
- End-to-end proven: authored in-app builder → pool → export → import → **character round-trip**
  (the choice survives save/load by its stable key). Tests in `draconic_ancestry_test.cljs`
  (cljs) + `extensibility_golden_test.cljc` (JVM round-trip).

### 3e. How to add a pool / a grant
- **New pool:** `(reg-sub ::x-pool :<- [::e5/plugin-vals] (fn [pv _] (pool pv ::e5/<key> <built-in>)))`.
- **New grant:** a `selection-cfg` whose `:options` map a per-entry compiler over the pool sub.
  Pass each entry's stored `:key` through (D10). Keep the compiler thin; pool-kind logic lives
  in the pool, never as a `cond` inside a shared grant fn (D14 god-function trap).

---

## 4. Invariants & gotchas (agents: violating these breaks user data or the framework)

- **D10 — identity from stable keys, never display names.** Saved characters reference content
  by key. Re-deriving a key from a mutated name orphans characters.
- **D14 — don't force heterogeneous kinds through one pattern.** Magic items (server-persisted)
  and the combat tracker (transient) are **excluded** from the registry/loops on purpose.
- **Variant forward-compat (D20).** Every pool derives from the one `::e5/plugin-vals`
  resolved-content seam — never raw `:plugins` — so `_copy`/`_mod` variants slot in later.
- **Routes fail-closed.** A missing generated route entry → 404, not a security hole; still,
  verify routes resolve after the routes pass.
- **String-vs-keyword (learned the hard way).** UI dropdowns emit *strings*; the engine expects
  *keywords* (`:thunder`, `::char5e/dex`). A builder field must store the keyword, and tests
  must use the field's **real output**, not idealized keyword data. (This shipped a broken
  breath-weapon: damage type stored as `"thunder"` → display + resistance broke.)
- **Greppability.** Derived event keywords are still literal at their dispatch sites; the
  registry is the single greppable source. Keep it that way — don't add a second derivation.

---

## 5. Verifying changes
Run the full gate before each commit (behavior-preserving until a step intends otherwise):
```
lein lint        # clj-kondo — must be 0 errors
lein test        # clj + cljc (incl. content_types + golden round-trip tests)
lein fig:test    # compiles the cljs; catches keyword/route/symbol errors
# then the headless harness for cljs behavior — see cljs-headless-harness.md
```
Tests must be **falsifiable** (if you break the code, does it go red?). Behavior-preserving
refactors are proven by the boon/draconic tests passing **unchanged**. For UI, the isolated
component preview (mount + Playwright screenshot) can show a form renders correctly.

---

## 6. Map of the docs
- **This doc** — the framework reference (what it is, schema, conventions, how-to, invariants).
- `content-extensibility-direction.md` — the **roadmap / current direction** (what's done, next).
- `content-extensibility-decisions.md` — **why** (D1–D22, incl. the pivots and the deflation→re-centering).
- `registry-before-after.md` — concrete **before/after** of adding a type.
- `content-extensibility-compatibility.md` — backward-compat invariants for orcbrew/characters.
- `cljs-headless-harness.md` — how to run the cljs tests headless.
- `verification-discipline.md` — testing/honesty lessons (incl. the toadyism failure mode).
- `homebrew-content-merge.md` — **READ before claiming a content type isn't homebrew-extensible.**
  The recurring `feat-options` trap (static `*-options` defs are SRD-minimal/`#_`-commented *by
  design*; homebrew is merged at the `concat` assembly point, not in the static def). Feats ARE
  extensible; fighting styles genuinely are NOT (no plugin path — the one real gap).
