# Modifier slot vs. trait slot — why a plain trait map is inert in `:modifiers`

**TL;DR:** A plain trait map (`{:name … :page … :level … :summary …}`, plain keys)
only does something when it sits in a **`:traits`** vector. Put the same map in a
**`:modifiers`** vector and it is **inert** — no trait, no name, no page reference.
Verified against code *and* in the live app on 2026-06-10.

## The mechanism

`entity/apply-modifiers` reduces each modifier through `modifiers/modifier-fn`,
which destructures **only** the namespaced keys a real modifier carries:

```clojure
(defn modifier-fn [{:keys [::value ::fn ::deferred-fn]}]   ; ::mods/value, ::mods/fn, …
  (if (and deferred-fn value) (deferred-fn value) fn))
```

A plain trait map has **plain** keys (`:name`, `:page`, …), none of the
`:orcpub.modifiers/*` keys. So `modifier-fn` returns `::fn` = `nil`; back in
`apply-modifiers`, `nil` is not a fn, the reduce over `nil` is a no-op, and the
entity comes back unchanged. Real traits reach the sheet only via
`mod5e/trait-cfg` / `mod5e/trait`, which build an actual `::mods/fn` that conj's
the trait (with its `:page`) onto the character.

In a `:traits` vector the map is consumed directly as trait data — names, pages,
and summaries all show. Same map, two slots, only `:traits` does anything.

## Worked example: `opt5e/evasion` and the three Evasions

`opt5e/evasion` (options.cljc) returns a plain trait map with a **short** summary
("when you succeed on a DEX save to take half damage…").

- **Monk** (classes.cljc ~1342) and **Rogue** (~2040) call it inside a **`:traits`**
  vector → a proper named "Evasion" trait. These were never broken.
- **Hunter / Superior Hunter's Defense** (~1925) calls `(opt5e/evasion 15 93)`
  inside a **`:modifiers`** vector → **inert**. The displayed Evasion trait and its
  p.93 come from the sibling `mod5e/trait-cfg` (which has the **long** "red dragon's
  fiery breath" summary). The Hunter's `opt5e/evasion` line is dead weight and could
  be deleted.

Only the Hunter's hand-written `trait-cfg` ever omitted `:name` — that was the
features-tab black-screen defect (nil name → `aloof-sort-by` → `lower-case` on nil).
Monk/Rogue carried a name via `opt5e/evasion`, so they never crashed.

## How to tell which definition you're looking at (don't trust the page number)

Both the `opt5e/evasion` map and the Hunter `trait-cfg` carry `:page 93`, so the
page number can't distinguish them. The **summary text** can: short = `opt5e/evasion`,
long "red dragon's fiery breath…" = the `trait-cfg`.

## Verification recipe (live)

In the dev build, call the real compiled fns from the page and confirm inertness —
no need to build a level-15 character:

```js
const w = window, c = w.cljs.core, mods = w.orcpub.modifiers, opts = w.orcpub.dnd.e5.options;
const m = opts.evasion.call(null, 15, 93);
mods.modifier_fn.call(null, m);                              // => nil
const base = c.hash_map.call(null, c.keyword.call(null,'traits'), c.vector.call(null));
c.pr_str(base) === c.pr_str(mods.apply_modifiers.call(null, base, c.vector.call(null, m))); // => true (unchanged)
```

Related: [entity-options-architecture.md](entity-options-architecture.md) (modifier
pipeline), the features-tab black-screen work on `claude/character-black-screen-feature-i8lvk3`.
