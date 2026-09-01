# Demo / example content tier — design notes

Design-in-progress. Captures the decision made so far so it isn't lost, and flags a separate
larger feature (variant rules) that must NOT be fused into this one.

## Goal

Ship app-provided **example content** users can see and try, with two properties:
1. **read-only** in place, and
2. **copy-on-edit** — editing an example copies it into the user's own library, so app updates
   to the demo content never clobber a user's edits.

## Builds on the current content model

- User content is a `plugins` map: `{source → content-type → item-key → item}`. Content is read
  by merging every **enabled** source. Source and item both carry `:disabled?` /
  `:disabled-reason`. (See `library-management-and-conflicts.md`.)
- **SRD content is hardcoded and always-on** — not a source in the map.
- There is **no tier / read-only / example concept yet** — the demo tier is new, but it slots
  onto the source model.

## Decision: copy-on-edit + a provenance breadcrumb (NOT a live diff)

- Demo content is an **app-shipped source**, flagged `:read-only` / `:tier :demo`. It merges into
  the content pools like any source and shows in My Content.
- Editing a demo item offers **"Copy to my library to edit"** → a **full, independent copy** in a
  user source; edits apply to the copy.
- On graduation, **hide the demo original for that user** (existing `:disabled?`) — avoids a
  duplicate-key conflict and showing both the copy and the original.
- Keep a **provenance breadcrumb** on the copy — `copied from demo <key> v<n>`. It's a full copy,
  **not** a live diff. It lets the app later *offer* "the demo version improved — re-copy?" as an
  **opt-in**, with none of a diff's moving-base fragility.

### Why copy, not a diff/override

- The demo base is **not frozen** — it updates with app releases (that's why there's a version
  marker). A live diff against a *moving* base is fragile: every update has to re-apply overrides
  onto a base that may have changed shape, renamed a field, or removed the item, and can resolve
  wrong silently.
- Copy-on-edit's whole goal is to **insulate** edits from upstream; a diff **re-couples** them —
  the opposite.
- Demo items are a few KB, so a diff's storage saving is negligible.
- Contrast: starting equipment's base was a **frozen SRD class**, so a delta was viable there.
  Different situation.

## Open decisions (settle before building)

1. **Where demo content lives** — hardcoded like SRD (simplest, versioned with releases) vs a
   bundled `.orcbrew` the app loads (maintainers edit without code changes).
2. **Usable in the builder** (merged into the pools so people can build with it) vs **showcase
   only** in My Content.
3. **Per-account version + graduation state** — server-side (per-account, follows the user across
   devices, needs backend) vs localStorage (per-device, simple, matches `disable-overlay` /
   `health-dismissed`). Biggest fork.

## Separate, bigger feature — variant rules (do NOT fuse this in)

The design conversation surfaced a related but distinct idea: **variant rules** — swapping a
feature or level for a different one (3e substitution levels, Pathfinder archetypes, "this feat
grants STR instead of DEX").

- Its shape is the **opposite** of demo content: a variant is an **override that stays related to
  its base** (Fighter-but-with-a-swap), not a fork/copy. The parts you didn't swap should still
  track the base.
- That override is viable because a variant's base is usually **SRD**, which *is* frozen — the
  same shape as the starting-equipment delta.
- It's a **whole system of its own**: how a swap addresses a specific level's feature, how it
  composes with the modifier/entity model, how it shows in the builder. Design it **separately,
  later**. Trying to make one mechanism serve both demo content and variants is what made the demo
  tier feel hard.
