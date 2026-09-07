(ns orcpub.dnd.e5.grant-pools
  "THE registry of grantable pools. One entry per pool; registering a pool is one entry here and
   nothing else — that is the acceptance gate the direction doc sets:

     \"exposing a SECOND pool in a builder must be a ~1-line registration. If it isn't trivially
      cheap, the retooling failed its own purpose; STOP and reassess.\"
     (docs/kb/content-extensibility-direction.md, Maintainability)

   ## Why each entry is a FUNCTION, not a description

   The pools do not agree on what a built-in is. Languages and draconic ancestries hold RAW data
   that a constructor turns into options; fighting styles' built-ins are ALREADY option-cfgs and
   only the homebrew half needs constructing; skills have no homebrew half at all.

   Describing that with keys (`:built-in-compiled?` and friends) would put a branch over pool KINDS
   in the shared code — the named anti-pattern:

     \"Pool-kind-specific logic lives in each pool's own definition, never as branches inside
      `grant`. A `cond` over pool kinds inside `grant` = the D14 god-function trap = failure.\"

   So a pool's definition is `(fn [plugin-vals] -> [option-cfg …])` and each one absorbs its own
   irregularity. `assemble` and `grant-selection` stay uniform and know nothing about any pool.

   ## Invariants
   - Options are option-cfgs carrying a real `::t/key` — grant-selection's `:key`/`:filter` modes
     address entries by it.
   - Every open pool derives from `plugin-vals`, the single resolved-content seam, never from raw
     `:plugins` (the variant forward-compat pin: `raw → resolve-variants → resolved → pools`).
   - `:offerable-by` is the scoping metadata the direction doc calls for (\"which builders may offer
     me\", so a feat can't grant \"choose a subrace\"). Declared now; no builder UI consumes it yet.
   - `:tags` are the selection tags a grant from this pool carries — the D30 fix. The character
     builder routes a selection to a TAB by its tags, so a granted language must carry the same
     `:profs :language-profs` the bespoke `language-selection-aux` does or it lands on a different
     tab than the choice it replaces. grant-selection merges these into its own `#{:grant <pool>}`.
     A pool with no `:tags` keeps the generic pair only.
   - A closed pool ignores `plugin-vals`. That is the whole difference between open and closed —
     not a second mechanism."
  (:require [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.content-pools :as pools]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.skills :as skills5e]
            [orcpub.dnd.e5.options :as opt5e]))

(def pools
  "{pool-key {:name … :offerable-by #{…} :tags #{…} :options-fn (fn [plugin-vals] -> [option-cfg …])}}

   To register a pool: add one entry. Nothing else changes — not template-selections, not any
   assembly fn, not any sub."
  {:languages
   {:name "Language"
    :offerable-by #{:feat :race :subrace :background :class :subclass}   ; not :monster — a stat block grants nothing (builder-disposition-audit.md)
    :tags #{:profs :language-profs}            ; = language-selection-aux, so it lands on Proficiencies
    ;; built-in ++ homebrew, both raw; one constructor over the lot
    :options-fn (fn [plugin-vals]
                  (map opt5e/language-option
                       (pools/pool plugin-vals ::e5/languages langs5e/languages)))}

   :fighting-styles
   {:name "Fighting Style"
    :offerable-by #{:feat :class :subclass :background}
    ;; built-ins are ALREADY option-cfgs; only the homebrew half is raw. Absorbed here so no
    ;; caller has to know that.
    :options-fn (fn [plugin-vals]
                  (concat opt5e/fighting-style-options
                          (map opt5e/fighting-style-option
                               (pools/homebrew-entries plugin-vals ::e5/fighting-styles))))}

   :skills
   {:name "Skill"
    :offerable-by #{:feat :race :subrace :background :class :subclass}
    :tags #{:profs :skill-profs}               ; = skill-selection
    ;; closed: a fixed vocabulary with no homebrew half. It ignores plugin-vals, and that is the
    ;; only way a closed pool differs from an open one.
    :options-fn (fn [_] (map opt5e/skill-option skills5e/skills))}})

(defn assemble
  "The registry, resolved against the current content, in the shape grant-selection reads:
   {pool-key {:name … :options [option-cfg …]}}. Uniform — it never inspects a pool."
  [plugin-vals]
  (into {}
        (map (fn [[k {:keys [name tags options-fn]}]]
               [k {:name name :tags tags :options (options-fn plugin-vals)}]))
        pools))
