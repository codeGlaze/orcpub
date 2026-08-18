# Library management + import/export key conflicts

How the **My Content** homebrew library works, and how the app resolves the one
hard problem it has: two pieces of content that claim the same key. This is
reference documentation — the mechanism and the reasoning behind it — not a
status list. Roadmap items live in [docs/TODO.md](../TODO.md).

## Data model

Homebrew content is stored as a `plugins` map, keyed by source, then content
type, then item key:

```clojure
{"My Pack" {:orcpub.dnd.e5/spells   {:fireball {:name "Fireball" :key :fireball …}}
            :orcpub.dnd.e5/monsters {:goblin   {:name "Goblin"   :key :goblin   …}}}}
```

A **source** (a.k.a. option source / plugin / `.orcbrew` file) is one top-level
entry. My Content lists sources; each source expands into per-content-type
categories (`my-content-types` drives that list, so empty categories hide and a
single add-content menu creates the first item of a missing type).

## Why a duplicate key is a problem

Content is read by merging every enabled source. For most content types a
duplicate key is harmless — the item merely appears twice in a pool (feats,
languages, backgrounds, subclasses, …). But for six types the merge **collapses
same-key items to one**, and *which* copy survives is decided by source-name hash
order — nondeterministic from the user's point of view:

```clojure
orcbrew-validation/collision-risk-types
;; => #{::e5/spells ::e5/races ::e5/classes ::e5/monsters ::e5/encounters ::e5/selections}
```

For these "collapse" types, leaving two same-key copies both enabled yields an
unpredictable winner. That is the latent bug the resolution machinery exists to
remove.

## Enable / disable model

Both a source and an individual item carry an optional `:disabled?` flag in the
plugin data (so it travels with an exported `.orcbrew`). Disabled content is
filtered out of the merge but kept in the library, dimmed and reachable via each
source's "show disabled (N)" toggle.

An item may also carry `:disabled-reason`, which records **why** it is off:

| `:disabled-reason` | meaning | badge |
|--------------------|---------|-------|
| *(absent)* | you turned it off | blue "N off" |
| `:compat` | the app turned it off to resolve a key conflict | amber "N off · compatibility" |

`source-disabled-counts` buckets a source's disabled items by reason to drive the
two library-header badge colors. A user toggle always **clears** `:disabled-reason`
— once you make a choice, the app's earlier compat-disable no longer applies.

## Duplicate-key resolution

`detect-duplicate-keys` finds two kinds of clash: **internal** (two sources
inside one multi-source import) and **external** (an import vs. already-loaded
content). Both feed the conflict modal, which offers per-conflict decisions:

- **Rename the import** — give the incoming item a fresh key (`generate-new-key`).
- **Rename the existing one** — keep the import's key as base and re-key the
  already-loaded item instead (the moderator's "decide what stays base").
  Renames are source-scoped and rewrite references (subclass→class, etc.) via
  `apply-key-renames`; the item's own `:key` field is updated too, so a renamed
  item cannot re-collide.
- **Keep both, turn one off** — for the collapse types, import (or keep) both but
  set `:disabled?` + `:disabled-reason :compat` on the loser, so exactly one is
  enabled and the winner is deterministic.
- **Keep both** — honest only for the pool types, where a duplicate is harmless.
- **Skip** — drop the incoming item.

The **internal keeper-picker** handles the all-one-import case: nothing is
already loaded, so nothing is "base" — the user picks which source keeps the key
(default = first source alphabetically, overridable).

Because the import popup only fires *on import*, already-loaded libraries can
still hold a silent nondeterministic winner. The **"Check for conflicts"** button
in My Content (`::e5/check-content-conflicts`) runs the same `correct-library`
analysis on demand and opens the modal in `:library` mode.

## Mutual exclusion — one enabled twin at a time

When two collapse-type items share a key across sources, only one may be enabled
(the ≤1-enabled invariant). That state is made legible instead of magic, and it
is **computed at display time** from a `collision-twin-index` over the live
plugins — nothing new is stored:

- **Per-row note.** A disabled item with an enabled same-key twin reads
  `off — "<twin>" in <source> is on`; the enabled winner reads
  `on — duplicate "<this>" in <source> is off`. Fires only for a genuine
  cross-source collision in a collision-risk type — never for a plain user-disable
  with no live twin (`twin-note`).
- **Library banner.** A line at the top of My Content —
  `N items off because a duplicate is on — review` — links into the conflict
  modal (`mutual-exclusion-off-count`).
- **Swap on enable.** `::e5/toggle-plugin-item` is exclusivity-aware: enabling a
  collision-risk item turns its live same-key twins OFF first (marking them
  `:disabled-reason :compat`), then turns this one ON, in a single atomic
  `set-plugins`, with a toast naming both items and their sources. Off-first is
  deliberate: only-one-enabled stays true throughout, so the nondeterministic
  winner never flickers. Restoring the other side is one click away, so the swap
  is automatic (no confirm gate) — the toast plus the recomputed notes make it
  self-evident (`enabled-twin-paths`).

## Where the code lives

- `orcpub.dnd.e5.orcbrew-validation` — the canonical `collision-risk-types` set;
  `detect-duplicate-keys`, `correct-library`, `generate-new-key`,
  `apply-key-renames`; and the mutual-exclusion helpers `collision-twin-index`,
  `twin-note`, `enabled-twin-paths`, `mutual-exclusion-off-count`.
- `orcpub.dnd.e5.events` — `::e5/toggle-plugin`, `::e5/toggle-plugin-item`
  (swap-aware), the `:apply-conflict-resolutions` / `:start-conflict-resolution`
  flow, and `::e5/check-content-conflicts`.
- `orcpub.dnd.e5.views` — My Content UI: `my-content`, `my-content-item`,
  `my-content-type`, `source-disabled-counts`, the per-row twin note, and the
  library banner.
- `orcpub.dnd.e5.views.conflict-resolution` — the conflict modal and the export
  warning modal.

## Related

- [keyword-trap-name-repair.md](keyword-trap-name-repair.md) — the adjacent case
  where a name derives an *invalid* key and the item is quarantined for repair.
