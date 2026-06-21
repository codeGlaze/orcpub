# Duplicate / colliding content keys — what actually happens, per layer

Answers a recurring, hard-to-pin-down question: are content keys required to be unique? The honest
answer is **it depends on the layer** — some layers treat a same-key homebrew entry as an intentional
**override** of the built-in, others let duplicates **coexist**, and import has its own **conflict
gate**. This is the map, traced from code (file:line cited) and pinned by `key_collision_test.clj`.

Markers: **VERIFIED** = read from code + test-backed. All cljs paths are in `spell_subs.cljs` /
`import_validation.cljs`.

## TL;DR
- **Top-level class / race / spell:** a homebrew entry with a **built-in's key OVERRIDES the built-in**
  (predictable, homebrew wins). This is the "override a built-in" capability some users rely on.
- **Subraces, pools, list content (backgrounds/languages/selections/monsters):** same-key entries
  **coexist** (both appear) — no override.
- **Import:** duplicate keys are **detected** (within the import + against existing) and routed to a
  conflict-resolution modal (rename / skip / replace) — the "duplicate keys won't just load" behavior.
- So keys are NOT globally unique-or-bust; uniqueness matters in different ways in different places.

## The map (VERIFIED)

| Layer | How content combines | Same-key collision |
|---|---|---|
| **Classes** | `(into (sorted-set-by ::t/key) (concat (reverse plugin-class-options) base-classes))` (`spell_subs.cljs:1016`) | **OVERRIDE** — plugin added first, the sorted set dedupes by key keeping the one already present, so the **homebrew class wins; the built-in is dropped** |
| **Races** | `(into (sorted-set-by compare-keys) (concat (reverse plugin-races) base))` (`:950`; `compare-keys` = `(compare (:key x) (:key y))`, `:936`) | **OVERRIDE** — homebrew race wins |
| **Spells** | `(into (sorted-set-by compare-keys) (concat (reverse plugin-spells) built-in))` (`:1228`); `spells-map` then reduces this deduped set into a map (`:1242`) | **OVERRIDE** — homebrew spell wins |
| **Subraces** | gathered by `mapcat` then `(update race :subraces concat (subraces-map key))` (`:161`, `:954`) | **COEXIST** — both same-key subraces appear under the race |
| **Backgrounds / Languages / Selections / Monsters / Encounters** | `(mapcat (comp vals ::e5/<type>) plugin-vals)` (`:104/:110/:116/:1087/:1093`) | **COEXIST** — a seq; duplicates both appear |
| **Pools** (draconic ancestry, fighting-style) | `(concat built-in homebrew)` (`content_pools.cljc/pool`) | **COEXIST** — both offered in the choice |
| **Spell lists** | `(merge-with merge-spell-lists …)` = `merge-with concat` (`:1277`, `:1253`) | **COEXIST** — keys merged, lists concatenated |
| **Import** | `detect-duplicate-keys` → `find-key-conflicts` (`import_validation.cljs:1120/1097`) → `:internal-conflicts` (within the import) + `:external-conflicts` (vs existing); `import-plugin` shows a resolution modal (`events.cljs:3878`) | **CAUGHT** — rename / skip / replace, not a silent merge |

## Why the override is "plugin wins" (the load-bearing semantics) — VERIFIED by test
`(into (sorted-set-by key-cmp) coll)` adds elements left-to-right; when a new element compares **equal**
(same key) to one already present, `conj` on a set is a **no-op** — the element already in the set
stays. The class/race/spell combines put `(reverse plugin-options)` **before** the built-ins in the
`concat`, so plugin entries are added first and **win** the collision. `key_collision_test.clj` pins
this: a `{::t/key :fighter}` plugin option placed before a built-in `:fighter` yields a 1-element set
containing the **plugin** one; distinct keys both survive; a plain `concat` (the pool/list shape) keeps
**both** same-key entries.

## Notes / boundaries
- **Within a single content-type map, keys are already unique** — the `.orcbrew` is EDN, so two entries
  with the same key in one map collapse at parse time (last-wins). `find-duplicate-keys-in-content`
  notes this (`import_validation.cljs:1056`: "Since items is a map, keys are inherently unique within
  it"). So intra-map duplicates can't survive to runtime; collisions are **cross-source** (plugin vs
  built-in, or plugin vs plugin).
- **The override is order-dependent and predictable**, but it is the *runtime combine* order
  (plugin-first), not "last loaded." Two homebrew plugins both overriding the same built-in key would
  collide with each other — that is exactly what the import conflict gate is for.
- **NOT-TRACED here:** how a *saved character* that chose an overridden key resolves after the override
  changes (it should resolve to whatever now holds that key — flagged, not yet tested).
- This is the tooling that was missing for "where do duplicate-key problems come from": the answer is
  layer-specific, and now it's one table + a test instead of guesswork.
