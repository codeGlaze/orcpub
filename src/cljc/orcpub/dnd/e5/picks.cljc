(ns orcpub.dnd.e5.picks
  "A character's stored picks (the entries under `::entity/options`): walking, reading, rewriting,
   removing and putting back. Pure data, shared by the browser and the server. A selection stores
   its picks as a vector when it is `::t/multiselect?` (kept, empty, after its last pick goes),
   otherwise as the one entry map; an absent selection was never chosen.
   GOTCHA: never depend on the iteration order of a set or map here; it differs between the JVM
   and the browser (`testing-infrastructure.md`)."
  (:require [orcpub.entity :as entity]
            [orcpub.dnd.e5.library :as library]))

(defn walk
  "`opts` (::entity/options) with `(f path entry)` applied to every chosen entry, where `path` is
   the vector of selection keys from the root down to it."
  [opts f]
  (letfn [(entry [path e]
            (if (map? e)
              (let [e' (f path e)]
                (cond-> e' (map? (::entity/options e')) (update ::entity/options #(walk path %))))
              e))
          (walk [path o]
            (into {} (map (fn [[sel v]]
                            (let [p (conj path sel)]
                              [sel (if (sequential? v) (mapv #(entry p %) v) (entry p v))])))
                  o))]
    (if (map? opts) (walk [] opts) opts)))

(defn- walk-typed
  "`opts` (::entity/options) with `f` applied to every pick that could be of `content-type`
   (`library/pick-types`); `content-type` nil: every pick."
  [opts content-type f]
  (walk opts (fn [path e]
               (if (or (nil? content-type) (seq (library/pick-types path [content-type])))
                 (f e)
                 e))))

(defn keys-of
  "Set of `character`'s pick keys that could be of `content-type` (see `walk-typed`)."
  [character content-type]
  (let [ks (atom #{})]
    (walk-typed (::entity/options character) content-type
                #(do (when-let [k (::entity/key %)] (swap! ks conj k)) %))
    @ks))

(defn relink
  "`character` with each pick of `from` that could be of `content-type` rewritten to `to`, as
   {:character :rewrote} (see `content-reconciliation/reconcile-former-keys`). `content-type` nil:
   every pick."
  [character content-type from to]
  (let [rewrote (atom [])
        opts (walk-typed (::entity/options character) content-type
                         #(if (= from (::entity/key %))
                            (do (swap! rewrote conj {:from from :to to}) (assoc % ::entity/key to))
                            %))]
    {:character (cond-> character (seq @rewrote) (assoc ::entity/options opts))
     :rewrote @rewrote}))

;; An address names one pick: [selection key, chosen entry's key] pairs from the root, the format
;; of the app's option paths (`entity/get-entity-path`), e.g. [:class :ranger :levels :level-7].
;; A key, never an index: removing one pick must not move another's address.

(defn- entry-index [v k]
  (first (keep-indexed (fn [i e] (when (= k (::entity/key e)) i)) v)))

(defn- options-path
  "The `get-in` path to the ::entity/options map holding the last pair of `address`, or nil when
   an entry on the way is not stored."
  [character address]
  (loop [path [::entity/options] [[sel k] & more] (partition 2 address)]
    (if (empty? more)
      path
      (let [v (get-in character (conj path sel))
            step (if (sequential? v)
                   (when-let [i (entry-index v k)] [sel i])
                   (when (= k (::entity/key v)) [sel]))]
        (when step (recur (into path (conj step ::entity/options)) more))))))

(defn remove-at
  "`character` without the pick at `address`, as {:character :removed}; `:removed` is
   {:address :entry :multiselect?}, what `put-at` takes, or nil when nothing is stored there.
   A multiselect keeps its vector, emptied if this was its last pick, as unticking does; a single
   pick's selection is dissoc'd. `:multiselect?` records which, since neither shows it after."
  [character address]
  {:pre [(even? (count address)) (seq address)]}
  (let [sel (nth address (- (count address) 2))
        k (last address)
        opath (options-path character address)
        v (when opath (get-in character (conj opath sel)))]
    (cond
      (and (sequential? v) (entry-index v k))
      {:character (assoc-in character (conj opath sel)
                            (with-meta (vec (remove #(= k (::entity/key %)) v)) (meta v)))
       :removed {:address address :entry (nth v (entry-index v k)) :multiselect? true}}

      (and (map? v) (= k (::entity/key v)))
      {:character (update-in character opath dissoc sel)
       :removed {:address address :entry v :multiselect? false}}

      :else {:character character :removed nil})))

(defn put-at
  "`character` with `removed` (from `remove-at`) stored again at its address, or nil when an entry
   on the way to it is no longer stored. `:multiselect?` true appends it to the selection's vector
   (creating one), unless that vector already holds its key; false makes it the selection's entry,
   replacing any there.
   GOTCHA: checks no rule; whether it still fits is the caller's question."
  [character {:keys [address entry multiselect?]}]
  (let [sel (nth address (- (count address) 2))
        opath (options-path character address)]
    (when opath
      (if multiselect?
        (update-in character (conj opath sel)
                   (fn [v]
                     (cond (not (sequential? v)) [entry]
                           (entry-index v (::entity/key entry)) v
                           :else (with-meta (conj (vec v) entry) (meta v)))))
        (assoc-in character (conj opath sel) entry)))))
