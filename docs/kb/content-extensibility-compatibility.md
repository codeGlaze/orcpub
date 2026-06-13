# Content Extensibility — Backward-Compatibility Audit

**Purpose:** Inventory the persisted data formats that the content-extensibility
redesign must not break, derive the hard invariants, and assess the proposed direction
against them. Existing `.orcbrew` libraries and saved characters must keep working.

**Status:**
- Section 1 (formats), Section 5 (risk surfaces), Section 6 (safety nets) are
  **verified from code** (file:line).
- Section 4 (proposal assessment) judges the **proposed, not-implemented** design in
  [content-extensibility.md](content-extensibility.md) against the invariants.
- Datomic *schema* was not separately audited this pass; the character *payload* sent
  to the backend is the strict entity (Section 1b), which is what matters here. Flagged.

**Branch note:** references read on the monolithic frontend layout of this branch; on
`agents/develop` the views layer is split. Grep symbols to confirm.

---

## 1. Persisted formats (verified)

These are the artifacts on users' disks / in the DB. We do **not** control them once
exported or saved.

### 1a. orcbrew / plugins (homebrew libraries)

The in-app plugins map and the exported `.orcbrew` file share one shape.

- `::e5/plugins` = `(map-of string? ::plugin)` — keyed by **option-pack name (string)**.
- `::plugin` = `(map-of ::content-keyword (or ::homebrew-items boolean?))`.
- `::content-keyword` = a **qualified keyword whose namespace is exactly
  `"orcpub.dnd.e5"`** (e.g. `:orcpub.dnd.e5/boons`, `…/spells`, `…/subraces`), or the
  literal `:disabled?`.
- `::homebrew-items` = `(map-of <keyword> ::homebrew-item)`; each item must carry
  `::option-pack`.
- Source: `src/cljs/orcpub/dnd/e5.cljc` (whole file — `::plugins`, `::plugin`,
  `::content-keyword`, `merge-all-plugins`).
- Stored under `[option-pack plugin-key key]` by `reg-save-homebrew`
  (`events.cljs` ~533); `key` = `(common/name-to-kw name)`.
- Exported file = EDN string of **one pack's `::plugin` map**
  (`::e5/export-plugin`, `events.cljs` ~3601, dispatched with
  `(str (new-plugins option-pack))`).
- Imported via `import-val/validate-import` → `merge-all-plugins`
  (`events.cljs` ~3807, ~3894); validated against `::e5/plugins`.

**What this constrains:** the per-type plugin key (`::e5/boons` etc.) and the
`orcpub.dnd.e5` namespace are part of the on-disk contract. A new content type's plugin
key must live in that namespace or it fails `::content-keyword` and won't import.

### 1b. Character (strict entity)

- `::se/entity` = `(keys :opt [::selections ::values])` + no duplicate selections.
- `::selection` = `(keys :req [::key] :opt [::option ::options])`.
- `::option` = `(keys :opt [::key ::int-value ::map-value ::selections])` — nests.
- `::values` = `(map-of qualified-keyword? some?)`.
- Source: `src/cljc/orcpub/entity/strict.cljc` (whole file).
- Round-trip: `char5e/to-strict` / `from-strict` (`character.cljc` ~266 / ~329).

**What this constrains:** a saved character records its choices as `::se/key` keywords
at selection/option nodes. Those keys are the addresses of choices. If a redesign
changes the **key of a selection or option a character has already chosen**, the stored
choice no longer resolves (orphaned).

**⚠️ name-to-kw is a creation-time default, NOT a re-derivable contract.** Keys are
*originally* produced by `common/name-to-kw` of a name (`common.cljc` ~19), but the
durable contract is the **stored `:key`**, not the name. This already bit the project:
class option keys were derived from the display `:name`, and when a plugin-source suffix
was folded into that name for display, `name-to-kw` produced a *different* key and
orphaned saved characters. The fix (`feature/name-keyword-fix`, off the same base
`d42e05d` as this branch) establishes the rule:

- **Identity derives from a stable id, never from a display string.** Selection keys now
  derive from `:class-key` via `options.cljc` `spell-selection-key`, not `name-to-kw` of
  the title (commit `fe54963`).
- **Display is a separate slot.** `option-cfg` carries `::plugin-source` distinct from
  `::name` (commit `39a054b`); the source suffix is a preference-gated *display* concern,
  never part of the key (`9a709c0`, `show-class-source-suffix`).
- A **reconciler** heals already-orphaned keys on load (`content_reconciliation.cljs`,
  commits `a3e2615`/`4289871`) — the existing shim for this exact failure.

**Rule for the catalog/grant work:** when building option-cfgs from catalog items
(boons, lineages, …), pass the item's **stored `:key`** to `option-cfg` — do not let it
re-derive from `:name` (today `pact-boon-options` re-derives, which is the same latent
footgun). Never call `name-to-kw` on a display-manipulated name.

### 1c. localStorage

Per-builder draft keys + the plugins blob + the current character
(`db.cljs` ~32–49: `character`, `plugins`, `boon`, `spell`, …). Read back through
`reg-local-store-cofx`, which **spec-validates and drops** anything invalid
(`db.cljs` ~252). So a format change that fails the spec silently discards the draft.

### 1d. Backend

Characters are saved to the server as the strict entity (`::char5e/save-character`,
`events.cljs` ~435). Datomic schema not audited here; the compatibility surface is the
same strict-entity payload as 1b.

