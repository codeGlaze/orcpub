# Renaming a custom item orphans it on every character that has it

**Status:** open · **Severity:** data-visible, live in production · **Branch:** `fix/item-stable-identity` (cut from `integration`)

> Filed here rather than on GitHub because Issues are disabled for this
> repository.

## Summary

A custom item's identity is its **name**. Rename the item and every character
referencing it silently loses it — the item disappears from the sheet and its
modifiers stop applying. No error, no warning.

This is live today and has nothing to do with sharing.

## Reproduction

Owner renames "Bastard Sword" → "Bastard Blade" (same entity, same `:db/id`).
Option keys before and after:

```
BEFORE:  :bastard-sword  ∈ magic-weapon-options
AFTER:   :bastard-blade  ∈ magic-weapon-options
         :bastard-sword  → gone
```

A character stores a bare option key:

```clojure
::entity/options {:magic-weapons [{::entity/key :bastard-sword
                                   ::entity/value {...}}]}
```

After the rename nothing resolves `:bastard-sword`, so `entity/build` produces
no modifiers for it and the sheet omits it entirely.

## Root cause

`src/cljc/orcpub/dnd/e5/magic_items.cljc` — `add-key` overwrites `:key`
unconditionally, deriving it from the name:

```clojure
(defn add-key [item]
  (assoc item :key (common/name-to-kw (name-key item))
              :name (name-key item)))
```

`expand-magic-items` calls it on every item. Meanwhile
`src/cljs/orcpub/dnd/e5/equipment_subs.cljs` already contains an ID-based
fallback that is **dead code**, because `:key` is always set by the time it
runs:

```clojure
(let [item-key (or key (keyword (str "id-" id)))]   ; the id branch is unreachable
```

## Why it was built this way — do NOT "just switch to :db/id"

Investigated before proposing a fix, because the obvious change breaks
something important.

1. **The ~805 static SRD items have no `:db/id`.** They are code literals in
   `raw-magic-items`, so a name-derived key is the only option for them.
   `add-key` was written for that case, and custom items were funnelled through
   the same function. This part is a genuine oversight.

2. **Name-keying is deliberate across the whole content system** — 58 uses of
   `name-to-kw` covering races, classes, spells, monsters, backgrounds and
   feats. `.orcbrew` exports are plain EDN keyed by name-derived keywords, so
   content stays resolvable when imported into a different account or
   deployment. **Datomic entity IDs cannot do this**: `:db/id` is only
   meaningful inside one database, and re-transacted content gets fresh IDs.
   Switching character references to `:db/id` would fix rename and break
   portability — which is exactly what the shared-character-URL feature needs.

3. **The scheme is safe for immutable content and unsafe for mutable content.**
   SRD items never get renamed. User items do. Nobody noticed the difference
   when custom items inherited the scheme.

Prior scar from the same root cause — `test/fixtures/keyword-trap.orcbrew`: a
class named "9 Lives" derives `:9-lives`, which is not a valid key, so the
content imported "successfully" and then silently never appeared in the
character builder.

## Proposed fix: a stable, portable key — neither the name nor `:db/id`

Store a random key on the item at creation time (`::mi/key`), used for
character references:

- **Survives rename** — not derived from the name.
- **Survives export/import into another database** — it is data that travels
  with the item, unlike `:db/id`.
- **Does not collide between users** — two people's "Bastard Sword" get
  different keys, so an imported shared character binds to the right item with
  no rename-on-import pass and no reference rewriting.
- Static SRD items keep their name-derived keys, which stays correct for
  immutable content.

## Migration — additive, no character writes

1. **Freeze the current name-derived key** onto each existing item as
   `::mi/legacy-key`, once, in a backfill. Frozen and never recomputed — that
   is what makes it survive later renames.
2. **Register both keys as options**: the frozen legacy key marked
   `legacy-only?` (resolvable, hidden from the pickers), and the stable key
   offered normally.
3. New selections take the stable key and are rename-proof by construction.
   Existing characters resolve on the frozen key indefinitely, through any
   number of renames.

This is the same additive pattern used on `fix/custom-item-classification`:
nothing is retracted, and no character is rewritten.

## Sequencing

Land this **before** the shared-character-URL feature. Sharing on top of
name-as-identity ships a wrong-item collision on day one: if a shared character
embeds a custom "Bastard Sword" and the recipient already has one, the
character binds to the recipient's item — different stats, no error.

## Scope

Cut from `integration`. `refactor/content-extensibility` forks from the same
base (`e632297`) and touches this area, so cutting from the common base lets
the fix merge into both.

## Checked and NOT a bug

- `::mi5e/armor` in the item builder's Type dropdown looks like it should
  break `(= :armor type)`, but does not. Reagent serialises a keyword
  attribute value with `name`, so the option reaches the DOM as `"armor"` and
  `set-item-type`'s `(keyword "armor")` gives back a bare `:armor`. Verified
  end to end by driving the real `<select>` in a browser: the stored type is
  `:armor`, the Base Armor selector renders, and the item lands in the Magic
  Armor picker and `all-armor-map`. The namespaced keyword is cosmetically
  odd and worth tidying, but it is not a defect.

## Related, not covered here

- `::char5e/characters`, `::party5e/parties` and `::folder5e/folders` share the
  auth-gated subscription pattern fixed for items on
  `fix/custom-item-classification`, and so share that bug: sign in without a
  page reload and the list stays empty for the whole session.
