(ns orcpub.dnd.e5.option-catalog
  "Generic helpers for assembling plugin-contributed options.

   This is the seam for the content-extensibility direction
   (docs/kb/content-extensibility.md): instead of each parent entity open-coding
   how its child options are grouped, they share one mechanism here.

   Phase 1 introduces `by-parent`, generalizing the per-type `(group-by :race ...)`
   / `(group-by :class ...)` calls used to attach subraces to races and subclasses
   to classes.

   LEAF NAMESPACE: depends on nothing else in the app (no events/subs/views/specs).
   Keep it that way — the registry/catalog code must stay dependency-light to avoid
   the circular deps the codebase already works around (see content-extensibility
   decisions D7/D8).")

(defn by-parent
  "Group plugin-contributed options by the value of `parent-key` on each option.

   `(by-parent :race subraces)` => {<race-key> [subrace ...] ...}

   Behaviour-identical to `(group-by parent-key options)`: order within each group
   follows input order. Exists so subraces, subclasses, and future nested option
   types resolve their parent buckets through one place rather than ad-hoc calls."
  [parent-key options]
  (group-by parent-key options))

(defn plugin-options
  "All options of `content-key` contributed across `plugin-vals`.

   `plugin-vals` is the seq of (enabled) plugin maps — e.g. the value of the
   `::e5/plugin-vals` subscription. Each plugin map holds content under namespaced
   keys (`:orcpub.dnd.e5/boons`, `…/invocations`, …) mapping option-key -> option.

   `(plugin-options :orcpub.dnd.e5/boons plugin-vals)` => seq of boon maps.

   Behaviour-identical to the per-type `(mapcat (comp vals <content-key>) plugin-vals)`
   extraction. This is the catalog READ primitive: a new content type becomes
   discoverable simply by being stored under its content-key, with no bespoke
   extraction code."
  [content-key plugin-vals]
  (mapcat #(-> % content-key vals) plugin-vals))
