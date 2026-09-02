# Homebrew fighting-style authoring — finish the wiring

**Branch:** `feature/fighting-style-authoring`, cut from `refactor/content-extensibility`.
**Status:** **Phase A (integration) DONE** — a pack can author a `::e5/fighting-styles`
item, it folds into the open pool (built-in ++ homebrew) via
`::classes5e/fighting-style-pool`, and a feat's `:grant {:from :fighting-styles}` offers it;
the pool is threaded into `template.cljc` (13-arity `template-selections`, defaulting to
built-in for the 12-arg callers) from `equipment_subs`. Spec via
`classes/homebrew-fighting-style` (field-schema) + `content_specs` + `field_schemas`;
classifier marks the type v2. Demo item `Demo: Tidewarden` + build tests assert the
homebrew style's mechanic lands on a built character. **Phase B (in-app builder UI for
authoring) remains** — the registry `:homebrew-builder?` entry, builder view, and the
hand-wired `core.cljs` route→view binding (see below).

## What we've been doing (context)

While building the **demo-content tier** (on `feature/demo-content-tier` — a bundled example
`.orcbrew` that loads at boot and doubles as a built-in test for the refactor's content
features), we inventoried the new content-authoring features and found the fighting-style one
is **half-finished**:

- The generic `:grant {:from :fighting-styles :choose N}` primitive on a feat **works** — a feat
  can grant a choice of a **built-in** fighting style, and the demo pack's `Demo: Versatile`
  feat covers that with a passing build test.
- But a pack **cannot author a NEW fighting style**. That half was left as an explicit follow-up.

Everything else the refactor added (ASI spreads, the `:save` rider, standalone
`:save-proficiencies`, the `:props` mechanics vocabulary, homebrew draconic ancestries) IS wired
for plugin content and is covered by demo items + build tests on the demo branch.

## The gap (evidence)

- **No `::e5/fighting-styles` plugin key** anywhere — no content-type entry, no save spec, no
  `plugin-fighting-styles` merge sub. Nowhere to put a homebrew style; nothing merges it.
- **`template.cljc` (the feat grantable-pools registry)** hard-codes the pool to built-in styles
  only: `{:fighting-styles {:name "Fighting Style" :options opt5e/fighting-style-options}}`, with
  the in-code comment calling it a **"BRIDGE PROTOTYPE"** and threading the homebrew pool **"the
  follow-up wiring step."**
- **`opt5e/fighting-style-option`** exists and is unit-tested (`fighting_style_feat_e2e_test.cljc`,
  `fighting_style_grant_matrix_test.cljc`) but only via hand-built pools — it never reaches plugin
  content.

## The plan — copy the draconic-ancestry pool pattern

Homebrew draconic ancestries are the working template for exactly this shape (an open pool =
built-in ++ homebrew, granted from a choice). Mirror it:

1. **Content type + spec.** Add `::e5/fighting-styles` to the content-types registry and a
   `homebrew-fighting-style` save spec (a field-schema via `bf/fields->spec`, like draconic
   ancestry — a fighting style is essentially name/key/option-pack + a `:props` mechanic or
   modifiers). Register it in `content_specs/save-specs`.
2. **Pool sub.** Add a `fighting-style-pool` sub backed by `::e5/plugin-vals` (built-in
   `opt5e/fighting-style-options` ++ `(mapcat (comp vals ::e5/fighting-styles) plugins)` mapped
   through `opt5e/fighting-style-option`), mirroring `::races5e/draconic-ancestry-pool`.
3. **Thread the pool into the feat registry.** Replace the hard-coded built-in-only registry in
   `template.cljc` (the "BRIDGE PROTOTYPE" block) so the feat's `grantable-pools`
   `:fighting-styles` `:options` come from the pool sub (built-in ++ homebrew). This is the
   "follow-up wiring step" the comment names.
4. **Demo item + test.** Once wired, add a `::e5/fighting-styles` item to the demo pack (on
   `feature/demo-content-tier`) and a build test that a feat granting it lands the style's
   mechanic on a built character.

## References

- Working pattern to copy: `src/cljs/orcpub/dnd/e5/spell_subs.cljs` — `draconic-ancestry-option`,
  `dragonborn-option-cfg`, `::races5e/draconic-ancestry-pool`.
- The hard-coded registry to replace: `src/cljc/orcpub/dnd/e5/template.cljc` (the "BRIDGE
  PROTOTYPE" grantable-pools block).
- The unwired option builder: `opt5e/fighting-style-option` / `opt5e/fighting-style-options`.
- Demo-tier context + the build-test pattern: `docs/kb/demo-content-tier.md` and
  `test/cljc/orcpub/dnd/e5/demo_content_build_test.cljc` (on the demo branch).
