(ns orcpub.dnd.e5.content-pools
  "The POOL half of the content-extensibility spine (see
   docs/kb/content-extensibility-direction.md). A *pool* is an open, type-addressed
   collection of grantable things: built-in entries (which live in code) ++ homebrew
   entries (which loaded orcbrew packs contribute). A consumer turns a pool into a choice
   with `orcpub.template/selection-cfg` whose options are compiled per entry — that is the
   GRANT half, and it stays at the call site (this ns is a pure, dependency-leaf primitive).

   `plugin-vals` is the resolved sequence of plugin packs — in the app it is the single
   `:orcpub.dnd.e5/plugin-vals` subscription that ALL plugin-derived content already reads
   through. That one indirection is the seam where variant (_copy/_mod) resolution will slot
   in later WITHOUT changing pools or grants (direction doc, the variant pin).")

(defn homebrew-entries
  "Every homebrew entry of one content type across all loaded packs. `plugin-key` is the
   content keyword (e.g. :orcpub.dnd.e5/draconic-ancestries). This is exactly the
   `(mapcat (comp vals plugin-key) plugin-vals)` shape used throughout the app, named once."
  [plugin-vals plugin-key]
  (mapcat (comp vals plugin-key) plugin-vals))

(defn pool
  "An open, type-addressed pool: the built-in entries (passed in — they live in code)
   followed by the homebrew entries derived over the resolved plugin packs. Order is
   built-in-first so existing characters' choices keep their position."
  [plugin-vals plugin-key built-in]
  (concat built-in (homebrew-entries plugin-vals plugin-key)))
