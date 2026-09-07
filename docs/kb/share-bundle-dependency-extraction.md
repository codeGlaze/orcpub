# Share-bundle dependency extraction

How to compute the complete set of homebrew ("plugin") content a single
character depends on, so it can be bundled into a shareable file (the
"embed the content in the shared link" feature). This is the data-model map
that makes the feature buildable; it is the output of a dedicated
dependency-surface spike and is the reference the extractor is implemented
against.

All file:line citations are against the tree as of the spike (develop-era
`orcbrew_validation`/`content_reconciliation` layout).

## Why this is not trivial

A shared character link carries only the character's server id. The server
returns the character's *selections*, which reference homebrew content by
**key** (`:my-subclass`, a custom spell). If the recipient does not have the
matching `.orcbrew` loaded, those keys dangle and the shared view renders
broken content. So vanilla SRD characters already share fine; the whole point
of the bundle is to make **homebrew-using characters portable**.

The obvious seed, `selected-plugin-options` (subs.cljs), is a red herring: it
only collects keys from `[::entity/options :optional-content]`, i.e. which
*source packs are toggled on*, not the content the character actually
references. The extractor has to walk the character's real selection tree.

## The content types (master list)

A plugin map is `{"Source Name" {::e5/<type> {key def} ... :disabled? bool}}`.
The authoritative, complete list of content-type keys is `content-specs/save-specs`
(content_specs.cljc:33-45) — every type with a builder/save handler, exactly
once. Thirteen types:

    ::e5/spells      ::e5/monsters     ::e5/encounters   ::e5/backgrounds
    ::e5/languages   ::e5/invocations  ::e5/boons        ::e5/selections
    ::e5/feats       ::e5/races        ::e5/subraces     ::e5/subclasses
    ::e5/classes

Do not hardcode this list in a switch. `classify-plugins-for-export` and the
fill/validate passes (orcbrew_validation.cljs:340-348) treat any key whose
namespace is `orcpub.dnd.e5` as content; the extractor should do the same
(generic namespace check). Note `content-type-names` (orcbrew_validation.cljs)
and `required-fields` list only 12 — they omit `::e5/boons`. `save-specs` is
the master.

### Important scope limit: magic items are NOT plugins

There is no `::e5/magic-items` / `::e5/weapons` / `::e5/armor` / `::e5/items`
plugin type. Custom magic items, weapons, and armor live in a separate
per-user **server-side** store, `::mi5e/custom-items`, fetched over the API
(equipment_subs.cljs:35-47, `expand-magic-items` at :57), and are referenced
in the character entity by **DB id**, not by a plugin key. They cannot be
pulled from the plugins map. A v1 orcbrew-style bundle therefore cannot carry
custom magic items; a character using one shows it as missing on the
recipient side (fail-soft handles this). Bundling them needs a separate
extraction path over the items API — a deliberate fast-follow, not part of the
plugin closure.

## How a character references content (direct edges)

Selections are a nested tree under `[::entity/options ...]`; each node carries
an `::entity/key` (entity.cljc:12-13).

The existing purpose-built direct extractor is
`content-reconciliation/extract-content-keys` (content_reconciliation.cljs:97-106),
covering six structural types by explicit `get-in`:

- race: `[::entity/options :race ::entity/key]`
- subrace: `[::entity/options :race ::entity/options :subrace ::entity/key]`
- class: `[::entity/options :class]` is a **vector**; each element's `::entity/key`
- subclass: nested under each class at a class-specific archetype selection key;
  the code probes the known set `subclass-selection-keys`
  (content_reconciliation.cljs:20-29, e.g. `:martial-archetype`, `:sacred-oath`)
- background: `[::entity/options :background ::entity/key]`
- feats: `[::entity/options :feats]` vector, each `::entity/key`

Types NOT covered by that extractor, and where they actually live:

- **spells** (known/prepared): deep under each class's spell-selection nodes
  (e.g. `:wizard-spells-known-1`, options.cljc:503-518); each chosen spell is a
  child option whose `::entity/key` is the spell key.
- **languages**: under `[::entity/options :languages ...]` (`:ref [:languages]`,
  multiselect, options.cljc:853-864).
- **invocations**: warlock invocation selection; chosen key = `::entity/key`.
- **selections** (`::e5/selections`, custom option groups): injected into the
  template; chosen options surface as `::entity/key` nodes.

There is no dedicated "collect ALL `::entity/key`" helper, but the building
block is `entity/flatten-options` (entity.cljc:299) → each flattened entry's
`::t/key` is the whole option node, from which `::entity/key` reads. A full
key-set is one sweep over `flatten-options`. It also captures non-content keys
(ability increases, feature toggles), so it MUST be filtered against the
plugins index — keep only keys that exist in some `::e5/<type>`. The content
TYPE of each key comes from which index map contains it, so the extractor is
type-agnostic on the entity side: sweep keys, filter by index, learn the type
from the index.

## Transitive edges (the crux)

What each pulled def can itself reference and therefore must also pull:

- **subclass -> class** (hard): `homebrew-subclass` requires `:class` = parent
  class key (classes.cljc:23-24).
- **subrace -> race** (hard): `homebrew-subrace` requires `:race` (races.cljc:15).
- **race/subrace -> spells**: granted via a `:spells` vector, each
  `{:value {:key ...}}` (spell_subs.cljs:124-134), and via `:level-modifiers`
  entries of `type :spell` referencing `(:key value)` (spell_subs.cljs:163-184).
