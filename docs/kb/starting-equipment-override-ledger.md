# Starting-equipment override ledger — design

**Purpose.** Design for "start from a class, keep it, record only the diff": a homebrew
class references an SRD class's starting equipment as a base and stores a small **ledger**
of changes, instead of a full copy. Captures the shapes involved, the addressing decision,
and the error handling. **Design doc — not yet implemented.** Companion to
`starting-equipment.md` (the serializable `:equipment-selections`
form this builds on).

## The shape (what a ledger addresses)

The class-map equipment (see `starting-equipment.md`) has two kinds of container:

- **Fixed grants** — `:weapons`/`:armor`/`:equipment` are **maps keyed by item-key**
  (`{:javelin 4}`). Diffing these is trivial and stable: it's map-to-map, keyed by a
  keyword that never moves.
- **Choice groups** — `:equipment-selections` is a **vector** of groups; each group has a
  vector of options; each option a vector of `:grants` (`{:kind :key :qty}`) and `:choose`
  (`{:from …}`). Grants are effectively keyed by their item-key; but **groups and options
  today carry only a free-text `:name`** — no stable id.

## The missing shape: groups/options have no stable id

This is the one real gap. Free-text names are unstable — they can be blank, duplicated,
renamed, or reordered — so a ledger that addresses "the Armor menu / the Chain Mail
option" by name is fragile. **Decision: give each group and option a stable minted `:key`,
and address the ledger by keys, never names or positions.** This mirrors how the rest of
the app identifies content and the `menu-id` rule in
[growable-option-menus.md](growable-option-menus.md) ("stable + unique — NEVER gensym").

Addressing, per level:
- **fixed grant** → item-key (already stable).
- **group / option** → minted `:key`. Derive from the name via `common/name-to-kw`
  (`common.cljc:8-20`) at creation, de-duplicated within its parent; fall back to a stable
  counter when the name is blank. Never gensym (so it survives a reload/re-export).
- **grant inside an option** → `[:kind :key]` (a longsword and a shield differ by key).
- **sub-choice** (`:choose`) → positional index; `:from` is *not* unique (a bundle can
  offer "two martial weapons", i.e. two `{:from :martial}`).

**Keying unlocks the "map to map" instinct:** once groups/options have keys, the vectors
become order-independent maps (`(into {} (map (juxt :key identity)) groups)`), so the whole
equipment structure is nested maps and the diff is a uniform recursive key-compare — no
name-matching, immune to reorder.

## SRD bases are frozen forever

The base is an SRD class's equipment, which must first exist as serializable
`:equipment-selections` with minted keys (shared prerequisite with the "start from a class"
fill-in). **This extraction is now implemented** (on `feat/starting-equipment`):
`srd_starting_equipment.cljc`'s `builder-equipment` **decompiles** the live class — it
reads the built `class-option`'s fixed grants as data and recovers each choice grant by
*applying the modifier fn* — so the base is derived from the one live definition, with no
hand-transcribed copy to drift. A decompile→recompile round-trip against every live class
(`orcpub.starting-equipment-test`) is the guard that the derived base equals what the class
actually produces.

**Once minted, those SRD group/option keys are permanent.** Renaming or renumbering them
silently rewrites what every dependent character's ledger resolves to — the same class of
breakage that made past key typos permanent: they were papered over with redirect **shims**
([LANGUAGE_SELECTION_FIX.md](../LANGUAGE_SELECTION_FIX.md); the pre-2024 wizard-possessive
spell-key map in `spell_subs.cljs`), never fixed by renaming. Treat SRD equipment keys the
same: fix a bad key with a shim, never a rename.

### Name fidelity is a key-stability prerequisite

Because a group/option/sub-choice **key is minted from its name** (`name-to-kw`), the
decompiler must reproduce the SRD's own names *verbatim* — a rename is a silent key change.
One real instance was caught and fixed: the recompiler was routing every sub-choice through
`new-starting-equipment-selection`, which prepends `"Starting Equipment: "` and appends a
`<none>` option, so a grouped focus the live class names `"Arcane Focus"` came back as
`"Starting Equipment: Arcane Focus"` — a different minted key. The fix branches the
recompiler: grouped-equipment picks mirror the live `equipment-option` exactly (name
verbatim, no prefix, no `<none>`); weapon-class picks keep the prefixed builder, which is
what live uses for *them*. The round-trip test now compares **every** nested selection name
(not just top-level) and the **quantities** on choice grants, so a future rename or a
dropped count fails the build rather than silently shifting keys. A non-pool sub-choice is
**enumerated** rather than dropped, and a truly undecompilable one throws — a starting
equipment must never silently vanish from a derived base.

## The op set (tiny)

Applied as a `reduce` over the resolved base:
- **`:add`** — append a fixed grant, a group, an option, a grant, or a sub-choice.
- **`:replace`** — swap the value at an addressed key.
- **remove is `:replace` with no replacement** — the empty-replacement special case, so
  there's no separate remove op.

The human never authors ops. The builder does fill-in → the user edits the **full
materialized form** (the existing UI) → on save the app **derives the ledger by diffing the
edited form against the resolved base** and exports `{:starting-equipment {:base <class>
:ledger [...]}}`. Hand-editing this is not a supported path (as with all of the app's
minted keys).

## Shapes / edge cases to handle (the checklist)

1. **No stable id on groups/options** — the core gap; solved by minting keys (above).
2. **`:choose` duplicates** — two `{:from :martial}` in one option; address by index.
3. **Quantity-only change** — same key, new `:qty` → a `:replace` of that grant.
4. **Base must exist as data** — SRD `:selections` extracted to `:equipment-selections`
   with frozen keys; a ledger against an un-extracted base is impossible. **Done:**
   `builder-equipment` decompiles the live class (see "SRD bases are frozen forever"),
   round-trip-verified against all 12 classes.
5. **Ledger op targets a key absent from the resolved base** (base drifted, or a stale
   ledger) — **skip and surface a warning**, never silently drop or crash.
6. **Reorder** — free with key-addressing; positions are irrelevant.
7. **Blank / nil guards** — reject a blank/nil addressable key (group/option key, item-key)
   at author time with a clear error; never mint a `:key` from a blank name without the
   counter fallback. The resolver must fail soft: a malformed ledger yields the base plus a
   surfaced error, not a broken character.
8. **Fixed grants** — already keyed by item-key; pure map-to-map, no special handling.
9. **Portability caveat** — `:base <srd-class>` makes the file depend on that class being
   loaded. Safe for SRD (always present); a base pointing at another *homebrew* class is
   not portable and should be disallowed or resolved-and-inlined on export.

## References

- `starting-equipment.md` — the `:equipment-selections` form + compiler.
- [name-to-kw-audit.md](name-to-kw-audit.md) — `name-to-kw`: lossy, non-invertible; key-gen snares.
- [growable-option-menus.md](growable-option-menus.md) — the "stable, never gensym" menu-id rule.
- [srd-vs-plugin-content.md](srd-vs-plugin-content.md) — what's SRD vs plugin (what can be a base).
- [../CONTENT_RECONCILIATION.md](../CONTENT_RECONCILIATION.md) — key/name reconciliation, fuzzy matching.