## 2. Who owns what

| Artifact | Owner | Can we change its shape freely? |
|----------|-------|-------------------------------|
| orcbrew files | users (exported, shared, re-imported) | **No** — read + forward compat |
| saved characters (local + DB) | users | **No** — must keep resolving |
| in-memory app-db wiring | us | Yes |
| registration call-sites | us | Yes |

## 3. Hard invariants (non-negotiable)

1. **Read invariant:** the app must load existing `::e5/plugins` maps and existing
   strict-entity characters **unchanged**, with no migration step required of the user.
2. **Plugin-key invariant:** existing per-type keys (`:orcpub.dnd.e5/boons`, etc.) and
   the `orcpub.dnd.e5` namespace stay valid; new types add keys, never rename old ones.
3. **Selection-key invariant:** selection/option `::t/key`s that characters can already
   have chosen must not change identity (see Section 5).
4. **Forward invariant (decide explicitly):** an orcbrew exported by the new version
   should still import into the **old** hosted version — i.e. keep the export shape, or
   accept an ecosystem split.

## 4. Proposal assessment against the invariants

### Layer 1 — content-type registry: **compatibility-neutral**

Changes only internal registration wiring (which call-sites register events/subs/routes).
No persisted shape changes. Satisfies all invariants by construction, provided the
descriptor reuses the **existing** plugin key and route keyword for each existing type
(invariant 2). Verdict: **safe, zero-migration.**

### Layer 2 — catalogs/grants: **safe if derived, not reformatted**

The redesign can satisfy the invariants **only if catalogs are derived over the existing
storage** rather than introducing a new on-disk format. Precedent is in the code:
subraces have no "catalog" on disk — it is computed by a subscription
(`group-by :race` over `::e5/subraces`, `spell_subs.cljs` ~887). If boons/invocations/
etc. catalogs are likewise derived from today's `::e5/boons`/`::e5/invocations` maps:

- orcbrew files keep loading (storage unchanged) → invariant 1, 2 hold.
- The danger is invariant 3: a grant defines a *selection*, and the selection's key is
  what a character stored its choice under. Migrating boons from positional-arg to
  `grant-choice` must **preserve the existing selection key and the option keys**
  (still `name-to-kw` of the same names), or already-built characters orphan their
  pact-boon choice. This is the one place Layer 2 can break compatibility, and it is
  testable (Section 7).

Verdict: **safe if (a) catalogs derive from existing plugin maps and (b) selection/
option keys are preserved.** Not safe if it introduces a new storage shape or renames
selections.

## 5. Specific risk surfaces (verified)

1. **Selection-key stability is also a safety-net dependency.**
   `content_reconciliation.cljs` hardcodes per-class archetype selection keys
   (`subclass-selection-keys`: `:otherworldly-patron`, `:martial-archetype`, …) and a
   `content-type->field` map. Changing selection keys breaks not just stored characters
   but the missing-content detector too. Treat selection keys as a public contract.
2. **`merge-all-plugins` is `merge-with merge` (shallow, two levels).**
   (`e5.cljc`.) Import merges packs by option-pack then by content-key. Any new
   catalog grouping must not assume deeper merge semantics than this.
3. **localStorage cofx silently drops spec-invalid data** (`db.cljs` ~252). A draft/
   format change that tightens a spec will quietly discard in-progress user drafts.
4. **The `orcpub.dnd.e5` namespace requirement** on `::content-keyword` (Section 1a)
   means a "type" field added for catalogs must not replace the namespaced plugin key
   that import relies on.

## 6. Existing safety nets (lean on these, don't reinvent)

- **Import validation** — progressive, auto-clean, conflict detection, text
  normalization (`import_validation.cljs`).
- **Conflict resolution** — pre-import modal for duplicate keys
  (`docs/CONFLICT_RESOLUTION.md`, `views/conflict_resolution.cljs`).
- **Content reconciliation** — detects missing/renamed content refs in a character and
  suggests matches (`content_reconciliation.cljs`, `docs/CONTENT_RECONCILIATION.md`).
- **Spec validation on every load** — `reg-local-store-cofx` and import both validate.

These are the migration tooling if anything turns out non-additive.

## 7. Migration & rollback posture

- **Aim for zero-migration:** derive over existing storage, preserve keys. If that holds,
  there is nothing to migrate and rollback is just reverting code.
- **Prove it with fixtures, not eyeballs.** Before/after the subrace spike and the boon
  migration, load a representative `.orcbrew` and a saved character (one that has chosen
  a pact boon) and assert the built character is identical — same selection keys, same
  resolved options. Round-trip `to-strict`/`from-strict` should be byte-stable.
- **If a change is unavoidably non-additive** (e.g. a selection key must change), do not
  silently rename: route it through the existing reconciliation/alias path so old keys
  still resolve, and document the alias.
- **Forward compat:** keep the export shape identical so new-version exports import on
  the old hosted site. If the shape must change, gate it and announce it.

## Related

- [content-extensibility.md](content-extensibility.md) — the design under audit.
- [content-extensibility-decisions.md](content-extensibility-decisions.md) — decisions.
- `docs/CONTENT_RECONCILIATION.md`, `docs/CONFLICT_RESOLUTION.md`,
  `docs/ORCBREW_FILE_VALIDATION.md` — the safety nets in detail.
