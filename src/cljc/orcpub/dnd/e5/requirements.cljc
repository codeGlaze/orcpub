(ns orcpub.dnd.e5.requirements
  "Registry of facts an effect can gate on: `:dual-wielding?`, `:armor?`.

   Entry: `{:gate :build|:toggle|:text  :text <phrasing>  :pred (fn [ctx] …)}`.
   `:text` entries are triggers and carry NO `:pred`.

   Pure leaf — `modifiers.cljc` requires it.
   Reference: docs/kb/requirements-registry.md."
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
  "Do `spec`'s requirements hold in `ctx`? Iterates the registry, so unknown keys are ignored.

   GOTCHA: tests `contains?`, not truthiness — `false` means only-when-NOT and must stay
   distinct from absent."
  [spec ctx]
  (every? (fn [[k {:keys [pred]}]]
            (let [want (when (contains? spec k) (get spec k))]
              (or (nil? want)
                  (nil? pred)                       ; :text gates never block computation
                  (= (boolean want) (boolean (pred ctx))))))
          requirements))