- **class/subclass -> spells**: granted via `:paladin-spells` / `:cleric-spells`
  / `:warlock-spells` maps of `{level {slot spell-key}}` (spell_subs.cljs:401-429),
  plus `:level-modifiers :spell`.
- **class <- spells (REVERSE edge, the subtle one)**: a homebrew class's spell
  list is NOT stored on the class. Each spell declares membership via
  `:spell-lists {class-key true}`; `plugin-spell-lists` folds it into
  `[class-key level]` (spell_subs.cljs:1217-1231). To give a homebrew class its
  intended spell list, pull EVERY homebrew spell whose `:spell-lists` names that
  class key — a reverse scan, not a forward pointer.
- **race/subrace -> languages (NAME strings, not keys)**: `homebrew-race` has a
  `:languages` set of language NAME strings (races.cljc:9-13). Resolving to a
  homebrew `::e5/languages` key needs `name-to-kw` matching. This is the one
  genuinely unreliable edge — a name that does not round-trip silently drops the
  language (the "name-to-kw disease"). Surface unresolved, do not drop silently.
- **feats / any `:props`**: `make-feat-modifiers` (options.cljc:3335-3391) turns
  prop keys into references; only `:language` (and non-existent-as-plugin
  weapon-profs) point at content. Enumerate against that switch, do not guess.
- **class -> selections**: `:level-selections` names a selection by `:type`
  indexing `selection-map` (spell_subs.cljs:341-366).

Depth is shallow. The longest realistic chain is
subclass -> parent class -> (spells folded in by the reverse scan), and
race -> granted spells/languages -> nothing further. Spells, languages,
monsters, feats are leaves. A **2-hop fixpoint plus a single reverse
spell-list pass** is sufficient.

## Existing dangling-reference detection to reuse

- `content-reconciliation/check-content-availability` /
  `generate-missing-content-report` (content_reconciliation.cljs:213-257),
  surfaced by `::char5e/missing-content-report` (subs.cljs) and the
  `missing-content-warning` banner (character_builder.cljs:1937-1977). Checks
  the six structural types, excludes SRD built-ins via hardcoded sets
  (content_reconciliation.cljs:164-207). Does NOT check spells, languages,
  invocations, selections, monsters — those are the blind spots.
- `missing-spell-keys` (options.cljc:471-480) / `warn-missing-spells!`: exactly
  the class<->spell edge above.

Reuse the banner machinery to SURFACE anything the closure fails to resolve,
rather than dropping it silently.

## Extraction algorithm

    extract-bundle(character, plugins):
      ;; index: {::e5/type {key {:def def :source source}}}, last-source-wins
      ;; built by iterating plugins and keeping keys whose namespace is
      ;; orcpub.dnd.e5 (matches the content-map subs)

      ;; PASS 1 DIRECT: sweep every ::entity/key via entity/flatten-options,
      ;; keep those present in the index; record (type,key) from the index.

      ;; PASS 2 CLOSURE (fixpoint, ~2 iters): for each (type,key) in the set,
      ;; follow outgoing-refs(type, def) and add any referenced homebrew key:
      ;;   subclass -> [::e5/classes (:class def)]
      ;;   subrace  -> [::e5/races (:race def)]
      ;;   race/subrace/class/subclass -> spell keys from :spells[*].value.key,
      ;;     :level-modifiers type :spell, :paladin/cleric/warlock-spells maps
      ;;   race/subrace -> language keys via name-to-kw on :languages names
      ;;   feats/props -> :language keys
      ;;   class -> :selections from :level-selections :type

      ;; REVERSE spell-list pass (class-driven, one scan): for each homebrew
      ;; class in the set, add every homebrew spell whose :spell-lists names it.

      ;; EMIT: rebuild a plugins-shaped map grouped by original source.

### Risk areas

- Reverse spell-list edge: highest value, easy to forget, but reliable once
  implemented (exact key match).
- Race `:languages` name strings: the one fuzzy edge; use name-to-kw, surface
  misses.
- `:props` grab-bag: enumerate against the `make-feat-modifiers` switch.
- `::e5/selections` by `:type`: treat those `:type` values as selection keys.
- Magic items / weapons / armor: out of the plugins model entirely; exclude
  from v1 or add a parallel `::mi5e/custom-items` extractor.

### Verdict

Tractable. A complete-enough bundle is achievable with a bounded 2-hop closure
over the plugins map, reusing `content-reconciliation` patterns for the direct
set, `entity/flatten-options` for the generic sweep, and the reverse
spell-list rule from `plugin-spell-lists`. Hardest parts are the two
non-key-based edges: the reverse class<->spell membership (structurally
inverted) and the name-string race->language grant (requires fuzzy
resolution). Everything else is exact-key graph closure over a shallow graph.

## Key files to implement against

- content_reconciliation.cljs (direct extraction + SRD-exclusion sets)
- entity.cljc:299 (`flatten-options` for the generic sweep)
- spell_subs.cljs:1217-1231 (reverse spell-list rule)
- content_specs.cljc:33-45 (master type list)
- orcbrew_validation.cljs:340-348 (the generic `::e5`-namespace iteration to
  reuse when re-emitting the bundle, and the sanitize/validate gate to reuse on
  the recipient side when importing a shared bundle)
