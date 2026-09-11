(ns orcpub.dnd.e5.requirements
  "THE registry of facts content asks about — \"while wielding two weapons\", \"while wearing no
   armor\". One entry per fact; anything that gates on it reads this rather than re-deriving the
   lookup. See docs/kb/requirements-registry.md for why, and for the evidence that `is a second
   weapon in hand` was hand-written in three features across two effect channels.

   PURE LEAF — data plus one pure fn, no requires. `modifiers.cljc` needs it, so it cannot pull
   anything back.

   ## The entry

     :gate  :build  — a fact derivable from the sheet; the engine evaluates :pred
            :toggle — a fact the player asserts about right now (the equipped?/deferred pattern)
            :text   — a TRIGGER: a moment in play. Carries NO :pred, structurally, so nothing can
                      accidentally claim to compute one (builder-form-schemas.md §4).
     :text  the phrasing, for the sheet and the PDF. Every entry has one; :props emits mechanics
            only, so without this a mechanic reaches neither.
     :pred  (fn [ctx] -> truthy). :build and :toggle only.

   ## Three-state, unknown ignored

   Same vocabulary as `weapons/matches?`: true = only when it holds, false = only when it does not,
   absent = either way. `meets-all?` iterates THIS registry rather than the author's spec, so an
   unknown key is ignored rather than failing — a pack authored against a build that knows more
   facts still applies what this build understands — and non-requirement keys like :bonus are
   skipped with no special case. See docs/kb/authoring-vocabulary.md.

   ## The context

   Assembled by the macro that builds the contributor, because that is where the ?-attributes
   resolve (see mod5e/ac-bonus). A predicate only ever sees what the ctx carries; adding a fact
   means adding it there too."
  #?(:clj (:refer-clojure :exclude [])))

(def requirements
  ;; :armor? / :shield? are the spellings :ac-bonus and :ac have SHIPPED with. Renaming them to
  ;; :armored? / :shielded? would have read marginally better beside :dual-wielding? and would have
  ;; cost a permanent alias on released data for nothing — so they keep their names (D9).
  {:armor?         {:gate :build :text "while wearing armor"
                    :pred (fn [{:keys [armor]}] (some? armor))}
   :shield?        {:gate :build :text "while wielding a shield"
                    :pred (fn [{:keys [shield]}] (some? shield))}
   :dual-wielding? {:gate :build :text "while wielding two weapons"
                    :pred (fn [{:keys [main-hand off-hand]}] (and (some? main-hand) (some? off-hand)))}
   :one-handed?    {:gate :build :text "while wielding a weapon in one hand and no other"
                    :pred (fn [{:keys [main-hand off-hand]}] (and (some? main-hand) (nil? off-hand)))}})

(defn meets-all?
  "Do every requirement this spec names hold in `ctx`? Iterating the registry (not the spec) is what
   makes an unknown key inert rather than fatal.

   NOTE `contains?` rather than a truthiness check: `false` is a MEANINGFUL value — it is the
   only-when-NOT state — so absent and false must stay distinguishable. An earlier version used
   `some`, which skips falsey values, so `{:shield? false}` read as absent and a Monk kept Unarmored
   Defense while holding a shield. The AC characterization sweep caught it."
  [spec ctx]
  (every? (fn [[k {:keys [pred]}]]
            (let [want (when (contains? spec k) (get spec k))]
              (or (nil? want)
                  (nil? pred)                       ; :text gates never block computation
                  (= (boolean want) (boolean (pred ctx))))))
          requirements))
