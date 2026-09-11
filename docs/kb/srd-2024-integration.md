# SRD 2024 Integration — Early Analysis

Captured 2026-02-25 during Tier 1 data extraction work. No implementation yet — just
architectural notes for when this becomes active.

## Key Constraint: Mix-and-Match

The 2024 SRD is marketed as "backwards compatible" with 2014. In practice, users
**mix and match** freely — a 2014 Fireball alongside a 2024 Ranger in the same
character. This is not a clean version swap; it's additive content where both
rulesets coexist.

## How Data Extraction Helps

The Tier 1 splits isolate SRD content into dedicated `_data.cljc` files:

| Data file | Content type | 2024 impact |
|-----------|-------------|-------------|
| `monsters_data.cljc` | 356 stat blocks | New/revised monsters |
| `spells_data.cljc` | ~400 spells | New/revised spells |
| `magic_items_data.cljc` | ~300 items | New/revised items |
| `classes_data.cljc` | 12 class builders | Revised class features |

The logic shells (`monsters.cljc`, `spells.cljc`, etc.) stay stable — they define
specs, derived lookups, and expansion logic that don't change between SRD versions.
Adding a second content source is a composition problem, not a logic change.

## The Real Problem: Overlapping Keys

Both SRDs define entries with the same names but different stats. Examples:
- "Fireball" exists in both, potentially with different damage/range
- "Ranger" class features differ significantly between versions
- Monster stat blocks may have revised HP, AC, or abilities

The plugin system already handles name collisions for homebrew (last-write-wins via
content reconciliation). But SRD-vs-SRD is different — users expect **both** to be
"correct" and want to pick per-entry, not per-source.

## Possible Approaches (not decided)

1. **Version-tagged entries**: Each entry carries `:srd-version #{:2014 :2024}`.
   Logic shells filter/merge based on user preference. Cleanest but most work.

2. **Separate data files**: `spells_data_2014.cljc` + `spells_data_2024.cljc`.
   Logic shells concat both. Simple but doubles file count and doesn't solve
   collision UX.

3. **2024 as a built-in plugin**: Ship 2024 content as an `.orcbrew`-format bundle
   that loads via the existing plugin system. Users toggle it on/off like homebrew.
   Leverages existing infrastructure but conflates official content with homebrew.

4. **Overlay with precedence**: 2024 entries override 2014 by default, with a
   user toggle to revert specific entries. Matches how most tables actually play.

## What Needs Investigation

- **Structural changes**: Does the 2024 SRD introduce new entity shapes (new
  keyword types, new modifier patterns) that would require logic shell changes?
  Or is it purely data-level differences within the existing schema?

- **Class builder impact**: `classes_data.cljc` isn't pure data — it's option
  builder functions that encode class progression logic. 2024 class revisions
  may require new builder functions, not just data tweaks.

- **Template tree**: `template.cljc` assembles the full character creation tree
  from class/race/background options. If 2024 changes the structure of any of
  these (e.g., new "species" replacing "race"), the template tree needs updating.

- **Content reconciliation**: `content_reconciliation.cljs` currently treats all
  SRD content as "builtin" and excludes it from missing-content warnings. With
  two SRD versions, "builtin" needs a more nuanced definition.

## Addendum 2026-09-11 — versioning granularity (UNVALIDATED THEORY)

> **Status: exploratory. Not a decision, not validated, no code written.**
> Produced in a side conversation about the companions leaf, by an agent that was not
> working the 2024 integration. Another agent owns that work and may have better
> information. Treat everything below as a hypothesis to test or discard — the four
> approaches above are still the live options.

### The case that constrains it

The owner's example: someone plays the **2024 druid** but their table uses the **2014 Wild
Shape CR rule**. Mixing inside a single feature, not between entries.

If that has to work, it rules out three of the four approaches above. Separate data files,
2024-as-a-plugin and overlay-with-precedence all pick a winner per *entry*; none can say
"this class from here, that one rule from there." Only version-tagging survives — and it has
to tag something smaller than an entry.

### What the engine already does

`apply-options` (`entity.cljc:663`) sorts every modifier by `::mods/order` before a
dependency-ordered application:

```clojure
modifiers (sort-by ::mods/order (collect-modifiers-2 raw-entity options template))
```

And `modifier` (`entity_spec.cljc:95`) replaces rather than combines —
`(update entity k (fn [_] new-value))`. Last writer wins, deterministically.

So an explicit precedence field and a last-wins rule both exist. **This may be a filter over
an existing pipeline rather than new architecture.** That is the part most worth testing.

### The trap: filter, don't reorder

`modifier` replaces, but `cum-sum-mod`, `vec-mod` and `set-mod` **accumulate**, and all four
are in live use in `classes.cljc`. Keep both editions' modifiers and merely reorder them, and
every accumulating modifier double-counts — two editions of the same bonus stacking silently.

Dropping the unpicked edition's modifiers *before* the pipeline is the only operation that is
safe across all four types.

### Granularity for the UI

Nobody toggles hundreds of modifiers. But classes are already shaped as named features owning
modifier lists (`:levels {N {:modifiers [...]}}`), so the natural control is one per feature —
"Wild Shape: 2014 | 2024" — flipping the small set beneath it. That grouping exists; it does
not need inventing.

### Consequence to decide deliberately

Mixing will produce incoherent characters — 2024 moves subclass choice to level 3, so pairing
it with a 2014 subclass feature at 2 leaves a hole. Letting that happen is probably right
("the world is a crazy place"), but the sheet should be able to **show what is mixed**, so a
DM sees the seams instead of finding them mid-session. Cheap to design in now, awkward later.

### What would validate or kill this

- Does `::mods/order` actually carry meaningful values, or is it mostly nil? Only its use at
  the sort site was checked.
- Can a modifier be traced back to the feature that produced it, at build time? The UI
  grouping depends on it.
- Do the 2024 changes stay within the existing modifier vocabulary, or introduce shapes the
  macros cannot express? That is the "structural changes" question above, unanswered.

## Related Files

- `src/cljc/orcpub/dnd/e5/*_data.cljc` — SRD data (Tier 1 extraction)
- `src/cljc/orcpub/dnd/e5/template.cljc` — character creation tree
- `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs` — missing content detection
- `src/cljs/orcpub/dnd/e5/spell_subs.cljs` — plugin content aggregation
- Agent KB: [srd-vs-plugin-content.md](srd-vs-plugin-content.md) — what's hardcoded vs from plugins
